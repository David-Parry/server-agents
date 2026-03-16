package com.davidparry.agent.service;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.LlmModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing customer token usage per model.
 * Handles usage tracking, limit validation, and automatic resets based on policy.
 *
 * Key behaviors:
 * - If no allowance exists, auto-creates one with allowed_tokens = 0
 * - allowed_tokens = 0 means "no access" (limit exceeded immediately)
 * - allowed_tokens = NULL means "unlimited"
 * - Automatically resets usage when policy period elapses
 */
@Service
@Transactional
public class CustomerUsageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerUsageService.class);

    private final CustomerRepository customerRepository;
    private final CustomerModelAllowanceRepository allowanceRepository;
    private final LlmModelRepository modelRepository;

    public CustomerUsageService(
            CustomerRepository customerRepository,
            CustomerModelAllowanceRepository allowanceRepository,
            LlmModelRepository modelRepository) {
        this.customerRepository = customerRepository;
        this.allowanceRepository = allowanceRepository;
        this.modelRepository = modelRepository;
    }

    /**
     * Validates if a customer can use a specific model.
     *
     * Flow:
     * 1. Find or create allowance (creates with allowed_tokens = 0 if missing)
     * 2. Check if policy period has elapsed, reset if needed (uses allowance's policy type)
     * 3. Check if limit exceeded (0 = no access, NULL = unlimited)
     *
     * @param customerId The customer's external UUID (from JWT)
     * @param model The model identifier (from agent_config)
     * @return ValidationResult with status and details
     */
    public UsageValidationResult validateUsage(UUID customerId, String model) {
        // Find customer
        Optional<CustomerEntity> customerOpt = customerRepository.findByCustomerId(customerId);
        if (customerOpt.isEmpty()) {
            LOGGER.warn("Customer not found: {}", customerId);
            return UsageValidationResult.customerNotFound(customerId);
        }

        CustomerEntity customer = customerOpt.get();

        // Find or create allowance
        CustomerModelAllowanceEntity allowance = getOrCreateAllowance(customer, model);

        // Get the policy type from the allowance (each allowance has its own policy)
        PolicyTypeEntity policy = allowance.getPolicyType();

        // Check if usage should be reset based on the allowance's policy
        if (policy != null && !policy.isUnlimited()) {
            Integer resetDays = policy.getResetDays();
            if (allowance.shouldResetUsage(resetDays)) {
                LOGGER.info("Resetting usage for customer {} on model {} (policy: {}, reset_days: {})",
                           customerId, model, policy.getName(), resetDays);
                allowance.resetUsage();
                allowanceRepository.save(allowance);
            }
        }

        // Check if customer has any access to this model
        if (!allowance.hasAccess()) {
            LOGGER.warn("No access for customer {} on model {} (allowed_tokens = 0)",
                       customerId, model);
            return UsageValidationResult.noAccess(
                customerId, model,
                "No token allowance configured for this model. Contact administrator."
            );
        }

        // Check if limit exceeded
        if (allowance.hasExceededLimit()) {
            Integer daysUntilReset = calculateDaysUntilReset(allowance, policy);
            LOGGER.warn("Token limit exceeded for customer {} on model {} (used: {}, limit: {}, resets in: {} days)",
                       customerId, model, allowance.getTokensUsed(), allowance.getAllowedTokens(), daysUntilReset);
            return UsageValidationResult.limitExceeded(
                customerId, model,
                allowance.getTokensUsed(),
                allowance.getAllowedTokens(),
                daysUntilReset
            );
        }

        // Check notification threshold status
        boolean belowThreshold = allowance.isBelowNotificationThreshold();
        boolean shouldNotify = allowance.shouldSendNotification();
        Long remainingTokens = allowance.getRemainingTokens();
        Long notificationThreshold = allowance.getEffectiveNotificationThreshold();

        if (belowThreshold) {
            LOGGER.warn("Low token warning for customer {} on model {} (remaining: {}, threshold: {})",
                       customerId, model, remainingTokens, notificationThreshold);
        }

        return UsageValidationResult.allowed(
            customerId, model,
            allowance.getTokensUsed(),
            allowance.getAllowedTokens(),
            allowance.isUnlimited(),
            calculateDaysUntilReset(allowance, policy),
            remainingTokens,
            notificationThreshold,
            belowThreshold,
            shouldNotify
        );
    }

    /**
     * Gets or creates an allowance for a customer and model.
     * If created, uses allowed_tokens = 0 (no access by default).
     */
    private CustomerModelAllowanceEntity getOrCreateAllowance(CustomerEntity customer, String model) {
        Optional<CustomerModelAllowanceEntity> existingOpt =
            allowanceRepository.findByCustomerIdAndModel(customer.getId(), model);

        if (existingOpt.isPresent()) {
            return existingOpt.get();
        }

        // Auto-create with 0 tokens (no access)
        LOGGER.info("Auto-creating allowance for customer {} on model {} with allowed_tokens = 0",
                   customer.getCustomerId(), model);

        LlmModelEntity llmModel = modelRepository.findById(model)
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + model));

        CustomerModelAllowanceEntity allowance = new CustomerModelAllowanceEntity();
        allowance.setCustomer(customer);
        allowance.setLlmModel(llmModel);
        allowance.setAllowedTokens(0L);  // No access by default
        allowance.setTokensUsed(0L);
        allowance.setTokensResetAt(LocalDateTime.now());
        allowance.setEnabled(true);

        return allowanceRepository.save(allowance);
    }

    /**
     * Records token usage for a customer on a specific model.
     */
    public void recordUsage(UUID customerId, String model, long tokensUsed) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        CustomerModelAllowanceEntity allowance = getOrCreateAllowance(customer, model);
        allowance.incrementTokensUsed(tokensUsed);
        allowanceRepository.save(allowance);

        LOGGER.debug("Recorded {} tokens for customer {} on model {} (total: {})",
                    tokensUsed, customerId, model, allowance.getTokensUsed());
    }

    /**
     * Gets all model allowances for a customer.
     */
    public List<CustomerModelAllowanceEntity> getCustomerAllowances(UUID customerId) {
        return allowanceRepository.findByCustomerCustomerIdWithModel(customerId);
    }

    /**
     * Sets the allowance for a customer on a model.
     *
     * @param allowedTokens NULL = unlimited, 0 = no access, > 0 = specific limit
     */
    public CustomerModelAllowanceEntity setAllowance(UUID customerId, String model, Long allowedTokens) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        CustomerModelAllowanceEntity allowance = getOrCreateAllowance(customer, model);
        allowance.setAllowedTokens(allowedTokens);

        LOGGER.info("Set allowance for customer {} on model {} to {} tokens",
                   customerId, model, allowedTokens == null ? "UNLIMITED" : allowedTokens);

        return allowanceRepository.save(allowance);
    }

    /**
     * Sets unlimited access for a customer on a model.
     */
    public CustomerModelAllowanceEntity setUnlimited(UUID customerId, String model) {
        return setAllowance(customerId, model, null);
    }

    /**
     * Sets unlimited access for a customer on ALL models.
     */
    public int setUnlimitedForAllModels(UUID customerId) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        List<LlmModelEntity> models = modelRepository.findByEnabledTrue();
        int updated = 0;

        for (LlmModelEntity model : models) {
            CustomerModelAllowanceEntity allowance = getOrCreateAllowance(customer, model.getModel());
            allowance.setAllowedTokens(null); // Unlimited
            allowanceRepository.save(allowance);
            updated++;
        }

        LOGGER.info("Set unlimited access for customer {} on {} models", customerId, updated);
        return updated;
    }

    /**
     * Sets a specific allowance for a customer on ALL models.
     */
    public int setAllowanceForAllModels(UUID customerId, Long allowedTokens) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        List<LlmModelEntity> models = modelRepository.findByEnabledTrue();
        int updated = 0;

        for (LlmModelEntity model : models) {
            CustomerModelAllowanceEntity allowance = getOrCreateAllowance(customer, model.getModel());
            allowance.setAllowedTokens(allowedTokens);
            allowanceRepository.save(allowance);
            updated++;
        }

        LOGGER.info("Set allowance {} for customer {} on {} models", allowedTokens, customerId, updated);
        return updated;
    }

    /**
     * Resets usage for a specific customer and model.
     */
    public void resetUsage(UUID customerId, String model) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        Optional<CustomerModelAllowanceEntity> allowanceOpt =
            allowanceRepository.findByCustomerIdAndModel(customer.getId(), model);

        if (allowanceOpt.isPresent()) {
            CustomerModelAllowanceEntity allowance = allowanceOpt.get();
            allowance.resetUsage();
            allowanceRepository.save(allowance);
            LOGGER.info("Reset usage for customer {} on model {}", customerId, model);
        }
    }

    /**
     * Resets usage for all models for a customer.
     */
    public void resetAllUsage(UUID customerId) {
        List<CustomerModelAllowanceEntity> allowances =
            allowanceRepository.findByCustomerCustomerIdWithModel(customerId);

        for (CustomerModelAllowanceEntity allowance : allowances) {
            allowance.resetUsage();
        }

        allowanceRepository.saveAll(allowances);
        LOGGER.info("Reset all usage for customer {} ({} models)", customerId, allowances.size());
    }

    /**
     * Sets the allowance for a customer on a model with a specific policy type.
     *
     * @param customerId The customer's external UUID
     * @param model The model identifier
     * @param allowedTokens NULL = unlimited, 0 = no access, > 0 = specific limit
     * @param policyType The policy type to assign to this allowance (can be null)
     * @return The updated allowance entity
     */
    public CustomerModelAllowanceEntity setAllowanceWithPolicyType(
            UUID customerId, String model, Long allowedTokens, PolicyTypeEntity policyType) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        CustomerModelAllowanceEntity allowance = getOrCreateAllowance(customer, model);
        allowance.setAllowedTokens(allowedTokens);
        allowance.setPolicyType(policyType);

        LOGGER.info("Set allowance for customer {} on model {} to {} tokens with policy {}",
                   customerId, model,
                   allowedTokens == null ? "UNLIMITED" : allowedTokens,
                   policyType != null ? policyType.getName() : "NONE");

        return allowanceRepository.save(allowance);
    }

    /**
     * Sets the policy type for a specific allowance.
     *
     * @param customerId The customer's external UUID
     * @param model The model identifier
     * @param policyType The policy type to assign
     * @return The updated allowance entity
     */
    public CustomerModelAllowanceEntity setAllowancePolicyType(
            UUID customerId, String model, PolicyTypeEntity policyType) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        CustomerModelAllowanceEntity allowance = getOrCreateAllowance(customer, model);
        allowance.setPolicyType(policyType);

        LOGGER.info("Set policy type for customer {} on model {} to {}",
                   customerId, model, policyType != null ? policyType.getName() : "NONE");

        return allowanceRepository.save(allowance);
    }

    /**
     * Sets the policy type for ALL allowances of a customer.
     * This is a cascade update that applies the same policy to all models.
     *
     * @param customerId The customer's external UUID
     * @param policyType The policy type to assign to all allowances
     * @return The number of allowances updated
     */
    public int setPolicyTypeForAllAllowances(UUID customerId, PolicyTypeEntity policyType) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        int updated = allowanceRepository.updatePolicyTypeForCustomer(
            customer.getId(), policyType, LocalDateTime.now());

        LOGGER.info("Set policy type {} for customer {} on {} allowances",
                   policyType != null ? policyType.getName() : "NONE", customerId, updated);

        return updated;
    }

    /**
     * Sets the allowance and policy type for ALL models for a customer.
     * Creates allowances for models that don't have one yet.
     *
     * @param customerId The customer's external UUID
     * @param allowedTokens NULL = unlimited, 0 = no access, > 0 = specific limit
     * @param policyType The policy type to assign to all allowances
     * @return The number of allowances updated/created
     */
    public int setAllowanceAndPolicyTypeForAllModels(
            UUID customerId, Long allowedTokens, PolicyTypeEntity policyType) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        List<LlmModelEntity> models = modelRepository.findByEnabledTrue();
        int updated = 0;

        for (LlmModelEntity model : models) {
            CustomerModelAllowanceEntity allowance = getOrCreateAllowance(customer, model.getModel());
            allowance.setAllowedTokens(allowedTokens);
            allowance.setPolicyType(policyType);
            allowanceRepository.save(allowance);
            updated++;
        }

        LOGGER.info("Set allowance {} with policy {} for customer {} on {} models",
                   allowedTokens == null ? "UNLIMITED" : allowedTokens,
                   policyType != null ? policyType.getName() : "NONE",
                   customerId, updated);

        return updated;
    }

    /**
     * Gets all model allowances for a customer with policy types loaded.
     */
    public List<CustomerModelAllowanceEntity> getCustomerAllowancesWithPolicyType(UUID customerId) {
        return allowanceRepository.findByCustomerCustomerIdWithModelAndPolicyType(customerId);
    }

    private Integer calculateDaysUntilReset(CustomerModelAllowanceEntity allowance, PolicyTypeEntity policy) {
        if (policy == null || policy.isUnlimited()) {
            return null;
        }

        Integer resetDays = policy.getResetDays();
        if (resetDays == null || allowance.getTokensResetAt() == null) {
            return null;
        }

        LocalDateTime nextReset = allowance.getTokensResetAt().plusDays(resetDays);
        long daysUntil = ChronoUnit.DAYS.between(LocalDateTime.now(), nextReset);
        return Math.max(0, (int) daysUntil);
    }

    /**
     * Sets the notification threshold for a customer on a specific model.
     *
     * @param customerId The customer's external UUID
     * @param model The model identifier
     * @param threshold The notification threshold (NULL to use customer's default)
     * @return The updated allowance entity
     */
    public CustomerModelAllowanceEntity setNotificationThreshold(
            UUID customerId, String model, Long threshold) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        CustomerModelAllowanceEntity allowance = getOrCreateAllowance(customer, model);
        allowance.setMinTokenNotificationThreshold(threshold);

        LOGGER.info("Set notification threshold for customer {} on model {} to {} tokens",
                   customerId, model, threshold != null ? threshold : "DEFAULT");

        return allowanceRepository.save(allowance);
    }

    /**
     * Gets an allowance by customer ID and model, with customer loaded for notification threshold fallback.
     */
    public Optional<CustomerModelAllowanceEntity> getAllowanceWithCustomer(UUID customerId, String model) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId).orElse(null);
        if (customer == null) {
            return Optional.empty();
        }
        return allowanceRepository.findByCustomerIdAndModel(customer.getId(), model);
    }

    /**
     * Result of usage validation.
     */
    public record UsageValidationResult(
        boolean allowed,
        boolean limitExceeded,
        boolean noAccess,
        boolean customerNotFound,
        boolean belowNotificationThreshold,
        boolean shouldSendNotification,
        UUID customerId,
        String model,
        Long tokensUsed,
        Long tokenLimit,
        Long remainingTokens,
        boolean unlimited,
        Integer daysUntilReset,
        Long notificationThreshold,
        String message
    ) {
        public static UsageValidationResult allowed(UUID customerId, String model,
                                                     Long tokensUsed, Long tokenLimit,
                                                     boolean unlimited, Integer daysUntilReset,
                                                     Long remainingTokens, Long notificationThreshold,
                                                     boolean belowNotificationThreshold,
                                                     boolean shouldSendNotification) {
            return new UsageValidationResult(
                true, false, false, false,
                belowNotificationThreshold, shouldSendNotification,
                customerId, model,
                tokensUsed, tokenLimit, remainingTokens,
                unlimited, daysUntilReset, notificationThreshold, null
            );
        }

        public static UsageValidationResult limitExceeded(UUID customerId, String model,
                                                           Long tokensUsed, Long tokenLimit,
                                                           Integer daysUntilReset) {
            String msg = String.format("Token limit exceeded (used: %d, limit: %d)%s",
                                       tokensUsed, tokenLimit,
                                       daysUntilReset != null ? ". Resets in " + daysUntilReset + " days." : ".");
            return new UsageValidationResult(
                false, true, false, false, false, false,
                customerId, model,
                tokensUsed, tokenLimit, 0L,
                false, daysUntilReset, null, msg
            );
        }

        public static UsageValidationResult noAccess(UUID customerId, String model, String message) {
            return new UsageValidationResult(
                false, false, true, false, false, false,
                customerId, model,
                0L, 0L, 0L,
                false, null, null, message
            );
        }

        public static UsageValidationResult customerNotFound(UUID customerId) {
            return new UsageValidationResult(
                false, false, false, true, false, false,
                customerId, null,
                null, null, null,
                false, null, null, "Customer not found"
            );
        }
    }
}
