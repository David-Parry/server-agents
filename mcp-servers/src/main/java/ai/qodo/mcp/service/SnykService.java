/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.service;

import ai.qodo.mcp.config.SnykMcpConfiguration;
import ai.qodo.mcp.pojo.ToolOutputResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.*;

@Service()
@ConditionalOnProperty(name = "mcp.snyk.enabled", havingValue = "true", matchIfMissing = true)
public class SnykService {

    private static final Logger logger = LoggerFactory.getLogger(SnykService.class);
    private final SnykMcpConfiguration snycMcpConfiguration;

    public SnykService(SnykMcpConfiguration snycMcpConfiguration) {
        this.snycMcpConfiguration = snycMcpConfiguration;
    }

    @Tool(name = "snyk_test_command", 
          description = "Executes Snyk security test on the project and returns vulnerability findings in JSON format." +
                  "Returns JSON output with security vulnerabilities, dependencies, and remediation advice.")
    public ToolOutputResult snykRunTest(ToolContext toolContext) throws InterruptedException {
        // Ensure roots are initialized before proceeding
        ensureRootsInitialized(toolContext);
        
        String snykApiToken = snycMcpConfiguration.getSnykApiToken();
        
        if (snykApiToken == null || snykApiToken.trim().isEmpty()) {
            logger.error("Snyk API token is not configured");
            return new ToolOutputResult(
                    "",
                    "Snyk API token is not configured",
                    -1,
                    true,
                    "Missing SNYK_TOKEN configuration"
            );
        }
        
        String workingDirectory = snycMcpConfiguration.getDefaultLocalPath();
        logger.info("Executing snyk test --json in directory: {}", workingDirectory);

        ProcessBuilder processBuilder = new ProcessBuilder();
        
        // Determine shell based on OS and set command with environment variable
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            processBuilder.command("cmd.exe", "/c", "set SNYK_TOKEN=" + snykApiToken + " && snyk test --json");
        } else {
            processBuilder.command("sh", "-c", "SNYK_TOKEN=" + snykApiToken + " snyk test --json");
        }
        
        // Set working directory - this ensures the command runs in the correct project directory
        if (workingDirectory != null) {
            processBuilder.directory(new File(workingDirectory));
        }
        
        // Don't merge error stream - keep them separate
        processBuilder.redirectErrorStream(false);
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        try {
            Process process = processBuilder.start();
            
            // Create futures for reading stdout and stderr
            Future<String> outputFuture = executor.submit(() -> readStream(process.getInputStream()));
            Future<String> errorFuture = executor.submit(() -> readStream(process.getErrorStream()));
            
            // Wait for process to complete with a reasonable timeout (Snyk can take time)
            long timeoutSeconds = 300; // 5 minutes for Snyk scan
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            
            if (!completed) {
                // Process timed out
                process.destroyForcibly();
                logger.warn("Snyk test timed out after {} seconds", timeoutSeconds);
                
                return new ToolOutputResult(
                        "",
                        "Snyk test execution timed out after " + timeoutSeconds + " seconds",
                        -1,
                        true,
                        "Timeout"
                );
            }
            
            // Get output and error streams
            String output = outputFuture.get(5, TimeUnit.SECONDS);
            String error = errorFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            
            // Snyk returns non-zero exit code when vulnerabilities are found
            // This is not necessarily an error - it's expected behavior
            boolean isError = exitCode != 0 && (output == null || output.trim().isEmpty());
            String errorMessage = null;
            
            if (isError) {
                errorMessage = "Snyk test failed with exit code " + exitCode;
                logger.error("Snyk test failed: exit code {}, error: {}", exitCode, error);
            } else if (exitCode != 0) {
                // Vulnerabilities found but command executed successfully
                logger.info("Snyk test completed with vulnerabilities found (exit code: {})", exitCode);
            } else {
                logger.info("Snyk test completed successfully with no vulnerabilities");
            }
            
            return new ToolOutputResult(output, error, exitCode, isError, errorMessage);
            
        } catch (IOException e) {
            logger.error("Failed to execute snyk test command", e);
            return new ToolOutputResult(
                    "",
                    "Failed to execute snyk test: " + e.getMessage(),
                    -1,
                    true,
                    "IOException"
            );
        } catch (TimeoutException e) {
            logger.error("Timeout reading snyk test output", e);
            return new ToolOutputResult(
                    "",
                    "Timeout reading snyk test output",
                    -1,
                    true,
                    "Timeout reading output"
            );
        } catch (ExecutionException e) {
            logger.error("Error reading snyk test output", e);
            return new ToolOutputResult(
                    "",
                    "Error reading snyk test output: " + e.getMessage(),
                    -1,
                    true,
                    "ExecutionException"
            );
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Ensures roots are initialized before proceeding with operations.
     * Delegates to configuration.
     */
    private void ensureRootsInitialized(ToolContext toolContext) throws InterruptedException {
        snycMcpConfiguration.ensureRootsInitialized(toolContext);
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
