package com.davidparry.agent.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuration properties for MCP Proxy.
 * Authentication is handled via database-backed customer tokens.
 * See: customer and customer_token tables.
 */
@Validated
@ConfigurationProperties(prefix = "mcp.proxy")
public record McpProxyProperties(
        boolean enabled,
        @Valid ConnectionConfig connection,
        @Valid SessionConfig session,
        @Valid StreamingConfig streaming,
        @Valid CircuitBreakerConfig circuitBreaker,
        @Valid WebSocketConfig webSocket
) {
    public McpProxyProperties {
        if (connection == null) {
            connection = new ConnectionConfig(300, 30, 5, 10, false);
        }
        if (session == null) {
            session = new SessionConfig(600, 60, 2, 1000);
        }
        if (streaming == null) {
            streaming = new StreamingConfig(true, 100, 50);
        }
        if (circuitBreaker == null) {
            circuitBreaker = new CircuitBreakerConfig(5, 3, 30, 3);
        }
        if (webSocket == null) {
            webSocket = new WebSocketConfig("/agent", List.of("*"), 0, 0, 0);
        }
    }

    /**
     * Connection management configuration
     */
    public record ConnectionConfig(
            @Min(value = 30, message = "Idle timeout must be at least 30 seconds")
            int idleTimeoutSeconds,
            @Min(value = 10, message = "Heartbeat interval must be at least 10 seconds")
            int heartbeatIntervalSeconds,
            @Min(value = 1, message = "Max connections per client must be at least 1")
            int maxConnectionsPerClient,
            @Min(value = 1, message = "Max concurrent sessions must be at least 1")
            int maxConcurrentSessions,
            boolean requireTls
    ) {
        public ConnectionConfig {
            if (idleTimeoutSeconds <= 0) {
                idleTimeoutSeconds = 300;
            }
            if (heartbeatIntervalSeconds <= 0) {
                heartbeatIntervalSeconds = 30;
            }
            if (maxConnectionsPerClient <= 0) {
                maxConnectionsPerClient = 5;
            }
            if (maxConcurrentSessions <= 0) {
                maxConcurrentSessions = 10;
            }
        }
    }

    /**
     * Session lifecycle configuration
     */
    public record SessionConfig(
            @Min(value = 60, message = "Max session duration must be at least 60 seconds")
            int maxDurationSeconds,
            @Min(value = 10, message = "Tool call timeout must be at least 10 seconds")
            int toolCallTimeoutSeconds,
            @Min(value = 0, message = "Retry attempts cannot be negative")
            int toolCallRetryAttempts,
            @Min(value = 100, message = "Retry delay must be at least 100ms")
            int toolCallRetryDelayMs
    ) {
        public SessionConfig {
            if (maxDurationSeconds <= 0) {
                maxDurationSeconds = 600;
            }
            if (toolCallTimeoutSeconds <= 0) {
                toolCallTimeoutSeconds = 60;
            }
            if (toolCallRetryAttempts < 0) {
                toolCallRetryAttempts = 2;
            }
            if (toolCallRetryDelayMs <= 0) {
                toolCallRetryDelayMs = 1000;
            }
        }
    }

    /**
     * Streaming response configuration
     */
    public record StreamingConfig(
            boolean enabled,
            @Min(value = 1, message = "Chunk size must be at least 1")
            int chunkSize,
            @Min(value = 10, message = "Flush interval must be at least 10ms")
            int flushIntervalMs
    ) {
        public StreamingConfig {
            if (chunkSize <= 0) {
                chunkSize = 100;
            }
            if (flushIntervalMs <= 0) {
                flushIntervalMs = 50;
            }
        }
    }

    /**
     * Circuit breaker configuration for reliability
     */
    public record CircuitBreakerConfig(
            @Min(value = 1, message = "Failure threshold must be at least 1")
            int failureThreshold,
            @Min(value = 1, message = "Success threshold must be at least 1")
            int successThreshold,
            @Min(value = 5, message = "Timeout must be at least 5 seconds")
            int timeoutSeconds,
            @Min(value = 1, message = "Half-open max calls must be at least 1")
            int halfOpenMaxCalls
    ) {
        public CircuitBreakerConfig {
            if (failureThreshold <= 0) {
                failureThreshold = 5;
            }
            if (successThreshold <= 0) {
                successThreshold = 3;
            }
            if (timeoutSeconds <= 0) {
                timeoutSeconds = 30;
            }
            if (halfOpenMaxCalls <= 0) {
                halfOpenMaxCalls = 3;
            }
        }
    }

    /**
     * WebSocket endpoint configuration
     */
    public record WebSocketConfig(
            @NotBlank(message = "WebSocket endpoint path cannot be blank")
            String endpoint,
            @NotNull(message = "Allowed origins list cannot be null")
            List<String> allowedOrigins,
            @Min(value = 8192, message = "Max text message buffer size must be at least 8KB")
            int maxTextMessageBufferSize,
            @Min(value = 8192, message = "Max binary message buffer size must be at least 8KB")
            int maxBinaryMessageBufferSize,
            @Min(value = 60000, message = "Max session idle timeout must be at least 60 seconds")
            long maxSessionIdleTimeoutMs
    ) {
        public WebSocketConfig {
            if (endpoint == null || endpoint.isBlank()) {
                endpoint = "/agent";
            }
            if (allowedOrigins == null || allowedOrigins.isEmpty()) {
                allowedOrigins = List.of("*");
            }
            // Default to 10MB for text messages (handles large LLM responses)
            if (maxTextMessageBufferSize <= 0) {
                maxTextMessageBufferSize = 10 * 1024 * 1024; // 10MB
            }
            // Default to 10MB for binary messages
            if (maxBinaryMessageBufferSize <= 0) {
                maxBinaryMessageBufferSize = 10 * 1024 * 1024; // 10MB
            }
            // Default to 30 minutes idle timeout
            if (maxSessionIdleTimeoutMs <= 0) {
                maxSessionIdleTimeoutMs = 30 * 60 * 1000L; // 30 minutes
            }
        }
    }
}
