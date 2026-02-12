package com.davidparry.agent.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for security audit logging.
 * Tracks token lifecycle events and secret version usage.
 */
@Entity
@Table(name = "security_audit_log")
public class SecurityAuditLogEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;
    
    @Column(name = "event_category", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private EventCategory eventCategory;
    
    @Column(name = "customer_id")
    private UUID customerId;
    
    @Column(name = "token_id")
    private UUID tokenId;
    
    @Column(name = "secret_version", length = 10)
    private String secretVersion;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "actor_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ActorType actorType;
    
    @Column(name = "actor_id", length = 100)
    private String actorId;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // Enums
    public enum EventType {
        TOKEN_CREATED,
        TOKEN_REVOKED,
        TOKEN_EXPIRED,
        TOKEN_VALIDATED,
        TOKEN_VALIDATION_FAILED,
        SECRET_VERSION_CONFIGURED,
        SECRET_VERSION_DEPRECATED,
        USAGE_RECORDED,
        USAGE_RESET,
        LIMIT_EXCEEDED,
        ALLOWANCE_CREATED,
        ALLOWANCE_UPDATED,
        MODEL_ADDED,
        LOW_TOKEN_WARNING,
        // Admin action events
        ADMIN_CUSTOMER_DISABLED,
        ADMIN_CUSTOMER_ENABLED,
        ADMIN_MODEL_DISABLED,
        ADMIN_MODEL_ENABLED,
        ADMIN_MODEL_UPDATED,
        ADMIN_POLICY_TYPE_CREATED,
        ADMIN_POLICY_TYPE_UPDATED,
        ADMIN_POLICY_TYPE_DISABLED,
        ADMIN_ALLOWANCE_BULK_UPDATED,
        ADMIN_USAGE_RESET,
        ADMIN_AUDIT_CLEANUP,
        // Agent type management events
        ADMIN_AGENT_CONFIG_UPDATED,
        ADMIN_AGENT_CONFIG_ENABLED,
        ADMIN_AGENT_CONFIG_DISABLED,
        ADMIN_CUSTOMER_AGENT_TYPE_ASSIGNED,
        ADMIN_CUSTOMER_AGENT_TYPE_UPDATED,
        ADMIN_CUSTOMER_AGENT_TYPE_REMOVED,
        ADMIN_CUSTOMER_AGENT_TYPES_BULK_ASSIGNED
    }
    
    public enum EventCategory {
        TOKEN,
        SECRET,
        AUTHENTICATION,
        USAGE,
        ALLOWANCE,
        MODEL,
        NOTIFICATION,
        ADMIN
    }
    
    public enum ActorType {
        SYSTEM,
        ADMIN,
        CUSTOMER,
        SCHEDULER
    }
    
    // Builder pattern for convenient construction
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final SecurityAuditLogEntity entity = new SecurityAuditLogEntity();
        
        public Builder eventType(EventType eventType) {
            entity.eventType = eventType;
            return this;
        }
        
        public Builder eventCategory(EventCategory eventCategory) {
            entity.eventCategory = eventCategory;
            return this;
        }
        
        public Builder customerId(UUID customerId) {
            entity.customerId = customerId;
            return this;
        }
        
        public Builder tokenId(UUID tokenId) {
            entity.tokenId = tokenId;
            return this;
        }
        
        public Builder secretVersion(String secretVersion) {
            entity.secretVersion = secretVersion;
            return this;
        }
        
        public Builder description(String description) {
            entity.description = description;
            return this;
        }
        
        public Builder metadata(String metadata) {
            entity.metadata = metadata;
            return this;
        }
        
        public Builder actorType(ActorType actorType) {
            entity.actorType = actorType;
            return this;
        }
        
        public Builder actorId(String actorId) {
            entity.actorId = actorId;
            return this;
        }
        
        public SecurityAuditLogEntity build() {
            return entity;
        }
    }
    
    // Getters
    public UUID getId() { 
        return id; 
    }
    
    public EventType getEventType() { 
        return eventType; 
    }
    
    public EventCategory getEventCategory() { 
        return eventCategory; 
    }
    
    public UUID getCustomerId() { 
        return customerId; 
    }
    
    public UUID getTokenId() { 
        return tokenId; 
    }
    
    public String getSecretVersion() { 
        return secretVersion; 
    }
    
    public String getDescription() { 
        return description; 
    }
    
    public String getMetadata() { 
        return metadata; 
    }
    
    public ActorType getActorType() { 
        return actorType; 
    }
    
    public String getActorId() { 
        return actorId; 
    }
    
    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
}
