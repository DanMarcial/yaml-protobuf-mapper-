package io.github.yamlmapper.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.yamlmapper.config.FieldConfig;
import java.util.Optional;

public class JsonNodeExtractor {

    private final PathResolver pathResolver;
    private final EmbeddedJsonParser jsonParser;
    private final TransformExecutor transformExecutor;

    public JsonNodeExtractor(
            PathResolver pathResolver,
            EmbeddedJsonParser jsonParser,
            TransformExecutor transformExecutor
    ) {
        this.pathResolver = pathResolver;
        this.jsonParser = jsonParser;
        this.transformExecutor = transformExecutor;
    }

    public Optional<JsonNode> extract(
            JsonNode root,
            FieldConfig config
    ) {

        for (String source : config.source()) {

            JsonNode node = pathResolver.resolve(root, source);

            if (node == null || node.isNull() || node.isMissingNode()) {
                continue;
            }

            // Parse embedded JSON
            node = jsonParser.tryParse(node);

            // Apply transforms
            node = transformExecutor.apply(node, config, root);

            return Optional.of(node);
        }

        return Optional.empty();
    }
}