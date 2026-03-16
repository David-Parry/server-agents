package com.davidparry.agent.service;

import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.SecurityAuditLogEntity;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.CustomerTokenRepository;
import com.davidparry.agent.repository.LlmModelRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import com.davidparry.agent.repository.SecurityAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for administrative operations.
 * Handles complex admin operations that span multiple entities.
 */
@Service
@Transactional
public class AdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminService.class);

    private final CustomerRepository customerRepository;
    private final CustomerTokenRepository tokenRepository;
    private final CustomerModelAllowanceRepository allowanceRepository;
    private final LlmModelRepository modelRepository;
    private final PolicyTypeRepository policyTypeRepository;
    private final SecurityAuditLogRepository auditLogRepository;
    private final SecurityAuditService auditService;

    public AdminService(
            CustomerRepository customerRepository,
            CustomerTokenRepository tokenRepository,
            CustomerModelAllowanceRepository allowanceRepository,
            LlmModelRepository modelRepository,
            PolicyTypeRepository policyTypeRepository,
            SecurityAuditLogRepository auditLogRepository,
            SecurityAuditService auditService) {
        this.customerRepository = customerRepository;
        this.tokenRepository = tokenRepository;
        this.allowanceRepository = allowanceRepository;
        this.modelRepository = modelRepository;
        this.policyTypeRepository = policyTypeRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    /**
     * Disables a customer and cascades to:
     * - Revokes all active tokens
     * - Disables all model allowances
     */
    public CustomerDisableResult disableCustomer(UUID customerId) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        LocalDateTime now = LocalDateTime.now();

        // Revoke all tokens
        int tokensRevoked = tokenRepository.revokeAllTokensForCustomer(customer.getId(), now);

        // Disable all allowances
        int allowancesDisabled = allowanceRepository.disableAllForCustomer(customer.getId(), now);

        // Disable customer
        customer.setEnabled(false);
        customerRepository.save(customer);

        // Audit
        auditService.logAdminCustomerAction(
            SecurityAuditLogEntity.EventType.ADMIN_CUSTOMER_DISABLED,
            customerId,
            String.format("Customer disabled. Tokens revoked: %d, Allowances disabled: %d",
                         tokensRevoked, allowancesDisabled)
        );

        LOGGER.info("Disabled customer {} with {} tokens revoked and {} allowances disabled",
                   customerId, tokensRevoked, allowancesDisabled);

        return new CustomerDisableResult(customerId, tokensRevoked, allowancesDisabled);
    }

    /**
     * Enables a customer (does NOT re-enable allowances - admin must do that explicitly).
     */
    public void enableCustomer(UUID customerId) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        customer.setEnabled(true);
        customerRepository.save(customer);

        auditService.logAdminCustomerAction(
            SecurityAuditLogEntity.EventType.ADMIN_CUSTOMER_ENABLED,
            customerId,
            "Customer enabled"
        );

        LOGGER.info("Enabled customer {}", customerId);
    }

    /**
     * Re-enables all allowances for a customer.
     */
    public int enableCustomerAllowances(UUID customerId) {
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        int enabled = allowanceRepository.enableAllForCustomer(customer.getId(), LocalDateTime.now());

        auditService.logAdminCustomerAction(
            SecurityAuditLogEntity.EventType.ADMIN_CUSTOMER_ENABLED,
            customerId,
            String.format("Re-enabled %d allowances for customer", enabled)
        );

        LOGGER.info("Re-enabled {} allowances for customer {}", enabled, customerId);
        return enabled;
    }

    /**
     * Gets system-wide statistics.
     */
    @Transactional(readOnly = true)
    public SystemStatistics getSystemStatistics() {
        return new SystemStatistics(
            customerRepository.count(),
            customerRepository.countByEnabledTrue(),
            modelRepository.count(),
            modelRepository.countByEnabledTrue(),
            policyTypeRepository.count(),
            allowanceRepository.count(),
            auditLogRepository.count()
        );
    }

    /**
     * Gets token statistics by secret version.
     */
    @Transactional(readOnly = true)
    public List<TokenVersionStatistics> getTokenStatistics() {
        return tokenRepository.countActiveTokensBySecretVersion(LocalDateTime.now())
            .stream()
            .map(row -> new TokenVersionStatistics(
                (String) row[0],
                ((Number) row[1]).longValue()
            ))
            .toList();
    }

    /**
     * Gets the count of active tokens for a customer.
     */
    @Transactional(readOnly = true)
    public int getActiveTokenCount(UUID customerInternalId) {
        return tokenRepository.findActiveTokensByCustomerId(customerInternalId, LocalDateTime.now()).size();
    }

    /**
     * Gets the count of model allowances for a customer.
     */
    @Transactional(readOnly = true)
    public int getAllowanceCount(UUID customerInternalId) {
        return allowanceRepository.findByCustomerIdWithModel(customerInternalId).size();
    }

    // Result records
    public record CustomerDisableResult(UUID customerId, int tokensRevoked, int allowancesDisabled) {}

    public record SystemStatistics(
        long totalCustomers,
        long enabledCustomers,
        long totalModels,
        long enabledModels,
        long totalPolicyTypes,
        long totalAllowances,
        long totalAuditLogs
    ) {}

    public record TokenVersionStatistics(String secretVersion, long activeTokenCount) {}
}
