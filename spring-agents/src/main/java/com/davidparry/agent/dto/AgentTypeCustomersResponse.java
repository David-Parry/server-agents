package com.davidparry.agent.dto;

import java.util.List;

/**
 * Response DTO for customers assigned to an agent type.
 */
public record AgentTypeCustomersResponse(
    String agentType,
    String agentName,
    int totalCustomers,
    int enabledCount,
    List<CustomerAgentTypeResponse> customers
) {}
