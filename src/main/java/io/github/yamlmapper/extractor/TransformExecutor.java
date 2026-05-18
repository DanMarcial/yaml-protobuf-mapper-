package io.github.yamlmapper.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;
import io.github.yamlmapper.transform.TransformContextImpl;
import io.github.yamlmapper.transform.TransformRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransformExecutor {

    private static final Logger log = LoggerFactory.getLogger(TransformExecutor.class);

    private final TransformRegistry registry;
    private final ObjectMapper objectMapper;

    public TransformExecutor(TransformRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public JsonNode apply(JsonNode node, FieldConfig config, JsonNode rootNode) {
        if (config.transform() == null) {
            return node;
        }

        Transform transform = registry.get(config.transform());

        if (transform == null) {
            log.warn("Transform '{}' not found for field '{}'", config.transform(), config.name());
            return node;
        }

        log.debug("Applying transform '{}' to field '{}'", config.transform(), config.name());

        TransformContext context = new TransformContextImpl(
                config.name(),
                config,
                rootNode,
                objectMapper
        );

        return transform.apply(node, context);
    }
}