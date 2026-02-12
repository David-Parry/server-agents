package com.davidparry.agent.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for model allowance information.
 */
public record ModelAllowanceResponse(
    UUID id,
    String model,
    String modelDisplayName,
    String provider,
    Long allowedTokens,
    Long tokensUsed,
    Long remainingTokens,
    boolean unlimited,
    Integer daysUntilReset,
    LocalDateTime tokensResetAt,
    String policyTypeName,
    Integer resetDays,
    // Notification fields
    Long minTokenNotificationThreshold,
    Long effectiveNotificationThreshold,
    boolean belowNotificationThreshold,
    LocalDateTime lowTokenNotificationSentAt
) {}
