package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

/**
 * Converts a string to uppercase.
 *
 * <p>Behavior:
 * <ul>
 *   <li>{@code "hello"} → {@code "HELLO"}</li>
 *   <li>Non-string values are returned unchanged</li>
 * </ul>
 *
 * <p>Usage in YAML:
 * <pre>{@code
 * code:
 *   type: string
 *   source: [code]
 *   transform: uppercase
 * }</pre>
 */
public class UppercaseTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode node, TransformContext context) {
    if (node == null || node.isNull() || !node.isTextual()) {
      return node;
    }

    return new TextNode(node.asText().toUpperCase());
  }

  @Override
  public String getName() {
    return "uppercase";
  }
}
