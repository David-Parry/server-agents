package com.davidparry.agent.dto;

/**
 * Request DTO for updating a policy type.
 */
public record UpdatePolicyTypeRequest(
    String description,
    Integer resetDays,
    Boolean enabled
) {}
