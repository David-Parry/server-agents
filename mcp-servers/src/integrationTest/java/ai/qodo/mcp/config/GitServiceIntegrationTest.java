/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.config;

import ai.qodo.mcp.service.GitService;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitServiceIntegrationTest {

    private GitService gitService;
    private GitMcpConfiguration gitMcpConfiguration;

    @TempDir
    Path tempDir;

    private String clonedRepoPath;

    @BeforeEach
    void setUp() {
        // Create a mock for GitMcpConfiguration
        gitMcpConfiguration = mock(GitMcpConfiguration.class);
        
        // Configure the mock to return the temp directory when getDefaultLocalPath() is called
        when(gitMcpConfiguration.getDefaultLocalPath()).thenReturn(tempDir.toString());
        
        // Inject the mock into GitService
        gitService = new GitService(gitMcpConfiguration);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up cloned repository if it exists
        if (clonedRepoPath != null) {
            File clonedRepo = new File(clonedRepoPath);
            if (clonedRepo.exists()) {
                deleteDirectory(clonedRepo);
            }
        }
    }

    @Test
    void testCloneRepositoryFromGitHub() throws GitAPIException, InterruptedException {
        // Clone a public repository to avoid SSH authentication issues
        // Using Spring PetClinic as a well-known public repository
        String remoteUrl = "https://github.com/spring-projects/spring-petclinic.git";
        
        String result = gitService.cloneRepository(remoteUrl, null);
        
        // Verify the clone was successful
        assertNotNull(result);
        assertTrue(result.contains("Repository cloned successfully"));
        assertTrue(result.contains("spring-petclinic"));
        
        // Extract the cloned repository path for cleanup
        clonedRepoPath = result.substring(result.indexOf(":") + 1).trim();
        
        // Verify the repository directory exists
        File repoDir = new File(clonedRepoPath);
        assertTrue(repoDir.exists(), "Cloned repository directory should exist");
        assertTrue(repoDir.isDirectory(), "Cloned repository should be a directory");
        
        // Verify .git directory exists
        File gitDir = new File(repoDir, ".git");
        assertTrue(gitDir.exists(), ".git directory should exist");
        assertTrue(gitDir.isDirectory(), ".git directory should be a directory");
    }

    @Test
    void testGetStatusAfterClone() throws GitAPIException, InterruptedException, IOException {
        // Clone the repository first
        String remoteUrl = "https://github.com/davidparry/profile-octo-robot.git";
        String cloneResult = gitService.cloneRepository(remoteUrl, null);
        
        // Extract the cloned repository path
        clonedRepoPath = cloneResult.substring(cloneResult.indexOf(":") + 1).trim();
        
        // Get the status of the cloned repository
        String status = gitService.getStatus(clonedRepoPath);
        
        // Verify status output
        assertNotNull(status);
        assertTrue(status.contains("Git Status"));
        assertTrue(status.contains("Branch:"));
        assertTrue(status.contains("Working tree clean") || status.contains("Untracked") || status.contains("Modified"));
    }

    @Test
    void testGetLogAfterClone() throws GitAPIException, InterruptedException, IOException {
        // Clone the repository first
        String remoteUrl = "https://github.com/davidparry/profile-octo-robot.git";
        String cloneResult = gitService.cloneRepository(remoteUrl, null);
        
        // Extract the cloned repository path
        clonedRepoPath = cloneResult.substring(cloneResult.indexOf(":") + 1).trim();
        
        // Get the log of the cloned repository
        String log = gitService.getLog(clonedRepoPath, 5);
        
        // Verify log output
        assertNotNull(log);
        assertTrue(log.contains("Git Log"));
        assertTrue(log.contains("Commit:"));
        assertTrue(log.contains("Author:"));
    }

    @Test
    void testListBranchesAfterClone() throws GitAPIException, InterruptedException, IOException {
        // Clone the repository first
        String remoteUrl = "https://github.com/davidparry/profile-octo-robot.git";
        String cloneResult = gitService.cloneRepository(remoteUrl, null);
        
        // Extract the cloned repository path
        clonedRepoPath = cloneResult.substring(cloneResult.indexOf(":") + 1).trim();
        
        // List branches of the cloned repository
        String branches = gitService.listBranches(clonedRepoPath);
        
        // Verify branches output
        assertNotNull(branches);
        assertTrue(branches.contains("Branches:"));
        assertTrue(branches.contains("master") || branches.contains("main") || branches.contains("trunk"));
    }

    @Test
    void testCreateBranchAddFileAndPush() throws GitAPIException, InterruptedException, IOException {
        // Clone the repository first using SSH
        String remoteUrl = "git@github.com:davidparry/profile-octo-robot.git";
        String cloneResult = gitService.cloneRepository(remoteUrl, null);
        
        // Extract the cloned repository path
        clonedRepoPath = cloneResult.substring(cloneResult.indexOf(":") + 1).trim();
        
        // Create a unique branch name using timestamp to avoid conflicts
        String branchName = "test-branch-" + System.currentTimeMillis();
        
        // Create a new branch
        String createBranchResult = gitService.createBranch(clonedRepoPath, branchName);
        assertTrue(createBranchResult.contains("Branch '" + branchName + "' created successfully"));
        
        // Checkout the new branch
        String checkoutResult = gitService.checkoutBranch(clonedRepoPath, branchName);
        assertTrue(checkoutResult.contains("Switched to branch '" + branchName + "'"));
        
        // Create a file.md in the repository
        Path filePath = Path.of(clonedRepoPath, "file.md");
        Files.writeString(filePath, "# Test File\n\nThis is a test file created by integration test.");
        
        // Verify the file was created
        assertTrue(Files.exists(filePath), "file.md should exist");
        
        // Commit the file
        String commitResult = gitService.commit(clonedRepoPath, "Add file.md for integration test", List.of("file.md"));
        assertTrue(commitResult.contains("Committed"));
        assertTrue(commitResult.contains("Add file.md for integration test"));
        
        // Push to remote origin
        String pushResult = gitService.push(clonedRepoPath, "origin", branchName);
        assertTrue(pushResult.contains("Pushed to origin/" + branchName));
        
        // Checkout trunk (or main/master)
        // First, let's check what the default branch is
        String branches = gitService.listBranches(clonedRepoPath);
        String defaultBranch = "trunk";
        if (branches.contains("main")) {
            defaultBranch = "main";
        } else if (branches.contains("master")) {
            defaultBranch = "master";
        }
        
        String checkoutTrunkResult = gitService.checkoutBranch(clonedRepoPath, defaultBranch);
        assertTrue(checkoutTrunkResult.contains("Switched to branch '" + defaultBranch + "'"));
        
        // Verify file.md does not exist in trunk
        assertFalse(Files.exists(filePath), "file.md should not exist in " + defaultBranch);
        
        // Checkout the test branch again
        String checkoutTestBranchAgain = gitService.checkoutBranch(clonedRepoPath, branchName);
        assertTrue(checkoutTestBranchAgain.contains("Switched to branch '" + branchName + "'"));
        
        // Verify file.md is still present
        assertTrue(Files.exists(filePath), "file.md should exist in " + branchName + " branch");
        
        // Verify the content of file.md
        String content = Files.readString(filePath);
        assertTrue(content.contains("# Test File"), "file.md should contain the expected content");
        assertTrue(content.contains("This is a test file created by integration test"), "file.md should contain the expected content");
    }

    /**
     * Recursively delete a directory and all its contents
     */
    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        Files.delete(file.toPath());
                    }
                }
            }
            Files.delete(directory.toPath());
        }
    }
}
