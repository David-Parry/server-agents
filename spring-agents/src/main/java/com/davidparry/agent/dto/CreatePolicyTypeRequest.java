package com.davidparry.agent.dto;

/**
 * Request DTO for creating a new policy type.
 */
public record CreatePolicyTypeRequest(
    String name,
    String description,
    Integer resetDays  // null for unlimited
) {}
