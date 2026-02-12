package com.davidparry.agent.client.context;

import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.SessionResult;

import java.util.List;
import java.util.Optional;

/**
 * Represents the complete result of a chained agent execution.
 * 
 * Contains all intermediate results from each agent in the chain,
 * along with the overall chain status and any failure information.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * ActivationResult activation = context.activateAgent("first_agent", params, listener);
 * ChainedSessionResult chainResult = activation.resultFuture().join();
 * 
 * if (chainResult.isFullySuccessful()) {
 *     // All agents completed successfully
 *     chainResult.getSuccessfulResults().forEach(result -> {
 *         System.out.println("Agent result: " + result.getContent());
 *     });
 * } else if (chainResult.hasPartialResults()) {
 *     // Some agents succeeded before failure
 *     System.out.println("Partial success: " + chainResult.getSuccessfulAgentCount() + " agents completed");
 * }
 * }</pre>
 */
public record ChainedSessionResult(
    String sessionId,
    List<AgentExecutionSnapshot> executionHistory,
    ChainStatus status,
    Throwable failureCause,
    long totalDurationMs
) {
    
    /**
     * Status of the chain execution.
     */
    public enum ChainStatus {
        /** All agents in chain completed successfully */
        COMPLETED,
        /** Some agents completed, then a failure occurred */
        PARTIAL_FAILURE,
        /** Chain failed before any agent completed (e.g., first agent failed) */
        FAILED
    }
    
    /**
     * Snapshot of a single agent's execution within the chain.
     * Immutable record capturing the state at completion time.
     * 
     * @param agentKey the key/name of the agent
     * @param agent the agent configuration
     * @param result the session result (null if agent never executed)
     * @param finalState the final state of the agent session
     * @param durationMs execution duration in milliseconds
     * @param chainPosition 0-indexed position in chain
     */
    public record AgentExecutionSnapshot(
        String agentKey,
        Agent agent,
        SessionResult result,
        State finalState,
        long durationMs,
        int chainPosition
    ) {
        /**
         * Checks if this agent execution was successful.
         * 
         * @return true if the agent completed with a successful result
         */
        public boolean isSuccess() {
            return result != null && result.isSuccess();
        }
        
        /**
         * Checks if this agent actually executed (has a result).
         * 
         * @return true if the agent produced a result
         */
        public boolean hasResult() {
            return result != null;
        }
    }
    
    /**
     * Gets the final result in the chain (last successful or attempted).
     * 
     * @return Optional containing the last result, or empty if no results
     */
    public Optional<SessionResult> getFinalResult() {
        if (executionHistory == null || executionHistory.isEmpty()) {
            return Optional.empty();
        }
        AgentExecutionSnapshot last = executionHistory.get(executionHistory.size() - 1);
        return Optional.ofNullable(last.result());
    }
    
    /**
     * Gets all successful results in execution order.
     * 
     * @return list of successful session results
     */
    public List<SessionResult> getSuccessfulResults() {
        if (executionHistory == null) {
            return List.of();
        }
        return executionHistory.stream()
            .filter(AgentExecutionSnapshot::isSuccess)
            .map(AgentExecutionSnapshot::result)
            .toList();
    }
    
    /**
     * Gets all results (successful or not) in execution order.
     * 
     * @return list of all session results (may contain nulls for failed agents)
     */
    public List<SessionResult> getAllResults() {
        if (executionHistory == null) {
            return List.of();
        }
        return executionHistory.stream()
            .map(AgentExecutionSnapshot::result)
            .toList();
    }
    
    /**
     * Gets the number of agents that executed (successfully or not).
     * 
     * @return count of executed agents
     */
    public int getExecutedAgentCount() {
        return executionHistory != null ? executionHistory.size() : 0;
    }
    
    /**
     * Gets the number of agents that completed successfully.
     * 
     * @return count of successful agents
     */
    public int getSuccessfulAgentCount() {
        if (executionHistory == null) {
            return 0;
        }
        return (int) executionHistory.stream()
            .filter(AgentExecutionSnapshot::isSuccess)
            .count();
    }
    
    /**
     * Checks if the entire chain completed successfully.
     * 
     * @return true if all agents in the chain succeeded
     */
    public boolean isFullySuccessful() {
        return status == ChainStatus.COMPLETED;
    }
    
    /**
     * Checks if any agents completed successfully before failure.
     * 
     * @return true if there are partial results available
     */
    public boolean hasPartialResults() {
        return status == ChainStatus.PARTIAL_FAILURE && getSuccessfulAgentCount() > 0;
    }
    
    /**
     * Checks if the chain failed completely (no successful agents).
     * 
     * @return true if the chain failed with no successful results
     */
    public boolean isCompleteFailure() {
        return status == ChainStatus.FAILED || 
               (status == ChainStatus.PARTIAL_FAILURE && getSuccessfulAgentCount() == 0);
    }
    
    /**
     * Gets the agent keys in execution order.
     * 
     * @return list of agent keys
     */
    public List<String> getAgentKeys() {
        if (executionHistory == null) {
            return List.of();
        }
        return executionHistory.stream()
            .map(AgentExecutionSnapshot::agentKey)
            .toList();
    }
    
    /**
     * Gets a specific agent's execution snapshot by position.
     * 
     * @param position the chain position (0-indexed)
     * @return Optional containing the snapshot, or empty if position is invalid
     */
    public Optional<AgentExecutionSnapshot> getSnapshotAtPosition(int position) {
        if (executionHistory == null || position < 0 || position >= executionHistory.size()) {
            return Optional.empty();
        }
        return Optional.of(executionHistory.get(position));
    }
    
    /**
     * Gets a specific agent's execution snapshot by agent key.
     * 
     * @param agentKey the agent key to find
     * @return Optional containing the snapshot, or empty if not found
     */
    public Optional<AgentExecutionSnapshot> getSnapshotByAgentKey(String agentKey) {
        if (executionHistory == null || agentKey == null) {
            return Optional.empty();
        }
        return executionHistory.stream()
            .filter(s -> agentKey.equals(s.agentKey()))
            .findFirst();
    }
}
