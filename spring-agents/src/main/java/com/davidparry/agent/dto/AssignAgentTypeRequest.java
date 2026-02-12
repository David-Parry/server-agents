package com.davidparry.agent.dto;

/**
 * Request DTO for assigning an agent type to a customer.
 */
public record AssignAgentTypeRequest(
    String agentType,
    Long customTokenLimit,
    Integer priority
) {}
