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
import io.github.yamlmapper.extractor.PathResolver;
import io.github.yamlmapper.loader.YamlConfigLoader;
import io.github.yamlmapper.resolver.TypeResolver;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformRegistry;
import io.github.yamlmapper.transform.builtin.BuiltinTransforms;
import io.github.yamlmapper.validation.SchemaValidator;
import io.github.yamlmapper.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Main entry point for mapping JSON to Protobuf messages using YAML configuration.
 *
 * <p>Example usage:
 * <pre>{@code
 * MappingEngine engine = MappingEngine.builder()
 *     .withProtobufPackage("com.google.cloud.retail.v2")
 *     .withConfig("classpath:mapping/search.yaml")
 *     .build();
 *
 * UserEvent event = engine.map(jsonString, "search", UserEvent.class);
 * }</pre>
 *
 * <p>This class is thread-safe after construction.
 */
public class MappingEngine {

  private static final Logger log = LoggerFactory.getLogger(MappingEngine.class);

  private final BuilderFactory builderFactory;
  private final GenericProtobufBuilder protobufBuilder;
  private final ObjectMapper objectMapper;
  private final Map<String, MappingSchema> configCache;
  private final boolean injectEventType;
  private final SchemaValidator schemaValidator;

  private MappingEngine(Builder builder) {
    this.objectMapper = builder.objectMapper;
    this.builderFactory = new BuilderFactory();
    this.configCache = Map.copyOf(builder.configCache);
    this.injectEventType = builder.injectEventType;

    TypeResolver typeResolver = new TypeResolver(builder.packagePrefixes);
    this.schemaValidator = new SchemaValidator(typeResolver, builder.transformRegistry);

    this.protobufBuilder = new GenericProtobufBuilder(
            new PathResolver(),
            builder.transformRegistry,
            objectMapper,
            new TypeConverter(),
            new SetterResolver(),
            typeResolver,
            builderFactory
    );
  }

  /**
   * Maps a JSON string to a Protobuf message.
   *
   * @param jsonString the JSON string to map
   * @param configId the configuration ID (e.g., "search", "add-to-cart")
   * @param targetClass the target Protobuf message class
   * @param <T> the message type
   * @return the mapped Protobuf message
   * @throws MappingException if mapping fails
   */
  public <T extends Message> T map(final String jsonString, final String configId, final Class<T> targetClass) {
    try {
      JsonNode json = objectMapper.readTree(jsonString);
      return map(json, configId, targetClass);
    } catch (IOException e) {
      throw new MappingException("Failed to parse JSON: " + e.getMessage(), e);
    }
  }

  /**
   * Maps a JsonNode to a Protobuf message.
   *
   * @param json the JSON node to map
   * @param configId the configuration ID
   * @param targetClass the target Protobuf message class
   * @param <T> the message type
   * @return the mapped Protobuf message
   * @throws MappingException if mapping fails
   */
  public <T extends Message> T map(final JsonNode json, final String configId, final Class<T> targetClass) {
    log.debug("Mapping JSON to {} using config '{}'", targetClass.getSimpleName(), configId);

    if (json == null) {
      throw new MappingException(ERR_JSON_NULL);
    }
    if (configId == null || configId.isBlank()) {
      throw new MappingException(ERR_CONFIG_ID_NULL);
    }

    MappingSchema schema = configCache.get(configId);
    if (schema == null) {
      throw new ConfigurationException(String.format(ERR_CONFIG_NOT_FOUND, configId));
    }

    Message.Builder builder = builderFactory.createBuilder(targetClass);

    if (injectEventType) {
      protobufBuilder.setBuilderEventType(builder, configId);
    }

    Map<String, FieldConfig> fieldsToMap = schema.fields();
    protobufBuilder.build(builder, json, fieldsToMap);

    @SuppressWarnings("unchecked")
    T result = (T) builder.build();

    log.debug("Successfully mapped {} with {} fields", targetClass.getSimpleName(), fieldsToMap.size());
    return result;
  }

  /**
   * Creates a new builder for MappingEngine.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for MappingEngine.
   */
  public static class Builder {
    private final List<String> packagePrefixes = new ArrayList<>();
    private final Map<String, MappingSchema> configCache = new HashMap<>();
    private final TransformRegistry transformRegistry = new TransformRegistry();
    private final YamlConfigLoader configLoader = new YamlConfigLoader();
    private ObjectMapper objectMapper = new ObjectMapper();
    private boolean injectEventType = true;
    private boolean registerBuiltinTransforms = true;

    private Builder() {}

    /**
     * Sets a custom ObjectMapper for JSON parsing.
     */
    public Builder withObjectMapper(final ObjectMapper objectMapper) {
      this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
      return this;
    }

    /**
     * Adds a Protobuf package prefix for type resolution.
     */
    public Builder withProtobufPackage(final String packagePrefix) {
      if (packagePrefix != null && !packagePrefix.isBlank()) {
        packagePrefixes.add(packagePrefix);
      }
      return this;
    }

    /**
     * Adds multiple Protobuf package prefixes.
     */
    public Builder withProtobufPackages(final String... prefixes) {
      for (final String prefix : prefixes) {
        withProtobufPackage(prefix);
      }
      return this;
    }

    /**
     * Loads a YAML configuration file.
     */
    public Builder withConfig(final String configPath) {
      final String configId = configLoader.extractConfigId(configPath);
      final MappingSchema schema = configLoader.load(configPath);
      configCache.put(configId, schema);
      return this;
    }

    /**
     * Registers a MappingSchema directly with an explicit configId.
     */
    public Builder withSchema(final String configId, final MappingSchema schema) {
      configCache.put(configId, schema);
      return this;
    }

    /**
     * Registers a custom transform.
     */
    public Builder registerTransform(final Transform transform) {
      transformRegistry.register(transform);
      return this;
    }

    /**
     * Sets whether to register builtin transforms (default: true).
     */
    public Builder registerBuiltinTransforms(final boolean register) {
      this.registerBuiltinTransforms = register;
      return this;
    }

    /**
     * Sets whether to automatically inject configId as eventType (default: true).
     */
    public Builder injectEventType(final boolean inject) {
      this.injectEventType = inject;
      return this;
    }

    /**
     * Builds the MappingEngine.
     *
     * @throws ConfigurationException if configuration is invalid
     */
    public MappingEngine build() {
      if (packagePrefixes.isEmpty()) {
        throw new ConfigurationException(
            "At least one Protobuf package prefix is required. Use .withProtobufPackage() to add one.");
      }

      if (registerBuiltinTransforms) {
        BuiltinTransforms.registerAll(transformRegistry);
      }

      MappingEngine engine = new MappingEngine(this);
      validateAllConfigs(engine);

      return engine;
    }

    private void validateAllConfigs(final MappingEngine engine) {
      List<String> allErrors = new ArrayList<>();

      for (String configId : configCache.keySet()) {
        ValidationResult result = engine.schemaValidator.validate(
            engine.configCache.get(configId), configId);
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
