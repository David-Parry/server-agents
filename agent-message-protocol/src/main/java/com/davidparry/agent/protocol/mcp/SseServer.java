package com.davidparry.agent.protocol.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Record representing an SSE (Server-Sent Events) based MCP server configuration.
 * This type of server is accessed via SSE URL (legacy remote transport).
 * 
 * @param name The name/identifier for this server
 * @param type The type of the server (should be "sse")
 * @param url The URL of the SSE server
 * @param headers Optional HTTP headers to include in requests
 * @param env Optional environment variables for the server
 */
public record SseServer(
    String name,
    
    @JsonProperty("type")
    String type,
    
    @JsonProperty("url")
    String url,
    
    @JsonProperty("headers")
    Map<String, String> headers,
    
    @JsonProperty("env")
    Map<String, String> env
) implements McpServer {
    
    /**
     * Constructor for SseServer with all fields.
     */
    public SseServer {
        if (type == null) {
            type = McpServer.SSE_TYPE;
        }
        if (headers == null) {
            headers = Map.of();
        }
        if (env == null) {
            env = Map.of();
        }
    }
    
    /**
     * Constructor for SseServer when only URL is provided.
     */
    public SseServer(String url) {
        this(null, McpServer.SSE_TYPE, url, null, null);
    }
    
    /**
     * Constructor for SseServer with URL and headers.
     */
    public SseServer(String url, Map<String, String> headers) {
        this(null, McpServer.SSE_TYPE, url, headers, null);
    }
    
    /**
     * Constructor for SseServer without name (name set later).
     */
    public SseServer(String type, String url, Map<String, String> headers, Map<String, String> env) {
        this(null, type, url, headers, env);
    }
    
    /**
     * Creates a copy of this server with the given name.
     */
    public SseServer withName(String name) {
        return new SseServer(name, this.type, this.url, this.headers, this.env);
    }
}
