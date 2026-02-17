package com.davidparry.agent.controller;

import com.davidparry.agent.dto.CreateModelRequest;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.LlmModelRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import com.davidparry.agent.service.LlmModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ModelController.
 */
@ExtendWith(MockitoExtension.class)
class ModelControllerTest {

    @Mock
    private LlmModelService llmModelService;

    @Mock
    private LlmModelRepository modelRepository;

    @Mock
    private PolicyTypeRepository policyTypeRepository;

    private ModelController controller;

    @BeforeEach
    void setUp() {
        controller = new ModelController(llmModelService, modelRepository, policyTypeRepository);
    }

    @Test
    void listModels_shouldReturnAllModels() {
        // Given
        LlmModelEntity model1 = createModel("claude-sonnet-4-5", "anthropic", true);
        LlmModelEntity model2 = createModel("llama3.1:8b", "ollama", false);
        when(modelRepository.findAll()).thenReturn(List.of(model1, model2));

        // When
        ResponseEntity<?> response = controller.listModels();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void listEnabledModels_shouldReturnOnlyEnabledModels() {
        // Given
        LlmModelEntity model = createModel("claude-sonnet-4-5", "anthropic", true);
        when(modelRepository.findByEnabledTrue()).thenReturn(List.of(model));

        // When
        ResponseEntity<?> response = controller.listEnabledModels();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getModel_shouldReturnModelWhenFound() {
        // Given
        LlmModelEntity model = createModel("claude-sonnet-4-5", "anthropic", true);
        when(modelRepository.findById("claude-sonnet-4-5")).thenReturn(Optional.of(model));

        // When
        ResponseEntity<?> response = controller.getModel("claude-sonnet-4-5");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getModel_shouldReturn404WhenNotFound() {
        // Given
        when(modelRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When
        ResponseEntity<?> response = controller.getModel("nonexistent");

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void createModel_shouldCreateModelSuccessfully() {
        // Given
        CreateModelRequest request = new CreateModelRequest(
                "new-model", "anthropic", "New Model", "Description", 100000L, null, null
        );
        LlmModelEntity createdModel = createModel("new-model", "anthropic", true);
        when(llmModelService.createModel(anyString(), anyString(), anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn(createdModel);

        // When
        ResponseEntity<?> response = controller.createModel(request);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createModel_shouldReturn400WhenModelMissing() {
        // Given
        CreateModelRequest request = new CreateModelRequest(
                null, "anthropic", "New Model", "Description", 100000L, null, null
        );

        // When
        ResponseEntity<?> response = controller.createModel(request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createModel_shouldReturn400WhenModelBlank() {
        // Given
        CreateModelRequest request = new CreateModelRequest(
                "  ", "anthropic", "New Model", "Description", 100000L, null, null
        );

        // When
        ResponseEntity<?> response = controller.createModel(request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createModel_shouldReturn400WhenProviderMissing() {
        // Given
        CreateModelRequest request = new CreateModelRequest(
                "new-model", null, "New Model", "Description", 100000L, null, null
        );

        // When
        ResponseEntity<?> response = controller.createModel(request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createModel_shouldReturn400WhenProviderBlank() {
        // Given
        CreateModelRequest request = new CreateModelRequest(
                "new-model", "  ", "New Model", "Description", 100000L, null, null
        );

        // When
        ResponseEntity<?> response = controller.createModel(request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createModel_shouldReturn409WhenModelExists() {
        // Given
        CreateModelRequest request = new CreateModelRequest(
                "existing-model", "anthropic", "Model", "Description", 100000L, null, null
        );
        when(llmModelService.createModel(anyString(), anyString(), anyString(), anyString(), anyLong(), any(), any()))
                .thenThrow(new IllegalArgumentException("Model already exists"));

        // When
        ResponseEntity<?> response = controller.createModel(request);

        // Then
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void updateDefaultTokens_shouldUpdateSuccessfully() {
        // Given
        LlmModelEntity updatedModel = createModel("claude-sonnet-4-5", "anthropic", true);
        updatedModel.setDefaultTokensForNewCustomers(200000L);
        when(llmModelService.updateDefaultTokens("claude-sonnet-4-5", 200000L)).thenReturn(updatedModel);

        // When
        ResponseEntity<?> response = controller.updateDefaultTokens(
                "claude-sonnet-4-5", 
                Map.of("defaultTokensForNewCustomers", 200000L)
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void updateDefaultTokens_shouldReturn404WhenNotFound() {
        // Given
        when(llmModelService.updateDefaultTokens("nonexistent", 200000L))
                .thenThrow(new IllegalArgumentException("Model not found"));

        // When
        ResponseEntity<?> response = controller.updateDefaultTokens(
                "nonexistent", 
                Map.of("defaultTokensForNewCustomers", 200000L)
        );

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void setModelEnabled_shouldEnableModel() {
        // Given
        LlmModelEntity updatedModel = createModel("claude-sonnet-4-5", "anthropic", true);
        when(llmModelService.setModelEnabled("claude-sonnet-4-5", true)).thenReturn(updatedModel);

        // When
        ResponseEntity<?> response = controller.setModelEnabled(
                "claude-sonnet-4-5", 
                Map.of("enabled", true)
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void setModelEnabled_shouldDisableModel() {
        // Given
        LlmModelEntity updatedModel = createModel("claude-sonnet-4-5", "anthropic", false);
        when(llmModelService.setModelEnabled("claude-sonnet-4-5", false)).thenReturn(updatedModel);

        // When
        ResponseEntity<?> response = controller.setModelEnabled(
                "claude-sonnet-4-5", 
                Map.of("enabled", false)
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void setModelEnabled_shouldReturn400WhenEnabledMissing() {
        // When
        ResponseEntity<?> response = controller.setModelEnabled(
                "claude-sonnet-4-5", 
                Map.of()
        );

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void setModelEnabled_shouldReturn404WhenNotFound() {
        // Given
        when(llmModelService.setModelEnabled("nonexistent", true))
                .thenThrow(new IllegalArgumentException("Model not found"));

        // When
        ResponseEntity<?> response = controller.setModelEnabled(
                "nonexistent", 
                Map.of("enabled", true)
        );

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void linkModelToAllCustomers_shouldLinkSuccessfully() {
        // Given
        LlmModelEntity model = createModel("claude-sonnet-4-5", "anthropic", true);
        PolicyTypeEntity policy = createPolicyType("UNLIMITED");
        when(modelRepository.findById("claude-sonnet-4-5")).thenReturn(Optional.of(model));
        when(policyTypeRepository.findUnlimitedPolicyType()).thenReturn(Optional.of(policy));
        when(llmModelService.linkModelToAllCustomers(model, policy)).thenReturn(5);

        // When
        ResponseEntity<?> response = controller.linkModelToAllCustomers("claude-sonnet-4-5");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void linkModelToAllCustomers_shouldReturn404WhenNotFound() {
        // Given
        when(modelRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When
        ResponseEntity<?> response = controller.linkModelToAllCustomers("nonexistent");

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void linkModelToAllCustomers_shouldHandleNullPolicy() {
        // Given
        LlmModelEntity model = createModel("claude-sonnet-4-5", "anthropic", true);
        when(modelRepository.findById("claude-sonnet-4-5")).thenReturn(Optional.of(model));
        when(policyTypeRepository.findUnlimitedPolicyType()).thenReturn(Optional.empty());
        when(llmModelService.linkModelToAllCustomers(model, null)).thenReturn(3);

        // When
        ResponseEntity<?> response = controller.linkModelToAllCustomers("claude-sonnet-4-5");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ==================== Helper Methods ====================

    private LlmModelEntity createModel(String model, String provider, boolean enabled) {
        LlmModelEntity entity = new LlmModelEntity();
        entity.setModel(model);
        entity.setProvider(provider);
        entity.setDisplayName("Test Model");
        entity.setDescription("Test Description");
        entity.setDefaultTokensForNewCustomers(100000L);
        entity.setEnabled(enabled);
        return entity;
    }

    private PolicyTypeEntity createPolicyType(String name) {
        PolicyTypeEntity policy = new PolicyTypeEntity();
        policy.setId(UUID.randomUUID());
        policy.setName(name);
        policy.setEnabled(true);
        return policy;
    }
}
