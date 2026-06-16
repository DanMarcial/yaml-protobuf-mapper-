package io.github.yamlmapper.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.exception.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads YAML configuration files into MappingSchema objects.
 *
 * <p>Supports loading from:
 * <ul>
 *   <li>Classpath resources: {@code "classpath:mapping/search.yaml"}</li>
 *   <li>File system paths: {@code "/etc/config/search.yaml"}</li>
 *   <li>InputStream directly</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * YamlConfigLoader loader = new YamlConfigLoader();
 * MappingSchema schema = loader.load("classpath:mapping/search.yaml");
 * }</pre>
 *
 * <p>This class is thread-safe. The internal ObjectMapper is thread-safe.
 */
public class YamlConfigLoader {

  private static final String CLASSPATH_PREFIX = "classpath:";
  private static final String FILE_PREFIX = "file:";

  private final ObjectMapper yamlMapper;

  /**
   * Creates a new YamlConfigLoader.
   */
  public YamlConfigLoader() {
    this.yamlMapper = new ObjectMapper(new YAMLFactory());
  }

  /**
   * Loads a YAML configuration from a path.
   *
   * @param path the path (classpath: prefix or file path)
   * @return the parsed MappingSchema
   * @throws ConfigurationException if the file cannot be loaded or parsed
   */
  public MappingSchema load(final String path) {
    if (path == null || path.isBlank()) {
      throw new ConfigurationException("Path cannot be null or blank");
    }

    try (InputStream is = openStream(path)) {
      if (is == null) {
        throw new ConfigurationException("Config file not found: " + path);
      }
      return load(is, extractConfigId(path));
    } catch (IOException e) {
      throw new ConfigurationException("Failed to load config: " + path, e);
    }
  }

  /**
   * Loads a YAML configuration from an InputStream.
   *
   * @param inputStream the input stream
   * @param configId the config identifier (for error messages)
   * @return the parsed MappingSchema
   * @throws ConfigurationException if the stream cannot be parsed
   */
  public MappingSchema load(final InputStream inputStream, final String configId) {
    try {
      YamlSchemaDto dto = yamlMapper.readValue(inputStream, YamlSchemaDto.class);
      return convertToSchema(dto, configId);
    } catch (IOException e) {
      throw new ConfigurationException(configId, "Failed to parse YAML: " + e.getMessage(), e);
    }
  }

  /**
   * Extracts the config ID from a path.
   *
   * <p>Examples:
   * <ul>
   *   <li>"classpath:mapping/search.yaml" → "search"</li>
   *   <li>"/etc/config/add-to-cart.yaml" → "add-to-cart"</li>
   * </ul>
   *
   * @param configPath the path
   * @return the config ID (filename without extension)
   */
  public String extractConfigId(final String configPath) {
    String path = configPath;

    // Remove classpath prefix
    if (path.startsWith(CLASSPATH_PREFIX)) {
      path = path.substring(CLASSPATH_PREFIX.length());
    }else if (configPath.startsWith(FILE_PREFIX)) {
      path = configPath.substring(FILE_PREFIX.length());
    }

    // Get filename
    int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

    // Remove extension
    int lastDot = filename.lastIndexOf('.');
    return lastDot > 0 ? filename.substring(0, lastDot) : filename;
  }

  private InputStream openStream(final String path) throws IOException {
    // Handle classpath: prefix
    if (path.startsWith(CLASSPATH_PREFIX)) {
      String resourcePath = path.substring(CLASSPATH_PREFIX.length());
      return getClass().getClassLoader().getResourceAsStream(resourcePath);
    }

    // Handle file: prefix
    if (path.startsWith(FILE_PREFIX)) {
      String filePath = path.substring(FILE_PREFIX.length());
      try {
        return Files.newInputStream(Path.of(filePath));
      } catch (IOException e) {
        throw new ConfigurationException("Cannot read file: " + filePath, e);
      }
    }

    // Try classpath first, then file
    InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
    if (stream != null) {
      return stream;
    }

    // Try as file path
    try {
      Path filePath = Path.of(path);
      if (Files.exists(filePath)) {
        return Files.newInputStream(filePath);
      }
    } catch (Exception e) {
      // Ignore
    }

    return null;

  }

  private MappingSchema convertToSchema(final YamlSchemaDto dto, final String configId) {
    if (dto == null) {
      throw new ConfigurationException(configId, "Empty YAML configuration");
    }

    Map<String, FieldConfig> fields = new LinkedHashMap<>();
    extractFieldsFromRaw(dto.rawFields, fields, configId);

    return new MappingSchema(dto.rootType, Map.copyOf(fields));
  }

