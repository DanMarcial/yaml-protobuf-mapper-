package io.github.yamlmapper.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import io.github.yamlmapper.builder.BuilderFactory;
import io.github.yamlmapper.builder.GenericProtobufBuilder;
import io.github.yamlmapper.builder.SetterResolver;
import io.github.yamlmapper.builder.TypeConverter;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;

import static io.github.yamlmapper.core.MdcKeys.CONFIG_ID;
import static io.github.yamlmapper.core.MdcKeys.TARGET_TYPE;
import static io.github.yamlmapper.exception.ErrorMessages.ERR_CONFIG_ID_NULL;
import static io.github.yamlmapper.exception.ErrorMessages.ERR_CONFIG_NOT_FOUND;
import static io.github.yamlmapper.exception.ErrorMessages.ERR_JSON_NULL;

import io.github.yamlmapper.exception.ConfigurationException;
import io.github.yamlmapper.exception.MappingException;
import io.github.yamlmapper.extractor.EmbeddedJsonParser;
import io.github.yamlmapper.extractor.JsonNodeExtractor;
import io.github.yamlmapper.extractor.PathResolver;
import io.github.yamlmapper.extractor.TransformExecutor;
import io.github.yamlmapper.loader.YamlConfigLoader;
import io.github.yamlmapper.resolver.TypeResolver;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformRegistry;
import io.github.yamlmapper.transform.builtin.BuiltinTransforms;
import io.github.yamlmapper.validation.ProtobufConstraints;
import io.github.yamlmapper.validation.ProtobufMessageValidator;
import io.github.yamlmapper.validation.SchemaValidator;
import io.github.yamlmapper.validation.ValidationResult;
import io.github.yamlmapper.validation.ValidatorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Main entry point for mapping JSON to Protobuf messages using YAML configuration.
 *
 * <p>MappingEngine provides a fluent builder API for configuration and a simple
 * map() method for converting JSON to Protobuf messages.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Build the engine
 * MappingEngine engine = MappingEngine.builder()
 *     .withProtobufPackage("com.google.cloud.retail.v2")
 *     .withConfig("classpath:mapping/search.yaml")
 *     .withConfig("classpath:mapping/add-to-cart.yaml")
 *     .registerTransform("customTransform", new CustomTransform())
 *     .build();
 *
 * // Map JSON to Protobuf
 * // The configId "search" is automatically injected as eventType
 * UserEvent event = engine.map(jsonString, "search", UserEvent.class);
 * }</pre>
 *
 * <p>Key features:
 * <ul>
 *   <li>Auto-discovery of Protobuf types via package prefix</li>
 *   <li>MethodHandles for high performance (~3ns per operation)</li>
 *   <li>ConfigId automatically injected as eventType</li>
 *   <li>YAML-driven field mapping</li>
 *   <li>Built-in transforms (singleItemToArray, truncate, etc.)</li>
 * </ul>
 *
 * <p>This class is thread-safe after construction.
 */
public class MappingEngine {

  private static final Logger log = LoggerFactory.getLogger(MappingEngine.class);

  private final TypeResolver typeResolver;
  private final BuilderFactory builderFactory;
  private final SetterResolver setterResolver;
  private final TypeConverter typeConverter;
  private final JsonNodeExtractor extractor;
  private final TransformRegistry transformRegistry;
  private final GenericProtobufBuilder protobufBuilder;
  private final ObjectMapper objectMapper;
  private final Map<String, MappingSchema> configCache;
  private final boolean injectEventType;
  private final PathResolver pathResolver;
  private final EmbeddedJsonParser jsonParser;
  private final TransformExecutor transformExecutor;
  private final SchemaValidator schemaValidator;
  private final ValidatorRegistry validatorRegistry;
  private final MappingMetrics metrics;
  private final boolean enablePostMappingValidation;
  private final boolean enableMetrics;


