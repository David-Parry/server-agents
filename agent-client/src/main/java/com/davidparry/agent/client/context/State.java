package com.davidparry.agent.client.context;

/**
 * Possible states for an agent session.
 */
public enum State {
    /**
     * Session has been created but not yet started
     */
    PENDING,
    /**
     * Session is actively running
     */
    RUNNING,
    /**
     * Session completed successfully
     */
    COMPLETED,
    /**
     * Session failed with an error
     */
    FAILED,
    /**
     * Session was cancelled
     */
    CANCELLED
}
