package com.davidparry.agent.service;

import com.davidparry.agent.dto.LowTokenNotificationEvent;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.pojo.ExecutionResult;
import com.davidparry.agent.pojo.LlmResponse;
import com.davidparry.agent.protocol.SessionResult;
import com.davidparry.agent.session.PromptSession;
import com.davidparry.agent.transform.JsonNodeOutputConverter;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Service for executing prompts using Spring AI ChatClient.
 * Supports both streaming and non-streaming responses with remote tool callbacks.
 *
 * Validates per-model token usage before execution and records usage after.
 */
@Service
public class PromptExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptExecutionService.class);

    private final ChatClient.Builder anthropicClientBuilder;
    private final ChatClient.Builder ollamaClientBuilder;
    private final CustomerUsageService customerUsageService;
    private final LowTokenNotificationService notificationService;
    private final Timer promptExecutionTimer;
    private final Counter promptSuccessCounter;
    private final Counter promptFailureCounter;
    private final Counter usageLimitExceededCounter;

    // Callback for sending WebSocket notifications (set by McpProxyWebSocketHandler)
    private Consumer<LowTokenNotificationEvent> webSocketNotifier;

    public PromptExecutionService(AnthropicChatModel anthropicChatModel, OllamaChatModel ollamaChatModel,
                                  CustomerUsageService customerUsageService,
                                  LowTokenNotificationService notificationService,
                                  MeterRegistry meterRegistry) {
        this.anthropicClientBuilder = ChatClient.builder(anthropicChatModel);
        this.ollamaClientBuilder = ChatClient.builder(ollamaChatModel);
        this.customerUsageService = customerUsageService;
        this.notificationService = notificationService;

        this.promptExecutionTimer = Timer
                .builder("mcp.prompts.duration")
                .description("Prompt execution duration")
                .register(meterRegistry);
        this.promptSuccessCounter = Counter
                .builder("mcp.prompts.success")
                .description("Successful prompt executions")
                .register(meterRegistry);
        this.promptFailureCounter = Counter
                .builder("mcp.prompts.failure")
                .description("Failed prompt executions")
                .register(meterRegistry);
        this.usageLimitExceededCounter = Counter
                .builder("mcp.prompts.usage_limit_exceeded")
                .description("Prompt executions rejected due to usage limit exceeded")
                .register(meterRegistry);
    }

    /**
     * Executes a prompt asynchronously.
     *
     * @param session the prompt session
     * @return a CompletableFuture containing the execution result with updated session
     */
    public CompletableFuture<ExecutionResult> executePrompt(PromptSession session) {
        return CompletableFuture.supplyAsync(() -> {
            try (var ignored = MDC.putCloseable("sessionId", session.getSessionId());
                 var ignored2 = MDC.putCloseable("connectionId", session.getConnection().getConnectionId());
                 var ignored3 = MDC.putCloseable("clientId", session.getConnection().getClientId())) {
                return promptExecutionTimer.record(() -> doExecutePrompt(session));
            }
        });
    }

    private ExecutionResult doExecutePrompt(PromptSession session) {
        // Track session state through execution using AtomicReference for streaming updates
        AtomicReference<PromptSession> currentSession = new AtomicReference<>(session.startExecution());

        // Update the session in the connection to reflect EXECUTING state
        session.getConnection().updateSession(currentSession.get());

        try {
            // Extract customer ID from connection (clientId is the customer's external UUID)
            UUID customerId = UUID.fromString(session.getConnection().getClientId());
            String model = session.getModel();

            // Validate usage BEFORE execution
            CustomerUsageService.UsageValidationResult validation =
                customerUsageService.validateUsage(customerId, model);

            if (!validation.allowed()) {
                LOGGER.warn("Usage validation failed for customer {} on model {}: {}",
                           customerId, model, validation.message());

                usageLimitExceededCounter.increment();
                promptFailureCounter.increment();

                PromptSession failedSession = currentSession.get().fail(validation.message());

                SessionResult result = SessionResult.builder()
                    .sessionId(failedSession.getSessionId())
                    .success(false)
                    .content(null)
                    .errorMessage(validation.message())
                    .toolCallsExecuted(0)
                    .totalDurationMs(failedSession.getDurationMs())
                    .metrics(failedSession.createMetrics(0))
                    .build();

                return new ExecutionResult(result, failedSession);
            }

            LOGGER.info("Starting prompt execution: streaming={}, model={}, customer={}",
                       session.isStreamingEnabled(), model, customerId);

            // Select the appropriate chat client based on model
            ChatClient client = selectChatClient(model);
            List<ToolCallback> callbacks = session.getTools();

            LlmResponse llmResponse;

            if (session.isStreamingEnabled()) {
                llmResponse = executeStreaming(client, currentSession, callbacks);
            } else {
                llmResponse = executeBlocking(client, session, callbacks);
            }

            // Record usage AFTER successful execution
            if (llmResponse.tokenCount() > 0) {
                customerUsageService.recordUsage(customerId, model, llmResponse.tokenCount());
                LOGGER.debug("Recorded {} tokens for customer {} on model {}",
                            llmResponse.tokenCount(), customerId, model);

                // Check for low token notification after recording usage
                checkAndSendLowTokenNotification(customerId, model);
            }

            // CRITICAL: Get the latest session from the connection to pick up toolCallCount
            // updated by RemoteToolCallback during tool execution
            PromptSession latestSession = session
                    .getConnection()
                    .getSession(session.getSessionId())
                    .orElse(currentSession.get());

            PromptSession completedSession = latestSession.complete();
            promptSuccessCounter.increment();

            LOGGER.info("Prompt execution completed successfully, toolCallCount={}, tokenCount={}",
                        completedSession.getToolCallCount(), llmResponse.tokenCount());

            LOGGER.debug("Prompt Session {}", completedSession);

            SessionResult result = SessionResult
                    .builder()
                    .sessionId(completedSession.getSessionId())
                    .success(llmResponse.success())
                    .content(llmResponse.content())
                    .errorMessage(null)
                    .toolCallsExecuted(completedSession.getToolCallCount())
                    .totalDurationMs(completedSession.getDurationMs())
                    .metrics(completedSession.createMetrics(llmResponse.tokenCount()))
                    .build();

            return new ExecutionResult(result, completedSession);

        } catch (Exception e) {
            LOGGER.error("Prompt execution failed", e);
            promptFailureCounter.increment();

            // Get the latest session from the connection to preserve toolCallCount
            PromptSession latestSession = session
                    .getConnection()
                    .getSession(session.getSessionId())
                    .orElse(currentSession.get());

            PromptSession failedSession = latestSession.fail(e.getMessage());

            SessionResult result = SessionResult
                    .builder()
                    .sessionId(failedSession.getSessionId())
                    .success(false)
                    .content(null)
                    .errorMessage(e.getMessage())
                    .toolCallsExecuted(failedSession.getToolCallCount())
                    .totalDurationMs(failedSession.getDurationMs())
                    .metrics(failedSession.createMetrics(0))
                    .build();

            return new ExecutionResult(result, failedSession);
        }
    }

    private LlmResponse executeBlocking(ChatClient client, PromptSession session, List<ToolCallback> callbacks) {
        LOGGER.debug("Executing blocking prompt");
        JsonNodeOutputConverter converter = new JsonNodeOutputConverter(session.responseSchema());

        // Enhance system prompt with JSON instruction
        String enhancedSystemPrompt = converter.getFormat() + "\n" + session.getSystemPrompt();

        ChatResponse chatResponse = client
                .prompt()
                .system(enhancedSystemPrompt)
                .user(session.getPrompt())
                .toolCallbacks(callbacks.toArray(new ToolCallback[0]))
                .call()
                .chatResponse();

        return convertToLlmResponse(converter, chatResponse);
    }

    /**
     * this is meant for calls that have an entire ChatResponse not portions of it.
     * streaming need to collect all the stream response till the end which in our case still only be Json
     *
     * @param converter
     * @param chatResponse
     * @return
     */
    private LlmResponse convertToLlmResponse(JsonNodeOutputConverter converter, ChatResponse chatResponse) {
        if (chatResponse != null) {
            return convertToLlmResponse(chatResponse.getResult().getOutput().getText(), converter, chatResponse);
        } else {
            return new LlmResponse(null, 0, false);
        }
    }

    private LlmResponse convertToLlmResponse(String content, JsonNodeOutputConverter converter,
                                             ChatResponse chatResponse) {
        boolean success = false;
        String cleansedJson = converter.extractAndValidateJson(content);
        JsonNode result;
        if (converter.isValidJson(cleansedJson)) {
            result = converter.convert(cleansedJson);
            if (result.has("success")) {
                success = result.get("success").asBoolean();
            }
            // Clean up internal properties from the result
            if (result.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) result).remove("success");
                ((com.fasterxml.jackson.databind.node.ObjectNode) result).remove("reason");
            }
        } else {
            result = converter.failedNodeConversionResponse(
                    "Content could not be cleansed and valid JSON could not be extracted from LLM content:\n"
                    + content);
        }

        // Extract token count from metadata
        int tokenCount = extractTokenCount(chatResponse);
        LOGGER.debug("Blocking execution completed: tokenCount={} success= {}", tokenCount, success);
        LOGGER.trace("!!!!!!! the result after being parsed is \n{}", result);
        return new LlmResponse(result, tokenCount, success);
    }

    private LlmResponse executeStreaming(ChatClient client, AtomicReference<PromptSession> sessionRef,
                                         List<ToolCallback> callbacks) {
        LOGGER.debug("Executing streaming prompt");

        PromptSession session = sessionRef.get();
        StringBuilder fullResponse = new StringBuilder();
        AtomicInteger chunkCount = new AtomicInteger(0);
        AtomicReference<ChatResponse> lastChatResponse = new AtomicReference<>();
        JsonNodeOutputConverter converter = new JsonNodeOutputConverter(session.responseSchema());

        // Enhance system prompt with JSON instruction
        String enhancedSystemPrompt = converter.getFormat() + "\n" + session.getSystemPrompt();

        try {

            // Use chatResponse() stream to capture metadata including token usage
            // Use LenientTemplateRenderer to replace variables when found but pass through
            // unmatched placeholders (e.g., JSON, code snippets) without throwing errors.
            Flux<ChatResponse> responseFlux = client
                    .prompt()
                    .system(enhancedSystemPrompt)
                    .user(session.getPrompt())
                    .toolCallbacks(callbacks.toArray(new ToolCallback[0]))
                    .stream()
                    .chatResponse();

            // Process the stream and send chunks via the session
            // Each sendStreamChunk returns a new session with incremented sequence
            responseFlux.doOnNext(chatResponse -> {
                // Store the last response to extract token count at the end
                lastChatResponse.set(chatResponse);

                if (chatResponse.getResult().getOutput().getText() != null) {
                    String chunk = chatResponse.getResult().getOutput().getText();
                    fullResponse.append(chunk);
                    PromptSession updated = sessionRef.get().sendStreamChunk(chunk, false);
                    sessionRef.set(updated);
                    chunkCount.incrementAndGet();
                }

            }).doOnComplete(() -> {
                // Send final chunk marker
                PromptSession updated = sessionRef.get().sendStreamChunk("", true);
                sessionRef.set(updated);
                LOGGER.debug("Streaming completed: {} chunks sent", chunkCount.get());

            }).doOnError(error -> {
                LOGGER.error("Streaming error", error);
            }).blockLast(); // Block until stream completes

        } catch (Exception e) {
            LOGGER.error("Error during streaming execution", e);
            throw new RuntimeException("Streaming execution failed: " + e.getMessage(), e);
        }

        // Extract token count from the last response (usually contains aggregated usage)
        int tokenCount = extractTokenCount(lastChatResponse.get());
        LOGGER.debug("Streaming execution completed: tokenCount={}", tokenCount);

        return convertToLlmResponse(fullResponse.toString(), converter, lastChatResponse.get());
    }

    /**
     * Extracts the total token count from a ChatResponse.
     *
     * @param chatResponse the response from the LLM
     * @return the total token count, or 0 if not available
     */
    private int extractTokenCount(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return 0;
        }

        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return 0;
        }

        Integer totalTokens = usage.getTotalTokens();
        return totalTokens != null ? totalTokens : 0;
    }

    private ChatClient selectChatClient(String model) {
        if (model == null || model.isBlank()) {
            // Default to Anthropic
            return anthropicClientBuilder.build();
        }

        String modelLower = model.toLowerCase();

        // Check for Ollama models
        if (modelLower.contains("llama") || modelLower.contains("mistral") || modelLower.contains("codellama") || modelLower.contains("ollama")) {
            LOGGER.debug("Using Ollama client for model: {}", model);
            return ollamaClientBuilder.build();
        }

        // Check for Anthropic models
        if (modelLower.contains("claude") || modelLower.contains("anthropic")) {
            LOGGER.debug("Using Anthropic client for model: {}", model);
            return anthropicClientBuilder.build();
        }

        // Default to Anthropic
        LOGGER.debug("Using default Anthropic client for model: {}", model);
        return anthropicClientBuilder.build();
    }

    /**
     * Sets the WebSocket notifier callback for sending low-token notifications to clients.
     * This is called by McpProxyWebSocketHandler during initialization.
     *
     * @param notifier the callback to send notifications via WebSocket
     */
    public void setWebSocketNotifier(Consumer<LowTokenNotificationEvent> notifier) {
        this.webSocketNotifier = notifier;
    }

    /**
     * Checks if a low-token notification should be sent and triggers it if needed.
     * This is called after recording token usage.
     */
    private void checkAndSendLowTokenNotification(UUID customerId, String model) {
        try {
            Optional<CustomerModelAllowanceEntity> allowanceOpt =
                customerUsageService.getAllowanceWithCustomer(customerId, model);

            if (allowanceOpt.isPresent()) {
                CustomerModelAllowanceEntity allowance = allowanceOpt.get();
                notificationService.checkAndNotify(allowance, webSocketNotifier);
            }
        } catch (Exception e) {
            // Don't let notification failures affect the main execution flow
            LOGGER.warn("Failed to check/send low token notification for customer {} on model {}: {}",
                       customerId, model, e.getMessage());
        }
    }
}
