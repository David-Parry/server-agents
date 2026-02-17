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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing execution configuration for an agent type per customer.
 * Contains parameters like max tokens, temperature, timeouts, and retry settings.
 *
 * This entity connects AgentConfigEntity (the agent definition) with CustomerEntity,
 * allowing customer-specific execution parameters. When customer_id is NULL, this
 * represents the default execution config for the agent type.
 *
 * <p>Uniqueness constraints:
 * <ul>
 *   <li>For customer-specific configs: (agent_config_id, customer_id) must be unique</li>
 *   <li>For default configs: only one row per agent_config_id where customer_id IS NULL
 *       (enforced via partial unique index idx_agent_exec_config_unique_default)</li>
 * </ul>
 */
@Entity
@Table(name = "agent_execution_config",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_agent_execution_config_agent_customer",
           columnNames = {"agent_config_id", "customer_id"}
       ))
public class AgentExecutionConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_config_id", nullable = false)
    private AgentConfigEntity agentConfig;

    /**
     * The customer this execution config belongs to.
     * When NULL, this represents the default execution config for the agent type.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    /**
     * Computed column for enforcing unique default configs.
     * Automatically set by the database - equals agent_config_id when customer is NULL.
     */
    @Column(name = "default_config_key", insertable = false, updatable = false)
    private UUID defaultConfigKey;

    @Column(name = "max_tokens")
    private Integer maxTokens = 4096;

    @Column(precision = 3, scale = 2)
    private BigDecimal temperature = new BigDecimal("0.70");

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds = 300;

    @Column(name = "retry_attempts")
    private Integer retryAttempts = 2;

    @Column(name = "retry_delay_ms")
    private Integer retryDelayMs = 1000;

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

    public AgentConfigEntity getAgentConfig() {
        return agentConfig;
    }

    public void setAgentConfig(AgentConfigEntity agentConfig) {
        this.agentConfig = agentConfig;
    }

    public CustomerEntity getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerEntity customer) {
        this.customer = customer;
    }

    /**
     * Checks if this is a default (global) execution config (no customer assigned).
     */
    public boolean isDefaultConfig() {
        return customer == null;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(Integer retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public Integer getRetryDelayMs() {
        return retryDelayMs;
    }

    public void setRetryDelayMs(Integer retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
