package com.davidparry.agent.service;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.LlmModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomerUsageService.
 * Tests per-model token usage validation and tracking.
 */
@ExtendWith(MockitoExtension.class)
class CustomerUsageServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerModelAllowanceRepository allowanceRepository;

    @Mock
    private LlmModelRepository modelRepository;

    private CustomerUsageService service;

    @BeforeEach
    void setUp() {
        service = new CustomerUsageService(customerRepository, allowanceRepository, modelRepository);
    }

    @Test
    void validateUsage_shouldAllowUnlimitedAllowance() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        
        CustomerEntity customer = createCustomer(customerId);
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, null, 0L); // NULL = unlimited
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(allowanceRepository.findByCustomerIdAndModel(customer.getId(), model))
            .thenReturn(Optional.of(allowance));

        // When
        CustomerUsageService.UsageValidationResult result = service.validateUsage(customerId, model);

        // Then
        assertTrue(result.allowed());
        assertTrue(result.unlimited());
        assertFalse(result.limitExceeded());
        assertNull(result.tokenLimit());
    }

    @Test
    void validateUsage_shouldAllowWhenUnderLimit() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        
        CustomerEntity customer = createCustomer(customerId);
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, 100000L, 50000L);
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(allowanceRepository.findByCustomerIdAndModel(customer.getId(), model))
            .thenReturn(Optional.of(allowance));

        // When
        CustomerUsageService.UsageValidationResult result = service.validateUsage(customerId, model);

        // Then
        assertTrue(result.allowed());
        assertFalse(result.unlimited());
        assertFalse(result.limitExceeded());
        assertEquals(100000L, result.tokenLimit());
        assertEquals(50000L, result.tokensUsed());
        assertEquals(50000L, result.remainingTokens());
    }

    @Test
    void validateUsage_shouldRejectWhenLimitExceeded() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        
        CustomerEntity customer = createCustomer(customerId);
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, 100000L, 150000L);
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(allowanceRepository.findByCustomerIdAndModel(customer.getId(), model))
            .thenReturn(Optional.of(allowance));

        // When
        CustomerUsageService.UsageValidationResult result = service.validateUsage(customerId, model);

        // Then
        assertFalse(result.allowed());
        assertTrue(result.limitExceeded());
        assertEquals(100000L, result.tokenLimit());
        assertEquals(150000L, result.tokensUsed());
    }

    @Test
    void validateUsage_shouldRejectWhenNoAccess() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        
        CustomerEntity customer = createCustomer(customerId);
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, 0L, 0L); // 0 = no access
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(allowanceRepository.findByCustomerIdAndModel(customer.getId(), model))
            .thenReturn(Optional.of(allowance));

        // When
        CustomerUsageService.UsageValidationResult result = service.validateUsage(customerId, model);

        // Then
        assertFalse(result.allowed());
        assertTrue(result.noAccess());
        assertNotNull(result.message());
    }

    @Test
    void validateUsage_shouldAutoCreateAllowanceWithZeroTokens() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        
        CustomerEntity customer = createCustomer(customerId);
        LlmModelEntity llmModel = createLlmModel(model);
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(allowanceRepository.findByCustomerIdAndModel(customer.getId(), model))
            .thenReturn(Optional.empty());
        when(modelRepository.findById(model)).thenReturn(Optional.of(llmModel));
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class)))
            .thenAnswer(i -> {
                CustomerModelAllowanceEntity a = i.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });

        // When
        CustomerUsageService.UsageValidationResult result = service.validateUsage(customerId, model);

        // Then
        assertFalse(result.allowed());
        assertTrue(result.noAccess());
        
        // Verify allowance was created with 0 tokens
        ArgumentCaptor<CustomerModelAllowanceEntity> captor = 
            ArgumentCaptor.forClass(CustomerModelAllowanceEntity.class);
        verify(allowanceRepository).save(captor.capture());
        assertEquals(0L, captor.getValue().getAllowedTokens());
    }

    @Test
    void validateUsage_shouldResetUsageWhenPolicyPeriodElapsed() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        
        CustomerEntity customer = createCustomer(customerId);
        PolicyTypeEntity monthlyPolicy = createMonthlyPolicyType();
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, 100000L, 90000L);
        // Set the policy type on the allowance
        allowance.setPolicyType(monthlyPolicy);
        // Set reset time to 31 days ago (past the 30-day reset period)
        allowance.setTokensResetAt(LocalDateTime.now().minusDays(31));
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(allowanceRepository.findByCustomerIdAndModel(customer.getId(), model))
            .thenReturn(Optional.of(allowance));
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class)))
            .thenAnswer(i -> i.getArgument(0));

        // When
        CustomerUsageService.UsageValidationResult result = service.validateUsage(customerId, model);

        // Then
        assertTrue(result.allowed());
        assertEquals(0L, result.tokensUsed()); // Usage was reset
        
        // Verify save was called (to persist the reset)
        verify(allowanceRepository).save(any(CustomerModelAllowanceEntity.class));
    }

    @Test
    void validateUsage_shouldReturnCustomerNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.empty());

        // When
        CustomerUsageService.UsageValidationResult result = service.validateUsage(customerId, model);

        // Then
        assertFalse(result.allowed());
        assertTrue(result.customerNotFound());
    }

    @Test
    void recordUsage_shouldIncrementTokensUsed() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        long tokensUsed = 5000L;
        
        CustomerEntity customer = createCustomer(customerId);
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, 100000L, 10000L);
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(allowanceRepository.findByCustomerIdAndModel(customer.getId(), model))
            .thenReturn(Optional.of(allowance));
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class)))
            .thenAnswer(i -> i.getArgument(0));

        // When
        service.recordUsage(customerId, model, tokensUsed);

        // Then
        assertEquals(15000L, allowance.getTokensUsed()); // 10000 + 5000
        verify(allowanceRepository).save(allowance);
    }

    @Test
    void setAllowance_shouldUpdateAllowedTokens() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        Long newAllowance = 200000L;
        
        CustomerEntity customer = createCustomer(customerId);
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, 100000L, 50000L);
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(allowanceRepository.findByCustomerIdAndModel(customer.getId(), model))
            .thenReturn(Optional.of(allowance));
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class)))
            .thenAnswer(i -> i.getArgument(0));

        // When
        CustomerModelAllowanceEntity result = service.setAllowance(customerId, model, newAllowance);

        // Then
        assertEquals(newAllowance, result.getAllowedTokens());
        verify(allowanceRepository).save(allowance);
    }

    @Test
    void setUnlimited_shouldSetAllowedTokensToNull() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        
        CustomerEntity customer = createCustomer(customerId);
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, 100000L, 50000L);
        
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(allowanceRepository.findByCustomerIdAndModel(customer.getId(), model))
            .thenReturn(Optional.of(allowance));
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class)))
            .thenAnswer(i -> i.getArgument(0));

        // When
        CustomerModelAllowanceEntity result = service.setUnlimited(customerId, model);

        // Then
        assertNull(result.getAllowedTokens());
        assertTrue(result.isUnlimited());
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

    private PolicyTypeEntity createMonthlyPolicyType() {
        PolicyTypeEntity policyType = new PolicyTypeEntity();
        policyType.setId(UUID.randomUUID());
        policyType.setName("MONTHLY");
        policyType.setResetDays(30);
        policyType.setEnabled(true);
        return policyType;
    }

    private LlmModelEntity createLlmModel(String model) {
        LlmModelEntity llmModel = new LlmModelEntity();
        llmModel.setModel(model);
        llmModel.setProvider("anthropic");
        llmModel.setDisplayName("Claude Sonnet 4.5");
        llmModel.setDefaultTokensForNewCustomers(0L);
        llmModel.setEnabled(true);
        return llmModel;
    }

    private CustomerModelAllowanceEntity createAllowance(CustomerEntity customer, String model, 
                                                          Long allowedTokens, Long tokensUsed) {
        LlmModelEntity llmModel = createLlmModel(model);
        
        CustomerModelAllowanceEntity allowance = new CustomerModelAllowanceEntity();
        allowance.setId(UUID.randomUUID());
        allowance.setCustomer(customer);
        allowance.setLlmModel(llmModel);
        allowance.setAllowedTokens(allowedTokens);
        allowance.setTokensUsed(tokensUsed);
        allowance.setTokensResetAt(LocalDateTime.now());
        allowance.setEnabled(true);
        return allowance;
    }
}
