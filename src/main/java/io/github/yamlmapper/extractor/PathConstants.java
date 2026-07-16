package io.github.yamlmapper.extractor;

/**
 * Constants for special path values in field source configuration.
 *
 * <p>These constants define special path values that users can use
 * in the {@code source} field of YAML configurations to reference
 * specific JSON contexts.
 *
 * <p>Example YAML usage:
 * <pre>{@code
 * fields:
 *   product:
 *     type: object
 *     source: ["."]  # Current context - passes the entire current node
 *   items:
 *     type: array
 *     source: ["$"]  # Root context - references the root JSON node
 * }</pre>
 */
public final class PathConstants {

    /**
     * Special path that refers to the current JSON context.
     *
     * <p>When used as a source, the entire current JSON node is passed
     * to the field mapping, useful for nested object mappings where
     * the current node IS the object to map.
     */
    public static final String PATH_CURRENT_CONTEXT = ".";

    /**
     * Special path that refers to the root JSON context.
     *
     * <p>When used as a source, the root JSON node is used regardless
     * of the current nesting level. Useful for accessing top-level
     * fields from within nested mappings.
     */
    public static final String PATH_ROOT = "$";

    private PathConstants() {
        // Utility class
    }
}
