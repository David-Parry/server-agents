package com.davidparry.agent.protocol;

import com.davidparry.agent.protocol.dto.AgentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentGraphTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void deserializesAgentWithGraphEdges() throws Exception {
        String json = """
                {
                  "description": "Graph test agent",
                  "type": "ANALYST",
                  "instructions": "Test graph",
                  "output_schema": "{\\"properties\\":{\\"status\\":{\\"type\\":\\"string\\"}}}",
                  "graph": {
                    "edges": [
                      {"when": "$schema_valid == false", "to": "FAILED_AGENT"},
                      {"when": "$status == 'design_complete'", "to": "coding_agent"},
                      {"when": "default", "to": "END_CHAIN"}
                    ]
                  }
                }
                """;

        Agent agent = objectMapper.readValue(json, Agent.class);

        assertEquals(AgentType.ANALYST, agent.type());
        assertTrue(agent.hasGraph());
        assertEquals(3, agent.graph().edges().size());
        assertEquals("$schema_valid == false", agent.graph().edges().getFirst().when());
        assertEquals("FAILED_AGENT", agent.graph().edges().getFirst().to());
    }

    @Test
    void constructorHandlesNullEdges() {
        AgentGraph graph = new AgentGraph(null);
        assertNotNull(graph.edges());
        assertTrue(graph.edges().isEmpty());
        assertFalse(graph.hasEdges());
    }

    @Test
    void backwardCompatibleConstructorDefaultsGraphToEmpty() {
        Agent agent = new Agent("name", "desc", AgentType.ANALYST, "instructions",
                                null, null, List.of(), null, null);

        assertNotNull(agent.graph());
        assertFalse(agent.hasGraph());
    }
}
