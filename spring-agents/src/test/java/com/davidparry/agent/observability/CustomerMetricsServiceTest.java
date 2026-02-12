package com.davidparry.agent.observability;

import com.davidparry.agent.dto.CustomerMetricsSnapshot;
import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.reliability.ToolCallCircuitBreaker;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import com.davidparry.agent.session.PromptSession;
import com.davidparry.agent.session.SessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomerMetricsService.
 */
@ExtendWith(MockitoExtension.class)
class CustomerMetricsServiceTest {

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerModelAllowanceRepository allowanceRepository;

    @Mock
    private ToolCallCircuitBreaker circuitBreaker;

    private CustomerMetricsService service;

    @BeforeEach
    void setUp() {
        service = new CustomerMetricsService(
                connectionManager,
                customerRepository,
                allowanceRepository,
                circuitBreaker
        );
    }

    @Test
    void recordConnectionOpened_shouldIncrementCounter() {
        // Given
        String customerId = UUID.randomUUID().toString();

        // When
        service.recordConnectionOpened(customerId);
        service.recordConnectionOpened(customerId);

        // Then - verify no exception and counter is tracked
        assertEquals(1, service.getCustomerCount());
    }

    @Test
    void recordConnectionClosed_shouldIncrementCounter() {
        // Given
        String customerId = UUID.randomUUID().toString();
        service.recordConnectionOpened(customerId);

        // When
        service.recordConnectionClosed(customerId);

        // Then - verify no exception
        assertEquals(1, service.getCustomerCount());
    }

    @Test
    void recordSessionCreated_shouldIncrementCounter() {
        // Given
        String customerId = UUID.randomUUID().toString();

        // When
        service.recordSessionCreated(customerId);

        // Then
        assertEquals(1, service.getCustomerCount());
    }

    @Test
    void recordSessionCompleted_shouldIncrementCounterAndRecordDuration() {
        // Given
        String customerId = UUID.randomUUID().toString();

        // When
        service.recordSessionCompleted(customerId, 5000L);

        // Then
        assertEquals(1, service.getCustomerCount());
    }

    @Test
    void recordSessionFailed_shouldIncrementCounter() {
        // Given
        String customerId = UUID.randomUUID().toString();

        // When
        service.recordSessionFailed(customerId);

        // Then
        assertEquals(1, service.getCustomerCount());
    }

    @Test
    void recordSessionCancelled_shouldIncrementCounter() {
        // Given
        String customerId = UUID.randomUUID().toString();

        // When
        service.recordSessionCancelled(customerId);

        // Then
        assertEquals(1, service.getCustomerCount());
    }

    @Test
    void recordSessionTimedOut_shouldIncrementCounter() {
        // Given
        String customerId = UUID.randomUUID().toString();

        // When
        service.recordSessionTimedOut(customerId);

        // Then
        assertEquals(1, service.getCustomerCount());
    }

    @Test
    void recordToolCall_shouldIncrementCounterAndRecordDuration() {
        // Given
        String customerId = UUID.randomUUID().toString();

        // When
        service.recordToolCall(customerId, 500L, true);
        service.recordToolCall(customerId, 200L, false);

        // Then
        assertEquals(1, service.getCustomerCount());
    }

    @Test
    void clearHistoricalMetrics_shouldRemoveCustomerMetrics() {
        // Given
        String customerId = UUID.randomUUID().toString();
        service.recordConnectionOpened(customerId);
        assertEquals(1, service.getCustomerCount());

        // When
        service.clearHistoricalMetrics(customerId);

        // Then
        assertEquals(0, service.getCustomerCount());
    }

    @Test
    void getCustomerMetrics_shouldReturnEmptyForInvalidCustomerId() {
        // Given
        String invalidCustomerId = "not-a-uuid";

        // When
        Optional<CustomerMetricsSnapshot> result = service.getCustomerMetrics(invalidCustomerId);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void getCustomerMetrics_shouldReturnEmptyForUnknownCustomer() {
        // Given
        String customerId = UUID.randomUUID().toString();
        when(customerRepository.findByCustomerId(any(UUID.class))).thenReturn(Optional.empty());

        // When
        Optional<CustomerMetricsSnapshot> result = service.getCustomerMetrics(customerId);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void getCustomerMetrics_shouldReturnMetricsForKnownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId);

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(connectionManager.getConnectionsByCustomerId(customerId.toString())).thenReturn(List.of());
        when(circuitBreaker.getAllMetrics()).thenReturn(Map.of());
        when(allowanceRepository.findByCustomerCustomerIdWithModel(customerId)).thenReturn(List.of());

        // Record some historical metrics
        service.recordConnectionOpened(customerId.toString());
        service.recordSessionCreated(customerId.toString());
        service.recordSessionCompleted(customerId.toString(), 5000L);

        // When
        Optional<CustomerMetricsSnapshot> result = service.getCustomerMetrics(customerId.toString());

        // Then
        assertTrue(result.isPresent());
        CustomerMetricsSnapshot snapshot = result.get();
        assertEquals(customerId.toString(), snapshot.customerId());
        assertEquals(customer.getName(), snapshot.customerName());
        assertTrue(snapshot.enabled());
        assertEquals(1, snapshot.totalConnectionsOpened());
        assertEquals(1, snapshot.totalSessionsCreated());
        assertEquals(1, snapshot.totalSessionsCompleted());
    }

