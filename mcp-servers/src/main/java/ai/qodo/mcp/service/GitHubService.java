/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.service;

import ai.qodo.mcp.config.GithubMcpConfiguration;
import ai.qodo.mcp.pojo.ToolOutputResult;
import ai.qodo.mcp.util.ParameterStringBuilder;
import jakarta.annotation.PostConstruct;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Service()
@ConditionalOnProperty(name = "mcp.github.enabled", havingValue = "true", matchIfMissing = true)
public class GitHubService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);
    private final GithubMcpConfiguration githubMcpConfiguration;

    public GitHubService(GithubMcpConfiguration githubMcpConfiguration) {
        this.githubMcpConfiguration = githubMcpConfiguration;
    }

    /**
     * Validates GitHub API token and connectivity at service initialization.
     */
    @PostConstruct
    public void validateConfiguration() {
        if (!githubMcpConfiguration.isConfigurationValid()) {
            logger.error("GitHub configuration is invalid - API token is missing or empty");
            return;
        }
        
        try {
            GitHub github = new GitHubBuilder()
                .withOAuthToken(githubMcpConfiguration.getGithubConfiguration().getApiToken())
                .build();
            
            // Test API access by getting current user
            GHMyself myself = github.getMyself();
            logger.info("GitHub API token validated successfully for user: {}", myself.getLogin());
            logger.debug("API rate limit: {}/{}", github.getRateLimit().getRemaining(), github.getRateLimit().getLimit());
        } catch (IOException e) {
            logger.error("GitHub API token validation failed: {}. Please verify your GITHUB_API_TOKEN is valid and has appropriate permissions.", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during GitHub configuration validation: {}", e.getMessage(), e);
        }
    }

    @Tool(name = "github_list_repositories", description = "Lists all GitHub repositories accessible to the authenticated user. "
            + "Returns repository information including the git clone URI (for checkout), title (name), and description. "
            + "This tool can list repositories owned by the authenticated user, or filter by a specific owner/organization. "
            + "Use this to discover available repositories, get clone URLs for checking out code, or explore repository details.")
    public ToolOutputResult listRepositories(
            @ToolParam(description = "Optional owner/organization name to filter repositories. If not provided, lists all repositories accessible to the authenticated user.", required = false) String owner,
            @ToolParam(description = "Maximum number of repositories to return. Defaults to 30 if not specified.", required = false) Integer limit) {
        
        int maxRepos = (limit != null && limit > 0) ? limit : 30;
        String params = ParameterStringBuilder.buildParameterString("owner", owner, "limit", String.valueOf(maxRepos));
        logger.info("Listing GitHub repositories with parameters: {}", params);
        
        if (!githubMcpConfiguration.isConfigurationValid()) {
            String errorMsg = "GitHub configuration is invalid - API token is missing or empty";
            logger.error(errorMsg);
            return new ToolOutputResult("", errorMsg, -1, true, "ConfigurationError");
        }
        
        try {
            GitHub github = new GitHubBuilder()
                    .withOAuthToken(githubMcpConfiguration.getGithubConfiguration().getApiToken())
                    .build();
            
            List<String> repoInfoList = new ArrayList<>();
            int count = 0;
            
            if (owner != null && !owner.isBlank()) {
                // List repositories for specific owner/organization
                logger.debug("Fetching repositories for owner: {}", owner);
                try {
                    GHUser user = github.getUser(owner);
                    PagedIterable<GHRepository> repositories = user.listRepositories();
                    
                    for (GHRepository repo : repositories) {
                        if (count >= maxRepos) break;
                        repoInfoList.add(formatRepositoryInfo(repo));
                        count++;
                    }
                } catch (GHFileNotFoundException e) {
                    String errorMsg = String.format("User or organization '%s' not found on GitHub", owner);
                    logger.error(errorMsg);
                    return new ToolOutputResult("", errorMsg, -1, true, "UserNotFound");
                }
            } else {
                // List all repositories accessible to authenticated user
                logger.debug("Fetching all repositories accessible to authenticated user");
                GHMyself myself = github.getMyself();
                PagedIterable<GHRepository> repositories = myself.listRepositories();
                
                for (GHRepository repo : repositories) {
                    if (count >= maxRepos) break;
                    repoInfoList.add(formatRepositoryInfo(repo));
                    count++;
                }
            }
            
            if (repoInfoList.isEmpty()) {
                String message = owner != null ? 
                    String.format("No repositories found for owner '%s'", owner) : 
                    "No repositories found for authenticated user";
                logger.info(message);
                return new ToolOutputResult(message, "", 0, false, "");
            }
            
            String output = String.join("\n\n", repoInfoList);
            String summary = String.format("Found %d repositories", count);
            logger.info("{} for parameters: {}", summary, params);
            
            return new ToolOutputResult(output, "", 0, false, "");
            
        } catch (IOException e) {
            String errorMsg = String.format("Failed to list repositories: %s", e.getMessage());
            logger.error(errorMsg, e);
            return new ToolOutputResult("", errorMsg, -1, true, "IOException");
        } catch (Exception e) {
            String errorMsg = String.format("Unexpected error while listing repositories: %s", e.getMessage());
            logger.error(errorMsg, e);
            return new ToolOutputResult("", errorMsg, -1, true, "UnexpectedException");
        }
    }
    
    /**
     * Formats repository information into a readable string.
     * 
     * @param repo The GitHub repository
     * @return Formatted string with repository details
     * @throws IOException if there's an error accessing repository information
     */
    private String formatRepositoryInfo(GHRepository repo) throws IOException {
        StringBuilder info = new StringBuilder();
        
        // Repository name (title)
        info.append("Repository: ").append(repo.getFullName()).append("\n");
        
        // Clone URL (git URI)
        info.append("Clone URL (HTTPS): ").append(repo.getHttpTransportUrl()).append("\n");
        info.append("Clone URL (SSH): ").append(repo.getSshUrl()).append("\n");
        
        // Description
        String description = repo.getDescription();
        info.append("Description: ").append(description != null && !description.isBlank() ? description : "No description available").append("\n");
        
        // Additional useful information
        info.append("Default Branch: ").append(repo.getDefaultBranch()).append("\n");
        info.append("Private: ").append(repo.isPrivate() ? "Yes" : "No").append("\n");
        info.append("URL: ").append(repo.getHtmlUrl());
        
        return info.toString();
    }

    @Tool(name = "github_create_pr", description = "Creates a new Pull Request on GitHub for the current repository. "
            + "This tool opens a PR from a source branch (typically a feature branch) to a target branch (typically main/master). "
            + "Use this when you need to propose code changes for review and merging. The tool automatically detects the GitHub "
            + "repository from the local git configuration and requires a valid GitHub API token to be configured. "
            + "Ensure both source and target branches exist on the remote repository before creating the PR.")
    public ToolOutputResult createPullRequest(
            @ToolParam(description = "The body of the Pull Request which is the indepth description of what has been "
                    + "developed") String body,
            @ToolParam(description = "The title of the Pull Request") String title,
            @ToolParam(description = "The source-branch for the Pull Request") String sourceBranch,
            @ToolParam(description = "The target-branch for the Pull Request if you are unsure do not guess instead the default branch will be used.", required = false) String targetBranch,
            ToolContext toolContext) {
        String params = ParameterStringBuilder.buildParameterString("body", body, "title", title, "sourceBranch",
                                                                    sourceBranch, "targetBranch", targetBranch);
        logger.info("Attempting Pull Request for parameters: {}", params);
        try {
            this.githubMcpConfiguration.ensureRootsInitialized(toolContext);
        } catch (InterruptedException e) {
            logger.error("Could not get of git repo to create the PR, failed to create PR for Title {} sourceBranch " + ":" + " {}, targetBranch {} description/body {} ", title, sourceBranch, targetBranch, body, e);
            return new ToolOutputResult("Error in creating PR for parameters " + params,
                                        "Failed to create PR: " + e.getMessage(), -1, true, "InterruptedException");
        }
        String mergeToBranch = "main";
        ToolOutputResult result;
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        
        File gitDir = findGitDirectory(this.githubMcpConfiguration.getDefaultLocalPath());
        if (gitDir == null) {
            String errorMsg = String.format("Could not find .git directory starting from path: %s", 
                this.githubMcpConfiguration.getDefaultLocalPath());
            logger.error(errorMsg);
            return new ToolOutputResult("", errorMsg, -1, true, "GitDirectoryNotFound");
        }
        logger.debug("Using .git directory: {}", gitDir.getAbsolutePath());
        
        try (Repository repository = builder
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()) {
            logger.debug("Successfully opened git repository: {}", repository.getDirectory());
            RemoteConfig remoteConfig = new RemoteConfig(repository.getConfig(), "origin");
            URIish uri = remoteConfig.getURIs().getFirst();

            String url = uri.toString();
            logger.debug("Git remote URL: {}", url);
            
            String ownerRepo = parseGitHubUrl(url);
            logger.info("Parsed GitHub repository: {}", ownerRepo);
            
            GitHub github = new GitHubBuilder()
                    .withOAuthToken(githubMcpConfiguration.getGithubConfiguration().getApiToken())
                    .build();
            
            // Attempt to get repository with enhanced error handling
            GHRepository ghRepository;
            try {
                ghRepository = github.getRepository(ownerRepo);
                logger.debug("Successfully accessed GitHub repository: {}", ghRepository.getFullName());
            } catch (GHFileNotFoundException e) {
                String errorMsg = String.format(
                    "GitHub repository '%s' not found (HTTP 404). Please verify:\n" +
                    "  1. Repository exists at https://github.com/%s\n" +
                    "  2. Repository name is correct (format: owner/repo-name)\n" +
                    "  3. GitHub API token has access to this repository\n" +
                    "  4. For private repositories, ensure token has 'repo' scope\n" +
                    "  5. Git remote URL is correctly configured: %s",
                    ownerRepo, ownerRepo, url
                );
                logger.error(errorMsg);
                return new ToolOutputResult("", errorMsg, -1, true, "RepositoryNotFound");
            } catch (IOException e) {
                String errorMsg = String.format(
                    "Failed to access GitHub repository '%s': %s\n" +
                    "This may be due to network issues, API rate limiting, or permission problems.",
                    ownerRepo, e.getMessage()
                );
                logger.error(errorMsg, e);
                return new ToolOutputResult("", errorMsg, -1, true, "RepositoryAccessError");
            }
            
            logger.debug("Trying to create PR for git repository {} gitHub Repo {} ", repository, ghRepository);

            if(targetBranch != null && !targetBranch.isBlank()) {
                // Use the provided target branch
                mergeToBranch = targetBranch;
                logger.debug("Using provided target branch: {}", mergeToBranch);
            } else {
                // Get the default branch from GitHub
                mergeToBranch = ghRepository.getDefaultBranch();
                if (mergeToBranch == null || mergeToBranch.isBlank()) {
                    // Fallback to 'main' if default branch is not available
                    mergeToBranch = "main";
                    logger.warn("Could not determine default branch from GitHub, using fallback: {}", mergeToBranch);
                } else {
                    logger.debug("Using repository default branch: {}", mergeToBranch);
                }
            }
            logger.info("Creating PR from '{}' to '{}'", sourceBranch, mergeToBranch);

            // Check if a PR already exists for this source branch to target branch
            GHPullRequest existingPr = findExistingPullRequest(ghRepository, sourceBranch, mergeToBranch);
            if (existingPr != null) {
                logger.info("Found existing PR #{} for branch '{}' to '{}': {}", 
                           existingPr.getNumber(), sourceBranch, mergeToBranch, existingPr.getHtmlUrl());
                result = new ToolOutputResult(
                    String.format("A Pull Request already exists for branch '%s' to '%s'.\n" +
                                  "PR #%d: %s\n" +
                                  "Title: %s\n" +
                                  "State: %s\n" +
                                  "URL: %s",
                                  sourceBranch, mergeToBranch,
                                  existingPr.getNumber(),
                                  existingPr.getTitle(),
                                  existingPr.getTitle(),
                                  existingPr.getState().name(),
                                  existingPr.getHtmlUrl()),
                    "", 0, false, "");
                return result;
            }

            GHPullRequest pr = ghRepository.createPullRequest(title, sourceBranch, mergeToBranch, body);
            long prId = pr.getId();
            result = new ToolOutputResult("Pull Request created with ID:" + prId + " for parameters: " + params + "\nURL: " + pr.getHtmlUrl(), "",
                                          0, false, "");

        } catch (GHFileNotFoundException ghfnfe) {
            // This should be caught earlier, but handle it here as well for safety
            logger.error("Repository not found when creating PR for Title {} sourceBranch: {}, targetBranch: {} - {}", 
                        title, sourceBranch, mergeToBranch, ghfnfe.getMessage());
            return new ToolOutputResult("", 
                "Repository not found. Please verify the repository exists and your API token has access to it: " + ghfnfe.getMessage(), 
                -1, true, "RepositoryNotFound");
        } catch (IOException ioe) {
            String errorMessage = ioe.getMessage();
            String errorType = "IOException";
            
            // Provide more specific error messages based on exception details
            if (errorMessage != null) {
                if (errorMessage.contains("404")) {
                    errorType = "NotFound";
                    errorMessage = "Resource not found (404). This could be the repository, branch, or other resource: " + errorMessage;
                } else if (errorMessage.contains("401") || errorMessage.contains("Unauthorized")) {
                    errorType = "Unauthorized";
                    errorMessage = "Authentication failed (401). Please verify your GitHub API token is valid and has not expired: " + errorMessage;
                } else if (errorMessage.contains("403") || errorMessage.contains("Forbidden")) {
                    errorType = "Forbidden";
                    errorMessage = "Access forbidden (403). Your API token may lack required permissions or rate limit exceeded: " + errorMessage;
                } else if (errorMessage.contains("422")) {
                    errorType = "ValidationFailed";
                    errorMessage = "Validation failed (422). Check that branches exist and PR parameters are valid: " + errorMessage;
                }
            }
            
            logger.error("Failed to create PR for Title: '{}', sourceBranch: '{}', targetBranch: '{}' - {}", 
                        title, sourceBranch, mergeToBranch, errorMessage, ioe);
            return new ToolOutputResult("", "Failed to create PR: " + errorMessage, -1, true, errorType);
        } catch (URISyntaxException urie) {
            logger.error("Failed to create PR for for Title {} sourceBranch : {}, targetBranch {} description/body " + "{}" + " ", title, sourceBranch, mergeToBranch, body, urie);
            return new ToolOutputResult("", "Failed to create PR: " + urie.getMessage(), -1, true,
                                        "URISyntaxException");
        } catch (Exception e) {
            // Catch-all for any unexpected exceptions to ensure proper error reporting
            String errorMsg = String.format("Unexpected error while creating PR for Title: '%s', sourceBranch: '%s', targetBranch: '%s' - %s: %s",
                title, sourceBranch, mergeToBranch, e.getClass().getSimpleName(), e.getMessage());
            logger.error(errorMsg, e);
            return new ToolOutputResult("", errorMsg, -1, true, "UnexpectedException");
        }

        return result;
    }


    /**
     * Finds an existing pull request for the given source and target branches.
     * Searches for open PRs first, then checks closed/merged PRs if no open PR is found.
     *
     * @param repository The GitHub repository to search in
     * @param sourceBranch The source branch (head) of the PR
     * @param targetBranch The target branch (base) of the PR
     * @return The existing GHPullRequest if found, null otherwise
     */
    private GHPullRequest findExistingPullRequest(GHRepository repository, String sourceBranch, String targetBranch) {
        try {
            // First, search for open PRs with matching head and base branches
            List<GHPullRequest> openPrs = repository.queryPullRequests()
                    .state(GHIssueState.OPEN)
                    .head(sourceBranch)
                    .base(targetBranch)
                    .list()
                    .toList();
            
            if (!openPrs.isEmpty()) {
                logger.debug("Found {} open PR(s) for branch '{}' to '{}'", openPrs.size(), sourceBranch, targetBranch);
                return openPrs.get(0); // Return the first matching open PR
            }
            
            // If no open PR found, also check for the branch without owner prefix
            // GitHub API sometimes requires the head to be in format "owner:branch"
            String repoOwner = repository.getOwnerName();
            String headWithOwner = repoOwner + ":" + sourceBranch;
            
            List<GHPullRequest> openPrsWithOwner = repository.queryPullRequests()
                    .state(GHIssueState.OPEN)
                    .head(headWithOwner)
                    .base(targetBranch)
                    .list()
                    .toList();
            
            if (!openPrsWithOwner.isEmpty()) {
                logger.debug("Found {} open PR(s) for branch '{}' (with owner prefix) to '{}'", 
                            openPrsWithOwner.size(), headWithOwner, targetBranch);
                return openPrsWithOwner.get(0);
            }
            
            logger.debug("No existing open PR found for branch '{}' to '{}'", sourceBranch, targetBranch);
            return null;
            
        } catch (IOException e) {
            logger.warn("Error while searching for existing PRs: {}. Proceeding with PR creation.", e.getMessage());
            return null;
        }
    }

    /**
     * Parses a GitHub URL to extract the owner/repo format.
     * Supports both SSH and HTTPS formats.
     * 
     * @param url The git remote URL
     * @return The repository in "owner/repo-name" format
     * @throws IllegalArgumentException if URL format is invalid
     */
    private String parseGitHubUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Git remote URL cannot be null or empty");
        }
        
        String originalUrl = url;
        
        // Handle SSH format: git@github.com:owner/repo.git
        if (url.startsWith("git@github.com:")) {
            url = url.replace("git@github.com:", "");
        }
        // Handle HTTPS format: https://github.com/owner/repo.git
        else if (url.contains("github.com/")) {
            url = url.substring(url.indexOf("github.com/") + 11);
        }
        // Handle git:// protocol
        else if (url.startsWith("git://github.com/")) {
            url = url.substring(17);
        }
        else {
            logger.warn("Unrecognized GitHub URL format: {}. Attempting to parse anyway.", originalUrl);
        }

        // Remove .git suffix if present
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }
        
        // Validate format (should be owner/repo)
        if (!url.contains("/") || url.split("/").length < 2) {
            throw new IllegalArgumentException(
                String.format("Invalid GitHub repository format. Expected 'owner/repo', got: '%s' from URL: '%s'", 
                             url, originalUrl)
            );
        }

        logger.debug("Parsed GitHub URL '{}' to repository identifier '{}'", originalUrl, url);
        return url; // Returns "owner/repo-name"
    }

    /**
     * Searches downward from the given directory to find the .git directory.
     * This method recursively traverses the directory tree from the starting path,
     * looking for a .git directory in the current directory and all subdirectories.
     *
     * @param startPath The starting directory path to begin the search from
     * @return File object pointing to the .git directory, or null if not found
     */
    protected File findGitDirectory(String startPath) {
        if (startPath == null || startPath.isEmpty()) {
            logger.warn("Start path is null or empty, cannot search for .git directory");
            return null;
        }

        File startDir = new File(startPath);
        
        // Ensure the starting path exists and is a directory
        if (!startDir.exists()) {
            logger.warn("Start path does not exist: {}", startPath);
            return null;
        }
        
        if (!startDir.isDirectory()) {
            // If it's a file, start from its parent directory
            startDir = startDir.getParentFile();
        }

        // Search recursively downward
        return searchGitDirectoryRecursive(startDir);
    }

    /**
     * Recursively searches for .git directory in the given directory and its subdirectories.
     *
     * @param directory The directory to search in
     * @return File object pointing to the .git directory, or null if not found
     */
    protected File searchGitDirectoryRecursive(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }

        // Check if .git exists in current directory
        File gitDir = new File(directory, ".git");
        if (gitDir.exists() && gitDir.isDirectory()) {
            logger.debug("Found .git directory at: {}", gitDir.getAbsolutePath());
            return gitDir;
        }

        // Search in subdirectories
        File[] subdirs = directory.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                // Skip hidden directories except .git (which we're looking for)
                if (subdir.getName().startsWith(".") && !subdir.getName().equals(".git")) {
                    continue;
                }
                
                File found = searchGitDirectoryRecursive(subdir);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Searches downward from the given directory to find the .git directory,
     * returning its fully qualified path as a String.
     *
     * @param startPath The starting directory path to begin the search from
     * @return Fully qualified path to the .git directory, or null if not found
     */
    protected String findGitDirectoryPath(String startPath) {
        File gitDir = findGitDirectory(startPath);
        return gitDir != null ? gitDir.getAbsolutePath() : null;
    }


}
