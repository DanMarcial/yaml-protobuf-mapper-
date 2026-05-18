package io.github.yamlmapper.resolver;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.protobuf.Message;
import io.github.yamlmapper.cache.CacheFactory;
import io.github.yamlmapper.config.CacheConfig;
import io.github.yamlmapper.exception.TypeNotFoundException;
import java.util.List;

/**
 * Resolves short Protobuf type names to their full class references.
 *
 * <p>Given a list of package prefixes, this resolver tries to find a Protobuf
 * message class by concatenating each prefix with the short type name.
 *
 * <p>Example:
 * <pre>{@code
 * TypeResolver resolver = new TypeResolver(List.of(
 *     "com.google.cloud.retail.v2",
 *     "com.google.cloud.retail.v2alpha"
 * ));
 *
 * // Resolves to com.google.cloud.retail.v2.UserEvent
 * Class<?> clazz = resolver.resolve("UserEvent");
 * }</pre>
 *
 * <p>This class is thread-safe. Resolved types are cached for performance.
 */
public class TypeResolver {

  private final List<String> packagePrefixes;
  private final Cache<String, Class<?>> cache;

  /**
   * Creates a new TypeResolver with the given package prefixes.
   *
   * <p>Packages are searched in order. The first package containing
   * the requested type wins.
   *
   * @param packagePrefixes list of package prefixes to search
   * @throws IllegalArgumentException if packagePrefixes is null or empty
   */
  public TypeResolver(List<String> packagePrefixes) {
    this(packagePrefixes, CacheConfig.TYPE_CACHE);
  }

  /**
   * Creates a new TypeResolver with custom cache configuration.
   *
   * @param packagePrefixes list of package prefixes to search
   * @param cacheConfig the cache configuration
   * @throws IllegalArgumentException if packagePrefixes is null or empty
   */
  public TypeResolver(List<String> packagePrefixes, CacheConfig cacheConfig) {
    if (packagePrefixes == null || packagePrefixes.isEmpty()) {
      throw new IllegalArgumentException("At least one package prefix is required");
    }
    this.packagePrefixes = List.copyOf(packagePrefixes);
    this.cache = CacheFactory.create(cacheConfig);
  }

  /**
   * Resolves a short type name to its full class.
   *
   * <p>The type name can be:
   * <ul>
   *   <li>A simple name: "UserEvent" → searches in all packages</li>
   *   <li>A nested type: "UserEvent.ProductDetail" → searches for nested class</li>
   *   <li>A fully qualified name: "com.example.MyType" → uses as-is</li>
   * </ul>
   *
   * @param typeName the short type name (e.g., "UserEvent")
   * @return the resolved Class
   * @throws TypeNotFoundException if the type cannot be found in any package
   */
  public Class<?> resolve(String typeName) {
    if (typeName == null || typeName.isBlank()) {
      throw new IllegalArgumentException("Type name cannot be null or blank");
    }

    // Check cache first
    Class<?> cached = cache.getIfPresent(typeName);
    if (cached != null) {
      return cached;
    }

    // If already fully qualified (contains package separator), try direct load
    if (typeName.contains(".") && Character.isLowerCase(typeName.charAt(0))) {
      Class<?> resolved = tryLoadClass(typeName);
      if (resolved != null) {
        cache.put(typeName, resolved);
        return resolved;
      }
    }

    // Try each package prefix
    Class<?> resolved = tryResolveInPackages(typeName);
    if (resolved != null) {
      cache.put(typeName, resolved);
      return resolved;
    }

    // Not found
    throw new TypeNotFoundException(typeName, buildNotFoundMessage(typeName));
  }

  /**
   * Resolves a type name and verifies it's a Protobuf Message type.
   *
   * @param typeName the short type name
   * @return the resolved Class, guaranteed to be a Message subclass
   * @throws TypeNotFoundException if not found or not a Message type
   */
  @SuppressWarnings("unchecked")
  public Class<? extends Message> resolveMessage(String typeName) {
    Class<?> clazz = resolve(typeName);

    if (!Message.class.isAssignableFrom(clazz)) {
      throw new TypeNotFoundException(typeName,
          String.format("Type '%s' (%s) is not a Protobuf Message", typeName, clazz.getName()));
    }

    return (Class<? extends Message>) clazz;
  }

  /**
   * Checks if a type can be resolved without throwing an exception.
   *
   * @param typeName the type name to check
   * @return true if the type can be resolved
   */
  public boolean canResolve(String typeName) {
    if (typeName == null || typeName.isBlank()) {
      return false;
    }

    if (cache.getIfPresent(typeName) != null) {
      return true;
    }

    try {
      resolve(typeName);
      return true;
    } catch (TypeNotFoundException e) {
      return false;
    }
  }

  /**
   * Gets the configured package prefixes.
   *
   * @return immutable list of package prefixes
   */
  public List<String> getPackagePrefixes() {
    return packagePrefixes;
  }

  private Class<?> tryResolveInPackages(String typeName) {
    // Handle nested types: "UserEvent.ProductDetail" -> "UserEvent$ProductDetail"
    String classNameSuffix = typeName.replace('.', '$');

    for (String prefix : packagePrefixes) {
      String fullClassName = prefix + "." + classNameSuffix;
      Class<?> clazz = tryLoadClass(fullClassName);
      if (clazz != null) {
        return clazz;
      }
    }

    return null;
  }

  private Class<?> tryLoadClass(String fullClassName) {
    try {
      return Class.forName(fullClassName);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private String buildNotFoundMessage(String typeName) {
    StringBuilder sb = new StringBuilder();
    sb.append("Type '").append(typeName).append("' not found in packages: [");

    for (int i = 0; i < packagePrefixes.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(packagePrefixes.get(i));
    }
    sb.append("]");

    return sb.toString();
  }
}
