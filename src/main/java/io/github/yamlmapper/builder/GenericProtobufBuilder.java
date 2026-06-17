package io.github.yamlmapper.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.protobuf.Timestamp;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.exception.FieldExtractionException;
import io.github.yamlmapper.exception.MappingException;
import io.github.yamlmapper.extractor.JsonNodeExtractor;
import io.github.yamlmapper.resolver.TypeResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.yamlmapper.config.TypeConstants.ARRAY;
import static io.github.yamlmapper.config.TypeConstants.BOOLEAN;
import static io.github.yamlmapper.config.TypeConstants.DOUBLE;
import static io.github.yamlmapper.config.TypeConstants.ENUM;
import static io.github.yamlmapper.config.TypeConstants.FLOAT;
import static io.github.yamlmapper.config.TypeConstants.FORMAT_ISO8601;
import static io.github.yamlmapper.config.TypeConstants.INT32;
import static io.github.yamlmapper.config.TypeConstants.INT64;
import static io.github.yamlmapper.config.TypeConstants.MAP;
import static io.github.yamlmapper.config.TypeConstants.OBJECT;
import static io.github.yamlmapper.config.TypeConstants.STRING;
import static io.github.yamlmapper.config.TypeConstants.TIMESTAMP;
import static io.github.yamlmapper.exception.ErrorMessages.ERR_FIELD_PROCESSING;

public class GenericProtobufBuilder {

    private static final Logger log = LoggerFactory.getLogger(GenericProtobufBuilder.class);

    private final JsonNodeExtractor extractor;
    private final TypeConverter typeConverter;
    private final SetterResolver setterResolver;
    private final TypeResolver typeResolver;
    private final BuilderFactory builderFactory;

    public GenericProtobufBuilder(JsonNodeExtractor extractor, TypeConverter typeConverter, SetterResolver setterResolver, TypeResolver typeResolver, BuilderFactory builderFactory) {
        this.extractor = extractor;
        this.typeConverter = typeConverter;
        this.setterResolver = setterResolver;
        this.typeResolver = typeResolver;
        this.builderFactory = builderFactory;
    }

    /**
     * Builds a Protobuf message from JSON using an existing builder.
     * Use this when you need to manipulate the builder before populating (e.g., setting eventType).
     */
    @SuppressWarnings("unchecked")
    public <T extends Message> T build(
            final Message.Builder builder,
            final JsonNode jsonNode,
            final Map<String, FieldConfig> fields) {

        if (fields == null || fields.isEmpty()) {
            throw new MappingException("Fields configuration cannot be null or empty");
        }

        populateFields(builder, jsonNode, fields);
        return (T) builder.build();
    }

    /**
     * Builds a Protobuf message from JSON, creating the builder internally.
     * Use this for simple cases where you don't need to manipulate the builder.
     */
    public <T extends Message> T build(
            final JsonNode jsonNode,
            final String messageType,
            final Map<String, FieldConfig> fields) {

        final Class<? extends Message> messageClass = typeResolver.resolveMessage(messageType);
        final Message.Builder builder = builderFactory.createBuilderFrom(messageClass);
        return build(builder, jsonNode, fields);
    }

    private void populateFields(
            final Message.Builder builder,
            final JsonNode jsonNode,
            final Map<String, FieldConfig> fields) {

        // Track which oneof groups have been set (oneofName -> fieldName that set it)
        Map<String, String> setOneofs = new HashMap<>();

        for (Map.Entry<String, FieldConfig> entry : fields.entrySet()) {
            final String fieldName = entry.getKey();
            final FieldConfig fieldConfig = entry.getValue();

            try {
                Object value = buildField(jsonNode, fieldConfig);

                if (value == null && fieldConfig.defaultValue() != null) {
                    value = convertDefaultValue(fieldConfig.defaultValue(), fieldConfig.type());
                    log.debug("Field '{}' using default value", fieldName);
                }

                if (value == null && fieldConfig.required()) {
                    throw new FieldExtractionException(fieldName, fieldConfig.source());
                }

                if (value != null) {
                    // Check for oneof conflict before setting value (log only)
                    checkOneofConflictLogOnly(builder, fieldName, setOneofs);

                    setterResolver.setValue(builder, fieldName, value);
                }
            } catch (MappingException e) {
                throw e;
            } catch (Exception e) {
                throw new MappingException(String.format(ERR_FIELD_PROCESSING, fieldName, e.getMessage()), e);
            }
        }
    }

