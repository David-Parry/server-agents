package com.davidparry.agent.sdk.context.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleOutputSchemaValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleOutputSchemaValidator validator = new SimpleOutputSchemaValidator(objectMapper);

    @Test
    void validatesRequiredAndTypes() throws Exception {
        String schema = """
                {
                  "properties": {
                    "status": {"type": "string"},
                    "attempts": {"type": "integer"}
                  },
                  "required": ["status", "attempts"]
                }
                """;

        assertTrue(validator.isValid(schema, objectMapper.readTree("{\"status\":\"ok\",\"attempts\":1}")));
        assertFalse(validator.isValid(schema, objectMapper.readTree("{\"status\":\"ok\"}")));
        assertFalse(validator.isValid(schema, objectMapper.readTree("{\"status\":\"ok\",\"attempts\":\"1\"}")));
    }

    @Test
    void rejectsUnknownPropertiesWhenAdditionalPropertiesFalse() throws Exception {
        String schema = """
                {
                  "properties": {
                    "status": {"type": "string"}
                  },
                  "additionalProperties": false
                }
                """;

        assertTrue(validator.isValid(schema, objectMapper.readTree("{\"status\":\"ok\"}")));
        assertFalse(validator.isValid(schema, objectMapper.readTree("{\"status\":\"ok\",\"extra\":true}")));
    }

    @Test
    void treatsInvalidSchemaAsInvalidOutput() throws Exception {
        String badSchema = "{not-valid-json}";
        assertFalse(validator.isValid(badSchema, objectMapper.readTree("{\"status\":\"ok\"}")));
    }
}
