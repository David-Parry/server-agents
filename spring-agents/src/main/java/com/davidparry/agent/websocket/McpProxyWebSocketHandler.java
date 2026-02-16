package com.davidparry.agent.websocket;

import com.davidparry.agent.config.AgentConfiguration;
import com.davidparry.agent.config.DatabaseAgentConfigurationProvider;
import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.repository.CustomerAgentTypeRepository;
import com.davidparry.agent.observability.CustomerMetricsService;
import com.davidparry.agent.observability.ServerMetrics;
import com.davidparry.agent.pojo.ExecutionResult;
import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.BaseLlmResponse;
import com.davidparry.agent.protocol.CancelSession;
import com.davidparry.agent.protocol.ConnectionEstablished;
import com.davidparry.agent.protocol.CreateSession;
import com.davidparry.agent.protocol.ErrorMessage;
import com.davidparry.agent.protocol.Heartbeat;
import com.davidparry.agent.protocol.McpProxyMessage;
import com.davidparry.agent.protocol.SessionCancelled;
import com.davidparry.agent.protocol.SessionResult;
import com.davidparry.agent.protocol.SessionStarted;
import com.davidparry.agent.protocol.ToolCallRequest;
import com.davidparry.agent.protocol.ToolCallResponse;
import com.davidparry.agent.protocol.dto.Capabilities;
import com.davidparry.agent.protocol.dto.ErrorCode;
import com.davidparry.agent.protocol.dto.ToolDefinition;
import com.davidparry.agent.service.PromptExecutionService;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import com.davidparry.agent.session.PendingToolCall;
import com.davidparry.agent.session.PromptSession;
import com.davidparry.agent.transform.SchemaMerger;
import com.davidparry.agent.transform.TemplateProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocket handler for MCP Proxy connections.
 * Handles all message routing between the server and remote agent-sdk instances.
 */
