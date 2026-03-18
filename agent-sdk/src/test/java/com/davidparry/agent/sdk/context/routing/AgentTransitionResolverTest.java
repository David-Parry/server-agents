package com.davidparry.agent.sdk.context.routing;

import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.AgentGraph;
import com.davidparry.agent.protocol.AgentTransitionEdge;
import com.davidparry.agent.protocol.SessionResult;
import com.davidparry.agent.protocol.dto.AgentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentTransitionResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentTransitionResolver resolver = new AgentTransitionResolver(
            new SimpleOutputSchemaValidator(objectMapper),
            new SimpleEdgeConditionEvaluator()
    );

    @Test
    void routesUsingGraphEdgeWhenConditionMatches() throws Exception {
        Agent agent = new Agent(
                "jira_agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                """
                {"properties":{"status":{"type":"string"}}}
                """,
                new AgentGraph(List.of(
                        new AgentTransitionEdge("$status == 'design_complete'", "coding_agent"),
                        new AgentTransitionEdge("default", "END_CHAIN")
                )),
                null
        );

        SessionResult result = SessionResult.builder()
                .sessionId("s1")
                .success(true)
                .content(objectMapper.readTree("{\"status\":\"design_complete\"}"))
                .build();

        TransitionDecision decision = resolver.resolve(agent, result);
        assertEquals("coding_agent", decision.nextAgentKey());
        assertTrue(decision.schemaValid());
        assertTrue(decision.effectiveSuccess());
        assertTrue(decision.matchedByGraph());
    }

    @Test
    void routesToFailedAgentWhenSchemaInvalidAndEdgeMatches() throws Exception {
        Agent agent = new Agent(
                "jira_agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                """
                {"properties":{"status":{"type":"string"}},"required":["status"]}
                """,
                new AgentGraph(List.of(
                        new AgentTransitionEdge("$schema_valid == false", "FAILED_AGENT"),
                        new AgentTransitionEdge("default", "END_CHAIN")
                )),
                null
        );

        SessionResult result = SessionResult.builder()
                .sessionId("s1")
                .success(true)
                .content(objectMapper.readTree("{\"status\":1}"))
                .build();

        TransitionDecision decision = resolver.resolve(agent, result);
        assertEquals("FAILED_AGENT", decision.nextAgentKey());
        assertFalse(decision.schemaValid());
        assertFalse(decision.effectiveSuccess());
    }

    @Test
    void fallsBackToLegacyNextAgentOnSuccess() throws Exception {
        Agent agent = new Agent(
                "jira_agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                null,
                "coding_agent"
        );

        SessionResult result = SessionResult.builder()
                .sessionId("s1")
                .success(true)
                .content(objectMapper.readTree("{\"status\":\"ok\"}"))
                .build();

        TransitionDecision decision = resolver.resolve(agent, result);
        assertEquals("coding_agent", decision.nextAgentKey());
        assertTrue(decision.effectiveSuccess());
    }

    @Test
    void defaultsToFailedAgentWhenUnsuccessfulWithoutGraph() throws Exception {
        Agent agent = new Agent(
                "jira_agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                null
        );

        SessionResult result = SessionResult.builder()
                .sessionId("s1")
                .success(false)
                .content(objectMapper.readTree("{\"reason\":\"bad\"}"))
                .build();

        TransitionDecision decision = resolver.resolve(agent, result);
        assertEquals("FAILED_AGENT", decision.nextAgentKey());
        assertFalse(decision.effectiveSuccess());
    }
}
