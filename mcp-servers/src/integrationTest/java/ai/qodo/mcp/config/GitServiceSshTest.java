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

/**
 * Integration test for GitService using SSH authentication.
 * This test requires SSH keys to be properly configured for GitHub access.
 * 
 * To run this test successfully, ensure:
 * 1. SSH keys are set up in ~/.ssh/
 * 2. The SSH key is added to your GitHub account
 * 3. The repository git@github.com:davidparry/profile-octo-robot.git is accessible
 */
class GitServiceSshTest {

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
    void testCloneRepositoryWithSsh() throws Exception {
        // Clone the specified repository using SSH
        // NOTE: This requires SSH keys to be properly configured
        String remoteUrl = "git@github.com:davidparry/profile-octo-robot.git";
        
        String result = gitService.cloneRepository(remoteUrl, null);
        
        // Verify the clone was successful
        assertNotNull(result);
        assertTrue(result.contains("Repository cloned successfully"));
        assertTrue(result.contains("profile-octo-robot"));
        
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
    void testGetStatusAfterSshClone() throws Exception {
        // Clone the repository first using SSH
        String remoteUrl = "git@github.com:davidparry/profile-octo-robot.git";
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
    void testGetLogAfterSshClone() throws Exception {
        // Clone the repository first using SSH
        String remoteUrl = "git@github.com:davidparry/profile-octo-robot.git";
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
    void testListBranchesAfterSshClone() throws Exception {
        // Clone the repository first using SSH
        String remoteUrl = "git@github.com:davidparry/profile-octo-robot.git";
        String cloneResult = gitService.cloneRepository(remoteUrl, null);
        
        // Extract the cloned repository path
        clonedRepoPath = cloneResult.substring(cloneResult.indexOf(":") + 1).trim();
        
        // List branches of the cloned repository
        String branches = gitService.listBranches(clonedRepoPath);
        
        // Verify branches output
        assertNotNull(branches);
        assertTrue(branches.contains("Branches:"));
        assertTrue(branches.contains("trunk") || branches.contains("master") || branches.contains("main"));
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
