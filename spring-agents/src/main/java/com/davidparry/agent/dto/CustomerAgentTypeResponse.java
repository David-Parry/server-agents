package com.davidparry.agent.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for customer-agent type association.
 */
public record CustomerAgentTypeResponse(
    UUID id,
    UUID customerId,
    String customerName,
    String agentType,
    String agentName,
    boolean enabled,
    Long customTokenLimit,
    Integer priority,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
