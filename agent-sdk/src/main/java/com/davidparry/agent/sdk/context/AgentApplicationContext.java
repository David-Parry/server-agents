package com.davidparry.agent.sdk.context;

import com.davidparry.agent.sdk.agent.AgentConfigLoader;
import com.davidparry.agent.sdk.config.AgentSdkProperties;
import com.davidparry.agent.sdk.context.routing.AgentTransitionResolver;
import com.davidparry.agent.sdk.context.routing.SimpleEdgeConditionEvaluator;
import com.davidparry.agent.sdk.context.routing.SimpleOutputSchemaValidator;
import com.davidparry.agent.sdk.context.routing.TransitionDecision;
import com.davidparry.agent.sdk.mcp.McpServerManager;
import com.davidparry.agent.sdk.mcp.SessionMcpManager;
import com.davidparry.agent.sdk.websocket.WebSocketClientHandler;
import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.Agents;
import com.davidparry.agent.protocol.CreateSession;
import com.davidparry.agent.protocol.SessionResult;
import com.davidparry.agent.protocol.dto.NextAgentStatus;
import com.davidparry.agent.protocol.dto.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Central application context for managing agent sessions.
 * <p>
 * This is the single point of entry for:
 * <ul>
 *   <li>Loading and accessing agent configurations</li>
 *   <li>Creating and managing agent sessions</li>
 *   <li>Invoking agents with prompt parameters and per-session listeners</li>
 *   <li>Managing chained agent executions with result accumulation</li>
 * </ul>
 * <p>
 * All session creation, ID generation, and CreateSession construction
 * is encapsulated within this class, providing a clean API that hides
 * implementation details from callers.
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Autowired
 * private AgentApplicationContext context;
 *
 * public void runAgent() {
 *     JsonNode params = objectMapper.valueToTree(Map.of("issueKey", "PROJ-123"));
 *
 *     // With a per-session listener
 *     SessionEventListener listener = new SessionEventListenerAdapter() {
 *         @Override
 *         public void onChainCompleted(AgentSession session, ChainedSessionResult chainResult) {
 *             System.out.println("Chain completed with " + chainResult.getExecutedAgentCount() + " agents");
 *         }
 *     };
 *
 *     ActivationResult activation = context.activateAgent("jira_agent", params, listener);
 *     ChainedSessionResult result = activation.getChainResult();
 * }
 * }</pre>
 */
public class AgentApplicationContext {

    private static final Logger logger = LoggerFactory.getLogger(AgentApplicationContext.class);

    private final AgentSdkProperties properties;
    private final ObjectMapper objectMapper;
    private final WebSocketClientHandler webSocketHandler;
    private final McpServerManager mcpServerManager;
    private final AgentConfigLoader agentConfigLoader;
    private final AgentTransitionResolver transitionResolver;

    // Active session tracking
    private final Map<String, AgentSession> activeSessions = new ConcurrentHashMap<>();

    // Pending session futures (waiting for results from WebSocket) - now returns ChainedSessionResult
    private final Map<String, CompletableFuture<ChainedSessionResult>> pendingFutures = new ConcurrentHashMap<>();

    // Chain history - accumulates results as chain progresses
    private final Map<String, List<ChainedSessionResult.AgentExecutionSnapshot>> chainHistory =
            new ConcurrentHashMap<>();

    // Pending chain resumes - chains waiting for reconnection
    private final Map<String, ChainResumeContext> pendingChainResumes = new ConcurrentHashMap<>();

    // Loaded agent configurations
    private volatile Agents agents;

