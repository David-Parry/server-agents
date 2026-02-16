package com.davidparry.agent.protocol.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server capabilities sent during connection establishment.
 * This class is immutable and thread-safe.
 */
public final class Capabilities {

    private final boolean streamingSupported;
    private final int maxConcurrentSessions;
    private final int sessionTimeoutSeconds;
    private final int toolCallTimeoutSeconds;

    @JsonCreator
    public Capabilities(
            @JsonProperty("streamingSupported") boolean streamingSupported,
            @JsonProperty("maxConcurrentSessions") int maxConcurrentSessions,
            @JsonProperty("sessionTimeoutSeconds") int sessionTimeoutSeconds,
            @JsonProperty("toolCallTimeoutSeconds") int toolCallTimeoutSeconds) {
        this.streamingSupported = streamingSupported;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        this.toolCallTimeoutSeconds = toolCallTimeoutSeconds;
    }

    public boolean isStreamingSupported() {
        return streamingSupported;
    }

    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    public int getSessionTimeoutSeconds() {
        return sessionTimeoutSeconds;
    }

    public int getToolCallTimeoutSeconds() {
        return toolCallTimeoutSeconds;
    }

    @Override
    public String toString() {
        return "Capabilities{" +
                "streamingSupported=" + streamingSupported +
                ", maxConcurrentSessions=" + maxConcurrentSessions +
                ", sessionTimeoutSeconds=" + sessionTimeoutSeconds +
                ", toolCallTimeoutSeconds=" + toolCallTimeoutSeconds +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean streamingSupported;
        private int maxConcurrentSessions;
        private int sessionTimeoutSeconds;
        private int toolCallTimeoutSeconds;

        private Builder() {
        }

        public Builder streamingSupported(boolean streamingSupported) {
            this.streamingSupported = streamingSupported;
            return this;
        }

        public Builder maxConcurrentSessions(int maxConcurrentSessions) {
            this.maxConcurrentSessions = maxConcurrentSessions;
            return this;
        }

        public Builder sessionTimeoutSeconds(int sessionTimeoutSeconds) {
            this.sessionTimeoutSeconds = sessionTimeoutSeconds;
            return this;
        }

        public Builder toolCallTimeoutSeconds(int toolCallTimeoutSeconds) {
            this.toolCallTimeoutSeconds = toolCallTimeoutSeconds;
            return this;
        }

        public Capabilities build() {
            return new Capabilities(
                    streamingSupported,
                    maxConcurrentSessions,
                    sessionTimeoutSeconds,
                    toolCallTimeoutSeconds
            );
        }
    }
}
