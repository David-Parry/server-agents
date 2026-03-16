package com.davidparry.agent.protocol;

import com.davidparry.agent.protocol.dto.ChunkType;
import com.davidparry.agent.protocol.dto.ToolCallInfo;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Streaming response chunk.
 * This class is immutable and thread-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class StreamChunk extends McpProxyMessage {

    private final String sessionId;
    private final int sequenceNumber;
    private final ChunkType chunkType;
    private final String content;
    private final ToolCallInfo toolCall;
    private final boolean isLast;

    @JsonCreator
    public StreamChunk(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("sequenceNumber") int sequenceNumber,
            @JsonProperty("chunkType") ChunkType chunkType,
            @JsonProperty("content") String content,
            @JsonProperty("toolCall") ToolCallInfo toolCall,
            @JsonProperty("isLast") boolean isLast) {
        super(messageId, timestamp);
        this.sessionId = sessionId;
        this.sequenceNumber = sequenceNumber;
        this.chunkType = chunkType;
        this.content = content;
        this.toolCall = toolCall;
        this.isLast = isLast;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public ChunkType getChunkType() {
        return chunkType;
    }

    public String getContent() {
        return content;
    }

    public ToolCallInfo getToolCall() {
        return toolCall;
    }

    public boolean isLast() {
        return isLast;
    }

    @Override
    public String toString() {
        return "StreamChunk{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", sessionId='" + sessionId + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", chunkType=" + chunkType +
                ", content='" + content + '\'' +
                ", toolCall=" + toolCall +
                ", isLast=" + isLast +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageId;
        private Instant timestamp;
        private String sessionId;
        private int sequenceNumber;
        private ChunkType chunkType;
        private String content;
        private ToolCallInfo toolCall;
        private boolean isLast;

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

        public Builder sequenceNumber(int sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder chunkType(ChunkType chunkType) {
            this.chunkType = chunkType;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder toolCall(ToolCallInfo toolCall) {
            this.toolCall = toolCall;
            return this;
        }

        public Builder isLast(boolean isLast) {
            this.isLast = isLast;
            return this;
        }

        public StreamChunk build() {
            return new StreamChunk(
                    messageId,
                    timestamp,
                    sessionId,
                    sequenceNumber,
                    chunkType,
                    content,
                    toolCall,
                    isLast
            );
        }
    }
}
