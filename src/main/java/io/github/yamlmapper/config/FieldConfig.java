package io.github.yamlmapper.config;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a single field mapping from JSON to Protobuf.
 *
 * <p>Represents the YAML configuration for a field:
 * <pre>{@code
 * fieldName:
 *   type: string
 *   source: [field1, field2]
 *   required: true
 *   default: "unknown"
 *   transform: singleItemToArray
 *   transformParams:
 *     key: value
 *   parseEmbeddedJson: true
 * }</pre>
 *
 * <p>For merge support (multiple definitions for the same field), use array syntax:
 * <pre>{@code
 * attributes:
 *   - type: map
 *     source: [facets]
 *     transform: parseKeyValuePairs
 *   - type: map
 *     transform: fieldsToAttributeMap
 *     transformParams:
 *       fields: [metadata:X]
 * }</pre>
 *
 * @param name the field name in the target Protobuf message
 * @param type the data type (string, int32, float, object, array, timestamp, enum, map)
 * @param source list of JSON paths to try in order (fallback support)
 * @param format for timestamps, the format (iso8601, unix_millis)
 * @param objectType for type=object, the Protobuf message type name
 * @param itemType for type=array, the type of array items
 * @param enumType for type=enum, the Protobuf enum type name
 * @param keyType for type=map, the type of map keys (typically string)
 * @param valueType for type=map, the type of map values
 * @param transform name of the transform to apply
 * @param transformParams parameters for the transform
 * @param fields for nested objects/arrays/maps, the child field configurations
 * @param required whether the field is required
 * @param defaultValue default value if field is not found
 * @param parseEmbeddedJson whether to parse JSON strings as embedded JSON (opt-in, default false)
 * @param mergeDefinitions for merge support, list of field configs to process and merge (results are combined)
 */
public record FieldConfig(
    String name,
    String type,
    List<String> source,
    String format,
    String objectType,
    String itemType,
    String enumType,
    String keyType,
    String valueType,
    String transform,
    Map<String, Object> transformParams,
    Map<String, FieldConfig> fields,
    boolean required,
    Object defaultValue,
    boolean parseEmbeddedJson,
    List<FieldConfig> mergeDefinitions) {

  /**
   * Creates a minimal FieldConfig for simple fields.
   *
   * @param name the field name
   * @param type the data type
   * @param source the source paths
   * @return a new FieldConfig
   */
  public static FieldConfig of(String name, String type, List<String> source) {
    return new FieldConfig(
        name, type, source, null, null, null, null, null, null, null, Map.of(), Map.of(), false, null, false, List.of());
  }

  /**
   * Checks if this field has merge definitions configured.
   *
   * @return true if merge definitions exist
   */
  public boolean hasMergeDefinitions() {
    return mergeDefinitions != null && !mergeDefinitions.isEmpty();
  }

  /**
   * Checks if this field has a transform configured.
   *
   * @return true if a transform is specified
   */
  public boolean hasTransform() {
    return transform != null && !transform.isBlank();
  }

  /**
   * Creates a builder for fluent construction.
   *
   * @param name the field name
   * @return a new builder
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Builder for FieldConfig.
   */
  public static class Builder {
    private final String name;
    private String type = "string";
    private List<String> source = List.of();
    private String format = null;
    private String objectType = null;
    private String itemType = null;
    private String enumType = null;
    private String keyType = null;
    private String valueType = null;
    private String transform = null;
    private Map<String, Object> transformParams = Map.of();
    private Map<String, FieldConfig> fields = Map.of();
    private boolean required = false;
    private Object defaultValue = null;
    private boolean parseEmbeddedJson = false;
    private List<FieldConfig> mergeDefinitions = List.of();

    private Builder(String name) {
      this.name = name;
    }

    public Builder type(String type) {
      this.type = type;
      return this;
    }

    public Builder source(String... sources) {
      this.source = List.of(sources);
      return this;
    }

    public Builder source(List<String> source) {
      this.source = source;
      return this;
    }

    public Builder format(String format) {
      this.format = format;
      return this;
    }

    public Builder objectType(String objectType) {
      this.objectType = objectType;
      return this;
    }

    public Builder itemType(String itemType) {
      this.itemType = itemType;
      return this;
    }

    public Builder enumType(String enumType) {
      this.enumType = enumType;
      return this;
    }

    public Builder keyType(String keyType) {
      this.keyType = keyType;
      return this;
    }

    public Builder valueType(String valueType) {
      this.valueType = valueType;
      return this;
    }

    public Builder transform(String transform) {
      this.transform = transform;
      return this;
    }

    public Builder transformParams(Map<String, Object> params) {
      this.transformParams = params;
      return this;
    }

    public Builder fields(Map<String, FieldConfig> fields) {
      this.fields = fields;
      return this;
    }

    public Builder required(boolean required) {
      this.required = required;
      return this;
    }

    public Builder defaultValue(Object defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    public Builder parseEmbeddedJson(boolean parseEmbeddedJson) {
      this.parseEmbeddedJson = parseEmbeddedJson;
      return this;
    }

    public Builder mergeDefinitions(List<FieldConfig> mergeDefinitions) {
      this.mergeDefinitions = mergeDefinitions;
      return this;
    }

    public FieldConfig build() {
      return new FieldConfig(
          name, type, source, format, objectType, itemType, enumType,
          keyType, valueType, transform, transformParams, fields, required, defaultValue,
          parseEmbeddedJson, mergeDefinitions);
    }
  }
}
