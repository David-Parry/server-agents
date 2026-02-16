package com.davidparry.agent.dto;

import java.time.LocalDateTime;

/**
 * Request DTO for cleaning up old audit logs.
 */
public record AuditCleanupRequest(
    LocalDateTime before
) {}
