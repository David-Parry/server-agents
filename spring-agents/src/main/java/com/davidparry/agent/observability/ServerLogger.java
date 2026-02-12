package com.davidparry.agent.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Structured logging utility for the MCP Proxy server.
 * Provides consistent MDC context management and log formatting.
 */
@Component
public class ServerLogger {

    private static final Logger logger = LoggerFactory.getLogger(ServerLogger.class);

    // MDC Keys
    public static final String CONNECTION_ID = "connectionId";
    public static final String CLIENT_ID = "clientId";
    public static final String SESSION_ID = "sessionId";
    public static final String TOOL_NAME = "toolName";
    public static final String REQUEST_ID = "requestId";

    /**
     * Executes a runnable with MDC context.
     *
     * @param context the MDC context to set
     * @param runnable the code to execute
     */
    public void withContext(Map<String, String> context, Runnable runnable) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        try {
            if (context != null) {
                context.forEach(MDC::put);
            }
            runnable.run();
        } finally {
            MDC.setContextMap(previousContext != null ? previousContext : Map.of());
        }
    }

    /**
     * Executes a supplier with MDC context.
     *
     * @param context the MDC context to set
     * @param supplier the code to execute
     * @param <T> the return type
     * @return the result of the supplier
     */
    public <T> T withContext(Map<String, String> context, Supplier<T> supplier) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        try {
            if (context != null) {
                context.forEach(MDC::put);
            }
            return supplier.get();
        } finally {
            MDC.setContextMap(previousContext != null ? previousContext : Map.of());
        }
    }

    /**
     * Creates a context builder for fluent MDC setup.
     *
     * @return a new context builder
     */
    public static ContextBuilder context() {
        return new ContextBuilder();
    }

    /**
     * Logs a connection event.
     */
    public void logConnectionEstablished(String connectionId, String clientId) {
        try (var ignored = MDC.putCloseable(CONNECTION_ID, connectionId);
             var ignored2 = MDC.putCloseable(CLIENT_ID, clientId)) {
            logger.info("Connection established");
        }
    }

    /**
     * Logs a connection closed event.
     */
    public void logConnectionClosed(String connectionId, String clientId, String reason) {
        try (var ignored = MDC.putCloseable(CONNECTION_ID, connectionId);
             var ignored2 = MDC.putCloseable(CLIENT_ID, clientId)) {
            logger.info("Connection closed: {}", reason);
        }
    }

    /**
     * Logs a session started event.
     */
    public void logSessionStarted(String connectionId, String sessionId, String prompt) {
        try (var ignored = MDC.putCloseable(CONNECTION_ID, connectionId);
             var ignored2 = MDC.putCloseable(SESSION_ID, sessionId)) {
            String truncatedPrompt = prompt.length() > 100 
                    ? prompt.substring(0, 100) + "..." 
                    : prompt;
            logger.info("Session started: {}", truncatedPrompt);
        }
    }

    /**
     * Logs a session completed event.
     */
    public void logSessionCompleted(String connectionId, String sessionId, long durationMs) {
        try (var ignored = MDC.putCloseable(CONNECTION_ID, connectionId);
             var ignored2 = MDC.putCloseable(SESSION_ID, sessionId)) {
            logger.info("Session completed in {}ms", durationMs);
        }
    }

    /**
     * Logs a session failed event.
     */
    public void logSessionFailed(String connectionId, String sessionId, String error) {
        try (var ignored = MDC.putCloseable(CONNECTION_ID, connectionId);
             var ignored2 = MDC.putCloseable(SESSION_ID, sessionId)) {
            logger.error("Session failed: {}", error);
        }
    }

    /**
     * Logs a tool call event.
     */
    public void logToolCall(String connectionId, String sessionId, String toolName, String requestId) {
        try (var ignored = MDC.putCloseable(CONNECTION_ID, connectionId);
             var ignored2 = MDC.putCloseable(SESSION_ID, sessionId);
             var ignored3 = MDC.putCloseable(TOOL_NAME, toolName);
             var ignored4 = MDC.putCloseable(REQUEST_ID, requestId)) {
            logger.info("Tool call initiated");
        }
    }

    /**
     * Logs a tool call completed event.
     */
    public void logToolCallCompleted(String connectionId, String sessionId, String toolName, 
                                     String requestId, long durationMs) {
        try (var ignored = MDC.putCloseable(CONNECTION_ID, connectionId);
             var ignored2 = MDC.putCloseable(SESSION_ID, sessionId);
             var ignored3 = MDC.putCloseable(TOOL_NAME, toolName);
             var ignored4 = MDC.putCloseable(REQUEST_ID, requestId)) {
            logger.info("Tool call completed in {}ms", durationMs);
        }
    }

    /**
     * Logs a tool call failed event.
     */
    public void logToolCallFailed(String connectionId, String sessionId, String toolName,
                                  String requestId, String error) {
        try (var ignored = MDC.putCloseable(CONNECTION_ID, connectionId);
             var ignored2 = MDC.putCloseable(SESSION_ID, sessionId);
             var ignored3 = MDC.putCloseable(TOOL_NAME, toolName);
             var ignored4 = MDC.putCloseable(REQUEST_ID, requestId)) {
            logger.error("Tool call failed: {}", error);
        }
    }

    /**
     * Builder for creating MDC context maps.
     */
    public static class ContextBuilder {
        private final java.util.HashMap<String, String> context = new java.util.HashMap<>();

        public ContextBuilder connectionId(String connectionId) {
            context.put(CONNECTION_ID, connectionId);
            return this;
        }

        public ContextBuilder clientId(String clientId) {
            context.put(CLIENT_ID, clientId);
            return this;
        }

        public ContextBuilder sessionId(String sessionId) {
            context.put(SESSION_ID, sessionId);
            return this;
        }

        public ContextBuilder toolName(String toolName) {
            context.put(TOOL_NAME, toolName);
            return this;
        }

        public ContextBuilder requestId(String requestId) {
            context.put(REQUEST_ID, requestId);
            return this;
        }

        public Map<String, String> build() {
            return Map.copyOf(context);
        }
    }
}
