package com.davidparry.agent.protocol.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for McpConfig record.
 */
class McpConfigTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testDefaultConstructor() {
        McpConfig config = new McpConfig();

        assertNotNull(config.mcpServers());
        assertTrue(config.mcpServers().isEmpty());
    }

    @Test
    void testConstructorWithServers() {
        StdioServer server = new StdioServer("java", List.of("-jar", "test.jar"), Map.of());
        McpConfig config = new McpConfig(Map.of("server1", server));

        assertEquals(1, config.mcpServers().size());
        assertTrue(config.mcpServers().containsKey("server1"));
    }

    @Test
    void testHasServersTrue() {
        StdioServer server = new StdioServer("java", List.of("-jar", "test.jar"), Map.of());
        McpConfig config = new McpConfig(Map.of("server1", server));

        assertTrue(config.hasServers());
    }

    @Test
    void testHasServersFalseWithEmptyMap() {
        McpConfig config = new McpConfig(Map.of());

        assertFalse(config.hasServers());
    }

    @Test
    void testHasServersFalseWithNull() {
        McpConfig config = new McpConfig(null);

        assertFalse(config.hasServers());
    }

    @Test
    void testServerCount() {
        StdioServer server1 = new StdioServer("java", List.of("-jar", "test1.jar"), Map.of());
        StdioServer server2 = new StdioServer("node", List.of("server.js"), Map.of());
        McpConfig config = new McpConfig(Map.of("server1", server1, "server2", server2));

        assertEquals(2, config.serverCount());
    }

    @Test
    void testServerCountWithNull() {
        McpConfig config = new McpConfig(null);

        assertEquals(0, config.serverCount());
    }

    @Test
    void testServerCountWithEmptyMap() {
        McpConfig config = new McpConfig(Map.of());

        assertEquals(0, config.serverCount());
    }

    @Test
    void testJsonDeserializationWithStdioServer() throws Exception {
        String json = """
                {
                    "mcpServers": {
                        "local-tools": {
                            "type": "stdio",
                            "command": "npx",
                            "args": ["-y", "@modelcontextprotocol/server-memory"],
                            "env": {"API_KEY": "test-key"}
                        }
                    }
                }
                """;

        McpConfig config = objectMapper.readValue(json, McpConfig.class);

        assertTrue(config.hasServers());
        assertEquals(1, config.serverCount());
        assertTrue(config.mcpServers().get("local-tools") instanceof StdioServer);
        
        StdioServer server = (StdioServer) config.mcpServers().get("local-tools");
        assertEquals("npx", server.command());
        assertEquals(2, server.args().size());
        assertEquals("test-key", server.env().get("API_KEY"));
    }

    @Test
    void testJsonDeserializationWithHttpServer() throws Exception {
        String json = """
                {
                    "mcpServers": {
                        "remote-api": {
                            "type": "http",
                            "url": "https://mcp.example.com/api",
                            "headers": {"Authorization": "Bearer token"}
                        }
                    }
                }
                """;

        McpConfig config = objectMapper.readValue(json, McpConfig.class);

        assertTrue(config.hasServers());
        assertTrue(config.mcpServers().get("remote-api") instanceof HttpServer);
        
        HttpServer server = (HttpServer) config.mcpServers().get("remote-api");
        assertEquals("https://mcp.example.com/api", server.url());
        assertEquals("Bearer token", server.headers().get("Authorization"));
    }

    @Test
    void testJsonDeserializationWithSseServer() throws Exception {
        String json = """
                {
                    "mcpServers": {
                        "sse-server": {
                            "type": "sse",
                            "url": "https://example.com/sse"
                        }
                    }
                }
                """;

        McpConfig config = objectMapper.readValue(json, McpConfig.class);

        assertTrue(config.hasServers());
        assertTrue(config.mcpServers().get("sse-server") instanceof SseServer);
        
        SseServer server = (SseServer) config.mcpServers().get("sse-server");
        assertEquals("https://example.com/sse", server.url());
    }

    @Test
    void testJsonDeserializationWithMultipleServers() throws Exception {
        String json = """
                {
                    "mcpServers": {
                        "stdio-server": {
                            "command": "java",
                            "args": ["-jar", "server.jar"]
                        },
                        "http-server": {
                            "type": "http",
                            "url": "https://api.example.com"
                        },
                        "sse-server": {
                            "type": "sse",
                            "url": "https://sse.example.com"
                        }
                    }
                }
                """;

        McpConfig config = objectMapper.readValue(json, McpConfig.class);

        assertEquals(3, config.serverCount());
        assertTrue(config.mcpServers().get("stdio-server") instanceof StdioServer);
        assertTrue(config.mcpServers().get("http-server") instanceof HttpServer);
        assertTrue(config.mcpServers().get("sse-server") instanceof SseServer);
    }
}
