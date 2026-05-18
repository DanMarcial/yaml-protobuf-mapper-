package io.github.yamlmapper.exception;

/**
 * Thrown when a Protobuf type cannot be found in any registered package.
 *
 * <p>This occurs when the YAML configuration references a type (via {@code rootType}
 * or {@code objectType}) that doesn't exist in the packages registered with
 * {@code withProtobufPackage()}.
 *
 * <p>Example scenario:
 * <pre>{@code
 * // Engine configured with:
 * .withProtobufPackage("com.google.cloud.retail.v2")
 *
 * // YAML references non-existent type:
 * rootType: NonExistentType
 *
 * // Results in:
 * TypeNotFoundException: Type 'NonExistentType' not found in packages:
 *   [com.google.cloud.retail.v2]
 * }</pre>
 */
public class TypeNotFoundException extends MappingException {

  private final String typeName;

  /**
   * Creates a new TypeNotFoundException.
   *
   * @param typeName the type name that was not found
   * @param message the error message with details
   */
  public TypeNotFoundException(String typeName, String message) {
    super(message);
    this.typeName = typeName;
  }

  /**
   * Creates a new TypeNotFoundException with a cause.
   *
   * @param typeName the type name that was not found
   * @param message the error message with details
   * @param cause the underlying cause (typically ClassNotFoundException)
   */
  public TypeNotFoundException(String typeName, String message, Throwable cause) {
    super(message, cause);
    this.typeName = typeName;
  }

  /**
   * Gets the type name that was not found.
   *
   * @return the missing type name
   */
  public String getTypeName() {
    return typeName;
  }
}
