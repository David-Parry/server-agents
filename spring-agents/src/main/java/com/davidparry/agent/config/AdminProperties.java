package com.davidparry.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the Admin API.
 * The admin API uses a separate token from customer JWT authentication.
 */
@ConfigurationProperties(prefix = "agent.admin")
public record AdminProperties(
    String apiToken,
    @DefaultValue("true") boolean enabled
) {
    public AdminProperties {
        if (enabled && (apiToken == null || apiToken.isBlank())) {
            throw new IllegalStateException("Admin API token must be configured when admin API is enabled. " +
                "Set 'agent.admin.api-token' or disable admin API with 'agent.admin.enabled=false'");
        }
    }
}
