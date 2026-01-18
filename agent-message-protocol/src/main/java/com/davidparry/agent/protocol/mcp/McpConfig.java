package com.davidparry.agent.protocol.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Root configuration record for MCP (Model Context Protocol) configuration.
 * Represents the top-level structure of mcp.json file.
 * 
 * Example JSON structure:
 * <pre>
 * {
 *   "mcpServers": {
 *     "local-tools": {
 *       "type": "stdio",
 *       "command": "npx",
 *       "args": ["-y", "@modelcontextprotocol/server-memory"],
 *       "env": { "API_KEY": "your-key" }
 *     },
 *     "remote-api": {
 *       "type": "http",
 *       "url": "https://mcp.sentry.dev/mcp"
 *     },
 *     "legacy-remote": {
 *       "type": "sse",
 *       "url": "https://example.com/sse"
 *     }
 *   }
 * }
 * </pre>
 * 
 * @param mcpServers Map of MCP server names to their configurations
 */
public record McpConfig(
    @JsonProperty("mcpServers")
    Map<String, McpServer> mcpServers
) {
    
    /**
     * Default constructor with empty servers map.
     */
    public McpConfig() {
        this(Map.of());
    }
    
    /**
     * Checks if this configuration has any servers defined.
     */
    public boolean hasServers() {
        return mcpServers != null && !mcpServers.isEmpty();
    }
    
    /**
     * Gets the number of configured servers.
     */
    public int serverCount() {
        return mcpServers != null ? mcpServers.size() : 0;
    }
}
