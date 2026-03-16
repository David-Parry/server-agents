package com.davidparry.agent.sdk.context;

import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.SessionResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Represents an active agent session with its metadata and state.
 * 
 * This class tracks the lifecycle of an agent session from creation through
 * completion or failure. It maintains references to the agent configuration,
 * prompt parameters, and timing information.
 * 
 * For chained agent executions, this class also tracks the position within
 * the chain and results from prior agents.
 * 
 * Thread-safe for concurrent access to state.
 */
public class AgentSession {


    private final String sessionId;
    private final String agentKey;
    private final Agent agent;
    private final JsonNode promptParams;
    private final long startTimeMs;
    private final SessionEventListener listener;
    
    // Chain tracking
    private final int chainPosition;
    private final List<SessionResult> priorChainResults;
    
    private volatile State state = State.PENDING;
    private volatile SessionResult result;
    private volatile Throwable error;
    private volatile long endTimeMs;
    private volatile String cancellationReason;

    /**
     * Creates a new AgentSession (standalone or first in chain).
     * 
     * @param sessionId unique identifier for this session
     * @param agentKey the key used to look up the agent
     * @param agent the agent configuration
     * @param promptParams parameters to substitute in the prompt
     * @param startTimeMs the start time in milliseconds since epoch
     */
    public AgentSession(
            String sessionId, 
            String agentKey, 
            Agent agent, 
            JsonNode promptParams,
            long startTimeMs) {
        this(sessionId, agentKey, agent, promptParams, startTimeMs, null, 0, List.of());
    }

    /**
     * Creates a new AgentSession with a listener (standalone or first in chain).
     * 
     * @param sessionId unique identifier for this session
     * @param agentKey the key used to look up the agent
     * @param agent the agent configuration
     * @param promptParams parameters to substitute in the prompt
     * @param startTimeMs the start time in milliseconds since epoch
     * @param listener optional listener for session events
     */
    public AgentSession(
            String sessionId, 
            String agentKey, 
            Agent agent, 
            JsonNode promptParams,
            long startTimeMs,
            SessionEventListener listener) {
        this(sessionId, agentKey, agent, promptParams, startTimeMs, listener, 0, List.of());
    }

    /**
     * Creates a new AgentSession with full chain context.
     * 
     * @param sessionId unique identifier for this session
     * @param agentKey the key used to look up the agent
     * @param agent the agent configuration
     * @param promptParams parameters to substitute in the prompt
     * @param startTimeMs the start time in milliseconds since epoch
     * @param listener optional listener for session events
     * @param chainPosition position in chain (0 = first agent)
     * @param priorChainResults results from previous agents in this chain
     */
    public AgentSession(
            String sessionId, 
            String agentKey, 
            Agent agent, 
            JsonNode promptParams,
            long startTimeMs,
            SessionEventListener listener,
            int chainPosition,
            List<SessionResult> priorChainResults) {
        this.sessionId = sessionId;
        this.agentKey = agentKey;
        this.agent = agent;
        this.promptParams = promptParams;
        this.startTimeMs = startTimeMs;
        this.listener = listener;
        this.chainPosition = chainPosition;
        this.priorChainResults = priorChainResults != null ? List.copyOf(priorChainResults) : List.of();
    }

    // ==================== Getters ====================

    /**
     * Gets the unique session identifier.
     * 
     * @return the session ID
     */
    public String getSessionId() { 
        return sessionId; 
    }

    /**
     * Gets the agent key used to look up this agent.
     * 
     * @return the agent key
     */
    public String getAgentKey() { 
        return agentKey; 
    }

    /**
     * Gets the agent configuration.
     * 
     * @return the agent
     */
    public Agent getAgent() { 
        return agent; 
    }

    /**
     * Gets the prompt parameters used for this session.
     * 
     * @return the prompt parameters as JsonNode
     */
    public JsonNode getPromptParams() { 
        return promptParams; 
    }

    /**
     * Gets the session start time.
     * 
     * @return start time in milliseconds since epoch
     */
    public long getStartTimeMs() { 
        return startTimeMs; 
    }

    /**
     * Gets the session event listener.
     * 
     * @return the listener, or null if none was provided
     */
    public SessionEventListener getListener() {
        return listener;
    }