  private MappingEngine(Builder builder) {
    this.objectMapper = builder.objectMapper;
    this.typeResolver = new TypeResolver(builder.packagePrefixes);

    this.pathResolver = new PathResolver();
    this.jsonParser = new EmbeddedJsonParser(objectMapper);
    this.transformExecutor = new TransformExecutor(builder.transformRegistry, objectMapper);

    this.builderFactory = new BuilderFactory();
    this.setterResolver = new SetterResolver();
    this.typeConverter = new TypeConverter();

    this.extractor = new JsonNodeExtractor(
            pathResolver,
            jsonParser,
            transformExecutor
    );

    this.transformRegistry = builder.transformRegistry;
    this.configCache = Map.copyOf(builder.configCache);
    this.injectEventType = builder.injectEventType;
    this.schemaValidator = new SchemaValidator(typeResolver, transformRegistry);
    this.enablePostMappingValidation = builder.enablePostMappingValidation;
    this.enableMetrics = builder.enableMetrics;
    this.metrics = builder.metrics != null ? builder.metrics : new MappingMetrics();

    // Initialize POST-mapping validators using registry
    if (enablePostMappingValidation) {
      this.validatorRegistry = builder.validatorRegistry != null
          ? builder.validatorRegistry
          : createDefaultValidatorRegistry();
    } else {
      this.validatorRegistry = new ValidatorRegistry();
    }

    this.protobufBuilder = new GenericProtobufBuilder(
            extractor,
            typeConverter,
            setterResolver,
            typeResolver,
            builderFactory
    );
  }

  /**
   * Maps a JSON string to a Protobuf message using the specified config.
   *
   * <p>The configId is used to:
   * <ol>
   *   <li>Look up the YAML configuration</li>
   *   <li>Automatically inject as eventType (if message has that field)</li>
   * </ol>
   *
   * @param jsonString the JSON string to map
   * @param configId the configuration ID (e.g., "search", "add-to-cart")
   * @param targetClass the target Protobuf message class
   * @param <T> the message type
   * @return the mapped Protobuf message
   * @throws MappingException if mapping fails
   */
  public <T extends Message> T map(String jsonString, String configId, Class<T> targetClass) {
    try {
      JsonNode json = objectMapper.readTree(jsonString);
      return map(json, configId, targetClass);
    } catch (IOException e) {
      throw new MappingException("Failed to parse JSON: " + e.getMessage(), e);
    }
  }

  /**
   * Maps a JsonNode to a Protobuf message using the specified config.
   *
   * @param json the JSON node to map
   * @param configId the configuration ID
   * @param targetClass the target Protobuf message class
   * @param <T> the message type
   * @return the mapped Protobuf message
   * @throws MappingException if mapping fails
   */
  public <T extends Message> T map(JsonNode json, String configId, Class<T> targetClass) {
    // Set MDC context for log correlation
    MDC.put(CONFIG_ID, configId);
    MDC.put(TARGET_TYPE, targetClass.getSimpleName());

    long startNanos = enableMetrics ? System.nanoTime() : 0;

    try {
      log.debug("Mapping JSON to {} using config '{}'", targetClass.getSimpleName(), configId);

      if (json == null) {
        throw new MappingException(ERR_JSON_NULL);
      }
      if (configId == null || configId.isBlank()) {
        throw new MappingException(ERR_CONFIG_ID_NULL);
      }

      MappingSchema schema = getConfig(configId);
      if (schema == null) {
        throw new ConfigurationException(String.format(ERR_CONFIG_NOT_FOUND, configId));
      }

      Message.Builder builder = builderFactory.createBuilder(targetClass);

      if (injectEventType) protobufBuilder.setBuilderEventType(builder, configId);

      Map<String, FieldConfig> fieldsToMap = schema.fields();
      protobufBuilder.build(builder, json, fieldsToMap);

      @SuppressWarnings("unchecked")
      T result = (T) builder.build();

      if (enableMetrics) {
        metrics.recordSuccess(System.nanoTime() - startNanos);
      }

      log.debug("Successfully mapped {} with {} fields", targetClass.getSimpleName(), fieldsToMap.size());
      return result;

    } catch (Exception e) {
      if (enableMetrics) {
        metrics.recordError();
      }
      throw e;
    } finally {
      MDC.remove(CONFIG_ID);
      MDC.remove(TARGET_TYPE);
    }
  }

