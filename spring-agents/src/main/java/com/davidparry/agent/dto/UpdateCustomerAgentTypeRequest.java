package com.davidparry.agent.dto;

/**
 * Request DTO for updating a customer-agent type association.
 */
public record UpdateCustomerAgentTypeRequest(
    Boolean enabled,
    Long customTokenLimit,
    Integer priority
) {}
