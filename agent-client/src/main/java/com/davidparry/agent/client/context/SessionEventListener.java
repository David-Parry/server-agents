package com.davidparry.agent.client.context;

import com.davidparry.agent.protocol.SessionResult;

import java.util.List;

/**
 * Listener interface for session lifecycle events.
 * 
 * Implementations can register with {@link AgentApplicationContext} to receive
 * notifications about session state changes. All methods have default empty
 * implementations, allowing listeners to override only the events they care about.
 * 
 * <h2>Event Flow for Single Agent:</h2>
 * <ol>
 *   <li>{@link #onSessionStarted(AgentSession)} - Agent begins execution</li>
 *   <li>{@link #onToolExecuted(AgentSession, String, boolean, long)} - Zero or more tool calls</li>
 *   <li>{@link #onSessionCompleted(AgentSession, SessionResult)} or {@link #onSessionFailed(AgentSession, Throwable)}</li>
 * </ol>
 * 
 * <h2>Event Flow for Chained Agents:</h2>
 * <ol>
 *   <li>{@link #onSessionStarted(AgentSession)} - First agent begins</li>
 *   <li>{@link #onAgentActivated(AgentSession)} - First agent activated</li>
 *   <li>{@link #onToolExecuted(AgentSession, String, boolean, long)} - Tool calls for first agent</li>
 *   <li>{@link #onChainedAgentStarted(AgentSession, int, List)} - Second agent begins</li>
 *   <li>... repeat for each agent in chain ...</li>
 *   <li>{@link #onChainCompleted(AgentSession, ChainedSessionResult)} or {@link #onChainFailed(AgentSession, ChainedSessionResult)}</li>
 * </ol>
 */
public interface SessionEventListener {

    /**
     * Called when a session is started (first agent in chain or standalone).
     * 
     * @param session the session that started
     */
    void onSessionStarted(AgentSession session);

    /**
     * Called when a session completes successfully.
     * For chained agents, this is called for the final agent only.
     * 
     * @param session the session that completed
     * @param result the session result
     */
    void onSessionCompleted(AgentSession session, SessionResult result);

    /**
     * Called when a session fails.
     * For chained agents, this is called when any agent in the chain fails.
     * 
     * @param session the session that failed
     * @param error the error that caused the failure
     */
    void onSessionFailed(AgentSession session, Throwable error);

    /**
     * Called when a session is cancelled.
     * 
     * @param session the session that was cancelled
     * @param reason the cancellation reason
     */
    void onSessionCancelled(AgentSession session, String reason);

    /**
     * Called when an agent is activated (started running).
     * 
     * @param session the session for the activated agent
     */
    void onAgentActivated(AgentSession session);

    /**
     * Called when a tool is executed within a session.
     * 
     * @param session the session
     * @param toolName the tool that was executed
     * @param success whether the tool execution succeeded
     * @param durationMs the execution duration in milliseconds
     */
    void onToolExecuted(AgentSession session, String toolName, boolean success, long durationMs);

    // ==================== Chain-Specific Events ====================

    /**
     * Called when a chained agent starts execution (not the first agent).
     * 
     * @param session the new agent session
     * @param chainPosition position in chain (1 = second agent, 2 = third, etc.)
     * @param priorResults results from previous agents in chain
     */
    default void onChainedAgentStarted(AgentSession session, int chainPosition, 
            List<SessionResult> priorResults) {}

    /**
     * Called when the entire chain completes successfully (all agents finished).
     * 
     * @param session the final session in the chain
     * @param chainResult the complete chain result with all agent results
     */
    default void onChainCompleted(AgentSession session, ChainedSessionResult chainResult) {}

    /**
     * Called when chain fails mid-execution.
     * This could be due to:
     * <ul>
     *   <li>An agent returning a failure result</li>
     *   <li>WebSocket disconnection with failed reconnection</li>
     *   <li>Timeout during reconnection</li>
     * </ul>
     * 
     * @param session the session that was active when failure occurred
     * @param chainResult partial results up to the failure point
     */
    default void onChainFailed(AgentSession session, ChainedSessionResult chainResult) {}

    /**
     * Called when reconnection is being attempted during chain execution.
     * This is called periodically while waiting for reconnection.
     * 
     * @param session the session waiting for reconnection
     * @param attemptNumber current reconnection attempt (1-based)
     * @param elapsedMs time elapsed since disconnection in milliseconds
     */
    default void onChainReconnecting(AgentSession session, int attemptNumber, long elapsedMs) {}
}
