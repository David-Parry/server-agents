package com.davidparry.agent.session;

import com.davidparry.agent.protocol.StreamChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromptSessionTest {

    private ClientConnection connection;
    private PromptSession session;

    @BeforeEach
    void setUp() {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.getId()).thenReturn("ws-123");

        connection = ClientConnection.builder()
                .connectionId("conn-123")
                .clientId("test-client-001")
                .webSocketSession(wsSession)
                .maxConcurrentSessions(10)
                .build();

        session = PromptSession.builder()
                .sessionId("sess-123")
                .connection(connection)
                .prompt("Test prompt")
                .systemPrompt("System prompt")
                .model("claude-3")
                .streamingEnabled(false)
                .tools(new ArrayList<>())
                .maxDurationSeconds(600)
                .build();
    }

    @Test
    void builder_createsSession() {
        assertNotNull(session);
        assertEquals("sess-123", session.getSessionId());
        assertEquals("Test prompt", session.getPrompt());
        assertEquals("System prompt", session.getSystemPrompt());
        assertEquals("claude-3", session.getModel());
        assertFalse(session.isStreamingEnabled());
        assertEquals(SessionState.PENDING, session.getState());
    }

    @Test
    void startExecution_transitionsState() {
        PromptSession executing = session.startExecution();
        
        // Original session unchanged (immutable)
        assertEquals(SessionState.PENDING, session.getState());
        // New session has updated state
        assertEquals(SessionState.EXECUTING, executing.getState());
    }

    @Test
    void waitingForTool_transitionsState() {
        PromptSession executing = session.startExecution();
        PromptSession waiting = executing.waitingForTool();
        
        assertEquals(SessionState.EXECUTING, executing.getState());
        assertEquals(SessionState.WAITING_FOR_TOOL, waiting.getState());
    }

    @Test
    void resumeExecution_transitionsState() {
        PromptSession executing = session.startExecution();
        PromptSession waiting = executing.waitingForTool();
        PromptSession resumed = waiting.resumeExecution();
        
        assertEquals(SessionState.WAITING_FOR_TOOL, waiting.getState());
        assertEquals(SessionState.EXECUTING, resumed.getState());
    }

    @Test
    void complete_transitionsToCompleted() {
        PromptSession executing = session.startExecution();
        PromptSession completed = executing.complete();

        assertEquals(SessionState.COMPLETED, completed.getState());
        assertNotNull(completed.getCompletedAt());
        assertTrue(completed.isTerminal());
        
        // Original unchanged
        assertEquals(SessionState.EXECUTING, executing.getState());
    }

    @Test
    void fail_transitionsToFailed() {
        PromptSession executing = session.startExecution();
        PromptSession failed = executing.fail("Error message");

        assertEquals(SessionState.FAILED, failed.getState());
        assertEquals("Error message", failed.getErrorMessage());
        assertNotNull(failed.getCompletedAt());
        assertTrue(failed.isTerminal());
    }

    @Test
    void cancel_transitionsToCancelled() {
        PromptSession executing = session.startExecution();
        PromptSession cancelled = executing.cancel("User cancelled");

        assertEquals(SessionState.CANCELLED, cancelled.getState());
        assertEquals("User cancelled", cancelled.getErrorMessage());
        assertNotNull(cancelled.getCompletedAt());
        assertTrue(cancelled.isTerminal());
    }

    @Test
    void timeout_transitionsToTimedOut() {
        PromptSession executing = session.startExecution();
        PromptSession timedOut = executing.timeout();

        assertEquals(SessionState.TIMED_OUT, timedOut.getState());
        assertEquals("Session timed out", timedOut.getErrorMessage());
        assertNotNull(timedOut.getCompletedAt());
        assertTrue(timedOut.isTerminal());
    }

    @Test
    void isExpired_returnsFalseForNewSession() {
        assertFalse(session.isExpired());
    }

    @Test
    void addPendingCall_addsToPendingCallsAndReturnsNewSession() {
        PendingToolCall pendingCall = new PendingToolCall(
                "req-123",
                "test_tool",
                "{}",
                Instant.now().plusSeconds(60)
        );

        PromptSession updated = session.addPendingCall(pendingCall);

        // Both sessions share the same pendingCalls map (by design for async coordination)
        assertTrue(session.hasPendingCalls());
        assertTrue(updated.hasPendingCalls());
        assertEquals(1, updated.getPendingCallCount());
        
        // Tool call count is incremented in the new session
        assertEquals(1, updated.getToolCallCount());
        assertEquals(0, session.getToolCallCount()); // Original has old count
    }

    @Test
    void getPendingCall_returnsPendingCall() {
        PendingToolCall pendingCall = new PendingToolCall(
                "req-123",
                "test_tool",
                "{}",
                Instant.now().plusSeconds(60)
        );

        session.addPendingCall(pendingCall);

        var retrieved = session.getPendingCall("req-123");
        assertTrue(retrieved.isPresent());
        assertEquals("test_tool", retrieved.get().toolName());
    }

    @Test
    void removePendingCall_removesPendingCall() {
        PendingToolCall pendingCall = new PendingToolCall(
                "req-123",
                "test_tool",
                "{}",
                Instant.now().plusSeconds(60)
        );

        session.addPendingCall(pendingCall);
        session.removePendingCall("req-123");

        assertFalse(session.hasPendingCalls());
        assertEquals(0, session.getPendingCallCount());
    }

    @Test
    void withNextStreamSequence_incrementsSequence() {
        assertEquals(0, session.streamSequence());
        
        PromptSession s1 = session.withNextStreamSequence();
        assertEquals(1, s1.streamSequence());
        
        PromptSession s2 = s1.withNextStreamSequence();
        assertEquals(2, s2.streamSequence());
        
        // Original unchanged
        assertEquals(0, session.streamSequence());
    }

    @Test
    void getDurationMs_returnsPositiveValue() {
        assertTrue(session.getDurationMs() >= 0);
    }

    @Test
    void builder_throwsOnMissingSessionId() {
        assertThrows(IllegalArgumentException.class, () ->
                PromptSession.builder()
                        .connection(connection)
                        .prompt("Test")
                        .build()
        );
    }

    @Test
    void builder_throwsOnMissingConnection() {
        assertThrows(IllegalArgumentException.class, () ->
                PromptSession.builder()
                        .sessionId("sess-123")
                        .prompt("Test")
                        .build()
        );
    }

    @Test
    void builder_throwsOnMissingPrompt() {
        assertThrows(IllegalArgumentException.class, () ->
                PromptSession.builder()
                        .sessionId("sess-123")
                        .connection(connection)
                        .build()
        );
    }

    @Test
    void withStreamChunkConsumer_setsConsumer() {
        var consumer = new java.util.concurrent.atomic.AtomicReference<StreamChunk>();
        
        PromptSession withConsumer = session.withStreamChunkConsumer(consumer::set);
        
        assertNotNull(withConsumer.streamChunkConsumer());
        assertNull(session.streamChunkConsumer()); // Original unchanged
    }

    @Test
    void stateTransitions_areIdempotentOnTerminalState() {
        PromptSession completed = session.startExecution().complete();
        
        // Trying to transition from terminal state returns same instance
        assertSame(completed, completed.fail("Should not change"));
        assertSame(completed, completed.cancel("Should not change"));
        assertSame(completed, completed.timeout());
        
        assertEquals(SessionState.COMPLETED, completed.getState());
    }

    @Test
    void recordAccessors_workCorrectly() {
        // Test that record accessor methods work
        assertEquals("sess-123", session.sessionId());
        assertEquals("Test prompt", session.prompt());
        assertEquals("System prompt", session.systemPrompt());
        assertEquals("claude-3", session.model());
        assertFalse(session.streamingEnabled());
        assertEquals(SessionState.PENDING, session.state());
        assertNotNull(session.createdAt());
        assertNotNull(session.deadline());
    }

    @Test
    void compatibilityGetters_workCorrectly() {
        // Test that compatibility getter methods work
        assertEquals("sess-123", session.getSessionId());
        assertEquals("Test prompt", session.getPrompt());
        assertEquals("System prompt", session.getSystemPrompt());
        assertEquals("claude-3", session.getModel());
        assertFalse(session.isStreamingEnabled());
        assertEquals(SessionState.PENDING, session.getState());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getDeadline());
    }
}
