package io.github.yamlmapper.transform;

/**
 * Constants for transform parameter names used in YAML configuration.
 *
 * <p>These constants define the parameter names that users can specify
 * in the {@code transformParams} section of field configurations.
 *
 * <p>Example YAML usage:
 * <pre>{@code
 * fields:
 *   availability:
 *     type: enum
 *     transform: mapValue
 *     transformParams:
 *       mapping:
 *         available: "IN_STOCK"
 *       default: "OUT_OF_STOCK"
 * }</pre>
 */
public final class TransformParams {

    // ============================================
    // Common parameters
    // ============================================

    /** Default value when mapping/transform fails or has no match. */
    public static final String PARAM_DEFAULT = "default";

    /** Mapping dictionary for value transformation. */
    public static final String PARAM_MAPPING = "mapping";

    /** Delimiter for splitting strings into arrays. */
    public static final String PARAM_DELIMITER = "delimiter";

    /** Separator for joining or parsing key-value pairs. */
    public static final String PARAM_SEPARATOR = "separator";

    /** Field name for wrapping values into objects. */
    public static final String PARAM_FIELD_NAME = "fieldName";

    /** Maximum length for truncation. */
    public static final String PARAM_MAX_LENGTH = "maxLength";

    // ============================================
    // ZipArrays transform parameters
    // ============================================

    /** Merge configuration for combining parallel arrays. */
    public static final String PARAM_MERGE = "merge";

    /** Lookup key for matching items across arrays. */
    public static final String PARAM_LOOKUP_KEY = "lookupKey";

    // ============================================
    // StringsToImages transform parameters
    // ============================================

    /** URI field name for image objects. */
    public static final String PARAM_URI_FIELD = "uriField";

    /** Default width for generated image objects. */
    public static final String PARAM_DEFAULT_WIDTH = "defaultWidth";

    /** Default height for generated image objects. */
    public static final String PARAM_DEFAULT_HEIGHT = "defaultHeight";

    // ============================================
    // FieldsToAttributeMap transform parameters
    // ============================================

    /** List of field names to include in the attribute map. */
    public static final String PARAM_FIELDS = "fields";

    // ============================================
    // ReplaceChars transform parameters
    // ============================================

    /** Characters to replace from. */
    public static final String PARAM_FROM = "from";

    /** Characters to replace to. */
    public static final String PARAM_TO = "to";

    private TransformParams() {
        // Utility class
    }
}
