# High-Level Architecture: Client Compute vs SaaS WebSocket Platform

> For a quick-start guide, component overview, and SDLC workflow examples, see the [main README](./README.md).

## Overview

This platform separates concerns between **Client Compute (customer
environment)** and **SaaS infrastructure**. The SaaS platform
(`spring-agents`) hosts the API, WebSocket server, LLM integration, and
session orchestration. The **Agent-SDK runs as a sandboxed process on
the customer's own compute environment**, executing tools against local
code, files, MCP servers and customized API's and resources. Customer credentials and secrets **never
leave the client environment**.

------------------------------------------------------------------------

## Architecture Diagram

``` mermaid
flowchart TB
  subgraph Customer_Env["Customer Compute Environment (Sandbox)"]
    direction TB
    SDK["Agent-SDK\n(Spring Boot Client)"]
    McpMgr["MCP Server Manager"]
    SessionMgr["Session MCP Manager\n(per-session isolation)"]

    subgraph MCP_Servers["Local MCP Servers"]
      STDIO["STDIO Servers\n(filesystem, terminal, git)"]
      HTTP_MCP["HTTP Servers"]
      SSE_MCP["SSE Servers"]
    end

    subgraph Local_Resources["Local Resources"]
      Codebase["Local Codebase / Repos"]
      Tools["Developer Tools / CLI"]
      Secrets["Customer Credentials\n& Secrets"]
    end

    SDK --> McpMgr
    McpMgr --> SessionMgr
    SessionMgr --> STDIO
    SessionMgr --> HTTP_MCP
    SessionMgr --> SSE_MCP
    STDIO --> Codebase
    STDIO --> Tools
    SDK -.->|"secrets never leave\ncustomer environment"| Secrets
  end

  subgraph Customer_Infra["Remote Customer Infrastructure"]
    CustDB["Databases\n(PostgreSQL, MySQL, MongoDB, etc.)"]
    SharedFiles["Shared File Systems\n(NFS, S3, Object Storage)"]
    CustomApps["Custom Applications\n(Internal APIs, Microservices)"]
    CustCI["CI/CD Pipelines\n(Jenkins, GitHub Actions, etc.)"]
  end

  MCP_Servers -->|"customer network"| CustDB
  MCP_Servers -->|"customer network"| SharedFiles
  MCP_Servers -->|"customer network"| CustomApps
  MCP_Servers -->|"customer network"| CustCI

  subgraph SaaS_Env["SaaS Platform Environment"]
    direction TB
    APIGW["API Gateway\n(/agent WebSocket endpoint)"]
    Auth["API Key Authentication\n& Tenant Validation"]
    ConnMgr["Connection Manager"]
    SessionOrch["Session Orchestrator"]
    LLM["LLM Integration\n(Anthropic Claude / Ollama)"]
    AdminAPI["Admin REST API"]

    subgraph Persistence["Persistence & Observability"]
      DB["Database\n(H2 / PostgreSQL)"]
      Metrics["Prometheus / Grafana"]
      AuditLog["Security Audit Log"]
    end

    APIGW --> Auth
    Auth --> ConnMgr
    ConnMgr --> SessionOrch
    SessionOrch --> LLM
    AdminAPI --> DB
    SessionOrch --> DB
    SessionOrch --> AuditLog
    SessionOrch --> Metrics
  end

  subgraph Admin_Env["Admin Portal"]
    AdminUI["Next.js Admin UI"]
  end

  SDK <-->|"WebSocket over TLS\n(persistent bidirectional)"| APIGW
  LLM -->|"ToolCallRequest"| SessionOrch
  SessionOrch -->|"ToolCallRequest"| SDK
  SDK -->|"ToolCallResponse"| SessionOrch
  SessionOrch -->|"tool result"| LLM
  AdminUI -->|"REST API\n(Bearer Token)"| AdminAPI
```

------------------------------------------------------------------------

## Components & Environments

### Customer Compute Environment (Sandbox)

> **The Agent runs entirely within the customer's own infrastructure.**
> No customer code, secrets, or filesystem access is exposed to the SaaS
> platform. The SaaS side only sends orchestration messages; all tool
> execution happens locally. The sandbox can reach out to remote customer
> infrastructure (databases, shared files, internal APIs) over the
> customer's own network -- these connections never traverse the SaaS
> platform.

