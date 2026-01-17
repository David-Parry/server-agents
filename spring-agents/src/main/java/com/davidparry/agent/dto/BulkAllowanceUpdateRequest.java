package com.davidparry.agent.dto;

/**
 * Request DTO for bulk updating allowances for a model.
 */
public record BulkAllowanceUpdateRequest(
    String model,
    Long allowedTokens,  // null for unlimited
    String policyTypeName  // optional
) {}
