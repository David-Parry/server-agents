package com.davidparry.agent.dto;

import java.util.List;

/**
 * Request DTO for bulk assigning agent types to a customer.
 */
public record BulkAssignAgentTypesRequest(
    List<String> agentTypes,
    Long defaultCustomTokenLimit,
    Integer defaultPriority
) {}
