# Spring Agents - MCP Proxy WebSocket Hub

A Spring Boot server that acts as a central hub for the Model Context Protocol (MCP) Proxy architecture. It accepts WebSocket connections from remote agent-clients, authenticates them using API keys, and routes tool calls between LLM providers (Anthropic, Ollama) and remote clients.

## Features

- **WebSocket Server** - Accepts connections at `/agent` endpoint
- **API Key Authentication** - Secure client authentication with rate limiting
- **Multiple LLM Providers** - Supports Anthropic (Claude) and Ollama models
- **Remote Tool Execution** - Proxies tool calls to connected agent-clients
- **Streaming Support** - Real-time streaming responses
- **Session Management** - Multiple concurrent sessions per connection
- **Circuit Breakers** - Reliability patterns for fault tolerance
- **Observability** - Metrics, structured logging, and admin endpoints

## Prerequisites

- Java 21+
- Gradle 8+
- Anthropic API key (for Claude models)
- Ollama running locally (optional, for local LLM support)

## Quick Start

### 1. Clone and Build

```bash
git clone <repository-url>
cd spring-agents
./gradlew build
```

### 2. Configure Environment Variables

The application requires certain secrets to be set via environment variables. These have **no defaults** in the base configuration for security reasons.

```bash
# Required for Anthropic/Claude models
export ANTHROPIC_API_KEY=your-anthropic-api-key

# Required security secrets (no defaults - must be set)
export AGENT_ADMIN_API_TOKEN=your-admin-api-token          # Generate with: openssl rand -base64 48
export AGENT_HASHING_SECRET_V1=your-hashing-secret-v1      # Generate with: openssl rand -base64 64 (min 32 chars)
export AGENT_JWT_SIGNING_KEY=your-jwt-signing-key          # Generate with: openssl rand -base64 48 (min 32 chars)

# Optional configuration with defaults
export AGENT_ADMIN_ENABLED=true                            # Enable/disable admin API
export AGENT_HASHING_SECRET_CURRENT=V1                     # Current hashing secret version
export AGENT_JWT_ISSUER=spring-agents                      # JWT issuer claim
export AGENT_JWT_EXPIRATION_DAYS=0                         # Token expiration (0 = non-expiring)
```

### 3. Spring Profiles

The application uses Spring Boot profiles for environment-specific configuration:

| Profile | Description | Usage |
|---------|-------------|-------|
| (default) | Base configuration - requires environment variables for secrets | Production |
| `local` | Local development with pre-configured secrets | Development |
| `test` | Test configuration with in-memory database | Testing |

#### Running with Profiles

```bash
# Production (default) - requires all environment variables set
./gradlew bootRun

# Local development - uses pre-configured development secrets
./gradlew bootRun --args='--spring.profiles.active=local'

# Or via environment variable
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun

# Multiple profiles (local inherits from default, then applies overrides)
./gradlew bootRun --args='--spring.profiles.active=local'
```

#### Profile Inheritance

Spring Boot profiles follow an inheritance model:
1. `application.yml` (base) is always loaded first
2. Profile-specific files (e.g., `application-local.yml`) override base settings
3. Environment variables can override any setting

**Security Note:** The `local` profile provides development-only secrets. **Never use these in production!**

### 4. Start the Server

```bash
./gradlew bootRun
```

The server will start on `http://localhost:8080` with the WebSocket endpoint at `ws://localhost:8080/agent`.

## Configuration

### application.yml

```yaml
mcp:
  proxy:
    enabled: true
    api-keys:
      - key: ${MCP_API_KEY_1:default-dev-key-1}
        name: "Remote Client 1"
        enabled: true
        rate-limit-per-minute: 100
      - key: ${MCP_API_KEY_2:default-dev-key-2}
        name: "Remote Client 2"
        enabled: true
        rate-limit-per-minute: 50

    connection:
      idle-timeout-seconds: 300
      heartbeat-interval-seconds: 30
      max-connections-per-client: 5
      max-concurrent-sessions: 10

    session:
      max-duration-seconds: 600
      tool-call-timeout-seconds: 60
      tool-call-retry-attempts: 2

    streaming:
      enabled: true

    circuit-breaker:
      failure-threshold: 5
      success-threshold: 3
      timeout-seconds: 30
```

