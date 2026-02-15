package com.davidparry.agent.session;

/**
 * Represents the state of a prompt session.
 */
public enum SessionState {
    /**
     * Session created, waiting to start execution
     */
    PENDING,

    /**
     * Session is actively executing the prompt
     */
    EXECUTING,

    /**
     * Session is waiting for a tool call response
     */
    WAITING_FOR_TOOL,

    /**
     * Session completed successfully
     */
    COMPLETED,

    /**
     * Session was cancelled by client or server
     */
    CANCELLED,

    /**
     * Session failed due to an error
     */
    FAILED,

    /**
     * Session timed out
     */
    TIMED_OUT
}
