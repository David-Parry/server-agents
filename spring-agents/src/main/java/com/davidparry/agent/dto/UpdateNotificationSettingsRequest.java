package com.davidparry.agent.dto;

/**
 * Request DTO for updating customer notification settings.
 */
public record UpdateNotificationSettingsRequest(
    /**
     * Default notification threshold for all models (NULL to keep current).
     */
    Long defaultNotificationThreshold,

    /**
     * Webhook URL for notifications (NULL to keep current, empty string to clear).
     */
    String webhookUrl,

    /**
     * Email for notifications (NULL to keep current, empty string to clear).
     */
    String email
) {}
