package com.davidparry.agent.service;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.LlmModelRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LlmModelService.
 */
@ExtendWith(MockitoExtension.class)
class LlmModelServiceTest {

    @Mock
    private LlmModelRepository modelRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerModelAllowanceRepository allowanceRepository;

    @Mock
    private PolicyTypeRepository policyTypeRepository;

    private LlmModelService service;

    @BeforeEach
    void setUp() {
        service = new LlmModelService(modelRepository, customerRepository, allowanceRepository, policyTypeRepository);
    }

    @Test
    void createModel_shouldCreateModelAndLinkToCustomers() {
        // Given
        String model = "claude-sonnet-4-5";
        String provider = "anthropic";
        String displayName = "Claude Sonnet 4.5";
        String description = "Latest Claude model";
        Long defaultTokens = 100000L;

        PolicyTypeEntity unlimitedPolicy = createPolicyType("UNLIMITED");
        CustomerEntity customer1 = createCustomer();
        CustomerEntity customer2 = createCustomer();

        when(modelRepository.existsById(model)).thenReturn(false);
        when(modelRepository.save(any(LlmModelEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(policyTypeRepository.findUnlimitedPolicyType()).thenReturn(Optional.of(unlimitedPolicy));
        when(customerRepository.findAll()).thenReturn(List.of(customer1, customer2));
        when(allowanceRepository.existsByCustomerAndLlmModel(any(), any())).thenReturn(false);
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        LlmModelEntity result = service.createModel(model, provider, displayName, description, defaultTokens, null, null);

        // Then
        assertEquals(model, result.getModel());
        assertEquals(provider, result.getProvider());
        assertEquals(displayName, result.getDisplayName());
        assertEquals(description, result.getDescription());
        assertEquals(defaultTokens, result.getDefaultTokensForNewCustomers());
        assertTrue(result.isEnabled());

        // Verify allowances were created for both customers
        verify(allowanceRepository, times(2)).save(any(CustomerModelAllowanceEntity.class));
    }

    @Test
    void createModel_shouldThrowWhenModelExists() {
        // Given
        String model = "claude-sonnet-4-5";
        when(modelRepository.existsById(model)).thenReturn(true);

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                service.createModel(model, "anthropic", "Claude", "desc", 100000L, null, null));
    }

    @Test
    void createModel_shouldUseZeroDefaultTokensWhenNull() {
        // Given
        String model = "claude-sonnet-4-5";
        when(modelRepository.existsById(model)).thenReturn(false);
        when(modelRepository.save(any(LlmModelEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(policyTypeRepository.findUnlimitedPolicyType()).thenReturn(Optional.empty());
        when(customerRepository.findAll()).thenReturn(List.of());

        // When
        LlmModelEntity result = service.createModel(model, "anthropic", "Claude", "desc", null, null, null);

        // Then
        assertEquals(0L, result.getDefaultTokensForNewCustomers());
    }

    @Test
    void linkModelToAllCustomers_shouldCreateAllowancesForAllCustomers() {
        // Given
        LlmModelEntity model = createLlmModel("claude-sonnet-4-5");
        PolicyTypeEntity policy = createPolicyType("MONTHLY");
        CustomerEntity customer1 = createCustomer();
        CustomerEntity customer2 = createCustomer();
        CustomerEntity customer3 = createCustomer();

        when(customerRepository.findAll()).thenReturn(List.of(customer1, customer2, customer3));
        when(allowanceRepository.existsByCustomerAndLlmModel(any(), any())).thenReturn(false);
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        int linked = service.linkModelToAllCustomers(model, policy);

        // Then
        assertEquals(3, linked);
        verify(allowanceRepository, times(3)).save(any(CustomerModelAllowanceEntity.class));
    }

    @Test
    void linkModelToAllCustomers_shouldSkipExistingAllowances() {
        // Given
        LlmModelEntity model = createLlmModel("claude-sonnet-4-5");
        PolicyTypeEntity policy = createPolicyType("MONTHLY");
        CustomerEntity customer1 = createCustomer();
        CustomerEntity customer2 = createCustomer();

        when(customerRepository.findAll()).thenReturn(List.of(customer1, customer2));
        when(allowanceRepository.existsByCustomerAndLlmModel(customer1, model)).thenReturn(true);
        when(allowanceRepository.existsByCustomerAndLlmModel(customer2, model)).thenReturn(false);
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        int linked = service.linkModelToAllCustomers(model, policy);

        // Then
        assertEquals(1, linked);
        verify(allowanceRepository, times(1)).save(any(CustomerModelAllowanceEntity.class));
    }

    @Test
    void linkAllModelsToCustomer_shouldCreateAllowancesForAllModels() {
        // Given
        CustomerEntity customer = createCustomer();
        PolicyTypeEntity policy = createPolicyType("MONTHLY");
        LlmModelEntity model1 = createLlmModel("claude-sonnet-4-5");
        LlmModelEntity model2 = createLlmModel("llama3.1:8b");

        when(modelRepository.findByEnabledTrue()).thenReturn(List.of(model1, model2));
        when(allowanceRepository.existsByCustomerAndLlmModel(any(), any())).thenReturn(false);
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        int linked = service.linkAllModelsToCustomer(customer, policy, 50000L, false);

        // Then
        assertEquals(2, linked);

        ArgumentCaptor<CustomerModelAllowanceEntity> captor = ArgumentCaptor.forClass(CustomerModelAllowanceEntity.class);
        verify(allowanceRepository, times(2)).save(captor.capture());

        List<CustomerModelAllowanceEntity> savedAllowances = captor.getAllValues();
        for (CustomerModelAllowanceEntity allowance : savedAllowances) {
            assertEquals(50000L, allowance.getAllowedTokens());
            assertEquals(policy, allowance.getPolicyType());
            assertTrue(allowance.isEnabled());
        }
    }

    @Test
    void linkAllModelsToCustomer_shouldSetUnlimitedWhenRequested() {
        // Given
        CustomerEntity customer = createCustomer();
        PolicyTypeEntity policy = createPolicyType("UNLIMITED");
        LlmModelEntity model = createLlmModel("claude-sonnet-4-5");

        when(modelRepository.findByEnabledTrue()).thenReturn(List.of(model));
        when(allowanceRepository.existsByCustomerAndLlmModel(any(), any())).thenReturn(false);
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        int linked = service.linkAllModelsToCustomer(customer, policy, null, true);

        // Then
        assertEquals(1, linked);

        ArgumentCaptor<CustomerModelAllowanceEntity> captor = ArgumentCaptor.forClass(CustomerModelAllowanceEntity.class);
        verify(allowanceRepository).save(captor.capture());
        assertNull(captor.getValue().getAllowedTokens()); // Unlimited
    }

    @Test
    void linkAllModelsToCustomer_shouldUseModelDefaultWhenNoDefaultProvided() {
        // Given
        CustomerEntity customer = createCustomer();
        PolicyTypeEntity policy = createPolicyType("MONTHLY");
        LlmModelEntity model = createLlmModel("claude-sonnet-4-5");
        model.setDefaultTokensForNewCustomers(75000L);

        when(modelRepository.findByEnabledTrue()).thenReturn(List.of(model));
        when(allowanceRepository.existsByCustomerAndLlmModel(any(), any())).thenReturn(false);
        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        int linked = service.linkAllModelsToCustomer(customer, policy, null, false);

        // Then
        assertEquals(1, linked);

        ArgumentCaptor<CustomerModelAllowanceEntity> captor = ArgumentCaptor.forClass(CustomerModelAllowanceEntity.class);
        verify(allowanceRepository).save(captor.capture());
        assertEquals(75000L, captor.getValue().getAllowedTokens());
    }

    @Test
    void getAllEnabledModels_shouldReturnOnlyEnabledModels() {
        // Given
        LlmModelEntity model1 = createLlmModel("claude-sonnet-4-5");
        LlmModelEntity model2 = createLlmModel("llama3.1:8b");
        when(modelRepository.findByEnabledTrue()).thenReturn(List.of(model1, model2));

        // When
        List<LlmModelEntity> result = service.getAllEnabledModels();

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void getAllModels_shouldReturnAllModels() {
        // Given
        LlmModelEntity model1 = createLlmModel("claude-sonnet-4-5");
        LlmModelEntity model2 = createLlmModel("llama3.1:8b");
        model2.setEnabled(false);
        when(modelRepository.findAll()).thenReturn(List.of(model1, model2));

        // When
        List<LlmModelEntity> result = service.getAllModels();

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void getModel_shouldReturnModel() {
        // Given
        String modelId = "claude-sonnet-4-5";
        LlmModelEntity model = createLlmModel(modelId);
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));

        // When
        LlmModelEntity result = service.getModel(modelId);

        // Then
        assertEquals(modelId, result.getModel());
    }

    @Test
    void getModel_shouldThrowWhenNotFound() {
        // Given
        String modelId = "nonexistent";
        when(modelRepository.findById(modelId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> service.getModel(modelId));
    }

    @Test
    void updateDefaultTokens_shouldUpdateModel() {
        // Given
        String modelId = "claude-sonnet-4-5";
        LlmModelEntity model = createLlmModel(modelId);
        Long newDefaultTokens = 200000L;

        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any(LlmModelEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        LlmModelEntity result = service.updateDefaultTokens(modelId, newDefaultTokens);

        // Then
        assertEquals(newDefaultTokens, result.getDefaultTokensForNewCustomers());
    }

    @Test
    void updateDefaultTokens_shouldThrowWhenNotFound() {
        // Given
        String modelId = "nonexistent";
        when(modelRepository.findById(modelId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> service.updateDefaultTokens(modelId, 100000L));
    }

    @Test
    void setModelEnabled_shouldEnableModel() {
        // Given
        String modelId = "claude-sonnet-4-5";
        LlmModelEntity model = createLlmModel(modelId);
        model.setEnabled(false);

        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any(LlmModelEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        LlmModelEntity result = service.setModelEnabled(modelId, true);

        // Then
        assertTrue(result.isEnabled());
    }

    @Test
    void setModelEnabled_shouldDisableModel() {
        // Given
        String modelId = "claude-sonnet-4-5";
        LlmModelEntity model = createLlmModel(modelId);

        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any(LlmModelEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        LlmModelEntity result = service.setModelEnabled(modelId, false);

        // Then
        assertFalse(result.isEnabled());
    }

    @Test
    void setModelEnabled_shouldThrowWhenNotFound() {
        // Given
        String modelId = "nonexistent";
        when(modelRepository.findById(modelId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> service.setModelEnabled(modelId, true));
    }

    // ==================== Helper Methods ====================

    private CustomerEntity createCustomer() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setCustomerId(UUID.randomUUID());
        customer.setName("Test Customer");
        customer.setEnabled(true);
        return customer;
    }

    private LlmModelEntity createLlmModel(String model) {
        LlmModelEntity llmModel = new LlmModelEntity();
        llmModel.setModel(model);
        llmModel.setProvider("anthropic");
        llmModel.setDisplayName("Test Model");
        llmModel.setDefaultTokensForNewCustomers(100000L);
        llmModel.setEnabled(true);
        return llmModel;
    }

    private PolicyTypeEntity createPolicyType(String name) {
        PolicyTypeEntity policy = new PolicyTypeEntity();
        policy.setId(UUID.randomUUID());
        policy.setName(name);
        policy.setResetDays(name.equals("UNLIMITED") ? null : 30);
        policy.setEnabled(true);
        return policy;
    }
}
