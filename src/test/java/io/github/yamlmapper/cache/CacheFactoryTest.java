package io.github.yamlmapper.cache;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.yamlmapper.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CacheFactory")
class CacheFactoryTest {

  @Nested
  @DisplayName("Cache Creation")
  class CacheCreation {

    @Test
    @DisplayName("should create cache with default config")
    void shouldCreateCacheWithDefaultConfig() {
      Cache<String, String> cache = CacheFactory.create(CacheConfig.DEFAULT);

      assertNotNull(cache);

      // Verify cache works
      cache.put("key", "value");
      assertEquals("value", cache.getIfPresent("key"));
    }

    @Test
    @DisplayName("should create cache with custom config")
    void shouldCreateCacheWithCustomConfig() {
      CacheConfig config = CacheConfig.builder()
          .maximumSize(100)
          .expireAfterAccess(Duration.ofMinutes(5))
          .recordStats(true)
          .build();

      Cache<String, Integer> cache = CacheFactory.create(config);

      assertNotNull(cache);
      cache.put("count", 42);
      assertEquals(42, cache.getIfPresent("count"));
    }

    @Test
    @DisplayName("should create path cache")
    void shouldCreatePathCache() {
      Cache<String, String[]> cache = CacheFactory.create(CacheConfig.PATH_CACHE);

      assertNotNull(cache);

      String[] segments = {"user", "address", "city"};
      cache.put("user.address.city", segments);
      assertArrayEquals(segments, cache.getIfPresent("user.address.city"));
    }

    @Test
    @DisplayName("should create type cache")
    void shouldCreateTypeCache() {
      Cache<String, Class<?>> cache = CacheFactory.create(CacheConfig.TYPE_CACHE);

      assertNotNull(cache);

      cache.put("String", String.class);
      assertEquals(String.class, cache.getIfPresent("String"));
    }
  }

  @Nested
  @DisplayName("Cache Behavior")
  class CacheBehavior {

    @Test
    @DisplayName("should respect maximum size")
    void shouldRespectMaximumSize() {
      CacheConfig config = CacheConfig.builder()
          .maximumSize(3)
          .build();

      Cache<Integer, String> cache = CacheFactory.create(config);

      // Add 5 entries
      for (int i = 0; i < 5; i++) {
        cache.put(i, "value" + i);
      }

      // Force cleanup
      cache.cleanUp();

      // Size should be at most 3
      assertTrue(cache.estimatedSize() <= 3);
    }

    @Test
    @DisplayName("should handle concurrent access")
    void shouldHandleConcurrentAccess() throws InterruptedException {
      Cache<Integer, Integer> cache = CacheFactory.create(CacheConfig.DEFAULT);

      Thread[] threads = new Thread[10];
      for (int i = 0; i < 10; i++) {
        final int index = i;
        threads[i] = new Thread(() -> {
          for (int j = 0; j < 100; j++) {
            cache.put(index * 100 + j, j);
          }
        });
      }

      for (Thread t : threads) t.start();
      for (Thread t : threads) t.join();

      // All entries should be accessible
      assertEquals(0, cache.getIfPresent(0));
      assertEquals(99, cache.getIfPresent(99));
    }
  }

  @Nested
  @DisplayName("CacheConfig Builder")
  class CacheConfigBuilder {

    @Test
    @DisplayName("should create config with all options")
    void shouldCreateConfigWithAllOptions() {
      CacheConfig config = CacheConfig.builder()
          .maximumSize(5000)
          .expireAfterAccess(Duration.ofMinutes(30))
          .expireAfterWrite(Duration.ofHours(1))
          .recordStats(true)
          .build();

      assertEquals(5000, config.maximumSize());
      assertEquals(Duration.ofMinutes(30), config.expireAfterAccess());
      assertEquals(Duration.ofHours(1), config.expireAfterWrite());
      assertTrue(config.recordStats());
    }

    @Test
    @DisplayName("should throw on invalid maximum size")
    void shouldThrowOnInvalidMaximumSize() {
      assertThrows(IllegalArgumentException.class, () ->
          CacheConfig.builder().maximumSize(0).build()
      );

      assertThrows(IllegalArgumentException.class, () ->
          CacheConfig.builder().maximumSize(-1).build()
      );
    }

    @Test
    @DisplayName("predefined configs should have sensible defaults")
    void predefinedConfigsShouldHaveSensibleDefaults() {
      // DEFAULT
      assertEquals(10_000, CacheConfig.DEFAULT.maximumSize());
      assertNull(CacheConfig.DEFAULT.expireAfterAccess());
      assertFalse(CacheConfig.DEFAULT.recordStats());

      // PATH_CACHE
      assertEquals(5_000, CacheConfig.PATH_CACHE.maximumSize());
      assertNotNull(CacheConfig.PATH_CACHE.expireAfterAccess());

      // TYPE_CACHE
      assertEquals(1_000, CacheConfig.TYPE_CACHE.maximumSize());

      // DEBUG
      assertTrue(CacheConfig.DEBUG.recordStats());
    }
  }
}
