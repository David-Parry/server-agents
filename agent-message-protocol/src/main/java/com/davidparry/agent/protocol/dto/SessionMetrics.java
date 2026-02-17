package com.davidparry.agent.protocol.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metrics for a completed session.
 * This class is immutable and thread-safe.
 */
public final class SessionMetrics {

    private final long durationMs;
    private final int toolCallCount;
    private final int tokenCount;

    @JsonCreator
    public SessionMetrics(
            @JsonProperty("durationMs") long durationMs,
            @JsonProperty("toolCallCount") int toolCallCount,
            @JsonProperty("tokenCount") int tokenCount) {
        this.durationMs = durationMs;
        this.toolCallCount = toolCallCount;
        this.tokenCount = tokenCount;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    @Override
    public String toString() {
        return "SessionMetrics{" +
                "durationMs=" + durationMs +
                ", toolCallCount=" + toolCallCount +
                ", tokenCount=" + tokenCount +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long durationMs;
        private int toolCallCount;
        private int tokenCount;

        private Builder() {
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder toolCallCount(int toolCallCount) {
            this.toolCallCount = toolCallCount;
            return this;
        }

        public Builder tokenCount(int tokenCount) {
            this.tokenCount = tokenCount;
            return this;
        }

        public SessionMetrics build() {
            return new SessionMetrics(durationMs, toolCallCount, tokenCount);
        }
    }
}
