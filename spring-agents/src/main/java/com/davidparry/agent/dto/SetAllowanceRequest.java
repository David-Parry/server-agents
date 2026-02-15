package com.davidparry.agent.dto;

/**
 * Request DTO for setting a model allowance.
 *
 * @param allowedTokens NULL = unlimited, 0 = no access, > 0 = specific limit
 * @param policyTypeName Optional policy type name to assign to this allowance (e.g., MONTHLY, YEARLY, UNLIMITED)
 */
public record SetAllowanceRequest(
    Long allowedTokens,
    String policyTypeName
) {}
