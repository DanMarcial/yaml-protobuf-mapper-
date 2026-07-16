package io.github.yamlmapper.config;

/**
 * Constants for YAML field type identifiers and formats.
 *
 * <p>These constants define the valid values for the {@code type} field
 * in YAML mapping configurations.
 *
 * <p>Example YAML usage:
 * <pre>{@code
 * fields:
 *   visitorId:
 *     type: string      # Uses STRING_TYPE
 *   count:
 *     type: int32       # Uses INT32_TYPE
 *   eventTime:
 *     type: timestamp   # Uses TIMESTAMP_TYPE
 *     format: iso8601   # Uses ISO8601_FORMAT
 * }</pre>
 */
public final class TypeConstants {

    // ============================================
    // Type identifiers
    // ============================================

    /** Type identifier for string fields. */
    public static final String STRING = "string";

    /** Type identifier for array/list fields. */
    public static final String ARRAY = "array";

    /** Type identifier for date/time fields. */
    public static final String TIMESTAMP = "timestamp";

    /** Type identifier for nested object fields. */
    public static final String OBJECT = "object";

    /** Type identifier for 32-bit integer fields. */
    public static final String INT32 = "int32";

    /** Type identifier for 64-bit integer fields. */
    public static final String INT64 = "int64";

    /** Type identifier for floating point fields. */
    public static final String FLOAT = "float";

    /** Type identifier for double precision fields. */
    public static final String DOUBLE = "double";

    /** Type identifier for boolean fields. */
    public static final String BOOLEAN = "boolean";

    /** Type identifier for enum fields. */
    public static final String ENUM = "enum";

    /** Type identifier for map fields. */
    public static final String MAP = "map";

    // ============================================
    // Timestamp formats
    // ============================================

    /** ISO 8601 timestamp format (default): "2024-03-15T14:30:00Z". */
    public static final String FORMAT_ISO8601 = "iso8601";

    /** Unix milliseconds timestamp format: 1710510600000. */
    public static final String FORMAT_UNIX_MILLIS = "unix_millis";

    // ============================================
    // Time conversion constants
    // ============================================

    /** Number of nanoseconds per millisecond (1,000,000). */
    public static final int NANOS_PER_MILLI = 1_000_000;

    /** Number of milliseconds per second. */
    public static final int MILLIS_PER_SECOND = 1000;

    // ============================================
    // Default values
    // ============================================

    /** Default max length for truncate transform. */
    public static final int DEFAULT_TRUNCATE_LENGTH = 5000;

    private TypeConstants() {
        // Utility class
    }
}
