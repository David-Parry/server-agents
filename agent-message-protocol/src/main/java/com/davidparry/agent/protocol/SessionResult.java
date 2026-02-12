package com.davidparry.agent.protocol;

import com.davidparry.agent.protocol.dto.SessionMetrics;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Final session result.
 * The sessionId is the client-generated identifier that was provided in CreateSession.
 * This class is immutable and thread-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SessionResult extends McpProxyMessage {

    private final String sessionId;
    private final boolean success;
    private final JsonNode content;  // Primary field name (accepts "response" for backward compatibility)
    private final String errorMessage;
    private final int toolCallsExecuted;
    private final long totalDurationMs;
    private final SessionMetrics metrics;
    private final String nextAgent;

    @JsonCreator
    public SessionResult(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("success") boolean success,
            @JsonProperty("content") @JsonAlias("response") JsonNode content,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("toolCallsExecuted") int toolCallsExecuted,
            @JsonProperty("totalDurationMs") long totalDurationMs,
            @JsonProperty("metrics") SessionMetrics metrics,
            @JsonProperty("next_agent") String nextAgent) {
        super(messageId, timestamp);
        this.sessionId = sessionId;
        this.success = success;
        this.content = content;
        this.errorMessage = errorMessage;
        this.toolCallsExecuted = toolCallsExecuted;
        this.totalDurationMs = totalDurationMs;
        this.metrics = metrics;
        this.nextAgent = nextAgent;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isSuccess() {
        return success;
    }

    public JsonNode getContent() {
        return content;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getToolCallsExecuted() {
        return toolCallsExecuted;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public SessionMetrics getMetrics() {
        return metrics;
    }

    public String getNextAgent() {
        return nextAgent;
    }

    @Override
    public String toString() {
        return "SessionResult{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", sessionId='" + sessionId + '\'' +
                ", success=" + success +
                ", content='" + content + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", toolCallsExecuted=" + toolCallsExecuted +
                ", totalDurationMs=" + totalDurationMs +
                ", metrics=" + metrics +
                ", nextAgent='" + nextAgent + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageId;
        private Instant timestamp;
        private String sessionId;
        private boolean success;
        private JsonNode content;
        private String errorMessage;
        private int toolCallsExecuted;
        private long totalDurationMs;
        private SessionMetrics metrics;
        private String nextAgent;

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

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder content(JsonNode content) {
            this.content = content;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder toolCallsExecuted(int toolCallsExecuted) {
            this.toolCallsExecuted = toolCallsExecuted;
            return this;
        }

        public Builder totalDurationMs(long totalDurationMs) {
            this.totalDurationMs = totalDurationMs;
            return this;
        }

        public Builder metrics(SessionMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder nextAgent(String nextAgent) {
            this.nextAgent = nextAgent;
            return this;
        }

        public SessionResult build() {
            return new SessionResult(
                    messageId,
                    timestamp,
                    sessionId,
                    success,
                    content,
                    errorMessage,
                    toolCallsExecuted,
                    totalDurationMs,
                    metrics,
                    nextAgent
            );
        }
    }
}
