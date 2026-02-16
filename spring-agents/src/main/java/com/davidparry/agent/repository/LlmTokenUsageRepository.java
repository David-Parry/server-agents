package com.davidparry.agent.repository;

import com.davidparry.agent.entity.LlmTokenUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LlmTokenUsageRepository extends JpaRepository<LlmTokenUsageEntity, UUID> {

    /**
     * Fast native INSERT using RANDOM_UUID() to avoid JPA entity overhead on the write path.
     */
    @Modifying
    @Query(value = "INSERT INTO llm_token_usage (id, customer_id, model, agent_type, session_id, "
            + "prompt_tokens, completion_tokens, total_tokens, tool_calls_count, created_at) "
            + "VALUES (RANDOM_UUID(), :customerId, :model, :agentType, :sessionId, "
            + ":promptTokens, :completionTokens, :totalTokens, :toolCallsCount, CURRENT_TIMESTAMP)",
            nativeQuery = true)
    void insertUsage(@Param("customerId") UUID customerId,
                     @Param("model") String model,
                     @Param("agentType") String agentType,
                     @Param("sessionId") String sessionId,
                     @Param("promptTokens") int promptTokens,
                     @Param("completionTokens") int completionTokens,
                     @Param("totalTokens") int totalTokens,
                     @Param("toolCallsCount") int toolCallsCount);

    /**
     * Monthly aggregation of token usage grouped by year, month, model, and agent type.
     */
    @Query(value = "SELECT EXTRACT(YEAR FROM u.created_at) AS yr, "
            + "EXTRACT(MONTH FROM u.created_at) AS mo, "
            + "u.model, "
            + "u.agent_type, "
            + "SUM(u.prompt_tokens) AS sum_prompt, "
            + "SUM(u.completion_tokens) AS sum_completion, "
            + "SUM(u.total_tokens) AS sum_total, "
            + "SUM(u.tool_calls_count) AS sum_tool_calls, "
            + "COUNT(*) AS call_count "
            + "FROM llm_token_usage u "
            + "WHERE u.customer_id = :customerId AND u.created_at >= :since "
            + "GROUP BY EXTRACT(YEAR FROM u.created_at), EXTRACT(MONTH FROM u.created_at), u.model, u.agent_type "
            + "ORDER BY yr DESC, mo DESC, u.model, u.agent_type",
            nativeQuery = true)
    List<Object[]> findMonthlyAggregation(@Param("customerId") UUID customerId,
                                          @Param("since") LocalDateTime since);

    /**
     * Monthly aggregation filtered by model.
     */
    @Query(value = "SELECT EXTRACT(YEAR FROM u.created_at) AS yr, "
            + "EXTRACT(MONTH FROM u.created_at) AS mo, "
            + "u.model, "
            + "u.agent_type, "
            + "SUM(u.prompt_tokens) AS sum_prompt, "
            + "SUM(u.completion_tokens) AS sum_completion, "
            + "SUM(u.total_tokens) AS sum_total, "
            + "SUM(u.tool_calls_count) AS sum_tool_calls, "
            + "COUNT(*) AS call_count "
            + "FROM llm_token_usage u "
            + "WHERE u.customer_id = :customerId AND u.model = :model AND u.created_at >= :since "
            + "GROUP BY EXTRACT(YEAR FROM u.created_at), EXTRACT(MONTH FROM u.created_at), u.model, u.agent_type "
            + "ORDER BY yr DESC, mo DESC, u.model, u.agent_type",
            nativeQuery = true)
    List<Object[]> findMonthlyAggregationByModel(@Param("customerId") UUID customerId,
                                                  @Param("model") String model,
                                                  @Param("since") LocalDateTime since);
}
