package com.davidparry.agent.protocol;

import com.davidparry.agent.protocol.dto.NextAgentStatus;
import com.davidparry.agent.protocol.dto.SessionMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SessionResult class.
 */
class SessionResultTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testSessionResultCreation() {
        Instant timestamp = Instant.now();
        JsonNode content = objectMapper.createObjectNode().put("result", "success");
        SessionMetrics metrics = new SessionMetrics(100L, 50, 10);

        SessionResult result = new SessionResult(
                "msg-123",
                timestamp,
                "session-456",
                true,
                content,
                null,
                5,
                1000L,
                metrics,
                "next-agent"
        );

        assertEquals("msg-123", result.getMessageId());
        assertEquals(timestamp, result.getTimestamp());
        assertEquals("session-456", result.getSessionId());
        assertTrue(result.isSuccess());
        assertEquals(content, result.getContent());
        assertNull(result.getErrorMessage());
        assertEquals(5, result.getToolCallsExecuted());
        assertEquals(1000L, result.getTotalDurationMs());
        assertEquals(metrics, result.getMetrics());
        assertEquals("next-agent", result.getNextAgent());
    }

    @Test
    void testSessionResultWithNullNextAgent() {
        Instant timestamp = Instant.now();
        JsonNode content = objectMapper.createObjectNode().put("result", "success");

        SessionResult result = new SessionResult(
                "msg-123",
                timestamp,
                "session-456",
                true,
                content,
                null,
                3,
                500L,
                null,
                null
        );

        assertNull(result.getNextAgent());
    }

    @Test
    void testSessionResultWithErrorMessage() {
        Instant timestamp = Instant.now();

        SessionResult result = new SessionResult(
                "msg-123",
                timestamp,
                "session-456",
                false,
                null,
                "Something went wrong",
                0,
                100L,
                null,
                null
        );

        assertFalse(result.isSuccess());
        assertNull(result.getContent());
        assertEquals("Something went wrong", result.getErrorMessage());
    }

    @Test
    void testSessionResultBuilder() {
        Instant timestamp = Instant.now();
        JsonNode content = objectMapper.createObjectNode().put("data", "test");
        SessionMetrics metrics = new SessionMetrics(200L, 100, 20);

        SessionResult result = SessionResult.builder()
                .messageId("msg-789")
                .timestamp(timestamp)
                .sessionId("session-101")
                .success(true)
                .content(content)
                .errorMessage(null)
                .toolCallsExecuted(10)
                .totalDurationMs(2000L)
                .metrics(metrics)
                .nextAgent("follow-up-agent")
                .build();

        assertEquals("msg-789", result.getMessageId());
        assertEquals(timestamp, result.getTimestamp());
        assertEquals("session-101", result.getSessionId());
        assertTrue(result.isSuccess());
        assertEquals(content, result.getContent());
        assertNull(result.getErrorMessage());
        assertEquals(10, result.getToolCallsExecuted());
        assertEquals(2000L, result.getTotalDurationMs());
        assertEquals(metrics, result.getMetrics());
        assertEquals("follow-up-agent", result.getNextAgent());
    }

    @Test
    void testSessionResultBuilderWithNullNextAgent() {
        SessionResult result = SessionResult.builder()
                .messageId("msg-001")
                .sessionId("session-001")
                .success(true)
                .build();

        assertNull(result.getNextAgent());
    }

    @Test
    void testSessionResultToString() {
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
        JsonNode content = objectMapper.createObjectNode().put("key", "value");

        SessionResult result = new SessionResult(
                "msg-123",
                timestamp,
                "session-456",
                true,
                content,
                null,
                5,
                1000L,
                null,
                "next-agent"
        );

        String toString = result.toString();

        assertTrue(toString.contains("msg-123"));
        assertTrue(toString.contains("session-456"));
        assertTrue(toString.contains("true"));
        assertTrue(toString.contains("1000"));
        assertTrue(toString.contains("next-agent"));
    }

    @Test
    void testSessionResultToStringWithNullNextAgent() {
        SessionResult result = new SessionResult(
                "msg-123",
                Instant.now(),
                "session-456",
                true,
                null,
                null,
                0,
                0L,
                null,
                null
        );

        String toString = result.toString();

        assertTrue(toString.contains("nextAgent='null'"));
    }

    @Test
    void testSessionResultJsonSerializationRoundTrip() throws Exception {
        SessionResult original = SessionResult.builder()
                .sessionId("session-456")
                .success(true)
                .content(objectMapper.createObjectNode().put("result", "done"))
                .toolCallsExecuted(5)
                .totalDurationMs(1000L)
                .nextAgent("next-agent")
                .build();

        String json = objectMapper.writeValueAsString(original);
        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionResult.class, deserialized);
        SessionResult result = (SessionResult) deserialized;
        assertEquals("session-456", result.getSessionId());
        assertTrue(result.isSuccess());
        assertEquals(5, result.getToolCallsExecuted());
        assertEquals(1000L, result.getTotalDurationMs());
        assertEquals("next-agent", result.getNextAgent());
    }

    @Test
    void testSessionResultJsonDeserializationWithNextAgent() throws Exception {
        String json = """
                {
                    "type": "session_result",
                    "messageId": "msg-123",
                    "timestamp": "2024-01-15T10:30:00Z",
                    "sessionId": "session-456",
                    "success": true,
                    "content": {"result": "done"},
                    "errorMessage": null,
                    "toolCallsExecuted": 5,
                    "totalDurationMs": 1000,
                    "metrics": null,
                    "next_agent": "next-agent"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionResult.class, deserialized);
        SessionResult result = (SessionResult) deserialized;
        assertEquals("msg-123", result.getMessageId());
        assertEquals("session-456", result.getSessionId());
        assertTrue(result.isSuccess());
        assertEquals(5, result.getToolCallsExecuted());
        assertEquals(1000L, result.getTotalDurationMs());
        assertEquals("next-agent", result.getNextAgent());
    }

    @Test
    void testSessionResultJsonDeserializationWithNullNextAgent() throws Exception {
        String json = """
                {
                    "type": "session_result",
                    "messageId": "msg-123",
                    "timestamp": "2024-01-15T10:30:00Z",
                    "sessionId": "session-456",
                    "success": true,
                    "content": null,
                    "errorMessage": null,
                    "toolCallsExecuted": 0,
                    "totalDurationMs": 100,
                    "metrics": null,
                    "next_agent": null
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionResult.class, deserialized);
        SessionResult result = (SessionResult) deserialized;
        assertNull(result.getNextAgent());
    }

    @Test
    void testSessionResultJsonDeserializationWithoutNextAgent() throws Exception {
        String json = """
                {
                    "type": "session_result",
                    "messageId": "msg-123",
                    "timestamp": "2024-01-15T10:30:00Z",
                    "sessionId": "session-456",
                    "success": true,
                    "content": null,
                    "errorMessage": null,
                    "toolCallsExecuted": 0,
                    "totalDurationMs": 100,
                    "metrics": null
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionResult.class, deserialized);
        SessionResult result = (SessionResult) deserialized;
        assertNull(result.getNextAgent());
    }

    @Test
    void testSessionResultJsonDeserializationWithResponseAlias() throws Exception {
        // Test backward compatibility with "response" field alias for "content"
        String json = """
                {
                    "type": "session_result",
                    "messageId": "msg-123",
                    "timestamp": "2024-01-15T10:30:00Z",
                    "sessionId": "session-456",
                    "success": true,
                    "response": {"data": "test"},
                    "errorMessage": null,
                    "toolCallsExecuted": 2,
                    "totalDurationMs": 500,
                    "metrics": null,
                    "next_agent": "another-agent"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionResult.class, deserialized);
        SessionResult result = (SessionResult) deserialized;
        assertNotNull(result.getContent());
        assertEquals("test", result.getContent().get("data").asText());
        assertEquals("another-agent", result.getNextAgent());
    }

    @Test
    void testSessionResultWithMetrics() throws Exception {
        String json = """
                {
                    "type": "session_result",
                    "messageId": "msg-123",
                    "timestamp": "2024-01-15T10:30:00Z",
                    "sessionId": "session-456",
                    "success": true,
                    "content": null,
                    "errorMessage": null,
                    "toolCallsExecuted": 3,
                    "totalDurationMs": 750,
                    "metrics": {
                        "durationMs": 750,
                        "toolCallCount": 3,
                        "tokenCount": 150
                    },
                    "next_agent": null
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionResult.class, deserialized);
        SessionResult result = (SessionResult) deserialized;
        assertNotNull(result.getMetrics());
        assertEquals(3, result.getToolCallsExecuted());
        assertEquals(750L, result.getTotalDurationMs());
    }

    @Test
    void testSessionResultBuilderAllFields() {
        Instant timestamp = Instant.now();
        JsonNode content = objectMapper.createObjectNode();
        SessionMetrics metrics = new SessionMetrics(50L, 25, 5);

        SessionResult result = SessionResult.builder()
                .messageId("msg-full")
                .timestamp(timestamp)
                .sessionId("session-full")
                .success(false)
                .content(content)
                .errorMessage("Test error")
                .toolCallsExecuted(7)
                .totalDurationMs(3000L)
                .metrics(metrics)
                .nextAgent("final-agent")
                .build();

        assertEquals("msg-full", result.getMessageId());
        assertEquals(timestamp, result.getTimestamp());
        assertEquals("session-full", result.getSessionId());
        assertFalse(result.isSuccess());
        assertEquals(content, result.getContent());
        assertEquals("Test error", result.getErrorMessage());
        assertEquals(7, result.getToolCallsExecuted());
        assertEquals(3000L, result.getTotalDurationMs());
        assertEquals(metrics, result.getMetrics());
        assertEquals("final-agent", result.getNextAgent());
    }

    @Test
    void testSessionResultWithEndChainStatus() {
        // When LLM succeeds and Agent's nextAgent is null/empty, use END_CHAIN
        SessionResult result = SessionResult.builder()
                .messageId("msg-end")
                .sessionId("session-end")
                .success(true)
                .nextAgent(NextAgentStatus.END_CHAIN.getValue())
                .build();

        assertTrue(result.isSuccess());
        assertEquals("END_CHAIN", result.getNextAgent());
        assertEquals(NextAgentStatus.END_CHAIN.getValue(), result.getNextAgent());
    }

    @Test
    void testSessionResultWithFailedAgentStatus() {
        // When LLM processing fails, use FAILED_AGENT
        SessionResult result = SessionResult.builder()
                .messageId("msg-fail")
                .sessionId("session-fail")
                .success(false)
                .errorMessage("LLM processing failed")
                .nextAgent(NextAgentStatus.FAILED_AGENT.getValue())
                .build();

        assertFalse(result.isSuccess());
        assertEquals("FAILED_AGENT", result.getNextAgent());
        assertEquals(NextAgentStatus.FAILED_AGENT.getValue(), result.getNextAgent());
    }

    @Test
    void testSessionResultJsonDeserializationWithEndChain() throws Exception {
        String json = """
                {
                    "type": "session_result",
                    "messageId": "msg-123",
                    "timestamp": "2024-01-15T10:30:00Z",
                    "sessionId": "session-456",
                    "success": true,
                    "content": {"result": "completed"},
                    "errorMessage": null,
                    "toolCallsExecuted": 5,
                    "totalDurationMs": 1000,
                    "metrics": null,
                    "next_agent": "END_CHAIN"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionResult.class, deserialized);
        SessionResult result = (SessionResult) deserialized;
        assertTrue(result.isSuccess());
        assertEquals(NextAgentStatus.END_CHAIN.getValue(), result.getNextAgent());
    }

    @Test
    void testSessionResultJsonDeserializationWithFailedAgent() throws Exception {
        String json = """
                {
                    "type": "session_result",
                    "messageId": "msg-123",
                    "timestamp": "2024-01-15T10:30:00Z",
                    "sessionId": "session-456",
                    "success": false,
                    "content": null,
                    "errorMessage": "Processing failed",
                    "toolCallsExecuted": 0,
                    "totalDurationMs": 100,
                    "metrics": null,
                    "next_agent": "FAILED_AGENT"
                }
                """;

        McpProxyMessage deserialized = objectMapper.readValue(json, McpProxyMessage.class);

        assertInstanceOf(SessionResult.class, deserialized);
        SessionResult result = (SessionResult) deserialized;
        assertFalse(result.isSuccess());
        assertEquals(NextAgentStatus.FAILED_AGENT.getValue(), result.getNextAgent());
    }
}
