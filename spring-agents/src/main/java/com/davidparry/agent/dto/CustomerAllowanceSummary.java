package com.davidparry.agent.dto;

import java.util.UUID;

/**
 * Summary DTO for a customer's allowance on a specific model.
 */
public record CustomerAllowanceSummary(
    UUID customerId,
    String customerName,
    boolean customerEnabled,
    Long allowedTokens,
    Long tokensUsed,
    Long remainingTokens,
    boolean unlimited,
    String policyTypeName,
    boolean allowanceEnabled
) {}
