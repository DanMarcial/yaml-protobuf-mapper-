package io.github.yamlmapper.config;

import io.github.yamlmapper.exception.ErrorMessages;

/**
 * @deprecated This class is deprecated. Use the specific constant classes instead:
 * <ul>
 *   <li>{@link TypeConstants} for type identifiers and formats</li>
 *   <li>{@link ErrorMessages} for error message templates</li>
 *   <li>{@link io.github.yamlmapper.transform.TransformParams} for transform parameters</li>
 *   <li>{@link io.github.yamlmapper.observability.HealthCheckKeys} for health check keys</li>
 *   <li>{@link io.github.yamlmapper.builder.ReflectionMethods} for reflection method names</li>
 *   <li>{@link io.github.yamlmapper.extractor.PathConstants} for path constants</li>
 *   <li>{@link io.github.yamlmapper.core.MdcKeys} for MDC keys</li>
 * </ul>
 */
@Deprecated(forRemoval = true)
public class Constants {

    // ============================================
    // Type identifiers - Use TypeConstants instead
    // ============================================

    /** @deprecated Use {@link TypeConstants#STRING} */
    @Deprecated(forRemoval = true)
    public static final String STRING_TYPE = TypeConstants.STRING;

    /** @deprecated Use {@link TypeConstants#ARRAY} */
    @Deprecated(forRemoval = true)
    public static final String ARRAY_TYPE = TypeConstants.ARRAY;

    /** @deprecated Use {@link TypeConstants#TIMESTAMP} */
    @Deprecated(forRemoval = true)
    public static final String TIMESTAMP_TYPE = TypeConstants.TIMESTAMP;

    /** @deprecated Use {@link TypeConstants#OBJECT} */
    @Deprecated(forRemoval = true)
    public static final String OBJECT_TYPE = TypeConstants.OBJECT;

    /** @deprecated Use {@link TypeConstants#INT32} */
    @Deprecated(forRemoval = true)
    public static final String INT32_TYPE = TypeConstants.INT32;

    /** @deprecated Use {@link TypeConstants#INT64} */
    @Deprecated(forRemoval = true)
    public static final String INT64_TYPE = TypeConstants.INT64;

    /** @deprecated Use {@link TypeConstants#FLOAT} */
    @Deprecated(forRemoval = true)
    public static final String FLOAT_TYPE = TypeConstants.FLOAT;

    /** @deprecated Use {@link TypeConstants#DOUBLE} */
    @Deprecated(forRemoval = true)
    public static final String DOUBLE_TYPE = TypeConstants.DOUBLE;

    /** @deprecated Use {@link TypeConstants#BOOLEAN} */
    @Deprecated(forRemoval = true)
    public static final String BOOLEAN_TYPE = TypeConstants.BOOLEAN;

    /** @deprecated Use {@link TypeConstants#ENUM} */
    @Deprecated(forRemoval = true)
    public static final String ENUM_TYPE = TypeConstants.ENUM;

    /** @deprecated Use {@link TypeConstants#MAP} */
    @Deprecated(forRemoval = true)
    public static final String MAP_TYPE = TypeConstants.MAP;

    // ============================================
    // Timestamp formats - Use TypeConstants instead
    // ============================================

    /** @deprecated Use {@link TypeConstants#FORMAT_ISO8601} */
    @Deprecated(forRemoval = true)
    public static final String ISO8601_FORMAT = TypeConstants.FORMAT_ISO8601;

    /** @deprecated Use {@link TypeConstants#FORMAT_UNIX_MILLIS} */
    @Deprecated(forRemoval = true)
    public static final String UNIX_MILLIS_FORMAT = TypeConstants.FORMAT_UNIX_MILLIS;

    // ============================================
    // Default values - Use TypeConstants instead
    // ============================================

    /** @deprecated Use {@link TypeConstants#DEFAULT_TRUNCATE_LENGTH} */
    @Deprecated(forRemoval = true)
    public static final int DEFAULT_TRUNCATE_LENGTH = TypeConstants.DEFAULT_TRUNCATE_LENGTH;

    // ============================================
    // Time conversion - Use TypeConstants instead
    // ============================================

    /** @deprecated Use {@link TypeConstants#NANOS_PER_MILLI} */
    @Deprecated(forRemoval = true)
    public static final int NANOS_PER_MILLI = TypeConstants.NANOS_PER_MILLI;

    /** @deprecated Use {@link TypeConstants#MILLIS_PER_SECOND} */
    @Deprecated(forRemoval = true)
    public static final int MILLIS_PER_SECOND = TypeConstants.MILLIS_PER_SECOND;

    // ============================================
    // Error messages - Use ErrorMessages instead
    // ============================================

    /** @deprecated Use {@link ErrorMessages#ERR_JSON_NULL} */
    @Deprecated(forRemoval = true)
    public static final String ERR_JSON_NULL = ErrorMessages.ERR_JSON_NULL;

    /** @deprecated Use {@link ErrorMessages#ERR_CONFIG_ID_NULL} */
    @Deprecated(forRemoval = true)
    public static final String ERR_CONFIG_ID_NULL = ErrorMessages.ERR_CONFIG_ID_NULL;

    /** @deprecated Use {@link ErrorMessages#ERR_CONFIG_NOT_FOUND} */
    @Deprecated(forRemoval = true)
    public static final String ERR_CONFIG_NOT_FOUND = ErrorMessages.ERR_CONFIG_NOT_FOUND;

    /** @deprecated Use {@link ErrorMessages#ERR_REQUIRED_FIELD_MISSING} */
    @Deprecated(forRemoval = true)
    public static final String ERR_REQUIRED_FIELD_MISSING = ErrorMessages.ERR_REQUIRED_FIELD_MISSING;

    /** @deprecated Use {@link ErrorMessages#ERR_FIELD_PROCESSING} */
    @Deprecated(forRemoval = true)
    public static final String ERR_FIELD_PROCESSING = ErrorMessages.ERR_FIELD_PROCESSING;

    /** @deprecated Use {@link ErrorMessages#ERR_CONFIG_NO_FIELDS} */
    @Deprecated(forRemoval = true)
    public static final String ERR_CONFIG_NO_FIELDS = ErrorMessages.ERR_CONFIG_NO_FIELDS;

    private Constants() {
        // Utility class
    }
}
