package com.davidparry.agent.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for customer information.
 * Token allowances are now per-model, accessible via the modelAllowances list.
 */
public record CustomerResponse(
    UUID id,
    UUID customerId,
    String name,
    boolean enabled,
    String policyTypeName,
    Integer resetDays,
    boolean unlimitedPolicy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<ModelAllowanceResponse> modelAllowances,
    // Notification settings
    Long defaultMinTokenNotificationThreshold,
    String notificationWebhookUrl,
    String notificationEmail
) {}
