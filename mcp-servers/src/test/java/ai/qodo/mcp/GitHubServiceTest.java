/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp;

import ai.qodo.mcp.config.GithubConfiguration;
import ai.qodo.mcp.config.GithubMcpConfiguration;
import ai.qodo.mcp.config.McpToolsConfiguration;
import ai.qodo.mcp.service.GitHubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GitHubService focusing on URL parsing and error handling.
 */
@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private GithubMcpConfiguration githubMcpConfiguration;

    @Mock
    private GithubConfiguration githubConfiguration;

    @Mock
    private McpToolsConfiguration mcpToolsConfiguration;

    private GitHubService gitHubService;

    @BeforeEach
    void setUp() {
        gitHubService = new GitHubService(githubMcpConfiguration);
    }

    @Test
    void testParseGitHubUrl_SSH_Format() throws Exception {
        String sshUrl = "git@github.com:owner/repo-name.git";
        String result = invokeParseGitHubUrl(sshUrl);
        assertEquals("owner/repo-name", result);
    }

    @Test
    void testParseGitHubUrl_HTTPS_Format() throws Exception {
        String httpsUrl = "https://github.com/owner/repo-name.git";
        String result = invokeParseGitHubUrl(httpsUrl);
        assertEquals("owner/repo-name", result);
    }

    @Test
    void testParseGitHubUrl_HTTPS_Without_Git_Extension() throws Exception {
        String httpsUrl = "https://github.com/owner/repo-name";
        String result = invokeParseGitHubUrl(httpsUrl);
        assertEquals("owner/repo-name", result);
    }

    @Test
    void testParseGitHubUrl_Git_Protocol() throws Exception {
        String gitUrl = "git://github.com/owner/repo-name.git";
        String result = invokeParseGitHubUrl(gitUrl);
        assertEquals("owner/repo-name", result);
    }

    @Test
    void testParseGitHubUrl_With_Hyphen_In_Name() throws Exception {
        String url = "git@github.com:davidparry/demo-spring-petclinic-graphql.git";
        String result = invokeParseGitHubUrl(url);
        assertEquals("davidparry/demo-spring-petclinic-graphql", result);
    }

    @Test
    void testParseGitHubUrl_Null_URL() {
        Exception exception = assertThrows(Exception.class, () -> {
            invokeParseGitHubUrl(null);
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void testParseGitHubUrl_Empty_URL() {
        Exception exception = assertThrows(Exception.class, () -> {
            invokeParseGitHubUrl("");
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void testParseGitHubUrl_Invalid_Format() {
        Exception exception = assertThrows(Exception.class, () -> {
            invokeParseGitHubUrl("invalid-url-without-slash");
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void testFindGitDirectory_Null_Path() throws Exception {
        File result = invokeFindGitDirectory(null);
        assertNull(result);
    }

    @Test
    void testFindGitDirectory_Empty_Path() throws Exception {
        File result = invokeFindGitDirectory("");
        assertNull(result);
    }

    @Test
    void testFindGitDirectory_NonExistent_Path() throws Exception {
        File result = invokeFindGitDirectory("/non/existent/path");
        assertNull(result);
    }

    @Test
    void testFindExistingPullRequest_ReturnsExistingPR() throws Exception {
        // Setup mocks
        GHRepository mockRepo = mock(GHRepository.class);
        GHPullRequestQueryBuilder mockQueryBuilder = mock(GHPullRequestQueryBuilder.class);
        PagedIterable<GHPullRequest> mockPagedIterable = mock(PagedIterable.class);
        GHPullRequest mockPR = mock(GHPullRequest.class);
        
        when(mockRepo.queryPullRequests()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.state(GHIssueState.OPEN)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.head("feature-branch")).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.base("main")).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(mockPagedIterable);
        when(mockPagedIterable.toList()).thenReturn(List.of(mockPR));
        when(mockPR.getNumber()).thenReturn(42);
        
        // Invoke the method
        GHPullRequest result = invokeFindExistingPullRequest(mockRepo, "feature-branch", "main");
        
        // Verify
        assertNotNull(result);
        assertEquals(42, result.getNumber());
    }

    @Test
    void testFindExistingPullRequest_ReturnsNullWhenNoPRExists() throws Exception {
        // Setup mocks
        GHRepository mockRepo = mock(GHRepository.class);
        GHPullRequestQueryBuilder mockQueryBuilder = mock(GHPullRequestQueryBuilder.class);
        PagedIterable<GHPullRequest> mockPagedIterable = mock(PagedIterable.class);
        
        when(mockRepo.queryPullRequests()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.state(GHIssueState.OPEN)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.head(anyString())).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.base("main")).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(mockPagedIterable);
        when(mockPagedIterable.toList()).thenReturn(Collections.emptyList());
        when(mockRepo.getOwnerName()).thenReturn("owner");
        
        // Invoke the method
        GHPullRequest result = invokeFindExistingPullRequest(mockRepo, "feature-branch", "main");
        
        // Verify
        assertNull(result);
    }

    @Test
    void testFindExistingPullRequest_ReturnsNullOnIOException() throws Exception {
        // Setup mocks
        GHRepository mockRepo = mock(GHRepository.class);
        GHPullRequestQueryBuilder mockQueryBuilder = mock(GHPullRequestQueryBuilder.class);
        PagedIterable<GHPullRequest> mockPagedIterable = mock(PagedIterable.class);
        
        when(mockRepo.queryPullRequests()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.state(GHIssueState.OPEN)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.head("feature-branch")).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.base("main")).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(mockPagedIterable);
        when(mockPagedIterable.toList()).thenThrow(new IOException("API error"));
        
        // Invoke the method - should return null and not throw
        GHPullRequest result = invokeFindExistingPullRequest(mockRepo, "feature-branch", "main");
        
        // Verify - should gracefully return null on error
        assertNull(result);
    }

    @Test
    void testFindExistingPullRequest_FindsPRWithOwnerPrefix() throws Exception {
        // Setup mocks
        GHRepository mockRepo = mock(GHRepository.class);
        GHPullRequestQueryBuilder mockQueryBuilder = mock(GHPullRequestQueryBuilder.class);
        PagedIterable<GHPullRequest> mockPagedIterable = mock(PagedIterable.class);
        PagedIterable<GHPullRequest> mockPagedIterableWithOwner = mock(PagedIterable.class);
        GHPullRequest mockPR = mock(GHPullRequest.class);
        
        when(mockRepo.queryPullRequests()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.state(GHIssueState.OPEN)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.head("feature-branch")).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.head("owner:feature-branch")).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.base("main")).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(mockPagedIterable).thenReturn(mockPagedIterableWithOwner);
        when(mockPagedIterable.toList()).thenReturn(Collections.emptyList());
        when(mockPagedIterableWithOwner.toList()).thenReturn(List.of(mockPR));
        when(mockRepo.getOwnerName()).thenReturn("owner");
        when(mockPR.getNumber()).thenReturn(99);
        
        // Invoke the method
        GHPullRequest result = invokeFindExistingPullRequest(mockRepo, "feature-branch", "main");
        
        // Verify - should find PR with owner prefix
        assertNotNull(result);
        assertEquals(99, result.getNumber());
    }

    /**
     * Helper method to invoke private parseGitHubUrl method via reflection.
     */
    private String invokeParseGitHubUrl(String url) throws Exception {
        Method method = GitHubService.class.getDeclaredMethod("parseGitHubUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(gitHubService, url);
    }

    /**
     * Helper method to invoke protected findGitDirectory method via reflection.
     */
    private File invokeFindGitDirectory(String path) throws Exception {
        Method method = GitHubService.class.getDeclaredMethod("findGitDirectory", String.class);
        method.setAccessible(true);
        return (File) method.invoke(gitHubService, path);
    }

    /**
     * Helper method to invoke private findExistingPullRequest method via reflection.
     */
    private GHPullRequest invokeFindExistingPullRequest(GHRepository repository, String sourceBranch, String targetBranch) throws Exception {
        Method method = GitHubService.class.getDeclaredMethod("findExistingPullRequest", GHRepository.class, String.class, String.class);
        method.setAccessible(true);
        return (GHPullRequest) method.invoke(gitHubService, repository, sourceBranch, targetBranch);
    }
}
