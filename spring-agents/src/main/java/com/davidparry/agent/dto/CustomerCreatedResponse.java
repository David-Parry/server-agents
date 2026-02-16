package com.davidparry.agent.dto;

import java.util.UUID;

/**
 * Response DTO for customer creation.
 */
public record CustomerCreatedResponse(
    UUID id,
    UUID customerId,
    String name,
    String policyTypeName,
    Integer resetDays,
    boolean unlimited,
    Long defaultAllowance,
    String apiToken,
    String warning
) {}
