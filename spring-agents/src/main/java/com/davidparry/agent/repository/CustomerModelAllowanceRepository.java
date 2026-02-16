package com.davidparry.agent.repository;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing CustomerModelAllowanceEntity persistence.
 */
@Repository
public interface CustomerModelAllowanceRepository extends JpaRepository<CustomerModelAllowanceEntity, UUID> {

    /**
     * Find allowance by customer and model entities.
     */
    Optional<CustomerModelAllowanceEntity> findByCustomerAndLlmModel(
        CustomerEntity customer, LlmModelEntity model);

    /**
     * Check if allowance exists for customer and model.
     */
    boolean existsByCustomerAndLlmModel(CustomerEntity customer, LlmModelEntity model);

    /**
     * Find allowance by customer ID (internal) and model string.
     */
    @Query("SELECT cma FROM CustomerModelAllowanceEntity cma "
           + "WHERE cma.customer.id = :customerId AND cma.llmModel.model = :model "
           + "AND cma.enabled = true")
    Optional<CustomerModelAllowanceEntity> findByCustomerIdAndModel(UUID customerId, String model);

    /**
     * Find allowance by customer's external UUID and model string.
     * This is the primary lookup method used during request validation.
     */
    @Query("SELECT cma FROM CustomerModelAllowanceEntity cma "
           + "JOIN FETCH cma.customer c "
           + "JOIN FETCH cma.llmModel "
           + "WHERE c.customerId = :customerId AND cma.llmModel.model = :model "
           + "AND cma.enabled = true AND c.enabled = true")
    Optional<CustomerModelAllowanceEntity> findByCustomerCustomerIdAndModel(UUID customerId, String model);

    /**
     * Find all allowances for a customer.
     */
    List<CustomerModelAllowanceEntity> findByCustomer(CustomerEntity customer);

    /**
     * Find all allowances for a customer with model eagerly loaded.
     */
    @Query("SELECT cma FROM CustomerModelAllowanceEntity cma "
           + "JOIN FETCH cma.llmModel "
           + "WHERE cma.customer.id = :customerId AND cma.enabled = true")
    List<CustomerModelAllowanceEntity> findByCustomerIdWithModel(UUID customerId);

    /**
     * Find all allowances for a customer by external UUID.
     */
    @Query("SELECT cma FROM CustomerModelAllowanceEntity cma "
           + "JOIN FETCH cma.llmModel "
           + "JOIN FETCH cma.customer c "
           + "WHERE c.customerId = :customerId AND cma.enabled = true")
    List<CustomerModelAllowanceEntity> findByCustomerCustomerIdWithModel(UUID customerId);

    /**
     * Find all allowances for a model (for bulk operations).
     */
    List<CustomerModelAllowanceEntity> findByLlmModel(LlmModelEntity model);

    /**
     * Update tokens used for an allowance.
     */
    @Modifying
    @Query("UPDATE CustomerModelAllowanceEntity cma "
           + "SET cma.tokensUsed = cma.tokensUsed + :tokensUsed, cma.updatedAt = :now "
           + "WHERE cma.id = :allowanceId")
    int incrementTokensUsed(UUID allowanceId, long tokensUsed, LocalDateTime now);

    /**
     * Reset usage for an allowance.
     */
    @Modifying
    @Query("UPDATE CustomerModelAllowanceEntity cma "
           + "SET cma.tokensUsed = 0, cma.tokensResetAt = :now, cma.updatedAt = :now "
           + "WHERE cma.id = :allowanceId")
    int resetUsage(UUID allowanceId, LocalDateTime now);

    /**
     * Find allowance with policy type eagerly loaded.
     */
    @Query("SELECT cma FROM CustomerModelAllowanceEntity cma "
           + "JOIN FETCH cma.customer c "
           + "JOIN FETCH cma.llmModel "
           + "LEFT JOIN FETCH cma.policyType "
           + "WHERE c.customerId = :customerId AND cma.llmModel.model = :model "
           + "AND cma.enabled = true AND c.enabled = true")
    Optional<CustomerModelAllowanceEntity> findByCustomerCustomerIdAndModelWithPolicyType(
        UUID customerId, String model);

    /**
     * Find all allowances for a customer with policy types loaded.
     */
    @Query("SELECT cma FROM CustomerModelAllowanceEntity cma "
           + "JOIN FETCH cma.llmModel "
           + "JOIN FETCH cma.customer c "
           + "LEFT JOIN FETCH cma.policyType "
           + "WHERE c.customerId = :customerId AND cma.enabled = true")
    List<CustomerModelAllowanceEntity> findByCustomerCustomerIdWithModelAndPolicyType(UUID customerId);

    /**
     * Find all allowances using a specific policy type.
     */
    List<CustomerModelAllowanceEntity> findByPolicyType(PolicyTypeEntity policyType);

