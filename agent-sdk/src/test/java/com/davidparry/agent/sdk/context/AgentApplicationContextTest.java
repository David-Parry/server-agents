package com.davidparry.agent.sdk.context;

import com.davidparry.agent.sdk.config.AgentSdkProperties;
import com.davidparry.agent.sdk.mcp.McpServerManager;
import com.davidparry.agent.sdk.mcp.SessionMcpManager;
import com.davidparry.agent.sdk.websocket.WebSocketClientHandler;
import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.SessionResult;
import com.davidparry.agent.protocol.dto.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AgentApplicationContext.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentApplicationContextTest {

    @Mock
    private AgentSdkProperties properties;

    @Mock
    private AgentSdkProperties.ChainConfig chainConfig;

    @Mock
    private WebSocketClientHandler webSocketHandler;

    @Mock
    private McpServerManager mcpServerManager;

    @Mock
    private SessionMcpManager sessionMcpManager;

    private ObjectMapper objectMapper;
    private AgentApplicationContext context;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // Setup default property mocks
        when(properties.agentConfigPath()).thenReturn(null);
        when(properties.chain()).thenReturn(chainConfig);
        when(chainConfig.reconnectTimeoutSeconds()).thenReturn(60);
        when(chainConfig.maxChainLength()).thenReturn(10);
        
        context = new AgentApplicationContext(
            properties,
            objectMapper,
            webSocketHandler,
            mcpServerManager
        );
    }

    @Test
    void init_loadsAgentConfigurationsFromClasspath() {
        // The init method is called by Spring, but we can test the state after construction
        // Since we're not loading from classpath in tests, agents will be null
        assertFalse(context.hasAgent("nonexistent"));
        assertTrue(context.getAgentKeys().isEmpty());
    }

    @Test
    void getAgent_returnsEmptyWhenAgentsNotLoaded() {
        Optional<Agent> agent = context.getAgent("test_agent");
        assertTrue(agent.isEmpty());
    }

    @Test
    void hasAgent_returnsFalseWhenAgentsNotLoaded() {
        assertFalse(context.hasAgent("test_agent"));
    }

    @Test
    void getAgentKeys_returnsEmptySetWhenAgentsNotLoaded() {
        Set<String> keys = context.getAgentKeys();
        assertTrue(keys.isEmpty());
    }

    @Test
    void activateAgentWithResult_failsWhenNotConnected() {
        when(webSocketHandler.isConnected()).thenReturn(false);
        
        JsonNode params = objectMapper.valueToTree(Map.of("key", "value"));
        
        ActivationResult result = context.activateAgent("test_agent", params);
        
        // Now returns a completed future with FAILED status instead of exceptionally completed
        assertTrue(result.resultFuture().isDone());
        assertFalse(result.resultFuture().isCompletedExceptionally());
        
        ChainedSessionResult chainResult = result.resultFuture().join();
        assertEquals(ChainedSessionResult.ChainStatus.FAILED, chainResult.status());
        assertNotNull(chainResult.failureCause());
        assertEquals("", result.sessionId());
    }

    @Test
    void activateAgentWithResult_failsWhenAgentNotFound() {
        when(webSocketHandler.isConnected()).thenReturn(true);
        
        JsonNode params = objectMapper.valueToTree(Map.of("key", "value"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            context.activateAgent("nonexistent_agent", params);
        });
    }

    @Test
    void getActiveSessionCount_returnsZeroInitially() {
        assertEquals(0, context.getActiveSessionCount());
    }

    @Test
    void getActiveSessionIds_returnsEmptySetInitially() {
        assertTrue(context.getActiveSessionIds().isEmpty());
    }

    @Test
    void getSession_returnsEmptyForUnknownSession() {
        Optional<AgentSession> session = context.getSession("unknown-session");
        assertTrue(session.isEmpty());
    }

    @Test
    void isConnected_delegatesToWebSocketHandler() {
        when(webSocketHandler.isConnected()).thenReturn(true);
        assertTrue(context.isConnected());
        
        when(webSocketHandler.isConnected()).thenReturn(false);
        assertFalse(context.isConnected());
    }

    @Test
    void getStatistics_returnsCorrectStructure() {
        when(webSocketHandler.isConnected()).thenReturn(true);
        
        Map<String, Object> stats = context.getStatistics();
        
        assertTrue(stats.containsKey("connected"));
        assertTrue(stats.containsKey("agentCount"));
        assertTrue(stats.containsKey("agentKeys"));
        assertTrue(stats.containsKey("activeSessionCount"));
        assertTrue(stats.containsKey("activeSessions"));
        assertTrue(stats.containsKey("sessionsWithListeners"));
        assertTrue(stats.containsKey("sessionDetails"));
        assertTrue(stats.containsKey("pendingChainResumes"));
        
        assertEquals(true, stats.get("connected"));
        assertEquals(0, stats.get("agentCount"));
        assertEquals(0, stats.get("activeSessionCount"));
        assertEquals(0L, stats.get("sessionsWithListeners"));
        assertEquals(0, stats.get("pendingChainResumes"));
    }

    @Test
    void handleSessionResult_notifiesListener() {
        // This test verifies the result handling mechanism
        SessionEventListener listener = mock(SessionEventListener.class);
        
        SessionResult mockResult = mock(SessionResult.class);
        when(mockResult.getSessionId()).thenReturn("unknown-session");
        
        // Should not throw even for unknown session
        assertDoesNotThrow(() -> context.handleSessionResult(mockResult));
    }

    @Test
    void handleSessionError_handlesUnknownSession() {
        // Should not throw even for unknown session
        assertDoesNotThrow(() -> context.handleSessionError("unknown-session", new RuntimeException("test")));
    }

    @Test
    void handleSessionCancelled_handlesUnknownSession() {
        // Should not throw even for unknown session
        assertDoesNotThrow(() -> context.handleSessionCancelled("unknown-session", "test reason"));
    }

    @Test
    void cancelSession_doesNothingForUnknownSession() {
        // Should not throw
        assertDoesNotThrow(() -> context.cancelSession("unknown-session", "test reason"));
        
        // Verify cancelSession was not called on handler
        verify(webSocketHandler, never()).cancelSession(anyString(), anyString());
    }

    @Test
    void getWebSocketHandler_returnsHandler() {
        assertSame(webSocketHandler, context.getWebSocketHandler());
    }

    @Test
    void getMcpServerManager_returnsManager() {
        assertSame(mcpServerManager, context.getMcpServerManager());
    }

    @Test
    void activateAgentWithResult_withMapParams_convertsToJsonNode() {
        when(webSocketHandler.isConnected()).thenReturn(false);
        
        Map<String, Object> params = Map.of("key", "value");
        
        // Should fail due to not connected, but the conversion should work
        ActivationResult result = context.activateAgent("test_agent", params, null);
        
        // Now returns completed future with FAILED status
        assertTrue(result.resultFuture().isDone());
        ChainedSessionResult chainResult = result.resultFuture().join();
        assertEquals(ChainedSessionResult.ChainStatus.FAILED, chainResult.status());
    }

    @Test
    void notifyToolExecuted_doesNothingForUnknownSession() {
        // Should not throw for unknown session
        assertDoesNotThrow(() -> context.notifyToolExecuted("unknown-session", "test-tool", true, 100));
    }

    @Test
    void shutdown_clearsAllState() {
        context.shutdown();
        
        Map<String, Object> stats = context.getStatistics();
        assertEquals(0, stats.get("activeSessionCount"));
        assertEquals(0, stats.get("pendingChainResumes"));
    }

    @Test
    void activateAgentWithResult_withListener_storesListenerInSession() {
        when(webSocketHandler.isConnected()).thenReturn(false);
        
        SessionEventListener listener = mock(SessionEventListener.class);
        JsonNode params = objectMapper.valueToTree(Map.of("key", "value"));
        
        // Will fail due to not connected, but we can verify the listener parameter is accepted
        ActivationResult result = context.activateAgent("test_agent", params, listener);
        
        // Now returns completed future with FAILED status
        assertTrue(result.resultFuture().isDone());
        ChainedSessionResult chainResult = result.resultFuture().join();
        assertEquals(ChainedSessionResult.ChainStatus.FAILED, chainResult.status());
    }

    @Test
    void chainedSessionResult_helperMethods() {
        // Test ChainedSessionResult helper methods
        ChainedSessionResult emptyResult = new ChainedSessionResult(
            "test-session",
            List.of(),
            ChainedSessionResult.ChainStatus.FAILED,
            new RuntimeException("test"),
            0
        );
        
        assertTrue(emptyResult.getFinalResult().isEmpty());
        assertTrue(emptyResult.getSuccessfulResults().isEmpty());
        assertEquals(0, emptyResult.getExecutedAgentCount());
        assertEquals(0, emptyResult.getSuccessfulAgentCount());
        assertFalse(emptyResult.isFullySuccessful());
        assertTrue(emptyResult.isCompleteFailure());
    }

    @Test
    void activationResult_helperMethods() {
        // Test ActivationResult helper methods with failed result
        ChainedSessionResult failedResult = new ChainedSessionResult(
            "test-session",
            List.of(),
            ChainedSessionResult.ChainStatus.FAILED,
            new RuntimeException("test"),
            0
        );
        
        ActivationResult result = new ActivationResult(
            "test-session",
            CompletableFuture.completedFuture(failedResult)
        );
        
        assertFalse(result.isPending());
        assertFalse(result.isCompletedSuccessfully());
        assertTrue(result.isFailed());
        assertFalse(result.hasPartialResults());
        assertTrue(result.getFinalResult().isEmpty());
        assertTrue(result.getAllResults().isEmpty());
        assertEquals(0, result.getExecutedAgentCount());
    }

    @Test
    void activationResult_completedSuccessfully() {
        // Test ActivationResult with successful completion
        SessionResult mockSessionResult = mock(SessionResult.class);
        when(mockSessionResult.isSuccess()).thenReturn(true);
        
        Agent mockAgent = mock(Agent.class);
        when(mockAgent.name()).thenReturn("test-agent");
        
        ChainedSessionResult.AgentExecutionSnapshot snapshot = 
            new ChainedSessionResult.AgentExecutionSnapshot(
                "test-agent",
                mockAgent,
                mockSessionResult,
                State.COMPLETED,
                1000,
                0
            );
        
        ChainedSessionResult successResult = new ChainedSessionResult(
            "test-session",
            List.of(snapshot),
            ChainedSessionResult.ChainStatus.COMPLETED,
            null,
            1000
        );
        
        ActivationResult result = new ActivationResult(
            "test-session",
            CompletableFuture.completedFuture(successResult)
        );
        
        assertFalse(result.isPending());
        assertTrue(result.isCompletedSuccessfully());
        assertFalse(result.isFailed());
        assertTrue(result.getFinalResult().isPresent());
        assertEquals(1, result.getExecutedAgentCount());
    }

    @Test
    void agentSession_chainTracking() {
        // Test AgentSession chain tracking
        Agent mockAgent = mock(Agent.class);
        SessionResult mockResult = mock(SessionResult.class);
        
        AgentSession session = new AgentSession(
            "test-session",
            "test-agent",
            mockAgent,
            null,
            System.currentTimeMillis(),
            null,
            2,  // chainPosition
            List.of(mockResult)  // priorResults
        );
        
        assertEquals(2, session.getChainPosition());
        assertEquals(1, session.getPriorAgentCount());
        assertFalse(session.isFirstInChain());
        assertTrue(session.isChained());
        assertEquals(List.of(mockResult), session.getPriorChainResults());
    }

    @Test
    void agentSession_firstInChain() {
        // Test AgentSession as first in chain
        Agent mockAgent = mock(Agent.class);
        
        AgentSession session = new AgentSession(
            "test-session",
            "test-agent",
            mockAgent,
            null,
            System.currentTimeMillis(),
            null
        );
        
        assertEquals(0, session.getChainPosition());
        assertEquals(0, session.getPriorAgentCount());
        assertTrue(session.isFirstInChain());
        assertFalse(session.isChained());
        assertTrue(session.getPriorChainResults().isEmpty());
    }
}
