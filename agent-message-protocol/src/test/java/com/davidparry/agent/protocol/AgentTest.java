package com.davidparry.agent.protocol;

import com.davidparry.agent.protocol.dto.AgentType;
import com.davidparry.agent.protocol.mcp.McpConfig;
import com.davidparry.agent.protocol.mcp.StdioServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Agent record.
 */
class AgentTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testAgentCreation() {
        Agent agent = new Agent(
                "test-agent",
                "Test description",
                AgentType.DIAGNOSTICIAN,
                "Test instructions",
                "{\"mcpServers\": {}}",
                null,
                List.of("tool1", "tool2"),
                "{\"type\": \"object\"}",
                null
        );

        assertEquals("test-agent", agent.name());
        assertEquals("Test description", agent.description());
        assertEquals(AgentType.DIAGNOSTICIAN, agent.type());
        assertEquals("Test instructions", agent.instructions());
        assertEquals("{\"mcpServers\": {}}", agent.mcpServers());
        assertNull(agent.mcpConfig());
        assertEquals(2, agent.tools().size());
        assertEquals("tool1", agent.tools().get(0));
        assertEquals("{\"type\": \"object\"}", agent.outputSchema());
        assertNull(agent.nextAgent());
    }

    @Test
    void testAgentWithNullTools() {
        Agent agent = new Agent(
                "test-agent",
                "Test description",
                AgentType.ANALYST,
                "Instructions",
                null,
                null,
                null,
                null,
                null
        );

        assertNotNull(agent.tools());
        assertTrue(agent.tools().isEmpty());
    }

    @Test
    void testWithName() {
        Agent original = new Agent(
                null,
                "Description",
                AgentType.ENGINEER,
                "Instructions",
                "mcpServers",
                null,
                List.of("tool1"),
                "schema",
                "next-agent"
        );

        Agent withName = original.withName("new-name");

        assertEquals("new-name", withName.name());
        assertEquals(original.description(), withName.description());
        assertEquals(original.type(), withName.type());
        assertEquals(original.instructions(), withName.instructions());
        assertEquals(original.mcpServers(), withName.mcpServers());
        assertEquals(original.tools(), withName.tools());
        assertEquals(original.outputSchema(), withName.outputSchema());
        assertEquals(original.nextAgent(), withName.nextAgent());
    }

    @Test
    void testWithMcpConfig() {
        Agent original = new Agent(
                "agent-name",
                "Description",
                AgentType.REVIEWER,
                "Instructions",
                "mcpServers",
                null,
                List.of("tool1"),
                "schema",
                "next-agent"
        );

        McpConfig mcpConfig = new McpConfig(Map.of(
                "server1", new StdioServer("java", List.of("-jar", "test.jar"), Map.of())
        ));

        Agent withConfig = original.withMcpConfig(mcpConfig);

        assertEquals(original.name(), withConfig.name());
        assertEquals(original.description(), withConfig.description());
        assertNotNull(withConfig.mcpConfig());
        assertEquals(mcpConfig, withConfig.mcpConfig());
        assertEquals(original.nextAgent(), withConfig.nextAgent());
    }

    @Test
    void testHasMcpServersWithValidString() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                "{\"mcpServers\": {}}",
                null,
                List.of(),
                null,
                null
        );

        assertTrue(agent.hasMcpServers());
    }

    @Test
    void testHasMcpServersWithNull() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                null
        );

        assertFalse(agent.hasMcpServers());
    }

    @Test
    void testHasMcpServersWithEmptyString() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                "",
                null,
                List.of(),
                null,
                null
        );

        assertFalse(agent.hasMcpServers());
    }

    @Test
    void testHasMcpServersWithWhitespaceOnly() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                "   ",
                null,
                List.of(),
                null,
                null
        );

        assertFalse(agent.hasMcpServers());
    }

    @Test
    void testHasMcpConfigWithValidConfig() {
        McpConfig mcpConfig = new McpConfig(Map.of(
                "server1", new StdioServer("java", List.of("-jar", "test.jar"), Map.of())
        ));

        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                mcpConfig,
                List.of(),
                null,
                null
        );

        assertTrue(agent.hasMcpConfig());
    }

    @Test
    void testHasMcpConfigWithNull() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                null
        );

        assertFalse(agent.hasMcpConfig());
    }

    @Test
    void testHasMcpConfigWithEmptyServers() {
        McpConfig mcpConfig = new McpConfig(Map.of());

        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                mcpConfig,
                List.of(),
                null,
                null
        );

        assertFalse(agent.hasMcpConfig());
    }

    @Test
    void testHasToolsWithTools() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of("tool1", "tool2"),
                null,
                null
        );

        assertTrue(agent.hasTools());
    }

    @Test
    void testHasToolsWithEmptyList() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                null
        );

        assertFalse(agent.hasTools());
    }

    @Test
    void testHasToolsWithNull() {
        // Note: compact constructor converts null to empty list
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                null,
                null,
                null
        );

        assertFalse(agent.hasTools());
    }

    @Test
    void testHasOutputSchemaWithValidSchema() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                "{\"type\": \"object\"}",
                null
        );

        assertTrue(agent.hasOutputSchema());
    }

    @Test
    void testHasOutputSchemaWithNull() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                null
        );

        assertFalse(agent.hasOutputSchema());
    }

    @Test
    void testHasOutputSchemaWithEmptyString() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                "",
                null
        );

        assertFalse(agent.hasOutputSchema());
    }

    @Test
    void testHasOutputSchemaWithWhitespaceOnly() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                "   ",
                null
        );

        assertFalse(agent.hasOutputSchema());
    }

    @Test
    void testAgentJsonDeserialization() throws Exception {
        String json = """
                {
                    "description": "Test agent description",
                    "type": "DIAGNOSTICIAN",
                    "instructions": "Do something",
                    "mcpServers": "{\\"mcpServers\\": {}}",
                    "tools": ["tool1", "tool2"],
                    "output_schema": "{\\"type\\": \\"object\\"}"
                }
                """;

        Agent agent = objectMapper.readValue(json, Agent.class);

        assertEquals("Test agent description", agent.description());
        assertEquals(AgentType.DIAGNOSTICIAN, agent.type());
        assertEquals("Do something", agent.instructions());
        assertEquals("{\"mcpServers\": {}}", agent.mcpServers());
        assertEquals(2, agent.tools().size());
        assertEquals("{\"type\": \"object\"}", agent.outputSchema());
    }

    @Test
    void testAgentJsonDeserializationWithNullTools() throws Exception {
        String json = """
                {
                    "description": "Test agent",
                    "type": "ANALYST",
                    "instructions": "Instructions",
                    "tools": null
                }
                """;

        Agent agent = objectMapper.readValue(json, Agent.class);

        assertNotNull(agent.tools());
        assertTrue(agent.tools().isEmpty());
    }

    // Additional tests to cover all branches

    @Test
    void testHasToolsWithNonNullNonEmptyList() {
        // Test the case where tools != null AND !tools.isEmpty()
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of("tool1"),
                null,
                null
        );

        assertTrue(agent.hasTools());
    }

    @Test
    void testHasToolsWithNonNullEmptyList() {
        // Test the case where tools != null but tools.isEmpty()
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                null
        );

        assertFalse(agent.hasTools());
    }

    @Test
    void testAgentRecordAccessors() {
        // Test all record accessors
        Agent agent = new Agent(
                "name",
                "description",
                AgentType.DIAGNOSTICIAN,
                "instructions",
                "mcpServers",
                null,
                List.of("tool1"),
                "outputSchema",
                "nextAgent"
        );

        assertEquals("name", agent.name());
        assertEquals("description", agent.description());
        assertEquals(AgentType.DIAGNOSTICIAN, agent.type());
        assertEquals("instructions", agent.instructions());
        assertEquals("mcpServers", agent.mcpServers());
        assertNull(agent.mcpConfig());
        assertEquals(1, agent.tools().size());
        assertEquals("outputSchema", agent.outputSchema());
        assertEquals("nextAgent", agent.nextAgent());
    }

    @Test
    void testHasNextAgentWithValidAgent() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                "next-agent-name"
        );

        assertTrue(agent.hasNextAgent());
    }

    @Test
    void testHasNextAgentWithNull() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                null
        );

        assertFalse(agent.hasNextAgent());
    }

    @Test
    void testHasNextAgentWithEmptyString() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                ""
        );

        assertFalse(agent.hasNextAgent());
    }

    @Test
    void testHasNextAgentWithWhitespaceOnly() {
        Agent agent = new Agent(
                "agent",
                "desc",
                AgentType.ANALYST,
                "instructions",
                null,
                null,
                List.of(),
                null,
                "   "
        );

        assertFalse(agent.hasNextAgent());
    }

    @Test
    void testAgentJsonDeserializationWithNextAgent() throws Exception {
        String json = """
                {
                    "description": "Test agent description",
                    "type": "DIAGNOSTICIAN",
                    "instructions": "Do something",
                    "tools": ["tool1"],
                    "next_agent": "another-agent"
                }
                """;

        Agent agent = objectMapper.readValue(json, Agent.class);

        assertEquals("Test agent description", agent.description());
        assertEquals(AgentType.DIAGNOSTICIAN, agent.type());
        assertEquals("another-agent", agent.nextAgent());
        assertTrue(agent.hasNextAgent());
    }
}
