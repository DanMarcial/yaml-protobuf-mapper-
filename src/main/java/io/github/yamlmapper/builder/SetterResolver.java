package io.github.yamlmapper.builder;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import io.github.yamlmapper.cache.CacheFactory;
import io.github.yamlmapper.config.CacheConfig;
import io.github.yamlmapper.config.CaseConverter;
import io.github.yamlmapper.exception.MappingException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * Resolves and caches MethodHandles for Protobuf builder setter methods.
 *
 * <p>This resolver automatically detects whether a field is singular or repeated
 * and uses the appropriate setter method:
 * <ul>
 *   <li>Singular fields: {@code setFieldName(value)}</li>
 *   <li>Repeated fields: {@code addAllFieldName(collection)}</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * SetterResolver resolver = new SetterResolver();
 * UserEvent.Builder builder = UserEvent.newBuilder();
 *
 * // Singular field
 * resolver.setValue(builder, "visitorId", "abc-123");
 *
 * // Repeated field
 * resolver.setValue(builder, "pageCategories", List.of("Electronics", "Computers"));
 * }</pre>
 *
 * <p>This class is thread-safe. MethodHandles are cached after first use.
 */
public class SetterResolver {

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

  // Cache key: "com.google.cloud.retail.v2.UserEvent$Builder#visitorId"
  private final Cache<String, SetterInfo> setterCache;

  /**
   * Creates a new SetterResolver.
   */
  public SetterResolver() {
    this.setterCache = CacheFactory.create(CacheConfig.SETTER_CACHE);
  }

  /**
   * Sets a value on a builder using the appropriate setter method.
   *
   * <p>Automatically handles:
   * <ul>
   *   <li>Singular fields via setX()</li>
   *   <li>Repeated fields via addAllX() when value is a Collection</li>
   *   <li>Repeated fields via addX() when value is a single item</li>
   * </ul>
   *
   * @param builder the Protobuf builder
   * @param fieldName the field name (camelCase, as in proto definition)
   * @param value the value to set
   * @throws MappingException if the setter cannot be found or invoked
   */
  public void setValue(final Message.Builder builder, final String fieldName, final Object value) {
    if (builder == null) {
      throw new IllegalArgumentException("Builder cannot be null");
    }
    if (fieldName == null || fieldName.isBlank()) {
      throw new IllegalArgumentException("Field name cannot be null or blank");
    }
    if (value == null) {
      // Protobuf doesn't allow null values; skip silently
      return;
    }

    try {
      // Handle Map values separately (Protobuf map fields use putAll)
      if (value instanceof Map<?, ?> mapValue) {
        SetterInfo mapInfo = getOrCreateMapSetterInfo(builder, fieldName);
        mapInfo.handle.invoke(builder, mapValue);
        return;
      }

      SetterInfo info = getOrCreateSetterInfo(builder, fieldName);

      // Adapt protobuf wrapper types automatically
      Object adaptedValue = adaptProtobufWrapper(info.parameterType, value);

      if (info.isRepeated && adaptedValue instanceof Collection<?> collection) {
        // Use addAll for collections on repeated fields
        info.handle.invoke(builder, collection);
      } else if (info.isRepeated && !(adaptedValue instanceof Collection<?>)) {
        // Single value for repeated field - need to use add() instead
        SetterInfo addInfo = getOrCreateAdderInfo(builder, fieldName);
        addInfo.handle.invoke(builder, adaptedValue);
      } else {
        // Singular field
        info.handle.invoke(builder, adaptedValue);
      }
    } catch (Throwable t) {
      throw new MappingException(
          String.format("Failed to set field '%s' on %s: %s",
              fieldName, builder.getClass().getSimpleName(), t.getMessage()),
          t);
    }
  }

  private SetterInfo getOrCreateSetterInfo(final Message.Builder builder, final String fieldName) {
    String cacheKey = buildCacheKey(builder.getClass(), fieldName);
    return setterCache.get(cacheKey, k -> createSetterInfo(builder, fieldName));
  }

  private SetterInfo getOrCreateAdderInfo(final Message.Builder builder, final String fieldName) {
    String cacheKey = buildCacheKey(builder.getClass(), fieldName) + "#add";
    return setterCache.get(cacheKey, k -> createAdderInfo(builder, fieldName));
  }

  private SetterInfo getOrCreateMapSetterInfo(final Message.Builder builder, final String fieldName) {
    String cacheKey = buildCacheKey(builder.getClass(), fieldName) + "#putAll";
    return setterCache.get(cacheKey, k -> createMapSetterInfo(builder, fieldName));
  }

  private String buildCacheKey(final Class<?> builderClass, final String fieldName) {
    return builderClass.getName() + "#" + fieldName;
  }