  /**
   * Maps a JsonNode to a Protobuf message with detailed status information.
   *
   * <p>This method provides insight into the mapping process, including which fields
   * were mapped, which used defaults, and any warnings encountered.
   *
   * @param json the JSON node to map
   * @param configId the configuration ID
   * @param targetClass the target Protobuf message class
   * @param <T> the message type
   * @return MappingResult containing the message and status details
   * @throws MappingException if mapping fails
   */
  public <T extends Message> MappingResult<T> mapWithDetails(
      JsonNode json,
      String configId,
      Class<T> targetClass) {

    // Set MDC context for log correlation
    MDC.put(CONFIG_ID, configId);
    MDC.put(TARGET_TYPE, targetClass.getSimpleName());

    long startTime = System.nanoTime();

    try {
      log.debug("Mapping JSON to {} with tracking using config '{}'", targetClass.getSimpleName(), configId);

      if (json == null) {
        throw new MappingException(ERR_JSON_NULL);
      }
      if (configId == null || configId.isBlank()) {
        throw new MappingException(ERR_CONFIG_ID_NULL);
      }

      MappingSchema schema = getConfig(configId);
      if (schema == null) {
        throw new ConfigurationException(String.format(ERR_CONFIG_NOT_FOUND, configId));
      }

      Message.Builder builder = builderFactory.createBuilder(targetClass);

      if (injectEventType) {
        protobufBuilder.setBuilderEventType(builder, configId);
      }

      Map<String, MappingResult.FieldStatus> fieldStatuses = new LinkedHashMap<>();
      List<String> warnings = new ArrayList<>();
      Map<String, FieldConfig> fieldsToMap = schema.fields();

      T message = protobufBuilder.buildWithTracking(builder, json, fieldsToMap, fieldStatuses, warnings);

      long endTime = System.nanoTime();
      Duration mappingTime = Duration.ofNanos(endTime - startTime);

      if (enableMetrics) {
        metrics.recordSuccess(mappingTime.toNanos());
      }

      if (!warnings.isEmpty()) {
        log.warn("Mapping completed with {} warnings for config '{}'", warnings.size(), configId);
      }

      // Perform POST-mapping validation if enabled
      ValidationResult validationResult = null;
      if (enablePostMappingValidation) {
        validationResult = validateMessageInternal(message);
        if (!validationResult.isValid()) {
          metrics.recordValidationError();
          log.warn("POST-mapping validation failed for config '{}': {} errors",
              configId, validationResult.errors().size());
        }
      }

      log.debug("Mapped {} in {}us - {} fields", targetClass.getSimpleName(), mappingTime.toNanos() / 1000, fieldStatuses.size());

      return MappingResult.<T>builder()
          .message(message)
          .fieldStatuses(fieldStatuses)
          .warnings(warnings)
          .mappingTime(mappingTime)
          .validationResult(validationResult)
          .build();

    } catch (Exception e) {
      if (enableMetrics) {
        metrics.recordError();
      }
      throw e;
    } finally {
      MDC.remove(CONFIG_ID);
      MDC.remove(TARGET_TYPE);
    }
  }

  /**
   * Gets a cached configuration by ID.
   *
   * @param configId the configuration ID
   * @return the MappingSchema, or null if not found
   */
  public MappingSchema getConfig(String configId) {
    return configCache.get(configId);
  }

  /**
   * Validates a configuration without executing any mapping.
   *
   * <p>This method checks the configuration for potential issues:
   * <ul>
   *   <li>Config exists</li>
   *   <li>Fields have valid types</li>
   *   <li>Object/array types have required nested configuration</li>
   *   <li>Transforms exist in registry</li>
   *   <li>Sources are not empty</li>
   * </ul>
   *
   * @param configId the configuration ID to validate
   * @return ValidationResult containing any errors and warnings found
   */
  public ValidationResult validateConfig(String configId) {
    log.debug("Validating configuration '{}'", configId);

    if (configId == null || configId.isBlank()) {
      return ValidationResult.invalid(List.of(ERR_CONFIG_ID_NULL));
    }

    MappingSchema schema = configCache.get(configId);
    if (schema == null) {
      return ValidationResult.invalid(List.of(String.format(ERR_CONFIG_NOT_FOUND, configId)));
    }

    return schemaValidator.validate(schema, configId);
  }

