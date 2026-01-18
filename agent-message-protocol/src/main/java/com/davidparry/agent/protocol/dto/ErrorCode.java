package com.davidparry.agent.protocol.dto;

/**
 * Error codes for error notifications.
 * Unified set covering all error scenarios for client-server communication.
 * Enums are inherently thread-safe.
 */
public enum ErrorCode {
    // Authentication & Authorization
    AUTHENTICATION_FAILED,
    RATE_LIMITED,
    CONNECTION_LIMIT_EXCEEDED,
    
    // Session Management
    SESSION_NOT_FOUND,
    SESSION_EXPIRED,
    SESSION_TIMEOUT,
    SESSION_LIMIT_EXCEEDED,
    
    // Tool Execution
    TOOL_NOT_FOUND,
    TOOL_EXECUTION_FAILED,
    TOOL_CALL_TIMEOUT,
    TOOL_CALL_FAILED,
    
    // Communication
    CONNECTION_ERROR,
    INVALID_MESSAGE,
    
    // System
    INTERNAL_ERROR
}
