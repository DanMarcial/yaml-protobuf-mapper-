package io.github.yamlmapper.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.yamlmapper.cache.CacheFactory;
import io.github.yamlmapper.config.CacheConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.yamlmapper.extractor.PathConstants.PATH_CURRENT_CONTEXT;
import static io.github.yamlmapper.extractor.PathConstants.PATH_ROOT;

/**
 * Resolves JSON paths supporting dot-notation and array index access.
 *
 * <p>Supported path formats:
 * <ul>
 *   <li>Simple field: {@code "fieldName"}</li>
 *   <li>Nested field: {@code "user.address.city"}</li>
 *   <li>Array index: {@code "items[0]"}</li>
 *   <li>Combined: {@code "users[0].address.street"}</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * PathResolver resolver = new PathResolver();
 * JsonNode value = resolver.resolve(rootNode, "user.address.city");
 * JsonNode first = resolver.resolve(rootNode, "items[0].name");
 * }</pre>
 *
 * <p>Performance optimization: Path segments are cached after first split
 * to avoid repeated String.split() allocations for frequently used paths.
 *
 * <p>This class is thread-safe.
 */
public class PathResolver {

  // Matches field[index] pattern, e.g., "items[0]" -> groups: "items", "0"
  private static final Pattern ARRAY_PATTERN =
          Pattern.compile("^([^\\[]+)\\[(\\d+)]$");

  // Caffeine cache for split path segments - better eviction and memory management
  private final Cache<String, String[]> segmentCache;

  /**
   * Creates a PathResolver with default cache configuration.
   */
  public PathResolver() {
    this(CacheConfig.PATH_CACHE);
  }

  /**
   * Creates a PathResolver with custom cache configuration.
   *
   * @param cacheConfig the cache configuration to use
   */
  public PathResolver(final CacheConfig cacheConfig) {
    this.segmentCache = CacheFactory.create(cacheConfig);
  }

  /**
   * Resolves a path within a JSON node.
   *
   * <p>Special paths:
   * <ul>
   *   <li>{@code "."} - Returns the current root node (current context)</li>
   *   <li>{@code "$"} - Same as "." (alternative syntax)</li>
   * </ul>
   *
   * @param root the root JSON node
   * @param path the path to resolve (e.g., "user.address.city")
   * @return the resolved JsonNode, or null if path doesn't exist
   */
  public JsonNode resolve(final JsonNode root, final String path) {

    if (root == null || path == null || path.isBlank()) {
      return null;
    }

    // Special case: "." or "$" means current context
    if (PATH_CURRENT_CONTEXT.equals(path) || PATH_ROOT.equals(path)) {
      return root;
    }

    JsonNode current = root;

    // Use Caffeine cache for path segments - better eviction than ConcurrentHashMap
    String[] segments = segmentCache.get(path, p -> p.split("\\."));

    for (String segment : segments) {

      if (current == null || current.isMissingNode() || current.isNull()) {
        return null;
      }

      current = resolveSegment(current, segment);
    }

    return current;
  }

  private JsonNode resolveSegment(final JsonNode current, final String segment) {

    Matcher matcher = ARRAY_PATTERN.matcher(segment);

    if (matcher.matches()) {

      String field = matcher.group(1);
      int index = Integer.parseInt(matcher.group(2));

      JsonNode array = current.get(field);

      if (array == null || !array.isArray()) {
        return null;
      }

      if (index < 0 || index >= array.size()) {
        return null;
      }

      return array.get(index);
    }

    return current.get(segment);
  }
}
