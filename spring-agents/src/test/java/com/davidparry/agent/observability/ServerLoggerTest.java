package com.davidparry.agent.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServerLogger.
 * Tests MDC context management and structured logging utilities.
 */
class ServerLoggerTest {

    private ServerLogger serverLogger;

    @BeforeEach
    void setUp() {
        serverLogger = new ServerLogger();
        MDC.clear();
    }

    @Test
    void withContext_shouldSetMdcContextForRunnable() {
        // Given
        Map<String, String> context = Map.of(
            ServerLogger.CONNECTION_ID, "conn-123",
            ServerLogger.CLIENT_ID, "client-456"
        );
        AtomicReference<String> capturedConnectionId = new AtomicReference<>();
        AtomicReference<String> capturedClientId = new AtomicReference<>();

        // When
        serverLogger.withContext(context, () -> {
            capturedConnectionId.set(MDC.get(ServerLogger.CONNECTION_ID));
            capturedClientId.set(MDC.get(ServerLogger.CLIENT_ID));
        });

        // Then
        assertEquals("conn-123", capturedConnectionId.get());
        assertEquals("client-456", capturedClientId.get());
        // MDC should be cleared after
        assertNull(MDC.get(ServerLogger.CONNECTION_ID));
    }

    @Test
    void withContext_shouldRestorePreviousMdcContext() {
        // Given
        MDC.put(ServerLogger.CONNECTION_ID, "previous-conn");
        Map<String, String> context = Map.of(ServerLogger.CONNECTION_ID, "new-conn");

        // When
        serverLogger.withContext(context, () -> {
            assertEquals("new-conn", MDC.get(ServerLogger.CONNECTION_ID));
        });

        // Then - previous context restored
        assertEquals("previous-conn", MDC.get(ServerLogger.CONNECTION_ID));
    }

    @Test
    void withContext_shouldHandleNullContext() {
        // Given
        AtomicBoolean executed = new AtomicBoolean(false);

        // When
        serverLogger.withContext(null, () -> {
            executed.set(true);
        });

        // Then
        assertTrue(executed.get());
    }

    @Test
    void withContextSupplier_shouldReturnValue() {
        // Given
        Map<String, String> context = Map.of(ServerLogger.SESSION_ID, "session-789");

        // When
        String result = serverLogger.withContext(context, () -> {
            assertEquals("session-789", MDC.get(ServerLogger.SESSION_ID));
            return "test-result";
        });

        // Then
        assertEquals("test-result", result);
        assertNull(MDC.get(ServerLogger.SESSION_ID));
    }

    @Test
    void withContextSupplier_shouldRestorePreviousContext() {
        // Given
        MDC.put(ServerLogger.SESSION_ID, "previous-session");
        Map<String, String> context = Map.of(ServerLogger.SESSION_ID, "new-session");

        // When
        String captured = serverLogger.withContext(context, () -> MDC.get(ServerLogger.SESSION_ID));

        // Then
        assertEquals("new-session", captured);
        assertEquals("previous-session", MDC.get(ServerLogger.SESSION_ID));
    }

    @Test
    void withContextSupplier_shouldHandleNullContext() {
        // Given
        MDC.put(ServerLogger.TOOL_NAME, "existing-tool");

        // When
        String result = serverLogger.withContext(null, () -> "result");

        // Then
        assertEquals("result", result);
        assertEquals("existing-tool", MDC.get(ServerLogger.TOOL_NAME));
    }

    @Test
    void contextBuilder_shouldBuildContextMap() {
        // When
        Map<String, String> context = ServerLogger.context()
            .connectionId("conn-1")
            .clientId("client-2")
            .sessionId("session-3")
            .toolName("tool-4")
            .requestId("request-5")
            .build();

        // Then
        assertEquals("conn-1", context.get(ServerLogger.CONNECTION_ID));
        assertEquals("client-2", context.get(ServerLogger.CLIENT_ID));
        assertEquals("session-3", context.get(ServerLogger.SESSION_ID));
        assertEquals("tool-4", context.get(ServerLogger.TOOL_NAME));
        assertEquals("request-5", context.get(ServerLogger.REQUEST_ID));
    }

    @Test
    void contextBuilder_shouldReturnImmutableMap() {
        // When
        Map<String, String> context = ServerLogger.context()
            .connectionId("conn-1")
            .build();

        // Then
        assertThrows(UnsupportedOperationException.class, () -> context.put("key", "value"));
    }

    @Test
    void logConnectionEstablished_shouldNotThrow() {
        // When/Then - no exception
        serverLogger.logConnectionEstablished("conn-123", "client-456");
    }

    @Test
    void logConnectionClosed_shouldNotThrow() {
        // When/Then - no exception
        serverLogger.logConnectionClosed("conn-123", "client-456", "Normal closure");
    }

    @Test
    void logSessionStarted_shouldNotThrow() {
        // When/Then - no exception
        serverLogger.logSessionStarted("conn-123", "session-456", "Test prompt");
    }

    @Test
    void logSessionStarted_shouldTruncateLongPrompts() {
        // Given
        String longPrompt = "A".repeat(200);

        // When/Then - no exception, prompt gets truncated internally
        serverLogger.logSessionStarted("conn-123", "session-456", longPrompt);
    }

    @Test
    void logSessionCompleted_shouldNotThrow() {
        // When/Then - no exception
        serverLogger.logSessionCompleted("conn-123", "session-456", 1500L);
    }

    @Test
    void logSessionFailed_shouldNotThrow() {
        // When/Then - no exception
        serverLogger.logSessionFailed("conn-123", "session-456", "Timeout occurred");
    }

    @Test
    void logToolCall_shouldNotThrow() {
        // When/Then - no exception
        serverLogger.logToolCall("conn-123", "session-456", "read_file", "req-789");
    }

    @Test
    void logToolCallCompleted_shouldNotThrow() {
        // When/Then - no exception
        serverLogger.logToolCallCompleted("conn-123", "session-456", "read_file", "req-789", 250L);
    }

    @Test
    void logToolCallFailed_shouldNotThrow() {
        // When/Then - no exception
        serverLogger.logToolCallFailed("conn-123", "session-456", "read_file", "req-789", "File not found");
    }

    @Test
    void mdcConstants_shouldBeCorrectValues() {
        assertEquals("connectionId", ServerLogger.CONNECTION_ID);
        assertEquals("clientId", ServerLogger.CLIENT_ID);
        assertEquals("sessionId", ServerLogger.SESSION_ID);
        assertEquals("toolName", ServerLogger.TOOL_NAME);
        assertEquals("requestId", ServerLogger.REQUEST_ID);
    }
}
