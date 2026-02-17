package com.davidparry.agent.transform;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JsonNodeOutputConverter}.
 * Tests the conversion of various string formats to JsonNode,
 * including markdown-wrapped JSON responses from LLMs.
 */
class JsonNodeOutputConverterTest {

    private JsonNodeOutputConverter converter;

    private static final String TEST_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "issueKey": { "type": "string" },
                "status": { "type": "string" },
                "success": { "type": "boolean" },
                "reason": { "type": "string" }
              }
            }
            """;

    private static final String TEST_RESPONSE = """
            I can see that this issue already has a comprehensive [AGENT-DESIGN] comment from David Parry dated 2026-01-04 19:02:49. According to the workflow rules, I need to check if this design is implementation-ready and if there are any subsequent comments indicating "Ready for Development" or "✅ Ready for Implementation".
            
            Let me analyze the situation:
            
            1. **Issue Status**: Currently "In Progress"
            2. **Existing Comment**: Contains [AGENT-DESIGN] with a complete implementation design
            3. **Design Quality**: The design is comprehensive, includes:
               - Overview of the codebase structure
               - Technical approach with JaCoCo configuration
               - 10 new test files to create with detailed descriptions
               - Implementation steps broken down by phase
               - Code structure examples
               - Testing strategy
               - Risks & mitigation
               - Rollback plan
               - Success criteria
            
            4. **Git Repository**: https://github.com/David-Parry/vetpet-clinic-demo.git
            
            The design document is implementation-ready and follows all the requirements. However, I notice that:
            - The comment was added by "David Parry" (a human user based on the account details)
            - The comment has the [AGENT-DESIGN] prefix but includes {AGENT_COMMENT_TIME: 25 minutes}
            - There's no subsequent comment indicating "Ready for Development"
            
            Since this is a design created by another agent or user and marked with [AGENT-DESIGN], and the issue is already in "In Progress" status, this appears to be ready for implementation by a development agent. However, per the critical rules, I should check if there's an explicit "Ready for Development" statement.
            
            Since there's no explicit "Ready for Development" comment but the design is comprehensive and implementation-ready, I should acknowledge this design and mark it as complete.
            
            ```json
            {
              "issueKey": "SCRUM-478",
              "status": "design_complete",
              "git_repo_uri": "https://github.com/David-Parry/vetpet-clinic-demo.git",
              "reason": "Comprehensive implementation design already exists in the issue comments. The [AGENT-DESIGN] comment from 2026-01-04 includes: JaCoCo configuration for Maven, 10 detailed test file specifications (5 service tests, 4 security tests, 1 utility test), step-by-step implementation phases, code structure examples, testing strategy targeting 75% coverage, risk mitigation, and rollback plan. The design is implementation-ready with concrete file paths, test method descriptions, and mock patterns matching the existing Spring Boot 3.2.0 + GraphQL + JWT architecture.",
              "success": true
            }
            ```
            """;


    @BeforeEach
    void setUp() {
        converter = new JsonNodeOutputConverter(TEST_SCHEMA);
    }

    @Nested
    @DisplayName("Markdown Code Block Extraction Tests")
    class MarkdownExtractionTests {

        @Test
        @DisplayName("Should extract JSON from Large response that is text and json all smashed together")
        void testandCovertMoreThanJustJSON() {
            String json = converter.extractAndValidateJson(TEST_RESPONSE);
            JsonNode result = converter.convert(json);

            assertNotNull(result);
            assertEquals("SCRUM-478", result.get("issueKey").asText());
            assertEquals("design_complete", result.get("status").asText());
            assertTrue(result.get("success").asBoolean());
        }


        @Test
        @DisplayName("Should extract JSON from markdown code block with leading/trailing whitespace and stray characters")
        void testConvert_MarkdownWrappedJson_WithLeadingTrailingWhitespaceAndStrayCharacters() {
            // This is the exact string from the user's requirement
            String input = """
                      
                    ```json
                    
                    {
                    
                    "issueKey": "SCRUM-478",
                    
                    "status": "design_complete",
                    
                    "git_repo_uri": "https://github.com/David-Parry/vetpet-clinic-demo.git",
                    
                    "reason": "Comprehensive implementation design created for achieving 75% unit test coverage. Analysis identified 76 main source files with only 14 test files, revealing critical gaps: 5 service classes with 0 unit tests, 4 security classes with 0 unit tests, and 1 utility class with 0 tests. Design includes JaCoCo Maven plugin configuration, 10 new test files covering services (OwnerServiceTest, PetServiceTest, VisitServiceTest, VetServiceTest, SpecialtyServiceTest), security components (JwtTokenServiceTest, LoginControllerTest, RSAKeyProviderTest, NeverExpiringTokenGeneratorTest), and utilities (EntityUtilsTest). Implementation follows existing test patterns using JUnit 5, Mockito, and AssertJ. Detailed test structure examples, mock patterns, and step-by-step implementation plan provided. Design is implementation-ready with 8-10 hour time estimate.",
                    
                    "success": true
                    
                    }
                    
                        "
                    """;
            String json = converter.extractAndValidateJson(input);

            JsonNode result = converter.convert(json);

            assertNotNull(result, "Result should not be null");
            assertTrue(result.has("issueKey"), "Result should have 'issueKey' field");
            assertEquals("SCRUM-478", result.get("issueKey").asText());
            assertTrue(result.has("status"), "Result should have 'status' field");
            assertEquals("design_complete", result.get("status").asText());
            assertTrue(result.has("git_repo_uri"), "Result should have 'git_repo_uri' field");
            assertEquals("https://github.com/David-Parry/vetpet-clinic-demo.git", result.get("git_repo_uri").asText());
            assertTrue(result.has("reason"), "Result should have 'reason' field");
            assertTrue(result.get("reason").asText().contains("Comprehensive implementation design"));
            assertTrue(result.has("success"), "Result should have 'success' field");
            assertTrue(result.get("success").asBoolean(), "success should be true");
        }

        @Test
        @DisplayName("Should extract JSON from markdown code block with json language tag")
        void testConvert_MarkdownJsonWithLanguageTag() {
            String input = """
                    ```json
                    {
                        "issueKey": "TEST-123",
                        "status": "complete",
                        "success": true
                    }
                    ```
                    """;
            String json = converter.extractAndValidateJson(input);
            JsonNode result = converter.convert(json);

            assertNotNull(result);
            assertEquals("TEST-123", result.get("issueKey").asText());
            assertEquals("complete", result.get("status").asText());
            assertTrue(result.get("success").asBoolean());
        }

        @Test
        @DisplayName("Should extract JSON from markdown code block without language tag")
        void testConvert_MarkdownJsonWithoutLanguageTag() {
            String input = """
                    ```
                    {
                        "issueKey": "TEST-456",
                        "status": "pending"
                    }
                    ```
                    """;


            String json = converter.extractAndValidateJson(input);
            JsonNode result = converter.convert(json);
            assertNotNull(result);
            assertEquals("TEST-456", result.get("issueKey").asText());
            assertEquals("pending", result.get("status").asText());
        }

        @Test
        @DisplayName("Should handle markdown with extra text before and after")
        void testConvert_MarkdownWithSurroundingText() {
            String input = """
                    Here is the JSON response:
                    
                    ```json
                    {
                        "issueKey": "TEST-789",
                        "success": true
                    }
                    ```
                    
                    Let me know if you need anything else!
                    """;

            String json = converter.extractAndValidateJson(input);
            JsonNode result = converter.convert(json);

            assertNotNull(result);
            assertEquals("TEST-789", result.get("issueKey").asText());
            assertTrue(result.get("success").asBoolean());
        }
    }

    @Nested
    @DisplayName("Clean JSON Parsing Tests")
    class CleanJsonTests {

        @Test
        @DisplayName("Should parse clean JSON object directly")
        void testConvert_CleanJsonObject() {
            String input = """
                    {
                        "issueKey": "CLEAN-001",
                        "status": "done",
                        "success": true,
                        "reason": "All tasks completed"
                    }
                    """;

            JsonNode result = converter.convert(input);

            assertNotNull(result);
            assertEquals("CLEAN-001", result.get("issueKey").asText());
            assertEquals("done", result.get("status").asText());
            assertTrue(result.get("success").asBoolean());
            assertEquals("All tasks completed", result.get("reason").asText());
        }

        @Test
        @DisplayName("Should parse clean JSON array directly")
        void testConvert_CleanJsonArray() {
            String input = """
                    [
                        {"id": 1, "name": "first"},
                        {"id": 2, "name": "second"}
                    ]
                    """;

            JsonNode result = converter.convert(input);

            assertNotNull(result);
            assertTrue(result.isArray());
            assertEquals(2, result.size());
            assertEquals(1, result.get(0).get("id").asInt());
            assertEquals("first", result.get(0).get("name").asText());
        }

        @Test
        @DisplayName("Should handle JSON with leading/trailing whitespace")
        void testConvert_JsonWithWhitespace() {
            String input = "   \n\n  {\"key\": \"value\"}  \n\n   ";

            JsonNode result = converter.convert(input);

            assertNotNull(result);
            assertEquals("value", result.get("key").asText());
        }
    }

    @Nested
    @DisplayName("JSON Extraction from Text Tests")
    class JsonExtractionTests {

        @Test
        @DisplayName("Should extract JSON object embedded in text")
        void testConvert_JsonEmbeddedInText() {
            String input = "The response is: {\"status\": \"ok\", \"count\": 42} and that's all.";
            String json = converter.extractAndValidateJson(input);
            JsonNode result = converter.convert(json);

            assertNotNull(result);
            assertEquals("ok", result.get("status").asText());
            assertEquals(42, result.get("count").asInt());
        }

        @Test
        @DisplayName("Should extract JSON array embedded in text")
        void testConvert_JsonArrayEmbeddedInText() {
            String input = "Results: [1, 2, 3, 4, 5] - total 5 items";
            String json = converter.extractAndValidateJson(input);
            JsonNode result = converter.convert(json);

            assertNotNull(result);
            assertTrue(result.isArray());
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("Should handle nested JSON objects")
        void testConvert_NestedJsonObjects() {
            String input = """
                    {
                        "outer": {
                            "inner": {
                                "value": "deep"
                            }
                        },
                        "array": [{"a": 1}, {"b": 2}]
                    }
                    """;

            JsonNode result = converter.convert(input);

            assertNotNull(result);
            assertEquals("deep", result.get("outer").get("inner").get("value").asText());
            assertEquals(2, result.get("array").size());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return failure response for invalid JSON")
        void testConvert_InvalidJson() {
            String input = "This is not JSON at all, just plain text without any braces.";

            JsonNode result = converter.convert(input);

            assertNotNull(result);
            // The converter wraps invalid content in a response object
            assertTrue(result.has("response") || result.has("success"),
                    "Should have either 'response' wrapper or 'success' failure indicator");
        }

        @Test
        @DisplayName("Should return empty JSON object for null input")
        void testConvert_NullInput() {
            JsonNode result = converter.convert(null);

            assertNotNull(result);
            // Implementation returns empty JSON object {} for null/empty content
            assertTrue(result.isObject(), "Result should be a JSON object");
        }

        @Test
        @DisplayName("Should return empty JSON object for empty string")
        void testConvert_EmptyString() {
            JsonNode result = converter.convert("");

            assertNotNull(result);
            // Implementation returns empty JSON object {} for null/empty content
            assertTrue(result.isObject(), "Result should be a JSON object");
        }

        @Test
        @DisplayName("Should return empty JSON object for blank string")
        void testConvert_BlankString() {
            JsonNode result = converter.convert("   \n\t   ");

            assertNotNull(result);
            // Implementation returns empty JSON object {} for null/empty content
            assertTrue(result.isObject(), "Result should be a JSON object");
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void testConvert_MalformedJson() {
            String input = "{\"key\": \"value\", \"broken\": }";

            JsonNode result = converter.convert(input);

            assertNotNull(result);
            // Should return a failure response or wrapped content
            assertTrue(result.has("success") || result.has("response"));
        }
    }

    @Nested
    @DisplayName("isValidJson Method Tests")
    class IsValidJsonTests {

        @Test
        @DisplayName("Should return true for valid JSON object")
        void testIsValidJson_ValidObject() {
            assertTrue(converter.isValidJson("{\"key\": \"value\"}"));
        }

        @Test
        @DisplayName("Should return true for valid JSON array")
        void testIsValidJson_ValidArray() {
            assertTrue(converter.isValidJson("[1, 2, 3]"));
        }

        @Test
        @DisplayName("Should return false for null")
        void testIsValidJson_Null() {
            assertFalse(converter.isValidJson(null));
        }

        @Test
        @DisplayName("Should return false for empty string")
        void testIsValidJson_EmptyString() {
            assertFalse(converter.isValidJson(""));
        }

        @Test
        @DisplayName("Should return false for blank string")
        void testIsValidJson_BlankString() {
            assertFalse(converter.isValidJson("   "));
        }
    }

    @Nested
    @DisplayName("getFormat Method Tests")
    class GetFormatTests {

        @Test
        @DisplayName("Should return format string containing JSON instructions and schema")
        void testGetFormat_ContainsInstructionsAndSchema() {
            String format = converter.getFormat();

            assertNotNull(format);
            assertTrue(format.contains("CRITICAL OUTPUT FORMAT REQUIREMENT"));
            assertTrue(format.contains("JSON"));
            assertTrue(format.contains(TEST_SCHEMA.trim()) || format.contains("$schema"));
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle JSON with special characters in strings")
        void testConvert_JsonWithSpecialCharacters() {
            String input = """
                    {
                        "message": "Hello \\"World\\"!",
                        "path": "C:\\\\Users\\\\test",
                        "newline": "line1\\nline2"
                    }
                    """;

            JsonNode result = converter.convert(input);

            assertNotNull(result);
            assertEquals("Hello \"World\"!", result.get("message").asText());
            assertEquals("C:\\Users\\test", result.get("path").asText());
            assertEquals("line1\nline2", result.get("newline").asText());
        }

        @Test
        @DisplayName("Should handle JSON with unicode characters")
        void testConvert_JsonWithUnicode() {
            String input = """
                    {
                        "greeting": "こんにちは",
                        "emoji": "🎉",
                        "mixed": "Hello 世界!"
                    }
                    """;

            JsonNode result = converter.convert(input);

            assertNotNull(result);
            assertEquals("こんにちは", result.get("greeting").asText());
            assertEquals("🎉", result.get("emoji").asText());
            assertEquals("Hello 世界!", result.get("mixed").asText());
        }

        @Test
        @DisplayName("Should handle deeply nested structures")
        void testConvert_DeeplyNestedJson() {
            String input = """
                    {
                        "level1": {
                            "level2": {
                                "level3": {
                                    "level4": {
                                        "value": "deep"
                                    }
                                }
                            }
                        }
                    }
                    """;

            JsonNode result = converter.convert(input);

            assertNotNull(result);
            assertEquals("deep",
                    result.get("level1").get("level2").get("level3").get("level4").get("value").asText());
        }

        @Test
        @DisplayName("Should prefer JSON object over array when both present")
        void testConvert_JsonObjectBeforeArray() {
            String input = "Here is the data: {\"type\": \"I am a value\"} and also [1,2,3]";
            String json = converter.extractAndValidateJson(input);
            JsonNode result = converter.convert(json);

            assertNotNull(result);
            // Should extract the object since { comes before [
            assertTrue(result.isObject());
            assertEquals("I am a value", result.get("type").asText());
        }

        @Test
        @DisplayName("Should prefer JSON array when it comes first")
        void testConvert_JsonArrayBeforeObject() {
            String input = "Array first: [1,2,3] then object {\"type\": \"object\"}";
            String json = converter.extractAndValidateJson(input);

            JsonNode result = converter.convert(json);

            assertNotNull(result);
            // Should extract the array since [ comes before {
            assertTrue(result.isArray());
            assertEquals(3, result.size());
        }
    }
}
