package com.davidparry.agent.pojo;

import com.davidparry.agent.protocol.SessionResult;
import com.davidparry.agent.session.PromptSession;
/**
 * Result of prompt execution containing both the session result and the updated session.
 * Since PromptSession is immutable, state changes produce new instances.
 */
public record ExecutionResult(SessionResult result, PromptSession updatedSession) {
}
