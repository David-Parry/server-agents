package com.davidparry.agent.observability;

import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.CustomerTokenRepository;
import com.davidparry.agent.repository.LlmModelRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import com.davidparry.agent.repository.SecurityAuditLogRepository;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prometheus metrics service for comprehensive application observability.
 * Exposes business and operational metrics via Micrometer/Prometheus.
 *
 * Metrics are divided into two categories:
 * 1. Real-time metrics - Updated on every scrape (connection counts, session counts)
 * 2. Cached metrics - Updated periodically via scheduler (database counts)
 */
@Component
public class PrometheusMetricsService implements MeterBinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusMetricsService.class);

    private final CustomerRepository customerRepository;
    private final CustomerTokenRepository tokenRepository;
    private final LlmModelRepository modelRepository;
    private final PolicyTypeRepository policyTypeRepository;
    private final CustomerModelAllowanceRepository allowanceRepository;
    private final SecurityAuditLogRepository auditLogRepository;
    private final ConnectionManager connectionManager;

    // Cached values for expensive database queries (updated periodically)
    private final AtomicLong totalCustomers = new AtomicLong(0);
    private final AtomicLong enabledCustomers = new AtomicLong(0);
    private final AtomicLong totalModels = new AtomicLong(0);
    private final AtomicLong enabledModels = new AtomicLong(0);
    private final AtomicLong totalPolicyTypes = new AtomicLong(0);
    private final AtomicLong enabledPolicyTypes = new AtomicLong(0);
    private final AtomicLong totalAllowances = new AtomicLong(0);
    private final AtomicLong enabledAllowances = new AtomicLong(0);
    private final AtomicLong totalAuditLogs = new AtomicLong(0);
    private final AtomicLong activeTokens = new AtomicLong(0);
    private final AtomicLong totalTokensUsed = new AtomicLong(0);

    public PrometheusMetricsService(
            CustomerRepository customerRepository,
            CustomerTokenRepository tokenRepository,
            LlmModelRepository modelRepository,
            PolicyTypeRepository policyTypeRepository,
            CustomerModelAllowanceRepository allowanceRepository,
            SecurityAuditLogRepository auditLogRepository,
            ConnectionManager connectionManager) {
        this.customerRepository = customerRepository;
        this.tokenRepository = tokenRepository;
        this.modelRepository = modelRepository;
        this.policyTypeRepository = policyTypeRepository;
        this.allowanceRepository = allowanceRepository;
        this.auditLogRepository = auditLogRepository;
        this.connectionManager = connectionManager;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // ==================== Customer Metrics ====================
        Gauge.builder("spring_agents_customers_total", totalCustomers, AtomicLong::get)
                .description("Total number of customers in the system")
                .register(registry);

        Gauge.builder("spring_agents_customers_enabled", enabledCustomers, AtomicLong::get)
                .description("Number of enabled customers")
                .register(registry);

        // Real-time: customers with active WebSocket connections
        Gauge.builder("spring_agents_customers_connected", connectionManager, ConnectionManager::getConnectedCustomerCount)
                .description("Number of customers with active WebSocket connections")
                .register(registry);

        // ==================== Token Metrics ====================
        Gauge.builder("spring_agents_tokens_active", activeTokens, AtomicLong::get)
                .description("Number of active (non-revoked, non-expired) API tokens")
                .register(registry);

        // ==================== Model Metrics ====================
        Gauge.builder("spring_agents_models_total", totalModels, AtomicLong::get)
                .description("Total number of LLM models configured")
                .register(registry);

        Gauge.builder("spring_agents_models_enabled", enabledModels, AtomicLong::get)
                .description("Number of enabled LLM models")
                .register(registry);

        // ==================== Policy Type Metrics ====================
        Gauge.builder("spring_agents_policy_types_total", totalPolicyTypes, AtomicLong::get)
                .description("Total number of policy types")
                .register(registry);

        Gauge.builder("spring_agents_policy_types_enabled", enabledPolicyTypes, AtomicLong::get)
                .description("Number of enabled policy types")
                .register(registry);

        // ==================== Allowance Metrics ====================
        Gauge.builder("spring_agents_allowances_total", totalAllowances, AtomicLong::get)
                .description("Total number of customer model allowances")
                .register(registry);

        Gauge.builder("spring_agents_allowances_enabled", enabledAllowances, AtomicLong::get)
                .description("Number of enabled customer model allowances")
                .register(registry);

        Gauge.builder("spring_agents_tokens_used_total", totalTokensUsed, AtomicLong::get)
                .description("Total LLM tokens used across all customers and models")
                .register(registry);

        // ==================== Audit Metrics ====================
        Gauge.builder("spring_agents_audit_logs_total", totalAuditLogs, AtomicLong::get)
                .description("Total number of audit log entries")
                .register(registry);

        // ==================== WebSocket Connection Metrics (Real-time) ====================
        Gauge.builder("spring_agents_websocket_connections_active", connectionManager, ConnectionManager::getConnectionCount)
                .description("Number of active WebSocket connections")
                .register(registry);

        Gauge.builder("spring_agents_websocket_sessions_active", connectionManager, ConnectionManager::getTotalSessionCount)
                .description("Number of active prompt sessions across all connections")
                .register(registry);

        // Total sessions created across all active connections
        Gauge.builder("spring_agents_websocket_sessions_created_total", connectionManager,
                cm -> cm.getAllConnections().stream()
                        .mapToLong(ClientConnection::getTotalSessionsCreated)
                        .sum())
                .description("Total prompt sessions created across all active connections")
                .register(registry);

        // Total tool calls across all active connections
        Gauge.builder("spring_agents_websocket_tool_calls_total", connectionManager,
                cm -> cm.getAllConnections().stream()
                        .mapToLong(ClientConnection::getTotalToolCalls)
                        .sum())
                .description("Total tool calls executed across all active connections")
                .register(registry);

        LOGGER.info("Prometheus metrics registered successfully");
    }

    /**
     * Refreshes cached metrics from the database.
     * Should be called periodically (e.g., every 30 seconds) via @Scheduled.
     */
    public void refreshMetrics() {
        try {
            // Customer metrics
            totalCustomers.set(customerRepository.count());
            enabledCustomers.set(customerRepository.countByEnabledTrue());

            // Token metrics
            activeTokens.set(tokenRepository.countActiveTokens(LocalDateTime.now()));

            // Model metrics
            totalModels.set(modelRepository.count());
            enabledModels.set(modelRepository.countByEnabledTrue());

            // Policy type metrics
            totalPolicyTypes.set(policyTypeRepository.count());
            enabledPolicyTypes.set(policyTypeRepository.countByEnabledTrue());

            // Allowance metrics
            totalAllowances.set(allowanceRepository.count());
            enabledAllowances.set(allowanceRepository.countByEnabledTrue());
            totalTokensUsed.set(allowanceRepository.sumTotalTokensUsed());

            // Audit metrics
            totalAuditLogs.set(auditLogRepository.count());

            LOGGER.trace("Prometheus metrics refreshed: customers={}, tokens={}, connections={}",
                    totalCustomers.get(), activeTokens.get(), connectionManager.getConnectionCount());
        } catch (Exception e) {
            LOGGER.error("Failed to refresh Prometheus metrics", e);
        }
    }

    // ==================== Getters for current metric values ====================

    public long getTotalCustomers() {
        return totalCustomers.get();
    }

    public long getEnabledCustomers() {
        return enabledCustomers.get();
    }

    public long getConnectedCustomers() {
        return connectionManager.getConnectedCustomerCount();
    }

    public long getActiveTokens() {
        return activeTokens.get();
    }

    public long getTotalModels() {
        return totalModels.get();
    }

    public long getEnabledModels() {
        return enabledModels.get();
    }

    public long getTotalPolicyTypes() {
        return totalPolicyTypes.get();
    }

    public long getTotalAllowances() {
        return totalAllowances.get();
    }

    public long getTotalAuditLogs() {
        return totalAuditLogs.get();
    }

    public int getActiveConnections() {
        return connectionManager.getConnectionCount();
    }

    public int getActiveSessions() {
        return connectionManager.getTotalSessionCount();
    }
}
