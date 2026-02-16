package com.davidparry.agent.protocol;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom deserializer that handles arguments as either a JSON object or a JSON string.
 * The server may send arguments as a stringified JSON that needs to be parsed.
 *
 * <p><b>Thread Safety:</b> This deserializer is thread-safe. The internal ObjectMapper
 * is configured once during class loading and only used for read operations thereafter.
 */
public final class StringOrMapDeserializer extends JsonDeserializer<Map<String, Object>> {

    private static final ObjectMapper MAPPER;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    static {
        ObjectMapper mapper = new ObjectMapper();
        MAPPER = mapper;
    }

    @Override
    public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        if (p.currentToken().isStructStart()) {
            // It's already a JSON object, deserialize normally
            return p.readValueAs(MAP_TYPE);
        } else {
            // It's a string, parse it as JSON
            String jsonString = p.getValueAsString();
            if (jsonString == null || jsonString.isEmpty()) {
                return new LinkedHashMap<>();
            }
            return MAPPER.readValue(jsonString, MAP_TYPE);
        }
    }
}
