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
import org.springframework.stereotype.Component;

/**
 * Configuration for Confluence MCP Server.
 * <p>
 * This configuration sets up the ConfluenceService which provides Confluence operations
 * that can be exposed through the MCP protocol.
 * <p>
 * The Spring AI MCP Server starter will automatically discover and expose
 * the ConfluenceService methods as MCP tools when properly annotated.
 * <p>
 * This configuration is conditional and will only be loaded when the property
 * 'mcp.confluence.enabled' is set to true in application.properties.
 */
@Component
public class ConfluenceMcpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ConfluenceMcpConfiguration.class);
    private final ConfluenceConfiguration confluenceConfiguration;

    public ConfluenceMcpConfiguration(ConfluenceConfiguration confluenceConfiguration) {
        this.confluenceConfiguration = confluenceConfiguration;
    }

    public ConfluenceConfiguration getConfluenceConfiguration() {
        return confluenceConfiguration;
    }

    /**
     * Validates that all required Confluence configuration is present.
     *
     * @return true if configuration is valid, false otherwise
     */
    public boolean isConfigurationValid() {
        if (confluenceConfiguration.getSiteUrl() == null || confluenceConfiguration.getSiteUrl().isEmpty()) {
            logger.warn("Confluence site URL is not configured");
            return false;
        }
        if (confluenceConfiguration.getEmail() == null || confluenceConfiguration.getEmail().isEmpty()) {
            logger.warn("Confluence email is not configured");
            return false;
        }
        if (confluenceConfiguration.getApiToken() == null || confluenceConfiguration.getApiToken().isEmpty()) {
            logger.warn("Confluence API token is not configured");
            return false;
        }
        return true;
    }
}
