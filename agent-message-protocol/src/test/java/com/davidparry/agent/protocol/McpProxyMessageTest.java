package com.davidparry.agent.protocol;

import com.davidparry.agent.protocol.dto.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MCP Proxy message serialization and deserialization.
 */
class McpProxyMessageTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ==================== CreateSession Tests ====================

    @Test
    void testCreateSessionSerializationRoundTrip() throws Exception {
        Agent agent = new Agent(
                "test-agent",
                "Test agent description",
                AgentType.DIAGNOSTICIAN,
                "Test instructions",
                null,
                null,
                List.of("tool1"),
                null,
                null
        );
        
        CreateSession original = CreateSession.builder()
                .sessionId("test-session-123")
                .agent(agent)
                .schema("test-schema-v1")
                .streamResponse(true)
                .tools(List.of(
                        ToolDefinition.builder()
                                .name("read_file")
                                .description("Read a file")
                                .inputSchema(Map.of("type", "object"))
                                .build()
                ))
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertEquals("test-session-123", result.getSessionId());
        assertNotNull(result.getAgent());
        assertEquals("test-agent", result.getAgent().name());
        assertEquals("Test agent description", result.getAgent().description());
        assertEquals("test-schema-v1", result.getSchema());
        assertTrue(result.isStreamResponse());
        assertEquals(1, result.getTools().size());
        assertEquals("read_file", result.getTools().get(0).getName());
    }

    @Test
    void testCreateSessionWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        ObjectNode promptParams = JsonNodeFactory.instance.objectNode();
        promptParams.put("key1", "value1");
        promptParams.put("key2", "value2");
        
        Agent agent = new Agent(
                "full-agent",
                "Full agent description",
                AgentType.ANALYST,
                "Full instructions",
                null,
                null,
                List.of("tool1", "tool2"),
                "{\"type\": \"object\"}",
                null
        );
        
        CreateSession session = CreateSession.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .sessionId("session-456")
                .agent(agent)
                .schema("custom-schema")
                .tools(List.of())
                .streamResponse(false)
                .maxDurationSeconds(300)
                .promptParams(promptParams)
                .build();

        assertEquals("msg-123", session.getMessageId());
        assertEquals(timestamp, session.getTimestamp());
        assertEquals("session-456", session.getSessionId());
        assertNotNull(session.getAgent());
        assertEquals("full-agent", session.getAgent().name());
        assertEquals("Full agent description", session.getAgent().description());
        assertEquals(AgentType.ANALYST, session.getAgent().type());
        assertEquals("Full instructions", session.getAgent().instructions());
        assertEquals("custom-schema", session.getSchema());
        assertTrue(session.getTools().isEmpty());
        assertFalse(session.isStreamResponse());
        assertEquals(300, session.getMaxDurationSeconds());
        assertNotNull(session.getPromptParams());
        assertEquals(2, session.getPromptParams().size());
        assertEquals("value1", session.getPromptParams().get("key1").asText());
        assertEquals("value2", session.getPromptParams().get("key2").asText());
        assertNotNull(session.toString());
        assertTrue(session.toString().contains("promptParams"));
        assertTrue(session.toString().contains("schema"));
        assertTrue(session.toString().contains("agent"));
    }

    @Test
    void testCreateSessionWithNullTools() throws Exception {
        String json = """
                {
                    "type": "create_session",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "agent": {
                        "name": "test-agent",
                        "description": "Test",
                        "type": "ANALYST",
                        "instructions": "Do something"
                    },
                    "tools": null
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertNotNull(result.getTools());
        assertTrue(result.getTools().isEmpty());
    }

    @Test
    void testCreateSessionWithSchema() throws Exception {
        String json = """
                {
                    "type": "create_session",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "agent": {
                        "name": "test-agent",
                        "description": "Test",
                        "type": "ANALYST",
                        "instructions": "Do something"
                    },
                    "schema": "my-custom-schema"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertEquals("my-custom-schema", result.getSchema());
    }

    @Test
    void testCreateSessionWithNullSchema() throws Exception {
        String json = """
                {
                    "type": "create_session",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "agent": {
                        "name": "test-agent",
                        "description": "Test",
                        "type": "ANALYST",
                        "instructions": "Do something"
                    },
                    "schema": null
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertNull(result.getSchema());
    }

    @Test
    void testCreateSessionWithMissingSchema() throws Exception {
        String json = """
                {
                    "type": "create_session",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "agent": {
                        "name": "test-agent",
                        "description": "Test",
                        "type": "ANALYST",
                        "instructions": "Do something"
                    }
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertNull(result.getSchema());
    }

    @Test
    void testCreateSessionWithNullPromptParams() throws Exception {
        String json = """
                {
                    "type": "create_session",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "agent": {
                        "name": "test-agent",
                        "description": "Test",
                        "type": "ANALYST",
                        "instructions": "Do something"
                    },
                    "promptParams": null
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertNotNull(result.getPromptParams());
        assertTrue(result.getPromptParams().isEmpty());
    }

    @Test
    void testCreateSessionWithMissingPromptParams() throws Exception {
        String json = """
                {
                    "type": "create_session",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "agent": {
                        "name": "test-agent",
                        "description": "Test",
                        "type": "ANALYST",
                        "instructions": "Do something"
                    }
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertNotNull(result.getPromptParams());
        assertTrue(result.getPromptParams().isEmpty());
    }

    @Test
    void testCreateSessionWithPromptParams() throws Exception {
        String json = """
                {
                    "type": "create_session",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "agent": {
                        "name": "test-agent",
                        "description": "Test",
                        "type": "ANALYST",
                        "instructions": "Hello {{name}}, your task is {{task}}"
                    },
                    "promptParams": {
                        "name": "Alice",
                        "task": "write code"
                    }
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertNotNull(result.getPromptParams());
        assertEquals(2, result.getPromptParams().size());
        assertEquals("Alice", result.getPromptParams().get("name").asText());
        assertEquals("write code", result.getPromptParams().get("task").asText());
    }

    @Test
    void testCreateSessionPromptParamsSerializationRoundTrip() throws Exception {
        ObjectNode promptParams = JsonNodeFactory.instance.objectNode();
        promptParams.put("param1", "value1");
        promptParams.put("param2", "value2");
        
        Agent agent = new Agent(
                "test-agent",
                "Test description",
                AgentType.ANALYST,
                "Test with {{param1}} and {{param2}}",
                null,
                null,
                List.of(),
                null,
                null
        );
        
        CreateSession original = CreateSession.builder()
                .sessionId("test-session-123")
                .agent(agent)
                .promptParams(promptParams)
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertNotNull(result.getPromptParams());
        assertEquals(2, result.getPromptParams().size());
        assertEquals("value1", result.getPromptParams().get("param1").asText());
        assertEquals("value2", result.getPromptParams().get("param2").asText());
    }

    @Test
    void testCreateSessionPromptParamsWithNestedObjects() throws Exception {
        String json = """
                {
                    "type": "create_session",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "agent": {
                        "name": "test-agent",
                        "description": "Test",
                        "type": "ANALYST",
                        "instructions": "Test with nested params"
                    },
                    "promptParams": {
                        "simple": "value",
                        "nested": {
                            "inner": "innerValue",
                            "number": 42
                        },
                        "array": [1, 2, 3]
                    }
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertNotNull(result.getPromptParams());
        assertEquals(3, result.getPromptParams().size());
        assertEquals("value", result.getPromptParams().get("simple").asText());
        assertTrue(result.getPromptParams().get("nested").isObject());
        assertEquals("innerValue", result.getPromptParams().get("nested").get("inner").asText());
        assertEquals(42, result.getPromptParams().get("nested").get("number").asInt());
        assertTrue(result.getPromptParams().get("array").isArray());
        assertEquals(3, result.getPromptParams().get("array").size());
    }

    @Test
    void testCreateSessionBuilderWithNullPromptParams() {
        Agent agent = new Agent(
                "test-agent",
                "Test description",
                AgentType.ANALYST,
                "Test instructions",
                null,
                null,
                List.of(),
                null,
                null
        );
        
        CreateSession session = CreateSession.builder()
                .sessionId("test-session")
                .agent(agent)
                .promptParams(null)
                .build();

        assertNotNull(session.getPromptParams());
        assertTrue(session.getPromptParams().isEmpty());
    }

    @Test
    void testCreateSessionWithNullAgent() throws Exception {
        String json = """
                {
                    "type": "create_session",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "agent": null
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertNull(result.getAgent());
    }

    @Test
    void testCreateSessionWithFullAgent() throws Exception {
        String json = """
                {
                    "type": "create_session",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "agent": {
                        "name": "full-agent",
                        "description": "A full agent with all fields",
                        "type": "DIAGNOSTICIAN",
                        "instructions": "Detailed instructions here",
                        "mcpServers": "{\\"mcpServers\\": {}}",
                        "tools": ["tool1", "tool2"],
                        "output_schema": "{\\"type\\": \\"object\\"}"
                }
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(CreateSession.class, deserialized);
        CreateSession result = (CreateSession) deserialized;
        assertNotNull(result.getAgent());
        assertEquals("full-agent", result.getAgent().name());
        assertEquals("A full agent with all fields", result.getAgent().description());
        assertEquals(AgentType.DIAGNOSTICIAN, result.getAgent().type());
        assertEquals("Detailed instructions here", result.getAgent().instructions());
        assertEquals("{\"mcpServers\": {}}", result.getAgent().mcpServers());
        assertEquals(2, result.getAgent().tools().size());
        assertEquals("{\"type\": \"object\"}", result.getAgent().outputSchema());
    }

    // ==================== SessionResult Tests ====================

    @Test
    void testSessionResultSerializationRoundTrip() throws Exception {
        SessionResult original = SessionResult.builder()
                .sessionId("test-session-456")
                .success(true)
                .content(JsonNodeFactory.instance.textNode("Operation completed successfully"))
                .toolCallsExecuted(3)
                .totalDurationMs(1500)
                .metrics(SessionMetrics.builder()
                        .durationMs(1500)
                        .toolCallCount(3)
                        .tokenCount(500)
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionResult.class, deserialized);
        SessionResult result = (SessionResult) deserialized;
        assertEquals("test-session-456", result.getSessionId());
        assertTrue(result.isSuccess());
        assertEquals("Operation completed successfully", result.getContent().asText());
        assertEquals(3, result.getToolCallsExecuted());
        assertEquals(1500, result.getTotalDurationMs());
        assertNotNull(result.getMetrics());
        assertEquals(500, result.getMetrics().getTokenCount());
    }

    @Test
    void testSessionResultBackwardCompatibilityWithResponseField() throws Exception {
        String jsonWithResponseField = """
                {
                    "type": "session_result",
                    "messageId": "msg-123",
                    "sessionId": "session-backward-compat",
                    "success": true,
                    "response": "This uses the old response field",
                    "toolCallsExecuted": 1,
                    "totalDurationMs": 100
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(jsonWithResponseField, McpProxyMessage.class);

        assertInstanceOf(SessionResult.class, deserialized);
        SessionResult result = (SessionResult) deserialized;
        assertEquals("This uses the old response field", result.getContent().asText());
    }

    @Test
    void testSessionResultWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        SessionResult result = SessionResult.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .sessionId("session-456")
                .success(false)
                .content(JsonNodeFactory.instance.textNode("Content"))
                .errorMessage("Error occurred")
                .toolCallsExecuted(5)
                .totalDurationMs(2000)
                .metrics(SessionMetrics.builder().build())
                .build();

        assertEquals("msg-123", result.getMessageId());
        assertEquals(timestamp, result.getTimestamp());
        assertEquals("session-456", result.getSessionId());
        assertFalse(result.isSuccess());
        assertEquals("Content", result.getContent().asText());
        assertEquals("Error occurred", result.getErrorMessage());
        assertEquals(5, result.getToolCallsExecuted());
        assertEquals(2000, result.getTotalDurationMs());
        assertNotNull(result.getMetrics());
        assertNotNull(result.toString());
    }

    // ==================== ConnectionEstablished Tests ====================

    @Test
    void testConnectionEstablishedSerialization() throws Exception {
        ConnectionEstablished original = ConnectionEstablished.builder()
                .connectionId("conn-123")
                .serverVersion("1.0.0")
                .maxConcurrentSessions(10)
                .capabilities(Capabilities.builder()
                        .streamingSupported(true)
                        .maxConcurrentSessions(10)
                        .sessionTimeoutSeconds(300)
                        .toolCallTimeoutSeconds(60)
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(ConnectionEstablished.class, deserialized);
        ConnectionEstablished result = (ConnectionEstablished) deserialized;
        assertEquals("conn-123", result.getConnectionId());
        assertEquals("1.0.0", result.getServerVersion());
        assertTrue(result.getCapabilities().isStreamingSupported());
    }

    @Test
    void testConnectionEstablishedWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        ConnectionEstablished conn = ConnectionEstablished.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .connectionId("conn-456")
                .serverVersion("2.0.0")
                .maxConcurrentSessions(20)
                .capabilities(Capabilities.builder().build())
                .build();

        assertEquals("msg-123", conn.getMessageId());
        assertEquals(timestamp, conn.getTimestamp());
        assertEquals("conn-456", conn.getConnectionId());
        assertEquals("2.0.0", conn.getServerVersion());
        assertEquals(20, conn.getMaxConcurrentSessions());
        assertNotNull(conn.getCapabilities());
        assertNotNull(conn.toString());
    }

    // ==================== ToolCallRequest Tests ====================

    @Test
    void testToolCallRequestSerialization() throws Exception {
        ToolCallRequest original = ToolCallRequest.builder()
                .sessionId("session-123")
                .requestId("request-456")
                .toolName("read_file")
                .deadline(Instant.now().plusSeconds(60))
                .arguments(Map.of("path", "/tmp/test.txt"))
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(ToolCallRequest.class, deserialized);
        ToolCallRequest result = (ToolCallRequest) deserialized;
        assertEquals("read_file", result.getToolName());
        assertEquals("/tmp/test.txt", result.getArguments().get("path"));
    }

    @Test
    void testToolCallRequestWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        Instant deadline = Instant.now().plusSeconds(120);
        ToolCallRequest request = ToolCallRequest.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .sessionId("session-456")
                .requestId("request-789")
                .toolName("write_file")
                .deadline(deadline)
                .arguments(Map.of("content", "test"))
                .build();

        assertEquals("msg-123", request.getMessageId());
        assertEquals(timestamp, request.getTimestamp());
        assertEquals("session-456", request.getSessionId());
        assertEquals("request-789", request.getRequestId());
        assertEquals("write_file", request.getToolName());
        assertEquals(deadline, request.getDeadline());
        assertEquals("test", request.getArguments().get("content"));
        assertNotNull(request.toString());
    }

    @Test
    void testToolCallRequestWithNullArguments() throws Exception {
        String json = """
                {
                    "type": "tool_call_request",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "requestId": "request-789",
                    "toolName": "test_tool",
                    "arguments": null
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(ToolCallRequest.class, deserialized);
        ToolCallRequest result = (ToolCallRequest) deserialized;
        assertNotNull(result.getArguments());
        assertTrue(result.getArguments().isEmpty());
    }

    // ==================== ErrorMessage Tests ====================

    @Test
    void testErrorMessageWithUnifiedErrorCode() throws Exception {
        ErrorMessage original = ErrorMessage.builder()
                .sessionId("session-789")
                .code(ErrorCode.TOOL_CALL_TIMEOUT)
                .message("Tool call timed out")
                .details(Map.of("timeout_ms", 30000))
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(ErrorMessage.class, deserialized);
        ErrorMessage result = (ErrorMessage) deserialized;
        assertEquals(ErrorCode.TOOL_CALL_TIMEOUT, result.getCode());
        assertEquals("Tool call timed out", result.getMessage());
    }

    @Test
    void testErrorMessageWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        ErrorMessage error = ErrorMessage.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .sessionId("session-456")
                .code(ErrorCode.INTERNAL_ERROR)
                .message("Internal error")
                .details(Map.of("stack", "trace"))
                .build();

        assertEquals("msg-123", error.getMessageId());
        assertEquals(timestamp, error.getTimestamp());
        assertEquals("session-456", error.getSessionId());
        assertEquals(ErrorCode.INTERNAL_ERROR, error.getCode());
        assertEquals("Internal error", error.getMessage());
        assertEquals("trace", error.getDetails().get("stack"));
        assertNotNull(error.toString());
    }

    @Test
    void testErrorMessageWithNullDetails() throws Exception {
        String json = """
                {
                    "type": "error",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "code": "INTERNAL_ERROR",
                    "message": "Error",
                    "details": null
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(ErrorMessage.class, deserialized);
        ErrorMessage result = (ErrorMessage) deserialized;
        assertNotNull(result.getDetails());
        assertTrue(result.getDetails().isEmpty());
    }

    // ==================== Heartbeat Tests ====================

    @Test
    void testHeartbeatSerialization() throws Exception {
        Heartbeat original = Heartbeat.builder()
                .sequenceNumber(42)
                .isResponse(false)
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(Heartbeat.class, deserialized);
        Heartbeat result = (Heartbeat) deserialized;
        assertEquals(42, result.getSequenceNumber());
        assertFalse(result.isResponse());
    }

    @Test
    void testHeartbeatWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        Heartbeat heartbeat = Heartbeat.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .sequenceNumber(100)
                .isResponse(true)
                .build();

        assertEquals("msg-123", heartbeat.getMessageId());
        assertEquals(timestamp, heartbeat.getTimestamp());
        assertEquals(100, heartbeat.getSequenceNumber());
        assertTrue(heartbeat.isResponse());
        assertNotNull(heartbeat.toString());
    }

    // ==================== SessionStarted Tests ====================

    @Test
    void testSessionStartedSerialization() throws Exception {
        SessionStarted original = SessionStarted.builder()
                .sessionId("session-123")
                .toolCount(5)
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionStarted.class, deserialized);
        SessionStarted result = (SessionStarted) deserialized;
        assertEquals("session-123", result.getSessionId());
        assertEquals(5, result.getToolCount());
    }

    @Test
    void testSessionStartedWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        SessionStarted started = SessionStarted.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .sessionId("session-456")
                .toolCount(10)
                .build();

        assertEquals("msg-123", started.getMessageId());
        assertEquals(timestamp, started.getTimestamp());
        assertEquals("session-456", started.getSessionId());
        assertEquals(10, started.getToolCount());
        assertNotNull(started.toString());
    }

    // ==================== SessionCancelled Tests ====================

    @Test
    void testSessionCancelledSerialization() throws Exception {
        SessionCancelled original = SessionCancelled.builder()
                .sessionId("session-123")
                .reason("User requested cancellation")
                .toolCallsCompleted(3)
                .toolCallsPending(2)
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionCancelled.class, deserialized);
        SessionCancelled result = (SessionCancelled) deserialized;
        assertEquals("session-123", result.getSessionId());
        assertEquals("User requested cancellation", result.getReason());
        assertEquals(3, result.getToolCallsCompleted());
        assertEquals(2, result.getToolCallsPending());
    }

    @Test
    void testSessionCancelledWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        SessionCancelled cancelled = SessionCancelled.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .sessionId("session-456")
                .reason("Timeout")
                .toolCallsCompleted(5)
                .toolCallsPending(0)
                .build();

        assertEquals("msg-123", cancelled.getMessageId());
        assertEquals(timestamp, cancelled.getTimestamp());
        assertEquals("session-456", cancelled.getSessionId());
        assertEquals("Timeout", cancelled.getReason());
        assertEquals(5, cancelled.getToolCallsCompleted());
        assertEquals(0, cancelled.getToolCallsPending());
        assertNotNull(cancelled.toString());
    }

    // ==================== CancelSession Tests ====================

    @Test
    void testCancelSessionSerialization() throws Exception {
        CancelSession original = CancelSession.builder()
                .sessionId("session-123")
                .reason("User cancelled")
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(CancelSession.class, deserialized);
        CancelSession result = (CancelSession) deserialized;
        assertEquals("session-123", result.getSessionId());
        assertEquals("User cancelled", result.getReason());
    }

    @Test
    void testCancelSessionWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        CancelSession cancel = CancelSession.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .sessionId("session-456")
                .reason("Abort")
                .build();

        assertEquals("msg-123", cancel.getMessageId());
        assertEquals(timestamp, cancel.getTimestamp());
        assertEquals("session-456", cancel.getSessionId());
        assertEquals("Abort", cancel.getReason());
        assertNotNull(cancel.toString());
    }

    // ==================== ToolCallResponse Tests ====================

    @Test
    void testToolCallResponseSerialization() throws Exception {
        ToolCallResponse original = ToolCallResponse.builder()
                .sessionId("session-123")
                .requestId("request-456")
                .success(true)
                .result("File content here")
                .executionTimeMs(150)
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(ToolCallResponse.class, deserialized);
        ToolCallResponse result = (ToolCallResponse) deserialized;
        assertEquals("session-123", result.getSessionId());
        assertEquals("request-456", result.getRequestId());
        assertTrue(result.isSuccess());
        assertEquals("File content here", result.getResult());
        assertEquals(150, result.getExecutionTimeMs());
    }

    @Test
    void testToolCallResponseWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        ToolCallResponse response = ToolCallResponse.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .sessionId("session-456")
                .requestId("request-789")
                .success(false)
                .result(null)
                .errorMessage("Tool failed")
                .executionTimeMs(500)
                .build();

        assertEquals("msg-123", response.getMessageId());
        assertEquals(timestamp, response.getTimestamp());
        assertEquals("session-456", response.getSessionId());
        assertEquals("request-789", response.getRequestId());
        assertFalse(response.isSuccess());
        assertNull(response.getResult());
        assertEquals("Tool failed", response.getErrorMessage());
        assertEquals(500, response.getExecutionTimeMs());
        assertNotNull(response.toString());
    }

    // ==================== StreamChunk Tests ====================

    @Test
    void testStreamChunkSerialization() throws Exception {
        StreamChunk original = StreamChunk.builder()
                .sessionId("session-123")
                .sequenceNumber(1)
                .chunkType(ChunkType.TEXT)
                .content("Hello")
                .isLast(false)
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(StreamChunk.class, deserialized);
        StreamChunk result = (StreamChunk) deserialized;
        assertEquals("session-123", result.getSessionId());
        assertEquals(1, result.getSequenceNumber());
        assertEquals(ChunkType.TEXT, result.getChunkType());
        assertEquals("Hello", result.getContent());
        assertFalse(result.isLast());
    }

    @Test
    void testStreamChunkWithAllBuilderMethods() throws Exception {
        Instant timestamp = Instant.now();
        ToolCallInfo toolCall = ToolCallInfo.builder()
                .toolName("test_tool")
                .requestId("req-123")
                .success(true)
                .build();

        StreamChunk chunk = StreamChunk.builder()
                .messageId("msg-123")
                .timestamp(timestamp)
                .sessionId("session-456")
                .sequenceNumber(5)
                .chunkType(ChunkType.TOOL_END)
                .content("Result")
                .toolCall(toolCall)
                .isLast(true)
                .build();

        assertEquals("msg-123", chunk.getMessageId());
        assertEquals(timestamp, chunk.getTimestamp());
        assertEquals("session-456", chunk.getSessionId());
        assertEquals(5, chunk.getSequenceNumber());
        assertEquals(ChunkType.TOOL_END, chunk.getChunkType());
        assertEquals("Result", chunk.getContent());
        assertNotNull(chunk.getToolCall());
        assertEquals("test_tool", chunk.getToolCall().getToolName());
        assertTrue(chunk.isLast());
        assertNotNull(chunk.toString());
    }

    // ==================== StringOrMapDeserializer Tests ====================

    @Test
    void testToolCallRequestWithStringArguments() throws Exception {
        String json = """
                {
                    "type": "tool_call_request",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "requestId": "request-789",
                    "toolName": "test_tool",
                    "arguments": "{\\"key\\": \\"value\\"}"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(ToolCallRequest.class, deserialized);
        ToolCallRequest result = (ToolCallRequest) deserialized;
        assertEquals("value", result.getArguments().get("key"));
    }

    @Test
    void testToolCallRequestWithEmptyStringArguments() throws Exception {
        String json = """
                {
                    "type": "tool_call_request",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "requestId": "request-789",
                    "toolName": "test_tool",
                    "arguments": ""
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(ToolCallRequest.class, deserialized);
        ToolCallRequest result = (ToolCallRequest) deserialized;
        assertTrue(result.getArguments().isEmpty());
    }

    @Test
    void testToolCallRequestWithStringArgumentsMultipleKeys() throws Exception {
        String json = """
                {
                    "type": "tool_call_request",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "requestId": "request-789",
                    "toolName": "test_tool",
                    "arguments": "{\\"path\\": \\"/tmp/file.txt\\", \\"content\\": \\"hello world\\", \\"append\\": true}"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(ToolCallRequest.class, deserialized);
        ToolCallRequest result = (ToolCallRequest) deserialized;
        assertEquals("/tmp/file.txt", result.getArguments().get("path"));
        assertEquals("hello world", result.getArguments().get("content"));
        assertEquals(true, result.getArguments().get("append"));
    }

    @Test
    void testToolCallRequestWithStringArgumentsNestedObject() throws Exception {
        String json = """
                {
                    "type": "tool_call_request",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "requestId": "request-789",
                    "toolName": "test_tool",
                    "arguments": "{\\"config\\": {\\"timeout\\": 30, \\"retries\\": 3}}"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(ToolCallRequest.class, deserialized);
        ToolCallRequest result = (ToolCallRequest) deserialized;
        assertNotNull(result.getArguments().get("config"));
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) result.getArguments().get("config");
        assertEquals(30, config.get("timeout"));
        assertEquals(3, config.get("retries"));
    }

    @Test
    void testToolCallRequestWithStringArgumentsContainingArray() throws Exception {
        String json = """
                {
                    "type": "tool_call_request",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "requestId": "request-789",
                    "toolName": "test_tool",
                    "arguments": "{\\"files\\": [\\"/tmp/a.txt\\", \\"/tmp/b.txt\\"], \\"recursive\\": false}"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(ToolCallRequest.class, deserialized);
        ToolCallRequest result = (ToolCallRequest) deserialized;
        assertNotNull(result.getArguments().get("files"));
        @SuppressWarnings("unchecked")
        java.util.List<String> files = (java.util.List<String>) result.getArguments().get("files");
        assertEquals(2, files.size());
        assertEquals("/tmp/a.txt", files.get(0));
        assertEquals("/tmp/b.txt", files.get(1));
        assertEquals(false, result.getArguments().get("recursive"));
    }

    @Test
    void testToolCallRequestWithStringArgumentsEmptyObject() throws Exception {
        String json = """
                {
                    "type": "tool_call_request",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "requestId": "request-789",
                    "toolName": "test_tool",
                    "arguments": "{}"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(ToolCallRequest.class, deserialized);
        ToolCallRequest result = (ToolCallRequest) deserialized;
        assertNotNull(result.getArguments());
        assertTrue(result.getArguments().isEmpty());
    }

    @Test
    void testToolCallRequestWithStringArgumentsNullValues() throws Exception {
        String json = """
                {
                    "type": "tool_call_request",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "requestId": "request-789",
                    "toolName": "test_tool",
                    "arguments": "{\\"key\\": null, \\"other\\": \\"value\\"}"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(ToolCallRequest.class, deserialized);
        ToolCallRequest result = (ToolCallRequest) deserialized;
        assertTrue(result.getArguments().containsKey("key"));
        assertNull(result.getArguments().get("key"));
        assertEquals("value", result.getArguments().get("other"));
    }

    @Test
    void testToolCallRequestWithStringArgumentsNumericValues() throws Exception {
        String json = """
                {
                    "type": "tool_call_request",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "requestId": "request-789",
                    "toolName": "test_tool",
                    "arguments": "{\\"intVal\\": 42, \\"doubleVal\\": 3.14, \\"negVal\\": -100}"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(ToolCallRequest.class, deserialized);
        ToolCallRequest result = (ToolCallRequest) deserialized;
        assertEquals(42, result.getArguments().get("intVal"));
        assertEquals(3.14, result.getArguments().get("doubleVal"));
        assertEquals(-100, result.getArguments().get("negVal"));
    }

    @Test
    void testToolCallRequestWithInvalidJsonStringArguments() throws Exception {
        String json = """
                {
                    "type": "tool_call_request",
                    "messageId": "msg-123",
                    "sessionId": "session-456",
                    "requestId": "request-789",
                    "toolName": "test_tool",
                    "arguments": "not valid json"
                }
                """;

        assertThrows(Exception.class, () -> {
            objectMapper.readValue(json, McpProxyMessage.class);
        });
    }

    @Test
    void testStringOrMapDeserializerDirectlyWithNullString() throws Exception {
        // Test the deserializer directly to cover the null string branch
        StringOrMapDeserializer deserializer = new StringOrMapDeserializer();
        ObjectMapper mapper = new ObjectMapper();
        
        // Create a JsonParser that returns null for getValueAsString()
        // by using a JSON null value token
        String jsonWithNullValue = "null";
        try (JsonParser parser = mapper.getFactory().createParser(jsonWithNullValue)) {
            parser.nextToken(); // Move to the VALUE_NULL token
            Map<String, Object> result = deserializer.deserialize(parser, mapper.getDeserializationContext());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== DTO Tests ====================

    @Test
    void testCapabilitiesBuilder() {
        Capabilities capabilities = Capabilities.builder()
                .streamingSupported(true)
                .maxConcurrentSessions(5)
                .sessionTimeoutSeconds(600)
                .toolCallTimeoutSeconds(30)
                .build();

        assertTrue(capabilities.isStreamingSupported());
        assertEquals(5, capabilities.getMaxConcurrentSessions());
        assertEquals(600, capabilities.getSessionTimeoutSeconds());
        assertEquals(30, capabilities.getToolCallTimeoutSeconds());
        assertNotNull(capabilities.toString());
    }

    @Test
    void testSessionMetricsBuilder() {
        SessionMetrics metrics = SessionMetrics.builder()
                .durationMs(1000)
                .toolCallCount(5)
                .tokenCount(250)
                .build();

        assertEquals(1000, metrics.getDurationMs());
        assertEquals(5, metrics.getToolCallCount());
        assertEquals(250, metrics.getTokenCount());
        assertNotNull(metrics.toString());
    }

    @Test
    void testToolDefinitionBuilder() {
        ToolDefinition tool = ToolDefinition.builder()
                .name("my_tool")
                .description("A test tool")
                .inputSchema(Map.of("type", "object"))
                .build();

        assertEquals("my_tool", tool.getName());
        assertEquals("A test tool", tool.getDescription());
        assertEquals("object", tool.getInputSchema().get("type"));
        assertNotNull(tool.toString());
    }

    @Test
    void testToolDefinitionWithNullInputSchema() throws Exception {
        String json = """
                {
                    "name": "test_tool",
                    "description": "Test",
                    "inputSchema": null
                }
                """;

        ToolDefinition tool = objectMapper.readValue(json, ToolDefinition.class);
        assertNotNull(tool.getInputSchema());
        assertTrue(tool.getInputSchema().isEmpty());
    }

    @Test
    void testToolCallInfoBuilder() {
        ToolCallInfo info = ToolCallInfo.builder()
                .toolName("my_tool")
                .requestId("req-123")
                .arguments(Map.of("arg1", "value1"))
                .result("Success")
                .success(true)
                .build();

        assertEquals("my_tool", info.getToolName());
        assertEquals("req-123", info.getRequestId());
        assertEquals("value1", info.getArguments().get("arg1"));
        assertEquals("Success", info.getResult());
        assertTrue(info.isSuccess());
        assertNotNull(info.toString());
    }

    @Test
    void testToolCallInfoWithNullArguments() throws Exception {
        String json = """
                {
                    "toolName": "test_tool",
                    "requestId": "req-123",
                    "arguments": null,
                    "success": true
                }
                """;

        ToolCallInfo info = objectMapper.readValue(json, ToolCallInfo.class);
        assertNotNull(info.getArguments());
        assertTrue(info.getArguments().isEmpty());
    }

    // ==================== Enum Tests ====================

    @Test
    void testChunkTypeValues() {
        assertEquals(5, ChunkType.values().length);
        assertNotNull(ChunkType.valueOf("TEXT"));
        assertNotNull(ChunkType.valueOf("TOOL_START"));
        assertNotNull(ChunkType.valueOf("TOOL_END"));
        assertNotNull(ChunkType.valueOf("THINKING"));
        assertNotNull(ChunkType.valueOf("ERROR"));
    }

    @Test
    void testErrorCodeValues() {
        assertEquals(15, ErrorCode.values().length);
        assertNotNull(ErrorCode.valueOf("AUTHENTICATION_FAILED"));
        assertNotNull(ErrorCode.valueOf("UNAUTHORIZED"));
        assertNotNull(ErrorCode.valueOf("RATE_LIMITED"));
        assertNotNull(ErrorCode.valueOf("CONNECTION_LIMIT_EXCEEDED"));
        assertNotNull(ErrorCode.valueOf("SESSION_NOT_FOUND"));
        assertNotNull(ErrorCode.valueOf("SESSION_EXPIRED"));
        assertNotNull(ErrorCode.valueOf("SESSION_TIMEOUT"));
        assertNotNull(ErrorCode.valueOf("SESSION_LIMIT_EXCEEDED"));
        assertNotNull(ErrorCode.valueOf("TOOL_NOT_FOUND"));
        assertNotNull(ErrorCode.valueOf("TOOL_EXECUTION_FAILED"));
        assertNotNull(ErrorCode.valueOf("TOOL_CALL_TIMEOUT"));
        assertNotNull(ErrorCode.valueOf("TOOL_CALL_FAILED"));
        assertNotNull(ErrorCode.valueOf("CONNECTION_ERROR"));
        assertNotNull(ErrorCode.valueOf("INVALID_MESSAGE"));
        assertNotNull(ErrorCode.valueOf("INTERNAL_ERROR"));
    }

    // ==================== McpProxyMessage Base Class Tests ====================

    @Test
    void testMcpProxyMessageWithNullMessageIdAndTimestamp() throws Exception {
        String json = """
                {
                    "type": "heartbeat",
                    "messageId": null,
                    "timestamp": null,
                    "sequenceNumber": 1,
                    "isResponse": false
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);
        assertInstanceOf(Heartbeat.class, deserialized);
        assertNotNull(deserialized.getMessageId());
        assertNotNull(deserialized.getTimestamp());
    }

    // ==================== Test Subclass for McpProxyMessage Base Class ====================

    /**
     * Test subclass to access protected default constructor and test base class toString
     */
    static class TestMessage extends McpProxyMessage {
        public TestMessage() {
            super();
        }

        // Don't override toString to test base class toString
    }

    @Test
    void testMcpProxyMessageDefaultConstructor() {
        TestMessage message = new TestMessage();
        assertNotNull(message.getMessageId());
        assertNotNull(message.getTimestamp());
    }

    @Test
    void testMcpProxyMessageBaseToString() {
        TestMessage message = new TestMessage();
        String str = message.toString();
        assertNotNull(str);
        assertTrue(str.contains("TestMessage"));
        assertTrue(str.contains("messageId="));
        assertTrue(str.contains("timestamp="));
    }
}
