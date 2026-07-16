package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

/**
 * Converts a string to lowercase.
 *
 * <p>Behavior:
 * <ul>
 *   <li>{@code "HELLO"} → {@code "hello"}</li>
 *   <li>Non-string values are returned unchanged</li>
 * </ul>
 *
 * <p>Usage in YAML:
 * <pre>{@code
 * email:
 *   type: string
 *   source: [email]
 *   transform: lowercase
 * }</pre>
 */
public class LowercaseTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode node, TransformContext context) {
    if (node == null || node.isNull() || !node.isTextual()) {
      return node;
    }

    return new TextNode(node.asText().toLowerCase());
  }

  @Override
  public String getName() {
    return "lowercase";
  }
}
