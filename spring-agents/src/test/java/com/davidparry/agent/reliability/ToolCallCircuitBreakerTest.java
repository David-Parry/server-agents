package com.davidparry.agent.reliability;

import com.davidparry.agent.config.McpProxyProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ToolCallCircuitBreaker.
 */
@ExtendWith(MockitoExtension.class)
class ToolCallCircuitBreakerTest {

    @Mock
    private McpProxyProperties properties;

    private ToolCallCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        McpProxyProperties.CircuitBreakerConfig config = new McpProxyProperties.CircuitBreakerConfig(
                5, 3, 30, 3
        );
        when(properties.circuitBreaker()).thenReturn(config);

        circuitBreaker = new ToolCallCircuitBreaker(properties, new SimpleMeterRegistry());
    }

    @Test
    void getBreaker_shouldCreateNewBreakerForNewKey() {
        // When
        CircuitBreaker breaker1 = circuitBreaker.getBreaker("conn-1", "tool-a");
        CircuitBreaker breaker2 = circuitBreaker.getBreaker("conn-1", "tool-b");
        CircuitBreaker breaker3 = circuitBreaker.getBreaker("conn-2", "tool-a");

        // Then
        assertNotNull(breaker1);
        assertNotNull(breaker2);
        assertNotNull(breaker3);
        assertNotSame(breaker1, breaker2);
        assertNotSame(breaker1, breaker3);
    }

    @Test
    void getBreaker_shouldReturnSameBreakerForSameKey() {
        // When
        CircuitBreaker breaker1 = circuitBreaker.getBreaker("conn-1", "tool-a");
        CircuitBreaker breaker2 = circuitBreaker.getBreaker("conn-1", "tool-a");

        // Then
        assertSame(breaker1, breaker2);
    }

    @Test
    void isToolAvailable_shouldReturnTrueForNewTool() {
        // When
        boolean available = circuitBreaker.isToolAvailable("conn-1", "new-tool");

        // Then
        assertTrue(available);
    }

    @Test
    void isToolAvailable_shouldReturnTrueWhenBreakerClosed() {
        // Given
        circuitBreaker.getBreaker("conn-1", "tool-a"); // Create breaker

        // When
        boolean available = circuitBreaker.isToolAvailable("conn-1", "tool-a");

        // Then
        assertTrue(available);
    }

    @Test
    void getState_shouldReturnNullForUnknownBreaker() {
        // When
        CircuitBreaker.State state = circuitBreaker.getState("unknown-conn", "unknown-tool");

        // Then
        assertNull(state);
    }

    @Test
    void getState_shouldReturnClosedForNewBreaker() {
        // Given
        circuitBreaker.getBreaker("conn-1", "tool-a");

        // When
        CircuitBreaker.State state = circuitBreaker.getState("conn-1", "tool-a");

        // Then
        assertEquals(CircuitBreaker.State.CLOSED, state);
    }

    @Test
    void reset_shouldResetBreaker() {
        // Given
        CircuitBreaker breaker = circuitBreaker.getBreaker("conn-1", "tool-a");
        // Simulate some failures to change state
        for (int i = 0; i < 10; i++) {
            try {
                breaker.executeSupplier(() -> {
                    throw new RuntimeException("Simulated failure");
                });
            } catch (Exception ignored) {
            }
        }

        // When
        circuitBreaker.reset("conn-1", "tool-a");

        // Then
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState("conn-1", "tool-a"));
    }

    @Test
    void reset_shouldHandleUnknownBreaker() {
        // When - should not throw
        circuitBreaker.reset("unknown-conn", "unknown-tool");

        // Then - no exception
    }

    @Test
    void removeConnection_shouldRemoveAllBreakersForConnection() {
        // Given
        circuitBreaker.getBreaker("conn-1", "tool-a");
        circuitBreaker.getBreaker("conn-1", "tool-b");
        circuitBreaker.getBreaker("conn-2", "tool-a");

        // When
        circuitBreaker.removeConnection("conn-1");

        // Then
        assertNull(circuitBreaker.getState("conn-1", "tool-a"));
        assertNull(circuitBreaker.getState("conn-1", "tool-b"));
        assertNotNull(circuitBreaker.getState("conn-2", "tool-a"));
    }

    @Test
    void getAllMetrics_shouldReturnMetricsForAllBreakers() {
        // Given
        circuitBreaker.getBreaker("conn-1", "tool-a");
        circuitBreaker.getBreaker("conn-1", "tool-b");
        circuitBreaker.getBreaker("conn-2", "tool-a");

        // When
        Map<String, ToolCallCircuitBreaker.CircuitBreakerMetrics> metrics = circuitBreaker.getAllMetrics();

        // Then
        assertEquals(3, metrics.size());
        assertTrue(metrics.containsKey("conn-1:tool-a"));
        assertTrue(metrics.containsKey("conn-1:tool-b"));
        assertTrue(metrics.containsKey("conn-2:tool-a"));
    }

    @Test
    void getAllMetrics_shouldReturnCorrectMetricsValues() {
        // Given
        CircuitBreaker breaker = circuitBreaker.getBreaker("conn-1", "tool-a");
        // Execute some successful calls
        for (int i = 0; i < 5; i++) {
            breaker.executeSupplier(() -> "success");
        }

        // When
        Map<String, ToolCallCircuitBreaker.CircuitBreakerMetrics> metrics = circuitBreaker.getAllMetrics();

        // Then
        ToolCallCircuitBreaker.CircuitBreakerMetrics toolMetrics = metrics.get("conn-1:tool-a");
        assertNotNull(toolMetrics);
        assertEquals("CLOSED", toolMetrics.state());
        assertEquals(5, toolMetrics.successfulCalls());
        assertEquals(0, toolMetrics.failedCalls());
    }

    @Test
    void countByState_shouldCountCorrectly() {
        // Given
        circuitBreaker.getBreaker("conn-1", "tool-a");
        circuitBreaker.getBreaker("conn-1", "tool-b");
        circuitBreaker.getBreaker("conn-2", "tool-a");

        // When
        long closedCount = circuitBreaker.countByState(CircuitBreaker.State.CLOSED);
        long openCount = circuitBreaker.countByState(CircuitBreaker.State.OPEN);

        // Then
        assertEquals(3, closedCount);
        assertEquals(0, openCount);
    }

    @Test
    void getAverageFailureRate_shouldReturnZeroWhenNoBreakers() {
        // When
        double rate = circuitBreaker.getAverageFailureRate();

        // Then
        assertEquals(0.0, rate);
    }

    @Test
    void getAverageSlowCallRate_shouldReturnZeroWhenNoBreakers() {
        // When
        double rate = circuitBreaker.getAverageSlowCallRate();

        // Then
        assertEquals(0.0, rate);
    }

    @Test
    void getTotalSuccessfulCalls_shouldSumAllBreakers() {
        // Given
        CircuitBreaker breaker1 = circuitBreaker.getBreaker("conn-1", "tool-a");
        CircuitBreaker breaker2 = circuitBreaker.getBreaker("conn-1", "tool-b");

        for (int i = 0; i < 3; i++) {
            breaker1.executeSupplier(() -> "success");
        }
        for (int i = 0; i < 2; i++) {
            breaker2.executeSupplier(() -> "success");
        }

        // When
        long total = circuitBreaker.getTotalSuccessfulCalls();

        // Then
        assertEquals(5, total);
    }

    @Test
    void getTotalFailedCalls_shouldSumAllBreakers() {
        // Given
        CircuitBreaker breaker1 = circuitBreaker.getBreaker("conn-1", "tool-a");
        CircuitBreaker breaker2 = circuitBreaker.getBreaker("conn-1", "tool-b");

        for (int i = 0; i < 3; i++) {
            try {
                breaker1.executeSupplier(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (Exception ignored) {
            }
        }
        for (int i = 0; i < 2; i++) {
            try {
                breaker2.executeSupplier(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (Exception ignored) {
            }
        }

        // When
        long total = circuitBreaker.getTotalFailedCalls();

        // Then
        assertEquals(5, total);
    }

    @Test
    void getTotalSlowCalls_shouldReturnZeroInitially() {
        // Given
        circuitBreaker.getBreaker("conn-1", "tool-a");

        // When
        long total = circuitBreaker.getTotalSlowCalls();

        // Then
        assertEquals(0, total);
    }
}
