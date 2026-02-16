package com.davidparry.agent.session;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a pending tool call waiting for a response from the remote client.
 *
 * <p>This record is immutable in terms of its field references. The {@link CompletableFuture}
 * is created at construction time and its completion state is managed internally by the future.
 *
 * @param requestId      unique identifier for this tool call request
 * @param toolName       name of the tool being invoked
 * @param arguments      JSON arguments passed to the tool
 * @param createdAt      timestamp when this pending call was created
 * @param deadline       timestamp after which this call is considered expired
 * @param responseFuture future that will be completed with the tool result
 */
public record PendingToolCall(
        String requestId,
        String toolName,
        String arguments,
        Instant createdAt,
        Instant deadline,
        CompletableFuture<String> responseFuture
) {

    /**
     * Creates a new PendingToolCall with auto-generated createdAt and responseFuture.
     *
     * @param requestId unique identifier for this tool call request
     * @param toolName  name of the tool being invoked
     * @param arguments JSON arguments passed to the tool
     * @param deadline  timestamp after which this call is considered expired
     */
    public PendingToolCall(String requestId, String toolName, String arguments, Instant deadline) {
        this(requestId, toolName, arguments, Instant.now(), deadline, new CompletableFuture<>());
    }

    /**
     * Completes the tool call with a successful result.
     *
     * @param result the tool execution result
     */
    public void complete(String result) {
        responseFuture.complete(result);
    }

    /**
     * Completes the tool call with an error.
     *
     * @param error the error that occurred
     */
    public void completeExceptionally(Throwable error) {
        responseFuture.completeExceptionally(error);
    }

    /**
     * Checks if the tool call has expired.
     *
     * @return true if the deadline has passed
     */
    public boolean isExpired() {
        return Instant.now().isAfter(deadline);
    }

    /**
     * Checks if the tool call is still pending.
     *
     * @return true if not yet completed
     */
    public boolean isPending() {
        return !responseFuture.isDone();
    }

    @Override
    public String toString() {
        return "PendingToolCall{"
                + "requestId='" + requestId + '\''
                + ", toolName='" + toolName + '\''
                + ", createdAt=" + createdAt
                + ", deadline=" + deadline
                + ", pending=" + isPending()
                + '}';
    }
}
