package com.davidparry.agent.session;

import com.davidparry.agent.config.McpProxyProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectionManagerTest {

    private ConnectionManager connectionManager;
    private SimpleMeterRegistry meterRegistry;
    private McpProxyProperties properties;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new McpProxyProperties(
                true,
                new McpProxyProperties.ConnectionConfig(300, 30, 5, 10, false),
                null,
                null,
                null,
                null
        );
        connectionManager = new ConnectionManager(properties, meterRegistry);
    }

    @Test
    void createConnection_createsNewConnection() {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.getId()).thenReturn("ws-123");

        ClientConnection connection = connectionManager.createConnection(wsSession, "test-client-001");

        assertNotNull(connection);
        assertNotNull(connection.getConnectionId());
        assertTrue(connection.getConnectionId().startsWith("conn-"));
        assertEquals("test-client-001", connection.getClientId());
        assertEquals(1, connectionManager.getConnectionCount());
    }

    @Test
    void removeConnection_removesConnection() {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.getId()).thenReturn("ws-123");

        ClientConnection connection = connectionManager.createConnection(wsSession, "test-client-001");
        String connectionId = connection.getConnectionId();

        connectionManager.removeConnection(connectionId);

        assertEquals(0, connectionManager.getConnectionCount());
        assertTrue(connectionManager.getConnection(connectionId).isEmpty());
    }

    @Test
    void getConnection_returnsConnection() {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.getId()).thenReturn("ws-123");

        ClientConnection connection = connectionManager.createConnection(wsSession, "test-client-001");

        var retrieved = connectionManager.getConnection(connection.getConnectionId());

        assertTrue(retrieved.isPresent());
        assertEquals(connection.getConnectionId(), retrieved.get().getConnectionId());
    }

    @Test
    void getConnectionByWebSocketId_returnsConnection() {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.getId()).thenReturn("ws-123");

        ClientConnection connection = connectionManager.createConnection(wsSession, "test-client-001");

        var retrieved = connectionManager.getConnectionByWebSocketId("ws-123");

        assertTrue(retrieved.isPresent());
        assertEquals(connection.getConnectionId(), retrieved.get().getConnectionId());
    }

    @Test
    void generateSessionId_generatesUniqueIds() {
        String id1 = connectionManager.generateSessionId();
        String id2 = connectionManager.generateSessionId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertTrue(id1.startsWith("sess-"));
        assertTrue(id2.startsWith("sess-"));
        assertNotEquals(id1, id2);
    }

    @Test
    void generateRequestId_generatesUniqueIds() {
        String id1 = connectionManager.generateRequestId();
        String id2 = connectionManager.generateRequestId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertTrue(id1.startsWith("req-"));
        assertTrue(id2.startsWith("req-"));
        assertNotEquals(id1, id2);
    }

    @Test
    void getAllConnections_returnsAllConnections() {
        WebSocketSession wsSession1 = mock(WebSocketSession.class);
        WebSocketSession wsSession2 = mock(WebSocketSession.class);
        when(wsSession1.getId()).thenReturn("ws-1");
        when(wsSession2.getId()).thenReturn("ws-2");

        connectionManager.createConnection(wsSession1, "client-001");
        connectionManager.createConnection(wsSession2, "client-002");

        assertEquals(2, connectionManager.getAllConnections().size());
    }

    @Test
    void disconnectAll_disconnectsAllConnections() {
        WebSocketSession wsSession1 = mock(WebSocketSession.class);
        WebSocketSession wsSession2 = mock(WebSocketSession.class);
        when(wsSession1.getId()).thenReturn("ws-1");
        when(wsSession2.getId()).thenReturn("ws-2");

        connectionManager.createConnection(wsSession1, "client-001");
        connectionManager.createConnection(wsSession2, "client-002");

        connectionManager.disconnectAll("Test shutdown");

        assertEquals(0, connectionManager.getConnectionCount());
    }
}
