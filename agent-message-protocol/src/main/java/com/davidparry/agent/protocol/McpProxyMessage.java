package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all MCP Proxy protocol messages.
 *
 * <p><b>Thread Safety:</b> All message subclasses are immutable and therefore thread-safe.
 * Once created, instances can be safely shared across threads without synchronization.
 *
 * <p>Messages are designed to be created via Jackson deserialization or through
 * their respective Builder classes for programmatic construction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    // Server -> Client messages
    @JsonSubTypes.Type(value = ConnectionEstablished.class, name = "connection_established"),
    @JsonSubTypes.Type(value = SessionStarted.class, name = "session_started"),
    @JsonSubTypes.Type(value = ToolCallRequest.class, name = "tool_call_request"),
    @JsonSubTypes.Type(value = StreamChunk.class, name = "stream_chunk"),
    @JsonSubTypes.Type(value = SessionResult.class, name = "session_result"),
    @JsonSubTypes.Type(value = SessionCancelled.class, name = "session_cancelled"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "error"),

    // Client -> Server messages
    @JsonSubTypes.Type(value = CreateSession.class, name = "create_session"),
    @JsonSubTypes.Type(value = ToolCallResponse.class, name = "tool_call_response"),
    @JsonSubTypes.Type(value = CancelSession.class, name = "cancel_session"),

    // Bidirectional messages
    @JsonSubTypes.Type(value = Heartbeat.class, name = "heartbeat")
})
public abstract class McpProxyMessage {

    private final String messageId;
    private final Instant timestamp;

    /**
     * Default constructor that generates a new messageId and timestamp.
     * Used by Jackson for deserialization and by subclass builders.
     */
    protected McpProxyMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    /**
     * Constructor with explicit messageId and timestamp.
     * Used when deserializing messages that already have these values.
     */
    protected McpProxyMessage(String messageId, Instant timestamp) {
        this.messageId = messageId != null ? messageId : UUID.randomUUID().toString();
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public String getMessageId() {
        return messageId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "messageId='" + messageId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
