package com.davidparry.agent.repository;

import com.davidparry.agent.entity.CustomerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for accessing customer data from the database.
 */
@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {
    
    /**
     * Find customer by their unique customer ID (UUID).
     */
    Optional<CustomerEntity> findByCustomerId(UUID customerId);
    
    /**
     * Find enabled customer by their unique customer ID.
     */
    Optional<CustomerEntity> findByCustomerIdAndEnabledTrue(UUID customerId);
    
    /**
     * Check if customer exists by customer ID.
     */
    boolean existsByCustomerId(UUID customerId);
    
    /**
     * Find all enabled customers with their active tokens.
     */
    @Query("SELECT DISTINCT c FROM CustomerEntity c LEFT JOIN FETCH c.tokens t " +
           "WHERE c.enabled = true AND t.active = true")
    List<CustomerEntity> findAllEnabledWithActiveTokens();
    
    /**
     * Count enabled customers.
     */
    long countByEnabledTrue();
    
    /**
     * Find customers by enabled status with pagination.
     */
    Page<CustomerEntity> findByEnabled(boolean enabled, Pageable pageable);
}
