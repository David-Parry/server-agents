package com.davidparry.agent.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for audit statistics.
 */
public record AuditStatisticsResponse(
    Map<String, Long> eventCountsByType,
    long totalEvents,
    LocalDateTime since
) {}
