package io.github.yamlmapper.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yamlmapper.extractor.PathResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of TransformContext.
 *
 * <p>Provides transforms with access to:
 * <ul>
 *   <li>Transform parameters from YAML configuration</li>
 *   <li>Root JSON document for cross-field access</li>
 *   <li>Shared ObjectMapper for JSON operations</li>
 * </ul>
 *
 * <p>This class is immutable and thread-safe.
 */
public class TransformContextImpl implements TransformContext {

  private final JsonNode rootNode;
  private final ObjectMapper objectMapper;
  private final Map<String, Object> params;
  private final PathResolver pathResolver;

  /**
   * Creates a new TransformContextImpl.
   *
   * @param rootNode the root JSON node
   * @param objectMapper the shared ObjectMapper
   * @param params the transform parameters
   */
  public TransformContextImpl(
      final JsonNode rootNode,
      final ObjectMapper objectMapper,
      final Map<String, Object> params) {
    this(rootNode, objectMapper, params, null);
  }

  /**
   * Creates a new TransformContextImpl with a PathResolver.
   *
   * @param rootNode the root JSON node
   * @param objectMapper the shared ObjectMapper
   * @param params the transform parameters
   * @param pathResolver the path resolver for efficient path lookups (optional)
   */
  public TransformContextImpl(
      final JsonNode rootNode,
      final ObjectMapper objectMapper,
      final Map<String, Object> params,
      final PathResolver pathResolver) {
    this.rootNode = rootNode;
    this.objectMapper = objectMapper;
    this.params = params != null ? Map.copyOf(params) : Map.of();
    this.pathResolver = pathResolver;
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
      Map<String, String> result = new HashMap<>();
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

  @Override
  public List<String> getParamAsList(String name) {
    Object value = params.get(name);
    if (value == null) {
      return List.of();
    }
    if (value instanceof Collection) {
      Collection<?> rawList = (Collection<?>) value;
      List<String> result = new ArrayList<>(rawList.size());
      for (Object item : rawList) {
        if (item != null) {
          result.add(item.toString());
        }
      }
      return Collections.unmodifiableList(result);
    }
    // Single value: wrap in list
    return List.of(value.toString());
  }

  @Override
  public JsonNode resolvePath(final String path) {
    if (path == null || path.isBlank() || rootNode == null) {
      return null;
    }

    // Use the provided PathResolver if available (for caching benefits)
    if (pathResolver != null) {
      return pathResolver.resolve(rootNode, path);
    }

    // Fallback: simple path resolution without caching
    return resolvePathFallback(path);
  }

  /**
   * Simple fallback path resolution when no PathResolver is available.
   */
  private JsonNode resolvePathFallback(final String path) {
    JsonNode current = rootNode;
    for (String segment : path.split("\\.")) {
      if (current == null || !current.isObject()) {
        return null;
      }
      current = current.get(segment);
    }
    return current;
  }

  /**
   * Builder for TransformContextImpl.
   */
  public static class Builder {
    private JsonNode rootNode;
    private ObjectMapper objectMapper;
    private Map<String, Object> params;
    private PathResolver pathResolver;

    public Builder rootNode(final JsonNode rootNode) {
      this.rootNode = rootNode;
      return this;
    }

    public Builder objectMapper(final ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
      return this;
    }

    public Builder params(final Map<String, Object> params) {
      this.params = params;
      return this;
    }

    public Builder pathResolver(final PathResolver pathResolver) {
      this.pathResolver = pathResolver;
      return this;
    }

    public TransformContextImpl build() {
      return new TransformContextImpl(rootNode, objectMapper, params, pathResolver);
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
