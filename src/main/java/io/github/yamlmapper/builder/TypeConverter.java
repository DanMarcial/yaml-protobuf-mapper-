package io.github.yamlmapper.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.Timestamp;
import io.github.yamlmapper.config.MappingConfig;
import io.github.yamlmapper.exception.MappingException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.yamlmapper.config.TypeConstants.FORMAT_ISO8601;
import static io.github.yamlmapper.config.TypeConstants.FORMAT_UNIX_MILLIS;
import static io.github.yamlmapper.config.TypeConstants.MILLIS_PER_SECOND;
import static io.github.yamlmapper.config.TypeConstants.NANOS_PER_MILLI;

/**
 * Converts JsonNode values into Java and Protobuf types.
 *
 * <p>Uses direct class comparison instead of Map lookup for better JIT optimization.
 *
 * <p>This class is thread-safe.
 */
public class TypeConverter {

  private final boolean treatBlankAsNull;

  /**
   * Creates a TypeConverter with default configuration.
   */
  public TypeConverter() {
    this(MappingConfig.DEFAULT);
  }

  /**
   * Creates a TypeConverter with the specified configuration.
   *
   * @param config the mapping configuration
   */
  public TypeConverter(final MappingConfig config) {
    this.treatBlankAsNull = config != null ? config.treatBlankAsNull() : true;
  }

  /**
   * Converts a JsonNode into the target type.
   *
   * <p>Uses direct class comparison for optimal JIT optimization.
   * Supported types: String, Integer, Long, Float, Double, Boolean, Timestamp.
   *
   * @param node the source node
   * @param targetType the target type
   * @param <T> generic target type
   * @return converted value or null
   * @throws MappingException if conversion fails or type is not supported
   */
  @SuppressWarnings("unchecked")
  public <T> T convert(final JsonNode node, final Class<T> targetType) {

    if (isNullNode(node)) {
      return null;
    }

    try {
      // Direct class comparison - JIT optimizes this better than Map.get()
      if (targetType == String.class) {
        return (T) convertToString(node);
      }
      if (targetType == Integer.class || targetType == int.class) {
        return (T) convertToInteger(node);
      }
      if (targetType == Long.class || targetType == long.class) {
        return (T) convertToLong(node);
      }
      if (targetType == Float.class || targetType == float.class) {
        return (T) convertToFloat(node);
      }
      if (targetType == Double.class || targetType == double.class) {
        return (T) convertToDouble(node);
      }
      if (targetType == Boolean.class || targetType == boolean.class) {
        return (T) convertToBoolean(node);
      }
      if (targetType == Timestamp.class) {
        return (T) convertTimestamp(node, FORMAT_ISO8601);
      }

      throw new MappingException("Unsupported target type: " + targetType.getName());

    } catch (MappingException e) {
      throw e;

    } catch (Exception e) {
      throw new MappingException(
              String.format(
                      "Failed to convert '%s' to %s: %s",
                      node.asText(),
                      targetType.getSimpleName(),
                      e.getMessage()
              ),
              e
      );
    }
  }

  /**
   * Converts node to String.
   *
   * <p>When treatBlankAsNull is enabled (default), returns null for blank strings
   * (empty or whitespace only). This ensures that fields with {@code required: true}
   * will fail validation when the source value is blank.
   *
   * <p>When treatBlankAsNull is disabled, blank strings are preserved as-is.
   */
  public String convertToString(final JsonNode node) {

    if (isNullNode(node)) {
      return null;
    }

    final String text = node.asText();

    // Treat blank strings as null to properly fail required validation
    if (treatBlankAsNull && text.isBlank()) {
      return null;
    }

    return text;
  }

