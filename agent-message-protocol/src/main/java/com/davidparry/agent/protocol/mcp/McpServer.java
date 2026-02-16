package com.davidparry.agent.protocol.mcp;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;

/**
 * Base interface for MCP server configurations.
 * Uses custom deserializer to handle different server types based on field presence.
 * Supports three types: stdio (command-based), http, and sse.
 */
@JsonDeserialize(using = McpServerDeserializer.class)
public sealed interface McpServer permits StdioServer, HttpServer, SseServer {
    
    String STDIO_TYPE = "stdio";
    String HTTP_TYPE = "http";
    String SSE_TYPE = "sse";
    String STREAMABLE_HTTP_TYPE = "streamable-http";

    /**
     * Gets the type of this MCP server.
     * 
     * @return the server type (stdio, http, or sse)
     */
    String type();

    /**
     * Gets the environment variables for this MCP server.
     * 
     * @return a map of environment variable names to values, or null if not set
     */
    Map<String, String> env();
    
    /**
     * Gets the name/identifier for this server (set during loading).
     * 
     * @return the server name
     */
    String name();
}
