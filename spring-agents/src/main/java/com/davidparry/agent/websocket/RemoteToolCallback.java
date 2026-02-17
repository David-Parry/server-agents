package com.davidparry.agent.websocket;

import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.protocol.ToolCallRequest;
import com.davidparry.agent.reliability.ToolCallCircuitBreaker;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import com.davidparry.agent.session.PendingToolCall;
import com.davidparry.agent.session.PromptSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ToolCallback implementation that proxies tool calls to a remote agent-sdk instance.
 * This is the critical bridge between Spring AI's tool system and remote execution.
 *
 * <p>Tool names use the format "server-toolname" (e.g., "terminal-list_files") which is
 * compatible with all LLM providers including Anthropic (which only accepts [a-zA-Z0-9_-]).
 */
public class RemoteToolCallback implements ToolCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteToolCallback.class);

    private final String toolName;
    private final String description;
    private final String inputSchema;
    private final String sessionId;
    private final ClientConnection connection;
    private final McpProxyWebSocketHandler.ToolCallSender sender;
    private final ConnectionManager connectionManager;
    private final ToolCallCircuitBreaker circuitBreakerManager;
    private final McpProxyProperties.SessionConfig config;
    private final Timer toolCallTimer;
    private final ObjectMapper objectMapper;

    public RemoteToolCallback(
            String toolName,
            String description,
            Map<String, Object> inputSchema,
            String sessionId,
            ClientConnection connection,
            McpProxyWebSocketHandler.ToolCallSender sender,
            ConnectionManager connectionManager,
            ToolCallCircuitBreaker circuitBreakerManager,
            McpProxyProperties.SessionConfig config,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.toolName = toolName;
        this.description = description;
        this.inputSchema = convertSchemaToString(inputSchema, objectMapper);
        this.sessionId = sessionId;
        this.connection = connection;
        this.sender = sender;
        this.connectionManager = connectionManager;
        this.circuitBreakerManager = circuitBreakerManager;
        this.config = config;
        this.objectMapper = objectMapper;

        this.toolCallTimer = Timer.builder("mcp.tool_calls.duration")
                .tag("tool", toolName)
                .description("Tool call duration")
                .register(meterRegistry);
    }

    private String convertSchemaToString(Map<String, Object> inputSchema, ObjectMapper objectMapper) {
        if (inputSchema == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(inputSchema);
        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to serialize inputSchema, using empty object: {}", e.getMessage());
            return "{}";
        }
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(toolName)
                .description(description)
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public String call(String arguments) {
        String requestId = connectionManager.generateRequestId();

        try (var ignored = MDC.putCloseable("requestId", requestId);
             var ignored2 = MDC.putCloseable("toolName", toolName);
             var ignored3 = MDC.putCloseable("sessionId", sessionId);
             var ignored4 = MDC.putCloseable("connectionId", connection.getConnectionId());
             var ignored5 = MDC.putCloseable("clientId", connection.getClientId())) {

            LOGGER.info("Executing remote tool call: {}", toolName);

            return toolCallTimer.record(() -> executeToolCall(requestId, arguments));
        } catch (Exception e) {
            // Return error as a string instead of throwing - allows LLM to see the error and decide how to proceed
            String errorMessage = extractErrorMessage(e);
            LOGGER.error("Tool call failed, returning error to LLM: {}", errorMessage);
            return formatErrorResponse(errorMessage);
        }
    }

    /**
     * Extracts a clean error message from an exception, unwrapping nested exceptions.
     */
    private String extractErrorMessage(Throwable e) {
        Throwable cause = e;
        String message = e.getMessage();

        // Unwrap nested exceptions to get the root cause message
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                message = cause.getMessage();
            }
        }

        // Clean up common prefixes that get nested
        if (message != null) {
            message = message.replaceAll("(?i)^(Tool call failed: )+", "");
            message = message.replaceAll("(?i)^(Tool execution error: )+", "");
        }

        return message != null ? message : "Unknown error occurred";
    }

    /**
     * Formats an error response in a structured way that the LLM can understand.
     */
    private String formatErrorResponse(String errorMessage) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "isError", true,
                    "error", errorMessage,
                    "tool", toolName,
                    "suggestion", "The tool execution failed. You may need to try a different approach or check the error message for details."
            ));
        } catch (JsonProcessingException e) {
            // Fallback to simple string format
            return "{\"isError\": true, \"error\": \"" + errorMessage.replace("\"", "\\\"") + "\"}";
        }
    }

    private String executeToolCall(String requestId, String arguments) {
        // Get the session
        PromptSession session = connectionManager.findSession(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        // Check session state
        if (session.isTerminal()) {
            throw new RuntimeException("Session is no longer active");
        }

        if (session.isExpired()) {
            throw new RuntimeException("Session has expired");
        }

        // Check circuit breaker
        CircuitBreaker circuitBreaker = circuitBreakerManager.getBreaker(
                connection.getConnectionId(), toolName);

        if (!circuitBreakerManager.isToolAvailable(connection.getConnectionId(), toolName)) {
            LOGGER.warn("Circuit breaker is open for tool: {}", toolName);
            throw new RuntimeException("Tool temporarily unavailable due to repeated failures");
        }

        // Calculate deadline
        Instant deadline = Instant.now().plusSeconds(config.toolCallTimeoutSeconds());

        // Create pending call and UPDATE SESSION - capture the new immutable session
        PendingToolCall pendingCall = new PendingToolCall(requestId, toolName, arguments, deadline);
        PromptSession updatedSession = session.addPendingCall(pendingCall);
        updatedSession = updatedSession.waitingForTool();

        // CRITICAL: Update the session in the connection so toolCallCount is preserved
        connection.updateSession(updatedSession);

        try {
            // Parse arguments string to Map for the request
            Map<String, Object> argumentsMap = parseArguments(arguments);

            // Send tool call request to client (use updatedSession for consistency)
            ToolCallRequest request = ToolCallRequest.builder()
                    .requestId(requestId)
                    .sessionId(sessionId)
                    .toolName(toolName)
                    .arguments(argumentsMap)
                    .deadline(deadline)
                    .build();
            sender.send(updatedSession, request);

            // Wait for response with timeout
            String result = circuitBreaker.executeSupplier(() -> {
                try {
                    return pendingCall.responseFuture()
                            .get(config.toolCallTimeoutSeconds(), TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    throw new RuntimeException("Tool call timed out after "
                            + config.toolCallTimeoutSeconds() + " seconds", e);
                } catch (Exception e) {
                    throw new RuntimeException("Tool call failed: " + e.getMessage(), e);
                }
            });

            // After success, get the latest session and resume execution
            PromptSession currentSession = connectionManager.findSession(sessionId)
                    .orElse(updatedSession);
            PromptSession resumedSession = currentSession.resumeExecution();
            connection.updateSession(resumedSession);

            LOGGER.info("Tool call completed successfully, toolCallCount={}", resumedSession.getToolCallCount());
            return result;

        } catch (Exception e) {
            // On failure, get the latest session, clean up, and resume
            PromptSession currentSession = connectionManager.findSession(sessionId)
                    .orElse(updatedSession);
            currentSession.removePendingCall(requestId);
            PromptSession resumedSession = currentSession.resumeExecution();
            connection.updateSession(resumedSession);

            LOGGER.error("Tool call failed: {}", e.getMessage());
            throw new RuntimeException("Tool call failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to parse arguments as JSON, wrapping as raw: {}", e.getMessage());
            return Map.of("raw", arguments);
        }
    }

    @Override
    public String toString() {
        return "RemoteToolCallback{"
                + "toolName='" + toolName + '\''
                + ", sessionId='" + sessionId + '\''
                + ", connectionId='" + connection.getConnectionId() + '\''
                + '}';
    }
}
