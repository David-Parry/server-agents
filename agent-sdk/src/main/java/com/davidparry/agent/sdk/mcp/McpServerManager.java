package com.davidparry.agent.sdk.mcp;

import com.davidparry.agent.sdk.config.AgentSdkProperties;
import com.davidparry.agent.protocol.dto.ToolDefinition;
import com.davidparry.agent.protocol.mcp.McpConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Factory and lifecycle manager for session-specific MCP server managers.
 * 
 * This component is responsible for:
 * - Creating session-specific SessionMcpManager instances
 * - Managing the lifecycle of all session managers
 * - Providing a 1-to-1 relationship between WebSocket sessions and MCP server instances
 *
 * Each session gets its own isolated set of MCP servers with session-specific workspace roots.
 * This ensures that tool executions are isolated per session.
 *
 * MCP server configuration is loaded from the Agent file's mcpServers field when a session
 * is activated. Each agent can define its own MCP servers, which are started when the
 * session is created and stopped when the session ends.
 */
@Component
public class McpServerManager {

    private static final Logger logger = LoggerFactory.getLogger(McpServerManager.class);

    private final AgentSdkProperties properties;
    private final ObjectMapper objectMapper;
    private final McpConfigLoader configLoader;
    private final Duration requestTimeout;
    private final String sandboxBasePath;

    // Stores the loaded MCP configuration (server definitions)
    private volatile McpConfig mcpConfig;

    // Maps session IDs to their SessionMcpManager instances
    private final Map<String, SessionMcpManager> sessionManagers = new ConcurrentHashMap<>();

    public McpServerManager(AgentSdkProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.configLoader = new McpConfigLoader(objectMapper);
        this.requestTimeout = Duration.ofMinutes(properties.mcp().requestTimeoutMinutes());
        this.sandboxBasePath = resolveSandboxBasePath(properties.mcp().sandboxBasePath());
        
        logger.info("MCP request timeout configured to {} minutes", properties.mcp().requestTimeoutMinutes());
        logger.info("MCP sandbox base path: {}", this.sandboxBasePath);
    }

