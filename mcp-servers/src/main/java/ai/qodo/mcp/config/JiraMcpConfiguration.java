/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.config;

import ai.qodo.mcp.service.JiraService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration for Jira MCP Server.
 * <p>
 * This configuration sets up the JiraService which provides Jira operations
 * that can be exposed through the MCP protocol.
 * <p>
 * The Spring AI MCP Server starter will automatically discover and expose
 * the JiraService methods as MCP tools when properly annotated.
 * <p>
 * This configuration is conditional and will only be loaded when the property
 * 'mcp.jira.enabled' is set to true in application.properties.
 */

@Component
public class JiraMcpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(JiraMcpConfiguration.class);
    private final JiraConfiguration jiraConfiguration;


    public JiraMcpConfiguration(JiraConfiguration jiraConfiguration) {
        this.jiraConfiguration = jiraConfiguration;
    }

    public JiraConfiguration getJiraConfiguration() {
        return jiraConfiguration;
    }

    /**
     * Validates that all required Jira configuration is present.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public boolean isConfigurationValid() {
        if (jiraConfiguration.getSiteUrl() == null || jiraConfiguration.getSiteUrl().isEmpty()) {
            logger.warn("Jira site URL is not configured");
            return false;
        }
        if (jiraConfiguration.getEmail() == null || jiraConfiguration.getEmail().isEmpty()) {
            logger.warn("Jira email is not configured");
            return false;
        }
        if (jiraConfiguration.getApiToken() == null || jiraConfiguration.getApiToken().isEmpty()) {
            logger.warn("Jira API token is not configured");
            return false;
        }
        return true;
    }

}