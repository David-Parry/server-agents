package com.davidparry.agent.security;

import com.davidparry.agent.config.TokenHashingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenHashingService with multi-version secret support.
 * Hash values are stored as hex strings (64 characters for SHA-256).
 */
class TokenHashingServiceTest {

    private TokenHashingService service;
    private static final String TEST_SECRET_V1 = "test-secret-v1-for-hashing-minimum-32-characters";
    private static final String TEST_SECRET_V2 = "test-secret-v2-for-hashing-minimum-32-characters";

    @BeforeEach
    void setUp() {
        TokenHashingProperties properties = new TokenHashingProperties(
            Map.of("V1", TEST_SECRET_V1, "V2", TEST_SECRET_V2),
            "V1"
        );
        service = new TokenHashingService(properties);
    }

    @Test
    void hashToken_shouldUseCurrentVersion() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "test-jwt-token";

        // When
        TokenHashingService.HashResult result = service.hashToken(token, customerId);

        // Then
        assertEquals("V1", result.secretVersion());
        assertNotNull(result.hash());
        assertEquals(64, result.hash().length(), "SHA-256 hex string should be 64 characters");
    }

    @Test
    void hashToken_shouldProduceConsistentHash() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "test-jwt-token";

        // When
        TokenHashingService.HashResult result1 = service.hashToken(token, customerId);
        TokenHashingService.HashResult result2 = service.hashToken(token, customerId);

        // Then
        assertEquals(result1.hash(), result2.hash(), "Same token and customer ID should produce same hash");
        assertEquals(result1.secretVersion(), result2.secretVersion());
    }

    @Test
    void hashToken_withDifferentCustomerIds_shouldProduceDifferentHashes() {
        // Given
        String token = "test-jwt-token";
        UUID customerId1 = UUID.randomUUID();
        UUID customerId2 = UUID.randomUUID();

        // When
        TokenHashingService.HashResult result1 = service.hashToken(token, customerId1);
        TokenHashingService.HashResult result2 = service.hashToken(token, customerId2);

        // Then
        assertNotEquals(result1.hash(), result2.hash(), 
            "Different customer IDs should produce different hashes");
    }

    @Test
    void hashToken_withDifferentTokens_shouldProduceDifferentHashes() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token1 = "token-one";
        String token2 = "token-two";

        // When
        TokenHashingService.HashResult result1 = service.hashToken(token1, customerId);
        TokenHashingService.HashResult result2 = service.hashToken(token2, customerId);

        // Then
        assertNotEquals(result1.hash(), result2.hash(), 
            "Different tokens should produce different hashes");
    }

    @Test
    void hashToken_withDifferentVersions_shouldProduceDifferentHashes() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "test-jwt-token";

        // When
        String hashV1 = service.hashTokenWithVersion(token, customerId, "V1");
        String hashV2 = service.hashTokenWithVersion(token, customerId, "V2");

        // Then
        assertNotEquals(hashV1, hashV2, 
            "Different secret versions should produce different hashes");
    }

    @Test
    void hashToken_shouldProduce64CharHexString() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "test-token";

        // When
        TokenHashingService.HashResult result = service.hashToken(token, customerId);

        // Then
        assertEquals(64, result.hash().length(), "SHA-256 hex string should be 64 characters");
        assertTrue(result.hash().matches("[0-9A-F]+"), "Hash should be uppercase hex");
    }

    @Test
    void verifyToken_withCorrectTokenAndVersion_shouldReturnTrue() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "correct-jwt-token";
        String hash = service.hashTokenWithVersion(token, customerId, "V1");

        // When
        boolean result = service.verifyToken(token, customerId, hash, "V1");

        // Then
        assertTrue(result, "Verification should succeed with correct token and version");
    }

    @Test
    void verifyToken_withWrongToken_shouldReturnFalse() {
        // Given
        UUID customerId = UUID.randomUUID();
        String correctToken = "correct-token";
        String wrongToken = "wrong-token";
        String hash = service.hashTokenWithVersion(correctToken, customerId, "V1");

        // When
        boolean result = service.verifyToken(wrongToken, customerId, hash, "V1");

        // Then
        assertFalse(result, "Verification should fail with wrong token");
    }

    @Test
    void verifyToken_withWrongCustomerId_shouldReturnFalse() {
        // Given
        UUID correctCustomerId = UUID.randomUUID();
        UUID wrongCustomerId = UUID.randomUUID();
        String token = "test-token";
        String hash = service.hashTokenWithVersion(token, correctCustomerId, "V1");

        // When
        boolean result = service.verifyToken(token, wrongCustomerId, hash, "V1");

        // Then
        assertFalse(result, "Verification should fail with wrong customer ID");
    }

    @Test
    void verifyToken_withWrongVersion_shouldReturnFalse() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "test-token";
        
        // Hash with V1
        String hash = service.hashTokenWithVersion(token, customerId, "V1");

        // When - Verify with V2 should fail
        boolean result = service.verifyToken(token, customerId, hash, "V2");

        // Then
        assertFalse(result, "Verification should fail with wrong secret version");
    }

    @Test
    void verifyToken_withUnconfiguredVersion_shouldReturnFalse() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "test-token";
        String hash = service.hashTokenWithVersion(token, customerId, "V1");

        // When - V99 doesn't exist
        boolean result = service.verifyToken(token, customerId, hash, "V99");

        // Then
        assertFalse(result, "Verification should fail with unconfigured version");
    }

    @Test
    void verifyToken_withTamperedHash_shouldReturnFalse() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "test-token";
        String hash = service.hashTokenWithVersion(token, customerId, "V1");
        
        // Tamper with the hash (change first character)
        char firstChar = hash.charAt(0);
        char tamperedChar = firstChar == 'A' ? 'B' : 'A';
        String tamperedHash = tamperedChar + hash.substring(1);

        // When
        boolean result = service.verifyToken(token, customerId, tamperedHash, "V1");

        // Then
        assertFalse(result, "Verification should fail with tampered hash");
    }

    @Test
    void hashToken_withEmptyToken_shouldStillProduceHash() {
        // Given
        UUID customerId = UUID.randomUUID();
        String emptyToken = "";

        // When
        TokenHashingService.HashResult result = service.hashToken(emptyToken, customerId);

        // Then
        assertNotNull(result.hash());
        assertEquals(64, result.hash().length());
    }

    @Test
    void hashToken_withLongToken_shouldProduceHash() {
        // Given
        UUID customerId = UUID.randomUUID();
        String longToken = "a".repeat(10000); // Very long token

        // When
        TokenHashingService.HashResult result = service.hashToken(longToken, customerId);

        // Then
        assertNotNull(result.hash());
        assertEquals(64, result.hash().length());
    }

    @Test
    void getCurrentSecretVersion_shouldReturnConfiguredCurrent() {
        // Then
        assertEquals("V1", service.getCurrentSecretVersion());
    }

    @Test
    void getAvailableVersions_shouldReturnAllConfigured() {
        // Then
        assertTrue(service.getAvailableVersions().contains("V1"));
        assertTrue(service.getAvailableVersions().contains("V2"));
        assertEquals(2, service.getAvailableVersions().size());
    }

    @Test
    void constructor_withMismatchedCurrentVersion_shouldThrow() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            new TokenHashingProperties(
                Map.of("V1", TEST_SECRET_V1),
                "V99"  // Not in map
            );
        });
    }

    @Test
    void constructor_withShortSecret_shouldThrow() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            new TokenHashingProperties(
                Map.of("V1", "too-short"),  // Less than 32 chars
                "V1"
            );
        });
    }

    @Test
    void constructor_withInvalidVersionKey_shouldThrow() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            new TokenHashingProperties(
                Map.of("invalid-key", TEST_SECRET_V1),  // Doesn't match V\d+ pattern
                "invalid-key"
            );
        });
    }

    @Test
    void serviceWithV2Current_shouldHashWithV2() {
        // Given
        TokenHashingProperties propertiesV2 = new TokenHashingProperties(
            Map.of("V1", TEST_SECRET_V1, "V2", TEST_SECRET_V2),
            "V2"  // V2 is current
        );
        TokenHashingService serviceV2 = new TokenHashingService(propertiesV2);
        
        UUID customerId = UUID.randomUUID();
        String token = "test-token";

        // When
        TokenHashingService.HashResult result = serviceV2.hashToken(token, customerId);

        // Then
        assertEquals("V2", result.secretVersion());
    }

    @Test
    void crossVersionVerification_shouldWork() {
        // Given - Service with V2 as current
        TokenHashingProperties propertiesV2 = new TokenHashingProperties(
            Map.of("V1", TEST_SECRET_V1, "V2", TEST_SECRET_V2),
            "V2"
        );
        TokenHashingService serviceV2 = new TokenHashingService(propertiesV2);
        
        UUID customerId = UUID.randomUUID();
        String token = "test-token";
        
        // Hash with V1 using original service
        String hashV1 = service.hashTokenWithVersion(token, customerId, "V1");

        // When - Verify V1 hash using service with V2 as current
        boolean result = serviceV2.verifyToken(token, customerId, hashV1, "V1");

        // Then - Should still work because V1 is still configured
        assertTrue(result, "Should verify V1 hash even when V2 is current");
    }
    
    @Test
    void verifyToken_withLowercaseHex_shouldStillWork() {
        // Given
        UUID customerId = UUID.randomUUID();
        String token = "test-token";
        String hash = service.hashTokenWithVersion(token, customerId, "V1");
        
        // Convert to lowercase (database might store either case)
        String lowercaseHash = hash.toLowerCase();

        // When
        boolean result = service.verifyToken(token, customerId, lowercaseHash, "V1");

        // Then
        assertTrue(result, "Verification should work with lowercase hex");
    }
}
