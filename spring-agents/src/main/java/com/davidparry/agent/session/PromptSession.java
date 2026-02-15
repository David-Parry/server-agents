package com.davidparry.agent.session;

import com.davidparry.agent.protocol.StreamChunk;
import com.davidparry.agent.protocol.dto.ChunkType;
import com.davidparry.agent.protocol.dto.SessionMetrics;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Represents an active prompt execution session using an immutable record pattern.
 * State changes create new instances via with* methods, following functional programming principles.
 *
 * <p>The session configuration is immutable, while runtime state is managed through
 * copy-on-write semantics for thread safety and predictability.</p>
 *
 * <p>The sessionId is provided by the client and used throughout the session lifecycle.</p>
 */
public record PromptSession(
        // Immutable configuration
        String sessionId,
        ClientConnection connection,
        String prompt,
        String systemPrompt,
        String model,
        boolean streamingEnabled,
        List<ToolCallback> tools,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant deadline,
        String responseSchema,

        // Runtime state (immutable per instance, new instance on change)
        SessionState state,
        String errorMessage,
        Instant completedAt,
        int streamSequence,
        int toolCallCount,

        // Mutable shared state (thread-safe containers)
        Map<String, PendingToolCall> pendingCalls,
        Consumer<StreamChunk> streamChunkConsumer,
        Map<String, Object> promptParams
) {

    /**
     * Compact constructor with validation and defensive copying.
     */
    public PromptSession {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required");
        }
        if (connection == null) {
            throw new IllegalArgumentException("Connection is required");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt is required");
        }

        // Defensive copies for collections
        tools = tools != null ? List.copyOf(tools) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        pendingCalls = pendingCalls != null ? pendingCalls : new ConcurrentHashMap<>();
    }

    // ==================== State Transition Methods (return new instances) ====================

    /**
     * Transitions the session to executing state.
     * @return new session in EXECUTING state, or this if not in PENDING state
     */
    public PromptSession startExecution() {
        if (state == SessionState.PENDING) {
            return withState(SessionState.EXECUTING);
        }
        return this;
    }

    /**
     * Marks the session as waiting for a tool call response.
     * @return new session in WAITING_FOR_TOOL state, or this if not in EXECUTING state
     */
    public PromptSession waitingForTool() {
        if (state == SessionState.EXECUTING) {
            return withState(SessionState.WAITING_FOR_TOOL);
        }
        return this;
    }

    /**
     * Resumes execution after tool call completes.
     * @return new session in EXECUTING state, or this if not in WAITING_FOR_TOOL state
     */
    public PromptSession resumeExecution() {
        if (state == SessionState.WAITING_FOR_TOOL) {
            return withState(SessionState.EXECUTING);
        }
        return this;
    }

    /**
     * Completes the session successfully.
     * @return new session in COMPLETED state
     */
    public PromptSession complete() {
        if (!isTerminal()) {
            cancelAllPendingCalls("Session completed");
            return new PromptSession(
                    sessionId, connection, prompt, systemPrompt, model,
                    streamingEnabled, tools, metadata, createdAt, deadline, responseSchema,
                    SessionState.COMPLETED, null, Instant.now(),
                    streamSequence, toolCallCount, pendingCalls, streamChunkConsumer, promptParams
            );
        }
        return this;
    }

    /**
     * Fails the session with an error.
     * @param errorMessage the error message
     * @return new session in FAILED state with error
     */
    public PromptSession fail(String errorMessage) {
        if (!isTerminal()) {
            cancelAllPendingCalls("Session failed: " + errorMessage);
            return new PromptSession(
                    sessionId, connection, prompt, systemPrompt, model,
                    streamingEnabled, tools, metadata, createdAt, deadline, responseSchema,
                    SessionState.FAILED, errorMessage, Instant.now(),
                    streamSequence, toolCallCount, pendingCalls, streamChunkConsumer, promptParams
            );
        }
        return this;
    }

    /**
     * Cancels the session.
     * @param reason the cancellation reason
     * @return new session in CANCELLED state
     */
    public PromptSession cancel(String reason) {
        if (!isTerminal()) {
            cancelAllPendingCalls("Session cancelled: " + reason);
            return new PromptSession(
                    sessionId, connection, prompt, systemPrompt, model,
                    streamingEnabled, tools, metadata, createdAt, deadline, responseSchema,
                    SessionState.CANCELLED, reason, Instant.now(),
                    streamSequence, toolCallCount, pendingCalls, streamChunkConsumer, promptParams
            );
        }
        return this;
    }

    /**
     * Times out the session.
     * @return new session in TIMED_OUT state
     */
    public PromptSession timeout() {
        if (!isTerminal()) {
            cancelAllPendingCalls("Session timed out");
            return new PromptSession(
                    sessionId, connection, prompt, systemPrompt, model,
                    streamingEnabled, tools, metadata, createdAt, deadline, responseSchema,
                    SessionState.TIMED_OUT, "Session timed out", Instant.now(),
                    streamSequence, toolCallCount, pendingCalls, streamChunkConsumer, promptParams
            );
        }
        return this;
    }

    // ==================== With Methods for State Changes ====================

    private PromptSession withState(SessionState newState) {
        return new PromptSession(
                sessionId, connection, prompt, systemPrompt, model,
                streamingEnabled, tools, metadata, createdAt, deadline, responseSchema,
                newState, errorMessage, completedAt,
                streamSequence, toolCallCount, pendingCalls, streamChunkConsumer, promptParams
        );
    }

    /**
     * Returns a new session with the stream chunk consumer set.
     * @param consumer the consumer that will send chunks to the client
     * @return new session with consumer set
     */
    public PromptSession withStreamChunkConsumer(Consumer<StreamChunk> consumer) {
        return new PromptSession(
                sessionId, connection, prompt, systemPrompt, model,
                streamingEnabled, tools, metadata, createdAt, deadline, responseSchema,
                state, errorMessage, completedAt,
                streamSequence, toolCallCount, pendingCalls, consumer, promptParams
        );
    }

    /**
     * Returns a new session with incremented stream sequence.
     * @return new session with next sequence number
     */
    public PromptSession withNextStreamSequence() {
        return new PromptSession(
                sessionId, connection, prompt, systemPrompt, model,
                streamingEnabled, tools, metadata, createdAt, deadline, responseSchema,
                state, errorMessage, completedAt,
                streamSequence + 1, toolCallCount, pendingCalls, streamChunkConsumer, promptParams
        );
    }

    /**
     * Returns a new session with incremented tool call count.
     * @return new session with incremented count
     */
    public PromptSession withIncrementedToolCallCount() {
        return new PromptSession(
                sessionId, connection, prompt, systemPrompt, model,
                streamingEnabled, tools, metadata, createdAt, deadline, responseSchema,
                state, errorMessage, completedAt,
                streamSequence, toolCallCount + 1, pendingCalls, streamChunkConsumer, promptParams
        );
    }

    // ==================== Stream Chunk Handling ====================

    /**
     * Sends a stream chunk to the client and returns updated session.
     * @param content the content to send
     * @param isLast whether this is the last chunk
     * @return new session with incremented sequence
     */
    public PromptSession sendStreamChunk(String content, boolean isLast) {
        if (streamChunkConsumer != null) {
            StreamChunk chunk = StreamChunk.builder()
                    .sessionId(sessionId)
                    .sequenceNumber(streamSequence)
                    .chunkType(ChunkType.TEXT)
                    .content(content)
                    .toolCall(null)
                    .isLast(isLast)
                    .build();
            streamChunkConsumer.accept(chunk);
        }
        return withNextStreamSequence();
    }

    // ==================== Query Methods ====================

    /**
     * Checks if the session is in a terminal state.
     * @return true if completed, cancelled, failed, or timed out
     */
    public boolean isTerminal() {
        return state == SessionState.COMPLETED
               || state == SessionState.CANCELLED
               || state == SessionState.FAILED
               || state == SessionState.TIMED_OUT;
    }

    /**
     * Checks if the session has expired.
     * @return true if past deadline
     */
    public boolean isExpired() {
        return Instant.now().isAfter(deadline);
    }

    /**
     * Gets the duration of the session in milliseconds.
     * @return duration in ms, or time since creation if not completed
     */
    public long getDurationMs() {
        Instant end = completedAt != null ? completedAt : Instant.now();
        return end.toEpochMilli() - createdAt.toEpochMilli();
    }

    // ==================== Tool Call Management ====================
    // Note: These mutate the shared pendingCalls map for practical reasons,
    // as tool calls need to be coordinated across async boundaries.

    /**
     * Registers a pending tool call.
     * @param pendingCall the pending tool call
     * @return new session with incremented tool call count
     */
    public PromptSession addPendingCall(PendingToolCall pendingCall) {
        pendingCalls.put(pendingCall.requestId(), pendingCall);
        return withIncrementedToolCallCount();
    }

    /**
     * Gets a pending tool call by request ID.
     * @param requestId the request ID
     * @return optional containing the pending call if found
     */
    public Optional<PendingToolCall> getPendingCall(String requestId) {
        return Optional.ofNullable(pendingCalls.get(requestId));
    }

    /**
     * Removes a pending tool call.
     * @param requestId the request ID
     * @return the removed pending call, or null if not found
     */
    public PendingToolCall removePendingCall(String requestId) {
        return pendingCalls.remove(requestId);
    }

    /**
     * Gets the count of pending tool calls.
     * @return number of pending calls
     */
    public int getPendingCallCount() {
        return pendingCalls.size();
    }

    /**
     * Checks if there are any pending tool calls.
     * @return true if there are pending calls
     */
    public boolean hasPendingCalls() {
        return !pendingCalls.isEmpty();
    }

    private void cancelAllPendingCalls(String reason) {
        pendingCalls.values().forEach(call ->
                call.completeExceptionally(new RuntimeException(reason))
        );
        pendingCalls.clear();
    }

    // ==================== Metrics ====================

    /**
     * Creates session metrics for reporting.
     * @param tokenCount the token count from LLM
     * @return session metrics
     */
    public SessionMetrics createMetrics(int tokenCount) {
        return SessionMetrics.builder()
                .durationMs(getDurationMs())
                .toolCallCount(toolCallCount)
                .tokenCount(tokenCount)
                .build();
    }

    // ==================== Compatibility Getters ====================
    // These provide backward compatibility with existing code using getter naming

    public String getSessionId() {
        return sessionId;
    }

    public ClientConnection getConnection() {
        return connection;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getModel() {
        return model;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    public List<ToolCallback> getTools() {
        return tools;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public SessionState getState() {
        return state;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public String getResponseSchema() {
        return responseSchema;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private ClientConnection connection;
        private String prompt;
        private String systemPrompt;
        private String model;
        private boolean streamingEnabled;
        private List<ToolCallback> tools = List.of();
        private Map<String, Object> metadata = Map.of();
        private int maxDurationSeconds = 600;
        private Map<String, String> promptParams;
        private String responseSchema;

        public Builder promptParams(Map<String, String> promptParams) {
            this.promptParams = promptParams;
            return this;
        }

        public Builder responseSchema(String responseSchema) {
            this.responseSchema = responseSchema;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder connection(ClientConnection connection) {
            this.connection = connection;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder streamingEnabled(boolean streamingEnabled) {
            this.streamingEnabled = streamingEnabled;
            return this;
        }

        public Builder tools(List<ToolCallback> tools) {
            this.tools = tools != null ? List.copyOf(tools) : List.of();
            return this;
        }

        public Builder addTool(ToolCallback tool) {
            if (this.tools.isEmpty()) {
                this.tools = new java.util.ArrayList<>();
            } else if (!(this.tools instanceof java.util.ArrayList)) {
                this.tools = new java.util.ArrayList<>(this.tools);
            }
            ((java.util.ArrayList<ToolCallback>) this.tools).add(tool);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
            return this;
        }

        public Builder maxDurationSeconds(int maxDurationSeconds) {
            this.maxDurationSeconds = maxDurationSeconds;
            return this;
        }

        public PromptSession build() {
            Instant now = Instant.now();
            return new PromptSession(
                    sessionId,
                    connection,
                    prompt,
                    systemPrompt,
                    model,
                    streamingEnabled,
                    tools,
                    metadata,
                    now,
                    now.plusSeconds(maxDurationSeconds),
                    responseSchema,
                    SessionState.PENDING,
                    null,  // errorMessage
                    null,  // completedAt
                    0,     // streamSequence
                    0,     // toolCallCount
                    new ConcurrentHashMap<>(),
                    null,
                    promptParams != null ? Map.copyOf(promptParams) : Map.of()
            );
        }
    }

    @Override
    public String toString() {
        return "PromptSession{"
                + "sessionId='" + sessionId + '\''
                + ", state=" + state
                + ", toolCallCount=" + toolCallCount
                + ", pendingCalls=" + pendingCalls.size()
                + ", streaming=" + streamingEnabled
                + ", promptParams=" + (promptParams != null ? promptParams.size() : 0)
                + '}';
    }
}
