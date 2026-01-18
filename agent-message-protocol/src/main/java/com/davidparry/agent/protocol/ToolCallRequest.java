package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sent by server to request tool execution.
 * This class is immutable and thread-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ToolCallRequest extends McpProxyMessage {

    private final String sessionId;
    private final String requestId;
    private final String toolName;
    private final Instant deadline;
    private final Map<String, Object> arguments;

    @JsonCreator
    public ToolCallRequest(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("requestId") String requestId,
            @JsonProperty("toolName") String toolName,
            @JsonProperty("deadline") Instant deadline,
            @JsonProperty("arguments") @JsonDeserialize(using = StringOrMapDeserializer.class) Map<String, Object> arguments) {
        super(messageId, timestamp);
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.toolName = toolName;
        this.deadline = deadline;
        this.arguments = arguments != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(arguments))
                : Collections.emptyMap();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getToolName() {
        return toolName;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "ToolCallRequest{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", sessionId='" + sessionId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", toolName='" + toolName + '\'' +
                ", deadline=" + deadline +
                ", arguments=" + arguments +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageId;
        private Instant timestamp;
        private String sessionId;
        private String requestId;
        private String toolName;
        private Instant deadline;
        private Map<String, Object> arguments;

        private Builder() {
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder deadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public ToolCallRequest build() {
            return new ToolCallRequest(
                    messageId,
                    timestamp,
                    sessionId,
                    requestId,
                    toolName,
                    deadline,
                    arguments
            );
        }
    }
}
