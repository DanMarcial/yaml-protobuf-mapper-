package io.github.yamlmapper.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.exception.FieldExtractionException;
import io.github.yamlmapper.exception.MappingException;
import io.github.yamlmapper.extractor.PathResolver;
import io.github.yamlmapper.resolver.TypeResolver;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;
import io.github.yamlmapper.transform.TransformContextImpl;
import io.github.yamlmapper.transform.TransformRegistry;

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

/**
 * Builds Protobuf messages from JSON using YAML-defined field configurations.
 *
 * <p>This class handles the complete flow of field extraction and conversion:
 * <ol>
 *   <li>Resolve JSON path to extract value</li>
 *   <li>Parse embedded JSON if enabled</li>
 *   <li>Apply transform if specified</li>
 *   <li>Convert to target type</li>
 *   <li>Set value on Protobuf builder</li>
 * </ol>
 */
public class GenericProtobufBuilder {

    private static final Logger log = LoggerFactory.getLogger(GenericProtobufBuilder.class);

    private final PathResolver pathResolver;
    private final TransformRegistry transformRegistry;
    private final ObjectMapper objectMapper;
    private final TypeConverter typeConverter;
    private final SetterResolver setterResolver;
    private final TypeResolver typeResolver;
    private final BuilderFactory builderFactory;

    public GenericProtobufBuilder(
            PathResolver pathResolver,
            TransformRegistry transformRegistry,
            ObjectMapper objectMapper,
            TypeConverter typeConverter,
            SetterResolver setterResolver,
            TypeResolver typeResolver,
            BuilderFactory builderFactory) {
        this.pathResolver = pathResolver;
        this.transformRegistry = transformRegistry;
        this.objectMapper = objectMapper;
        this.typeConverter = typeConverter;
        this.setterResolver = setterResolver;
        this.typeResolver = typeResolver;
        this.builderFactory = builderFactory;
    }

    /**
     * Builds a Protobuf message from JSON using an existing builder.
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

    public void setBuilderEventType(Message.Builder builder, String eventType) {
        setterResolver.setValue(builder, "eventType", eventType);
    }

    // =========================================================================
    // Field Population
    // =========================================================================

    private void populateFields(
            final Message.Builder builder,
            final JsonNode jsonNode,
            final Map<String, FieldConfig> fields) {

        if (fields == null || fields.isEmpty()) {
            return;
        }

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
                    checkOneofConflict(builder, fieldName, setOneofs);
                    setterResolver.setValue(builder, fieldName, value);
                }
            } catch (MappingException e) {
                throw e;
            } catch (Exception e) {
                throw new MappingException(String.format(ERR_FIELD_PROCESSING, fieldName, e.getMessage()), e);
            }
        }
    }

    private void populateFieldsSafe(
            final Message.Builder builder,
            final JsonNode jsonNode,
            final Map<String, FieldConfig> fields) {

        if (fields == null || fields.isEmpty()) {
            return;
        }
        populateFields(builder, jsonNode, fields);
    }

    private void checkOneofConflict(
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

    // =========================================================================
    // Field Extraction
    // =========================================================================

    /**
     * Extracts a value from JSON based on field configuration.
     * Handles path resolution, embedded JSON parsing, and transforms.
     */
    private Optional<JsonNode> extractField(final JsonNode root, final FieldConfig config) {
        for (String source : config.source()) {
            JsonNode node = pathResolver.resolve(root, source);

            if (node == null || node.isNull() || node.isMissingNode()) {
                continue;
            }

            // Parse embedded JSON if enabled
            if (config.parseEmbeddedJson()) {
                node = tryParseEmbeddedJson(node);
            }

            // Apply transform if specified
            if (config.transform() != null) {
                node = applyTransform(node, config, root);
            }

            return Optional.of(node);
        }

        return Optional.empty();
    }

