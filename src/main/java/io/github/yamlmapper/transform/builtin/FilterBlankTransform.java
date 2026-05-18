package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

/**
 * Filters out blank/empty strings from an array.
 *
 * <p>Behavior:
 * <ul>
 *   <li>{@code ["a", "", "b", "  "]} → {@code ["a", "b"]}</li>
 *   <li>{@code null} → {@code []}</li>
 *   <li>Non-array → unchanged</li>
 * </ul>
 *
 * <p>Usage in YAML:
 * <pre>{@code
 * tags:
 *   type: array
 *   source: [tags]
 *   transform: filterBlank
 * }</pre>
 */
public class FilterBlankTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode node, TransformContext context) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return JsonNodeFactory.instance.arrayNode();
    }

    if (!node.isArray()) {
      return node;
    }

    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    for (JsonNode element : node) {
      if (element == null || element.isNull()) {
        continue;
      }
      if (element.isTextual()) {
        String text = element.asText();
        if (text != null && !text.trim().isEmpty()) {
          result.add(element);
        }
      } else {
        // Keep non-string elements
        result.add(element);
      }
    }

    return result;
  }

  @Override
  public String getName() {
    return "filterBlank";
  }
}
