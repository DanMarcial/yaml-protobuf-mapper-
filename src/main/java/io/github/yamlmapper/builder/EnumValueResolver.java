package io.github.yamlmapper.builder;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.ProtocolMessageEnum;
import io.github.yamlmapper.cache.CacheFactory;
import io.github.yamlmapper.config.CacheConfig;
import io.github.yamlmapper.exception.MappingException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves and caches Protobuf enum values.
 *
 * <p>This resolver pre-computes normalized name mappings for each enum type,
 * eliminating repeated string transformations during mapping.
 *
 * <p>For each enum type, it caches:
 * <ul>
 *   <li>Original name (e.g., "SEARCH_PAGE")</li>
 *   <li>Lowercase name (e.g., "search_page")</li>
 *   <li>camelCase conversion (e.g., "searchPage")</li>
 * </ul>
 *
 * <p>This class is thread-safe.
 */
public class EnumValueResolver {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    // Cache for enum name -> EnumValueDescriptor mappings per enum class
    private final ConcurrentHashMap<Class<?>, Map<String, EnumValueDescriptor>> enumNameMappings;

    // Cache for EnumDescriptor per enum class
    private final Cache<Class<?>, EnumDescriptor> descriptorCache;

    // Cache for forNumber MethodHandle per enum class
    private final Cache<Class<?>, MethodHandle> forNumberHandles;

    /**
     * Creates a new EnumValueResolver.
     */
    public EnumValueResolver() {
        this.enumNameMappings = new ConcurrentHashMap<>();
        this.descriptorCache = CacheFactory.create(CacheConfig.TYPE_CACHE);
        this.forNumberHandles = CacheFactory.create(CacheConfig.BUILDER_CACHE);
    }

    /**
     * Resolves an enum value from a string or integer input.
     *
     * @param enumClass the Protobuf enum class
     * @param value the value to resolve (String or Number)
     * @return the resolved enum instance
     * @throws MappingException if the value cannot be resolved
     */
    public ProtocolMessageEnum resolve(final Class<?> enumClass, final Object value) {
        if (value == null) {
            return null;
        }

        EnumDescriptor descriptor = getDescriptor(enumClass);
        EnumValueDescriptor valueDescriptor;

        if (value instanceof Number number) {
            valueDescriptor = descriptor.findValueByNumber(number.intValue());
        } else {
            String textValue = value.toString().trim();
            valueDescriptor = resolveByName(enumClass, textValue);
        }

        if (valueDescriptor == null) {
            throw new MappingException(String.format(
                "Enum value '%s' not found in %s. Valid values: %s",
                value, enumClass.getSimpleName(), descriptor.getValues()));
        }

        return createInstance(enumClass, valueDescriptor.getNumber());
    }

    /**
     * Resolves an enum value by name using cached name mappings.
     */
    private EnumValueDescriptor resolveByName(final Class<?> enumClass, final String name) {
        Map<String, EnumValueDescriptor> mappings = enumNameMappings.computeIfAbsent(
            enumClass, this::buildNameMappings);

        // Try exact match first
        EnumValueDescriptor result = mappings.get(name);
        if (result != null) {
            return result;
        }

        // Try lowercase match
        return mappings.get(name.toLowerCase());
    }

    /**
     * Builds all name mappings for an enum type.
     * This is called once per enum type and cached.
     */
    private Map<String, EnumValueDescriptor> buildNameMappings(final Class<?> enumClass) {
        EnumDescriptor descriptor = getDescriptor(enumClass);
        Map<String, EnumValueDescriptor> mappings = new HashMap<>();

        for (EnumValueDescriptor value : descriptor.getValues()) {
            String originalName = value.getName();

            // Original name (e.g., "SEARCH_PAGE")
            mappings.put(originalName, value);

            // Lowercase (e.g., "search_page")
            mappings.put(originalName.toLowerCase(), value);

            // camelCase to UPPER_SNAKE_CASE conversion
            // e.g., "searchPage" -> "SEARCH_PAGE"
            String fromCamelCase = camelToUpperSnake(originalName);
            if (!fromCamelCase.equals(originalName)) {
                mappings.put(fromCamelCase, value);
                mappings.put(fromCamelCase.toLowerCase(), value);
            }

            // Also handle input that's already camelCase
            String camelVersion = snakeToCamel(originalName);
            mappings.put(camelVersion, value);
            mappings.put(camelVersion.toLowerCase(), value);
        }

        return mappings;
    }

    /**
     * Converts camelCase to UPPER_SNAKE_CASE.
     * e.g., "searchPage" -> "SEARCH_PAGE"
     */
    private String camelToUpperSnake(final String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                result.append('_');
            }
            result.append(Character.toUpperCase(c));
        }
        return result.toString();
    }

    /**
     * Converts UPPER_SNAKE_CASE to camelCase.
     * e.g., "SEARCH_PAGE" -> "searchPage"
     */
    private String snakeToCamel(final String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    /**
     * Gets the EnumDescriptor for an enum class.
     */
    private EnumDescriptor getDescriptor(final Class<?> enumClass) {
        return descriptorCache.get(enumClass, clazz -> {
            try {
                var method = clazz.getMethod("getDescriptor");
                return (EnumDescriptor) method.invoke(null);
            } catch (Exception e) {
                throw new MappingException(
                    "Failed to get descriptor for enum: " + clazz.getName(), e);
            }
        });
    }

    /**
     * Creates an enum instance using cached MethodHandle.
     */
    private ProtocolMessageEnum createInstance(final Class<?> enumClass, final int number) {
        try {
            MethodHandle handle = forNumberHandles.get(enumClass, clazz -> {
                try {
                    return LOOKUP.findStatic(clazz, "forNumber",
                        MethodType.methodType(clazz, int.class));
                } catch (Exception e) {
                    throw new MappingException(
                        "Failed to get forNumber method for enum: " + clazz.getName(), e);
                }
            });

            return (ProtocolMessageEnum) handle.invoke(number);
        } catch (Throwable t) {
            throw new MappingException(
                "Failed to create enum instance for number: " + number, t);
        }
    }
}
