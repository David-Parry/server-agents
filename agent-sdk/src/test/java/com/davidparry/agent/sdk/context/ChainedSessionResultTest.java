package com.davidparry.agent.sdk.context;

import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.SessionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChainedSessionResult.
 */
class ChainedSessionResultTest {

    private Agent mockAgent1;
    private Agent mockAgent2;
    private Agent mockAgent3;
    private SessionResult successResult1;
    private SessionResult successResult2;
    private SessionResult failedResult;

    @BeforeEach
    void setUp() {
        mockAgent1 = mock(Agent.class);
        when(mockAgent1.name()).thenReturn("agent1");
        
        mockAgent2 = mock(Agent.class);
        when(mockAgent2.name()).thenReturn("agent2");
        
        mockAgent3 = mock(Agent.class);
        when(mockAgent3.name()).thenReturn("agent3");
        
        successResult1 = mock(SessionResult.class);
        when(successResult1.isSuccess()).thenReturn(true);
        
        successResult2 = mock(SessionResult.class);
        when(successResult2.isSuccess()).thenReturn(true);
        
        failedResult = mock(SessionResult.class);
        when(failedResult.isSuccess()).thenReturn(false);
    }

    @Test
    void emptyChain_hasCorrectDefaults() {
        ChainedSessionResult result = new ChainedSessionResult(
            "session-123",
            List.of(),
            ChainedSessionResult.ChainStatus.FAILED,
            new RuntimeException("test"),
            0
        );
        
        assertEquals("session-123", result.sessionId());
        assertTrue(result.executionHistory().isEmpty());
        assertEquals(ChainedSessionResult.ChainStatus.FAILED, result.status());
        assertNotNull(result.failureCause());
        assertEquals(0, result.totalDurationMs());
        
        assertTrue(result.getFinalResult().isEmpty());
        assertTrue(result.getSuccessfulResults().isEmpty());
        assertTrue(result.getAllResults().isEmpty());
        assertEquals(0, result.getExecutedAgentCount());
        assertEquals(0, result.getSuccessfulAgentCount());
        assertFalse(result.isFullySuccessful());
        assertFalse(result.hasPartialResults());
        assertTrue(result.isCompleteFailure());
        assertTrue(result.getAgentKeys().isEmpty());
    }

    @Test
    void singleSuccessfulAgent_hasCorrectState() {
        ChainedSessionResult.AgentExecutionSnapshot snapshot = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent1",
                mockAgent1,
                successResult1,
                State.COMPLETED,
                1000,
                0
            );
        
        ChainedSessionResult result = new ChainedSessionResult(
            "session-123",
            List.of(snapshot),
            ChainedSessionResult.ChainStatus.COMPLETED,
            null,
            1000
        );
        
        assertTrue(result.isFullySuccessful());
        assertFalse(result.hasPartialResults());
        assertFalse(result.isCompleteFailure());
        assertEquals(1, result.getExecutedAgentCount());
        assertEquals(1, result.getSuccessfulAgentCount());
        
        assertTrue(result.getFinalResult().isPresent());
        assertEquals(successResult1, result.getFinalResult().get());
        
