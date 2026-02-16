package com.davidparry.agent.service;

import com.davidparry.agent.dto.LowTokenNotificationEvent;
import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LowTokenNotificationService.
 */
@ExtendWith(MockitoExtension.class)
class LowTokenNotificationServiceTest {

    @Mock
    private CustomerModelAllowanceRepository allowanceRepository;

    @Mock
    private SecurityAuditService auditService;

    private LowTokenNotificationService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new LowTokenNotificationService(
                allowanceRepository,
                auditService,
                objectMapper,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void checkAndNotify_shouldNotNotifyWhenNotNeeded() {
        // Given
        CustomerModelAllowanceEntity allowance = createAllowance(100000L, 10000L, 20000L);
        // 90000 remaining > 20000 threshold, so no notification needed

        // When
        boolean result = service.checkAndNotify(allowance, null);

        // Then
        assertFalse(result);
        verify(allowanceRepository, never()).save(any());
    }

    @Test
    void checkAndNotify_shouldNotifyWhenBelowThreshold() {
        // Given
        CustomerModelAllowanceEntity allowance = createAllowance(100000L, 85000L, 20000L);
        // 15000 remaining < 20000 threshold, notification needed
        allowance.setLowTokenNotificationSentAt(null); // Never notified

        AtomicReference<LowTokenNotificationEvent> capturedEvent = new AtomicReference<>();
        Consumer<LowTokenNotificationEvent> webSocketNotifier = capturedEvent::set;

        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        boolean result = service.checkAndNotify(allowance, webSocketNotifier);

        // Then
        assertTrue(result);
        assertNotNull(capturedEvent.get());
        assertEquals(allowance.getCustomer().getCustomerId(), capturedEvent.get().customerId());
        assertEquals(allowance.getLlmModel().getModel(), capturedEvent.get().model());
        verify(allowanceRepository).save(allowance);
    }

    @Test
    void checkAndNotify_shouldNotNotifyIfAlreadyNotifiedThisPeriod() {
        // Given
        CustomerModelAllowanceEntity allowance = createAllowance(100000L, 85000L, 20000L);
        allowance.setLowTokenNotificationSentAt(LocalDateTime.now().minusHours(1)); // Recently notified

        // When
        boolean result = service.checkAndNotify(allowance, null);

        // Then
        assertFalse(result);
        verify(allowanceRepository, never()).save(any());
    }

    @Test
    void checkAndNotify_shouldNotifyViaWebhookWhenConfigured() {
        // Given
        CustomerModelAllowanceEntity allowance = createAllowance(100000L, 85000L, 20000L);
        allowance.setLowTokenNotificationSentAt(null);
        allowance.getCustomer().setNotificationWebhookUrl("https://example.com/webhook");

        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        boolean result = service.checkAndNotify(allowance, null);

        // Then
        assertTrue(result);
        verify(allowanceRepository).save(allowance);
    }

    @Test
    void checkAndNotify_shouldNotifyViaEmailWhenConfigured() {
        // Given
        CustomerModelAllowanceEntity allowance = createAllowance(100000L, 85000L, 20000L);
        allowance.setLowTokenNotificationSentAt(null);
        allowance.getCustomer().setNotificationEmail("test@example.com");

        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        boolean result = service.checkAndNotify(allowance, null);

        // Then
        assertTrue(result);
        verify(allowanceRepository).save(allowance);
    }

    @Test
    void checkAndNotify_shouldHandleWebSocketNotifierException() {
        // Given
        CustomerModelAllowanceEntity allowance = createAllowance(100000L, 85000L, 20000L);
        allowance.setLowTokenNotificationSentAt(null);

        Consumer<LowTokenNotificationEvent> failingNotifier = event -> {
            throw new RuntimeException("WebSocket error");
        };

        when(allowanceRepository.save(any(CustomerModelAllowanceEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When - should not throw
        boolean result = service.checkAndNotify(allowance, failingNotifier);

        // Then - notification still marked as sent
        assertTrue(result);
        verify(allowanceRepository).save(allowance);
    }

    @Test
    void createEvent_shouldCreateCorrectEvent() {
        // Given
        CustomerModelAllowanceEntity allowance = createAllowance(100000L, 85000L, 20000L);
        PolicyTypeEntity policy = createPolicyType("MONTHLY", 30);
        allowance.setPolicyType(policy);
        allowance.setTokensResetAt(LocalDateTime.now().minusDays(15)); // 15 days until reset

        // When
        LowTokenNotificationEvent event = service.createEvent(allowance);

        // Then
        assertEquals(allowance.getCustomer().getCustomerId(), event.customerId());
        assertEquals(allowance.getCustomer().getName(), event.customerName());
        assertEquals(allowance.getLlmModel().getModel(), event.model());
        assertEquals(allowance.getLlmModel().getDisplayName(), event.modelDisplayName());
        assertEquals(15000L, event.remainingTokens()); // 100000 - 85000
        assertEquals(100000L, event.allowedTokens());
        assertEquals(20000L, event.notificationThreshold());
        assertNotNull(event.daysUntilReset());
        assertEquals("MONTHLY", event.policyTypeName());
    }

    @Test
    void createEvent_shouldHandleUnlimitedPolicy() {
        // Given
        CustomerModelAllowanceEntity allowance = createAllowance(null, 85000L, 20000L);
        PolicyTypeEntity policy = createPolicyType("UNLIMITED", null);
        allowance.setPolicyType(policy);

        // When
        LowTokenNotificationEvent event = service.createEvent(allowance);

        // Then
        assertNull(event.allowedTokens());
        assertNull(event.daysUntilReset());
        assertEquals("UNLIMITED", event.policyTypeName());
    }

    @Test
    void createEvent_shouldHandleNullPolicy() {
        // Given
        CustomerModelAllowanceEntity allowance = createAllowance(100000L, 85000L, 20000L);
        allowance.setPolicyType(null);

        // When
        LowTokenNotificationEvent event = service.createEvent(allowance);

        // Then
        assertNull(event.policyTypeName());
        assertNull(event.daysUntilReset());
    }

    // ==================== Helper Methods ====================

    private CustomerModelAllowanceEntity createAllowance(Long allowedTokens, Long tokensUsed, Long notificationThreshold) {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setCustomerId(UUID.randomUUID());
        customer.setName("Test Customer");
        customer.setEnabled(true);

        LlmModelEntity model = new LlmModelEntity();
        model.setModel("claude-sonnet-4-5");
        model.setProvider("anthropic");
        model.setDisplayName("Claude Sonnet 4.5");
        model.setEnabled(true);

        CustomerModelAllowanceEntity allowance = new CustomerModelAllowanceEntity();
        allowance.setId(UUID.randomUUID());
        allowance.setCustomer(customer);
        allowance.setLlmModel(model);
        allowance.setAllowedTokens(allowedTokens);
        allowance.setTokensUsed(tokensUsed);
        allowance.setMinTokenNotificationThreshold(notificationThreshold);
        allowance.setTokensResetAt(LocalDateTime.now());
        allowance.setEnabled(true);

        return allowance;
    }

    private PolicyTypeEntity createPolicyType(String name, Integer resetDays) {
        PolicyTypeEntity policy = new PolicyTypeEntity();
        policy.setId(UUID.randomUUID());
        policy.setName(name);
        policy.setResetDays(resetDays);
        policy.setEnabled(true);
        return policy;
    }
}
