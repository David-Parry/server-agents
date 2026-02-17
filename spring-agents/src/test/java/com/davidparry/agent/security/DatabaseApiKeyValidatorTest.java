package com.davidparry.agent.security;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerTokenEntity;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.CustomerTokenRepository;
import com.davidparry.agent.service.SecurityAuditService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
 * Unit tests for DatabaseApiKeyValidator.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseApiKeyValidatorTest {

    @Mock
    private CustomerTokenRepository tokenRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TokenHashingService hashingService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private SecurityAuditService auditService;

    private MeterRegistry meterRegistry;

    private DatabaseApiKeyValidator validator;

    private static final String SAMPLE_HASH = "4F48285575663521CDACDED9A6844A3A33FFC55808B48755E08FDDCF4EC63042";

    @BeforeEach
    void setUp() {
        // Use a real SimpleMeterRegistry to avoid mocking complexity
        meterRegistry = new SimpleMeterRegistry();
        
        validator = new DatabaseApiKeyValidator(
            tokenRepository,
            customerRepository,
            hashingService,
            jwtTokenService,
            auditService,
            meterRegistry
        );
    }

    @Test
    void validateToken_shouldRejectNullToken() {
        // When
        DatabaseApiKeyValidator.ValidationResult result = validator.validateToken(null);

        // Then
        assertFalse(result.valid());
        assertEquals("Missing API token", result.rejectionReason());
    }

    @Test
    void validateToken_shouldRejectBlankToken() {
        // When
        DatabaseApiKeyValidator.ValidationResult result = validator.validateToken("   ");

        // Then
        assertFalse(result.valid());
        assertEquals("Missing API token", result.rejectionReason());
    }

    @Test
    void validateToken_shouldRejectInvalidJwt() {
        // Given
        String token = "invalid.jwt.token";
        when(jwtTokenService.validateToken(token))
            .thenReturn(new JwtTokenService.TokenValidationResult(false, null, null, "Invalid signature"));

        // When
        DatabaseApiKeyValidator.ValidationResult result = validator.validateToken(token);

        // Then
        assertFalse(result.valid());
        assertTrue(result.rejectionReason().contains("Invalid JWT"));
    }

    @Test
    void validateToken_shouldRejectWhenCustomerNotFound() {
        // Given
        String token = "valid.jwt.token";
        UUID customerId = UUID.randomUUID();
        
        when(jwtTokenService.validateToken(token))
            .thenReturn(new JwtTokenService.TokenValidationResult(true, customerId, "Test", null));
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.empty());

        // When
        DatabaseApiKeyValidator.ValidationResult result = validator.validateToken(token);

        // Then
        assertFalse(result.valid());
        assertEquals("Customer not found", result.rejectionReason());
    }

    @Test
    void validateToken_shouldRejectDisabledCustomer() {
        // Given
        String token = "valid.jwt.token";
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        customer.setEnabled(false);
        
        when(jwtTokenService.validateToken(token))
            .thenReturn(new JwtTokenService.TokenValidationResult(true, customerId, "Test", null));
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));

        // When
        DatabaseApiKeyValidator.ValidationResult result = validator.validateToken(token);

        // Then
        assertFalse(result.valid());
        assertEquals("Customer account is disabled", result.rejectionReason());
    }

    @Test
    void validateToken_shouldRejectWhenNoActiveTokens() {
        // Given
        String token = "valid.jwt.token";
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        customer.setId(UUID.randomUUID());
        
        when(jwtTokenService.validateToken(token))
            .thenReturn(new JwtTokenService.TokenValidationResult(true, customerId, "Test", null));
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(tokenRepository.findActiveTokensByCustomerId(eq(customer.getId()), any(LocalDateTime.class)))
            .thenReturn(List.of());

        // When
        DatabaseApiKeyValidator.ValidationResult result = validator.validateToken(token);

        // Then
        assertFalse(result.valid());
        assertEquals("No active tokens for customer", result.rejectionReason());
    }

    @Test
    void validateToken_shouldAcceptValidToken() {
        // Given
        String token = "valid.jwt.token";
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        customer.setId(UUID.randomUUID());
        
        CustomerTokenEntity storedToken = new CustomerTokenEntity();
        storedToken.setId(UUID.randomUUID());
        storedToken.setCustomer(customer);
        storedToken.setTokenHash(SAMPLE_HASH);
        storedToken.setSecretVersion("V1");
        storedToken.setActive(true);
        
        when(jwtTokenService.validateToken(token))
            .thenReturn(new JwtTokenService.TokenValidationResult(true, customerId, "Test", null));
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(tokenRepository.findActiveTokensByCustomerId(eq(customer.getId()), any(LocalDateTime.class)))
            .thenReturn(List.of(storedToken));
        when(hashingService.verifyToken(token, customerId, SAMPLE_HASH, "V1"))
            .thenReturn(true);

        // When
        DatabaseApiKeyValidator.ValidationResult result = validator.validateToken(token);

        // Then
        assertTrue(result.valid());
        assertEquals(customerId.toString(), result.customerId());
    }

    @Test
    void validateToken_shouldRejectWhenHashDoesNotMatch() {
        // Given
        String token = "valid.jwt.token";
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomer(customerId, "Test Customer");
        customer.setId(UUID.randomUUID());
        
        CustomerTokenEntity storedToken = new CustomerTokenEntity();
        storedToken.setId(UUID.randomUUID());
        storedToken.setCustomer(customer);
        storedToken.setTokenHash(SAMPLE_HASH);
        storedToken.setSecretVersion("V1");
        storedToken.setActive(true);
        
        when(jwtTokenService.validateToken(token))
            .thenReturn(new JwtTokenService.TokenValidationResult(true, customerId, "Test", null));
        when(customerRepository.findByCustomerId(customerId))
            .thenReturn(Optional.of(customer));
        when(tokenRepository.findActiveTokensByCustomerId(eq(customer.getId()), any(LocalDateTime.class)))
            .thenReturn(List.of(storedToken));
        when(hashingService.verifyToken(token, customerId, SAMPLE_HASH, "V1"))
            .thenReturn(false);

        // When
        DatabaseApiKeyValidator.ValidationResult result = validator.validateToken(token);

        // Then
        assertFalse(result.valid());
        assertEquals("Invalid API token", result.rejectionReason());
    }

    @Test
    void isOperational_shouldReturnTrueWhenDatabaseIsAccessible() {
        // Given
        when(tokenRepository.count()).thenReturn(10L);

        // When
        boolean operational = validator.isOperational();

        // Then
        assertTrue(operational);
    }

    @Test
    void isOperational_shouldReturnFalseWhenDatabaseIsNotAccessible() {
        // Given
        when(tokenRepository.count()).thenThrow(new RuntimeException("Database error"));

        // When
        boolean operational = validator.isOperational();

        // Then
        assertFalse(operational);
    }

    private CustomerEntity createCustomer(UUID customerId, String name) {
        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(customerId);
        customer.setName(name);
        customer.setEnabled(true);
        return customer;
    }
}
