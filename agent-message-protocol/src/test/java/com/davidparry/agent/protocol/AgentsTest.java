package com.davidparry.agent.protocol;

import com.davidparry.agent.protocol.dto.AgentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Agents record.
 */
class AgentsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testAgentsDefaultConstructor() {
        Agents agents = new Agents();

        assertNull(agents.version());
        assertNotNull(agents.agents());
        assertTrue(agents.agents().isEmpty());
    }

    @Test
    void testAgentsWithVersionAndAgents() {
        Agent agent1 = new Agent(
                "agent1",
                "Description 1",
                AgentType.ANALYST,
                "Instructions 1",
                null,
                null,
                List.of("tool1"),
                null,
                null
        );

        Agent agent2 = new Agent(
                "agent2",
                "Description 2",
                AgentType.ENGINEER,
                "Instructions 2",
                null,
                null,
                List.of("tool2"),
                null,
                "agent1"
        );

        Agents agents = new Agents("1.0", Map.of("agent1", agent1, "agent2", agent2));

        assertEquals("1.0", agents.version());
        assertEquals(2, agents.agents().size());
    }

    @Test
    void testAgentsWithNullAgentsMap() {
        Agents agents = new Agents("1.0", null);

        assertNotNull(agents.agents());
        assertTrue(agents.agents().isEmpty());
    }

    @Test
    void testHasAgentsWithAgents() {
        Agent agent = new Agent(
                "agent1",
                "Description",
                AgentType.ANALYST,
                "Instructions",
                null,
                null,
                List.of(),
                null,
                null
        );

        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        assertTrue(agents.hasAgents());
    }

    @Test
    void testHasAgentsWithEmptyMap() {
        Agents agents = new Agents("1.0", Map.of());

        assertFalse(agents.hasAgents());
    }

    @Test
    void testHasAgentsWithNullMapConvertsToEmpty() {
        // Null is converted to empty map by compact constructor
        Agents agents = new Agents("1.0", null);

        assertFalse(agents.hasAgents());
    }

    @Test
    void testAgentCount() {
        Agent agent1 = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agent agent2 = new Agent("agent2", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);

        Agents agents = new Agents("1.0", Map.of("agent1", agent1, "agent2", agent2));

        assertEquals(2, agents.agentCount());
    }

    @Test
    void testAgentCountWithNullMap() {
        Agents agents = new Agents("1.0", null);

        assertEquals(0, agents.agentCount());
    }

    @Test
    void testAgentCountWithEmptyMap() {
        Agents agents = new Agents("1.0", Map.of());

        assertEquals(0, agents.agentCount());
    }

    @Test
    void testGetAgentFound() {
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        Optional<Agent> result = agents.getAgent("agent1");

        assertTrue(result.isPresent());
        assertEquals("agent1", result.get().name());
    }

    @Test
    void testGetAgentNotFound() {
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        Optional<Agent> result = agents.getAgent("nonexistent");

        assertFalse(result.isPresent());
    }

    @Test
    void testGetAgentWithNullName() {
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        Optional<Agent> result = agents.getAgent(null);

        assertFalse(result.isPresent());
    }

    @Test
    void testGetAgentWithNullAgentsMap() {
        Agents agents = new Agents("1.0", null);

        Optional<Agent> result = agents.getAgent("agent1");

        assertFalse(result.isPresent());
    }

    @Test
    void testGetAgentNames() {
        Agent agent1 = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agent agent2 = new Agent("agent2", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent1, "agent2", agent2));

        Set<String> names = agents.getAgentNames();

        assertEquals(2, names.size());
        assertTrue(names.contains("agent1"));
        assertTrue(names.contains("agent2"));
    }

    @Test
    void testGetAgentNamesWithNullMap() {
        Agents agents = new Agents("1.0", null);

        Set<String> names = agents.getAgentNames();

        assertNotNull(names);
        assertTrue(names.isEmpty());
    }

    @Test
    void testGetAgentNamesReturnsUnmodifiableSet() {
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        Set<String> names = agents.getAgentNames();

        assertThrows(UnsupportedOperationException.class, () -> names.add("newAgent"));
    }

    @Test
    void testHasAgentTrue() {
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        assertTrue(agents.hasAgent("agent1"));
    }

    @Test
    void testHasAgentFalse() {
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        assertFalse(agents.hasAgent("nonexistent"));
    }

    @Test
    void testHasAgentWithNullName() {
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        assertFalse(agents.hasAgent(null));
    }

    @Test
    void testHasAgentWithNullAgentsMap() {
        Agents agents = new Agents("1.0", null);

        assertFalse(agents.hasAgent("agent1"));
    }

    @Test
    void testAgentsJsonDeserialization() throws Exception {
        String json = """
                {
                    "version": "1.0",
                    "agents": {
                        "test_agent": {
                            "description": "Test agent",
                            "type": "DIAGNOSTICIAN",
                            "instructions": "Do something",
                            "tools": ["tool1"]
                        }
                    }
                }
                """;

        Agents agents = objectMapper.readValue(json, Agents.class);

        assertEquals("1.0", agents.version());
        assertEquals(1, agents.agentCount());
        assertTrue(agents.hasAgent("test_agent"));
    }

    @Test
    void testAgentsJsonDeserializationWithNullAgents() throws Exception {
        String json = """
                {
                    "version": "1.0",
                    "agents": null
                }
                """;

        Agents agents = objectMapper.readValue(json, Agents.class);

        assertEquals("1.0", agents.version());
        assertNotNull(agents.agents());
        assertTrue(agents.agents().isEmpty());
    }

    // Additional tests to cover all branches

    @Test
    void testHasAgentsWithNonNullNonEmptyMap() {
        // This tests the case where agents != null AND !agents.isEmpty()
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        // Both conditions should be true
        assertTrue(agents.hasAgents());
    }

    @Test
    void testAgentCountWithNonNullMap() {
        // This tests the case where agents != null
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        assertEquals(1, agents.agentCount());
    }

    @Test
    void testGetAgentWithNullAgentsMapAndNullName() {
        // Test both null checks in getAgent
        Agents agents = new Agents("1.0", null);

        // agents is null
        assertFalse(agents.getAgent("test").isPresent());
        // name is null
        assertFalse(agents.getAgent(null).isPresent());
    }

    @Test
    void testGetAgentNamesWithNonNullMap() {
        // Test the case where agents != null
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        Set<String> names = agents.getAgentNames();
        assertEquals(1, names.size());
        assertTrue(names.contains("agent1"));
    }

    @Test
    void testHasAgentWithAllConditions() {
        // Test all branches of hasAgent: agents != null && name != null && agents.containsKey(name)
        Agent agent = new Agent("agent1", "desc", AgentType.ANALYST, "inst", null, null, List.of(), null, null);
        Agents agents = new Agents("1.0", Map.of("agent1", agent));

        // All conditions true
        assertTrue(agents.hasAgent("agent1"));
        
        // agents != null, name != null, but key doesn't exist
        assertFalse(agents.hasAgent("nonexistent"));
        
        // agents != null, name is null
        assertFalse(agents.hasAgent(null));
    }

    @Test
    void testHasAgentsWithNullAgentsMap() {
        // Explicitly test the null agents case for hasAgents
        Agents agents = new Agents("1.0", null);
        assertFalse(agents.hasAgents());
    }

    @Test
    void testAgentsJsonDeserializationWithNextAgent() throws Exception {
        String json = """
                {
                    "version": "1.0",
                    "agents": {
                        "first_agent": {
                            "description": "First agent",
                            "type": "DIAGNOSTICIAN",
                            "instructions": "Do something",
                            "tools": ["tool1"],
                            "next_agent": "second_agent"
                        },
                        "second_agent": {
                            "description": "Second agent",
                            "type": "ANALYST",
                            "instructions": "Analyze",
                            "tools": ["tool2"]
                        }
                    }
                }
                """;

        Agents agents = objectMapper.readValue(json, Agents.class);

        assertEquals("1.0", agents.version());
        assertEquals(2, agents.agentCount());
        
        Agent firstAgent = agents.getAgent("first_agent").orElseThrow();
        assertEquals("second_agent", firstAgent.nextAgent());
        assertTrue(firstAgent.hasNextAgent());
        
        Agent secondAgent = agents.getAgent("second_agent").orElseThrow();
        assertNull(secondAgent.nextAgent());
        assertFalse(secondAgent.hasNextAgent());
    }
}
