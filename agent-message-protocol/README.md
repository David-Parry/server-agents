# Agent Message Protocol

Shared message protocol library for WebSocket communication between agent client and server components.

## Protocol Specification

The complete protocol specification is documented in **[PROTOCOL.md](PROTOCOL.md)**, which includes:

- **Message catalog** -- all 11 message types with wire format, field tables, and JSON examples
- **Sequence diagrams** -- session lifecycle, tool execution, agent chaining, cancellation, and heartbeat flows
- **State machine** -- session state transitions from connection through completion
- **Shared data types** -- Agent, ToolDefinition, Capabilities, SessionMetrics, and all enumerations (ErrorCode, ChunkType, AgentType, NextAgentStatus)
- **MCP server configuration** -- Stdio, HTTP, and SSE transport schemas

A machine-readable **[AsyncAPI 3.0.0 specification](asyncapi.yaml)** is also provided for code generation, validation, and interactive documentation tooling.

## Overview

This library provides:
- Immutable message classes with builder pattern
- Jackson serialization/deserialization support
- Polymorphic message type handling
- Thread-safe implementations

## Installation

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.davidparry.agent:message-protocol:1.0.0")
}
```

### Gradle (Groovy)

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation "com.davidparry.agent:message-protocol:1.0.0"
}
```

## Usage

### Creating Messages

```java
import com.davidparry.agent.protocol.*;
import com.davidparry.agent.protocol.dto.*;

// Create a session
CreateSession createSession = CreateSession.builder()
    .sessionId(UUID.randomUUID().toString())
    .prompt("Hello, world!")
    .model("claude-sonnet-4-5")
    .streamResponse(true)
    .tools(List.of(
        ToolDefinition.builder()
            .name("read_file")
            .description("Read a file")
            .inputSchema(Map.of("type", "object"))
            .build()
    ))
    .build();

// Session result
SessionResult result = SessionResult.builder()
    .sessionId(sessionId)
    .success(true)
    .content("Operation completed successfully")
    .toolCallsExecuted(3)
    .totalDurationMs(1500)
    .metrics(SessionMetrics.builder()
        .durationMs(1500)
        .toolCallCount(3)
        .tokenCount(500)
        .build())
    .build();
```

### JSON Serialization

```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());

// Serialize
String json = mapper.writeValueAsString(createSession);

// Deserialize (polymorphic)
McpProxyMessage message = mapper.readValue(json, McpProxyMessage.class);
```

## Message Types

### Server → Client
- `ConnectionEstablished` - Connection confirmation with capabilities
- `SessionStarted` - Session creation confirmation
- `ToolCallRequest` - Request to execute a tool
- `StreamChunk` - Streaming response chunk
- `SessionResult` - Final session result
- `SessionCancelled` - Session cancellation confirmation
- `ErrorMessage` - Error notification

### Client → Server
- `CreateSession` - Create a new session
- `ToolCallResponse` - Tool execution result
- `CancelSession` - Cancel an active session

### Bidirectional
- `Heartbeat` - Keep-alive message

## Building

```bash
./gradlew clean build
./gradlew publishToMavenLocal
```