  private SetterInfo createSetterInfo(final Message.Builder builder, final String fieldName) {
    // Check if field is repeated using Protobuf descriptor
    FieldDescriptor fd = builder.getDescriptorForType().findFieldByName(fieldName);

    if (fd == null) {
      // Try converting camelCase to snake_case for proto field names
      String snakeCaseName = CaseConverter.camelToSnake(fieldName);
      fd = builder.getDescriptorForType().findFieldByName(snakeCaseName);
    }

    if (fd == null) {
      throw new MappingException(
          String.format("Field '%s' not found in %s",
              fieldName, builder.getDescriptorForType().getFullName()));
    }

    boolean isRepeated = fd.isRepeated();
    String methodName = isRepeated
        ? "addAll" + capitalize(fieldName)
        : "set" + capitalize(fieldName);

    try {
      Method method = findMethod(builder.getClass(), methodName);
      if (method == null) {
        throw new MappingException(
            String.format("Method '%s' not found in %s",
                methodName, builder.getClass().getSimpleName()));
      }

      MethodHandle handle = LOOKUP.unreflect(method);
      Class<?> paramType = method.getParameterTypes()[0];

      return new SetterInfo(handle, paramType, isRepeated);

    } catch (IllegalAccessException e) {
      throw new MappingException(
          String.format("Cannot access method '%s' in %s",
              methodName, builder.getClass().getSimpleName()),
          e);
    }
  }

  private SetterInfo createAdderInfo(final Message.Builder builder, final String fieldName) {
    String methodName = "add" + capitalize(fieldName);

    try {
      Method method = findMethod(builder.getClass(), methodName);
      if (method == null) {
        throw new MappingException(
            String.format("Method '%s' not found in %s",
                methodName, builder.getClass().getSimpleName()));
      }

      MethodHandle handle = LOOKUP.unreflect(method);
      Class<?> paramType = method.getParameterTypes()[0];

      return new SetterInfo(handle, paramType, true);

    } catch (IllegalAccessException e) {
      throw new MappingException(
          String.format("Cannot access method '%s' in %s",
              methodName, builder.getClass().getSimpleName()),
          e);
    }
  }

  private SetterInfo createMapSetterInfo(final Message.Builder builder, final String fieldName) {
    String methodName = "putAll" + capitalize(fieldName);

    try {
      Method method = findMethod(builder.getClass(), methodName);
      if (method == null) {
        throw new MappingException(
            String.format("Map method '%s' not found in %s",
                methodName, builder.getClass().getSimpleName()));
      }

      MethodHandle handle = LOOKUP.unreflect(method);
      Class<?> paramType = method.getParameterTypes()[0];

      return new SetterInfo(handle, paramType, false);

    } catch (IllegalAccessException e) {
      throw new MappingException(
          String.format("Cannot access method '%s' in %s",
              methodName, builder.getClass().getSimpleName()),
          e);
    }
  }

  private Method findMethod(final Class<?> clazz, final String methodName) {
    Method bestMatch = null;

    for (Method method : clazz.getMethods()) {
      if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
        Class<?> paramType = method.getParameterTypes()[0];

        // Prefer methods that take the Message type over Builder type
        // E.g., prefer setUserInfo(UserInfo) over setUserInfo(UserInfo.Builder)
        if (bestMatch == null) {
          bestMatch = method;
        } else if (!Message.Builder.class.isAssignableFrom(paramType)) {
          // This method takes a non-Builder type, prefer it
          bestMatch = method;
        }
      }
    }
    return bestMatch;
  }

  private String capitalize(final String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private Object adaptProtobufWrapper(final Class<?> targetType, final Object value) {

    if (value == null) {
      return null;
    }

    // Already correct type
    if (targetType.isInstance(value)) {
      return value;
    }

    // Int32Value
    if (targetType == Int32Value.class) {
      return Int32Value.of(((Number) value).intValue());
    }

    // Int64Value
    if (targetType == Int64Value.class) {
      return Int64Value.of(((Number) value).longValue());
    }

    // FloatValue
    if (targetType == FloatValue.class) {
      return FloatValue.of(((Number) value).floatValue());
    }

    // DoubleValue
    if (targetType == DoubleValue.class) {
      return DoubleValue.of(((Number) value).doubleValue());
    }

    // BoolValue
    if (targetType == BoolValue.class) {
      return BoolValue.of((Boolean) value);
    }

    // StringValue
    if (targetType == StringValue.class) {
      return StringValue.of(String.valueOf(value));
    }

    return value;
  }

  /**
   * Gets the oneof information for a field, if it belongs to a oneof.
   *
   * @param builder the Protobuf builder
   * @param fieldName the field name (camelCase)
   * @return OneofInfo if field belongs to a oneof, null otherwise
   */
  public OneofInfo getOneofInfo(final Message.Builder builder, final String fieldName) {
    FieldDescriptor fd = findFieldDescriptor(builder, fieldName);
    if (fd == null) {
      return null;
    }

    OneofDescriptor oneof = fd.getContainingOneof();
    if (oneof == null) {
      return null;
    }

    return new OneofInfo(oneof.getName(), fd.getName());
  }

  /**
   * Finds the FieldDescriptor for a field name.
   */
  private FieldDescriptor findFieldDescriptor(final Message.Builder builder, final String fieldName) {
    FieldDescriptor fd = builder.getDescriptorForType().findFieldByName(fieldName);
    if (fd == null) {
      String snakeCaseName = CaseConverter.camelToSnake(fieldName);
      fd = builder.getDescriptorForType().findFieldByName(snakeCaseName);
    }
    return fd;
  }

  /**
   * Information about a oneof field.
   *
   * @param oneofName the name of the oneof group
   * @param fieldName the name of the field within the oneof
   */
  public record OneofInfo(String oneofName, String fieldName) {}

  /**
   * Holds cached setter information.
   */
  private record SetterInfo(
      MethodHandle handle,
      Class<?> parameterType,
      boolean isRepeated
  ) {}
}
