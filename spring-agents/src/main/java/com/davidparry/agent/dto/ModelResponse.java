package com.davidparry.agent.dto;

import java.math.BigDecimal;
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
    BigDecimal inputTokenPricePerMillion,
    BigDecimal outputTokenPricePerMillion,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
