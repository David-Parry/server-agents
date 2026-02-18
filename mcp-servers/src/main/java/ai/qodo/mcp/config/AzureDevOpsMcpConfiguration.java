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
public class AzureDevOpsMcpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AzureDevOpsMcpConfiguration.class);
    private final AzureDevOpsConfiguration azureDevOpsConfiguration;
    private final McpToolsConfiguration mcpToolsConfiguration;

    public AzureDevOpsMcpConfiguration(AzureDevOpsConfiguration azureDevOpsConfiguration,
                                       McpToolsConfiguration mcpToolsConfiguration) {
        this.azureDevOpsConfiguration = azureDevOpsConfiguration;
        this.mcpToolsConfiguration = mcpToolsConfiguration;
    }

    public AzureDevOpsConfiguration getAzureDevOpsConfiguration() {
        return azureDevOpsConfiguration;
    }

    /**
     * Returns the default local path from centralized configuration.
     */
    public String getDefaultLocalPath() {
        return mcpToolsConfiguration.getDefaultLocalPath();
    }

    /**
     * Validates that all required Azure DevOps configuration is present.
     *
     * @return true if configuration is valid, false otherwise
     */
    public boolean isConfigurationValid() {
        if (azureDevOpsConfiguration.getPersonalAccessToken() == null || 
            azureDevOpsConfiguration.getPersonalAccessToken().isEmpty()) {
            logger.warn("Azure DevOps Personal Access Token is not configured");
            return false;
        }
        if (azureDevOpsConfiguration.getOrganizationUrl() == null || 
            azureDevOpsConfiguration.getOrganizationUrl().isEmpty()) {
            logger.warn("Azure DevOps Organization URL is not configured");
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
