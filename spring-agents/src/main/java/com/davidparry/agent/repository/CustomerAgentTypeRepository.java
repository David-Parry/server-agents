package com.davidparry.agent.repository;

import com.davidparry.agent.entity.CustomerAgentTypeEntity;
import com.davidparry.agent.protocol.dto.AgentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing customer-agent type associations.
 */
@Repository
public interface CustomerAgentTypeRepository extends JpaRepository<CustomerAgentTypeEntity, UUID> {

    /**
     * Find all agent types assigned to a customer.
     */
    @Query("SELECT cat FROM CustomerAgentTypeEntity cat "
           + "JOIN FETCH cat.customer c "
           + "WHERE c.customerId = :customerId")
    List<CustomerAgentTypeEntity> findByCustomerCustomerId(@Param("customerId") UUID customerId);

    /**
     * Find all enabled agent types for a customer.
     */
    @Query("SELECT cat FROM CustomerAgentTypeEntity cat "
           + "JOIN FETCH cat.customer c "
           + "WHERE c.customerId = :customerId AND cat.enabled = true")
    List<CustomerAgentTypeEntity> findEnabledByCustomerCustomerId(@Param("customerId") UUID customerId);

    /**
     * Find a specific customer-agent type association.
     */
    @Query("SELECT cat FROM CustomerAgentTypeEntity cat "
           + "JOIN FETCH cat.customer c "
           + "WHERE c.customerId = :customerId AND cat.agentType = :agentType")
    Optional<CustomerAgentTypeEntity> findByCustomerCustomerIdAndAgentType(
            @Param("customerId") UUID customerId,
            @Param("agentType") AgentType agentType);

    /**
     * Find all customers assigned to a specific agent type.
     */
    @Query("SELECT cat FROM CustomerAgentTypeEntity cat "
           + "JOIN FETCH cat.customer c "
           + "WHERE cat.agentType = :agentType")
    List<CustomerAgentTypeEntity> findByAgentType(@Param("agentType") AgentType agentType);

    /**
     * Find all enabled customers for a specific agent type.
     */
    @Query("SELECT cat FROM CustomerAgentTypeEntity cat "
           + "JOIN FETCH cat.customer c "
           + "WHERE cat.agentType = :agentType AND cat.enabled = true AND c.enabled = true")
    List<CustomerAgentTypeEntity> findEnabledByAgentType(@Param("agentType") AgentType agentType);

    /**
     * Check if a customer has access to a specific agent type.
     */
    @Query("SELECT CASE WHEN COUNT(cat) > 0 THEN true ELSE false END "
           + "FROM CustomerAgentTypeEntity cat "
           + "JOIN cat.customer c "
           + "WHERE c.customerId = :customerId AND cat.agentType = :agentType AND cat.enabled = true")
    boolean hasAccess(@Param("customerId") UUID customerId, @Param("agentType") AgentType agentType);

    /**
     * Check if association exists.
     */
    @Query("SELECT CASE WHEN COUNT(cat) > 0 THEN true ELSE false END "
           + "FROM CustomerAgentTypeEntity cat "
           + "JOIN cat.customer c "
           + "WHERE c.customerId = :customerId AND cat.agentType = :agentType")
    boolean existsByCustomerCustomerIdAndAgentType(
            @Param("customerId") UUID customerId,
            @Param("agentType") AgentType agentType);

    /**
     * Delete association by customer and agent type.
     */
    @Modifying
    @Query("DELETE FROM CustomerAgentTypeEntity cat "
           + "WHERE cat.customer.customerId = :customerId AND cat.agentType = :agentType")
    int deleteByCustomerCustomerIdAndAgentType(
            @Param("customerId") UUID customerId,
            @Param("agentType") AgentType agentType);

    /**
     * Disable all agent types for a customer.
     */
    @Modifying
    @Query("UPDATE CustomerAgentTypeEntity cat SET cat.enabled = false, cat.updatedAt = :now "
           + "WHERE cat.customer.id = :customerInternalId")
    int disableAllForCustomer(@Param("customerInternalId") UUID customerInternalId,
                              @Param("now") LocalDateTime now);

    /**
     * Enable all agent types for a customer.
     */
    @Modifying
    @Query("UPDATE CustomerAgentTypeEntity cat SET cat.enabled = true, cat.updatedAt = :now "
           + "WHERE cat.customer.id = :customerInternalId")
    int enableAllForCustomer(@Param("customerInternalId") UUID customerInternalId,
                             @Param("now") LocalDateTime now);

    /**
     * Count customers by agent type.
     */
    @Query("SELECT cat.agentType, COUNT(cat) FROM CustomerAgentTypeEntity cat "
           + "WHERE cat.enabled = true "
           + "GROUP BY cat.agentType")
    List<Object[]> countCustomersByAgentType();

    /**
     * Find by customer internal ID.
     */
    List<CustomerAgentTypeEntity> findByCustomerId(UUID customerInternalId);
}
