package com.davidparry.agent.sdk.mcp;

import com.davidparry.agent.protocol.mcp.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loader for MCP configuration files.
 * Reads JSON configuration files and parses them into McpConfig objects.
 * Supports environment variable substitution using {VAR_NAME} syntax.
 */
public class McpConfigLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(McpConfigLoader.class);
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    private final ObjectMapper objectMapper;
    
    /**
     * Creates a new McpConfigLoader with the given ObjectMapper.
     */
    public McpConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Creates a new McpConfigLoader with a default ObjectMapper.
     */
    public McpConfigLoader() {
        this(new ObjectMapper());
    }
    
    /**
     * Loads MCP configuration from a file path.
     * 
     * @param filePath the path to the configuration file
     * @return the parsed and validated McpConfig
     * @throws IOException if the file cannot be read or parsed
     */
    public McpConfig loadFromPath(Path filePath) throws IOException {
        logger.info("Loading MCP configuration from: {}", filePath);
        
        if (!Files.exists(filePath)) {
            throw new IOException("Configuration file not found: " + filePath);
        }
        
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return loadFromInputStream(inputStream, filePath.toString());
        }
    }
    
    /**
     * Loads MCP configuration from a file path string.
     * 
     * @param filePath the path to the configuration file as a string
     * @return the parsed and validated McpConfig
     * @throws IOException if the file cannot be read or parsed
     */
    public McpConfig loadFromPath(String filePath) throws IOException {
        return loadFromPath(Path.of(filePath));
    }
    
    /**
     * Loads MCP configuration from a JSON string.
     * 
     * @param jsonContent the JSON content as a string
     * @return the parsed and validated McpConfig
     * @throws IOException if the JSON cannot be parsed
     */
    public McpConfig loadFromString(String jsonContent) throws IOException {
        logger.debug("Loading MCP configuration from string content");
        
        McpConfig rawConfig = objectMapper.readValue(jsonContent, McpConfig.class);
        return processConfig(rawConfig);
    }
    
    /**
     * Loads MCP configuration from an InputStream.
     * 
     * @param inputStream the input stream containing JSON configuration
     * @param description a description of the source for error reporting
     * @return the parsed and validated McpConfig
     * @throws IOException if the stream cannot be read or parsed
     */
    public McpConfig loadFromInputStream(InputStream inputStream, String description) throws IOException {
        logger.debug("Loading MCP configuration from: {}", description);
        
        try {
            McpConfig rawConfig = objectMapper.readValue(inputStream, McpConfig.class);
            return processConfig(rawConfig);
        } catch (IOException e) {
            throw new IOException("Failed to parse MCP configuration from " + description, e);
        }
    }
    
    /**
     * Loads MCP configuration from classpath resource.
     * 
     * @param resourcePath the classpath resource path (e.g., "mcp.json")
     * @return the parsed and validated McpConfig
     * @throws IOException if the resource cannot be found or parsed
     */
    public McpConfig loadFromClasspath(String resourcePath) throws IOException {
        logger.info("Loading MCP configuration from classpath: {}", resourcePath);
        
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Classpath resource not found: " + resourcePath);
            }
            return loadFromInputStream(inputStream, "classpath:" + resourcePath);
        }
    }
    
    /**
     * Processes the raw configuration by:
     * 1. Setting server names from the map keys
     * 2. Performing environment variable substitution
     * 3. Validating the configuration
     */
    private McpConfig processConfig(McpConfig rawConfig) throws IOException {
        if (rawConfig == null || rawConfig.mcpServers() == null) {
            throw new IOException("Invalid configuration: mcpServers is required");
        }
        
        // Process each server: set name and substitute environment variables
        Map<String, McpServer> processedServers = new HashMap<>();
        
        for (Map.Entry<String, McpServer> entry : rawConfig.mcpServers().entrySet()) {
            String serverName = entry.getKey();
            McpServer server = entry.getValue();
            
            // Set the name on the server
            McpServer namedServer = setServerName(server, serverName);
            
            // Perform environment variable substitution
            McpServer substitutedServer = substituteEnvVars(namedServer);
            
            // Validate the server
            validateServer(serverName, substitutedServer);
            
            processedServers.put(serverName, substitutedServer);
        }
        
        logger.info("Loaded {} MCP server configurations", processedServers.size());
        return new McpConfig(processedServers);
    }
    
    /**
     * Sets the name on a server instance.
     */
    private McpServer setServerName(McpServer server, String name) {
        if (server instanceof StdioServer stdioServer) {
            return stdioServer.withName(name);
        } else if (server instanceof HttpServer httpServer) {
            return httpServer.withName(name);
        } else if (server instanceof SseServer sseServer) {
            return sseServer.withName(name);
        }
        return server;
    }
    
    /**
     * Performs environment variable substitution on a server configuration.
     */
    private McpServer substituteEnvVars(McpServer server) {
        Map<String, String> systemEnv = System.getenv();
        
        if (server instanceof StdioServer stdioServer) {
            return substituteStdioServer(stdioServer, systemEnv);
        } else if (server instanceof HttpServer httpServer) {
            return substituteHttpServer(httpServer, systemEnv);
        } else if (server instanceof SseServer sseServer) {
            return substituteSseServer(sseServer, systemEnv);
        }
        return server;
    }
    
    private StdioServer substituteStdioServer(StdioServer server, Map<String, String> systemEnv) {
        // Process server's env map first
        Map<String, String> processedEnv = substituteMap(server.env(), systemEnv);
        
        // Build combined env for field substitution
        Map<String, String> combinedEnv = new HashMap<>(systemEnv);
        combinedEnv.putAll(processedEnv);
        
        // Substitute in command and args
        String substitutedCommand = substituteString(server.command(), combinedEnv);
        java.util.List<String> substitutedArgs = server.args().stream()
            .map(arg -> substituteString(arg, combinedEnv))
            .toList();
        
        return new StdioServer(server.name(), server.type(), substitutedCommand, substitutedArgs, processedEnv);
    }
    
    private HttpServer substituteHttpServer(HttpServer server, Map<String, String> systemEnv) {
        Map<String, String> processedEnv = substituteMap(server.env(), systemEnv);
        
        Map<String, String> combinedEnv = new HashMap<>(systemEnv);
        combinedEnv.putAll(processedEnv);
        
        String substitutedUrl = substituteString(server.url(), combinedEnv);
        Map<String, String> substitutedHeaders = substituteMap(server.headers(), combinedEnv);
        
        return new HttpServer(server.name(), server.type(), substitutedUrl, substitutedHeaders, processedEnv);
    }
    
    private SseServer substituteSseServer(SseServer server, Map<String, String> systemEnv) {
        Map<String, String> processedEnv = substituteMap(server.env(), systemEnv);
        
        Map<String, String> combinedEnv = new HashMap<>(systemEnv);
        combinedEnv.putAll(processedEnv);
        
        String substitutedUrl = substituteString(server.url(), combinedEnv);
        Map<String, String> substitutedHeaders = substituteMap(server.headers(), combinedEnv);
        
        return new SseServer(server.name(), server.type(), substitutedUrl, substitutedHeaders, processedEnv);
    }
    
    /**
     * Substitutes environment variables in a map's values.
     * For each entry, first applies {VAR_NAME} substitution to the value, then checks
     * for a system environment variable named MCP_{KEY} which takes precedence if present.
     * This allows secrets to be stored as MCP_JIRA_SITE_URL, MCP_JIRA_API_TOKEN, etc.
     * without embedding them in the agent configuration file.
     */
    private Map<String, String> substituteMap(Map<String, String> map, Map<String, String> env) {
        if (map == null || map.isEmpty()) {
            return map;
        }

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String resolved = substituteString(entry.getValue(), env);
            String mcpEnvValue = env.get("MCP_" + key);
            if (mcpEnvValue != null) {
                logger.debug("Resolved env key '{}' from system environment variable 'MCP_{}'", key, key);
                resolved = mcpEnvValue;
            }
            result.put(key, resolved);
        }
        return result;
    }
    
    /**
     * Substitutes environment variables in a string.
     * Replaces {VAR_NAME} with the value from the environment.
     */
    private String substituteString(String input, Map<String, String> env) {
        if (input == null || env == null || env.isEmpty()) {
            return input;
        }
        
        Matcher matcher = ENV_VAR_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = env.get(varName);
            
            if (replacement != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            } else {
                // Keep the original placeholder if no replacement found
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Validates a server configuration.
     */
    private void validateServer(String serverName, McpServer server) throws IOException {
        if (server == null) {
            throw new IOException("Server '" + serverName + "' is null");
        }
        
        if (server instanceof StdioServer stdioServer) {
            if (stdioServer.command() == null || stdioServer.command().trim().isEmpty()) {
                throw new IOException("STDIO server '" + serverName + "' has no command specified");
            }
        } else if (server instanceof HttpServer httpServer) {
            if (httpServer.url() == null || httpServer.url().trim().isEmpty()) {
                throw new IOException("HTTP server '" + serverName + "' has no URL specified");
            }
        } else if (server instanceof SseServer sseServer) {
            if (sseServer.url() == null || sseServer.url().trim().isEmpty()) {
                throw new IOException("SSE server '" + serverName + "' has no URL specified");
            }
        }
    }
}
