package com.davidparry.agent.observability;

import com.davidparry.agent.session.ConnectionManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized metrics collection for the MCP Proxy server.
 * Provides counters, gauges, timers, and histograms for monitoring server health and performance.
 * 
 * <h2>Metric Categories</h2>
 * <ul>
 *   <li><b>Counters:</b> Cumulative counts (connections, sessions, tool calls)</li>
 *   <li><b>Gauges:</b> Current values (pending calls, active connections)</li>
 *   <li><b>Timers:</b> Duration measurements with histograms</li>
 *   <li><b>Distribution Summaries:</b> Value distributions with percentiles</li>
 * </ul>
 */
@Component
public class ServerMetrics {

    private static final Logger logger = LoggerFactory.getLogger(ServerMetrics.class);

    private final MeterRegistry meterRegistry;
    private final ConnectionManager connectionManager;

    // Counters
    private final Counter connectionsOpened;
    private final Counter connectionsClosed;
    private final Counter sessionsStarted;
    private final Counter sessionsCompleted;
    private final Counter sessionsFailed;
    private final Counter sessionsCancelled;
    private final Counter sessionsTimedOut;
    private final Counter toolCallsExecuted;
    private final Counter toolCallsFailed;

    // Atomic values for gauges
    private final AtomicLong pendingToolCalls = new AtomicLong(0);

    // Timers
    private final Timer sessionDuration;
    private final Timer toolCallDuration;

    // Per-tool timers (created dynamically)
    private final Map<String, Timer> toolCallTimersByTool = new ConcurrentHashMap<>();

    // Distribution summaries for histograms
    private final DistributionSummary sessionDurationHistogram;
    private final DistributionSummary toolCallDurationHistogram;

