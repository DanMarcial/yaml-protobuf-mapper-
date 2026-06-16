package io.github.yamlmapper.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yamlmapper.config.FieldConfig;
import java.util.Map;

/**
 * Context available during transform execution.
 *
 * <p>Provides access to:
 * <ul>
 *   <li>The current field being processed</li>
 *   <li>The field configuration from YAML</li>
 *   <li>The complete root JSON for accessing other fields</li>
 *   <li>Transform parameters from YAML configuration</li>
 *   <li>ObjectMapper for JSON operations</li>
 * </ul>
 *
 * <p>Example usage in a transform:
 * <pre>{@code
 * public JsonNode apply(JsonNode node, TransformContext context) {
 *     // Access transform parameters
 *     int maxLength = context.getParamAsInt("maxLength", 5000);
 *
 *     // Access the root JSON to read other fields
 *     JsonNode root = context.getRootNode();
 *     JsonNode otherField = root.get("someOtherField");
 *
 *     // Access current field info
 *     String fieldName = context.getFieldName();
 *
 *     // Perform transformation...
 * }
 * }</pre>
 */
public interface TransformContext {

  /**
   * Gets the name of the field currently being processed.
   *
   * @return the field name as defined in the YAML configuration
   */
  String getFieldName();

  /**
   * Gets the configuration for the current field.
   *
   * @return the field configuration, or null if not available
   */
  FieldConfig getFieldConfig();

  /**
   * Gets the complete root JSON node.
   *
   * <p>Useful for transforms that need to access multiple fields, such as
   * a transform that determines a value based on the combination of several
   * input fields.
   *
   * @return the root JSON node of the input document
   */
  JsonNode getRootNode();

  /**
   * Gets the ObjectMapper instance for JSON operations.
   *
   * <p>Useful for creating new JSON nodes or parsing embedded JSON strings.
   *
   * @return the shared ObjectMapper instance
   */
  ObjectMapper getObjectMapper();

  /**
   * Gets a transform parameter as a String.
   *
   * <p>Parameters are defined in YAML under {@code transformParams}:
   * <pre>{@code
   * fieldName:
   *   transform: myTransform
   *   transformParams:
   *     paramName: "value"
   * }</pre>
   *
   * @param name the parameter name
   * @return the parameter value, or null if not defined
   */
  String getParam(String name);

  /**
   * Gets a transform parameter as a String with a default value.
   *
   * @param name the parameter name
   * @param defaultValue value to return if parameter is not defined
   * @return the parameter value, or defaultValue if not defined
   */
  String getParam(String name, String defaultValue);

  /**
   * Gets a transform parameter as an integer.
   *
   * @param name the parameter name
   * @param defaultValue value to return if parameter is not defined or not a valid integer
   * @return the parameter value as int, or defaultValue if not defined/invalid
   */
  int getParamAsInt(String name, int defaultValue);

  /**
   * Gets a transform parameter as a boolean.
   *
   * <p>Recognizes "true", "yes", "1" as true (case-insensitive).
   *
   * @param name the parameter name
   * @param defaultValue value to return if parameter is not defined
   * @return the parameter value as boolean, or defaultValue if not defined
   */
  boolean getParamAsBoolean(String name, boolean defaultValue);

  /**
   * Gets a transform parameter as a double.
   *
   * @param name the parameter name
   * @param defaultValue value to return if parameter is not defined or not a valid number
   * @return the parameter value as double, or defaultValue if not defined/invalid
   */
  double getParamAsDouble(String name, double defaultValue);

  /**
   * Gets a transform parameter as a Map.
   *
   * <p>Useful for transforms like "switch" that need key-value mappings:
   * <pre>{@code
   * fieldName:
   *   transform: switch
   *   transformParams:
   *     cases:
   *       key1: "value1"
   *       key2: "value2"
   * }</pre>
   *
   * @param name the parameter name
   * @return the parameter as a Map, or empty map if not defined/invalid
   */
  Map<String, String> getParamAsMap(String name);

  /**
   * Gets a transform parameter as a List of Strings.
   *
   * <p>Useful for transforms that need a list of field names or values:
   * <pre>{@code
   * fieldName:
   *   transform: fieldsToAttributeMap
   *   transformParams:
   *     fields:
   *       - vendor
   *       - lengths_cm
   *       - heights_cm
   * }</pre>
   *
   * @param name the parameter name
   * @return the parameter as a List, or empty list if not defined/invalid
   */
  java.util.List<String> getParamAsList(String name);
}
