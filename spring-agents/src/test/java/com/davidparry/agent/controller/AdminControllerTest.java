package com.davidparry.agent.controller;

import com.davidparry.agent.dto.*;
import com.davidparry.agent.entity.*;
import com.davidparry.agent.repository.*;
import com.davidparry.agent.service.*;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminController.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;
    
    @Mock
    private CustomerRepository customerRepository;
    
    @Mock
    private CustomerTokenRepository tokenRepository;
    
    @Mock
    private LlmModelRepository modelRepository;
    
    @Mock
    private LlmModelService llmModelService;
    
    @Mock
    private PolicyTypeRepository policyTypeRepository;
    
    @Mock
    private CustomerModelAllowanceRepository allowanceRepository;
    
    @Mock
    private SecurityAuditLogRepository auditLogRepository;
    
    @Mock
    private SecurityAuditService auditService;
    
    @Mock
    private CustomerUsageService customerUsageService;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private AgentConfigRepository agentConfigRepository;
    
    @Mock
    private CustomerAgentTypeRepository customerAgentTypeRepository;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(
            adminService,
            customerRepository,
            tokenRepository,
            modelRepository,
            llmModelService,
            policyTypeRepository,
            allowanceRepository,
            auditLogRepository,
            auditService,
            customerUsageService,
            connectionManager,
            agentConfigRepository,
            customerAgentTypeRepository
        );
    }

    // ==================== Customer Management Tests ====================

    @Test
    void listCustomers_shouldReturnPagedCustomers() {
        // Given
        CustomerEntity customer = createCustomer(UUID.randomUUID(), "Test Customer");
        Page<CustomerEntity> page = new PageImpl<>(List.of(customer));
        
        when(customerRepository.findAll(any(PageRequest.class))).thenReturn(page);
        when(adminService.getActiveTokenCount(customer.getId())).thenReturn(2);
        when(adminService.getAllowanceCount(customer.getId())).thenReturn(5);

        // When
        ResponseEntity<Page<AdminCustomerSummaryResponse>> response = 
            controller.listCustomers(0, 20, null);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("Test Customer", response.getBody().getContent().get(0).name());
    }

    @Test
    void listCustomers_shouldFilterByEnabled() {
        // Given
        CustomerEntity customer = createCustomer(UUID.randomUUID(), "Enabled Customer");
        Page<CustomerEntity> page = new PageImpl<>(List.of(customer));
        
        when(customerRepository.findByEnabled(eq(true), any(PageRequest.class))).thenReturn(page);
        when(adminService.getActiveTokenCount(customer.getId())).thenReturn(1);
        when(adminService.getAllowanceCount(customer.getId())).thenReturn(3);

        // When
        ResponseEntity<Page<AdminCustomerSummaryResponse>> response = 
            controller.listCustomers(0, 20, true);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(customerRepository).findByEnabled(eq(true), any(PageRequest.class));
    }

    @Test
    void getCustomer_shouldReturnCustomerDetails() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(customerUsageService.getCustomerAllowancesWithPolicyType(customerId)).thenReturn(List.of());

        // When
        ResponseEntity<CustomerResponse> response = controller.getCustomer(customerId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Test Customer", response.getBody().name());
    }

    @Test
    void getCustomer_shouldReturn404ForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        // When
        ResponseEntity<CustomerResponse> response = controller.getCustomer(customerId);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void disableCustomer_shouldDisableAndCascade() {
        // Given
        UUID customerId = UUID.randomUUID();
        AdminService.CustomerDisableResult result = 
            new AdminService.CustomerDisableResult(customerId, 3, 5);
        
        when(adminService.disableCustomer(customerId)).thenReturn(result);

        // When
        ResponseEntity<CustomerDisableResponse> response = controller.disableCustomer(customerId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().disabled());
        assertEquals(3, response.getBody().tokensRevoked());
        assertEquals(5, response.getBody().allowancesDisabled());
    }

    @Test
    void disableCustomer_shouldReturn404ForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(adminService.disableCustomer(customerId))
            .thenThrow(new IllegalArgumentException("Customer not found"));

        // When
        ResponseEntity<CustomerDisableResponse> response = controller.disableCustomer(customerId);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void enableCustomer_shouldEnableCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        doNothing().when(adminService).enableCustomer(customerId);

        // When
        ResponseEntity<Map<String, Object>> response = controller.enableCustomer(customerId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(customerId, response.getBody().get("customerId"));
        assertEquals(true, response.getBody().get("enabled"));
    }

    @Test
    void getTokenStatistics_shouldReturnStats() {
        // Given
        List<AdminService.TokenVersionStatistics> stats = List.of(
            new AdminService.TokenVersionStatistics("V1", 10),
            new AdminService.TokenVersionStatistics("V2", 5)
        );
        when(adminService.getTokenStatistics()).thenReturn(stats);

        // When
        ResponseEntity<List<TokenStatisticsResponse>> response = controller.getTokenStatistics();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals("V1", response.getBody().get(0).secretVersion());
        assertEquals(10, response.getBody().get(0).activeTokenCount());
    }

    // ==================== Model Management Tests ====================

    @Test
    void listAllModels_shouldReturnAllModels() {
        // Given
        LlmModelEntity model = createLlmModel("claude-sonnet-4-5");
        when(modelRepository.findAll()).thenReturn(List.of(model));

        // When
        ResponseEntity<List<ModelResponse>> response = controller.listAllModels();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("claude-sonnet-4-5", response.getBody().get(0).model());
    }

    @Test
    void updateModel_shouldUpdateModelDetails() {
        // Given
        String modelId = "claude-sonnet-4-5";
        LlmModelEntity model = createLlmModel(modelId);
        
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any(LlmModelEntity.class))).thenReturn(model);

        UpdateModelRequest request = new UpdateModelRequest(
            "New Display Name", "New description", 50000L, null, null, null);

        // When
        ResponseEntity<ModelResponse> response = controller.updateModel(modelId, request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auditService).logAdminModelAction(
            eq(SecurityAuditLogEntity.EventType.ADMIN_MODEL_UPDATED),
            eq(modelId),
            anyString()
        );
    }

    @Test
    void updateModel_shouldReturn404ForUnknownModel() {
        // Given
        when(modelRepository.findById("unknown")).thenReturn(Optional.empty());

        // When
        ResponseEntity<ModelResponse> response = 
            controller.updateModel("unknown", new UpdateModelRequest(null, null, null, null, null, null));

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void disableModel_shouldDisableModel() {
        // Given
        String modelId = "claude-sonnet-4-5";
        LlmModelEntity model = createLlmModel(modelId);
        
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any(LlmModelEntity.class))).thenReturn(model);

        // When
        ResponseEntity<ModelResponse> response = controller.disableModel(modelId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auditService).logAdminModelAction(
            eq(SecurityAuditLogEntity.EventType.ADMIN_MODEL_DISABLED),
            eq(modelId),
            anyString()
        );
    }

    // ==================== Policy Type Management Tests ====================

    @Test
    void listAllPolicyTypes_shouldReturnAllPolicyTypes() {
        // Given
        PolicyTypeEntity policyType = createPolicyType("MONTHLY", 30);
        when(policyTypeRepository.findAll()).thenReturn(List.of(policyType));

        // When
        ResponseEntity<List<PolicyTypeResponse>> response = controller.listAllPolicyTypes();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("MONTHLY", response.getBody().get(0).name());
    }

    @Test
    void createPolicyType_shouldCreateNewPolicyType() {
        // Given
        CreatePolicyTypeRequest request = new CreatePolicyTypeRequest("WEEKLY", "Weekly reset", 7);
        PolicyTypeEntity policyType = createPolicyType("WEEKLY", 7);
        
        when(policyTypeRepository.existsByName("WEEKLY")).thenReturn(false);
        when(policyTypeRepository.save(any(PolicyTypeEntity.class))).thenReturn(policyType);

        // When
        ResponseEntity<PolicyTypeResponse> response = controller.createPolicyType(request);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("WEEKLY", response.getBody().name());
        verify(auditService).logAdminAction(
            eq(SecurityAuditLogEntity.EventType.ADMIN_POLICY_TYPE_CREATED),
            anyString(),
            isNull()
        );
    }

    @Test
    void createPolicyType_shouldReturn409ForDuplicate() {
        // Given
        CreatePolicyTypeRequest request = new CreatePolicyTypeRequest("MONTHLY", "Duplicate", 30);
        when(policyTypeRepository.existsByName("MONTHLY")).thenReturn(true);

        // When
        ResponseEntity<PolicyTypeResponse> response = controller.createPolicyType(request);

        // Then
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void createPolicyType_shouldReturn400ForEmptyName() {
        // Given
        CreatePolicyTypeRequest request = new CreatePolicyTypeRequest("", "Description", 30);

        // When
        ResponseEntity<PolicyTypeResponse> response = controller.createPolicyType(request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updatePolicyType_shouldUpdatePolicyType() {
        // Given
        UUID id = UUID.randomUUID();
        PolicyTypeEntity policyType = createPolicyType("MONTHLY", 30);
        policyType.setId(id);
        
        when(policyTypeRepository.findById(id)).thenReturn(Optional.of(policyType));
        when(policyTypeRepository.save(any(PolicyTypeEntity.class))).thenReturn(policyType);

        UpdatePolicyTypeRequest request = new UpdatePolicyTypeRequest("Updated description", 31, null);

        // When
        ResponseEntity<PolicyTypeResponse> response = controller.updatePolicyType(id, request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auditService).logAdminAction(
            eq(SecurityAuditLogEntity.EventType.ADMIN_POLICY_TYPE_UPDATED),
            anyString(),
            isNull()
        );
    }

    @Test
    void disablePolicyType_shouldDisablePolicyType() {
        // Given
        UUID id = UUID.randomUUID();
        PolicyTypeEntity policyType = createPolicyType("MONTHLY", 30);
        policyType.setId(id);
        
        when(policyTypeRepository.findById(id)).thenReturn(Optional.of(policyType));
        when(policyTypeRepository.save(any(PolicyTypeEntity.class))).thenReturn(policyType);

        // When
        ResponseEntity<PolicyTypeResponse> response = controller.disablePolicyType(id);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auditService).logAdminAction(
            eq(SecurityAuditLogEntity.EventType.ADMIN_POLICY_TYPE_DISABLED),
            anyString(),
            isNull()
        );
    }

    // ==================== Allowance Management Tests ====================

    @Test
    void getAllowancesByModel_shouldReturnAllowances() {
        // Given
        String modelId = "claude-sonnet-4-5";
        LlmModelEntity model = createLlmModel(modelId);
        CustomerEntity customer = createCustomer(UUID.randomUUID(), "Test Customer");
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, 100000L, 5000L);
        
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(allowanceRepository.findAllByModelWithCustomer(modelId)).thenReturn(List.of(allowance));

        // When
        ResponseEntity<AllowancesByModelResponse> response = controller.getAllowancesByModel(modelId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(modelId, response.getBody().model());
        assertEquals(1, response.getBody().totalAllowances());
    }

    @Test
    void getAllowancesByModel_shouldReturn404ForUnknownModel() {
        // Given
        when(modelRepository.findById("unknown")).thenReturn(Optional.empty());

        // When
        ResponseEntity<AllowancesByModelResponse> response = controller.getAllowancesByModel("unknown");

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void bulkUpdateAllowances_shouldUpdateAllowances() {
        // Given
        BulkAllowanceUpdateRequest request = new BulkAllowanceUpdateRequest(
            "claude-sonnet-4-5", 100000L, null);
        
        when(modelRepository.existsById("claude-sonnet-4-5")).thenReturn(true);
        when(allowanceRepository.bulkUpdateAllowanceForModel(
            eq("claude-sonnet-4-5"), eq(100000L), any(LocalDateTime.class))).thenReturn(10);

        // When
        ResponseEntity<BulkUpdateResponse> response = controller.bulkUpdateAllowances(request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10, response.getBody().customersUpdated());
        verify(auditService).logAdminAction(
            eq(SecurityAuditLogEntity.EventType.ADMIN_ALLOWANCE_BULK_UPDATED),
            anyString(),
            isNull()
        );
    }

    @Test
    void bulkUpdateAllowances_shouldReturn404ForUnknownModel() {
        // Given
        BulkAllowanceUpdateRequest request = new BulkAllowanceUpdateRequest("unknown", 100000L, null);
        when(modelRepository.existsById("unknown")).thenReturn(false);

        // When
        ResponseEntity<BulkUpdateResponse> response = controller.bulkUpdateAllowances(request);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void resetAllUsageForModel_shouldResetUsage() {
        // Given
        String modelId = "claude-sonnet-4-5";
        when(modelRepository.existsById(modelId)).thenReturn(true);
        when(allowanceRepository.resetUsageForModel(eq(modelId), any(LocalDateTime.class))).thenReturn(15);

        // When
        ResponseEntity<UsageResetResponse> response = controller.resetAllUsageForModel(modelId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(15, response.getBody().allowancesReset());
        verify(auditService).logAdminAction(
            eq(SecurityAuditLogEntity.EventType.ADMIN_USAGE_RESET),
            anyString(),
            isNull()
        );
    }

    // ==================== Audit Log Tests ====================

    @Test
    void queryAuditLogs_shouldReturnPagedLogs() {
        // Given
        SecurityAuditLogEntity log = createAuditLog(
            SecurityAuditLogEntity.EventType.TOKEN_CREATED,
            SecurityAuditLogEntity.EventCategory.TOKEN
        );
        Page<SecurityAuditLogEntity> page = new PageImpl<>(List.of(log));
        
        when(auditLogRepository.findByFilters(
            isNull(), isNull(), isNull(), isNull(), isNull(), any(PageRequest.class)))
            .thenReturn(page);

        // When
        ResponseEntity<Page<AuditLogResponse>> response = 
            controller.queryAuditLogs(null, null, null, null, null, 0, 50);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void queryAuditLogs_shouldReturn400ForInvalidEventType() {
        // When
        ResponseEntity<Page<AuditLogResponse>> response = 
            controller.queryAuditLogs(null, "INVALID_TYPE", null, null, null, 0, 50);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getAuditStatistics_shouldReturnStats() {
        // Given
        List<Object[]> counts = List.of(
            new Object[]{SecurityAuditLogEntity.EventType.TOKEN_CREATED, 10L},
            new Object[]{SecurityAuditLogEntity.EventType.TOKEN_VALIDATED, 100L}
        );
        when(auditLogRepository.countEventsByTypeSince(any(LocalDateTime.class))).thenReturn(counts);

        // When
        ResponseEntity<AuditStatisticsResponse> response = controller.getAuditStatistics(null);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(110, response.getBody().totalEvents());
        assertEquals(2, response.getBody().eventCountsByType().size());
    }

    @Test
    void cleanupAuditLogs_shouldDeleteOldLogs() {
        // Given
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        AuditCleanupRequest request = new AuditCleanupRequest(cutoff);
        
        when(auditLogRepository.deleteByCreatedAtBefore(cutoff)).thenReturn(100);

        // When
        ResponseEntity<AuditCleanupResponse> response = controller.cleanupAuditLogs(request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(100, response.getBody().deletedCount());
        verify(auditService).logAdminAction(
            eq(SecurityAuditLogEntity.EventType.ADMIN_AUDIT_CLEANUP),
            anyString(),
            isNull()
        );
    }

    @Test
    void cleanupAuditLogs_shouldReturn400ForNullDate() {
        // Given
        AuditCleanupRequest request = new AuditCleanupRequest(null);

        // When
        ResponseEntity<AuditCleanupResponse> response = controller.cleanupAuditLogs(request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ==================== System Statistics Tests ====================

    @Test
    void getSystemStatistics_shouldReturnStats() {
        // Given
        AdminService.SystemStatistics stats = new AdminService.SystemStatistics(
            100, 95, 10, 8, 3, 500, 10000);
        when(adminService.getSystemStatistics()).thenReturn(stats);

        // When
        ResponseEntity<SystemStatisticsResponse> response = controller.getSystemStatistics();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(100, response.getBody().totalCustomers());
        assertEquals(95, response.getBody().enabledCustomers());
        assertEquals(10, response.getBody().totalModels());
        assertEquals(8, response.getBody().enabledModels());
    }

    // ==================== Customer Connection Status Tests ====================

    @Test
    void getCustomerConnectionStatus_shouldReturnConnectedStatus() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        ClientConnection mockConnection = mock(ClientConnection.class);
        
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(connectionManager.getConnectionsByCustomerId(customerId.toString()))
            .thenReturn(List.of(mockConnection));
        when(mockConnection.isActive()).thenReturn(true);
        when(mockConnection.getConnectionId()).thenReturn("conn-12345678");
        when(mockConnection.getConnectedAt()).thenReturn(Instant.now().minusSeconds(300));
        when(mockConnection.getLastActivityAt()).thenReturn(Instant.now().minusSeconds(10));
        when(mockConnection.getActiveSessionCount()).thenReturn(2);
        when(mockConnection.getTotalSessionsCreated()).thenReturn(5L);
        when(mockConnection.getTotalToolCalls()).thenReturn(10L);
        when(mockConnection.getDurationMs()).thenReturn(300000L);
        when(mockConnection.getIdleTimeMs()).thenReturn(10000L);

        // When
        ResponseEntity<CustomerConnectionStatusResponse> response = 
            controller.getCustomerConnectionStatus(customerId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().connected());
        assertEquals(1, response.getBody().activeConnectionCount());
        assertEquals(2, response.getBody().totalActiveSessions());
        assertEquals("Test Customer", response.getBody().customerName());
    }

    @Test
    void getCustomerConnectionStatus_shouldReturnNotConnectedStatus() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(connectionManager.getConnectionsByCustomerId(customerId.toString()))
            .thenReturn(List.of());

        // When
        ResponseEntity<CustomerConnectionStatusResponse> response = 
            controller.getCustomerConnectionStatus(customerId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().connected());
        assertEquals(0, response.getBody().activeConnectionCount());
        assertEquals(0, response.getBody().totalActiveSessions());
    }

    @Test
    void getCustomerConnectionStatus_shouldReturn404ForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        // When
        ResponseEntity<CustomerConnectionStatusResponse> response = 
            controller.getCustomerConnectionStatus(customerId);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ==================== Helper Methods ====================

    private CustomerEntity createCustomer(UUID customerId, String name) {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setCustomerId(customerId);
        customer.setName(name);
        customer.setEnabled(true);
        return customer;
    }

    private LlmModelEntity createLlmModel(String model) {
        LlmModelEntity llmModel = new LlmModelEntity();
        llmModel.setModel(model);
        llmModel.setProvider("anthropic");
        llmModel.setDisplayName("Claude Sonnet 4.5");
        llmModel.setEnabled(true);
        return llmModel;
    }

    private PolicyTypeEntity createPolicyType(String name, Integer resetDays) {
        PolicyTypeEntity policyType = new PolicyTypeEntity();
        policyType.setId(UUID.randomUUID());
        policyType.setName(name);
        policyType.setDescription(name + " policy");
        policyType.setResetDays(resetDays);
        policyType.setEnabled(true);
        return policyType;
    }

    private CustomerModelAllowanceEntity createAllowance(CustomerEntity customer, LlmModelEntity model,
                                                          Long allowedTokens, Long tokensUsed) {
        CustomerModelAllowanceEntity allowance = new CustomerModelAllowanceEntity();
        allowance.setId(UUID.randomUUID());
        allowance.setCustomer(customer);
        allowance.setLlmModel(model);
        allowance.setAllowedTokens(allowedTokens);
        allowance.setTokensUsed(tokensUsed);
        allowance.setTokensResetAt(LocalDateTime.now());
        allowance.setEnabled(true);
        return allowance;
    }

    private SecurityAuditLogEntity createAuditLog(SecurityAuditLogEntity.EventType eventType,
                                                   SecurityAuditLogEntity.EventCategory category) {
        return SecurityAuditLogEntity.builder()
            .eventType(eventType)
            .eventCategory(category)
            .description("Test audit log")
            .actorType(SecurityAuditLogEntity.ActorType.SYSTEM)
            .build();
    }
}
