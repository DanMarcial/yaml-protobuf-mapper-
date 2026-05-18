package io.github.yamlmapper.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.exception.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
  public MappingSchema load(String path) {
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
  public MappingSchema load(InputStream inputStream, String configId) {
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
  public String extractConfigId(String configPath) {
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

  private InputStream openStream(String path) throws IOException {
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

  private MappingSchema convertToSchema(YamlSchemaDto dto, String configId) {
    if (dto == null) {
      throw new ConfigurationException(configId, "Empty YAML configuration");
    }

    Map<String, FieldConfig> fields = new LinkedHashMap<>();
    extractFields(dto.fields, fields, configId);

    return new MappingSchema(dto.rootType, Map.copyOf(fields));
  }

  private FieldConfig convertToFieldConfig(String name, YamlFieldDto dto, String configId) {
    if (dto == null) {
      throw new ConfigurationException(configId,
          String.format("Field '%s' has null configuration", name));
    }

    // Convert nested fields if present
    Map<String, FieldConfig> nestedFields = new LinkedHashMap<>();
    extractFields(dto.fields, nestedFields, configId);

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
        dto.defaultValue
    );
  }

  private void extractFields(Map<String, YamlFieldDto> dto, Map<String, FieldConfig> fields, String configId){
    if (dto != null) {
      for (Map.Entry<String, YamlFieldDto> entry : dto.entrySet()) {
        String fieldName = entry.getKey();
        FieldConfig fieldConfig = convertToFieldConfig(fieldName, entry.getValue(), configId);
        fields.put(fieldName, fieldConfig);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private List<String> normalizeSource(Object source) {
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
   */
  static class YamlSchemaDto {
    public String rootType;
    public Map<String, YamlFieldDto> fields;
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
    public Map<String, YamlFieldDto> fields;
    public boolean required;

    @JsonProperty("default")
    public Object defaultValue;
  }
}
