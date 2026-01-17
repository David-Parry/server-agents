package com.davidparry.agent.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a customer who can authenticate with JWT tokens.
 * The customer_id (UUID) is used as the salt for token hashing.
 * 
 * Token allowances are managed per-model in CustomerModelAllowanceEntity.
 * Each allowance has its own policy type that defines the reset period.
 */
@Entity
@Table(name = "customer")
public class CustomerEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * The customer's unique identifier (GUID).
     * This value is ALSO used as the SALT for token hashing.
     */
    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;
    
    @Column(nullable = false, length = 200)
    private String name;
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    /**
     * Global default notification threshold for this customer.
     * Used when model-specific threshold is NULL.
     * Default: 30,000 tokens
     */
    @Column(name = "default_min_token_notification_threshold")
    private Long defaultMinTokenNotificationThreshold = 30000L;
    
    /**
     * Webhook URL for low-token notifications (optional).
     * If set, a POST request will be sent to this URL when tokens fall below threshold.
     */
    @Column(name = "notification_webhook_url", length = 500)
    private String notificationWebhookUrl;
    
    /**
     * Email address for low-token notifications (optional).
     * If set, an email will be sent when tokens fall below threshold.
     */
    @Column(name = "notification_email", length = 255)
    private String notificationEmail;
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, 
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<CustomerTokenEntity> tokens = new ArrayList<>();
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, 
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<CustomerModelAllowanceEntity> modelAllowances = new ArrayList<>();
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (customerId == null) {
            customerId = UUID.randomUUID();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Returns the customer_id as bytes for use as salt in token hashing.
     * UUID is 128 bits (16 bytes), which provides sufficient entropy for salt.
     */
    public byte[] getCustomerIdAsSalt() {
        return uuidToBytes(customerId);
    }
    
    private static byte[] uuidToBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >>> (8 * (7 - i)));
            bytes[8 + i] = (byte) (lsb >>> (8 * (7 - i)));
        }
        return bytes;
    }
    
    // Getters and setters
    public UUID getId() { 
        return id; 
    }
    
    public void setId(UUID id) { 
        this.id = id; 
    }
    
    public UUID getCustomerId() { 
        return customerId; 
    }
    
    public void setCustomerId(UUID customerId) { 
        this.customerId = customerId; 
    }
    
    public String getName() { 
        return name; 
    }
    
    public void setName(String name) { 
        this.name = name; 
    }
    
    public boolean isEnabled() { 
        return enabled; 
    }
    
    public void setEnabled(boolean enabled) { 
        this.enabled = enabled; 
    }
    
    public List<CustomerTokenEntity> getTokens() { 
        return tokens; 
    }
    
    public void setTokens(List<CustomerTokenEntity> tokens) { 
        this.tokens = tokens; 
    }
    
    public List<CustomerModelAllowanceEntity> getModelAllowances() { 
        return modelAllowances; 
    }
    
    public void setModelAllowances(List<CustomerModelAllowanceEntity> modelAllowances) { 
        this.modelAllowances = modelAllowances; 
    }
    
    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
    
    public LocalDateTime getUpdatedAt() { 
        return updatedAt; 
    }
    
    public Long getDefaultMinTokenNotificationThreshold() {
        return defaultMinTokenNotificationThreshold;
    }
    
    public void setDefaultMinTokenNotificationThreshold(Long defaultMinTokenNotificationThreshold) {
        this.defaultMinTokenNotificationThreshold = defaultMinTokenNotificationThreshold;
    }
    
    public String getNotificationWebhookUrl() {
        return notificationWebhookUrl;
    }
    
    public void setNotificationWebhookUrl(String notificationWebhookUrl) {
        this.notificationWebhookUrl = notificationWebhookUrl;
    }
    
    public String getNotificationEmail() {
        return notificationEmail;
    }
    
    public void setNotificationEmail(String notificationEmail) {
        this.notificationEmail = notificationEmail;
    }
    
    /**
     * Gets the effective notification threshold (defaults to 30,000 if not set).
     */
    public Long getEffectiveNotificationThreshold() {
        return defaultMinTokenNotificationThreshold != null ? defaultMinTokenNotificationThreshold : 30000L;
    }
    
    /**
     * Checks if this customer has any notification channels configured.
     */
    public boolean hasNotificationChannels() {
        return (notificationWebhookUrl != null && !notificationWebhookUrl.isBlank()) ||
               (notificationEmail != null && !notificationEmail.isBlank());
    }
}
