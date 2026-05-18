package io.github.yamlmapper.exception;

/**
 * Centralized error message templates for exceptions.
 *
 * <p>All error messages use {@link String#format} placeholders where applicable.
 * This allows consistent error messaging across the library.
 */
public final class ErrorMessages {

    // ============================================
    // Input validation errors
    // ============================================

    /** Error when JSON input is null. */
    public static final String ERR_JSON_NULL = "JSON input cannot be null";

    /** Error when config ID is null or blank. */
    public static final String ERR_CONFIG_ID_NULL = "Config ID cannot be null or blank";

    /** Error when message class is null. */
    public static final String ERR_MESSAGE_CLASS_NULL = "Message class cannot be null";

    /** Error when builder is null. */
    public static final String ERR_BUILDER_NULL = "Builder cannot be null";

    /** Error when field name is null or blank. */
    public static final String ERR_FIELD_NAME_NULL = "Field name cannot be null or blank";

    /** Error when fields configuration is null or empty. */
    public static final String ERR_FIELDS_CONFIG_NULL = "Fields configuration cannot be null or empty";

    /** Error when transform is null. */
    public static final String ERR_TRANSFORM_NULL = "Transform cannot be null";

    /** Error when message type is null or blank. */
    public static final String ERR_MESSAGE_TYPE_NULL = "Message type cannot be null or blank";

    /** Error when validator is null. */
    public static final String ERR_VALIDATOR_NULL = "Validator cannot be null";

    /** Error when message is null for validation. */
    public static final String ERR_MESSAGE_NULL = "Message cannot be null";

    // ============================================
    // Configuration errors
    // ============================================

    /** Error template when configuration is not found. */
    public static final String ERR_CONFIG_NOT_FOUND = "No configuration found for configId '%s'";

    /** Error template when configuration has no fields. */
    public static final String ERR_CONFIG_NO_FIELDS = "Configuration '%s' has no fields defined";

    /** Error template for config-specific errors. */
    public static final String ERR_CONFIG_FORMAT = "Config '%s': %s";

    // ============================================
    // Field processing errors
    // ============================================

    /** Error template for missing required field. */
    public static final String ERR_REQUIRED_FIELD_MISSING =
        "Required field '%s' is missing and has no default value. Tried sources: %s";

    /** Error template for field processing error. */
    public static final String ERR_FIELD_PROCESSING = "Error processing field '%s': %s";

    /** Error template for field with null configuration. */
    public static final String ERR_FIELD_NULL_CONFIG = "Field '%s' has null configuration";

    // ============================================
    // Type/Builder errors
    // ============================================

    /** Error template when builder creation fails. */
    public static final String ERR_BUILDER_CREATION_FAILED = "Failed to create builder for %s: %s";

    /** Error template when method is not found. */
    public static final String ERR_METHOD_NOT_FOUND = "Method '%s' not found in %s";

    /** Error template when method cannot be accessed. */
    public static final String ERR_CANNOT_ACCESS_METHOD = "Cannot access method '%s' in %s";

    /** Error template when field is not found in message. */
    public static final String ERR_FIELD_NOT_FOUND = "Field '%s' not found in %s";

    /** Error template when setting field fails. */
    public static final String ERR_SET_FIELD_FAILED = "Failed to set field '%s' on %s: %s";

    /** Error template for unsupported type. */
    public static final String ERR_UNSUPPORTED_TYPE = "Unsupported type: %s";

    /** Error template for unsupported map key type. */
    public static final String ERR_UNSUPPORTED_MAP_KEY =
        "Map keyType '%s' is not supported. Only 'string' keys are currently supported.";

    /** Error template for unsupported map value type. */
    public static final String ERR_UNSUPPORTED_MAP_VALUE = "Map valueType '%s' is not supported";

    // ============================================
    // Enum errors
    // ============================================

    /** Error template when type is not a Protobuf enum. */
    public static final String ERR_NOT_PROTOBUF_ENUM = "Type '%s' is not a Protobuf enum";

    /** Error template when enum value is not found. */
    public static final String ERR_ENUM_VALUE_NOT_FOUND =
        "Enum value '%s' not found in %s. Valid values: %s";

    /** Error template when building enum fails. */
    public static final String ERR_ENUM_BUILD_FAILED = "Failed to build enum '%s': %s";

    // ============================================
    // Parsing errors
    // ============================================

    /** Error type identifier for parse failures. */
    public static final String ERR_TYPE_PARSE = "ParseError";

    /** Error template for JSON parsing failure. */
    public static final String ERR_JSON_PARSE_FAILED = "Failed to parse JSON: %s";

    /** Error template for YAML parsing failure. */
    public static final String ERR_YAML_PARSE_FAILED = "Failed to parse YAML: %s";

    /** Error template for embedded JSON parsing failure. */
    public static final String ERR_EMBEDDED_JSON_FAILED = "Failed to parse embedded JSON: %s";

    // ============================================
    // Validation errors
    // ============================================

    /** Error template for integer overflow. */
    public static final String ERR_INTEGER_OVERFLOW =
        "Integer overflow: value %d is outside valid range [%d, %d]";

    private ErrorMessages() {
        // Utility class
    }
}
