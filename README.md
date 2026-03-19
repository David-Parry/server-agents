# Server-Agents Platform

The **server-side backbone** of this AI agent infrastructure. It orchestrates LLM interactions, enforces governance policies, and manages multi-tenant access so that customer-deployed agents operate within controlled, auditable boundaries.

The **Agent SDK** is the customer-facing runtime. It runs on customer infrastructure, connects to the Platform over a secure WebSocket, and executes tools locally via the [Model Context Protocol (MCP)](https://modelcontextprotocol.io). The platform provides **model abstraction** (customers interact with agents, not specific LLMs), **governance** (token budgets, usage policies, audit trails), and **system prompt injection safeguards** (prompts are managed server-side, not exposed to client modification).

## Problem

AI agents that participate in the SDLC — analyzing requirements, writing code, reviewing pull requests, running CI pipelines — require elevated privileges: API keys, service accounts, database credentials, and infrastructure access. A single agent effectively merges multiple permission boundaries into one execution point ([OWASP Top 10 for Agentic Applications, 2025](https://genai.owasp.org/2025/12/09/owasp-genai-security-project-releases-top-10-risks-and-mitigations-for-agentic-ai-security/)). This creates three compounding challenges:

1. **Agents must run where the secrets live.** Because these agents hold "keys to the kingdom" — production credentials, source code access, CI/CD tokens — enterprises must execute them on-premises or in controlled environments to satisfy security, compliance, and sovereignty requirements. Gartner identifies AI Sovereignty as a top strategic trend for 2026, and Forrester has formalized the [Agent Control Plane](https://www.forrester.com/blogs/announcing-our-evaluation-of-the-agent-control-plane-market/) market category around centralized governance of agent execution.

2. **The model lifecycle is operationally expensive and fast-moving.** Model selection, continuous upgrades, prompt versioning, token budget management, cost optimization, and regression testing across model versions are a full-time operational burden. Gartner predicts that over 40% of agentic AI projects will be canceled due to escalating costs and insufficient controls ([Gartner, 2025](https://www.gartner.com/en/newsroom/press-releases/2025-08-26-gartner-predicts-40-percent-of-enterprise-apps-will-feature-task-specific-ai-agents-by-2026-up-from-less-than-5-percent-in-2025)). LLMOps — prompt version control, automated evaluation, drift detection — has emerged as a distinct discipline precisely because this complexity exceeds what most teams can absorb alongside their core work.

3. **Enterprises should not have to choose between control and velocity.** A SaaS provider can own the boilerplate — model routing, prompt management, token economics, continuous model upgrades and testing — and deliver it through a lightweight SDK. The enterprise retains full execution control: agent runtimes, credentials, and tool access never leave customer infrastructure. This hybrid pattern (SaaS-managed intelligence + on-prem execution) aligns with enterprise-grade frameworks already running critical workloads — Spring AI on the JVM ([InfoQ Java Trends, 2026](https://www.javacodegeeks.com/2026/03/5-latest-java-trends-to-keep-your-eye-on-in-2026.html)), Microsoft Semantic Kernel for .NET, and Akka for distributed systems — so adoption doesn't require replatforming to a Python-only stack.

**This platform exists to solve that split:** the managed SaaS handles model orchestration, governance policy, and prompt lifecycle; the Agent SDK runs on customer infrastructure, executing tools locally via [MCP](https://modelcontextprotocol.io) with customer secrets that never cross the boundary.

---

## Table of Contents

- [Problem](#problem)
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

| Component | Purpose |
|-----------|---------|
| **spring-agents** | central orchestration server — LLM routing, session management, governance enforcement, token tracking, and policy controls |
| **agent-sdk** | Customer-deployed agent runtime — connects to the platform, receives tasks, and executes tools locally via MCP. Customer credentials never leave their environment |
| **admin-client-spring-agents** | Admin portal for managing customers, models, token policies, and viewing audit logs |
| **agent-message-protocol** | Shared message library defining the WebSocket protocol between SDK and server |

### Key Features

- **Model Abstraction**: Customers use agents without coupling to specific LLM providers; the platform routes to Anthropic (Claude), Ollama, or future providers transparently
- **Governance & Policy Enforcement**: Per-customer token budgets, configurable reset policies, and centralized audit logging
- **System Prompt Safeguards**: Agent prompts are managed server-side, preventing client-side injection or tampering
- **Secure Remote Execution**: Tool calls are proxied to the SDK for local execution — customer secrets and code never leave their infrastructure
- **MCP Integration**: Full support for Model Context Protocol servers (STDIO, HTTP, SSE)
- **Enterprise Security**: API key authentication, JWT tokens, rate limiting, security audit trails
- **Observability**: Prometheus metrics, Grafana dashboards, structured logging

---

## Architecture

``` mermaid
flowchart TB
  subgraph SaaS["SaaS"]
    direction TB
    C1["Admin Portal\n(Web UI)"]

    subgraph Platform["Platform Services"]
      direction LR
      B1["WebSocket Server\n+ Session Orchestration"]
      B2["LLM Providers\n(Claude / Ollama)"]
      B3["Auth / Governance\n/ Telemetry"]
      B1 --> B2
      B1 --> B3
    end

    C1 -->|"REST"| Platform
  end

  Internet{{"☁️ Internet"}}

  subgraph Customer["Customer Environment"]
    subgraph Compute["Customer Compute (Sandbox)"]
      direction LR
      A1["Agent-SDK\n(Runtime)"]
      A2["Local MCP Servers\n+ Code + Tools"]
      A1 --> A2
    end

    subgraph CustInfra["Customer Infrastructure"]
      D1["Databases / Shared Files\nCustom Apps / CI Pipelines"]
    end

    A2 -->|"private network"| D1
  end

  C1 <-->|"HTTPS"| Internet
  B1 <-->|"Secure WebSocket\n(WSS + API Key)"| Internet
  Internet <-->|"Secure WebSocket\n(WSS + API Key)"| A1

  linkStyle 6 stroke:red
  linkStyle 7 stroke:red
```

The **SaaS platform** runs as a managed SaaS service. The **Agent SDK** runs on customer infrastructure, connecting through the internet via secure WebSocket (WSS). Customer credentials and secrets **never leave the customer environment** — the platform only sends orchestration messages and receives tool results.

📖 [In-depth architecture, security boundaries, and orchestration flows](./client_saas_architecture.md)

---

## Components

### spring-agents (Platform Server)

The orchestration hub that manages LLM interactions, enforces governance, and routes tool calls to connected agent runtimes.

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

### agent-sdk (Customer Agent Runtime)

A Spring Boot runtime that runs on customer infrastructure, connecting to the Platform and executing tools locally. Customer credentials and secrets never leave their environment.

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
