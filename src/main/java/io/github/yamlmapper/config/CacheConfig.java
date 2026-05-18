package io.github.yamlmapper.config;

import java.time.Duration;

/**
 * Configuration for Caffeine caches used throughout the mapper.
 *
 * <p>Provides sensible defaults with the ability to customize cache behavior
 * for different use cases (path resolution, type lookups, etc.).
 *
 * <p>Example usage:
 * <pre>{@code
 * CacheConfig config = CacheConfig.builder()
 *     .maximumSize(5000)
 *     .expireAfterAccess(Duration.ofMinutes(30))
 *     .build();
 * }</pre>
 *
 * <p>This class is immutable and thread-safe.
 */
public record CacheConfig(
    long maximumSize,
    Duration expireAfterAccess,
    Duration expireAfterWrite,
    boolean recordStats
) {

  /**
   * Default cache configuration.
   * - Maximum 10,000 entries
   * - No time-based expiration (entries live until evicted by size)
   * - Stats recording disabled for production performance
   */
  public static final CacheConfig DEFAULT = new CacheConfig(
      10_000,
      null,
      null,
      false
  );

  /**
   * Cache configuration optimized for path resolution.
   * - Maximum 5,000 entries (paths are typically reused)
   * - Expire after 1 hour of no access
   */
  public static final CacheConfig PATH_CACHE = new CacheConfig(
      5_000,
      Duration.ofHours(1),
      null,
      false
  );

  /**
   * Cache configuration for type resolution.
   * - Maximum 1,000 entries (limited number of Protobuf types)
   * - No expiration (types don't change at runtime)
   */
  public static final CacheConfig TYPE_CACHE = new CacheConfig(
      1_000,
      null,
      null,
      false
  );

  /**
   * Cache configuration for development/debugging.
   * - Stats recording enabled
   * - Smaller size for faster iteration
   */
  public static final CacheConfig DEBUG = new CacheConfig(
      1_000,
      Duration.ofMinutes(5),
      null,
      true
  );

  /**
   * Creates a new builder for CacheConfig.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for CacheConfig.
   */
  public static class Builder {
    private long maximumSize = 10_000;
    private Duration expireAfterAccess = null;
    private Duration expireAfterWrite = null;
    private boolean recordStats = false;

    private Builder() {}

    /**
     * Sets the maximum number of entries in the cache.
     *
     * @param maximumSize maximum entries (must be positive)
     * @return this builder
     */
    public Builder maximumSize(long maximumSize) {
      if (maximumSize <= 0) {
        throw new IllegalArgumentException("Maximum size must be positive");
      }
      this.maximumSize = maximumSize;
      return this;
    }

    /**
     * Sets the duration after which entries expire if not accessed.
     *
     * @param duration the expiration duration, or null to disable
     * @return this builder
     */
    public Builder expireAfterAccess(Duration duration) {
      this.expireAfterAccess = duration;
      return this;
    }

    /**
     * Sets the duration after which entries expire after being written.
     *
     * @param duration the expiration duration, or null to disable
     * @return this builder
     */
    public Builder expireAfterWrite(Duration duration) {
      this.expireAfterWrite = duration;
      return this;
    }

    /**
     * Enables cache statistics recording.
     *
     * <p>Useful for debugging and tuning, but has a small performance overhead.
     *
     * @param recordStats true to enable stats
     * @return this builder
     */
    public Builder recordStats(boolean recordStats) {
      this.recordStats = recordStats;
      return this;
    }

    /**
     * Builds the CacheConfig.
     *
     * @return the configured CacheConfig
     */
    public CacheConfig build() {
      return new CacheConfig(maximumSize, expireAfterAccess, expireAfterWrite, recordStats);
    }
  }
}
