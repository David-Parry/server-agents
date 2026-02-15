package com.davidparry.agent.sdk.websocket;

import com.davidparry.agent.sdk.config.AgentSdkProperties;
import com.davidparry.agent.sdk.context.AgentApplicationContext;
import com.davidparry.agent.sdk.mcp.McpServerManager;
import com.davidparry.agent.sdk.mcp.SessionMcpManager;
import com.davidparry.agent.sdk.observability.ClientMetrics;
import com.davidparry.agent.protocol.*;
import com.davidparry.agent.protocol.dto.ChunkType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket client handler for connecting to the main server.
 * Handles connection lifecycle, reconnection, and message routing.
 * <p>
 * Each session created through this handler gets its own isolated set of MCP servers
 * via SessionMcpManager, ensuring 1-to-1 relationship between sessions and MCP server instances.
 * <p>
 * Session managers are stored in McpServerManager as the single source of truth,
 * ensuring that sessions created via any path (WebSocketClientHandler)
 * are accessible for tool execution.
 */
@Component
public class WebSocketClientHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientHandler.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final AgentSdkProperties properties;
    private final ObjectMapper objectMapper;
    private final McpServerManager mcpServerManager;
    private final ClientMetrics metrics;
    private final ApplicationContext applicationContext;
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();
    private final AtomicReference<String> connectionIdRef = new AtomicReference<>();
    private final AtomicLong heartbeatSequence = new AtomicLong(0);
    private final AtomicReference<Instant> lastActivityRef = new AtomicReference<>(Instant.now());
    
    // Outbound message queue for thread-safe WebSocket writes
    // Messages are queued here and processed by a single virtual thread to prevent concurrent write conflicts
    private final BlockingQueue<McpProxyMessage> outboundQueue = new LinkedBlockingQueue<>(1000);
    
    // Virtual thread for processing outbound messages - ensures sequential writes to WebSocket
    private volatile Thread messageSenderThread;
    
    // Flag to control the message sender loop
    private volatile boolean messageSenderRunning = true;
    
    // Lazy injection to avoid circular dependency (AgentApplicationContext depends on WebSocketClientHandler)
    @Lazy
    @Autowired
    private AgentApplicationContext agentApplicationContext;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> reconnectTask;
    private int reconnectAttempts = 0;

    public WebSocketClientHandler(AgentSdkProperties properties, ObjectMapper objectMapper,
                                  McpServerManager mcpServerManager, ClientMetrics metrics,
                                  ApplicationContext applicationContext) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mcpServerManager = mcpServerManager;
        this.metrics = metrics;
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ws-client-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // Start virtual thread for sequential message sending
        // This prevents concurrent WebSocket write conflicts (TEXT_PARTIAL_WRITING errors)
        startMessageSender();
        
        connect();
    }

    /**
     * Starts the virtual thread that processes outbound messages sequentially.
     * Using a virtual thread is efficient because:
     * - Lightweight (no OS thread consumed when blocking on queue)
     * - Blocking on BlockingQueue.take() yields the carrier thread
     * - Simple lifecycle management compared to ExecutorService
     */
    private void startMessageSender() {
        messageSenderThread = Thread.ofVirtual()
            .name("ws-message-sender")
            .start(this::processOutboundMessages);
        logger.info("Virtual thread message sender started");
    }

    /**
     * Processes outbound messages from the queue sequentially.
     * This method runs in a virtual thread and ensures only one message
     * is written to the WebSocket at a time, preventing concurrent write conflicts.
     */
    private void processOutboundMessages() {
        logger.debug("Message sender loop started");
        
        while (messageSenderRunning && !Thread.currentThread().isInterrupted()) {
            try {
                // Block efficiently on virtual thread until a message is available
                McpProxyMessage message = outboundQueue.take();
                sendMessageInternal(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Message sender interrupted, shutting down");
                break;
            } catch (Exception e) {
                // Log but continue processing - don't let one bad message kill the sender
                logger.error("Error processing outbound message", e);
            }
        }
        
        logger.info("Message sender loop stopped");
    }

    /**
     * Internal method that actually sends the message to the WebSocket.
     * Only called from the single message sender thread, so no synchronization needed.
     * Includes retry logic for transient WebSocket state conflicts.
     */
    private void sendMessageInternal(McpProxyMessage message) {
        WebSocketSession session = sessionRef.get();
        if (session == null || !session.isOpen()) {
            logger.warn("Cannot send message - not connected: {}", message.getClass().getSimpleName());
            return;
        }

        int maxRetries = 3;
        long initialDelayMs = 50;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
                logger.debug("Sent message: {}", message.getClass().getSimpleName());
                return; // Success
            } catch (IllegalStateException e) {
                // WebSocket state conflict (e.g., TEXT_PARTIAL_WRITING) - retry
                if (attempt < maxRetries) {
                    logger.warn("WebSocket write conflict, retrying ({}/{}): {}", 
                        attempt, maxRetries, message.getClass().getSimpleName());
                    try {
                        // Virtual thread - sleep is efficient, yields carrier thread
                        Thread.sleep(initialDelayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.warn("Retry interrupted for message: {}", message.getClass().getSimpleName());
                        return;
                    }
                } else {
                    logger.error("Failed to send message after {} attempts: {}", 
                        maxRetries, message.getClass().getSimpleName(), e);
                }
            } catch (IOException e) {
                logger.error("IO error sending message: {}", message.getClass().getSimpleName(), e);
                return; // Don't retry IO errors
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down WebSocket client...");
        state.set(ConnectionState.DRAINING);

        // Stop accepting new messages and signal the message sender to stop
        messageSenderRunning = false;
        
        // Interrupt the virtual thread message sender
        if (messageSenderThread != null) {
            messageSenderThread.interrupt();
            logger.debug("Message sender thread interrupted");
        }

        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
        }

        // Note: Session MCP managers are cleaned up by McpServerManager's @PreDestroy

        WebSocketSession session = sessionRef.get();
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.GOING_AWAY);
            } catch (IOException e) {
                logger.warn("Error closing WebSocket session", e);
            }
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Log any remaining messages in the queue
        int remainingMessages = outboundQueue.size();
        if (remainingMessages > 0) {
            logger.warn("Shutdown with {} messages remaining in outbound queue", remainingMessages);
        }
        
        logger.info("WebSocket client shutdown complete");
    }

    /**
     * Initiate connection to the server.
     */
    public void connect() {
        if (state.get() == ConnectionState.DRAINING) {
            logger.info("Cannot connect - client is shutting down");
            return;
        }

        if (!state.compareAndSet(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING) && !state.compareAndSet(ConnectionState.RECONNECTING, ConnectionState.CONNECTING)) {
            logger.debug("Connection already in progress or established");
            return;
        }

        logger.info("Connecting to server: {}", properties.serverUrl());

        try {
            // Configure WebSocket container with larger buffer size for large messages
            int bufferSize = properties.connection().textMessageBufferSize();
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(bufferSize);
            container.setDefaultMaxBinaryMessageBufferSize(bufferSize);
            logger.debug("WebSocket buffer size configured to {} bytes", bufferSize);

            StandardWebSocketClient client = new StandardWebSocketClient(container);

            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.add(API_KEY_HEADER, properties.apiKey());

            URI uri = URI.create(properties.serverUrl());

            client.execute(this, headers, uri).whenComplete((session, error) -> {
                if (error != null) {
                    logger.error("Failed to connect to server", error);
                    handleConnectionFailure();
                }
            });

        } catch (Exception e) {
            logger.error("Error initiating connection", e);
            handleConnectionFailure();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket connection established: {}", session.getId());
        sessionRef.set(session);
        state.set(ConnectionState.CONNECTED);
        reconnectAttempts = 0;
        lastActivityRef.set(Instant.now());

        metrics.connectionOpened();

        // Start heartbeat
        startHeartbeat();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        lastActivityRef.set(Instant.now());

        try {
            McpProxyMessage msg = objectMapper.readValue(message.getPayload(), McpProxyMessage.class);
            handleMessage(msg);
        } catch (Exception e) {
            logger.error("Error processing message: {}", message.getPayload(), e);
        }
    }

    private void handleMessage(McpProxyMessage message) {
        logger.debug("Received message: {}", message.getClass().getSimpleName());

        switch (message) {
            case ConnectionEstablished established -> {
                connectionIdRef.set(established.getConnectionId());
                logger.info("Connection established with ID: {}, server version: {}, max sessions: {}",
                            established.getConnectionId(), established.getServerVersion(),
                            established.getMaxConcurrentSessions());
            }

            case ToolCallRequest request -> {
                handleToolCallRequest(request);
            }

            case SessionStarted started -> {
                logger.info("Session started: {}", started.getSessionId());
            }

            case StreamChunk chunk -> {
                handleStreamChunk(chunk);
            }

            case SessionResult result -> {
                handleSessionResult(result);
            }

            case SessionCancelled cancelled -> {
                logger.info("Session cancelled: {} - {}", cancelled.getSessionId(), cancelled.getReason());
                cleanupSession(cancelled.getSessionId());
                // Delegate to AgentApplicationContext for proper future completion and listener notification
                agentApplicationContext.handleSessionCancelled(cancelled.getSessionId(), cancelled.getReason());
            }

            case Heartbeat heartbeat -> {
                logger.trace("Heartbeat received: {}", heartbeat.getSequenceNumber());
            }

            case ErrorMessage error -> {
                logger.error("Server error: {} - {} (session: {})", error.getCode(), error.getMessage(),
                             error.getSessionId());
                if (error.getSessionId() != null) {
                    cleanupSession(error.getSessionId());
                    // Delegate to AgentApplicationContext for proper future completion and listener notification
                    agentApplicationContext.handleSessionError(error.getSessionId(), 
                        new RuntimeException(error.getCode() + ": " + error.getMessage()));
                }
            }

            default -> logger.warn("Unhandled message type: {}", message.getClass().getSimpleName());
        }
    }

    private void handleToolCallRequest(ToolCallRequest request) {
        logger.info("Tool call request: {} for session {}", request.getToolName(), request.getSessionId());

        metrics.toolCallStarted(request.getToolName());
        long startTime = System.currentTimeMillis();

        CompletableFuture.supplyAsync(() -> {
            try {
                // Get the session-specific MCP manager from the central McpServerManager
                SessionMcpManager sessionManager = mcpServerManager
                        .getSessionManager(request.getSessionId())
                        .orElseThrow(() -> new IllegalStateException("No MCP manager found for session: " + request.getSessionId()));

                String result = sessionManager.executeTool(request.getToolName(), request.getArguments());
                return new ToolExecutionResult(true, result, null);
            } catch (Exception e) {
                logger.error("Tool execution failed: {}", request.getToolName(), e);
                return new ToolExecutionResult(false, null, e.getMessage());
            }
        }).thenAccept(result -> {
            long executionTime = System.currentTimeMillis() - startTime;
            metrics.toolCallCompleted(request.getToolName(), result.success(), executionTime);

            // Notify the session's listener about tool execution
            agentApplicationContext.notifyToolExecuted(request.getSessionId(), request.getToolName(),
                                                       result.success(), executionTime);

            ToolCallResponse response = ToolCallResponse
                    .builder()
                    .sessionId(request.getSessionId())
                    .requestId(request.getRequestId())
                    .success(result.success())
                    .result(result.result())
                    .errorMessage(result.errorMessage())
                    .executionTimeMs(executionTime)
                    .build();

            sendMessage(response);
        });
    }

    private void handleStreamChunk(StreamChunk chunk) {
        logger.debug("Stream chunk for session {}: type={}, seq={}, last={}", chunk.getSessionId(),
                     chunk.getChunkType(), chunk.getSequenceNumber(), chunk.isLast());

        // For now, just log streaming chunks
        // In a full implementation, these would be forwarded to a stream consumer
        if (chunk.getChunkType() == ChunkType.TEXT) {
            logger.trace("Stream content: {}", chunk.getContent());
        }
    }

    private void handleSessionResult(SessionResult result) {
        logger.info("Session result: {} - success={}, tools executed={}", result.getSessionId(), result.isSuccess(),
                    result.getToolCallsExecuted());
        logger.debug("Complete Result: {}", result);

        try {
            logger.info(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            logger.error("Something very wrong could not write {}", result, e);
        }

        // Cleanup the session's MCP manager
        cleanupSession(result.getSessionId());

        // Delegate to AgentApplicationContext for proper future completion and listener notification
        // AgentApplicationContext is the single source of truth for session state management
        agentApplicationContext.handleSessionResult(result);

        metrics.sessionCompleted(result.isSuccess(), result.getTotalDurationMs());
    }

    /**
     * Cleanup session resources including the session-specific MCP manager.
     * Uses McpServerManager as the single source of truth for session managers.
     */
    private void cleanupSession(String sessionId) {
        logger.info("Cleaning up session: {}", sessionId);
        mcpServerManager.destroySessionManager(sessionId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("WebSocket transport error", exception);
        metrics.connectionError();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("WebSocket connection closed: {} - {}", status.getCode(), status.getReason());
        sessionRef.set(null);
        connectionIdRef.set(null);

        metrics.connectionClosed(status.getCode());

        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }

        if (state.get() != ConnectionState.DRAINING) {
            state.set(ConnectionState.DISCONNECTED);
            scheduleReconnect();
        }
    }

    private void handleConnectionFailure() {
        state.set(ConnectionState.DISCONNECTED);
        metrics.connectionError();
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!properties.reconnect().enabled()) {
            logger.info("Reconnection disabled");
            state.set(ConnectionState.FAILED);
            return;
        }

        if (reconnectAttempts >= properties.reconnect().maxAttempts()) {
            logger.error("Max reconnection attempts ({}) reached - shutting down application", properties
                    .reconnect()
                    .maxAttempts());
            state.set(ConnectionState.FAILED);
            initiateApplicationShutdown();
            return;
        }

        reconnectAttempts++;
        long delay = calculateReconnectDelay();

        logger.info("Scheduling reconnection attempt {} in {}ms", reconnectAttempts, delay);
        state.set(ConnectionState.RECONNECTING);

        reconnectTask = scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Initiates a graceful shutdown of the Spring Boot application.
     * This is called when max reconnection attempts have been exhausted.
     */
    private void initiateApplicationShutdown() {
        logger.info("Initiating application shutdown due to connection failure");
        // Use a separate thread to avoid blocking the current execution
        CompletableFuture.runAsync(() -> {
            try {
                // Small delay to allow logging to complete
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int exitCode = SpringApplication.exit(applicationContext, () -> 1);
            System.exit(exitCode);
        });
    }

    private long calculateReconnectDelay() {
        double multiplier = Math.pow(properties.reconnect().backoffMultiplier(), reconnectAttempts - 1);
        long delay = (long) (properties.reconnect().initialDelayMs() * multiplier);
        return Math.min(delay, properties.reconnect().maxDelayMs());
    }

    private void startHeartbeat() {
        int intervalSeconds = properties.connection().heartbeatIntervalSeconds();

        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (state.get() == ConnectionState.CONNECTED) {
                Heartbeat heartbeat = Heartbeat.builder().sequenceNumber(heartbeatSequence.incrementAndGet()).build();
                sendMessage(heartbeat);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Queue a message to be sent to the server.
     * Messages are processed sequentially by a virtual thread to prevent
     * concurrent WebSocket write conflicts (TEXT_PARTIAL_WRITING errors).
     * 
     * This method is thread-safe and can be called from any thread.
     * 
     * @param message the message to send
     */
    public void sendMessage(McpProxyMessage message) {
        if (!messageSenderRunning) {
            logger.warn("Cannot send message - handler is shutting down: {}", message.getClass().getSimpleName());
            return;
        }
        
        if (!outboundQueue.offer(message)) {
            logger.error("Outbound queue full (capacity: 1000), message dropped: {}", 
                message.getClass().getSimpleName());
        } else {
            logger.trace("Message queued for sending: {}", message.getClass().getSimpleName());
        }
    }


    /**
     * Cancel an active session.
     */
    public void cancelSession(String sessionId, String reason) {
        CancelSession cancel = CancelSession.builder().sessionId(sessionId).reason(reason).build();
        sendMessage(cancel);

        // Cleanup will happen when we receive SessionCancelled message
    }

    // Getters for status
    public ConnectionState getState() {
        return state.get();
    }

    public String getConnectionId() {
        return connectionIdRef.get();
    }

    public boolean isConnected() {
        return state.get() == ConnectionState.CONNECTED;
    }

    public Instant getLastActivity() {
        return lastActivityRef.get();
    }

    private record ToolExecutionResult(boolean success, String result, String errorMessage) {
    }
}
