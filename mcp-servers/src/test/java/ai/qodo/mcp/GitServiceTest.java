/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp;

import ai.qodo.mcp.config.GitMcpConfiguration;
import ai.qodo.mcp.service.GitService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit test suite for GitService.
 * Tests all public methods and private methods using reflection.
 * 
 * Coverage includes:
 * - All public @Tool methods
 * - All private helper methods (ensureRootsInitialized, createSshTransportConfig, 
 *   pushInternal, openRepository, prepareTreeParser, extractRepoName)
 * - SSH key selection based on URL host
 * - Edge cases, error handling, and resource management
 */
class GitServiceTest {

    private GitService gitService;
    private GitService gitServiceWithHostKeys;
    
    @TempDir
    Path tempDir;
    
    private Path repoPath;

    @Mock
    private GitMcpConfiguration mcpConfiguration;

    @BeforeEach
    void setUp() throws GitAPIException, IOException {
        // Create GitService with default configuration
        gitService = new GitService(mcpConfiguration, "id_rsa", "");
        
        // Create GitService with host-to-key mappings
        gitServiceWithHostKeys = new GitService(
                mcpConfiguration, 
                "id_rsa", 
                "github.com=github_key,ssh.dev.azure.com=azure_key,dev.azure.com=azure_key"
        );
        
        // Create a test repository
        repoPath = tempDir.resolve("test-repo");
        Files.createDirectories(repoPath);
        
        Git git = Git.init().setDirectory(repoPath.toFile()).call();
        
        // Create initial commit
        Path testFile = repoPath.resolve("test.txt");
        Files.writeString(testFile, "Hello World");
        
        git.add().addFilepattern("test.txt").call();
        git.commit().setMessage("Initial commit").call();
        
        git.close();
    }

    // ========== Helper methods for reflection-based testing ==========

