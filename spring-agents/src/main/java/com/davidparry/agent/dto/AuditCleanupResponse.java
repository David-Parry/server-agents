package com.davidparry.agent.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for audit cleanup operation.
 */
public record AuditCleanupResponse(
    int deletedCount,
    LocalDateTime cutoffDate
) {}
