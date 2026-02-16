package com.davidparry.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing a customer's token allowance for a specific model.
 * Each customer has an allowance entry for EVERY enabled model.
 *
 * Allowance values:
 * - NULL = unlimited tokens
 * - 0 = no access (limit reached immediately)
 * - > 0 = specific token limit
 */
@Entity
@Table(name = "customer_model_allowance")
public class CustomerModelAllowanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model", nullable = false)
    private LlmModelEntity llmModel;

    /**
     * The policy type for this specific model allowance.
     * Defines the reset period for this allowance.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_type_id")
    private PolicyTypeEntity policyType;

    /**
     * Maximum tokens allowed for this customer on this model.
     * NULL = unlimited
     * 0 = no access (default for new linkages)
     * > 0 = specific limit
     */
    @Column(name = "allowed_tokens")
    private Long allowedTokens = 0L;

    @Column(name = "tokens_used")
    private Long tokensUsed = 0L;

    @Column(name = "tokens_reset_at")
    private LocalDateTime tokensResetAt;

    /**
     * Model-specific notification threshold.
     * NULL = use customer's default threshold.
     * Default: 30,000 tokens
     */
    @Column(name = "min_token_notification_threshold")
    private Long minTokenNotificationThreshold = 30000L;

    /**
     * Tracks when the last low-token notification was sent.
     * Used to ensure only one notification per reset period.
     * Reset when usage is reset.
     */
    @Column(name = "low_token_notification_sent_at")
    private LocalDateTime lowTokenNotificationSentAt;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (tokensUsed == null) {
            tokensUsed = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CustomerEntity getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerEntity customer) {
        this.customer = customer;
    }

    public LlmModelEntity getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(LlmModelEntity llmModel) {
        this.llmModel = llmModel;
    }

    public PolicyTypeEntity getPolicyType() {
        return policyType;
    }

    public void setPolicyType(PolicyTypeEntity policyType) {
        this.policyType = policyType;
    }

    public Long getAllowedTokens() {
        return allowedTokens;
    }

    public void setAllowedTokens(Long allowedTokens) {
        this.allowedTokens = allowedTokens;
    }

    public Long getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Long tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public LocalDateTime getTokensResetAt() {
        return tokensResetAt;
    }

    public void setTokensResetAt(LocalDateTime tokensResetAt) {
        this.tokensResetAt = tokensResetAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Business logic methods

    /**
     * Checks if this allowance is unlimited (NULL allowed_tokens).
     */
    public boolean isUnlimited() {
        return allowedTokens == null;
    }

    /**
     * Checks if the token limit has been exceeded.
     * For allowedTokens = 0, this returns true immediately (no access).
     */
    public boolean hasExceededLimit() {
        if (isUnlimited()) {
            return false;
        }
        long used = tokensUsed != null ? tokensUsed : 0L;
        return used >= allowedTokens;
    }

    /**
     * Checks if this allowance grants any access.
     * Returns false if allowedTokens = 0 (no access granted).
     */
    public boolean hasAccess() {
        return isUnlimited() || (allowedTokens != null && allowedTokens > 0);
    }

    /**
     * Gets remaining tokens. Returns null for unlimited.
     */
    public Long getRemainingTokens() {
        if (isUnlimited()) {
            return null;
        }
        long used = tokensUsed != null ? tokensUsed : 0L;
        return Math.max(0, allowedTokens - used);
    }

    /**
     * Increments the token usage counter.
     */
    public void incrementTokensUsed(long amount) {
        if (tokensUsed == null) {
            tokensUsed = 0L;
        }
        tokensUsed += amount;
    }

    /**
     * Resets the usage counter.
     */
    public void resetUsage() {
        tokensUsed = 0L;
        tokensResetAt = LocalDateTime.now();
    }

    /**
     * Checks if usage should be reset based on policy reset days.
     */
    public boolean shouldResetUsage(Integer resetDays) {
        if (resetDays == null) {
            return false;
        }
        if (tokensResetAt == null) {
            return true;
        }
        return tokensResetAt.plusDays(resetDays).isBefore(LocalDateTime.now());
    }

    /**
     * Gets the reset days from the allowance's policy type.
     * Returns null if no policy type is set or if the policy is unlimited.
     */
    public Integer getResetDays() {
        return policyType != null ? policyType.getResetDays() : null;
    }

    /**
     * Checks if usage should be reset based on the allowance's policy type.
     */
    public boolean shouldResetUsageFromPolicy() {
        Integer resetDays = getResetDays();
        return shouldResetUsage(resetDays);
    }

    /**
     * Checks if this allowance has an unlimited policy (no reset period).
     */
    public boolean hasUnlimitedPolicy() {
        return policyType == null || policyType.isUnlimited();
    }

    /**
     * Gets the policy type name, or null if not set.
     */
    public String getPolicyTypeName() {
        return policyType != null ? policyType.getName() : null;
    }

    // Notification threshold methods

    public Long getMinTokenNotificationThreshold() {
        return minTokenNotificationThreshold;
    }

    public void setMinTokenNotificationThreshold(Long minTokenNotificationThreshold) {
        this.minTokenNotificationThreshold = minTokenNotificationThreshold;
    }

    public LocalDateTime getLowTokenNotificationSentAt() {
        return lowTokenNotificationSentAt;
    }

    public void setLowTokenNotificationSentAt(LocalDateTime lowTokenNotificationSentAt) {
        this.lowTokenNotificationSentAt = lowTokenNotificationSentAt;
    }

    /**
     * Gets the effective notification threshold for this allowance.
     * Uses model-specific threshold if set, otherwise falls back to customer's default.
     *
     * @return the effective threshold, or 30,000 as ultimate default
     */
    public Long getEffectiveNotificationThreshold() {
        if (minTokenNotificationThreshold != null) {
            return minTokenNotificationThreshold;
        }
        if (customer != null) {
            return customer.getEffectiveNotificationThreshold();
        }
        return 30000L; // Ultimate default
    }

    /**
     * Checks if remaining tokens are below the notification threshold.
     * Returns false for unlimited allowances.
     *
     * @return true if remaining tokens are below threshold
     */
    public boolean isBelowNotificationThreshold() {
        if (isUnlimited()) {
            return false;
        }
        Long remaining = getRemainingTokens();
        Long threshold = getEffectiveNotificationThreshold();
        return remaining != null && remaining < threshold;
    }

    /**
     * Checks if a notification should be sent based on:
     * 1. Tokens are below threshold
     * 2. No notification has been sent in the current reset period
     *
     * @return true if a notification should be sent
     */
    public boolean shouldSendNotification() {
        if (!isBelowNotificationThreshold()) {
            return false;
        }

        // If no notification has ever been sent, send one
        if (lowTokenNotificationSentAt == null) {
            return true;
        }

        // Check if we're in a new reset period
        Integer resetDays = getResetDays();
        if (resetDays == null) {
            // Unlimited policy - only send once ever
            return false;
        }

        // If tokens were reset after the last notification, we can send again
        if (tokensResetAt != null && tokensResetAt.isAfter(lowTokenNotificationSentAt)) {
            return true;
        }

        return false;
    }

    /**
     * Marks that a notification has been sent.
     */
    public void markNotificationSent() {
        this.lowTokenNotificationSentAt = LocalDateTime.now();
    }

    /**
     * Resets the usage counter and clears the notification sent flag.
     * This allows a new notification to be sent in the new period.
     */
    public void resetUsageAndNotification() {
        tokensUsed = 0L;
        tokensResetAt = LocalDateTime.now();
        // Note: We don't clear lowTokenNotificationSentAt here
        // The shouldSendNotification() method checks if tokensResetAt > lowTokenNotificationSentAt
    }
}
