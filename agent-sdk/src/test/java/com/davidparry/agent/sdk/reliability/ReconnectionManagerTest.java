package com.davidparry.agent.sdk.reliability;

import com.davidparry.agent.sdk.config.AgentSdkProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconnectionManagerTest {

    private AgentSdkProperties properties(boolean enabled, int maxAttempts, long initialDelay, long maxDelay, double multiplier) {
        return new AgentSdkProperties(
            "api-key",
            "ws://localhost:8080/mcp-proxy",
            new AgentSdkProperties.ConnectionConfig(30, 30, 300, AgentSdkProperties.DEFAULT_TEXT_MESSAGE_BUFFER_SIZE),
            new AgentSdkProperties.ReconnectConfig(enabled, maxAttempts, initialDelay, maxDelay, multiplier),
            new AgentSdkProperties.ChainConfig(),
            "agent.yml",
            new AgentSdkProperties.McpConfig(5)
        );
    }

    @Test
    void shouldUseExponentialBackoffAndCapAtMaxDelay() {
        ReconnectionManager manager = new ReconnectionManager(properties(true, 5, 1000, 3000, 2.0));

        assertEquals(1000, manager.getNextDelay().toMillis());
        assertEquals(2000, manager.getNextDelay().toMillis());
        assertEquals(3000, manager.getNextDelay().toMillis());
        assertEquals(3000, manager.getNextDelay().toMillis());
    }

    @Test
    void shouldStopAttemptingAfterMaxAttempts() {
        ReconnectionManager manager = new ReconnectionManager(properties(true, 2, 500, 2000, 2.0));

        assertTrue(manager.shouldAttemptReconnect());
        manager.getNextDelay();
        assertTrue(manager.shouldAttemptReconnect());
        manager.getNextDelay();
        assertFalse(manager.shouldAttemptReconnect());
        assertTrue(manager.isExhausted());
    }

    @Test
    void shouldNotReconnectWhenDisabled() {
        ReconnectionManager manager = new ReconnectionManager(properties(false, 3, 500, 2000, 2.0));
        assertFalse(manager.shouldAttemptReconnect());
    }

    @Test
    void resetShouldClearAttemptState() {
        ReconnectionManager manager = new ReconnectionManager(properties(true, 3, 500, 2000, 2.0));

        manager.getNextDelay();
        manager.getNextDelay();
        assertEquals(2, manager.getAttemptCount());

        manager.reset();
        assertEquals(0, manager.getAttemptCount());
        assertFalse(manager.isExhausted());
    }
}
