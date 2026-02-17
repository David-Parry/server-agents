package com.davidparry.agent.protocol.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool definition for session creation.
 * The inputSchema is a JSON Schema object represented as an immutable Map.
 * This class is immutable and thread-safe.
 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> inputSchema;

    @JsonCreator
    public ToolDefinition(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("inputSchema") Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(inputSchema))
                : Collections.emptyMap();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    @Override
    public String toString() {
        return "ToolDefinition{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", inputSchema=" + inputSchema +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public ToolDefinition build() {
            return new ToolDefinition(name, description, inputSchema);
        }
    }
}
