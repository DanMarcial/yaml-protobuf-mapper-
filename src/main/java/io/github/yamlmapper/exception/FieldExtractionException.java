package io.github.yamlmapper.exception;

import java.util.List;

/**
 * Thrown when a required field cannot be extracted from the input JSON.
 *
 * <p>This occurs when a field is marked as {@code required: true} in the YAML
 * configuration, but none of the specified source paths contain a value in
 * the input JSON.
 *
 * <p>Example scenario:
 * <pre>{@code
 * // YAML configuration:
 * visitorId:
 *   type: string
 *   source: [visitorId, visitor_id, vid]
 *   required: true
 *
 * // Input JSON (missing all sources):
 * {"userId": "123", "name": "John"}
 *
 * // Results in:
 * FieldExtractionException: Required field 'visitorId' not found.
 *   Tried sources: [visitorId, visitor_id, vid]
 * }</pre>
 */
public class FieldExtractionException extends MappingException {

  private final String fieldName;
  private final List<String> triedSources;

  /**
   * Creates a new FieldExtractionException.
   *
   * @param fieldName the name of the field that could not be extracted
   * @param triedSources the source paths that were attempted
   */
  public FieldExtractionException(String fieldName, List<String> triedSources) {
    super(buildMessage(fieldName, triedSources));
    this.fieldName = fieldName;
    this.triedSources = triedSources != null ? List.copyOf(triedSources) : List.of();
  }

  /**
   * Creates a new FieldExtractionException with a custom message.
   *
   * @param fieldName the name of the field that could not be extracted
   * @param triedSources the source paths that were attempted
   * @param message custom error message
   */
  public FieldExtractionException(String fieldName, List<String> triedSources, String message) {
    super(message);
    this.fieldName = fieldName;
    this.triedSources = triedSources != null ? List.copyOf(triedSources) : List.of();
  }

  /**
   * Creates a new FieldExtractionException with a cause.
   *
   * @param fieldName the name of the field that could not be extracted
   * @param message custom error message
   * @param cause the underlying cause
   */
  public FieldExtractionException(String fieldName, String message, Throwable cause) {
    super(message, cause);
    this.fieldName = fieldName;
    this.triedSources = List.of();
  }

  /**
   * Gets the name of the field that could not be extracted.
   *
   * @return the field name
   */
  public String getFieldName() {
    return fieldName;
  }

  /**
   * Gets the list of source paths that were tried.
   *
   * @return immutable list of tried sources
   */
  public List<String> getTriedSources() {
    return triedSources;
  }

  private static String buildMessage(String fieldName, List<String> sources) {
    if (sources == null || sources.isEmpty()) {
      return String.format("Required field '%s' not found (no sources configured)", fieldName);
    }
    return String.format("Required field '%s' not found. Tried sources: %s", fieldName, sources);
  }
}
