package com.davidparry.agent.observability;

import com.davidparry.agent.dto.CustomerMetricsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Custom Actuator endpoint that exposes per-customer metrics in Prometheus format.
 * 
 * <p>Accessible at: <code>/actuator/prometheuscustomers</code></p>
 * 
 * <p>This endpoint is designed to be scraped by Prometheus separately from the main
 * <code>/actuator/prometheus</code> endpoint to avoid high cardinality issues on the
 * main metrics endpoint.</p>
 * 
 * <h2>Prometheus Scrape Configuration</h2>
 * <pre>
 * scrape_configs:
 *   # Main application metrics (low cardinality)
 *   - job_name: 'spring-agents'
 *     metrics_path: '/actuator/prometheus'
 *     scrape_interval: 30s
 *     static_configs:
 *       - targets: ['localhost:8080']
 *   
 *   # Per-customer metrics (higher cardinality, less frequent)
 *   - job_name: 'spring-agents-customers'
 *     metrics_path: '/actuator/prometheuscustomers'
 *     scrape_interval: 60s
 *     scrape_timeout: 30s
 *     static_configs:
 *       - targets: ['localhost:8080']
 * </pre>
 * 
 * <h2>Exposed Metrics</h2>
 * <ul>
 *   <li><code>spring_agents_customer_connections_active</code> - Active WebSocket connections</li>
 *   <li><code>spring_agents_customer_connections_opened_total</code> - Total connections opened</li>
 *   <li><code>spring_agents_customer_connections_closed_total</code> - Total connections closed</li>
 *   <li><code>spring_agents_customer_sessions_active</code> - Active sessions</li>
 *   <li><code>spring_agents_customer_sessions_by_state</code> - Sessions by state</li>
 *   <li><code>spring_agents_customer_sessions_*_total</code> - Session outcome counters</li>
 *   <li><code>spring_agents_customer_tool_calls_*</code> - Tool call metrics</li>
 *   <li><code>spring_agents_customer_session_duration_*_ms</code> - Duration metrics</li>
 *   <li><code>spring_agents_customer_tokens_*</code> - Token usage metrics</li>
 *   <li><code>spring_agents_customer_circuit_breakers_*</code> - Circuit breaker metrics</li>
 * </ul>
 */
