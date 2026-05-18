package io.github.yamlmapper.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yamlmapper.exception.MappingException;

public class EmbeddedJsonParser {

    private final ObjectMapper objectMapper;

    public EmbeddedJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode tryParse(JsonNode node) {

        if (node == null || !node.isTextual()) {
            return node;
        }

        String text = node.asText().trim();

        boolean looksLikeJson =
                (text.startsWith("{") && text.endsWith("}")) ||
                        (text.startsWith("[") && text.endsWith("]"));

        if (!looksLikeJson) {
            return node;
        }

        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new MappingException(
                    "Failed to parse embedded JSON: " + text,
                    e
            );
        }
    }
}