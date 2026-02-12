package com.davidparry.agent.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for JWT token generation.
 * The signing key should be at least 256 bits (32 bytes) for HS256.
 */
@Validated
@ConfigurationProperties(prefix = "agent.security.jwt")
public record JwtProperties(
    /**
     * Secret key for signing JWTs. Must be at least 32 characters (256 bits) for HS256.
     * In production, use a securely generated key via environment variable.
     */
    @NotBlank(message = "JWT signing key must be configured")
    String signingKey,
    
    /**
     * Issuer claim for the JWT.
     */
    @NotBlank(message = "JWT issuer must be configured")
    String issuer,
    
    /**
     * Default token expiration in days. Set to 0 for non-expiring tokens.
     */
    @Min(value = 0, message = "Token expiration days must be 0 or greater (0 = never expires)")
    int expirationDays
) {
    /**
     * Compact constructor with validation.
     */
    public JwtProperties {
        if (signingKey != null && signingKey.length() < 32) {
            throw new IllegalArgumentException(
                "JWT signing key must be at least 32 characters (256 bits) for HS256 security"
            );
        }
    }
}