  /**
   * Converts node to Integer.
   * Validates that the value is within Integer range to prevent silent overflow.
   */
  public Integer convertToInteger(final JsonNode node) {

    if (isNullNode(node)) {
      return null;
    }

    try {

      if (node.isNumber()) {
        long longValue = node.longValue();
        validateIntegerRange(longValue);
        return (int) longValue;
      }

      if (node.isTextual()) {

        String text = normalizedText(node);

        if (text.isEmpty()) {
          return null;
        }

        // Parse as long first to detect overflow
        long longValue = Long.parseLong(text);
        validateIntegerRange(longValue);
        return (int) longValue;
      }

      throw invalidConversion(node, "Integer");

    } catch (NumberFormatException e) {

      throw new MappingException(
              "Invalid integer value: " + node.asText(),
              e
      );
    }
  }

  private void validateIntegerRange(final long value) {
    if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
      throw new MappingException(
              String.format("Integer overflow: value %d is outside valid range [%d, %d]",
                      value, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }
  }

  /**
   * Converts node to Long.
   */
  public Long convertToLong(final JsonNode node) {

    if (isNullNode(node)) {
      return null;
    }

    try {

      if (node.isNumber()) {
        return node.longValue();
      }

      if (node.isTextual()) {

        String text = normalizedText(node);

        if (text.isEmpty()) {
          return null;
        }

        return Long.parseLong(text);
      }

      throw invalidConversion(node, "Long");

    } catch (NumberFormatException e) {

      throw new MappingException(
              "Invalid long value: " + node.asText(),
              e
      );
    }
  }

  /**
   * Converts node to Float.
   */
  public Float convertToFloat(final JsonNode node) {

    if (isNullNode(node)) {
      return null;
    }

    try {

      if (node.isNumber()) {
        return node.floatValue();
      }

      if (node.isTextual()) {

        String text = normalizedText(node);

        if (text.isEmpty()) {
          return null;
        }

        return Float.parseFloat(text);
      }

      throw invalidConversion(node, "Float");

    } catch (NumberFormatException e) {

      throw new MappingException(
              "Invalid float value: " + node.asText(),
              e
      );
    }
  }

  /**
   * Converts node to Double.
   */
  public Double convertToDouble(final JsonNode node) {

    if (isNullNode(node)) {
      return null;
    }

    try {

      if (node.isNumber()) {
        return node.doubleValue();
      }

      if (node.isTextual()) {

        String text = normalizedText(node);

        if (text.isEmpty()) {
          return null;
        }

        return Double.parseDouble(text);
      }

      throw invalidConversion(node, "Double");

    } catch (NumberFormatException e) {

      throw new MappingException(
              "Invalid double value: " + node.asText(),
              e
      );
    }
  }

  /**
   * Converts node to Boolean.
   */
  public Boolean convertToBoolean(final JsonNode node) {

    if (isNullNode(node)) {
      return null;
    }

    if (node.isBoolean()) {
      return node.booleanValue();
    }

    if (node.isTextual()) {
      String text = normalizedText(node);
      // Use equalsIgnoreCase to avoid toLowerCase() allocation
      return "true".equalsIgnoreCase(text)
              || "yes".equalsIgnoreCase(text)
              || "1".equals(text);
    }

    if (node.isNumber()) {
      return node.intValue() != 0;
    }

    throw invalidConversion(node, "Boolean");
  }

  /**
   * Pattern to match timezone offsets without colon (e.g., -0300, +0530).
   * Captures: group(1)=sign, group(2)=hours, group(3)=minutes
   */
  private static final Pattern TIMEZONE_WITHOUT_COLON =
          Pattern.compile("([+-])(\\d{2})(\\d{2})$");

  /**
   * Converts node to protobuf Timestamp.
   *
   * <p>Supports multiple timestamp formats:
   * <ul>
   *   <li>ISO 8601 with Z: "2024-01-15T10:30:00Z"</li>
   *   <li>ISO 8601 with offset: "2024-01-15T10:30:00+05:30"</li>
   *   <li>Non-standard offset (normalized): "2024-01-15T10:30:00-0300" → "-03:00"</li>
   *   <li>Unix milliseconds (when format="unix_millis")</li>
   * </ul>
   */
  public Timestamp convertTimestamp(final JsonNode node, final String format) {

    if (isNullNode(node)) {
      return null;
    }

    try {

      if (FORMAT_UNIX_MILLIS.equalsIgnoreCase(format)) {

        long millis = node.asLong();

        return Timestamp.newBuilder()
                .setSeconds(millis / MILLIS_PER_SECOND)
                .setNanos((int) ((millis % MILLIS_PER_SECOND) * NANOS_PER_MILLI))
                .build();
      }

      String isoString = normalizedText(node);

      if (isoString.isEmpty()) {
        return null;
      }

      // Normalize timezone offset: -0300 → -03:00
      isoString = normalizeTimezoneOffset(isoString);

      Instant instant = parseToInstant(isoString);

      return Timestamp.newBuilder()
              .setSeconds(instant.getEpochSecond())
              .setNanos(instant.getNano())
              .build();

    } catch (DateTimeParseException e) {

      throw new MappingException(
              String.format(
                      "Failed to parse timestamp '%s' with format '%s'",
                      node.asText(),
                      format
              ),
              e
      );
    }
  }

  /**
   * Normalizes timezone offsets without colon to RFC 3339 format.
   * Converts -0300 to -03:00, +0530 to +05:30, etc.
   *
   * @param timestamp the timestamp string
   * @return normalized timestamp with proper offset format
   */
  private String normalizeTimezoneOffset(final String timestamp) {
    Matcher matcher = TIMEZONE_WITHOUT_COLON.matcher(timestamp);
    if (matcher.find()) {
      return matcher.replaceFirst("$1$2:$3");
    }
    return timestamp;
  }

  /**
   * Parses a timestamp string to Instant, handling both Z and offset formats.
   *
   * @param isoString the ISO 8601 timestamp string
   * @return the parsed Instant
   */
  private Instant parseToInstant(final String isoString) {
    // Try parsing as Instant first (handles Z suffix)
    if (isoString.endsWith("Z")) {
      return Instant.parse(isoString);
    }

    // Parse with OffsetDateTime for offset formats (+05:30, -03:00)
    OffsetDateTime odt = OffsetDateTime.parse(isoString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    return odt.toInstant();
  }

  // Cached empty list to avoid allocation
  private static final List<?> EMPTY_LIST = List.of();

  /**
   * Converts JSON array to typed list.
   */
  @SuppressWarnings("unchecked")
  public <T> List<T> convertList(
          final JsonNode arrayNode,
          final Class<T> elementType
  ) {

    if (isNullNode(arrayNode)) {
      return (List<T>) EMPTY_LIST;
    }

    if (!arrayNode.isArray()) {
      throw new MappingException(
              "Expected array node but got: " + arrayNode.getNodeType());
    }

    // Pre-size ArrayList to avoid resizing
    List<T> result = new ArrayList<>(arrayNode.size());

    for (JsonNode element : arrayNode) {
      T converted = convert(element, elementType);
      if (converted != null) {
        result.add(converted);
      }
    }

    return result;
  }

  /**
   * Converts protobuf enum values.
   */
  public <E extends Enum<E>> E convertEnum(
          final JsonNode node,
          final Class<E> enumClass
  ) {

    if (isNullNode(node)) {
      return null;
    }

    String value = normalizedText(node);

    if (value.isEmpty()) {
      return null;
    }

    for (E enumValue : enumClass.getEnumConstants()) {

      if (enumValue.name().equalsIgnoreCase(value)) {
        return enumValue;
      }
    }

    throw new MappingException(
            String.format(
                    "Enum value '%s' not found in %s",
                    value,
                    enumClass.getSimpleName()
            )
    );
  }

  // =========================================================
  // Helpers
  // =========================================================

  private boolean isNullNode(final JsonNode node) {
    return node == null
            || node.isNull()
            || node.isMissingNode();
  }

  private String normalizedText(final JsonNode node) {
    return node.asText().trim();
  }

  private MappingException invalidConversion(
          final JsonNode node,
          final String targetType
  ) {

    return new MappingException(
            String.format(
                    "Cannot convert %s to %s",
                    node.getNodeType(),
                    targetType
            )
    );
  }
}