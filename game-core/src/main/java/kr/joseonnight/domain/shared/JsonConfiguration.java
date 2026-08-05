package kr.joseonnight.domain.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;

public final class JsonConfiguration {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private JsonConfiguration() {
    }

    public static JsonNode fromMap(Map<String, Object> configuration) {
        return OBJECT_MAPPER.valueToTree(Map.copyOf(Objects.requireNonNull(configuration, "configuration")));
    }

    public static Map<String, Object> toMap(JsonNode configuration) {
        Objects.requireNonNull(configuration, "configuration");
        JsonNode normalized = configuration;
        if (configuration.isTextual()) {
            try {
                normalized = OBJECT_MAPPER.readTree(configuration.textValue());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Stored catalog configuration is not valid JSON", exception);
            }
        }
        return Map.copyOf(OBJECT_MAPPER.convertValue(normalized, MAP_TYPE));
    }
}
