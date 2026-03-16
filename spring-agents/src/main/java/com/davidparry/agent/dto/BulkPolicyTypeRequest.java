package com.davidparry.agent.dto;

/**
 * Request DTO for bulk policy type updates on all allowances.
 *
 * @param policyTypeName The policy type name to assign to all allowances (e.g., MONTHLY, YEARLY, UNLIMITED)
 * @param allowedTokens Optional: if provided, also sets the token allowance for all models
 */
public record BulkPolicyTypeRequest(
    String policyTypeName,
    Long allowedTokens
) {}
