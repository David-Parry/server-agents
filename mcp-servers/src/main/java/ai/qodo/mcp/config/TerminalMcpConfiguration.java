/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Configuration for Terminal MCP Server.
 * <p>
 * This configuration manages terminal-specific settings like blocked commands
 * and provides a facade to the centralized McpToolsConfiguration for roots management,
 * insulating TerminalService from direct dependencies on the shared configuration.
 */
@Configuration
public class TerminalMcpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TerminalMcpConfiguration.class);
    private final McpToolsConfiguration mcpToolsConfiguration;

    // Combined blocked commands (default + configured)
    private final Set<String> BLOCKED_COMMANDS = new HashSet<>();

    @Value("${mcp.terminal.blocked:}")
    private String blockedCommands;

    @Value("${mcp.terminal.default-timeout-seconds:500}")
    private long defaultTimeoutSeconds;

    public TerminalMcpConfiguration(McpToolsConfiguration mcpToolsConfiguration) {
        this.mcpToolsConfiguration = mcpToolsConfiguration;
    }

    public Set<String> getBlockedCommands() {
        return Set.copyOf(BLOCKED_COMMANDS);
    }

    /**
     * Returns the default timeout in seconds for terminal command execution.
     */
    public long getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    /**
     * Returns the default local path from centralized configuration.
     */
    public String getDefaultLocalPath() {
        return mcpToolsConfiguration.getDefaultLocalPath();
    }

    /**
     * Checks if roots are initialized in centralized configuration.
     */
    public boolean isRootsInitialized() {
        return mcpToolsConfiguration.isRootsInitialized();
    }

    /**
     * Returns the roots latch from centralized configuration.
     */
    public CountDownLatch getRootsLatch() {
        return mcpToolsConfiguration.getRootsLatch();
    }


    /**
     * Ensures roots are initialized before proceeding with operations.
     * Delegates to centralized configuration.
     *
     * @param toolContext The tool context containing the MCP exchange
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void ensureRootsInitialized(ToolContext toolContext) throws InterruptedException {
        mcpToolsConfiguration.ensureRootsInitialized(toolContext);
    }

    @PostConstruct
    public void init() {
        // Parse and add additional blocked commands from configuration
        if (blockedCommands != null && !blockedCommands.trim().isEmpty()) {
            String[] additionalCommands = blockedCommands.split(",");
            for (String cmd : additionalCommands) {
                String trimmedCmd = cmd.trim();
                if (!trimmedCmd.isEmpty()) {
                    BLOCKED_COMMANDS.add(trimmedCmd.toLowerCase());
                }
            }
        }

        logger.info("Initialized with {} blocked commands which are {}", BLOCKED_COMMANDS.size(), BLOCKED_COMMANDS);
    }
}
