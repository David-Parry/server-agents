package com.davidparry.agent.dto;

import java.util.UUID;

/**
 * Response DTO for customer disable operation.
 */
public record CustomerDisableResponse(
    UUID customerId,
    boolean disabled,
    int tokensRevoked,
    int allowancesDisabled
) {}
