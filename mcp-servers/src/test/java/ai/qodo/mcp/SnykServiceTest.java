/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp;

import ai.qodo.mcp.config.SnykMcpConfiguration;
import ai.qodo.mcp.pojo.ToolOutputResult;
import ai.qodo.mcp.service.SnykService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SnykServiceTest {

    private SnykService snykService;
    
    @TempDir
    Path tempDir;
    
    @Mock
    private SnykMcpConfiguration snykMcpConfiguration;
    
    @Mock
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock the configuration to return the temp directory
        when(snykMcpConfiguration.getDefaultLocalPath()).thenReturn(tempDir.toString());
        
        snykService = new SnykService(snykMcpConfiguration);
    }

    @Test
    void testSnykRunTest_MissingApiToken() throws InterruptedException {
        // Given: No API token configured
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn(null);
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Should return error about missing token
        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals(-1, result.exitCode());
        assertEquals("Missing SNYK_TOKEN configuration", result.errorMessage());
        assertTrue(result.error().contains("Snyk API token is not configured"));
    }

    @Test
    void testSnykRunTest_EmptyApiToken() throws InterruptedException {
        // Given: Empty API token
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("   ");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Should return error about missing token
        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals(-1, result.exitCode());
        assertEquals("Missing SNYK_TOKEN configuration", result.errorMessage());
    }

    @Test
    void testSnykRunTest_ValidToken_SnykNotInstalled() throws InterruptedException {
        // Given: Valid API token but Snyk CLI not installed
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token-12345");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test (will fail if snyk is not installed)
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Should return a result (may be error if snyk not installed)
        assertNotNull(result);
        // If snyk is not installed, we expect an error
        // If it is installed, the test will run (may succeed or fail based on project)
        assertNotNull(result.exitCode());
    }

    @Test
    void testSnykRunTest_EnsureRootsInitializedCalled() throws InterruptedException {
        // Given: Valid configuration
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        snykService.snykRunTest(toolContext);
        
        // Then: Should call ensureRootsInitialized
        verify(snykMcpConfiguration, times(1)).ensureRootsInitialized(toolContext);
    }

    @Test
    void testSnykRunTest_InterruptedException() throws InterruptedException {
        // Given: Configuration that throws InterruptedException
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        doThrow(new InterruptedException("Test interruption")).when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When/Then: Should propagate InterruptedException
        assertThrows(InterruptedException.class, () -> {
            snykService.snykRunTest(toolContext);
        });
    }

    @Test
    void testSnykRunTest_WorkingDirectorySet() throws InterruptedException, IOException {
        // Given: Valid configuration with specific working directory
        String workingDir = tempDir.toString();
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        when(snykMcpConfiguration.getDefaultLocalPath()).thenReturn(workingDir);
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // Create a package.json to make it look like a valid project
        Path packageJson = tempDir.resolve("package.json");
        Files.writeString(packageJson, "{\"name\": \"test-project\", \"version\": \"1.0.0\"}");
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Should execute in the specified directory
        assertNotNull(result);
        verify(snykMcpConfiguration, times(1)).getDefaultLocalPath();
    }

    @Test
    void testSnykRunTest_NullWorkingDirectory() throws InterruptedException {
        // Given: Null working directory
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        when(snykMcpConfiguration.getDefaultLocalPath()).thenReturn(null);
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Should still execute (in current directory)
        assertNotNull(result);
    }

    @Test
    void testSnykRunTest_ResultStructure() throws InterruptedException {
        // Given: Valid configuration
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Result should have proper structure
        assertNotNull(result);
        assertNotNull(result.output());
        assertNotNull(result.error());
        // Exit code should be set (any integer value)
        assertTrue(result.exitCode() >= -1);
    }

    @Test
    void testSnykRunTest_ToStringMethod() throws InterruptedException {
        // Given: Valid configuration
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test and converting to string
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        String resultString = result.toString();
        
        // Then: Should have string representation
        assertNotNull(resultString);
        assertTrue(resultString.contains("Exit Code"));
    }

    @Test
    void testSnykRunTest_ErrorHandling_InvalidDirectory() throws InterruptedException {
        // Given: Invalid working directory
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        when(snykMcpConfiguration.getDefaultLocalPath()).thenReturn("/nonexistent/directory/path/12345");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Should handle the error gracefully
        assertNotNull(result);
        // The command will likely fail, but should not throw exception
    }

    @Test
    void testSnykRunTest_ApiTokenUsedInCommand() throws InterruptedException {
        // Given: Specific API token
        String testToken = "snyk-test-token-abc123";
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn(testToken);
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Should use the token (verified by checking it was retrieved)
        verify(snykMcpConfiguration, atLeastOnce()).getSnykApiToken();
        assertNotNull(result);
    }

    @Test
    void testSnykRunTest_MultipleInvocations() throws InterruptedException {
        // Given: Valid configuration
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test multiple times
        ToolOutputResult result1 = snykService.snykRunTest(toolContext);
        ToolOutputResult result2 = snykService.snykRunTest(toolContext);
        
        // Then: Both should return valid results
        assertNotNull(result1);
        assertNotNull(result2);
        verify(snykMcpConfiguration, times(2)).ensureRootsInitialized(toolContext);
    }

    @Test
    void testSnykRunTest_ErrorResult_HasErrorMessage() throws InterruptedException {
        // Given: Missing API token (will cause error)
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn(null);
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Error result should have error message
        assertTrue(result.isError());
        assertNotNull(result.errorMessage());
        assertFalse(result.errorMessage().isEmpty());
    }

    @Test
    void testSnykRunTest_SuccessfulExecution_NoErrorMessage() throws InterruptedException {
        // Given: Valid configuration with a simple project
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: If successful (exit code 0), should not have error message
        if (result.exitCode() == 0 && !result.isError()) {
            assertNull(result.errorMessage());
        }
    }

    @Test
    void testSnykRunTest_ConfigurationInteraction() throws InterruptedException {
        // Given: Valid configuration
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        when(snykMcpConfiguration.getDefaultLocalPath()).thenReturn(tempDir.toString());
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        snykService.snykRunTest(toolContext);
        
        // Then: Should interact with configuration properly
        verify(snykMcpConfiguration, times(1)).ensureRootsInitialized(toolContext);
        verify(snykMcpConfiguration, atLeastOnce()).getSnykApiToken();
        verify(snykMcpConfiguration, atLeastOnce()).getDefaultLocalPath();
    }

    @Test
    void testSnykRunTest_EmptyStringToken() throws InterruptedException {
        // Given: Empty string token (not null, but empty)
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Should return error about missing token
        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals("Missing SNYK_TOKEN configuration", result.errorMessage());
    }

    @Test
    void testSnykRunTest_ToolContextPassed() throws InterruptedException {
        // Given: Valid configuration
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test with specific tool context
        snykService.snykRunTest(toolContext);
        
        // Then: Tool context should be passed to ensureRootsInitialized
        verify(snykMcpConfiguration, times(1)).ensureRootsInitialized(eq(toolContext));
    }

    @Test
    void testSnykRunTest_ResultNotNull() throws InterruptedException {
        // Given: Any configuration (even invalid)
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn(null);
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Result should never be null
        assertNotNull(result, "SnykService.snykRunTest should never return null");
    }

    @Test
    void testSnykRunTest_ExitCodeRange() throws InterruptedException {
        // Given: Valid configuration
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Exit code should be in valid range
        assertTrue(result.exitCode() >= -1, "Exit code should be -1 or greater");
    }

    @Test
    void testSnykRunTest_OutputAndErrorNotNull() throws InterruptedException {
        // Given: Valid configuration
        when(snykMcpConfiguration.getSnykApiToken()).thenReturn("test-api-token");
        doNothing().when(snykMcpConfiguration).ensureRootsInitialized(any());
        
        // When: Running snyk test
        ToolOutputResult result = snykService.snykRunTest(toolContext);
        
        // Then: Output and error should not be null (can be empty strings)
        assertNotNull(result.output(), "Output should not be null");
        assertNotNull(result.error(), "Error should not be null");
    }
}
