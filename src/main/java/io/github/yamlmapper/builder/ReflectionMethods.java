package io.github.yamlmapper.builder;

/**
 * Constants for Protobuf reflection method names.
 *
 * <p>These constants define the method names used when dynamically
 * invoking Protobuf builder and enum methods via reflection.
 */
public final class ReflectionMethods {

    // ============================================
    // Builder methods
    // ============================================

    /** Static method to create a new builder instance. */
    public static final String METHOD_NEW_BUILDER = "newBuilder";

    // ============================================
    // Setter method prefixes
    // ============================================

    /** Prefix for singular field setters: setFieldName(value). */
    public static final String PREFIX_SET = "set";

    /** Prefix for repeated field single adders: addFieldName(value). */
    public static final String PREFIX_ADD = "add";

    /** Prefix for repeated field bulk adders: addAllFieldName(collection). */
    public static final String PREFIX_ADD_ALL = "addAll";

    /** Prefix for map field bulk setters: putAllFieldName(map). */
    public static final String PREFIX_PUT_ALL = "putAll";

    // ============================================
    // Enum methods
    // ============================================

    /** Method to get enum descriptor. */
    public static final String METHOD_GET_DESCRIPTOR = "getDescriptor";

    /** Method to get enum value by number. */
    public static final String METHOD_FOR_NUMBER = "forNumber";

    private ReflectionMethods() {
        // Utility class
    }
}
