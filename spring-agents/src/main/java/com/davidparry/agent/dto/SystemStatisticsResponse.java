package com.davidparry.agent.dto;

/**
 * Response DTO for system-wide statistics.
 */
public record SystemStatisticsResponse(
    long totalCustomers,
    long enabledCustomers,
    long totalModels,
    long enabledModels,
    long totalPolicyTypes,
    long totalAllowances,
    long totalAuditLogs
) {}
