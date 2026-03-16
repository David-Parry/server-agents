package com.davidparry.agent.observability;

import com.davidparry.agent.dto.CustomerMetricsSnapshot;
import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.reliability.ToolCallCircuitBreaker;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import com.davidparry.agent.session.PromptSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Service for collecting and aggregating per-customer metrics.
 * Maintains historical counters that persist across connection lifecycles.
 *
 * This service provides:
 * - Real-time metrics from active connections
 * - Historical counters that survive connection disconnects
 * - Aggregated metrics for Prometheus export
 */
@Service
public class CustomerMetricsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerMetricsService.class);

    private final ConnectionManager connectionManager;
    private final CustomerRepository customerRepository;
    private final CustomerModelAllowanceRepository allowanceRepository;
    private final ToolCallCircuitBreaker circuitBreaker;

    // Historical counters per customer (persist across connections)
    private final Map<String, CustomerHistoricalMetrics> historicalMetrics = new ConcurrentHashMap<>();

    public CustomerMetricsService(
            ConnectionManager connectionManager,
            CustomerRepository customerRepository,
            CustomerModelAllowanceRepository allowanceRepository,
            ToolCallCircuitBreaker circuitBreaker) {
        this.connectionManager = connectionManager;
        this.customerRepository = customerRepository;
        this.allowanceRepository = allowanceRepository;
        this.circuitBreaker = circuitBreaker;

        LOGGER.info("CustomerMetricsService initialized");
    }

    /**
     * Gets metrics snapshot for all connected customers.
     *
     * @return list of metrics snapshots for all customers with active connections
     */
    public List<CustomerMetricsSnapshot> getConnectedCustomerMetrics() {
        Set<String> connectedCustomerIds = connectionManager.getConnectedCustomerIds();

        return connectedCustomerIds.stream()
                .map(this::getCustomerMetrics)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Gets metrics snapshot for all customers with historical data.
     * Includes customers who have disconnected but have historical metrics.
     *
     * @return list of metrics snapshots for all customers with any metrics
     */
    public List<CustomerMetricsSnapshot> getAllCustomerMetrics() {
        Set<String> allCustomerIds = new HashSet<>();
        allCustomerIds.addAll(connectionManager.getConnectedCustomerIds());
        allCustomerIds.addAll(historicalMetrics.keySet());

        return allCustomerIds.stream()
                .map(this::getCustomerMetrics)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Gets metrics snapshot for a specific customer.
     *
     * @param customerId the customer's UUID string
     * @return optional containing the metrics snapshot if customer exists
     */
    public Optional<CustomerMetricsSnapshot> getCustomerMetrics(String customerId) {
        // Get customer entity
        Optional<CustomerEntity> customerOpt;
        try {
            customerOpt = customerRepository.findByCustomerId(UUID.fromString(customerId));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid customer ID format: {}", customerId);
            return Optional.empty();
        }

        if (customerOpt.isEmpty()) {
            return Optional.empty();
        }

        CustomerEntity customer = customerOpt.get();
        Collection<ClientConnection> connections = connectionManager.getConnectionsByCustomerId(customerId);
        CustomerHistoricalMetrics historical = getOrCreateHistorical(customerId);

        // Aggregate current connection metrics
        int activeConnections = 0;
        int activeSessions = 0;
        int pendingSessions = 0;
        int executingSessions = 0;
        int waitingForToolSessions = 0;
        int pendingToolCalls = 0;
        double totalDurationMs = 0;
        double maxDurationMs = 0;
        int sessionCount = 0;

        for (ClientConnection conn : connections) {
            if (!conn.isActive()) {
                continue;
            }

            activeConnections++;

            for (PromptSession session : conn.getAllSessions()) {
                sessionCount++;
                double durationMs = session.getDurationMs();
                totalDurationMs += durationMs;
                maxDurationMs = Math.max(maxDurationMs, durationMs);
                pendingToolCalls += session.getPendingCallCount();

                switch (session.getState()) {
                    case PENDING -> pendingSessions++;
                    case EXECUTING -> executingSessions++;
                    case WAITING_FOR_TOOL -> waitingForToolSessions++;
                    default -> {
                        // Terminal states not counted as active
                    }
                }

                if (!session.isTerminal()) {
                    activeSessions++;
                }
            }
        }

        // Get circuit breaker stats for this customer's connections
        Map<String, ToolCallCircuitBreaker.CircuitBreakerMetrics> cbMetrics =
                circuitBreaker.getAllMetrics();

        int cbTotal = 0;
        int cbOpen = 0;
        int cbClosed = 0;
        int cbHalfOpen = 0;
        for (Map.Entry<String, ToolCallCircuitBreaker.CircuitBreakerMetrics> entry : cbMetrics.entrySet()) {
            // Circuit breaker keys are "connectionId:toolName"
            String connId = entry.getKey().split(":")[0];
            // Check if this belongs to a connection for this customer
            for (ClientConnection conn : connections) {
                if (conn.getConnectionId().equals(connId)) {
                    cbTotal++;
                    switch (entry.getValue().state()) {
                        case "OPEN" -> cbOpen++;
                        case "CLOSED" -> cbClosed++;
                        case "HALF_OPEN" -> cbHalfOpen++;
                        default -> { }
                    }
                    break;
                }
            }
        }

        // Get token usage from database
        long totalTokensUsed = 0;
        long totalTokensAllowed = 0;
        try {
            List<CustomerModelAllowanceEntity> allowances =
                    allowanceRepository.findByCustomerCustomerIdWithModel(UUID.fromString(customerId));
            for (CustomerModelAllowanceEntity allowance : allowances) {
                totalTokensUsed += allowance.getTokensUsed();
                if (allowance.getAllowedTokens() != null) {
                    totalTokensAllowed += allowance.getAllowedTokens();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not fetch token usage for customer {}: {}", customerId, e.getMessage());
        }

        double avgSessionDurationMs = sessionCount > 0 ? totalDurationMs / sessionCount : 0;

        return Optional.of(CustomerMetricsSnapshot.builder()
                .customerId(customerId)
                .customerName(customer.getName())
                .enabled(customer.isEnabled())
                .activeConnections(activeConnections)
                .totalConnectionsOpened(historical.connectionsOpened.sum())
                .totalConnectionsClosed(historical.connectionsClosed.sum())
                .activeSessions(activeSessions)
                .pendingSessions(pendingSessions)
                .executingSessions(executingSessions)
                .waitingForToolSessions(waitingForToolSessions)
                .totalSessionsCreated(historical.sessionsCreated.sum())
                .totalSessionsCompleted(historical.sessionsCompleted.sum())
                .totalSessionsFailed(historical.sessionsFailed.sum())
                .totalSessionsCancelled(historical.sessionsCancelled.sum())
                .totalSessionsTimedOut(historical.sessionsTimedOut.sum())
                .totalToolCalls(historical.toolCalls.sum())
                .totalToolCallsFailed(historical.toolCallsFailed.sum())
                .pendingToolCalls(pendingToolCalls)
                .avgSessionDurationMs(avgSessionDurationMs)
                .maxSessionDurationMs(maxDurationMs)
                .avgToolCallDurationMs(historical.getAverageToolCallDurationMs())
                .totalTokensUsed(totalTokensUsed)
                .totalTokensAllowed(totalTokensAllowed)
                .circuitBreakersTotal(cbTotal)
                .circuitBreakersOpen(cbOpen)
                .circuitBreakersClosed(cbClosed)
                .circuitBreakersHalfOpen(cbHalfOpen)
                .snapshotTime(Instant.now())
                .build());
    }

    // ==================== Event Recording Methods ====================
    // These methods are called from other services to record events

    /**
     * Records a connection opened event for a customer.
     */
    public void recordConnectionOpened(String customerId) {
        getOrCreateHistorical(customerId).connectionsOpened.increment();
        LOGGER.trace("Recorded connection opened for customer {}", customerId);
    }

    /**
     * Records a connection closed event for a customer.
     */
    public void recordConnectionClosed(String customerId) {
        getOrCreateHistorical(customerId).connectionsClosed.increment();
        LOGGER.trace("Recorded connection closed for customer {}", customerId);
    }

    /**
     * Records a session created event for a customer.
     */
    public void recordSessionCreated(String customerId) {
        getOrCreateHistorical(customerId).sessionsCreated.increment();
        LOGGER.trace("Recorded session created for customer {}", customerId);
    }

    /**
     * Records a session completed event for a customer.
     *
     * @param customerId the customer ID
     * @param durationMs the session duration in milliseconds
     */
    public void recordSessionCompleted(String customerId, long durationMs) {
        CustomerHistoricalMetrics h = getOrCreateHistorical(customerId);
        h.sessionsCompleted.increment();
        h.recordSessionDuration(durationMs);
        LOGGER.trace("Recorded session completed for customer {} ({}ms)", customerId, durationMs);
    }

    /**
     * Records a session failed event for a customer.
     */
    public void recordSessionFailed(String customerId) {
        getOrCreateHistorical(customerId).sessionsFailed.increment();
        LOGGER.trace("Recorded session failed for customer {}", customerId);
    }

    /**
     * Records a session cancelled event for a customer.
     */
    public void recordSessionCancelled(String customerId) {
        getOrCreateHistorical(customerId).sessionsCancelled.increment();
        LOGGER.trace("Recorded session cancelled for customer {}", customerId);
    }

    /**
     * Records a session timed out event for a customer.
     */
    public void recordSessionTimedOut(String customerId) {
        getOrCreateHistorical(customerId).sessionsTimedOut.increment();
        LOGGER.trace("Recorded session timed out for customer {}", customerId);
    }

    /**
     * Records a tool call event for a customer.
     *
     * @param customerId the customer ID
     * @param durationMs the tool call duration in milliseconds
     * @param success whether the tool call succeeded
     */
    public void recordToolCall(String customerId, long durationMs, boolean success) {
        CustomerHistoricalMetrics h = getOrCreateHistorical(customerId);
        h.toolCalls.increment();
        if (!success) {
            h.toolCallsFailed.increment();
        }
        h.recordToolCallDuration(durationMs);
        LOGGER.trace("Recorded tool call for customer {} ({}ms, success={})", customerId, durationMs, success);
    }

    /**
     * Gets or creates historical metrics for a customer.
     */
    private CustomerHistoricalMetrics getOrCreateHistorical(String customerId) {
        return historicalMetrics.computeIfAbsent(customerId, k -> new CustomerHistoricalMetrics());
    }

    /**
     * Clears historical metrics for a customer.
     * Use with caution - this resets all counters.
     */
    public void clearHistoricalMetrics(String customerId) {
        historicalMetrics.remove(customerId);
        LOGGER.info("Cleared historical metrics for customer {}", customerId);
    }

    /**
     * Gets the number of customers with historical metrics.
     */
    public int getCustomerCount() {
        return historicalMetrics.size();
    }

    // ==================== Inner Classes ====================

    /**
     * Historical metrics that persist across connection lifecycles.
     * Uses LongAdder for high-concurrency counter updates.
     */
    private static final class CustomerHistoricalMetrics {
        final LongAdder connectionsOpened = new LongAdder();
        final LongAdder connectionsClosed = new LongAdder();
        final LongAdder sessionsCreated = new LongAdder();
        final LongAdder sessionsCompleted = new LongAdder();
        final LongAdder sessionsFailed = new LongAdder();
        final LongAdder sessionsCancelled = new LongAdder();
        final LongAdder sessionsTimedOut = new LongAdder();
        final LongAdder toolCalls = new LongAdder();
        final LongAdder toolCallsFailed = new LongAdder();

        // For calculating averages
        final AtomicLong totalSessionDurationMs = new AtomicLong(0);
        final AtomicLong totalToolCallDurationMs = new AtomicLong(0);

        void recordSessionDuration(long durationMs) {
            totalSessionDurationMs.addAndGet(durationMs);
        }

        void recordToolCallDuration(long durationMs) {
            totalToolCallDurationMs.addAndGet(durationMs);
        }

        double getAverageToolCallDurationMs() {
            long calls = toolCalls.sum();
            return calls > 0 ? (double) totalToolCallDurationMs.get() / calls : 0;
        }
    }
}
