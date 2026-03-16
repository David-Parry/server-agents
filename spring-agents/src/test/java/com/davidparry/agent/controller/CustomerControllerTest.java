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
import com.davidparry.agent.service.LlmTokenUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    @Mock
    private LlmTokenUsageService llmTokenUsageService;

    private CustomerController controller;

    @BeforeEach
    void setUp() {
        controller = new CustomerController(
            customerTokenService,
            customerUsageService,
            customerRepository,
            policyTypeRepository,
            jwtTokenService,
            llmTokenUsageService
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
    void generateToken_shouldCreateNewToken() {
        // Given
        UUID customerId = UUID.randomUUID();
        String mockJwt = "new.jwt.token";
        CustomerEntity customer = createCustomer(customerId, "Test Customer");

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(jwtTokenService.generateToken(eq(customerId), eq("Test Customer"), isNull())).thenReturn(mockJwt);

        // When
        ResponseEntity<TokenGeneratedResponse> response =
            controller.generateToken(customerId, null);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockJwt, response.getBody().apiToken());
        verify(customerTokenService).registerToken(customerId, mockJwt, null);
    }

    @Test
    void generateToken_shouldCreateTokenWithExpiration() {
        // Given
        UUID customerId = UUID.randomUUID();
        String mockJwt = "new.jwt.token";
        CustomerEntity customer = createCustomer(customerId, "Test Customer");

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(jwtTokenService.generateToken(eq(customerId), eq("Test Customer"), any(LocalDateTime.class)))
            .thenReturn(mockJwt);

        GenerateTokenRequest request = new GenerateTokenRequest(30, false);

        // When
        ResponseEntity<TokenGeneratedResponse> response = controller.generateToken(customerId, request);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockJwt, response.getBody().apiToken());
        assertNotNull(response.getBody().expiresAt());
    }

    @Test
    void generateToken_shouldRevokeExistingTokensWhenRequested() {
        // Given
        UUID customerId = UUID.randomUUID();
        String mockJwt = "new.jwt.token";
        CustomerEntity customer = createCustomer(customerId, "Test Customer");

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(jwtTokenService.generateToken(eq(customerId), eq("Test Customer"), isNull())).thenReturn(mockJwt);

        GenerateTokenRequest request = new GenerateTokenRequest(null, true);

        // When
        ResponseEntity<TokenGeneratedResponse> response = controller.generateToken(customerId, request);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(customerTokenService).rotateToken(customerId, mockJwt, null, true);
    }

    @Test
    void generateToken_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        // When
        ResponseEntity<TokenGeneratedResponse> response = controller.generateToken(customerId, null);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void generateToken_shouldRejectDisabledCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        customer.setEnabled(false);

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));

        // When
        ResponseEntity<TokenGeneratedResponse> response = controller.generateToken(customerId, null);

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().message().contains("disabled"));
    }

    @Test
    void revokeAllTokens_shouldRevokeTokens() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(customerTokenService.revokeAllTokens(customerId)).thenReturn(3);

        // When
        ResponseEntity<TokensRevokedResponse> response = controller.revokeAllTokens(customerId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().revokedCount());
    }

    @Test
    void revokeAllTokens_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(false);

        // When
        ResponseEntity<TokensRevokedResponse> response = controller.revokeAllTokens(customerId);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listCustomers_shouldReturnAllCustomers() {
        // Given
        CustomerEntity customer1 = createCustomer(UUID.randomUUID(), "Customer 1");
        CustomerEntity customer2 = createCustomer(UUID.randomUUID(), "Customer 2");

        when(customerRepository.findAll()).thenReturn(List.of(customer1, customer2));
        when(customerUsageService.getCustomerAllowancesWithPolicyType(any())).thenReturn(List.of());

        // When
        ResponseEntity<List<CustomerResponse>> response = controller.listCustomers();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void updateStatus_shouldEnableCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        customer.setEnabled(false);

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(customerUsageService.getCustomerAllowancesWithPolicyType(customerId)).thenReturn(List.of());

        // When
        ResponseEntity<CustomerResponse> response =
            controller.updateStatus(customerId, new UpdateStatusRequest(true));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(customer.isEnabled());
    }

    @Test
    void updateStatus_shouldDisableCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(customerUsageService.getCustomerAllowancesWithPolicyType(customerId)).thenReturn(List.of());

        // When
        ResponseEntity<CustomerResponse> response =
            controller.updateStatus(customerId, new UpdateStatusRequest(false));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(customer.isEnabled());
    }

    @Test
    void updateStatus_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        // When
        ResponseEntity<CustomerResponse> response =
            controller.updateStatus(customerId, new UpdateStatusRequest(true));

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getCustomerAllowances_shouldReturnAllowances() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        LlmModelEntity model = createLlmModel("claude-sonnet-4-5");
        PolicyTypeEntity policyType = createMonthlyPolicyType();
        CustomerModelAllowanceEntity allowance = createAllowance(customer, model, 100000L, 5000L);
        allowance.setPolicyType(policyType);

        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(customerUsageService.getCustomerAllowancesWithPolicyType(customerId))
            .thenReturn(List.of(allowance));

        // When
        ResponseEntity<List<ModelAllowanceResponse>> response =
            controller.getCustomerAllowances(customerId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("claude-sonnet-4-5", response.getBody().get(0).model());
    }

    @Test
    void getCustomerAllowances_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(false);

        // When
        ResponseEntity<List<ModelAllowanceResponse>> response =
            controller.getCustomerAllowances(customerId);

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
    void setAllowance_shouldSetAllowanceWithPolicyType() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        Long newAllowance = 200000L;
        String policyName = "MONTHLY";

        PolicyTypeEntity policyType = createMonthlyPolicyType();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        LlmModelEntity llmModel = createLlmModel(model);
        CustomerModelAllowanceEntity allowance = createAllowance(customer, llmModel, newAllowance, 0L);
        allowance.setPolicyType(policyType);

        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(policyTypeRepository.findByName(policyName)).thenReturn(Optional.of(policyType));
        when(customerUsageService.setAllowanceWithPolicyType(customerId, model, newAllowance, policyType))
            .thenReturn(allowance);

        // When
        ResponseEntity<ModelAllowanceResponse> response =
            controller.setAllowance(customerId, model, new SetAllowanceRequest(newAllowance, policyName));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("MONTHLY", response.getBody().policyTypeName());
    }

    @Test
    void setAllowance_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(false);

        // When
        ResponseEntity<ModelAllowanceResponse> response =
            controller.setAllowance(customerId, "model", new SetAllowanceRequest(1000L, null));

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void setAllowance_shouldReturnBadRequestForInvalidPolicyType() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(policyTypeRepository.findByName("INVALID")).thenReturn(Optional.empty());

        // When
        ResponseEntity<ModelAllowanceResponse> response =
            controller.setAllowance(customerId, "model", new SetAllowanceRequest(1000L, "INVALID"));

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
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
    void setBulkAllowance_shouldSetSpecificAllowanceForAllModels() {
        // Given
        UUID customerId = UUID.randomUUID();
        Long allowance = 50000L;

        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(customerUsageService.setAllowanceForAllModels(customerId, allowance)).thenReturn(5);

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.setBulkAllowance(customerId, new BulkSetAllowanceRequest(allowance, false));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().get("modelsUpdated"));
    }

    @Test
    void setBulkAllowance_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(false);

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.setBulkAllowance(customerId, new BulkSetAllowanceRequest(1000L, false));

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
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
    void resetModelUsage_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(false);

        // When
        ResponseEntity<Void> response = controller.resetModelUsage(customerId, "model");

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void resetAllUsage_shouldResetAllModels() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);

        // When
        ResponseEntity<Void> response = controller.resetAllUsage(customerId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(customerUsageService).resetAllUsage(customerId);
    }

    @Test
    void resetAllUsage_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(false);

        // When
        ResponseEntity<Void> response = controller.resetAllUsage(customerId);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listPolicyTypes_shouldReturnEnabledPolicyTypes() {
        // Given
        PolicyTypeEntity policy1 = createUnlimitedPolicyType();
        PolicyTypeEntity policy2 = createMonthlyPolicyType();

        when(policyTypeRepository.findByEnabledTrue()).thenReturn(List.of(policy1, policy2));

        // When
        ResponseEntity<List<PolicyTypeResponse>> response = controller.listPolicyTypes();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void setBulkPolicyType_shouldSetPolicyForAllAllowances() {
        // Given
        UUID customerId = UUID.randomUUID();
        PolicyTypeEntity policyType = createMonthlyPolicyType();

        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(policyTypeRepository.findByName("MONTHLY")).thenReturn(Optional.of(policyType));
        when(customerUsageService.setPolicyTypeForAllAllowances(customerId, policyType)).thenReturn(5);

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.setBulkPolicyType(customerId, new BulkPolicyTypeRequest("MONTHLY", null));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().get("allowancesUpdated"));
        assertEquals("MONTHLY", response.getBody().get("policyTypeName"));
    }

    @Test
    void setBulkPolicyType_shouldSetPolicyWithAllowance() {
        // Given
        UUID customerId = UUID.randomUUID();
        PolicyTypeEntity policyType = createMonthlyPolicyType();
        Long allowance = 50000L;

        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(policyTypeRepository.findByName("MONTHLY")).thenReturn(Optional.of(policyType));
        when(customerUsageService.setAllowanceAndPolicyTypeForAllModels(customerId, allowance, policyType))
            .thenReturn(5);

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.setBulkPolicyType(customerId, new BulkPolicyTypeRequest("MONTHLY", allowance));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().get("allowancesUpdated"));
        assertEquals(allowance, response.getBody().get("allowedTokens"));
    }

    @Test
    void setBulkPolicyType_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(false);

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.setBulkPolicyType(customerId, new BulkPolicyTypeRequest("MONTHLY", null));

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void setBulkPolicyType_shouldReturnBadRequestForNullPolicyName() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.setBulkPolicyType(customerId, new BulkPolicyTypeRequest(null, null));

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void setBulkPolicyType_shouldReturnBadRequestForEmptyPolicyName() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.setBulkPolicyType(customerId, new BulkPolicyTypeRequest("", null));

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void setBulkPolicyType_shouldReturnBadRequestForInvalidPolicyType() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(policyTypeRepository.findByName("INVALID")).thenReturn(Optional.empty());

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.setBulkPolicyType(customerId, new BulkPolicyTypeRequest("INVALID", null));

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updateNotificationSettings_shouldUpdateSettings() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UpdateNotificationSettingsRequest request =
            new UpdateNotificationSettingsRequest(5000L, "https://webhook.example.com", "test@example.com");

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.updateNotificationSettings(customerId, request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5000L, customer.getDefaultMinTokenNotificationThreshold());
        assertEquals("https://webhook.example.com", customer.getNotificationWebhookUrl());
        assertEquals("test@example.com", customer.getNotificationEmail());
    }

    @Test
    void updateNotificationSettings_shouldClearWebhookWithEmptyString() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        customer.setNotificationWebhookUrl("https://existing.com");

        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UpdateNotificationSettingsRequest request =
            new UpdateNotificationSettingsRequest(null, "", null);

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.updateNotificationSettings(customerId, request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(customer.getNotificationWebhookUrl());
    }

    @Test
    void updateNotificationSettings_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        // When
        ResponseEntity<Map<String, Object>> response =
            controller.updateNotificationSettings(customerId,
                new UpdateNotificationSettingsRequest(null, null, null));

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void updateAllowanceNotificationThreshold_shouldUpdateThreshold() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        Long threshold = 10000L;

        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        LlmModelEntity llmModel = createLlmModel(model);
        PolicyTypeEntity policyType = createMonthlyPolicyType();
        CustomerModelAllowanceEntity allowance = createAllowance(customer, llmModel, 100000L, 5000L);
        allowance.setPolicyType(policyType);
        allowance.setMinTokenNotificationThreshold(threshold);

        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);
        when(customerUsageService.setNotificationThreshold(customerId, model, threshold))
            .thenReturn(allowance);

        // When
        ResponseEntity<ModelAllowanceResponse> response =
            controller.updateAllowanceNotificationThreshold(customerId, model,
                new UpdateAllowanceNotificationThresholdRequest(threshold));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(threshold, response.getBody().minTokenNotificationThreshold());
    }

    @Test
    void updateAllowanceNotificationThreshold_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(false);

        // When
        ResponseEntity<ModelAllowanceResponse> response =
            controller.updateAllowanceNotificationThreshold(customerId, "model",
                new UpdateAllowanceNotificationThresholdRequest(1000L));

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
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

    @Test
    void updatePolicy_shouldReturnBadRequestForNullPolicyName() {
        // Given
        UUID customerId = UUID.randomUUID();

        // When
        ResponseEntity<?> response =
            controller.updatePolicy(customerId, new UpdatePolicyRequest(null, null));

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updatePolicy_shouldReturnBadRequestForEmptyPolicyName() {
        // Given
        UUID customerId = UUID.randomUUID();

        // When
        ResponseEntity<?> response =
            controller.updatePolicy(customerId, new UpdatePolicyRequest("", null));

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updatePolicy_shouldReturnBadRequestForInvalidPolicyType() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(policyTypeRepository.findByName("INVALID")).thenReturn(Optional.empty());

        // When
        ResponseEntity<?> response =
            controller.updatePolicy(customerId, new UpdatePolicyRequest("INVALID", null));

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updatePolicy_shouldReturnNotFoundForUnknownCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        PolicyTypeEntity policyType = createMonthlyPolicyType();

        when(policyTypeRepository.findByName("MONTHLY")).thenReturn(Optional.of(policyType));
        when(customerTokenService.updateAllowancesPolicyType(customerId, policyType))
            .thenThrow(new IllegalArgumentException("Customer not found"));

        // When
        ResponseEntity<?> response =
            controller.updatePolicy(customerId, new UpdatePolicyRequest("MONTHLY", null));

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
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
