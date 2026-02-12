package com.davidparry.agent.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing a policy type that defines token reset periods.
 * Policy types define the reset schedule (e.g., monthly, yearly) only.
 * Token allowances are now managed per-customer per-model in CustomerModelAllowanceEntity.
 */
@Entity
@Table(name = "policy_type")
public class PolicyTypeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * The policy type name (e.g., UNLIMITED, MONTHLY, YEARLY).
     */
    @Column(nullable = false, length = 50, unique = true)
    private String name;
    
    @Column(length = 500)
    private String description;
    
    /**
     * Number of days until token usage resets.
     * NULL for unlimited policies (no reset).
     * 30 for monthly, 365 for yearly, etc.
     */
    @Column(name = "reset_days")
    private Integer resetDays;
    
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
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Integer getResetDays() {
        return resetDays;
    }
    
    public void setResetDays(Integer resetDays) {
        this.resetDays = resetDays;
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
    
    /**
     * Checks if this policy type has unlimited tokens (no reset, no limits).
     */
    public boolean isUnlimited() {
        return resetDays == null;
    }
    
    /**
     * Checks if this policy type requires periodic token resets.
     */
    public boolean hasResetPeriod() {
        return resetDays != null && resetDays > 0;
    }
}