    /**
     * Attempts to parse a text node as embedded JSON.
     */
    private JsonNode tryParseEmbeddedJson(final JsonNode node) {
        if (node == null || !node.isTextual()) {
            return node;
        }

        String text = node.asText().trim();
        boolean looksLikeJson =
                (text.startsWith("{") && text.endsWith("}")) ||
                (text.startsWith("[") && text.endsWith("]"));

        if (!looksLikeJson) {
            return node;
        }

        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new MappingException("Failed to parse embedded JSON: " + text, e);
        }
    }

    /**
     * Applies a transform to a node.
     */
    private JsonNode applyTransform(final JsonNode node, final FieldConfig config, final JsonNode root) {
        Transform transform = transformRegistry.get(config.transform());

        if (transform == null) {
            log.warn("Transform '{}' not found for field '{}'", config.transform(), config.name());
            return node;
        }

        log.debug("Applying transform '{}' to field '{}'", config.transform(), config.name());

        TransformContext context = new TransformContextImpl(
                root,
                objectMapper,
                config.transformParams()
        );

        return transform.apply(node, context);
    }

    // =========================================================================
    // Field Building by Type
    // =========================================================================

    public Object buildField(final JsonNode jsonNode, final FieldConfig config) {
        if (config.hasMergeDefinitions()) {
            return buildMergedField(jsonNode, config);
        }

        final String type = config.type();

        return switch (type) {
            case STRING, INT32, INT64, FLOAT, DOUBLE, BOOLEAN -> {
                final JsonNode extracted = extractField(jsonNode, config).orElse(null);
                yield convertPrimitive(extracted, type);
            }
            case TIMESTAMP -> {
                final JsonNode extracted = extractField(jsonNode, config).orElse(null);
                final String format = config.format() != null ? config.format() : FORMAT_ISO8601;
                yield typeConverter.convertTimestamp(extracted, format);
            }
            case ARRAY -> buildArray(jsonNode, config);
            case OBJECT -> buildObject(jsonNode, config);
            case ENUM -> buildEnum(jsonNode, config);
            case MAP -> buildMap(jsonNode, config);
            default -> throw new IllegalStateException("Unsupported type: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    private Object buildMergedField(final JsonNode jsonNode, final FieldConfig config) {
        final String type = config.type();

        if (MAP.equals(type)) {
            Map<String, Object> mergedMap = new HashMap<>();
            for (FieldConfig definition : config.mergeDefinitions()) {
                Object result = buildField(jsonNode, definition);
                if (result instanceof Map) {
                    mergedMap.putAll((Map<String, Object>) result);
                }
            }
            return mergedMap.isEmpty() ? null : mergedMap;
        }

        if (ARRAY.equals(type)) {
            List<Object> mergedList = new ArrayList<>();
            for (FieldConfig definition : config.mergeDefinitions()) {
                Object result = buildField(jsonNode, definition);
                if (result instanceof List) {
                    mergedList.addAll((List<?>) result);
                }
            }
            return mergedList.isEmpty() ? null : mergedList;
        }

        for (FieldConfig definition : config.mergeDefinitions()) {
            Object result = buildField(jsonNode, definition);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

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

    // =========================================================================
    // Complex Type Builders
    // =========================================================================

    private List<?> buildArray(final JsonNode jsonNode, final FieldConfig config) {
        final String itemType = config.itemType();
        final JsonNode extracted = extractField(jsonNode, config).orElse(null);

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
        final Optional<JsonNode> arrayNodeOpt = extractField(jsonNode, config);

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
        final Optional<JsonNode> extractedOpt = extractField(jsonNode, config);
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

    private ProtocolMessageEnum buildEnum(final JsonNode jsonNode, final FieldConfig config) {
        final Optional<JsonNode> extractedOpt = extractField(jsonNode, config);
        if (extractedOpt.isEmpty()) {
            return null;
        }

        final JsonNode node = extractedOpt.get();
        final String enumTypeName = config.enumType();

        if (enumTypeName == null || enumTypeName.isBlank()) {
            throw new MappingException("Field type 'enum' requires 'enumType' to be specified");
        }

        final Class<?> enumClass = typeResolver.resolve(enumTypeName);
        if (!ProtocolMessageEnum.class.isAssignableFrom(enumClass)) {
            throw new MappingException(String.format("Type '%s' is not a Protobuf enum", enumTypeName));
        }

        try {
            final Descriptors.EnumDescriptor descriptor = getEnumDescriptor(enumClass);
            Descriptors.EnumValueDescriptor valueDescriptor = null;

            if (node.isNumber()) {
                valueDescriptor = descriptor.findValueByNumber(node.intValue());
            } else if (node.isTextual()) {
                final String textValue = node.asText().trim();
                valueDescriptor = descriptor.findValueByName(textValue);

                if (valueDescriptor == null) {
                    valueDescriptor = descriptor.findValueByName(textValue.toUpperCase());
                }

                if (valueDescriptor == null) {
                    String normalized = textValue.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
                    valueDescriptor = descriptor.findValueByName(normalized);
                }
            }

            if (valueDescriptor == null) {
                throw new MappingException(String.format(
                        "Enum value '%s' not found in %s. Valid values: %s",
                        node.asText(), enumTypeName, descriptor.getValues()));
            }

            return createEnumInstance(enumClass, valueDescriptor);

        } catch (MappingException e) {
            throw e;
        } catch (Exception e) {
            throw new MappingException(
                    String.format("Failed to build enum '%s': %s", enumTypeName, e.getMessage()), e);
        }
    }

    private Map<?, ?> buildMap(final JsonNode jsonNode, final FieldConfig config) {
        final Optional<JsonNode> extractedOpt = extractField(jsonNode, config);
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

    // =========================================================================
    // Helpers
    // =========================================================================

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

            if (fieldValue.isArray()) {
                List<Object> values = convertJsonArray(fieldValue);
                if (!values.isEmpty()) {
                    setterResolver.setValue(builder, fieldName, values);
                }
            } else {
                Object value = convertJsonScalar(fieldValue);
                if (value != null) {
                    setterResolver.setValue(builder, fieldName, value);
                }
            }
        }

        return builder.build();
    }

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

    private Object convertJsonScalar(final JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        return switch (node.getNodeType()) {
            case STRING -> node.textValue();
            case BOOLEAN -> node.booleanValue();
            case NUMBER -> node.numberValue();
            default -> node.asText();
        };
    }

    private Descriptors.EnumDescriptor getEnumDescriptor(Class<?> enumClass) {
        try {
            var method = enumClass.getMethod("getDescriptor");
            return (Descriptors.EnumDescriptor) method.invoke(null);
        } catch (Exception e) {
            throw new MappingException("Failed to get descriptor for enum: " + enumClass.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private ProtocolMessageEnum createEnumInstance(
            Class<?> enumClass,
            Descriptors.EnumValueDescriptor valueDescriptor) {
        try {
            var method = enumClass.getMethod("forNumber", int.class);
            return (ProtocolMessageEnum) method.invoke(null, valueDescriptor.getNumber());
        } catch (Exception e) {
            throw new MappingException("Failed to create enum instance for: " + valueDescriptor.getName(), e);
        }
    }
}
