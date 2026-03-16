/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mcp.azure-devops")
public class AzureDevOpsConfiguration {
    
    /**
     * Personal Access Token (PAT) for Azure DevOps authentication.
     * Required scopes: Code (Read & Write), Pull Request Threads (Read & Write)
     */
    private String personalAccessToken;
    
    /**
     * Azure DevOps organization URL (e.g., https://dev.azure.com/myorg)
     */
    private String organizationUrl;
    
    /**
     * Default project name to use when not specified in tool calls
     */
    private String defaultProject;
    
    /**
     * Whether Azure DevOps MCP tools are enabled
     */
    private boolean enabled = true;

    public String getPersonalAccessToken() {
        return personalAccessToken;
    }

    public void setPersonalAccessToken(String personalAccessToken) {
        this.personalAccessToken = personalAccessToken;
    }

    public String getOrganizationUrl() {
        return organizationUrl;
    }

    public void setOrganizationUrl(String organizationUrl) {
        this.organizationUrl = organizationUrl;
    }

    public String getDefaultProject() {
        return defaultProject;
    }

    public void setDefaultProject(String defaultProject) {
        this.defaultProject = defaultProject;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
