package com.davidparry.agent.protocol.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Information about a tool call within a stream chunk.
 * This class is immutable and thread-safe.
 */
public final class ToolCallInfo {

    private final String toolName;
    private final String requestId;
    private final Map<String, Object> arguments;
    private final String result;
    private final boolean success;

    @JsonCreator
    public ToolCallInfo(
            @JsonProperty("toolName") String toolName,
            @JsonProperty("requestId") String requestId,
            @JsonProperty("arguments") Map<String, Object> arguments,
            @JsonProperty("result") String result,
            @JsonProperty("success") boolean success) {
        this.toolName = toolName;
        this.requestId = requestId;
        this.arguments = arguments != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(arguments))
                : Collections.emptyMap();
        this.result = result;
        this.success = success;
    }

    public String getToolName() {
        return toolName;
    }

    public String getRequestId() {
        return requestId;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public String getResult() {
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    @Override
    public String toString() {
        return "ToolCallInfo{" +
                "toolName='" + toolName + '\'' +
                ", requestId='" + requestId + '\'' +
                ", arguments=" + arguments +
                ", result='" + result + '\'' +
                ", success=" + success +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String toolName;
        private String requestId;
        private Map<String, Object> arguments;
        private String result;
        private boolean success;

        private Builder() {
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public ToolCallInfo build() {
            return new ToolCallInfo(toolName, requestId, arguments, result, success);
        }
    }
}
