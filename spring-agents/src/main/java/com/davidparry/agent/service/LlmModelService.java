package com.davidparry.agent.service;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.LlmModelRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing LLM models and their linkage to customers.
 * When a new model is added, it automatically creates allowances for all existing customers.
 */
@Service
@Transactional
public class LlmModelService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmModelService.class);

    private final LlmModelRepository modelRepository;
    private final CustomerRepository customerRepository;
    private final CustomerModelAllowanceRepository allowanceRepository;
    private final PolicyTypeRepository policyTypeRepository;

    public LlmModelService(
            LlmModelRepository modelRepository,
            CustomerRepository customerRepository,
            CustomerModelAllowanceRepository allowanceRepository,
            PolicyTypeRepository policyTypeRepository) {
        this.modelRepository = modelRepository;
        this.customerRepository = customerRepository;
        this.allowanceRepository = allowanceRepository;
        this.policyTypeRepository = policyTypeRepository;
    }

    /**
     * Creates a new LLM model and links it to all existing customers.
     * Each customer gets an allowance with the model's default_tokens_for_new_customers value.
     * Uses the default UNLIMITED policy type for new allowances.
     */
    public LlmModelEntity createModel(String model, String provider, String displayName,
                                       String description, Long defaultTokens) {
        if (modelRepository.existsById(model)) {
            throw new IllegalArgumentException("Model already exists: " + model);
        }

        LlmModelEntity llmModel = new LlmModelEntity();
        llmModel.setModel(model);
        llmModel.setProvider(provider);
        llmModel.setDisplayName(displayName);
        llmModel.setDescription(description);
        llmModel.setDefaultTokensForNewCustomers(defaultTokens != null ? defaultTokens : 0L);
        llmModel.setEnabled(true);

        llmModel = modelRepository.save(llmModel);

        // Link to all existing customers with default unlimited policy
        PolicyTypeEntity defaultPolicy = policyTypeRepository.findUnlimitedPolicyType()
            .orElse(null);
        linkModelToAllCustomers(llmModel, defaultPolicy);

        LOGGER.info("Created model {} and linked to all customers with default tokens: {}",
                   model, defaultTokens);

        return llmModel;
    }

    /**
     * Links a model to all existing customers with the model's default token allowance.
     * Sets the specified policy type on each allowance.
     */
    public int linkModelToAllCustomers(LlmModelEntity model, PolicyTypeEntity policyType) {
        List<CustomerEntity> customers = customerRepository.findAll();
        int linked = 0;

        for (CustomerEntity customer : customers) {
            // Check if allowance already exists
            if (!allowanceRepository.existsByCustomerAndLlmModel(customer, model)) {
                CustomerModelAllowanceEntity allowance = new CustomerModelAllowanceEntity();
                allowance.setCustomer(customer);
                allowance.setLlmModel(model);
                allowance.setPolicyType(policyType);
                allowance.setAllowedTokens(model.getDefaultTokensForNewCustomers());
                allowance.setTokensUsed(0L);
                allowance.setTokensResetAt(LocalDateTime.now());
                allowance.setEnabled(true);

                allowanceRepository.save(allowance);
                linked++;
            }
        }

        LOGGER.info("Linked model {} to {} customers with policy {}",
                   model.getModel(), linked, policyType != null ? policyType.getName() : "NONE");
        return linked;
    }

    /**
     * Links all enabled models to a specific customer.
     * Used when creating a new customer.
     * Sets the specified policy type on each allowance.
     *
     * @param customer The customer to link models to
     * @param policyType The policy type to assign to all allowances
     * @param defaultAllowance Default token allowance for all models (null = use model's default)
     * @param unlimited If true, set all allowances to unlimited (NULL)
     */
    public int linkAllModelsToCustomer(CustomerEntity customer, PolicyTypeEntity policyType,
                                        Long defaultAllowance, boolean unlimited) {
        List<LlmModelEntity> models = modelRepository.findByEnabledTrue();
        int linked = 0;

        for (LlmModelEntity model : models) {
            if (!allowanceRepository.existsByCustomerAndLlmModel(customer, model)) {
                CustomerModelAllowanceEntity allowance = new CustomerModelAllowanceEntity();
                allowance.setCustomer(customer);
                allowance.setLlmModel(model);
                allowance.setPolicyType(policyType);

                if (unlimited) {
                    allowance.setAllowedTokens(null); // Unlimited
                } else if (defaultAllowance != null) {
                    allowance.setAllowedTokens(defaultAllowance);
                } else {
                    allowance.setAllowedTokens(model.getDefaultTokensForNewCustomers());
                }

                allowance.setTokensUsed(0L);
                allowance.setTokensResetAt(LocalDateTime.now());
                allowance.setEnabled(true);

                allowanceRepository.save(allowance);
                linked++;
            }
        }

        LOGGER.info("Linked {} models to customer {} (policy: {}, unlimited: {}, default: {})",
                   linked, customer.getCustomerId(),
                   policyType != null ? policyType.getName() : "NONE",
                   unlimited, defaultAllowance);
        return linked;
    }

    /**
     * Gets all enabled models.
     */
    public List<LlmModelEntity> getAllEnabledModels() {
        return modelRepository.findByEnabledTrue();
    }

    /**
     * Gets all models.
     */
    public List<LlmModelEntity> getAllModels() {
        return modelRepository.findAll();
    }

    /**
     * Gets a model by ID.
     */
    public LlmModelEntity getModel(String model) {
        return modelRepository.findById(model)
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + model));
    }

    /**
     * Updates the default tokens for new customers for a model.
     */
    public LlmModelEntity updateDefaultTokens(String model, Long defaultTokens) {
        LlmModelEntity llmModel = modelRepository.findById(model)
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + model));

        llmModel.setDefaultTokensForNewCustomers(defaultTokens);
        return modelRepository.save(llmModel);
    }

    /**
     * Enables or disables a model.
     */
    public LlmModelEntity setModelEnabled(String model, boolean enabled) {
        LlmModelEntity llmModel = modelRepository.findById(model)
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + model));

        llmModel.setEnabled(enabled);
        return modelRepository.save(llmModel);
    }
}
