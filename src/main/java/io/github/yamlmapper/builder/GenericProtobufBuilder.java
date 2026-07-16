package io.github.yamlmapper.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingConfig;
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
    private final EnumValueResolver enumValueResolver;
    private final int maxNestingDepth;

    /**
     * Creates a GenericProtobufBuilder with default configuration.
     */
    public GenericProtobufBuilder(
            PathResolver pathResolver,
            TransformRegistry transformRegistry,
            ObjectMapper objectMapper,
            TypeConverter typeConverter,
            SetterResolver setterResolver,
            TypeResolver typeResolver,
            BuilderFactory builderFactory) {
        this(pathResolver, transformRegistry, objectMapper, typeConverter,
             setterResolver, typeResolver, builderFactory, MappingConfig.DEFAULT);
    }

    /**
     * Creates a GenericProtobufBuilder with the specified configuration.
     */
    public GenericProtobufBuilder(
            PathResolver pathResolver,
            TransformRegistry transformRegistry,
            ObjectMapper objectMapper,
            TypeConverter typeConverter,
            SetterResolver setterResolver,
            TypeResolver typeResolver,
            BuilderFactory builderFactory,
            MappingConfig config) {
        this.pathResolver = pathResolver;
        this.transformRegistry = transformRegistry;
        this.objectMapper = objectMapper;
        this.typeConverter = typeConverter;
        this.setterResolver = setterResolver;
        this.typeResolver = typeResolver;
        this.builderFactory = builderFactory;
        this.enumValueResolver = new EnumValueResolver();
        this.maxNestingDepth = config != null ? config.maxNestingDepth() : MappingConfig.DEFAULT_MAX_NESTING_DEPTH;
    }

    /**
     * Builds a Protobuf message from JSON using an existing builder.
     */
    @SuppressWarnings("unchecked")
    public <T extends Message> T build(
            final Message.Builder builder,
            final JsonNode jsonNode,
            final Map<String, FieldConfig> fields) {
        // Skip validation - already validated at config load time
        populateFields(builder, jsonNode, fields, 0);
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
            final Map<String, FieldConfig> fields,
            final int currentDepth) {
        // Check depth limit to prevent StackOverflowError from circular configs
        if (currentDepth > maxNestingDepth) {
            throw new MappingException(String.format(
                "Maximum nesting depth (%d) exceeded. Check for circular references in your mapping configuration.",
                maxNestingDepth));
        }

        // Lazy initialization - only create if we encounter a oneof field
        Map<String, String> setOneofs = null;

        for (Map.Entry<String, FieldConfig> entry : fields.entrySet()) {
            final String fieldName = entry.getKey();
            final FieldConfig fieldConfig = entry.getValue();

            try {
                Object value = buildField(jsonNode, fieldConfig, currentDepth);

                if (value == null && fieldConfig.defaultValue() != null) {
                    value = convertDefaultValue(fieldConfig.defaultValue(), fieldConfig.type());
                    if (log.isDebugEnabled()) {
                        log.debug("Field '{}' using default value", fieldName);
                    }
                }

                if (value == null && fieldConfig.required()) {
                    throw new FieldExtractionException(fieldName, fieldConfig.source());
                }

                if (value != null) {
                    setOneofs = checkOneofConflict(builder, fieldName, setOneofs);
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
     * Checks and tracks oneof conflicts. Returns the (possibly newly created) tracking map.
     * Lazy initialization avoids HashMap allocation when no oneofs are present.
     */
    private Map<String, String> checkOneofConflict(
            final Message.Builder builder,
            final String fieldName,
            Map<String, String> setOneofs) {

        SetterResolver.OneofInfo oneofInfo = setterResolver.getOneofInfo(builder, fieldName);
        if (oneofInfo == null) {
            return setOneofs;  // Not a oneof field, no tracking needed
        }

        // Lazy create the tracking map only when we encounter a oneof field
        if (setOneofs == null) {
            setOneofs = new HashMap<>();
        }

        String previousField = setOneofs.get(oneofInfo.oneofName());
        if (previousField != null && !previousField.equals(oneofInfo.fieldName())) {
            log.warn("OneOf conflict in '{}': setting '{}' overwrites previous value from '{}'",
                    oneofInfo.oneofName(), fieldName, previousField);
        }

        setOneofs.put(oneofInfo.oneofName(), oneofInfo.fieldName());
        return setOneofs;
    }

    // =========================================================================
    // Field Extraction
    // =========================================================================

    /**
     * Extracts a value from JSON based on field configuration.
     * Handles path resolution, embedded JSON parsing, and transforms.
     * Returns null if no value found (avoids Optional allocation).
     */
    private JsonNode extractField(final JsonNode root, final FieldConfig config) {
        List<String> sources = config.source();

        // Fast path: single source (most common case) - avoids iterator allocation
        if (sources.size() == 1) {
            return extractFromSource(root, sources.get(0), config);
        }

        // Multiple sources: try each in order (fallback chain)
        for (String source : sources) {
            JsonNode result = extractFromSource(root, source, config);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Extracts value from a single source path.
     */
    private JsonNode extractFromSource(final JsonNode root, final String source, final FieldConfig config) {
        JsonNode node = pathResolver.resolve(root, source);

        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        // Parse embedded JSON if enabled
        if (config.parseEmbeddedJson()) {
            node = tryParseEmbeddedJson(node);
        }

        // Apply transform if specified
        if (config.transform() != null) {
            node = applyTransform(node, config, root);
        }

        return node;
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

        if (log.isDebugEnabled()) {
            log.debug("Applying transform '{}' to field '{}'", config.transform(), config.name());
        }

        TransformContext context = new TransformContextImpl(
                root,
                objectMapper,
                config.transformParams(),
                pathResolver
        );

        return transform.apply(node, context);
    }

    // =========================================================================
    // Field Building by Type
    // =========================================================================

    Object buildField(final JsonNode jsonNode, final FieldConfig config, final int currentDepth) {
        if (config.hasMergeDefinitions()) {
            return buildMergedField(jsonNode, config, currentDepth);
        }

        final String type = config.type();

        return switch (type) {
            case STRING, INT32, INT64, FLOAT, DOUBLE, BOOLEAN -> {
                final JsonNode extracted = extractField(jsonNode, config);
                yield convertPrimitive(extracted, type);
            }
            case TIMESTAMP -> {
                final JsonNode extracted = extractField(jsonNode, config);
                final String format = config.format() != null ? config.format() : FORMAT_ISO8601;
                yield typeConverter.convertTimestamp(extracted, format);
            }
            case ARRAY -> buildArray(jsonNode, config, currentDepth);
            case OBJECT -> buildObject(jsonNode, config, currentDepth);
            case ENUM -> buildEnum(jsonNode, config);
            case MAP -> buildMap(jsonNode, config, currentDepth);
            default -> throw new IllegalStateException("Unsupported type: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    private Object buildMergedField(final JsonNode jsonNode, final FieldConfig config, final int currentDepth) {
        final String type = config.type();

        if (MAP.equals(type)) {
            Map<String, Object> mergedMap = new HashMap<>();
            for (FieldConfig definition : config.mergeDefinitions()) {
                Object result = buildField(jsonNode, definition, currentDepth);
                if (result instanceof Map) {
                    mergedMap.putAll((Map<String, Object>) result);
                }
            }
            return mergedMap.isEmpty() ? null : mergedMap;
        }

        if (ARRAY.equals(type)) {
            List<Object> mergedList = new ArrayList<>();
            for (FieldConfig definition : config.mergeDefinitions()) {
                Object result = buildField(jsonNode, definition, currentDepth);
                if (result instanceof List) {
                    mergedList.addAll((List<?>) result);
                }
            }
            return mergedList.isEmpty() ? null : mergedList;
        }

        for (FieldConfig definition : config.mergeDefinitions()) {
            Object result = buildField(jsonNode, definition, currentDepth);
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

    private List<?> buildArray(final JsonNode jsonNode, final FieldConfig config, final int currentDepth) {
        final String itemType = config.itemType();
        final JsonNode extracted = extractField(jsonNode, config);

        return switch (itemType) {
            case STRING -> typeConverter.convertList(extracted, String.class);
            case INT32 -> typeConverter.convertList(extracted, Integer.class);
            case INT64 -> typeConverter.convertList(extracted, Long.class);
            case FLOAT -> typeConverter.convertList(extracted, Float.class);
            case DOUBLE -> typeConverter.convertList(extracted, Double.class);
            case BOOLEAN -> typeConverter.convertList(extracted, Boolean.class);
            default -> buildObjectArray(jsonNode, config, itemType, currentDepth);
        };
    }

    private List<Message> buildObjectArray(final JsonNode jsonNode, final FieldConfig config, final String itemType, final int currentDepth) {
        final JsonNode arrayNode = extractField(jsonNode, config);

        if (itemType == null || arrayNode == null || !arrayNode.isArray()) {
            return null;
        }
        final Map<String, FieldConfig> itemFields = config.fields();
        final Class<? extends Message> messageClass = typeResolver.resolveMessage(itemType);

        final List<Message> result = new ArrayList<>();

        for (JsonNode elementNode : arrayNode) {
            if (elementNode == null || elementNode.isNull()) {
                continue;
            }

            final Message.Builder itemBuilder = builderFactory.createBuilderFrom(messageClass);
            populateFields(itemBuilder, elementNode, itemFields, currentDepth + 1);
            result.add(itemBuilder.build());
        }

        return result;
    }

    private Message buildObject(final JsonNode jsonNode, final FieldConfig config, final int currentDepth) {
        final JsonNode objectNode = extractField(jsonNode, config);
        if (objectNode == null || !objectNode.isObject()) {
            return null;
        }
        final String objectType = config.objectType();

        if (objectType == null) {
            return null;
        }

        final Class<? extends Message> messageClass = typeResolver.resolveMessage(objectType);
        final Message.Builder nestedBuilder = builderFactory.createBuilderFrom(messageClass);

        populateFields(nestedBuilder, objectNode, config.fields(), currentDepth + 1);

        return nestedBuilder.build();
    }

    private ProtocolMessageEnum buildEnum(final JsonNode jsonNode, final FieldConfig config) {
        final JsonNode node = extractField(jsonNode, config);
        if (node == null) {
            return null;
        }
        final String enumTypeName = config.enumType();

        if (enumTypeName == null || enumTypeName.isBlank()) {
            throw new MappingException("Field type 'enum' requires 'enumType' to be specified");
        }

        final Class<?> enumClass = typeResolver.resolve(enumTypeName);
        if (!ProtocolMessageEnum.class.isAssignableFrom(enumClass)) {
            throw new MappingException(String.format("Type '%s' is not a Protobuf enum", enumTypeName));
        }

        // Use cached EnumValueResolver for efficient lookups
        Object value = node.isNumber() ? node.intValue() : node.asText();
        return enumValueResolver.resolve(enumClass, value);
    }

    private Map<?, ?> buildMap(final JsonNode jsonNode, final FieldConfig config, final int currentDepth) {
        final JsonNode mapNode = extractField(jsonNode, config);
        if (mapNode == null || !mapNode.isObject()) {
            return null;
        }
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
                Message message = jsonToMessage(valueNode, messageClass, currentDepth + 1);
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

    private Message jsonToMessage(final JsonNode json, final Class<? extends Message> messageClass, final int currentDepth) {
        // Check depth limit
        if (currentDepth > maxNestingDepth) {
            throw new MappingException(String.format(
                "Maximum nesting depth (%d) exceeded in map value. Check for circular references.",
                maxNestingDepth));
        }

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
}
