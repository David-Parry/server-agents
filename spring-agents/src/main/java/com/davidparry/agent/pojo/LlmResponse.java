package com.davidparry.agent.pojo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Immutable record holding the response content and token count from an LLM execution.
 *
 * <p>This record is thread-safe by design:</p>
 * <ul>
 *   <li>Records are immutable - fields cannot be modified after construction</li>
 *   <li>Each LLM execution creates a new instance - no shared mutable state</li>
 *   <li>Instances are created locally within method scope and returned</li>
 * </ul>
 *
 * @param content the text content returned by the LLM
 * @param tokenCount the total number of tokens used (prompt + completion)
 */
public record LlmResponse(JsonNode content, int tokenCount, boolean success) {
}
