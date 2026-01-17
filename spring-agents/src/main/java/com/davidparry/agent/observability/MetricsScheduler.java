package com.davidparry.agent.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for periodic metrics refresh.
 * Updates database-backed Prometheus metrics at regular intervals.
 */
@Component
public class MetricsScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MetricsScheduler.class);

    private final PrometheusMetricsService metricsService;

    public MetricsScheduler(PrometheusMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * Refresh database-backed metrics every 30 seconds.
     * Initial delay of 5 seconds allows the application to fully start.
     */
    @Scheduled(fixedRate = 30000, initialDelay = 5000)
    public void refreshMetrics() {
        try {
            metricsService.refreshMetrics();
            logger.trace("Scheduled metrics refresh completed");
        } catch (Exception e) {
            logger.error("Scheduled metrics refresh failed", e);
        }
    }
}
