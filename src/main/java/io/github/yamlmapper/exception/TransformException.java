package io.github.yamlmapper.exception;

/**
 * Thrown when a transform fails to execute.
 *
 * <p>This occurs when a registered transform throws an exception or
 * cannot process the input data. The exception includes context about
 * which transform failed and on which field.
 *
 * <p>Example scenario:
 * <pre>{@code
 * // YAML configuration:
 * description:
 *   type: string
 *   source: [description]
 *   transform: truncate
 *   transformParams:
 *     maxLength: -5  // Invalid parameter
 *
 * // Results in:
 * TransformException: Transform 'truncate' failed on field 'description':
 *   maxLength must be positive
 * }</pre>
 *
 * <p>Note: Well-behaved transforms should handle invalid input gracefully
 * and return the input unchanged rather than throwing exceptions. This
 * exception is for truly exceptional cases like invalid parameters.
 */
public class TransformException extends MappingException {

  private final String transformName;
  private final String fieldName;

  /**
   * Creates a new TransformException.
   *
   * @param transformName the name of the transform that failed
   * @param fieldName the field being transformed
   * @param message the error message
   */
  public TransformException(String transformName, String fieldName, String message) {
    super(buildMessage(transformName, fieldName, message));
    this.transformName = transformName;
    this.fieldName = fieldName;
  }

  /**
   * Creates a new TransformException with a cause.
   *
   * @param transformName the name of the transform that failed
   * @param fieldName the field being transformed
   * @param message the error message
   * @param cause the underlying cause
   */
  public TransformException(String transformName, String fieldName, String message,
      Throwable cause) {
    super(buildMessage(transformName, fieldName, message), cause);
    this.transformName = transformName;
    this.fieldName = fieldName;
  }

  /**
   * Gets the name of the transform that failed.
   *
   * @return the transform name
   */
  public String getTransformName() {
    return transformName;
  }

  /**
   * Gets the name of the field being transformed.
   *
   * @return the field name
   */
  public String getFieldName() {
    return fieldName;
  }

  private static String buildMessage(String transformName, String fieldName, String message) {
    return String.format("Transform '%s' failed on field '%s': %s",
        transformName, fieldName, message);
  }
}