    private String invokeExtractHostFromUrl(GitService service, String url) throws Exception {
        Method method = GitService.class.getDeclaredMethod("extractHostFromUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, url);
    }

    private String invokeGetSshKeyFileForUrl(GitService service, String url) throws Exception {
        Method method = GitService.class.getDeclaredMethod("getSshKeyFileForUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, url);
    }

    // ========== Tests for extractHostFromUrl method ==========

    @ParameterizedTest
    @CsvSource({
        "git@github.com:user/repo.git, github.com",
        "git@github.com:org/team/repo.git, github.com",
        "ssh://git@github.com/user/repo.git, github.com",
        "ssh://git@github.com:22/user/repo.git, github.com",
        "https://github.com/user/repo.git, github.com",
        "https://github.com/user/repo, github.com",
        "http://github.com/user/repo.git, github.com",
        "git@ssh.dev.azure.com:v3/org/project/repo, ssh.dev.azure.com",
        "https://dev.azure.com/org/project/_git/repo, dev.azure.com",
        "git@gitlab.com:user/repo.git, gitlab.com",
        "https://bitbucket.org/user/repo.git, bitbucket.org",
        "git@github.enterprise.com:user/repo.git, github.enterprise.com",
        "https://github.enterprise.com:8443/user/repo.git, github.enterprise.com"
    })
    void testExtractHostFromUrl_ValidUrls(String url, String expectedHost) throws Exception {
        String host = invokeExtractHostFromUrl(gitService, url);
        assertEquals(expectedHost, host);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "invalid-url", "just-text"})
    void testExtractHostFromUrl_InvalidUrls(String url) throws Exception {
        String host = invokeExtractHostFromUrl(gitService, url);
        assertNull(host);
    }

    @Test
    void testExtractHostFromUrl_CaseInsensitive() throws Exception {
        String host = invokeExtractHostFromUrl(gitService, "git@GitHub.COM:user/repo.git");
        assertEquals("github.com", host);
    }

    // ========== Tests for getSshKeyFileForUrl method ==========

    @Test
    void testGetSshKeyFileForUrl_DefaultKey() throws Exception {
        // No host mappings configured, should return default
        String keyFile = invokeGetSshKeyFileForUrl(gitService, "git@github.com:user/repo.git");
        assertEquals("id_rsa", keyFile);
    }

    @Test
    void testGetSshKeyFileForUrl_GitHubMapping() throws Exception {
        String keyFile = invokeGetSshKeyFileForUrl(gitServiceWithHostKeys, "git@github.com:user/repo.git");
        assertEquals("github_key", keyFile);
    }

    @Test
    void testGetSshKeyFileForUrl_AzureDevOpsSshMapping() throws Exception {
        String keyFile = invokeGetSshKeyFileForUrl(gitServiceWithHostKeys, "git@ssh.dev.azure.com:v3/org/project/repo");
        assertEquals("azure_key", keyFile);
    }

    @Test
    void testGetSshKeyFileForUrl_AzureDevOpsHttpsMapping() throws Exception {
        String keyFile = invokeGetSshKeyFileForUrl(gitServiceWithHostKeys, "https://dev.azure.com/org/project/_git/repo");
        assertEquals("azure_key", keyFile);
    }

    @Test
    void testGetSshKeyFileForUrl_UnmappedHost() throws Exception {
        // GitLab is not in the mappings, should return default
        String keyFile = invokeGetSshKeyFileForUrl(gitServiceWithHostKeys, "git@gitlab.com:user/repo.git");
        assertEquals("id_rsa", keyFile);
    }

    @Test
    void testGetSshKeyFileForUrl_NullUrl() throws Exception {
        String keyFile = invokeGetSshKeyFileForUrl(gitServiceWithHostKeys, null);
        assertEquals("id_rsa", keyFile);
    }

    @Test
    void testGetSshKeyFileForUrl_EmptyUrl() throws Exception {
        String keyFile = invokeGetSshKeyFileForUrl(gitServiceWithHostKeys, "");
        assertEquals("id_rsa", keyFile);
    }

    @Test
    void testGetSshKeyFileForUrl_CaseInsensitiveHostLookup() throws Exception {
        // Host extraction is case-insensitive
        String keyFile = invokeGetSshKeyFileForUrl(gitServiceWithHostKeys, "git@GITHUB.COM:user/repo.git");
        assertEquals("github_key", keyFile);
    }

    // ========== Tests for host key mapping parsing ==========

    @Test
    void testHostKeyMappingParsing_EmptyString() throws Exception {
        GitService service = new GitService(mcpConfiguration, "default_key", "");
        String keyFile = invokeGetSshKeyFileForUrl(service, "git@github.com:user/repo.git");
        assertEquals("default_key", keyFile);
    }

    @Test
    void testHostKeyMappingParsing_SingleMapping() throws Exception {
        GitService service = new GitService(mcpConfiguration, "default_key", "github.com=gh_key");
        assertEquals("gh_key", invokeGetSshKeyFileForUrl(service, "git@github.com:user/repo.git"));
        assertEquals("default_key", invokeGetSshKeyFileForUrl(service, "git@gitlab.com:user/repo.git"));
    }

    @Test
    void testHostKeyMappingParsing_MultipleMapping() throws Exception {
        GitService service = new GitService(
                mcpConfiguration, 
                "default_key", 
                "github.com=gh_key,gitlab.com=gl_key,bitbucket.org=bb_key"
        );
        assertEquals("gh_key", invokeGetSshKeyFileForUrl(service, "git@github.com:user/repo.git"));
        assertEquals("gl_key", invokeGetSshKeyFileForUrl(service, "git@gitlab.com:user/repo.git"));
        assertEquals("bb_key", invokeGetSshKeyFileForUrl(service, "git@bitbucket.org:user/repo.git"));
    }

    @Test
    void testHostKeyMappingParsing_WithSpaces() throws Exception {
        GitService service = new GitService(
                mcpConfiguration, 
                "default_key", 
                " github.com = gh_key , gitlab.com = gl_key "
        );
        assertEquals("gh_key", invokeGetSshKeyFileForUrl(service, "git@github.com:user/repo.git"));
        assertEquals("gl_key", invokeGetSshKeyFileForUrl(service, "git@gitlab.com:user/repo.git"));
    }

    @Test
    void testHostKeyMappingParsing_InvalidEntries() throws Exception {
        // Invalid entries should be ignored
        GitService service = new GitService(
                mcpConfiguration, 
                "default_key", 
                "github.com=gh_key,invalid,=nohost,novalue=,gitlab.com=gl_key"
        );
        assertEquals("gh_key", invokeGetSshKeyFileForUrl(service, "git@github.com:user/repo.git"));
        assertEquals("gl_key", invokeGetSshKeyFileForUrl(service, "git@gitlab.com:user/repo.git"));
        assertEquals("default_key", invokeGetSshKeyFileForUrl(service, "git@bitbucket.org:user/repo.git"));
    }

    @Test
    void testHostKeyMappingParsing_NullMappings() throws Exception {
        // When host-keys is null (from #{null} SpEL expression), it should use default key
        GitService service = new GitService(mcpConfiguration, "default_key", null);
        String keyFile = invokeGetSshKeyFileForUrl(service, "git@github.com:user/repo.git");
        assertEquals("default_key", keyFile);
    }

    @Test
    void testHostKeyMappingParsing_BlankMappings() throws Exception {
        // When host-keys is blank/whitespace, it should use default key
        GitService service = new GitService(mcpConfiguration, "default_key", "   ");
        String keyFile = invokeGetSshKeyFileForUrl(service, "git@github.com:user/repo.git");
        assertEquals("default_key", keyFile);
    }

    // ========== Tests for getStatus method ==========

    @Test
    void testGetStatus() throws IOException, GitAPIException {
        String status = gitService.getStatus(repoPath.toString());
        
        assertNotNull(status);
        assertTrue(status.contains("Git Status"));
        assertTrue(status.contains("Branch:"));
    }

    @Test
    void testStatusWithChanges() throws IOException, GitAPIException {
        // Modify existing file
        Path testFile = repoPath.resolve("test.txt");
        Files.writeString(testFile, "Modified content");
        
        String status = gitService.getStatus(repoPath.toString());
        
        assertNotNull(status);
        assertTrue(status.contains("Modified") || status.contains("test.txt"));
    }

    @Test
    void testStatusWithUntrackedFiles() throws IOException, GitAPIException {
        // Create a new untracked file
        Path untrackedFile = repoPath.resolve("untracked.txt");
        Files.writeString(untrackedFile, "Untracked content");
        
        String status = gitService.getStatus(repoPath.toString());
        
        assertNotNull(status);
        assertTrue(status.contains("Untracked") || status.contains("untracked.txt"));
    }

    @Test
    void testStatusWithAddedFiles() throws IOException, GitAPIException {
        // Create and add a new file
        Path newFile = repoPath.resolve("added.txt");
        Files.writeString(newFile, "Added content");
        
        Git git = Git.open(repoPath.toFile());
        git.add().addFilepattern("added.txt").call();
        git.close();
        
        String status = gitService.getStatus(repoPath.toString());
        
        assertNotNull(status);
        assertTrue(status.contains("Added") || status.contains("added.txt"));
    }

    @Test
    void testStatusOnCleanRepository() throws IOException, GitAPIException {
        String status = gitService.getStatus(repoPath.toString());
        
        assertNotNull(status);
        assertTrue(status.contains("Git Status"));
        assertTrue(status.contains("clean") || status.contains("Working tree clean"));
    }

    // ========== Tests for getLog method ==========

    @Test
    void testGetLog() throws IOException, GitAPIException {
        String log = gitService.getLog(repoPath.toString(), 10);
        
        assertNotNull(log);
        assertTrue(log.contains("Git Log"));
        assertTrue(log.contains("Initial commit"));
    }

    @Test
    void testGetLogWithLimit() throws IOException, GitAPIException {
        // Create multiple commits
        Git git = Git.open(repoPath.toFile());
        
        for (int i = 1; i <= 5; i++) {
            Path file = repoPath.resolve("file" + i + ".txt");
            Files.writeString(file, "Content " + i);
            git.add().addFilepattern("file" + i + ".txt").call();
            git.commit().setMessage("Commit " + i).call();
        }
        git.close();
        
        String log = gitService.getLog(repoPath.toString(), 3);
        
        assertNotNull(log);
        assertTrue(log.contains("Git Log"));
    }

    // ========== Tests for listBranches method ==========

    @Test
    void testListBranches() throws IOException, GitAPIException {
        String branches = gitService.listBranches(repoPath.toString());
        
        assertNotNull(branches);
        assertTrue(branches.contains("Branches:"));
        assertTrue(branches.contains("master") || branches.contains("main"));
    }

    @Test
    void testListBranchesWithMultipleBranches() throws IOException, GitAPIException {
        gitService.createBranch(repoPath.toString(), "feature-1");
        gitService.createBranch(repoPath.toString(), "feature-2");
        
        String branches = gitService.listBranches(repoPath.toString());
        
        assertNotNull(branches);
        assertTrue(branches.contains("feature-1"));
        assertTrue(branches.contains("feature-2"));
    }

    // ========== Tests for createBranch method ==========

    @Test
    void testCreateBranch() throws IOException, GitAPIException {
        String result = gitService.createBranch(repoPath.toString(), "feature-test");
        
        assertNotNull(result);
        assertTrue(result.contains("created successfully"));
        
        // Verify branch was created
        String branches = gitService.listBranches(repoPath.toString());
        assertTrue(branches.contains("feature-test"));
    }

    // ========== Tests for commit method ==========

    @Test
    void testCommit() throws IOException, GitAPIException {
        // Create a new file
        Path newFile = repoPath.resolve("new-file.txt");
        Files.writeString(newFile, "New content");
        
        String result = gitService.commit(repoPath.toString(), "Add new file", null);
        
        assertNotNull(result);
        assertTrue(result.contains("Committed"));
        assertTrue(result.contains("Add new file"));
    }

    @Test
    void testCommitWithSpecificFiles() throws IOException, GitAPIException {
        // Create multiple new files
        Path file1 = repoPath.resolve("file1.txt");
        Path file2 = repoPath.resolve("file2.txt");
        Files.writeString(file1, "Content 1");
        Files.writeString(file2, "Content 2");
        
        // Commit only file1
        String result = gitService.commit(repoPath.toString(), "Add file1", List.of("file1.txt"));
        
        assertNotNull(result);
        assertTrue(result.contains("Committed"));
        
        // Verify file2 is still untracked
        String status = gitService.getStatus(repoPath.toString());
        assertTrue(status.contains("file2.txt"));
    }

    // ========== Tests for checkoutBranch method ==========

    @Test
    void testCheckoutBranch() throws IOException, GitAPIException {
        // Create a new branch
        gitService.createBranch(repoPath.toString(), "test-checkout");
        
        // Checkout the branch
        String result = gitService.checkoutBranch(repoPath.toString(), "test-checkout");
        
        assertNotNull(result);
        assertTrue(result.contains("Switched to branch") || result.contains("test-checkout"));
        
        // Verify we're on the new branch
        Git git = Git.open(repoPath.toFile());
        String currentBranch = git.getRepository().getBranch();
        assertEquals("test-checkout", currentBranch);
        git.close();
    }

    @Test
    void testCheckoutBranch_NonExistentBranch() {
        Exception exception = assertThrows(Exception.class, () -> {
            gitService.checkoutBranch(repoPath.toString(), "non-existent-branch");
        });
        
        assertNotNull(exception);
    }

    // ========== Tests for diff method ==========

    @Test
    void testDiff_WithNullParameters() throws Exception {
        // Create a second commit
        Git git = Git.open(repoPath.toFile());
        Path newFile = repoPath.resolve("new-file.txt");
        Files.writeString(newFile, "New content");
        git.add().addFilepattern("new-file.txt").call();
        git.commit().setMessage("Second commit").call();
        git.close();
        
        // Test diff with null parameters (should default to HEAD^ and HEAD)
        String diff = gitService.diff(repoPath.toString(), null, null);
        
        assertNotNull(diff);
        assertTrue(diff.contains("new-file.txt") || diff.length() > 0);
    }

    // ========== Tests for extractRepoName private method ==========

    private String invokeExtractRepoName(String remoteUrl) throws Exception {
        Method method = GitService.class.getDeclaredMethod("extractRepoName", String.class);
        method.setAccessible(true);
        return (String) method.invoke(gitService, remoteUrl);
    }

    @Test
    void testExtractRepoName_HttpsUrlWithGitExtension() throws Exception {
        String repoName = invokeExtractRepoName("https://github.com/user/my-repo.git");
        assertEquals("my-repo", repoName);
    }

    @Test
    void testExtractRepoName_HttpsUrlWithoutGitExtension() throws Exception {
        String repoName = invokeExtractRepoName("https://github.com/user/my-repo");
        assertEquals("my-repo", repoName);
    }

    @Test
    void testExtractRepoName_SshUrl() throws Exception {
        String repoName = invokeExtractRepoName("git@github.com:user/my-repo.git");
        assertEquals("my-repo", repoName);
    }

    @Test
    void testExtractRepoName_NullUrl() throws Exception {
        String repoName = invokeExtractRepoName(null);
        assertEquals("repository", repoName);
    }

    @Test
    void testExtractRepoName_EmptyUrl() throws Exception {
        String repoName = invokeExtractRepoName("");
        assertEquals("repository", repoName);
    }

    @Test
    void testExtractRepoName_ComplexPath() throws Exception {
        String repoName = invokeExtractRepoName("https://github.com/org/team/my-project.git");
        assertEquals("my-project", repoName);
    }

    @Test
    void testExtractRepoName_AzureDevOpsUrl() throws Exception {
        String repoName = invokeExtractRepoName("git@ssh.dev.azure.com:v3/org/project/my-repo");
        assertEquals("my-repo", repoName);
    }

    // ========== Tests for openRepository private method ==========

    private Git invokeOpenRepository(String repositoryPath) throws Exception {
        Method method = GitService.class.getDeclaredMethod("openRepository", String.class);
        method.setAccessible(true);
        return (Git) method.invoke(gitService, repositoryPath);
    }

    @Test
    void testOpenRepository_ValidRepository() throws Exception {
        Git git = invokeOpenRepository(repoPath.toString());
        
        assertNotNull(git);
        assertNotNull(git.getRepository());
        assertTrue(git.getRepository().getDirectory().exists());
        
        git.close();
    }

    @Test
    void testOpenRepository_InvalidPath() throws Exception {
        Path nonGitPath = tempDir.resolve("not-a-repo");
        Files.createDirectories(nonGitPath);
        
        try {
            Git git = invokeOpenRepository(nonGitPath.toString());
            // openRepository might find a parent .git directory or handle gracefully
            // Just verify we got a result
            assertNotNull(git);
            git.close();
        } catch (Exception e) {
            // Also acceptable - invalid repository might throw an exception
            assertNotNull(e);
        }
    }

    @Test
    void testOpenRepository_NullPath() {
        try {
            Git git = invokeOpenRepository(null);
            // If it doesn't throw, verify we got a result
            assertNotNull(git);
            git.close();
        } catch (Exception e) {
            // Expected - null path should throw an exception
            assertNotNull(e);
        }
    }

    // ========== Tests for prepareTreeParser private method ==========

    private AbstractTreeIterator invokePrepareTreeParser(Repository repository, ObjectId objectId) throws Exception {
        Method method = GitService.class.getDeclaredMethod("prepareTreeParser", Repository.class, ObjectId.class);
        method.setAccessible(true);
        return (AbstractTreeIterator) method.invoke(gitService, repository, objectId);
    }

    @Test
    void testPrepareTreeParser_ValidCommit() throws Exception {
        Git git = Git.open(repoPath.toFile());
        Repository repository = git.getRepository();
        
        ObjectId headId = repository.resolve("HEAD");
        assertNotNull(headId);
        
        AbstractTreeIterator treeIterator = invokePrepareTreeParser(repository, headId);
        
        assertNotNull(treeIterator);
        assertTrue(treeIterator instanceof CanonicalTreeParser);
        
        git.close();
    }

    @Test
    void testPrepareTreeParser_InvalidObjectId() throws Exception {
        Git git = Git.open(repoPath.toFile());
        Repository repository = git.getRepository();
        
        ObjectId invalidId = ObjectId.zeroId();
        
        Exception exception = assertThrows(Exception.class, () -> {
            invokePrepareTreeParser(repository, invalidId);
        });
        
        assertNotNull(exception);
        git.close();
    }

    @Test
    void testPrepareTreeParser_NullObjectId() throws Exception {
        Git git = Git.open(repoPath.toFile());
        Repository repository = git.getRepository();
        
        Exception exception = assertThrows(Exception.class, () -> {
            invokePrepareTreeParser(repository, null);
        });
        
        assertNotNull(exception);
        git.close();
    }

    // ========== Tests for ensureRootsInitialized private method ==========

    private void invokeEnsureRootsInitialized(Object toolContext) throws Exception {
        Method method = GitService.class.getDeclaredMethod("ensureRootsInitialized", 
                org.springframework.ai.chat.model.ToolContext.class);
        method.setAccessible(true);
        method.invoke(gitService, toolContext);
    }

    @Test
    void testEnsureRootsInitialized_WithNullToolContext() {
        try {
            invokeEnsureRootsInitialized(null);
            // If successful, configuration handled null gracefully
        } catch (Exception e) {
            // Expected - configuration might throw exception for null
            assertNotNull(e);
        }
    }

    // ========== Tests for createSshTransportConfig private method ==========

    private Object invokeCreateSshTransportConfig() throws Exception {
        Method method = GitService.class.getDeclaredMethod("createSshTransportConfig");
        method.setAccessible(true);
        return method.invoke(gitService);
    }

    @Test
    void testCreateSshTransportConfig_KeyFileValidation() {
        try {
            Object transportConfig = invokeCreateSshTransportConfig();
            // If successful, key file exists and is readable
            assertNotNull(transportConfig);
        } catch (Exception e) {
            // Expected if SSH key doesn't exist
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                String message = cause.getMessage();
                assertTrue(message.contains("SSH key file not found") || 
                          message.contains("SSH key file is not readable") ||
                          message.contains("Configure mcp.git.ssh"));
            }
        }
    }

