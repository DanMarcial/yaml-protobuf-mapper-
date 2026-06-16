package io.github.yamlmapper.validation;

import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.resolver.TypeResolver;
import io.github.yamlmapper.transform.TransformRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.github.yamlmapper.config.TypeConstants.ARRAY;
import static io.github.yamlmapper.config.TypeConstants.BOOLEAN;
import static io.github.yamlmapper.config.TypeConstants.DOUBLE;
import static io.github.yamlmapper.config.TypeConstants.ENUM;
import static io.github.yamlmapper.config.TypeConstants.FLOAT;
import static io.github.yamlmapper.config.TypeConstants.INT32;
import static io.github.yamlmapper.config.TypeConstants.INT64;
import static io.github.yamlmapper.config.TypeConstants.MAP;
import static io.github.yamlmapper.config.TypeConstants.OBJECT;
import static io.github.yamlmapper.config.TypeConstants.STRING;
import static io.github.yamlmapper.config.TypeConstants.TIMESTAMP;

/**
 * Validates YAML mapping schema configurations.
 *
 * <p>This validator checks that field configurations are correct:
 * <ul>
 *   <li>Required attributes are present (type, objectType, itemType, etc.)</li>
 *   <li>Referenced types can be resolved</li>
 *   <li>Transforms are registered</li>
 *   <li>Default values are compatible with field types</li>
 * </ul>
 *
 * <p>This class is thread-safe after construction.
 */
public class SchemaValidator {