@Component
public class McpProxyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpProxyWebSocketHandler.class);
    private static final String SERVER_VERSION = "1.0.0";
    // Pattern for validating tool names: server-toolname format (LLM-compatible)
    // Uses hyphen as separator since Anthropic only accepts [a-zA-Z0-9_-]
    private static final java.util.regex.Pattern TOOL_NAME_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z][a-zA" + "-Z0-9_" + "]*-[a" + "-zA-Z" + "][a-zA" + "-Z0-9_"
                                                    + "]*$");
    private final ConnectionManager connectionManager;
    private final PromptExecutionService promptExecutionService;
    private final RemoteToolCallbackFactory toolCallbackFactory;
    private final McpProxyProperties properties;
    private final ObjectMapper objectMapper;
    private final Counter messagesReceivedCounter;
    private final Counter messagesSentCounter;
    private final Counter messageErrorsCounter;
    private final TemplateProcessor templateProcessor;
    private final SchemaMerger schemaMerger;
    private final DatabaseAgentConfigurationProvider agentConfigurationProvider;
    private final CustomerMetricsService customerMetricsService;
    private final ServerMetrics serverMetrics;
    private final CustomerAgentTypeRepository customerAgentTypeRepository;

    public McpProxyWebSocketHandler(ConnectionManager connectionManager, PromptExecutionService promptExecutionService,
                                    RemoteToolCallbackFactory toolCallbackFactory, McpProxyProperties properties,
                                    ObjectMapper objectMapper, MeterRegistry meterRegistry,
                                    TemplateProcessor templateProcessor, SchemaMerger schemaMerger,
                                    DatabaseAgentConfigurationProvider agentConfigurationProvider,
                                    CustomerMetricsService customerMetricsService,
                                    ServerMetrics serverMetrics,
                                    CustomerAgentTypeRepository customerAgentTypeRepository) {
        this.connectionManager = connectionManager;
        this.promptExecutionService = promptExecutionService;
        this.toolCallbackFactory = toolCallbackFactory;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.templateProcessor = templateProcessor;
        this.schemaMerger = schemaMerger;
        this.agentConfigurationProvider = agentConfigurationProvider;
        this.customerMetricsService = customerMetricsService;
        this.serverMetrics = serverMetrics;
        this.customerAgentTypeRepository = customerAgentTypeRepository;

        this.messagesReceivedCounter = Counter
                .builder("mcp.messages.received")
                .description("Total messages received")
                .register(meterRegistry);
        this.messagesSentCounter = Counter
                .builder("mcp.messages.sent")
                .description("Total messages sent")
                .register(meterRegistry);
        this.messageErrorsCounter = Counter
                .builder("mcp.messages.errors")
                .description("Message processing errors")
                .register(meterRegistry);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String clientId = (String) session.getAttributes().get(ApiKeyHandshakeInterceptor.CLIENT_ID_ATTRIBUTE);

        ClientConnection connection = connectionManager.createConnection(session, clientId);

        try (var ignored = MDC.putCloseable("connectionId", connection.getConnectionId()); var ignored2 =
                MDC.putCloseable("clientId", clientId)) {

            LOGGER.info("WebSocket connection established");

            // Record metrics
            serverMetrics.recordConnectionOpened();
            customerMetricsService.recordConnectionOpened(clientId);

            // Send connection established message
            ConnectionEstablished established = ConnectionEstablished
                    .builder()
                    .connectionId(connection.getConnectionId())
                    .serverVersion(SERVER_VERSION)
                    .maxConcurrentSessions(properties.connection().maxConcurrentSessions())
                    .capabilities(Capabilities
                                          .builder()
                                          .streamingSupported(properties.streaming().enabled())
                                          .maxConcurrentSessions(properties.connection().maxConcurrentSessions())
                                          .sessionTimeoutSeconds(properties.session().maxDurationSeconds())
                                          .toolCallTimeoutSeconds(properties.session().toolCallTimeoutSeconds())
                                          .build())
                    .build();

            sendMessage(session, established);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        messagesReceivedCounter.increment();
        LOGGER.debug("Message Payload: {}", message.getPayload());
        Optional<ClientConnection> connectionOpt = connectionManager.getConnectionByWebSocketId(session.getId());
        if (connectionOpt.isEmpty()) {
            LOGGER.error("Received message for unknown connection: {}", session.getId());
            return;
        }

        ClientConnection connection = connectionOpt.get();
        connection.updateActivity();

        try (var ignored = MDC.putCloseable("connectionId", connection.getConnectionId()); var ignored2 =
                MDC.putCloseable("clientId", connection.getClientId())) {

            McpProxyMessage proxyMessage = objectMapper.readValue(message.getPayload(), McpProxyMessage.class);

            LOGGER.debug("Received message: {}", proxyMessage.getClass().getSimpleName());

            switch (proxyMessage) {
                case CreateSession createSession -> handleCreateSession(connection, createSession);
                case ToolCallResponse toolCallResponse -> handleToolCallResponse(connection, toolCallResponse);
                case CancelSession cancelSession -> handleCancelSession(connection, cancelSession);
                case Heartbeat heartbeat -> handleHeartbeat(connection, heartbeat);
                default -> {
                    LOGGER.warn("Unexpected message type: {}", proxyMessage.getClass().getSimpleName());
                    sendError(session, null, ErrorCode.INVALID_MESSAGE, "Unexpected message type: " + proxyMessage
                            .getClass()
                            .getSimpleName());
                }
            }
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            messageErrorsCounter.increment();
            String payload = message.getPayload();
            LOGGER.warn("Invalid JSON received: {} | Failed payload: '{}'", e.getMessage(), payload);
            sendError(session, null, ErrorCode.INVALID_MESSAGE, "Invalid JSON format: " + e.getOriginalMessage());
        } catch (com.fasterxml.jackson.databind.exc.InvalidTypeIdException e) {
            messageErrorsCounter.increment();
            String payload = message.getPayload();
            LOGGER.warn("Missing or invalid message type: {} | Failed payload: '{}'", e.getMessage(), payload);
            sendError(session, null, ErrorCode.INVALID_MESSAGE, "Missing or invalid 'type' field. Valid types: "
                    + "create_session, tool_call_response, cancel_session, heartbeat");
        } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
            messageErrorsCounter.increment();
            String payload = message.getPayload();
            LOGGER.warn("JSON mapping error: {} | Failed payload: '{}'", e.getMessage(), payload);
            sendError(session, null, ErrorCode.INVALID_MESSAGE, "Invalid message structure: " + e.getOriginalMessage());
        } catch (Exception e) {
            messageErrorsCounter.increment();
            LOGGER.error("Error processing message", e);
            sendError(session, null, ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Optional<ClientConnection> connectionOpt = connectionManager.getConnectionByWebSocketId(session.getId());

        if (connectionOpt.isPresent()) {
            ClientConnection connection = connectionOpt.get();

            try (var ignored = MDC.putCloseable("connectionId", connection.getConnectionId()); var ignored2 =
                    MDC.putCloseable("clientId", connection.getClientId())) {

                LOGGER.info("WebSocket connection closed: {}", status);

                // Record metrics
                serverMetrics.recordConnectionClosed();
                customerMetricsService.recordConnectionClosed(connection.getClientId());

                connection.disconnect("WebSocket closed: " + status.getReason());
                connectionManager.removeConnection(connection.getConnectionId());
            }
        }
    }

    // ==================== Message Handlers ====================

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Optional<ClientConnection> connectionOpt = connectionManager.getConnectionByWebSocketId(session.getId());

        if (connectionOpt.isPresent()) {
            ClientConnection connection = connectionOpt.get();

            try (var ignored = MDC.putCloseable("connectionId", connection.getConnectionId()); var ignored2 =
                    MDC.putCloseable("clientId", connection.getClientId())) {

                LOGGER.error("WebSocket transport error", exception);

                connection.fail("Transport error: " + exception.getMessage());
                connectionManager.removeConnection(connection.getConnectionId());
            }
        }
    }

    private void handleCreateSession(ClientConnection connection, CreateSession createSession) {
        String sessionId = createSession.getSessionId();

        // Validate sessionId is provided
        if (sessionId == null || sessionId.isBlank()) {
            LOGGER.warn("CreateSession missing sessionId");
            sendError(connection.getWebSocketSession(), null, ErrorCode.INVALID_MESSAGE,
                      "sessionId is required in " + "CreateSession");
            return;
        }

        try (var ignored = MDC.putCloseable("sessionId", sessionId);
             var ignored2 = MDC.putCloseable("connectionId", connection.getConnectionId());
             var ignored3 = MDC.putCloseable("clientId", connection.getClientId())) {
            LOGGER.info("Creating session for Agent: {}...", createSession.getAgent().name());

            // Check for duplicate sessionId
            if (connectionManager.findSession(sessionId).isPresent()) {
                LOGGER.warn("Duplicate sessionId: {}", sessionId);
                sendError(connection.getWebSocketSession(), sessionId, ErrorCode.INVALID_MESSAGE, "Session with this "
                        + "sessionId already exists: " + sessionId);
                return;
            }

            // Check if connection can accept new sessions
            if (!connection.canAcceptSession()) {
                LOGGER.warn("Connection cannot accept new sessions");
                sendError(connection.getWebSocketSession(), sessionId, ErrorCode.SESSION_LIMIT_EXCEEDED,
                          "Maximum " + "concurrent sessions reached");
                return;
            }

            // Validate tool names follow server-toolname format (LLM-compatible)
            for (ToolDefinition toolDef : createSession.getTools()) {
                if (!TOOL_NAME_PATTERN.matcher(toolDef.getName()).matches()) {
                    LOGGER.warn("Invalid tool name format: {}", toolDef.getName());
                    sendError(connection.getWebSocketSession(), sessionId, ErrorCode.INVALID_MESSAGE,
                            "Tool name must follow 'server-toolname' format (e.g., 'terminal-list_files'). Invalid: "
                            + toolDef.getName());
                    return;
                }
            }

            // Check for duplicate tool names
            List<String> toolNames = createSession.getTools().stream().map(ToolDefinition::getName).toList();
            if (toolNames.size() != toolNames.stream().distinct().count()) {
                LOGGER.warn("Duplicate tool names in session request");
                sendError(connection.getWebSocketSession(), sessionId, ErrorCode.INVALID_MESSAGE,
                          "Duplicate tool " + "names are not allowed");
                return;
            }

            // Check if customer has access to the requested agent type
            Agent agent = createSession.getAgent();
            UUID customerId;
            try {
                customerId = UUID.fromString(connection.getClientId());
            } catch (IllegalArgumentException e) {
                LOGGER.error("Invalid customer ID format: {}", connection.getClientId());
                sendError(connection.getWebSocketSession(), sessionId, ErrorCode.INTERNAL_ERROR,
                          "Invalid customer ID format");
                return;
            }

            if (!customerAgentTypeRepository.hasAccess(customerId, agent.type())) {
                LOGGER.warn("Customer {} does not have access to agent type {}", customerId, agent.type());
                sendError(connection.getWebSocketSession(), sessionId, ErrorCode.UNAUTHORIZED,
                          "Customer does not have access to agent type: " + agent.type());
                return;
            }

            // Create tool callbacks for remote execution
            List<ToolCallback> toolCallbacks = new ArrayList<>();
            for (ToolDefinition toolDef : createSession.getTools()) {
                ToolCallback callback = toolCallbackFactory.createCallback(toolDef, sessionId, connection,
                                                                           this::sendToolCallRequest);
                toolCallbacks.add(callback);
            }

            // Create the session with stream chunk consumer
            // Get agent configuration (system prompt and model) based on agent type
            AgentConfiguration agentConfig = agentConfigurationProvider.getConfiguration(agent.type());

            String systemPrompt = agentConfig.systemPrompt();
            String model = agentConfig.model();

            LOGGER.debug("Using agent configuration for type {}: model={}", agent.type(), model);

            // Merge the agent's output schema with the base LLM response schema
            String completeSchema = schemaMerger.mergePropertiesWithClass(agent.outputSchema(), BaseLlmResponse.class);

            LOGGER.trace("Completed merging schema {}", completeSchema);

            PromptSession session = PromptSession
                    .builder()
                    .sessionId(sessionId)
                    .connection(connection)
                    .systemPrompt(templateProcessor.processTemplate(systemPrompt, createSession.getPromptParams()))
                    .prompt(templateProcessor.processTemplate(agent.instructions(), createSession.getPromptParams()))
                    .model(model)
                    .streamingEnabled(createSession.isStreamResponse() && properties.streaming().enabled())
                    .tools(toolCallbacks)
                    .metadata(Map.of())
                    .maxDurationSeconds(properties.session().maxDurationSeconds())
                    .responseSchema(completeSchema)
                    .build()
                    .withStreamChunkConsumer(chunk -> sendMessage(connection.getWebSocketSession(), chunk));

            // Add to connection
            if (!connection.addSession(session)) {
                LOGGER.error("Failed to add session to connection");
                sendError(connection.getWebSocketSession(), sessionId, ErrorCode.INTERNAL_ERROR,
                          "Failed to create " + "session");
                return;
            }

            connectionManager.registerSession(session);

            // Record session created metrics
            serverMetrics.recordSessionStarted();
            customerMetricsService.recordSessionCreated(connection.getClientId());

            // Send session started confirmation
            SessionStarted started = SessionStarted
                    .builder()
                    .sessionId(sessionId)
                    .toolCount(toolCallbacks.size())
                    .build();
            sendMessage(connection.getWebSocketSession(), started);

            // Execute the prompt asynchronously
            executePromptAsync(session);
        }
    }

    private void handleToolCallResponse(ClientConnection connection, ToolCallResponse response) {
        // Get tool name from pending call for MDC context
        Optional<PromptSession> sessionForMdc = connection.getSession(response.getSessionId());
        String toolName = sessionForMdc
                .flatMap(s -> s.getPendingCall(response.getRequestId()))
                .map(PendingToolCall::toolName)
                .orElse("");

        try (var ignored = MDC.putCloseable("sessionId", response.getSessionId());
             var ignored2 = MDC.putCloseable("requestId", response.getRequestId());
             var ignored3 = MDC.putCloseable("connectionId", connection.getConnectionId());
             var ignored4 = MDC.putCloseable("clientId", connection.getClientId());
             var ignored5 = MDC.putCloseable("toolName", toolName)) {

            LOGGER.debug("Received tool call response: success={}", response.isSuccess());

            Optional<PromptSession> sessionOpt = connection.getSession(response.getSessionId());
            if (sessionOpt.isEmpty()) {
                LOGGER.warn("Tool call response for unknown session: {}", response.getSessionId());
                return;
            }

            PromptSession session = sessionOpt.get();
            Optional<PendingToolCall> pendingCallOpt = session.getPendingCall(response.getRequestId());

            if (pendingCallOpt.isEmpty()) {
                LOGGER.warn("Tool call response for unknown request: {}", response.getRequestId());
                return;
            }

            PendingToolCall pendingCall = pendingCallOpt.get();
            session.removePendingCall(response.getRequestId());

            if (response.isSuccess()) {
                pendingCall.complete(response.getResult());
            } else {
                pendingCall.completeExceptionally(new RuntimeException("Tool call failed: " + response.getErrorMessage()));
            }

            connection.incrementToolCalls();
        }
    }

    private void handleCancelSession(ClientConnection connection, CancelSession cancelSession) {
        try (var ignored = MDC.putCloseable("sessionId", cancelSession.getSessionId()); var ignored2 =
                MDC.putCloseable("connectionId", connection.getConnectionId()); var ignored3 = MDC.putCloseable(
                        "clientId", connection.getClientId())) {
            LOGGER.info("Cancelling session: {}", cancelSession.getReason());

            Optional<PromptSession> sessionOpt = connection.getSession(cancelSession.getSessionId());
            if (sessionOpt.isEmpty()) {
                LOGGER.warn("Cancel request for unknown session: {}", cancelSession.getSessionId());
                return;
            }

            PromptSession session = sessionOpt.get();
            PromptSession cancelledSession = session.cancel(cancelSession.getReason());
            connection.updateSession(cancelledSession);

            // Record cancellation metrics
            serverMetrics.recordSessionCancelled();
            serverMetrics.recordSessionOutcome("cancelled", cancelledSession.getDurationMs());
            customerMetricsService.recordSessionCancelled(connection.getClientId());

            // Send cancellation confirmation
            SessionCancelled cancelled = SessionCancelled
                    .builder()
                    .sessionId(cancelledSession.getSessionId())
                    .reason(cancelSession.getReason())
                    .toolCallsCompleted(cancelledSession.getToolCallCount())
                    .toolCallsPending(cancelledSession.getPendingCallCount())
                    .build();
            sendMessage(connection.getWebSocketSession(), cancelled);

            // Clean up
            connection.removeSession(cancelledSession.getSessionId());
            connectionManager.unregisterSession(cancelledSession.getSessionId());
        }
    }

    private void handleHeartbeat(ClientConnection connection, Heartbeat heartbeat) {
        LOGGER.trace("Received heartbeat");

        if (!heartbeat.isResponse()) {
            // Send heartbeat response
            Heartbeat response = Heartbeat
                    .builder()
                    .sequenceNumber(heartbeat.getSequenceNumber())
                    .isResponse(true)
                    .build();
            sendMessage(connection.getWebSocketSession(), response);
        }
    }

    // ==================== Helper Methods ====================

    private void executePromptAsync(PromptSession session) {
        CompletableFuture.runAsync(() -> {
            try (var ignored = MDC.putCloseable("sessionId", session.getSessionId());
                 var ignored2 = MDC.putCloseable("connectionId", session.getConnection().getConnectionId());
                 var ignored3 = MDC.putCloseable("clientId", session.getConnection().getClientId())) {

                promptExecutionService
                        .executePrompt(session)
                        .thenAccept(this::handleExecutionResult)
                        .exceptionally(error -> {
                            handleSessionError(session, error);
                            return null;
                        });
            }
        });
    }

    private void handleExecutionResult(ExecutionResult executionResult) {
        PromptSession updatedSession = executionResult.updatedSession();
        SessionResult result = executionResult.result();

        try (var ignored = MDC.putCloseable("sessionId", updatedSession.getSessionId()); var ignored2 =
                MDC.putCloseable("connectionId", updatedSession
                .getConnection()
                .getConnectionId()); var ignored3 = MDC.putCloseable("clientId", updatedSession
                .getConnection()
                .getClientId())) {
            // Use toString() for JsonNode since textValue() returns null for non-text nodes (objects, arrays, etc.)
            String contentStr = result.getContent() != null ? result.getContent().toString() : null;
            LOGGER.info("Session completed: success={}, duration={}ms, contentLength={}", result.isSuccess(),
                        result.getTotalDurationMs(), contentStr != null ? contentStr.length() : "null");

            if (LOGGER.isDebugEnabled() && contentStr != null) {
                // Log first 1200 chars of response for debugging
                String preview = contentStr.length() > 1200 ? contentStr.substring(0, 1200) + "..." : contentStr;
                LOGGER.debug("Response preview: {}", preview);
            }

            // Record session completion metrics
            String clientId = updatedSession.getConnection().getClientId();
            long durationMs = result.getTotalDurationMs();

            if (result.isSuccess()) {
                serverMetrics.recordSessionCompleted();
                serverMetrics.recordSessionOutcome("completed", durationMs);
                customerMetricsService.recordSessionCompleted(clientId, durationMs);
            } else {
                serverMetrics.recordSessionFailed();
                serverMetrics.recordSessionOutcome("failed", durationMs);
                customerMetricsService.recordSessionFailed(clientId);
            }

            // Update the session in the connection with the final state
            updatedSession.getConnection().updateSession(updatedSession);

            sendMessage(updatedSession.getConnection().getWebSocketSession(), result);

            // Clean up
            updatedSession.getConnection().removeSession(updatedSession.getSessionId());
            connectionManager.unregisterSession(updatedSession.getSessionId());
        }
    }

    private void handleSessionError(PromptSession session, Throwable error) {
        try (var ignored = MDC.putCloseable("sessionId", session.getSessionId()); var ignored2 = MDC.putCloseable(
                "connectionId", session
                .getConnection()
                .getConnectionId()); var ignored3 = MDC.putCloseable("clientId", session
                .getConnection()
                .getClientId())) {
            LOGGER.error("Session failed", error);

            PromptSession failedSession = session.fail(error.getMessage());
            session.getConnection().updateSession(failedSession);

            SessionResult result = SessionResult
                    .builder()
                    .sessionId(failedSession.getSessionId())
                    .success(false)
                    .content(null)
                    .errorMessage(error.getMessage())
                    .toolCallsExecuted(failedSession.getToolCallCount())
                    .totalDurationMs(failedSession.getDurationMs())
                    .metrics(failedSession.createMetrics(0))
                    .build();

            sendMessage(failedSession.getConnection().getWebSocketSession(), result);

            // Clean up
            failedSession.getConnection().removeSession(failedSession.getSessionId());
            connectionManager.unregisterSession(failedSession.getSessionId());
        }
    }

    /**
     * Sends a tool call request to the remote client.
     */
    public void sendToolCallRequest(PromptSession session, ToolCallRequest request) {
        sendMessage(session.getConnection().getWebSocketSession(), request);
    }

    /**
     * Sends a message to a WebSocket session.
     */
    public void sendMessage(WebSocketSession session, McpProxyMessage message) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
                messagesSentCounter.increment();
                LOGGER.trace("Sent message: {}", message.getClass().getSimpleName());
            } else {
                LOGGER.warn("Cannot send message - WebSocket is closed");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to send message", e);
            messageErrorsCounter.increment();
        }
    }

    private void sendError(WebSocketSession session, String sessionId, ErrorCode code, String message) {
        ErrorMessage error = ErrorMessage
                .builder()
                .sessionId(sessionId)
                .code(code)
                .message(message)
                .details(Map.of())
                .build();
        sendMessage(session, error);
    }

    @FunctionalInterface
    public interface ToolCallSender {
        void send(PromptSession session, ToolCallRequest request);
    }
}
