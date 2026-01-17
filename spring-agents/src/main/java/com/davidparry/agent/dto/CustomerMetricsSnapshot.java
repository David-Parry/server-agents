package com.davidparry.agent.dto;

import java.time.Instant;

/**
 * Snapshot of metrics for a single customer.
 * Used for the per-customer Prometheus endpoint.
 * 
 * This record captures both real-time metrics (from active connections)
 * and historical counters (persisted across connection lifecycles).
 */
public record CustomerMetricsSnapshot(
    // Customer identification
    String customerId,
    String customerName,
    boolean enabled,
    
    // Connection metrics
    int activeConnections,
    long totalConnectionsOpened,
    long totalConnectionsClosed,
    
    // Session metrics - current state
    int activeSessions,
    int pendingSessions,
    int executingSessions,
    int waitingForToolSessions,
    
    // Session metrics - historical counters
    long totalSessionsCreated,
    long totalSessionsCompleted,
    long totalSessionsFailed,
    long totalSessionsCancelled,
    long totalSessionsTimedOut,
    
    // Tool call metrics
    long totalToolCalls,
    long totalToolCallsFailed,
    int pendingToolCalls,
    
    // Timing metrics (in milliseconds)
    double avgSessionDurationMs,
    double maxSessionDurationMs,
    double avgToolCallDurationMs,
    
    // Token usage from database
    long totalTokensUsed,
    long totalTokensAllowed,
    
    // Circuit breaker summary for this customer's connections
    int circuitBreakersTotal,
    int circuitBreakersOpen,
    int circuitBreakersClosed,
    int circuitBreakersHalfOpen,
    
    // Snapshot timestamp
    Instant snapshotTime
) {
    /**
     * Creates a builder for CustomerMetricsSnapshot.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for CustomerMetricsSnapshot.
     */
    public static class Builder {
        private String customerId;
        private String customerName;
        private boolean enabled;
        private int activeConnections;
        private long totalConnectionsOpened;
        private long totalConnectionsClosed;
        private int activeSessions;
        private int pendingSessions;
        private int executingSessions;
        private int waitingForToolSessions;
        private long totalSessionsCreated;
        private long totalSessionsCompleted;
        private long totalSessionsFailed;
        private long totalSessionsCancelled;
        private long totalSessionsTimedOut;
        private long totalToolCalls;
        private long totalToolCallsFailed;
        private int pendingToolCalls;
        private double avgSessionDurationMs;
        private double maxSessionDurationMs;
        private double avgToolCallDurationMs;
        private long totalTokensUsed;
        private long totalTokensAllowed;
        private int circuitBreakersTotal;
        private int circuitBreakersOpen;
        private int circuitBreakersClosed;
        private int circuitBreakersHalfOpen;
        private Instant snapshotTime = Instant.now();
        
        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }
        
        public Builder customerName(String customerName) {
            this.customerName = customerName;
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder activeConnections(int activeConnections) {
            this.activeConnections = activeConnections;
            return this;
        }
        
        public Builder totalConnectionsOpened(long totalConnectionsOpened) {
            this.totalConnectionsOpened = totalConnectionsOpened;
            return this;
        }
        
        public Builder totalConnectionsClosed(long totalConnectionsClosed) {
            this.totalConnectionsClosed = totalConnectionsClosed;
            return this;
        }
        
        public Builder activeSessions(int activeSessions) {
            this.activeSessions = activeSessions;
            return this;
        }
        
        public Builder pendingSessions(int pendingSessions) {
            this.pendingSessions = pendingSessions;
            return this;
        }
        
        public Builder executingSessions(int executingSessions) {
            this.executingSessions = executingSessions;
            return this;
        }
        
        public Builder waitingForToolSessions(int waitingForToolSessions) {
            this.waitingForToolSessions = waitingForToolSessions;
            return this;
        }
        
        public Builder totalSessionsCreated(long totalSessionsCreated) {
            this.totalSessionsCreated = totalSessionsCreated;
            return this;
        }
        
        public Builder totalSessionsCompleted(long totalSessionsCompleted) {
            this.totalSessionsCompleted = totalSessionsCompleted;
            return this;
        }
        
        public Builder totalSessionsFailed(long totalSessionsFailed) {
            this.totalSessionsFailed = totalSessionsFailed;
            return this;
        }
        
        public Builder totalSessionsCancelled(long totalSessionsCancelled) {
            this.totalSessionsCancelled = totalSessionsCancelled;
            return this;
        }
        
        public Builder totalSessionsTimedOut(long totalSessionsTimedOut) {
            this.totalSessionsTimedOut = totalSessionsTimedOut;
            return this;
        }
        
        public Builder totalToolCalls(long totalToolCalls) {
            this.totalToolCalls = totalToolCalls;
            return this;
        }
        
        public Builder totalToolCallsFailed(long totalToolCallsFailed) {
            this.totalToolCallsFailed = totalToolCallsFailed;
            return this;
        }
        
        public Builder pendingToolCalls(int pendingToolCalls) {
            this.pendingToolCalls = pendingToolCalls;
            return this;
        }
        
        public Builder avgSessionDurationMs(double avgSessionDurationMs) {
            this.avgSessionDurationMs = avgSessionDurationMs;
            return this;
        }
        
        public Builder maxSessionDurationMs(double maxSessionDurationMs) {
            this.maxSessionDurationMs = maxSessionDurationMs;
            return this;
        }
        
        public Builder avgToolCallDurationMs(double avgToolCallDurationMs) {
            this.avgToolCallDurationMs = avgToolCallDurationMs;
            return this;
        }
        
        public Builder totalTokensUsed(long totalTokensUsed) {
            this.totalTokensUsed = totalTokensUsed;
            return this;
        }
        
        public Builder totalTokensAllowed(long totalTokensAllowed) {
            this.totalTokensAllowed = totalTokensAllowed;
            return this;
        }
        
        public Builder circuitBreakersTotal(int circuitBreakersTotal) {
            this.circuitBreakersTotal = circuitBreakersTotal;
            return this;
        }
        
        public Builder circuitBreakersOpen(int circuitBreakersOpen) {
            this.circuitBreakersOpen = circuitBreakersOpen;
            return this;
        }
        
        public Builder circuitBreakersClosed(int circuitBreakersClosed) {
            this.circuitBreakersClosed = circuitBreakersClosed;
            return this;
        }
        
        public Builder circuitBreakersHalfOpen(int circuitBreakersHalfOpen) {
            this.circuitBreakersHalfOpen = circuitBreakersHalfOpen;
            return this;
        }
        
        public Builder snapshotTime(Instant snapshotTime) {
            this.snapshotTime = snapshotTime;
            return this;
        }
        
        public CustomerMetricsSnapshot build() {
            return new CustomerMetricsSnapshot(
                customerId,
                customerName,
                enabled,
                activeConnections,
                totalConnectionsOpened,
                totalConnectionsClosed,
                activeSessions,
                pendingSessions,
                executingSessions,
                waitingForToolSessions,
                totalSessionsCreated,
                totalSessionsCompleted,
                totalSessionsFailed,
                totalSessionsCancelled,
                totalSessionsTimedOut,
                totalToolCalls,
                totalToolCallsFailed,
                pendingToolCalls,
                avgSessionDurationMs,
                maxSessionDurationMs,
                avgToolCallDurationMs,
                totalTokensUsed,
                totalTokensAllowed,
                circuitBreakersTotal,
                circuitBreakersOpen,
                circuitBreakersClosed,
                circuitBreakersHalfOpen,
                snapshotTime
            );
        }
    }
}
