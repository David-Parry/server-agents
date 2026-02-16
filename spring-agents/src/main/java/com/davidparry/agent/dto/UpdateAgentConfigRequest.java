package com.davidparry.agent.dto;

import java.math.BigDecimal;

/**
 * Request DTO for updating an agent configuration.
 */
public record UpdateAgentConfigRequest(
    String name,
    String description,
    String systemPrompt,
    String model,
    Boolean enabled,
    Integer maxTokens,
    BigDecimal temperature,
    Integer timeoutSeconds,
    Integer retryAttempts,
    Integer retryDelayMs
) {}