@Component
@Endpoint(id = "prometheuscustomers")
public class CustomerPrometheusEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(CustomerPrometheusEndpoint.class);

    private final CustomerMetricsService customerMetricsService;

    public CustomerPrometheusEndpoint(CustomerMetricsService customerMetricsService) {
        this.customerMetricsService = customerMetricsService;
        logger.info("CustomerPrometheusEndpoint initialized at /actuator/prometheuscustomers");
    }

    /**
     * Scrape endpoint that returns metrics in Prometheus text format.
     * 
     * @return Prometheus-formatted metrics text
     */
    @ReadOperation(produces = "text/plain; version=0.0.4; charset=utf-8")
    public String scrape() {
        List<CustomerMetricsSnapshot> snapshots = customerMetricsService.getConnectedCustomerMetrics();

        StringBuilder sb = new StringBuilder();

        // Add HELP and TYPE declarations for all metrics
        appendMetricHeader(sb, "spring_agents_customer_connections_active", "gauge",
                "Active WebSocket connections for customer");
        appendMetricHeader(sb, "spring_agents_customer_connections_opened_total", "counter",
                "Total connections opened for customer");
        appendMetricHeader(sb, "spring_agents_customer_connections_closed_total", "counter",
                "Total connections closed for customer");
        appendMetricHeader(sb, "spring_agents_customer_sessions_active", "gauge",
                "Active sessions for customer");
        appendMetricHeader(sb, "spring_agents_customer_sessions_by_state", "gauge",
                "Sessions by state for customer");
        appendMetricHeader(sb, "spring_agents_customer_sessions_created_total", "counter",
                "Total sessions created for customer");
        appendMetricHeader(sb, "spring_agents_customer_sessions_completed_total", "counter",
                "Total sessions completed for customer");
        appendMetricHeader(sb, "spring_agents_customer_sessions_failed_total", "counter",
                "Total sessions failed for customer");
        appendMetricHeader(sb, "spring_agents_customer_sessions_cancelled_total", "counter",
                "Total sessions cancelled for customer");
        appendMetricHeader(sb, "spring_agents_customer_sessions_timedout_total", "counter",
                "Total sessions timed out for customer");
        appendMetricHeader(sb, "spring_agents_customer_tool_calls_total", "counter",
                "Total tool calls for customer");
        appendMetricHeader(sb, "spring_agents_customer_tool_calls_failed_total", "counter",
                "Total failed tool calls for customer");
        appendMetricHeader(sb, "spring_agents_customer_tool_calls_pending", "gauge",
                "Pending tool calls for customer");
        appendMetricHeader(sb, "spring_agents_customer_session_duration_avg_ms", "gauge",
                "Average session duration in milliseconds");
        appendMetricHeader(sb, "spring_agents_customer_session_duration_max_ms", "gauge",
                "Maximum session duration in milliseconds");
        appendMetricHeader(sb, "spring_agents_customer_tool_call_duration_avg_ms", "gauge",
                "Average tool call duration in milliseconds");
        appendMetricHeader(sb, "spring_agents_customer_tokens_used_total", "counter",
                "Total LLM tokens used by customer");
        appendMetricHeader(sb, "spring_agents_customer_tokens_allowed_total", "gauge",
                "Total LLM tokens allowed for customer");
        appendMetricHeader(sb, "spring_agents_customer_circuit_breakers_total", "gauge",
                "Total circuit breakers for customer");
        appendMetricHeader(sb, "spring_agents_customer_circuit_breakers_by_state", "gauge",
                "Circuit breakers by state for customer");
        appendMetricHeader(sb, "spring_agents_customer_enabled", "gauge",
                "Whether customer is enabled (1=enabled, 0=disabled)");

        // Append metrics for each customer
        for (CustomerMetricsSnapshot snapshot : snapshots) {
            String baseLabels = String.format("customer_id=\"%s\",customer_name=\"%s\"",
                    escapeLabel(snapshot.customerId()),
                    escapeLabel(snapshot.customerName()));

            // Customer enabled status
            appendMetric(sb, "spring_agents_customer_enabled", baseLabels,
                    snapshot.enabled() ? 1 : 0);

            // Connection metrics
            appendMetric(sb, "spring_agents_customer_connections_active", baseLabels,
                    snapshot.activeConnections());
            appendMetric(sb, "spring_agents_customer_connections_opened_total", baseLabels,
                    snapshot.totalConnectionsOpened());
            appendMetric(sb, "spring_agents_customer_connections_closed_total", baseLabels,
                    snapshot.totalConnectionsClosed());

            // Session metrics - active count
            appendMetric(sb, "spring_agents_customer_sessions_active", baseLabels,
                    snapshot.activeSessions());

            // Session metrics - by state
            appendMetric(sb, "spring_agents_customer_sessions_by_state",
                    baseLabels + ",state=\"PENDING\"", snapshot.pendingSessions());
            appendMetric(sb, "spring_agents_customer_sessions_by_state",
                    baseLabels + ",state=\"EXECUTING\"", snapshot.executingSessions());
            appendMetric(sb, "spring_agents_customer_sessions_by_state",
                    baseLabels + ",state=\"WAITING_FOR_TOOL\"", snapshot.waitingForToolSessions());

            // Session metrics - historical counters
            appendMetric(sb, "spring_agents_customer_sessions_created_total", baseLabels,
                    snapshot.totalSessionsCreated());
            appendMetric(sb, "spring_agents_customer_sessions_completed_total", baseLabels,
                    snapshot.totalSessionsCompleted());
            appendMetric(sb, "spring_agents_customer_sessions_failed_total", baseLabels,
                    snapshot.totalSessionsFailed());
            appendMetric(sb, "spring_agents_customer_sessions_cancelled_total", baseLabels,
                    snapshot.totalSessionsCancelled());
            appendMetric(sb, "spring_agents_customer_sessions_timedout_total", baseLabels,
                    snapshot.totalSessionsTimedOut());

            // Tool call metrics
            appendMetric(sb, "spring_agents_customer_tool_calls_total", baseLabels,
                    snapshot.totalToolCalls());
            appendMetric(sb, "spring_agents_customer_tool_calls_failed_total", baseLabels,
                    snapshot.totalToolCallsFailed());
            appendMetric(sb, "spring_agents_customer_tool_calls_pending", baseLabels,
                    snapshot.pendingToolCalls());

            // Duration metrics
            appendMetric(sb, "spring_agents_customer_session_duration_avg_ms", baseLabels,
                    snapshot.avgSessionDurationMs());
            appendMetric(sb, "spring_agents_customer_session_duration_max_ms", baseLabels,
                    snapshot.maxSessionDurationMs());
            appendMetric(sb, "spring_agents_customer_tool_call_duration_avg_ms", baseLabels,
                    snapshot.avgToolCallDurationMs());

            // Token usage
            appendMetric(sb, "spring_agents_customer_tokens_used_total", baseLabels,
                    snapshot.totalTokensUsed());
            appendMetric(sb, "spring_agents_customer_tokens_allowed_total", baseLabels,
                    snapshot.totalTokensAllowed());

            // Circuit breaker metrics
            appendMetric(sb, "spring_agents_customer_circuit_breakers_total", baseLabels,
                    snapshot.circuitBreakersTotal());
            appendMetric(sb, "spring_agents_customer_circuit_breakers_by_state",
                    baseLabels + ",state=\"OPEN\"", snapshot.circuitBreakersOpen());
            appendMetric(sb, "spring_agents_customer_circuit_breakers_by_state",
                    baseLabels + ",state=\"CLOSED\"", snapshot.circuitBreakersClosed());
            appendMetric(sb, "spring_agents_customer_circuit_breakers_by_state",
                    baseLabels + ",state=\"HALF_OPEN\"", snapshot.circuitBreakersHalfOpen());
        }

        // Add a meta metric for the number of customers being tracked
        sb.append("# HELP spring_agents_customers_tracked_total Number of customers with metrics\n");
        sb.append("# TYPE spring_agents_customers_tracked_total gauge\n");
        sb.append("spring_agents_customers_tracked_total ").append(snapshots.size()).append("\n");

        logger.trace("Generated Prometheus metrics for {} customers", snapshots.size());
        return sb.toString();
    }

    /**
     * Appends a metric header (HELP and TYPE) to the output.
     */
    private void appendMetricHeader(StringBuilder sb, String name, String type, String help) {
        sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
        sb.append("# TYPE ").append(name).append(" ").append(type).append("\n");
    }

    /**
     * Appends a metric value to the output.
     */
    private void appendMetric(StringBuilder sb, String name, String labels, Number value) {
        sb.append(name).append("{").append(labels).append("} ").append(formatValue(value)).append("\n");
    }

    /**
     * Formats a numeric value for Prometheus output.
     */
    private String formatValue(Number value) {
        if (value instanceof Double || value instanceof Float) {
            double d = value.doubleValue();
            if (Double.isNaN(d)) {
                return "NaN";
            } else if (Double.isInfinite(d)) {
                return d > 0 ? "+Inf" : "-Inf";
            }
            // Format with reasonable precision
            return String.format("%.6f", d).replaceAll("0+$", "").replaceAll("\\.$", ".0");
        }
        return value.toString();
    }

    /**
     * Escapes a label value for Prometheus format.
     * Handles backslashes, double quotes, and newlines.
     */
    private String escapeLabel(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
