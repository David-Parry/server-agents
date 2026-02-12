package com.davidparry.agent.entity;

import com.davidparry.agent.protocol.dto.AgentType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing the many-to-many relationship between customers and agent types.
 * This allows administrators to control which agent types each customer can access.
 */
@Entity
@Table(name = "customer_agent_type",
       uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "agent_type"}))
public class CustomerAgentTypeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;
    
    @Column(name = "agent_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AgentType agentType;
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    /**
     * Optional custom token limit for this customer-agent combination.
     * If null, uses the customer's model allowance limits.
     */
    @Column(name = "custom_token_limit")
    private Long customTokenLimit;
    
    /**
     * Optional priority level for this customer's access to this agent type.
     * Higher values indicate higher priority (useful for rate limiting).
     */
    @Column(name = "priority")
    private Integer priority = 0;
    
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
    
    public CustomerEntity getCustomer() {
        return customer;
    }
    
    public void setCustomer(CustomerEntity customer) {
        this.customer = customer;
    }
    
    public AgentType getAgentType() {
        return agentType;
    }
    
    public void setAgentType(AgentType agentType) {
        this.agentType = agentType;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Long getCustomTokenLimit() {
        return customTokenLimit;
    }
    
    public void setCustomTokenLimit(Long customTokenLimit) {
        this.customTokenLimit = customTokenLimit;
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
