package com.davidparry.agent.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for admin customer summary information.
 */
public record AdminCustomerSummaryResponse(
    UUID id,
    UUID customerId,
    String name,
    boolean enabled,
    int activeTokenCount,
    int modelAllowanceCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
