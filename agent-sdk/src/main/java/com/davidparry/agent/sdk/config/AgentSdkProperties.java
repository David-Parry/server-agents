package com.davidparry.agent.sdk.config;

/**
 * Configuration properties for the Agent Client.
 * <p>
 * Required properties: {@code apiKey}, {@code serverUrl}, and {@code agentConfigPath}.
 * All other properties have sensible defaults.
 */
public record AgentSdkProperties(
    String apiKey,
    String serverUrl,
    ConnectionConfig connection,
    ReconnectConfig reconnect,
    ChainConfig chain,
    String agentConfigPath,
    McpConfig mcp
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

    public AgentSdkProperties {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalArgumentException("agent.client.api-key is required");
        if (serverUrl == null || serverUrl.isBlank())
            throw new IllegalArgumentException("agent.client.server-url is required");
        if (agentConfigPath == null || agentConfigPath.isBlank())
            throw new IllegalArgumentException("agent.client.agent-config-path is required");
        if (connection == null) connection = new ConnectionConfig(30, 30, 300, DEFAULT_TEXT_MESSAGE_BUFFER_SIZE);
        if (reconnect == null) reconnect = new ReconnectConfig(true, 5, 1000, 30000, 2.0);
        if (chain == null) chain = new ChainConfig(DEFAULT_CHAIN_RECONNECT_TIMEOUT_SECONDS, DEFAULT_MAX_CHAIN_LENGTH);
        if (mcp == null) mcp = new McpConfig(DEFAULT_MCP_TIMEOUT_MINUTES);
    }

    /**
     * WebSocket connection configuration.
     */
    public record ConnectionConfig(
        int heartbeatIntervalSeconds,
        int connectionTimeoutSeconds,
        int idleTimeoutSeconds,
        int textMessageBufferSize
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
        int maxAttempts,
        long initialDelayMs,
        long maxDelayMs,
        double backoffMultiplier
    ) {
        public ReconnectConfig {
            if (backoffMultiplier == 0) backoffMultiplier = 2.0;
        }
    }

    /**
     * MCP (Model Context Protocol) client configuration.
     */
    public record McpConfig(
        int requestTimeoutMinutes,
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
        int reconnectTimeoutSeconds,
        int maxChainLength
    ) {
        /**
         * Default constructor with standard values.
         */
        public ChainConfig() {
            this(DEFAULT_CHAIN_RECONNECT_TIMEOUT_SECONDS, DEFAULT_MAX_CHAIN_LENGTH);
        }
    }
}