    /**
     * Creates a new AgentApplicationContext.
     *
     * @param properties       the agent client configuration properties
     * @param objectMapper     the Jackson ObjectMapper for JSON processing
     * @param webSocketHandler the WebSocket handler for server communication
     * @param mcpServerManager the MCP server manager for tool execution
     */
    public AgentApplicationContext(AgentSdkProperties properties, ObjectMapper objectMapper,
                                   WebSocketClientHandler webSocketHandler, McpServerManager mcpServerManager) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        this.mcpServerManager = mcpServerManager;
        this.agentConfigLoader = new AgentConfigLoader(objectMapper);
        this.transitionResolver = new AgentTransitionResolver(new SimpleOutputSchemaValidator(objectMapper),
                                                              new SimpleEdgeConditionEvaluator());
    }

    /**
     * Initializes the context by loading agent configurations.
     */
    public void init() {
        logger.info("Initializing AgentApplicationContext");
        loadAgentConfigurations();
        logger.info("AgentApplicationContext initialized with {} agents", agents != null ? agents.agentCount() : 0);
    }

    /**
     * Shuts down the context, cancelling all active sessions.
     */
    public void shutdown() {
        logger.info("Shutting down AgentApplicationContext");

        // Cancel all active sessions
        for (String sessionId : new HashSet<>(activeSessions.keySet())) {
            cancelSession(sessionId, "Application shutdown");
        }

        activeSessions.clear();
        pendingFutures.clear();
        chainHistory.clear();
        pendingChainResumes.clear();

        logger.info("AgentApplicationContext shutdown complete");
    }

    // ==================== Agent Configuration ====================

    /**
     * Loads agent configurations from the configured path or classpath.
     */
    private void loadAgentConfigurations() {
        String configPath = properties.agentConfigPath();

        try {
            if (configPath != null && !configPath.isBlank()) {
                if (configPath.startsWith("classpath:")) {
                    String resourcePath = configPath.substring("classpath:".length());
                    this.agents = agentConfigLoader.loadFromClasspath(resourcePath);
                } else {
                    this.agents = agentConfigLoader.loadFromPath(Path.of(configPath));
                }
            } else {
                // Try default classpath location
                String defaultPath = AgentSdkProperties.DEFAULT_AGENT_CONFIG;
                if (getClass().getClassLoader().getResource(defaultPath) != null) {
                    this.agents = agentConfigLoader.loadFromClasspath(defaultPath);
                } else {
                    logger.warn("No agent configuration found. Agent activation will fail.");
                    this.agents = null;
                }
            }

            if (agents != null) {
                logger.info("Loaded {} agent configurations: {}", agents.agentCount(), agents.getAgentNames());
            }
        } catch (IOException e) {
            logger.error("Failed to load agent configurations", e);
            this.agents = null;
        }
    }

    /**
     * Reloads agent configurations from the configured source.
     * This can be called to refresh configurations without restarting the application.
     */
    public void reloadAgentConfigurations() {
        logger.info("Reloading agent configurations");
        loadAgentConfigurations();
    }

    /**
     * Gets an agent by its key (name).
     *
     * @param agentKey the agent key/name
     * @return Optional containing the agent if found
     */
    public Optional<Agent> getAgent(String agentKey) {
        if (agents == null || agentKey == null) {
            return Optional.empty();
        }
        return agents.getAgent(agentKey);
    }

    /**
     * Gets all available agent keys.
     *
     * @return set of agent keys
     */
    public Set<String> getAgentKeys() {
        if (agents == null) {
            return Set.of();
        }
        return agents.getAgentNames();
    }

    /**
     * Checks if an agent exists.
     *
     * @param agentKey the agent key
     * @return true if the agent exists
     */
    public boolean hasAgent(String agentKey) {
        return agents != null && agents.hasAgent(agentKey);
    }

    /**
     * Gets the loaded Agents configuration.
     *
     * @return the Agents configuration, or null if not loaded
     */
    public Agents getAgents() {
        return agents;
    }

    // ==================== Session Activation ====================

    /**
     * Activates an agent session and returns both the session ID and result future.
     * <p>
     * This is the primary method for invoking an agent. It:
     * <ol>
     *   <li>Looks up the agent by key</li>
     *   <li>Generates a unique session ID</li>
     *   <li>Creates the session-specific MCP manager with agent's MCP config</li>
     *   <li>Constructs the CreateSession message internally</li>
     *   <li>Sends the session to the server via WebSocket</li>
     * </ol>
     *
     * @param agentKey     the agent key (maps to agent name in configuration)
     * @param promptParams JsonNode containing parameters to substitute in the prompt
     * @return ActivationResult containing the session ID and result future
     * @throws IllegalArgumentException if the agent key is not found
     */
    public ActivationResult activateAgent(String agentKey, JsonNode promptParams) {
        return activateAgent(agentKey, promptParams, null);
    }

    /**
     * Activates an agent session with a per-session listener.
     * <p>
     * This is the core activation method. The listener will be notified of all
     * events for this specific session only.
     *
     * @param agentKey     the agent key (maps to agent name in configuration)
     * @param promptParams JsonNode containing parameters to substitute in the prompt
     * @param listener     optional listener for session events (can be null)
     * @return ActivationResult containing the session ID and result future
     * @throws IllegalArgumentException if the agent key is not found
     */
    public ActivationResult activateAgent(String agentKey, JsonNode promptParams, SessionEventListener listener) {

        // Validate connection first - caller needs to check and make sure result was successful
        if (!webSocketHandler.isConnected()) {
            ChainedSessionResult failedResult = new ChainedSessionResult("", List.of(),
                                                                         ChainedSessionResult.ChainStatus.FAILED,
                                                                         new IllegalStateException("Not connected to " +
                                                                                                           "server"),
                                                                         0);
            CompletableFuture<ChainedSessionResult> failedFuture = CompletableFuture.completedFuture(failedResult);
            return new ActivationResult("", failedFuture);
        }

        // Look up agent
        Agent agent =
                getAgent(agentKey).orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentKey + "." +
                                                                                          " Available agents: " + getAgentKeys()));

        // Generate session ID
        String sessionId = generateSessionId();

        logger.info("Activating agent '{}' with session ID: {}", agentKey, sessionId);

        // Initialize chain history for this session
        chainHistory.put(sessionId, new CopyOnWriteArrayList<>());

        // Create session-specific MCP manager with agent's MCP config
        SessionMcpManager sessionMcpManager = createSessionMcpManager(sessionId, agent);

        // Get available tools from the session MCP manager
        List<ToolDefinition> tools = sessionMcpManager.getAvailableTools();

        // Build CreateSession internally
        CreateSession createSession = CreateSession
                .builder()
                .sessionId(sessionId)
                .agent(agent)
                .streamResponse(false)
                .tools(tools)
                .promptParams(promptParams)
                .build();

        // Create AgentSession tracking object with chain position 0 (first in chain)
        AgentSession agentSession = new AgentSession(sessionId, agentKey, agent, promptParams,
                                                     System.currentTimeMillis(), listener, 0,  // chainPosition
                                                     List.of()  // no prior results
        );
        activeSessions.put(sessionId, agentSession);

        // Create future for chain result
        CompletableFuture<ChainedSessionResult> resultFuture = new CompletableFuture<>();
        pendingFutures.put(sessionId, resultFuture);

        // Register timeout handler
        registerChainTimeoutHandler(sessionId, resultFuture);

        // Mark session as running
        agentSession.markRunning();

        // Notify the session's listener of session start
        notifySessionStarted(agentSession);

        // Send the message
        webSocketHandler.sendMessage(createSession);

        logger.info("Agent '{}' activated with session {} - {} tools available", agentKey, sessionId, tools.size());

        return new ActivationResult(sessionId, resultFuture);
    }

    /**
     * Activates an agent session with parameters provided as a Map and a listener.
     *
     * @param agentKey     the agent key
     * @param promptParams parameters as a Map
     * @param listener     optional listener for session events
     * @return ActivationResult containing the session ID and result future
     */
    public ActivationResult activateAgent(String agentKey, Map<String, Object> promptParams,
                                          SessionEventListener listener) {
        JsonNode paramsNode = objectMapper.valueToTree(promptParams);
        return activateAgent(agentKey, paramsNode, listener);
    }

    /**
     * Generates a unique session ID.
     *
     * @return a new UUID-based session ID
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Creates a session-specific MCP manager using the agent's MCP configuration.
     */
    private SessionMcpManager createSessionMcpManager(String sessionId, Agent agent) {
        // If agent has its own MCP config, load it into the server manager
        if (agent.hasMcpConfig()) {
            logger.debug("Loading agent-specific MCP config for agent '{}'", agent.name());
            mcpServerManager.loadFromConfig(agent.mcpConfig());
        }

        return mcpServerManager.createSessionManager(sessionId);
    }

    // ==================== Session Management ====================

    /**
     * Gets an active session by ID.
     *
     * @param sessionId the session ID
     * @return Optional containing the session if active
     */
    public Optional<AgentSession> getSession(String sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    /**
     * Gets all active session IDs.
     *
     * @return set of active session IDs
     */
    public Set<String> getActiveSessionIds() {
        return new HashSet<>(activeSessions.keySet());
    }

    /**
     * Gets the count of active sessions.
     *
     * @return number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Gets all active sessions.
     *
     * @return unmodifiable collection of active sessions
     */
    public Collection<AgentSession> getActiveSessions() {
        return Collections.unmodifiableCollection(activeSessions.values());
    }

    /**
     * Cancels an active session.
     *
     * @param sessionId the session ID to cancel
     * @param reason    the cancellation reason
     */
    public void cancelSession(String sessionId, String reason) {
        AgentSession session = activeSessions.get(sessionId);
        if (session != null) {
            logger.info("Cancelling session: {} - {}", sessionId, reason);
            webSocketHandler.cancelSession(sessionId, reason);

            // Mark session as cancelled
            session.markCancelled(reason);

            // Build chain result with cancellation
            List<ChainedSessionResult.AgentExecutionSnapshot> history = chainHistory.getOrDefault(sessionId, List.of());

            ChainedSessionResult chainResult = new ChainedSessionResult(sessionId, new ArrayList<>(history),
                                                                        ChainedSessionResult.ChainStatus.FAILED,
                                                                        new CancellationException(reason),
                                                                        calculateTotalDuration(history));

            // Complete the future
            CompletableFuture<ChainedSessionResult> future = pendingFutures.remove(sessionId);
            if (future != null) {
                future.complete(chainResult);
            }

            // Notify the session's listener
            notifySessionCancelled(session, reason);

            // Cleanup
            activeSessions.remove(sessionId);
            chainHistory.remove(sessionId);
            pendingChainResumes.remove(sessionId);
        }
    }

    /**
     * Handles a session result received from the server.
     * This method should be called by WebSocketClientHandler when a SessionResult is received.
     *
     * @param result the session result
     */
    public void handleSessionResult(SessionResult result) {
        String sessionId = result.getSessionId();
        AgentSession session = activeSessions.get(sessionId);

        if (session == null) {
            logger.warn("Received result for unknown session: {}", sessionId);
            return;
        }

        TransitionDecision transitionDecision = transitionResolver.resolve(session.getAgent(), result);

        // Record this agent's execution in chain history
        ChainedSessionResult.AgentExecutionSnapshot snapshot =
                new ChainedSessionResult.AgentExecutionSnapshot(session.getAgentKey(), session.getAgent(), result,
                                                                transitionDecision.effectiveSuccess() ? State.COMPLETED
                                                                                                      : State.FAILED,
                                                                session.getDurationMs(), session.getChainPosition());

        List<ChainedSessionResult.AgentExecutionSnapshot> history = chainHistory.computeIfAbsent(sessionId,
                                                                                                 k -> new CopyOnWriteArrayList<>());
        history.add(snapshot);

        String nextAgentKey = transitionDecision.nextAgentKey();
        if (NextAgentStatus.END_CHAIN
                .getValue()
                .equals(nextAgentKey) || nextAgentKey == null || nextAgentKey.isBlank()) {
            if (transitionDecision.effectiveSuccess()) {
                logger.info("Session {} chain ended by transition decision", sessionId);
                completeChainSuccessfully(sessionId, result);
            } else {
                logger.info("Session {} chain ended with failure decision (schemaValid={})", sessionId,
                            transitionDecision.schemaValid());
                completeChainWithFailure(sessionId, result, new IllegalStateException(
                        "Chain ended without a valid next target and unsuccessful outcome"));
            }
            return;
        }

        Agent nextAgent = getAgent(nextAgentKey).orElse(null);
        if (NextAgentStatus.FAILED_AGENT.getValue().equals(nextAgentKey)) {
            logger.info("Session {} directed to FAILED_AGENT", sessionId);
            Optional<Agent> failedAgentOpt = getAgent(NextAgentStatus.FAILED_AGENT.getValue());
            if (failedAgentOpt.isEmpty()) {
                completeChainWithFailure(sessionId, result, new Exception(
                        "Routing resolved to FAILED_AGENT but no agent is configured with key: "
                                + NextAgentStatus.FAILED_AGENT.getValue()));
                return;
            }
            nextAgent = failedAgentOpt.get();
        }

        if (nextAgent == null) {
            logger.warn("Session {} chain ended - next agent '{}' not found", sessionId, nextAgentKey);
            completeChainWithFailure(sessionId, result,
                                     new IllegalStateException("Next agent not found: " + nextAgentKey));
            return;
        }

        // Check max chain length
        int nextPosition = session.getChainPosition() + 1;
        if (nextPosition >= properties.chain().maxChainLength()) {
            logger.warn("Session {} chain ended - max chain length {} reached", sessionId, properties
                    .chain()
                    .maxChainLength());
            completeChainSuccessfully(sessionId, result);
            return;
        }

        // Continue chain - with reconnection support
        activateChainedAgentWithReconnect(nextAgent, sessionId, result, nextPosition, session.getListener());
    }

    // ==================== Chain Execution ====================

    /**
     * Activates a chained agent with reconnection support.
     * If WebSocket is disconnected, attempts reconnection before failing.
     */
    private void activateChainedAgentWithReconnect(Agent agent, String sessionId, SessionResult lastResult,
                                                   int chainPosition, SessionEventListener listener) {

        if (webSocketHandler.isConnected()) {
            // Happy path - proceed immediately
            doActivateChainedAgent(agent, sessionId, lastResult, chainPosition, listener);
            return;
        }

        // WebSocket disconnected - attempt reconnection
        logger.warn("WebSocket disconnected during chain at position {}. Attempting reconnection for session {}",
                    chainPosition, sessionId);

        // Get accumulated history
        List<ChainedSessionResult.AgentExecutionSnapshot> completedSnapshots = chainHistory.getOrDefault(sessionId,
                                                                                                         List.of());

        // Store context for resumption
        ChainResumeContext resumeContext = new ChainResumeContext(agent, sessionId, lastResult, chainPosition,
                                                                  new ArrayList<>(completedSnapshots), listener,
                                                                  Instant.now());
        pendingChainResumes.put(sessionId, resumeContext);

        // Start reconnection attempt in background
        attemptReconnectionForChain(sessionId, resumeContext);
    }

    /**
     * Attempts to reconnect WebSocket for a chain that was interrupted.
     * Uses configurable timeout from properties.
     */
    private void attemptReconnectionForChain(String sessionId, ChainResumeContext context) {
        int timeoutSeconds = properties.chain().reconnectTimeoutSeconds();
        long timeoutMs = timeoutSeconds * 1000L;

        CompletableFuture.runAsync(() -> {
            int attemptNumber = 0;
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                attemptNumber++;

                // Notify listener of reconnection attempt
                AgentSession currentSession = activeSessions.get(sessionId);
                if (currentSession != null && currentSession.hasListener()) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    try {
                        currentSession.getListener().onChainReconnecting(currentSession, attemptNumber, elapsed);
                    } catch (Exception e) {
                        logger.warn("Error notifying listener of reconnection attempt for session {}", sessionId, e);
                    }
                }

                // Check if already reconnected (another chain might have triggered it)
                if (webSocketHandler.isConnected()) {
                    logger.info("WebSocket reconnected. Resuming chain for session {}", sessionId);
                    pendingChainResumes.remove(sessionId);
                    doActivateChainedAgent(context.nextAgent(), context.sessionId(), context.lastResult(),
                                           context.nextChainPosition(), context.listener());
                    return;
                }

                // Trigger reconnection attempt
                webSocketHandler.connect();

                // Wait a bit before checking/retrying
                try {
                    Thread.sleep(Math.min(2000, timeoutMs / 10));  // Check every 2 seconds max
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Timeout reached - fail the chain
            logger.error("Reconnection timeout ({} seconds) reached for session {}. Failing chain.", timeoutSeconds,
                         sessionId);

            pendingChainResumes.remove(sessionId);
            failChainDueToDisconnection(sessionId, context);
        });
    }

    /**
     * Fails a chain due to WebSocket disconnection after reconnection timeout.
     */
    private void failChainDueToDisconnection(String sessionId, ChainResumeContext context) {
        // Build chain result with partial history
        List<ChainedSessionResult.AgentExecutionSnapshot> history = new ArrayList<>(context.completedSnapshots());

        // Add a failure marker for the agent that couldn't start
        ChainedSessionResult.AgentExecutionSnapshot failureSnapshot =
                new ChainedSessionResult.AgentExecutionSnapshot(context
                                                                                                                              .nextAgent()
                                                                                                                              .name(), context.nextAgent(), null,  // No result - never executed
                                                                                                                      State.FAILED, 0, context.nextChainPosition());
        history.add(failureSnapshot);

        long totalDuration = calculateTotalDuration(history);
        totalDuration += context.getWaitingDurationMs();  // Include reconnection wait time

        ChainedSessionResult chainResult = new ChainedSessionResult(sessionId, history,
                                                                    ChainedSessionResult.ChainStatus.PARTIAL_FAILURE,
                                                                    new IllegalStateException("WebSocket disconnected" +
                                                                                                      " and " +
                                                                                                      "reconnection " +
                                                                                                      "timed out " +
                                                                                                      "after " + properties
                .chain()
                .reconnectTimeoutSeconds() + " seconds"), totalDuration);

        // Complete the future
        CompletableFuture<ChainedSessionResult> future = pendingFutures.remove(sessionId);
        if (future != null) {
            future.complete(chainResult);
        }

        // Notify listener
        AgentSession session = activeSessions.remove(sessionId);
        if (session != null && session.hasListener()) {
            try {
                session.getListener().onChainFailed(session, chainResult);
            } catch (Exception e) {
                logger.warn("Error notifying listener of chain failure for session {}", sessionId, e);
            }
        }

        // Cleanup
        chainHistory.remove(sessionId);
    }

    /**
     * Actually activates a chained agent (called when WebSocket is connected).
     */
    private void doActivateChainedAgent(Agent agent, String sessionId, SessionResult lastResult, int chainPosition,
                                        SessionEventListener listener) {

        // Get prior results for the new session
        List<SessionResult> priorResults = chainHistory
                .getOrDefault(sessionId, List.of())
                .stream()
                .map(ChainedSessionResult.AgentExecutionSnapshot::result)
                .filter(Objects::nonNull)
                .toList();

        // Create session-specific MCP manager
        SessionMcpManager sessionMcpManager = createSessionMcpManager(sessionId, agent);
        List<ToolDefinition> tools = sessionMcpManager.getAvailableTools();

        String agentKey = agent.name();

        // Remove prior AgentSession (but keep chain history)
        AgentSession priorSession = activeSessions.remove(sessionId);
        if (priorSession != null) {
            priorSession.markCompleted(lastResult);
        }

        // Build CreateSession with result from previous agent as params
        CreateSession createSession = CreateSession
                .builder()
                .sessionId(sessionId)
                .agent(agent)
                .streamResponse(false)
                .tools(tools)
                .promptParams(lastResult.getContent())
                .build();

        // Create new AgentSession with chain context
        AgentSession agentSession = new AgentSession(sessionId, agentKey, agent, lastResult.getContent(),
                                                     System.currentTimeMillis(), listener, chainPosition, priorResults);
        activeSessions.put(sessionId, agentSession);

        // Mark as running
        agentSession.markRunning();

        // Notify listeners
        notifyAgentActivated(agentSession);
        if (listener != null) {
            try {
                listener.onChainedAgentStarted(agentSession, chainPosition, priorResults);
            } catch (Exception e) {
                logger.warn("Error notifying listener of chained agent start for session {}", sessionId, e);
            }
        }

        // Send the message
        webSocketHandler.sendMessage(createSession);

        logger.info("Chained agent '{}' activated at position {} for session {} - {} tools available", agentKey,
                    chainPosition, sessionId, tools.size());
    }

    // ==================== Chain Completion ====================

    /**
     * Completes a chain successfully.
     */
    private void completeChainSuccessfully(String sessionId, SessionResult finalResult) {
        AgentSession session = activeSessions.get(sessionId);

        List<ChainedSessionResult.AgentExecutionSnapshot> history = chainHistory.getOrDefault(sessionId, List.of());

        long totalDuration = calculateTotalDuration(history);

        ChainedSessionResult chainResult = new ChainedSessionResult(sessionId, new ArrayList<>(history),
                                                                    ChainedSessionResult.ChainStatus.COMPLETED, null,
                                                                    totalDuration);

        // Mark session completed
        if (session != null) {
            session.markCompleted(finalResult);
        }

        // Complete the future
        CompletableFuture<ChainedSessionResult> future = pendingFutures.remove(sessionId);
        if (future != null) {
            future.complete(chainResult);
        }

        // Notify listeners
        if (session != null) {
            notifySessionCompleted(session, finalResult);
            if (session.hasListener()) {
                try {
                    session.getListener().onChainCompleted(session, chainResult);
                } catch (Exception e) {
                    logger.warn("Error notifying listener of chain completion for session {}", sessionId, e);
                }
            }
        }

        // Cleanup
        activeSessions.remove(sessionId);
        chainHistory.remove(sessionId);
        pendingChainResumes.remove(sessionId);

        logger.info("Chain completed successfully for session {}: {} agents executed in {}ms", sessionId,
                    history.size(), totalDuration);
    }

    /**
     * Completes a chain with failure.
     */
    private void completeChainWithFailure(String sessionId, SessionResult lastResult, Throwable cause) {
        AgentSession session = activeSessions.get(sessionId);

        List<ChainedSessionResult.AgentExecutionSnapshot> history = chainHistory.getOrDefault(sessionId, List.of());

        long totalDuration = calculateTotalDuration(history);

        // Determine status based on whether any agents succeeded
        ChainedSessionResult.ChainStatus status = history
                .stream()
                .anyMatch(ChainedSessionResult.AgentExecutionSnapshot::isSuccess) ?
                ChainedSessionResult.ChainStatus.PARTIAL_FAILURE : ChainedSessionResult.ChainStatus.FAILED;

        Throwable failureCause = cause != null ? cause :
                new RuntimeException("Agent execution failed: " + (lastResult != null ? lastResult.getErrorMessage()
                        : "unknown"));

        ChainedSessionResult chainResult = new ChainedSessionResult(sessionId, new ArrayList<>(history), status,
                                                                    failureCause, totalDuration);

        // Mark session failed
        if (session != null) {
            session.markFailed(failureCause);
        }

        // Complete the future
        CompletableFuture<ChainedSessionResult> future = pendingFutures.remove(sessionId);
        if (future != null) {
            future.complete(chainResult);  // Complete normally with failure status
        }

        // Notify listeners
        if (session != null) {
            notifySessionFailed(session, failureCause);
            if (session.hasListener()) {
                try {
                    session.getListener().onChainFailed(session, chainResult);
                } catch (Exception e) {
                    logger.warn("Error notifying listener of chain failure for session {}", sessionId, e);
                }
            }
        }

        // Cleanup
        activeSessions.remove(sessionId);
        chainHistory.remove(sessionId);
        pendingChainResumes.remove(sessionId);

        logger.info("Chain failed for session {}: {} agents executed, status={}", sessionId, history.size(), status);
    }

    /**
     * Calculates total duration from chain history.
     */
    private long calculateTotalDuration(List<ChainedSessionResult.AgentExecutionSnapshot> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        return history.stream().mapToLong(ChainedSessionResult.AgentExecutionSnapshot::durationMs).sum();
    }

    // ==================== Error Handling ====================

    /**
     * Handles a session error.
     * This method should be called by WebSocketClientHandler when a session fails.
     *
     * @param sessionId the session ID
     * @param error     the error that occurred
     */
    public void handleSessionError(String sessionId, Throwable error) {
        AgentSession session = activeSessions.get(sessionId);

        if (session == null) {
            logger.warn("Received error for unknown session: {}", sessionId);
            return;
        }

        logger.error("Session {} failed: {}", sessionId, error.getMessage());

        // Mark session as failed
        session.markFailed(error);

        // Build chain result with error
        List<ChainedSessionResult.AgentExecutionSnapshot> history = chainHistory.getOrDefault(sessionId, List.of());

        ChainedSessionResult.ChainStatus status = history
                .stream()
                .anyMatch(ChainedSessionResult.AgentExecutionSnapshot::isSuccess) ?
                ChainedSessionResult.ChainStatus.PARTIAL_FAILURE : ChainedSessionResult.ChainStatus.FAILED;

        ChainedSessionResult chainResult = new ChainedSessionResult(sessionId, new ArrayList<>(history), status,
                                                                    error, calculateTotalDuration(history));

        // Complete the future
        CompletableFuture<ChainedSessionResult> future = pendingFutures.remove(sessionId);
        if (future != null) {
            future.complete(chainResult);
        }

        // Notify the session's listener
        notifySessionFailed(session, error);
        if (session.hasListener()) {
            try {
                session.getListener().onChainFailed(session, chainResult);
            } catch (Exception e) {
                logger.warn("Error notifying listener of chain failure for session {}", sessionId, e);
            }
        }

        // Cleanup
        activeSessions.remove(sessionId);
        chainHistory.remove(sessionId);
        pendingChainResumes.remove(sessionId);
    }

    /**
     * Handles a session cancellation from the server.
     * This method should be called by WebSocketClientHandler when a SessionCancelled is received.
     *
     * @param sessionId the session ID
     * @param reason    the cancellation reason
     */
    public void handleSessionCancelled(String sessionId, String reason) {
        AgentSession session = activeSessions.get(sessionId);

        if (session == null) {
            logger.warn("Received cancellation for unknown session: {}", sessionId);
            return;
        }

        logger.info("Session {} cancelled: {}", sessionId, reason);

        // Mark session as cancelled
        session.markCancelled(reason);

        // Build chain result with cancellation
        List<ChainedSessionResult.AgentExecutionSnapshot> history = chainHistory.getOrDefault(sessionId, List.of());

        ChainedSessionResult chainResult = new ChainedSessionResult(sessionId, new ArrayList<>(history),
                                                                    ChainedSessionResult.ChainStatus.FAILED,
                                                                    new CancellationException(reason),
                                                                    calculateTotalDuration(history));

        // Complete the future
        CompletableFuture<ChainedSessionResult> future = pendingFutures.remove(sessionId);
        if (future != null) {
            future.complete(chainResult);
        }

        // Notify the session's listener
        notifySessionCancelled(session, reason);

        // Cleanup
        activeSessions.remove(sessionId);
        chainHistory.remove(sessionId);
        pendingChainResumes.remove(sessionId);
    }

    // ==================== Internal Event Handling ====================

    /**
     * Registers a timeout handler for the chain.
     */
    private void registerChainTimeoutHandler(String sessionId, CompletableFuture<ChainedSessionResult> future) {
        // Set up timeout handling - 30 minutes for entire chain
        future.orTimeout(30, TimeUnit.MINUTES).whenComplete((result, error) -> {
            if (error instanceof TimeoutException) {
                handleSessionError(sessionId, new TimeoutException("Chain timed out after 30 minutes"));
            }
        });
    }

    /**
     * Notifies the session's listener of session start.
     */
    private void notifySessionStarted(AgentSession session) {
        if (session.hasListener()) {
            try {
                session.getListener().onSessionStarted(session);
            } catch (Exception e) {
                logger.warn("Error notifying listener of session start for session {}", session.getSessionId(), e);
            }
        }
    }

    /**
     * Notifies the session's listener of agent activation.
     */
    private void notifyAgentActivated(AgentSession session) {
        if (session.hasListener()) {
            try {
                session.getListener().onAgentActivated(session);
            } catch (Exception e) {
                logger.warn("Error notifying listener of agent activation for session {}", session.getSessionId(), e);
            }
        }
    }

    /**
     * Notifies the session's listener of session completion.
     */
    private void notifySessionCompleted(AgentSession session, SessionResult result) {
        if (session.hasListener()) {
            try {
                session.getListener().onSessionCompleted(session, result);
            } catch (Exception e) {
                logger.warn("Error notifying listener of session completion for session {}", session.getSessionId(), e);
            }
        }
    }

    /**
     * Notifies the session's listener of session failure.
     */
    private void notifySessionFailed(AgentSession session, Throwable error) {
        if (session.hasListener()) {
            try {
                session.getListener().onSessionFailed(session, error);
            } catch (Exception e) {
                logger.warn("Error notifying listener of session failure for session {}", session.getSessionId(), e);
            }
        }
    }

    /**
     * Notifies the session's listener of session cancellation.
     */
    private void notifySessionCancelled(AgentSession session, String reason) {
        if (session.hasListener()) {
            try {
                session.getListener().onSessionCancelled(session, reason);
            } catch (Exception e) {
                logger.warn("Error notifying listener of session cancellation for session {}", session.getSessionId()
                        , e);
            }
        }
    }

    /**
     * Notifies the session's listener of a tool execution.
     * This can be called by the tool execution handler.
     *
     * @param sessionId  the session ID
     * @param toolName   the tool that was executed
     * @param success    whether the execution succeeded
     * @param durationMs the execution duration
     */
    public void notifyToolExecuted(String sessionId, String toolName, boolean success, long durationMs) {
        AgentSession session = activeSessions.get(sessionId);
        if (session != null && session.hasListener()) {
            try {
                session.getListener().onToolExecuted(session, toolName, success, durationMs);
            } catch (Exception e) {
                logger.warn("Error notifying listener of tool execution for session {}", sessionId, e);
            }
        }
    }

    // ==================== Status & Diagnostics ====================

    /**
     * Checks if connected to the server.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return webSocketHandler.isConnected();
    }

    /**
     * Gets context statistics.
     *
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("connected", isConnected());
        stats.put("agentCount", agents != null ? agents.agentCount() : 0);
        stats.put("agentKeys", getAgentKeys());
        stats.put("activeSessionCount", activeSessions.size());
        stats.put("activeSessions", getActiveSessionIds());
        stats.put("pendingChainResumes", pendingChainResumes.size());

        // Count sessions with listeners
        long sessionsWithListeners = activeSessions.values().stream().filter(AgentSession::hasListener).count();
        stats.put("sessionsWithListeners", sessionsWithListeners);

        // Add per-session details
        Map<String, Map<String, Object>> sessionDetails = new LinkedHashMap<>();
        for (AgentSession session : activeSessions.values()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("agentKey", session.getAgentKey());
            detail.put("state", session.getState().name());
            detail.put("chainPosition", session.getChainPosition());
            detail.put("durationMs", session.getDurationMs());
            detail.put("hasListener", session.hasListener());

            // Add chain history size
            List<ChainedSessionResult.AgentExecutionSnapshot> history = chainHistory.get(session.getSessionId());
            detail.put("chainHistorySize", history != null ? history.size() : 0);

            sessionDetails.put(session.getSessionId(), detail);
        }
        stats.put("sessionDetails", sessionDetails);

        return stats;
    }

    /**
     * Gets the WebSocket handler for direct access if needed.
     *
     * @return the WebSocket handler
     */
    public WebSocketClientHandler getWebSocketHandler() {
        return webSocketHandler;
    }

    /**
     * Gets the MCP server manager for direct access if needed.
     *
     * @return the MCP server manager
     */
    public McpServerManager getMcpServerManager() {
        return mcpServerManager;
    }
}
