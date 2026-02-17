package com.davidparry.agent.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateProcessor.
 */
class TemplateProcessorTest {

    private TemplateProcessor processor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        processor = new TemplateProcessor(objectMapper);
    }

    @Test
    void processTemplate_shouldReplaceSimpleKeys() throws Exception {
        // Given
        String template = "Hello {name}, welcome to {place}!";
        String json = "{\"name\": \"John\", \"place\": \"Spring Agents\"}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Hello John, welcome to Spring Agents!", result);
    }

    @Test
    void processTemplate_shouldHandleJsonPointerPaths() throws Exception {
        // Given
        String template = "User: {/user/name}, Role: {/user/role}";
        String json = "{\"user\": {\"name\": \"Alice\", \"role\": \"admin\"}}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("User: Alice, Role: admin", result);
    }

    @Test
    void processTemplate_shouldHandleDotNotation() throws Exception {
        // Given
        String template = "Project: {project.name}, Version: {project.version}";
        String json = "{\"project\": {\"name\": \"spring-agents\", \"version\": \"1.0.0\"}}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Project: spring-agents, Version: 1.0.0", result);
    }

    @Test
    void processTemplate_shouldPreserveMissingPlaceholders() throws Exception {
        // Given
        String template = "Hello {name}, email: {email}";
        String json = "{\"name\": \"John\"}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Hello John, email: {email}", result);
    }

    @Test
    void processTemplate_shouldHandleNumericValues() throws Exception {
        // Given
        String template = "Score: {score}, Level: {level}";
        String json = "{\"score\": 95, \"level\": 5}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Score: 95, Level: 5", result);
    }

    @Test
    void processTemplate_shouldHandleBooleanValues() throws Exception {
        // Given
        String template = "Active: {active}, Verified: {verified}";
        String json = "{\"active\": true, \"verified\": false}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Active: true, Verified: false", result);
    }

    @Test
    void processTemplate_shouldHandleNullValues() throws Exception {
        // Given
        String template = "Name: {name}, Email: {email}";
        String json = "{\"name\": \"John\", \"email\": null}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Name: John, Email: {email}", result);
    }

    @Test
    void processTemplate_shouldHandleArrayValues() throws Exception {
        // Given
        String template = "Tags: {tags}";
        String json = "{\"tags\": [\"java\", \"spring\", \"ai\"]}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Tags: [\"java\",\"spring\",\"ai\"]", result);
    }

    @Test
    void processTemplate_shouldHandleObjectValues() throws Exception {
        // Given
        String template = "Config: {config}";
        String json = "{\"config\": {\"key\": \"value\"}}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Config: {\"key\":\"value\"}", result);
    }

    @Test
    void processTemplate_shouldHandleEmptyTemplate() throws Exception {
        // Given
        String template = "";
        String json = "{\"name\": \"John\"}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("", result);
    }

    @Test
    void processTemplate_shouldHandleTemplateWithNoPlaceholders() throws Exception {
        // Given
        String template = "Hello World!";
        String json = "{\"name\": \"John\"}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Hello World!", result);
    }

    @Test
    void processTemplate_shouldHandleDeepNestedPaths() throws Exception {
        // Given
        String template = "Value: {/a/b/c/d}";
        String json = "{\"a\": {\"b\": {\"c\": {\"d\": \"deep value\"}}}}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Value: deep value", result);
    }

    @Test
    void processTemplate_shouldHandleJsonNode() throws Exception {
        // Given
        String template = "Hello {name}!";
        JsonNode jsonNode = objectMapper.readTree("{\"name\": \"World\"}");

        // When
        String result = processor.processTemplate(template, jsonNode);

        // Then
        assertEquals("Hello World!", result);
    }

    @Test
    void processTemplate_shouldHandleSpecialCharactersInValues() throws Exception {
        // Given
        String template = "Message: {message}";
        String json = "{\"message\": \"Hello $World! (test)\"}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Message: Hello $World! (test)", result);
    }

    @Test
    void processTemplate_shouldHandleMultipleSamePlaceholders() throws Exception {
        // Given
        String template = "{name} says hello. {name} is here.";
        String json = "{\"name\": \"John\"}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("John says hello. John is here.", result);
    }

    @Test
    void processTemplate_shouldHandleMissingNestedPath() throws Exception {
        // Given
        String template = "Value: {/a/b/missing}";
        String json = "{\"a\": {\"b\": {\"c\": \"value\"}}}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Value: {/a/b/missing}", result);
    }

    @Test
    void processTemplate_shouldHandleMissingDotNotationPath() throws Exception {
        // Given
        String template = "Value: {a.b.missing}";
        String json = "{\"a\": {\"b\": {\"c\": \"value\"}}}";

        // When
        String result = processor.processTemplate(template, json);

        // Then
        assertEquals("Value: {a.b.missing}", result);
    }
}
