package com.davidparry.agent.sdk.mcp;

import com.davidparry.agent.protocol.mcp.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for McpConfigLoader to verify JSON parsing and server type detection.
 */
class McpConfigLoaderTest {
    
    private McpConfigLoader loader;
    
    @BeforeEach
    void setUp() {
        loader = new McpConfigLoader(new ObjectMapper());
    }
    
    @Test
    void shouldParseStdioServerWithExplicitType() throws IOException {
        String json = """
            {
              "mcpServers": {
                "local-tools": {
                  "type": "stdio",
                  "command": "npx",
                  "args": ["-y", "@modelcontextprotocol/server-memory"],
                  "env": { "API_KEY": "test-key" }
                }
              }
            }
            """;
        
        McpConfig config = loader.loadFromString(json);
        
        assertNotNull(config);
        assertEquals(1, config.serverCount());
        
        McpServer server = config.mcpServers().get("local-tools");
        assertInstanceOf(StdioServer.class, server);
        
        StdioServer stdioServer = (StdioServer) server;
        assertEquals("local-tools", stdioServer.name());
        assertEquals("stdio", stdioServer.type());
        assertEquals("npx", stdioServer.command());
        assertEquals(2, stdioServer.args().size());
        assertEquals("-y", stdioServer.args().get(0));
        assertEquals("@modelcontextprotocol/server-memory", stdioServer.args().get(1));
        assertEquals("test-key", stdioServer.env().get("API_KEY"));
    }
    
    @Test
    void shouldParseStdioServerWithoutExplicitType() throws IOException {
        // When no type is specified but command is present, it should be treated as STDIO
        String json = """
            {
              "mcpServers": {
                "local-tools": {
                  "command": "npx",
                  "args": ["-y", "@modelcontextprotocol/server-memory"]
                }
              }
            }
            """;
        
        McpConfig config = loader.loadFromString(json);
        
        McpServer server = config.mcpServers().get("local-tools");
        assertInstanceOf(StdioServer.class, server);
        
        StdioServer stdioServer = (StdioServer) server;
        assertEquals("npx", stdioServer.command());
    }
    
    @Test
    void shouldParseHttpServer() throws IOException {
        String json = """
            {
              "mcpServers": {
                "remote-api": {
                  "type": "http",
                  "url": "https://mcp.sentry.dev/mcp"
                }
              }
            }
            """;
        
        McpConfig config = loader.loadFromString(json);
        
        McpServer server = config.mcpServers().get("remote-api");
        assertInstanceOf(HttpServer.class, server);
        
        HttpServer httpServer = (HttpServer) server;
        assertEquals("remote-api", httpServer.name());
        assertEquals("http", httpServer.type());
        assertEquals("https://mcp.sentry.dev/mcp", httpServer.url());
    }
    
    @Test
    void shouldParseSseServer() throws IOException {
        String json = """
            {
              "mcpServers": {
                "legacy-remote": {
                  "type": "sse",
                  "url": "https://example.com/sse"
                }
              }
            }
            """;
        
        McpConfig config = loader.loadFromString(json);
        
        McpServer server = config.mcpServers().get("legacy-remote");
        assertInstanceOf(SseServer.class, server);
        
        SseServer sseServer = (SseServer) server;
        assertEquals("legacy-remote", sseServer.name());
        assertEquals("sse", sseServer.type());
        assertEquals("https://example.com/sse", sseServer.url());
    }
    
    @Test
    void shouldParseMultipleServerTypes() throws IOException {
        String json = """
            {
              "mcpServers": {
                "local-tools": {
                  "type": "stdio",
                  "command": "npx",
                  "args": ["-y", "@modelcontextprotocol/server-memory"],
                  "env": { "API_KEY": "your-key" }
                },
                "remote-api": {
                  "type": "http",
                  "url": "https://mcp.sentry.dev/mcp"
                },
                "legacy-remote": {
                  "type": "sse",
                  "url": "https://example.com/sse"
                }
              }
            }
            """;
        
        McpConfig config = loader.loadFromString(json);
        
        assertNotNull(config);
        assertEquals(3, config.serverCount());
        
        assertInstanceOf(StdioServer.class, config.mcpServers().get("local-tools"));
        assertInstanceOf(HttpServer.class, config.mcpServers().get("remote-api"));
        assertInstanceOf(SseServer.class, config.mcpServers().get("legacy-remote"));
    }
    
    @Test
    void shouldParseHttpServerWithHeaders() throws IOException {
        String json = """
            {
              "mcpServers": {
                "authenticated-api": {
                  "type": "http",
                  "url": "https://api.example.com/mcp",
                  "headers": {
                    "Authorization": "Bearer token123",
                    "X-Custom-Header": "custom-value"
                  }
                }
              }
            }
            """;
        
        McpConfig config = loader.loadFromString(json);
        
        HttpServer httpServer = (HttpServer) config.mcpServers().get("authenticated-api");
        assertEquals(2, httpServer.headers().size());
        assertEquals("Bearer token123", httpServer.headers().get("Authorization"));
        assertEquals("custom-value", httpServer.headers().get("X-Custom-Header"));
    }
    
    @Test
    void shouldParseStreamableHttpType() throws IOException {
        String json = """
            {
              "mcpServers": {
                "streamable-api": {
                  "type": "streamable-http",
                  "url": "https://api.example.com/mcp"
                }
              }
            }
            """;
        
        McpConfig config = loader.loadFromString(json);
        
        McpServer server = config.mcpServers().get("streamable-api");
        assertInstanceOf(HttpServer.class, server);
        
        HttpServer httpServer = (HttpServer) server;
        assertEquals("streamable-http", httpServer.type());
    }
    
    @Test
    void shouldThrowExceptionForMissingCommand() {
        String json = """
            {
              "mcpServers": {
                "invalid-stdio": {
                  "type": "stdio"
                }
              }
            }
            """;
        
        assertThrows(IOException.class, () -> loader.loadFromString(json));
    }
    
    @Test
    void shouldThrowExceptionForMissingUrl() {
        String json = """
            {
              "mcpServers": {
                "invalid-http": {
                  "type": "http"
                }
              }
            }
            """;
        
        assertThrows(IOException.class, () -> loader.loadFromString(json));
    }
    
    @Test
    void shouldDefaultToHttpWhenOnlyUrlPresent() throws IOException {
        String json = """
            {
              "mcpServers": {
                "url-only": {
                  "url": "https://example.com/mcp"
                }
              }
            }
            """;
        
        McpConfig config = loader.loadFromString(json);
        
        McpServer server = config.mcpServers().get("url-only");
        assertInstanceOf(HttpServer.class, server);
    }
}
