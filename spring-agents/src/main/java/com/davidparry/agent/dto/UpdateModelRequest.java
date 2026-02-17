package com.davidparry.agent.dto;

import java.math.BigDecimal;

/**
 * Request DTO for updating a model.
 */
public record UpdateModelRequest(
    String displayName,
    String description,
    Long defaultTokensForNewCustomers,
    Boolean enabled,
    BigDecimal inputTokenPricePerMillion,
    BigDecimal outputTokenPricePerMillion
) {}
