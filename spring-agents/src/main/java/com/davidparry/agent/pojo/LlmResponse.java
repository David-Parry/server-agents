package com.davidparry.agent.pojo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Immutable record holding the response content and token counts from an LLM execution.
 *
 * <p>This record is thread-safe by design:</p>
 * <ul>
 *   <li>Records are immutable - fields cannot be modified after construction</li>
 *   <li>Each LLM execution creates a new instance - no shared mutable state</li>
 *   <li>Instances are created locally within method scope and returned</li>
 * </ul>
 *
 * @param content the text content returned by the LLM
 * @param inputTokens the number of input tokens used
 * @param outputTokens the number of output tokens used
 * @param totalTokens the total number of tokens used (input + output)
 * @param success whether the LLM call was successful
 */
public record LlmResponse(JsonNode content, int inputTokens, int outputTokens, int totalTokens, boolean success) {

    /**
     * Backward-compatible accessor returning total tokens.
     */
    public int tokenCount() {
        return totalTokens;
    }
}
