package com.davidparry.agent.app.config;

import com.davidparry.agent.sdk.config.AgentSdkProperties;
import com.davidparry.agent.sdk.config.JacksonConfig;
import com.davidparry.agent.sdk.context.AgentApplicationContext;
import com.davidparry.agent.sdk.mcp.McpServerManager;
import com.davidparry.agent.sdk.observability.ClientMetrics;
import com.davidparry.agent.sdk.reliability.ReconnectionManager;
import com.davidparry.agent.sdk.websocket.WebSocketClientHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;

/**
 * Spring Boot configuration that wires all agent-sdk beans.
 * <p>
 * The agent-sdk module is a plain Java library with no Spring annotations.
 * This configuration class bridges it into the Spring context by creating
 * and initializing all SDK beans, and calling their lifecycle methods.
 */
@Configuration
public class AgentSdkAutoConfiguration {

    @Bean
    public AgentSdkProperties agentSdkProperties(
            @Value("${agent.client.api-key}") String apiKey,
            @Value("${agent.client.server-url}") String serverUrl,
            @Value("${agent.client.agent-config-path}") String agentConfigPath,
            @Value("${agent.client.connection.heartbeat-interval-seconds:30}") int heartbeatIntervalSeconds,
            @Value("${agent.client.connection.connection-timeout-seconds:30}") int connectionTimeoutSeconds,
            @Value("${agent.client.connection.idle-timeout-seconds:300}") int idleTimeoutSeconds,
            @Value("${agent.client.connection.text-message-buffer-size:10485760}") int textMessageBufferSize,
            @Value("${agent.client.reconnect.enabled:true}") boolean reconnectEnabled,
            @Value("${agent.client.reconnect.max-attempts:5}") int maxAttempts,
            @Value("${agent.client.reconnect.initial-delay-ms:1000}") long initialDelayMs,
            @Value("${agent.client.reconnect.max-delay-ms:30000}") long maxDelayMs,
            @Value("${agent.client.reconnect.backoff-multiplier:2.0}") double backoffMultiplier,
            @Value("${agent.client.chain.reconnect-timeout-seconds:60}") int chainReconnectTimeoutSeconds,
            @Value("${agent.client.chain.max-chain-length:10}") int maxChainLength,
            @Value("${agent.client.mcp.request-timeout-minutes:5}") int mcpRequestTimeoutMinutes,
            @Value("${agent.client.mcp.sandbox-base-path:#{null}}") String sandboxBasePath
    ) {
        return new AgentSdkProperties(
                apiKey,
                serverUrl,
                new AgentSdkProperties.ConnectionConfig(
                        heartbeatIntervalSeconds, connectionTimeoutSeconds,
                        idleTimeoutSeconds, textMessageBufferSize),
                new AgentSdkProperties.ReconnectConfig(
                        reconnectEnabled, maxAttempts, initialDelayMs,
                        maxDelayMs, backoffMultiplier),
                new AgentSdkProperties.ChainConfig(chainReconnectTimeoutSeconds, maxChainLength),
                agentConfigPath,
                new AgentSdkProperties.McpConfig(mcpRequestTimeoutMinutes, sandboxBasePath)
        );
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JacksonConfig.createObjectMapper();
    }

    @Bean
    public ClientMetrics clientMetrics(MeterRegistry registry) {
        return new ClientMetrics(registry);
    }

    @Bean
    public ReconnectionManager reconnectionManager(AgentSdkProperties properties) {
        return new ReconnectionManager(properties);
    }

    @Bean(destroyMethod = "shutdown")
    public McpServerManager mcpServerManager(AgentSdkProperties properties, ObjectMapper objectMapper) {
        McpServerManager manager = new McpServerManager(properties, objectMapper);
        manager.init();
        return manager;
    }

    @Bean(destroyMethod = "shutdown")
    public WebSocketClientHandler webSocketClientHandler(
            AgentSdkProperties properties,
            ObjectMapper objectMapper,
            McpServerManager mcpServerManager,
            ClientMetrics clientMetrics,
            ApplicationContext applicationContext) {
        WebSocketClientHandler handler = new WebSocketClientHandler(
                properties, objectMapper, mcpServerManager, clientMetrics);

        // Configure the fatal connection failure hook to shut down the Spring Boot app
        handler.setOnFatalConnectionFailure(() -> {
            int exitCode = SpringApplication.exit(applicationContext, () -> 1);
            System.exit(exitCode);
        });

        return handler;
    }

    @Bean(initMethod = "init", destroyMethod = "shutdown")
    public AgentApplicationContext agentApplicationContext(
            AgentSdkProperties properties,
            ObjectMapper objectMapper,
            WebSocketClientHandler webSocketHandler,
            McpServerManager mcpServerManager) {
        AgentApplicationContext ctx = new AgentApplicationContext(
                properties, objectMapper, webSocketHandler, mcpServerManager);

        // Wire the circular dependency: WebSocketClientHandler needs AgentApplicationContext
        webSocketHandler.setAgentApplicationContext(ctx);

        // Now that the circular dependency is resolved, initialize the WebSocket handler
        webSocketHandler.init();

        return ctx;
    }
}
