package com.davidparry.agent.repository;

import com.davidparry.agent.entity.CustomerTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for accessing customer token data from the database.
 */
@Repository
public interface CustomerTokenRepository extends JpaRepository<CustomerTokenEntity, UUID> {

    /**
     * Find all active, non-expired tokens with their customers for validation.
     * Eagerly fetches customer to get the customer_id (used as salt).
     */
    @Query("SELECT t FROM CustomerTokenEntity t JOIN FETCH t.customer c "
           + "WHERE t.active = true AND c.enabled = true "
           + "AND (t.expiresAt IS NULL OR t.expiresAt > :now)")
    List<CustomerTokenEntity> findAllActiveTokens(LocalDateTime now);

    /**
     * Find active tokens for a specific customer by customer table ID.
     */
    @Query("SELECT t FROM CustomerTokenEntity t "
           + "WHERE t.customer.id = :customerId AND t.active = true "
           + "AND (t.expiresAt IS NULL OR t.expiresAt > :now)")
    List<CustomerTokenEntity> findActiveTokensByCustomerId(UUID customerId, LocalDateTime now);

    /**
     * Revoke all tokens for a customer (for rotation).
     */
    @Modifying
    @Query("UPDATE CustomerTokenEntity t SET t.active = false, t.revokedAt = :now "
           + "WHERE t.customer.id = :customerId AND t.active = true")
    int revokeAllTokensForCustomer(UUID customerId, LocalDateTime now);

    /**
     * Clean up expired tokens (maintenance job).
     */
    @Modifying
    @Query("DELETE FROM CustomerTokenEntity t WHERE t.expiresAt < :cutoff")
    int deleteExpiredTokens(LocalDateTime cutoff);

    /**
     * Count active tokens grouped by secret version (for rotation monitoring).
     */
    @Query("SELECT t.secretVersion, COUNT(t) FROM CustomerTokenEntity t "
           + "WHERE t.active = true AND (t.expiresAt IS NULL OR t.expiresAt > :now) "
           + "GROUP BY t.secretVersion")
    List<Object[]> countActiveTokensBySecretVersion(LocalDateTime now);

    /**
     * Find active tokens using a specific secret version.
     */
    @Query("SELECT t FROM CustomerTokenEntity t "
           + "WHERE t.secretVersion = :version AND t.active = true "
           + "AND (t.expiresAt IS NULL OR t.expiresAt > :now)")
    List<CustomerTokenEntity> findActiveTokensBySecretVersion(String version, LocalDateTime now);

    /**
     * Count active tokens for a specific secret version.
     */
    @Query("SELECT COUNT(t) FROM CustomerTokenEntity t "
           + "WHERE t.secretVersion = :version AND t.active = true "
           + "AND (t.expiresAt IS NULL OR t.expiresAt > :now)")
    long countActiveTokensForSecretVersion(String version, LocalDateTime now);

    /**
     * Count all active tokens (not revoked, not expired).
     * Used for Prometheus metrics.
     */
    @Query("SELECT COUNT(t) FROM CustomerTokenEntity t "
           + "WHERE t.active = true AND (t.expiresAt IS NULL OR t.expiresAt > :now)")
    long countActiveTokens(LocalDateTime now);
}
