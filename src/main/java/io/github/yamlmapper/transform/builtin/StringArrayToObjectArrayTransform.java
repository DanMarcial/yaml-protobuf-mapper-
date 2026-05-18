package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yamlmapper.exception.MappingException;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

/**
 * Converts an array of strings to an array of objects with a specified field.
 *
 * <p>Behavior:
 * <ul>
 *   <li>{@code ["id1", "id2"]} → {@code [{id: "id1"}, {id: "id2"}]}</li>
 *   <li>{@code null} → {@code []}</li>
 *   <li>Single string → {@code [{id: "value"}]}</li>
 * </ul>
 *
 * <p>Optional parameter {@code fieldName} (default: "id"):
 * <pre>{@code
 * productIds:
 *   type: array
 *   source: [ids]
 *   transform: stringArrayToObjectArray
 *   transformParams:
 *     fieldName: productId
 * }</pre>
 */
public class StringArrayToObjectArrayTransform implements Transform {
  private static final String DEFAULT_FIELD_NAME = "id";

  @Override
  public JsonNode apply(JsonNode node, TransformContext context) {
    final JsonNode effectiveNode = resolveEffectiveNode(node, context);
    ArrayNode result = JsonNodeFactory.instance.arrayNode();

    if (effectiveNode == null || effectiveNode.isNull() || effectiveNode.isMissingNode()) {
      return result;
    }

    String fieldName = context.getParam("fieldName", DEFAULT_FIELD_NAME);

    if (effectiveNode.isArray()) {
      for (JsonNode element : effectiveNode) {
        if (!element.isNull() && element.isTextual()) {
          ObjectNode obj = JsonNodeFactory.instance.objectNode();
          obj.put(fieldName, element.asText());
          result.add(obj);
        }
      }
    } else if (effectiveNode.isTextual()) {
      // Handle single string
      ObjectNode obj = JsonNodeFactory.instance.objectNode();
      obj.put(fieldName, effectiveNode.asText());
      result.add(obj);
    }

    return result;
  }

  /**
   * Resolves the node to be processed. If the input is a String that looks like a JSON array, it
   * attempts to parse it into a {@link JsonNode}.
   *
   * @param node the raw input node
   * @return the node to be used for transformation
   */
  private JsonNode resolveEffectiveNode(final JsonNode node, TransformContext context) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }

    final String textValue = node.asText();
    if (textValue.startsWith("[") && textValue.endsWith("]")) {
      try {
        return context.getObjectMapper().readTree(textValue);
      } catch (JsonProcessingException e) {
        throw new MappingException("Failed to parse string as JSON array: " + textValue, e);
      }
    }
    return node;
  }

  @Override
  public String getName() {
    return "stringArrayToObjectArray";
  }
}
