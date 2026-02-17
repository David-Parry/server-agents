package com.davidparry.agent.lifecycle;

import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Handles graceful shutdown of the MCP Proxy server.
 * Ensures active sessions complete before the server stops.
 */
@Component
public class GracefulShutdownHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GracefulShutdownHandler.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final int POLL_INTERVAL_MS = 500;

    private final ConnectionManager connectionManager;
    private final McpProxyProperties properties;

    public GracefulShutdownHandler(ConnectionManager connectionManager, McpProxyProperties properties) {
        this.connectionManager = connectionManager;
        this.properties = properties;
    }

    @EventListener
    public void onApplicationShutdown(ContextClosedEvent event) {
        if (!properties.enabled()) {
            return;
        }

        LOGGER.info("Initiating graceful shutdown of MCP Proxy...");

        int connectionCount = connectionManager.getConnectionCount();
        int sessionCount = connectionManager.getTotalSessionCount();

        if (connectionCount == 0) {
            LOGGER.info("No active connections, shutdown complete");
            return;
        }

        LOGGER.info("Draining {} connections with {} active sessions", connectionCount, sessionCount);

        // Initiate draining on all connections
        connectionManager.initiateGracefulShutdown();

        // Wait for sessions to complete
        long startTime = System.currentTimeMillis();
        long timeoutMs = TimeUnit.SECONDS.toMillis(SHUTDOWN_TIMEOUT_SECONDS);

        while (connectionManager.getTotalSessionCount() > 0) {
            long elapsed = System.currentTimeMillis() - startTime;

            if (elapsed > timeoutMs) {
                LOGGER.warn("Shutdown timeout reached, forcing disconnection of remaining sessions");
                break;
            }

            int remaining = connectionManager.getTotalSessionCount();
            LOGGER.debug("Waiting for {} sessions to complete...", remaining);

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Shutdown interrupted");
                break;
            }
        }

        // Force disconnect any remaining connections
        int remainingConnections = connectionManager.getConnectionCount();
        if (remainingConnections > 0) {
            LOGGER.info("Force disconnecting {} remaining connections", remainingConnections);
            connectionManager.disconnectAll("Server shutdown");
        }

        // Close WebSocket sessions
        for (ClientConnection connection : connectionManager.getAllConnections()) {
            try {
                if (connection.isWebSocketOpen()) {
                    connection.getWebSocketSession().close();
                }
            } catch (Exception e) {
                LOGGER.warn("Error closing WebSocket for connection {}: {}",
                        connection.getConnectionId(), e.getMessage());
            }
        }

        LOGGER.info("Graceful shutdown complete");
    }
}
