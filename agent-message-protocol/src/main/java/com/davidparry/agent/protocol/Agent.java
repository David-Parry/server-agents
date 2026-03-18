package com.davidparry.agent.protocol;
import com.davidparry.agent.protocol.dto.AgentType;
import com.davidparry.agent.protocol.mcp.McpConfig;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Record representing a single agent configuration from agent.yml.
 * 
 * Each agent has:
 * - A name (set from the map key during loading)
 * - A description of what the agent does
 * - A type (e.g., DIAGNOSTICIAN, ANALYST)
 * - Instructions for the agent's behavior
 * - MCP server configurations (as JSON string that gets parsed into McpConfig)
 * - A list of tools the agent can use
 * - An output schema defining the expected response format
 * - An optional graph of conditional transition edges
 * - An optional next agent to hand off to after completion
 * 
 * @param name The name/identifier for this agent (set during loading from map key)
 * @param description A description of what this agent does
 * @param type The type of agent (e.g., DIAGNOSTICIAN, ANALYST)
 * @param instructions The detailed instructions for the agent's behavior
 * @param mcpServers The MCP server configuration as a JSON string
 * @param mcpConfig The parsed MCP configuration (set during loading)
 * @param tools List of tool identifiers the agent can use (format: "server-name.tool-name")
 * @param outputSchema The JSON schema defining the expected output format
 * @param graph Optional conditional transition graph for selecting the next agent
 * @param nextAgent The optional name of the next agent to hand off to after this agent completes
 */
public record Agent(
    String name,
    
    @JsonProperty("description")
    String description,
    
    @JsonProperty("type")
    AgentType type,
    
    @JsonProperty("instructions")
    String instructions,
    
    @JsonProperty("mcpServers")
    String mcpServers,
    
    McpConfig mcpConfig,
    
    @JsonProperty("tools")
    List<String> tools,
    
    @JsonProperty("output_schema")
    String outputSchema,

    @JsonProperty("graph")
    AgentGraph graph,

    @JsonProperty("next_agent")
    String nextAgent

) {
    
    /**
     * Compact constructor with defaults.
     */
    public Agent {
        if (tools == null) {
            tools = List.of();
        }
        if (graph == null) {
            graph = AgentGraph.empty();
        }
    }

    /**
     * Backward-compatible constructor used by existing callers.
     */
    public Agent(
            String name,
            String description,
            AgentType type,
            String instructions,
            String mcpServers,
            McpConfig mcpConfig,
            List<String> tools,
            String outputSchema,
            String nextAgent
    ) {
        this(name, description, type, instructions, mcpServers, mcpConfig, tools, outputSchema, AgentGraph.empty(),
             nextAgent);
    }
    
    /**
     * Creates a copy of this agent with the given name.
     * 
     * @param name the name to set
     * @return a new Agent instance with the name set
     */
    public Agent withName(String name) {
        return new Agent(name, this.description, this.type, this.instructions, 
                        this.mcpServers, this.mcpConfig, this.tools, this.outputSchema, this.graph, this.nextAgent);
    }
    
    /**
     * Creates a copy of this agent with the parsed MCP configuration.
     * 
     * @param mcpConfig the parsed MCP configuration
     * @return a new Agent instance with the MCP config set
     */
    public Agent withMcpConfig(McpConfig mcpConfig) {
        return new Agent(this.name, this.description, this.type, this.instructions,
                        this.mcpServers, mcpConfig, this.tools, this.outputSchema, this.graph, this.nextAgent);
    }
    
    /**
     * Checks if this agent has MCP servers configured.
     * 
     * @return true if mcpServers string is present and not empty
     */
    public boolean hasMcpServers() {
        return mcpServers != null && !mcpServers.trim().isEmpty();
    }
    
    /**
     * Checks if this agent has a parsed MCP configuration.
     * 
     * @return true if mcpConfig is present and has servers
     */
    public boolean hasMcpConfig() {
        return mcpConfig != null && mcpConfig.hasServers();
    }
    
    /**
     * Checks if this agent has tools configured.
     * 
     * @return true if tools list is not empty
     */
    public boolean hasTools() {
        return !tools.isEmpty();
    }
    
    /**
     * Checks if this agent has an output schema defined.
     * 
     * @return true if outputSchema is present and not empty
     */
    public boolean hasOutputSchema() {
        return outputSchema != null && !outputSchema.trim().isEmpty();
    }
    
    /**
     * Checks if this agent has a next agent configured for handoff.
     * 
     * @return true if nextAgent is present and not empty
     */
    public boolean hasNextAgent() {
        return nextAgent != null && !nextAgent.trim().isEmpty();
    }

    /**
     * Checks if this agent has graph edges configured.
     *
     * @return true if graph contains at least one edge
     */
    public boolean hasGraph() {
        return graph.hasEdges();
    }
}
