/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp;

import ai.qodo.mcp.config.ConfluenceConfiguration;
import ai.qodo.mcp.config.ConfluenceMcpConfiguration;
import ai.qodo.mcp.service.ConfluenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ConfluenceServiceTest {

    private ConfluenceService confluenceService;

    @Mock
    private ConfluenceMcpConfiguration mcpConfiguration;

    @Mock
    private ConfluenceConfiguration confluenceConfiguration;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup configuration mocks
        when(mcpConfiguration.getConfluenceConfiguration()).thenReturn(confluenceConfiguration);
        when(confluenceConfiguration.getSiteUrl()).thenReturn("https://test.atlassian.net");
        when(confluenceConfiguration.getEmail()).thenReturn("test@example.com");
        when(confluenceConfiguration.getApiToken()).thenReturn("test-token");
        when(mcpConfiguration.isConfigurationValid()).thenReturn(true);

        // Create service instance
        confluenceService = new ConfluenceService(mcpConfiguration);

        // Use reflection to inject the mocked WebClient
        try {
            java.lang.reflect.Field clientField = ConfluenceService.class.getDeclaredField("webClient");
            clientField.setAccessible(true);
            clientField.set(confluenceService, webClient);
        } catch (Exception e) {
            fail("Failed to inject mocked WebClient: " + e.getMessage());
        }
    }

    @Test
    void testShouldThrowExceptionWhenClientNotInitialized() {
        // Create a new service with invalid configuration
        when(mcpConfiguration.isConfigurationValid()).thenReturn(false);
        ConfluenceService uninitializedService = new ConfluenceService(mcpConfiguration);
        uninitializedService.init();

        // Verify that operations throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            uninitializedService.getSpaces(null);
        });
    }

    @Test
    void testShouldRetrieveSpacesSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "results": [
                    {
                        "id": "123456",
                        "key": "DEV",
                        "name": "Development",
                        "type": "global",
                        "description": {
                            "plain": {
                                "value": "Development space"
                            }
                        }
                    }
                ]
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getSpaces(25);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("DEV"));
        assertTrue(result.contains("Development"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldRetrieveSpaceDetailsSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "id": "123456",
                "key": "DEV",
                "name": "Development",
                "type": "global",
                "status": "current",
                "homepageId": "789",
                "description": {
                    "plain": {
                        "value": "Development space description"
                    }
                }
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getSpace("123456");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("DEV"));
        assertTrue(result.contains("Development"));
        assertTrue(result.contains("current"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldSearchContentSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "results": [
                    {
                        "id": "12345",
                        "title": "API Documentation",
                        "type": "page",
                        "space": {
                            "key": "DEV",
                            "name": "Development"
                        },
                        "version": {
                            "number": 5
                        },
                        "_links": {
                            "webui": "/spaces/DEV/pages/12345"
                        }
                    }
                ]
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.searchContent("space = DEV AND type = page", 25);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("API Documentation"));
        assertTrue(result.contains("DEV"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldRetrievePageSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "id": "12345",
                "title": "Test Page",
                "status": "current",
                "spaceId": "123456",
                "parentId": "11111",
                "version": {
                    "number": 3,
                    "createdAt": "2024-01-15T10:30:00Z"
                },
                "authorId": "user123",
                "body": {
                    "storage": {
                        "value": "<p>Page content here</p>"
                    }
                }
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPage("12345", true);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Test Page"));
        assertTrue(result.contains("current"));
        assertTrue(result.contains("Page content here"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldRetrievePageWithoutBody() {
        // Setup mock response
        String mockResponse = """
            {
                "id": "12345",
                "title": "Test Page",
                "status": "current",
                "spaceId": "123456",
                "version": {
                    "number": 3,
                    "createdAt": "2024-01-15T10:30:00Z"
                },
                "body": {
                    "storage": {
                        "value": "<p>Page content here</p>"
                    }
                }
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPage("12345", false);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Test Page"));
        assertFalse(result.contains("Page content here"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldCreatePageSuccessfully() {
        // Setup mock responses
        String createResponse = """
            {
                "id": "99999",
                "title": "New Page",
                "status": "current"
            }
            """;

        String spaceResponse = """
            {
                "id": "123456",
                "key": "DEV",
                "name": "Development"
            }
            """;

        // Mock POST for creating page
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.just(createResponse))
                .thenReturn(Mono.just(spaceResponse));

        // Mock GET for fetching space key
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);

        // Execute
        String result = confluenceService.createPage("123456", "New Page", "<p>Content</p>", null);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Page created successfully"));
        assertTrue(result.contains("99999"));
        verify(webClient, times(1)).post();
    }

    @Test
    void testShouldCreatePageWithParent() {
        // Setup mock responses
        String createResponse = """
            {
                "id": "99999",
                "title": "Child Page",
                "status": "current"
            }
            """;

        String spaceResponse = """
            {
                "id": "123456",
                "key": "DEV",
                "name": "Development"
            }
            """;

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(createResponse));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(spaceResponse));

        // Execute
        String result = confluenceService.createPage("123456", "Child Page", "<p>Content</p>", "88888");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Page created successfully"));
        verify(webClient, times(1)).post();
    }

    @Test
    void testShouldUpdatePageSuccessfully() {
        // Setup mock responses
        String currentPageResponse = """
            {
                "id": "12345",
                "title": "Existing Page",
                "status": "current",
                "spaceId": "123456"
            }
            """;

        String updateResponse = """
            {
                "id": "12345",
                "title": "Updated Page",
                "status": "current"
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(currentPageResponse));

        when(webClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(updateResponse));

        // Execute
        String result = confluenceService.updatePage("12345", "Updated Page", "<p>New content</p>", 5);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Page updated successfully"));
        assertTrue(result.contains("12345"));
        verify(webClient, times(1)).put();
    }

    @Test
    void testShouldDeletePageSuccessfully() {
        // Setup mock
        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        // Execute
        String result = confluenceService.deletePage("12345");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("deleted successfully"));
        assertTrue(result.contains("12345"));
        verify(webClient, times(1)).delete();
    }

    @Test
    void testShouldRetrievePageChildrenSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "results": [
                    {
                        "id": "22222",
                        "title": "Child Page 1",
                        "status": "current"
                    },
                    {
                        "id": "33333",
                        "title": "Child Page 2",
                        "status": "current"
                    }
                ]
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPageChildren("12345", 25);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Child Page 1"));
        assertTrue(result.contains("Child Page 2"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldHandleNoChildPages() {
        // Setup mock response
        String mockResponse = """
            {
                "results": []
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPageChildren("12345", 25);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("No child pages found"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldRetrieveSpacePagesSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "results": [
                    {
                        "id": "11111",
                        "title": "Page 1",
                        "status": "current",
                        "parentId": null
                    },
                    {
                        "id": "22222",
                        "title": "Page 2",
                        "status": "current",
                        "parentId": "11111"
                    }
                ]
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getSpacePages("123456", 25);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Page 1"));
        assertTrue(result.contains("Page 2"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldAddCommentSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "id": "comment123",
                "pageId": "12345"
            }
            """;

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.addComment("12345", "<p>This is a comment</p>");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Comment added successfully"));
        assertTrue(result.contains("comment123"));
        verify(webClient, times(1)).post();
    }

    @Test
    void testShouldRetrievePageCommentsSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "results": [
                    {
                        "id": "comment1",
                        "createdAt": "2024-01-15T10:30:00Z",
                        "body": {
                            "storage": {
                                "value": "<p>First comment</p>"
                            }
                        }
                    },
                    {
                        "id": "comment2",
                        "createdAt": "2024-01-16T11:00:00Z",
                        "body": {
                            "storage": {
                                "value": "<p>Second comment</p>"
                            }
                        }
                    }
                ]
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPageComments("12345", 25);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("First comment"));
        assertTrue(result.contains("Second comment"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldHandleNoComments() {
        // Setup mock response
        String mockResponse = """
            {
                "results": []
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPageComments("12345", 25);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("No comments found"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldRetrievePageLabelsSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "results": [
                    {"name": "documentation"},
                    {"name": "api"},
                    {"name": "important"}
                ]
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPageLabels("12345");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("documentation"));
        assertTrue(result.contains("api"));
        assertTrue(result.contains("important"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldHandleNoLabels() {
        // Setup mock response
        String mockResponse = """
            {
                "results": []
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPageLabels("12345");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("No labels found"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldAddLabelSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "results": [{"name": "new-label"}]
            }
            """;

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.addLabel("12345", "new-label");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Label 'new-label' added successfully"));
        verify(webClient, times(1)).post();
    }

    @Test
    void testShouldRetrievePageVersionsSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "results": [
                    {
                        "number": 5,
                        "createdAt": "2024-01-15T10:30:00Z",
                        "message": "Updated content",
                        "authorId": "user123"
                    },
                    {
                        "number": 4,
                        "createdAt": "2024-01-14T09:00:00Z",
                        "message": "Fixed typo",
                        "authorId": "user456"
                    }
                ]
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPageVersions("12345", 10);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Version: 5"));
        assertTrue(result.contains("Version: 4"));
        assertTrue(result.contains("Updated content"));
        assertTrue(result.contains("Fixed typo"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldRetrieveContentByLabelSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "results": [
                    {
                        "id": "11111",
                        "title": "Labeled Page 1",
                        "type": "page",
                        "space": {
                            "key": "DEV",
                            "name": "Development"
                        },
                        "version": {
                            "number": 2
                        }
                    },
                    {
                        "id": "22222",
                        "title": "Labeled Page 2",
                        "type": "page",
                        "space": {
                            "key": "DEV",
                            "name": "Development"
                        },
                        "version": {
                            "number": 1
                        }
                    }
                ]
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getContentByLabel("documentation", 25);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Labeled Page 1"));
        assertTrue(result.contains("Labeled Page 2"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldHandleNoContentByLabel() {
        // Setup mock response
        String mockResponse = """
            {
                "results": []
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getContentByLabel("nonexistent-label", 25);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("No content found with label"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldHandleNullLimitInGetSpaces() {
        // Setup mock response
        String mockResponse = """
            {
                "results": []
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute with null limit - should default to 25
        String result = confluenceService.getSpaces(null);

        // Verify
        assertNotNull(result);
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldHandleNullIncludeBodyInGetPage() {
        // Setup mock response
        String mockResponse = """
            {
                "id": "12345",
                "title": "Test Page",
                "status": "current",
                "spaceId": "123456",
                "version": {
                    "number": 1
                },
                "body": {
                    "storage": {
                        "value": "<p>Content</p>"
                    }
                }
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute with null includeBody - should default to true
        String result = confluenceService.getPage("12345", null);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Content"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldGetPageByTitleSuccessfully() {
        // Setup mock response
        String mockResponse = """
            {
                "results": [
                    {
                        "id": "12345",
                        "title": "API Documentation",
                        "type": "page",
                        "status": "current",
                        "space": {
                            "key": "DEV",
                            "name": "Development"
                        },
                        "version": {
                            "number": 3,
                            "when": "2024-01-15T10:30:00Z",
                            "by": {
                                "displayName": "John Doe"
                            }
                        },
                        "ancestors": [
                            {"title": "Parent Page"}
                        ],
                        "_links": {
                            "webui": "/spaces/DEV/pages/12345"
                        },
                        "body": {
                            "storage": {
                                "value": "<p>API documentation content</p>"
                            }
                        }
                    }
                ]
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPageByTitle("DEV", "API Documentation", true);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("API Documentation"));
        assertTrue(result.contains("DEV"));
        assertTrue(result.contains("API documentation content"));
        verify(webClient, times(1)).get();
    }

    @Test
    void testShouldHandlePageNotFoundByTitle() {
        // Setup mock response
        String mockResponse = """
            {
                "results": []
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        // Execute
        String result = confluenceService.getPageByTitle("DEV", "Nonexistent Page", true);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Page not found"));
        verify(webClient, times(1)).get();
    }
}
