package com.davidparry.agent.security;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerTokenEntity;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.CustomerTokenRepository;
import com.davidparry.agent.service.SecurityAuditService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Database-backed API key (JWT token) validator.
 * 
 * Validation flow:
 * 1. Validate JWT signature and extract customerId claim
 * 2. Look up customer by customerId
 * 3. Find customer's active tokens and their secret versions
 * 4. Verify the token hash matches using the customer's UUID as salt
 * 
 * Note: Per-model token limits are now checked in PromptExecutionService
 * using CustomerUsageService, not here.
 */
@Component
public class DatabaseApiKeyValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseApiKeyValidator.class);
    
    private final CustomerTokenRepository tokenRepository;
    private final CustomerRepository customerRepository;
    private final TokenHashingService hashingService;
    private final JwtTokenService jwtTokenService;
    private final SecurityAuditService auditService;
    private final Counter authSuccessCounter;
    private final Counter authFailureCounter;
    
    public DatabaseApiKeyValidator(
            CustomerTokenRepository tokenRepository,
            CustomerRepository customerRepository,
            TokenHashingService hashingService,
            JwtTokenService jwtTokenService,
            SecurityAuditService auditService,
            MeterRegistry meterRegistry) {
        this.tokenRepository = tokenRepository;
        this.customerRepository = customerRepository;
        this.hashingService = hashingService;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
        
        this.authSuccessCounter = Counter.builder("mcp.auth.success")
            .description("Successful authentication attempts")
            .register(meterRegistry);
        this.authFailureCounter = Counter.builder("mcp.auth.failures")
            .description("Failed authentication attempts")
            .register(meterRegistry);
    }
    
    /**
     * Validates a JWT token using the following flow:
     * 1. Validate JWT signature and extract customerId claim
     * 2. Look up customer by customerId from the claim
     * 3. Find customer's active tokens with their secret versions
     * 4. Verify the token hash matches using the customer's UUID as salt
     * 
     * Note: Per-model token limits are checked separately in PromptExecutionService.
     */
    public ValidationResult validateToken(String token) {
        if (token == null || token.isBlank()) {
            authFailureCounter.increment();
            auditService.logTokenValidationFailed("Missing token");
            logger.warn("Authentication failed: missing token");
            return ValidationResult.rejected("Missing API token");
        }
        
        // Step 1: Validate JWT signature and extract customerId claim
        JwtTokenService.TokenValidationResult jwtResult = jwtTokenService.validateToken(token);
        if (!jwtResult.valid()) {
            authFailureCounter.increment();
            auditService.logTokenValidationFailed("Invalid JWT: " + jwtResult.errorMessage());
            logger.warn("Authentication failed: invalid JWT - {}", jwtResult.errorMessage());
            return ValidationResult.rejected("Invalid JWT token: " + jwtResult.errorMessage());
        }
        
        UUID customerId = jwtResult.customerId();
        logger.debug("JWT validated, extracted customerId: {}", customerId);
        
        // Step 2: Look up customer by customerId from the JWT claim
        Optional<CustomerEntity> customerOpt = customerRepository.findByCustomerId(customerId);
        if (customerOpt.isEmpty()) {
            authFailureCounter.increment();
            auditService.logTokenValidationFailed("Customer not found for customerId: " + customerId);
            logger.warn("Authentication failed: customer not found for customerId: {}", customerId);
            return ValidationResult.rejected("Customer not found");
        }
        
        CustomerEntity customer = customerOpt.get();
        
        // Check if customer is enabled
        if (!customer.isEnabled()) {
            authFailureCounter.increment();
            auditService.logTokenValidationFailed("Customer disabled: " + customerId);
            logger.warn("Authentication failed: customer disabled: {}", customerId);
            return ValidationResult.rejected("Customer account is disabled");
        }
        
        // Step 3: Find customer's active tokens with their secret versions
        List<CustomerTokenEntity> activeTokens = tokenRepository
            .findActiveTokensByCustomerId(customer.getId(), LocalDateTime.now());
        
        if (activeTokens.isEmpty()) {
            authFailureCounter.increment();
            auditService.logTokenValidationFailed("No active tokens for customer: " + customerId);
            logger.warn("Authentication failed: no active tokens for customer: {}", customerId);
            return ValidationResult.rejected("No active tokens for customer");
        }
        
        // Step 4: Verify the token hash matches using the customer's UUID as salt
        for (CustomerTokenEntity storedToken : activeTokens) {
            if (hashingService.verifyToken(
                    token, 
                    customer.getCustomerId(), 
                    storedToken.getTokenHash(),
                    storedToken.getSecretVersion())) {
                
                authSuccessCounter.increment();
                
                // Audit successful validation (async)
                auditService.logTokenValidated(
                    customer.getCustomerId(), 
                    storedToken.getSecretVersion()
                );
                
                logger.debug("Authentication successful for customer: {} ({}) using secret version {}", 
                            customer.getName(), customer.getCustomerId(), 
                            storedToken.getSecretVersion());
                return ValidationResult.valid(customer.getCustomerId().toString());
            }
        }
        
        // Token hash didn't match any stored tokens for this customer
        authFailureCounter.increment();
        auditService.logTokenValidationFailed("Token hash mismatch for customer: " + customerId);
        logger.warn("Authentication failed: token hash mismatch for customer: {}", customerId);
        return ValidationResult.rejected("Invalid API token");
    }
    
    /**
     * Checks if the validator is operational (has database connectivity).
     */
    public boolean isOperational() {
        try {
            tokenRepository.count();
            return true;
        } catch (Exception e) {
            logger.error("Database connectivity check failed", e);
            return false;
        }
    }
    
    /**
     * Result of token validation.
     * Note: Token limits are now checked per-model in CustomerUsageService.
     */
    public record ValidationResult(
        boolean valid,
        String customerId,
        String rejectionReason
    ) {
        public static ValidationResult valid(String customerId) {
            return new ValidationResult(true, customerId, null);
        }
        
        public static ValidationResult rejected(String reason) {
            return new ValidationResult(false, null, reason);
        }
    }
}
