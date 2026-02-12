package com.davidparry.agent.protocol.dto;

/**
 * Status values for the nextAgent field in SessionResult.
 * 
 * These constants indicate the outcome of agent chain processing:
 * <ul>
 *   <li>{@link #END_CHAIN} - The LLM succeeded and the Agent's nextAgent was empty/null,
 *       indicating the agent chain has completed successfully.</li>
 *   <li>{@link #FAILED_AGENT} - The LLM processing failed, indicating the agent
 *       did not complete successfully.</li>
 * </ul>
 * 
 * When the server receives a successful LLM response and the Agent's nextAgent
 * property is empty or null, the SessionResult.nextAgent should be set to
 * {@link #END_CHAIN}. If the processing fails, it should be set to {@link #FAILED_AGENT}.
 * 
 * Enums are inherently thread-safe.
 */
public enum NextAgentStatus {
    
    /**
     * Indicates successful completion of the agent chain.
     * Used when the LLM succeeds and the Agent's nextAgent property is empty or null.
     */
    END_CHAIN,
    
    /**
     * Indicates the agent processing failed.
     * Used when the LLM processing was not successful.
     */
    FAILED_AGENT;
    
    /**
     * Returns the string value of this status.
     * This is useful for setting the nextAgent field in SessionResult.
     * 
     * @return the name of this enum constant
     */
    public String getValue() {
        return this.name();
    }
}
