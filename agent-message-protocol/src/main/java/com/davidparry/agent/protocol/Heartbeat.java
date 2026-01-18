package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Heartbeat message for keep-alive.
 * This class is immutable and thread-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Heartbeat extends McpProxyMessage {

    private final long sequenceNumber;
    private final boolean isResponse;

    @JsonCreator
    public Heartbeat(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("sequenceNumber") long sequenceNumber,
            @JsonProperty("isResponse") boolean isResponse) {
        super(messageId, timestamp);
        this.sequenceNumber = sequenceNumber;
        this.isResponse = isResponse;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public boolean isResponse() {
        return isResponse;
    }

    @Override
    public String toString() {
        return "Heartbeat{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", sequenceNumber=" + sequenceNumber +
                ", isResponse=" + isResponse +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageId;
        private Instant timestamp;
        private long sequenceNumber;
        private boolean isResponse;

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

        public Builder sequenceNumber(long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder isResponse(boolean isResponse) {
            this.isResponse = isResponse;
            return this;
        }

        public Heartbeat build() {
            return new Heartbeat(
                    messageId,
                    timestamp,
                    sequenceNumber,
                    isResponse
            );
        }
    }
}
