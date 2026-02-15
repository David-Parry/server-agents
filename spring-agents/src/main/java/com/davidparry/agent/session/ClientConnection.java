package com.davidparry.agent.session;

import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a WebSocket connection from a remote agent-client.
 * Manages multiple concurrent prompt sessions within a single connection.
 */
public final class ClientConnection {

    private final String connectionId;
    private final String clientId;
    private final WebSocketSession webSocketSession;
    private final Instant connectedAt;
    private final int maxConcurrentSessions;

    private final Map<String, PromptSession> sessions;
    private final AtomicInteger sessionCount;
    private final AtomicLong totalSessionsCreated;
    private final AtomicLong totalToolCalls;

    private volatile ConnectionState state;
    private volatile Instant lastActivityAt;
    private volatile String disconnectReason;

    private ClientConnection(Builder builder) {
        this.connectionId = builder.connectionId;
        this.clientId = builder.clientId;
        this.webSocketSession = builder.webSocketSession;
        this.connectedAt = Instant.now();
        this.maxConcurrentSessions = builder.maxConcurrentSessions;

        this.sessions = new ConcurrentHashMap<>();
        this.sessionCount = new AtomicInteger(0);
        this.totalSessionsCreated = new AtomicLong(0);
        this.totalToolCalls = new AtomicLong(0);

        this.state = ConnectionState.ACTIVE;
        this.lastActivityAt = connectedAt;
    }

    // ==================== Getters ====================

    public String getConnectionId() {
        return connectionId;
    }

    public String getClientId() {
        return clientId;
    }

    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public ConnectionState getState() {
        return state;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public String getDisconnectReason() {
        return disconnectReason;
    }

    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    public int getActiveSessionCount() {
        return sessionCount.get();
    }

    public long getTotalSessionsCreated() {
        return totalSessionsCreated.get();
    }

    public long getTotalToolCalls() {
        return totalToolCalls.get();
    }

    // ==================== Session Management ====================

    /**
     * Checks if the connection can accept a new session.
     *
     * @return true if under session limit and in active state
     */
    public boolean canAcceptSession() {
        return state == ConnectionState.ACTIVE
               && sessionCount.get() < maxConcurrentSessions;
    }

    /**
     * Adds a new session to this connection.
     *
     * @param session the session to add
     * @return true if added successfully, false if at capacity or not active
     */
    public boolean addSession(PromptSession session) {
        if (!canAcceptSession()) {
            return false;
        }

        if (sessions.putIfAbsent(session.getSessionId(), session) == null) {
            sessionCount.incrementAndGet();
            totalSessionsCreated.incrementAndGet();
            updateActivity();
            return true;
        }
        return false;
    }

    /**
     * Gets a session by ID.
     *
     * @param sessionId the session ID
     * @return optional containing the session if found
     */
    public Optional<PromptSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Updates a session in this connection.
     * Used when session state changes (since PromptSession is immutable).
     *
     * @param session the updated session
     * @return true if the session was updated, false if not found
     */
    public boolean updateSession(PromptSession session) {
        if (sessions.containsKey(session.getSessionId())) {
            sessions.put(session.getSessionId(), session);
            updateActivity();
            return true;
        }
        return false;
    }

    /**
     * Removes a session from this connection.
     *
     * @param sessionId the session ID
     * @return the removed session, or null if not found
     */
    public PromptSession removeSession(String sessionId) {
        PromptSession removed = sessions.remove(sessionId);
        if (removed != null) {
            sessionCount.decrementAndGet();
            updateActivity();
        }
        return removed;
    }

    /**
     * Gets all active sessions.
     *
     * @return collection of sessions
     */
    public Collection<PromptSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * Checks if there are any active sessions.
     *
     * @return true if there are sessions
     */
    public boolean hasActiveSessions() {
        return !sessions.isEmpty();
    }

    /**
     * Increments the tool call counter.
     */
    public void incrementToolCalls() {
        totalToolCalls.incrementAndGet();
        updateActivity();
    }

    // ==================== State Management ====================

    /**
     * Updates the last activity timestamp.
     */
    public void updateActivity() {
        this.lastActivityAt = Instant.now();
    }

    /**
     * Initiates graceful shutdown - stops accepting new sessions.
     */
    public synchronized void initiateGracefulShutdown() {
        if (state == ConnectionState.ACTIVE) {
            state = ConnectionState.DRAINING;
        }
    }

    /**
     * Marks the connection as disconnected.
     *
     * @param reason the disconnect reason
     */
    public synchronized void disconnect(String reason) {
        if (state != ConnectionState.DISCONNECTED && state != ConnectionState.FAILED) {
            state = ConnectionState.DISCONNECTED;
            disconnectReason = reason;
            cancelAllSessions("Connection disconnected: " + reason);
        }
    }

    /**
     * Marks the connection as failed.
     *
     * @param reason the failure reason
     */
    public synchronized void fail(String reason) {
        if (state != ConnectionState.FAILED) {
            state = ConnectionState.FAILED;
            disconnectReason = reason;
            cancelAllSessions("Connection failed: " + reason);
        }
    }

    /**
     * Cancels all active sessions.
     *
     * @param reason the cancellation reason
     */
    public void cancelAllSessions(String reason) {
        sessions.values().forEach(session -> {
            if (!session.isTerminal()) {
                session.cancel(reason);
            }
        });
    }

    /**
     * Checks if the connection is usable for new operations.
     *
     * @return true if active
     */
    public boolean isActive() {
        return state == ConnectionState.ACTIVE;
    }

    /**
     * Checks if the connection is in a terminal state.
     *
     * @return true if disconnected or failed
     */
    public boolean isTerminal() {
        return state == ConnectionState.DISCONNECTED || state == ConnectionState.FAILED;
    }

    /**
     * Checks if the WebSocket session is still open.
     *
     * @return true if WebSocket is open
     */
    public boolean isWebSocketOpen() {
        return webSocketSession != null && webSocketSession.isOpen();
    }

    /**
     * Gets the duration of the connection in milliseconds.
     *
     * @return duration in ms
     */
    public long getDurationMs() {
        return Instant.now().toEpochMilli() - connectedAt.toEpochMilli();
    }

    /**
     * Gets the idle time in milliseconds.
     *
     * @return idle time in ms
     */
    public long getIdleTimeMs() {
        return Instant.now().toEpochMilli() - lastActivityAt.toEpochMilli();
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String connectionId;
        private String clientId;
        private WebSocketSession webSocketSession;
        private int maxConcurrentSessions = 10;

        public Builder connectionId(String connectionId) {
            this.connectionId = connectionId;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder webSocketSession(WebSocketSession webSocketSession) {
            this.webSocketSession = webSocketSession;
            return this;
        }

        public Builder maxConcurrentSessions(int maxConcurrentSessions) {
            this.maxConcurrentSessions = maxConcurrentSessions;
            return this;
        }

        public ClientConnection build() {
            if (connectionId == null || connectionId.isBlank()) {
                throw new IllegalArgumentException("Connection ID is required");
            }
            if (clientId == null || clientId.isBlank()) {
                throw new IllegalArgumentException("Client ID is required");
            }
            if (webSocketSession == null) {
                throw new IllegalArgumentException("WebSocket session is required");
            }
            return new ClientConnection(this);
        }
    }

    @Override
    public String toString() {
        return "ClientConnection{"
                + "connectionId='" + connectionId + '\''
                + ", clientId='" + clientId + '\''
                + ", state=" + state
                + ", activeSessions=" + sessionCount.get()
                + ", totalSessions=" + totalSessionsCreated.get()
                + '}';
    }
}
