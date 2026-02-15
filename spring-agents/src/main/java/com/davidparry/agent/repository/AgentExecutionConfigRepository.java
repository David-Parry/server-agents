package com.davidparry.agent.repository;

import com.davidparry.agent.entity.AgentExecutionConfigEntity;
import com.davidparry.agent.protocol.dto.AgentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing agent execution configurations.
 * Supports both default (global) and customer-specific execution configs.
 */
@Repository
public interface AgentExecutionConfigRepository extends JpaRepository<AgentExecutionConfigEntity, UUID> {

    /**
     * Find the default execution config for an agent type (where customer is null).
     */
    @Query("SELECT ec FROM AgentExecutionConfigEntity ec "
           + "WHERE ec.agentConfig.agentType = :agentType AND ec.customer IS NULL")
    Optional<AgentExecutionConfigEntity> findDefaultByAgentType(@Param("agentType") AgentType agentType);

    /**
     * Find the execution config for a specific customer and agent type.
     */
    @Query("SELECT ec FROM AgentExecutionConfigEntity ec "
           + "WHERE ec.agentConfig.agentType = :agentType AND ec.customer.customerId = :customerId")
    Optional<AgentExecutionConfigEntity> findByAgentTypeAndCustomerId(
            @Param("agentType") AgentType agentType,
            @Param("customerId") UUID customerId);

    /**
     * Find the effective execution config for a customer and agent type.
     * Returns customer-specific config if exists, otherwise returns default config.
     */
    @Query("SELECT ec FROM AgentExecutionConfigEntity ec "
           + "WHERE ec.agentConfig.agentType = :agentType "
           + "AND (ec.customer.customerId = :customerId OR ec.customer IS NULL) "
           + "ORDER BY CASE WHEN ec.customer IS NULL THEN 1 ELSE 0 END")
    List<AgentExecutionConfigEntity> findEffectiveConfigByAgentTypeAndCustomerId(
            @Param("agentType") AgentType agentType,
            @Param("customerId") UUID customerId);

    /**
     * Find all execution configs for an agent type (both default and customer-specific).
     */
    @Query("SELECT ec FROM AgentExecutionConfigEntity ec "
           + "LEFT JOIN FETCH ec.customer "
           + "WHERE ec.agentConfig.agentType = :agentType")
    List<AgentExecutionConfigEntity> findAllByAgentType(@Param("agentType") AgentType agentType);

    /**
     * Find all execution configs for a customer.
     */
    @Query("SELECT ec FROM AgentExecutionConfigEntity ec "
           + "JOIN FETCH ec.agentConfig "
           + "WHERE ec.customer.customerId = :customerId")
    List<AgentExecutionConfigEntity> findAllByCustomerId(@Param("customerId") UUID customerId);

    /**
     * Find execution config by agent config ID and customer ID.
     */
    @Query("SELECT ec FROM AgentExecutionConfigEntity ec "
           + "WHERE ec.agentConfig.id = :agentConfigId AND ec.customer.id = :customerInternalId")
    Optional<AgentExecutionConfigEntity> findByAgentConfigIdAndCustomerInternalId(
            @Param("agentConfigId") UUID agentConfigId,
            @Param("customerInternalId") UUID customerInternalId);

    /**
     * Find default execution config by agent config ID.
     */
    @Query("SELECT ec FROM AgentExecutionConfigEntity ec "
           + "WHERE ec.agentConfig.id = :agentConfigId AND ec.customer IS NULL")
    Optional<AgentExecutionConfigEntity> findDefaultByAgentConfigId(@Param("agentConfigId") UUID agentConfigId);

    /**
     * Check if a customer-specific config exists.
     */
    @Query("SELECT CASE WHEN COUNT(ec) > 0 THEN true ELSE false END "
           + "FROM AgentExecutionConfigEntity ec "
           + "WHERE ec.agentConfig.agentType = :agentType AND ec.customer.customerId = :customerId")
    boolean existsByAgentTypeAndCustomerId(
            @Param("agentType") AgentType agentType,
            @Param("customerId") UUID customerId);

    /**
     * Delete customer-specific execution config.
     */
    @Query("DELETE FROM AgentExecutionConfigEntity ec "
           + "WHERE ec.agentConfig.agentType = :agentType AND ec.customer.customerId = :customerId")
    int deleteByAgentTypeAndCustomerId(
            @Param("agentType") AgentType agentType,
            @Param("customerId") UUID customerId);

    /**
     * Count customer-specific configs per agent type.
     */
    @Query("SELECT ec.agentConfig.agentType, COUNT(ec) FROM AgentExecutionConfigEntity ec "
           + "WHERE ec.customer IS NOT NULL "
           + "GROUP BY ec.agentConfig.agentType")
    List<Object[]> countCustomerConfigsByAgentType();
}
