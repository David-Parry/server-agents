package com.davidparry.agent.service;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerTokenEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.CustomerTokenRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import com.davidparry.agent.security.TokenHashingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing customer tokens with support for rotation.
 * Uses customer's UUID (customer_id) as the salt for token hashing.
 * Stores the secret version used for each token to support secret rotation.
 */
@Service
@Transactional
public class CustomerTokenService {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomerTokenService.class);
    
    private final CustomerRepository customerRepository;
    private final CustomerTokenRepository tokenRepository;
    private final PolicyTypeRepository policyTypeRepository;
    private final CustomerModelAllowanceRepository allowanceRepository;
    private final TokenHashingService hashingService;
    private final SecurityAuditService auditService;
    private final LlmModelService llmModelService;
    
    public CustomerTokenService(
            CustomerRepository customerRepository,
            CustomerTokenRepository tokenRepository,
            PolicyTypeRepository policyTypeRepository,
            CustomerModelAllowanceRepository allowanceRepository,
            TokenHashingService hashingService,
            SecurityAuditService auditService,
            LlmModelService llmModelService) {
        this.customerRepository = customerRepository;
        this.tokenRepository = tokenRepository;
        this.policyTypeRepository = policyTypeRepository;
        this.allowanceRepository = allowanceRepository;
        this.hashingService = hashingService;
        this.auditService = auditService;
        this.llmModelService = llmModelService;
    }
    
    /**
     * Registers a new token for a customer.
     * Uses the customer's UUID (customer_id) as the salt for hashing.
     * Stores the secret version used for future verification.
     * Does NOT revoke existing tokens (allows multiple active tokens for rotation).
     * 
     * @param customerId The customer's UUID
     * @param token The JWT token to register
     * @param expiresAt Optional expiration time
     */
    public void registerToken(UUID customerId, String token, LocalDateTime expiresAt) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        
        // Hash the token - returns hash and version used
        TokenHashingService.HashResult hashResult = hashingService.hashToken(token, customer.getCustomerId());
        
        CustomerTokenEntity tokenEntity = new CustomerTokenEntity();
        tokenEntity.setCustomer(customer);
        tokenEntity.setTokenHash(hashResult.hash());
        tokenEntity.setSecretVersion(hashResult.secretVersion());
        tokenEntity.setActive(true);
        tokenEntity.setExpiresAt(expiresAt);
        
        tokenEntity = tokenRepository.save(tokenEntity);
        
        // Audit log the token creation
        auditService.logTokenCreated(
            customer.getCustomerId(), 
            tokenEntity.getId(), 
            hashResult.secretVersion(),
            "CustomerTokenService"
        );
        
        logger.info("Registered new token for customer: {} ({}) using secret version {}", 
                   customer.getName(), customerId, hashResult.secretVersion());
    }
    
    /**
     * Rotates tokens for a customer:
     * 1. Registers the new token
     * 2. Optionally revokes old tokens after a grace period
     * 
     * For zero-downtime rotation, call registerToken first, then revokeOldTokens later.
     */
    public void rotateToken(UUID customerId, String newToken, LocalDateTime expiresAt, 
                           boolean revokeOldImmediately) {
        // Register new token first
        registerToken(customerId, newToken, expiresAt);
        
        if (revokeOldImmediately) {
            CustomerEntity customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow();
            
            // Get the newest token (just created)
            var activeTokens = tokenRepository.findActiveTokensByCustomerId(
                customer.getId(), LocalDateTime.now());
            
            // Revoke all but the most recent
            activeTokens.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .skip(1)
                .forEach(CustomerTokenEntity::revoke);
            
            logger.info("Rotated token for customer: {}, revoked {} old tokens", 
                       customerId, activeTokens.size() - 1);
        }
    }
    
    /**
     * Revokes all tokens for a customer.
     */
    public int revokeAllTokens(UUID customerId) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        
        int revoked = tokenRepository.revokeAllTokensForCustomer(
            customer.getId(), LocalDateTime.now());
        
        logger.info("Revoked {} tokens for customer: {}", revoked, customerId);
        return revoked;
    }
    
    /**
     * Creates a new customer with an initial token and default unlimited policy type.
     * Links all enabled models to the customer with unlimited access.
     */
    public CustomerEntity createCustomer(String name, String initialToken) {
        PolicyTypeEntity defaultPolicyType = policyTypeRepository.findUnlimitedPolicyType()
            .orElseThrow(() -> new IllegalStateException("No unlimited policy type found"));
        return createCustomerWithPolicyType(name, defaultPolicyType, null, true, initialToken);
    }
    
    /**
     * Creates a new customer with an initial token and specified policy type for all model allowances.
     * Links all enabled models to the customer with specified default allowance.
     * 
     * @param name Customer name
     * @param policyType The policy type to assign to all model allowances
     * @param defaultAllowance Default token allowance for all models (null = use model defaults)
     * @param unlimited If true, set all model allowances to unlimited
     * @param initialToken The initial authentication token
     */
    public CustomerEntity createCustomerWithPolicyType(String name, PolicyTypeEntity policyType, 
                                                        Long defaultAllowance, boolean unlimited,
                                                        String initialToken) {
        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(UUID.randomUUID());
        customer.setName(name);
        customer.setEnabled(true);
        
        customer = customerRepository.save(customer);
        
        // Link all enabled models to this customer with the specified policy type
        llmModelService.linkAllModelsToCustomer(customer, policyType, defaultAllowance, unlimited);
        
        // Register initial token using the new customer's UUID as salt
        registerToken(customer.getCustomerId(), initialToken, null);
        
        logger.info("Created customer: {} with ID {} (policy: {}, unlimited: {}, default: {})", 
                   name, customer.getCustomerId(), policyType.getName(), unlimited, defaultAllowance);
        return customer;
    }
    
    /**
     * Creates a new customer with a specific UUID (for migration scenarios).
     * Links all enabled models to the customer with specified default allowance.
     */
    public CustomerEntity createCustomerWithId(UUID customerId, String name, 
                                               PolicyTypeEntity policyType, Long defaultAllowance,
                                               boolean unlimited, String initialToken) {
        if (customerRepository.existsByCustomerId(customerId)) {
            throw new IllegalArgumentException("Customer already exists: " + customerId);
        }
        
        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(customerId);
        customer.setName(name);
        customer.setEnabled(true);
        
        customer = customerRepository.save(customer);
        
        // Link all enabled models to this customer with the specified policy type
        llmModelService.linkAllModelsToCustomer(customer, policyType, defaultAllowance, unlimited);
        
        // Register initial token
        registerToken(customerId, initialToken, null);
        
        logger.info("Created customer: {} with specified ID {} (policy: {}, unlimited: {}, default: {})", 
                   name, customerId, policyType.getName(), unlimited, defaultAllowance);
        return customer;
    }
    
    /**
     * Updates the policy type for all allowances of a customer.
     * This is a bulk operation that cascades to all model allowances.
     */
    public int updateAllowancesPolicyType(UUID customerId, PolicyTypeEntity newPolicyType) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        
        int updated = allowanceRepository.updatePolicyTypeForCustomer(
            customer.getId(), newPolicyType, LocalDateTime.now());
        
        logger.info("Updated policy type for customer {} to {} ({} allowances)", 
                   customerId, newPolicyType.getName(), updated);
        return updated;
    }
}
