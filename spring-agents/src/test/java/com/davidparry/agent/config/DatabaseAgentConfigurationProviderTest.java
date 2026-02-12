package com.davidparry.agent.config;

import com.davidparry.agent.entity.AgentConfigEntity;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.protocol.dto.AgentType;
import com.davidparry.agent.repository.AgentConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DatabaseAgentConfigurationProvider.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseAgentConfigurationProviderTest {

    @Mock
    private AgentConfigRepository agentConfigRepository;

    private DatabaseAgentConfigurationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DatabaseAgentConfigurationProvider(agentConfigRepository);
    }

    @Test
    void getConfiguration_shouldReturnConfigurationFromDatabase() {
        // Given
        AgentConfigEntity entity = createAgentConfigEntity(
            AgentType.ANALYST, 
            "Test system prompt for analyst", 
            "test-model"
        );
        when(agentConfigRepository.findByAgentTypeAndEnabledTrue(AgentType.ANALYST))
            .thenReturn(Optional.of(entity));

        // When
        AgentConfiguration config = provider.getConfiguration(AgentType.ANALYST);

        // Then
        assertNotNull(config);
        assertEquals("Test system prompt for analyst", config.systemPrompt());
        assertEquals("test-model", config.model());
        verify(agentConfigRepository).findByAgentTypeAndEnabledTrue(AgentType.ANALYST);
    }

    @Test
    void getConfiguration_shouldReturnDefaultWhenNotFound() {
        // Given
        when(agentConfigRepository.findByAgentTypeAndEnabledTrue(AgentType.ANALYST))
            .thenReturn(Optional.empty());

        // When
        AgentConfiguration config = provider.getConfiguration(AgentType.ANALYST);

        // Then
        assertNotNull(config);
        assertNotNull(config.systemPrompt());
        assertFalse(config.systemPrompt().isBlank());
        assertEquals(AgentConfiguration.DEFAULT_MODEL, config.model());
    }

    @Test
    void getSystemPrompt_shouldReturnPromptFromConfiguration() {
        // Given
        AgentConfigEntity entity = createAgentConfigEntity(
            AgentType.ENGINEER, 
            "Engineer system prompt", 
            "claude-sonnet-4-5"
        );
        when(agentConfigRepository.findByAgentTypeAndEnabledTrue(AgentType.ENGINEER))
            .thenReturn(Optional.of(entity));

        // When
        String systemPrompt = provider.getSystemPrompt(AgentType.ENGINEER);

        // Then
        assertEquals("Engineer system prompt", systemPrompt);
    }

    @Test
    void getModel_shouldReturnModelFromConfiguration() {
        // Given
        AgentConfigEntity entity = createAgentConfigEntity(
            AgentType.REVIEWER, 
            "Reviewer prompt", 
            "custom-model"
        );
        when(agentConfigRepository.findByAgentTypeAndEnabledTrue(AgentType.REVIEWER))
            .thenReturn(Optional.of(entity));

        // When
        String model = provider.getModel(AgentType.REVIEWER);

        // Then
        assertEquals("custom-model", model);
    }

    @Test
    void hasConfiguration_shouldReturnTrueWhenExists() {
        // Given
        when(agentConfigRepository.existsByAgentType(AgentType.DIAGNOSTICIAN))
            .thenReturn(true);

        // When
        boolean exists = provider.hasConfiguration(AgentType.DIAGNOSTICIAN);

        // Then
        assertTrue(exists);
        verify(agentConfigRepository).existsByAgentType(AgentType.DIAGNOSTICIAN);
    }

    @Test
    void hasConfiguration_shouldReturnFalseWhenNotExists() {
        // Given
        when(agentConfigRepository.existsByAgentType(AgentType.ANALYST))
            .thenReturn(false);

        // When
        boolean exists = provider.hasConfiguration(AgentType.ANALYST);

        // Then
        assertFalse(exists);
    }

    @Test
    void agentConfiguration_withDefaultModel_shouldSetCorrectModel() {
        String testPrompt = "Test system prompt";
        AgentConfiguration config = AgentConfiguration.withDefaultModel(testPrompt);

        assertEquals(testPrompt, config.systemPrompt());
        assertEquals(AgentConfiguration.DEFAULT_MODEL, config.model());
    }

    private AgentConfigEntity createAgentConfigEntity(AgentType type, String systemPrompt, String model) {
        // Create LlmModelEntity
        LlmModelEntity llmModel = new LlmModelEntity();
        llmModel.setModel(model);
        llmModel.setProvider("anthropic");
        llmModel.setEnabled(true);
        
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setId(UUID.randomUUID());
        entity.setAgentType(type);
        entity.setName(type.name());
        entity.setSystemPrompt(systemPrompt);
        entity.setLlmModel(llmModel);
        entity.setEnabled(true);
        return entity;
    }
}
