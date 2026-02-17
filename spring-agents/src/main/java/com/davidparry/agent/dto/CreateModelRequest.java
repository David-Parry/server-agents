package com.davidparry.agent.dto;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new LLM model.
 *
 * @param model The model identifier (e.g., "claude-sonnet-4-5")
 * @param provider The provider name (e.g., "anthropic", "openai")
 * @param displayName Human-readable display name
 * @param description Description of the model
 * @param defaultTokensForNewCustomers Default token allowance when linking to new customers (0 = no access)
 * @param inputTokenPricePerMillion Price per million input tokens (e.g., 3.00 for $3/M)
 * @param outputTokenPricePerMillion Price per million output tokens (e.g., 15.00 for $15/M)
 */
public record CreateModelRequest(
    String model,
    String provider,
    String displayName,
    String description,
    Long defaultTokensForNewCustomers,
    BigDecimal inputTokenPricePerMillion,
    BigDecimal outputTokenPricePerMillion
) {}