        assertEquals(List.of(successResult1), result.getSuccessfulResults());
        assertEquals(List.of("agent1"), result.getAgentKeys());
    }

    @Test
    void multipleSuccessfulAgents_hasCorrectState() {
        ChainedSessionResult.AgentExecutionSnapshot snapshot1 = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent1", mockAgent1, successResult1, State.COMPLETED, 1000, 0
            );
        ChainedSessionResult.AgentExecutionSnapshot snapshot2 = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent2", mockAgent2, successResult2, State.COMPLETED, 2000, 1
            );
        
        ChainedSessionResult result = new ChainedSessionResult(
            "session-123",
            List.of(snapshot1, snapshot2),
            ChainedSessionResult.ChainStatus.COMPLETED,
            null,
            3000
        );
        
        assertTrue(result.isFullySuccessful());
        assertEquals(2, result.getExecutedAgentCount());
        assertEquals(2, result.getSuccessfulAgentCount());
        
        // Final result should be the last one
        assertEquals(successResult2, result.getFinalResult().get());
        
        assertEquals(List.of(successResult1, successResult2), result.getSuccessfulResults());
        assertEquals(List.of("agent1", "agent2"), result.getAgentKeys());
    }

    @Test
    void partialFailure_hasCorrectState() {
        ChainedSessionResult.AgentExecutionSnapshot snapshot1 = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent1", mockAgent1, successResult1, State.COMPLETED, 1000, 0
            );
        ChainedSessionResult.AgentExecutionSnapshot snapshot2 = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent2", mockAgent2, failedResult, State.FAILED, 500, 1
            );
        
        ChainedSessionResult result = new ChainedSessionResult(
            "session-123",
            List.of(snapshot1, snapshot2),
            ChainedSessionResult.ChainStatus.PARTIAL_FAILURE,
            new RuntimeException("Agent 2 failed"),
            1500
        );
        
        assertFalse(result.isFullySuccessful());
        assertTrue(result.hasPartialResults());
        assertFalse(result.isCompleteFailure());
        assertEquals(2, result.getExecutedAgentCount());
        assertEquals(1, result.getSuccessfulAgentCount());
        
        // Final result is the failed one
        assertEquals(failedResult, result.getFinalResult().get());
        
        // Only successful results
        assertEquals(List.of(successResult1), result.getSuccessfulResults());
    }

    @Test
    void completeFailure_hasCorrectState() {
        ChainedSessionResult.AgentExecutionSnapshot snapshot = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent1", mockAgent1, failedResult, State.FAILED, 500, 0
            );
        
        ChainedSessionResult result = new ChainedSessionResult(
            "session-123",
            List.of(snapshot),
            ChainedSessionResult.ChainStatus.FAILED,
            new RuntimeException("First agent failed"),
            500
        );
        
        assertFalse(result.isFullySuccessful());
        assertFalse(result.hasPartialResults());
        assertTrue(result.isCompleteFailure());
        assertEquals(1, result.getExecutedAgentCount());
        assertEquals(0, result.getSuccessfulAgentCount());
        
        assertTrue(result.getSuccessfulResults().isEmpty());
    }

    @Test
    void getSnapshotAtPosition_returnsCorrectSnapshot() {
        ChainedSessionResult.AgentExecutionSnapshot snapshot1 = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent1", mockAgent1, successResult1, State.COMPLETED, 1000, 0
            );
        ChainedSessionResult.AgentExecutionSnapshot snapshot2 = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent2", mockAgent2, successResult2, State.COMPLETED, 2000, 1
            );
        
        ChainedSessionResult result = new ChainedSessionResult(
            "session-123",
            List.of(snapshot1, snapshot2),
            ChainedSessionResult.ChainStatus.COMPLETED,
            null,
            3000
        );
        
        Optional<ChainedSessionResult.AgentExecutionSnapshot> found0 = result.getSnapshotAtPosition(0);
        assertTrue(found0.isPresent());
        assertEquals("agent1", found0.get().agentKey());
        
        Optional<ChainedSessionResult.AgentExecutionSnapshot> found1 = result.getSnapshotAtPosition(1);
        assertTrue(found1.isPresent());
        assertEquals("agent2", found1.get().agentKey());
        
        // Invalid positions
        assertTrue(result.getSnapshotAtPosition(-1).isEmpty());
        assertTrue(result.getSnapshotAtPosition(2).isEmpty());
        assertTrue(result.getSnapshotAtPosition(100).isEmpty());
    }

    @Test
    void getSnapshotByAgentKey_returnsCorrectSnapshot() {
        ChainedSessionResult.AgentExecutionSnapshot snapshot1 = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent1", mockAgent1, successResult1, State.COMPLETED, 1000, 0
            );
        ChainedSessionResult.AgentExecutionSnapshot snapshot2 = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent2", mockAgent2, successResult2, State.COMPLETED, 2000, 1
            );
        
        ChainedSessionResult result = new ChainedSessionResult(
            "session-123",
            List.of(snapshot1, snapshot2),
            ChainedSessionResult.ChainStatus.COMPLETED,
            null,
            3000
        );
        
        Optional<ChainedSessionResult.AgentExecutionSnapshot> found1 = result.getSnapshotByAgentKey("agent1");
        assertTrue(found1.isPresent());
        assertEquals(0, found1.get().chainPosition());
        
        Optional<ChainedSessionResult.AgentExecutionSnapshot> found2 = result.getSnapshotByAgentKey("agent2");
        assertTrue(found2.isPresent());
        assertEquals(1, found2.get().chainPosition());
        
        // Non-existent agent
        assertTrue(result.getSnapshotByAgentKey("nonexistent").isEmpty());
        assertTrue(result.getSnapshotByAgentKey(null).isEmpty());
    }

    @Test
    void agentExecutionSnapshot_isSuccess() {
        ChainedSessionResult.AgentExecutionSnapshot successSnapshot = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent1", mockAgent1, successResult1, State.COMPLETED, 1000, 0
            );
        assertTrue(successSnapshot.isSuccess());
        assertTrue(successSnapshot.hasResult());
        
        ChainedSessionResult.AgentExecutionSnapshot failedSnapshot = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent2", mockAgent2, failedResult, State.FAILED, 500, 1
            );
        assertFalse(failedSnapshot.isSuccess());
        assertTrue(failedSnapshot.hasResult());
        
        ChainedSessionResult.AgentExecutionSnapshot noResultSnapshot = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "agent3", mockAgent3, null, State.FAILED, 0, 2
            );
        assertFalse(noResultSnapshot.isSuccess());
        assertFalse(noResultSnapshot.hasResult());
    }

    @Test
    void nullHistory_handledGracefully() {
        ChainedSessionResult result = new ChainedSessionResult(
            "session-123",
            null,
            ChainedSessionResult.ChainStatus.FAILED,
            new RuntimeException("test"),
            0
        );
        
        assertTrue(result.getFinalResult().isEmpty());
        assertTrue(result.getSuccessfulResults().isEmpty());
        assertTrue(result.getAllResults().isEmpty());
        assertEquals(0, result.getExecutedAgentCount());
        assertEquals(0, result.getSuccessfulAgentCount());
        assertTrue(result.getAgentKeys().isEmpty());
        assertTrue(result.getSnapshotAtPosition(0).isEmpty());
        assertTrue(result.getSnapshotByAgentKey("agent1").isEmpty());
    }
}
