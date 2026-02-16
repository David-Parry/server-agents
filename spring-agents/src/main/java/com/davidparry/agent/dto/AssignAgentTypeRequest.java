package com.davidparry.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for assigning an agent type to a customer.
 */
public record AssignAgentTypeRequest(
    @NotBlank(message = "agentType is required")
    String agentType,
    Long customTokenLimit,
    Integer priority
) {}
