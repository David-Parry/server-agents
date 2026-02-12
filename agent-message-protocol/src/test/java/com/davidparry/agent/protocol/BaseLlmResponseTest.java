package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BaseLlmResponse record.
 */
class BaseLlmResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testBaseLlmResponseCreation() {
        BaseLlmResponse response = new BaseLlmResponse(true, "Operation completed successfully");

        assertTrue(response.success());
        assertEquals("Operation completed successfully", response.reason());
    }

    @Test
    void testBaseLlmResponseWithFalseSuccess() {
        BaseLlmResponse response = new BaseLlmResponse(false, "Operation failed due to invalid input");

        assertFalse(response.success());
        assertEquals("Operation failed due to invalid input", response.reason());
    }

    @Test
    void testBaseLlmResponseWithNullReason() {
        BaseLlmResponse response = new BaseLlmResponse(true, null);

        assertTrue(response.success());
        assertNull(response.reason());
    }

    @Test
    void testBaseLlmResponseJsonSerialization() throws Exception {
        BaseLlmResponse response = new BaseLlmResponse(true, "Success reason");

        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"reason\":\"Success reason\""));
    }

    @Test
    void testBaseLlmResponseJsonDeserialization() throws Exception {
        String json = """
                {
                    "success": true,
                    "reason": "The operation was successful"
                }
                """;

        BaseLlmResponse response = objectMapper.readValue(json, BaseLlmResponse.class);

        assertTrue(response.success());
        assertEquals("The operation was successful", response.reason());
    }

    @Test
    void testBaseLlmResponseJsonDeserializationWithFalse() throws Exception {
        String json = """
                {
                    "success": false,
                    "reason": "Validation failed"
                }
                """;

        BaseLlmResponse response = objectMapper.readValue(json, BaseLlmResponse.class);

        assertFalse(response.success());
        assertEquals("Validation failed", response.reason());
    }

    @Test
    void testBaseLlmResponseJsonDeserializationWithNullReason() throws Exception {
        String json = """
                {
                    "success": true,
                    "reason": null
                }
                """;

        BaseLlmResponse response = objectMapper.readValue(json, BaseLlmResponse.class);

        assertTrue(response.success());
        assertNull(response.reason());
    }

    @Test
    void testBaseLlmResponseEquality() {
        BaseLlmResponse response1 = new BaseLlmResponse(true, "Same reason");
        BaseLlmResponse response2 = new BaseLlmResponse(true, "Same reason");
        BaseLlmResponse response3 = new BaseLlmResponse(false, "Same reason");

        assertEquals(response1, response2);
        assertNotEquals(response1, response3);
    }

    @Test
    void testBaseLlmResponseHashCode() {
        BaseLlmResponse response1 = new BaseLlmResponse(true, "Same reason");
        BaseLlmResponse response2 = new BaseLlmResponse(true, "Same reason");

        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testBaseLlmResponseToString() {
        BaseLlmResponse response = new BaseLlmResponse(true, "Test reason");

        String toString = response.toString();

        assertTrue(toString.contains("true"));
        assertTrue(toString.contains("Test reason"));
    }

    @Test
    void testJsonPropertyDescriptionAnnotationOnSuccess() throws Exception {
        // Check on record component
        RecordComponent[] components = BaseLlmResponse.class.getRecordComponents();
        RecordComponent successComponent = null;
        for (RecordComponent component : components) {
            if ("success".equals(component.getName())) {
                successComponent = component;
                break;
            }
        }
        assertNotNull(successComponent, "success component should exist");
        
        // The annotation may be on the accessor method instead of the component
        Method accessor = successComponent.getAccessor();
        JsonPropertyDescription annotation = accessor.getAnnotation(JsonPropertyDescription.class);
        
        // If not on accessor, check the component itself
        if (annotation == null) {
            annotation = successComponent.getAnnotation(JsonPropertyDescription.class);
        }
        
        assertNotNull(annotation, "@JsonPropertyDescription should be present on success field");
        assertEquals("indicates whether the LLM determined the operation, evaluation, or request was successful", 
                annotation.value());
    }

    @Test
    void testJsonPropertyDescriptionAnnotationOnReason() throws Exception {
        // Check on record component
        RecordComponent[] components = BaseLlmResponse.class.getRecordComponents();
        RecordComponent reasonComponent = null;
        for (RecordComponent component : components) {
            if ("reason".equals(component.getName())) {
                reasonComponent = component;
                break;
            }
        }
        assertNotNull(reasonComponent, "reason component should exist");
        
        // The annotation may be on the accessor method instead of the component
        Method accessor = reasonComponent.getAccessor();
        JsonPropertyDescription annotation = accessor.getAnnotation(JsonPropertyDescription.class);
        
        // If not on accessor, check the component itself
        if (annotation == null) {
            annotation = reasonComponent.getAnnotation(JsonPropertyDescription.class);
        }
        
        assertNotNull(annotation, "@JsonPropertyDescription should be present on reason field");
        assertEquals("concise, human-readable explanation justifying the result", 
                annotation.value());
    }
}
