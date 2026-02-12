package com.davidparry.agent.client.agent;

import com.davidparry.agent.protocol.mcp.McpConfig;
import com.davidparry.agent.client.mcp.McpConfigLoader;
import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.Agents;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loader for agent configuration files.
 * Reads YAML configuration files and parses them into Agents objects.
 * Also parses the embedded mcpServers JSON strings into McpConfig objects.
 */
public class AgentConfigLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentConfigLoader.class);
    
    private final ObjectMapper yamlMapper;
    private final McpConfigLoader mcpConfigLoader;
    
    /**
     * Creates a new AgentConfigLoader with the given ObjectMapper for JSON parsing.
     * 
     * @param jsonObjectMapper the ObjectMapper configured for JSON (used for MCP config parsing)
     */
    public AgentConfigLoader(ObjectMapper jsonObjectMapper) {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.mcpConfigLoader = new McpConfigLoader(jsonObjectMapper);
    }
    
    /**
     * Creates a new AgentConfigLoader with default ObjectMappers.
     */
    public AgentConfigLoader() {
        this(new ObjectMapper());
    }
    
    /**
     * Loads agent configuration from a file path.
     * 
     * @param filePath the path to the configuration file
     * @return the parsed and processed Agents configuration
     * @throws IOException if the file cannot be read or parsed
     */
    public Agents loadFromPath(Path filePath) throws IOException {
        logger.info("Loading agent configuration from: {}", filePath);
        
        if (!Files.exists(filePath)) {
            throw new IOException("Configuration file not found: " + filePath);
        }
        
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return loadFromInputStream(inputStream, filePath.toString());
        }
    }
    

    /**
     * Loads agent configuration from a YAML string.
     * 
     * @param yamlContent the YAML content as a string
     * @return the parsed and processed Agents configuration
     * @throws IOException if the YAML cannot be parsed
     */
    public Agents loadFromString(String yamlContent) throws IOException {
        logger.debug("Loading agent configuration from string content");
        
        Agents rawAgents = yamlMapper.readValue(yamlContent, Agents.class);
        return processAgents(rawAgents);
    }
    
    /**
     * Loads agent configuration from an InputStream.
     * 
     * @param inputStream the input stream containing YAML configuration
     * @param description a description of the source for error reporting
     * @return the parsed and processed Agents configuration
     * @throws IOException if the stream cannot be read or parsed
     */
    public Agents loadFromInputStream(InputStream inputStream, String description) throws IOException {
        logger.debug("Loading agent configuration from: {}", description);
        
        try {
            Agents rawAgents = yamlMapper.readValue(inputStream, Agents.class);
            return processAgents(rawAgents);
        } catch (IOException e) {
            throw new IOException("Failed to parse agent configuration from " + description, e);
        }
    }
    
    /**
     * Loads agent configuration from classpath resource.
     * 
     * @param resourcePath the classpath resource path (e.g., "agent.yml")
     * @return the parsed and processed Agents configuration
     * @throws IOException if the resource cannot be found or parsed
     */
    public Agents loadFromClasspath(String resourcePath) throws IOException {
        logger.info("Loading agent configuration from classpath: {}", resourcePath);
        
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Classpath resource not found: " + resourcePath);
            }
            return loadFromInputStream(inputStream, "classpath:" + resourcePath);
        }
    }
    
    /**
     * Processes the raw agents configuration by:
     * 1. Setting agent names from the map keys
     * 2. Parsing embedded mcpServers JSON strings into McpConfig objects
     * 3. Validating the configuration
     */
    private Agents processAgents(Agents rawAgents) throws IOException {
        if (rawAgents == null) {
            throw new IOException("Invalid configuration: agents configuration is null");
        }
        
        if (rawAgents.agents() == null || rawAgents.agents().isEmpty()) {
            logger.warn("No agents defined in configuration");
            return rawAgents;
        }
        
        // Process each agent: set name and parse MCP config
        Map<String, Agent> processedAgents = new HashMap<>();
        
        for (Map.Entry<String, Agent> entry : rawAgents.agents().entrySet()) {
            String agentName = entry.getKey();
            Agent agent = entry.getValue();
            
            // Set the name on the agent
            Agent namedAgent = agent.withName(agentName);
            
            // Parse the mcpServers JSON string into McpConfig
            Agent processedAgent = parseMcpConfig(namedAgent);
            
            // Validate the agent
            validateAgent(agentName, processedAgent);
            
            processedAgents.put(agentName, processedAgent);
        }
        
        logger.info("Loaded {} agent configurations", processedAgents.size());
        return new Agents(rawAgents.version(), processedAgents);
    }
    
    /**
     * Parses the mcpServers JSON string in an agent into a McpConfig object.
     */
    private Agent parseMcpConfig(Agent agent) throws IOException {
        if (!agent.hasMcpServers()) {
            logger.debug("Agent '{}' has no MCP servers configured", agent.name());
            return agent;
        }
        
        try {
            String mcpServersJson = agent.mcpServers().trim();
            McpConfig mcpConfig = mcpConfigLoader.loadFromString(mcpServersJson);
            
            logger.debug("Parsed {} MCP servers for agent '{}'", 
                        mcpConfig.serverCount(), agent.name());
            
            return agent.withMcpConfig(mcpConfig);
        } catch (IOException e) {
            throw new IOException("Failed to parse mcpServers for agent '" + agent.name() + "'", e);
        }
    }
    
    /**
     * Validates an agent configuration.
     */
    private void validateAgent(String agentName, Agent agent) throws IOException {
        if (agent == null) {
            throw new IOException("Agent '" + agentName + "' is null");
        }
        
        if (agent.description() == null || agent.description().trim().isEmpty()) {
            logger.warn("Agent '{}' has no description", agentName);
        }
        
        if (agent.type() == null ) {
            logger.warn("Agent '{}' has no type specified", agentName);
        }
        
        if (agent.instructions() == null || agent.instructions().trim().isEmpty()) {
            throw new IOException("Agent '" + agentName + "' has no instructions specified");
        }
        
        // Validate that tools reference valid MCP servers if MCP config is present
        if (agent.hasTools() && agent.hasMcpConfig()) {
            validateToolReferences(agentName, agent);
        }
    }
    
    /**
     * Validates that tool references match configured MCP servers.
     */
    private void validateToolReferences(String agentName, Agent agent) {
        McpConfig mcpConfig = agent.mcpConfig();
        
        for (String tool : agent.tools()) {
            // Tools are in format "server-name.tool-name"
            int dotIndex = tool.indexOf('.');
            if (dotIndex > 0) {
                String serverName = tool.substring(0, dotIndex);
                if (!mcpConfig.mcpServers().containsKey(serverName)) {
                    logger.warn("Agent '{}' references tool '{}' but MCP server '{}' is not configured",
                               agentName, tool, serverName);
                }
            } else {
                logger.warn("Agent '{}' has tool '{}' without server prefix (expected format: server-name.tool-name)",
                           agentName, tool);
            }
        }
    }
}
