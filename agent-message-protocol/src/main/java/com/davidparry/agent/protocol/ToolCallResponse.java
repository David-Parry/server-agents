package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Sent by client with tool execution result.
 * This class is immutable and thread-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ToolCallResponse extends McpProxyMessage {

    private final String sessionId;
    private final String requestId;
    private final boolean success;
    private final String result;
    private final String errorMessage;
    private final long executionTimeMs;

    @JsonCreator
    public ToolCallResponse(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("requestId") String requestId,
            @JsonProperty("success") boolean success,
            @JsonProperty("result") String result,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("executionTimeMs") long executionTimeMs) {
        super(messageId, timestamp);
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.success = success;
        this.result = result;
        this.errorMessage = errorMessage;
        this.executionTimeMs = executionTimeMs;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    @Override
    public String toString() {
        return "ToolCallResponse{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", sessionId='" + sessionId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", success=" + success +
                ", result='" + result + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", executionTimeMs=" + executionTimeMs +
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
        private boolean success;
        private String result;
        private String errorMessage;
        private long executionTimeMs;

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

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public ToolCallResponse build() {
            return new ToolCallResponse(
                    messageId,
                    timestamp,
                    sessionId,
                    requestId,
                    success,
                    result,
                    errorMessage,
                    executionTimeMs
            );
        }
    }
}
