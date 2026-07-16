package io.github.yamlmapper.config;

import java.util.Map;

/**
 * Root configuration for a YAML mapping file.
 *
 * <p>Represents the top-level structure of a mapping configuration:
 * <pre>{@code
 * # The target Protobuf message type
 * rootType: UserEvent
 *
 * # Field mappings
 * fields:
 *   visitorId:
 *     type: string
 *     source: [visitor_id, visitorId]
 *   searchQuery:
 *     type: string
 *     source: [searchQuery, query]
 * }</pre>
 *
 * @param rootType the target Protobuf message type name (e.g., "UserEvent", "Product")
 * @param fields map of field name to field configuration
 */
public record MappingSchema(
    String rootType,
    Map<String, FieldConfig> fields) {

  /**
   * Creates a MappingSchema.
   *
   * @param rootType the target Protobuf message type
   * @param fields the field configurations
   * @return a new MappingSchema
   */
  public static MappingSchema of(String rootType, Map<String, FieldConfig> fields) {
    return new MappingSchema(rootType, fields);
  }

  /**
   * Creates a builder for fluent construction.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for MappingSchema.
   */
  public static class Builder {
    private String rootType;
    private final Map<String, FieldConfig> fields = new java.util.HashMap<>();

    private Builder() {}

    public Builder rootType(String rootType) {
      this.rootType = rootType;
      return this;
    }

    public Builder field(String name, FieldConfig config) {
      this.fields.put(name, config);
      return this;
    }

    public Builder fields(Map<String, FieldConfig> fields) {
      this.fields.putAll(fields);
      return this;
    }

    public MappingSchema build() {
      return new MappingSchema(rootType, Map.copyOf(fields));
    }
  }
}
