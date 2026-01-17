package com.davidparry.agent.repository;

import com.davidparry.agent.entity.PolicyTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing PolicyTypeEntity persistence.
 */
@Repository
public interface PolicyTypeRepository extends JpaRepository<PolicyTypeEntity, UUID> {
    
    /**
     * Find a policy type by name (e.g., UNLIMITED, MONTHLY, YEARLY).
     */
    Optional<PolicyTypeEntity> findByName(String name);
    
    /**
     * Find all enabled policy types.
     */
    List<PolicyTypeEntity> findByEnabledTrue();
    
    /**
     * Check if a policy type with the given name exists.
     */
    boolean existsByName(String name);
    
    /**
     * Find the unlimited policy type.
     */
    @Query("SELECT pt FROM PolicyTypeEntity pt WHERE pt.name = 'UNLIMITED' AND pt.enabled = true")
    Optional<PolicyTypeEntity> findUnlimitedPolicyType();
    
    /**
     * Find policy types that have a reset period (non-unlimited).
     */
    @Query("SELECT pt FROM PolicyTypeEntity pt WHERE pt.resetDays IS NOT NULL AND pt.enabled = true")
    List<PolicyTypeEntity> findPolicyTypesWithResetPeriod();

    /**
     * Count enabled policy types.
     * Used for Prometheus metrics.
     */
    long countByEnabledTrue();
}
