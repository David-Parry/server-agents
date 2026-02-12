package com.davidparry.agent.service;

import com.davidparry.agent.dto.LowTokenNotificationEvent;
import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Service for handling low-token notifications.
 * Supports multiple notification channels:
 * - WebSocket (via callback)
 * - Webhook (HTTP POST)
 * - Email (placeholder for future implementation)
 * - Audit log
 * 
 * Notifications are sent only once per reset period to avoid spamming.
 */
@Service
public class LowTokenNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(LowTokenNotificationService.class);
    
    private final CustomerModelAllowanceRepository allowanceRepository;
    private final SecurityAuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    
    private final Counter notificationsSentCounter;
    private final Counter webhookSuccessCounter;
    private final Counter webhookFailureCounter;
    
    public LowTokenNotificationService(
            CustomerModelAllowanceRepository allowanceRepository,
            SecurityAuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.allowanceRepository = allowanceRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        this.notificationsSentCounter = Counter.builder("customer_token_allowance_low_notifications_total")
            .description("Total low token allowance notifications sent to customers")
            .register(meterRegistry);
        this.webhookSuccessCounter = Counter.builder("customer_token_allowance_webhook_success_total")
            .description("Successful webhook notifications for low token allowance warnings")
            .register(meterRegistry);
        this.webhookFailureCounter = Counter.builder("customer_token_allowance_webhook_failure_total")
            .description("Failed webhook notifications for low token allowance warnings")
            .register(meterRegistry);
    }
    
    /**
     * Checks if a notification should be sent and triggers all configured channels.
     * This method should be called after recording token usage.
     * 
     * @param allowance The allowance to check
     * @param webSocketNotifier Optional callback to send notification via WebSocket
     * @return true if a notification was sent
     */
    @Transactional
    public boolean checkAndNotify(
            CustomerModelAllowanceEntity allowance,
            Consumer<LowTokenNotificationEvent> webSocketNotifier) {
        
        if (!allowance.shouldSendNotification()) {
            return false;
        }
        
        CustomerEntity customer = allowance.getCustomer();
        LowTokenNotificationEvent event = createNotificationEvent(allowance);
        
        logger.info("Sending low token notification for customer {} on model {}: {} tokens remaining",
                   customer.getCustomerId(), allowance.getLlmModel().getModel(), 
                   allowance.getRemainingTokens());
        
        // Mark notification as sent BEFORE sending to prevent duplicates
        allowance.markNotificationSent();
        allowanceRepository.save(allowance);
        
        // Send via all configured channels
        sendNotifications(customer, event, webSocketNotifier);
        
        notificationsSentCounter.increment();
        return true;
    }
    
    /**
     * Sends notifications via all configured channels.
     */
    private void sendNotifications(
            CustomerEntity customer,
            LowTokenNotificationEvent event,
            Consumer<LowTokenNotificationEvent> webSocketNotifier) {
        
        // 1. WebSocket notification (synchronous, immediate feedback to client)
        if (webSocketNotifier != null) {
            try {
                webSocketNotifier.accept(event);
                logger.debug("Sent WebSocket notification for customer {}", customer.getCustomerId());
            } catch (Exception e) {
                logger.warn("Failed to send WebSocket notification: {}", e.getMessage());
            }
        }
        
        // 2. Webhook notification (async)
        if (customer.getNotificationWebhookUrl() != null && !customer.getNotificationWebhookUrl().isBlank()) {
            sendWebhookNotificationAsync(customer.getNotificationWebhookUrl(), event);
        }
        
        // 3. Email notification (async, placeholder)
        if (customer.getNotificationEmail() != null && !customer.getNotificationEmail().isBlank()) {
            sendEmailNotificationAsync(customer.getNotificationEmail(), event);
        }
        
        // 4. Audit log (always)
        logNotificationToAudit(event);
    }
    
    /**
     * Sends a webhook notification asynchronously.
     */
    @Async
    public void sendWebhookNotificationAsync(String webhookUrl, LowTokenNotificationEvent event) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .header("X-Event-Type", "low_token_warning")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Webhook notification sent successfully to {} for customer {}",
                           webhookUrl, event.customerId());
                webhookSuccessCounter.increment();
            } else {
                logger.warn("Webhook notification failed with status {} for customer {}: {}",
                           response.statusCode(), event.customerId(), response.body());
                webhookFailureCounter.increment();
            }
        } catch (Exception e) {
            logger.error("Failed to send webhook notification to {} for customer {}: {}",
                        webhookUrl, event.customerId(), e.getMessage());
            webhookFailureCounter.increment();
        }
    }
    
    /**
     * Sends an email notification asynchronously.
     * This is a placeholder - actual email sending would require SMTP configuration.
     */
    @Async
    public void sendEmailNotificationAsync(String email, LowTokenNotificationEvent event) {
        // TODO: Implement actual email sending when SMTP is configured
        logger.info("Email notification would be sent to {} for customer {}: {}",
                   email, event.customerId(), event.getMessage());
        
        // For now, just log the event
        // In a real implementation, you would:
        // 1. Use JavaMailSender or a third-party email service
        // 2. Format the email with a proper template
        // 3. Handle failures and retries
    }
    
    /**
     * Logs the notification event to the security audit log.
     */
    private void logNotificationToAudit(LowTokenNotificationEvent event) {
        try {
            String metadata = objectMapper.writeValueAsString(event);
            auditService.logLowTokenWarning(
                event.customerId(),
                event.model(),
                event.remainingTokens(),
                event.notificationThreshold(),
                metadata
            );
        } catch (Exception e) {
            logger.warn("Failed to log notification to audit: {}", e.getMessage());
        }
    }
    
    /**
     * Creates a notification event from an allowance entity.
     */
    private LowTokenNotificationEvent createNotificationEvent(CustomerModelAllowanceEntity allowance) {
        CustomerEntity customer = allowance.getCustomer();
        PolicyTypeEntity policy = allowance.getPolicyType();
        Integer daysUntilReset = calculateDaysUntilReset(allowance, policy);
        
        return LowTokenNotificationEvent.from(
            customer.getCustomerId(),
            customer.getName(),
            allowance.getLlmModel().getModel(),
            allowance.getLlmModel().getDisplayName(),
            allowance.getRemainingTokens(),
            allowance.getAllowedTokens(),
            allowance.getEffectiveNotificationThreshold(),
            daysUntilReset,
            policy != null ? policy.getName() : null
        );
    }
    
    private Integer calculateDaysUntilReset(CustomerModelAllowanceEntity allowance, PolicyTypeEntity policy) {
        if (policy == null || policy.isUnlimited()) {
            return null;
        }
        
        Integer resetDays = policy.getResetDays();
        if (resetDays == null || allowance.getTokensResetAt() == null) {
            return null;
        }
        
        LocalDateTime nextReset = allowance.getTokensResetAt().plusDays(resetDays);
        long daysUntil = ChronoUnit.DAYS.between(LocalDateTime.now(), nextReset);
        return Math.max(0, (int) daysUntil);
    }
    
    /**
     * Creates a notification event for a given allowance (for manual triggering or testing).
     */
    public LowTokenNotificationEvent createEvent(CustomerModelAllowanceEntity allowance) {
        return createNotificationEvent(allowance);
    }
}