## Connection Flow

### 1. WebSocket Connection

Connect to the WebSocket endpoint with your API key:

```
ws://localhost:8080/agent
Header: X-API-Key: default-dev-key-2
```

Or via query parameter (quote the URL in shell to escape `?`):
```bash
websocat "ws://localhost:8080/agent?apiKey=default-dev-key-1"
```

### 2. Connection Established

Upon successful connection, the server sends a `ConnectionEstablished` message:

```json
{
  "type": "connection_established",
  "connectionId": "conn-abc12345",
  "serverVersion": "1.0.0",
  "timestamp": "2024-01-15T10:30:00Z",
  "capabilities": {
    "streamingSupported": true,
    "maxConcurrentSessions": 10,
    "sessionTimeoutSeconds": 600,
    "toolCallTimeoutSeconds": 60
  }
}
```

### 3. Create a Session

Send a `CreateSession` message to start a prompt execution. Tools are provided by the client and will be proxied back for execution.

**Tool Naming Convention:** Tool names must follow the format `server-toolname` where:
- `server` identifies the MCP server/service providing the tool (e.g., `terminal`, `filesystem`, `git`)
- `toolname` is the specific tool name (e.g., `list_files`, `execute_command`)
- The hyphen (`-`) separator is used because it's compatible with all LLM providers (Anthropic only accepts `[a-zA-Z0-9_-]`)

```json
{"type":"create_session","clientSessionId":"my-session-001","prompt":"List all files in the current directory","systemPrompt":"You are a helpful assistant with access to file system tools.","model":"claude-sonnet-4-5","streamingEnabled":true,"tools":[{"name":"terminal-list_files","description":"Lists files in a directory","inputSchema":"{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}"},{"name":"terminal-execute_command","description":"Executes a shell command","inputSchema":"{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}"}],"metadata":{"workingDirectory":"/home/user/project"}}
```

### 4. Session Started

The server confirms the session:

```json
{
  "type": "session_started",
  "sessionId": "sess-xyz789",
  "clientSessionId": "my-session-001",
  "startedAt": "2024-01-15T10:30:01Z",
  "deadline": "2024-01-15T10:40:01Z"
}
```

### 5. Tool Call Request

When the LLM needs to execute a tool, the server sends the request back to the client with the full `server-toolname`:

```json
{
  "type": "tool_call_request",
  "requestId": "req-abc123",
  "sessionId": "sess-xyz789",
  "toolName": "terminal-list_files",
  "arguments": "{\"path\":\"/home/user/project\"}",
  "deadline": "2024-01-15T10:31:01Z"
}
```

### 6. Tool Call Response

The client executes the tool locally and responds:

```json
{
  "type": "tool_call_response",
  "requestId": "req-abc123",
  "sessionId": "sess-xyz789",
  "success": true,
  "result": "file1.txt\nfile2.txt\nREADME.md",
  "errorMessage": null
}
```

### 7. Stream Chunks (if streaming enabled)

The server sends response chunks as they arrive:

```json
{
  "type": "stream_chunk",
  "sessionId": "sess-xyz789",
  "sequenceNumber": 0,
  "content": "The directory contains ",
  "isLast": false
}
```

### 8. Session Result

When the prompt completes:

```json
{
  "type": "session_result",
  "sessionId": "sess-xyz789",
  "clientSessionId": "my-session-001",
  "success": true,
  "content": "The directory contains 3 files: file1.txt, file2.txt, and README.md.",
  "errorMessage": null,
  "metrics": {
    "durationMs": 2500,
    "toolCallCount": 1,
    "tokenCount": 150
  }
}
```

## Client Examples

### cURL - Admin Endpoints

```bash
# Check server status
curl http://localhost:8080/api/admin/status

# List active connections
curl http://localhost:8080/api/admin/connections

# List active sessions
curl http://localhost:8080/api/admin/sessions

# Get metrics summary
curl http://localhost:8080/api/admin/metrics

# Force disconnect a connection
curl -X DELETE http://localhost:8080/api/admin/connections/conn-abc12345

# Cancel a session
curl -X DELETE http://localhost:8080/api/admin/sessions/sess-xyz789

# View circuit breaker status
curl http://localhost:8080/api/admin/circuit-breakers

# Reset a circuit breaker
curl -X POST http://localhost:8080/api/admin/circuit-breakers/conn-abc12345/list_files/reset
```

