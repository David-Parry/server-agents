package com.davidparry.agent.service;

import com.davidparry.agent.entity.SecurityAuditLogEntity;
import com.davidparry.agent.entity.SecurityAuditLogEntity.ActorType;
import com.davidparry.agent.entity.SecurityAuditLogEntity.EventCategory;
import com.davidparry.agent.entity.SecurityAuditLogEntity.EventType;
import com.davidparry.agent.repository.SecurityAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for recording security audit events.
 * Uses async processing to avoid impacting main request flow.
 */
@Service
public class SecurityAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditLogRepository auditRepository;

    public SecurityAuditService(SecurityAuditLogRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Records a token creation event.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTokenCreated(UUID customerId, UUID tokenId, String secretVersion, String actorId) {
        SecurityAuditLogEntity log = SecurityAuditLogEntity.builder()
            .eventType(EventType.TOKEN_CREATED)
            .eventCategory(EventCategory.TOKEN)
            .customerId(customerId)
            .tokenId(tokenId)
            .secretVersion(secretVersion)
            .description("New token created using secret version " + secretVersion)
            .actorType(ActorType.SYSTEM)
            .actorId(actorId)
            .build();

        auditRepository.save(log);
        LOGGER.debug("Audit: TOKEN_CREATED for customer {} with secret {}", customerId, secretVersion);
    }

    /**
     * Records a token revocation event.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTokenRevoked(UUID customerId, UUID tokenId, String secretVersion,
                                ActorType actorType, String actorId, String reason) {
        SecurityAuditLogEntity log = SecurityAuditLogEntity.builder()
            .eventType(EventType.TOKEN_REVOKED)
            .eventCategory(EventCategory.TOKEN)
            .customerId(customerId)
            .tokenId(tokenId)
            .secretVersion(secretVersion)
            .description("Token revoked: " + reason)
            .actorType(actorType)
            .actorId(actorId)
            .build();

        auditRepository.save(log);
        LOGGER.debug("Audit: TOKEN_REVOKED for customer {}", customerId);
    }

    /**
     * Records a successful token validation.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTokenValidated(UUID customerId, String secretVersion) {
        SecurityAuditLogEntity log = SecurityAuditLogEntity.builder()
            .eventType(EventType.TOKEN_VALIDATED)
            .eventCategory(EventCategory.AUTHENTICATION)
            .customerId(customerId)
            .secretVersion(secretVersion)
            .description("Token validated successfully using secret version " + secretVersion)
            .actorType(ActorType.SYSTEM)
            .build();

        auditRepository.save(log);
    }

    /**
     * Records a failed token validation attempt.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTokenValidationFailed(String reason) {
        SecurityAuditLogEntity log = SecurityAuditLogEntity.builder()
            .eventType(EventType.TOKEN_VALIDATION_FAILED)
            .eventCategory(EventCategory.AUTHENTICATION)
            .description("Token validation failed: " + reason)
            .actorType(ActorType.SYSTEM)
            .build();

        auditRepository.save(log);
    }

    /**
     * Records when a new secret version is configured (called at startup).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSecretVersionConfigured(String secretVersion, boolean isCurrent) {
        String description = isCurrent
            ? "Secret version " + secretVersion + " configured as CURRENT"
            : "Secret version " + secretVersion + " configured (legacy/rotation)";

        SecurityAuditLogEntity log = SecurityAuditLogEntity.builder()
            .eventType(EventType.SECRET_VERSION_CONFIGURED)
            .eventCategory(EventCategory.SECRET)
            .secretVersion(secretVersion)
            .description(description)
            .actorType(ActorType.SYSTEM)
            .actorId("application-startup")
            .build();

        auditRepository.save(log);
        LOGGER.info("Audit: SECRET_VERSION_CONFIGURED - {}", description);
    }

    /**
     * Records a low token warning notification.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLowTokenWarning(UUID customerId, String model, Long remainingTokens,
                                    Long threshold, String metadata) {
        String description = String.format(
            "Low token warning: %d tokens remaining on model %s (threshold: %d)",
            remainingTokens, model, threshold
        );

        SecurityAuditLogEntity log = SecurityAuditLogEntity.builder()
            .eventType(EventType.LOW_TOKEN_WARNING)
            .eventCategory(EventCategory.NOTIFICATION)
            .customerId(customerId)
            .description(description)
            .metadata(metadata)
            .actorType(ActorType.SYSTEM)
            .actorId("notification-service")
            .build();

        auditRepository.save(log);
        LOGGER.debug("Audit: LOW_TOKEN_WARNING for customer {} on model {}", customerId, model);
    }

    /**
     * Records a usage limit exceeded event.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLimitExceeded(UUID customerId, String model, Long tokensUsed, Long tokenLimit) {
        String description = String.format(
            "Token limit exceeded on model %s: used %d of %d tokens",
            model, tokensUsed, tokenLimit
        );

        SecurityAuditLogEntity log = SecurityAuditLogEntity.builder()
            .eventType(EventType.LIMIT_EXCEEDED)
            .eventCategory(EventCategory.USAGE)
            .customerId(customerId)
            .description(description)
            .actorType(ActorType.SYSTEM)
            .build();

        auditRepository.save(log);
        LOGGER.debug("Audit: LIMIT_EXCEEDED for customer {} on model {}", customerId, model);
    }

    // ==================== Admin Action Audit Methods ====================

    /**
     * Records a generic admin action.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAdminAction(EventType eventType, String description, String metadata) {
        SecurityAuditLogEntity log = SecurityAuditLogEntity.builder()
            .eventType(eventType)
            .eventCategory(EventCategory.ADMIN)
            .description(description)
            .metadata(metadata)
            .actorType(ActorType.ADMIN)
            .actorId("admin-api")
            .build();

        auditRepository.save(log);
        LOGGER.info("Audit: {} - {}", eventType, description);
    }

    /**
     * Records an admin action affecting a customer.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAdminCustomerAction(EventType eventType, UUID customerId, String description) {
        SecurityAuditLogEntity log = SecurityAuditLogEntity.builder()
            .eventType(eventType)
            .eventCategory(EventCategory.ADMIN)
            .customerId(customerId)
            .description(description)
            .actorType(ActorType.ADMIN)
            .actorId("admin-api")
            .build();

        auditRepository.save(log);
        LOGGER.info("Audit: {} for customer {} - {}", eventType, customerId, description);
    }

    /**
     * Records an admin action affecting a model.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAdminModelAction(EventType eventType, String model, String description) {
        SecurityAuditLogEntity log = SecurityAuditLogEntity.builder()
            .eventType(eventType)
            .eventCategory(EventCategory.ADMIN)
            .description(description)
            .metadata(String.format("{\"model\": \"%s\"}", model))
            .actorType(ActorType.ADMIN)
            .actorId("admin-api")
            .build();

        auditRepository.save(log);
        LOGGER.info("Audit: {} for model {} - {}", eventType, model, description);
    }
}
