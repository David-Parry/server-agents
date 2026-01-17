package com.davidparry.agent.security;

import com.davidparry.agent.config.TokenHashingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Service for hashing and verifying customer JWT tokens.
 * Supports multiple secret versions for rotation.
 * Uses HMAC-SHA256 with the customer's UUID (customer_id) as the salt.
 */
@Service
public class TokenHashingService {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenHashingService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    private final TokenHashingProperties properties;
    
    public TokenHashingService(TokenHashingProperties properties) {
        this.properties = properties;
        logger.info("TokenHashingService initialized with {} secret version(s), current: {}", 
                   properties.getAvailableVersions().size(),
                   properties.currentSecretVersion());
    }
    
    /**
     * Gets the current secret version (for new tokens).
     */
    public String getCurrentSecretVersion() {
        return properties.currentSecretVersion();
    }
    
    /**
     * Gets all available secret versions.
     */
    public Set<String> getAvailableVersions() {
        return properties.getAvailableVersions();
    }
    
    /**
     * Hashes a token using the CURRENT secret version.
     * Returns both the hash as a hex string and the version used.
     * 
     * @param token The JWT token to hash
     * @param customerId The customer's UUID (used as salt)
     * @return HashResult containing the hash as hex string and version used
     */
    public HashResult hashToken(String token, UUID customerId) {
        String version = properties.currentSecretVersion();
        byte[] hash = hashTokenToBytes(token, customerId, version);
        String hexHash = bytesToHex(hash);
        return new HashResult(hexHash, version);
    }
    
    /**
     * Hashes a token using a SPECIFIC secret version and returns as hex string.
     * Used for verification against stored hashes.
     * 
     * @param token The JWT token to hash
     * @param customerId The customer's UUID (used as salt)
     * @param secretVersion The secret version to use (e.g., "V1", "V2")
     * @return The hash as a hex string (64 characters for SHA-256)
     */
    public String hashTokenWithVersion(String token, UUID customerId, String secretVersion) {
        byte[] hash = hashTokenToBytes(token, customerId, secretVersion);
        return bytesToHex(hash);
    }
    
    /**
     * Internal method to hash a token to bytes.
     */
    private byte[] hashTokenToBytes(String token, UUID customerId, String secretVersion) {
        String secret = properties.getSecret(secretVersion);
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        byte[] salt = uuidToBytes(customerId);
        
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretBytes, HMAC_ALGORITHM);
            mac.init(keySpec);
            
            // Include salt (customer UUID bytes) in the message
            mac.update(salt);
            mac.update(token.getBytes(StandardCharsets.UTF_8));
            
            return mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SecurityException("Failed to hash token with version " + secretVersion, e);
        }
    }
    
    /**
     * Verifies a token against a stored hash (hex string) using the specified secret version.
     * Uses constant-time comparison to prevent timing attacks.
     * 
     * @param token The token to verify
     * @param customerId The customer's UUID (used as salt)
     * @param storedHashHex The stored hash as hex string to compare against
     * @param secretVersion The secret version that was used to create the stored hash
     * @return true if the token matches
     */
    public boolean verifyToken(String token, UUID customerId, String storedHashHex, String secretVersion) {
        // Check if the secret version is still configured
        if (!properties.hasVersion(secretVersion)) {
            logger.warn("Attempted to verify token with unconfigured secret version: {}", secretVersion);
            return false;
        }
        
        byte[] computedHash = hashTokenToBytes(token, customerId, secretVersion);
        byte[] storedHash = hexToBytes(storedHashHex);
        return constantTimeEquals(computedHash, storedHash);
    }
    
    /**
     * Result of hashing operation, includes version for storage.
     * Hash is stored as hex string for database storage.
     */
    public record HashResult(String hash, String secretVersion) {}
    
    /**
     * Converts bytes to uppercase hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }
    
    /**
     * Converts hex string to bytes.
     */
    private static byte[] hexToBytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }
    
    /**
     * Converts a UUID to a 16-byte array.
     * UUID is 128 bits = 16 bytes, providing sufficient entropy for salt.
     */
    private static byte[] uuidToBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >>> (8 * (7 - i)));
            bytes[8 + i] = (byte) (lsb >>> (8 * (7 - i)));
        }
        return bytes;
    }
    
    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
