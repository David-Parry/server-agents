package com.davidparry.agent.sdk.websocket;

/**
 * Represents the state of a WebSocket connection.
 */
public enum ConnectionState {
    DISCONNECTED,    // Not connected
    CONNECTING,      // Connection in progress
    CONNECTED,       // Connected and ready
    RECONNECTING,    // Attempting to reconnect
    DRAINING,        // Graceful shutdown in progress
    FAILED           // Unrecoverable error
}
