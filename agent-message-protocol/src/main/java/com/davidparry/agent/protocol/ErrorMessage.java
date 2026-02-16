package com.davidparry.agent.protocol;

import com.davidparry.agent.protocol.dto.ErrorCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error notification.
 * This class is immutable and thread-safe.
 *
 * <p>Note: Named ErrorMessage instead of Error to avoid conflict with java.lang.Error
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ErrorMessage extends McpProxyMessage {

    private final String sessionId;
    private final ErrorCode code;
    private final String message;
    private final Map<String, Object> details;

    @JsonCreator
    public ErrorMessage(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("code") ErrorCode code,
            @JsonProperty("message") String message,
            @JsonProperty("details") Map<String, Object> details) {
        super(messageId, timestamp);
        this.sessionId = sessionId;
        this.code = code;
        this.message = message;
        this.details = details != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(details))
                : Collections.emptyMap();
    }

    public String getSessionId() {
        return sessionId;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "ErrorMessage{" +
                "messageId='" + getMessageId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", sessionId='" + sessionId + '\'' +
                ", code=" + code +
                ", message='" + message + '\'' +
                ", details=" + details +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageId;
        private Instant timestamp;
        private String sessionId;
        private ErrorCode code;
        private String message;
        private Map<String, Object> details;

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

        public Builder code(ErrorCode code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public ErrorMessage build() {
            return new ErrorMessage(
                    messageId,
                    timestamp,
                    sessionId,
                    code,
                    message,
                    details
            );
        }
    }
}
