package io.github.yamlmapper.builder;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.protobuf.Message;
import io.github.yamlmapper.cache.CacheFactory;
import io.github.yamlmapper.config.CacheConfig;
import io.github.yamlmapper.exception.MappingException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Factory for creating Protobuf message builders using MethodHandles.
 *
 * <p>This factory caches MethodHandles for the {@code newBuilder()} static method
 * of each Protobuf message class. MethodHandles provide near-native performance
 * (~3ns per invocation) compared to reflection (~15ns).
 *
 * <p>Example usage:
 * <pre>{@code
 * BuilderFactory factory = new BuilderFactory();
 * Message.Builder builder = factory.createBuilder(UserEvent.class);
 * }</pre>
 *
 * <p>This class is thread-safe. MethodHandles are cached after first use.
 */
public class BuilderFactory {

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

  private final Cache<Class<?>, MethodHandle> builderHandles;

  /**
   * Creates a new BuilderFactory.
   */
  public BuilderFactory() {
    this.builderHandles = CacheFactory.create(CacheConfig.BUILDER_CACHE);
  }

  /**
   * Creates a new builder for the given Protobuf message class.
   *
   * <p>The class must be a Protobuf-generated message class with a static
   * {@code newBuilder()} method.
   *
   * @param messageClass the Protobuf message class
   * @param <T> the message type
   * @return a new builder instance
   * @throws MappingException if the builder cannot be created
   */
  @SuppressWarnings("unchecked")
  public <T extends Message> Message.Builder createBuilder(Class<T> messageClass) {
    if (messageClass == null) {
      throw new IllegalArgumentException("Message class cannot be null");
    }

    try {
      MethodHandle handle = getOrCreateHandle(messageClass);
      return (Message.Builder) handle.invoke();
    } catch (Throwable t) {
      throw new MappingException(
          String.format("Failed to create builder for %s: %s",
              messageClass.getName(), t.getMessage()),
          t);
    }
  }

  /**
   * Creates a new builder for the given Protobuf message class (raw Class version).
   *
   * <p>Use this when you have a Class<?> that you know is a Protobuf message.
   *
   * @param messageClass the Protobuf message class
   * @return a new builder instance
   * @throws MappingException if the builder cannot be created
   */
  public Message.Builder createBuilderFrom(Class<?> messageClass) {
    if (messageClass == null) {
      throw new IllegalArgumentException("Message class cannot be null");
    }

    if (!Message.class.isAssignableFrom(messageClass)) {
      throw new MappingException(
          String.format("Class %s is not a Protobuf Message type", messageClass.getName()));
    }

    try {
      MethodHandle handle = getOrCreateHandle(messageClass);
      return (Message.Builder) handle.invoke();
    } catch (Throwable t) {
      throw new MappingException(
          String.format("Failed to create builder for %s: %s",
              messageClass.getName(), t.getMessage()),
          t);
    }
  }

  private MethodHandle getOrCreateHandle(Class<?> messageClass) {
    return builderHandles.get(messageClass, this::createHandle);
  }

  private MethodHandle createHandle(Class<?> messageClass) {
    try {
      // Protobuf generates: public static Builder newBuilder()
      MethodType methodType = MethodType.methodType(
          getBuilderClass(messageClass));

      return LOOKUP.findStatic(messageClass, "newBuilder", methodType);

    } catch (NoSuchMethodException e) {
      throw new MappingException(
          String.format("Class %s does not have a newBuilder() method. " +
              "Is it a valid Protobuf message class?", messageClass.getName()),
          e);
    } catch (IllegalAccessException e) {
      throw new MappingException(
          String.format("Cannot access newBuilder() method in %s", messageClass.getName()),
          e);
    }
  }

  private Class<?> getBuilderClass(Class<?> messageClass) {
    // Protobuf Builder is always a nested class named "Builder"
    for (Class<?> inner : messageClass.getDeclaredClasses()) {
      if (inner.getSimpleName().equals("Builder")
          && Message.Builder.class.isAssignableFrom(inner)) {
        return inner;
      }
    }

    throw new MappingException(
        String.format("Could not find Builder class in %s", messageClass.getName()));
  }
}
