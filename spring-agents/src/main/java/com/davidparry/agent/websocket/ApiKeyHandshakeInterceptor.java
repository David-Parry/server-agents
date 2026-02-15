package com.davidparry.agent.websocket;

import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.security.DatabaseApiKeyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket handshake interceptor that validates API keys (JWT tokens) before allowing connections.
 * Uses database-backed token validation with one-way hashing.
 *
 * Note: Per-model token limits are checked later in PromptExecutionService,
 * not during the handshake.
 */
@Component
public class ApiKeyHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiKeyHandshakeInterceptor.class);

    public static final String CLIENT_ID_ATTRIBUTE = "clientId";
    public static final String API_KEY_HEADER = "X-API-Key";

    private final DatabaseApiKeyValidator apiKeyValidator;
    private final McpProxyProperties mcpProxyProperties;

    public ApiKeyHandshakeInterceptor(DatabaseApiKeyValidator apiKeyValidator,
                                      McpProxyProperties mcpProxyProperties) {
        this.apiKeyValidator = apiKeyValidator;
        this.mcpProxyProperties = mcpProxyProperties;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        // Check if proxy is enabled
        if (!mcpProxyProperties.enabled()) {
            LOGGER.warn("MCP Proxy is disabled, rejecting WebSocket connection");
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }

        // Extract API key (JWT token) from header
        String apiKey = request.getHeaders().getFirst(API_KEY_HEADER);

        // Also check query parameter as fallback (for clients that can't set headers)
        if (apiKey == null || apiKey.isBlank()) {
            String query = request.getURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("apiKey=")) {
                        apiKey = param.substring(7);
                        break;
                    }
                }
            }
        }

        // Validate token against database
        DatabaseApiKeyValidator.ValidationResult result = apiKeyValidator.validateToken(apiKey);

        if (!result.valid()) {
            LOGGER.warn("Unauthorized WebSocket connection attempt: {}", result.rejectionReason());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // Store customer id in attributes for use in handler
        attributes.put(CLIENT_ID_ATTRIBUTE, result.customerId());

        LOGGER.info("WebSocket handshake authorized for customer: {}", result.customerId());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {

        if (exception != null) {
            LOGGER.error("WebSocket handshake failed", exception);
        }
    }
}
