package com.davidparry.agent.sdk.health;

import com.davidparry.agent.sdk.mcp.McpServerManager;
import com.davidparry.agent.sdk.websocket.ConnectionState;
import com.davidparry.agent.sdk.websocket.WebSocketClientHandler;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class AgentSdkHealthIndicator implements HealthIndicator {
    
    private final WebSocketClientHandler webSocketHandler;
    private final McpServerManager mcpServerManager;
    
    public AgentSdkHealthIndicator(
            WebSocketClientHandler webSocketHandler,
            McpServerManager mcpServerManager) {
        this.webSocketHandler = webSocketHandler;
        this.mcpServerManager = mcpServerManager;
    }
    
    @Override
    public Health health() {
        ConnectionState state = webSocketHandler.getState();
        boolean connected = webSocketHandler.isConnected();
        
        Health.Builder builder = connected ? Health.up() : Health.down();
        
        builder.withDetail("connectionState", state.name())
               .withDetail("connectionId", webSocketHandler.getConnectionId())
               .withDetail("configuredMcpServers", mcpServerManager.getConfiguredServerCount())
               .withDetail("activeSessions", mcpServerManager.getActiveSessionCount())
               .withDetail("sandboxBasePath", mcpServerManager.getSandboxBasePath());
        
        Instant lastActivity = webSocketHandler.getLastActivity();
        if (lastActivity != null) {
            builder.withDetail("lastActivityAgo", 
                Duration.between(lastActivity, Instant.now()).toSeconds() + "s");
        }
        
        if (!connected) {
            builder.withDetail("reason", getDisconnectionReason(state));
        }
        
        return builder.build();
    }
    
    private String getDisconnectionReason(ConnectionState state) {
        return switch (state) {
            case DISCONNECTED -> "Not connected to server";
            case CONNECTING -> "Connection in progress";
            case RECONNECTING -> "Attempting to reconnect";
            case DRAINING -> "Shutting down";
            case FAILED -> "Connection failed - max retries exceeded";
            default -> "Unknown";
        };
    }
}
