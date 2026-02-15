package com.davidparry.agent.sdk.context;

import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.SessionResult;

import java.time.Instant;
import java.util.List;

/**
 * Context needed to resume a chain after reconnection.
 * 
 * Stores the state of a chain that was interrupted by a WebSocket
 * disconnection, allowing it to be resumed once reconnected.
 * 
 * This is an in-memory only structure - chain state is not persisted
 * across application restarts.
 * 
 * @param nextAgent the next agent to execute in the chain
 * @param sessionId the session ID for the entire chain
 * @param lastResult the result from the last successfully executed agent
 * @param nextChainPosition the position of the next agent (0-indexed)
 * @param completedSnapshots snapshots of all completed agent executions
 * @param listener the session event listener for notifications
 * @param disconnectedAt timestamp when the disconnection occurred
 */
public record ChainResumeContext(
    Agent nextAgent,
    String sessionId,
    SessionResult lastResult,
    int nextChainPosition,
    List<ChainedSessionResult.AgentExecutionSnapshot> completedSnapshots,
    SessionEventListener listener,
    Instant disconnectedAt
) {
    /**
     * Calculates how long the chain has been waiting for reconnection.
     * 
     * @return duration in milliseconds since disconnection
     */
    public long getWaitingDurationMs() {
        return Instant.now().toEpochMilli() - disconnectedAt.toEpochMilli();
    }
    
    /**
     * Calculates how long the chain has been waiting for reconnection.
     * 
     * @return duration in seconds since disconnection
     */
    public long getWaitingDurationSeconds() {
        return getWaitingDurationMs() / 1000;
    }
    
    /**
     * Gets the number of agents that completed before disconnection.
     * 
     * @return count of completed agents
     */
    public int getCompletedAgentCount() {
        return completedSnapshots != null ? completedSnapshots.size() : 0;
    }
    
    /**
     * Gets the key of the next agent to execute.
     * 
     * @return the agent key/name
     */
    public String getNextAgentKey() {
        return nextAgent != null ? nextAgent.name() : null;
    }
}
