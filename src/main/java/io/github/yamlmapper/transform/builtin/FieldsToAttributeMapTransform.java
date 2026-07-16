package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

import java.util.List;

import static io.github.yamlmapper.transform.TransformParams.PARAM_FIELDS;

/**
 * Transforms multiple JSON fields into a map of CustomAttribute objects.
 *
 * <p>This transform is useful for Google Retail API's {@code Product.attributes}
 * field which expects {@code map<string, CustomAttribute>} where CustomAttribute
 * has {@code text} (repeated string) and {@code numbers} (repeated double) fields.
 *
 * <p>The transform reads specified fields from the root JSON and converts each
 * into an attribute object with either "text" or "numbers" based on the value type.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code fields} - List of field names to include in the attribute map</li>
 * </ul>
 *
 * <p>Example YAML configuration:
 * <pre>{@code
 * attributes:
 *   type: map
 *   source: ["."]
 *   transform: fieldsToAttributeMap
 *   transformParams:
 *     fields:
 *       - vendor
 *       - lengths_cm
 *       - heights_cm
 * }</pre>
 *
 * <p>Example input JSON:
 * <pre>{@code
 * {
 *   "vendor": ["vendor123", "vendor456"],
 *   "lengths_cm": [2.3, 15.4],
 *   "heights_cm": [8.1, 6.4],
 *   "color": "red",
 *   "weight_kg": 5.5
 * }
 * }</pre>
 *
 * <p>Example output:
 * <pre>{@code
 * {
 *   "vendor": {"text": ["vendor123", "vendor456"]},
 *   "lengths_cm": {"numbers": [2.3, 15.4]},
 *   "heights_cm": {"numbers": [8.1, 6.4]},
 *   "color": {"text": ["red"]},
 *   "weight_kg": {"numbers": [5.5]}
 * }
 * }</pre>
 *
 * <p>Type detection rules:
 * <ul>
 *   <li>If all values are numeric, uses "numbers"</li>
 *   <li>If any value is non-numeric (or mixed types), uses "text" (converts all to strings)</li>
 *   <li>Single values are wrapped in arrays</li>
 *   <li>Null or missing fields are skipped</li>
 * </ul>
 */
public class FieldsToAttributeMapTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode input, TransformContext context) {
    JsonNode root = context.getRootNode();
    if (root == null || root.isNull()) {
      return context.getObjectMapper().createObjectNode();
    }

    List<String> fields = context.getParamAsList(PARAM_FIELDS);
    if (fields.isEmpty()) {
      return context.getObjectMapper().createObjectNode();
    }

    ObjectNode result = context.getObjectMapper().createObjectNode();

    for (String fieldName : fields) {
      JsonNode value = root.get(fieldName);
      if (value == null || value.isNull()) {
        continue;
      }

      ObjectNode attribute = createAttribute(value, context);
      if (attribute != null) {
        result.set(fieldName, attribute);
      }
    }

    return result;
  }

  /**
   * Creates a CustomAttribute object from a JSON value.
   *
   * @param value the JSON value (can be scalar or array)
   * @param context the transform context
   * @return an ObjectNode with either "text" or "numbers" array
   */
  private ObjectNode createAttribute(JsonNode value, TransformContext context) {
    ObjectNode attribute = context.getObjectMapper().createObjectNode();
    ArrayNode arrayValue = toArray(value, context);

    if (arrayValue.isEmpty()) {
      return null;
    }

    if (isAllNumeric(arrayValue)) {
      ArrayNode numbers = context.getObjectMapper().createArrayNode();
      for (JsonNode item : arrayValue) {
        if (item.isNumber()) {
          numbers.add(item.asDouble());
        } else {
          // Parse numeric string
          numbers.add(Double.parseDouble(item.asText().trim()));
        }
      }
      attribute.set("numbers", numbers);
    } else {
      ArrayNode text = context.getObjectMapper().createArrayNode();
      for (JsonNode item : arrayValue) {
        text.add(item.asText());
      }
      attribute.set("text", text);
    }

    return attribute;
  }

  /**
   * Converts a value to an array, wrapping scalars.
   */
  private ArrayNode toArray(JsonNode value, TransformContext context) {
    ArrayNode array = context.getObjectMapper().createArrayNode();

    if (value.isArray()) {
      for (JsonNode item : value) {
        if (!item.isNull()) {
          array.add(item);
        }
      }
    } else {
      array.add(value);
    }

    return array;
  }

  /**
   * Checks if all values in the array are numeric or can be parsed as numbers.
   * This allows string values like "1899.00" to be detected as numeric.
   */
  private boolean isAllNumeric(ArrayNode array) {
    for (JsonNode item : array) {
      if (!item.isNumber() && !isNumericString(item)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if a text node contains a parseable numeric value.
   */
  private boolean isNumericString(JsonNode item) {
    if (!item.isTextual()) {
      return false;
    }
    String text = item.asText().trim();
    if (text.isEmpty()) {
      return false;
    }
    try {
      Double.parseDouble(text);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  @Override
  public String getName() {
    return "fieldsToAttributeMap";
  }
}
