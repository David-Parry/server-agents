package com.davidparry.agent.dto;

/**
 * Request DTO for creating a new LLM model.
 *
 * @param model The model identifier (e.g., "claude-sonnet-4-5")
 * @param provider The provider name (e.g., "anthropic", "openai")
 * @param displayName Human-readable display name
 * @param description Description of the model
 * @param defaultTokensForNewCustomers Default token allowance when linking to new customers (0 = no access)
 */
public record CreateModelRequest(
    String model,
    String provider,
    String displayName,
    String description,
    Long defaultTokensForNewCustomers
) {}
