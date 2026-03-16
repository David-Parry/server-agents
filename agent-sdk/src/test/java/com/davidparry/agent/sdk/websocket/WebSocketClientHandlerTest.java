package com.davidparry.agent.sdk.websocket;

import com.davidparry.agent.protocol.ConnectionEstablished;
import com.davidparry.agent.protocol.ErrorMessage;
import com.davidparry.agent.protocol.McpProxyMessage;
import com.davidparry.agent.protocol.SessionCancelled;
import com.davidparry.agent.protocol.SessionResult;
import com.davidparry.agent.protocol.dto.ErrorCode;
import com.davidparry.agent.sdk.config.AgentSdkProperties;
import com.davidparry.agent.sdk.context.AgentApplicationContext;
import com.davidparry.agent.sdk.mcp.McpServerManager;
import com.davidparry.agent.sdk.observability.ClientMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketClientHandlerTest {

    @Mock
    private McpServerManager mcpServerManager;

    @Mock
    private ClientMetrics metrics;

    @Mock
    private AgentApplicationContext agentApplicationContext;

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @Mock
    private WebSocketSession webSocketSession;

    private ObjectMapper objectMapper;
    private WebSocketClientHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new WebSocketClientHandler(properties(true), objectMapper, mcpServerManager, metrics);
        handler.setAgentApplicationContext(agentApplicationContext);
    }

    private AgentSdkProperties properties(boolean reconnectEnabled) {
        return new AgentSdkProperties(
            "api-key",
            "ws://localhost:8080/mcp-proxy",
            new AgentSdkProperties.ConnectionConfig(1, 30, 300, AgentSdkProperties.DEFAULT_TEXT_MESSAGE_BUFFER_SIZE),
            new AgentSdkProperties.ReconnectConfig(reconnectEnabled, 2, 50, 1000, 2.0),
            new AgentSdkProperties.ChainConfig(),
            "agent.yml",
            new AgentSdkProperties.McpConfig(5)
        );
    }

    @SuppressWarnings("unchecked")
    private BlockingQueue<McpProxyMessage> outboundQueue() throws Exception {
        Field queueField = WebSocketClientHandler.class.getDeclaredField("outboundQueue");
        queueField.setAccessible(true);
        return (BlockingQueue<McpProxyMessage>) queueField.get(handler);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = WebSocketClientHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }

    @SuppressWarnings("unchecked")
    private void setState(ConnectionState state) throws Exception {
        Field stateField = WebSocketClientHandler.class.getDeclaredField("state");
        stateField.setAccessible(true);
        ((AtomicReference<ConnectionState>) stateField.get(handler)).set(state);
    }

    private void invokeHandleMessage(McpProxyMessage message) throws Exception {
        Method method = WebSocketClientHandler.class.getDeclaredMethod("handleMessage", McpProxyMessage.class);
        method.setAccessible(true);
        method.invoke(handler, message);
    }

    @Test
    void cancelSession_enqueuesCancelMessage() throws Exception {
        handler.cancelSession("session-1", "no longer needed");

        assertEquals(1, outboundQueue().size());
        assertNotNull(outboundQueue().peek());
    }

    @Test
    void sendMessage_dropsWhenShuttingDown() throws Exception {
        setField("messageSenderRunning", false);

        handler.cancelSession("session-1", "test");

        assertEquals(0, outboundQueue().size());
    }

    @Test
    void handleTransportError_reportsMetric() {
        handler.handleTransportError(webSocketSession, new RuntimeException("boom"));
        verify(metrics).connectionError();
    }

    @Test
    void afterConnectionEstablished_setsConnectedStateAndStartsHeartbeat() throws Exception {
        when(webSocketSession.getId()).thenReturn("ws-1");
        doReturn(scheduledFuture).when(scheduler)
            .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        setField("scheduler", scheduler);

        handler.afterConnectionEstablished(webSocketSession);

        assertEquals(ConnectionState.CONNECTED, handler.getState());
        verify(metrics).connectionOpened();
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any());
    }

    @Test
    void afterConnectionClosed_withReconnectDisabled_marksFailed() {
        WebSocketClientHandler reconnectDisabled = new WebSocketClientHandler(
            properties(false),
            objectMapper,
            mcpServerManager,
            metrics
        );
        reconnectDisabled.afterConnectionClosed(webSocketSession, CloseStatus.SERVER_ERROR);

        assertEquals(ConnectionState.FAILED, reconnectDisabled.getState());
        verify(metrics).connectionClosed(CloseStatus.SERVER_ERROR.getCode());
    }

    @Test
    void afterConnectionClosed_whileDraining_doesNotReconnect() throws Exception {
        setState(ConnectionState.DRAINING);

        handler.afterConnectionClosed(webSocketSession, CloseStatus.NORMAL);

        assertEquals(ConnectionState.DRAINING, handler.getState());
        verify(metrics).connectionClosed(CloseStatus.NORMAL.getCode());
    }

    @Test
    void handleMessage_connectionEstablished_setsConnectionId() throws Exception {
        ConnectionEstablished established = ConnectionEstablished.builder()
            .connectionId("conn-123")
            .serverVersion("1.0.0")
            .maxConcurrentSessions(5)
            .build();

        invokeHandleMessage(established);

        assertEquals("conn-123", handler.getConnectionId());
    }

    @Test
    void handleMessage_sessionCancelled_cleansUpAndDelegates() throws Exception {
        SessionCancelled cancelled = SessionCancelled.builder()
            .sessionId("session-2")
            .reason("cancelled by user")
            .build();

        invokeHandleMessage(cancelled);

        verify(mcpServerManager).destroySessionManager("session-2");
        verify(agentApplicationContext).handleSessionCancelled("session-2", "cancelled by user");
    }

    @Test
    void handleMessage_errorMessageWithSession_cleansUpAndDelegatesError() throws Exception {
        ErrorMessage error = ErrorMessage.builder()
            .sessionId("session-3")
            .code(ErrorCode.TOOL_EXECUTION_FAILED)
            .message("tool failed")
            .build();

        invokeHandleMessage(error);

        verify(mcpServerManager).destroySessionManager("session-3");
        verify(agentApplicationContext).handleSessionError(eq("session-3"), any(RuntimeException.class));
    }

    @Test
    void handleMessage_sessionResult_cleansUpDelegatesAndRecordsMetrics() throws Exception {
        SessionResult result = SessionResult.builder()
            .sessionId("session-4")
            .success(true)
            .totalDurationMs(123L)
            .content(objectMapper.createObjectNode().put("ok", true))
            .build();

        invokeHandleMessage(result);

        verify(mcpServerManager).destroySessionManager("session-4");
        verify(agentApplicationContext).handleSessionResult(result);
        verify(metrics).sessionCompleted(true, 123L);
    }
}
