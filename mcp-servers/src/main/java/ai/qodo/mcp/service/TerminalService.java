/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.service;

import ai.qodo.mcp.config.TerminalMcpConfiguration;
import ai.qodo.mcp.pojo.ToolOutputResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.*;

@Service()
@ConditionalOnProperty(
        name = "mcp.terminal.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TerminalService {

    private static final Logger logger = LoggerFactory.getLogger(TerminalService.class);
    private final TerminalMcpConfiguration mcpConfiguration;

    public TerminalService(TerminalMcpConfiguration terminalMcpConfiguration) {
        this.mcpConfiguration = terminalMcpConfiguration;
    }


    /**
     * Ensures roots are initialized before proceeding with operations.
     * Delegates to configuration.
     */
    private void ensureRootsInitialized(ToolContext toolContext) throws InterruptedException {
        mcpConfiguration.ensureRootsInitialized(toolContext);
    }

    /**
     * Validates that the command is not in the blocked list.
     * Checks the first word of the command against blocked commands.
     */
    private void validateCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        String[] parts = command.trim().split("\\s+");
        String baseCommand = parts[0];
        
        // Extract just the command name if it's a path
        if (baseCommand.contains("/")) {
            baseCommand = baseCommand.substring(baseCommand.lastIndexOf("/") + 1);
        }

        if (mcpConfiguration.getBlockedCommands().contains(baseCommand.toLowerCase())) {
            throw new SecurityException("Command '" + baseCommand + "' is blocked for security reasons");
        }
    }

    @Tool(name = "terminal_execute_command", 
          description = "Executes a shell command in a separate process and returns the output. " +
                  "The command runs in the configured root directory (from MCP roots). " +
                  "Commands are executed with a timeout to prevent hanging. " +
                  "Dangerous commands (rm, shutdown, etc.) are blocked for security. " +
                  "Returns both stdout and stderr, along with exit code and error status. " +
                  "Use this to run shell commands, build scripts, tests, or any CLI operations.")
    public ToolOutputResult executeCommand(
            @ToolParam(description = "The shell command to execute") String command,
            @ToolParam(description = "Optional timeout in seconds (default: 180)",required = false) Integer timeoutSeconds,
            ToolContext toolContext) throws InterruptedException {
        
        // Ensure roots are initialized before proceeding
        ensureRootsInitialized(toolContext);
        
        // Validate command is not blocked
        try {
            validateCommand(command);
        } catch (SecurityException | IllegalArgumentException e) {
            logger.error("Command validation failed: {}", e.getMessage());
            return new ToolOutputResult(
                    "",
                    e.getMessage(),
                    -1,
                    true,
                    "Command validation failed"
            );
        }

        String workingDirectory = this.mcpConfiguration.getDefaultLocalPath();
        long timeout = timeoutSeconds != null ? timeoutSeconds : mcpConfiguration.getDefaultTimeoutSeconds();
        
        logger.info("Executing command: {} in directory: {} with timeout: {}s", 
                command, workingDirectory, timeout);

        ProcessBuilder processBuilder = new ProcessBuilder();
        
        // Determine shell based on OS
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            processBuilder.command("cmd.exe", "/c", command);
        } else {
            processBuilder.command("sh", "-c", command);
        }
        
        // Set working directory
        if (workingDirectory != null) {
            processBuilder.directory(new File(workingDirectory));
        }
        
        // Merge error stream with output stream
        processBuilder.redirectErrorStream(false);
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        try {
            Process process = processBuilder.start();
            
            // Create futures for reading stdout and stderr
            Future<String> outputFuture = executor.submit(() -> readStream(process.getInputStream()));
            Future<String> errorFuture = executor.submit(() -> readStream(process.getErrorStream()));
            
            // Wait for process to complete with timeout
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            
            if (!completed) {
                // Process timed out
                process.destroyForcibly();
                logger.warn("Command timed out after {} seconds: {}", timeout, command);
                
                return new ToolOutputResult(
                        "",
                        "Command execution timed out after " + timeout + " seconds",
                        -1,
                        true,
                        "Timeout"
                );
            }
            
            // Get output and error streams
            String output = outputFuture.get(5, TimeUnit.SECONDS);
            String error = errorFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            
            boolean isError = exitCode != 0;
            String errorMessage = isError ? "Command exited with code " + exitCode : null;
            
            logger.info("Command completed with exit code: {}", exitCode);
            
            return new ToolOutputResult(output, error, exitCode, isError, errorMessage);
            
        } catch (IOException e) {
            logger.error("Failed to execute command: {}", command, e);
            return new ToolOutputResult(
                    "",
                    "Failed to execute command: " + e.getMessage(),
                    -1,
                    true,
                    "IOException"
            );
        } catch (TimeoutException e) {
            logger.error("Timeout reading command output: {}", command, e);
            return new ToolOutputResult(
                    "",
                    "Timeout reading command output",
                    -1,
                    true,
                    "Timeout reading output"
            );
        } catch (ExecutionException e) {
            logger.error("Error reading command output: {}", command, e);
            return new ToolOutputResult(
                    "",
                    "Error reading command output: " + e.getMessage(),
                    -1,
                    true,
                    "ExecutionException"
            );
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Reads an input stream and returns its content as a string.
     */
    private String readStream(java.io.InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
}
