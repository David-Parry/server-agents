package com.davidparry.agent.app.controller;

import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.dto.AgentType;
import com.davidparry.agent.sdk.context.ActivationResult;
import com.davidparry.agent.sdk.context.AgentApplicationContext;
import com.davidparry.agent.sdk.context.AgentSession;
import com.davidparry.agent.sdk.context.ChainedSessionResult;
import com.davidparry.agent.sdk.context.State;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    private AgentApplicationContext context;
    private AgentController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        context = mock(AgentApplicationContext.class);
        objectMapper = new ObjectMapper();
        controller = new AgentController(context, objectMapper);
    }

    @Test
    void listAgentsReturnsConfiguredKeys() {
        when(context.getAgentKeys()).thenReturn(Set.of("analyst", "engineer"));

        var response = controller.listAgents();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void getAgentReturnsNotFoundForUnknownAgent() {
        when(context.getAgent("missing")).thenReturn(Optional.empty());

        var response = controller.getAgent("missing");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void getAgentReturnsDetailsForConfiguredAgent() {
        Agent agent = new Agent(
            "analyst",
            "Analyzes incidents",
            AgentType.ANALYST,
            "Investigate the logs",
            null,
            null,
            List.of("terminal-list_files"),
            "{\"type\":\"object\"}",
            null
        );
        when(context.getAgent("analyst")).thenReturn(Optional.of(agent));

        var response = controller.getAgent("analyst");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("analyst", response.getBody().get("name"));
        assertEquals(true, response.getBody().get("hasTools"));
    }

    @Test
    void activateAgentReturnsServiceUnavailableWhenDisconnected() {
        when(context.hasAgent("analyst")).thenReturn(true);
        when(context.isConnected()).thenReturn(false);

        var response = controller.activateAgent("analyst", Map.of("incidentId", "INC-101"));

        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().get("connected"));
    }

    @Test
    void activateAgentReturnsAcceptedWhenActivationStarts() {
        when(context.hasAgent("analyst")).thenReturn(true);
        when(context.isConnected()).thenReturn(true);
        when(context.activateAgent(anyString(), org.mockito.ArgumentMatchers.<JsonNode>any(), any())).thenReturn(new ActivationResult(
            "sess-123",
            CompletableFuture.completedFuture(
                new ChainedSessionResult("sess-123", List.of(), ChainedSessionResult.ChainStatus.COMPLETED, null, 10L)
            )
        ));

        var response = controller.activateAgent("analyst", Map.of("incidentId", "INC-102"));

        assertEquals(202, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("sess-123", response.getBody().get("sessionId"));
        assertEquals("activated", response.getBody().get("status"));
    }

    @Test
    void activateAgentReturnsNotFoundWhenAgentMissing() {
        when(context.hasAgent("missing")).thenReturn(false);
        when(context.getAgentKeys()).thenReturn(Set.of("analyst"));

        var response = controller.activateAgent("missing", Map.of("k", "v"));

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void activateAgentReturnsBadRequestOnInvalidArguments() {
        when(context.hasAgent("analyst")).thenReturn(true);
        when(context.isConnected()).thenReturn(true);
        when(context.activateAgent(eq("analyst"), any(JsonNode.class), any()))
            .thenThrow(new IllegalArgumentException("invalid params"));

        var response = controller.activateAgent("analyst", Map.of("k", "v"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("invalid params", response.getBody().get("error"));
    }

    @Test
    void activateAgentReturnsServiceUnavailableOnIllegalState() {
        when(context.hasAgent("analyst")).thenReturn(true);
        when(context.isConnected()).thenReturn(true);
        when(context.activateAgent(eq("analyst"), any(JsonNode.class), any()))
            .thenThrow(new IllegalStateException("not ready"));

        var response = controller.activateAgent("analyst", Map.of("k", "v"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("not ready", response.getBody().get("error"));
    }

    @Test
    void listSessionsReturnsSessionSummaries() {
        AgentSession session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn("sess-1");
        when(session.getAgentKey()).thenReturn("analyst");
        when(session.getState()).thenReturn(State.RUNNING);
        when(session.getDurationMs()).thenReturn(100L);
        when(session.getStartTimeMs()).thenReturn(10L);
        when(context.getActiveSessionCount()).thenReturn(1);
        when(context.isConnected()).thenReturn(true);
        when(context.getActiveSessions()).thenReturn(List.of(session));

        var response = controller.listSessions();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().get("activeSessionCount"));
    }

    @Test
    void getSessionReturnsNotFoundWhenMissing() {
        when(context.getSession("missing")).thenReturn(Optional.empty());
        var response = controller.getSession("missing");
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void getSessionReturnsCompletedSessionDetails() {
        AgentSession session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn("sess-1");
        when(session.getAgentKey()).thenReturn("analyst");
        when(session.getState()).thenReturn(State.COMPLETED);
        when(session.isActive()).thenReturn(false);
        when(session.isCompleted()).thenReturn(true);
        when(session.isFailed()).thenReturn(false);
        when(session.isCancelled()).thenReturn(false);
        when(session.getDurationMs()).thenReturn(50L);
        when(session.getStartTimeMs()).thenReturn(10L);
        when(session.getEndTimeMs()).thenReturn(60L);
        var result = mock(com.davidparry.agent.protocol.SessionResult.class);
        when(result.isSuccess()).thenReturn(true);
        when(result.getContent()).thenReturn(objectMapper.valueToTree("done"));
        when(session.getResult()).thenReturn(result);
        when(context.getSession("sess-1")).thenReturn(Optional.of(session));

        var response = controller.getSession("sess-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("done", ((JsonNode) response.getBody().get("content")).asText());
    }

    @Test
    void cancelSessionCancelsWhenFound() {
        AgentSession session = mock(AgentSession.class);
        when(context.getSession("sess-1")).thenReturn(Optional.of(session));

        var response = controller.cancelSession("sess-1", Map.of("reason", "manual"));

        assertEquals(200, response.getStatusCode().value());
        verify(context).cancelSession("sess-1", "manual");
        assertEquals("cancelled", response.getBody().get("status"));
    }

    @Test
    void cancelSessionUsesDefaultReasonWhenNoBody() {
        AgentSession session = mock(AgentSession.class);
        when(context.getSession("sess-1")).thenReturn(Optional.of(session));

        var response = controller.cancelSession("sess-1", null);

        assertEquals(200, response.getStatusCode().value());
        verify(context).cancelSession("sess-1", "Cancelled via API");
    }

    @Test
    void getStatisticsDelegatesToContext() {
        when(context.getStatistics()).thenReturn(Map.of("connected", true, "agentCount", 2));
        var response = controller.getStatistics();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().get("agentCount"));
    }

    @Test
    void reloadConfigurationsReturnsUpdatedAgentKeys() {
        when(context.getAgentKeys()).thenReturn(Set.of("analyst", "reviewer"));
        var response = controller.reloadConfigurations();
        assertEquals(200, response.getStatusCode().value());
        verify(context).reloadAgentConfigurations();
        assertEquals("reloaded", response.getBody().get("status"));
    }

    @Test
    void getStatusReturnsConnectionSnapshot() {
        when(context.isConnected()).thenReturn(true);
        when(context.getAgentKeys()).thenReturn(Set.of("analyst"));
        when(context.getActiveSessionCount()).thenReturn(3);
        var response = controller.getStatus();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("connected"));
        assertEquals(3, response.getBody().get("activeSessionCount"));
    }
}
