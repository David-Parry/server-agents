package com.davidparry.agent.sdk.context.routing;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Validates a model response payload against an output schema definition.
 */
public interface OutputSchemaValidator {

    /**
     * @param outputSchemaJson output schema JSON string from agent configuration
     * @param content model response payload
     * @return true when payload conforms to schema (or no schema is configured)
     */
    boolean isValid(String outputSchemaJson, JsonNode content);
}
