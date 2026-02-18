/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CountDownLatch;

/**
 * Configuration for Terminal MCP Server.
 * <p>
 * This configuration manages terminal-specific settings like blocked commands
 * and provides a facade to the centralized McpToolsConfiguration for roots management,
 * insulating TerminalService from direct dependencies on the shared configuration.
 */
@Configuration
public class SnykMcpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SnykMcpConfiguration.class);
    private final McpToolsConfiguration mcpToolsConfiguration;
    private final SnykConfiguration snykConfiguration;

    public SnykMcpConfiguration(McpToolsConfiguration mcpToolsConfiguration, SnykConfiguration snykConfiguration) {
        this.mcpToolsConfiguration = mcpToolsConfiguration;
        this.snykConfiguration = snykConfiguration;
    }

    public String getSnykApiToken() {
        return snykConfiguration.getApiToken();
    }

    public SnykConfiguration getSnykConfiguration() {
        return snykConfiguration;
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

    public boolean isConfigurationValid() {
        if (getSnykApiToken() == null || getSnykApiToken().isEmpty()) {
            logger.warn("Snyk API token is not configured");
            return false;
        }
        return true;
    }

}
