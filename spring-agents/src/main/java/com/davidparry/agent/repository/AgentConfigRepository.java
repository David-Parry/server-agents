package com.davidparry.agent.repository;

import com.davidparry.agent.entity.AgentConfigEntity;
import com.davidparry.agent.protocol.dto.AgentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for accessing agent configuration data from the database.
 */
@Repository
public interface AgentConfigRepository extends JpaRepository<AgentConfigEntity, UUID> {
    
    /**
     * Find agent configuration by type.
     */
    Optional<AgentConfigEntity> findByAgentType(AgentType agentType);
    
    /**
     * Find enabled agent configuration by type.
     */
    Optional<AgentConfigEntity> findByAgentTypeAndEnabledTrue(AgentType agentType);
    
    /**
     * Find all enabled agent configurations.
     */
    List<AgentConfigEntity> findAllByEnabledTrue();
    
    /**
     * Check if configuration exists for agent type.
     */
    boolean existsByAgentType(AgentType agentType);
    
    /**
     * Find agent configuration with execution config eagerly loaded.
     */
    @Query("SELECT a FROM AgentConfigEntity a LEFT JOIN FETCH a.executionConfig " +
           "WHERE a.agentType = :agentType AND a.enabled = true")
    Optional<AgentConfigEntity> findByAgentTypeWithExecutionConfig(AgentType agentType);
}
