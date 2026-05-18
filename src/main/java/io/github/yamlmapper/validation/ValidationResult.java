package io.github.yamlmapper.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of validating a mapping configuration or Protobuf message.
 *
 * <p>Contains information about whether the validation passed
 * and any errors found.
 *
 * <p>Example usage:
 * <pre>{@code
 * ValidationResult result = validator.validate(config);
 * if (!result.isValid()) {
 *     log.error("Validation errors: {}", result.errors());
 * }
 * }</pre>
 */
public record ValidationResult(
    boolean valid,
    List<String> errors,
    List<String> warnings
) {

  /**
   * Creates a valid result with no errors or warnings.
   *
   * @return a valid ValidationResult
   */
  public static ValidationResult success() {
    return new ValidationResult(true, List.of(), List.of());
  }

  /**
   * Checks if the configuration is valid (alias for valid() accessor).
   *
   * @return true if no errors were found
   */
  public boolean isValid() {
    return valid;
  }

  /**
   * Creates an invalid result with errors.
   *
   * @param errors the error messages
   * @return an invalid ValidationResult
   */
  public static ValidationResult invalid(List<String> errors) {
    return new ValidationResult(false, List.copyOf(errors), List.of());
  }

  /**
   * Builder for ValidationResult.
   */
  public static class Builder {
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public Builder addError(String error) {
      errors.add(error);
      return this;
    }

    public Builder addWarning(String warning) {
      warnings.add(warning);
      return this;
    }

    public Builder addErrors(List<String> errors) {
      this.errors.addAll(errors);
      return this;
    }

    public Builder addWarnings(List<String> warnings) {
      this.warnings.addAll(warnings);
      return this;
    }

    /**
     * Merges another ValidationResult into this builder.
     *
     * @param other the result to merge
     * @return this builder
     */
    public Builder merge(ValidationResult other) {
      if (other != null) {
        this.errors.addAll(other.errors());
        this.warnings.addAll(other.warnings());
      }
      return this;
    }

    public ValidationResult build() {
      return new ValidationResult(
          errors.isEmpty(),
          List.copyOf(errors),
          List.copyOf(warnings)
      );
    }
  }

  /**
   * Creates a new builder.
   *
   * @return a new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }
}
