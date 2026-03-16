/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp;

import ai.qodo.mcp.config.TerminalMcpConfiguration;
import ai.qodo.mcp.pojo.ToolOutputResult;
import ai.qodo.mcp.service.TerminalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class TerminalServiceTest {

    private TerminalService terminalService;
    
    @TempDir
    Path tempDir;
    
    @Mock
    private TerminalMcpConfiguration mcpConfiguration;
    
    @Mock
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock the configuration to return the temp directory
        when(mcpConfiguration.getDefaultLocalPath()).thenReturn(tempDir.toString());
        when(mcpConfiguration.isRootsInitialized()).thenReturn(true);
        when(mcpConfiguration.getRootsLatch()).thenReturn(new CountDownLatch(0));
        when(mcpConfiguration.getDefaultTimeoutSeconds()).thenReturn(30L);
        
        terminalService = new TerminalService(mcpConfiguration);
    }

    @Test
    void testExecuteSimpleCommand() throws InterruptedException {
        ToolOutputResult result = terminalService.executeCommand("echo Hello World", null, toolContext);
        
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Hello World"));
    }

    @Test
    void testExecuteCommandWithTimeout() throws InterruptedException {
        // Test with a very short timeout
        ToolOutputResult result = terminalService.executeCommand("sleep 10", 1, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.errorMessage().contains("Timeout") || result.errorMessage().contains("timeout"));
    }

    @Test
    void testBlockedCommand() throws InterruptedException {
        when(mcpConfiguration.getBlockedCommands()).thenReturn(Set.of("rm"));

        ToolOutputResult result = terminalService.executeCommand("rm -rf /", null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.error().contains("blocked") || result.error().contains("security"));
    }

    @Test
    void testCommandWithNonZeroExit() throws InterruptedException {
        // This command should fail on most systems
        ToolOutputResult result = terminalService.executeCommand("ls /nonexistent-directory-12345", null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
        assertNotEquals(0, result.exitCode());
    }

    @Test
    void testCommandInWorkingDirectory() throws InterruptedException, IOException {
        // Create a test file in the temp directory
        Path testFile = tempDir.resolve("test-file.txt");
        Files.writeString(testFile, "test content");
        
        // List files in the directory
        ToolOutputResult result = terminalService.executeCommand("ls", null, toolContext);
        
        assertNotNull(result);
        assertFalse(result.isError());
        assertTrue(result.output().contains("test-file.txt"));
    }

    @Test
    void testEmptyCommand() throws InterruptedException {
        ToolOutputResult result = terminalService.executeCommand("", null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
    }

    @Test
    void testCommandWithStderr() throws InterruptedException {
        // Command that writes to stderr (works on Unix-like systems)
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win") ? "echo Error message 1>&2" : "echo 'Error message' >&2";
        
        ToolOutputResult result = terminalService.executeCommand(command, null, toolContext);
        
        assertNotNull(result);
        // The command itself succeeds, but writes to stderr
        assertFalse(result.isError());
        assertTrue(result.error().contains("Error message") || result.output().contains("Error message"));
    }

    @Test
    void testMultipleBlockedCommands() throws InterruptedException {
        String[] blockedCommands = {"rm test.txt", "sudo ls", "shutdown now", "chmod 777 file"};
        
        for (String command : blockedCommands) {
            ToolOutputResult result = terminalService.executeCommand(command, null, toolContext);
            
            assertNotNull(result, "Result should not be null for command: " + command);
            assertTrue(result.isError(), "Command should be blocked: " + command);
        }
    }

    @Test
    void testTerminalResultToString() throws InterruptedException {
        ToolOutputResult result = terminalService.executeCommand("echo test", null, toolContext);
        
        String resultString = result.toString();
        assertNotNull(resultString);
        assertTrue(resultString.contains("Exit Code"));
    }

    @Test
    void testValidateCommandWithPath() throws InterruptedException {
        // Test command with path should extract base command
        when(mcpConfiguration.getBlockedCommands()).thenReturn(Set.of("rm"));
        
        // This should be blocked because it contains 'rm'
        ToolOutputResult result = terminalService.executeCommand("/usr/bin/rm file.txt", null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.error().contains("blocked") || result.error().contains("security"));
    }

    @Test
    void testNullCommand() throws InterruptedException {
        ToolOutputResult result = terminalService.executeCommand(null, null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.error().contains("empty") || result.error().contains("validation"));
    }

    @Test
    void testWhitespaceOnlyCommand() throws InterruptedException {
        ToolOutputResult result = terminalService.executeCommand("   ", null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
    }

    @Test
    void testCommandWithCustomTimeout() throws InterruptedException {
        // Test with a reasonable timeout
        ToolOutputResult result = terminalService.executeCommand("echo 'test with timeout'", 5, toolContext);
        
        assertNotNull(result);
        assertFalse(result.isError());
        assertTrue(result.output().contains("test with timeout"));
    }

    @Test
    void testIOExceptionHandling() throws InterruptedException {
        // Try to execute a command that doesn't exist to trigger IOException path
        String invalidCommand = "this_command_definitely_does_not_exist_12345";
        ToolOutputResult result = terminalService.executeCommand(invalidCommand, null, toolContext);
        
        assertNotNull(result);
        // The result should indicate an error
        assertTrue(result.isError() || result.exitCode() != 0);
    }

    @Test
    void testCommandWithLongOutput() throws InterruptedException {
        // Test command that produces multiple lines of output
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win") ? "dir" : "ls -la";
        
        ToolOutputResult result = terminalService.executeCommand(command, null, toolContext);
        
        assertNotNull(result);
        assertFalse(result.isError());
        assertFalse(result.output().isEmpty());
    }

    @Test
    void testCommandWithSpecialCharacters() throws InterruptedException {
        // Test command with special characters
        ToolOutputResult result = terminalService.executeCommand("echo 'Hello & World'", null, toolContext);
        
        assertNotNull(result);
        assertFalse(result.isError());
    }

    @Test
    void testBlockedCommandCaseInsensitive() throws InterruptedException {
        when(mcpConfiguration.getBlockedCommands()).thenReturn(Set.of("rm"));
        
        // Test uppercase version of blocked command
        ToolOutputResult result = terminalService.executeCommand("RM file.txt", null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
    }

    @Test
    void testSuccessfulCommandWithZeroExitCode() throws InterruptedException {
        ToolOutputResult result = terminalService.executeCommand("echo success", null, toolContext);
        
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals(0, result.exitCode());
        assertNull(result.errorMessage());
    }

    @Test
    void testCommandWithBothStdoutAndStderr() throws InterruptedException {
        // Create a command that writes to both stdout and stderr
        String os = System.getProperty("os.name").toLowerCase();
        String command;
        if (os.contains("win")) {
            command = "echo stdout && echo stderr 1>&2";
        } else {
            command = "echo 'stdout' && echo 'stderr' >&2";
        }
        
        ToolOutputResult result = terminalService.executeCommand(command, null, toolContext);
        
        assertNotNull(result);
        // Command should succeed
        assertFalse(result.isError());
        // Should have output in stdout
        assertTrue(result.output().contains("stdout"));
        // Should have output in stderr
        assertTrue(result.error().contains("stderr"));
    }

    @Test
    void testMultipleSequentialCommands() throws InterruptedException {
        // Execute multiple commands in sequence
        ToolOutputResult result1 = terminalService.executeCommand("echo first", null, toolContext);
        ToolOutputResult result2 = terminalService.executeCommand("echo second", null, toolContext);
        ToolOutputResult result3 = terminalService.executeCommand("echo third", null, toolContext);
        
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);
        
        assertFalse(result1.isError());
        assertFalse(result2.isError());
        assertFalse(result3.isError());
        
        assertTrue(result1.output().contains("first"));
        assertTrue(result2.output().contains("second"));
        assertTrue(result3.output().contains("third"));
    }

    @Test
    void testCommandInDifferentWorkingDirectory() throws InterruptedException, IOException {
        // Create a subdirectory
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        Path fileInSubDir = subDir.resolve("subfile.txt");
        Files.writeString(fileInSubDir, "content in subdir");
        
        // The command should run in tempDir, so it should see the subdir
        ToolOutputResult result = terminalService.executeCommand("ls", null, toolContext);
        
        assertNotNull(result);
        assertFalse(result.isError());
        assertTrue(result.output().contains("subdir"));
    }

    @Test
    void testTerminalResultFields() throws InterruptedException {
        ToolOutputResult result = terminalService.executeCommand("echo test", null, toolContext);
        
        assertNotNull(result);
        assertNotNull(result.output());
        assertNotNull(result.error());
        assertEquals(0, result.exitCode());
        assertFalse(result.isError());
    }

    @Test
    void testErrorResultFields() throws InterruptedException {
        ToolOutputResult result = terminalService.executeCommand("ls /nonexistent-path-12345", null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
        assertNotEquals(0, result.exitCode());
        assertNotNull(result.errorMessage());
    }
}
