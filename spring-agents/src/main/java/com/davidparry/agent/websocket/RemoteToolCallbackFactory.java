package com.davidparry.agent.websocket;

import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.protocol.dto.ToolDefinition;
import com.davidparry.agent.reliability.ToolCallCircuitBreaker;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * Factory for creating RemoteToolCallback instances.
 * Associates callbacks with specific sessions and configures timeout/retry behavior.
 */
@Component
public class RemoteToolCallbackFactory {

    private final McpProxyProperties properties;
    private final ConnectionManager connectionManager;
    private final ToolCallCircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public RemoteToolCallbackFactory(
            McpProxyProperties properties,
            ConnectionManager connectionManager,
            ToolCallCircuitBreaker circuitBreaker,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.connectionManager = connectionManager;
        this.circuitBreaker = circuitBreaker;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a ToolCallback for remote execution.
     *
     * @param toolDefinition the tool definition from the client
     * @param sessionId the session ID this callback belongs to
     * @param connection the client connection
     * @param sender the function to send tool call requests
     * @return a ToolCallback that proxies calls to the remote client
     */
    public ToolCallback createCallback(
            ToolDefinition toolDefinition,
            String sessionId,
            ClientConnection connection,
            McpProxyWebSocketHandler.ToolCallSender sender) {

        return new RemoteToolCallback(
                toolDefinition.getName(),
                toolDefinition.getDescription(),
                toolDefinition.getInputSchema(),
                sessionId,
                connection,
                sender,
                connectionManager,
                circuitBreaker,
                properties.session(),
                meterRegistry,
                objectMapper
        );
    }
}
