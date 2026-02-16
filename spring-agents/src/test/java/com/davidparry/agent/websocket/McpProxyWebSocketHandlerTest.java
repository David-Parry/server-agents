package com.davidparry.agent.websocket;

import com.davidparry.agent.config.AgentConfiguration;
import com.davidparry.agent.config.DatabaseAgentConfigurationProvider;
import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.repository.CustomerAgentTypeRepository;
import com.davidparry.agent.observability.CustomerMetricsService;
import com.davidparry.agent.observability.ServerMetrics;
import com.davidparry.agent.pojo.ExecutionResult;
import com.davidparry.agent.protocol.*;
import com.davidparry.agent.protocol.dto.AgentType;
import com.davidparry.agent.protocol.dto.ToolDefinition;
import com.davidparry.agent.service.PromptExecutionService;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import com.davidparry.agent.session.PendingToolCall;
import com.davidparry.agent.session.PromptSession;
import com.davidparry.agent.session.SessionState;
import com.davidparry.agent.transform.SchemaMerger;
import com.davidparry.agent.transform.TemplateProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpProxyWebSocketHandlerTest {

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private PromptExecutionService promptExecutionService;

    @Mock
    private RemoteToolCallbackFactory toolCallbackFactory;

    @Mock
    private TemplateProcessor templateProcessor;

    @Mock
    private SchemaMerger schemaMerger;

    @Mock
    private DatabaseAgentConfigurationProvider agentConfigurationProvider;

    @Mock
    private CustomerMetricsService customerMetricsService;

    @Mock
    private ServerMetrics serverMetrics;

    @Mock
    private CustomerAgentTypeRepository customerAgentTypeRepository;

    private McpProxyProperties properties;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private McpProxyWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        properties = new McpProxyProperties(
                true,
                new McpProxyProperties.ConnectionConfig(300, 30, 5, 10, false),
                new McpProxyProperties.SessionConfig(600, 60, 2, 1000),
                new McpProxyProperties.StreamingConfig(true, 100, 50),
                new McpProxyProperties.CircuitBreakerConfig(5, 3, 30, 3),
                new McpProxyProperties.WebSocketConfig("/agent", List.of("*"), 0, 0, 0)
        );

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        meterRegistry = new SimpleMeterRegistry();

        handler = new McpProxyWebSocketHandler(
                connectionManager,
                promptExecutionService,
                toolCallbackFactory,
                properties,
                objectMapper,
                meterRegistry,
                templateProcessor,
                schemaMerger,
                agentConfigurationProvider,
                customerMetricsService,
                serverMetrics,
                customerAgentTypeRepository
        );
    }

    private static final String TEST_CUSTOMER_ID = "550e8400-e29b-41d4-a716-446655440000";

    private WebSocketSession createMockWebSocketSession(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        lenient().when(session.getId()).thenReturn(sessionId);
        lenient().when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ApiKeyHandshakeInterceptor.CLIENT_ID_ATTRIBUTE, TEST_CUSTOMER_ID);
        lenient().when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    private ClientConnection createMockClientConnection(WebSocketSession wsSession) {
        ClientConnection connection = mock(ClientConnection.class);
        lenient().when(connection.getConnectionId()).thenReturn("conn-123");
        lenient().when(connection.getClientId()).thenReturn(TEST_CUSTOMER_ID);
        lenient().when(connection.getWebSocketSession()).thenReturn(wsSession);
        lenient().when(connection.canAcceptSession()).thenReturn(true);
        lenient().when(connection.addSession(any())).thenReturn(true);
        return connection;
    }

    private ClientConnection createRealClientConnection(WebSocketSession wsSession) {
        return ClientConnection.builder()
                .connectionId("conn-123")
                .clientId(TEST_CUSTOMER_ID)
                .webSocketSession(wsSession)
                .maxConcurrentSessions(10)
                .build();
    }

    @Nested
    class AfterConnectionEstablished {

        @Test
        void sendsConnectionEstablishedMessage() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");
            ClientConnection connection = createRealClientConnection(wsSession);
            when(connectionManager.createConnection(wsSession, TEST_CUSTOMER_ID)).thenReturn(connection);

            handler.afterConnectionEstablished(wsSession);

            verify(connectionManager).createConnection(wsSession, TEST_CUSTOMER_ID);
            verify(serverMetrics).recordConnectionOpened();
            verify(customerMetricsService).recordConnectionOpened(TEST_CUSTOMER_ID);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("connection_established"));
            assertTrue(payload.contains("conn-123"));
        }

        @Test
        void includesCapabilitiesInConnectionEstablishedMessage() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-456");
            ClientConnection connection = createRealClientConnection(wsSession);
            when(connectionManager.createConnection(wsSession, TEST_CUSTOMER_ID)).thenReturn(connection);

            handler.afterConnectionEstablished(wsSession);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            JsonNode json = objectMapper.readTree(payload);

            assertTrue(json.has("capabilities"));
            JsonNode capabilities = json.get("capabilities");
            assertTrue(capabilities.get("streamingSupported").asBoolean());
            assertEquals(10, capabilities.get("maxConcurrentSessions").asInt());
        }
    }

    @Nested
    class HandleTextMessage {

        private WebSocketSession wsSession;
        private ClientConnection connection;

        @BeforeEach
        void setUp() {
            wsSession = createMockWebSocketSession("ws-123");
            connection = createMockClientConnection(wsSession);
            lenient().when(connectionManager.getConnectionByWebSocketId("ws-123")).thenReturn(Optional.of(connection));
        }

        @Test
        void handleCreateSession_withValidRequest() throws Exception {
            Agent agent = new Agent(
                    "test-agent",
                    "Test agent description",
                    AgentType.ANALYST,
                    "Follow instructions",
                    null, null,
                    List.of(),
                    "{}",
                    null
            );

            ToolDefinition tool = ToolDefinition.builder()
                    .name("server-tool")
                    .description("A test tool")
                    .inputSchema(Map.of("type", "object"))
                    .build();

            CreateSession createSession = CreateSession.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .agent(agent)
                    .tools(List.of(tool))
                    .streamResponse(true)
                    .maxDurationSeconds(300)
                    .build();

            String json = objectMapper.writeValueAsString(createSession);
            TextMessage message = new TextMessage(json);

            when(connectionManager.findSession("sess-123")).thenReturn(Optional.empty());
            when(customerAgentTypeRepository.hasAccess(UUID.fromString(TEST_CUSTOMER_ID), AgentType.ANALYST))
                    .thenReturn(true);
            when(agentConfigurationProvider.getConfiguration(AgentType.ANALYST))
                    .thenReturn(new AgentConfiguration("System prompt", "claude-3"));
            when(templateProcessor.processTemplate(anyString(), any(JsonNode.class))).thenAnswer(inv -> inv.getArgument(0));
            when(schemaMerger.mergePropertiesWithClass(anyString(), any())).thenReturn("{}");

            ToolCallback mockCallback = mock(ToolCallback.class);
            when(toolCallbackFactory.createCallback(any(), anyString(), any(), any())).thenReturn(mockCallback);

            // Mock the prompt execution to avoid NPE
            ExecutionResult mockResult = mock(ExecutionResult.class);
            PromptSession mockUpdatedSession = mock(PromptSession.class);
            when(mockResult.updatedSession()).thenReturn(mockUpdatedSession);
            when(promptExecutionService.executePrompt(any()))
                    .thenReturn(CompletableFuture.completedFuture(mockResult));

            handler.handleTextMessage(wsSession, message);

            verify(connectionManager).findSession("sess-123");
            verify(customerAgentTypeRepository).hasAccess(UUID.fromString(TEST_CUSTOMER_ID), AgentType.ANALYST);
            verify(agentConfigurationProvider).getConfiguration(AgentType.ANALYST);
            verify(connectionManager).registerSession(any(PromptSession.class));
            verify(serverMetrics).recordSessionStarted();
            verify(customerMetricsService).recordSessionCreated(TEST_CUSTOMER_ID);
        }

        @Test
        void handleCreateSession_rejectsDuplicateSessionId() throws Exception {
            Agent agent = new Agent(
                    "test-agent",
                    "Test agent description",
                    AgentType.ANALYST,
                    "Follow instructions",
                    null, null,
                    List.of(),
                    "{}",
                    null
            );

            CreateSession createSession = CreateSession.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .agent(agent)
                    .tools(List.of())
                    .build();

            String json = objectMapper.writeValueAsString(createSession);
            TextMessage message = new TextMessage(json);

            // Create a mock existing session
            PromptSession existingSession = mock(PromptSession.class);
            when(connectionManager.findSession("sess-123")).thenReturn(Optional.of(existingSession));

            handler.handleTextMessage(wsSession, message);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("error"));
            assertTrue(payload.contains("INVALID_MESSAGE"));
        }

        @Test
        void handleCreateSession_rejectsMissingSessionId() throws Exception {
            Agent agent = new Agent(
                    "test-agent",
                    "Test agent description",
                    AgentType.ANALYST,
                    "Follow instructions",
                    null, null,
                    List.of(),
                    "{}",
                    null
            );

            CreateSession createSession = CreateSession.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId(null)
                    .agent(agent)
                    .tools(List.of())
                    .build();

            String json = objectMapper.writeValueAsString(createSession);
            TextMessage message = new TextMessage(json);

            handler.handleTextMessage(wsSession, message);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("error"));
            assertTrue(payload.contains("sessionId is required"));
        }

        @Test
        void handleCreateSession_rejectsInvalidToolNameFormat() throws Exception {
            Agent agent = new Agent(
                    "test-agent",
                    "Test agent description",
                    AgentType.ANALYST,
                    "Follow instructions",
                    null, null,
                    List.of(),
                    "{}",
                    null
            );

            ToolDefinition invalidTool = ToolDefinition.builder()
                    .name("invalid_tool_name")  // Missing hyphen separator
                    .description("A test tool")
                    .inputSchema(Map.of("type", "object"))
                    .build();

            CreateSession createSession = CreateSession.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .agent(agent)
                    .tools(List.of(invalidTool))
                    .build();

            String json = objectMapper.writeValueAsString(createSession);
            TextMessage message = new TextMessage(json);

            when(connectionManager.findSession("sess-123")).thenReturn(Optional.empty());

            handler.handleTextMessage(wsSession, message);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("error"));
            assertTrue(payload.contains("server-toolname"));
        }

        @Test
        void handleCreateSession_rejectsDuplicateToolNames() throws Exception {
            Agent agent = new Agent(
                    "test-agent",
                    "Test agent description",
                    AgentType.ANALYST,
                    "Follow instructions",
                    null, null,
                    List.of(),
                    "{}",
                    null
            );

            ToolDefinition tool1 = ToolDefinition.builder()
                    .name("server-tool")
                    .description("First tool")
                    .inputSchema(Map.of("type", "object"))
                    .build();

            ToolDefinition tool2 = ToolDefinition.builder()
                    .name("server-tool")  // Duplicate name
                    .description("Second tool")
                    .inputSchema(Map.of("type", "object"))
                    .build();

            CreateSession createSession = CreateSession.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .agent(agent)
                    .tools(List.of(tool1, tool2))
                    .build();

            String json = objectMapper.writeValueAsString(createSession);
            TextMessage message = new TextMessage(json);

            when(connectionManager.findSession("sess-123")).thenReturn(Optional.empty());

            handler.handleTextMessage(wsSession, message);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("error"));
            assertTrue(payload.contains("Duplicate tool names"));
        }

        @Test
        void handleCreateSession_rejectsWhenSessionLimitExceeded() throws Exception {
            // Configure connection to not accept sessions
            when(connection.canAcceptSession()).thenReturn(false);

            Agent agent = new Agent(
                    "test-agent",
                    "Test agent description",
                    AgentType.ANALYST,
                    "Follow instructions",
                    null, null,
                    List.of(),
                    "{}",
                    null
            );

            CreateSession createSession = CreateSession.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .agent(agent)
                    .tools(List.of())
                    .build();

            String json = objectMapper.writeValueAsString(createSession);
            TextMessage message = new TextMessage(json);

            when(connectionManager.findSession("sess-123")).thenReturn(Optional.empty());

            handler.handleTextMessage(wsSession, message);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("error"));
            assertTrue(payload.contains("SESSION_LIMIT_EXCEEDED"));
        }

        @Test
        void handleCreateSession_rejectsWhenCustomerHasNoAccessToAgentType() throws Exception {
            Agent agent = new Agent(
                    "test-agent",
                    "Test agent description",
                    AgentType.ANALYST,
                    "Follow instructions",
                    null, null,
                    List.of(),
                    "{}",
                    null
            );

            CreateSession createSession = CreateSession.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .agent(agent)
                    .tools(List.of())
                    .build();

            String json = objectMapper.writeValueAsString(createSession);
            TextMessage message = new TextMessage(json);

            when(connectionManager.findSession("sess-123")).thenReturn(Optional.empty());
            when(customerAgentTypeRepository.hasAccess(UUID.fromString(TEST_CUSTOMER_ID), AgentType.ANALYST))
                    .thenReturn(false);

            handler.handleTextMessage(wsSession, message);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("error"));
            assertTrue(payload.contains("UNAUTHORIZED"));
            assertTrue(payload.contains("does not have access to agent type"));
        }

        @Test
        void handleCreateSession_checksAccessForCorrectAgentType() throws Exception {
            // Test that access is checked for the specific agent type requested
            Agent engineerAgent = new Agent(
                    "engineer-agent",
                    "Engineer agent description",
                    AgentType.ENGINEER,
                    "Follow instructions",
                    null, null,
                    List.of(),
                    "{}",
                    null
            );

            CreateSession createSession = CreateSession.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .agent(engineerAgent)
                    .tools(List.of())
                    .build();

            String json = objectMapper.writeValueAsString(createSession);
            TextMessage message = new TextMessage(json);

            when(connectionManager.findSession("sess-123")).thenReturn(Optional.empty());
            // Customer has access to ANALYST but not ENGINEER
            when(customerAgentTypeRepository.hasAccess(UUID.fromString(TEST_CUSTOMER_ID), AgentType.ENGINEER))
                    .thenReturn(false);

            handler.handleTextMessage(wsSession, message);

            // Verify access was checked for ENGINEER, not any other type
            verify(customerAgentTypeRepository).hasAccess(UUID.fromString(TEST_CUSTOMER_ID), AgentType.ENGINEER);
            verify(customerAgentTypeRepository, never()).hasAccess(any(), eq(AgentType.ANALYST));

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("UNAUTHORIZED"));
            assertTrue(payload.contains("ENGINEER"));
        }

        @Test
        void handleToolCallResponse_withSuccessfulResult() throws Exception {
            ToolCallResponse response = ToolCallResponse.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .requestId("req-123")
                    .success(true)
                    .result("{\"data\": \"result\"}")
                    .executionTimeMs(100)
                    .build();

            String json = objectMapper.writeValueAsString(response);
            TextMessage message = new TextMessage(json);

            // Setup session with pending call
            PromptSession session = mock(PromptSession.class);
            PendingToolCall pendingCall = new PendingToolCall(
                    "req-123",
                    "server-tool",
                    "{}",
                    Instant.now().plusSeconds(60)
            );
            when(session.getPendingCall("req-123")).thenReturn(Optional.of(pendingCall));
            when(connection.getSession("sess-123")).thenReturn(Optional.of(session));

            handler.handleTextMessage(wsSession, message);

            verify(session).removePendingCall("req-123");
            assertTrue(pendingCall.responseFuture().isDone());
            assertEquals("{\"data\": \"result\"}", pendingCall.responseFuture().get());
        }

        @Test
        void handleToolCallResponse_withFailedResult() throws Exception {
            ToolCallResponse response = ToolCallResponse.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .requestId("req-123")
                    .success(false)
                    .errorMessage("Tool execution failed")
                    .executionTimeMs(50)
                    .build();

            String json = objectMapper.writeValueAsString(response);
            TextMessage message = new TextMessage(json);

            PromptSession session = mock(PromptSession.class);
            PendingToolCall pendingCall = new PendingToolCall(
                    "req-123",
                    "server-tool",
                    "{}",
                    Instant.now().plusSeconds(60)
            );
            when(session.getPendingCall("req-123")).thenReturn(Optional.of(pendingCall));
            when(connection.getSession("sess-123")).thenReturn(Optional.of(session));

            handler.handleTextMessage(wsSession, message);

            verify(session).removePendingCall("req-123");
            assertTrue(pendingCall.responseFuture().isCompletedExceptionally());
        }

        @Test
        void handleToolCallResponse_ignoresUnknownSession() throws Exception {
            ToolCallResponse response = ToolCallResponse.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("unknown-session")
                    .requestId("req-123")
                    .success(true)
                    .result("result")
                    .executionTimeMs(100)
                    .build();

            String json = objectMapper.writeValueAsString(response);
            TextMessage message = new TextMessage(json);

            when(connection.getSession("unknown-session")).thenReturn(Optional.empty());

            handler.handleTextMessage(wsSession, message);

            // Should not throw and not send any error response for unknown session
            verify(wsSession, never()).sendMessage(any());
        }

        @Test
        void handleToolCallResponse_ignoresUnknownRequestId() throws Exception {
            ToolCallResponse response = ToolCallResponse.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .requestId("unknown-request")
                    .success(true)
                    .result("result")
                    .executionTimeMs(100)
                    .build();

            String json = objectMapper.writeValueAsString(response);
            TextMessage message = new TextMessage(json);

            PromptSession session = mock(PromptSession.class);
            when(session.getPendingCall("unknown-request")).thenReturn(Optional.empty());
            when(connection.getSession("sess-123")).thenReturn(Optional.of(session));

            handler.handleTextMessage(wsSession, message);

            verify(session, never()).removePendingCall(anyString());
        }

        @Test
        void handleCancelSession_cancelsActiveSession() throws Exception {
            CancelSession cancelSession = CancelSession.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("sess-123")
                    .reason("User requested cancellation")
                    .build();

            String json = objectMapper.writeValueAsString(cancelSession);
            TextMessage message = new TextMessage(json);

            PromptSession session = mock(PromptSession.class);
            PromptSession cancelledSession = mock(PromptSession.class);
            when(cancelledSession.getSessionId()).thenReturn("sess-123");
            when(cancelledSession.getToolCallCount()).thenReturn(2);
            when(cancelledSession.getPendingCallCount()).thenReturn(1);
            when(cancelledSession.getDurationMs()).thenReturn(5000L);
            when(session.cancel("User requested cancellation")).thenReturn(cancelledSession);
            when(connection.getSession("sess-123")).thenReturn(Optional.of(session));

            handler.handleTextMessage(wsSession, message);

            verify(session).cancel("User requested cancellation");
            verify(connection).updateSession(cancelledSession);
            verify(serverMetrics).recordSessionCancelled();
            verify(customerMetricsService).recordSessionCancelled(TEST_CUSTOMER_ID);
            verify(connection).removeSession("sess-123");
            verify(connectionManager).unregisterSession("sess-123");

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());
            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("session_cancelled"));
        }

        @Test
        void handleCancelSession_ignoresUnknownSession() throws Exception {
            CancelSession cancelSession = CancelSession.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sessionId("unknown-session")
                    .reason("User requested cancellation")
                    .build();

            String json = objectMapper.writeValueAsString(cancelSession);
            TextMessage message = new TextMessage(json);

            when(connection.getSession("unknown-session")).thenReturn(Optional.empty());

            handler.handleTextMessage(wsSession, message);

            verify(wsSession, never()).sendMessage(any());
        }

        @Test
        void handleHeartbeat_respondsToHeartbeatRequest() throws Exception {
            // Create heartbeat request JSON directly to ensure correct field name
            String json = """
                {
                    "type": "heartbeat",
                    "messageId": "msg-123",
                    "sequenceNumber": 42,
                    "isResponse": false
                }
                """;
            TextMessage message = new TextMessage(json);

            handler.handleTextMessage(wsSession, message);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            JsonNode responseJson = objectMapper.readTree(payload);
            assertEquals("heartbeat", responseJson.get("type").asText());
            assertEquals(42, responseJson.get("sequenceNumber").asLong());
            // Check both possible field names due to Jackson bean naming conventions
            JsonNode isResponseNode = responseJson.has("isResponse")
                    ? responseJson.get("isResponse")
                    : responseJson.get("response");
            assertNotNull(isResponseNode, "Expected isResponse or response field in JSON");
            assertTrue(isResponseNode.asBoolean());
        }

        @Test
        void handleHeartbeat_doesNotRespondToHeartbeatResponse() throws Exception {
            // Create heartbeat response JSON directly to ensure correct field name
            String json = """
                {
                    "type": "heartbeat",
                    "messageId": "msg-123",
                    "sequenceNumber": 42,
                    "isResponse": true
                }
                """;
            TextMessage message = new TextMessage(json);

            // Reset mock to clear any prior invocations from BeforeEach
            reset(wsSession);
            lenient().when(wsSession.getId()).thenReturn("ws-123");
            lenient().when(wsSession.isOpen()).thenReturn(true);

            handler.handleTextMessage(wsSession, message);

            // Should not send any response for heartbeat that is already a response
            verify(wsSession, never()).sendMessage(any());
        }

        @Test
        void handleTextMessage_returnsErrorForInvalidJson() throws Exception {
            TextMessage message = new TextMessage("invalid json {{{");

            handler.handleTextMessage(wsSession, message);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("error"));
            assertTrue(payload.contains("INVALID_MESSAGE"));
        }

        @Test
        void handleTextMessage_returnsErrorForMissingType() throws Exception {
            TextMessage message = new TextMessage("{\"sessionId\": \"sess-123\"}");

            handler.handleTextMessage(wsSession, message);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("error"));
        }

        @Test
        void handleTextMessage_ignoresUnknownConnection() throws Exception {
            when(connectionManager.getConnectionByWebSocketId("ws-123")).thenReturn(Optional.empty());

            TextMessage message = new TextMessage("{\"type\": \"heartbeat\"}");

            handler.handleTextMessage(wsSession, message);

            verify(wsSession, never()).sendMessage(any());
        }

        @Test
        void handleTextMessage_updatesConnectionActivity() throws Exception {
            Heartbeat heartbeat = Heartbeat.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sequenceNumber(1)
                    .isResponse(true)
                    .build();

            String json = objectMapper.writeValueAsString(heartbeat);
            TextMessage message = new TextMessage(json);

            handler.handleTextMessage(wsSession, message);

            verify(connection).updateActivity();
        }
    }

    @Nested
    class AfterConnectionClosed {

        @Test
        void cleansUpConnectionOnClose() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");
            ClientConnection connection = createMockClientConnection(wsSession);
            when(connectionManager.getConnectionByWebSocketId("ws-123")).thenReturn(Optional.of(connection));

            handler.afterConnectionClosed(wsSession, CloseStatus.NORMAL);

            verify(serverMetrics).recordConnectionClosed();
            verify(customerMetricsService).recordConnectionClosed(TEST_CUSTOMER_ID);
            verify(connectionManager).removeConnection("conn-123");
        }

        @Test
        void handlesUnknownConnectionGracefully() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-unknown");
            when(connectionManager.getConnectionByWebSocketId("ws-unknown")).thenReturn(Optional.empty());

            handler.afterConnectionClosed(wsSession, CloseStatus.NORMAL);

            verify(serverMetrics, never()).recordConnectionClosed();
            verify(connectionManager, never()).removeConnection(anyString());
        }

        @Test
        void disconnectsConnectionWithReason() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");
            ClientConnection connection = createMockClientConnection(wsSession);
            when(connectionManager.getConnectionByWebSocketId("ws-123")).thenReturn(Optional.of(connection));

            CloseStatus closeStatus = new CloseStatus(1000, "Client closed connection");
            handler.afterConnectionClosed(wsSession, closeStatus);

            verify(connection).disconnect(contains("WebSocket closed"));
            verify(connectionManager).removeConnection("conn-123");
        }
    }

    @Nested
    class HandleTransportError {

        @Test
        void failsConnectionOnTransportError() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");
            ClientConnection connection = createMockClientConnection(wsSession);
            when(connectionManager.getConnectionByWebSocketId("ws-123")).thenReturn(Optional.of(connection));

            Exception error = new IOException("Connection reset");
            handler.handleTransportError(wsSession, error);

            verify(connection).fail("Transport error: Connection reset");
            verify(connectionManager).removeConnection("conn-123");
        }

        @Test
        void handlesUnknownConnectionGracefully() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-unknown");
            when(connectionManager.getConnectionByWebSocketId("ws-unknown")).thenReturn(Optional.empty());

            handler.handleTransportError(wsSession, new IOException("Error"));

            verify(connectionManager, never()).removeConnection(anyString());
        }
    }

    @Nested
    class SendMessage {

        @Test
        void sendsMessageWhenSessionIsOpen() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");

            ConnectionEstablished message = ConnectionEstablished.builder()
                    .connectionId("conn-123")
                    .serverVersion("1.0.0")
                    .maxConcurrentSessions(10)
                    .build();

            handler.sendMessage(wsSession, message);

            verify(wsSession).sendMessage(any(TextMessage.class));
        }

        @Test
        void doesNotSendMessageWhenSessionIsClosed() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");
            when(wsSession.isOpen()).thenReturn(false);

            ConnectionEstablished message = ConnectionEstablished.builder()
                    .connectionId("conn-123")
                    .serverVersion("1.0.0")
                    .maxConcurrentSessions(10)
                    .build();

            handler.sendMessage(wsSession, message);

            verify(wsSession, never()).sendMessage(any(TextMessage.class));
        }

        @Test
        void handlesIOExceptionGracefully() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");
            doThrow(new IOException("Send failed")).when(wsSession).sendMessage(any(TextMessage.class));

            ConnectionEstablished message = ConnectionEstablished.builder()
                    .connectionId("conn-123")
                    .serverVersion("1.0.0")
                    .maxConcurrentSessions(10)
                    .build();

            // Should not throw
            assertDoesNotThrow(() -> handler.sendMessage(wsSession, message));
        }
    }

    @Nested
    class SendToolCallRequest {

        @Test
        void sendsToolCallRequestToClient() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");

            ClientConnection connection = createRealClientConnection(wsSession);
            PromptSession session = PromptSession.builder()
                    .sessionId("sess-123")
                    .connection(connection)
                    .prompt("Test prompt")
                    .systemPrompt("System prompt")
                    .model("claude-3")
                    .build();

            ToolCallRequest request = ToolCallRequest.builder()
                    .sessionId("sess-123")
                    .requestId("req-123")
                    .toolName("server-tool")
                    .arguments(Map.of())
                    .build();

            handler.sendToolCallRequest(session, request);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(wsSession).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("tool_call_request"));
            assertTrue(payload.contains("server-tool"));
        }
    }

    @Nested
    class MetricsTracking {

        @Test
        void incrementsMessagesReceivedCounter() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");
            ClientConnection connection = createMockClientConnection(wsSession);
            when(connectionManager.getConnectionByWebSocketId("ws-123")).thenReturn(Optional.of(connection));

            Heartbeat heartbeat = Heartbeat.builder()
                    .messageId("msg-123")
                    .timestamp(Instant.now())
                    .sequenceNumber(1)
                    .isResponse(true)
                    .build();

            String json = objectMapper.writeValueAsString(heartbeat);
            TextMessage message = new TextMessage(json);

            handler.handleTextMessage(wsSession, message);

            double count = meterRegistry.get("mcp.messages.received").counter().count();
            assertEquals(1.0, count);
        }

        @Test
        void incrementsMessagesSentCounter() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");

            ConnectionEstablished message = ConnectionEstablished.builder()
                    .connectionId("conn-123")
                    .serverVersion("1.0.0")
                    .maxConcurrentSessions(10)
                    .build();

            handler.sendMessage(wsSession, message);

            double count = meterRegistry.get("mcp.messages.sent").counter().count();
            assertEquals(1.0, count);
        }

        @Test
        void incrementsMessageErrorsCounterOnInvalidJson() throws Exception {
            WebSocketSession wsSession = createMockWebSocketSession("ws-123");
            ClientConnection connection = createMockClientConnection(wsSession);
            when(connectionManager.getConnectionByWebSocketId("ws-123")).thenReturn(Optional.of(connection));

            TextMessage message = new TextMessage("invalid json {{{");

            handler.handleTextMessage(wsSession, message);

            double count = meterRegistry.get("mcp.messages.errors").counter().count();
            assertEquals(1.0, count);
        }
    }
}
