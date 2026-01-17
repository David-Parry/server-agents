package com.davidparry.agent.dto;

/**
 * Response DTO for usage reset operations.
 */
public record UsageResetResponse(
    String model,
    int allowancesReset
) {}