### WebSocket Testing with websocat

Install [websocat](https://github.com/vi/websocat) for WebSocket testing:

```bash
# Connect with API key header
websocat -H="X-API-Key: default-dev-key-1" "ws://localhost:8080/agent"
```

**Important:** After connecting, type the entire JSON message on a single line and press Enter. Each line you type is sent as a separate WebSocket message.

```bash
# Simple session without tools (type this on ONE line, then press Enter):
{"type":"create_session","clientSessionId":"test-001","prompt":"Hello, what is 2+2?","model":"claude-sonnet-4-5","streamingEnabled":false,"tools":[]}

# Session with tools (type this on ONE line, then press Enter):
{"type":"create_session","clientSessionId":"test-002","prompt":"List files","model":"claude-sonnet-4-5","streamingEnabled":false,"tools":[{"name":"terminal-list_files","description":"Lists files","inputSchema":"{}"}]}

# Send a heartbeat:
{"type":"heartbeat","timestamp":"2024-01-15T10:30:00Z","isResponse":false}
```

**Tip:** You can also pipe JSON from a file:
```bash
echo '{"type":"create_session","clientSessionId":"test-001","prompt":"Hello!","model":"claude-sonnet-4-5","streamingEnabled":false,"tools":[]}' | websocat -H="X-API-Key: default-dev-key-1" "ws://localhost:8080/agent"
```

### Java WebSocket Client

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class McpProxyClient {

    private static final String API_KEY = "default-dev-key-1";
    private static final String SERVER_URL = "ws://localhost:8080/agent";
    
    private final ObjectMapper objectMapper;
    private WebSocketSession session;

    public McpProxyClient() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void connect() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("X-API-Key", API_KEY);

        CompletableFuture<WebSocketSession> future = client.execute(
            new McpMessageHandler(),
            headers,
            URI.create(SERVER_URL)
        );

        this.session = future.get();
        System.out.println("Connected to MCP Proxy");
    }

    public void createSession(String prompt, List<ToolDefinition> tools) throws Exception {
        Map<String, Object> message = Map.of(
            "type", "create_session",
            "clientSessionId", "java-client-" + System.currentTimeMillis(),
            "prompt", prompt,
            "model", "claude-sonnet-4-5",
            "streamingEnabled", false,
            "tools", tools
        );

        String json = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(json));
    }

    public void sendToolResponse(String requestId, String sessionId, String result) throws Exception {
        Map<String, Object> message = Map.of(
            "type", "tool_call_response",
            "requestId", requestId,
            "sessionId", sessionId,
            "success", true,
            "result", result
        );

        String json = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(json));
    }

    public void close() throws Exception {
        if (session != null && session.isOpen()) {
            session.close();
        }
    }

    private class McpMessageHandler extends TextWebSocketHandler {
        
        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            try {
                Map<String, Object> msg = objectMapper.readValue(
                    message.getPayload(), 
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
                );
                
                String type = (String) msg.get("type");
                System.out.println("Received: " + type);

                switch (type) {
                    case "connection_established" -> {
                        System.out.println("Connection ID: " + msg.get("connectionId"));
                    }
                    case "session_started" -> {
                        System.out.println("Session started: " + msg.get("sessionId"));
                    }
                    case "tool_call_request" -> {
                        String toolName = (String) msg.get("toolName");
                        String arguments = (String) msg.get("arguments");
                        System.out.println("Tool call: " + toolName + " with args: " + arguments);
                        
                        // Execute tool locally and send response
                        String result = executeLocalTool(toolName, arguments);
                        sendToolResponse(
                            (String) msg.get("requestId"),
                            (String) msg.get("sessionId"),
                            result
                        );
                    }
                    case "stream_chunk" -> {
                        System.out.print(msg.get("content"));
                    }
                    case "session_result" -> {
                        System.out.println("\nResult: " + msg.get("content"));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metrics = (Map<String, Object>) msg.get("metrics");
                        System.out.println("Duration: " + metrics.get("durationMs") + "ms");
                    }
                    case "error" -> {
                        System.err.println("Error: " + msg.get("message"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String executeLocalTool(String toolName, String arguments) {
        // Implement your tool execution logic here
        return "Tool '" + toolName + "' executed successfully";
    }

    // Tool definition helper
    public record ToolDefinition(String name, String description, String inputSchema) {}

    // Example usage
    public static void main(String[] args) throws Exception {
        McpProxyClient client = new McpProxyClient();
        
        try {
            client.connect();
            
            // Create a simple session without tools
            client.createSession("What is the capital of France?", List.of());
            
            // Wait for response
            Thread.sleep(10000);
            
        } finally {
            client.close();
        }
    }
}
```

### Java Dependencies (Maven)

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-websocket</artifactId>
        <version>6.1.0</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.15.0</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
        <version>2.15.0</version>
    </dependency>
</dependencies>
```

### Java Dependencies (Gradle)

```kotlin
dependencies {
    implementation("org.springframework:spring-websocket:6.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.0")
}
```

## Actuator Endpoints

```bash
# Health check
curl http://localhost:8080/actuator/health

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# All metrics
curl http://localhost:8080/actuator/metrics

# Specific metric
curl http://localhost:8080/actuator/metrics/mcp.connections.active
```

## Message Types Reference

### Client → Server

| Message Type | Description |
|--------------|-------------|
| `create_session` | Start a new prompt session with tools |
| `tool_call_response` | Response to a tool call request |
| `cancel_session` | Cancel an in-progress session |
| `heartbeat` | Keep connection alive |

### Server → Client

| Message Type | Description |
|--------------|-------------|
| `connection_established` | Confirms WebSocket connection |
| `session_started` | Confirms session created |
| `tool_call_request` | Request tool execution |
| `stream_chunk` | Streaming response chunk |
| `session_result` | Final prompt result |
| `session_cancelled` | Cancellation confirmation |
| `error` | Error notification |
| `heartbeat` | Keep connection alive response |

## Error Codes

| Code | Description |
|------|-------------|
| `INVALID_MESSAGE` | Malformed or unexpected message |
| `SESSION_NOT_FOUND` | Session ID not found |
| `SESSION_EXPIRED` | Session has timed out |
| `TOOL_CALL_TIMEOUT` | Tool call exceeded timeout |
| `TOOL_CALL_FAILED` | Tool execution failed |
| `RATE_LIMITED` | Rate limit exceeded |
| `INTERNAL_ERROR` | Server internal error |
| `CONNECTION_LIMIT_EXCEEDED` | Max connections reached |
| `SESSION_LIMIT_EXCEEDED` | Max sessions per connection reached |

## Development

### Running Tests

```bash
./gradlew test
```

### Building

```bash
./gradlew build
```

### Running with Debug Logging

```bash
./gradlew bootRun --args='--logging.level.com.davidparry.agent=DEBUG'
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Agents Server                      │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              WebSocket: /agent                        │   │
│  │  ┌─────────────────────────────────────────────────┐ │   │
│  │  │ Connection: conn-001 (Client A)                 │ │   │
│  │  │   ├── Session: sess-abc (tools: [read, write])  │ │   │
│  │  │   └── Session: sess-def (tools: [search])       │ │   │
│  │  └─────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ API Key      │  │ Rate         │  │ Circuit      │       │
│  │ Validator    │  │ Limiter      │  │ Breaker      │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              LLM Integration (Spring AI)              │   │
│  │         Anthropic (Claude) | Ollama (Local)           │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## How It Works

1. **Client connects** via WebSocket with an API key
2. **Client sends a session request** with a prompt and tool definitions
3. **Server executes the prompt** using the configured LLM (Anthropic/Ollama)
4. **When LLM needs a tool**, server sends a `tool_call_request` back to the client
5. **Client executes the tool locally** and sends the result back
6. **Server continues the LLM conversation** with the tool result
7. **Final response** is sent to the client as `session_result`

This architecture allows the LLM to use tools that run on the client's machine, enabling secure access to local resources without exposing them to the server.