  private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class);

  private final TypeResolver typeResolver;
  private final TransformRegistry transformRegistry;

  /**
   * Creates a new SchemaValidator.
   *
   * @param typeResolver the type resolver for checking Protobuf types
   * @param transformRegistry the transform registry for checking transforms
   */
  public SchemaValidator(TypeResolver typeResolver, TransformRegistry transformRegistry) {
    this.typeResolver = typeResolver;
    this.transformRegistry = transformRegistry;
  }

  /**
   * Validates a mapping schema.
   *
   * @param schema the schema to validate
   * @param configId the config ID for error messages
   * @return validation result with errors and warnings
   */
  public ValidationResult validate(MappingSchema schema, String configId) {
    log.debug("Validating schema '{}'", configId);

    ValidationResult.Builder result = ValidationResult.builder();

    if (schema == null) {
      result.addError(String.format("Schema '%s' is null", configId));
      return result.build();
    }

    Map<String, FieldConfig> fields = schema.fields();
    if (fields == null || fields.isEmpty()) {
      result.addError(String.format("Configuration '%s' has no fields defined", configId));
      return result.build();
    }

    for (Map.Entry<String, FieldConfig> entry : fields.entrySet()) {
      validateField(entry.getKey(), entry.getValue(), result);
    }

    ValidationResult validationResult = result.build();

    if (validationResult.isValid()) {
      log.debug("Schema '{}' valid: {} fields", configId, fields.size());
    } else {
      log.warn("Schema '{}' invalid: {} errors", configId, validationResult.errors().size());
    }

    return validationResult;
  }

  /**
   * Validates a single field configuration recursively.
   *
   * @param fieldName the field name (may include parent path)
   * @param config the field configuration
   * @param result the result builder to add errors/warnings
   */
  public void validateField(String fieldName, FieldConfig config, ValidationResult.Builder result) {
    // Handle merge definitions - validate each definition instead of parent
    if (config.hasMergeDefinitions()) {
      validateMergeDefinitions(fieldName, config, result);
      return;
    }

    // Validate type is specified
    if (config.type() == null || config.type().isBlank()) {
      result.addError(String.format("Field '%s': type is required", fieldName));
      return; // Can't continue validation without type
    }

    // Validate source is not empty
    if (config.source() == null || config.source().isEmpty()) {
      result.addWarning(String.format("Field '%s': no source defined, will only use default value", fieldName));
    }

    // Type-specific validations
    switch (config.type()) {
      case OBJECT -> validateObjectField(fieldName, config, result);
      case ARRAY -> validateArrayField(fieldName, config, result);
      case ENUM -> validateEnumField(fieldName, config, result);
      case MAP -> validateMapField(fieldName, config, result);
    }

    // Validate transform exists
    if (config.transform() != null && !config.transform().isBlank()) {
      if (transformRegistry.get(config.transform()) == null) {
        result.addError(String.format("Field '%s': transform '%s' is not registered", fieldName, config.transform()));
      }
    }

    // Validate default value compatibility with type
    if (config.defaultValue() != null && config.type() != null) {
      validateDefaultValueType(fieldName, config.defaultValue(), config.type(), result);
    }

    // Validate nested fields recursively
    if (config.fields() != null && !config.fields().isEmpty()) {
      for (Map.Entry<String, FieldConfig> nested : config.fields().entrySet()) {
        validateField(fieldName + "." + nested.getKey(), nested.getValue(), result);
      }
    }

    // Warning for required field without source
    if (config.required() && (config.source() == null || config.source().isEmpty()) && config.defaultValue() == null) {
      result.addWarning(String.format("Field '%s': required field has no source and no default", fieldName));
    }
  }

  /**
   * Validates merge definitions for a field.
   * Each definition in the list is validated as a separate field config.
   *
   * @param fieldName the field name
   * @param config the field configuration with merge definitions
   * @param result the result builder to add errors/warnings
   */
  private void validateMergeDefinitions(String fieldName, FieldConfig config, ValidationResult.Builder result) {
    var definitions = config.mergeDefinitions();

    if (definitions.isEmpty()) {
      result.addError(String.format("Field '%s': merge definitions list is empty", fieldName));
      return;
    }

    // Validate each merge definition
    for (int i = 0; i < definitions.size(); i++) {
      FieldConfig def = definitions.get(i);
      String defName = String.format("%s[%d]", fieldName, i);
      validateField(defName, def, result);
    }

    // All definitions should have the same type (for consistency)
    String firstType = definitions.get(0).type();
    for (int i = 1; i < definitions.size(); i++) {
      String defType = definitions.get(i).type();
      if (firstType != null && !firstType.equals(defType)) {
        result.addWarning(String.format(
            "Field '%s': merge definition[%d] has type '%s' but definition[0] has type '%s'. Results may be incompatible.",
            fieldName, i, defType, firstType));
      }
    }
  }

  private void validateObjectField(String fieldName, FieldConfig config, ValidationResult.Builder result) {
    if (config.objectType() == null || config.objectType().isBlank()) {
      result.addError(String.format("Field '%s': type 'object' requires 'objectType'", fieldName));
    } else if (!typeResolver.canResolve(config.objectType())) {
      result.addError(String.format("Field '%s': objectType '%s' cannot be resolved", fieldName, config.objectType()));
    } else if (config.fields() == null || config.fields().isEmpty()) {
      result.addError(String.format(
          "Field '%s': type 'object' with objectType '%s' requires 'fields' mapping to populate the nested object",
          fieldName, config.objectType()));
    }
  }

  private void validateArrayField(String fieldName, FieldConfig config, ValidationResult.Builder result) {
    if (config.itemType() == null || config.itemType().isBlank()) {
      result.addError(String.format("Field '%s': type 'array' requires 'itemType'", fieldName));
    } else if (!isPrimitiveType(config.itemType()) && !typeResolver.canResolve(config.itemType())) {
      result.addError(String.format("Field '%s': itemType '%s' cannot be resolved", fieldName, config.itemType()));
    } else if (!isPrimitiveType(config.itemType()) && (config.fields() == null || config.fields().isEmpty())) {
      result.addError(String.format(
          "Field '%s': type 'array' with itemType '%s' requires 'fields' mapping to populate array elements",
          fieldName, config.itemType()));
    }
  }

  private void validateEnumField(String fieldName, FieldConfig config, ValidationResult.Builder result) {
    if (config.enumType() == null || config.enumType().isBlank()) {
      result.addError(String.format("Field '%s': type 'enum' requires 'enumType'", fieldName));
    } else if (!typeResolver.canResolve(config.enumType())) {
      result.addError(String.format("Field '%s': enumType '%s' cannot be resolved", fieldName, config.enumType()));
    }
  }

  private void validateMapField(String fieldName, FieldConfig config, ValidationResult.Builder result) {
    if (config.keyType() == null || config.keyType().isBlank()) {
      result.addError(String.format("Field '%s': type 'map' requires 'keyType'", fieldName));
    } else if (!STRING.equals(config.keyType())) {
      result.addError(String.format("Field '%s': map keyType '%s' is not supported. Only 'string' keys are currently supported.", fieldName, config.keyType()));
    }

    if (config.valueType() == null || config.valueType().isBlank()) {
      result.addError(String.format("Field '%s': type 'map' requires 'valueType'", fieldName));
    } else if (OBJECT.equals(config.valueType())) {
      if (config.objectType() == null || config.objectType().isBlank()) {
        result.addError(String.format("Field '%s': map with valueType 'object' requires 'objectType'", fieldName));
      } else if (!typeResolver.canResolve(config.objectType())) {
        result.addError(String.format("Field '%s': objectType '%s' cannot be resolved", fieldName, config.objectType()));
      } else if (config.fields() == null || config.fields().isEmpty()) {
        result.addError(String.format("Field '%s': map with valueType 'object' requires 'fields' mapping", fieldName));
      }
    }
  }

  /**
   * Checks if a type is a primitive type (not requiring resolution).
   *
   * @param type the type name
   * @return true if primitive
   */
  public boolean isPrimitiveType(String type) {
    return STRING.equals(type) ||
           INT32.equals(type) ||
           INT64.equals(type) ||
           FLOAT.equals(type) ||
           DOUBLE.equals(type) ||
           BOOLEAN.equals(type);
  }

  private void validateDefaultValueType(String fieldName, Object defaultValue, String type, ValidationResult.Builder result) {
    try {
      switch (type) {
        case INT32, INT64 -> {
          if (!(defaultValue instanceof Number)) {
            Long.parseLong(defaultValue.toString());
          }
        }
        case FLOAT, DOUBLE -> {
          if (!(defaultValue instanceof Number)) {
            Double.parseDouble(defaultValue.toString());
          }
        }
        case BOOLEAN -> {
          if (!(defaultValue instanceof Boolean)) {
            String val = defaultValue.toString().toLowerCase();
            if (!val.equals("true") && !val.equals("false") &&
                !val.equals("yes") && !val.equals("no") &&
                !val.equals("1") && !val.equals("0")) {
              result.addError(String.format("Field '%s': default value '%s' is not a valid boolean", fieldName, defaultValue));
            }
          }
        }
        case STRING -> {
          // Any value can be converted to string
        }
        case ARRAY, OBJECT, TIMESTAMP -> {
          result.addWarning(String.format("Field '%s': default values for type '%s' may not work as expected", fieldName, type));
        }
      }
    } catch (NumberFormatException e) {
      result.addError(String.format("Field '%s': default value '%s' is not compatible with type '%s'", fieldName, defaultValue, type));
    }
  }
}
