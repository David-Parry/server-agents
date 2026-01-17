package com.davidparry.agent.config;

import com.davidparry.agent.entity.AgentConfigEntity;
import com.davidparry.agent.protocol.dto.AgentType;
import com.davidparry.agent.repository.AgentConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Database-backed implementation of agent configuration provider.
 * Reads agent configurations directly from the database (no caching per requirements).
 * Replaces the in-memory AgentConfigurationProvider.
 */
@Component
public class DatabaseAgentConfigurationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseAgentConfigurationProvider.class);
    
    private final AgentConfigRepository agentConfigRepository;
    
    public DatabaseAgentConfigurationProvider(AgentConfigRepository agentConfigRepository) {
        this.agentConfigRepository = agentConfigRepository;
    }
    
    /**
     * Gets the configuration for the specified agent type.
     * Reads directly from database on each call.
     * 
     * @param agentType The type of agent
     * @return The agent configuration, or a default configuration if the type is not found
     */
    public AgentConfiguration getConfiguration(AgentType agentType) {
        return agentConfigRepository.findByAgentTypeAndEnabledTrue(agentType)
            .map(this::toAgentConfiguration)
            .orElseGet(() -> {
                logger.warn("No configuration found for agent type: {}, using default", agentType);
                return getDefaultConfiguration();
            });
    }
    
    /**
     * Gets the system prompt for the specified agent type.
     * 
     * @param agentType The type of agent
     * @return The system prompt for the agent
     */
    public String getSystemPrompt(AgentType agentType) {
        return getConfiguration(agentType).systemPrompt();
    }
    
    /**
     * Gets the model for the specified agent type.
     * 
     * @param agentType The type of agent
     * @return The model identifier for the agent
     */
    public String getModel(AgentType agentType) {
        return getConfiguration(agentType).model();
    }
    
    /**
     * Checks if a configuration exists for the specified agent type.
     * 
     * @param agentType The type of agent to check
     * @return true if a specific configuration exists, false otherwise
     */
    public boolean hasConfiguration(AgentType agentType) {
        return agentConfigRepository.existsByAgentType(agentType);
    }
    
    private AgentConfiguration toAgentConfiguration(AgentConfigEntity entity) {
        return new AgentConfiguration(entity.getSystemPrompt(), entity.getModel());
    }
    
    /**
     * Returns a default configuration for unknown agent types.
     * 
     * @return A default agent configuration
     */
    private AgentConfiguration getDefaultConfiguration() {
        return AgentConfiguration.withDefaultModel("""
            You are a helpful AI assistant. Follow the instructions provided and complete 
            the requested task to the best of your ability.
            
            Guidelines:
            1. Be accurate and thorough in your responses
            2. Ask for clarification if the request is ambiguous
            3. Use the available tools when appropriate
            4. Provide clear and well-structured output
            """);
    }
}
