package com.davidparry.agent.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for LLM model information.
 */
public record ModelResponse(
    String model,
    String provider,
    String displayName,
    String description,
    Long defaultTokensForNewCustomers,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
