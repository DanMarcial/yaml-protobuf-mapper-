package io.github.yamlmapper.validation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry for Protobuf message validators.
 *
 * <p>Allows dynamic registration of validators for different message types,
 * eliminating the need for hardcoded switch statements.
 *
 * <p>Example usage:
 * <pre>{@code
 * ValidatorRegistry registry = new ValidatorRegistry();
 *
 * // Register validators
 * registry.register("UserEvent", new ProtobufMessageValidator(userEventConstraints));
 * registry.register("Product", new ProtobufMessageValidator(productConstraints));
 *
 * // Get validator for a message type
 * ProtobufMessageValidator validator = registry.get("UserEvent");
 * if (validator != null) {
 *     ValidationResult result = validator.validate(message);
 * }
 * }</pre>
 *
 * <p>This class is thread-safe.
 */
public class ValidatorRegistry {

  private final ConcurrentMap<String, ProtobufMessageValidator> validators;

  /**
   * Creates a new empty ValidatorRegistry.
   */
  public ValidatorRegistry() {
    this.validators = new ConcurrentHashMap<>();
  }

  /**
   * Registers a validator for a message type.
   *
   * <p>If a validator already exists for the type, it will be replaced.
   *
   * @param messageType the Protobuf message type name (e.g., "UserEvent")
   * @param validator the validator to register
   * @return this registry for chaining
   * @throws IllegalArgumentException if messageType or validator is null
   */
  public ValidatorRegistry register(String messageType, ProtobufMessageValidator validator) {
    if (messageType == null || messageType.isBlank()) {
      throw new IllegalArgumentException("Message type cannot be null or blank");
    }
    if (validator == null) {
      throw new IllegalArgumentException("Validator cannot be null");
    }
    validators.put(messageType, validator);
    return this;
  }

  /**
   * Gets the validator for a message type.
   *
   * @param messageType the Protobuf message type name
   * @return the validator, or null if not registered
   */
  public ProtobufMessageValidator get(String messageType) {
    if (messageType == null) {
      return null;
    }
    return validators.get(messageType);
  }

  /**
   * Checks if a validator is registered for a message type.
   *
   * @param messageType the message type to check
   * @return true if a validator is registered
   */
  public boolean hasValidator(String messageType) {
    return messageType != null && validators.containsKey(messageType);
  }

  /**
   * Removes a validator for a message type.
   *
   * @param messageType the message type
   * @return the removed validator, or null if none was registered
   */
  public ProtobufMessageValidator remove(String messageType) {
    if (messageType == null) {
      return null;
    }
    return validators.remove(messageType);
  }

  /**
   * Gets the number of registered validators.
   *
   * @return the count
   */
  public int size() {
    return validators.size();
  }

  /**
   * Removes all registered validators.
   */
  public void clear() {
    validators.clear();
  }
}