  /**
   * Validates a Protobuf message against schema constraints (POST-mapping validation).
   *
   * <p>This validates the built message against Google Cloud Retail API constraints:
   * <ul>
   *   <li>Required fields (always required and conditional by eventType)</li>
   *   <li>String maxLength limits</li>
   *   <li>Numeric ranges (minimum/maximum)</li>
   *   <li>Enum values</li>
   * </ul>
   *
   * <p>Note: POST-mapping validation must be enabled via builder.enablePostMappingValidation(true)
   *
   * @param message the Protobuf message to validate
   * @return ValidationResult with any constraint violations
   * @throws IllegalStateException if POST-mapping validation is not enabled
   */
  public ValidationResult validateMessage(Message message) {
    if (!enablePostMappingValidation) {
      throw new IllegalStateException(
          "POST-mapping validation is not enabled. Use builder.enablePostMappingValidation(true)");
    }

    if (message == null) {
      return ValidationResult.invalid(List.of("Message cannot be null"));
    }

    String messageType = message.getDescriptorForType().getName();
    ProtobufMessageValidator validator = getValidatorForType(messageType);

    if (validator == null) {
      log.debug("No validator available for message type: {}", messageType);
      return ValidationResult.success();
    }

    return validator.validate(message);
  }

  /**
   * Checks if POST-mapping validation is enabled.
   *
   * @return true if POST-mapping validation is enabled
   */
  public boolean isPostMappingValidationEnabled() {
    return enablePostMappingValidation;
  }

  /**
   * Gets the appropriate validator for a message type.
   */
  private ProtobufMessageValidator getValidatorForType(String messageType) {
    return validatorRegistry.get(messageType);
  }

  /**
   * Internal validation method used by mapWithDetails.
   */
  private ValidationResult validateMessageInternal(Message message) {
    if (message == null) {
      return ValidationResult.invalid(List.of("Message cannot be null"));
    }

    String messageType = message.getDescriptorForType().getName();
    ProtobufMessageValidator validator = getValidatorForType(messageType);

    if (validator == null) {
      return ValidationResult.success();
    }

    return validator.validate(message);
  }

  /**
   * Creates a validator from a schema path, returning null if loading fails.
   */
  private static ProtobufMessageValidator createValidator(String schemaPath) {
    try {
      ProtobufConstraints constraints = ProtobufConstraints.fromClasspath(schemaPath);
      return new ProtobufMessageValidator(constraints);
    } catch (IOException e) {
      log.warn("Failed to load schema for POST-mapping validation: {}. Validation will be skipped.", schemaPath);
      return null;
    }
  }

  /**
   * Creates the default validator registry with built-in validators.
   */
  private static ValidatorRegistry createDefaultValidatorRegistry() {
    ValidatorRegistry registry = new ValidatorRegistry();

    ProtobufMessageValidator userEventValidator = createValidator("schemas/user-event.schema.json");
    if (userEventValidator != null) {
      registry.register("UserEvent", userEventValidator);
    }

    ProtobufMessageValidator productValidator = createValidator("schemas/product.schema.json");
    if (productValidator != null) {
      registry.register("Product", productValidator);
    }

    return registry;
  }

  /**
   * Gets the metrics collector for this engine.
   *
   * <p>Use this to access mapping statistics such as success/error counts,
   * latency measurements, and validation error counts.
   *
   * @return the MappingMetrics instance
   */
  public MappingMetrics getMetrics() {
    return metrics;
  }

  /**
   * Checks if metrics collection is enabled.
   *
   * @return true if metrics are being collected
   */
  public boolean isMetricsEnabled() {
    return enableMetrics;
  }

  /**
   * Gets the validator registry.
   *
   * @return the ValidatorRegistry instance
   */
  public ValidatorRegistry getValidatorRegistry() {
    return validatorRegistry;
  }

