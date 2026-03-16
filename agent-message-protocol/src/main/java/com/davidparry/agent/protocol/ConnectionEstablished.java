package com.davidparry.agent.protocol;

import com.davidparry.agent.protocol.dto.Capabilities;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Sent by server when WebSocket connection is established.
 * This class is immutable and thread-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConnectionEstablished extends McpProxyMessage {

    private final String connectionId;
    private final String serverVersion;
    private final int maxConcurrentSessions;
    private final Capabilities capabilities;

    @JsonCreator
    public ConnectionEstablished(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("connectionId") String connectionId,
            @JsonProperty("serverVersion") String serverVersion,
            @JsonProperty("maxConcurrentSessions") int maxConcurrentSessions,
            @JsonProperty("capabilities") Capabilities capabilities) {
        super(messageId, timestamp);
        this.connectionId = connectionId;
        this.serverVersion = serverVersion;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.capabilities = capabilities;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public String toString() {
        return "ConnectionEstablished{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", connectionId='" + connectionId + '\'' +
                ", serverVersion='" + serverVersion + '\'' +
                ", maxConcurrentSessions=" + maxConcurrentSessions +
                ", capabilities=" + capabilities +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageId;
        private Instant timestamp;
        private String connectionId;
        private String serverVersion;
        private int maxConcurrentSessions;
        private Capabilities capabilities;

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

        public Builder connectionId(String connectionId) {
            this.connectionId = connectionId;
            return this;
        }

        public Builder serverVersion(String serverVersion) {
            this.serverVersion = serverVersion;
            return this;
        }

        public Builder maxConcurrentSessions(int maxConcurrentSessions) {
            this.maxConcurrentSessions = maxConcurrentSessions;
            return this;
        }

        public Builder capabilities(Capabilities capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public ConnectionEstablished build() {
            return new ConnectionEstablished(
                    messageId,
                    timestamp,
                    connectionId,
                    serverVersion,
                    maxConcurrentSessions,
                    capabilities
            );
        }
    }
}
