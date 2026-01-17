package com.davidparry.agent.transform;

import com.davidparry.agent.protocol.BaseLlmResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.StructuredOutputConverter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonNodeOutputConverter implements StructuredOutputConverter<JsonNode> {
    private static final Logger logger = LoggerFactory.getLogger(JsonNodeOutputConverter.class);
    /**
     * JSON instruction to append to system prompts to ensure valid JSON responses.
     */
    private static final String JSON_RESPONSE_INSTRUCTION = """
            CRITICAL OUTPUT FORMAT REQUIREMENT:
            You MUST respond with ONLY raw JSON - no markdown, no code blocks, no backticks, no explanatory text.
            
            **FORBIDDEN**:
            - Do NOT use ```json or ``` markers
            - Do NOT add any text before or after the JSON
            - Do NOT include markdown formatting of any kind
            
            **REQUIRED**:
            - Start your response with { or [
            - End your response with } or ]
            - Output ONLY the JSON object/array, nothing else
            
            Your response must conform to this JSON schema:
            """;

    /**
     * Pattern to extract JSON from markdown code blocks if the LLM wraps the response.
     * Handles various formats including:
     * - ```json ... ```
     * - ``` ... ```
     * - Leading/trailing whitespace around the code block
     * - Content after the closing backticks
     */
    private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String jsonSchema;

    public JsonNodeOutputConverter(String jsonSchema) {
        init(jsonSchema);
    }

    public void init(String jsonSchema) {
        this.jsonSchema = jsonSchema;
    }

    @Override
    public String getFormat() {
        return JSON_RESPONSE_INSTRUCTION + jsonSchema;
    }

    @Override
    public JsonNode convert(String source) {
        JsonNode result;
        try {
            result = objectMapper.readTree(source);
            if (result == null || result.isEmpty()) {
                result = failedNodeConversionResponse("Failure reason: the source from LLM:'" + source + "'");
            }
        } catch (Exception e) {
            result = failedNodeConversionResponse("Failure reseason:" + e.getMessage() + " non" + " json " +
                                                          "structured response from LLM:\n" + source);
        }
        return result;
    }

    public JsonNode failedNodeConversionResponse(String reason) {
        BaseLlmResponse baseLlmResponse = new BaseLlmResponse(false, reason);
        return objectMapper.valueToTree(baseLlmResponse);
    }


    /**
     * Extracts and validates JSON from the LLM response.
     * Handles cases where the LLM wraps JSON in markdown code blocks or includes
     * additional text before/after the JSON.
     *
     * @param content the raw content from the LLM
     * @return valid JSON string
     * @throws RuntimeException if no valid JSON can be extracted
     */
    public String extractAndValidateJson(String content) {
        if (content == null || content.isBlank()) {
            logger.warn("Empty content received from LLM, returning empty JSON object");
            return "{}";
        }

        String trimmedContent = content.trim();

        // First, try to parse the content directly as JSON
        if (isValidJson(trimmedContent)) {
            logger.debug("Content is already valid JSON");
            return trimmedContent;
        }
        logger.warn("The content is not valid JSON, attempting to extract from markdown code blocks... \n{}", content);

        // Try to extract JSON from markdown code blocks
        Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(content);
        if (matcher.find()) {
            String extractedJson = matcher.group(1).trim();
            if (isValidJson(extractedJson)) {
                logger.debug("Extracted valid JSON from markdown code block");
                return extractedJson;
            }
        }

        // Try to find JSON object or array in the content
        String extractedJson = extractJsonFromText(trimmedContent);
        if (isValidJson(extractedJson)) {
            logger.debug("Extracted valid JSON from text content");
            return extractedJson;
        }

        // If all extraction attempts fail, wrap the content in a JSON object
        logger.warn("Could not extract valid JSON from LLM response, wrapping content in JSON object");
        try {
            // Escape the content and wrap it in a response object
            String escapedContent = objectMapper.writeValueAsString(trimmedContent);
            return "{\"response\":" + escapedContent + "}";
        } catch (JsonProcessingException e) {
            logger.error("Failed to escape content for JSON wrapping", e);
            return "{\"response\":\"Error processing LLM response\",\"error\":true}";
        }
    }

    /**
     * Checks if the given string is valid JSON.
     * Performs a direct parse without any extraction or modification.
     *
     * @param json the string to validate
     * @return true if valid JSON, false otherwise
     */
    public boolean isValidJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            // Catch all exceptions including JsonProcessingException and IllegalArgumentException
            logger.debug("Content is not valid JSON: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Attempts to extract a JSON object or array from text that may contain
     * additional content before or after the JSON.
     *
     * @param text the text to search for JSON
     * @return the extracted JSON string, or null if not found
     */
    private String extractJsonFromText(String text) {
        // Find the first { or [ character
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');

        int start;
        char endChar;

        if (objectStart == -1 && arrayStart == -1) {
            return null;
        } else if (objectStart == -1) {
            start = arrayStart;
            endChar = ']';
        } else if (arrayStart == -1) {
            start = objectStart;
            endChar = '}';
        } else {
            // Use whichever comes first
            if (objectStart < arrayStart) {
                start = objectStart;
                endChar = '}';
            } else {
                start = arrayStart;
                endChar = ']';
            }
        }

        // Find the matching closing bracket
        int depth = 0;
        char startChar = text.charAt(start);
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == startChar) {
                    depth++;
                } else if (c == endChar) {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }

        return null;
    }

}
