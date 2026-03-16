package com.davidparry.agent.app.health;

import com.davidparry.agent.sdk.mcp.McpServerManager;
import com.davidparry.agent.sdk.websocket.ConnectionState;
import com.davidparry.agent.sdk.websocket.WebSocketClientHandler;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSdkHealthIndicatorTest {

    @Test
    void reportsUpWhenConnected() {
        WebSocketClientHandler webSocketHandler = mock(WebSocketClientHandler.class);
        McpServerManager mcpServerManager = mock(McpServerManager.class);
        when(webSocketHandler.getState()).thenReturn(ConnectionState.CONNECTED);
        when(webSocketHandler.isConnected()).thenReturn(true);
        when(webSocketHandler.getConnectionId()).thenReturn("conn-1");
        when(webSocketHandler.getLastActivity()).thenReturn(Instant.now().minusSeconds(1));
        when(mcpServerManager.getConfiguredServerCount()).thenReturn(3);
        when(mcpServerManager.getActiveSessionCount()).thenReturn(1);
        when(mcpServerManager.getSandboxBasePath()).thenReturn("/tmp/agents");

        AgentSdkHealthIndicator indicator = new AgentSdkHealthIndicator(webSocketHandler, mcpServerManager);
        var health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals("CONNECTED", health.getDetails().get("connectionState"));
        assertEquals(3, health.getDetails().get("configuredMcpServers"));
    }

    @Test
    void reportsDownWithReconnectionReasonWhenNotConnected() {
        WebSocketClientHandler webSocketHandler = mock(WebSocketClientHandler.class);
        McpServerManager mcpServerManager = mock(McpServerManager.class);
        when(webSocketHandler.getState()).thenReturn(ConnectionState.RECONNECTING);
        when(webSocketHandler.isConnected()).thenReturn(false);
        when(webSocketHandler.getConnectionId()).thenReturn("disconnected");
        when(mcpServerManager.getConfiguredServerCount()).thenReturn(0);
        when(mcpServerManager.getActiveSessionCount()).thenReturn(0);
        when(mcpServerManager.getSandboxBasePath()).thenReturn("not-configured");

        AgentSdkHealthIndicator indicator = new AgentSdkHealthIndicator(webSocketHandler, mcpServerManager);
        var health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("Attempting to reconnect", health.getDetails().get("reason"));
        assertNotNull(health.getDetails().get("connectionState"));
    }
}
