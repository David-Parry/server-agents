package com.davidparry.agent.protocol;

import com.davidparry.agent.protocol.dto.ToolDefinition;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Sent by client to create a new session with tools and agent configuration.
 * The client generates the sessionId which becomes the authoritative identifier for the session.
 * This class is immutable and thread-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CreateSession extends McpProxyMessage {

    private final String sessionId;
    private final Agent agent;
    private final String schema;
    private final List<ToolDefinition> tools;
    private final boolean streamResponse;
    private final int maxDurationSeconds;
    private final JsonNode promptParams;

    @JsonCreator
    public CreateSession(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("agent") Agent agent,
            @JsonProperty("schema") String schema,
            @JsonProperty("tools") List<ToolDefinition> tools,
            @JsonProperty("streamResponse") boolean streamResponse,
            @JsonProperty("maxDurationSeconds") int maxDurationSeconds,
            @JsonProperty("promptParams") JsonNode promptParams) {
        super(messageId, timestamp);
        this.sessionId = sessionId;
        this.agent = agent;
        this.schema = schema;
        this.tools = tools != null
                ? List.copyOf(tools)
                : Collections.emptyList();
        this.streamResponse = streamResponse;
        this.maxDurationSeconds = maxDurationSeconds;
        this.promptParams = promptParams != null
                ? promptParams
                : JsonNodeFactory.instance.objectNode();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Agent getAgent() {
        return agent;
    }

    public String getSchema() {
        return schema;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public boolean isStreamResponse() {
        return streamResponse;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public JsonNode getPromptParams() {
        return promptParams;
    }

    @Override
    public String toString() {
        return "CreateSession{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", sessionId='" + sessionId + '\'' +
                ", agent=" + agent +
                ", schema='" + schema + '\'' +
                ", tools=" + tools.size() + " tools" +
                ", streamResponse=" + streamResponse +
                ", maxDurationSeconds=" + maxDurationSeconds +
                ", promptParams=" + promptParams +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageId;
        private Instant timestamp;
        private String sessionId;
        private Agent agent;
        private String schema;
        private List<ToolDefinition> tools;
        private boolean streamResponse;
        private int maxDurationSeconds;
        private JsonNode promptParams;

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

        public Builder agent(Agent agent) {
            this.agent = agent;
            return this;
        }

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder tools(List<ToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        public Builder streamResponse(boolean streamResponse) {
            this.streamResponse = streamResponse;
            return this;
        }

        public Builder maxDurationSeconds(int maxDurationSeconds) {
            this.maxDurationSeconds = maxDurationSeconds;
            return this;
        }

        public Builder promptParams(JsonNode promptParams) {
            this.promptParams = promptParams;
            return this;
        }

        public CreateSession build() {
            return new CreateSession(
                    messageId,
                    timestamp,
                    sessionId,
                    agent,
                    schema,
                    tools,
                    streamResponse,
                    maxDurationSeconds,
                    promptParams
            );
        }
    }
}
