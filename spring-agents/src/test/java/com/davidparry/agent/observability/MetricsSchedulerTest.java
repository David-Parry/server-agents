package com.davidparry.agent.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MetricsScheduler.
 * Tests scheduled metrics refresh functionality.
 */
@ExtendWith(MockitoExtension.class)
class MetricsSchedulerTest {

    @Mock
    private PrometheusMetricsService metricsService;

    private MetricsScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MetricsScheduler(metricsService);
    }

    @Test
    void refreshMetrics_shouldDelegateToMetricsService() {
        // When
        scheduler.refreshMetrics();

        // Then
        verify(metricsService).refreshMetrics();
    }

    @Test
    void refreshMetrics_shouldHandleExceptionsGracefully() {
        // Given
        doThrow(new RuntimeException("Test exception")).when(metricsService).refreshMetrics();

        // When/Then - should not throw
        assertDoesNotThrow(() -> scheduler.refreshMetrics());
    }

    @Test
    void refreshMetrics_canBeCalledMultipleTimes() {
        // When
        scheduler.refreshMetrics();
        scheduler.refreshMetrics();
        scheduler.refreshMetrics();

        // Then
        verify(metricsService, times(3)).refreshMetrics();
    }
}
