package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

/**
 * Trims whitespace from the beginning and end of a string.
 *
 * <p>Behavior:
 * <ul>
 *   <li>{@code "  hello  "} → {@code "hello"}</li>
 *   <li>Non-string values are returned unchanged</li>
 * </ul>
 *
 * <p>Usage in YAML:
 * <pre>{@code
 * name:
 *   type: string
 *   source: [name]
 *   transform: trim
 * }</pre>
 */
public class TrimTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode node, TransformContext context) {
    if (node == null || node.isNull() || !node.isTextual()) {
      return node;
    }

    String trimmed = node.asText().trim();
    return new TextNode(trimmed);
  }

  @Override
  public String getName() {
    return "trim";
  }
}