  private FieldConfig convertToFieldConfig(final String name, final YamlFieldDto dto, final String configId) {
    if (dto == null) {
      throw new ConfigurationException(configId,
          String.format("Field '%s' has null configuration", name));
    }

    // Convert nested fields if present
    Map<String, FieldConfig> nestedFields = new LinkedHashMap<>();
    extractFieldsFromRaw(dto.rawFields, nestedFields, configId);

    // Normalize source to always be a list
    List<String> sourceList = normalizeSource(dto.source);

    return new FieldConfig(
        name,
        dto.type != null ? dto.type : "string",
        sourceList,
        dto.format,
        dto.objectType,
        dto.itemType,
        dto.enumType,
        dto.keyType,
        dto.valueType,
        dto.transform,
        dto.transformParams != null ? Map.copyOf(dto.transformParams) : Map.of(),
        nestedFields.isEmpty() ? Map.of() : Map.copyOf(nestedFields),
        dto.required,
        dto.defaultValue,
        dto.parseEmbeddedJson,
        List.of()  // No merge definitions for single field config
    );
  }

  /**
   * Creates a FieldConfig with merge definitions from an array of field definitions.
   * The first definition's type is used as the overall type.
   */
  private FieldConfig convertToMergeFieldConfig(final String name, final List<YamlFieldDto> definitions, final String configId) {
    if (definitions == null || definitions.isEmpty()) {
      throw new ConfigurationException(configId,
          String.format("Field '%s' has empty merge definitions", name));
    }

    // Convert each definition to a FieldConfig
    List<FieldConfig> mergeDefinitions = new ArrayList<>();
    for (int i = 0; i < definitions.size(); i++) {
      YamlFieldDto def = definitions.get(i);
      FieldConfig defConfig = convertToFieldConfig(name + "[" + i + "]", def, configId);
      mergeDefinitions.add(defConfig);
    }

    // Use the first definition's type as the overall field type
    YamlFieldDto firstDef = definitions.get(0);

    return new FieldConfig(
        name,
        firstDef.type != null ? firstDef.type : "map",  // Default to map for merge scenarios
        List.of(),  // No single source - sources are in merge definitions
        null,
        firstDef.objectType,
        firstDef.itemType,
        null,
        firstDef.keyType,
        firstDef.valueType,
        null,  // No single transform - transforms are in merge definitions
        Map.of(),
        Map.of(),
        false,
        null,
        false,
        List.copyOf(mergeDefinitions)
    );
  }

  /**
   * Extracts fields from raw JSON nodes, detecting array syntax for merge definitions.
   */
  @SuppressWarnings("unchecked")
  private void extractFieldsFromRaw(final Map<String, Object> rawFields, final Map<String, FieldConfig> fields, final String configId) {
    if (rawFields == null) {
      return;
    }

    for (Map.Entry<String, Object> entry : rawFields.entrySet()) {
      String fieldName = entry.getKey();
      Object rawValue = entry.getValue();

      FieldConfig fieldConfig;

      if (rawValue instanceof List) {
        // Array syntax - merge definitions
        List<Map<String, Object>> definitionsList = (List<Map<String, Object>>) rawValue;
        List<YamlFieldDto> definitions = new ArrayList<>();
        for (Map<String, Object> defMap : definitionsList) {
          YamlFieldDto dto = yamlMapper.convertValue(defMap, YamlFieldDto.class);
          definitions.add(dto);
        }
        fieldConfig = convertToMergeFieldConfig(fieldName, definitions, configId);
      } else if (rawValue instanceof Map) {
        // Object syntax - single definition
        YamlFieldDto dto = yamlMapper.convertValue(rawValue, YamlFieldDto.class);
        fieldConfig = convertToFieldConfig(fieldName, dto, configId);
      } else {
        throw new ConfigurationException(configId,
            String.format("Field '%s' has invalid configuration type: %s", fieldName,
                rawValue != null ? rawValue.getClass().getSimpleName() : "null"));
      }

      fields.put(fieldName, fieldConfig);
    }
  }

  @SuppressWarnings("unchecked")
  private List<String> normalizeSource(final Object source) {
    if (source == null) {
      return List.of();
    }
    if (source instanceof List) {
      return List.copyOf((List<String>) source);
    }
    if (source instanceof String) {
      return List.of((String) source);
    }
    return List.of(source.toString());
  }

  // ==================== Internal DTOs for Jackson deserialization ====================

  /**
   * Internal DTO for YAML schema deserialization.
   * Uses rawFields to detect array syntax for merge definitions.
   */
  static class YamlSchemaDto {
    public String rootType;

    @JsonProperty("fields")
    public Map<String, Object> rawFields;  // Object to detect array vs object syntax
  }

  /**
   * Internal DTO for YAML field deserialization.
   * Maps YAML field names to Java field names.
   */
  static class YamlFieldDto {
    public String type;
    public Object source;  // Can be String or List<String>
    public String format;
    public String objectType;
    public String itemType;
    public String enumType;
    public String keyType;
    public String valueType;
    public String transform;
    public Map<String, Object> transformParams;

    @JsonProperty("fields")
    public Map<String, Object> rawFields;  // Object to detect array vs object syntax

    public boolean required;
    public boolean parseEmbeddedJson;

    @JsonProperty("default")
    public Object defaultValue;
  }
}
