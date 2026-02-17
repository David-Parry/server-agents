package com.davidparry.agent.dto;

/**
 * Response DTO for monthly-aggregated token usage data.
 *
 * @param year the year of the aggregation period
 * @param month the month of the aggregation period (1-12)
 * @param model the LLM model identifier
 * @param agentType the agent type
 * @param promptTokens total prompt tokens in this period
 * @param completionTokens total completion tokens in this period
 * @param totalTokens total tokens in this period
 * @param toolCallsCount total tool calls in this period
 * @param callCount number of LLM calls in this period
 */
public record MonthlyTokenUsageResponse(
        int year,
        int month,
        String model,
        String agentType,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long toolCallsCount,
        long callCount
) {
}