    public ServerMetrics(MeterRegistry meterRegistry, ConnectionManager connectionManager) {
        this.meterRegistry = meterRegistry;
        this.connectionManager = connectionManager;

        // Initialize counters
        this.connectionsOpened = Counter.builder("mcp.connections.opened")
                .description("Total WebSocket connections opened")
                .register(meterRegistry);

        this.connectionsClosed = Counter.builder("mcp.connections.closed")
                .description("Total WebSocket connections closed")
                .register(meterRegistry);

        this.sessionsStarted = Counter.builder("mcp.sessions.started")
                .description("Total prompt sessions started")
                .register(meterRegistry);

        this.sessionsCompleted = Counter.builder("mcp.sessions.completed")
                .description("Total prompt sessions completed successfully")
                .register(meterRegistry);

        this.sessionsFailed = Counter.builder("mcp.sessions.failed")
                .description("Total prompt sessions that failed")
                .register(meterRegistry);

        this.sessionsCancelled = Counter.builder("mcp.sessions.cancelled")
                .description("Total prompt sessions cancelled")
                .register(meterRegistry);

        this.sessionsTimedOut = Counter.builder("mcp.sessions.timedout")
                .description("Total prompt sessions that timed out")
                .register(meterRegistry);

        this.toolCallsExecuted = Counter.builder("mcp.tool_calls.executed")
                .description("Total tool calls executed")
                .register(meterRegistry);

        this.toolCallsFailed = Counter.builder("mcp.tool_calls.failed")
                .description("Total tool calls that failed")
                .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("mcp.tool_calls.pending", pendingToolCalls, AtomicLong::get)
                .description("Number of pending tool calls")
                .register(meterRegistry);

        // Initialize timers with percentile histograms
        this.sessionDuration = Timer.builder("mcp.sessions.duration")
                .description("Session duration histogram")
                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.toolCallDuration = Timer.builder("mcp.tool_calls.duration.all")
                .description("Tool call duration histogram")
                .publishPercentiles(0.5, 0.9, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        // Initialize distribution summaries for additional histogram views
        this.sessionDurationHistogram = DistributionSummary.builder("spring_agents_session_duration_seconds")
                .description("Session duration distribution in seconds")
                .baseUnit("seconds")
                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                .publishPercentileHistogram()
                .serviceLevelObjectives(1, 5, 10, 30, 60, 120, 300, 600)
                .register(meterRegistry);

        this.toolCallDurationHistogram = DistributionSummary.builder("spring_agents_tool_call_duration_seconds")
                .description("Tool call duration distribution in seconds")
                .baseUnit("seconds")
                .publishPercentiles(0.5, 0.9, 0.99)
                .publishPercentileHistogram()
                .serviceLevelObjectives(0.1, 0.5, 1, 5, 10, 30, 60)
                .register(meterRegistry);

        // Register session outcome counters with status tags
        registerSessionOutcomeCounters();

        logger.info("ServerMetrics initialized with histograms and per-tool timers");
    }

    /**
     * Registers session outcome counters with status tags for Prometheus.
     */
    private void registerSessionOutcomeCounters() {
        // These are registered as separate counters with tags for better Prometheus querying
        Counter.builder("spring_agents_sessions_total")
                .tag("status", "completed")
                .description("Total sessions by outcome")
                .register(meterRegistry);

        Counter.builder("spring_agents_sessions_total")
                .tag("status", "failed")
                .description("Total sessions by outcome")
                .register(meterRegistry);

        Counter.builder("spring_agents_sessions_total")
                .tag("status", "cancelled")
                .description("Total sessions by outcome")
                .register(meterRegistry);

        Counter.builder("spring_agents_sessions_total")
                .tag("status", "timedout")
                .description("Total sessions by outcome")
                .register(meterRegistry);
    }

    /**
     * Gets or creates a timer for a specific tool.
     * 
     * @param toolName the tool name
     * @return the timer for this tool
     */
    public Timer getToolCallTimer(String toolName) {
        return toolCallTimersByTool.computeIfAbsent(toolName, name ->
                Timer.builder("spring_agents_tool_calls_duration_seconds")
                        .tag("tool_name", name)
                        .description("Tool call duration for " + name)
                        .publishPercentiles(0.5, 0.9, 0.99)
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        );
    }

    // ==================== Connection Metrics ====================

    public void recordConnectionOpened() {
        connectionsOpened.increment();
    }

    public void recordConnectionClosed() {
        connectionsClosed.increment();
    }

    // ==================== Session Metrics ====================

    public void recordSessionStarted() {
        sessionsStarted.increment();
    }

    public void recordSessionCompleted() {
        sessionsCompleted.increment();
    }

    public void recordSessionFailed() {
        sessionsFailed.increment();
    }

    public void recordSessionCancelled() {
        sessionsCancelled.increment();
    }

    public void recordSessionTimedOut() {
        sessionsTimedOut.increment();
    }

    public Timer.Sample startSessionTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopSessionTimer(Timer.Sample sample) {
        sample.stop(sessionDuration);
    }

    /**
     * Records a session outcome with duration for histogram tracking.
     * 
     * @param status the session outcome status (completed, failed, cancelled, timedout)
     * @param durationMs the session duration in milliseconds
     */
    public void recordSessionOutcome(String status, long durationMs) {
        // Increment the tagged counter
        Counter.builder("spring_agents_sessions_total")
                .tag("status", status)
                .description("Total sessions by outcome")
                .register(meterRegistry)
                .increment();

        // Record in the histogram (convert to seconds)
        double durationSeconds = durationMs / 1000.0;
        sessionDurationHistogram.record(durationSeconds);

        logger.trace("Recorded session outcome: status={}, duration={}ms", status, durationMs);
    }

    /**
     * Records session duration in the histogram.
     * 
     * @param durationSeconds the duration in seconds
     */
    public void recordSessionDuration(double durationSeconds) {
        sessionDurationHistogram.record(durationSeconds);
    }

    // ==================== Tool Call Metrics ====================

    public void recordToolCallExecuted() {
        toolCallsExecuted.increment();
    }

    public void recordToolCallFailed() {
        toolCallsFailed.increment();
    }

    public void incrementPendingToolCalls() {
        pendingToolCalls.incrementAndGet();
    }

    public void decrementPendingToolCalls() {
        pendingToolCalls.decrementAndGet();
    }

    public Timer.Sample startToolCallTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopToolCallTimer(Timer.Sample sample) {
        sample.stop(toolCallDuration);
    }

    /**
     * Records a tool call outcome with duration for histogram tracking.
     * 
     * @param toolName the name of the tool
     * @param status the outcome status (success, failed)
     * @param durationMs the tool call duration in milliseconds
     */
    public void recordToolCallOutcome(String toolName, String status, long durationMs) {
        // Increment the tagged counter
        Counter.builder("spring_agents_tool_calls_total")
                .tag("tool_name", toolName)
                .tag("status", status)
                .description("Total tool calls by tool and outcome")
                .register(meterRegistry)
                .increment();

        // Record in the per-tool timer
        getToolCallTimer(toolName).record(durationMs, TimeUnit.MILLISECONDS);

        // Record in the global histogram (convert to seconds)
        double durationSeconds = durationMs / 1000.0;
        toolCallDurationHistogram.record(durationSeconds);

        logger.trace("Recorded tool call outcome: tool={}, status={}, duration={}ms", 
                toolName, status, durationMs);
    }

    /**
     * Records tool call duration in the histogram.
     * 
     * @param durationSeconds the duration in seconds
     */
    public void recordToolCallDuration(double durationSeconds) {
        toolCallDurationHistogram.record(durationSeconds);
    }

    // ==================== Summary Metrics ====================

    /**
     * Gets a summary of current server metrics.
     *
     * @return metrics summary
     */
    public MetricsSummary getSummary() {
        return new MetricsSummary(
                connectionManager.getConnectionCount(),
                connectionManager.getTotalSessionCount(),
                pendingToolCalls.get(),
                connectionsOpened.count(),
                connectionsClosed.count(),
                sessionsStarted.count(),
                sessionsCompleted.count(),
                sessionsFailed.count(),
                sessionsCancelled.count(),
                toolCallsExecuted.count(),
                toolCallsFailed.count()
        );
    }

    /**
     * Summary of server metrics.
     */
    public record MetricsSummary(
            int activeConnections,
            int activeSessions,
            long pendingToolCalls,
            double totalConnectionsOpened,
            double totalConnectionsClosed,
            double totalSessionsStarted,
            double totalSessionsCompleted,
            double totalSessionsFailed,
            double totalSessionsCancelled,
            double totalToolCallsExecuted,
            double totalToolCallsFailed
    ) {}
}
