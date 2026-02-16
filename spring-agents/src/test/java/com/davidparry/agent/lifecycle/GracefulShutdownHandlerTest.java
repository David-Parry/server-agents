package com.davidparry.agent.lifecycle;

import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for GracefulShutdownHandler.
 */
@ExtendWith(MockitoExtension.class)
class GracefulShutdownHandlerTest {

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private McpProxyProperties properties;

    @Mock
    private ContextClosedEvent event;

    private GracefulShutdownHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GracefulShutdownHandler(connectionManager, properties);
    }

    @Test
    void onApplicationShutdown_shouldDoNothingWhenProxyDisabled() {
        // Given
        when(properties.enabled()).thenReturn(false);

        // When
        handler.onApplicationShutdown(event);

        // Then
        verify(connectionManager, never()).getConnectionCount();
        verify(connectionManager, never()).initiateGracefulShutdown();
    }

    @Test
    void onApplicationShutdown_shouldCompleteImmediatelyWhenNoConnections() {
        // Given
        when(properties.enabled()).thenReturn(true);
        when(connectionManager.getConnectionCount()).thenReturn(0);

        // When
        handler.onApplicationShutdown(event);

        // Then
        verify(connectionManager, never()).initiateGracefulShutdown();
        verify(connectionManager, never()).disconnectAll(anyString());
    }

    @Test
    void onApplicationShutdown_shouldInitiateGracefulShutdownWithConnections() {
        // Given
        when(properties.enabled()).thenReturn(true);
        when(connectionManager.getConnectionCount()).thenReturn(2);
        when(connectionManager.getTotalSessionCount()).thenReturn(3, 0); // First call returns 3, then 0

        // When
        handler.onApplicationShutdown(event);

        // Then
        verify(connectionManager).initiateGracefulShutdown();
    }

    @Test
    void onApplicationShutdown_shouldForceDisconnectRemainingConnections() throws Exception {
        // Given
        when(properties.enabled()).thenReturn(true);
        when(connectionManager.getConnectionCount()).thenReturn(2, 1); // Still has connections after drain
        when(connectionManager.getTotalSessionCount()).thenReturn(0); // No active sessions

        ClientConnection connection = mock(ClientConnection.class);
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(connection.isWebSocketOpen()).thenReturn(true);
        when(connection.getWebSocketSession()).thenReturn(wsSession);
        when(connectionManager.getAllConnections()).thenReturn(List.of(connection));

        // When
        handler.onApplicationShutdown(event);

        // Then
        verify(connectionManager).disconnectAll("Server shutdown");
        verify(wsSession).close();
    }

    @Test
    void onApplicationShutdown_shouldHandleWebSocketCloseException() throws Exception {
        // Given
        when(properties.enabled()).thenReturn(true);
        when(connectionManager.getConnectionCount()).thenReturn(1, 1);
        when(connectionManager.getTotalSessionCount()).thenReturn(0);

        ClientConnection connection = mock(ClientConnection.class);
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(connection.isWebSocketOpen()).thenReturn(true);
        when(connection.getWebSocketSession()).thenReturn(wsSession);
        when(connection.getConnectionId()).thenReturn("conn-123");
        doThrow(new java.io.IOException("Close failed")).when(wsSession).close();
        when(connectionManager.getAllConnections()).thenReturn(List.of(connection));

        // When - should not throw
        handler.onApplicationShutdown(event);

        // Then - no exception thrown, close was attempted
    }

    @Test
    void onApplicationShutdown_shouldSkipClosedWebSockets() throws Exception {
        // Given
        when(properties.enabled()).thenReturn(true);
        when(connectionManager.getConnectionCount()).thenReturn(1, 1);
        when(connectionManager.getTotalSessionCount()).thenReturn(0);

        ClientConnection connection = mock(ClientConnection.class);
        when(connection.isWebSocketOpen()).thenReturn(false);
        when(connectionManager.getAllConnections()).thenReturn(List.of(connection));

        // When
        handler.onApplicationShutdown(event);

        // Then
        verify(connection, never()).getWebSocketSession();
    }
}
