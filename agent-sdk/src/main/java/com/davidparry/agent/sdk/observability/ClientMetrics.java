package com.davidparry.agent.sdk.observability;

import io.micrometer.core.instrument.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics collection for the Agent Client.
 */
public class ClientMetrics {
    
    private final MeterRegistry registry;
    
    // Gauges
    private final AtomicInteger connectionStatus = new AtomicInteger(0);
    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final AtomicInteger pendingToolCalls = new AtomicInteger(0);
    private final AtomicInteger runningServers = new AtomicInteger(0);
    private final AtomicInteger registeredTools = new AtomicInteger(0);
    
    public ClientMetrics(MeterRegistry registry) {
        this.registry = registry;
        
        // Register gauges
        Gauge.builder("agent.client.connection.status", connectionStatus, AtomicInteger::get)
            .description("Connection status (0=disconnected, 1=connected)")
            .register(registry);
        
        Gauge.builder("agent.client.sessions.active", activeSessions, AtomicInteger::get)
            .description("Number of active sessions")
            .register(registry);
        
        Gauge.builder("agent.client.tool_calls.pending", pendingToolCalls, AtomicInteger::get)
            .description("Number of pending tool calls")
            .register(registry);
        
        Gauge.builder("agent.client.mcp_servers.running", runningServers, AtomicInteger::get)
            .description("Number of running MCP servers")
            .register(registry);
        
        Gauge.builder("agent.client.tools.registered", registeredTools, AtomicInteger::get)
            .description("Number of registered tools")
            .register(registry);
    }
    
    // Connection metrics
    public void connectionOpened() {
        connectionStatus.set(1);
        registry.counter("agent.client.connections.opened").increment();
    }
    
    public void connectionClosed(int statusCode) {
        connectionStatus.set(0);
        registry.counter("agent.client.connections.closed", "status", String.valueOf(statusCode)).increment();
    }
    
    public void connectionError() {
        registry.counter("agent.client.connections.errors").increment();
    }
    
    // Session metrics
    public void sessionStarted() {
        activeSessions.incrementAndGet();
        registry.counter("agent.client.sessions.started").increment();
    }
    
    public void sessionCompleted(boolean success, long durationMs) {
        activeSessions.decrementAndGet();
        registry.counter("agent.client.sessions.completed", "success", String.valueOf(success)).increment();
        registry.timer("agent.client.sessions.duration", "success", String.valueOf(success))
            .record(java.time.Duration.ofMillis(durationMs));
    }
    
    // Tool call metrics
    public void toolCallStarted(String toolName) {
        pendingToolCalls.incrementAndGet();
        registry.counter("agent.client.tool_calls.started", "tool", toolName).increment();
    }
    
    public void toolCallCompleted(String toolName, boolean success, long durationMs) {
        pendingToolCalls.decrementAndGet();
        registry.counter("agent.client.tool_calls.completed", "tool", toolName, "success", String.valueOf(success)).increment();
        registry.timer("agent.client.tool_calls.duration", "tool", toolName, "success", String.valueOf(success))
            .record(java.time.Duration.ofMillis(durationMs));
    }
    
    // MCP server metrics
    public void serverStarted(String serverName) {
        runningServers.incrementAndGet();
        registry.counter("agent.client.mcp_servers.started", "server", serverName).increment();
    }
    
    public void serverStopped(String serverName) {
        runningServers.decrementAndGet();
        registry.counter("agent.client.mcp_servers.stopped", "server", serverName).increment();
    }
    
    public void setRegisteredToolCount(int count) {
        registeredTools.set(count);
    }
    
    // Heartbeat metrics
    public void heartbeatSent() {
        registry.counter("agent.client.heartbeats.sent").increment();
    }
    
    public void heartbeatReceived() {
        registry.counter("agent.client.heartbeats.received").increment();
    }
}
