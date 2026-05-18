package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

import java.util.Map;

import static io.github.yamlmapper.transform.TransformParams.PARAM_DEFAULT;
import static io.github.yamlmapper.transform.TransformParams.PARAM_MAPPING;

/**
 * Maps input values to output values using a dictionary.
 *
 * <p>Useful for transforming legacy values to standard enum values.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code mapping} - Map of input -> output values</li>
 *   <li>{@code default} - Default value if no mapping found (optional)</li>
 * </ul>
 *
 * <p>Example YAML:
 * <pre>{@code
 * availability:
 *   type: enum
 *   enumType: Product.Availability
 *   source: [stock_status]
 *   transform: mapValue
 *   transformParams:
 *     mapping:
 *       available: "IN_STOCK"
 *       in_stock: "IN_STOCK"
 *       out_of_stock: "OUT_OF_STOCK"
 *       sold_out: "OUT_OF_STOCK"
 *     default: "IN_STOCK"
 * }</pre>
 *
 * <p>Input: "available"
 * <p>Output: "IN_STOCK"
 */
public class MapValueTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode input, TransformContext context) {
    if (input == null || input.isNull() || input.isMissingNode()) {
      String defaultValue = context.getParam(PARAM_DEFAULT);
      return defaultValue != null ? new TextNode(defaultValue) : null;
    }

    String inputValue = input.asText();
    if (inputValue == null || inputValue.isBlank()) {
      String defaultValue = context.getParam(PARAM_DEFAULT);
      return defaultValue != null ? new TextNode(defaultValue) : input;
    }

    Map<String, String> mapping = context.getParamAsMap(PARAM_MAPPING);
    if (mapping == null || mapping.isEmpty()) {
      return input;
    }

    // Look for matching key (case-insensitive)
    for (Map.Entry<String, String> entry : mapping.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(inputValue)) {
        return new TextNode(entry.getValue());
      }
    }

    // No match found - return default or original
    String defaultValue = context.getParam(PARAM_DEFAULT);
    return defaultValue != null ? new TextNode(defaultValue) : input;
  }

  @Override
  public String getName() {
    return "mapValue";
  }
}
