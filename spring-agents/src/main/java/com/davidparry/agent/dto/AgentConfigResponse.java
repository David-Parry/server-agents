package com.davidparry.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for agent configuration details.
 */
public record AgentConfigResponse(
    UUID id,
    String agentType,
    String name,
    String description,
    String model,
    String modelDisplayName,
    boolean enabled,
    int version,
    ExecutionConfigResponse executionConfig,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    
    /**
     * Nested response for execution configuration.
     */
    public record ExecutionConfigResponse(
        Integer maxTokens,
        BigDecimal temperature,
        Integer timeoutSeconds,
        Integer retryAttempts,
        Integer retryDelayMs
    ) {}
}
