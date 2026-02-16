package com.davidparry.agent.security;

import com.davidparry.agent.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenService.
 */
class JwtTokenServiceTest {

    private static final String SIGNING_KEY = "test-jwt-signing-key-for-testing-only-minimum-32-characters";
    private static final String ISSUER = "spring-agents-test";
    private static final int EXPIRATION_DAYS = 365;

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(SIGNING_KEY, ISSUER, EXPIRATION_DAYS);
        jwtTokenService = new JwtTokenService(properties);
    }

    @Test
    void generateToken_createsValidJwt() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";

        // When
        String token = jwtTokenService.generateToken(customerId, customerName);

        // Then
        assertNotNull(token);
        assertTrue(token.contains(".")); // JWT format: header.payload.signature
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
    }

    @Test
    void generateToken_includesCustomerIdClaim() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";

        // When
        String token = jwtTokenService.generateToken(customerId, customerName);

        // Then
        Claims claims = jwtTokenService.parseToken(token);
        assertEquals(customerId.toString(), claims.get(JwtTokenService.CLAIM_CUSTOMER_ID, String.class));
        assertEquals(customerName, claims.get(JwtTokenService.CLAIM_CUSTOMER_NAME, String.class));
        assertEquals(JwtTokenService.TOKEN_TYPE_API, claims.get(JwtTokenService.CLAIM_TOKEN_TYPE, String.class));
    }

    @Test
    void generateToken_setsCorrectIssuer() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";

        // When
        String token = jwtTokenService.generateToken(customerId, customerName);

        // Then
        Claims claims = jwtTokenService.parseToken(token);
        assertEquals(ISSUER, claims.getIssuer());
    }

    @Test
    void generateToken_setsSubjectToCustomerId() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";

        // When
        String token = jwtTokenService.generateToken(customerId, customerName);

        // Then
        Claims claims = jwtTokenService.parseToken(token);
        assertEquals(customerId.toString(), claims.getSubject());
    }

    @Test
    void generateToken_withCustomExpiration_setsExpiresAt() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);

        // When
        String token = jwtTokenService.generateToken(customerId, customerName, expiresAt);

        // Then
        Claims claims = jwtTokenService.parseToken(token);
        assertNotNull(claims.getExpiration());
    }

    @Test
    void extractCustomerId_returnsCorrectId() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";
        String token = jwtTokenService.generateToken(customerId, customerName);

        // When
        UUID extractedId = jwtTokenService.extractCustomerId(token);

        // Then
        assertEquals(customerId, extractedId);
    }

    @Test
    void isValidToken_returnsTrueForValidToken() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";
        String token = jwtTokenService.generateToken(customerId, customerName);

        // When
        boolean isValid = jwtTokenService.isValidToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    void isValidToken_returnsFalseForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        boolean isValid = jwtTokenService.isValidToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void isValidToken_returnsFalseForTamperedToken() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";
        String token = jwtTokenService.generateToken(customerId, customerName);
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        // When
        boolean isValid = jwtTokenService.isValidToken(tamperedToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void isValidTokenForCustomer_returnsTrueForMatchingCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";
        String token = jwtTokenService.generateToken(customerId, customerName);

        // When
        boolean isValid = jwtTokenService.isValidTokenForCustomer(token, customerId);

        // Then
        assertTrue(isValid);
    }

    @Test
    void isValidTokenForCustomer_returnsFalseForDifferentCustomer() {
        // Given
        UUID customerId = UUID.randomUUID();
        UUID differentCustomerId = UUID.randomUUID();
        String customerName = "Test Customer";
        String token = jwtTokenService.generateToken(customerId, customerName);

        // When
        boolean isValid = jwtTokenService.isValidTokenForCustomer(token, differentCustomerId);

        // Then
        assertFalse(isValid);
    }

    @Test
    void validateToken_returnsValidResultForValidToken() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";
        String token = jwtTokenService.generateToken(customerId, customerName);

        // When
        JwtTokenService.TokenValidationResult result = jwtTokenService.validateToken(token);

        // Then
        assertTrue(result.valid());
        assertEquals(customerId, result.customerId());
        assertEquals(customerName, result.customerName());
        assertNull(result.errorMessage());
    }

    @Test
    void validateToken_returnsInvalidResultForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        JwtTokenService.TokenValidationResult result = jwtTokenService.validateToken(invalidToken);

        // Then
        assertFalse(result.valid());
        assertNull(result.customerId());
        assertNull(result.customerName());
        assertNotNull(result.errorMessage());
    }

    @Test
    void parseToken_throwsExceptionForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When/Then
        assertThrows(JwtException.class, () -> jwtTokenService.parseToken(invalidToken));
    }

    @Test
    void parseToken_throwsExceptionForWrongIssuer() {
        // Given - create a token with a different service (different issuer)
        JwtProperties differentProperties = new JwtProperties(SIGNING_KEY, "different-issuer", EXPIRATION_DAYS);
        JwtTokenService differentService = new JwtTokenService(differentProperties);
        
        UUID customerId = UUID.randomUUID();
        String token = differentService.generateToken(customerId, "Test Customer");

        // When/Then - parsing with original service should fail due to issuer mismatch
        assertThrows(JwtException.class, () -> jwtTokenService.parseToken(token));
    }

    @Test
    void generateToken_generatesUniqueTokenIds() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";

        // When
        String token1 = jwtTokenService.generateToken(customerId, customerName);
        String token2 = jwtTokenService.generateToken(customerId, customerName);

        // Then
        Claims claims1 = jwtTokenService.parseToken(token1);
        Claims claims2 = jwtTokenService.parseToken(token2);
        assertNotEquals(claims1.getId(), claims2.getId());
    }
}
