package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Root configuration record for agent configurations.
 * Represents the top-level structure of agent.yml file.
 * 
 * Example YAML structure:
 * <pre>
 * version: "1.0"
 * 
 * agents:
 *   failure_diagnostician:
 *     description: "Reads the conversation..."
 *     type: "DIAGNOSTICIAN"
 *     instructions: |
 *       ### Phase 1: Issue Retrieval...
 *     mcpServers: |
 *       {
 *         "mcpServers": {
 *           "internal-server": {
 *             "command": "java",
 *             "args": ["-jar", "/app/mcp-internal-1.0.3.jar"]
 *           }
 *         }
 *       }
 *     tools: ["internal-server.jira_get_issue"]
 *     output_schema: |
 *       { "properties": { ... } }
 * </pre>
 * 
 * @param version The version of the agent configuration format
 * @param agents Map of agent names to their configurations
 */
public record Agents(
    @JsonProperty("version")
    String version,
    
    @JsonProperty("agents")
    Map<String, Agent> agents
) {
    
    /**
     * Default constructor with empty agents map.
     */
    public Agents() {
        this(null, Map.of());
    }
    
    /**
     * Compact constructor with defaults.
     */
    public Agents {
        if (agents == null) {
            agents = Map.of();
        }
    }
    
    /**
     * Checks if this configuration has any agents defined.
     * 
     * @return true if agents map is not empty
     */
    public boolean hasAgents() {
        return !agents.isEmpty();
    }
    
    /**
     * Gets the number of configured agents.
     * 
     * @return the count of agents
     */
    public int agentCount() {
        return agents.size();
    }
    
    /**
     * Gets an agent by name.
     * 
     * @param name the agent name
     * @return an Optional containing the agent if found, empty otherwise
     */
    public Optional<Agent> getAgent(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(agents.get(name));
    }
    
    /**
     * Gets all agent names.
     * 
     * @return an unmodifiable set of agent names
     */
    public Set<String> getAgentNames() {
        return Collections.unmodifiableSet(agents.keySet());
    }
    
    /**
     * Checks if an agent with the given name exists.
     * 
     * @param name the agent name to check
     * @return true if an agent with that name exists
     */
    public boolean hasAgent(String name) {
        return name != null && agents.containsKey(name);
    }
}
