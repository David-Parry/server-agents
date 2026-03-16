package com.davidparry.agent.dto;

/**
 * Request DTO for updating the notification threshold for a specific model allowance.
 */
public record UpdateAllowanceNotificationThresholdRequest(
    /**
     * The notification threshold for this model.
     * NULL = use customer's default threshold.
     */
    Long threshold
) {}
