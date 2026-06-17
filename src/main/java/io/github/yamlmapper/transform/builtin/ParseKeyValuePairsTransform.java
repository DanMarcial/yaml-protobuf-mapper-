package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a delimited string of key:value pairs into a map of CustomAttribute objects.
 *
 * <p>This transform handles legacy data formats where multiple attributes are encoded
 * in a single string field, such as:
 * <pre>{@code
 * "Price:56.99|Health Feature:Scientific Formula|Health Feature:Hip and Joint|Weight:29.1"
 * }</pre>
 *
 * <p>The output is a map suitable for Protobuf's {@code map<string, CustomAttribute>}:
 * <pre>{@code
 * {
 *   "Price": {"numbers": [56.99]},
 *   "Health Feature": {"text": ["Scientific Formula", "Hip and Joint"]},
 *   "Weight": {"numbers": [29.1]}
 * }
 * }</pre>
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code pairDelimiter} - Delimiter between pairs (default: "|")</li>
 *   <li>{@code keyValueDelimiter} - Delimiter between key and value (default: ":")</li>
 * </ul>
 *
 * <p>Example YAML configuration:
 * <pre>{@code
 * facetAttributes:
 *   type: map
 *   source: [facets]
 *   keyType: string
 *   valueType: object
 *   objectType: CustomAttribute
 *   transform: parseKeyValuePairs
 *   transformParams:
 *     pairDelimiter: "|"
 *     keyValueDelimiter: ":"
 *   fields:
 *     text:
 *       type: array
 *       itemType: string
 *       source: [text]
 *     numbers:
 *       type: array
 *       itemType: double
 *       source: [numbers]
 * }</pre>
 *
 * <p>Type detection rules:
 * <ul>
 *   <li>If ALL values for a key are numeric, uses "numbers"</li>
 *   <li>If ANY value is non-numeric, uses "text" for all values of that key</li>
 *   <li>Empty values are skipped</li>
 *   <li>Duplicate keys are grouped (values become array)</li>
 * </ul>
 */
public class ParseKeyValuePairsTransform implements Transform {

  private static final String PARAM_PAIR_DELIMITER = "pairDelimiter";
  private static final String PARAM_KV_DELIMITER = "keyValueDelimiter";
  private static final String DEFAULT_PAIR_DELIMITER = "|";
  private static final String DEFAULT_KV_DELIMITER = ":";

  @Override
  public JsonNode apply(JsonNode input, TransformContext context) {
    if (input == null || input.isNull() || !input.isTextual()) {
      return context.getObjectMapper().createObjectNode();
    }

    String text = input.asText().trim();
    if (text.isEmpty()) {
      return context.getObjectMapper().createObjectNode();
    }

    String pairDelimiter = context.getParam(PARAM_PAIR_DELIMITER, DEFAULT_PAIR_DELIMITER);
    String kvDelimiter = context.getParam(PARAM_KV_DELIMITER, DEFAULT_KV_DELIMITER);

    // Parse and group by key
    Map<String, List<String>> grouped = parseAndGroup(text, pairDelimiter, kvDelimiter);

    // Convert to CustomAttribute structure
    ObjectNode result = context.getObjectMapper().createObjectNode();

    for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
      String key = entry.getKey();
      List<String> values = entry.getValue();

      ObjectNode attribute = createAttribute(values, context);
      if (attribute != null) {
        result.set(key, attribute);
      }
    }

    return result;
  }

  /**
   * Parses the text and groups values by key.
   */
  private Map<String, List<String>> parseAndGroup(String text, String pairDelimiter, String kvDelimiter) {
    Map<String, List<String>> grouped = new HashMap<>();

    // Split by pair delimiter (escape regex special chars)
    String[] pairs = text.split(escapeRegex(pairDelimiter));

    for (String pair : pairs) {
      pair = pair.trim();
      if (pair.isEmpty()) {
        continue;
      }

      // Find the FIRST occurrence of kv delimiter (value may contain the delimiter)
      int delimIndex = pair.indexOf(kvDelimiter);
      if (delimIndex <= 0) {
        // No delimiter or empty key, skip
        continue;
      }

      String key = pair.substring(0, delimIndex).trim();
      String value = pair.substring(delimIndex + kvDelimiter.length()).trim();

      if (key.isEmpty() || value.isEmpty()) {
        continue;
      }

      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    return grouped;
  }

  /**
   * Creates a CustomAttribute object from a list of values.
   */
  private ObjectNode createAttribute(List<String> values, TransformContext context) {
    if (values.isEmpty()) {
      return null;
    }

    ObjectNode attribute = context.getObjectMapper().createObjectNode();

    // Check if all values are numeric
    boolean allNumeric = values.stream().allMatch(this::isNumeric);

    if (allNumeric) {
      ArrayNode numbers = context.getObjectMapper().createArrayNode();
      for (String value : values) {
        numbers.add(parseNumber(value));
      }
      attribute.set("numbers", numbers);
    } else {
      ArrayNode text = context.getObjectMapper().createArrayNode();
      for (String value : values) {
        text.add(value);
      }
      attribute.set("text", text);
    }

    return attribute;
  }

  /**
   * Checks if a string represents a numeric value.
   */
  private boolean isNumeric(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    try {
      Double.parseDouble(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Parses a string to a number.
   */
  private double parseNumber(String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  /**
   * Escapes regex special characters in a delimiter.
   */
  private String escapeRegex(String delimiter) {
    return delimiter.replaceAll("([\\[\\]{}()*+?^$\\\\|.])", "\\\\$1");
  }

  @Override
  public String getName() {
    return "parseKeyValuePairs";
  }
}
