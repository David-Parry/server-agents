package com.davidparry.agent.dto;

import java.util.List;

/**
 * Response DTO for listing all allowances for a specific model.
 */
public record AllowancesByModelResponse(
    String model,
    String modelDisplayName,
    int totalAllowances,
    List<CustomerAllowanceSummary> allowances
) {}
