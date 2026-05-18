package io.github.yamlmapper.exception;

/**
 * Base exception for all mapping errors in yaml-protobuf-mapper.
 *
 * <p>This is the root of the exception hierarchy. All specific mapping
 * exceptions extend this class, allowing callers to catch all mapping
 * errors with a single catch block if desired.
 *
 * <p>Example usage:
 * <pre>{@code
 * try {
 *     UserEvent event = engine.map(json, "search", UserEvent.class);
 * } catch (MappingException e) {
 *     // Handle any mapping error
 *     logger.error("Mapping failed: {}", e.getMessage());
 * }
 * }</pre>
 */
public class MappingException extends RuntimeException {

  /**
   * Creates a new MappingException with a message.
   *
   * @param message the error message
   */
  public MappingException(String message) {
    super(message);
  }

  /**
   * Creates a new MappingException with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public MappingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a new MappingException with a cause.
   *
   * @param cause the underlying cause
   */
  public MappingException(Throwable cause) {
    super(cause);
  }
}
