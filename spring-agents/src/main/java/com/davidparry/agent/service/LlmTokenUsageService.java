package com.davidparry.agent.service;

import com.davidparry.agent.dto.MonthlyTokenUsageResponse;
import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.LlmTokenUsageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for recording and querying per-call LLM token usage.
 */
@Service
public class LlmTokenUsageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmTokenUsageService.class);
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final LlmTokenUsageRepository tokenUsageRepository;
    private final CustomerRepository customerRepository;
    private final Counter usageRecordFailureCounter;

    public LlmTokenUsageService(LlmTokenUsageRepository tokenUsageRepository,
                                CustomerRepository customerRepository,
                                MeterRegistry meterRegistry) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.customerRepository = customerRepository;
        this.usageRecordFailureCounter = Counter
                .builder("llm.token.usage.record.failures")
                .description("Number of failures when recording token usage")
                .register(meterRegistry);
    }

    /**
     * Records a single LLM call's token usage. Wrapped in try/catch so it never fails the main flow.
     *
     * @param externalCustomerId the customer's external UUID (from connection clientId)
     * @param model the LLM model used
     * @param agentType the agent type
     * @param sessionId the session ID
     * @param inputTokens input tokens
     * @param outputTokens output tokens
     * @param totalTokens total tokens
     * @param toolCallsCount number of tool calls in this session
     */
    @Transactional
    public void recordUsage(UUID externalCustomerId, String model, String agentType, String sessionId,
                            int inputTokens, int outputTokens, int totalTokens, int toolCallsCount) {
        try {
            int rowsInserted = tokenUsageRepository.insertUsageByExternalId(externalCustomerId, model,
                    agentType, sessionId, inputTokens, outputTokens, totalTokens, toolCallsCount);

            if (rowsInserted == 0) {
                LOGGER.warn("Cannot record token usage: customer not found for external ID {}", externalCustomerId);
                return;
            }

            LOGGER.debug("Recorded token usage: customer={}, model={}, agent={}, total={}",
                    externalCustomerId, model, agentType, totalTokens);
        } catch (Exception e) {
            usageRecordFailureCounter.increment();
            LOGGER.error("Failed to record token usage for customer={}, model={}, session={}",
                    externalCustomerId, model, sessionId, e);
        }
    }

    /**
     * Returns monthly-aggregated token usage for a customer.
     *
     * @param externalCustomerId the customer's external UUID
     * @param months number of months to look back
     * @param model optional model filter (null or blank for all models)
     * @return list of monthly aggregated usage records
     */
    @Transactional(readOnly = true)
    public List<MonthlyTokenUsageResponse> getMonthlyUsage(UUID externalCustomerId, int months, String model) {
        if (months < 1 || months > 24) {
            throw new IllegalArgumentException("months must be between 1 and 24, got: " + months);
        }

        Optional<CustomerEntity> customerOpt = customerRepository.findByCustomerId(externalCustomerId);
        if (customerOpt.isEmpty()) {
            return List.of();
        }

        UUID internalCustomerId = customerOpt.get().getId();
        LocalDateTime since = LocalDateTime.now().minusMonths(months);

        List<Object[]> rows;
        if (model != null && !model.isBlank()) {
            rows = tokenUsageRepository.findMonthlyAggregationByModel(internalCustomerId, model, since);
        } else {
            rows = tokenUsageRepository.findMonthlyAggregation(internalCustomerId, since);
        }

        return rows.stream()
                .map(this::toMonthlyResponse)
                .toList();
    }

    private MonthlyTokenUsageResponse toMonthlyResponse(Object[] row) {
        long inputTokens = ((Number) row[4]).longValue();
        long outputTokens = ((Number) row[5]).longValue();
        BigDecimal inputPrice = row[9] != null ? new BigDecimal(row[9].toString()) : BigDecimal.ZERO;
        BigDecimal outputPrice = row[10] != null ? new BigDecimal(row[10].toString()) : BigDecimal.ZERO;

        BigDecimal estimatedInputCost = calculateCost(inputTokens, inputPrice);
        BigDecimal estimatedOutputCost = calculateCost(outputTokens, outputPrice);
        BigDecimal estimatedTotalCost = estimatedInputCost.add(estimatedOutputCost);

        return new MonthlyTokenUsageResponse(
                ((Number) row[0]).intValue(),   // year
                ((Number) row[1]).intValue(),   // month
                (String) row[2],                // model
                (String) row[3],                // agentType
                inputTokens,                    // inputTokens
                outputTokens,                   // outputTokens
                ((Number) row[6]).longValue(),  // totalTokens
                ((Number) row[7]).longValue(),  // toolCallsCount
                ((Number) row[8]).longValue(),  // callCount
                inputPrice,
                outputPrice,
                estimatedInputCost,
                estimatedOutputCost,
                estimatedTotalCost
        );
    }

    private BigDecimal calculateCost(long tokens, BigDecimal pricePerMillion) {
        return BigDecimal.valueOf(tokens)
                .divide(ONE_MILLION, 10, RoundingMode.HALF_UP)
                .multiply(pricePerMillion)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