| Component | Role |
|---|---|
| **Agent-SDK** | Spring Boot client that establishes a WebSocket connection to the SaaS platform, manages local MCP servers, and executes tool calls on behalf of the LLM. Runs on port 8081. |
| **MCP Server Manager** | Discovers, starts, and manages local MCP servers (STDIO, HTTP, SSE transports) based on `mcp.json` configuration. |
| **Session MCP Manager** | Provides per-session isolation so concurrent sessions each get independent MCP server instances. |
| **Local MCP Servers** | STDIO-based servers for filesystem, terminal, and git operations; HTTP/SSE servers for extended integrations. These run as child processes on customer compute. |
| **Customer Code & Tools** | Local repositories, developer toolchains, and CLI utilities accessed exclusively by the MCP servers. |
| **Customer Secrets** | API keys, credentials, and environment variables that remain on customer infrastructure and are **never transmitted** to the SaaS platform. |
| **Remote Customer Databases** | Customer-owned databases (PostgreSQL, MySQL, MongoDB, etc.) accessible from the sandbox over the customer's network. MCP servers can query and mutate data as part of tool execution. |
| **Shared File Systems** | Network-attached storage, NFS mounts, S3 buckets, or object storage accessible within the customer's infrastructure for reading/writing shared artifacts. |
| **Custom Applications** | Internal APIs, microservices, and line-of-business applications that MCP servers can interact with over the customer's private network. |
| **CI/CD Pipelines** | Build and deployment systems (Jenkins, GitHub Actions, GitLab CI, etc.) that MCP servers can trigger or query as part of agent workflows. |

### SaaS Platform Environment

| Component | Role |
|---|---|
| **spring-agents** | Spring Boot server (port 8080) hosting the WebSocket endpoint (`/agent`), LLM integration, session orchestration, and admin APIs. |
| **API Key Authentication** | Validates customer API keys (hashed with versioned secrets) during WebSocket handshake via `X-API-Key` header. |
| **Connection Manager** | Tracks active WebSocket connections per tenant with rate limiting and concurrent session limits. |
| **Session Orchestrator** | Coordinates prompt execution: sends prompts to LLMs, routes `ToolCallRequest` messages to the connected Agent-SDK, and feeds `ToolCallResponse` results back to the LLM. |
| **LLM Integration** | Connects to Anthropic Claude (via Spring AI) and Ollama. Manages per-customer model allowances and token usage tracking. |
| **Database** | H2 (development) or PostgreSQL (production) with Flyway migrations. Stores customers, tokens, model allowances, agent configs, policies, and audit logs. |
| **Observability** | Prometheus metrics, Grafana dashboards, and a security audit log for all authentication and session events. |

### Admin Portal

| Component | Role |
|---|---|
| **Next.js Admin UI** | Web application (port 3000) for managing customers, models, token policies, and viewing audit logs. Communicates with `spring-agents` via REST API with Bearer token auth. |

------------------------------------------------------------------------

## Shared Protocol: agent-message-protocol

A shared Java library defining all WebSocket message types exchanged
between the Agent-SDK and spring-agents:

| Direction | Message | Purpose |
|---|---|---|
| Server -> Client | `ConnectionEstablished` | Confirms connection with capabilities |
| Server -> Client | `SessionStarted` | Confirms session creation |
| Server -> Client | `ToolCallRequest` | Requests local tool execution |
| Server -> Client | `StreamChunk` | Streams partial LLM response |
| Server -> Client | `SessionResult` | Final LLM response for the session |
| Server -> Client | `SessionCancelled` | Confirms cancellation |
| Server -> Client | `ErrorMessage` | Error notification |
| Client -> Server | `CreateSession` | Starts a new session (agent type, tools, model, prompt) |
| Client -> Server | `ToolCallResponse` | Returns tool execution result |
| Client -> Server | `CancelSession` | Cancels an active session |
| Bidirectional | `Heartbeat` | Keep-alive (30s interval) |

------------------------------------------------------------------------

## Agent Orchestration Flow

``` mermaid
sequenceDiagram
    participant EVE as Event
    participant SDK as Agent-SDK<br/>(Customer Sandbox)
    participant MCP as Local MCP Servers<br/>(Customer Sandbox)
    participant Infra as Remote Customer Infra<br/>(DBs, Apps, Files)
    participant WS as spring-agents<br/>(SaaS)
    participant LLM as LLM<br/>(Claude / Ollama)

    EVE->>SDK: trigger task
    SDK->>SDK: discover tools from local MCP servers
    SDK->>WS: CreateSession (agent type, tools, prompt, model)
    WS->>WS: authenticate & validate allowance
    WS-->>SDK: SessionStarted

    WS->>LLM: prompt + available tool definitions
    LLM-->>WS: tool_use (call a tool)

    WS-->>SDK: ToolCallRequest
    SDK->>MCP: execute tool locally
    MCP->>MCP: access local code / files / git
    opt tool requires remote customer resources
        MCP->>Infra: query DB / call API / read shared files
        Infra-->>MCP: result (over customer network)
    end
    MCP-->>SDK: tool result
    SDK-->>WS: ToolCallResponse

    WS->>LLM: tool result
    LLM-->>WS: final response (or more tool calls)

    WS-->>SDK: SessionResult
    SDK-->>EVE: display result
```

