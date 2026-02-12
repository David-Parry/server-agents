package com.davidparry.agent.protocol.mcp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom deserializer for McpServer that determines the concrete type
 * based on the "type" field or presence of specific fields in the JSON.
 * 
 * Supports three server types:
 * - stdio: Command-based servers (identified by "command" field or type="stdio")
 * - http: HTTP-based servers (identified by type="http" or "streamable-http")
 * - sse: SSE-based servers (identified by type="sse")
 */
public class McpServerDeserializer extends JsonDeserializer<McpServer> {

    @Override
    public McpServer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();
        
        String type = node.has("type") ? node.get("type").asText() : null;
        
        // Determine server type based on explicit type field or field presence
        if (isStdioServer(node, type)) {
            return deserializeStdioServer(node);
        } else if (isSseServer(type)) {
            return deserializeSseServer(node);
        } else if (isHttpServer(node, type)) {
            return deserializeHttpServer(node);
        }
        
        throw new IllegalArgumentException("Unable to determine MCP server type from JSON: " + node);
    }
    
    /**
     * Determines if a JsonNode represents a STDIO server configuration.
     */
    private boolean isStdioServer(JsonNode node, String type) {
        // Explicit type check
        if (McpServer.STDIO_TYPE.equalsIgnoreCase(type)) {
            return true;
        }
        // If no type specified but has "command" field, it's a STDIO server
        if (type == null && node.has("command")) {
            return true;
        }
        return false;
    }
    
    /**
     * Determines if a type represents an SSE server configuration.
     */
    private boolean isSseServer(String type) {
        return McpServer.SSE_TYPE.equalsIgnoreCase(type);
    }
    
    /**
     * Determines if a JsonNode represents an HTTP server configuration.
     */
    private boolean isHttpServer(JsonNode node, String type) {
        if (McpServer.HTTP_TYPE.equalsIgnoreCase(type) || 
            McpServer.STREAMABLE_HTTP_TYPE.equalsIgnoreCase(type)) {
            return true;
        }
        // If has URL and no explicit type, default to HTTP
        if (type == null && node.has("url")) {
            return true;
        }
        return false;
    }
    
    /**
     * Deserializes a STDIO server from JSON.
     */
    private StdioServer deserializeStdioServer(JsonNode node) {
        String type = node.has("type") ? node.get("type").asText() : McpServer.STDIO_TYPE;
        String command = node.has("command") ? node.get("command").asText() : null;
        
        List<String> args = new ArrayList<>();
        if (node.has("args") && node.get("args").isArray()) {
            for (JsonNode argNode : node.get("args")) {
                args.add(argNode.asText());
            }
        }
        
        Map<String, String> env = parseEnvMap(node);
        
        return new StdioServer(null, type, command, args, env);
    }
    
    /**
     * Deserializes an HTTP server from JSON.
     */
    private HttpServer deserializeHttpServer(JsonNode node) {
        String type = node.has("type") ? node.get("type").asText() : McpServer.HTTP_TYPE;
        String url = node.has("url") ? node.get("url").asText() : null;
        
        Map<String, String> headers = parseHeadersMap(node);
        Map<String, String> env = parseEnvMap(node);
        
        return new HttpServer(null, type, url, headers, env);
    }
    
    /**
     * Deserializes an SSE server from JSON.
     * Note: This method is only called when type="sse" is explicitly set.
     */
    private SseServer deserializeSseServer(JsonNode node) {
        // Type is always present when this method is called (isSseServer checks for it)
        String type = node.get("type").asText();
        String url = node.has("url") ? node.get("url").asText() : null;
        
        Map<String, String> headers = parseHeadersMap(node);
        Map<String, String> env = parseEnvMap(node);
        
        return new SseServer(null, type, url, headers, env);
    }
    
    /**
     * Parses the "env" field from a JSON node.
     */
    private Map<String, String> parseEnvMap(JsonNode node) {
        Map<String, String> env = new HashMap<>();
        if (node.has("env") && node.get("env").isObject()) {
            node.get("env").fields().forEachRemaining(
                entry -> env.put(entry.getKey(), entry.getValue().asText())
            );
        }
        return env;
    }
    
    /**
     * Parses the "headers" field from a JSON node.
     */
    private Map<String, String> parseHeadersMap(JsonNode node) {
        Map<String, String> headers = new HashMap<>();
        if (node.has("headers") && node.get("headers").isObject()) {
            node.get("headers").fields().forEachRemaining(
                entry -> headers.put(entry.getKey(), entry.getValue().asText())
            );
        }
        return headers;
    }
}
