package com.davidparry.agent.protocol.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Record representing an HTTP-based MCP server configuration.
 * This type of server is accessed via HTTP URL (streamable HTTP transport).
 * 
 * @param name The name/identifier for this server
 * @param type The type of the server (should be "http" or "streamable-http")
 * @param url The URL of the HTTP server
 * @param headers Optional HTTP headers to include in requests
 * @param env Optional environment variables for the server
 */
public record HttpServer(
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
     * Constructor for HttpServer with all fields.
     */
    public HttpServer {
        if (type == null) {
            type = McpServer.HTTP_TYPE;
        }
        if (headers == null) {
            headers = Map.of();
        }
        if (env == null) {
            env = Map.of();
        }
    }
    
    /**
     * Constructor for HttpServer when only URL is provided.
     */
    public HttpServer(String url) {
        this(null, McpServer.HTTP_TYPE, url, null, null);
    }
    
    /**
     * Constructor for HttpServer with URL and headers.
     */
    public HttpServer(String url, Map<String, String> headers) {
        this(null, McpServer.HTTP_TYPE, url, headers, null);
    }
    
    /**
     * Constructor for HttpServer without name (name set later).
     */
    public HttpServer(String type, String url, Map<String, String> headers, Map<String, String> env) {
        this(null, type, url, headers, env);
    }
    
    /**
     * Creates a copy of this server with the given name.
     */
    public HttpServer withName(String name) {
        return new HttpServer(name, this.type, this.url, this.headers, this.env);
    }
}
