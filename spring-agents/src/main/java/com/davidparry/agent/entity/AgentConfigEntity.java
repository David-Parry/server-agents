package com.davidparry.agent.entity;

import com.davidparry.agent.protocol.dto.AgentType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing agent configuration stored in the database.
 * Replaces the in-memory AgentConfigurationProvider.
 */
@Entity
@Table(name = "agent_config")
public class AgentConfigEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "agent_type", nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
    private AgentType agentType;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(length = 500)
    private String description;
    
    @Column(name = "system_prompt", nullable = false, columnDefinition = "CLOB")
    private String systemPrompt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model", nullable = false)
    private LlmModelEntity llmModel;
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    @Column(nullable = false)
    private int version = 1;
    
    @Column(columnDefinition = "CLOB")
    private String metadata;
    
    @OneToMany(mappedBy = "agentConfig", cascade = CascadeType.ALL, 
               fetch = FetchType.LAZY, orphanRemoval = true)
    private java.util.List<AgentExecutionConfigEntity> executionConfigs = new java.util.ArrayList<>();
    
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
    
    public AgentType getAgentType() { 
        return agentType; 
    }
    
    public void setAgentType(AgentType agentType) { 
        this.agentType = agentType; 
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
    
    public String getSystemPrompt() { 
        return systemPrompt; 
    }
    
    public void setSystemPrompt(String systemPrompt) { 
        this.systemPrompt = systemPrompt; 
    }
    
    public LlmModelEntity getLlmModel() { 
        return llmModel; 
    }
    
    public void setLlmModel(LlmModelEntity llmModel) { 
        this.llmModel = llmModel; 
    }
    
    /**
     * Convenience method for backward compatibility.
     * Returns the model identifier string.
     */
    public String getModel() {
        return llmModel != null ? llmModel.getModel() : null;
    }
    
    public boolean isEnabled() { 
        return enabled; 
    }
    
    public void setEnabled(boolean enabled) { 
        this.enabled = enabled; 
    }
    
    public int getVersion() { 
        return version; 
    }
    
    public void setVersion(int version) { 
        this.version = version; 
    }
    
    public String getMetadata() { 
        return metadata; 
    }
    
    public void setMetadata(String metadata) { 
        this.metadata = metadata; 
    }
    
    public java.util.List<AgentExecutionConfigEntity> getExecutionConfigs() { 
        return executionConfigs; 
    }
    
    public void setExecutionConfigs(java.util.List<AgentExecutionConfigEntity> executionConfigs) { 
        this.executionConfigs = executionConfigs; 
    }
    
    /**
     * Gets the default execution config (where customer is null).
     * Returns null if no default config exists.
     */
    public AgentExecutionConfigEntity getDefaultExecutionConfig() {
        return executionConfigs.stream()
            .filter(AgentExecutionConfigEntity::isDefaultConfig)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Gets the execution config for a specific customer.
     * Falls back to default config if no customer-specific config exists.
     */
    public AgentExecutionConfigEntity getExecutionConfigForCustomer(CustomerEntity customer) {
        if (customer == null) {
            return getDefaultExecutionConfig();
        }
        return executionConfigs.stream()
            .filter(ec -> customer.equals(ec.getCustomer()))
            .findFirst()
            .orElseGet(this::getDefaultExecutionConfig);
    }
    
    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
    
    public LocalDateTime getUpdatedAt() { 
        return updatedAt; 
    }
}
