package com.davidparry.agent.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for audit log entries.
 */
public record AuditLogResponse(
    UUID id,
    String eventType,
    String eventCategory,
    UUID customerId,
    UUID tokenId,
    String secretVersion,
    String description,
    String metadata,
    String actorType,
    String actorId,
    LocalDateTime createdAt
) {}
