package com.davidparry.agent.session;

/**
 * Represents the state of a WebSocket connection.
 */
public enum ConnectionState {
    /**
     * WebSocket handshake in progress
     */
    CONNECTING,

    /**
     * Connection active, can accept sessions
     */
    ACTIVE,

    /**
     * Graceful shutdown - finishing active sessions, not accepting new ones
     */
    DRAINING,

    /**
     * WebSocket disconnected
     */
    DISCONNECTED,

    /**
     * Unrecoverable error occurred
     */
    FAILED
}
