package io.github.yamlmapper.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yamlmapper.config.FieldConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default implementation of TransformContext.
 *
 * <p>Provides transforms with access to:
 * <ul>
 *   <li>Current field configuration and parameters</li>
 *   <li>Root JSON document for cross-field access</li>
 *   <li>Shared ObjectMapper for JSON operations</li>
 * </ul>
 *
 * <p>This class is immutable and thread-safe.
 */
public class TransformContextImpl implements TransformContext {

  private final String fieldName;
  private final FieldConfig fieldConfig;
  private final JsonNode rootNode;
  private final ObjectMapper objectMapper;
  private final Map<String, Object> params;

  /**
   * Creates a new TransformContextImpl.
   *
   * @param fieldName the current field name
   * @param fieldConfig the field configuration
   * @param rootNode the root JSON node
   * @param objectMapper the shared ObjectMapper
   */
  public TransformContextImpl(
      String fieldName,
      FieldConfig fieldConfig,
      JsonNode rootNode,
      ObjectMapper objectMapper) {
    this.fieldName = fieldName;
    this.fieldConfig = fieldConfig;
    this.rootNode = rootNode;
    this.objectMapper = objectMapper;
    this.params = fieldConfig != null && fieldConfig.transformParams() != null
        ? Map.copyOf(fieldConfig.transformParams())
        : Map.of();
  }

  @Override
  public String getFieldName() {
    return fieldName;
  }

  @Override
  public FieldConfig getFieldConfig() {
    return fieldConfig;
  }

  @Override
  public JsonNode getRootNode() {
    return rootNode;
  }

  @Override
  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  @Override
  public String getParam(String name) {
    Object value = params.get(name);
    return value != null ? value.toString() : null;
  }

  @Override
  public String getParam(String name, String defaultValue) {
    String value = getParam(name);
    return value != null ? value : defaultValue;
  }

  @Override
  public int getParamAsInt(String name, int defaultValue) {
    Object value = params.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    try {
      return Integer.parseInt(value.toString().trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  public boolean getParamAsBoolean(String name, boolean defaultValue) {
    Object value = params.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    String str = value.toString().trim().toLowerCase();
    return "true".equals(str) || "yes".equals(str) || "1".equals(str);
  }

  @Override
  public double getParamAsDouble(String name, double defaultValue) {
    Object value = params.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    try {
      return Double.parseDouble(value.toString().trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, String> getParamAsMap(String name) {
    Object value = params.get(name);
    if (value == null) {
      return Map.of();
    }
    if (value instanceof Map) {
      Map<?, ?> rawMap = (Map<?, ?>) value;
      Map<String, String> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
        String key = entry.getKey() != null ? entry.getKey().toString() : null;
        String val = entry.getValue() != null ? entry.getValue().toString() : null;
        if (key != null) {
          result.put(key, val);
        }
      }
      return Collections.unmodifiableMap(result);
    }
    return Map.of();
  }

  /**
   * Builder for TransformContextImpl.
   */
  public static class Builder {
    private String fieldName;
    private FieldConfig fieldConfig;
    private JsonNode rootNode;
    private ObjectMapper objectMapper;

    public Builder fieldName(String fieldName) {
      this.fieldName = fieldName;
      return this;
    }

    public Builder fieldConfig(FieldConfig fieldConfig) {
      this.fieldConfig = fieldConfig;
      return this;
    }

    public Builder rootNode(JsonNode rootNode) {
      this.rootNode = rootNode;
      return this;
    }

    public Builder objectMapper(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
      return this;
    }

    public TransformContextImpl build() {
      return new TransformContextImpl(fieldName, fieldConfig, rootNode, objectMapper);
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
