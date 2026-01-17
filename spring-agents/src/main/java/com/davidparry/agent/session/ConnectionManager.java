package com.davidparry.agent.session;

import com.davidparry.agent.config.McpProxyProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active WebSocket connections and their sessions.
 * Provides centralized access to connections and sessions across the application.
 */
@Component
public class ConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);

    private final McpProxyProperties properties;
    private final Map<String, ClientConnection> connections;
    private final Map<String, String> sessionToConnectionMap;

    public ConnectionManager(McpProxyProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.connections = new ConcurrentHashMap<>();
        this.sessionToConnectionMap = new ConcurrentHashMap<>();

        // Register metrics
        Gauge.builder("mcp.connections.active", connections, Map::size)
                .description("Number of active WebSocket connections")
                .register(meterRegistry);

        Gauge.builder("mcp.sessions.active", this, cm -> 
                cm.getAllConnections().stream()
                        .mapToInt(ClientConnection::getActiveSessionCount)
                        .sum())
                .description("Number of active prompt sessions")
                .register(meterRegistry);
    }

    /**
     * Creates a new connection for a WebSocket session.
     *
     * @param webSocketSession the WebSocket session
     * @param clientId the authenticated client ID
     * @return the created connection
     */
    public ClientConnection createConnection(WebSocketSession webSocketSession, String clientId) {
        String connectionId = generateConnectionId();
        
        ClientConnection connection = ClientConnection.builder()
                .connectionId(connectionId)
                .clientId(clientId)
                .webSocketSession(webSocketSession)
                .maxConcurrentSessions(properties.connection().maxConcurrentSessions())
                .build();

        connections.put(connectionId, connection);
        logger.info("Created connection {} for client {}", connectionId, clientId);
        
        return connection;
    }

    /**
     * Removes a connection and all its sessions.
     *
     * @param connectionId the connection ID
     */
    public void removeConnection(String connectionId) {
        ClientConnection connection = connections.remove(connectionId);
        if (connection != null) {
            // Remove all session mappings
            connection.getAllSessions().forEach(session -> 
                sessionToConnectionMap.remove(session.getSessionId())
            );
            logger.info("Removed connection {} for client {}", connectionId, connection.getClientId());
        }
    }

    /**
     * Gets a connection by ID.
     *
     * @param connectionId the connection ID
     * @return optional containing the connection if found
     */
    public Optional<ClientConnection> getConnection(String connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    /**
     * Gets a connection by WebSocket session ID.
     *
     * @param webSocketSessionId the WebSocket session ID
     * @return optional containing the connection if found
     */
    public Optional<ClientConnection> getConnectionByWebSocketId(String webSocketSessionId) {
        return connections.values().stream()
                .filter(conn -> conn.getWebSocketSession().getId().equals(webSocketSessionId))
                .findFirst();
    }

    /**
     * Finds a session across all connections.
     *
     * @param sessionId the session ID
     * @return optional containing the session if found
     */
    public Optional<PromptSession> findSession(String sessionId) {
        String connectionId = sessionToConnectionMap.get(sessionId);
        if (connectionId != null) {
            return getConnection(connectionId)
                    .flatMap(conn -> conn.getSession(sessionId));
        }
        
        // Fallback: search all connections
        return connections.values().stream()
                .map(conn -> conn.getSession(sessionId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * Registers a session with the manager for quick lookup.
     *
     * @param session the session to register
     */
    public void registerSession(PromptSession session) {
        sessionToConnectionMap.put(session.getSessionId(), session.getConnection().getConnectionId());
    }

    /**
     * Unregisters a session from the manager.
     *
     * @param sessionId the session ID
     */
    public void unregisterSession(String sessionId) {
        sessionToConnectionMap.remove(sessionId);
    }

    /**
     * Gets all active connections.
     *
     * @return collection of connections
     */
    public Collection<ClientConnection> getAllConnections() {
        return connections.values();
    }

    /**
     * Gets the total number of active connections.
     *
     * @return connection count
     */
    public int getConnectionCount() {
        return connections.size();
    }

    /**
     * Gets the total number of active sessions across all connections.
     *
     * @return session count
     */
    public int getTotalSessionCount() {
        return connections.values().stream()
                .mapToInt(ClientConnection::getActiveSessionCount)
                .sum();
    }

    /**
     * Initiates graceful shutdown of all connections.
     */
    public void initiateGracefulShutdown() {
        logger.info("Initiating graceful shutdown of {} connections", connections.size());
        connections.values().forEach(ClientConnection::initiateGracefulShutdown);
    }

    /**
     * Forces disconnection of all connections.
     *
     * @param reason the disconnect reason
     */
    public void disconnectAll(String reason) {
        logger.info("Disconnecting all {} connections: {}", connections.size(), reason);
        connections.values().forEach(conn -> conn.disconnect(reason));
        connections.clear();
        sessionToConnectionMap.clear();
    }

    /**
     * Generates a unique connection ID.
     *
     * @return the generated ID
     */
    public String generateConnectionId() {
        return "conn-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generates a unique session ID.
     *
     * @return the generated ID
     */
    public String generateSessionId() {
        return "sess-" + UUID.randomUUID().toString().substring(0, 12);
    }

    /**
     * Generates a unique request ID for tool calls.
     *
     * @return the generated ID
     */
    public String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ==================== Customer-based Connection Lookup ====================

    /**
     * Gets all connections for a specific customer.
     *
     * @param customerId the customer's UUID string
     * @return collection of connections for this customer
     */
    public Collection<ClientConnection> getConnectionsByCustomerId(String customerId) {
        return connections.values().stream()
                .filter(conn -> customerId.equals(conn.getClientId()))
                .toList();
    }

    /**
     * Checks if a customer has any active WebSocket connections.
     *
     * @param customerId the customer's UUID string
     * @return true if customer has at least one active connection
     */
    public boolean isCustomerConnected(String customerId) {
        return connections.values().stream()
                .filter(conn -> customerId.equals(conn.getClientId()))
                .anyMatch(ClientConnection::isActive);
    }

    /**
     * Gets the count of active connections for a customer.
     *
     * @param customerId the customer's UUID string
     * @return number of active connections
     */
    public int getConnectionCountForCustomer(String customerId) {
        return (int) connections.values().stream()
                .filter(conn -> customerId.equals(conn.getClientId()))
                .filter(ClientConnection::isActive)
                .count();
    }

    /**
     * Gets the count of unique customers with active connections.
     *
     * @return number of connected customers
     */
    public long getConnectedCustomerCount() {
        return connections.values().stream()
                .filter(ClientConnection::isActive)
                .map(ClientConnection::getClientId)
                .distinct()
                .count();
    }

    /**
     * Gets the set of customer IDs that have active connections.
     *
     * @return set of customer IDs
     */
    public Set<String> getConnectedCustomerIds() {
        return connections.values().stream()
                .filter(ClientConnection::isActive)
                .map(ClientConnection::getClientId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
