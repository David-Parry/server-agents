package com.davidparry.agent.sdk.context;

import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.SessionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for
 */
class AgentSessionTest {

    private ObjectMapper objectMapper;
    private Agent mockAgent;
    private JsonNode promptParams;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockAgent = mock(Agent.class);
        when(mockAgent.name()).thenReturn("test_agent");
        when(mockAgent.description()).thenReturn("Test agent description");
        promptParams = objectMapper.valueToTree(Map.of("key", "value"));
    }

    @Test
    void constructor_initializesCorrectly() {
        long startTime = System.currentTimeMillis();
        
        AgentSession session = new AgentSession(
            "session-123",
            "test_agent",
            mockAgent,
            promptParams,
            startTime
        );

        assertEquals("session-123", session.getSessionId());
        assertEquals("test_agent", session.getAgentKey());
        assertEquals(mockAgent, session.getAgent());
        assertEquals(promptParams, session.getPromptParams());
        assertEquals(startTime, session.getStartTimeMs());
        assertEquals(State.PENDING, session.getState());
        assertNull(session.getResult());
        assertNull(session.getError());
        assertEquals(0, session.getEndTimeMs());
        
        // Chain tracking defaults
        assertEquals(0, session.getChainPosition());
        assertTrue(session.getPriorChainResults().isEmpty());
        assertTrue(session.isFirstInChain());
        assertFalse(session.isChained());
    }

    @Test
    void constructor_withChainContext_initializesCorrectly() {
        long startTime = System.currentTimeMillis();
        SessionResult priorResult1 = mock(SessionResult.class);
        SessionResult priorResult2 = mock(SessionResult.class);
        List<SessionResult> priorResults = List.of(priorResult1, priorResult2);
        
        AgentSession session = new AgentSession(
            "session-123",
            "test_agent",
            mockAgent,
            promptParams,
            startTime,
            null,  // listener
            2,     // chainPosition
            priorResults
        );

        assertEquals("session-123", session.getSessionId());
        assertEquals("test_agent", session.getAgentKey());
        assertEquals(2, session.getChainPosition());
        assertEquals(2, session.getPriorAgentCount());
        assertFalse(session.isFirstInChain());
        assertTrue(session.isChained());
        assertEquals(priorResults, session.getPriorChainResults());
    }

    @Test
    void isActive_returnsTrueForPendingAndRunning() {
        AgentSession session = createSession();

        assertTrue(session.isActive());
        assertEquals(State.PENDING, session.getState());

        session.markRunning();
        assertTrue(session.isActive());
        assertEquals(State.RUNNING, session.getState());
    }

    @Test
    void isActive_returnsFalseForCompletedFailedCancelled() {
        AgentSession session = createSession();
        SessionResult mockResult = mock(SessionResult.class);

        session.markCompleted(mockResult);
        assertFalse(session.isActive());

        session = createSession();
        session.markFailed(new RuntimeException("test error"));
        assertFalse(session.isActive());

        session = createSession();
        session.markCancelled("test reason");
        assertFalse(session.isActive());
    }

    @Test
    void markCompleted_setsStateAndResult() {
        AgentSession session = createSession();
        SessionResult mockResult = mock(SessionResult.class);

        session.markCompleted(mockResult);

        assertEquals(State.COMPLETED, session.getState());
        assertEquals(mockResult, session.getResult());
        assertTrue(session.getEndTimeMs() > 0);
        assertTrue(session.isCompleted());
        assertFalse(session.isFailed());
        assertFalse(session.isCancelled());
    }

    @Test
    void markFailed_setsStateAndError() {
        AgentSession session = createSession();
        RuntimeException error = new RuntimeException("test error");

        session.markFailed(error);

        assertEquals(State.FAILED, session.getState());
        assertEquals(error, session.getError());
        assertTrue(session.getEndTimeMs() > 0);
        assertFalse(session.isCompleted());
        assertTrue(session.isFailed());
        assertFalse(session.isCancelled());
    }

    @Test
    void markCancelled_setsStateAndReason() {
        AgentSession session = createSession();

        session.markCancelled("user requested");

        assertEquals(State.CANCELLED, session.getState());
        assertEquals("user requested", session.getCancellationReason());
        assertTrue(session.getEndTimeMs() > 0);
        assertFalse(session.isCompleted());
        assertFalse(session.isFailed());
        assertTrue(session.isCancelled());
    }

    @Test
    void getDurationMs_returnsCorrectDuration() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        AgentSession session = new AgentSession(
            "session-123",
            "test_agent",
            mockAgent,
            promptParams,
            startTime
        );

        // While running, duration should be current time - start time
        Thread.sleep(50);
        long runningDuration = session.getDurationMs();
        assertTrue(runningDuration >= 50);

        // After completion, duration should be fixed
        SessionResult mockResult = mock(SessionResult.class);
        session.markCompleted(mockResult);
        long completedDuration = session.getDurationMs();
        
        Thread.sleep(50);
        assertEquals(completedDuration, session.getDurationMs());
    }

    @Test
    void toString_containsRelevantInfo() {
        AgentSession session = createSession();
        String str = session.toString();

        assertTrue(str.contains("session-123"));
        assertTrue(str.contains("test_agent"));
        assertTrue(str.contains("PENDING"));
        assertTrue(str.contains("chainPosition=0"));
    }

    @Test
    void toString_containsChainPosition() {
        SessionResult priorResult = mock(SessionResult.class);
        AgentSession session = new AgentSession(
            "session-123",
            "test_agent",
            mockAgent,
            promptParams,
            System.currentTimeMillis(),
            null,
            3,
            List.of(priorResult)
        );
        
        String str = session.toString();
        assertTrue(str.contains("chainPosition=3"));
    }

    @Test
    void priorChainResults_isImmutable() {
        SessionResult priorResult = mock(SessionResult.class);
        List<SessionResult> priorResults = List.of(priorResult);
        
        AgentSession session = new AgentSession(
            "session-123",
            "test_agent",
            mockAgent,
            promptParams,
            System.currentTimeMillis(),
            null,
            1,
            priorResults
        );
        
        // The returned list should be immutable
        List<SessionResult> returnedResults = session.getPriorChainResults();
        assertThrows(UnsupportedOperationException.class, () -> {
            returnedResults.add(mock(SessionResult.class));
        });
    }

    @Test
    void chainPosition_zeroMeansFirstInChain() {
        AgentSession session = new AgentSession(
            "session-123",
            "test_agent",
            mockAgent,
            promptParams,
            System.currentTimeMillis(),
            null,
            0,
            List.of()
        );
        
        assertTrue(session.isFirstInChain());
        assertFalse(session.isChained());
        assertEquals(0, session.getPriorAgentCount());
    }

    @Test
    void chainPosition_nonZeroMeansChained() {
        SessionResult priorResult = mock(SessionResult.class);
        AgentSession session = new AgentSession(
            "session-123",
            "test_agent",
            mockAgent,
            promptParams,
            System.currentTimeMillis(),
            null,
            1,
            List.of(priorResult)
        );
        
        assertFalse(session.isFirstInChain());
        assertTrue(session.isChained());
        assertEquals(1, session.getPriorAgentCount());
    }

    @Test
    void hasListener_returnsTrueWhenListenerProvided() {
        SessionEventListener listener = mock(SessionEventListener.class);
        AgentSession session = new AgentSession(
            "session-123",
            "test_agent",
            mockAgent,
            promptParams,
            System.currentTimeMillis(),
            listener
        );
        
        assertTrue(session.hasListener());
        assertEquals(listener, session.getListener());
    }

    @Test
    void hasListener_returnsFalseWhenNoListener() {
        AgentSession session = createSession();
        
        assertFalse(session.hasListener());
        assertNull(session.getListener());
    }

    private AgentSession createSession() {
        return new AgentSession(
            "session-123",
            "test_agent",
            mockAgent,
            promptParams,
            System.currentTimeMillis()
        );
    }
}
