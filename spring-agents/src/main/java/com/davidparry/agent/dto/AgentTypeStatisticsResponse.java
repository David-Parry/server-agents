package com.davidparry.agent.dto;

import java.util.Map;

/**
 * Response DTO for agent type statistics.
 */
public record AgentTypeStatisticsResponse(
    Map<String, Long> customerCountByAgentType,
    long totalAssignments,
    int totalAgentTypes
) {}
