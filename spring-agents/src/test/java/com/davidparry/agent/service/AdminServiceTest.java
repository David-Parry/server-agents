package com.davidparry.agent.service;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.SecurityAuditLogEntity;
import com.davidparry.agent.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminService.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerTokenRepository tokenRepository;

    @Mock
    private CustomerModelAllowanceRepository allowanceRepository;

    @Mock
    private LlmModelRepository modelRepository;

    @Mock
    private PolicyTypeRepository policyTypeRepository;

    @Mock
    private SecurityAuditLogRepository auditLogRepository;

    @Mock
    private SecurityAuditService auditService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                customerRepository,
                tokenRepository,
                allowanceRepository,
                modelRepository,
                policyTypeRepository,
                auditLogRepository,
                auditService
        );
    }

    @Test
    void disableCustomer_shouldDisableCustomerAndRevokeTokens() {
        // Given
        UUID customerId = UUID.randomUUID();
        UUID internalId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, internalId);

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(tokenRepository.revokeAllTokensForCustomer(eq(internalId), any(LocalDateTime.class))).thenReturn(3);
        when(allowanceRepository.disableAllForCustomer(eq(internalId), any(LocalDateTime.class))).thenReturn(2);
        when(customerRepository.save(any(CustomerEntity.class))).thenReturn(customer);

        // When
        AdminService.CustomerDisableResult result = adminService.disableCustomer(customerId);

        // Then
        assertEquals(customerId, result.customerId());
        assertEquals(3, result.tokensRevoked());
        assertEquals(2, result.allowancesDisabled());
        assertFalse(customer.isEnabled());
        verify(auditService).logAdminCustomerAction(
                eq(SecurityAuditLogEntity.EventType.ADMIN_CUSTOMER_DISABLED),
                eq(customerId),
                anyString()
        );
    }

    @Test
    void disableCustomer_shouldThrowWhenCustomerNotFound() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> adminService.disableCustomer(customerId));
    }

    @Test
    void enableCustomer_shouldEnableCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, UUID.randomUUID());
        customer.setEnabled(false);

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(CustomerEntity.class))).thenReturn(customer);

        // When
        adminService.enableCustomer(customerId);

        // Then
        assertTrue(customer.isEnabled());
        verify(auditService).logAdminCustomerAction(
                eq(SecurityAuditLogEntity.EventType.ADMIN_CUSTOMER_ENABLED),
                eq(customerId),
                eq("Customer enabled")
        );
    }

    @Test
    void enableCustomer_shouldThrowWhenCustomerNotFound() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> adminService.enableCustomer(customerId));
    }

    @Test
    void enableCustomerAllowances_shouldEnableAllAllowances() {
        // Given
        UUID customerId = UUID.randomUUID();
        UUID internalId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, internalId);

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(allowanceRepository.enableAllForCustomer(eq(internalId), any(LocalDateTime.class))).thenReturn(5);

        // When
        int result = adminService.enableCustomerAllowances(customerId);

        // Then
        assertEquals(5, result);
        verify(auditService).logAdminCustomerAction(
                eq(SecurityAuditLogEntity.EventType.ADMIN_CUSTOMER_ENABLED),
                eq(customerId),
                contains("Re-enabled 5 allowances")
        );
    }

    @Test
    void enableCustomerAllowances_shouldThrowWhenCustomerNotFound() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> adminService.enableCustomerAllowances(customerId));
    }

    @Test
    void getSystemStatistics_shouldReturnAllStatistics() {
        // Given
        when(customerRepository.count()).thenReturn(100L);
        when(customerRepository.countByEnabledTrue()).thenReturn(95L);
        when(modelRepository.count()).thenReturn(10L);
        when(modelRepository.countByEnabledTrue()).thenReturn(8L);
        when(policyTypeRepository.count()).thenReturn(5L);
        when(allowanceRepository.count()).thenReturn(500L);
        when(auditLogRepository.count()).thenReturn(10000L);

        // When
        AdminService.SystemStatistics stats = adminService.getSystemStatistics();

        // Then
        assertEquals(100L, stats.totalCustomers());
        assertEquals(95L, stats.enabledCustomers());
        assertEquals(10L, stats.totalModels());
        assertEquals(8L, stats.enabledModels());
        assertEquals(5L, stats.totalPolicyTypes());
        assertEquals(500L, stats.totalAllowances());
        assertEquals(10000L, stats.totalAuditLogs());
    }

    @Test
    void getTokenStatistics_shouldReturnStatsByVersion() {
        // Given
        Object[] row1 = new Object[]{"V1", 50L};
        Object[] row2 = new Object[]{"V2", 30L};
        when(tokenRepository.countActiveTokensBySecretVersion(any(LocalDateTime.class)))
                .thenReturn(List.of(row1, row2));

        // When
        List<AdminService.TokenVersionStatistics> stats = adminService.getTokenStatistics();

        // Then
        assertEquals(2, stats.size());
        assertEquals("V1", stats.get(0).secretVersion());
        assertEquals(50L, stats.get(0).activeTokenCount());
        assertEquals("V2", stats.get(1).secretVersion());
        assertEquals(30L, stats.get(1).activeTokenCount());
    }

    @Test
    void getActiveTokenCount_shouldReturnCount() {
        // Given
        UUID internalId = UUID.randomUUID();
        when(tokenRepository.findActiveTokensByCustomerId(eq(internalId), any(LocalDateTime.class)))
                .thenReturn(List.of(mock(com.davidparry.agent.entity.CustomerTokenEntity.class),
                        mock(com.davidparry.agent.entity.CustomerTokenEntity.class)));

        // When
        int count = adminService.getActiveTokenCount(internalId);

        // Then
        assertEquals(2, count);
    }

    @Test
    void getAllowanceCount_shouldReturnCount() {
        // Given
        UUID internalId = UUID.randomUUID();
        when(allowanceRepository.findByCustomerIdWithModel(internalId))
                .thenReturn(List.of(
                        mock(com.davidparry.agent.entity.CustomerModelAllowanceEntity.class),
                        mock(com.davidparry.agent.entity.CustomerModelAllowanceEntity.class),
                        mock(com.davidparry.agent.entity.CustomerModelAllowanceEntity.class)
                ));

        // When
        int count = adminService.getAllowanceCount(internalId);

        // Then
        assertEquals(3, count);
    }

    // ==================== Helper Methods ====================

    private CustomerEntity createCustomer(UUID customerId, UUID internalId) {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(internalId);
        customer.setCustomerId(customerId);
        customer.setName("Test Customer");
        customer.setEnabled(true);
        return customer;
    }

    private static String contains(String substring) {
        return argThat(s -> s != null && s.contains(substring));
    }
}
