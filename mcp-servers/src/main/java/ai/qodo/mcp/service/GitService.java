/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.service;

import ai.qodo.mcp.config.GitMcpConfiguration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service()
@ConditionalOnProperty(name = "mcp.git.enabled", havingValue = "true", matchIfMissing = true)
public class GitService {

    private static final Logger logger = LoggerFactory.getLogger(GitService.class);
    
    // Pattern to extract host from HTTPS/HTTP URLs like https://github.com/user/repo.git
    private static final Pattern HTTPS_URL_PATTERN = Pattern.compile("^https?://([^/:]+)(?::\\d+)?/.*$");
    
    // Pattern to extract host from SSH URLs like git@github.com:user/repo.git or ssh://git@github.com/user/repo.git
    private static final Pattern SSH_URL_PATTERN = Pattern.compile("^(?:ssh://)?[^@]+@([^:/]+)(?::\\d+)?[:/].*$");
    
    private final GitMcpConfiguration mcpConfiguration;
    private final Map<String, String> hostToSshKeyMap;
    private final String defaultSshKeyFile;

    /**
     * Creates a GitService with configurable SSH key mappings.
     * 
     * SSH keys can be configured via properties:
     * - mcp.git.ssh.default-key: Default SSH key file name (default: id_rsa)
     * - mcp.git.ssh.host-keys: Comma-separated host=keyfile mappings
     *   Example: github.com=github_key,ssh.dev.azure.com=azure_key
     * 
     * The service will automatically detect the host from the remote URL and use
     * the appropriate SSH key. If no mapping is found, it falls back to the default key.
     * 
     * Your ~/.ssh/config file is also respected by JGit's SSH implementation,
     * so you can configure host aliases there as well.
     */
    public GitService(
            GitMcpConfiguration gitMcpConfiguration,
            @Value("${mcp.git.ssh.default-key:id_rsa}") String defaultSshKey,
            @Value("${mcp.git.ssh.host-keys:#{null}}") String hostKeyMappings) {
        this.mcpConfiguration = gitMcpConfiguration;
        this.defaultSshKeyFile = defaultSshKey;
        this.hostToSshKeyMap = parseHostKeyMappings(hostKeyMappings);
        
        logger.info("GitService initialized with default SSH key: {}", defaultSshKeyFile);
        if (!hostToSshKeyMap.isEmpty()) {
            logger.info("SSH key mappings configured: {}", hostToSshKeyMap);
        }
        
        SshSessionFactory.setInstance(new SshdSessionFactory());
    }
    
    /**
     * Parses the host-to-key mappings from configuration string.
     * Format: "host1=key1,host2=key2"
     * Example: "github.com=github_key,ssh.dev.azure.com=azure_key"
     */
    private Map<String, String> parseHostKeyMappings(String mappings) {
        Map<String, String> result = new HashMap<>();
        if (mappings == null || mappings.isBlank()) {
            return result;
        }
        
        for (String mapping : mappings.split(",")) {
            String[] parts = mapping.trim().split("=", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                result.put(parts[0].trim().toLowerCase(), parts[1].trim());
                logger.debug("Registered SSH key mapping: {} -> {}", parts[0].trim(), parts[1].trim());
            }
        }
        return result;
    }
    
    /**
     * Extracts the host from a Git remote URL.
     * Supports both SSH and HTTPS URL formats:
     * - git@github.com:user/repo.git -> github.com
     * - ssh://git@github.com/user/repo.git -> github.com
     * - https://github.com/user/repo.git -> github.com
     * - git@ssh.dev.azure.com:v3/org/project/repo -> ssh.dev.azure.com
     * - https://dev.azure.com/org/project/_git/repo -> dev.azure.com
     */
    String extractHostFromUrl(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }
        
        // Try SSH URL pattern first
        Matcher sshMatcher = SSH_URL_PATTERN.matcher(remoteUrl);
        if (sshMatcher.matches()) {
            return sshMatcher.group(1).toLowerCase();
        }
        
        // Try HTTPS URL pattern
        Matcher httpsMatcher = HTTPS_URL_PATTERN.matcher(remoteUrl);
        if (httpsMatcher.matches()) {
            return httpsMatcher.group(1).toLowerCase();
        }
        
