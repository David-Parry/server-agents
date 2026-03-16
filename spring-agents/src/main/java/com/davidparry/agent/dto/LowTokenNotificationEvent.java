package com.davidparry.agent.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representing a low-token notification event.
 * Used for webhook payloads and internal event handling.
 */
public record LowTokenNotificationEvent(
    UUID customerId,
    String customerName,
    String model,
    String modelDisplayName,
    Long remainingTokens,
    Long allowedTokens,
    Long notificationThreshold,
    Integer daysUntilReset,
    String policyTypeName,
    LocalDateTime timestamp
) {

    /**
     * Creates a notification event from allowance data.
     */
    public static LowTokenNotificationEvent from(
            UUID customerId,
            String customerName,
            String model,
            String modelDisplayName,
            Long remainingTokens,
            Long allowedTokens,
            Long notificationThreshold,
            Integer daysUntilReset,
            String policyTypeName) {
        return new LowTokenNotificationEvent(
            customerId,
            customerName,
            model,
            modelDisplayName,
            remainingTokens,
            allowedTokens,
            notificationThreshold,
            daysUntilReset,
            policyTypeName,
            LocalDateTime.now()
        );
    }

    /**
     * Gets a human-readable message for this notification.
     */
    public String getMessage() {
        String resetInfo = daysUntilReset != null
            ? String.format(" Tokens will reset in %d days.", daysUntilReset)
            : "";

        return String.format(
            "Low token warning for %s on model %s: %d tokens remaining (threshold: %d, limit: %d).%s",
            customerName,
            modelDisplayName != null ? modelDisplayName : model,
            remainingTokens,
            notificationThreshold,
            allowedTokens,
            resetInfo
        );
    }
}
