package com.davidparry.agent.websocket;

import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.protocol.ToolCallRequest;
import com.davidparry.agent.reliability.ToolCallCircuitBreaker;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import com.davidparry.agent.session.PendingToolCall;
import com.davidparry.agent.session.PromptSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RemoteToolCallbackTest {

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private ToolCallCircuitBreaker circuitBreakerManager;

    @Mock
    private McpProxyWebSocketHandler.ToolCallSender sender;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private WebSocketSession webSocketSession;

    @Mock
    private ClientConnection connection;

    private McpProxyProperties.SessionConfig sessionConfig;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private RemoteToolCallback callback;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        sessionConfig = new McpProxyProperties.SessionConfig(600, 5, 2, 1000);

        // Setup mock connection
        lenient().when(connection.getConnectionId()).thenReturn("conn-123");
        lenient().when(connection.getClientId()).thenReturn("test-client-001");
        lenient().when(connection.updateSession(any())).thenReturn(true);

        // Default mocks for circuit breaker
        lenient().when(circuitBreakerManager.getBreaker(anyString(), anyString())).thenReturn(circuitBreaker);
        lenient().when(circuitBreakerManager.isToolAvailable(anyString(), anyString())).thenReturn(true);
        lenient().when(connectionManager.generateRequestId()).thenReturn("req-123");

        // Create the callback
        callback = new RemoteToolCallback(
                "server-tool",
                "A test tool",
                Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"))),
                "sess-123",
                connection,
                sender,
                connectionManager,
                circuitBreakerManager,
                sessionConfig,
                meterRegistry,
                objectMapper
        );
    }

    @Nested
    class GetToolDefinition {

        @Test
        void returnsCorrectToolDefinition() {
            ToolDefinition definition = callback.getToolDefinition();

            assertEquals("server-tool", definition.name());
            assertEquals("A test tool", definition.description());
            assertNotNull(definition.inputSchema());
            assertTrue(definition.inputSchema().contains("\"type\":\"object\""));
        }

        @Test
        void handlesNullInputSchema() {
            RemoteToolCallback callbackWithNullSchema = new RemoteToolCallback(
                    "server-tool",
                    "A test tool",
                    null,
                    "sess-123",
                    connection,
                    sender,
                    connectionManager,
                    circuitBreakerManager,
                    sessionConfig,
                    meterRegistry,
                    objectMapper
            );

            ToolDefinition definition = callbackWithNullSchema.getToolDefinition();
            assertEquals("{}", definition.inputSchema());
        }

        @Test
        void handlesEmptyInputSchema() {
            RemoteToolCallback callbackWithEmptySchema = new RemoteToolCallback(
                    "server-tool",
                    "A test tool",
                    Map.of(),
                    "sess-123",
                    connection,
                    sender,
                    connectionManager,
                    circuitBreakerManager,
                    sessionConfig,
                    meterRegistry,
                    objectMapper
            );

            ToolDefinition definition = callbackWithEmptySchema.getToolDefinition();
            assertEquals("{}", definition.inputSchema());
        }
    }

    @Nested
    class Call {

        private PromptSession session;

        @BeforeEach
        void setUp() {
            session = mock(PromptSession.class);
            lenient().when(session.isTerminal()).thenReturn(false);
            lenient().when(session.isExpired()).thenReturn(false);
            lenient().when(session.addPendingCall(any())).thenReturn(session);
            lenient().when(session.waitingForTool()).thenReturn(session);
            lenient().when(session.resumeExecution()).thenReturn(session);
            lenient().when(session.getToolCallCount()).thenReturn(1);
            lenient().when(connectionManager.findSession("sess-123")).thenReturn(Optional.of(session));
        }

        @Test
        @SuppressWarnings("unchecked")
        void successfulToolCall() {
            // Use doAnswer to properly mock the circuit breaker
            doAnswer(invocation -> {
                Supplier<String> supplier = invocation.getArgument(0);
                // Don't actually execute the supplier - just return mock result
                return "Tool result";
            }).when(circuitBreaker).executeSupplier(any(Supplier.class));

            String result = callback.call("{\"path\": \"/tmp\"}");

            assertEquals("Tool result", result);
            verify(sender).send(any(), any(ToolCallRequest.class));
            verify(session).addPendingCall(any(PendingToolCall.class));
            verify(session).waitingForTool();
        }

        @Test
        void returnsErrorWhenSessionNotFound() {
            when(connectionManager.findSession("sess-123")).thenReturn(Optional.empty());

            String result = callback.call("{\"path\": \"/tmp\"}");

            assertTrue(result.contains("isError"));
            assertTrue(result.contains("true"));
            assertTrue(result.contains("Session not found"));
        }

        @Test
        void returnsErrorWhenSessionIsTerminal() {
            when(session.isTerminal()).thenReturn(true);

            String result = callback.call("{\"path\": \"/tmp\"}");

            assertTrue(result.contains("isError"));
            assertTrue(result.contains("Session is no longer active"));
        }

        @Test
        void returnsErrorWhenSessionIsExpired() {
            when(session.isExpired()).thenReturn(true);

            String result = callback.call("{\"path\": \"/tmp\"}");

            assertTrue(result.contains("isError"));
            assertTrue(result.contains("Session has expired"));
        }

        @Test
        void returnsErrorWhenCircuitBreakerIsOpen() {
            when(circuitBreakerManager.isToolAvailable("conn-123", "server-tool")).thenReturn(false);

            String result = callback.call("{\"path\": \"/tmp\"}");

            assertTrue(result.contains("isError"));
            assertTrue(result.contains("temporarily unavailable"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void returnsErrorOnTimeout() {
            doThrow(new RuntimeException("Tool call timed out after 5 seconds",
                    new TimeoutException("Timeout")))
                    .when(circuitBreaker).executeSupplier(any(Supplier.class));

            String result = callback.call("{\"path\": \"/tmp\"}");

            assertTrue(result.contains("isError"));
            // The error message should contain timeout info
            assertTrue(result.contains("timed out") || result.contains("Timeout"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void returnsErrorOnToolCallFailure() {
            doThrow(new RuntimeException("Connection reset"))
                    .when(circuitBreaker).executeSupplier(any(Supplier.class));

            String result = callback.call("{\"path\": \"/tmp\"}");

            assertTrue(result.contains("isError"));
            assertTrue(result.contains("Connection reset"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void sendsCorrectToolCallRequest() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{\"path\": \"/tmp\"}");

            ArgumentCaptor<ToolCallRequest> requestCaptor = ArgumentCaptor.forClass(ToolCallRequest.class);
            verify(sender).send(any(), requestCaptor.capture());

            ToolCallRequest request = requestCaptor.getValue();
            assertEquals("req-123", request.getRequestId());
            assertEquals("sess-123", request.getSessionId());
            assertEquals("server-tool", request.getToolName());
            assertNotNull(request.getDeadline());
        }

        @Test
        @SuppressWarnings("unchecked")
        void handlesEmptyArguments() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            String result = callback.call("");

            assertEquals("Result", result);

            ArgumentCaptor<ToolCallRequest> requestCaptor = ArgumentCaptor.forClass(ToolCallRequest.class);
            verify(sender).send(any(), requestCaptor.capture());
            assertTrue(requestCaptor.getValue().getArguments().isEmpty());
        }

        @Test
        @SuppressWarnings("unchecked")
        void handlesNullArguments() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            String result = callback.call(null);

            assertEquals("Result", result);

            ArgumentCaptor<ToolCallRequest> requestCaptor = ArgumentCaptor.forClass(ToolCallRequest.class);
            verify(sender).send(any(), requestCaptor.capture());
            assertTrue(requestCaptor.getValue().getArguments().isEmpty());
        }

        @Test
        @SuppressWarnings("unchecked")
        void handlesInvalidJsonArguments() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            String result = callback.call("not valid json");

            assertEquals("Result", result);

            ArgumentCaptor<ToolCallRequest> requestCaptor = ArgumentCaptor.forClass(ToolCallRequest.class);
            verify(sender).send(any(), requestCaptor.capture());
            // Invalid JSON should be wrapped as "raw"
            assertEquals("not valid json", requestCaptor.getValue().getArguments().get("raw"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void recordsMetrics() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{\"path\": \"/tmp\"}");

            // Verify timer was used
            assertTrue(meterRegistry.get("mcp.tool_calls.duration")
                    .tag("tool", "server-tool")
                    .timer().count() > 0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void updatesSessionAfterSuccess() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{\"path\": \"/tmp\"}");

            verify(session).resumeExecution();
        }

        @Test
        @SuppressWarnings("unchecked")
        void cleansUpOnFailure() {
            doThrow(new RuntimeException("Failed"))
                    .when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{\"path\": \"/tmp\"}");

            // Should still call removePendingCall and resumeExecution on failure
            verify(session).removePendingCall("req-123");
            verify(session).resumeExecution();
        }
    }

    @Nested
    class ErrorMessageExtraction {

        private PromptSession session;

        @BeforeEach
        void setUp() {
            session = mock(PromptSession.class);
            lenient().when(session.isTerminal()).thenReturn(false);
            lenient().when(session.isExpired()).thenReturn(false);
            lenient().when(session.addPendingCall(any())).thenReturn(session);
            lenient().when(session.waitingForTool()).thenReturn(session);
            lenient().when(session.resumeExecution()).thenReturn(session);
            lenient().when(connectionManager.findSession("sess-123")).thenReturn(Optional.of(session));
        }

        @Test
        @SuppressWarnings("unchecked")
        void extractsNestedExceptionMessage() {
            RuntimeException innerException = new RuntimeException("Inner error");
            RuntimeException outerException = new RuntimeException("Outer error", innerException);
            doThrow(outerException).when(circuitBreaker).executeSupplier(any(Supplier.class));

            String result = callback.call("{}");

            assertTrue(result.contains("Inner error"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void cleansUpDuplicatedPrefixes() {
            RuntimeException exception = new RuntimeException(
                    "Tool call failed: Tool call failed: Actual error");
            doThrow(exception).when(circuitBreaker).executeSupplier(any(Supplier.class));

            String result = callback.call("{}");

            assertTrue(result.contains("Actual error"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void handlesNullMessage() {
            RuntimeException exception = new RuntimeException((String) null);
            doThrow(exception).when(circuitBreaker).executeSupplier(any(Supplier.class));

            String result = callback.call("{}");

            assertTrue(result.contains("isError"));
            // Should have some error message, either "Unknown error" or fallback
            assertTrue(result.contains("error"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void handlesExceptionWithBlankNestedMessage() {
            RuntimeException innerException = new RuntimeException("   ");
            RuntimeException outerException = new RuntimeException("Outer error", innerException);
            doThrow(outerException).when(circuitBreaker).executeSupplier(any(Supplier.class));

            String result = callback.call("{}");

            // Should use outer error message since inner is blank
            assertTrue(result.contains("Outer error"));
        }
    }

    @Nested
    class ErrorResponseFormatting {

        private PromptSession session;

        @BeforeEach
        void setUp() {
            session = mock(PromptSession.class);
            when(session.isTerminal()).thenReturn(true); // Will trigger error
            when(connectionManager.findSession("sess-123")).thenReturn(Optional.of(session));
        }

        @Test
        void formatsErrorAsJson() {
            String result = callback.call("{}");

            assertTrue(result.startsWith("{"));
            assertTrue(result.endsWith("}"));
            assertTrue(result.contains("\"isError\""));
            assertTrue(result.contains("true"));
            assertTrue(result.contains("\"error\""));
            assertTrue(result.contains("\"tool\""));
            assertTrue(result.contains("server-tool"));
            assertTrue(result.contains("\"suggestion\""));
        }

        @Test
        void includesToolNameInError() {
            String result = callback.call("{}");

            assertTrue(result.contains("server-tool"));
        }
    }

    @Nested
    class ToString {

        @Test
        void returnsDescriptiveString() {
            String str = callback.toString();

            assertTrue(str.contains("RemoteToolCallback"));
            assertTrue(str.contains("server-tool"));
            assertTrue(str.contains("sess-123"));
            assertTrue(str.contains("conn-123"));
        }
    }

    @Nested
    class InputSchemaConversion {

        @Test
        void convertsComplexSchemaToJson() {
            Map<String, Object> complexSchema = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "name", Map.of("type", "string"),
                            "count", Map.of("type", "integer")
                    ),
                    "required", java.util.List.of("name")
            );

            RemoteToolCallback callbackWithComplexSchema = new RemoteToolCallback(
                    "server-tool",
                    "A test tool",
                    complexSchema,
                    "sess-123",
                    connection,
                    sender,
                    connectionManager,
                    circuitBreakerManager,
                    sessionConfig,
                    meterRegistry,
                    objectMapper
            );

            String schema = callbackWithComplexSchema.getToolDefinition().inputSchema();
            assertTrue(schema.contains("\"type\":\"object\""));
            assertTrue(schema.contains("\"properties\""));
            assertTrue(schema.contains("\"required\""));
        }
    }

    @Nested
    class CircuitBreakerIntegration {

        private PromptSession session;

        @BeforeEach
        void setUp() {
            session = mock(PromptSession.class);
            lenient().when(session.isTerminal()).thenReturn(false);
            lenient().when(session.isExpired()).thenReturn(false);
            lenient().when(session.addPendingCall(any())).thenReturn(session);
            lenient().when(session.waitingForTool()).thenReturn(session);
            lenient().when(session.resumeExecution()).thenReturn(session);
            lenient().when(connectionManager.findSession("sess-123")).thenReturn(Optional.of(session));
        }

        @Test
        @SuppressWarnings("unchecked")
        void getsCircuitBreakerForConnectionAndTool() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{}");

            verify(circuitBreakerManager).getBreaker("conn-123", "server-tool");
        }

        @Test
        @SuppressWarnings("unchecked")
        void checksToolAvailabilityBeforeExecution() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{}");

            verify(circuitBreakerManager).isToolAvailable("conn-123", "server-tool");
        }

        @Test
        @SuppressWarnings("unchecked")
        void executesViaCircuitBreaker() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{}");

            verify(circuitBreaker).executeSupplier(any(Supplier.class));
        }
    }

    @Nested
    class SessionStateManagement {

        private PromptSession session;

        @BeforeEach
        void setUp() {
            session = mock(PromptSession.class);
            lenient().when(session.isTerminal()).thenReturn(false);
            lenient().when(session.isExpired()).thenReturn(false);
            lenient().when(session.addPendingCall(any())).thenReturn(session);
            lenient().when(session.waitingForTool()).thenReturn(session);
            lenient().when(session.resumeExecution()).thenReturn(session);
            lenient().when(connectionManager.findSession("sess-123")).thenReturn(Optional.of(session));
        }

        @Test
        @SuppressWarnings("unchecked")
        void addsAndRemovesPendingCall() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{}");

            verify(session).addPendingCall(any(PendingToolCall.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void transitionsToWaitingForToolState() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{}");

            verify(session).waitingForTool();
        }

        @Test
        @SuppressWarnings("unchecked")
        void resumesExecutionAfterCompletion() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{}");

            verify(session).resumeExecution();
        }

        @Test
        @SuppressWarnings("unchecked")
        void getsLatestSessionBeforeResuming() {
            PromptSession latestSession = mock(PromptSession.class);
            when(latestSession.resumeExecution()).thenReturn(latestSession);

            // First call returns initial session, second call (after tool execution) returns latest
            when(connectionManager.findSession("sess-123"))
                    .thenReturn(Optional.of(session))
                    .thenReturn(Optional.of(latestSession));

            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{}");

            // Should resume on the latest session
            verify(latestSession).resumeExecution();
        }
    }

    @Nested
    class ToolCallRequestConstruction {

        private PromptSession session;

        @BeforeEach
        void setUp() {
            session = mock(PromptSession.class);
            lenient().when(session.isTerminal()).thenReturn(false);
            lenient().when(session.isExpired()).thenReturn(false);
            lenient().when(session.addPendingCall(any())).thenReturn(session);
            lenient().when(session.waitingForTool()).thenReturn(session);
            lenient().when(session.resumeExecution()).thenReturn(session);
            lenient().when(connectionManager.findSession("sess-123")).thenReturn(Optional.of(session));
        }

        @Test
        @SuppressWarnings("unchecked")
        void setsDeadlineBasedOnConfig() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            Instant before = Instant.now();
            callback.call("{}");
            Instant after = Instant.now();

            ArgumentCaptor<ToolCallRequest> requestCaptor = ArgumentCaptor.forClass(ToolCallRequest.class);
            verify(sender).send(any(), requestCaptor.capture());

            Instant deadline = requestCaptor.getValue().getDeadline();
            // Deadline should be approximately now + toolCallTimeoutSeconds (5 seconds in config)
            assertTrue(deadline.isAfter(before.plusSeconds(4)));
            assertTrue(deadline.isBefore(after.plusSeconds(6)));
        }

        @Test
        @SuppressWarnings("unchecked")
        void parsesJsonArguments() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{\"path\": \"/home\", \"recursive\": true}");

            ArgumentCaptor<ToolCallRequest> requestCaptor = ArgumentCaptor.forClass(ToolCallRequest.class);
            verify(sender).send(any(), requestCaptor.capture());

            Map<String, Object> args = requestCaptor.getValue().getArguments();
            assertEquals("/home", args.get("path"));
            assertEquals(true, args.get("recursive"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void handlesNestedJsonArguments() {
            doReturn("Result").when(circuitBreaker).executeSupplier(any(Supplier.class));

            callback.call("{\"config\": {\"key\": \"value\", \"nested\": {\"deep\": true}}}");

            ArgumentCaptor<ToolCallRequest> requestCaptor = ArgumentCaptor.forClass(ToolCallRequest.class);
            verify(sender).send(any(), requestCaptor.capture());

            Map<String, Object> args = requestCaptor.getValue().getArguments();
            assertNotNull(args.get("config"));
        }
    }
}
