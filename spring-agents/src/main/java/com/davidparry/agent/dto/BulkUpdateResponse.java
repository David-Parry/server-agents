package com.davidparry.agent.dto;

/**
 * Response DTO for bulk update operations.
 */
public record BulkUpdateResponse(
    String model,
    int customersUpdated,
    Long allowedTokens,
    String policyTypeName
) {}