Key points:
- **Tool execution always happens in the customer sandbox.** The SaaS
  platform never directly accesses customer code or infrastructure.
- The LLM decides which tools to call; the SaaS platform routes those
  requests to the Agent-SDK; the Agent-SDK executes them locally via MCP
  servers.
- Multiple tool call rounds can occur within a single session.

------------------------------------------------------------------------

## Environment & Deployment Summary

``` mermaid
%%{init: {'theme': 'dark', 'themeVariables': {'lineColor': '#a0aec0'}}}%%
flowchart TB
  subgraph sandbox["Customer Compute (Sandbox)"]
    direction LR
    sdk["Agent-SDK"]
    mcp["MCP Servers\n(STDIO/HTTP/SSE)"]
    code["Code / Tools / Secrets"]
    sdk --- mcp --- code
  end

  subgraph custinfra["Remote Customer Infrastructure"]
    direction LR
    dbs["Databases"]
    files["Shared Files"]
    apps["Custom Apps"]
    ci["CI/CD"]
  end

  mcp -->|"customer network"| custinfra

  subgraph saas["SaaS Platform"]
    direction LR
    server["spring-agents"]
    db["H2 / PostgreSQL"]
    mon["Prometheus :9090\nGrafana :3001"]
    server --- db
    server --- mon
  end

  subgraph admin["Admin"]
    ui["Next.js Admin UI"]
  end

  sdk <-->|"WSS"| server
  ui -->|"REST"| server

  style sandbox fill:#2d4a3e,stroke:#5a9e7a,stroke-width:3px,color:#c8e6d0
  style custinfra fill:#3b4a3e,stroke:#5a9e7a,stroke-width:2px,stroke-dasharray: 5 5,color:#c8e6d0
  style saas fill:#2d3e5a,stroke:#5a7fb0,stroke-width:2px,color:#b8d0e8
  style admin fill:#4a3d2d,stroke:#b08a5a,stroke-width:2px,color:#e8d8c0

  style sdk fill:#3a6b52,stroke:#7bc4a0,color:#e0f0e8
  style mcp fill:#3a6b52,stroke:#7bc4a0,color:#e0f0e8
  style code fill:#3a6b52,stroke:#7bc4a0,color:#e0f0e8
  style dbs fill:#4a5b42,stroke:#7bc4a0,color:#e0f0e8
  style files fill:#4a5b42,stroke:#7bc4a0,color:#e0f0e8
  style apps fill:#4a5b42,stroke:#7bc4a0,color:#e0f0e8
  style ci fill:#4a5b42,stroke:#7bc4a0,color:#e0f0e8
  style server fill:#3a4f6b,stroke:#7ba0c4,color:#e0e8f0
  style db fill:#3a4f6b,stroke:#7ba0c4,color:#e0e8f0
  style mon fill:#3a4f6b,stroke:#7ba0c4,color:#e0e8f0
  style ui fill:#5a4a32,stroke:#c4a060,color:#f0e8d0
```

| Environment | Runs On | Components | Key Ports |
|---|---|---|---|
| **Customer Compute (Sandbox)** | Customer's own machine / infrastructure | Agent-SDK, MCP Servers, local code & tools, customer secrets | 8081 (SDK REST API) |
| **Remote Customer Infrastructure** | Customer's private network / cloud | Databases, shared file systems, custom applications, CI/CD pipelines | Customer-defined |
| **SaaS Platform** | Hosted cloud infrastructure | spring-agents, database, monitoring stack | 8080 (API + WebSocket) |
| **Admin Portal** | Hosted alongside SaaS or separately | Next.js Admin UI | 3000 |
| **Monitoring** | Docker (alongside SaaS) | Prometheus, Grafana | 9090, 3001 |

------------------------------------------------------------------------

## Security Boundaries

- **Customer secrets never cross the WebSocket boundary.** Environment
  variables, credentials, and API keys used by MCP servers stay in the
  customer sandbox.
- **API key authentication** with hashed, versioned secrets secures the
  WebSocket handshake.
- **Per-customer rate limiting** and **token allowances** prevent abuse.
- **Security audit logging** records all authentication and session
  events on the SaaS side.
- **Tool execution is sandboxed** to the customer environment -- the SaaS
  platform can only request tool calls, never execute them directly.
- **Remote customer infrastructure** (databases, shared files, custom
  applications, CI/CD) is accessed exclusively from within the customer
  sandbox over the customer's own network. These connections **never
  route through the SaaS platform**.
