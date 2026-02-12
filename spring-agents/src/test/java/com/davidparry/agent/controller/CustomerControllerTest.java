package com.davidparry.agent.controller;

import com.davidparry.agent.dto.*;
import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import com.davidparry.agent.security.JwtTokenService;
import com.davidparry.agent.service.CustomerTokenService;
import com.davidparry.agent.service.CustomerUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomerController.
 */
@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerTokenService customerTokenService;
    
    @Mock
    private CustomerUsageService customerUsageService;
    
    @Mock
    private CustomerRepository customerRepository;
    
    @Mock
    private PolicyTypeRepository policyTypeRepository;
    
    @Mock
    private JwtTokenService jwtTokenService;

    private CustomerController controller;

    @BeforeEach
    void setUp() {
        controller = new CustomerController(
            customerTokenService, 
            customerUsageService,
            customerRepository, 
            policyTypeRepository, 
            jwtTokenService
        );
    }

    @Test
    void createCustomer_shouldCreateCustomerWithDefaultUnlimitedPolicy() {
        // Given
        String customerName = "Test Customer";
        String mockJwt = "mock.jwt.token";
        UUID customerId = UUID.randomUUID();
        
        PolicyTypeEntity policyType = createUnlimitedPolicyType();
        CustomerEntity customer = createCustomer(customerId, customerName);
        
        when(policyTypeRepository.findUnlimitedPolicyType()).thenReturn(Optional.of(policyType));
        when(jwtTokenService.generateToken(any(UUID.class), eq(customerName))).thenReturn(mockJwt);
        when(customerTokenService.createCustomerWithId(any(UUID.class), eq(customerName), eq(policyType), isNull(), eq(false), eq(mockJwt)))
            .thenReturn(customer);

        // When
        ResponseEntity<CustomerCreatedResponse> response = 
            controller.createCustomer(new CreateCustomerRequest(customerName, null, null, null));

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(customerName, response.getBody().name());
        assertEquals("UNLIMITED", response.getBody().policyTypeName());
        assertEquals(mockJwt, response.getBody().apiToken());
    }

    @Test
    void createCustomer_shouldCreateCustomerWithSpecifiedPolicyType() {
        // Given
        String customerName = "Test Customer";
        String policyTypeName = "MONTHLY";
        String mockJwt = "mock.jwt.token";
        UUID customerId = UUID.randomUUID();
        
        PolicyTypeEntity policyType = createMonthlyPolicyType();
        CustomerEntity customer = createCustomer(customerId, customerName);
        
        when(policyTypeRepository.findByName(policyTypeName)).thenReturn(Optional.of(policyType));
        when(jwtTokenService.generateToken(any(UUID.class), eq(customerName))).thenReturn(mockJwt);
        when(customerTokenService.createCustomerWithId(any(UUID.class), eq(customerName), eq(policyType), isNull(), eq(false), eq(mockJwt)))
            .thenReturn(customer);

        // When
        ResponseEntity<CustomerCreatedResponse> response = 
            controller.createCustomer(new CreateCustomerRequest(customerName, policyTypeName, null, null));

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MONTHLY", response.getBody().policyTypeName());
        assertEquals(30, response.getBody().resetDays());
    }

    @Test
    void createCustomer_shouldCreateCustomerWithUnlimitedFlag() {
        // Given
        String customerName = "Test Customer";
        String mockJwt = "mock.jwt.token";
        UUID customerId = UUID.randomUUID();
        
        PolicyTypeEntity policyType = createUnlimitedPolicyType();
        CustomerEntity customer = createCustomer(customerId, customerName);
        
        when(policyTypeRepository.findUnlimitedPolicyType()).thenReturn(Optional.of(policyType));
        when(jwtTokenService.generateToken(any(UUID.class), eq(customerName))).thenReturn(mockJwt);
        when(customerTokenService.createCustomerWithId(any(UUID.class), eq(customerName), eq(policyType), isNull(), eq(true), eq(mockJwt)))
            .thenReturn(customer);

        // When
        ResponseEntity<CustomerCreatedResponse> response = 
            controller.createCustomer(new CreateCustomerRequest(customerName, null, null, true));

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().unlimited());
    }

    @Test
    void createCustomer_shouldRejectEmptyName() {
        // When
        ResponseEntity<CustomerCreatedResponse> response = 
            controller.createCustomer(new CreateCustomerRequest("", null, null, null));

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createCustomer_shouldRejectNullName() {
        // When
        ResponseEntity<CustomerCreatedResponse> response = 
            controller.createCustomer(new CreateCustomerRequest(null, null, null, null));

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getCustomer_shouldReturnCustomerWithAllowances() {
        // Given
        UUID customerId = UUID.randomUUID();
        PolicyTypeEntity policyType = createMonthlyPolicyType();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        
        LlmModelEntity model = createLlmModel("claude-sonnet-4-5");
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, 100000L, 5000L);
        allowance.setPolicyType(policyType);
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(customerUsageService.getCustomerAllowancesWithPolicyType(customerId))
            .thenReturn(List.of(allowance));

        // When
        ResponseEntity<CustomerResponse> response = controller.getCustomer(customerId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Test Customer", response.getBody().name());
        assertEquals(1, response.getBody().modelAllowances().size());
        assertEquals("claude-sonnet-4-5", response.getBody().modelAllowances().get(0).model());
        assertEquals(100000L, response.getBody().modelAllowances().get(0).allowedTokens());
        assertEquals(5000L, response.getBody().modelAllowances().get(0).tokensUsed());
    }

    @Test
    void getCustomer_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.empty());

        // When
        ResponseEntity<CustomerResponse> response = controller.getCustomer(customerId);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void setAllowance_shouldUpdateAllowanceForModel() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        Long newAllowance = 200000L;
        
        PolicyTypeEntity policyType = createMonthlyPolicyType();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        LlmModelEntity llmModel = createLlmModel(model);
        CustomerModelAllowanceEntity allowance = createAllowance(customer, llmModel, newAllowance, 0L);
        allowance.setPolicyType(policyType);
        
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(customerUsageService.setAllowance(customerId, model, newAllowance))
            .thenReturn(allowance);

        // When
        ResponseEntity<ModelAllowanceResponse> response = 
            controller.setAllowance(customerId, model, new SetAllowanceRequest(newAllowance, null));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(newAllowance, response.getBody().allowedTokens());
    }

    @Test
    void setBulkAllowance_shouldSetUnlimitedForAllModels() {
        // Given
        UUID customerId = UUID.randomUUID();
        
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(customerUsageService.setUnlimitedForAllModels(customerId)).thenReturn(5);

        // When
        ResponseEntity<?> response = 
            controller.setBulkAllowance(customerId, new BulkSetAllowanceRequest(null, true));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void resetModelUsage_shouldResetUsageForModel() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);

        // When
        ResponseEntity<Void> response = controller.resetModelUsage(customerId, model);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(customerUsageService).resetUsage(customerId, model);
    }

    @Test
    void updatePolicy_shouldUpdateAllAllowancesPolicyType() {
        // Given
        UUID customerId = UUID.randomUUID();
        PolicyTypeEntity newPolicyType = createMonthlyPolicyType();
        
        when(policyTypeRepository.findByName("MONTHLY")).thenReturn(Optional.of(newPolicyType));
        when(customerTokenService.updateAllowancesPolicyType(customerId, newPolicyType))
            .thenReturn(5);

        // When
        ResponseEntity<?> response = 
            controller.updatePolicy(customerId, new UpdatePolicyRequest("MONTHLY", null));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ==================== Helper Methods ====================

    private PolicyTypeEntity createUnlimitedPolicyType() {
        PolicyTypeEntity policyType = new PolicyTypeEntity();
        policyType.setId(UUID.randomUUID());
        policyType.setName("UNLIMITED");
        policyType.setDescription("Unlimited policy");
        policyType.setResetDays(null);
        policyType.setEnabled(true);
        return policyType;
    }

    private PolicyTypeEntity createMonthlyPolicyType() {
        PolicyTypeEntity policyType = new PolicyTypeEntity();
        policyType.setId(UUID.randomUUID());
        policyType.setName("MONTHLY");
        policyType.setDescription("Monthly policy");
        policyType.setResetDays(30);
        policyType.setEnabled(true);
        return policyType;
    }

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
}
