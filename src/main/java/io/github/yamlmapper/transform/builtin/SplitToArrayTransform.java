package io.github.yamlmapper.transform.builtin;

import static io.github.yamlmapper.transform.TransformParams.PARAM_DELIMITER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

/**
 * Splits a string into an array using a delimiter.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code delimiter} - The delimiter to split on (default: ",")</li>
 *   <li>{@code trim} - Whether to trim each element (default: true)</li>
 * </ul>
 *
 * <p>Example YAML:
 * <pre>{@code
 * categories:
 *   type: array
 *   itemType: string
 *   source: [category_path]
 *   transform: splitToArray
 *   transformParams:
 *     delimiter: " > "
 * }</pre>
 *
 * <p>Input: "Electronics > Computers > Laptops"
 * <p>Output: ["Electronics", "Computers", "Laptops"]
 */
public class SplitToArrayTransform implements Transform {

  private static final String DEFAULT_DELIMITER = ",";

  @Override
  public JsonNode apply(JsonNode input, TransformContext context) {
    if (input == null || input.isNull() || input.isMissingNode()) {
      return context.getObjectMapper().createArrayNode();
    }

    // If already an array, return as-is
    if (input.isArray()) {
      return input;
    }

    String text = input.asText();
    if (text == null || text.isBlank()) {
      return context.getObjectMapper().createArrayNode();
    }

    String delimiter = context.getParam(PARAM_DELIMITER, DEFAULT_DELIMITER);
    boolean trim = context.getParamAsBoolean("trim", true);

    ArrayNode result = context.getObjectMapper().createArrayNode();
    String[] parts = text.split(java.util.regex.Pattern.quote(delimiter));

    for (String part : parts) {
      String value = trim ? part.trim() : part;
      if (!value.isEmpty()) {
        result.add(value);
      }
    }

    return result;
  }

  @Override
  public String getName() {
    return "splitToArray";
  }
}