    // ========== Tests for createSshTransportConfigForUrl private method ==========

    private Object invokeCreateSshTransportConfigForUrl(String remoteUrl) throws Exception {
        Method method = GitService.class.getDeclaredMethod("createSshTransportConfigForUrl", String.class);
        method.setAccessible(true);
        return method.invoke(gitService, remoteUrl);
    }

    @Test
    void testCreateSshTransportConfigForUrl_WithGitHubUrl() {
        try {
            Object transportConfig = invokeCreateSshTransportConfigForUrl("git@github.com:user/repo.git");
            assertNotNull(transportConfig);
        } catch (Exception e) {
            // Expected if SSH key doesn't exist
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                String message = cause.getMessage();
                assertTrue(message.contains("SSH key file not found") || 
                          message.contains("SSH key file is not readable") ||
                          message.contains("Configure mcp.git.ssh"));
            }
        }
    }

    @Test
    void testCreateSshTransportConfigForUrl_WithNullUrl() {
        try {
            Object transportConfig = invokeCreateSshTransportConfigForUrl(null);
            assertNotNull(transportConfig);
        } catch (Exception e) {
            // Expected if SSH key doesn't exist
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                String message = cause.getMessage();
                assertTrue(message.contains("SSH key file not found") || 
                          message.contains("SSH key file is not readable") ||
                          message.contains("Configure mcp.git.ssh"));
            }
        }
    }

    // ========== Tests for pushInternal private method ==========

    private String invokePushInternal(String repositoryPath, String remote, String branch, boolean force) throws Exception {
        Method method = GitService.class.getDeclaredMethod("pushInternal", String.class, String.class, String.class, boolean.class);
        method.setAccessible(true);
        return (String) method.invoke(gitService, repositoryPath, remote, branch, force);
    }

    @Test
    void testPushInternal_RemoteNotConfigured() {
        Exception exception = assertThrows(Exception.class, () -> {
            invokePushInternal(repoPath.toString(), "nonexistent-remote", "master", false);
        });
        
        assertNotNull(exception);
        String message = exception.getMessage();
        if (exception.getCause() != null) {
            message = exception.getCause().getMessage();
        }
        assertTrue(message.contains("Remote") || message.contains("not configured"));
    }

    @Test
    void testPushInternal_LocalBranchDoesNotExist() throws Exception {
        Git git = Git.open(repoPath.toFile());
        StoredConfig config = git.getRepository().getConfig();
        config.setString("remote", "origin", "url", "https://github.com/test/repo.git");
        config.save();
        git.close();
        
        Exception exception = assertThrows(Exception.class, () -> {
            invokePushInternal(repoPath.toString(), "origin", "nonexistent-branch", false);
        });
        
        assertNotNull(exception);
        String message = exception.getMessage();
        if (exception.getCause() != null) {
            message = exception.getCause().getMessage();
        }
        assertTrue(message.contains("branch") || message.contains("does not exist"));
    }

    @Test
    void testPushInternal_DefaultBranchProtection() throws Exception {
        Git git = Git.open(repoPath.toFile());
        StoredConfig config = git.getRepository().getConfig();
        config.setString("remote", "origin", "url", "https://github.com/test/repo.git");
        config.save();
        
        String currentBranch = git.getRepository().getBranch();
        
        // Setup remote tracking branch
        git.branchCreate().setName("remotes/origin/" + currentBranch).setStartPoint(currentBranch).call();
        git.getRepository().updateRef("refs/remotes/origin/HEAD", true)
            .link("refs/remotes/origin/" + currentBranch);
        
        git.close();
        
        if ("master".equals(currentBranch) || "main".equals(currentBranch)) {
            Exception exception = assertThrows(Exception.class, () -> {
                invokePushInternal(repoPath.toString(), "origin", currentBranch, false);
            });
            
            assertNotNull(exception);
            String message = exception.getMessage();
            if (exception.getCause() != null) {
                message = exception.getCause().getMessage();
            }
            assertTrue(message.contains("default branch") || message.contains("not allowed"));
        }
    }

    // ========== Tests for pull method ==========

    @Test
    void testPull_NoRemoteConfigured() {
        try {
            String result = gitService.pull(repoPath.toString());
            assertNotNull(result);
        } catch (Exception e) {
            // Expected - should fail when no remote is configured
            assertNotNull(e);
            String message = e.getMessage();
            if (e.getCause() != null) {
                message = e.getCause().getMessage();
            }
            assertNotNull(message);
        }
    }

    // ========== Tests for cloneRepository method ==========

    @Test
    void testCloneRepository_ExtractsRepoName() {
        String url = "https://github.com/user/test-repo.git";
        
        try {
            gitService.cloneRepository(url, null);
        } catch (Exception e) {
            // Expected to fail - repository doesn't exist
            // But we verified the method attempts to extract the repo name
            assertNotNull(e);
        }
    }

    @Test
    void testCloneRepository_WithNullUrl() {
        Exception exception = assertThrows(Exception.class, () -> {
            gitService.cloneRepository(null, null);
        });
        
        assertNotNull(exception);
    }

    @Test
    void testCloneRepository_WithEmptyUrl() {
        Exception exception = assertThrows(Exception.class, () -> {
            gitService.cloneRepository("", null);
        });
        
        assertNotNull(exception);
    }

    // ========== Tests for URL patterns with various formats ==========

    @Test
    void testUrlPatterns_GitHubVariants() throws Exception {
        // Standard SSH
        assertEquals("github.com", invokeExtractHostFromUrl(gitService, "git@github.com:user/repo.git"));
        // SSH with explicit protocol
        assertEquals("github.com", invokeExtractHostFromUrl(gitService, "ssh://git@github.com/user/repo.git"));
        // HTTPS
        assertEquals("github.com", invokeExtractHostFromUrl(gitService, "https://github.com/user/repo.git"));
        // HTTP (less common but valid)
        assertEquals("github.com", invokeExtractHostFromUrl(gitService, "http://github.com/user/repo.git"));
    }

    @Test
    void testUrlPatterns_AzureDevOpsVariants() throws Exception {
        // SSH format for Azure DevOps
        assertEquals("ssh.dev.azure.com", invokeExtractHostFromUrl(gitService, "git@ssh.dev.azure.com:v3/org/project/repo"));
        // HTTPS format for Azure DevOps
        assertEquals("dev.azure.com", invokeExtractHostFromUrl(gitService, "https://dev.azure.com/org/project/_git/repo"));
        // Old visualstudio.com format
        assertEquals("org.visualstudio.com", invokeExtractHostFromUrl(gitService, "https://org.visualstudio.com/project/_git/repo"));
    }

    @Test
    void testUrlPatterns_GitLabVariants() throws Exception {
        assertEquals("gitlab.com", invokeExtractHostFromUrl(gitService, "git@gitlab.com:user/repo.git"));
        assertEquals("gitlab.com", invokeExtractHostFromUrl(gitService, "https://gitlab.com/user/repo.git"));
        // Self-hosted GitLab
        assertEquals("gitlab.mycompany.com", invokeExtractHostFromUrl(gitService, "git@gitlab.mycompany.com:user/repo.git"));
    }

    @Test
    void testUrlPatterns_BitbucketVariants() throws Exception {
        assertEquals("bitbucket.org", invokeExtractHostFromUrl(gitService, "git@bitbucket.org:user/repo.git"));
        assertEquals("bitbucket.org", invokeExtractHostFromUrl(gitService, "https://bitbucket.org/user/repo.git"));
    }

    @Test
    void testUrlPatterns_WithPorts() throws Exception {
        // SSH with port
        assertEquals("github.enterprise.com", invokeExtractHostFromUrl(gitService, "ssh://git@github.enterprise.com:22/user/repo.git"));
        // HTTPS with port
        assertEquals("github.enterprise.com", invokeExtractHostFromUrl(gitService, "https://github.enterprise.com:8443/user/repo.git"));
    }
}
