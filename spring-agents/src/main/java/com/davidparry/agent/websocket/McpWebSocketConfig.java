package com.davidparry.agent.websocket;

import com.davidparry.agent.config.McpProxyProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket configuration for the MCP Proxy endpoint.
 * Only enabled when mcp.proxy.enabled=true.
 */
@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "mcp.proxy.enabled", havingValue = "true")
public class McpWebSocketConfig implements WebSocketConfigurer {

    private final McpProxyWebSocketHandler handler;
    private final ApiKeyHandshakeInterceptor interceptor;
    private final McpProxyProperties properties;

    public McpWebSocketConfig(
            McpProxyWebSocketHandler handler,
            ApiKeyHandshakeInterceptor interceptor,
            McpProxyProperties properties) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        McpProxyProperties.WebSocketConfig wsConfig = properties.webSocket();
        registry.addHandler(handler, wsConfig.endpoint())
                .addInterceptors(interceptor)
                .setAllowedOrigins(wsConfig.allowedOrigins().toArray(String[]::new));
    }

    /**
     * Configure WebSocket container with increased buffer sizes.
     * This addresses the "decoded text message was too big" error (close code 1009).
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        McpProxyProperties.WebSocketConfig wsConfig = properties.webSocket();
        // Set text buffer size (default is 8KB, increasing to handle larger messages)
        container.setMaxTextMessageBufferSize(wsConfig.maxTextMessageBufferSize());
        // Set binary buffer size
        container.setMaxBinaryMessageBufferSize(wsConfig.maxBinaryMessageBufferSize());
        // Set max session idle timeout (in milliseconds)
        container.setMaxSessionIdleTimeout(wsConfig.maxSessionIdleTimeoutMs());
        return container;
    }
}
