package io.github.yamlmapper.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.yamlmapper.config.CacheConfig;

/**
 * Factory for creating Caffeine caches with consistent configuration.
 *
 * <p>Provides a simple API to create caches based on {@link CacheConfig}
 * settings, ensuring consistent behavior across the application.
 *
 * <p>Example usage:
 * <pre>{@code
 * Cache<String, String[]> pathCache = CacheFactory.create(CacheConfig.PATH_CACHE);
 * Cache<String, Class<?>> typeCache = CacheFactory.create(CacheConfig.TYPE_CACHE);
 * }</pre>
 *
 * <p>This class is stateless and thread-safe.
 */
public final class CacheFactory {

  private CacheFactory() {
    // Utility class
  }

  /**
   * Creates a new Caffeine cache with the given configuration.
   *
   * @param config the cache configuration
   * @param <K> the key type
   * @param <V> the value type
   * @return a new Caffeine cache
   */
  public static <K, V> Cache<K, V> create(CacheConfig config) {
    Caffeine<Object, Object> builder = Caffeine.newBuilder()
        .maximumSize(config.maximumSize());

    if (config.expireAfterAccess() != null) {
      builder.expireAfterAccess(config.expireAfterAccess());
    }

    if (config.expireAfterWrite() != null) {
      builder.expireAfterWrite(config.expireAfterWrite());
    }

    if (config.recordStats()) {
      builder.recordStats();
    }

    return builder.build();
  }

  /**
   * Creates a cache with default configuration.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @return a new cache with default settings
   */
  public static <K, V> Cache<K, V> createDefault() {
    return create(CacheConfig.DEFAULT);
  }

  /**
   * Creates a cache optimized for path resolution.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @return a new path-optimized cache
   */
  public static <K, V> Cache<K, V> createPathCache() {
    return create(CacheConfig.PATH_CACHE);
  }

  /**
   * Creates a cache optimized for type resolution.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @return a new type-optimized cache
   */
  public static <K, V> Cache<K, V> createTypeCache() {
    return create(CacheConfig.TYPE_CACHE);
  }
}
