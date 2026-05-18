package io.github.yamlmapper.validation;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import io.github.yamlmapper.config.CaseConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates Protobuf messages POST-mapping against schema constraints.
 *
 * <p>This validator checks the built Protobuf message against constraints
 * defined in the JSON Schema (from Google Cloud Retail API spec):
 * <ul>
 *   <li>Always required fields must be present</li>
 *   <li>Conditional required fields by eventType</li>
 *   <li>String maxLength constraints</li>
 *   <li>Numeric range constraints (minimum/maximum)</li>
 *   <li>Enum value validation</li>
 *   <li>Nested object validation (recursive)</li>
 * </ul>
 *
 * <p>This class is thread-safe.
 */
public class ProtobufMessageValidator {

  private static final Logger log = LoggerFactory.getLogger(ProtobufMessageValidator.class);

  private final ProtobufConstraints constraints;

  /**
   * Creates a validator with the specified constraints.
   *
   * @param constraints the schema constraints to validate against
   */
  public ProtobufMessageValidator(ProtobufConstraints constraints) {
    this.constraints = constraints;
  }

  /**
   * Validates a Protobuf message against schema constraints.
   *
   * @param message the message to validate
   * @return validation result with any errors or warnings
   */
  public ValidationResult validate(Message message) {
    if (message == null) {
      return ValidationResult.invalid(List.of("Message cannot be null"));
    }

    ValidationResult.Builder result = ValidationResult.builder();
    String messageType = message.getDescriptorForType().getName();

    log.debug("Validating {} message", messageType);

    // Validate always-required fields
    validateRequiredFields(message, "", result);

    // Validate conditional required fields (for UserEvent based on eventType)
    if ("UserEvent".equals(messageType)) {
      validateConditionalRequired(message, result);
    }

    // Validate field constraints (maxLength, ranges, enums)
    validateFieldConstraints(message, "", result);

    return result.build();
  }

