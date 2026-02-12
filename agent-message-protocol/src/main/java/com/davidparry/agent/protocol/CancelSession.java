package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Cancel an in-progress session.
 * This class is immutable and thread-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CancelSession extends McpProxyMessage {

    private final String sessionId;
    private final String reason;

    @JsonCreator
    public CancelSession(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("reason") String reason) {
        super(messageId, timestamp);
        this.sessionId = sessionId;
        this.reason = reason;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "CancelSession{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", sessionId='" + sessionId + '\'' +
                ", reason='" + reason + '\'' +
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

        public CancelSession build() {
            return new CancelSession(
                    messageId,
                    timestamp,
                    sessionId,
                    reason
            );
        }
    }
}
