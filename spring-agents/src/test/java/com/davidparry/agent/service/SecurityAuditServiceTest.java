package com.davidparry.agent.service;

import com.davidparry.agent.entity.SecurityAuditLogEntity;
import com.davidparry.agent.entity.SecurityAuditLogEntity.*;
import com.davidparry.agent.repository.SecurityAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SecurityAuditService.
 */
@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceTest {

    @Mock
    private SecurityAuditLogRepository auditRepository;

    private SecurityAuditService service;

    @BeforeEach
    void setUp() {
        service = new SecurityAuditService(auditRepository);
    }

    @Test
    void logTokenCreated_shouldSaveAuditLog() {
        // Given
        UUID customerId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String secretVersion = "V1";
        String actorId = "test-actor";

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logTokenCreated(customerId, tokenId, secretVersion, actorId);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertEquals(EventType.TOKEN_CREATED, log.getEventType());
        assertEquals(EventCategory.TOKEN, log.getEventCategory());
        assertEquals(customerId, log.getCustomerId());
        assertEquals(tokenId, log.getTokenId());
        assertEquals(secretVersion, log.getSecretVersion());
        assertEquals(ActorType.SYSTEM, log.getActorType());
        assertEquals(actorId, log.getActorId());
        assertTrue(log.getDescription().contains(secretVersion));
    }

    @Test
    void logTokenRevoked_shouldSaveAuditLog() {
        // Given
        UUID customerId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String secretVersion = "V1";
        String reason = "User requested revocation";

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logTokenRevoked(customerId, tokenId, secretVersion, ActorType.ADMIN, "admin-user", reason);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertEquals(EventType.TOKEN_REVOKED, log.getEventType());
        assertEquals(EventCategory.TOKEN, log.getEventCategory());
        assertEquals(customerId, log.getCustomerId());
        assertEquals(tokenId, log.getTokenId());
        assertEquals(ActorType.ADMIN, log.getActorType());
        assertEquals("admin-user", log.getActorId());
        assertTrue(log.getDescription().contains(reason));
    }

    @Test
    void logTokenValidated_shouldSaveAuditLog() {
        // Given
        UUID customerId = UUID.randomUUID();
        String secretVersion = "V1";

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logTokenValidated(customerId, secretVersion);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertEquals(EventType.TOKEN_VALIDATED, log.getEventType());
        assertEquals(EventCategory.AUTHENTICATION, log.getEventCategory());
        assertEquals(customerId, log.getCustomerId());
        assertEquals(secretVersion, log.getSecretVersion());
    }

    @Test
    void logTokenValidationFailed_shouldSaveAuditLog() {
        // Given
        String reason = "Invalid signature";

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logTokenValidationFailed(reason);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertEquals(EventType.TOKEN_VALIDATION_FAILED, log.getEventType());
        assertEquals(EventCategory.AUTHENTICATION, log.getEventCategory());
        assertTrue(log.getDescription().contains(reason));
    }

    @Test
    void logSecretVersionConfigured_shouldSaveAuditLogForCurrentVersion() {
        // Given
        String secretVersion = "V2";

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logSecretVersionConfigured(secretVersion, true);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertEquals(EventType.SECRET_VERSION_CONFIGURED, log.getEventType());
        assertEquals(EventCategory.SECRET, log.getEventCategory());
        assertEquals(secretVersion, log.getSecretVersion());
        assertTrue(log.getDescription().contains("CURRENT"));
    }

    @Test
    void logSecretVersionConfigured_shouldSaveAuditLogForLegacyVersion() {
        // Given
        String secretVersion = "V1";

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logSecretVersionConfigured(secretVersion, false);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertTrue(log.getDescription().contains("legacy"));
    }

    @Test
    void logLowTokenWarning_shouldSaveAuditLog() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        Long remainingTokens = 5000L;
        Long threshold = 10000L;
        String metadata = "{\"test\": \"data\"}";

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logLowTokenWarning(customerId, model, remainingTokens, threshold, metadata);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertEquals(EventType.LOW_TOKEN_WARNING, log.getEventType());
        assertEquals(EventCategory.NOTIFICATION, log.getEventCategory());
        assertEquals(customerId, log.getCustomerId());
        assertEquals(metadata, log.getMetadata());
        assertTrue(log.getDescription().contains(model));
        assertTrue(log.getDescription().contains(String.valueOf(remainingTokens)));
    }

    @Test
    void logLimitExceeded_shouldSaveAuditLog() {
        // Given
        UUID customerId = UUID.randomUUID();
        String model = "claude-sonnet-4-5";
        Long tokensUsed = 150000L;
        Long tokenLimit = 100000L;

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logLimitExceeded(customerId, model, tokensUsed, tokenLimit);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertEquals(EventType.LIMIT_EXCEEDED, log.getEventType());
        assertEquals(EventCategory.USAGE, log.getEventCategory());
        assertEquals(customerId, log.getCustomerId());
        assertTrue(log.getDescription().contains(model));
        assertTrue(log.getDescription().contains(String.valueOf(tokensUsed)));
        assertTrue(log.getDescription().contains(String.valueOf(tokenLimit)));
    }

    @Test
    void logAdminAction_shouldSaveAuditLog() {
        // Given
        EventType eventType = EventType.MODEL_ADDED;
        String description = "Created new model";
        String metadata = "{\"model\": \"test\"}";

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logAdminAction(eventType, description, metadata);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertEquals(eventType, log.getEventType());
        assertEquals(EventCategory.ADMIN, log.getEventCategory());
        assertEquals(description, log.getDescription());
        assertEquals(metadata, log.getMetadata());
        assertEquals(ActorType.ADMIN, log.getActorType());
    }

    @Test
    void logAdminCustomerAction_shouldSaveAuditLog() {
        // Given
        EventType eventType = EventType.ADMIN_CUSTOMER_DISABLED;
        UUID customerId = UUID.randomUUID();
        String description = "Customer disabled by admin";

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logAdminCustomerAction(eventType, customerId, description);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertEquals(eventType, log.getEventType());
        assertEquals(EventCategory.ADMIN, log.getEventCategory());
        assertEquals(customerId, log.getCustomerId());
        assertEquals(description, log.getDescription());
        assertEquals(ActorType.ADMIN, log.getActorType());
    }

    @Test
    void logAdminModelAction_shouldSaveAuditLog() {
        // Given
        EventType eventType = EventType.ADMIN_MODEL_UPDATED;
        String model = "claude-sonnet-4-5";
        String description = "Model updated";

        when(auditRepository.save(any(SecurityAuditLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.logAdminModelAction(eventType, model, description);

        // Then
        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(auditRepository).save(captor.capture());

        SecurityAuditLogEntity log = captor.getValue();
        assertEquals(eventType, log.getEventType());
        assertEquals(EventCategory.ADMIN, log.getEventCategory());
        assertEquals(description, log.getDescription());
        assertTrue(log.getMetadata().contains(model));
        assertEquals(ActorType.ADMIN, log.getActorType());
    }
}
