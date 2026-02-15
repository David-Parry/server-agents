package com.davidparry.agent.repository;

import com.davidparry.agent.entity.LlmModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for managing LlmModelEntity persistence.
 */
@Repository
public interface LlmModelRepository extends JpaRepository<LlmModelEntity, String> {

    /**
     * Find all enabled models.
     */
    List<LlmModelEntity> findByEnabledTrue();

    /**
     * Find models by provider.
     */
    List<LlmModelEntity> findByProvider(String provider);

    /**
     * Find enabled models by provider.
     */
    List<LlmModelEntity> findByProviderAndEnabledTrue(String provider);

    /**
     * Check if a model exists by ID.
     */
    boolean existsByModel(String model);

    /**
     * Count enabled models.
     */
    long countByEnabledTrue();
}
