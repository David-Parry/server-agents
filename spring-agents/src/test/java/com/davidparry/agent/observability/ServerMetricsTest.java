package com.davidparry.agent.observability;

import com.davidparry.agent.session.ConnectionManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServerMetrics.
 */
@ExtendWith(MockitoExtension.class)
class ServerMetricsTest {

    @Mock
    private ConnectionManager connectionManager;

    private MeterRegistry meterRegistry;
    private ServerMetrics serverMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        serverMetrics = new ServerMetrics(meterRegistry, connectionManager);
    }

    @Test
    void recordConnectionOpened_shouldIncrementCounter() {
        // When
        serverMetrics.recordConnectionOpened();
        serverMetrics.recordConnectionOpened();

        // Then
        double count = meterRegistry.get("mcp.connections.opened").counter().count();
        assertEquals(2.0, count);
    }

    @Test
    void recordConnectionClosed_shouldIncrementCounter() {
        // When
        serverMetrics.recordConnectionClosed();

        // Then
        double count = meterRegistry.get("mcp.connections.closed").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void recordSessionStarted_shouldIncrementCounter() {
        // When
        serverMetrics.recordSessionStarted();
        serverMetrics.recordSessionStarted();
        serverMetrics.recordSessionStarted();

        // Then
        double count = meterRegistry.get("mcp.sessions.started").counter().count();
        assertEquals(3.0, count);
    }

    @Test
    void recordSessionCompleted_shouldIncrementCounter() {
        // When
        serverMetrics.recordSessionCompleted();

        // Then
        double count = meterRegistry.get("mcp.sessions.completed").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void recordSessionFailed_shouldIncrementCounter() {
        // When
        serverMetrics.recordSessionFailed();

        // Then
        double count = meterRegistry.get("mcp.sessions.failed").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void recordSessionCancelled_shouldIncrementCounter() {
        // When
        serverMetrics.recordSessionCancelled();

        // Then
        double count = meterRegistry.get("mcp.sessions.cancelled").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void recordSessionTimedOut_shouldIncrementCounter() {
        // When
        serverMetrics.recordSessionTimedOut();

        // Then
        double count = meterRegistry.get("mcp.sessions.timedout").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void recordToolCallExecuted_shouldIncrementCounter() {
        // When
        serverMetrics.recordToolCallExecuted();
        serverMetrics.recordToolCallExecuted();

        // Then
        double count = meterRegistry.get("mcp.tool_calls.executed").counter().count();
        assertEquals(2.0, count);
    }

    @Test
    void recordToolCallFailed_shouldIncrementCounter() {
        // When
        serverMetrics.recordToolCallFailed();

        // Then
        double count = meterRegistry.get("mcp.tool_calls.failed").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void pendingToolCalls_shouldTrackCorrectly() {
        // When
        serverMetrics.incrementPendingToolCalls();
        serverMetrics.incrementPendingToolCalls();
        serverMetrics.decrementPendingToolCalls();

        // Then
        double count = meterRegistry.get("mcp.tool_calls.pending").gauge().value();
        assertEquals(1.0, count);
    }

    @Test
    void sessionTimer_shouldRecordDuration() {
        // When
        Timer.Sample sample = serverMetrics.startSessionTimer();
        // Simulate some work
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        serverMetrics.stopSessionTimer(sample);

        // Then
        Timer timer = meterRegistry.get("mcp.sessions.duration").timer();
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 10);
    }

    @Test
    void toolCallTimer_shouldRecordDuration() {
        // When
        Timer.Sample sample = serverMetrics.startToolCallTimer();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        serverMetrics.stopToolCallTimer(sample);

        // Then
        Timer timer = meterRegistry.get("mcp.tool_calls.duration.all").timer();
        assertEquals(1, timer.count());
    }

    @Test
    void recordSessionOutcome_shouldRecordCounterAndHistogram() {
        // When
        serverMetrics.recordSessionOutcome("completed", 5000);
        serverMetrics.recordSessionOutcome("failed", 2000);
        serverMetrics.recordSessionOutcome("completed", 3000);

        // Then
        double completedCount = meterRegistry.get("spring_agents_sessions_total")
                .tag("status", "completed")
                .counter().count();
        double failedCount = meterRegistry.get("spring_agents_sessions_total")
                .tag("status", "failed")
                .counter().count();

        assertEquals(2.0, completedCount);
        assertEquals(1.0, failedCount);
    }

    @Test
    void recordSessionDuration_shouldRecordInHistogram() {
        // When
        serverMetrics.recordSessionDuration(5.5);
        serverMetrics.recordSessionDuration(10.2);

        // Then
        double count = meterRegistry.get("spring_agents_session_duration_seconds")
                .summary().count();
        assertEquals(2, count);
    }

    @Test
    void recordToolCallOutcome_shouldRecordCounterAndHistogram() {
        // When
        serverMetrics.recordToolCallOutcome("terminal-list_files", "success", 500);
        serverMetrics.recordToolCallOutcome("terminal-list_files", "failed", 100);
        serverMetrics.recordToolCallOutcome("editor-read_file", "success", 200);

        // Then
        double terminalSuccessCount = meterRegistry.get("spring_agents_tool_calls_total")
                .tag("tool_name", "terminal-list_files")
                .tag("status", "success")
                .counter().count();
        double terminalFailedCount = meterRegistry.get("spring_agents_tool_calls_total")
                .tag("tool_name", "terminal-list_files")
                .tag("status", "failed")
                .counter().count();

        assertEquals(1.0, terminalSuccessCount);
        assertEquals(1.0, terminalFailedCount);
    }

    @Test
    void recordToolCallDuration_shouldRecordInHistogram() {
        // When
        serverMetrics.recordToolCallDuration(0.5);
        serverMetrics.recordToolCallDuration(1.2);

        // Then
        double count = meterRegistry.get("spring_agents_tool_call_duration_seconds")
                .summary().count();
        assertEquals(2, count);
    }

    @Test
    void getToolCallTimer_shouldCreateTimerForTool() {
        // When
        Timer timer1 = serverMetrics.getToolCallTimer("terminal-list_files");
        Timer timer2 = serverMetrics.getToolCallTimer("terminal-list_files");
        Timer timer3 = serverMetrics.getToolCallTimer("editor-read_file");

        // Then
        assertSame(timer1, timer2); // Same tool should return same timer
        assertNotSame(timer1, timer3); // Different tools should have different timers
    }

    @Test
    void getSummary_shouldReturnCorrectMetrics() {
        // Given
        when(connectionManager.getConnectionCount()).thenReturn(5);
        when(connectionManager.getTotalSessionCount()).thenReturn(10);

        serverMetrics.recordConnectionOpened();
        serverMetrics.recordConnectionOpened();
        serverMetrics.recordConnectionClosed();
        serverMetrics.recordSessionStarted();
        serverMetrics.recordSessionCompleted();
        serverMetrics.recordSessionFailed();
        serverMetrics.recordSessionCancelled();
        serverMetrics.recordToolCallExecuted();
        serverMetrics.recordToolCallFailed();
        serverMetrics.incrementPendingToolCalls();

        // When
        ServerMetrics.MetricsSummary summary = serverMetrics.getSummary();

        // Then
        assertEquals(5, summary.activeConnections());
        assertEquals(10, summary.activeSessions());
        assertEquals(1, summary.pendingToolCalls());
        assertEquals(2.0, summary.totalConnectionsOpened());
        assertEquals(1.0, summary.totalConnectionsClosed());
        assertEquals(1.0, summary.totalSessionsStarted());
        assertEquals(1.0, summary.totalSessionsCompleted());
        assertEquals(1.0, summary.totalSessionsFailed());
        assertEquals(1.0, summary.totalSessionsCancelled());
        assertEquals(1.0, summary.totalToolCallsExecuted());
        assertEquals(1.0, summary.totalToolCallsFailed());
    }
}
