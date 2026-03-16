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
import org.springframework.stereotype.Component;

@Component
public class GithubMcpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GithubMcpConfiguration.class);
    private final GithubConfiguration githubConfiguration;
    private final McpToolsConfiguration mcpToolsConfiguration;


    public GithubMcpConfiguration(GithubConfiguration githubConfiguration,
                                  McpToolsConfiguration mcpToolsConfiguration) {
        this.githubConfiguration = githubConfiguration;
        this.mcpToolsConfiguration = mcpToolsConfiguration;
    }

    public GithubConfiguration getGithubConfiguration() {
        return githubConfiguration;
    }

    /**
     * Returns the default local path from centralized configuration.
     */
    public String getDefaultLocalPath() {
        return mcpToolsConfiguration.getDefaultLocalPath();
    }

    /**
     * Validates that all required Jira configuration is present.
     *
     * @return true if configuration is valid, false otherwise
     */
    public boolean isConfigurationValid() {
        if (githubConfiguration.getApiToken() == null || githubConfiguration.getApiToken().isEmpty()) {
            logger.warn("Github API token is not configured");
            return false;
        }
        return true;
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

}