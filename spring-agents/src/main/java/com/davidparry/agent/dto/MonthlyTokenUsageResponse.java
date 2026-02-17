package com.davidparry.agent.dto;

import java.math.BigDecimal;

/**
 * Response DTO for monthly-aggregated token usage data with cost estimates.
 *
 * @param year the year of the aggregation period
 * @param month the month of the aggregation period (1-12)
 * @param model the LLM model identifier
 * @param agentType the agent type
 * @param inputTokens total input tokens in this period
 * @param outputTokens total output tokens in this period
 * @param totalTokens total tokens in this period
 * @param toolCallsCount total tool calls in this period
 * @param callCount number of LLM calls in this period
 * @param inputTokenPricePerMillion price per million input tokens (USD)
 * @param outputTokenPricePerMillion price per million output tokens (USD)
 * @param estimatedInputCost estimated cost for input tokens (USD)
 * @param estimatedOutputCost estimated cost for output tokens (USD)
 * @param estimatedTotalCost estimated total cost (USD)
 */
public record MonthlyTokenUsageResponse(
        int year,
        int month,
        String model,
        String agentType,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long toolCallsCount,
        long callCount,
        BigDecimal inputTokenPricePerMillion,
        BigDecimal outputTokenPricePerMillion,
        BigDecimal estimatedInputCost,
        BigDecimal estimatedOutputCost,
        BigDecimal estimatedTotalCost
) {
}