        logger.warn("Could not extract host from URL: {}", remoteUrl);
        return null;
    }
    
    /**
     * Determines the SSH key file to use based on the remote URL's host.
     * Falls back to the default key if no specific mapping is found.
     */
    String getSshKeyFileForUrl(String remoteUrl) {
        String host = extractHostFromUrl(remoteUrl);
        if (host != null && hostToSshKeyMap.containsKey(host)) {
            String keyFile = hostToSshKeyMap.get(host);
            logger.debug("Using SSH key '{}' for host '{}'", keyFile, host);
            return keyFile;
        }
        logger.debug("Using default SSH key '{}' for URL: {}", defaultSshKeyFile, remoteUrl);
        return defaultSshKeyFile;
    }

    /**
     * Ensures roots are initialized before proceeding with operations.
     * Delegates to configuration.
     */
    private void ensureRootsInitialized(ToolContext toolContext) throws InterruptedException {
        mcpConfiguration.ensureRootsInitialized(toolContext);
    }


    @Tool(name = "git_clone_repository", description = "Clones a remote Git repository to the local filesystem. " +
            "Accepts both HTTPS and SSH URLs (e.g., 'https://github.com/user/repo.git' or 'git@github.com:user/repo.git'). The repository will be cloned to a directory named after the repository in the configured " + "default path. Supports SSH authentication using standard SSH keys from ~/.ssh/. Use this to create a local copy of a remote repository.")
    public String cloneRepository(
            @ToolParam(description = "The remote url for the repository to clone") String remoteUrl,
            ToolContext toolContext) throws GitAPIException, InterruptedException {
        // Ensure roots are initialized before proceeding
        ensureRootsInitialized(toolContext);

        String repoName = extractRepoName(remoteUrl);
        String targetPath = this.mcpConfiguration.getDefaultLocalPath() + File.separator + repoName;
        logger.info("Using path from roots: {}", targetPath);

        // Configure SSH transport with URL-specific key file selection
        TransportConfigCallback transportConfigCallback = createSshTransportConfigForUrl(remoteUrl);

        Git git = Git
                .cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(new File(targetPath))
                .setTransportConfigCallback(transportConfigCallback)
                .call();

        git.close();
        return "Repository cloned successfully to repositoryPath: " + targetPath;
    }


    /**
     * Creates a transport configuration callback that configures SSH to use a specific private key
     * based on the remote URL's host.
     * <p>
     * The key selection works as follows:
     * 1. Extract the host from the remote URL (e.g., github.com, ssh.dev.azure.com)
     * 2. Look up the host in the configured host-to-key mappings
     * 3. If found, use the mapped key file; otherwise, use the default key
     * <p>
     * Additionally, JGit respects your ~/.ssh/config file, so host aliases configured there
     * will also work (e.g., Host github-work, Host azure-personal).
     *
     * @param remoteUrl The remote URL to determine which SSH key to use (can be null for default)
     */
    private TransportConfigCallback createSshTransportConfigForUrl(String remoteUrl) {
        File homeDir = FS.DETECTED.userHome();
        File sshDir = new File(homeDir, ".ssh");
        
        // Determine which key file to use based on the URL
        String keyFileName = getSshKeyFileForUrl(remoteUrl);
        File keyFile = new File(sshDir, keyFileName);

        logger.debug("=== SSH Configuration Validation ===");
        logger.debug("Remote URL: {}", remoteUrl);
        logger.debug("Detected host: {}", extractHostFromUrl(remoteUrl));
        logger.debug("Selected SSH key file: {}", keyFileName);
        logger.debug("Detected user home: {}", homeDir);
        logger.debug("SSH directory: {}", sshDir.getAbsolutePath());
        logger.debug("SSH directory exists: {}", sshDir.exists());
        logger.debug("SSH directory readable: {}", sshDir.canRead());
        logger.debug("Target key file: {}", keyFile.getAbsolutePath());
        logger.debug("Key file exists: {}", keyFile.exists());
        logger.debug("Key file readable: {}", keyFile.canRead());

        // List all files in SSH directory for debugging
        if (sshDir.exists() && sshDir.isDirectory()) {
            File[] files = sshDir.listFiles();
            if (files != null && files.length > 0) {
                logger.debug("Files in SSH directory:");
                for (File f : files) {
                    logger.debug("  - {} (readable: {}, size: {} bytes)", f.getName(), f.canRead(), f.length());
                }
            } else {
                logger.warn("SSH directory is empty or cannot list files");
            }
        }

        // Verify the key file exists and is readable
        if (!keyFile.exists()) {
            logger.error("SSH key file does not exist: {}", keyFile.getAbsolutePath());
            throw new RuntimeException("SSH key file not found: " + keyFile.getAbsolutePath() + 
                    ". Configure mcp.git.ssh.default-key or mcp.git.ssh.host-keys property.");
        }
        if (!keyFile.canRead()) {
            logger.error("SSH key file is not readable: {}", keyFile.getAbsolutePath());
            throw new RuntimeException("SSH key file is not readable: " + keyFile.getAbsolutePath() + 
                    ". Check file permissions (should be 600).");
        }

        logger.debug("SSH key validation passed. Key file size: {} bytes", keyFile.length());
        logger.debug("=== End SSH Configuration Validation ===");

        return transport -> {
            if (transport instanceof SshTransport sshTransport) {
                logger.debug("Configuring SSH transport for connection using key: {}", keyFileName);

                // Create a custom SSH session factory with the specific key file
                // The public key is automatically derived from the private key - no .pub file needed
                // JGit will also read ~/.ssh/config for additional host configurations
                SshdSessionFactory sshSessionFactory = new SshdSessionFactoryBuilder()
                        .setPreferredAuthentications("publickey")
                        .setSshDirectory(sshDir)
                        .setHomeDirectory(homeDir)
                        .build(null);

                sshTransport.setSshSessionFactory(sshSessionFactory);
                logger.debug("SSH session factory configured successfully");
            }
        };
    }

    @Tool(name = "get_status", description = "Get the status of the Git Repository based on the repository path")
    public String getStatus(String repositoryPath) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            Status status = git.status().call();

            StringBuilder result = new StringBuilder();
            result.append("Git Status:\n");
            result.append("Branch: ").append(git.getRepository().getBranch()).append("\n\n");

            if (!status.getAdded().isEmpty()) {
                result.append("Added:\n");
                status.getAdded().forEach(file -> result.append("  + ").append(file).append("\n"));
            }

            if (!status.getModified().isEmpty()) {
                result.append("Modified:\n");
                status.getModified().forEach(file -> result.append("  M ").append(file).append("\n"));
            }

            if (!status.getRemoved().isEmpty()) {
                result.append("Removed:\n");
                status.getRemoved().forEach(file -> result.append("  - ").append(file).append("\n"));
            }

            if (!status.getUntracked().isEmpty()) {
                result.append("Untracked:\n");
                status.getUntracked().forEach(file -> result.append("  ? ").append(file).append("\n"));
            }

            if (status.isClean()) {
                result.append("Working tree clean\n");
            }

            return result.toString();
        }
    }

    @Tool(name = "git_log", description = "Retrieves the commit history for a Git repository. Returns a formatted " +
            "log" + " showing commit hashes, authors, dates, and messages. Use this to view the history of changes in" +
            " the " + "repository.")
    public String getLog(String repositoryPath, int maxCount) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            Iterable<RevCommit> logs = git.log().setMaxCount(maxCount).call();

            StringBuilder result = new StringBuilder();
            result.append("Git Log:\n\n");

            for (RevCommit commit : logs) {
                result.append("Commit: ").append(commit.getName()).append("\n");
                result
                        .append("Author: ")
                        .append(commit.getAuthorIdent().getName())
                        .append(" <")
                        .append(commit.getAuthorIdent().getEmailAddress())
                        .append(">\n");
                result.append("Date: ").append(commit.getAuthorIdent().getWhen()).append("\n");
                result.append("Message: ").append(commit.getFullMessage()).append("\n\n");
            }

            return result.toString();
        }
    }

    @Tool(name = "git_branches", description =
            "Lists all branches in the repository. Shows the current branch with " + "an asterisk (*) and all other " +
                    "available branches. Use this to see what branches exist in the repository.")
    public String listBranches(String repositoryPath) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            List<Ref> branches = git.branchList().call();

            StringBuilder result = new StringBuilder();
            result.append("Branches:\n");

            String currentBranch = git.getRepository().getBranch();

            for (Ref branch : branches) {
                String branchName = branch.getName().replace("refs/heads/", "");
                if (branchName.equals(currentBranch)) {
                    result.append("* ").append(branchName).append("\n");
                } else {
                    result.append("  ").append(branchName).append("\n");
                }
            }

            return result.toString();
        }
    }

    @Tool(name = "git_create_branch", description = "Creates a new branch in the repository with the specified name. "
            + "The new branch will be created from the current HEAD position. Use this when you need to start working" +
            " " + "on a new feature or fix.")
    public String createBranch(String repositoryPath, String branchName) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            git.branchCreate().setName(branchName).call();
            String output = "Branch '" + branchName + "' created successfully";
            logger.debug("Response returned {}", output);
            return output;
        }
    }

    @Tool(name = "git_checkout", description =
            "Switches the working directory to the specified branch. Use this to " + "change between different " +
                    "branches in the repository to work on different features or versions.")
    public String checkoutBranch(String repositoryPath, String branchName) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            git.checkout().setName(branchName).call();
            String output = "Switched to branch '" + branchName + "'";
            logger.debug("Response returned {}", output);
            return output;
        }
    }

    @Tool(name = "git_commit", description = "Creates a new commit with the specified message and files. If no files "
            + "are specified, all changes in the working directory will be staged and committed. Use this to save " +
            "your " + "changes to the repository history.")
    public String commit(String repositoryPath, String message,
                         List<String> files) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            // Add files
            if (files == null || files.isEmpty()) {
                git.add().addFilepattern(".").call();
            } else {
                for (String file : files) {
                    git.add().addFilepattern(file).call();
                }
            }

            // Commit
            RevCommit commit = git.commit().setMessage(message).call();

            return "Committed: " + commit.getName() + "\n" + message;
        }
    }

    /**
     * Checks if the repository has uncommitted changes.
     */
    private boolean hasUncommittedChanges(Git git) throws GitAPIException {
        return !git.status().call().isClean();
    }

    /**
     * Formats the git status for clear LLM understanding.
     */
    private String formatGitStatus(Status status) {
        StringBuilder result = new StringBuilder();

        if (!status.getModified().isEmpty()) {
            result.append("Modified files (").append(status.getModified().size()).append("):\n");
            status.getModified().forEach(f -> result.append("  M ").append(f).append("\n"));
        }

        if (!status.getAdded().isEmpty()) {
            result.append("Added files (").append(status.getAdded().size()).append("):\n");
            status.getAdded().forEach(f -> result.append("  A ").append(f).append("\n"));
        }

        if (!status.getUntracked().isEmpty()) {
            result.append("Untracked files (").append(status.getUntracked().size()).append("):\n");
            status.getUntracked().forEach(f -> result.append("  ? ").append(f).append("\n"));
        }

        if (!status.getRemoved().isEmpty()) {
            result.append("Removed files (").append(status.getRemoved().size()).append("):\n");
            status.getRemoved().forEach(f -> result.append("  D ").append(f).append("\n"));
        }

        return result.toString();
    }

    /**
     * Validates that the repository is ready to push (no uncommitted changes).
     */
    private void validateReadyToPush(Git git, String branch) throws GitAPIException {
        Status status = git.status().call();

        if (!status.isClean()) {

            String errorMsg = "❌ PUSH FAILED: Working directory has uncommitted changes.\n\n" +
                    "📋 REQUIRED STEPS TO FIX:\n" +
                    "1. Commit your changes first:\n" +
                    "   git_commit(repositoryPath=\"<path>\", message=\"Your commit message\", files=null)" +
                    "\n\n" +
                    "2. Then push to remote:\n" +
                    "   git_push(repositoryPath=\"<path>\", remote=\"origin\", branch=\"" +
                    branch +
                    "\")\n\n" +
                    "📁 UNCOMMITTED CHANGES:\n" +
                    formatGitStatus(status) +
                    "\n💡 TIP: Use files=null in git_commit to commit all changes at once.\n";

            throw new GitAPIException(errorMsg) {
            };
        }
    }

    @Tool(name = "git_check_push_readiness", description = "Checks if the repository is ready to push to remote. " +
            "Validates that all changes are " + "committed and provides guidance on next steps. Use this before " +
            "attempting to push to avoid errors.")
    public String checkPushReadiness(@ToolParam(description = "Path to the repository") String repositoryPath,
                                     @ToolParam(description = "Branch name to check") String branch) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            Repository repository = git.getRepository();
            Status status = git.status().call();

            StringBuilder result = new StringBuilder();
            result.append("🔍 Push Readiness Check for branch '").append(branch).append("'\n\n");

            // Check 1: Uncommitted changes
            if (!status.isClean()) {
                result.append("❌ NOT READY TO PUSH\n\n");
                result.append("Reason: You have uncommitted changes.\n\n");
                result.append("📁 Uncommitted Changes:\n");
                result.append(formatGitStatus(status));
                result.append("\n📋 Required Actions:\n");
                result.append("1. Commit your changes:\n");
                result.append("   git_commit(repositoryPath=\"").append(repositoryPath).append("\", ");
                result.append("message=\"Your commit message\", files=null)\n\n");
                result.append("2. Then check readiness again or push directly:\n");
                result.append("   git_push(repositoryPath=\"").append(repositoryPath).append("\", ");
                result.append("remote=\"origin\", branch=\"").append(branch).append("\")\n");
                return result.toString();
            }

            // Check 2: Branch exists
            Ref localBranchRef = repository.findRef("refs/heads/" + branch);
            if (localBranchRef == null) {
                result.append("❌ NOT READY TO PUSH\n\n");
                result.append("Reason: Local branch '").append(branch).append("' does not exist.\n\n");
                result.append("📋 Required Actions:\n");
                result.append("1. Create the branch: git_create_branch(repositoryPath, branchName)\n");
                result.append("2. Or checkout an existing branch: git_checkout(repositoryPath, branchName)\n");
                return result.toString();
            }

            // Check 3: Compare with remote
            try {
                Ref remoteBranchRef = repository.findRef("refs/remotes/origin/" + branch);
                if (remoteBranchRef != null) {
                    if (localBranchRef.getObjectId().equals(remoteBranchRef.getObjectId())) {
                        result.append("ℹ️  ALREADY UP-TO-DATE\n\n");
                        result
                                .append("Your local branch '")
                                .append(branch)
                                .append("' is synchronized with 'origin/")
                                .append(branch)
                                .append("'.\n");
                        result.append("No new commits to push.\n\n");
                        result.append("If you expected to have changes:\n");
                        result.append("- Check status: git_status(repositoryPath)\n");
                        result.append("- View commits: git_log(repositoryPath, maxCount=5)\n");
                        return result.toString();
                    } else {
                        result.append("✅ READY TO PUSH\n\n");
                        result.append("Your local branch has new commits that can be pushed.\n\n");
                        result.append("📤 To push:\n");
                        result.append("   git_push(repositoryPath=\"").append(repositoryPath).append("\", ");
                        result.append("remote=\"origin\", branch=\"").append(branch).append("\")\n");
                        return result.toString();
                    }
                } else {
                    result.append("✅ READY TO PUSH (New Branch)\n\n");
                    result.append("This will create a new branch '").append(branch).append("' on the remote.\n\n");
                    result.append("📤 To push:\n");
                    result.append("   git_push(repositoryPath=\"").append(repositoryPath).append("\", ");
                    result.append("remote=\"origin\", branch=\"").append(branch).append("\")\n");
                    return result.toString();
                }
            } catch (Exception e) {
                result.append("⚠️  Could not check remote branch status: ").append(e.getMessage()).append("\n\n");
                result.append("✅ Local changes are committed. You can attempt to push.\n");
                return result.toString();
            }
        }
    }

    @Tool(name = "git_push", description = "Pushes local commits to a remote repository. Uploads the specified " +
            "branch" + " to the remote repository (e.g., 'origin'). Use this to share your local commits with others " +
            "or backup " + "your work to a remote server.")
    public String push(String repositoryPath, String remote, String branch) throws IOException, GitAPIException {
        return pushInternal(repositoryPath, remote, branch, true);
    }

    @Tool(name = "git_push_force", description = "Force pushes local commits to a remote repository, overwriting " +
            "remote history. Use this when you need to update a branch that has diverged or when UP_TO_DATE status " + "is incorrect. WARNING: This can overwrite remote changes. Use with caution.")
    public String pushForce(String repositoryPath, String remote, String branch) throws IOException, GitAPIException {
        return pushInternal(repositoryPath, remote, branch, true);
    }

    private String pushInternal(String repositoryPath, String remote, String branch,
                                boolean force) throws IOException, GitAPIException {
        logger.debug("The pushInternal method is called with repositoryPath: {}, remote: {}, branch: {}, force: {}",
                     repositoryPath, remote, branch, force);
        try (Git git = openRepository(repositoryPath)) {
            Repository repository = git.getRepository();

            // STEP 1: Validate repository has no uncommitted changes
            validateReadyToPush(git, branch);

            // Verify the remote exists and get its URL
            String remoteUrl = repository.getConfig().getString("remote", remote, "url");
            if (remoteUrl == null || remoteUrl.isEmpty()) {
                String errorMsg = "❌ ERROR: Remote '" + remote + "' is not configured in the repository.\n\n" + "Use " +
                        "get_status to see configured remotes or check repository configuration.";
                logger.error(errorMsg);
                throw new GitAPIException(errorMsg) {
                };
            }
            logger.debug("Remote '{}' URL: {}", remote, remoteUrl);

            // Check if the local branch exists
            Ref localBranchRef = repository.findRef("refs/heads/" + branch);
            if (localBranchRef == null) {
                String errorMsg = "❌ ERROR: Local branch '" + branch + "' does not exist.\n\n" + "Create the branch " +
                        "first using git_create_branch or checkout an existing branch.";
                logger.error(errorMsg);
                throw new GitAPIException(errorMsg) {
                };
            }
            logger.debug("Local branch '{}' exists at commit: {}", branch, localBranchRef.getObjectId().getName());

            // Check current branch
            String currentBranch = repository.getBranch();
            logger.debug("Current branch: {}", currentBranch);

            // Check if there are commits to push
            try {
                Ref remoteBranchRef = repository.findRef("refs/remotes/" + remote + "/" + branch);
                if (remoteBranchRef != null) {
                    logger.debug("Remote tracking branch exists at commit: {}", remoteBranchRef
                            .getObjectId()
                            .getName());
                    if (localBranchRef.getObjectId().equals(remoteBranchRef.getObjectId())) {
                        // Local and remote are at same commit - nothing to push
                        String infoMsg =
                                "ℹ️  Branch '" + branch + "' is already up-to-date with '" + remote + "/" + branch +
                                        "'.\n\n" + "✅ No new commits to push. The remote repository already has all " +
                                        "your local commits.\n\n" + "If you expected to push changes:\n" + "1. Verify" +
                                        " you committed your changes: git_status(repositoryPath)\n" + "2. View recent" +
                                        " commits: git_log(repositoryPath, maxCount=5)\n" + "3. If changes are " +
                                        "missing, commit them first before pushing";
                        logger.info(infoMsg);
                        return infoMsg;
                    }
                } else {
                    logger.debug("Remote tracking branch does not exist locally. This will be a new branch on remote.");
                }
            } catch (Exception e) {
                logger.warn("Could not check remote tracking branch: {}", e.getMessage());
            }

            // Check if the branch is the default branch for the remote
            try {
                // First, try to get the default branch from the remote HEAD
                Ref remoteHead = repository.findRef("refs/remotes/" + remote + "/HEAD");
                if (remoteHead != null && remoteHead.isSymbolic()) {
                    String defaultBranchRef = remoteHead.getTarget().getName();
                    String defaultBranch = defaultBranchRef.replace("refs/remotes/" + remote + "/", "");
                    logger.debug("Remote '{}' default branch: {}", remote, defaultBranch);

                    if (branch.equals(defaultBranch)) {
                        String errorMsg =
                                "❌ ERROR: Cannot push to default branch '" + branch + "' of remote '" + remote + "'" +
                                        ".\n\n" + "Pushing to the default branch (typically 'main' or 'master') is " +
                                        "not allowed to prevent " + "accidental changes to the primary branch.\n\n" + "Please push to a feature branch instead.";
                        logger.error(errorMsg);
                        throw new GitAPIException(errorMsg) {
                        };
                    }
                } else {
                    // If remote HEAD is not available, check against common default branch names
                    logger.warn("Could not determine remote default branch from HEAD. Checking against common default" +
                                        " branch names.");
                    if (branch.equals("main") || branch.equals("master")) {
                        logger.warn("Attempting to push to '{}' which is commonly a default branch. " + "This may be " +
                                            "blocked by remote repository rules.", branch);
                    }
                }
            } catch (GitAPIException e) {
                // Re-throw GitAPIException (our custom error)
                throw e;
            } catch (Exception e) {
                logger.warn("Could not verify if branch is default branch: {}", e.getMessage());
                // Continue with push attempt - let the remote decide if it's allowed
            }

            // Use URL-aware SSH transport configuration
            TransportConfigCallback transportConfigCallback = createSshTransportConfigForUrl(remoteUrl);

            // Execute push and capture results
            var pushCommand = git
                    .push()
                    .setRemote(remote)
                    .add(branch)
                    .setTransportConfigCallback(transportConfigCallback);

            if (force) {
                pushCommand.setForce(true);
                logger.debug("Executing FORCE push to {}/{}", remote, branch);
            }

            var pushResults = pushCommand.call();

            // Verify push was successful by checking the results
            StringBuilder resultMessage = new StringBuilder();
            boolean hasErrors = false;

            for (var pushResult : pushResults) {
                String remoteMessages = pushResult.getMessages();
                if (remoteMessages != null && !remoteMessages.isEmpty()) {
                    logger.debug("Push result for remote {}: {}", pushResult.getURI(), remoteMessages);

                    // Check if remote messages contain error indicators
                    // Note: "Bypassed rule violations" is informational, not an error
                    String lowerMessages = remoteMessages.toLowerCase();
                    boolean isBypassMessage = lowerMessages.contains("bypassed rule violations");

                    if (!isBypassMessage && (lowerMessages.contains("error") || lowerMessages.contains("rejected") || lowerMessages.contains("denied"))) {
                        hasErrors = true;
                        resultMessage.append("Remote rejected push: ").append(remoteMessages).append("\n");
                        logger.error("Remote rejected push with message: {}", remoteMessages);
                    } else if (isBypassMessage) {
                        logger.info("Push succeeded with bypassed rules: {}", remoteMessages);
                    }
                }

                // Check each ref update
                for (var remoteRefUpdate : pushResult.getRemoteUpdates()) {
                    logger.debug("Ref update: {} -> {} (status: {})", remoteRefUpdate.getSrcRef(),
                                 remoteRefUpdate.getRemoteName(), remoteRefUpdate.getStatus());

                    // Check if the update was successful
                    switch (remoteRefUpdate.getStatus()) {
                        case OK:
                            logger.debug("Push successful for ref: {}", remoteRefUpdate.getRemoteName());
                            break;
                        case UP_TO_DATE:
                            // UP_TO_DATE can be misleading - verify working directory is clean
                            logger.warn("Ref reported as up-to-date: {}", remoteRefUpdate.getRemoteName());
                            // Note: We already validated no uncommitted changes at the start of this method
                            // So if we get here, it truly means the branch is up-to-date
                            resultMessage
                                    .append("ℹ️  Branch '")
                                    .append(branch)
                                    .append("' is already up-to-date with remote.\n");
                            break;
                        case REJECTED_NONFASTFORWARD:
                        case REJECTED_NODELETE:
                        case REJECTED_REMOTE_CHANGED:
                        case REJECTED_OTHER_REASON:
                            hasErrors = true;
                            String rejectMsg = "❌ Push rejected: " + remoteRefUpdate.getStatus();
                            if (remoteRefUpdate.getMessage() != null) {
                                rejectMsg += "\nReason: " + remoteRefUpdate.getMessage();
                            }
                            rejectMsg += "\n\n💡 SUGGESTED ACTIONS:\n";
                            if (remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                                rejectMsg += "- Pull latest changes first: git_pull(repositoryPath)\n";
                                rejectMsg += "- Or use force push if you're sure: git_push_force(repositoryPath, " +
                                        "remote, branch)\n";
                            } else {
                                rejectMsg += "- Check remote repository permissions\n";
                                rejectMsg += "- Verify branch protection rules\n";
                            }
                            resultMessage.append(rejectMsg).append("\n");
                            logger.error(rejectMsg);
                            break;
                        default:
                            hasErrors = true;
                            String failMsg = "❌ Push failed with status: " + remoteRefUpdate.getStatus();
                            if (remoteRefUpdate.getMessage() != null) {
                                failMsg += "\nDetails: " + remoteRefUpdate.getMessage();
                            }
                            resultMessage.append(failMsg).append("\n");
                            logger.error(failMsg);
                            break;
                    }
                }

                // Add any additional messages from the push result (informational only)
                if (remoteMessages != null && !remoteMessages.isEmpty()) {
                    // These are informational messages from the remote (like GitHub rule bypasses)
                    // They don't indicate failure
                    logger.debug("Remote messages: {}", remoteMessages);
                }
            }

            if (hasErrors) {
                String errorOutput = "❌ PUSH FAILED to " + remote + "/" + branch + "\n\n" + resultMessage;
                logger.error("!!! Git Push has failed with errors: {}", errorOutput);
                throw new GitAPIException(errorOutput) {
                };
            }

            // Verify the push by checking if the remote ref exists
            try {
                String remoteRef = "refs/remotes/" + remote + "/" + branch;
                logger.debug("Verifying push by checking remote tracking branch: {}", remoteRef);

                // Fetch to update remote tracking branches
                git
                        .fetch()
                        .setRemote(remote)
                        .setRefSpecs("refs/heads/" + branch + ":refs/remotes/" + remote + "/" + branch)
                        .setTransportConfigCallback(transportConfigCallback)
                        .call();

                Ref remoteRefObj = git.getRepository().findRef(remoteRef);
                if (remoteRefObj != null) {
                    logger.debug("Push verified: Remote branch {} exists at commit {}", remoteRef, remoteRefObj
                            .getObjectId()
                            .getName());
                } else {
                    logger.warn("Push reported success but remote tracking branch {} not found. " + "This may be " +
                                        "normal for new branches.", remoteRef);
                }
            } catch (Exception e) {
                logger.warn("Could not verify push (this is not necessarily an error): {}", e.getMessage());
            }

            String output = "✅ Successfully pushed to " + remote + "/" + branch + "\n\n" + "Your commits are now " +
                    "available on the remote repository.";
            logger.debug("Response returned {}", output);
            return output;
        }
    }

    @Tool(name = "git_pull", description =
            "Fetches and merges changes from the remote repository into the current " + "branch. Use this to update " +
                    "your local repository with the latest changes from the remote server.")
    public String pull(String repositoryPath) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            Repository repository = git.getRepository();
            
            // Get the remote URL to determine which SSH key to use
            String remoteUrl = repository.getConfig().getString("remote", "origin", "url");
            TransportConfigCallback transportConfigCallback = createSshTransportConfigForUrl(remoteUrl);
            
            git.pull().setTransportConfigCallback(transportConfigCallback).call();

            return "Pull completed successfully";
        }
    }

    @Tool(name = "git_diff", description = "Shows the differences between two commits. If oldCommit and newCommit " +
            "are" + " not specified, it defaults to comparing HEAD^ with HEAD (the last commit). Returns a detailed " +
            "diff " + "showing what changed between the two commits. Use this to review changes between different " +
            "versions of " + "the code.")
    public String diff(String repositoryPath, String oldCommit, String newCommit) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            Repository repository = git.getRepository();

            ObjectId oldId = repository.resolve(oldCommit != null ? oldCommit : "HEAD^");
            ObjectId newId = repository.resolve(newCommit != null ? newCommit : "HEAD");

            try (ObjectReader reader = repository.newObjectReader()) {
                AbstractTreeIterator oldTreeIter = prepareTreeParser(repository, oldId);
                AbstractTreeIterator newTreeIter = prepareTreeParser(repository, newId);

                List<DiffEntry> diffs = git.diff().setOldTree(oldTreeIter).setNewTree(newTreeIter).call();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (DiffFormatter formatter = new DiffFormatter(out)) {
                    formatter.setRepository(repository);

                    for (DiffEntry diff : diffs) {
                        formatter.format(diff);
                    }
                }

                return out.toString();
            }
        }
    }

    private Git openRepository(String repositoryPath) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder
                .setGitDir(new File(repositoryPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();

        return new Git(repository);
    }

    private AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId objectId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(objectId);
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();
            return treeParser;
        }
    }

    /**
     * Extracts the repository name from a Git URL.
     * Examples:
     * https://github.com/user/repo.git -> repo
     * https://github.com/user/repo -> repo
     * git@github.com:user/repo.git -> repo
     */
    private String extractRepoName(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isEmpty()) {
            return "repository";
        }

        // Remove .git suffix if present
        String url = remoteUrl.endsWith(".git") ? remoteUrl.substring(0, remoteUrl.length() - 4) : remoteUrl;

        // Extract the last part of the path
        String[] parts = url.split("[/:]");
        return parts[parts.length - 1];
    }
}
