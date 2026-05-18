package io.github.yamlmapper.transform;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Interface for JSON transformations applied during field mapping.
 *
 * <p>Transforms allow YAML configurations to specify data transformations without code changes.
 * They are applied after extracting a value from JSON and before assigning it to the Protobuf field.
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class UpperCaseTransform implements Transform {
 *
 *     @Override
 *     public JsonNode apply(JsonNode node, TransformContext context) {
 *         if (node == null || !node.isTextual()) {
 *             return node;
 *         }
 *         return new TextNode(node.asText().toUpperCase());
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "upperCase";
 *     }
 * }
 * }</pre>
 *
 * <p>Usage in YAML:
 * <pre>{@code
 * title:
 *   type: string
 *   source: [title]
 *   transform: upperCase
 * }</pre>
 *
 * @see TransformContext
 */
@FunctionalInterface
public interface Transform {

  /**
   * Applies the transformation to a JSON node.
   *
   * <p>Implementations should:
   * <ul>
   *   <li>Handle null input gracefully (typically return null)</li>
   *   <li>Handle unexpected types gracefully (typically return input unchanged)</li>
   *   <li>Use context to access transform parameters, root JSON, etc.</li>
   *   <li>Never throw exceptions for invalid data (return input unchanged instead)</li>
   * </ul>
   *
   * @param node the input JSON node (may be null)
   * @param context the transform context with parameters and access to root JSON
   * @return the transformed JSON node
   */
  JsonNode apply(JsonNode node, TransformContext context);

  /**
   * Gets the unique name of this transform.
   *
   * <p>This name is used in YAML configurations to reference the transform:
   * <pre>{@code
   * fieldName:
   *   transform: thisName
   * }</pre>
   *
   * <p>By convention, transform names use camelCase (e.g., "singleItemToArray",
   * "parseJsonString").
   *
   * @return the transform name, must be unique within a registry
   */
  default String getName() {
    // Default: derive from class name
    String className = getClass().getSimpleName();
    if (className.endsWith("Transform")) {
      className = className.substring(0, className.length() - "Transform".length());
    }
    // Convert to camelCase
    if (!className.isEmpty()) {
      return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }
    return className;
  }

}
