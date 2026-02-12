package com.davidparry.agent.websocket;

import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.protocol.dto.ToolDefinition;
import com.davidparry.agent.reliability.ToolCallCircuitBreaker;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RemoteToolCallbackFactory.
 */
@ExtendWith(MockitoExtension.class)
class RemoteToolCallbackFactoryTest {

    @Mock
    private McpProxyProperties properties;

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private ToolCallCircuitBreaker circuitBreaker;

    @Mock
    private ClientConnection connection;

    @Mock
    private McpProxyWebSocketHandler.ToolCallSender sender;

    private RemoteToolCallbackFactory factory;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        factory = new RemoteToolCallbackFactory(
                properties,
                connectionManager,
                circuitBreaker,
                new SimpleMeterRegistry(),
                objectMapper
        );
    }

    @Test
    void createCallback_shouldCreateRemoteToolCallback() {
        // Given
        ToolDefinition toolDefinition = ToolDefinition.builder()
                .name("terminal-list_files")
                .description("Lists files in a directory")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Directory path")
                        )
                ))
                .build();
        String sessionId = "test-session-123";

        McpProxyProperties.SessionConfig sessionConfig = new McpProxyProperties.SessionConfig(
                600, 60, 2, 1000
        );
        when(properties.session()).thenReturn(sessionConfig);

        // When
        ToolCallback callback = factory.createCallback(toolDefinition, sessionId, connection, sender);

        // Then
        assertNotNull(callback);
        assertInstanceOf(RemoteToolCallback.class, callback);
        assertEquals("terminal-list_files", callback.getToolDefinition().name());
        assertEquals("Lists files in a directory", callback.getToolDefinition().description());
    }

    @Test
    void createCallback_shouldHandleNullInputSchema() {
        // Given
        ToolDefinition toolDefinition = ToolDefinition.builder()
                .name("simple-tool")
                .description("A simple tool")
                .inputSchema(null)
                .build();
        String sessionId = "test-session-123";

        McpProxyProperties.SessionConfig sessionConfig = new McpProxyProperties.SessionConfig(
                600, 60, 2, 1000
        );
        when(properties.session()).thenReturn(sessionConfig);

        // When
        ToolCallback callback = factory.createCallback(toolDefinition, sessionId, connection, sender);

        // Then
        assertNotNull(callback);
        assertEquals("{}", callback.getToolDefinition().inputSchema());
    }

    @Test
    void createCallback_shouldHandleEmptyInputSchema() {
        // Given
        ToolDefinition toolDefinition = ToolDefinition.builder()
                .name("simple-tool")
                .description("A simple tool")
                .inputSchema(Map.of())
                .build();
        String sessionId = "test-session-123";

        McpProxyProperties.SessionConfig sessionConfig = new McpProxyProperties.SessionConfig(
                600, 60, 2, 1000
        );
        when(properties.session()).thenReturn(sessionConfig);

        // When
        ToolCallback callback = factory.createCallback(toolDefinition, sessionId, connection, sender);

        // Then
        assertNotNull(callback);
        assertEquals("{}", callback.getToolDefinition().inputSchema());
    }

    @Test
    void createCallback_shouldSerializeComplexInputSchema() {
        // Given
        Map<String, Object> complexSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "count", Map.of("type", "integer"),
                        "options", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        )
                ),
                "required", java.util.List.of("name")
        );

        ToolDefinition toolDefinition = ToolDefinition.builder()
                .name("complex-tool")
                .description("A complex tool")
                .inputSchema(complexSchema)
                .build();
        String sessionId = "test-session-123";

        McpProxyProperties.SessionConfig sessionConfig = new McpProxyProperties.SessionConfig(
                600, 60, 2, 1000
        );
        when(properties.session()).thenReturn(sessionConfig);

        // When
        ToolCallback callback = factory.createCallback(toolDefinition, sessionId, connection, sender);

        // Then
        assertNotNull(callback);
        String inputSchema = callback.getToolDefinition().inputSchema();
        assertTrue(inputSchema.contains("\"type\":\"object\""));
        assertTrue(inputSchema.contains("\"properties\""));
        assertTrue(inputSchema.contains("\"required\""));
    }
}