    /**
     * Checks for oneof conflicts and logs a warning (without collecting in a list).
     */
    private void checkOneofConflictLogOnly(
            final Message.Builder builder,
            final String fieldName,
            final Map<String, String> setOneofs) {

        SetterResolver.OneofInfo oneofInfo = setterResolver.getOneofInfo(builder, fieldName);
        if (oneofInfo == null) {
            return;
        }

        String previousField = setOneofs.get(oneofInfo.oneofName());
        if (previousField != null && !previousField.equals(oneofInfo.fieldName())) {
            log.warn("OneOf conflict in '{}': setting '{}' overwrites previous value from '{}'",
                    oneofInfo.oneofName(), fieldName, previousField);
        }

        setOneofs.put(oneofInfo.oneofName(), oneofInfo.fieldName());
    }

    private Object convertDefaultValue(final Object defaultValue, final String type) {
        if (defaultValue == null) {
            return null;
        }

        return switch (type) {
            case STRING -> defaultValue.toString();
            case INT32 -> toNumber(defaultValue, Number::intValue, Integer::parseInt);
            case INT64 -> toNumber(defaultValue, Number::longValue, Long::parseLong);
            case FLOAT -> toNumber(defaultValue, Number::floatValue, Float::parseFloat);
            case DOUBLE -> toNumber(defaultValue, Number::doubleValue, Double::parseDouble);
            case BOOLEAN -> toBoolean(defaultValue);
            default -> defaultValue;
        };
    }

    /**
     * Converts an Object to a Number type using the appropriate converter.
     */
    private <T extends Number> T toNumber(
            final Object value,
            final java.util.function.Function<Number, T> fromNumber,
            final java.util.function.Function<String, T> fromString) {
        if (value instanceof Number number) {
            return fromNumber.apply(number);
        }
        return fromString.apply(value.toString());
    }

