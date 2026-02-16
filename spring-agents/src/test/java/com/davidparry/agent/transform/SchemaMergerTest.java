package com.davidparry.agent.transform;

import com.davidparry.agent.protocol.BaseLlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMergerTest {

    @Test
    void testMergePropertiesWithClass() {
        ObjectMapper objectMapper = new ObjectMapper();
        SchemaMerger merger = new SchemaMerger(objectMapper);

        // The partial schema with just properties (from your agent.yml output_schema)
        String partialPropertiesJson = """
                {
                    "properties": {
                        "issueKey": {
                            "description": "The Jira Issue Key that you have used to update, comment and work on the issue.",
                            "type": "string"
                        },
                        "summary": {
                            "description": "Summary of the Jira ticket that you have looked up.",
                            "type": "string"
                        },
                        "status": {
                            "description": "One of: needs_clarification, acknowledged_updates, design_complete",
                            "type": "string"
                        },
                        "questions": {
                            "description": "List of questions that need to be still answered before proceeding to implement the design",
                            "type": "array",
                            "items": {
                                "type": "string",
                                "description": "A single question it **must** also have the time estimate it would take a human developer to come up with this question"
                            }
                        },
                        "design": {
                            "description": "The complete design document that has been added as a Jira comment and is also included here for other agents to use",
                            "type": "string"
                        },
                        "reason": {
                            "description": "Optional field explaining why success is false, only present when success is false",
                            "type": "string"
                        },
                        "git_repo_uri": {
                            "description": "the git repository URI of the project that will be in the jira issue",
                            "type": "string"
                        }
                    }
                }
                """;

        // Merge the partial properties with BaseLlmResponse class
        String completeSchema = merger.mergePropertiesWithClass(partialPropertiesJson, BaseLlmResponse.class);

        System.out.println("Complete Merged JSON Schema:");
        System.out.println(completeSchema);

        // Assertions
        assertNotNull(completeSchema);
        assertTrue(completeSchema.contains("$schema"));
        assertTrue(completeSchema.contains("https://json-schema.org/draft/2020-12/schema"));
        assertTrue(completeSchema.contains("issueKey"));
        assertTrue(completeSchema.contains("summary"));
        assertTrue(completeSchema.contains("status"));
        assertTrue(completeSchema.contains("questions"));
        assertTrue(completeSchema.contains("design"));
        assertTrue(completeSchema.contains("reason"));
        assertTrue(completeSchema.contains("git_repo_uri"));
        assertTrue(completeSchema.contains("required"));
        assertTrue(completeSchema.contains("additionalProperties"));
    }

    @Test
    void testMergePropertiesWithClass_InvalidJson_ReturnsFallbackSchema() {
        ObjectMapper objectMapper = new ObjectMapper();
        SchemaMerger merger = new SchemaMerger(objectMapper);

        // Invalid JSON that will cause parsing to fail
        String invalidJson = "{ this is not valid json }";

        // Should return fallback schema instead of throwing exception
        String result = merger.mergePropertiesWithClass(invalidJson, Object.class);

        // Verify fallback schema is returned
        assertNotNull(result);
        assertTrue(result.contains("$schema"));
        assertTrue(result.contains("https://json-schema.org/draft/2020-12/schema"));
        assertTrue(result.contains("success"));
        assertTrue(result.contains("Indicates whether the operation completed successfully"));
        assertTrue(result.contains("boolean"));
        assertTrue(result.contains("reason"));
        assertTrue(result.contains("Explanation of the result, particularly when success is false"));
        assertTrue(result.contains("required"));
        assertTrue(result.contains("additionalProperties"));
    }

    @Test
    void testMergePropertiesWithClass_NullInput_ReturnsFallbackSchema() {
        ObjectMapper objectMapper = new ObjectMapper();
        SchemaMerger merger = new SchemaMerger(objectMapper);

        // Null input should trigger fallback
        String result = merger.mergePropertiesWithClass(null, Object.class);

        // Verify fallback schema is returned
        assertNotNull(result);
        assertTrue(result.contains("success"));
        assertTrue(result.contains("reason"));
    }
}
