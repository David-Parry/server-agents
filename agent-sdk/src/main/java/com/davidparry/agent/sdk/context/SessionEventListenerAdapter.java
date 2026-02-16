package com.davidparry.agent.sdk.context;

import com.davidparry.agent.protocol.SessionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Abstract adapter class for {@link SessionEventListener} that provides
 * default implementations with debug logging.
 * 
 * Extend this class and override only the methods you need. The default
 * implementations log the event parameters at DEBUG level.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * SessionEventListener listener = new SessionEventListenerAdapter() {
 *     @Override
 *     public void onSessionCompleted(AgentSession session, SessionResult result) {
 *         // Custom handling for completion
 *         System.out.println("Session " + session.getSessionId() + " completed!");
 *     }
 *     
 *     @Override
 *     public void onChainCompleted(AgentSession session, ChainedSessionResult chainResult) {
 *         // Handle chain completion
 *         System.out.println("Chain completed with " + chainResult.getExecutedAgentCount() + " agents");
 *     }
 * };
 * }</pre>
 */
public abstract class SessionEventListenerAdapter implements SessionEventListener {

    private static final Logger logger = LoggerFactory.getLogger(SessionEventListenerAdapter.class);

    @Override
    public void onAgentActivated(AgentSession session) {
        logger.debug("Agent activated: sessionId={}, agentKey={}, chainPosition={}, state={}",
                     session.getSessionId(),
                     session.getAgentKey(),
                     session.getChainPosition(),
                     session.getState());
    }

    /**
     * Called when a session is started.
     * Default implementation logs the event at DEBUG level.
     * 
     * @param session the session that started
     */
    @Override
    public void onSessionStarted(AgentSession session) {
        logger.debug("Session started: sessionId={}, agentKey={}, chainPosition={}, state={}",
            session.getSessionId(), 
            session.getAgentKey(),
            session.getChainPosition(),
            session.getState());
    }

    /**
     * Called when a session completes successfully.
     * Default implementation logs the event at DEBUG level.
     * 
     * @param session the session that completed
     * @param result the session result
     */
    @Override
    public void onSessionCompleted(AgentSession session, SessionResult result) {
        logger.debug("Session completed: sessionId={}, agentKey={}, chainPosition={}, success={}, durationMs={}, toolCallsExecuted={}",
            session.getSessionId(), 
            session.getAgentKey(),
            session.getChainPosition(),
            result.isSuccess(),
            session.getDurationMs(),
            result.getToolCallsExecuted());
    }

    /**
     * Called when a session fails.
     * Default implementation logs the event at DEBUG level.
     * 
     * @param session the session that failed
     * @param error the error that caused the failure
     */
    @Override
    public void onSessionFailed(AgentSession session, Throwable error) {
        logger.debug("Session failed: sessionId={}, agentKey={}, chainPosition={}, errorType={}, errorMessage={}",
            session.getSessionId(), 
            session.getAgentKey(),
            session.getChainPosition(),
            error.getClass().getSimpleName(),
            error.getMessage());
    }

    /**
     * Called when a session is cancelled.
     * Default implementation logs the event at DEBUG level.
     * 
     * @param session the session that was cancelled
     * @param reason the cancellation reason
     */
    @Override
    public void onSessionCancelled(AgentSession session, String reason) {
        logger.debug("Session cancelled: sessionId={}, agentKey={}, chainPosition={}, reason={}",
            session.getSessionId(), 
            session.getAgentKey(),
            session.getChainPosition(),
            reason);
    }

    /**
     * Called when a tool is executed within a session.
     * Default implementation logs the event at DEBUG level.
     * 
     * @param session the session
     * @param toolName the tool that was executed
     * @param success whether the tool execution succeeded
     * @param durationMs the execution duration in milliseconds
     */
    @Override
    public void onToolExecuted(AgentSession session, String toolName, boolean success, long durationMs) {
        logger.debug("Tool executed: sessionId={}, agentKey={}, chainPosition={}, toolName={}, success={}, durationMs={}",
            session.getSessionId(), 
            session.getAgentKey(),
            session.getChainPosition(),
            toolName,
            success,
            durationMs);
    }

    // ==================== Chain-Specific Events ====================

    /**
     * Called when a chained agent starts execution (not the first agent).
     * Default implementation logs the event at DEBUG level.
     * 
     * @param session the new agent session
     * @param chainPosition position in chain (1 = second agent, 2 = third, etc.)
     * @param priorResults results from previous agents in chain
     */
    @Override
    public void onChainedAgentStarted(AgentSession session, int chainPosition, 
            List<SessionResult> priorResults) {
        logger.debug("Chained agent started: sessionId={}, agentKey={}, chainPosition={}, priorResultCount={}",
            session.getSessionId(),
            session.getAgentKey(),
            chainPosition,
            priorResults.size());
    }

    /**
     * Called when the entire chain completes successfully.
     * Default implementation logs the event at DEBUG level.
     * 
     * @param session the final session in the chain
     * @param chainResult the complete chain result with all agent results
     */
    @Override
    public void onChainCompleted(AgentSession session, ChainedSessionResult chainResult) {
        logger.debug("Chain completed: sessionId={}, status={}, executedAgentCount={}, successfulAgentCount={}, totalDurationMs={}",
            session.getSessionId(),
            chainResult.status(),
            chainResult.getExecutedAgentCount(),
            chainResult.getSuccessfulAgentCount(),
            chainResult.totalDurationMs());
    }

    /**
     * Called when chain fails mid-execution.
     * Default implementation logs the event at DEBUG level.
     * 
     * @param session the session that was active when failure occurred
     * @param chainResult partial results up to the failure point
     */
    @Override
    public void onChainFailed(AgentSession session, ChainedSessionResult chainResult) {
        logger.debug("Chain failed: sessionId={}, status={}, successfulAgentCount={}, failureCause={}",
            session.getSessionId(),
            chainResult.status(),
            chainResult.getSuccessfulAgentCount(),
            chainResult.failureCause() != null ? chainResult.failureCause().getMessage() : "unknown");
    }

    /**
     * Called when reconnection is being attempted during chain execution.
     * Default implementation logs the event at DEBUG level.
     * 
     * @param session the session waiting for reconnection
     * @param attemptNumber current reconnection attempt (1-based)
     * @param elapsedMs time elapsed since disconnection in milliseconds
     */
    @Override
    public void onChainReconnecting(AgentSession session, int attemptNumber, long elapsedMs) {
        logger.debug("Chain reconnecting: sessionId={}, agentKey={}, attemptNumber={}, elapsedMs={}",
            session.getSessionId(),
            session.getAgentKey(),
            attemptNumber,
            elapsedMs);
    }
}
