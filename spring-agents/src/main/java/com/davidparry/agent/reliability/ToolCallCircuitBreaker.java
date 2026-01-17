package com.davidparry.agent.reliability;

import com.davidparry.agent.config.McpProxyProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages circuit breakers for tool calls.
 * Creates per-connection, per-tool circuit breakers to isolate failures.
 * Exposes metrics to Prometheus for monitoring circuit breaker health.
 */
@Component
public class ToolCallCircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(ToolCallCircuitBreaker.class);

    private final CircuitBreakerRegistry registry;
    private final Map<String, CircuitBreaker> breakers;
    private final McpProxyProperties.CircuitBreakerConfig config;
    private final MeterRegistry meterRegistry;

    public ToolCallCircuitBreaker(McpProxyProperties properties, MeterRegistry meterRegistry) {
        this.config = properties.circuitBreaker();
        this.breakers = new ConcurrentHashMap<>();
        this.meterRegistry = meterRegistry;

        // Create circuit breaker configuration
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(calculateFailureRateThreshold())
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(config.timeoutSeconds()))
                .waitDurationInOpenState(Duration.ofSeconds(config.timeoutSeconds()))
                .permittedNumberOfCallsInHalfOpenState(config.halfOpenMaxCalls())
                .minimumNumberOfCalls(config.failureThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(config.failureThreshold() * 2)
                .build();

        this.registry = CircuitBreakerRegistry.of(circuitBreakerConfig);

        // Register Prometheus metrics
        registerMetrics();

        logger.info("Circuit breaker initialized: failureThreshold={}, timeout={}s",
                config.failureThreshold(), config.timeoutSeconds());
    }

    /**
     * Registers Prometheus metrics for circuit breaker monitoring.
     */
    private void registerMetrics() {
        // Total circuit breakers
        Gauge.builder("mcp_tool_circuit_breakers_total", breakers, Map::size)
                .description("Total number of MCP tool circuit breakers")
                .register(meterRegistry);

        // Circuit breakers by state
        Gauge.builder("mcp_tool_circuit_breakers_by_state", this,
                cb -> cb.countByState(CircuitBreaker.State.CLOSED))
                .tag("state", "CLOSED")
                .description("MCP tool circuit breakers in CLOSED state (healthy)")
                .register(meterRegistry);

        Gauge.builder("mcp_tool_circuit_breakers_by_state", this,
                cb -> cb.countByState(CircuitBreaker.State.OPEN))
                .tag("state", "OPEN")
                .description("MCP tool circuit breakers in OPEN state (failing)")
                .register(meterRegistry);

        Gauge.builder("mcp_tool_circuit_breakers_by_state", this,
                cb -> cb.countByState(CircuitBreaker.State.HALF_OPEN))
                .tag("state", "HALF_OPEN")
                .description("MCP tool circuit breakers in HALF_OPEN state (testing)")
                .register(meterRegistry);

        Gauge.builder("mcp_tool_circuit_breakers_by_state", this,
                cb -> cb.countByState(CircuitBreaker.State.DISABLED))
                .tag("state", "DISABLED")
                .description("MCP tool circuit breakers in DISABLED state")
                .register(meterRegistry);

        Gauge.builder("mcp_tool_circuit_breakers_by_state", this,
                cb -> cb.countByState(CircuitBreaker.State.FORCED_OPEN))
                .tag("state", "FORCED_OPEN")
                .description("MCP tool circuit breakers in FORCED_OPEN state")
                .register(meterRegistry);

        // Average failure rate across all breakers
        Gauge.builder("mcp_tool_call_failure_rate_percent", this,
                ToolCallCircuitBreaker::getAverageFailureRate)
                .description("Average failure rate of MCP tool calls across all circuit breakers (percentage)")
                .register(meterRegistry);

        // Average slow call rate across all breakers
        Gauge.builder("mcp_tool_call_slow_rate_percent", this,
                ToolCallCircuitBreaker::getAverageSlowCallRate)
                .description("Average slow call rate of MCP tool calls across all circuit breakers (percentage)")
                .register(meterRegistry);

        // Total successful calls across all breakers
        Gauge.builder("mcp_tool_calls_successful_total", this,
                ToolCallCircuitBreaker::getTotalSuccessfulCalls)
                .description("Total successful MCP tool calls across all circuit breakers")
                .register(meterRegistry);

        // Total failed calls across all breakers
        Gauge.builder("mcp_tool_calls_failed_total", this,
                ToolCallCircuitBreaker::getTotalFailedCalls)
                .description("Total failed MCP tool calls across all circuit breakers")
                .register(meterRegistry);

        // Total slow calls across all breakers
        Gauge.builder("mcp_tool_calls_slow_total", this,
                ToolCallCircuitBreaker::getTotalSlowCalls)
                .description("Total slow MCP tool calls across all circuit breakers")
                .register(meterRegistry);

        logger.info("MCP tool circuit breaker Prometheus metrics registered");
    }

    /**
     * Counts circuit breakers in a specific state.
     */
    public long countByState(CircuitBreaker.State state) {
        return breakers.values().stream()
                .filter(cb -> cb.getState() == state)
                .count();
    }

    /**
     * Gets the average failure rate across all circuit breakers.
     */
    public double getAverageFailureRate() {
        if (breakers.isEmpty()) return 0.0;
        return breakers.values().stream()
                .mapToDouble(cb -> cb.getMetrics().getFailureRate())
                .average()
                .orElse(0.0);
    }

    /**
     * Gets the average slow call rate across all circuit breakers.
     */
    public double getAverageSlowCallRate() {
        if (breakers.isEmpty()) return 0.0;
        return breakers.values().stream()
                .mapToDouble(cb -> cb.getMetrics().getSlowCallRate())
                .average()
                .orElse(0.0);
    }

    /**
     * Gets total successful calls across all circuit breakers.
     */
    public long getTotalSuccessfulCalls() {
        return breakers.values().stream()
                .mapToLong(cb -> cb.getMetrics().getNumberOfSuccessfulCalls())
                .sum();
    }

    /**
     * Gets total failed calls across all circuit breakers.
     */
    public long getTotalFailedCalls() {
        return breakers.values().stream()
                .mapToLong(cb -> cb.getMetrics().getNumberOfFailedCalls())
                .sum();
    }

    /**
     * Gets total slow calls across all circuit breakers.
     */
    public long getTotalSlowCalls() {
        return breakers.values().stream()
                .mapToLong(cb -> cb.getMetrics().getNumberOfSlowCalls())
                .sum();
    }

    /**
     * Gets or creates a circuit breaker for a specific connection and tool.
     *
     * @param connectionId the connection ID
     * @param toolName the tool name
     * @return the circuit breaker
     */
    public CircuitBreaker getBreaker(String connectionId, String toolName) {
        String key = createKey(connectionId, toolName);
        return breakers.computeIfAbsent(key, k -> {
            CircuitBreaker breaker = registry.circuitBreaker(k);
            
            // Add event listeners for logging and metrics
            breaker.getEventPublisher()
                    .onStateTransition(event -> {
                        // Record state transition in Prometheus counter
                        Counter.builder("mcp_tool_circuit_breaker_state_transitions_total")
                                .tag("from_state", event.getStateTransition().getFromState().name())
                                .tag("to_state", event.getStateTransition().getToState().name())
                                .description("MCP tool circuit breaker state transitions")
                                .register(meterRegistry)
                                .increment();
                        
                        logger.info("Circuit breaker {} state transition: {} -> {}",
                                k, event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState());
                    })
                    .onFailureRateExceeded(event ->
                            logger.warn("Circuit breaker {} failure rate exceeded: {}%",
                                    k, event.getFailureRate()))
                    .onSlowCallRateExceeded(event ->
                            logger.warn("Circuit breaker {} slow call rate exceeded: {}%",
                                    k, event.getSlowCallRate()));
            
            return breaker;
        });
    }

    /**
     * Checks if a tool is available (circuit breaker not open).
     *
     * @param connectionId the connection ID
     * @param toolName the tool name
     * @return true if the tool is available
     */
    public boolean isToolAvailable(String connectionId, String toolName) {
        String key = createKey(connectionId, toolName);
        CircuitBreaker breaker = breakers.get(key);
        
        if (breaker == null) {
            return true; // No breaker yet means tool is available
        }
        
        return breaker.getState() != CircuitBreaker.State.OPEN;
    }

    /**
     * Gets the state of a circuit breaker.
     *
     * @param connectionId the connection ID
     * @param toolName the tool name
     * @return the circuit breaker state, or null if no breaker exists
     */
    public CircuitBreaker.State getState(String connectionId, String toolName) {
        String key = createKey(connectionId, toolName);
        CircuitBreaker breaker = breakers.get(key);
        return breaker != null ? breaker.getState() : null;
    }

    /**
     * Resets a circuit breaker.
     *
     * @param connectionId the connection ID
     * @param toolName the tool name
     */
    public void reset(String connectionId, String toolName) {
        String key = createKey(connectionId, toolName);
        CircuitBreaker breaker = breakers.get(key);
        if (breaker != null) {
            breaker.reset();
            logger.info("Circuit breaker {} reset", key);
        }
    }

    /**
     * Removes all circuit breakers for a connection.
     *
     * @param connectionId the connection ID
     */
    public void removeConnection(String connectionId) {
        String prefix = connectionId + ":";
        breakers.keySet().removeIf(key -> key.startsWith(prefix));
        logger.debug("Removed circuit breakers for connection: {}", connectionId);
    }

    /**
     * Gets metrics for all circuit breakers.
     *
     * @return map of breaker key to metrics
     */
    public Map<String, CircuitBreakerMetrics> getAllMetrics() {
        Map<String, CircuitBreakerMetrics> metrics = new ConcurrentHashMap<>();
        
        breakers.forEach((key, breaker) -> {
            CircuitBreaker.Metrics m = breaker.getMetrics();
            metrics.put(key, new CircuitBreakerMetrics(
                    breaker.getState().name(),
                    m.getFailureRate(),
                    m.getSlowCallRate(),
                    m.getNumberOfSuccessfulCalls(),
                    m.getNumberOfFailedCalls(),
                    m.getNumberOfSlowCalls()
            ));
        });
        
        return metrics;
    }

    private String createKey(String connectionId, String toolName) {
        return connectionId + ":" + toolName;
    }

    private float calculateFailureRateThreshold() {
        // Calculate failure rate threshold based on failure threshold
        // e.g., if failureThreshold is 5 and we use a sliding window of 10,
        // then 50% failure rate triggers the breaker
        return (float) config.failureThreshold() / (config.failureThreshold() * 2) * 100;
    }

    /**
     * Circuit breaker metrics for monitoring.
     */
    public record CircuitBreakerMetrics(
            String state,
            float failureRate,
            float slowCallRate,
            long successfulCalls,
            long failedCalls,
            long slowCalls
    ) {}
}
