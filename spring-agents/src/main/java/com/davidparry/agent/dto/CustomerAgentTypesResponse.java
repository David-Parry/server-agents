package com.davidparry.agent.dto;

import java.util.List;

/**
 * Response DTO for agent types assigned to a customer.
 */
public record CustomerAgentTypesResponse(
    java.util.UUID customerId,
    String customerName,
    int totalAssigned,
    int enabledCount,
    List<CustomerAgentTypeResponse> agentTypes
) {}
