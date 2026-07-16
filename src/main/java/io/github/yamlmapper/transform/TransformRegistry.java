package io.github.yamlmapper.transform;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for transform functions.
 *
 * <p>Manages registration of transforms by name. Transforms are registered
 * and retrieved for use during field mapping.
 *
 * <p>Example usage:
 * <pre>{@code
 * TransformRegistry registry = new TransformRegistry();
 *
 * // Register custom transform
 * registry.register("upperCase", (node, ctx) -> {
 *     if (node == null || !node.isTextual()) return node;
 *     return new TextNode(node.asText().toUpperCase());
 * });
 *
 * // Register transform with getName()
 * registry.register(new MyCustomTransform());
 * }</pre>
 *
 * <p>This class is thread-safe.
 */
public class TransformRegistry {

  private final ConcurrentHashMap<String, Transform> transforms;

  /**
   * Creates a new empty TransformRegistry.
   */
  public TransformRegistry() {
    this.transforms = new ConcurrentHashMap<>();
  }

  /**
   * Registers a transform by name.
   *
   * @param name the transform name (as used in YAML)
   * @param transform the transform implementation
   * @return this registry for chaining
   * @throws IllegalArgumentException if name or transform is null
   */
  public TransformRegistry register(String name, Transform transform) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Transform name cannot be null or blank");
    }
    if (transform == null) {
      throw new IllegalArgumentException("Transform cannot be null");
    }
    transforms.put(name, transform);
    return this;
  }

  /**
   * Registers a transform using its getName() method.
   *
   * @param transform the transform to register
   * @return this registry for chaining
   * @throws IllegalArgumentException if transform is null or has no name
   */
  public TransformRegistry register(Transform transform) {
    if (transform == null) {
      throw new IllegalArgumentException("Transform cannot be null");
    }
    String name = transform.getName();
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Transform must have a name");
    }
    return register(name, transform);
  }

  /**
   * Gets a transform by name.
   *
   * @param name the transform name
   * @return the transform, or null if not found
   */
  public Transform get(String name) {
    if (name == null) {
      return null;
    }
    return transforms.get(name);
  }
}
