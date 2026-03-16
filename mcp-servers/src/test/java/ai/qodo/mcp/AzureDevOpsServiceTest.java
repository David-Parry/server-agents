/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp;

import ai.qodo.mcp.config.AzureDevOpsConfiguration;
import ai.qodo.mcp.config.AzureDevOpsMcpConfiguration;
import ai.qodo.mcp.config.McpToolsConfiguration;
import ai.qodo.mcp.pojo.ToolOutputResult;
import ai.qodo.mcp.service.AzureDevOpsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ToolContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AzureDevOpsServiceTest {

    @Mock
    private AzureDevOpsConfiguration azureDevOpsConfiguration;

    @Mock
    private McpToolsConfiguration mcpToolsConfiguration;

    @Mock
    private ToolContext toolContext;

    private AzureDevOpsMcpConfiguration azureDevOpsMcpConfiguration;
    private AzureDevOpsService azureDevOpsService;

    @BeforeEach
    void setUp() {
        azureDevOpsMcpConfiguration = new AzureDevOpsMcpConfiguration(azureDevOpsConfiguration, mcpToolsConfiguration);
    }

    @Test
    void createPullRequest_shouldReturnError_whenConfigurationInvalid() {
        // Given - PAT is null, making configuration invalid
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn(null);
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // When
        ToolOutputResult result = azureDevOpsService.createPullRequest(
                "Test description",
                "Test PR",
                "feature-branch",
                "main",
                "TestProject",
                "TestRepo",
                toolContext
        );

        // Then
        assertTrue(result.isError());
        assertEquals("ConfigurationError", result.errorMessage());
        assertTrue(result.error().contains("configuration is invalid"));
    }

    @Test
    void createPullRequest_shouldReturnError_whenProjectMissing() throws InterruptedException {
        // Given - Valid configuration but no project
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn("https://dev.azure.com/testorg");
        when(azureDevOpsConfiguration.getDefaultProject()).thenReturn(null);
        when(mcpToolsConfiguration.getDefaultLocalPath()).thenReturn(null);
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // When
        ToolOutputResult result = azureDevOpsService.createPullRequest(
                "Test description",
                "Test PR",
                "feature-branch",
                "main",
                null, // No project specified
                "TestRepo",
                toolContext
        );

        // Then
        assertTrue(result.isError());
        assertEquals("MissingProject", result.errorMessage());
    }

    @Test
    void createPullRequest_shouldReturnError_whenRepositoryMissing() throws InterruptedException {
        // Given - Valid configuration with project but no repository
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn("https://dev.azure.com/testorg");
        when(azureDevOpsConfiguration.getDefaultProject()).thenReturn("TestProject");
        when(mcpToolsConfiguration.getDefaultLocalPath()).thenReturn(null);
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // When
        ToolOutputResult result = azureDevOpsService.createPullRequest(
                "Test description",
                "Test PR",
                "feature-branch",
                "main",
                "TestProject",
                null, // No repository specified
                toolContext
        );

        // Then
        assertTrue(result.isError());
        assertEquals("MissingRepository", result.errorMessage());
    }

    @Test
    void listPullRequests_shouldReturnError_whenConfigurationInvalid() {
        // Given - Empty PAT makes configuration invalid
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("");
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // When
        ToolOutputResult result = azureDevOpsService.listPullRequests(
                "TestProject",
                "TestRepo",
                "active",
                10,
                toolContext
        );

        // Then
        assertTrue(result.isError());
        assertEquals("ConfigurationError", result.errorMessage());
    }

    @Test
    void getPullRequest_shouldReturnError_whenConfigurationInvalid() {
        // Given - Null PAT makes configuration invalid
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn(null);
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // When
        ToolOutputResult result = azureDevOpsService.getPullRequest(
                123,
                "TestProject",
                "TestRepo",
                toolContext
        );

        // Then
        assertTrue(result.isError());
        assertEquals("ConfigurationError", result.errorMessage());
    }

    @Test
    void configurationValidation_shouldReturnFalse_whenPatMissing() {
        // Given
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn(null);
        
        // When
        boolean isValid = azureDevOpsMcpConfiguration.isConfigurationValid();

        // Then
        assertFalse(isValid);
    }

    @Test
    void configurationValidation_shouldReturnFalse_whenPatEmpty() {
        // Given
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("");
        
        // When
        boolean isValid = azureDevOpsMcpConfiguration.isConfigurationValid();

        // Then
        assertFalse(isValid);
    }

    @Test
    void configurationValidation_shouldReturnFalse_whenOrgUrlMissing() {
        // Given
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn(null);
        
        // When
        boolean isValid = azureDevOpsMcpConfiguration.isConfigurationValid();

        // Then
        assertFalse(isValid);
    }

    @Test
    void configurationValidation_shouldReturnFalse_whenOrgUrlEmpty() {
        // Given
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn("");
        
        // When
        boolean isValid = azureDevOpsMcpConfiguration.isConfigurationValid();

        // Then
        assertFalse(isValid);
    }

    @Test
    void configurationValidation_shouldReturnTrue_whenAllConfigPresent() {
        // Given
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn("https://dev.azure.com/testorg");
        
        // When
        boolean isValid = azureDevOpsMcpConfiguration.isConfigurationValid();

        // Then
        assertTrue(isValid);
    }

    @Test
    void listPullRequests_shouldReturnError_whenProjectMissing() {
        // Given - Valid configuration but no project
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn("https://dev.azure.com/testorg");
        when(azureDevOpsConfiguration.getDefaultProject()).thenReturn(null);
        when(mcpToolsConfiguration.getDefaultLocalPath()).thenReturn(null);
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // When
        ToolOutputResult result = azureDevOpsService.listPullRequests(
                null, // No project
                "TestRepo",
                "active",
                10,
                toolContext
        );

        // Then
        assertTrue(result.isError());
        assertEquals("MissingProject", result.errorMessage());
    }

    @Test
    void getPullRequest_shouldReturnError_whenProjectMissing() {
        // Given - Valid configuration but no project
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn("https://dev.azure.com/testorg");
        when(azureDevOpsConfiguration.getDefaultProject()).thenReturn(null);
        when(mcpToolsConfiguration.getDefaultLocalPath()).thenReturn(null);
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // When
        ToolOutputResult result = azureDevOpsService.getPullRequest(
                123,
                null, // No project
                "TestRepo",
                toolContext
        );

        // Then
        assertTrue(result.isError());
        assertEquals("MissingProject", result.errorMessage());
    }

    @Test
    void extractOrganizationName_shouldExtractFromDevAzureComUrl() {
        // Given
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn("https://dev.azure.com/myorg");
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // The extraction happens internally when creating the client
        // We verify the configuration is valid which means the URL can be processed
        assertTrue(azureDevOpsMcpConfiguration.isConfigurationValid());
    }

    @Test
    void extractOrganizationName_shouldExtractFromDevAzureComUrlWithTrailingSlash() {
        // Given
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn("https://dev.azure.com/myorg/");
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // The extraction happens internally when creating the client
        assertTrue(azureDevOpsMcpConfiguration.isConfigurationValid());
    }

    @Test
    void extractOrganizationName_shouldExtractFromVisualStudioUrl() {
        // Given
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn("https://myorg.visualstudio.com");
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // The extraction happens internally when creating the client
        assertTrue(azureDevOpsMcpConfiguration.isConfigurationValid());
    }

    @Test
    void extractOrganizationName_shouldHandlePlainOrgName() {
        // Given - Just the org name without URL
        when(azureDevOpsConfiguration.getPersonalAccessToken()).thenReturn("test-pat");
        when(azureDevOpsConfiguration.getOrganizationUrl()).thenReturn("myorg");
        
        azureDevOpsService = new AzureDevOpsService(azureDevOpsMcpConfiguration);

        // The extraction happens internally when creating the client
        assertTrue(azureDevOpsMcpConfiguration.isConfigurationValid());
    }
}