  /**
   * Validates that always-required fields are present.
   */
  private void validateRequiredFields(Message message, String prefix, ValidationResult.Builder result) {
    List<String> alwaysRequired = constraints.getAlwaysRequired();
    String messageType = message.getDescriptorForType().getName();

    for (String requiredField : alwaysRequired) {
      String fieldToCheck;
      String expectedPrefix;

      // Handle nested type required fields (e.g., "ProductDetail.product")
      if (requiredField.contains(".")) {
        String[] parts = requiredField.split("\\.", 2);
        expectedPrefix = parts[0];
        fieldToCheck = parts[1];

        // Only validate if we're in the right nested context
        if (!prefix.isEmpty() && prefix.equals(expectedPrefix + ".")) {
          if (!hasFieldValue(message, fieldToCheck)) {
            result.addError(String.format("Required field '%s' is missing in %s",
                fieldToCheck, expectedPrefix));
          }
        }
      } else {
        // Root-level required field
        if (prefix.isEmpty() && !hasFieldValue(message, requiredField)) {
          result.addError(String.format("Required field '%s' is missing", requiredField));
        }
      }
    }

    // Recursively validate nested messages
    for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
      if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE && hasFieldPresent(message, field)) {
        Object value = message.getField(field);
        if (field.isRepeated()) {
          @SuppressWarnings("unchecked")
          List<Message> list = (List<Message>) value;
          for (Message nested : list) {
            String nestedType = nested.getDescriptorForType().getName();
            validateRequiredFields(nested, nestedType + ".", result);
          }
        } else {
          Message nested = (Message) value;
          String nestedType = nested.getDescriptorForType().getName();
          validateRequiredFields(nested, nestedType + ".", result);
        }
      }
    }
  }

  /**
   * Validates conditional required fields based on eventType.
   */
  private void validateConditionalRequired(Message message, ValidationResult.Builder result) {
    // Get eventType value
    String eventType = getStringFieldValue(message, "eventType");
    if (eventType == null || eventType.isEmpty()) {
      return; // Can't validate conditional without eventType
    }

    List<ProtobufConstraints.ConditionalRequired> rules =
        constraints.getRequiredForEventType(eventType);

    for (ProtobufConstraints.ConditionalRequired rule : rules) {
      if (rule.isOrCondition()) {
        // At least one of the fields must be present
        boolean anyPresent = rule.requiredFields().stream()
            .anyMatch(field -> hasFieldValue(message, field));
        if (!anyPresent) {
          result.addError(String.format(
              "For eventType '%s', at least one of these fields is required: %s",
              eventType, String.join(" or ", rule.requiredFields())));
        }
      } else {
        // All fields must be present
        for (String field : rule.requiredFields()) {
          if (!hasFieldValue(message, field)) {
            result.addError(String.format(
                "For eventType '%s', field '%s' is required",
                eventType, field));
          }
        }
      }
    }
  }

  /**
   * Validates field constraints (maxLength, ranges, enums).
   */
  private void validateFieldConstraints(Message message, String prefix,
      ValidationResult.Builder result) {

    for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
      // Convert snake_case field name to camelCase for constraint lookup
      String protoFieldName = field.getName();
      String constraintFieldName = CaseConverter.snakeToCamel(protoFieldName);
      String fieldPath = prefix + constraintFieldName;
      Object value = message.getField(field);

      // Skip if field is not set
      if (!hasFieldPresent(message, field)) {
        continue;
      }

      switch (field.getJavaType()) {
        case STRING -> {
          if (field.isRepeated()) {
            // Repeated strings - validate each item
            int count = message.getRepeatedFieldCount(field);
            for (int i = 0; i < count; i++) {
              String item = (String) message.getRepeatedField(field, i);
              validateStringField(fieldPath + "[" + i + "]", item, result);
            }
          } else {
            validateStringField(fieldPath, (String) value, result);
          }
        }
        case INT, LONG -> {
          if (!field.isRepeated()) {
            validateNumericField(fieldPath, (Number) value, result);
          }
        }
        case MESSAGE -> {
          if (field.isRepeated()) {
            int count = message.getRepeatedFieldCount(field);
            for (int i = 0; i < count; i++) {
              Message nested = (Message) message.getRepeatedField(field, i);
              String nestedType = nested.getDescriptorForType().getName();
              validateFieldConstraints(nested, nestedType + ".", result);
            }
          } else {
            Message nested = (Message) value;
            String nestedType = nested.getDescriptorForType().getName();
            validateFieldConstraints(nested, nestedType + ".", result);
          }
        }
        case ENUM -> validateEnumField(fieldPath, value.toString(), result);
        default -> { /* No validation for other types */ }
      }
    }
  }

  /**
   * Validates a string field against maxLength constraint.
   */
  private void validateStringField(String fieldPath, String value,
      ValidationResult.Builder result) {
    if (value == null || value.isEmpty()) {
      return;
    }

    Optional<Integer> maxLength = constraints.getMaxLength(fieldPath);
    if (maxLength.isPresent() && value.length() > maxLength.get()) {
      result.addError(String.format(
          "Field '%s' exceeds maxLength: %d > %d",
          fieldPath, value.length(), maxLength.get()));
    }

    // Also check enum values for string fields
    Optional<List<String>> enumValues = constraints.getEnumValues(fieldPath);
    if (enumValues.isPresent() && !enumValues.get().contains(value)) {
      result.addError(String.format(
          "Field '%s' has invalid value '%s'. Valid values: %s",
          fieldPath, value, enumValues.get()));
    }
  }

  /**
   * Validates a numeric field against range constraints.
   */
  private void validateNumericField(String fieldPath, Number value,
      ValidationResult.Builder result) {
    if (value == null) {
      return;
    }

    Optional<ProtobufConstraints.Range> range = constraints.getRange(fieldPath);
    if (range.isPresent() && !range.get().isValid(value)) {
      ProtobufConstraints.Range r = range.get();
      String rangeDesc;
      if (r.minimum() != null && r.maximum() != null) {
        rangeDesc = String.format("between %d and %d", r.minimum(), r.maximum());
      } else if (r.minimum() != null) {
        rangeDesc = String.format(">= %d", r.minimum());
      } else {
        rangeDesc = String.format("<= %d", r.maximum());
      }
      result.addError(String.format(
          "Field '%s' value %s is out of range (must be %s)",
          fieldPath, value, rangeDesc));
    }
  }

  /**
   * Validates an enum field against allowed values.
   * Skips validation for UNSPECIFIED values (Protobuf default).
   */
  private void validateEnumField(String fieldPath, String value,
      ValidationResult.Builder result) {
    // Skip UNSPECIFIED values - they're Protobuf defaults, not user-set values
    if (value == null || value.endsWith("_UNSPECIFIED") || value.equals("UNSPECIFIED")) {
      return;
    }

    Optional<List<String>> enumValues = constraints.getEnumValues(fieldPath);
    if (enumValues.isPresent() && !enumValues.get().contains(value)) {
      result.addError(String.format(
          "Field '%s' has invalid enum value '%s'. Valid values: %s",
          fieldPath, value, enumValues.get()));
    }
  }

  /**
   * Checks if a field has a meaningful value set in the message (by field name).
   * For strings, checks non-empty. For messages, checks hasField. For repeated, checks count.
   * Supports both camelCase (schema) and snake_case (protobuf) field names.
   */
  private boolean hasFieldValue(Message message, String fieldName) {
    FieldDescriptor field = findField(message, fieldName);
    if (field == null) {
      return false;
    }
    return hasFieldPresent(message, field);
  }

  /**
   * Finds a field by name, trying both camelCase and snake_case variants.
   */
  private FieldDescriptor findField(Message message, String fieldName) {
    // Try original name first
    FieldDescriptor field = message.getDescriptorForType().findFieldByName(fieldName);
    if (field != null) {
      return field;
    }
    // Try converting camelCase to snake_case
    String snakeCase = CaseConverter.camelToSnake(fieldName);
    return message.getDescriptorForType().findFieldByName(snakeCase);
  }

  /**
   * Checks if a field has a meaningful value set in the message (by descriptor).
   * Handles Proto3 semantics where scalar fields always have default values.
   */
  private boolean hasFieldPresent(Message message, FieldDescriptor field) {
    if (field.isRepeated()) {
      return message.getRepeatedFieldCount(field) > 0;
    }

    // For message types, use hasField
    if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
      return message.hasField(field);
    }

    // For Proto3 scalar types, check if value is non-default
    Object value = message.getField(field);

    return switch (field.getJavaType()) {
      case STRING -> value != null && !((String) value).isEmpty();
      case INT, LONG -> ((Number) value).longValue() != 0;
      case FLOAT, DOUBLE -> ((Number) value).doubleValue() != 0.0;
      case BOOLEAN -> (Boolean) value;
      case ENUM -> {
        // Enum value 0 is considered "unset" in many conventions
        // But for eventType validation, any enum value is valid
        yield true;
      }
      case BYTE_STRING -> value != null && !((com.google.protobuf.ByteString) value).isEmpty();
      default -> value != null;
    };
  }

  /**
   * Gets a string field value from the message.
   * Supports both camelCase and snake_case field names.
   */
  private String getStringFieldValue(Message message, String fieldName) {
    FieldDescriptor field = findField(message, fieldName);
    if (field == null) {
      return null;
    }

    Object value = message.getField(field);
    if (value == null) {
      return null;
    }

    // Handle string values
    if (value instanceof String s) {
      return s.isEmpty() ? null : s;
    }

    // Handle enum values
    if (value instanceof Enum<?>) {
      return value.toString();
    }

    return value.toString();
  }

  /**
   * Gets the constraints used by this validator.
   */
  public ProtobufConstraints getConstraints() {
    return constraints;
  }
}
