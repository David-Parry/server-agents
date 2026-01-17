package com.davidparry.agent.client.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Agent Client.
 */
@Validated
@ConfigurationProperties(prefix = "agent.client")
public record AgentClientProperties(
    @NotBlank String apiKey,
    @NotBlank String serverUrl,
    @Valid ConnectionConfig connection,
    @Valid ReconnectConfig reconnect,
    @Valid ChainConfig chain,
    String agentConfigPath,
    @Valid McpConfig mcp
) {
    
    /** Default classpath location for agent config */
    public static final String DEFAULT_AGENT_CONFIG = "agent.yml";
    
    /** Default MCP request timeout in minutes */
    public static final int DEFAULT_MCP_TIMEOUT_MINUTES = 5;
    
    /** Default WebSocket text message buffer size (10 MB) */
    public static final int DEFAULT_TEXT_MESSAGE_BUFFER_SIZE = 10 * 1024 * 1024;
    
    /** Default chain reconnect timeout in seconds */
    public static final int DEFAULT_CHAIN_RECONNECT_TIMEOUT_SECONDS = 60;
    
    /** Default maximum chain length */
    public static final int DEFAULT_MAX_CHAIN_LENGTH = 10;
    
    public AgentClientProperties {
        if (connection == null) connection = new ConnectionConfig(30, 30, 300, DEFAULT_TEXT_MESSAGE_BUFFER_SIZE);
        if (reconnect == null) reconnect = new ReconnectConfig(true, 5, 1000, 30000, 2.0);
        if (chain == null) chain = new ChainConfig(DEFAULT_CHAIN_RECONNECT_TIMEOUT_SECONDS, DEFAULT_MAX_CHAIN_LENGTH);
        if (mcp == null) mcp = new McpConfig(DEFAULT_MCP_TIMEOUT_MINUTES);
        // agentConfigPath is optional - null means use default classpath lookup
        // MCP servers are loaded from the Agent file's mcpServers field
    }

    /**
     * WebSocket connection configuration.
     */
    public record ConnectionConfig(
        @Min(5) int heartbeatIntervalSeconds,
        @Min(5) int connectionTimeoutSeconds,
        @Min(60) int idleTimeoutSeconds,
        @Min(65536) int textMessageBufferSize
    ) {
        /**
         * Backward-compatible constructor for existing code.
         */
        public ConnectionConfig(int heartbeatIntervalSeconds, int connectionTimeoutSeconds, int idleTimeoutSeconds) {
            this(heartbeatIntervalSeconds, connectionTimeoutSeconds, idleTimeoutSeconds, DEFAULT_TEXT_MESSAGE_BUFFER_SIZE);
        }
    }

    /**
     * Reconnection strategy configuration.
     */
    public record ReconnectConfig(
        boolean enabled,
        @Min(1) @Max(10) int maxAttempts,
        @Min(100) long initialDelayMs,
        @Min(1000) long maxDelayMs,
        @DecimalMin("1.0") @DecimalMax("5.0") double backoffMultiplier
    ) {
        public ReconnectConfig {
            if (backoffMultiplier == 0) backoffMultiplier = 2.0;
        }
    }

    /**
     * MCP (Model Context Protocol) client configuration.
     */
    public record McpConfig(
        @Min(1) int requestTimeoutMinutes,
        String sandboxBasePath
    ) {
        /**
         * Backward-compatible constructor for existing code.
         */
        public McpConfig(int requestTimeoutMinutes) {
            this(requestTimeoutMinutes, null);
        }
    }

    /**
     * Chain execution configuration.
     * 
     * Controls behavior when agents are chained together, including
     * reconnection handling if WebSocket disconnects mid-chain.
     */
    public record ChainConfig(
        @Min(10) @Max(300) int reconnectTimeoutSeconds,
        @Min(1) @Max(20) int maxChainLength
    ) {
        /**
         * Default constructor with standard values.
         */
        public ChainConfig() {
            this(DEFAULT_CHAIN_RECONNECT_TIMEOUT_SECONDS, DEFAULT_MAX_CHAIN_LENGTH);
        }
    }
}