  /**
   * Gets the ObjectMapper used for JSON parsing.
   *
   * <p>This ObjectMapper is thread-safe and should be reused
   * for all JSON operations to avoid unnecessary object creation.
   *
   * @return the ObjectMapper instance
   */
  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  /**
   * Creates a new builder for MappingEngine.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for MappingEngine.
   *
   * <p>Provides fluent API for configuring the engine.
   */
  public static class Builder {
    private final List<String> packagePrefixes = new ArrayList<>();
    private final Map<String, MappingSchema> configCache = new LinkedHashMap<>();
    private final TransformRegistry transformRegistry = new TransformRegistry();
    private ObjectMapper objectMapper = new ObjectMapper();
    private boolean injectEventType = true;
    private boolean registerBuiltinTransforms = true;
    private boolean enablePostMappingValidation = false;
    private boolean enableMetrics = false;
    private MappingMetrics metrics = null;
    private ValidatorRegistry validatorRegistry = null;
    private final YamlConfigLoader configLoader = new YamlConfigLoader();

    private Builder() {}

    /**
     * Sets a custom ObjectMapper for JSON parsing.
     *
     * <p>Use this to share an ObjectMapper instance across your application
     * or to customize serialization settings (date formats, null handling, etc.).
     *
     * <p>If not set, a default ObjectMapper is created.
     *
     * @param objectMapper the ObjectMapper to use
     * @return this builder for chaining
     * @throws NullPointerException if objectMapper is null
     */
    public Builder withObjectMapper(ObjectMapper objectMapper) {
      this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
      return this;
    }

    /**
     * Adds a Protobuf package prefix for type resolution.
     *
     * <p>When resolving types like "UserEvent", the engine will try
     * each package prefix in order until a match is found.
     *
     * @param packagePrefix the package prefix (e.g., "com.google.cloud.retail.v2")
     * @return this builder for chaining
     */
    public Builder withProtobufPackage(String packagePrefix) {
      if (packagePrefix != null && !packagePrefix.isBlank()) {
        packagePrefixes.add(packagePrefix);
      }
      return this;
    }

    /**
     * Adds multiple Protobuf package prefixes.
     *
     * @param prefixes the package prefixes
     * @return this builder for chaining
     */
    public Builder withProtobufPackages(String... prefixes) {
      for (String prefix : prefixes) {
        withProtobufPackage(prefix);
      }
      return this;
    }

    /**
     * Loads a YAML configuration file.
     *
     * <p>Supports multiple path formats:
     * <ul>
     *   <li>{@code classpath:mapping/search.yaml} - from classpath</li>
     *   <li>{@code file:/path/to/config.yaml} - from file system</li>
     *   <li>{@code search.yaml} - relative classpath path</li>
     * </ul>
     *
     * <p>The configId is derived from the filename without extension.
     *
     * @param configPath the path to the YAML configuration
     * @return this builder for chaining
     * @throws ConfigurationException if loading fails
     */
    public Builder withConfig(String configPath) {
      String configId = configLoader.extractConfigId(configPath);
      MappingSchema schema = configLoader.load(configPath);
      configCache.put(configId, schema);
      return this;
    }

    /**
     * Registers a MappingSchema directly with an explicit configId.
     *
     * @param configId the configuration identifier
     * @param schema the mapping schema
     * @return this builder for chaining
     */
    public Builder withSchema(String configId, MappingSchema schema) {
      configCache.put(configId, schema);
      return this;
    }

    /**
     * Registers a custom transform using its getName() method.
     *
     * @param transform the transform to register
     * @return this builder for chaining
     */
    public Builder registerTransform(Transform transform) {
      transformRegistry.register(transform);
      return this;
    }

    /**
     * Sets whether to register builtin transforms.
     *
     * <p>Default is true.
     *
     * @param register true to register builtins
     * @return this builder for chaining
     */
    public Builder registerBuiltinTransforms(boolean register) {
      this.registerBuiltinTransforms = register;
      return this;
    }

    /**
     * Sets whether to automatically inject configId as eventType.
     *
     * <p>Default is true.
     *
     * @param inject true to inject eventType
     * @return this builder for chaining
     */
    public Builder injectEventType(boolean inject) {
      this.injectEventType = inject;
      return this;
    }

