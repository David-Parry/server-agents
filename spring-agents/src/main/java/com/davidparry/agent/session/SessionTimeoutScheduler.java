package com.davidparry.agent.session;

import com.davidparry.agent.config.McpProxyProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled task that checks for and handles expired sessions and idle connections.
 */
@Component
public class SessionTimeoutScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SessionTimeoutScheduler.class);

    private final ConnectionManager connectionManager;
    private final McpProxyProperties properties;
    private final Counter sessionTimeoutCounter;
    private final Counter connectionTimeoutCounter;

    public SessionTimeoutScheduler(
            ConnectionManager connectionManager,
            McpProxyProperties properties,
            MeterRegistry meterRegistry) {
        this.connectionManager = connectionManager;
        this.properties = properties;

        this.sessionTimeoutCounter = Counter.builder("mcp.sessions.timed_out")
                .description("Number of sessions that timed out")
                .register(meterRegistry);

        this.connectionTimeoutCounter = Counter.builder("mcp.connections.timed_out")
                .description("Number of connections that timed out due to inactivity")
                .register(meterRegistry);
    }

    /**
     * Checks for expired sessions every 5 seconds.
     */
    @Scheduled(fixedRate = 5000)
    public void checkSessionTimeouts() {
        if (!properties.enabled()) {
            return;
        }

        Instant now = Instant.now();
        List<SessionTimeout> timeouts = new ArrayList<>();

        for (ClientConnection connection : connectionManager.getAllConnections()) {
            for (PromptSession session : connection.getAllSessions()) {
                if (!session.isTerminal() && session.isExpired()) {
                    timeouts.add(new SessionTimeout(connection, session));
                }
            }
        }

        // Process timeouts outside the iteration to avoid concurrent modification
        for (SessionTimeout timeout : timeouts) {
            handleSessionTimeout(timeout.connection(), timeout.session());
        }

        if (!timeouts.isEmpty()) {
            logger.info("Timed out {} sessions", timeouts.size());
        }
    }

    /**
     * Checks for idle connections every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void checkIdleConnections() {
        if (!properties.enabled()) {
            return;
        }

        long idleTimeoutMs = properties.connection().idleTimeoutSeconds() * 1000L;
        List<ClientConnection> idleConnections = new ArrayList<>();

        for (ClientConnection connection : connectionManager.getAllConnections()) {
            if (connection.isActive() && 
                !connection.hasActiveSessions() && 
                connection.getIdleTimeMs() > idleTimeoutMs) {
                idleConnections.add(connection);
            }
        }

        // Process idle connections
        for (ClientConnection connection : idleConnections) {
            handleIdleConnection(connection);
        }

        if (!idleConnections.isEmpty()) {
            logger.info("Closed {} idle connections", idleConnections.size());
        }
    }

    /**
     * Checks for expired tool calls every 2 seconds.
     */
    @Scheduled(fixedRate = 2000)
    public void checkToolCallTimeouts() {
        if (!properties.enabled()) {
            return;
        }

        for (ClientConnection connection : connectionManager.getAllConnections()) {
            for (PromptSession session : connection.getAllSessions()) {
                if (!session.isTerminal()) {
                    checkSessionToolCalls(session);
                }
            }
        }
    }

    private void handleSessionTimeout(ClientConnection connection, PromptSession session) {
        logger.warn("Session {} timed out on connection {} (client: {})",
                session.getSessionId(),
                connection.getConnectionId(),
                connection.getClientId());

        PromptSession timedOutSession = session.timeout();
        connection.updateSession(timedOutSession);
        sessionTimeoutCounter.increment();

        // Remove from connection
        connection.removeSession(timedOutSession.getSessionId());
        connectionManager.unregisterSession(timedOutSession.getSessionId());
    }

    private void handleIdleConnection(ClientConnection connection) {
        logger.info("Closing idle connection {} (client: {}, idle for {}ms)",
                connection.getConnectionId(),
                connection.getClientId(),
                connection.getIdleTimeMs());

        connection.disconnect("Idle timeout");
        connectionTimeoutCounter.increment();

        // Close WebSocket
        try {
            if (connection.isWebSocketOpen()) {
                connection.getWebSocketSession().close();
            }
        } catch (Exception e) {
            logger.warn("Error closing WebSocket for connection {}: {}",
                    connection.getConnectionId(), e.getMessage());
        }

        connectionManager.removeConnection(connection.getConnectionId());
    }

    private void checkSessionToolCalls(PromptSession session) {
        // Check each pending tool call for timeout
        session.getPendingCall("").ifPresent(call -> {
            // This is a placeholder - actual implementation would iterate all pending calls
        });
        
        // The actual timeout handling is done in RemoteToolCallback via CompletableFuture timeout
    }

    private record SessionTimeout(ClientConnection connection, PromptSession session) {}
}