    private Boolean toBoolean(final Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Populates fields without throwing if fields is null/empty.
     * Used internally for recursive calls where empty fields is valid.
     */
    private void populateFieldsSafe(
            final Message.Builder builder,
            final JsonNode jsonNode,
            final Map<String, FieldConfig> fields) {

        if (fields == null || fields.isEmpty()) {
            return;
        }
        populateFields(builder, jsonNode, fields);
    }

    /**
     * Builds a field value from JSON based on the field configuration.
     *
     * <p>For primitive types (string, int, long, float, double, boolean, timestamp),
     * extraction is done once and the value is passed directly to the converter.
     * This avoids redundant extraction calls for better performance.
     *
     * <p>For fields with merge definitions, each definition is processed separately
     * and results are merged (maps are combined, arrays are concatenated).
     *
     * @param jsonNode the source JSON
     * @param config the field configuration
     * @return the converted value, or null if not found
     */
    public Object buildField(final JsonNode jsonNode, final FieldConfig config) {
        // Handle merge definitions
        if (config.hasMergeDefinitions()) {
            return buildMergedField(jsonNode, config);
        }

        final String type = config.type();

        // For primitive types, extract once and convert directly
        // This avoids calling extractor.extract() multiple times
        return switch (type) {
            case STRING, INT32, INT64, FLOAT, DOUBLE, BOOLEAN -> {
                final JsonNode extracted = extractor.extract(jsonNode, config).orElse(null);
                yield convertPrimitive(extracted, type);
            }
            case TIMESTAMP -> {
                final JsonNode extracted = extractor.extract(jsonNode, config).orElse(null);
                final String format = config.format() != null ? config.format() : FORMAT_ISO8601;
                yield typeConverter.convertTimestamp(extracted, format);
            }
            // Complex types need special handling with their own extraction
            case ARRAY -> buildArray(jsonNode, config);
            case OBJECT -> buildObject(jsonNode, config);
            case ENUM -> buildEnum(jsonNode, config);
            case MAP -> buildMap(jsonNode, config);
            default -> throw new IllegalStateException("Unsupported type: " + type);
        };
    }

    /**
     * Builds a field by processing multiple merge definitions and combining results.
     * Currently supports merging maps (entries are combined) and arrays (concatenated).
     *
     * @param jsonNode the source JSON
     * @param config the field configuration with merge definitions
     * @return the merged value, or null if all definitions return null
     */
    @SuppressWarnings("unchecked")
    private Object buildMergedField(final JsonNode jsonNode, final FieldConfig config) {
        final String type = config.type();

        if (MAP.equals(type)) {
            // Merge maps: combine all entries from each definition
            Map<String, Object> mergedMap = new HashMap<>();

            for (FieldConfig definition : config.mergeDefinitions()) {
                Object result = buildField(jsonNode, definition);
                if (result instanceof Map) {
                    Map<String, Object> partialMap = (Map<String, Object>) result;
                    mergedMap.putAll(partialMap);
                }
            }

            return mergedMap.isEmpty() ? null : mergedMap;
        }

        if (ARRAY.equals(type)) {
            // Merge arrays: concatenate all results
            List<Object> mergedList = new ArrayList<>();

            for (FieldConfig definition : config.mergeDefinitions()) {
                Object result = buildField(jsonNode, definition);
                if (result instanceof List) {
                    mergedList.addAll((List<?>) result);
                }
            }

            return mergedList.isEmpty() ? null : mergedList;
        }

        // For other types, use the first non-null result (fallback behavior)
        for (FieldConfig definition : config.mergeDefinitions()) {
            Object result = buildField(jsonNode, definition);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Converts an already-extracted JsonNode to the target primitive type.
     * This is more efficient than extracting inside each conversion method.
     */
    private Object convertPrimitive(final JsonNode extracted, final String type) {
        return switch (type) {
            case STRING -> typeConverter.convert(extracted, String.class);
            case INT32 -> typeConverter.convert(extracted, Integer.class);
            case INT64 -> typeConverter.convert(extracted, Long.class);
            case FLOAT -> typeConverter.convert(extracted, Float.class);
            case DOUBLE -> typeConverter.convert(extracted, Double.class);
            case BOOLEAN -> typeConverter.convert(extracted, Boolean.class);
            default -> null;
        };
    }

    private List<?> buildArray(final JsonNode jsonNode, final FieldConfig config){
        final String itemType = config.itemType();
        final JsonNode extracted = extractor.extract(jsonNode, config).orElse(null);

        return switch (itemType) {
            case STRING -> typeConverter.convertList(extracted, String.class);
            case INT32 -> typeConverter.convertList(extracted, Integer.class);
            case INT64 -> typeConverter.convertList(extracted, Long.class);
            case FLOAT -> typeConverter.convertList(extracted, Float.class);
            case DOUBLE -> typeConverter.convertList(extracted, Double.class);
            case BOOLEAN -> typeConverter.convertList(extracted, Boolean.class);
            default -> buildObjectArray(jsonNode, config, itemType);
        };
    }

    private List<Message> buildObjectArray(final JsonNode jsonNode, final FieldConfig config, final String itemType) {
        final Optional<JsonNode> arrayNodeOpt = extractor.extract(jsonNode, config);

        if (itemType == null || arrayNodeOpt.isEmpty() || !arrayNodeOpt.get().isArray()) {
            return null;
        }

        final JsonNode arrayNode = arrayNodeOpt.get();
        final Map<String, FieldConfig> itemFields = config.fields();
        final Class<? extends Message> messageClass = typeResolver.resolveMessage(itemType);

        final List<Message> result = new ArrayList<>();

        for (JsonNode elementNode : arrayNode) {
            if (elementNode == null || elementNode.isNull()) {
                continue;
            }

            final Message.Builder itemBuilder = builderFactory.createBuilderFrom(messageClass);
            populateFieldsSafe(itemBuilder, elementNode, itemFields);
            result.add(itemBuilder.build());
        }

        return result;
    }

    private Message buildObject(final JsonNode jsonNode, final FieldConfig config) {
        final Optional<JsonNode> extractedOpt = extractor.extract(jsonNode, config);
        if (extractedOpt.isEmpty() || !extractedOpt.get().isObject()) {
            return null;
        }

        final JsonNode objectNode = extractedOpt.get();
        final String objectType = config.objectType();

        if (objectType == null) {
            return null;
        }

        final Class<? extends Message> messageClass = typeResolver.resolveMessage(objectType);
        final Message.Builder nestedBuilder = builderFactory.createBuilderFrom(messageClass);

        populateFieldsSafe(nestedBuilder, objectNode, config.fields());

        return nestedBuilder.build();
    }

    /**
     * Builds a Protobuf enum value from JSON.
     *
     * <p>Supports multiple input formats:
     * <ul>
     *   <li>String matching enum name: "IN_STOCK"</li>
     *   <li>String case-insensitive: "in_stock", "InStock"</li>
     *   <li>Numeric value: 1 (matches enum number)</li>
     * </ul>
     *
     * @param jsonNode the source JSON
     * @param config the field configuration with enumType
     * @return the Protobuf enum value, or null if not found
     */
    private ProtocolMessageEnum buildEnum(final JsonNode jsonNode, final FieldConfig config) {
        final Optional<JsonNode> extractedOpt = extractor.extract(jsonNode, config);
        if (extractedOpt.isEmpty()) {
            return null;
        }

        final JsonNode node = extractedOpt.get();
        final String enumTypeName = config.enumType();

        if (enumTypeName == null || enumTypeName.isBlank()) {
            throw new MappingException(
                    "Field type 'enum' requires 'enumType' to be specified");
        }

        // Resolve the enum class
        final Class<?> enumClass = typeResolver.resolve(enumTypeName);
        if (!ProtocolMessageEnum.class.isAssignableFrom(enumClass)) {
            throw new MappingException(
                    String.format("Type '%s' is not a Protobuf enum", enumTypeName));
        }

        // Get the enum descriptor
        try {
            final Descriptors.EnumDescriptor descriptor = getEnumDescriptor(enumClass);

            Descriptors.EnumValueDescriptor valueDescriptor = null;

            if (node.isNumber()) {
                // Match by enum number
                valueDescriptor = descriptor.findValueByNumber(node.intValue());
            } else if (node.isTextual()) {
                final String textValue = node.asText().trim();

                // Try exact match first
                valueDescriptor = descriptor.findValueByName(textValue);

                // Try uppercase if not found
                if (valueDescriptor == null) {
                    valueDescriptor = descriptor.findValueByName(textValue.toUpperCase());
                }

                // Try with underscores (e.g., "inStock" -> "IN_STOCK")
                if (valueDescriptor == null) {
                    String normalized = textValue
                            .replaceAll("([a-z])([A-Z])", "$1_$2")
                            .toUpperCase();
                    valueDescriptor = descriptor.findValueByName(normalized);
                }
            }

            if (valueDescriptor == null) {
                throw new MappingException(
                        String.format("Enum value '%s' not found in %s. Valid values: %s",
                                node.asText(), enumTypeName, descriptor.getValues()));
            }

            // Create the enum instance
            return createEnumInstance(enumClass, valueDescriptor);

        } catch (MappingException e) {
            throw e;
        } catch (Exception e) {
            throw new MappingException(
                    String.format("Failed to build enum '%s': %s", enumTypeName, e.getMessage()), e);
        }
    }

    private Descriptors.EnumDescriptor getEnumDescriptor(Class<?> enumClass) {
        try {
            var method = enumClass.getMethod("getDescriptor");
            return (Descriptors.EnumDescriptor) method.invoke(null);
        } catch (Exception e) {
            throw new MappingException(
                    "Failed to get descriptor for enum: " + enumClass.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private ProtocolMessageEnum createEnumInstance(Class<?> enumClass,
                                                    Descriptors.EnumValueDescriptor valueDescriptor) {
        try {
            var method = enumClass.getMethod("forNumber", int.class);
            return (ProtocolMessageEnum) method.invoke(null, valueDescriptor.getNumber());
        } catch (Exception e) {
            throw new MappingException(
                    "Failed to create enum instance for: " + valueDescriptor.getName(), e);
        }
    }

    /**
     * Builds a Protobuf map field from JSON.
     *
     * <p>The JSON is expected to already have the correct structure from the transform.
     * For object values, the JSON fields are mapped directly to the Protobuf message
     * using the field descriptors.
     *
     * @param jsonNode the source JSON
     * @param config the field configuration with objectType
     * @return a Map suitable for Protobuf putAll, or null if not found
     */
    private Map<?, ?> buildMap(final JsonNode jsonNode, final FieldConfig config) {
        final Optional<JsonNode> extractedOpt = extractor.extract(jsonNode, config);
        if (extractedOpt.isEmpty() || !extractedOpt.get().isObject()) {
            return null;
        }

        final JsonNode mapNode = extractedOpt.get();
        final String objectType = config.objectType();

        if (objectType == null) {
            throw new MappingException("Map requires 'objectType' to be specified");
        }

        final Class<? extends Message> messageClass = typeResolver.resolveMessage(objectType);
        Map<String, Message> result = new HashMap<>();

        var entries = mapNode.fields();

        while (entries.hasNext()) {
            var entry = entries.next();
            String key = entry.getKey();
            JsonNode valueNode = entry.getValue();

            if (valueNode != null && valueNode.isObject()) {
                Message message = jsonToMessage(valueNode, messageClass);
                if (message != null) {
                    result.put(key, message);
                }
            }
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * Converts a JSON object directly to a Protobuf message using field descriptors.
     * Maps JSON fields to Protobuf fields automatically by name.
     */
    private Message jsonToMessage(final JsonNode json, final Class<? extends Message> messageClass) {
        final Message.Builder builder = builderFactory.createBuilderFrom(messageClass);

        var jsonFields = json.fields();
        while (jsonFields.hasNext()) {
            var entry = jsonFields.next();
            String fieldName = entry.getKey();
            JsonNode fieldValue = entry.getValue();

            if (fieldValue == null || fieldValue.isNull() || fieldValue.isMissingNode()) {
                continue;
            }

            // Convert JSON array to List for repeated fields
            if (fieldValue.isArray()) {
                List<Object> values = convertJsonArray(fieldValue);
                if (!values.isEmpty()) {
                    setterResolver.setValue(builder, fieldName, values);
                }
            } else {
                // Scalar value
                Object value = convertJsonScalar(fieldValue);
                if (value != null) {
                    setterResolver.setValue(builder, fieldName, value);
                }
            }
        }

        return builder.build();
    }

    /**
     * Converts a JSON array to a List of appropriate types.
     */
    private List<Object> convertJsonArray(final JsonNode arrayNode) {
        List<Object> result = new ArrayList<>();
        for (JsonNode element : arrayNode) {
            Object value = convertJsonScalar(element);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * Converts a JSON scalar value to the appropriate Java type.
     */
    private Object convertJsonScalar(final JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.asText();
    }

    public void setBuilderEventType(Message.Builder builder, String eventType) {
        setterResolver.setValue(builder, "eventType", eventType);
    }
}