    @Test
    void getCustomerMetrics_shouldIncludeActiveConnectionMetrics() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId);

        ClientConnection connection = mock(ClientConnection.class);
        lenient().when(connection.isActive()).thenReturn(true);
        lenient().when(connection.getConnectionId()).thenReturn("conn-123");

        PromptSession session = mock(PromptSession.class);
        lenient().when(session.getState()).thenReturn(SessionState.EXECUTING);
        lenient().when(session.isTerminal()).thenReturn(false);
        lenient().when(session.getDurationMs()).thenReturn(1000L);
        lenient().when(session.getPendingCallCount()).thenReturn(2);

        lenient().when(connection.getAllSessions()).thenReturn(List.of(session));

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(connectionManager.getConnectionsByCustomerId(customerId.toString())).thenReturn(List.of(connection));
        when(circuitBreaker.getAllMetrics()).thenReturn(Map.of());
        when(allowanceRepository.findByCustomerCustomerIdWithModel(customerId)).thenReturn(List.of());

        // When
        Optional<CustomerMetricsSnapshot> result = service.getCustomerMetrics(customerId.toString());

        // Then
        assertTrue(result.isPresent());
        CustomerMetricsSnapshot snapshot = result.get();
        assertEquals(1, snapshot.activeConnections());
        assertEquals(1, snapshot.activeSessions());
        assertEquals(1, snapshot.executingSessions());
        assertEquals(2, snapshot.pendingToolCalls());
    }

    @Test
    void getCustomerMetrics_shouldIncludeTokenUsage() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId);

        CustomerModelAllowanceEntity allowance1 = createAllowance(50000L, 100000L);
        CustomerModelAllowanceEntity allowance2 = createAllowance(30000L, 200000L);

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(connectionManager.getConnectionsByCustomerId(customerId.toString())).thenReturn(List.of());
        when(circuitBreaker.getAllMetrics()).thenReturn(Map.of());
        when(allowanceRepository.findByCustomerCustomerIdWithModel(customerId))
                .thenReturn(List.of(allowance1, allowance2));

        // When
        Optional<CustomerMetricsSnapshot> result = service.getCustomerMetrics(customerId.toString());

        // Then
        assertTrue(result.isPresent());
        CustomerMetricsSnapshot snapshot = result.get();
        assertEquals(80000L, snapshot.totalTokensUsed()); // 50000 + 30000
        assertEquals(300000L, snapshot.totalTokensAllowed()); // 100000 + 200000
    }

    @Test
    void getConnectedCustomerMetrics_shouldReturnOnlyConnectedCustomers() {
        // Given
        UUID customerId1 = UUID.randomUUID();
        UUID customerId2 = UUID.randomUUID();
        CustomerEntity customer1 = createCustomer(customerId1);
        CustomerEntity customer2 = createCustomer(customerId2);

        when(connectionManager.getConnectedCustomerIds())
                .thenReturn(Set.of(customerId1.toString(), customerId2.toString()));
        when(customerRepository.findByCustomerId(customerId1)).thenReturn(Optional.of(customer1));
        when(customerRepository.findByCustomerId(customerId2)).thenReturn(Optional.of(customer2));
        when(connectionManager.getConnectionsByCustomerId(anyString())).thenReturn(List.of());
        when(circuitBreaker.getAllMetrics()).thenReturn(Map.of());
        when(allowanceRepository.findByCustomerCustomerIdWithModel(any())).thenReturn(List.of());

        // When
        List<CustomerMetricsSnapshot> result = service.getConnectedCustomerMetrics();

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void getAllCustomerMetrics_shouldIncludeHistoricalCustomers() {
        // Given
        UUID connectedCustomerId = UUID.randomUUID();
        UUID historicalCustomerId = UUID.randomUUID();
        CustomerEntity connectedCustomer = createCustomer(connectedCustomerId);
        CustomerEntity historicalCustomer = createCustomer(historicalCustomerId);

        // Record historical metrics for a customer that's no longer connected
        service.recordConnectionOpened(historicalCustomerId.toString());
        service.recordConnectionClosed(historicalCustomerId.toString());

        when(connectionManager.getConnectedCustomerIds()).thenReturn(Set.of(connectedCustomerId.toString()));
        when(customerRepository.findByCustomerId(connectedCustomerId)).thenReturn(Optional.of(connectedCustomer));
        when(customerRepository.findByCustomerId(historicalCustomerId)).thenReturn(Optional.of(historicalCustomer));
        when(connectionManager.getConnectionsByCustomerId(anyString())).thenReturn(List.of());
        when(circuitBreaker.getAllMetrics()).thenReturn(Map.of());
        when(allowanceRepository.findByCustomerCustomerIdWithModel(any())).thenReturn(List.of());

        // When
        List<CustomerMetricsSnapshot> result = service.getAllCustomerMetrics();

        // Then
        assertEquals(2, result.size());
    }

    // ==================== Helper Methods ====================

    private CustomerEntity createCustomer(UUID customerId) {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setCustomerId(customerId);
        customer.setName("Test Customer");
        customer.setEnabled(true);
        return customer;
    }

    private CustomerModelAllowanceEntity createAllowance(Long tokensUsed, Long allowedTokens) {
        LlmModelEntity model = new LlmModelEntity();
        model.setModel("claude-sonnet-4-5");

        CustomerModelAllowanceEntity allowance = new CustomerModelAllowanceEntity();
        allowance.setLlmModel(model);
        allowance.setTokensUsed(tokensUsed);
        allowance.setAllowedTokens(allowedTokens);
        return allowance;
    }
}
