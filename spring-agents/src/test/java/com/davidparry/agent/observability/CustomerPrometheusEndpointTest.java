package com.davidparry.agent.observability;

import com.davidparry.agent.dto.CustomerMetricsSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CustomerPrometheusEndpoint.
 * Tests Prometheus metrics generation for per-customer metrics.
 */
@ExtendWith(MockitoExtension.class)
class CustomerPrometheusEndpointTest {

    @Mock
    private CustomerMetricsService customerMetricsService;

    private CustomerPrometheusEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new CustomerPrometheusEndpoint(customerMetricsService);
    }

    @Test
    void scrape_shouldReturnEmptyMetricsWhenNoCustomers() {
        // Given
        when(customerMetricsService.getConnectedCustomerMetrics()).thenReturn(List.of());

        // When
        String result = endpoint.scrape();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("# HELP"));
        assertTrue(result.contains("# TYPE"));
        assertTrue(result.contains("spring_agents_customers_tracked_total 0"));
    }

    @Test
    void scrape_shouldGenerateMetricsForCustomer() {
        // Given
        CustomerMetricsSnapshot snapshot = createTestSnapshot("customer-123", "Test Customer");
        when(customerMetricsService.getConnectedCustomerMetrics()).thenReturn(List.of(snapshot));

        // When
        String result = endpoint.scrape();

        // Then
        assertNotNull(result);

        // Check for connection metrics
        assertTrue(result.contains("spring_agents_customer_connections_active{customer_id=\"customer-123\",customer_name=\"Test Customer\"}"));
        assertTrue(result.contains("spring_agents_customer_connections_opened_total"));
        assertTrue(result.contains("spring_agents_customer_connections_closed_total"));

        // Check for session metrics
        assertTrue(result.contains("spring_agents_customer_sessions_active"));
        assertTrue(result.contains("spring_agents_customer_sessions_created_total"));
        assertTrue(result.contains("spring_agents_customer_sessions_completed_total"));
        assertTrue(result.contains("spring_agents_customer_sessions_failed_total"));
        assertTrue(result.contains("spring_agents_customer_sessions_cancelled_total"));
        assertTrue(result.contains("spring_agents_customer_sessions_timedout_total"));

        // Check for session state metrics
        assertTrue(result.contains("state=\"PENDING\""));
        assertTrue(result.contains("state=\"EXECUTING\""));
        assertTrue(result.contains("state=\"WAITING_FOR_TOOL\""));

        // Check for tool call metrics
        assertTrue(result.contains("spring_agents_customer_tool_calls_total"));
        assertTrue(result.contains("spring_agents_customer_tool_calls_failed_total"));
        assertTrue(result.contains("spring_agents_customer_tool_calls_pending"));

        // Check for duration metrics
        assertTrue(result.contains("spring_agents_customer_session_duration_avg_ms"));
        assertTrue(result.contains("spring_agents_customer_session_duration_max_ms"));
        assertTrue(result.contains("spring_agents_customer_tool_call_duration_avg_ms"));

        // Check for token metrics
        assertTrue(result.contains("spring_agents_customer_tokens_used_total"));
        assertTrue(result.contains("spring_agents_customer_tokens_allowed_total"));

        // Check for circuit breaker metrics
        assertTrue(result.contains("spring_agents_customer_circuit_breakers_total"));
        assertTrue(result.contains("state=\"OPEN\""));
        assertTrue(result.contains("state=\"CLOSED\""));
        assertTrue(result.contains("state=\"HALF_OPEN\""));

        // Check for enabled status
        assertTrue(result.contains("spring_agents_customer_enabled"));

        // Check customer count
        assertTrue(result.contains("spring_agents_customers_tracked_total 1"));
    }

    @Test
    void scrape_shouldGenerateMetricsForMultipleCustomers() {
        // Given
        CustomerMetricsSnapshot snapshot1 = createTestSnapshot("customer-1", "Customer One");
        CustomerMetricsSnapshot snapshot2 = createTestSnapshot("customer-2", "Customer Two");
        when(customerMetricsService.getConnectedCustomerMetrics()).thenReturn(List.of(snapshot1, snapshot2));

        // When
        String result = endpoint.scrape();

        // Then
        assertTrue(result.contains("customer_id=\"customer-1\""));
        assertTrue(result.contains("customer_id=\"customer-2\""));
        assertTrue(result.contains("customer_name=\"Customer One\""));
        assertTrue(result.contains("customer_name=\"Customer Two\""));
        assertTrue(result.contains("spring_agents_customers_tracked_total 2"));
    }

    @Test
    void scrape_shouldEscapeSpecialCharactersInLabels() {
        // Given
        CustomerMetricsSnapshot snapshot = createTestSnapshot("cust-123", "Customer \"Test\" Name\\n");
        when(customerMetricsService.getConnectedCustomerMetrics()).thenReturn(List.of(snapshot));

        // When
        String result = endpoint.scrape();

        // Then
        // Quotes and backslashes should be escaped
        assertTrue(result.contains("\\\"Test\\\""));
        assertTrue(result.contains("\\\\n"));
    }

    @Test
    void scrape_shouldHandleNullCustomerName() {
        // Given
        CustomerMetricsSnapshot snapshot = CustomerMetricsSnapshot.builder()
            .customerId("customer-123")
            .customerName(null)
            .enabled(true)
            .activeConnections(2)
            .totalConnectionsOpened(10L)
            .totalConnectionsClosed(5L)
            .activeSessions(3)
            .pendingSessions(1)
            .executingSessions(1)
            .waitingForToolSessions(1)
            .totalSessionsCreated(5L)
            .totalSessionsCompleted(3L)
            .totalSessionsFailed(1L)
            .totalSessionsCancelled(0L)
            .totalSessionsTimedOut(0L)
            .totalToolCalls(10L)
            .totalToolCallsFailed(2L)
            .pendingToolCalls(1)
            .avgSessionDurationMs(100.0)
            .maxSessionDurationMs(200.0)
            .avgToolCallDurationMs(50.0)
            .totalTokensUsed(5000L)
            .totalTokensAllowed(10000L)
            .circuitBreakersTotal(3)
            .circuitBreakersOpen(1)
            .circuitBreakersClosed(2)
            .circuitBreakersHalfOpen(0)
            .snapshotTime(Instant.now())
            .build();
        when(customerMetricsService.getConnectedCustomerMetrics()).thenReturn(List.of(snapshot));

        // When
        String result = endpoint.scrape();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("customer_name=\"\""));
    }

    @Test
    void scrape_shouldIncludeMetricHeaders() {
        // Given
        when(customerMetricsService.getConnectedCustomerMetrics()).thenReturn(List.of());

        // When
        String result = endpoint.scrape();

        // Then
        // Check for HELP declarations
        assertTrue(result.contains("# HELP spring_agents_customer_connections_active Active WebSocket connections for customer"));
        assertTrue(result.contains("# HELP spring_agents_customer_sessions_active Active sessions for customer"));
        assertTrue(result.contains("# HELP spring_agents_customer_tokens_used_total Total LLM tokens used by customer"));

        // Check for TYPE declarations
        assertTrue(result.contains("# TYPE spring_agents_customer_connections_active gauge"));
        assertTrue(result.contains("# TYPE spring_agents_customer_connections_opened_total counter"));
        assertTrue(result.contains("# TYPE spring_agents_customer_sessions_active gauge"));
    }

    @Test
    void scrape_shouldFormatDoubleValues() {
        // Given
        CustomerMetricsSnapshot snapshot = CustomerMetricsSnapshot.builder()
            .customerId("customer-123")
            .customerName("Test")
            .enabled(true)
            .avgSessionDurationMs(123.456789)
            .snapshotTime(Instant.now())
            .build();
        when(customerMetricsService.getConnectedCustomerMetrics()).thenReturn(List.of(snapshot));

        // When
        String result = endpoint.scrape();

        // Then
        // Double values should be formatted reasonably
        assertTrue(result.contains("spring_agents_customer_session_duration_avg_ms"));
    }

    @Test
    void scrape_shouldHandleDisabledCustomer() {
        // Given
        CustomerMetricsSnapshot snapshot = CustomerMetricsSnapshot.builder()
            .customerId("customer-123")
            .customerName("Disabled Customer")
            .enabled(false)
            .snapshotTime(Instant.now())
            .build();
        when(customerMetricsService.getConnectedCustomerMetrics()).thenReturn(List.of(snapshot));

        // When
        String result = endpoint.scrape();

        // Then
        assertTrue(result.contains("spring_agents_customer_enabled{customer_id=\"customer-123\",customer_name=\"Disabled Customer\"} 0"));
    }

    @Test
    void scrape_shouldHandleEnabledCustomer() {
        // Given
        CustomerMetricsSnapshot snapshot = createTestSnapshot("customer-123", "Enabled Customer");
        when(customerMetricsService.getConnectedCustomerMetrics()).thenReturn(List.of(snapshot));

        // When
        String result = endpoint.scrape();

        // Then
        assertTrue(result.contains("spring_agents_customer_enabled{customer_id=\"customer-123\",customer_name=\"Enabled Customer\"} 1"));
    }

    // ==================== Helper Methods ====================

    private CustomerMetricsSnapshot createTestSnapshot(String customerId, String customerName) {
        return CustomerMetricsSnapshot.builder()
            .customerId(customerId)
            .customerName(customerName)
            .enabled(true)
            .activeConnections(2)
            .totalConnectionsOpened(10L)
            .totalConnectionsClosed(5L)
            .activeSessions(3)
            .pendingSessions(1)
            .executingSessions(1)
            .waitingForToolSessions(1)
            .totalSessionsCreated(5L)
            .totalSessionsCompleted(3L)
            .totalSessionsFailed(1L)
            .totalSessionsCancelled(0L)
            .totalSessionsTimedOut(0L)
            .totalToolCalls(10L)
            .totalToolCallsFailed(2L)
            .pendingToolCalls(1)
            .avgSessionDurationMs(100.5)
            .maxSessionDurationMs(250.0)
            .avgToolCallDurationMs(50.0)
            .totalTokensUsed(5000L)
            .totalTokensAllowed(10000L)
            .circuitBreakersTotal(3)
            .circuitBreakersOpen(1)
            .circuitBreakersClosed(2)
            .circuitBreakersHalfOpen(0)
            .snapshotTime(Instant.now())
            .build();
    }
}
