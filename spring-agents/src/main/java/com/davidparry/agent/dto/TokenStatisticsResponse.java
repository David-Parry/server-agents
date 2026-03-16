package com.davidparry.agent.dto;

/**
 * Response DTO for token statistics by secret version.
 */
public record TokenStatisticsResponse(
    String secretVersion,
    long activeTokenCount
) {}
