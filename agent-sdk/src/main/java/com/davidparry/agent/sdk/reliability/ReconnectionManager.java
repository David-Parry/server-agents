package com.davidparry.agent.sdk.reliability;

import com.davidparry.agent.sdk.config.AgentSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages reconnection logic with exponential backoff.
 */
public class ReconnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ReconnectionManager.class);
    
    private final AgentSdkProperties.ReconnectConfig config;
    private final AtomicInteger attemptCount = new AtomicInteger(0);
    private final AtomicReference<Instant> lastAttemptTime = new AtomicReference<>();
    
    public ReconnectionManager(AgentSdkProperties properties) {
        this.config = properties.reconnect();
    }
    
    /**
     * Check if reconnection is enabled and attempts remain.
     */
    public boolean shouldAttemptReconnect() {
        if (!config.enabled()) {
            return false;
        }
        return attemptCount.get() < config.maxAttempts();
    }
    
    /**
     * Calculate the delay before the next reconnection attempt.
     */
    public Duration getNextDelay() {
        int attempt = attemptCount.incrementAndGet();
        lastAttemptTime.set(Instant.now());
        
        double multiplier = Math.pow(config.backoffMultiplier(), attempt - 1);
        long delayMs = (long) (config.initialDelayMs() * multiplier);
        delayMs = Math.min(delayMs, config.maxDelayMs());
        
        logger.debug("Reconnection attempt {} - delay: {}ms", attempt, delayMs);
        return Duration.ofMillis(delayMs);
    }
    
    /**
     * Reset the reconnection state after successful connection.
     */
    public void reset() {
        attemptCount.set(0);
        lastAttemptTime.set(null);
        logger.debug("Reconnection state reset");
    }
    
    /**
     * Get the current attempt count.
     */
    public int getAttemptCount() {
        return attemptCount.get();
    }
    
    /**
     * Get the maximum allowed attempts.
     */
    public int getMaxAttempts() {
        return config.maxAttempts();
    }
    
    /**
     * Check if max attempts have been reached.
     */
    public boolean isExhausted() {
        return attemptCount.get() >= config.maxAttempts();
    }
}
