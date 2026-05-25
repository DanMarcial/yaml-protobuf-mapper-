package io.github.yamlmapper.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds validation constraints extracted from JSON Schema files.
 *
 * <p>This class reads constraints from JSON Schema files that define
 * the Protobuf message structure. Constraints include:
 * <ul>
 *   <li>maxLength for string fields</li>
 *   <li>minimum/maximum for numeric fields</li>
 *   <li>required fields (always required)</li>
 *   <li>conditional required fields (by eventType)</li>
 *   <li>enum values</li>
 * </ul>
 *
 * <p>Schemas can be loaded from multiple sources:
 * <ul>
 *   <li>{@link #fromClasspath(String)} - from classpath resources</li>
 *   <li>{@link #fromPath(Path)} - from filesystem</li>
 *   <li>{@link #fromInputStream(InputStream)} - from any input stream</li>
 *   <li>{@link #fromJsonNode(JsonNode)} - from parsed JSON</li>
 * </ul>
 *
 * <p>Example - loading a custom schema with stricter constraints:
 * <pre>{@code
 * // Load custom schema from filesystem
 * ProtobufConstraints strictConstraints = ProtobufConstraints.fromPath(
 *     Paths.get("my-schemas/strict-user-event.json"));
 *
 * // Use with MappingEngine
 * MappingEngine engine = MappingEngine.builder()
 *     .withValidationSchema("UserEvent", strictConstraints)
 *     .build();
 * }</pre>
 *
 * <p>This class is thread-safe after construction.
 */
public class ProtobufConstraints {

  private static final Logger log = LoggerFactory.getLogger(ProtobufConstraints.class);

  private final Map<String, Integer> maxLengths;
  private final Map<String, Range> ranges;
  private final List<String> alwaysRequired;
  private final Map<String, List<ConditionalRequired>> requiredByEventType;
  private final Map<String, List<String>> enumValues;
  private final String schemaTitle;

  /**
   * Range constraint for numeric fields.
   */
  public record Range(Integer minimum, Integer maximum) {
    public static Range min(int min) {
      return new Range(min, null);
    }

    public static Range max(int max) {
      return new Range(null, max);
    }

    public static Range between(int min, int max) {
      return new Range(min, max);
    }

    public boolean isValid(Number value) {
      if (value == null) return true;
      double v = value.doubleValue();
      if (minimum != null && v < minimum) return false;
      if (maximum != null && v > maximum) return false;
      return true;
    }
  }

  /**
   * Conditional required field based on eventType.
   */
  public record ConditionalRequired(String eventType, List<String> requiredFields, boolean isOrCondition) {}

  private ProtobufConstraints(Builder builder) {
    this.maxLengths = Map.copyOf(builder.maxLengths);
    this.ranges = Map.copyOf(builder.ranges);
    this.alwaysRequired = List.copyOf(builder.alwaysRequired);
    this.requiredByEventType = Map.copyOf(builder.requiredByEventType);
    this.enumValues = Map.copyOf(builder.enumValues);
    this.schemaTitle = builder.schemaTitle;
  }

  /**
   * Loads constraints from a JSON Schema file in the classpath.
   *
   * @param schemaPath classpath path to the schema (e.g., "schemas/user-event.schema.json")
   * @return ProtobufConstraints parsed from the schema
   * @throws IOException if schema cannot be read or parsed
   */
  public static ProtobufConstraints fromClasspath(String schemaPath) throws IOException {
    try (InputStream is = ProtobufConstraints.class.getClassLoader().getResourceAsStream(schemaPath)) {
      if (is == null) {
        throw new IOException("Schema not found in classpath: " + schemaPath);
      }
      return fromInputStream(is);
    }
  }

  /**
   * Loads constraints from a JSON Schema InputStream.
   *
   * @param inputStream the schema input stream
   * @return ProtobufConstraints parsed from the schema
   * @throws IOException if schema cannot be parsed
   */
  public static ProtobufConstraints fromInputStream(InputStream inputStream) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode schema = mapper.readTree(inputStream);
    return fromJsonNode(schema);
  }

  /**
   * Loads constraints from a JSON Schema file on the filesystem.
   *
   * <p>Use this method to load custom schemas that are not in the classpath:
   * <pre>{@code
   * ProtobufConstraints constraints = ProtobufConstraints.fromPath(
   *     Paths.get("/my-project/schemas/strict-user-event.json"));
   * }</pre>
   *
   * @param schemaPath path to the schema file
   * @return ProtobufConstraints parsed from the schema
   * @throws IOException if schema cannot be read or parsed
   */
  public static ProtobufConstraints fromPath(Path schemaPath) throws IOException {
    try (InputStream is = Files.newInputStream(schemaPath)) {
      return fromInputStream(is);
    }
  }

  /**
   * Loads constraints from a parsed JSON Schema node.
   *
   * @param schema the root schema node
   * @return ProtobufConstraints parsed from the schema
   */
  public static ProtobufConstraints fromJsonNode(JsonNode schema) {
    Builder builder = new Builder();
    builder.schemaTitle = schema.path("title").asText("Unknown");

    log.debug("Parsing constraints from schema: {}", builder.schemaTitle);

    // Parse root required fields
    JsonNode required = schema.path("required");
    if (required.isArray()) {
      for (JsonNode field : required) {
        builder.alwaysRequired.add(field.asText());
      }
    }

    // Parse properties for maxLength, minimum, maximum, enum
    JsonNode properties = schema.path("properties");
    parseProperties(properties, "", builder);

    // Parse $defs for nested types
    JsonNode defs = schema.path("$defs");
    if (defs.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = defs.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String typeName = entry.getKey();
        JsonNode typeDef = entry.getValue();

        // Parse nested type properties
        JsonNode typeProps = typeDef.path("properties");
        parseProperties(typeProps, typeName + ".", builder);

        // Parse nested type required
        JsonNode typeRequired = typeDef.path("required");
        if (typeRequired.isArray()) {
          for (JsonNode field : typeRequired) {
            builder.alwaysRequired.add(typeName + "." + field.asText());
          }
        }
      }
    }

    // Parse allOf for conditional required (eventType-based)
    JsonNode allOf = schema.path("allOf");
    if (allOf.isArray()) {
      for (JsonNode condition : allOf) {
        parseConditionalRequired(condition, builder);
      }
    }

    log.info("Loaded constraints from '{}': {} maxLengths, {} ranges, {} always required, {} conditional rules",
        builder.schemaTitle, builder.maxLengths.size(), builder.ranges.size(),
        builder.alwaysRequired.size(), builder.requiredByEventType.size());

    return builder.build();
  }

  private static void parseProperties(JsonNode properties, String prefix, Builder builder) {
    if (!properties.isObject()) return;

    Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      String fieldName = prefix + entry.getKey();
      JsonNode fieldDef = entry.getValue();

      // maxLength
      if (fieldDef.has("maxLength")) {
        builder.maxLengths.put(fieldName, fieldDef.get("maxLength").asInt());
      }

      // minimum/maximum
      Integer min = fieldDef.has("minimum") ? fieldDef.get("minimum").asInt() : null;
      Integer max = fieldDef.has("maximum") ? fieldDef.get("maximum").asInt() : null;
      if (min != null || max != null) {
        builder.ranges.put(fieldName, new Range(min, max));
      }

      // enum values
      if (fieldDef.has("enum")) {
        List<String> values = new ArrayList<>();
        for (JsonNode v : fieldDef.get("enum")) {
          values.add(v.asText());
        }
        builder.enumValues.put(fieldName, values);
      }
    }
  }

  private static void parseConditionalRequired(JsonNode condition, Builder builder) {
    JsonNode ifNode = condition.path("if");
    JsonNode thenNode = condition.path("then");

    if (ifNode.isMissingNode() || thenNode.isMissingNode()) return;

    // Extract eventType from if condition
    JsonNode eventTypeNode = ifNode.path("properties").path("eventType").path("const");
    if (eventTypeNode.isMissingNode()) return;

    String eventType = eventTypeNode.asText();

    // Check for anyOf (OR condition) vs required (AND condition)
    JsonNode anyOf = thenNode.path("anyOf");
    if (anyOf.isArray()) {
      // OR condition: any one of these field sets
      List<String> orFields = new ArrayList<>();
      for (JsonNode option : anyOf) {
        JsonNode req = option.path("required");
        if (req.isArray() && req.size() > 0) {
          orFields.add(req.get(0).asText());
        }
      }
      if (!orFields.isEmpty()) {
        builder.requiredByEventType
            .computeIfAbsent(eventType, k -> new ArrayList<>())
            .add(new ConditionalRequired(eventType, orFields, true));
      }
    } else {
      // AND condition: all of these fields required
      JsonNode required = thenNode.path("required");
      if (required.isArray()) {
        List<String> andFields = new ArrayList<>();
        for (JsonNode field : required) {
          andFields.add(field.asText());
        }
        if (!andFields.isEmpty()) {
          builder.requiredByEventType
              .computeIfAbsent(eventType, k -> new ArrayList<>())
              .add(new ConditionalRequired(eventType, andFields, false));
        }
      }
    }
  }

  // ==================== Query Methods ====================

  /**
   * Gets the maxLength constraint for a field.
   *
   * @param fieldPath the field path (e.g., "visitorId" or "Product.id")
   * @return Optional containing the max length, or empty if no constraint
   */
  public Optional<Integer> getMaxLength(String fieldPath) {
    return Optional.ofNullable(maxLengths.get(fieldPath));
  }

  /**
   * Gets the range constraint for a field.
   *
   * @param fieldPath the field path
   * @return Optional containing the range, or empty if no constraint
   */
  public Optional<Range> getRange(String fieldPath) {
    return Optional.ofNullable(ranges.get(fieldPath));
  }

  /**
   * Gets the list of always-required fields.
   */
  public List<String> getAlwaysRequired() {
    return alwaysRequired;
  }

  /**
   * Gets the conditional required rules for a specific eventType.
   *
   * @param eventType the event type (e.g., "add-to-cart")
   * @return list of conditional required rules, or empty list
   */
  public List<ConditionalRequired> getRequiredForEventType(String eventType) {
    return requiredByEventType.getOrDefault(eventType, List.of());
  }

  /**
   * Gets valid enum values for a field.
   *
   * @param fieldPath the field path
   * @return Optional containing the valid values, or empty if not an enum
   */
  public Optional<List<String>> getEnumValues(String fieldPath) {
    return Optional.ofNullable(enumValues.get(fieldPath));
  }

  /**
   * Gets the schema title (message type name).
   *
   * <p>The title is extracted from the JSON Schema "title" field
   * and typically matches the Protobuf message name (e.g., "UserEvent", "Product").
   *
   * @return the schema title
   */
  public String getSchemaTitle() {
    return schemaTitle;
  }

  /**
   * Builder for ProtobufConstraints.
   */
  private static class Builder {
    private final Map<String, Integer> maxLengths = new HashMap<>();
    private final Map<String, Range> ranges = new HashMap<>();
    private final List<String> alwaysRequired = new ArrayList<>();
    private final Map<String, List<ConditionalRequired>> requiredByEventType = new HashMap<>();
    private final Map<String, List<String>> enumValues = new HashMap<>();
    private String schemaTitle = "Unknown";

    ProtobufConstraints build() {
      return new ProtobufConstraints(this);
    }
  }
}
