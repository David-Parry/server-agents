package com.davidparry.agent.sdk.mcp;

import com.davidparry.agent.protocol.dto.ToolDefinition;
import com.davidparry.agent.protocol.mcp.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Session-specific MCP server manager.
 * Each WebSocket session gets its own instance of this manager with isolated MCP servers.
 * This ensures 1-to-1 relationship between sessions and their MCP server instances.
 * 
 * The session ID is used to create session-specific workspace roots for MCP servers,
 * allowing each session to have isolated file system access.
 * 
 * Session directories are created under the configured sandbox base path and are
 * automatically cleaned up when the session ends.
 */
public class SessionMcpManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionMcpManager.class);

    private final String sessionId;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final McpConfig mcpConfig;
    private final String sandboxBasePath;
    private final Path sessionWorkspacePath;

    private final Map<String, McpSyncClient> runningClients = new ConcurrentHashMap<>();
    private final Map<String, McpSchema.Tool> toolRegistry = new ConcurrentHashMap<>();
    private final Map<String, String> toolServerMapping = new ConcurrentHashMap<>();
    private final Map<String, McpServer> serverConfigs = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;
    private volatile boolean shutdown = false;

    /**
     * Creates a new session-specific MCP manager.
     *
     * @param sessionId the unique session identifier
     * @param objectMapper the Jackson ObjectMapper for JSON processing
     * @param requestTimeout the timeout for MCP requests
     * @param mcpConfig the MCP configuration containing server definitions
     * @param sandboxBasePath the base path for session sandbox directories
     */
    public SessionMcpManager(
            String sessionId, 
            ObjectMapper objectMapper, 
            Duration requestTimeout, 
            McpConfig mcpConfig,
            String sandboxBasePath) {
        this.sessionId = sessionId;
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
        this.mcpConfig = mcpConfig;
        this.sandboxBasePath = sandboxBasePath;
        this.sessionWorkspacePath = Path.of(sandboxBasePath, sessionId);
        
        logger.info("Created SessionMcpManager for session: {} with workspace: {}", 
            sessionId, sessionWorkspacePath);
    }

    /**
     * Initializes and starts all MCP servers defined in the configuration.
     * Creates the session workspace directory if it doesn't exist.
     * This should be called when a session is created.
     */
    public void initialize() {
        if (initialized) {
            logger.warn("SessionMcpManager for session {} is already initialized", sessionId);
            return;
        }

        if (shutdown) {
            throw new IllegalStateException("SessionMcpManager for session " + sessionId + " has been shutdown");
        }

        logger.info("Initializing MCP servers for session: {}", sessionId);

        // Create session workspace directory
        createSessionWorkspaceDirectory();

        if (mcpConfig == null || !mcpConfig.hasServers()) {
            logger.warn("No MCP servers configured for session: {}", sessionId);
            initialized = true;
            return;
        }

        for (Map.Entry<String, McpServer> entry : mcpConfig.mcpServers().entrySet()) {
            String serverName = entry.getKey();
            McpServer server = entry.getValue();

            try {
                startServer(serverName, server);
            } catch (Exception e) {
                logger.error("Failed to start MCP server {} for session {}: {}", 
                    serverName, sessionId, e.getMessage(), e);
            }
        }

        initialized = true;
        logger.info("Session {} initialized with {} servers and {} tools",
            sessionId, runningClients.size(), toolRegistry.size());
    }

    /**
     * Creates the session-specific workspace directory.
     * 
     * @throws RuntimeException if the directory cannot be created
     */
    private void createSessionWorkspaceDirectory() {
        if (!Files.exists(sessionWorkspacePath)) {
            try {
                Files.createDirectories(sessionWorkspacePath);
                logger.info("Created session workspace directory: {}", sessionWorkspacePath);
            } catch (IOException e) {
                logger.error("Failed to create session workspace directory: {}", sessionWorkspacePath, e);
                throw new RuntimeException("Cannot create session workspace directory: " + sessionWorkspacePath, e);
            }
        } else {
            logger.debug("Session workspace directory already exists: {}", sessionWorkspacePath);
        }
    }

    /**
     * Shuts down all MCP servers for this session and cleans up the session directory.
     * This should be called when a session ends.
     */
    public void shutdown() {
        if (shutdown) {
            logger.debug("SessionMcpManager for session {} is already shutdown", sessionId);
            return;
        }

        logger.info("Shutting down MCP servers for session: {}", sessionId);
        shutdown = true;

        // Stop all MCP clients
        for (Map.Entry<String, McpSyncClient> entry : runningClients.entrySet()) {
            try {
                logger.debug("Stopping MCP server {} for session {}", entry.getKey(), sessionId);
                entry.getValue().close();
            } catch (Exception e) {
                logger.warn("Error stopping server {} for session {}: {}", 
                    entry.getKey(), sessionId, e.getMessage());
            }
        }

        runningClients.clear();
        toolRegistry.clear();
        toolServerMapping.clear();
        serverConfigs.clear();

        // Clean up session workspace directory
        cleanupSessionWorkspaceDirectory();

        logger.info("Session {} MCP servers shutdown complete", sessionId);
    }

    /**
     * Recursively deletes the session workspace directory and all its contents.
     */
    private void cleanupSessionWorkspaceDirectory() {
        if (!Files.exists(sessionWorkspacePath)) {
            logger.debug("Session workspace directory does not exist, nothing to clean up: {}", 
                sessionWorkspacePath);
            return;
        }

        try (Stream<Path> pathStream = Files.walk(sessionWorkspacePath)) {
            // Sort in reverse order so files are deleted before their parent directories
            pathStream
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        logger.trace("Deleted: {}", path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete path during cleanup: {}", path, e);
                    }
                });
            
            logger.info("Cleaned up session workspace directory: {}", sessionWorkspacePath);
        } catch (IOException e) {
            logger.error("Failed to cleanup session workspace directory: {}", sessionWorkspacePath, e);
        }
    }

    /**
     * Start an MCP server based on its type.
     */
    private void startServer(String serverName, McpServer server) {
        logger.info("Starting MCP server {} for session {}: type={}", serverName, sessionId, server.type());

        if (runningClients.containsKey(serverName)) {
            logger.warn("Server {} already running for session {}", serverName, sessionId);
            return;
        }

        try {
            McpSyncClient client;

            if (server instanceof StdioServer stdioServer) {
                client = startStdioServer(serverName, stdioServer);
            } else if (server instanceof HttpServer httpServer) {
                client = startHttpServer(serverName, httpServer);
            } else if (server instanceof SseServer sseServer) {
                client = startSseServer(serverName, sseServer);
            } else {
                throw new IllegalArgumentException("Unknown server type: " + server.getClass().getName());
            }

            runningClients.put(serverName, client);
            serverConfigs.put(serverName, server);

            discoverTools(serverName, client);

            long toolCount = toolServerMapping.values().stream().filter(s -> s.equals(serverName)).count();
            logger.info("MCP server {} started for session {} with {} tools", serverName, sessionId, toolCount);

        } catch (Exception e) {
            logger.error("Failed to start MCP server {} for session {}", serverName, sessionId, e);
            throw new RuntimeException("Failed to start MCP server: " + serverName, e);
        }
    }

    /**
     * Start a STDIO-based MCP server with session-specific workspace root.
     */
    private McpSyncClient startStdioServer(String serverName, StdioServer config) {
        logger.info("Starting STDIO server {} for session {} with command: {}", 
            serverName, sessionId, config.command());

        ServerParameters.Builder paramsBuilder = ServerParameters.builder(config.command());

        if (config.args() != null && !config.args().isEmpty()) {
            paramsBuilder.args(config.args().toArray(new String[0]));
        }

        if (config.env() != null && !config.env().isEmpty()) {
            paramsBuilder.env(config.env());
        }

        ServerParameters serverParams = paramsBuilder.build();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        StdioClientTransport transport = new StdioClientTransport(serverParams, jsonMapper);

        // Configure client capabilities including roots support
        McpSchema.ClientCapabilities capabilities = McpSchema.ClientCapabilities.builder()
            .roots(true)
            .build();

        // Use the pre-computed session workspace path from the configurable sandbox
        McpSchema.Root workspaceRoot = new McpSchema.Root(
            "file://" + sessionWorkspacePath.toAbsolutePath().toString(), 
            "workspace"
        );

        logger.debug("Session {} workspace root: {}", sessionId, sessionWorkspacePath);

        McpSyncClient syncClient = McpClient.sync(transport)
            .requestTimeout(requestTimeout)
            .clientInfo(new McpSchema.Implementation("agent-sdk", "1.0.0"))
            .capabilities(capabilities)
            .roots(workspaceRoot)
            .build();

        McpSchema.InitializeResult initResult = syncClient.initialize();
        logger.info("STDIO server {} initialized for session {}: protocol={}, server={} v{}",
            serverName,
            sessionId,
            initResult.protocolVersion(),
            initResult.serverInfo().name(),
            initResult.serverInfo().version());

        return syncClient;
    }

    /**
     * Start an HTTP-based MCP server.
     */
    private McpSyncClient startHttpServer(String serverName, HttpServer config) {
        logger.info("Starting HTTP server {} for session {} with URL: {}", serverName, sessionId, config.url());

        WebClient.Builder webClientBuilder = WebClient.builder().baseUrl(config.url());
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        WebFluxSseClientTransport transport = new WebFluxSseClientTransport(webClientBuilder, jsonMapper);

        McpSyncClient syncClient = McpClient.sync(transport)
            .requestTimeout(requestTimeout)
            .clientInfo(new McpSchema.Implementation("agent-sdk", "1.0.0"))
            .build();

        McpSchema.InitializeResult initResult = syncClient.initialize();
        logger.info("HTTP server {} initialized for session {}: protocol={}, server={} v{}",
            serverName,
            sessionId,
            initResult.protocolVersion(),
            initResult.serverInfo().name(),
            initResult.serverInfo().version());

        return syncClient;
    }

    /**
     * Start an SSE-based MCP server.
     */
    private McpSyncClient startSseServer(String serverName, SseServer config) {
        logger.info("Starting SSE server {} for session {} with URL: {}", serverName, sessionId, config.url());

        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(config.url())
            .jsonMapper(jsonMapper)
            .build();

        McpSyncClient syncClient = McpClient.sync(transport)
            .requestTimeout(requestTimeout)
            .clientInfo(new McpSchema.Implementation("agent-sdk", "1.0.0"))
            .build();

        McpSchema.InitializeResult initResult = syncClient.initialize();
        logger.info("SSE server {} initialized for session {}: protocol={}, server={} v{}",
            serverName,
            sessionId,
            initResult.protocolVersion(),
            initResult.serverInfo().name(),
            initResult.serverInfo().version());

        return syncClient;
    }

    /**
     * Discover tools from an MCP server and register them.
     */
    private void discoverTools(String serverName, McpSyncClient client) {
        try {
            McpSchema.ListToolsResult toolsResult = client.listTools();

            if (toolsResult.tools() != null) {
                String normalizedServerName = serverName.replace("-", "_");
                for (McpSchema.Tool tool : toolsResult.tools()) {
                    String prefixedName = normalizedServerName + "-" + tool.name();
                    toolRegistry.put(prefixedName, tool);
                    toolServerMapping.put(prefixedName, serverName);
                    logger.debug("Session {} registered tool: {} (original: {}) from {}", 
                        sessionId, prefixedName, tool.name(), serverName);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to discover tools from server {} for session {}: {}", 
                serverName, sessionId, e.getMessage(), e);
        }
    }

    /**
     * Execute a tool call by proxying to the appropriate MCP server.
     */
    public String executeTool(String toolName, Map<String, Object> arguments) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("SessionMcpManager for session " + sessionId + " is not initialized");
        }

        if (shutdown) {
            throw new IllegalStateException("SessionMcpManager for session " + sessionId + " has been shutdown");
        }

        McpSchema.Tool tool = toolRegistry.get(toolName);
        if (tool == null) {
            String availableTools = toolRegistry.isEmpty()
                ? "No tools registered"
                : "Available tools: " + String.join(", ", toolRegistry.keySet());
            throw new IllegalArgumentException(
                String.format("Unknown tool: %s for session %s. %s", toolName, sessionId, availableTools));
        }

        String serverName = toolServerMapping.get(toolName);
        if (serverName == null) {
            throw new IllegalStateException("No server mapping for tool: " + toolName);
        }

        McpSyncClient client = runningClients.get(serverName);
        if (client == null) {
            throw new IllegalStateException("Server not running: " + serverName);
        }

        String originalToolName = tool.name();

        logger.info("Session {} executing tool: {} (original: {}) on server: {} with arguments: {}",
            sessionId, toolName, originalToolName, serverName, arguments);

        try {
            McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest(originalToolName, arguments)
            );

            if (result.content() != null && !result.content().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (McpSchema.Content content : result.content()) {
                    if (content instanceof McpSchema.TextContent textContent) {
                        sb.append(textContent.text());
                    } else if (content instanceof McpSchema.ImageContent imageContent) {
                        sb.append("[Image: ").append(imageContent.mimeType()).append("]");
                    } else if (content instanceof McpSchema.EmbeddedResource embeddedResource) {
                        sb.append("[Resource: ").append(embeddedResource.resource().uri()).append("]");
                    }
                }

                if (result.isError() != null && result.isError()) {
                    throw new RuntimeException("Tool execution error: " + sb.toString());
                }

                return sb.toString();
            }

            return "";

        } catch (Exception e) {
            logger.error("Tool execution failed for session {}: {} - {}", sessionId, toolName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get list of available tools for session creation.
     */
    public List<ToolDefinition> getAvailableTools() {
        return toolRegistry.entrySet().stream()
            .map(entry -> {
                String prefixedName = entry.getKey();
                McpSchema.Tool tool = entry.getValue();
                String serverName = toolServerMapping.get(prefixedName);
                String normalizedServerName = serverName != null ? serverName.replace("-", "_") : "";
                String externalToolName = normalizedServerName + "-" + tool.name();
                return ToolDefinition.builder()
                        .name(externalToolName)
                        .description(tool.description())
                        .inputSchema(convertJsonSchemaToMap(tool.inputSchema()))
                        .build();
            })
            .toList();
    }

    /**
     * Convert McpSchema.JsonSchema to a Map representation for JSON serialization.
     */
    private Map<String, Object> convertJsonSchemaToMap(McpSchema.JsonSchema schema) {
        if (schema == null) {
            return null;
        }

        Map<String, Object> schemaMap = new HashMap<>();

        if (schema.type() != null) {
            schemaMap.put("type", schema.type());
        }
        if (schema.properties() != null) {
            schemaMap.put("properties", schema.properties());
        }
        if (schema.required() != null) {
            schemaMap.put("required", schema.required());
        }
        if (schema.additionalProperties() != null) {
            schemaMap.put("additionalProperties", schema.additionalProperties());
        }
        if (schema.defs() != null) {
            schemaMap.put("$defs", schema.defs());
        }
        if (schema.definitions() != null) {
            schemaMap.put("definitions", schema.definitions());
        }

        return schemaMap;
    }

    /**
     * Get list of available tools as McpSchema.Tool objects.
     */
    public List<McpSchema.Tool> getAvailableToolsAsMcpTools() {
        return new ArrayList<>(toolRegistry.values());
    }

    /**
     * Get tool count.
     */
    public int getToolCount() {
        return toolRegistry.size();
    }

    /**
     * Get running server count.
     */
    public int getServerCount() {
        return runningClients.size();
    }

    /**
     * Get list of running server names.
     */
    public Set<String> getRunningServers() {
        return new HashSet<>(runningClients.keySet());
    }

    /**
     * Check if a specific server is running.
     */
    public boolean isServerRunning(String serverName) {
        return runningClients.containsKey(serverName);
    }

    /**
     * Check if this manager is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if this manager has been shutdown.
     */
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Get the server configuration for a running server.
     */
    public Optional<McpServer> getServerConfig(String serverName) {
        return Optional.ofNullable(serverConfigs.get(serverName));
    }

    /**
     * Get tools for a specific server as McpSchema.Tool objects.
     */
    public List<McpSchema.Tool> getToolsForServer(String serverName) {
        return toolServerMapping.entrySet().stream()
            .filter(e -> e.getValue().equals(serverName))
            .map(e -> toolRegistry.get(e.getKey()))
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Refresh tools from all running servers.
     */
    public void refreshTools() {
        logger.info("Refreshing tools for session {}", sessionId);
        toolRegistry.clear();
        toolServerMapping.clear();

        for (Map.Entry<String, McpSyncClient> entry : runningClients.entrySet()) {
            discoverTools(entry.getKey(), entry.getValue());
        }

        logger.info("Session {} tool refresh complete. Total tools: {}", sessionId, toolRegistry.size());
    }

    /**
     * Get server statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("sessionId", sessionId);
        stats.put("initialized", initialized);
        stats.put("shutdown", shutdown);
        stats.put("totalServers", runningClients.size());
        stats.put("totalTools", toolRegistry.size());

        Map<String, Integer> serverToolCounts = new HashMap<>();
        for (String serverName : runningClients.keySet()) {
            int toolCount = (int) toolServerMapping.values().stream()
                .filter(s -> s.equals(serverName))
                .count();
            serverToolCounts.put(serverName, toolCount);
        }
        stats.put("toolsPerServer", serverToolCounts);

        Map<String, String> serverTypes = new HashMap<>();
        for (Map.Entry<String, McpServer> entry : serverConfigs.entrySet()) {
            serverTypes.put(entry.getKey(), entry.getValue().type());
        }
        stats.put("serverTypes", serverTypes);

        return stats;
    }

    /**
     * Get the server name for a specific tool.
     */
    public Optional<String> getServerForTool(String toolName) {
        return Optional.ofNullable(toolServerMapping.get(toolName));
    }

    /**
     * Get a tool by name as McpSchema.Tool.
     */
    public Optional<McpSchema.Tool> getTool(String toolName) {
        return Optional.ofNullable(toolRegistry.get(toolName));
    }

    /**
     * Gets the session workspace path.
     * 
     * @return the path to the session's workspace directory
     */
    public Path getSessionWorkspacePath() {
        return sessionWorkspacePath;
    }

    /**
     * Gets the sandbox base path.
     * 
     * @return the base path for all session sandboxes
     */
    public String getSandboxBasePath() {
        return sandboxBasePath;
    }
}
