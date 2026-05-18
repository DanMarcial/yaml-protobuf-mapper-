package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

/**
 * Wraps a single value in an array, or returns the array unchanged.
 *
 * <p>Behavior:
 * <ul>
 *   <li>{@code "value"} → {@code ["value"]}</li>
 *   <li>{@code ["a", "b"]} → {@code ["a", "b"]} (unchanged)</li>
 *   <li>{@code null} → {@code []} (empty array)</li>
 * </ul>
 *
 * <p>Usage in YAML:
 * <pre>{@code
 * categories:
 *   type: array
 *   source: [category]
 *   transform: singleItemToArray
 * }</pre>
 */
public class SingleItemToArrayTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode node, TransformContext context) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return JsonNodeFactory.instance.arrayNode();
    }

    if (node.isArray()) {
      return node;
    }

    ArrayNode array = JsonNodeFactory.instance.arrayNode();
    array.add(node);
    return array;
  }

  @Override
  public String getName() {
    return "singleItemToArray";
  }
}
