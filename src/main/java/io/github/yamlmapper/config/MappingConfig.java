package io.github.yamlmapper.config;

/**
 * Configuration options for the MappingEngine.
 *
 * <p>Consolidates all behavioral settings into a single, immutable configuration object.
 * This replaces scattered boolean flags with a clear, documented configuration API.
 *
 * <p>Example usage:
 * <pre>{@code
 * MappingConfig config = MappingConfig.builder()
 *     .maxJsonInputBytes(10 * 1024 * 1024)  // 10MB limit
 *     .maxNestingDepth(50)
 *     .injectEventType(true)
 *     .build();
 *
 * MappingEngine engine = MappingEngine.builder()
 *     .withConfig(config)
 *     .withProtobufPackage("com.example")
 *     .build();
 * }</pre>
 *
 * @param maxJsonInputBytes Maximum allowed JSON input size in bytes. -1 for unlimited.
 *                          Default: -1 (unlimited). Recommended for production: 10MB.
 * @param maxNestingDepth Maximum allowed nesting depth for nested objects/arrays.
 *                        Prevents StackOverflowError from circular configurations.
 *                        Default: 64.
 * @param injectEventType Whether to automatically set eventType field from configId.
 *                        Default: true.
 * @param registerBuiltinTransforms Whether to register built-in transforms.
 *                                  Default: true.
 * @param treatBlankAsNull Whether blank strings (whitespace only) should be treated as null.
 *                         When true, blank values will trigger default values and required checks.
 *                         Default: true.
 */
public record MappingConfig(
    long maxJsonInputBytes,
    int maxNestingDepth,
    boolean injectEventType,
    boolean registerBuiltinTransforms,
    boolean treatBlankAsNull
) {

  /** Default maximum nesting depth. */
  public static final int DEFAULT_MAX_NESTING_DEPTH = 64;

  /** Unlimited input size sentinel value. */
  public static final long UNLIMITED_INPUT_SIZE = -1L;

  /** Default configuration with sensible production defaults. */
  public static final MappingConfig DEFAULT = new MappingConfig(
      UNLIMITED_INPUT_SIZE,
      DEFAULT_MAX_NESTING_DEPTH,
      true,
      true,
      true
  );

  /**
   * Production-recommended configuration with size limits.
   * Uses 10MB input limit and 64-level nesting depth.
   */
  public static final MappingConfig PRODUCTION = new MappingConfig(
      10 * 1024 * 1024L, // 10MB
      DEFAULT_MAX_NESTING_DEPTH,
      true,
      true,
      true
  );

  /**
   * Validates the configuration values.
   */
  public MappingConfig {
    if (maxJsonInputBytes < UNLIMITED_INPUT_SIZE) {
      throw new IllegalArgumentException(
          "maxJsonInputBytes must be -1 (unlimited) or a positive value");
    }
    if (maxNestingDepth < 1) {
      throw new IllegalArgumentException("maxNestingDepth must be at least 1");
    }
  }

  /**
   * Returns true if JSON input size should be validated.
   */
  public boolean hasInputSizeLimit() {
    return maxJsonInputBytes != UNLIMITED_INPUT_SIZE;
  }

  /**
   * Creates a new builder with default values.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a new builder initialized from this config.
   */
  public Builder toBuilder() {
    return new Builder()
        .maxJsonInputBytes(maxJsonInputBytes)
        .maxNestingDepth(maxNestingDepth)
        .injectEventType(injectEventType)
        .registerBuiltinTransforms(registerBuiltinTransforms)
        .treatBlankAsNull(treatBlankAsNull);
  }

  /**
   * Builder for MappingConfig.
   */
  public static class Builder {
    private long maxJsonInputBytes = UNLIMITED_INPUT_SIZE;
    private int maxNestingDepth = DEFAULT_MAX_NESTING_DEPTH;
    private boolean injectEventType = true;
    private boolean registerBuiltinTransforms = true;
    private boolean treatBlankAsNull = true;

    private Builder() {}

    /**
     * Sets the maximum allowed JSON input size in bytes.
     *
     * @param bytes maximum bytes, or -1 for unlimited
     * @return this builder
     */
    public Builder maxJsonInputBytes(final long bytes) {
      this.maxJsonInputBytes = bytes;
      return this;
    }

    /**
     * Sets the maximum allowed nesting depth.
     *
     * @param depth maximum nesting depth (minimum 1)
     * @return this builder
     */
    public Builder maxNestingDepth(final int depth) {
      this.maxNestingDepth = depth;
      return this;
    }

    /**
     * Sets whether to inject configId as eventType.
     *
     * @param inject true to inject eventType
     * @return this builder
     */
    public Builder injectEventType(final boolean inject) {
      this.injectEventType = inject;
      return this;
    }

    /**
     * Sets whether to register built-in transforms.
     *
     * @param register true to register built-in transforms
     * @return this builder
     */
    public Builder registerBuiltinTransforms(final boolean register) {
      this.registerBuiltinTransforms = register;
      return this;
    }

    /**
     * Sets whether blank strings should be treated as null.
     *
     * @param treatAsNull true to treat blank strings as null
     * @return this builder
     */
    public Builder treatBlankAsNull(final boolean treatAsNull) {
      this.treatBlankAsNull = treatAsNull;
      return this;
    }

    /**
     * Builds the configuration.
     *
     * @return the immutable configuration
     */
    public MappingConfig build() {
      return new MappingConfig(
          maxJsonInputBytes,
          maxNestingDepth,
          injectEventType,
          registerBuiltinTransforms,
          treatBlankAsNull
      );
    }
  }
}
