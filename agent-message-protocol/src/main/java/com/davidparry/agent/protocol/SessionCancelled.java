package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Session cancelled confirmation.
 * This class is immutable and thread-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SessionCancelled extends McpProxyMessage {

    private final String sessionId;
    private final String reason;
    private final int toolCallsCompleted;
    private final int toolCallsPending;

    @JsonCreator
    public SessionCancelled(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("reason") String reason,
            @JsonProperty("toolCallsCompleted") int toolCallsCompleted,
            @JsonProperty("toolCallsPending") int toolCallsPending) {
        super(messageId, timestamp);
        this.sessionId = sessionId;
        this.reason = reason;
        this.toolCallsCompleted = toolCallsCompleted;
        this.toolCallsPending = toolCallsPending;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getReason() {
        return reason;
    }

    public int getToolCallsCompleted() {
        return toolCallsCompleted;
    }

    public int getToolCallsPending() {
        return toolCallsPending;
    }

    @Override
    public String toString() {
        return "SessionCancelled{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", sessionId='" + sessionId + '\'' +
                ", reason='" + reason + '\'' +
                ", toolCallsCompleted=" + toolCallsCompleted +
                ", toolCallsPending=" + toolCallsPending +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageId;
        private Instant timestamp;
        private String sessionId;
        private String reason;
        private int toolCallsCompleted;
        private int toolCallsPending;

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

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder toolCallsCompleted(int toolCallsCompleted) {
            this.toolCallsCompleted = toolCallsCompleted;
            return this;
        }

        public Builder toolCallsPending(int toolCallsPending) {
            this.toolCallsPending = toolCallsPending;
            return this;
        }

        public SessionCancelled build() {
            return new SessionCancelled(
                    messageId,
                    timestamp,
                    sessionId,
                    reason,
                    toolCallsCompleted,
                    toolCallsPending
            );
        }
    }
}
