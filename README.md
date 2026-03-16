# Server-Agents Platform

A comprehensive **AI Agent Platform** designed to enable remote AI agents to augment and automate workflows across the **Software Development Lifecycle (SDLC)**. The platform follows a client-server architecture where AI-powered agents assist developers, QA engineers, and other contributors with intelligent automation.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Components](#components)
- [Agent Types](#agent-types)
- [Quick Start](#quick-start)
- [Observability](#observability)
- [SDLC Workflow Examples](#sdlc-workflow-examples)
- [License](#license)

---

## Overview

**Server-Agents** provides an enterprise-grade platform for AI-assisted software development:

| Component | Purpose |
|-----------|---------|
| **spring-agents** | Central server hosting LLM integrations (Anthropic Claude, Ollama) with customer management, token usage tracking, and policy enforcement |
| **agent-sdk** | Runs on developer machines, connecting to the server via WebSocket and executing tools locally through the Model Context Protocol (MCP) |
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

``` mermaid
flowchart TB
  subgraph Admin["Admin"]
    C1["Admin Portal"]
  end

  subgraph SaaS["SaaS Platform"]
    direction LR
    B1["WebSocket Server\n+ Session Orchestration"]
    B2["LLM\n(Claude / Ollama)"]
    B3["Auth / State\n/ Telemetry"]
    B1 --> B2
    B1 --> B3
  end

  subgraph Client["Customer Compute (Sandbox)"]
    direction LR
    A1["Agent-SDK"]
    A2["Local MCP Servers\n+ Code + Tools"]
    A1 --> A2
  end

  subgraph CustInfra["Remote Customer Infrastructure"]
    D1["Databases / Shared Files\nCustom Apps / CI Pipelines"]
  end

  C1 -->|"REST"| SaaS
  A1 <-->|"Secure WebSocket"| B1
  A2 -->|"customer network"| D1
```

The platform separates concerns between **Client Compute** (customer environment) and **SaaS infrastructure**. The Agent-SDK runs as a sandboxed process on the customer's own compute, executing tools against local code, files, and MCP servers. Customer credentials and secrets **never leave the client environment**.

📖 [In-depth architecture, security boundaries, and orchestration flows](./client_saas_architecture.md)

---

## Components

### spring-agents (Server)

The central hub that orchestrates AI agent interactions.

| Feature | Description |
|---------|-------------|
| WebSocket Server | Accepts connections at `/agent` endpoint with API key authentication |
| Multi-LLM Support | Integrates with Anthropic (Claude) and Ollama via Spring AI |
| Remote Tool Execution | Proxies tool calls to connected agent-sdk instances |
| Customer Management | Multi-tenant support with per-customer token allowances |
| Usage Tracking | Per-model token usage with configurable reset policies |
| Security | JWT tokens, API key hashing (versioned secrets), audit logging |
| Observability | Prometheus metrics, structured logging, health endpoints |

📖 [Full spring-agents documentation](./spring-agents/README.md)

---

### agent-sdk (Client)

A Spring Boot application that runs on developer machines, bridging local tools with the remote server.

| Feature | Description |
|---------|-------------|
| WebSocket Client | Connects to spring-agents with auto-reconnect and heartbeat keep-alive |
| MCP Server Manager | Manages local MCP servers (STDIO, HTTP, SSE transports) |
| Session Isolation | Each session gets its own MCP server instances |
| Tool Execution | Executes tools locally and returns results to server |
| Configuration | YAML/JSON-based agent and MCP server configuration |

📖 [Full agent-sdk documentation](./agent-sdk/README.md)

---

### admin-client-spring-agents (Admin Portal)

A Next.js web application (Next.js 15, TypeScript, Tailwind CSS, React Query) for platform administration: customer management, model configuration, token policies, and audit log viewing.

📖 [Full admin-client-spring-agents documentation](./admin-client-spring-agents/admin-ui/README.md)
📖 [Admin Console overview (lights-out agent management)](./admin-client-spring-agents/docs/README.md)

---

### agent-message-protocol (Shared Library)

A shared Java library defining all WebSocket message types exchanged between the Agent-SDK and spring-agents.

📖 [Full agent-message-protocol documentation](./agent-message-protocol/README.md) | [Protocol details](./client_saas_architecture.md#shared-protocol-agent-message-protocol)

---

## Agent Types

Four specialized agent roles for the SDLC:

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

Create the file `spring-agents/src/main/resources/application-local.yml` (this file is git-ignored):

```yaml
agent:
  admin:
    api-token: <add your token>

  security:
    hashing-secrets:
      V1: <add your token>

    jwt:
      signing-key: <add your token>
```

You can generate secure values for these fields with:

```bash
# Generate admin API token
openssl rand -base64 48

# Generate hashing secret (minimum 32 characters)
openssl rand -base64 64

# Generate JWT signing key (minimum 32 characters for HS256)
openssl rand -base64 48
```

You also need to set the Anthropic API key as an environment variable:

```bash
export ANTHROPIC_API_KEY=your-anthropic-api-key
```

Then run with the local profile:

```bash
cd spring-agents
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

Save the returned `apiToken` for the agent-sdk.

### 4. Start the Agent Client

Create the file `agent-sdk/src/main/resources/application-local.yml` (this file is git-ignored):

```yaml
agent:
  client:
    api-key: <add your token>
    agent-config-path: /path/to/your/agent.yml
```

The `api-key` is the API token returned from step 3 when you created a customer. The `agent-config-path` points to your agent configuration file (see [Agent Configuration Example](#agent-configuration-example)).

Then run with the local profile:

```bash
cd agent-sdk
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 5. Configure MCP Servers

Create `mcp.json` in the agent-sdk resources:

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
├── agent-sdk/            # Client application
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

# Build agent-sdk
cd agent-sdk && ./gradlew build

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
cd agent-sdk && ./gradlew test

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

---

## Support

For questions, issues, or feature requests, please open an issue in the repository.
