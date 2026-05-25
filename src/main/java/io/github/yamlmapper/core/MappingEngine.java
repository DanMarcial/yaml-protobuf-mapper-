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

import java.io.IOException;
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
  private final boolean enablePostMappingValidation;


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

    // Initialize POST-mapping validators using registry
    if (enablePostMappingValidation) {
      this.validatorRegistry = builder.validatorRegistry != null
          ? builder.validatorRegistry
          : createValidatorRegistry(builder.customValidationSchemas);
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

    log.debug("Successfully mapped {} with {} fields", targetClass.getSimpleName(), fieldsToMap.size());
    return result;
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
   * <p>This validates the built message against constraints:
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
   * Creates a validator registry, using custom schemas when provided,
   * falling back to default schemas from classpath.
   *
   * @param customSchemas user-provided schemas (may be empty)
   * @return configured ValidatorRegistry
   */
  private static ValidatorRegistry createValidatorRegistry(Map<String, ProtobufConstraints> customSchemas) {
    ValidatorRegistry registry = new ValidatorRegistry();

    // Default schema paths (used when no custom schema provided)
    Map<String, String> defaultSchemaPaths = Map.of(
        "UserEvent", "schemas/user-event.schema.json",
        "Product", "schemas/product.schema.json"
    );

    // Register custom schemas first
    for (Map.Entry<String, ProtobufConstraints> entry : customSchemas.entrySet()) {
      String messageType = entry.getKey();
      ProtobufConstraints constraints = entry.getValue();
      registry.register(messageType, new ProtobufMessageValidator(constraints));
      log.info("Registered custom validator for '{}'", messageType);
    }

    // Register default schemas for types not already registered
    for (Map.Entry<String, String> entry : defaultSchemaPaths.entrySet()) {
      String messageType = entry.getKey();
      if (registry.hasValidator(messageType)) {
        continue; // Custom schema already registered
      }

      ProtobufMessageValidator validator = createValidator(entry.getValue());
      if (validator != null) {
        registry.register(messageType, validator);
        log.debug("Registered default validator for '{}'", messageType);
      }
    }

    return registry;
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
    private final Map<String, ProtobufConstraints> customValidationSchemas = new LinkedHashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();
    private boolean injectEventType = true;
    private boolean registerBuiltinTransforms = true;
    private boolean enablePostMappingValidation = false;
    private ValidatorRegistry validatorRegistry = null;
    private final YamlConfigLoader configLoader = new YamlConfigLoader();

    private Builder() {}

    /**
     * Sets a custom ObjectMapper for JSON parsing.
     *
     * @param objectMapper the ObjectMapper to use
     * @return this builder for chaining
     */
    public Builder withObjectMapper(ObjectMapper objectMapper) {
      this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
      return this;
    }

    /**
     * Adds a Protobuf package prefix for type resolution.
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
     * @param configPath the path to the YAML configuration
     * @return this builder for chaining
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
     * @param enable true to enable POST-mapping validation
     * @return this builder for chaining
     */
    public Builder enablePostMappingValidation(boolean enable) {
      this.enablePostMappingValidation = enable;
      return this;
    }

    /**
     * Sets a custom ValidatorRegistry for POST-mapping validation.
     *
     * @param validatorRegistry the ValidatorRegistry to use
     * @return this builder for chaining
     */
    public Builder withValidatorRegistry(ValidatorRegistry validatorRegistry) {
      this.validatorRegistry = Objects.requireNonNull(validatorRegistry, "ValidatorRegistry cannot be null");
      return this;
    }

    /**
     * Registers a custom validation schema for a message type.
     *
     * <p>Use this to override the default schemas or add stricter constraints:
     * <pre>{@code
     * MappingEngine engine = MappingEngine.builder()
     *     .withValidationSchema("UserEvent", "my-schemas/strict-user-event.schema.json")
     *     .enablePostMappingValidation(true)
     *     .build();
     * }</pre>
     *
     * @param messageType the Protobuf message type name (e.g., "UserEvent", "Product")
     * @param schemaPath classpath path to the JSON schema file
     * @return this builder for chaining
     * @throws ConfigurationException if the schema cannot be loaded
     */
    public Builder withValidationSchema(String messageType, String schemaPath) {
      Objects.requireNonNull(messageType, "messageType cannot be null");
      Objects.requireNonNull(schemaPath, "schemaPath cannot be null");
      try {
        ProtobufConstraints constraints = ProtobufConstraints.fromClasspath(schemaPath);
        customValidationSchemas.put(messageType, constraints);
        return this;
      } catch (IOException e) {
        throw new ConfigurationException("Failed to load validation schema: " + schemaPath, e);
      }
    }

    /**
     * Registers a custom validation schema for a message type.
     *
     * <p>Use this to provide pre-loaded constraints or load from external sources:
     * <pre>{@code
     * ProtobufConstraints strictConstraints = ProtobufConstraints.fromPath(
     *     Paths.get("/my-project/schemas/strict-user-event.json"));
     *
     * MappingEngine engine = MappingEngine.builder()
     *     .withValidationSchema("UserEvent", strictConstraints)
     *     .enablePostMappingValidation(true)
     *     .build();
     * }</pre>
     *
     * @param messageType the Protobuf message type name (e.g., "UserEvent", "Product")
     * @param constraints the pre-loaded constraints
     * @return this builder for chaining
     */
    public Builder withValidationSchema(String messageType, ProtobufConstraints constraints) {
      Objects.requireNonNull(messageType, "messageType cannot be null");
      Objects.requireNonNull(constraints, "constraints cannot be null");
      customValidationSchemas.put(messageType, constraints);
      return this;
    }

    /**
     * Validates the configuration before building.
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
     * @return the configured MappingEngine
     * @throws ConfigurationException if configuration is invalid
     */
    public MappingEngine build() {
      validate();

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
