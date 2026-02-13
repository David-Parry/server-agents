# Server-Agents Platform

A comprehensive **AI Agent Platform** designed to enable remote AI agents to augment and automate workflows across the **Software Development Lifecycle (SDLC)**. The platform follows a client-server architecture where AI-powered agents assist developers, QA engineers, and other contributors with intelligent automation.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Components](#components)
  - [spring-agents (Server)](#spring-agents-server)
  - [agent-client (Client)](#agent-client-client)
  - [admin-client-spring-agents (Admin Portal)](#admin-client-spring-agents-admin-portal)
  - [agent-message-protocol (Shared Library)](#agent-message-protocol-shared-library)
- [Agent Types](#agent-types)
- [Quick Start](#quick-start)
- [Communication Flow](#communication-flow)
- [Security Model](#security-model)
- [Observability](#observability)
- [SDLC Workflow Examples](#sdlc-workflow-examples)
- [License](#license)

---

## Overview

**Server-Agents** provides an enterprise-grade platform for AI-assisted software development:

| Component | Purpose |
|-----------|---------|
| **spring-agents** | Central server hosting LLM integrations (Anthropic Claude, Ollama) with customer management, token usage tracking, and policy enforcement |
| **agent-client** | Runs on developer machines, connecting to the server via WebSocket and executing tools locally through the Model Context Protocol (MCP) |
| **admin-client-spring-agents** | Web application for administrators to manage customers, monitor usage, and configure the platform |
| **agent-message-protocol** | Shared Java library defining the WebSocket message protocol between client and server |

### Key Features

- **Multi-LLM Support**: Integrates with Anthropic (Claude) and Ollama via Spring AI
- **Remote Tool Execution**: Proxies tool calls to connected clients for local execution
- **Multi-Tenant Architecture**: Per-customer token allowances with configurable reset policies
- **MCP Integration**: Full support for Model Context Protocol servers (STDIO, HTTP, SSE)
- **Enterprise Security**: API key authentication, JWT tokens, audit logging, rate limiting
- **Observability**: Prometheus metrics, Grafana dashboards, structured logging

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        ADMIN PORTAL (admin-client-spring-agents)                 │
│                         Next.js Web Application (Port 3000)                      │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────────┐ │
│  │ Dashboard   │ │ Customers   │ │ Models      │ │ Policies    │ │ Audit Logs │ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ └────────────┘ │
└────────────────────────────────────┬────────────────────────────────────────────┘
                                     │ REST API (Bearer Token Auth)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           SPRING-AGENTS SERVER (Port 8080)                       │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                        WebSocket Endpoint: /agent                         │   │
│  │  • API Key Authentication (X-API-Key header)                              │   │
│  │  • Rate Limiting per customer                                             │   │
│  │  • Session Management (multiple concurrent sessions)                      │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐  │
│  │ LLM Integration │  │ Customer/Token  │  │ Agent Configuration             │  │
│  │ (Spring AI)     │  │ Management      │  │ • ANALYST                       │  │
│  │ • Anthropic     │  │ • Token Hashing │  │ • ENGINEER                      │  │
│  │ • Ollama        │  │ • Usage Tracking│  │ • REVIEWER                      │  │
│  └─────────────────┘  │ • Policy Types  │  │ • DIAGNOSTICIAN                 │  │
│                       └─────────────────┘  └─────────────────────────────────┘  │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                         Database (H2/PostgreSQL)                         │    │
│  │  CUSTOMER | CUSTOMER_TOKEN | LLM_MODEL | POLICY_TYPE | ALLOWANCE | AUDIT │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
└────────────────────────────────────┬────────────────────────────────────────────┘
                                     │ WebSocket (TLS)
                                     │ Tool Call Requests ↓ / Tool Results ↑
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         AGENT-CLIENT (Developer Machine)                         │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                    WebSocket Client Handler                               │   │
│  │  • Auto-reconnect with exponential backoff                                │   │
│  │  • Heartbeat keep-alive                                                   │   │
│  │  • Session-isolated MCP server management                                 │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                    MCP Server Manager (Spring AI MCP)                    │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │    │
│  │  │ Filesystem  │  │ Terminal    │  │ Git         │  │ Custom MCP  │     │    │
│  │  │ (STDIO)     │  │ (STDIO)     │  │ (STDIO)     │  │ (HTTP/SSE)  │     │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘     │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                    Local Resources (Developer Machine)                   │    │
│  │  • File System Access    • Terminal/Shell Commands                       │    │
│  │  • Git Repositories      • Databases, APIs, etc.                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Components

### spring-agents (Server)

The central hub that orchestrates AI agent interactions.

**Key Features:**

| Feature | Description |
|---------|-------------|
| WebSocket Server | Accepts connections at `/agent` endpoint with API key authentication |
| Multi-LLM Support | Integrates with Anthropic (Claude) and Ollama via Spring AI |
| Remote Tool Execution | Proxies tool calls to connected agent-clients |
| Customer Management | Multi-tenant support with per-customer token allowances |
| Usage Tracking | Per-model token usage with configurable reset policies |
| Security | JWT tokens, API key hashing (versioned secrets), audit logging |
| Observability | Prometheus metrics, structured logging, health endpoints |

**Database Entities:**

- `CUSTOMER` - Multi-tenant customer records
- `CUSTOMER_TOKEN` - Hashed API tokens with versioned secrets
- `LLM_MODEL` - Supported LLM models (Claude, Ollama variants)
- `POLICY_TYPE` - Token reset policies (daily, weekly, monthly, unlimited)
- `CUSTOMER_MODEL_ALLOWANCE` - Per-customer, per-model token limits
- `SECURITY_AUDIT_LOG` - Comprehensive audit trail

📖 [Full spring-agents documentation](./spring-agents/README.md)

---

### agent-client (Client)

A Spring Boot application that runs on developer machines, bridging local tools with the remote server.

**Key Features:**

| Feature | Description |
|---------|-------------|
| WebSocket Client | Connects to spring-agents with auto-reconnect |
| MCP Server Manager | Manages local MCP servers (STDIO, HTTP, SSE) |
| Session Isolation | Each session gets its own MCP server instances |
| Tool Execution | Executes tools locally and returns results to server |
| Configuration | YAML/JSON-based agent and MCP server configuration |

**Supported MCP Server Types:**

| Type | Transport | Example |
|------|-----------|---------|
| STDIO | stdin/stdout | `npx @modelcontextprotocol/server-filesystem` |
| HTTP | Streamable HTTP | `https://mcp.sentry.dev/mcp` |
| SSE | Server-Sent Events | Legacy remote servers |

📖 [Full agent-client documentation](./agent-client/README.md)

---

### admin-client-spring-agents (Admin Portal)

A Next.js web application for platform administration.

**Features:**

| Page | Functionality |
|------|---------------|
| Dashboard | System statistics, recent activity, quick actions |
| Customers | Create, enable/disable, manage API tokens, view allowances |
| Models | Add/configure LLM models, set default token allocations |
| Policies | Configure token reset policies (daily, weekly, monthly) |
| Audit Logs | View/filter security audit trail, cleanup old logs |
| Settings | Configure admin token and API settings |

**Tech Stack:** Next.js 15, TypeScript, Tailwind CSS, React Query

📖 [Full admin-client-spring-agents documentation](./admin-client-spring-agents/admin-ui/README.md)

---

### agent-message-protocol (Shared Library)

A shared Java library defining the WebSocket message protocol.

**Message Types:**

| Direction | Message | Description |
|-----------|---------|-------------|
| Server → Client | `ConnectionEstablished` | Connection confirmation with capabilities |
| Server → Client | `SessionStarted` | Session creation confirmation |
| Server → Client | `ToolCallRequest` | Request to execute a tool |
| Server → Client | `StreamChunk` | Streaming response chunk |
| Server → Client | `SessionResult` | Final session result |
| Server → Client | `ErrorMessage` | Error notification |
| Client → Server | `CreateSession` | Create a new session with agent config |
| Client → Server | `ToolCallResponse` | Tool execution result |
| Client → Server | `CancelSession` | Cancel an active session |
| Bidirectional | `Heartbeat` | Keep-alive message |

📖 [Full agent-message-protocol documentation](./agent-message-protocol/README.md)

---

## Agent Types

The platform provides four core agent types designed for specific SDLC roles:

| Agent Type | Purpose | Writes Code | Uses Tools |
|------------|---------|-------------|------------|
| **ANALYST** | Understands intent, resolves ambiguity, produces execution plans and technical designs | No | Yes |
| **ENGINEER** | Implements approved designs by producing production-ready code | Yes | Yes |
| **REVIEWER** | Validates correctness, security, and alignment with requirements | No | Yes |
| **DIAGNOSTICIAN** | Performs root cause analysis when other agents fail | No | Yes |

### Agent Configuration Example

```yaml
version: "1.0"
agents:
  code-reviewer:
    description: "Reviews code for quality and security"
    type: REVIEWER
    instructions: |
      Review the provided code for:
      1. Code quality and maintainability
      2. Security vulnerabilities
      3. Performance issues
      4. Adherence to best practices
    mcpServers: |
      {
        "filesystem": {
          "command": "npx",
          "args": ["-y", "@modelcontextprotocol/server-filesystem", "/project"]
        }
      }
    tools:
      - filesystem.read_file
      - filesystem.list_directory
    output_schema: '{"type": "object", "properties": {"findings": {...}}}'
```

📖 [Full agent types documentation](./spring-agents/agent_types.md)

---

## Quick Start

### Prerequisites

- Java 21+
- Node.js 18+
- Gradle 8+
- Anthropic API key (for Claude models)
- Ollama (optional, for local LLM support)

### 1. Start the Server (spring-agents)

```bash
cd spring-agents

# Set required environment variables
export ANTHROPIC_API_KEY=your-anthropic-api-key
export AGENT_ADMIN_API_TOKEN=$(openssl rand -base64 48)
export AGENT_HASHING_SECRET_V1=$(openssl rand -base64 64)
export AGENT_JWT_SIGNING_KEY=$(openssl rand -base64 48)

# Run with local profile for development
./gradlew bootRun --args='--spring.profiles.active=local'
```

The server will start on `http://localhost:8080` with WebSocket endpoint at `ws://localhost:8080/agent`.

### 2. Start the Admin Portal (admin-client-spring-agents)

```bash
cd admin-client-spring-agents/admin-ui

# Install dependencies
npm install

# Run development server
npm run dev
```

The admin portal will be available at `http://localhost:3000`.

### 3. Create a Customer and Get API Token

Using the admin portal or API:

```bash
# Create a customer
curl -X POST http://localhost:8080/api/customers \
  -H "Authorization: Bearer $AGENT_ADMIN_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "My Development Team"}'
```

Save the returned `apiToken` for the agent-client.

### 4. Start the Agent Client

```bash
cd agent-client

# Set environment variables
export AGENT_API_KEY=<api-token-from-step-3>
export AGENT_SERVER_URL=ws://localhost:8080/agent

# Run the client
./gradlew bootRun
```

### 5. Configure MCP Servers

Create `mcp.json` in the agent-client resources:

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/project"]
    },
    "terminal": {
      "command": "npx",
      "args": ["-y", "@anthropic/mcp-server-terminal"]
    }
  }
}
```

---

## Communication Flow

```
1. Agent-Client connects via WebSocket with API key
         │
         ▼
2. Server validates API key, creates connection
         │
         ▼
3. Server sends ConnectionEstablished with capabilities
         │
         ▼
4. Client sends CreateSession with agent config and tools
         │
         ▼
5. Server validates session, sends SessionStarted
         │
         ▼
6. Server executes prompt via LLM (Anthropic/Ollama)
         │
         ▼
7. When LLM needs a tool:
   ┌─────────────────────────────────────────┐
   │ a. Server sends ToolCallRequest         │
   │ b. Client executes tool locally via MCP │
   │ c. Client sends ToolCallResponse        │
   │ d. Server continues LLM conversation    │
   └─────────────────────────────────────────┘
         │
         ▼
8. Server sends SessionResult with final response
         │
         ▼
9. Session cleanup on both sides
```

---

## Security Model

| Layer | Mechanism |
|-------|-----------|
| **Authentication** | API keys (hashed with versioned secrets) |
| **Authorization** | Per-customer model allowances |
| **Rate Limiting** | Per-customer, per-minute limits |
| **Token Management** | JWT with configurable expiration |
| **Audit Trail** | Comprehensive security event logging |
| **Secret Rotation** | Versioned hashing secrets for zero-downtime rotation |

### API Key Authentication

```bash
# WebSocket connection with API key header
websocat -H="X-API-Key: your-api-key" "ws://localhost:8080/agent"

# Or via query parameter
websocat "ws://localhost:8080/agent?apiKey=your-api-key"
```

### Admin API Authentication

```bash
curl -H "Authorization: Bearer $AGENT_ADMIN_API_TOKEN" \
  http://localhost:8080/api/admin/stats
```

---

## Observability

### Metrics Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/metrics` | All metrics |

### Key Metrics

- `mcp.connections.active` - Active WebSocket connections
- `mcp.sessions.active` - Active prompt sessions
- `mcp.prompts.duration` - Prompt execution duration
- `mcp.tool_calls.duration` - Tool call execution duration
- `mcp.messages.received` / `mcp.messages.sent` - Message counts

### Grafana Dashboards

Pre-configured dashboards are available in `spring-agents/docker/local/monitoring/grafana/dashboards/`:

- `spring-boot.json` - Spring Boot application metrics
- `agents.json` - Agent-specific metrics

### Local Monitoring Stack

```bash
cd spring-agents/docker/local
docker-compose up -d
```

This starts:
- Prometheus (metrics collection)
- Grafana (visualization)

---

## SDLC Workflow Examples

### Developer Workflow: Feature Implementation

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   ANALYST   │ ──► │  ENGINEER   │ ──► │  REVIEWER   │
│             │     │             │     │             │
│ • Analyze   │     │ • Write     │     │ • Review    │
│   requirements    │   code      │     │   code      │
│ • Create    │     │ • Add tests │     │ • Check     │
│   design    │     │             │     │   security  │
└─────────────┘     └─────────────┘     └─────────────┘
```

### QA Workflow: Bug Investigation

```
┌─────────────┐     ┌───────────────┐
│   ANALYST   │ ──► │ DIAGNOSTICIAN │
│             │     │               │
│ • Analyze   │     │ • Root cause  │
│   bug report│     │   analysis    │
│ • Gather    │     │ • Remediation │
│   context   │     │   steps       │
└─────────────┘     └───────────────┘
```

### DevOps Workflow: Incident Response

```
┌───────────────┐     ┌─────────────┐
│ DIAGNOSTICIAN │ ──► │   ANALYST   │
│               │     │             │
│ • Analyze     │     │ • Create    │
│   logs/errors │     │   fix plan  │
│ • Identify    │     │ • Document  │
│   root cause  │     │   findings  │
└───────────────┘     └─────────────┘
```

---

## Project Structure

```
server-agents/
├── spring-agents/           # Server application
│   ├── src/main/java/       # Java source code
│   ├── src/main/resources/  # Configuration files
│   ├── docker/local/        # Local development Docker setup
│   └── README.md
│
├── agent-client/            # Client application
│   ├── src/main/java/       # Java source code
│   ├── src/main/resources/  # Configuration files
│   └── README.md
│
├── admin-client-spring-agents/  # Admin portal
│   └── admin-ui/                # Next.js application
│       ├── src/app/             # App router pages
│       ├── src/components/      # React components
│       ├── src/lib/             # Utilities and API client
│       └── README.md
│
├── agent-message-protocol/  # Shared message library
│   ├── src/main/java/       # Protocol definitions
│   └── README.md
│
└── README.md                # This file
```

---

## Development

### Building All Components

```bash
# Build spring-agents
cd spring-agents && ./gradlew build

# Build agent-client
cd agent-client && ./gradlew build

# Build agent-message-protocol
cd agent-message-protocol && ./gradlew build

# Build admin-ui
cd admin-client-spring-agents/admin-ui && npm run build
```

### Running Tests

```bash
# Server tests
cd spring-agents && ./gradlew test

# Client tests
cd agent-client && ./gradlew test

# Protocol tests
cd agent-message-protocol && ./gradlew test
```

### Publishing Protocol Library

```bash
cd agent-message-protocol
./gradlew publishToMavenLocal
```

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.

```
Copyright 2024 David Parry

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## Support

For questions, issues, or feature requests, please open an issue in the repository.
