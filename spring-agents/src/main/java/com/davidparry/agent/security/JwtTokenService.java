package com.davidparry.agent.security;

import com.davidparry.agent.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * Service for generating and validating JWT tokens for customer authentication.
 * JWTs include the customerId as a claim, which is used for identification and
 * as a salt for token hashing.
 */
@Service
public class JwtTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenService.class);

    public static final String CLAIM_CUSTOMER_ID = "customerId";
    public static final String CLAIM_CUSTOMER_NAME = "customerName";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_API = "API";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.signingKey().getBytes(StandardCharsets.UTF_8));
        LOGGER.info("JwtTokenService initialized with issuer: {}, expiration: {} days",
                   properties.issuer(), properties.expirationDays());
    }

    /**
     * Generates a JWT token for a customer.
     *
     * @param customerId The customer's UUID (included as a claim)
     * @param customerName The customer's name (included as a claim for convenience)
     * @return A signed JWT token string
     */
    public String generateToken(UUID customerId, String customerName) {
        return generateToken(customerId, customerName, null);
    }

    /**
     * Generates a JWT token for a customer with optional custom expiration.
     *
     * @param customerId The customer's UUID (included as a claim)
     * @param customerName The customer's name (included as a claim for convenience)
     * @param expiresAt Optional expiration time (null uses default from properties)
     * @return A signed JWT token string
     */
    public String generateToken(UUID customerId, String customerName, LocalDateTime expiresAt) {
        Instant now = Instant.now();

        var builder = Jwts.builder()
            .id(UUID.randomUUID().toString())  // Unique token ID (jti)
            .issuer(properties.issuer())
            .subject(customerId.toString())
            .issuedAt(Date.from(now))
            .claim(CLAIM_CUSTOMER_ID, customerId.toString())
            .claim(CLAIM_CUSTOMER_NAME, customerName)
            .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_API);

        // Set expiration
        if (expiresAt != null) {
            builder.expiration(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()));
        } else if (properties.expirationDays() > 0) {
            builder.expiration(Date.from(now.plusSeconds(properties.expirationDays() * 24L * 60 * 60)));
        }
        // If expirationDays is 0 and no custom expiration, token doesn't expire

        String token = builder.signWith(signingKey).compact();

        LOGGER.debug("Generated JWT for customer: {} ({})", customerName, customerId);
        return token;
    }

    /**
     * Parses and validates a JWT token.
     *
     * @param token The JWT token string
     * @return The parsed claims if valid
     * @throws JwtException if the token is invalid or expired
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(properties.issuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Extracts the customer ID from a JWT token.
     *
     * @param token The JWT token string
     * @return The customer UUID
     * @throws JwtException if the token is invalid
     */
    public UUID extractCustomerId(String token) {
        Claims claims = parseToken(token);
        String customerIdStr = claims.get(CLAIM_CUSTOMER_ID, String.class);
        if (customerIdStr == null) {
            throw new JwtException("Token missing customerId claim");
        }
        return UUID.fromString(customerIdStr);
    }

    /**
     * Validates a JWT token without throwing exceptions.
     *
     * @param token The JWT token string
     * @return true if the token is valid and not expired
     */
    public boolean isValidToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            LOGGER.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates that a JWT token belongs to a specific customer.
     *
     * @param token The JWT token string
     * @param expectedCustomerId The expected customer UUID
     * @return true if the token is valid and belongs to the customer
     */
    public boolean isValidTokenForCustomer(String token, UUID expectedCustomerId) {
        try {
            UUID tokenCustomerId = extractCustomerId(token);
            return expectedCustomerId.equals(tokenCustomerId);
        } catch (JwtException e) {
            LOGGER.debug("Invalid JWT token for customer {}: {}", expectedCustomerId, e.getMessage());
            return false;
        }
    }

    /**
     * Result of token validation with extracted information.
     */
    public record TokenValidationResult(
        boolean valid,
        UUID customerId,
        String customerName,
        String errorMessage
    ) {
        public static TokenValidationResult valid(UUID customerId, String customerName) {
            return new TokenValidationResult(true, customerId, customerName, null);
        }

        public static TokenValidationResult invalid(String errorMessage) {
            return new TokenValidationResult(false, null, null, errorMessage);
        }
    }

    /**
     * Validates a token and returns detailed result.
     *
     * @param token The JWT token string
     * @return Validation result with customer info if valid
     */
    public TokenValidationResult validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            String customerIdStr = claims.get(CLAIM_CUSTOMER_ID, String.class);
            String customerName = claims.get(CLAIM_CUSTOMER_NAME, String.class);

            if (customerIdStr == null) {
                return TokenValidationResult.invalid("Token missing customerId claim");
            }

            return TokenValidationResult.valid(UUID.fromString(customerIdStr), customerName);
        } catch (JwtException e) {
            return TokenValidationResult.invalid(e.getMessage());
        }
    }
}
