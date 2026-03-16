package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Base, minimal response contract required from an LLM.
 *
 * <p>This record defines the smallest valid JSON structure that an LLM
 * must return for a successful structured response. All other response
 * types or schemas may extend this contract by adding optional fields,
 * but must always include these core attributes.</p>
 *
 * <p>This type is intended to be used for schema validation, structured
 * output enforcement, and agent decision handling.</p>
 *
 * @param success indicates whether the LLM determined the operation,
 *                evaluation, or request was successful
 * @param reason  concise, human-readable explanation justifying the result
 */
public record BaseLlmResponse(
        @JsonPropertyDescription("indicates whether the LLM determined the operation, evaluation, or request was successful")
        boolean success,
        @JsonPropertyDescription("concise, human-readable explanation justifying the result")
        String reason) {
}
