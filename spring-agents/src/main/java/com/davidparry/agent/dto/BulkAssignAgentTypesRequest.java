package com.davidparry.agent.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for bulk assigning agent types to a customer.
 */
public record BulkAssignAgentTypesRequest(
    @NotEmpty(message = "agentTypes list cannot be null or empty")
    List<@NotNull(message = "agentTypes list cannot contain null values") String> agentTypes,
    Long defaultCustomTokenLimit,
    Integer defaultPriority
) {}
