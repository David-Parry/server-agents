package com.davidparry.agent.protocol.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for McpServer implementations (StdioServer, HttpServer, SseServer).
 */
class McpServerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ==================== StdioServer Tests ====================

    @Test
    void testStdioServerFullConstructor() {
        StdioServer server = new StdioServer(
                "server-name",
                "stdio",
                "java",
                List.of("-jar", "test.jar"),
                Map.of("KEY", "value")
        );

        assertEquals("server-name", server.name());
        assertEquals("stdio", server.type());
        assertEquals("java", server.command());
        assertEquals(2, server.args().size());
        assertEquals("value", server.env().get("KEY"));
    }

    @Test
    void testStdioServerSimpleConstructor() {
        StdioServer server = new StdioServer("java", List.of("-jar", "test.jar"), Map.of("KEY", "value"));

        assertNull(server.name());
        assertEquals("stdio", server.type());
        assertEquals("java", server.command());
        assertEquals(2, server.args().size());
    }

    @Test
    void testStdioServerWithNullType() {
        StdioServer server = new StdioServer(null, null, "java", List.of(), Map.of());

        assertEquals("stdio", server.type());
    }

    @Test
    void testStdioServerWithNullArgs() {
        StdioServer server = new StdioServer("name", "stdio", "java", null, Map.of());

        assertNotNull(server.args());
        assertTrue(server.args().isEmpty());
    }

    @Test
    void testStdioServerWithNullEnv() {
        StdioServer server = new StdioServer("name", "stdio", "java", List.of(), null);

        assertNotNull(server.env());
        assertTrue(server.env().isEmpty());
    }

    @Test
    void testStdioServerWithName() {
        StdioServer original = new StdioServer("java", List.of("-jar", "test.jar"), Map.of());
        StdioServer withName = original.withName("new-name");

        assertEquals("new-name", withName.name());
        assertEquals(original.type(), withName.type());
        assertEquals(original.command(), withName.command());
        assertEquals(original.args(), withName.args());
        assertEquals(original.env(), withName.env());
    }

    @Test
    void testStdioServerJsonDeserialization() throws Exception {
        String json = """
                {
                    "type": "stdio",
                    "command": "npx",
                    "args": ["-y", "@modelcontextprotocol/server-memory"],
                    "env": {"API_KEY": "test-key"}
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(StdioServer.class, server);
        StdioServer stdioServer = (StdioServer) server;
        assertEquals("stdio", stdioServer.type());
        assertEquals("npx", stdioServer.command());
        assertEquals(2, stdioServer.args().size());
        assertEquals("test-key", stdioServer.env().get("API_KEY"));
    }

    @Test
    void testStdioServerJsonDeserializationWithoutType() throws Exception {
        // When no type is specified but command is present, should be STDIO
        String json = """
                {
                    "command": "java",
                    "args": ["-jar", "server.jar"]
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(StdioServer.class, server);
    }

    // ==================== HttpServer Tests ====================

    @Test
    void testHttpServerFullConstructor() {
        HttpServer server = new HttpServer(
                "server-name",
                "http",
                "https://api.example.com",
                Map.of("Authorization", "Bearer token"),
                Map.of("KEY", "value")
        );

        assertEquals("server-name", server.name());
        assertEquals("http", server.type());
        assertEquals("https://api.example.com", server.url());
        assertEquals("Bearer token", server.headers().get("Authorization"));
        assertEquals("value", server.env().get("KEY"));
    }

    @Test
    void testHttpServerUrlOnlyConstructor() {
        HttpServer server = new HttpServer("https://api.example.com");

        assertNull(server.name());
        assertEquals("http", server.type());
        assertEquals("https://api.example.com", server.url());
        assertTrue(server.headers().isEmpty());
        assertTrue(server.env().isEmpty());
    }

    @Test
    void testHttpServerUrlAndHeadersConstructor() {
        HttpServer server = new HttpServer("https://api.example.com", Map.of("Auth", "token"));

        assertNull(server.name());
        assertEquals("http", server.type());
        assertEquals("https://api.example.com", server.url());
        assertEquals("token", server.headers().get("Auth"));
    }

    @Test
    void testHttpServerTypeUrlHeadersEnvConstructor() {
        HttpServer server = new HttpServer("streamable-http", "https://api.example.com", 
                Map.of("Auth", "token"), Map.of("KEY", "value"));

        assertNull(server.name());
        assertEquals("streamable-http", server.type());
        assertEquals("https://api.example.com", server.url());
    }

    @Test
    void testHttpServerWithNullType() {
        HttpServer server = new HttpServer(null, null, "https://api.example.com", null, null);

        assertEquals("http", server.type());
    }

    @Test
    void testHttpServerWithNullHeaders() {
        HttpServer server = new HttpServer("name", "http", "https://api.example.com", null, Map.of());

        assertNotNull(server.headers());
        assertTrue(server.headers().isEmpty());
    }

    @Test
    void testHttpServerWithNullEnv() {
        HttpServer server = new HttpServer("name", "http", "https://api.example.com", Map.of(), null);

        assertNotNull(server.env());
        assertTrue(server.env().isEmpty());
    }

    @Test
    void testHttpServerWithName() {
        HttpServer original = new HttpServer("https://api.example.com");
        HttpServer withName = original.withName("new-name");

        assertEquals("new-name", withName.name());
        assertEquals(original.type(), withName.type());
        assertEquals(original.url(), withName.url());
        assertEquals(original.headers(), withName.headers());
        assertEquals(original.env(), withName.env());
    }

    @Test
    void testHttpServerJsonDeserialization() throws Exception {
        String json = """
                {
                    "type": "http",
                    "url": "https://mcp.example.com/api",
                    "headers": {"Authorization": "Bearer token"},
                    "env": {"API_KEY": "key"}
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(HttpServer.class, server);
        HttpServer httpServer = (HttpServer) server;
        assertEquals("http", httpServer.type());
        assertEquals("https://mcp.example.com/api", httpServer.url());
        assertEquals("Bearer token", httpServer.headers().get("Authorization"));
        assertEquals("key", httpServer.env().get("API_KEY"));
    }

    @Test
    void testHttpServerJsonDeserializationStreamableHttp() throws Exception {
        String json = """
                {
                    "type": "streamable-http",
                    "url": "https://mcp.example.com/api"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(HttpServer.class, server);
        HttpServer httpServer = (HttpServer) server;
        assertEquals("streamable-http", httpServer.type());
    }

    @Test
    void testHttpServerJsonDeserializationWithUrlOnly() throws Exception {
        // When only URL is present and no type, should default to HTTP
        String json = """
                {
                    "url": "https://api.example.com"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(HttpServer.class, server);
    }

    // ==================== SseServer Tests ====================

    @Test
    void testSseServerFullConstructor() {
        SseServer server = new SseServer(
                "server-name",
                "sse",
                "https://sse.example.com",
                Map.of("Authorization", "Bearer token"),
                Map.of("KEY", "value")
        );

        assertEquals("server-name", server.name());
        assertEquals("sse", server.type());
        assertEquals("https://sse.example.com", server.url());
        assertEquals("Bearer token", server.headers().get("Authorization"));
        assertEquals("value", server.env().get("KEY"));
    }

    @Test
    void testSseServerUrlOnlyConstructor() {
        SseServer server = new SseServer("https://sse.example.com");

        assertNull(server.name());
        assertEquals("sse", server.type());
        assertEquals("https://sse.example.com", server.url());
        assertTrue(server.headers().isEmpty());
        assertTrue(server.env().isEmpty());
    }

    @Test
    void testSseServerUrlAndHeadersConstructor() {
        SseServer server = new SseServer("https://sse.example.com", Map.of("Auth", "token"));

        assertNull(server.name());
        assertEquals("sse", server.type());
        assertEquals("https://sse.example.com", server.url());
        assertEquals("token", server.headers().get("Auth"));
    }

    @Test
    void testSseServerTypeUrlHeadersEnvConstructor() {
        SseServer server = new SseServer("sse", "https://sse.example.com", 
                Map.of("Auth", "token"), Map.of("KEY", "value"));

        assertNull(server.name());
        assertEquals("sse", server.type());
        assertEquals("https://sse.example.com", server.url());
    }

    @Test
    void testSseServerWithNullType() {
        SseServer server = new SseServer(null, null, "https://sse.example.com", null, null);

        assertEquals("sse", server.type());
    }

    @Test
    void testSseServerWithNullHeaders() {
        SseServer server = new SseServer("name", "sse", "https://sse.example.com", null, Map.of());

        assertNotNull(server.headers());
        assertTrue(server.headers().isEmpty());
    }

    @Test
    void testSseServerWithNullEnv() {
        SseServer server = new SseServer("name", "sse", "https://sse.example.com", Map.of(), null);

        assertNotNull(server.env());
        assertTrue(server.env().isEmpty());
    }

    @Test
    void testSseServerWithName() {
        SseServer original = new SseServer("https://sse.example.com");
        SseServer withName = original.withName("new-name");

        assertEquals("new-name", withName.name());
        assertEquals(original.type(), withName.type());
        assertEquals(original.url(), withName.url());
        assertEquals(original.headers(), withName.headers());
        assertEquals(original.env(), withName.env());
    }

    @Test
    void testSseServerJsonDeserialization() throws Exception {
        String json = """
                {
                    "type": "sse",
                    "url": "https://sse.example.com",
                    "headers": {"Authorization": "Bearer token"},
                    "env": {"API_KEY": "key"}
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(SseServer.class, server);
        SseServer sseServer = (SseServer) server;
        assertEquals("sse", sseServer.type());
        assertEquals("https://sse.example.com", sseServer.url());
        assertEquals("Bearer token", sseServer.headers().get("Authorization"));
        assertEquals("key", sseServer.env().get("API_KEY"));
    }

    // ==================== McpServer Interface Constants Tests ====================

    @Test
    void testMcpServerTypeConstants() {
        assertEquals("stdio", McpServer.STDIO_TYPE);
        assertEquals("http", McpServer.HTTP_TYPE);
        assertEquals("sse", McpServer.SSE_TYPE);
        assertEquals("streamable-http", McpServer.STREAMABLE_HTTP_TYPE);
    }

    // ==================== McpServerDeserializer Edge Cases ====================

    @Test
    void testDeserializerWithInvalidJson() {
        String json = """
                {
                    "unknown_field": "value"
                }
                """;

        assertThrows(Exception.class, () -> objectMapper.readValue(json, McpServer.class));
    }

    @Test
    void testDeserializerWithEmptyArgs() throws Exception {
        String json = """
                {
                    "type": "stdio",
                    "command": "java",
                    "args": []
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(StdioServer.class, server);
        StdioServer stdioServer = (StdioServer) server;
        assertTrue(stdioServer.args().isEmpty());
    }

    @Test
    void testDeserializerWithEmptyEnv() throws Exception {
        String json = """
                {
                    "type": "stdio",
                    "command": "java",
                    "env": {}
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(StdioServer.class, server);
        StdioServer stdioServer = (StdioServer) server;
        assertTrue(stdioServer.env().isEmpty());
    }

    @Test
    void testDeserializerWithEmptyHeaders() throws Exception {
        String json = """
                {
                    "type": "http",
                    "url": "https://api.example.com",
                    "headers": {}
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(HttpServer.class, server);
        HttpServer httpServer = (HttpServer) server;
        assertTrue(httpServer.headers().isEmpty());
    }

    @Test
    void testDeserializerWithMissingUrl() throws Exception {
        String json = """
                {
                    "type": "http"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(HttpServer.class, server);
        HttpServer httpServer = (HttpServer) server;
        assertNull(httpServer.url());
    }

    @Test
    void testDeserializerWithMissingCommand() throws Exception {
        String json = """
                {
                    "type": "stdio"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(StdioServer.class, server);
        StdioServer stdioServer = (StdioServer) server;
        assertNull(stdioServer.command());
    }

    // Additional tests to cover all branches in McpServerDeserializer

    @Test
    void testDeserializerSseServerWithoutUrl() throws Exception {
        // Test SSE server without URL to cover the branch in deserializeSseServer
        String json = """
                {
                    "type": "sse"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(SseServer.class, server);
        SseServer sseServer = (SseServer) server;
        assertNull(sseServer.url());
    }

    @Test
    void testDeserializerSseServerWithoutType() throws Exception {
        // This should not happen in practice, but tests the branch
        // where type is explicitly "sse" but no URL
        String json = """
                {
                    "type": "sse",
                    "headers": {"Auth": "token"}
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(SseServer.class, server);
        SseServer sseServer = (SseServer) server;
        assertEquals("token", sseServer.headers().get("Auth"));
    }

    @Test
    void testDeserializerStdioWithArgsNotArray() throws Exception {
        // Test when args field exists but is not an array
        String json = """
                {
                    "type": "stdio",
                    "command": "java",
                    "args": "not-an-array"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(StdioServer.class, server);
        StdioServer stdioServer = (StdioServer) server;
        assertTrue(stdioServer.args().isEmpty());
    }

    @Test
    void testDeserializerStdioWithoutArgs() throws Exception {
        // Test when args field is missing entirely
        String json = """
                {
                    "type": "stdio",
                    "command": "java"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(StdioServer.class, server);
        StdioServer stdioServer = (StdioServer) server;
        assertTrue(stdioServer.args().isEmpty());
    }

    @Test
    void testDeserializerEnvNotObject() throws Exception {
        // Test when env field exists but is not an object
        String json = """
                {
                    "type": "stdio",
                    "command": "java",
                    "env": "not-an-object"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(StdioServer.class, server);
        StdioServer stdioServer = (StdioServer) server;
        assertTrue(stdioServer.env().isEmpty());
    }

    @Test
    void testDeserializerHeadersNotObject() throws Exception {
        // Test when headers field exists but is not an object
        String json = """
                {
                    "type": "http",
                    "url": "https://api.example.com",
                    "headers": "not-an-object"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(HttpServer.class, server);
        HttpServer httpServer = (HttpServer) server;
        assertTrue(httpServer.headers().isEmpty());
    }

    @Test
    void testDeserializerWithTypeNullAndUrlPresent() throws Exception {
        // Test the fallback case where type is null but URL is present
        // This should go through isHttpServer returning true
        String json = """
                {
                    "url": "https://api.example.com"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(HttpServer.class, server);
    }

    @Test
    void testDeserializerWithTypeNullAndCommandPresent() throws Exception {
        // Test when type is null but command is present (should be STDIO)
        String json = """
                {
                    "command": "java",
                    "args": ["-jar", "test.jar"]
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(StdioServer.class, server);
    }

    @Test
    void testDeserializerWithTypeNotNullAndCommandPresent() throws Exception {
        // Test when type is not null (not stdio) but command is present
        // This tests the branch where type != null in isStdioServer
        String json = """
                {
                    "type": "stdio",
                    "command": "java"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(StdioServer.class, server);
    }

    @Test
    void testDeserializerHttpWithTypeNotNullAndUrlPresent() throws Exception {
        // Test when type is http and URL is present
        String json = """
                {
                    "type": "http",
                    "url": "https://api.example.com"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(HttpServer.class, server);
    }

    @Test
    void testDeserializerWithEnvMissing() throws Exception {
        // Test when env field is completely missing
        String json = """
                {
                    "type": "http",
                    "url": "https://api.example.com"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(HttpServer.class, server);
        HttpServer httpServer = (HttpServer) server;
        assertTrue(httpServer.env().isEmpty());
    }

    @Test
    void testDeserializerWithHeadersMissing() throws Exception {
        // Test when headers field is completely missing
        String json = """
                {
                    "type": "http",
                    "url": "https://api.example.com"
                }
                """;

        McpServer server = objectMapper.readValue(json, McpServer.class);

        assertInstanceOf(HttpServer.class, server);
        HttpServer httpServer = (HttpServer) server;
        assertTrue(httpServer.headers().isEmpty());
    }

    @Test
    void testDeserializerWithUnknownTypeAndUrl() throws Exception {
        // Test when type is set to an unknown value but URL is present
        // This should throw an exception because the type is not recognized
        String json = """
                {
                    "type": "unknown",
                    "url": "https://api.example.com"
                }
                """;

        assertThrows(Exception.class, () -> objectMapper.readValue(json, McpServer.class));
    }

    @Test
    void testDeserializerWithUnknownTypeNoUrl() throws Exception {
        // Test when type is set to an unknown value and no URL
        String json = """
                {
                    "type": "unknown"
                }
                """;

        assertThrows(Exception.class, () -> objectMapper.readValue(json, McpServer.class));
    }
}
