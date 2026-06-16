package io.github.yamlmapper.config;

import java.util.Map;

/**
 * Constants for validation schema paths and message types.
 *
 * <p>These constants define the default schema paths used for POST-mapping validation
 * when no custom schema is provided.
 *
 * <p>Example usage:
 * <pre>{@code
 * String schemaPath = SchemaConstants.DEFAULT_SCHEMA_PATHS.get("UserEvent");
 * // Returns: "schemas/user-event.schema.json"
 * }</pre>
 */
public final class SchemaConstants {

    // ============================================
    // Message type identifiers
    // ============================================

    /** Message type for UserEvent. */
    public static final String USER_EVENT = "UserEvent";

    /** Message type for Product. */
    public static final String PRODUCT = "Product";

    // ============================================
    // Default schema paths (classpath)
    // ============================================

    /** Schema path for UserEvent validation. */
    public static final String USER_EVENT_SCHEMA_PATH = "schemas/user-event.schema.json";

    /** Schema path for Product validation. */
    public static final String PRODUCT_SCHEMA_PATH = "schemas/product.schema.json";

    /** Schema path for common definitions. */
    public static final String COMMON_SCHEMA_PATH = "schemas/common.schema.json";

    // ============================================
    // Default schema path mapping
    // ============================================

    /**
     * Immutable map of message types to their default schema paths.
     *
     * <p>Used by MappingEngine to register default validators when
     * POST-mapping validation is enabled.
     */
    public static final Map<String, String> DEFAULT_SCHEMA_PATHS = Map.of(
            USER_EVENT, USER_EVENT_SCHEMA_PATH,
            PRODUCT, PRODUCT_SCHEMA_PATH
    );

    private SchemaConstants() {
        // Utility class
    }
}
