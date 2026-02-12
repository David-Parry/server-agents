package com.davidparry.agent.dto;

/**
 * Request DTO for updating a model.
 */
public record UpdateModelRequest(
    String displayName,
    String description,
    Long defaultTokensForNewCustomers,
    Boolean enabled
) {}
