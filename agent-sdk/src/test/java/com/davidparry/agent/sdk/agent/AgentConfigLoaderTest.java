package com.davidparry.agent.sdk.agent;

import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.Agents;
import com.davidparry.agent.protocol.dto.AgentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgentConfigLoader.
 */
class AgentConfigLoaderTest {
    
    private AgentConfigLoader loader;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        loader = new AgentConfigLoader(new ObjectMapper());
    }
    
    @Test
    void loadFromString_withValidYaml_parsesAgents() throws IOException {
        String yaml = """
            version: "1.0"
            
            agents:
              test_agent:
                description: "A test agent"
                type: "ANALYST"
                instructions: |
                  This is a test instruction.
                  It has multiple lines.
                mcpServers: |
                  {
                    "mcpServers": {
                      "test-server": {
                        "command": "java",
                        "args": ["-jar", "test.jar"],
                        "env": {
                          "API_KEY": "test-key"
                        }
                      }
                    }
                  }
                tools: ["test-server.tool1", "test-server.tool2"]
                output_schema: |
                  {
                    "properties": {
                      "success": {
                        "type": "boolean"
                      }
                    }
                  }
            """;
        
        Agents agents = loader.loadFromString(yaml);
        
        assertNotNull(agents);
        assertEquals("1.0", agents.version());
        assertTrue(agents.hasAgents());
        assertEquals(1, agents.agentCount());
        
        Agent agent = agents.getAgent("test_agent").orElseThrow();
        assertEquals("test_agent", agent.name());
        assertEquals("A test agent", agent.description());
        assertEquals(AgentType.ANALYST, agent.type());
        assertTrue(agent.instructions().contains("This is a test instruction"));
        assertTrue(agent.hasTools());
        assertEquals(2, agent.tools().size());
        assertTrue(agent.hasOutputSchema());
        
        // Verify MCP config was parsed
        assertTrue(agent.hasMcpConfig());
        assertEquals(1, agent.mcpConfig().serverCount());
        assertTrue(agent.mcpConfig().mcpServers().containsKey("test-server"));
    }
    
    @Test
    void loadFromString_withMultipleAgents_parsesAll() throws IOException {
        String yaml = """
            version: "1.0"
            
            agents:
              agent_one:
                description: "First agent"
                type: "ANALYST"
                instructions: "Do something"
              
              agent_two:
                description: "Second agent"
                type: "ENGINEER"
                instructions: "Do something else"
            """;
        
        Agents agents = loader.loadFromString(yaml);
        
        assertNotNull(agents);
        assertEquals(2, agents.agentCount());
        assertTrue(agents.hasAgent("agent_one"));
        assertTrue(agents.hasAgent("agent_two"));
        
        assertEquals("First agent", agents.getAgent("agent_one").orElseThrow().description());
        assertEquals("Second agent", agents.getAgent("agent_two").orElseThrow().description());
    }
    
    @Test
    void loadFromString_withoutMcpServers_parsesSuccessfully() throws IOException {
        String yaml = """
            version: "1.0"
            
            agents:
              simple_agent:
                description: "A simple agent without MCP"
                type: "REVIEWER"
                instructions: "Just do it"
            """;
        
        Agents agents = loader.loadFromString(yaml);
        
        assertNotNull(agents);
        Agent agent = agents.getAgent("simple_agent").orElseThrow();
        assertFalse(agent.hasMcpServers());
        assertFalse(agent.hasMcpConfig());
    }
    
    @Test
    void loadFromPath_withValidFile_parsesAgents() throws IOException {
        String yaml = """
            version: "2.0"
            
            agents:
              file_agent:
                description: "Agent from file"
                type: "ENGINEER"
                instructions: "Read from file"
            """;
        
        Path configFile = tempDir.resolve("agent.yml");
        Files.writeString(configFile, yaml);
        
        Agents agents = loader.loadFromPath(configFile);
        
        assertNotNull(agents);
        assertEquals("2.0", agents.version());
        assertTrue(agents.hasAgent("file_agent"));
    }
    
    @Test
    void loadFromPath_withNonExistentFile_throwsException() {
        Path nonExistent = tempDir.resolve("does-not-exist.yml");
        
        assertThrows(IOException.class, () -> loader.loadFromPath(nonExistent));
    }
    
    @Test
    void loadFromString_withMissingInstructions_throwsException() {
        String yaml = """
            version: "1.0"
            
            agents:
              invalid_agent:
                description: "Agent without instructions"
                type: "INVALID"
            """;
        
        assertThrows(IOException.class, () -> loader.loadFromString(yaml));
    }
    
    @Test
    void loadFromString_withEmptyAgents_returnsEmptyAgents() throws IOException {
        String yaml = """
            version: "1.0"
            
            agents: {}
            """;
        
        Agents agents = loader.loadFromString(yaml);
        
        assertNotNull(agents);
        assertFalse(agents.hasAgents());
        assertEquals(0, agents.agentCount());
    }
    
    @Test
    void getAgentNames_returnsAllNames() throws IOException {
        String yaml = """
            version: "1.0"
            
            agents:
              alpha:
                description: "Alpha"
                type: "ANALYST"
                instructions: "Alpha instructions"
              beta:
                description: "Beta"
                type: "ENGINEER"
                instructions: "Beta instructions"
              gamma:
                description: "Gamma"
                type: "REVIEWER"
                instructions: "Gamma instructions"
            """;
        
        Agents agents = loader.loadFromString(yaml);
        
        assertEquals(3, agents.getAgentNames().size());
        assertTrue(agents.getAgentNames().contains("alpha"));
        assertTrue(agents.getAgentNames().contains("beta"));
        assertTrue(agents.getAgentNames().contains("gamma"));
    }
    
    @Test
    void loadFromString_withHttpMcpServer_parsesCorrectly() throws IOException {
        String yaml = """
            version: "1.0"
            
            agents:
              http_agent:
                description: "Agent with HTTP MCP server"
                type: "ANALYST"
                instructions: "Use HTTP server"
                mcpServers: |
                  {
                    "mcpServers": {
                      "remote-api": {
                        "type": "http",
                        "url": "https://api.example.com/mcp"
                      }
                    }
                  }
                tools: ["remote-api.fetch_data"]
            """;
        
        Agents agents = loader.loadFromString(yaml);
        
        Agent agent = agents.getAgent("http_agent").orElseThrow();
        assertTrue(agent.hasMcpConfig());
        assertTrue(agent.mcpConfig().mcpServers().containsKey("remote-api"));
    }

    @Test
    void loadFromString_withGraphTransitions_parsesAndValidates() throws IOException {
        String yaml = """
            version: "1.0"

            agents:
              jira_agent:
                description: "jira"
                type: "ANALYST"
                instructions: "analyze"
                output_schema: |
                  { "properties": { "status": { "type": "string" } } }
                graph:
                  edges:
                    - when: "$status == 'design_complete'"
                      to: "coding_agent"
                    - when: "default"
                      to: "END_CHAIN"
              coding_agent:
                description: "coding"
                type: "ENGINEER"
                instructions: "code"
            """;

        Agents agents = loader.loadFromString(yaml);
        Agent jiraAgent = agents.getAgent("jira_agent").orElseThrow();
        assertTrue(jiraAgent.hasGraph());
        assertEquals(2, jiraAgent.graph().edges().size());
    }

    @Test
    void loadFromString_withGraphUnknownTarget_throwsException() {
        String yaml = """
            version: "1.0"

            agents:
              jira_agent:
                description: "jira"
                type: "ANALYST"
                instructions: "analyze"
                graph:
                  edges:
                    - when: "default"
                      to: "missing_agent"
            """;

        assertThrows(IOException.class, () -> loader.loadFromString(yaml));
    }

    @Test
    void loadFromString_withMultipleDefaultEdges_throwsException() {
        String yaml = """
            version: "1.0"

            agents:
              jira_agent:
                description: "jira"
                type: "ANALYST"
                instructions: "analyze"
                graph:
                  edges:
                    - when: "default"
                      to: "END_CHAIN"
                    - when: "default"
                      to: "FAILED_AGENT"
            """;

        assertThrows(IOException.class, () -> loader.loadFromString(yaml));
    }

    @Test
    void loadFromString_withMalformedGraphCondition_throwsException() {
        String yaml = """
            version: "1.0"

            agents:
              jira_agent:
                description: "jira"
                type: "ANALYST"
                instructions: "analyze"
                graph:
                  edges:
                    - when: "$status = 'bad_syntax'"
                      to: "END_CHAIN"
            """;

        assertThrows(IOException.class, () -> loader.loadFromString(yaml));
    }
    
    @Test
    void loadFromClasspath_withExampleAgentYml_parsesCorrectly() throws IOException {
        Agents agents = loader.loadFromClasspath("agent.yml");
        
        assertNotNull(agents);
        assertEquals("1.0", agents.version());
        assertTrue(agents.hasAgents());
        assertEquals(2, agents.agentCount());
        
        // Verify failure_diagnostician agent
        assertTrue(agents.hasAgent("failure_diagnostician"));
        Agent diagnostician = agents.getAgent("failure_diagnostician").orElseThrow();
        assertEquals("failure_diagnostician", diagnostician.name());
        assertEquals(AgentType.DIAGNOSTICIAN, diagnostician.type());
        assertTrue(diagnostician.hasMcpConfig());
        assertTrue(diagnostician.mcpConfig().mcpServers().containsKey("internal-server"));
        assertTrue(diagnostician.hasTools());
        assertTrue(diagnostician.hasOutputSchema());
        
        // Verify jira_agent
        assertTrue(agents.hasAgent("jira_agent"));
        Agent jiraAgent = agents.getAgent("jira_agent").orElseThrow();
        assertEquals("jira_agent", jiraAgent.name());
        assertEquals(AgentType.ANALYST, jiraAgent.type());
        assertTrue(jiraAgent.hasMcpConfig());
        assertTrue(jiraAgent.hasTools());
        assertTrue(jiraAgent.tools().contains("internal-server.jira_get_issue"));
    }
}
