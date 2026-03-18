package com.davidparry.agent.sdk.context.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Lightweight schema validator for common output_schema contracts.
 *
 * This is intentionally scoped to key constraints used by agent outputs:
 * - required fields
 * - primitive/object/array type checks
 * - additionalProperties=false handling
 */
public class SimpleOutputSchemaValidator implements OutputSchemaValidator {

    private static final Logger logger = LoggerFactory.getLogger(SimpleOutputSchemaValidator.class);

    private final ObjectMapper objectMapper;

    public SimpleOutputSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isValid(String outputSchemaJson, JsonNode content) {
        if (outputSchemaJson == null || outputSchemaJson.isBlank()) {
            return true;
        }
        if (content == null || !content.isObject()) {
            return false;
        }

        try {
            JsonNode schema = objectMapper.readTree(outputSchemaJson);
            JsonNode properties = schema.path("properties");

            if (!properties.isObject()) {
                return true;
            }

            if (!validateRequired(schema.path("required"), content)) {
                return false;
            }

            if (!validatePropertyTypes(properties, content)) {
                return false;
            }

            JsonNode additionalProperties = schema.get("additionalProperties");
            if (additionalProperties != null && additionalProperties.isBoolean() && !additionalProperties.asBoolean()) {
                return validateNoUnknownProperties(properties, content);
            }

            return true;
        } catch (Exception e) {
            logger.warn("Failed to parse output schema; treating response as schema-invalid", e);
            return false;
        }
    }

    private boolean validateRequired(JsonNode required, JsonNode content) {
        if (!required.isArray()) {
            return true;
        }
        for (JsonNode requiredField : required) {
            String name = requiredField.asText();
            if (!content.has(name) || content.get(name).isNull()) {
                return false;
            }
        }
        return true;
    }

    private boolean validatePropertyTypes(JsonNode properties, JsonNode content) {
        Iterator<String> fieldNames = content.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode propertySchema = properties.get(fieldName);
            if (propertySchema == null || propertySchema.isNull()) {
                continue;
            }
            JsonNode typeNode = propertySchema.get("type");
            if (typeNode == null || typeNode.isNull()) {
                continue;
            }
            if (!matchesType(typeNode.asText(), content.get(fieldName))) {
                return false;
            }
        }
        return true;
    }

    private boolean validateNoUnknownProperties(JsonNode properties, JsonNode content) {
        Set<String> allowed = new HashSet<>();
        properties.fieldNames().forEachRemaining(allowed::add);
        Iterator<String> fieldNames = content.fieldNames();
        while (fieldNames.hasNext()) {
            if (!allowed.contains(fieldNames.next())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesType(String type, JsonNode node) {
        return switch (type) {
            case "string" -> node.isTextual();
            case "boolean" -> node.isBoolean();
            case "number" -> node.isNumber();
            case "integer" -> node.isIntegralNumber();
            case "array" -> node.isArray();
            case "object" -> node.isObject();
            case "null" -> node.isNull();
            default -> true;
        };
    }
}
