package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Sent by server when session is created.
 * The sessionId is the client-generated identifier that was provided in CreateSession.
 * This class is immutable and thread-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SessionStarted extends McpProxyMessage {

    private final String sessionId;
    private final int toolCount;

    @JsonCreator
    public SessionStarted(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("toolCount") int toolCount) {
        super(messageId, timestamp);
        this.sessionId = sessionId;
        this.toolCount = toolCount;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getToolCount() {
        return toolCount;
    }

    @Override
    public String toString() {
        return "SessionStarted{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", sessionId='" + sessionId + '\'' +
                ", toolCount=" + toolCount +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageId;
        private Instant timestamp;
        private String sessionId;
        private int toolCount;

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

        public Builder toolCount(int toolCount) {
            this.toolCount = toolCount;
            return this;
        }

        public SessionStarted build() {
            return new SessionStarted(
                    messageId,
                    timestamp,
                    sessionId,
                    toolCount
            );
        }
    }
}
