package com.davidparry.agent.service;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerTokenEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.CustomerTokenRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import com.davidparry.agent.security.TokenHashingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Unit tests for CustomerTokenService with multi-version secret support.
 * Token hashes are stored as hex strings (64 characters for SHA-256).
 */
@ExtendWith(MockitoExtension.class)
class CustomerTokenServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerTokenRepository tokenRepository;

    @Mock
    private PolicyTypeRepository policyTypeRepository;

    @Mock
    private CustomerModelAllowanceRepository allowanceRepository;

    @Mock
    private TokenHashingService hashingService;

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private LlmModelService llmModelService;

    private CustomerTokenService service;

    // Sample hex hash (64 characters for SHA-256)
    private static final String SAMPLE_HASH = "4F48285575663521CDACDED9A6844A3A33FFC55808B48755E08FDDCF4EC63042";

    @BeforeEach
    void setUp() {
        service = new CustomerTokenService(
            customerRepository, 
            tokenRepository, 
            policyTypeRepository,
            allowanceRepository,
            hashingService, 
            auditService,
            llmModelService
        );
    }

    @Test
    void registerToken_shouldHashAndSaveTokenWithVersion() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "jwt-token";
        String hash = SAMPLE_HASH;
        String secretVersion = "V1";
        
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(hashingService.hashToken(token, customerId))
            .thenReturn(new TokenHashingService.HashResult(hash, secretVersion));
        when(tokenRepository.save(any(CustomerTokenEntity.class))).thenAnswer(i -> {
            CustomerTokenEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        // When
        service.registerToken(customerId, token, null);

        // Then
        ArgumentCaptor<CustomerTokenEntity> captor = ArgumentCaptor.forClass(CustomerTokenEntity.class);
        verify(tokenRepository).save(captor.capture());
        
        CustomerTokenEntity savedToken = captor.getValue();
        assertEquals(customer, savedToken.getCustomer());
        assertEquals(hash, savedToken.getTokenHash());
        assertEquals(secretVersion, savedToken.getSecretVersion());
        assertTrue(savedToken.isActive());
        assertNull(savedToken.getExpiresAt());
        
        // Verify audit logging
        verify(auditService).logTokenCreated(eq(customerId), any(UUID.class), eq(secretVersion), eq("CustomerTokenService"));
    }

    @Test
    void registerToken_withExpiration_shouldSetExpiresAt() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "jwt-token";
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(hashingService.hashToken(token, customerId))
            .thenReturn(new TokenHashingService.HashResult(SAMPLE_HASH, "V1"));
        when(tokenRepository.save(any(CustomerTokenEntity.class))).thenAnswer(i -> {
            CustomerTokenEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        // When
        service.registerToken(customerId, token, expiresAt);

        // Then
        ArgumentCaptor<CustomerTokenEntity> captor = ArgumentCaptor.forClass(CustomerTokenEntity.class);
        verify(tokenRepository).save(captor.capture());
        assertEquals(expiresAt, captor.getValue().getExpiresAt());
    }

    @Test
    void registerToken_withV2Secret_shouldStoreV2Version() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "jwt-token";
        String hash = SAMPLE_HASH;
        String secretVersion = "V2";
        
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(hashingService.hashToken(token, customerId))
            .thenReturn(new TokenHashingService.HashResult(hash, secretVersion));
        when(tokenRepository.save(any(CustomerTokenEntity.class))).thenAnswer(i -> {
            CustomerTokenEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        // When
        service.registerToken(customerId, token, null);

        // Then
        ArgumentCaptor<CustomerTokenEntity> captor = ArgumentCaptor.forClass(CustomerTokenEntity.class);
        verify(tokenRepository).save(captor.capture());
        assertEquals("V2", captor.getValue().getSecretVersion());
    }

    @Test
    void registerToken_withUnknownCustomer_shouldThrowException() {
        // Given
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, 
            () -> service.registerToken(customerId, "token", null));
    }

    @Test
    void revokeAllTokens_shouldRevokeAndReturnCount() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        customer.setId(UUID.randomUUID());
        
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(tokenRepository.revokeAllTokensForCustomer(eq(customer.getId()), any(LocalDateTime.class)))
            .thenReturn(3);

        // When
        int revoked = service.revokeAllTokens(customerId);

        // Then
        assertEquals(3, revoked);
        verify(tokenRepository).revokeAllTokensForCustomer(eq(customer.getId()), any(LocalDateTime.class));
    }

    @Test
    void createCustomer_shouldCreateCustomerAndTokenWithDefaultPolicyType() {
        // Given
        String name = "New Customer";
        String initialToken = "initial-jwt-token";
        PolicyTypeEntity unlimitedPolicyType = createUnlimitedPolicyType();
        
        when(policyTypeRepository.findUnlimitedPolicyType()).thenReturn(Optional.of(unlimitedPolicyType));
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(invocation -> {
            CustomerEntity c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            // Also set up the findByCustomerId mock for the subsequent registerToken call
            when(customerRepository.findByCustomerId(c.getCustomerId())).thenReturn(Optional.of(c));
            return c;
        });
        when(hashingService.hashToken(eq(initialToken), any(UUID.class)))
            .thenReturn(new TokenHashingService.HashResult(SAMPLE_HASH, "V1"));
        when(tokenRepository.save(any(CustomerTokenEntity.class))).thenAnswer(i -> {
            CustomerTokenEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        // When
        CustomerEntity customer = service.createCustomer(name, initialToken);

        // Then
        assertNotNull(customer);
        assertEquals(name, customer.getName());
        assertTrue(customer.isEnabled());
        assertNotNull(customer.getCustomerId());
        
        verify(customerRepository).save(any(CustomerEntity.class));
        verify(tokenRepository).save(any(CustomerTokenEntity.class));
        verify(llmModelService).linkAllModelsToCustomer(any(CustomerEntity.class), eq(unlimitedPolicyType), isNull(), eq(true));
    }

    @Test
    void createCustomerWithPolicyType_shouldCreateCustomerWithSpecifiedPolicyType() {
        // Given
        String name = "New Customer";
        String initialToken = "initial-jwt-token";
        PolicyTypeEntity monthlyPolicyType = createMonthlyPolicyType();
        Long customTokenLimit = 200000L;
        
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(invocation -> {
            CustomerEntity c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            when(customerRepository.findByCustomerId(c.getCustomerId())).thenReturn(Optional.of(c));
            return c;
        });
        when(hashingService.hashToken(eq(initialToken), any(UUID.class)))
            .thenReturn(new TokenHashingService.HashResult(SAMPLE_HASH, "V1"));
        when(tokenRepository.save(any(CustomerTokenEntity.class))).thenAnswer(i -> {
            CustomerTokenEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        // When
        CustomerEntity customer = service.createCustomerWithPolicyType(name, monthlyPolicyType, customTokenLimit, false, initialToken);

        // Then
        assertNotNull(customer);
        assertEquals(name, customer.getName());
        assertTrue(customer.isEnabled());
        assertNotNull(customer.getCustomerId());
        
        verify(customerRepository).save(any(CustomerEntity.class));
        verify(tokenRepository).save(any(CustomerTokenEntity.class));
        verify(llmModelService).linkAllModelsToCustomer(any(CustomerEntity.class), eq(monthlyPolicyType), eq(customTokenLimit), eq(false));
    }

    @Test
    void createCustomerWithId_shouldUseProvidedId() {
        // Given
        UUID customerId = UUID.randomUUID();
        String name = "New Customer";
        PolicyTypeEntity policyType = createUnlimitedPolicyType();
        String initialToken = "initial-token";
        
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(false);
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(invocation -> {
            CustomerEntity c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            // Set up the findByCustomerId mock for the subsequent registerToken call
            when(customerRepository.findByCustomerId(c.getCustomerId())).thenReturn(Optional.of(c));
            return c;
        });
        when(hashingService.hashToken(eq(initialToken), eq(customerId)))
            .thenReturn(new TokenHashingService.HashResult(SAMPLE_HASH, "V1"));
        when(tokenRepository.save(any(CustomerTokenEntity.class))).thenAnswer(i -> {
            CustomerTokenEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        // When
        CustomerEntity customer = service.createCustomerWithId(customerId, name, policyType, null, true, initialToken);

        // Then
        assertEquals(customerId, customer.getCustomerId());
        assertEquals(name, customer.getName());
        verify(llmModelService).linkAllModelsToCustomer(any(CustomerEntity.class), eq(policyType), isNull(), eq(true));
    }

    @Test
    void createCustomerWithId_withExistingId_shouldThrowException() {
        // Given
        UUID customerId = UUID.randomUUID();
        PolicyTypeEntity policyType = createUnlimitedPolicyType();
        when(customerRepository.existsByCustomerId(customerId)).thenReturn(true);

        // When/Then
        assertThrows(IllegalArgumentException.class,
            () -> service.createCustomerWithId(customerId, "Name", policyType, null, false, "token"));
    }

    @Test
    void rotateToken_withRevokeOldImmediately_shouldRevokeOldTokens() {
        // Given
        UUID customerId = UUID.randomUUID();
        String newToken = "new-jwt-token";
        
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        customer.setId(UUID.randomUUID());
        
        // Create tokens with different creation times
        CustomerTokenEntity oldToken1 = createTokenEntityWithCreatedAt(customer, LocalDateTime.now().minusHours(2));
        CustomerTokenEntity oldToken2 = createTokenEntityWithCreatedAt(customer, LocalDateTime.now().minusHours(1));
        CustomerTokenEntity newTokenEntity = createTokenEntityWithCreatedAt(customer, LocalDateTime.now());
        
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(hashingService.hashToken(newToken, customerId))
            .thenReturn(new TokenHashingService.HashResult(SAMPLE_HASH, "V1"));
        when(tokenRepository.save(any(CustomerTokenEntity.class))).thenAnswer(i -> {
            CustomerTokenEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(tokenRepository.findActiveTokensByCustomerId(eq(customer.getId()), any(LocalDateTime.class)))
            .thenReturn(List.of(newTokenEntity, oldToken1, oldToken2));

        // When
        service.rotateToken(customerId, newToken, null, true);

        // Then
        verify(tokenRepository).save(any(CustomerTokenEntity.class)); // New token saved
        // Old tokens should be revoked (the service calls revoke() on them)
        assertFalse(oldToken1.isActive());
        assertFalse(oldToken2.isActive());
    }

    @Test
    void updateAllowancesPolicyType_shouldUpdateAllAllowances() {
        // Given
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        customer.setId(UUID.randomUUID());
        PolicyTypeEntity newPolicyType = createMonthlyPolicyType();
        
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(allowanceRepository.updatePolicyTypeForCustomer(eq(customer.getId()), eq(newPolicyType), any(LocalDateTime.class)))
            .thenReturn(5);

        // When
        int updated = service.updateAllowancesPolicyType(customerId, newPolicyType);

        // Then
        assertEquals(5, updated);
        verify(allowanceRepository).updatePolicyTypeForCustomer(eq(customer.getId()), eq(newPolicyType), any(LocalDateTime.class));
    }

    private CustomerEntity createCustomer(UUID customerId, String name) {
        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(customerId);
        customer.setName(name);
        customer.setEnabled(true);
        return customer;
    }
    
    private PolicyTypeEntity createUnlimitedPolicyType() {
        PolicyTypeEntity policyType = new PolicyTypeEntity();
        policyType.setId(UUID.randomUUID());
        policyType.setName("UNLIMITED");
        policyType.setDescription("Unlimited token usage");
        policyType.setResetDays(null);
        policyType.setEnabled(true);
        return policyType;
    }
    
    private PolicyTypeEntity createMonthlyPolicyType() {
        PolicyTypeEntity policyType = new PolicyTypeEntity();
        policyType.setId(UUID.randomUUID());
        policyType.setName("MONTHLY");
        policyType.setDescription("Monthly token allocation");
        policyType.setResetDays(30);
        policyType.setEnabled(true);
        return policyType;
    }

    private CustomerTokenEntity createTokenEntityWithCreatedAt(CustomerEntity customer, LocalDateTime createdAt) {
        // Use anonymous class to override getCreatedAt since it's set by @PrePersist
        CustomerTokenEntity token = new CustomerTokenEntity() {
            @Override
            public LocalDateTime getCreatedAt() {
                return createdAt;
            }
        };
        token.setId(UUID.randomUUID());
        token.setCustomer(customer);
        token.setTokenHash(SAMPLE_HASH);
        token.setSecretVersion("V1");
        token.setActive(true);
        return token;
    }
}
