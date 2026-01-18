package com.davidparry.agent.protocol.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Record representing a STDIO-based MCP server configuration.
 * This type of server is launched via a command with arguments.
 * 
 * @param name The name/identifier for this server
 * @param type The type of the server (should be "stdio")
 * @param command The command to execute to start the server
 * @param args The list of arguments to pass to the command
 * @param env Optional environment variables for the server process
 */
public record StdioServer(
    String name,
    
    @JsonProperty("type")
    String type,
    
    @JsonProperty("command")
    String command,
    
    @JsonProperty("args")
    List<String> args,
    
    @JsonProperty("env")
    Map<String, String> env
) implements McpServer {
    
    /**
     * Constructor for StdioServer with all fields.
     */
    public StdioServer {
        if (type == null) {
            type = McpServer.STDIO_TYPE;
        }
        if (args == null) {
            args = List.of();
        }
        if (env == null) {
            env = Map.of();
        }
    }
    
    /**
     * Constructor for StdioServer without name (name set later).
     */
    public StdioServer(String command, List<String> args, Map<String, String> env) {
        this(null, McpServer.STDIO_TYPE, command, args, env);
    }
    
    /**
     * Creates a copy of this server with the given name.
     */
    public StdioServer withName(String name) {
        return new StdioServer(name, this.type, this.command, this.args, this.env);
    }
}
