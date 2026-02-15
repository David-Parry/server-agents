package com.davidparry.agent.dto;

/**
 * Request DTO for setting allowances on all models for a customer.
 *
 * @param allowedTokens NULL = unlimited (if unlimited is false), 0 = no access, > 0 = specific limit
 * @param unlimited If true, ignores allowedTokens and sets unlimited for all models
 */
public record BulkSetAllowanceRequest(
    Long allowedTokens,
    Boolean unlimited
) {}
