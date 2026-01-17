/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for processing template strings with placeholder markers.
 * This class can replace {key} markers in template strings with values from JSON payloads.
 * <p>
 * Features:
 * - Supports JSON Pointer notation (e.g., {/path/to/value})
 * - Supports dot notation (e.g., {path.to.value})
 * - Preserves original placeholders when values are not found (allows multi-pass processing)
 * - Works with any JSON structure
 * <p>
 * Example usage:
 * <pre>
 * TemplateProcessor processor = new TemplateProcessor();
 * String template = "Hello {/name}, your score is {/score}!";
 * String json = "{\"name\": \"John\", \"score\": 95}";
 * String result = processor.processTemplate(template, json);
 * // Result: "Hello John, your score is 95!"
 *
 * // Missing values preserve the placeholder:
 * String template2 = "Hello {/name}, email: {/email}";
 * String json2 = "{\"name\": \"John\"}";
 * String result2 = processor.processTemplate(template2, json2);
 * // Result: "Hello John, email: {/email}"
 * </pre>
 */
@Component
public class TemplateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TemplateProcessor.class);
    private final ObjectMapper objectMapper;


    public TemplateProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a template string by replacing all {key} markers with values from the JSON string
     *
     * @param template   The template string containing {key} markers
     * @param jsonString The JSON string to extract values from
     * @return The processed template with all markers replaced
     * @throws Exception if JSON parsing fails
     */
    public String processTemplate(String template, String jsonString) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        return processTemplate(template, jsonNode);
    }

    /**
     * Processes a template string by replacing all {key} markers with values from the JSON payload
     *
     * @param template The template string containing {key} markers
     * @param jsonNode The JSON payload to extract values from
     * @return The processed template with all markers replaced
     */
    public String processTemplate(String template, JsonNode jsonNode) {
        // Pattern to match {key} markers, including nested paths like {/path/to/value}
        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(template);

        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1); // Extract the key between {}
            String value = extractValueFromJson(jsonNode, key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        if (logger.isTraceEnabled()) {
            logger.trace("Processed template: {}", result);
        }
        return result.toString();
    }

    /**
     * Extracts a value from JSON using a key path
     *
     * @param jsonNode The JSON node to search in
     * @param keyPath  The path to the value (e.g., "/eventType" or "project.name")
     * @return The string value or the original placeholder if not found
     */
    private String extractValueFromJson(JsonNode jsonNode, String keyPath) {
        try {
            JsonNode targetNode = jsonNode;

            // Handle JSON Pointer style paths (starting with /)
            if (keyPath.startsWith("/")) {
                targetNode = jsonNode.at(keyPath);
            } else {
                // Handle dot notation or simple keys
                String[] pathParts = keyPath.split("\\.");
                for (String part : pathParts) {
                    if (targetNode.has(part)) {
                        targetNode = targetNode.get(part);
                    } else {
                        // Return original placeholder unchanged
                        return "{" + keyPath + "}";
                    }
                }
            }

            // Return the value as text, or return original placeholder if not found
            if (targetNode.isMissingNode() || targetNode.isNull()) {
                return "{" + keyPath + "}";
            } else if (targetNode.isTextual()) {
                return targetNode.asText();
            } else {
                return targetNode.toString();
            }

        } catch (Exception e) {
            // Return original placeholder on error
            return "{" + keyPath + "}";
        }
    }

}