    /**
     * Checks if this session has a listener.
     * 
     * @return true if a listener is registered
     */
    public boolean hasListener() {
        return listener != null;
    }

    /**
     * Gets the current session state.
     * 
     * @return the current state
     */
    public State getState() {
        return state; 
    }

    /**
     * Gets the session result if completed successfully.
     * 
     * @return the result, or null if not completed
     */
    public SessionResult getResult() { 
        return result; 
    }

    /**
     * Gets the error if the session failed.
     * 
     * @return the error, or null if not failed
     */
    public Throwable getError() { 
        return error; 
    }

    /**
     * Gets the session end time.
     * 
     * @return end time in milliseconds since epoch, or 0 if not ended
     */
    public long getEndTimeMs() { 
        return endTimeMs; 
    }

    /**
     * Gets the cancellation reason if the session was cancelled.
     * 
     * @return the cancellation reason, or null if not cancelled
     */
    public String getCancellationReason() {
        return cancellationReason;
    }

    /**
     * Gets the duration of the session.
     * 
     * @return duration in milliseconds (current duration if still running)
     */
    public long getDurationMs() {
        if (endTimeMs > 0) {
            return endTimeMs - startTimeMs;
        }
        return System.currentTimeMillis() - startTimeMs;
    }

    // ==================== Chain Tracking ====================

    /**
     * Gets the position of this agent in the chain (0-indexed).
     * 
     * @return chain position (0 = first agent)
     */
    public int getChainPosition() {
        return chainPosition;
    }

    /**
     * Gets results from previous agents in this chain.
     * 
     * @return immutable list of prior results (empty for first agent)
     */
    public List<SessionResult> getPriorChainResults() {
        return priorChainResults;
    }

    /**
     * Checks if this is the first agent in a chain (or standalone).
     * 
     * @return true if this is the first agent
     */
    public boolean isFirstInChain() {
        return chainPosition == 0;
    }

    /**
     * Checks if this agent is part of a chain (has prior results or position > 0).
     * 
     * @return true if this is a chained agent
     */
    public boolean isChained() {
        return chainPosition > 0 || !priorChainResults.isEmpty();
    }

    /**
     * Gets the number of agents that executed before this one in the chain.
     * 
     * @return count of prior agents
     */
    public int getPriorAgentCount() {
        return priorChainResults.size();
    }

    // ==================== State Queries ====================

    /**
     * Checks if the session is still active (pending or running).
     * 
     * @return true if the session is active
     */
    public boolean isActive() {
        return state == State.PENDING || state == State.RUNNING;
    }

    /**
     * Checks if the session completed successfully.
     * 
     * @return true if completed
     */
    public boolean isCompleted() {
        return state == State.COMPLETED;
    }

    /**
     * Checks if the session failed.
     * 
     * @return true if failed
     */
    public boolean isFailed() {
        return state == State.FAILED;
    }

    /**
     * Checks if the session was cancelled.
     * 
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return state == State.CANCELLED;
    }

    // ==================== State Transitions ====================

    /**
     * Marks the session as running.
     * Package-private for use by AgentApplicationContext.
     */
    void markRunning() {
        this.state = State.RUNNING;
    }

    /**
     * Marks the session as completed with a result.
     * Package-private for use by AgentApplicationContext.
     * 
     * @param result the session result
     */
    void markCompleted(SessionResult result) {
        this.state = State.COMPLETED;
        this.result = result;
        this.endTimeMs = System.currentTimeMillis();
    }

    /**
     * Marks the session as failed with an error.
     * Package-private for use by AgentApplicationContext.
     * 
     * @param error the error that caused the failure
     */
    void markFailed(Throwable error) {
        this.state = State.FAILED;
        this.error = error;
        this.endTimeMs = System.currentTimeMillis();
    }

    /**
     * Marks the session as cancelled.
     * Package-private for use by AgentApplicationContext.
     * 
     * @param reason the cancellation reason
     */
    void markCancelled(String reason) {
        this.state = State.CANCELLED;
        this.cancellationReason = reason;
        this.endTimeMs = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "AgentSession{" +
                "sessionId='" + sessionId + '\'' +
                ", agentKey='" + agentKey + '\'' +
                ", state=" + state +
                ", chainPosition=" + chainPosition +
                ", durationMs=" + getDurationMs() +
                '}';
    }
}
