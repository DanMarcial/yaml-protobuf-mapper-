package io.github.yamlmapper.core;

import com.google.protobuf.Message;
import io.github.yamlmapper.validation.ValidationResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Result of a mapping operation with detailed status information.
 *
 * <p>Provides insight into:
 * <ul>
 *   <li>The mapped message</li>
 *   <li>Status of each field (mapped, default used, skipped, etc.)</li>
 *   <li>Warnings encountered during mapping</li>
 *   <li>Total mapping time</li>
 *   <li>POST-mapping validation results</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * MappingResult<UserEvent> result = engine.mapWithDetails(json, "search", UserEvent.class);
 *
 * if (!result.isValid()) {
 *     log.error("Validation errors: {}", result.validationErrors());
 * }
 *
 * UserEvent event = result.message();
 * }</pre>
 *
 * @param <T> the Protobuf message type
 */
public record MappingResult<T extends Message>(
    T message,
    Map<String, FieldStatus> fieldStatuses,
    List<String> warnings,
    Duration mappingTime,
    ValidationResult validationResult
) {

  /**
   * Status of a field after mapping.
   */
  public enum FieldStatus {
    /**
     * Field was successfully mapped from JSON.
     */
    MAPPED,

    /**
     * Field used its default value because source was not found.
     */
    DEFAULT_USED,

    /**
     * Optional field was not found and skipped (no default configured).
     */
    SKIPPED_OPTIONAL,

    /**
     * A transform was applied to the field value.
     */
    TRANSFORM_APPLIED,

    /**
     * Field mapping failed but was non-fatal.
     */
    FAILED
  }

  /**
   * Creates a builder for MappingResult.
   *
   * @param <T> the message type
   * @return a new builder
   */
  public static <T extends Message> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * Checks if POST-mapping validation was performed and passed.
   *
   * @return true if validation passed or was not performed
   */
  public boolean isValid() {
    return validationResult == null || validationResult.isValid();
  }

  /**
   * Checks if POST-mapping validation was performed.
   *
   * @return true if validation was performed
   */
  public boolean hasValidation() {
    return validationResult != null;
  }

  /**
   * Gets validation errors if any.
   *
   * @return list of validation errors, or empty list
   */
  public List<String> validationErrors() {
    return validationResult != null ? validationResult.errors() : List.of();
  }

  /**
   * Builder for MappingResult.
   *
   * @param <T> the message type
   */
  public static class Builder<T extends Message> {
    private T message;
    private Map<String, FieldStatus> fieldStatuses = Map.of();
    private List<String> warnings = List.of();
    private Duration mappingTime = Duration.ZERO;
    private ValidationResult validationResult = null;

    public Builder<T> message(T message) {
      this.message = message;
      return this;
    }

    public Builder<T> fieldStatuses(Map<String, FieldStatus> fieldStatuses) {
      this.fieldStatuses = Map.copyOf(fieldStatuses);
      return this;
    }

    public Builder<T> warnings(List<String> warnings) {
      this.warnings = List.copyOf(warnings);
      return this;
    }

    public Builder<T> mappingTime(Duration mappingTime) {
      this.mappingTime = mappingTime;
      return this;
    }

    public Builder<T> validationResult(ValidationResult validationResult) {
      this.validationResult = validationResult;
      return this;
    }

    public MappingResult<T> build() {
      return new MappingResult<>(message, fieldStatuses, warnings, mappingTime, validationResult);
    }
  }
}
