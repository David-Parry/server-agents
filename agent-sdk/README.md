# Agent SDK

A Spring Boot application that acts as a bridge proxy between local and remote MCP (Model Context Protocol) servers and a remote main server via WebSocket connections. Uses **Spring AI MCP Client** for managing MCP server connections.

## Overview

The Agent SDK is part of the MCP Proxy Architecture (Version 2.0). It:

1. Connects to a main server via WebSocket with API key authentication
2. Manages MCP servers using **Spring AI MCP Client** (STDIO, HTTP, and SSE-based)
3. Routes tool call requests from the main server to MCP servers
4. Provides REST API for dynamic MCP server management
5. Handles reconnection with exponential backoff
6. Provides metrics and health endpoints

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           AGENT SDK                                   │
│                                                                          │
│  ┌─────────────────┐    ┌─────────────────────────────────────────────┐ │
│  │  WebSocket      │    │     MCP Server Manager (Spring AI MCP)      │ │
│  │  Client Handler │◄──►│  ┌─────────┐  ┌─────────┐  ┌─────────┐     │ │
│  │                 │    │  │ STDIO   │  │ HTTP    │  │ SSE     │     │ │
│  │  - Connect      │    │  │ Server  │  │ Server  │  │ Server  │ ... │ │
│  │  - Reconnect    │    │  │ (local) │  │ (remote)│  │ (remote)│     │ │
│  │  - Heartbeat    │    │  └─────────┘  └─────────┘  └─────────┘     │ │
│  └────────┬────────┘    └─────────────────────────────────────────────┘ │
│           │                              ▲                               │
│           │                              │                               │
│  ┌────────┴────────┐    ┌─────��──────────┴────────────────────────────┐ │
│  │  REST API       │    │     MCP Proxy Controller                    │ │
│  │  /api/mcp/*     │◄──►│  - Add/Remove servers dynamically           │ │
│  │                 │    │  - List tools                               │ │
│  │                 │    │  - Execute tools                            │ │
│  └─────────────────┘    └─────────────────────────────────────────────┘ │
│                                                                          │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
                               │ WebSocket (TLS)
                               │
                               ▼
                       ┌───────────────┐
                       │  MAIN SERVER  │
                       │  (Hub)        │
                       └───────────────┘
```

## Features

### Spring AI MCP Integration

This client uses the **Spring AI MCP Client** library (`org.springframework.ai:spring-ai-mcp`) to manage MCP servers. This provides:

- Robust STDIO, HTTP, and SSE transport handling
- Proper MCP protocol implementation
- Tool discovery and registration
- Async/sync client support

### Supported Server Types

The client supports three types of MCP servers:

| Type | Description | Transport |
|------|-------------|-----------|
| `stdio` | Local command-based servers | STDIO (stdin/stdout) |
| `http` | Remote HTTP-based servers | Streamable HTTP |
| `sse` | Remote SSE-based servers | Server-Sent Events |

### Dynamic MCP Server Management

MCP servers can be:
- Configured at startup via `application.yml`
- Loaded from a JSON configuration file
- Added dynamically at runtime via REST API
- Removed at runtime via REST API

### Tool Proxying

All tools from connected MCP servers are:
- Automatically discovered and registered
- Proxied to the main server via WebSocket
- Executable via REST API for testing

## Configuration

### JSON Configuration File (mcp.json)

The recommended way to configure MCP servers is via a JSON file:

```json
{
  "mcpServers": {
    "local-tools": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-memory"],
      "env": {
        "API_KEY": "{MCP_API_KEY}"
      }
    },
    "remote-api": {
      "type": "http",
      "url": "https://mcp.sentry.dev/mcp",
      "headers": {
        "Authorization": "Bearer {SENTRY_TOKEN}"
      }
    },
    "legacy-remote": {
      "type": "sse",
      "url": "https://example.com/sse"
    },
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
    }
  }
}
```

**Server Type Detection:**
- If `type` is `"stdio"` or `command` is present → STDIO server
- If `type` is `"http"` or `"streamable-http"` → HTTP server
- If `type` is `"sse"` → SSE server
- If only `url` is present (no type) → defaults to HTTP server

**Environment Variable Substitution:**
Use `{VAR_NAME}` syntax to reference environment variables in the configuration.

### Loading Configuration Programmatically

```java
@Autowired
private McpServerManager mcpServerManager;

// Load from file path
mcpServerManager.loadFromConfigFile("/path/to/mcp.json");

// Load from classpath resource
mcpServerManager.loadFromClasspath("mcp.json");

// Load from JSON string
mcpServerManager.loadFromJsonString(jsonContent);
```

### application.yml

```yaml
server:
  port: 8081

agent:
  client:
    api-key: ${AGENT_API_KEY:your-api-key}
    server-url: ${AGENT_SERVER_URL:ws://localhost:8080/mcp-proxy}
    
    connection:
      heartbeat-interval-seconds: 30
      connection-timeout-seconds: 30
      idle-timeout-seconds: 300
    
    reconnect:
      enabled: true
      max-attempts: 5
      initial-delay-ms: 1000
      max-delay-ms: 30000
      backoff-multiplier: 2.0
    
    # Legacy STDIO-only configuration (still supported)
    mcp-servers:
      - name: filesystem
        command: npx
        args:
          - -y
          - "@modelcontextprotocol/server-filesystem"
          - "/path/to/allowed/directory"
        enabled: true
        startup-timeout-ms: 10000
        shutdown-timeout-ms: 5000
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP server port | `8081` |
| `AGENT_API_KEY` | API key for server authentication | `default-dev-key-1` |
| `AGENT_SERVER_URL` | WebSocket URL of the main server | `ws://localhost:8080/mcp-proxy` |

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew bootRun
```

Or with environment variables:

```bash
AGENT_API_KEY=your-key AGENT_SERVER_URL=wss://server.example.com/mcp-proxy ./gradlew bootRun
```

## REST API Endpoints

### MCP Server Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/mcp/status` | Get overall status (server count, tool count) |
| `GET` | `/api/mcp/servers` | List all running MCP servers |
| `GET` | `/api/mcp/servers/{name}` | Get details of a specific server |
| `POST` | `/api/mcp/servers` | Add a new MCP server dynamically |
| `DELETE` | `/api/mcp/servers/{name}` | Remove an MCP server |

### Tool Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/mcp/tools` | List all available tools |
| `POST` | `/api/mcp/tools/{toolName}/execute` | Execute a tool |
| `POST` | `/api/mcp/tools/refresh` | Refresh tools from all servers |

### Health & Metrics

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health status including connection state |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus metrics endpoint |

## API Examples

### Add an MCP Server Dynamically

```bash
curl -X POST http://localhost:8081/api/mcp/servers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "filesystem",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
    "startupTimeoutMs": 15000
  }'
```

### List Available Tools

```bash
curl http://localhost:8081/api/mcp/tools
```

### Execute a Tool

```bash
curl -X POST http://localhost:8081/api/mcp/tools/read_file/execute \
  -H "Content-Type: application/json" \
  -d '{
    "path": "/tmp/test.txt"
  }'
```

### Remove an MCP Server

```bash
curl -X DELETE http://localhost:8081/api/mcp/servers/filesystem
```

## MCP Server Types

### STDIO Servers (Local)

STDIO servers are local processes that communicate via stdin/stdout:

```json
{
  "local-tools": {
    "type": "stdio",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-memory"],
    "env": {
      "API_KEY": "your-key"
    }
  }
}
```

### HTTP Servers (Remote)

HTTP servers use the streamable HTTP transport:

```json
{
  "remote-api": {
    "type": "http",
    "url": "https://mcp.sentry.dev/mcp",
    "headers": {
      "Authorization": "Bearer token"
    }
  }
}
```

### SSE Servers (Legacy Remote)

SSE servers use Server-Sent Events transport:

```json
{
  "legacy-remote": {
    "type": "sse",
    "url": "https://example.com/sse"
  }
}
```

## Metrics

The following metrics are exposed:

- `agent.client.connection.status` - Connection status (0=disconnected, 1=connected)
- `agent.client.sessions.active` - Number of active sessions
- `agent.client.tool_calls.pending` - Number of pending tool calls
- `agent.client.mcp_servers.running` - Number of running MCP servers
- `agent.client.tools.registered` - Number of registered tools
- `agent.client.connections.opened` - Total connections opened
- `agent.client.connections.closed` - Total connections closed
- `agent.client.tool_calls.duration` - Tool call duration histogram

## Protocol Messages

The client handles the following message types:

| Message | Direction | Description |
|---------|-----------|-------------|
| `ConnectionEstablished` | Server → Client | Connection confirmed |
| `ToolCallRequest` | Server → Client | Execute a tool |
| `ToolCallResponse` | Client → Server | Tool execution result |
| `StreamChunk` | Server → Client | Streaming response chunk |
| `SessionResult` | Server → Client | Final session result |
| `Heartbeat` | Bidirectional | Keep-alive |
| `Error` | Server → Client | Error notification |

## Supported MCP Servers

Any MCP server can be used. Popular examples:

| Server | Type | Command/URL | Description |
|--------|------|-------------|-------------|
| Filesystem | STDIO | `npx -y @modelcontextprotocol/server-filesystem /path` | File operations |
| Git | STDIO | `uvx mcp-server-git --repository /path` | Git operations |
| SQLite | STDIO | `uvx mcp-server-sqlite --db-path /path/db.sqlite` | SQLite database |
| Brave Search | STDIO | `npx -y @anthropic/mcp-server-brave-search` | Web search |
| Puppeteer | STDIO | `npx -y @anthropic/mcp-server-puppeteer` | Browser automation |
| Sentry | HTTP | `https://mcp.sentry.dev/mcp` | Sentry integration |

## Dependencies

- Spring Boot 3.4.1
- Spring AI MCP 1.0.0-M6
- MCP SDK 0.7.0
- MCP Spring WebFlux 0.7.0 (for HTTP/SSE transports)
- Resilience4j for circuit breaker/retry
- Micrometer for metrics

## License

MIT
