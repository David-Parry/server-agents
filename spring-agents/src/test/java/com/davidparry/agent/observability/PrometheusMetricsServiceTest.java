package com.davidparry.agent.observability;

import com.davidparry.agent.repository.*;
import com.davidparry.agent.session.ConnectionManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PrometheusMetricsService.
 * Tests metrics registration and refresh functionality.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrometheusMetricsServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerTokenRepository tokenRepository;

    @Mock
    private LlmModelRepository modelRepository;

    @Mock
    private PolicyTypeRepository policyTypeRepository;

    @Mock
    private CustomerModelAllowanceRepository allowanceRepository;

    @Mock
    private SecurityAuditLogRepository auditLogRepository;

    @Mock
    private ConnectionManager connectionManager;

    private PrometheusMetricsService service;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new PrometheusMetricsService(
            customerRepository,
            tokenRepository,
            modelRepository,
            policyTypeRepository,
            allowanceRepository,
            auditLogRepository,
            connectionManager
        );
    }

    @Test
    void bindTo_shouldRegisterAllMetrics() {
        // When
        service.bindTo(meterRegistry);

        // Then - verify metrics are registered
        assertNotNull(meterRegistry.find("spring_agents_customers_total").gauge());
        assertNotNull(meterRegistry.find("spring_agents_customers_enabled").gauge());
        assertNotNull(meterRegistry.find("spring_agents_customers_connected").gauge());
        assertNotNull(meterRegistry.find("spring_agents_tokens_active").gauge());
        assertNotNull(meterRegistry.find("spring_agents_models_total").gauge());
        assertNotNull(meterRegistry.find("spring_agents_models_enabled").gauge());
        assertNotNull(meterRegistry.find("spring_agents_policy_types_total").gauge());
        assertNotNull(meterRegistry.find("spring_agents_policy_types_enabled").gauge());
        assertNotNull(meterRegistry.find("spring_agents_allowances_total").gauge());
        assertNotNull(meterRegistry.find("spring_agents_allowances_enabled").gauge());
        assertNotNull(meterRegistry.find("spring_agents_tokens_used_total").gauge());
        assertNotNull(meterRegistry.find("spring_agents_audit_logs_total").gauge());
        assertNotNull(meterRegistry.find("spring_agents_websocket_connections_active").gauge());
        assertNotNull(meterRegistry.find("spring_agents_websocket_sessions_active").gauge());
    }

    @Test
    void refreshMetrics_shouldUpdateAllCachedMetrics() {
        // Given
        when(customerRepository.count()).thenReturn(10L);
        when(customerRepository.countByEnabledTrue()).thenReturn(8L);
        when(tokenRepository.countActiveTokens(any(LocalDateTime.class))).thenReturn(15L);
        when(modelRepository.count()).thenReturn(5L);
        when(modelRepository.countByEnabledTrue()).thenReturn(4L);
        when(policyTypeRepository.count()).thenReturn(3L);
        when(policyTypeRepository.countByEnabledTrue()).thenReturn(2L);
        when(allowanceRepository.count()).thenReturn(50L);
        when(allowanceRepository.countByEnabledTrue()).thenReturn(45L);
        when(allowanceRepository.sumTotalTokensUsed()).thenReturn(100000L);
        when(auditLogRepository.count()).thenReturn(1000L);

        // When
        service.refreshMetrics();

        // Then
        assertEquals(10L, service.getTotalCustomers());
        assertEquals(8L, service.getEnabledCustomers());
        assertEquals(15L, service.getActiveTokens());
        assertEquals(5L, service.getTotalModels());
        assertEquals(4L, service.getEnabledModels());
        assertEquals(3L, service.getTotalPolicyTypes());
        assertEquals(50L, service.getTotalAllowances());
        assertEquals(1000L, service.getTotalAuditLogs());
    }

    @Test
    void refreshMetrics_shouldHandleExceptionsGracefully() {
        // Given
        when(customerRepository.count()).thenThrow(new RuntimeException("Database error"));

        // When/Then - should not throw
        assertDoesNotThrow(() -> service.refreshMetrics());
    }

    @Test
    void getConnectedCustomers_shouldDelegateToConnectionManager() {
        // Given
        when(connectionManager.getConnectedCustomerCount()).thenReturn(5L);

        // When
        long result = service.getConnectedCustomers();

        // Then
        assertEquals(5L, result);
    }

    @Test
    void getActiveConnections_shouldDelegateToConnectionManager() {
        // Given
        when(connectionManager.getConnectionCount()).thenReturn(10);

        // When
        int result = service.getActiveConnections();

        // Then
        assertEquals(10, result);
    }

    @Test
    void getActiveSessions_shouldDelegateToConnectionManager() {
        // Given
        when(connectionManager.getTotalSessionCount()).thenReturn(15);

        // When
        int result = service.getActiveSessions();

        // Then
        assertEquals(15, result);
    }

    @Test
    void getters_shouldReturnInitialZeroValues() {
        // When/Then - initial values should be 0
        assertEquals(0L, service.getTotalCustomers());
        assertEquals(0L, service.getEnabledCustomers());
        assertEquals(0L, service.getActiveTokens());
        assertEquals(0L, service.getTotalModels());
        assertEquals(0L, service.getEnabledModels());
        assertEquals(0L, service.getTotalPolicyTypes());
        assertEquals(0L, service.getTotalAllowances());
        assertEquals(0L, service.getTotalAuditLogs());
    }

    @Test
    void metricsUpdate_shouldReflectInBoundRegistry() {
        // Given
        service.bindTo(meterRegistry);
        when(customerRepository.count()).thenReturn(25L);
        when(customerRepository.countByEnabledTrue()).thenReturn(20L);
        when(tokenRepository.countActiveTokens(any(LocalDateTime.class))).thenReturn(30L);
        when(modelRepository.count()).thenReturn(10L);
        when(modelRepository.countByEnabledTrue()).thenReturn(8L);
        when(policyTypeRepository.count()).thenReturn(5L);
        when(policyTypeRepository.countByEnabledTrue()).thenReturn(4L);
        when(allowanceRepository.count()).thenReturn(100L);
        when(allowanceRepository.countByEnabledTrue()).thenReturn(90L);
        when(allowanceRepository.sumTotalTokensUsed()).thenReturn(500000L);
        when(auditLogRepository.count()).thenReturn(5000L);

        // When
        service.refreshMetrics();

        // Then
        assertEquals(25.0, meterRegistry.find("spring_agents_customers_total").gauge().value());
        assertEquals(20.0, meterRegistry.find("spring_agents_customers_enabled").gauge().value());
        assertEquals(30.0, meterRegistry.find("spring_agents_tokens_active").gauge().value());
        assertEquals(10.0, meterRegistry.find("spring_agents_models_total").gauge().value());
        assertEquals(8.0, meterRegistry.find("spring_agents_models_enabled").gauge().value());
    }

    @Test
    void websocketMetrics_shouldReflectConnectionManagerState() {
        // Given
        when(connectionManager.getConnectionCount()).thenReturn(3);
        when(connectionManager.getTotalSessionCount()).thenReturn(7);
        when(connectionManager.getAllConnections()).thenReturn(List.of());

        service.bindTo(meterRegistry);

        // When/Then - websocket metrics should be read from connection manager
        assertEquals(3.0, meterRegistry.find("spring_agents_websocket_connections_active").gauge().value());
        assertEquals(7.0, meterRegistry.find("spring_agents_websocket_sessions_active").gauge().value());
    }
}