    /**
     * Resolves the sandbox base path from configuration or defaults to user.dir.
     *
     * @param configuredPath the configured sandbox path (may be null or blank)
     * @return the resolved sandbox base path
     */
    private String resolveSandboxBasePath(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return configuredPath;
        }
        return System.getProperty("user.dir");
    }

    @PostConstruct
    public void init() {
        logger.info("Initializing MCP Server Manager (session-based architecture)");

        // Validate and create sandbox directory
        validateAndCreateSandboxDirectory();

        // MCP server configuration is loaded from Agent files when sessions are activated.
        // Each agent defines its own mcpServers in the agent.yml configuration.
        logger.info("MCP Server Manager initialized. MCP servers will be loaded from Agent configurations on session activation.");
    }

    /**
     * Validates the sandbox base path exists and is writable.
     * Creates the directory if it doesn't exist.
     * 
     * @throws IllegalStateException if the directory cannot be created or is not writable
     */
    private void validateAndCreateSandboxDirectory() {
        Path sandboxPath = Path.of(sandboxBasePath);
        
        if (!Files.exists(sandboxPath)) {
            try {
                Files.createDirectories(sandboxPath);
                logger.info("Created sandbox base directory: {}", sandboxBasePath);
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Cannot create sandbox base directory: " + sandboxBasePath, e);
            }
        }
        
        if (!Files.isDirectory(sandboxPath)) {
            throw new IllegalStateException(
                "Sandbox base path is not a directory: " + sandboxBasePath);
        }
        
        if (!Files.isWritable(sandboxPath)) {
            throw new IllegalStateException(
                "Sandbox base directory is not writable: " + sandboxBasePath);
        }
        
        logger.info("Sandbox base path validated: {}", sandboxBasePath);
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down MCP Server Manager and all session managers...");

        for (Map.Entry<String, SessionMcpManager> entry : sessionManagers.entrySet()) {
            try {
                logger.info("Shutting down session manager: {}", entry.getKey());
                entry.getValue().shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down session manager: {}", entry.getKey(), e);
            }
        }

        sessionManagers.clear();
        logger.info("MCP Server Manager shutdown complete");
    }

    // ==================== Session Management ====================

    /**
     * Creates and initializes a new SessionMcpManager for the given session ID.
     * This starts all configured MCP servers for the session.
     *
     * @param sessionId the unique session identifier
     * @return the initialized SessionMcpManager
     * @throws IllegalStateException if a manager already exists for this session
     */
    public SessionMcpManager createSessionManager(String sessionId) {
        if (sessionManagers.containsKey(sessionId)) {
            throw new IllegalStateException("Session manager already exists for session: " + sessionId);
        }

        logger.info("Creating session manager for session: {}", sessionId);

        SessionMcpManager sessionManager = new SessionMcpManager(
            sessionId,
            objectMapper,
            requestTimeout,
            mcpConfig,
            sandboxBasePath
        );

        sessionManagers.put(sessionId, sessionManager);

        // Initialize the session manager (starts MCP servers)
        sessionManager.initialize();

        logger.info("Session manager created and initialized for session: {} with {} servers and {} tools",
            sessionId, sessionManager.getServerCount(), sessionManager.getToolCount());

        return sessionManager;
    }

    /**
     * Gets the SessionMcpManager for the given session ID.
     *
     * @param sessionId the session identifier
     * @return Optional containing the session manager if it exists
     */
    public Optional<SessionMcpManager> getSessionManager(String sessionId) {
        return Optional.ofNullable(sessionManagers.get(sessionId));
    }

    /**
     * Shuts down and removes the SessionMcpManager for the given session ID.
     *
     * @param sessionId the session identifier
     */
    public void destroySessionManager(String sessionId) {
        SessionMcpManager manager = sessionManagers.remove(sessionId);
        if (manager != null) {
            logger.info("Destroying session manager for session: {}", sessionId);
            manager.shutdown();
        } else {
            logger.debug("No session manager found for session: {}", sessionId);
        }
    }

    /**
     * Checks if a session manager exists for the given session ID.
     *
     * @param sessionId the session identifier
     * @return true if a manager exists
     */
    public boolean hasSessionManager(String sessionId) {
        return sessionManagers.containsKey(sessionId);
    }

    /**
     * Gets all active session IDs.
     *
     * @return set of active session IDs
     */
    public Set<String> getActiveSessions() {
        return new HashSet<>(sessionManagers.keySet());
    }

    /**
     * Gets the count of active sessions.
     *
     * @return number of active sessions
     */
    public int getActiveSessionCount() {
        return sessionManagers.size();
    }

    // ==================== Configuration Loading ====================

    /**
     * Load MCP servers from a JSON configuration file.
     */
    public void loadFromConfigFile(String configPath) throws IOException {
        loadFromConfigFile(Path.of(configPath));
    }

    /**
     * Load MCP servers from a JSON configuration file.
     */
    public void loadFromConfigFile(Path configPath) throws IOException {
        logger.info("Loading MCP configuration from file: {}", configPath);
        this.mcpConfig = configLoader.loadFromPath(configPath);
        logger.info("Loaded MCP configuration with {} servers", mcpConfig.serverCount());
    }

    /**
     * Load MCP servers from a JSON string.
     */
    public void loadFromJsonString(String jsonContent) throws IOException {
        logger.info("Loading MCP configuration from JSON string");
        this.mcpConfig = configLoader.loadFromString(jsonContent);
        logger.info("Loaded MCP configuration with {} servers", mcpConfig.serverCount());
    }

    /**
     * Load MCP servers from a classpath resource.
     */
    public void loadFromClasspath(String resourcePath) throws IOException {
        logger.info("Loading MCP configuration from classpath: {}", resourcePath);
        this.mcpConfig = configLoader.loadFromClasspath(resourcePath);
        logger.info("Loaded MCP configuration with {} servers", mcpConfig.serverCount());
    }

    /**
     * Load MCP servers from a parsed McpConfig.
     */
    public void loadFromConfig(McpConfig config) {
        if (config == null) {
            logger.warn("Null configuration provided");
            return;
        }
        this.mcpConfig = config;
        logger.info("Loaded MCP configuration with {} servers", config.serverCount());
    }

    // ==================== Convenience Methods (delegate to session manager) ====================

    /**
     * Execute a tool call for a specific session.
     * 
     * @param sessionId the session identifier
     * @param toolName the tool name
     * @param arguments the tool arguments
     * @return the tool execution result
     * @throws Exception if execution fails or session not found
     */
    public String executeTool(String sessionId, String toolName, Map<String, Object> arguments) throws Exception {
        SessionMcpManager manager = sessionManagers.get(sessionId);
        if (manager == null) {
            throw new IllegalStateException("No session manager found for session: " + sessionId);
        }
        return manager.executeTool(toolName, arguments);
    }

    /**
     * Get available tools for a specific session.
     *
     * @param sessionId the session identifier
     * @return list of available tools
     */
    public List<ToolDefinition> getAvailableTools(String sessionId) {
        SessionMcpManager manager = sessionManagers.get(sessionId);
        if (manager == null) {
            logger.warn("No session manager found for session: {}. Returning empty tool list.", sessionId);
            return List.of();
        }
        return manager.getAvailableTools();
    }

    /**
     * Get available tools as McpSchema.Tool objects for a specific session.
     *
     * @param sessionId the session identifier
     * @return list of available tools
     */
    public List<McpSchema.Tool> getAvailableToolsAsMcpTools(String sessionId) {
        SessionMcpManager manager = sessionManagers.get(sessionId);
        if (manager == null) {
            return List.of();
        }
        return manager.getAvailableToolsAsMcpTools();
    }

    // ==================== Legacy Methods (for backward compatibility) ====================
    // These methods operate without session context - useful for REST API endpoints

    /**
     * Get the loaded MCP configuration.
     *
     * @return the MCP configuration, or null if not loaded
     */
    public McpConfig getMcpConfig() {
        return mcpConfig;
    }

    /**
     * Check if MCP configuration is loaded.
     *
     * @return true if configuration is loaded
     */
    public boolean hasConfiguration() {
        return mcpConfig != null && mcpConfig.hasServers();
    }

    /**
     * Get the number of configured servers (from configuration, not running).
     *
     * @return number of configured servers
     */
    public int getConfiguredServerCount() {
        return mcpConfig != null ? mcpConfig.serverCount() : 0;
    }

    /**
     * Get configured server names.
     *
     * @return set of configured server names
     */
    public Set<String> getConfiguredServers() {
        if (mcpConfig == null || mcpConfig.mcpServers() == null) {
            return Set.of();
        }
        return mcpConfig.mcpServers().keySet();
    }

    /**
     * Get statistics across all sessions.
     *
     * @return aggregated statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("configuredServers", getConfiguredServerCount());
        stats.put("activeSessions", sessionManagers.size());
        stats.put("sandboxBasePath", sandboxBasePath);

        Map<String, Map<String, Object>> sessionStats = new HashMap<>();
        for (Map.Entry<String, SessionMcpManager> entry : sessionManagers.entrySet()) {
            sessionStats.put(entry.getKey(), entry.getValue().getStatistics());
        }
        stats.put("sessions", sessionStats);

        return stats;
    }

    /**
     * Gets the configured sandbox base path.
     * 
     * @return the sandbox base path where session directories are created
     */
    public String getSandboxBasePath() {
        return sandboxBasePath;
    }

}
