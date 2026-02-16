package com.davidparry.agent.websocket;

import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.security.DatabaseApiKeyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiKeyHandshakeInterceptor.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyHandshakeInterceptorTest {

    @Mock
    private DatabaseApiKeyValidator apiKeyValidator;

    @Mock
    private McpProxyProperties mcpProxyProperties;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    private ApiKeyHandshakeInterceptor interceptor;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        interceptor = new ApiKeyHandshakeInterceptor(apiKeyValidator, mcpProxyProperties);
        attributes = new HashMap<>();
    }

    @Test
    void beforeHandshake_shouldRejectWhenProxyDisabled() throws Exception {
        // Given
        when(mcpProxyProperties.enabled()).thenReturn(false);

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        verify(apiKeyValidator, never()).validateToken(anyString());
    }

    @Test
    void beforeHandshake_shouldAcceptValidApiKeyFromHeader() throws Exception {
        // Given
        String apiKey = "valid-jwt-token";
        UUID customerId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.add(ApiKeyHandshakeInterceptor.API_KEY_HEADER, apiKey);

        when(mcpProxyProperties.enabled()).thenReturn(true);
        when(request.getHeaders()).thenReturn(headers);
        when(apiKeyValidator.validateToken(apiKey))
                .thenReturn(new DatabaseApiKeyValidator.ValidationResult(true, customerId.toString(), null));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertTrue(result);
        assertEquals(customerId.toString(), attributes.get(ApiKeyHandshakeInterceptor.CLIENT_ID_ATTRIBUTE));
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void beforeHandshake_shouldAcceptValidApiKeyFromQueryParam() throws Exception {
        // Given
        String apiKey = "valid-jwt-token";
        UUID customerId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();

        when(mcpProxyProperties.enabled()).thenReturn(true);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/agent?apiKey=" + apiKey));
        when(apiKeyValidator.validateToken(apiKey))
                .thenReturn(new DatabaseApiKeyValidator.ValidationResult(true, customerId.toString(), null));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertTrue(result);
        assertEquals(customerId.toString(), attributes.get(ApiKeyHandshakeInterceptor.CLIENT_ID_ATTRIBUTE));
    }

    @Test
    void beforeHandshake_shouldRejectInvalidApiKey() throws Exception {
        // Given
        String apiKey = "invalid-token";
        HttpHeaders headers = new HttpHeaders();
        headers.add(ApiKeyHandshakeInterceptor.API_KEY_HEADER, apiKey);

        when(mcpProxyProperties.enabled()).thenReturn(true);
        when(request.getHeaders()).thenReturn(headers);
        when(apiKeyValidator.validateToken(apiKey))
                .thenReturn(new DatabaseApiKeyValidator.ValidationResult(false, null, "Invalid token"));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertNull(attributes.get(ApiKeyHandshakeInterceptor.CLIENT_ID_ATTRIBUTE));
    }

    @Test
    void beforeHandshake_shouldRejectMissingApiKey() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();

        when(mcpProxyProperties.enabled()).thenReturn(true);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/agent"));
        when(apiKeyValidator.validateToken(null))
                .thenReturn(new DatabaseApiKeyValidator.ValidationResult(false, null, "Missing API key"));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beforeHandshake_shouldHandleMultipleQueryParams() throws Exception {
        // Given
        String apiKey = "valid-jwt-token";
        UUID customerId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();

        when(mcpProxyProperties.enabled()).thenReturn(true);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/agent?foo=bar&apiKey=" + apiKey + "&baz=qux"));
        when(apiKeyValidator.validateToken(apiKey))
                .thenReturn(new DatabaseApiKeyValidator.ValidationResult(true, customerId.toString(), null));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertTrue(result);
        assertEquals(customerId.toString(), attributes.get(ApiKeyHandshakeInterceptor.CLIENT_ID_ATTRIBUTE));
    }

    @Test
    void beforeHandshake_shouldPreferHeaderOverQueryParam() throws Exception {
        // Given
        String headerApiKey = "header-token";
        String queryApiKey = "query-token";
        UUID customerId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.add(ApiKeyHandshakeInterceptor.API_KEY_HEADER, headerApiKey);

        when(mcpProxyProperties.enabled()).thenReturn(true);
        when(request.getHeaders()).thenReturn(headers);
        when(apiKeyValidator.validateToken(headerApiKey))
                .thenReturn(new DatabaseApiKeyValidator.ValidationResult(true, customerId.toString(), null));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertTrue(result);
        verify(apiKeyValidator).validateToken(headerApiKey);
        verify(apiKeyValidator, never()).validateToken(queryApiKey);
    }

    @Test
    void afterHandshake_shouldLogExceptionWhenPresent() {
        // Given
        Exception exception = new RuntimeException("Test exception");

        // When - should not throw
        interceptor.afterHandshake(request, response, wsHandler, exception);

        // Then - just verify no exception is thrown
    }

    @Test
    void afterHandshake_shouldHandleNullException() {
        // When - should not throw
        interceptor.afterHandshake(request, response, wsHandler, null);

        // Then - just verify no exception is thrown
    }
}