    /**
     * Enables POST-mapping validation against Protobuf schema constraints.
     *
     * <p>When enabled, you can call validateMessage() to check:
     * <ul>
     *   <li>Required fields (always required and conditional by eventType)</li>
     *   <li>String maxLength limits</li>
     *   <li>Numeric ranges (minimum/maximum)</li>
     *   <li>Enum values</li>
     * </ul>
     *
     * <p>Default is false (disabled).
     *
     * @param enable true to enable POST-mapping validation
     * @return this builder for chaining
     */
    public Builder enablePostMappingValidation(boolean enable) {
      this.enablePostMappingValidation = enable;
      return this;
    }

    /**
     * Enables metrics collection for mapping operations.
     *
     * <p>When enabled, the engine tracks:
     * <ul>
     *   <li>Total, successful, and failed mapping counts</li>
     *   <li>Latency statistics (average, min, max)</li>
     *   <li>Validation error counts</li>
     * </ul>
     *
     * <p>Access metrics via {@link MappingEngine#getMetrics()}.
     *
     * <p>Default is false (disabled).
     *
     * @param enable true to enable metrics collection
     * @return this builder for chaining
     */
    public Builder enableMetrics(boolean enable) {
      this.enableMetrics = enable;
      return this;
    }

    /**
     * Sets a custom MappingMetrics instance for metrics collection.
     *
     * <p>Use this to share metrics across multiple engines or to integrate
     * with external monitoring systems.
     *
     * <p>Implicitly enables metrics collection.
     *
     * @param metrics the MappingMetrics instance to use
     * @return this builder for chaining
     * @throws NullPointerException if metrics is null
     */
    public Builder withMetrics(MappingMetrics metrics) {
      this.metrics = Objects.requireNonNull(metrics, "MappingMetrics cannot be null");
      this.enableMetrics = true;
      return this;
    }

    /**
     * Sets a custom ValidatorRegistry for POST-mapping validation.
     *
     * <p>Use this to register validators for custom message types or to
     * override the default validators.
     *
     * <p>Example:
     * <pre>{@code
     * ValidatorRegistry registry = new ValidatorRegistry()
     *     .register("UserEvent", myUserEventValidator)
     *     .register("CustomMessage", customValidator);
     *
     * MappingEngine engine = MappingEngine.builder()
     *     .withValidatorRegistry(registry)
     *     .enablePostMappingValidation(true)
     *     .build();
     * }</pre>
     *
     * @param validatorRegistry the ValidatorRegistry to use
     * @return this builder for chaining
     * @throws NullPointerException if validatorRegistry is null
     */
    public Builder withValidatorRegistry(ValidatorRegistry validatorRegistry) {
      this.validatorRegistry = Objects.requireNonNull(validatorRegistry, "ValidatorRegistry cannot be null");
      return this;
    }

    /**
     * Validates the configuration before building.
     *
     * <p>This is called automatically during build().
     *
     * @throws ConfigurationException if configuration is invalid
     */
    public void validate() {
      if (packagePrefixes.isEmpty()) {
        throw new ConfigurationException(
            "At least one Protobuf package prefix is required. " +
            "Use .withProtobufPackage() to add one.");
      }
    }

    /**
     * Builds the MappingEngine.
     *
     * <p>All loaded configurations are validated at build time.
     * Invalid configurations will cause a ConfigurationException.
     * This provides fail-fast behavior to catch configuration errors early.
     *
     * @return the configured MappingEngine
     * @throws ConfigurationException if configuration is invalid
     */
    public MappingEngine build() {
      validate();

      // Register builtin transforms if enabled
      if (registerBuiltinTransforms) {
        BuiltinTransforms.registerAll(transformRegistry);
      }

      MappingEngine engine = new MappingEngine(this);
      validateAllConfigs(engine);

      return engine;
    }

    private void validateAllConfigs(MappingEngine engine) {
      List<String> allErrors = new ArrayList<>();

      for (String configId : configCache.keySet()) {
        ValidationResult result = engine.validateConfig(configId);
        if (!result.isValid()) {
          allErrors.add(String.format("Config '%s': %s", configId, String.join("; ", result.errors())));
        }
      }

      if (!allErrors.isEmpty()) {
        throw new ConfigurationException(
            "Schema validation failed:\n  - " + String.join("\n  - ", allErrors));
      }
    }
  }
}