    /**
     * Find all allowances for a customer (by internal ID) with policy type.
     */
    @Query("SELECT cma FROM CustomerModelAllowanceEntity cma "
           + "JOIN FETCH cma.llmModel "
           + "LEFT JOIN FETCH cma.policyType "
           + "WHERE cma.customer.id = :customerId AND cma.enabled = true")
    List<CustomerModelAllowanceEntity> findByCustomerIdWithModelAndPolicyType(UUID customerId);

    /**
     * Update policy type for all allowances of a customer.
     */
    @Modifying
    @Query("UPDATE CustomerModelAllowanceEntity cma "
           + "SET cma.policyType = :policyType, cma.updatedAt = :now "
           + "WHERE cma.customer.id = :customerId")
    int updatePolicyTypeForCustomer(UUID customerId, PolicyTypeEntity policyType, LocalDateTime now);

    /**
     * Update policy type for a specific allowance.
     */
    @Modifying
    @Query("UPDATE CustomerModelAllowanceEntity cma "
           + "SET cma.policyType = :policyType, cma.updatedAt = :now "
           + "WHERE cma.id = :allowanceId")
    int updatePolicyType(UUID allowanceId, PolicyTypeEntity policyType, LocalDateTime now);

    /**
     * Disable all allowances for a customer (cascade disable).
     */
    @Modifying
    @Query("UPDATE CustomerModelAllowanceEntity cma "
           + "SET cma.enabled = false, cma.updatedAt = :now "
           + "WHERE cma.customer.id = :customerId")
    int disableAllForCustomer(UUID customerId, LocalDateTime now);

    /**
     * Enable all allowances for a customer.
     */
    @Modifying
    @Query("UPDATE CustomerModelAllowanceEntity cma "
           + "SET cma.enabled = true, cma.updatedAt = :now "
           + "WHERE cma.customer.id = :customerId")
    int enableAllForCustomer(UUID customerId, LocalDateTime now);

    /**
     * Find all allowances for a model with customer info.
     */
    @Query("SELECT cma FROM CustomerModelAllowanceEntity cma "
           + "JOIN FETCH cma.customer c "
           + "LEFT JOIN FETCH cma.policyType "
           + "WHERE cma.llmModel.model = :model")
    List<CustomerModelAllowanceEntity> findAllByModelWithCustomer(String model);

    /**
     * Reset usage for all customers on a model.
     */
    @Modifying
    @Query("UPDATE CustomerModelAllowanceEntity cma "
           + "SET cma.tokensUsed = 0, cma.tokensResetAt = :now, cma.updatedAt = :now "
           + "WHERE cma.llmModel.model = :model")
    int resetUsageForModel(String model, LocalDateTime now);

    /**
     * Bulk update allowances for a model.
     */
    @Modifying
    @Query("UPDATE CustomerModelAllowanceEntity cma "
           + "SET cma.allowedTokens = :allowedTokens, cma.updatedAt = :now "
           + "WHERE cma.llmModel.model = :model")
    int bulkUpdateAllowanceForModel(String model, Long allowedTokens, LocalDateTime now);

    /**
     * Bulk update allowances and policy type for a model.
     */
    @Modifying
    @Query("UPDATE CustomerModelAllowanceEntity cma "
           + "SET cma.allowedTokens = :allowedTokens, cma.policyType = :policyType, cma.updatedAt = :now "
           + "WHERE cma.llmModel.model = :model")
    int bulkUpdateAllowanceAndPolicyForModel(String model, Long allowedTokens,
                                              PolicyTypeEntity policyType, LocalDateTime now);

    // ==================== Metrics Queries ====================

    /**
     * Count enabled allowances.
     * Used for Prometheus metrics.
     */
    @Query("SELECT COUNT(cma) FROM CustomerModelAllowanceEntity cma WHERE cma.enabled = true")
    long countByEnabledTrue();

    /**
     * Sum of all tokens used across all allowances.
     * Used for Prometheus metrics.
     */
    @Query("SELECT COALESCE(SUM(cma.tokensUsed), 0) FROM CustomerModelAllowanceEntity cma WHERE cma.enabled = true")
    long sumTotalTokensUsed();

    /**
     * Sum of tokens used for a specific model.
     * Used for per-model Prometheus metrics.
     */
    @Query("SELECT COALESCE(SUM(cma.tokensUsed), 0) FROM CustomerModelAllowanceEntity cma "
           + "WHERE cma.llmModel.model = :model AND cma.enabled = true")
    long sumTokensUsedByModel(String model);

    /**
     * Count of customers with allowance for a specific model.
     * Used for per-model Prometheus metrics.
     */
    @Query("SELECT COUNT(DISTINCT cma.customer.id) FROM CustomerModelAllowanceEntity cma "
           + "WHERE cma.llmModel.model = :model AND cma.enabled = true")
    long countCustomersByModel(String model);
}
