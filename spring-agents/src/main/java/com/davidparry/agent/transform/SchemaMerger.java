package com.davidparry.agent.transform;

import com.davidparry.agent.protocol.BaseLlmResponse;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import org.springframework.stereotype.Component;

/**
 * Utility class to merge a partial JSON Schema (properties only) with a schema
 * generated from a Java class, producing a complete JSON Schema.
 */
@Component
public class SchemaMerger {

    private final ObjectMapper objectMapper;
    private final SchemaGenerator generator;

    public SchemaMerger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // Configure the schema generator with Jackson module
        JacksonModule jacksonModule = new JacksonModule(
                JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                JacksonOption.RESPECT_JSONPROPERTY_ORDER
        );

        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON
        )
                .with(jacksonModule)
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);

        // Make all fields required by default
        configBuilder.forFields().withRequiredCheck(f -> true);

        SchemaGeneratorConfig config = configBuilder.build();
        this.generator = new SchemaGenerator(config);
    }

    /**
     * Hardcoded fallback JSON schema containing success and reason properties
     * matching the BaseLlmResponse schema definition.
     */
    private static final String FALLBACK_SCHEMA = """
            {
              "$schema" : "https://json-schema.org/draft/2020-12/schema",
              "type" : "object",
              "properties" : {
                "success" : {
                  "description" : "Indicates whether the operation completed successfully",
                  "type" : "boolean"
                },
                "reason" : {
                  "description" : "Explanation of the result, particularly when success is false",
                  "type" : "string"
                }
              },
              "required" : [ "success", "reason" ],
              "additionalProperties" : false
            }
            """;

    /**
     * Takes a partial schema string containing only "properties" and merges it with
     * a schema generated from a Java class.
     *
     * @param propertiesJson The JSON string containing just the properties object
     *                       (e.g., {"properties": {...}})
     * @param pojoClass      The Java class to generate additional schema from
     * @return The complete merged JSON Schema as a pretty-printed string, or a fallback
     *         schema with success and reason properties if any exception occurs
     */
    public String mergePropertiesWithClass(String propertiesJson, Class<?> pojoClass) {
        try {
            // Step 1: Parse the existing properties JSON into a JsonNode
            JsonNode existingNode = objectMapper.readTree(propertiesJson);

            // Step 2: Generate schema from the POJO class
            JsonNode generatedSchema = generator.generateSchema(pojoClass);

            // Step 3: Create the complete schema object
            ObjectNode completeSchema = objectMapper.createObjectNode();

            // Add $schema
            completeSchema.put("$schema", "https://json-schema.org/draft/2020-12/schema");

            // Add type
            completeSchema.put("type", "object");

            // Step 4: Merge properties from both sources
            ObjectNode mergedProperties = objectMapper.createObjectNode();

            // Add properties from the existing partial schema
            if (existingNode.has("properties")) {
                JsonNode existingProperties = existingNode.get("properties");
                existingProperties.fields().forEachRemaining(entry ->
                        mergedProperties.set(entry.getKey(), entry.getValue().deepCopy()));
            }

            // Add properties from the generated schema (POJO)
            if (generatedSchema.has("properties")) {
                JsonNode generatedProperties = generatedSchema.get("properties");
                generatedProperties.fields().forEachRemaining(entry ->
                        mergedProperties.set(entry.getKey(), entry.getValue().deepCopy()));
            }

            completeSchema.set("properties", mergedProperties);

            // Step 5: Build the required array from all property names
            ArrayNode requiredArray = objectMapper.createArrayNode();

            // Add all property names to required
            mergedProperties.fieldNames().forEachRemaining(requiredArray::add);

            completeSchema.set("required", requiredArray);

            // Step 6: Add additionalProperties: false
            completeSchema.put("additionalProperties", false);

            // Step 7: Pretty print the complete schema
            ObjectWriter objectWriter = objectMapper.writer(new DefaultPrettyPrinter()
                    .withObjectIndenter(new DefaultIndenter().withLinefeed(System.lineSeparator())));

            return objectWriter.writeValueAsString(completeSchema);
        } catch (Exception e) {
            // Return fallback schema with success and reason properties matching BaseLlmResponse
            return FALLBACK_SCHEMA;
        }
    }

    /**
     * Main method demonstrating the merge functionality.
     */
    public static void main(String[] args) {
        try {
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

            // Merge the partial properties with SimplePojo class
            String completeSchema = merger.mergePropertiesWithClass(partialPropertiesJson, BaseLlmResponse.class);

            System.out.println("Complete Merged JSON Schema:");
            System.out.println(completeSchema);

        } catch (Exception e) {
            System.err.println("Error processing JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
