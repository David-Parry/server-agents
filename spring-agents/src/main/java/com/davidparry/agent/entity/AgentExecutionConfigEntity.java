package com.davidparry.agent.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing execution configuration for an agent type.
 * Contains parameters like max tokens, temperature, timeouts, and retry settings.
 */
@Entity
@Table(name = "agent_execution_config")
public class AgentExecutionConfigEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_config_id", nullable = false, unique = true)
    private AgentConfigEntity agentConfig;
    
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
