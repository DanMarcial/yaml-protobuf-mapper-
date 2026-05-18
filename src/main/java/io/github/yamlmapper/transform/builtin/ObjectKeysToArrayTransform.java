package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;
import java.util.Iterator;
import java.util.Map;

/**
 * Converts an object to an array of "key:value" strings.
 *
 * <p>Behavior:
 * <ul>
 *   <li>{@code {a: "1", b: "2"}} → {@code ["a:1", "b:2"]}</li>
 *   <li>{@code null} → {@code []}</li>
 *   <li>Non-object → {@code []} (empty array)</li>
 * </ul>
 *
 * <p>Optional parameter {@code separator} (default: ":"):
 * <pre>{@code
 * attributes:
 *   type: array
 *   source: [attrs]
 *   transform: objectKeysToArray
 *   transformParams:
 *     separator: "="
 * }</pre>
 */
public class ObjectKeysToArrayTransform implements Transform {

  private static final String DEFAULT_SEPARATOR = ":";

  @Override
  public JsonNode apply(JsonNode node, TransformContext context) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();

    if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
      return result;
    }

    String separator = context.getParam("separator", DEFAULT_SEPARATOR);

    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      String key = entry.getKey();
      String value = entry.getValue().isTextual()
          ? entry.getValue().asText()
          : entry.getValue().toString();
      result.add(key + separator + value);
    }

    return result;
  }

  @Override
  public String getName() {
    return "objectKeysToArray";
  }
}
