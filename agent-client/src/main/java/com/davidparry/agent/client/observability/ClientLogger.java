package com.davidparry.agent.client.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Structured logging utilities for the Agent Client.
 */
public class ClientLogger {
    
    private static final Logger logger = LoggerFactory.getLogger("agent.client");
    
    public static void logConnectionEvent(String connectionId, String event) {
        try (var ignored = MDC.putCloseable("connectionId", connectionId)) {
            logger.info("Connection event: {}", event);
        }
    }
    
    public static void logSessionEvent(String sessionId, String event) {
        try (var ignored = MDC.putCloseable("sessionId", sessionId)) {
            logger.info("Session event: {}", event);
        }
    }
    
    public static void logToolCall(String sessionId, String toolName, String requestId, String event) {
        try (var ignored = MDC.putCloseable("sessionId", sessionId);
             var ignored2 = MDC.putCloseable("toolName", toolName);
             var ignored3 = MDC.putCloseable("requestId", requestId)) {
            logger.info("Tool call: {}", event);
        }
    }
    
    public static void logMcpServer(String serverName, String event) {
        try (var ignored = MDC.putCloseable("mcpServer", serverName)) {
            logger.info("MCP server: {}", event);
        }
    }
    
    public static void logError(String context, String message, Throwable error) {
        try (var ignored = MDC.putCloseable("context", context)) {
            logger.error(message, error);
        }
    }
}
