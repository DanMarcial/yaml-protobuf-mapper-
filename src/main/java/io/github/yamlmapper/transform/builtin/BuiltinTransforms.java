package io.github.yamlmapper.transform.builtin;

import io.github.yamlmapper.transform.TransformRegistry;

/**
 * Utility class to register all builtin transforms.
 *
 * <p>Usage:
 * <pre>{@code
 * TransformRegistry registry = new TransformRegistry();
 * BuiltinTransforms.registerAll(registry);
 * }</pre>
 *
 * <p>Available builtin transforms:
 * <ul>
 *   <li>{@code singleItemToArray} - Wraps single value in array</li>
 *   <li>{@code truncate} - Truncates string to max length</li>
 *   <li>{@code filterBlank} - Removes blank strings from array</li>
 *   <li>{@code trim} - Trims whitespace</li>
 *   <li>{@code lowercase} - Converts to lowercase</li>
 *   <li>{@code uppercase} - Converts to uppercase</li>
 *   <li>{@code splitToArray} - Splits string by delimiter into array</li>
 *   <li>{@code mapValue} - Maps values using a dictionary</li>
 *   <li>{@code replaceChars} - Replaces characters in string</li>
 *   <li>{@code stringsToImages} - Converts string URLs to Image objects</li>
 *   <li>{@code zipArrays} - Merges parallel arrays into unified objects</li>
 *   <li>{@code fieldsToAttributeMap} - Converts fields to CustomAttribute map</li>
 *   <li>{@code parseKeyValuePairs} - Parses "key:value|key:value" string to CustomAttribute map</li>
 * </ul>
 */
public final class BuiltinTransforms {

  private BuiltinTransforms() {
    // Utility class
  }

  /**
   * Registers all builtin transforms in the given registry.
   *
   * @param registry the transform registry
   * @return the registry for chaining
   */
  public static TransformRegistry registerAll(TransformRegistry registry) {
    return registry
        .register(new SingleItemToArrayTransform())
        .register(new TruncateTransform())
        .register(new FilterBlankTransform())
        .register(new TrimTransform())
        .register(new LowercaseTransform())
        .register(new UppercaseTransform())
        .register(new SplitToArrayTransform())
        .register(new MapValueTransform())
        .register(new ReplaceCharsTransform())
        .register(new StringsToImagesTransform())
        .register(new ZipArraysTransform())
        .register(new FieldsToAttributeMapTransform())
        .register(new ParseKeyValuePairsTransform());
  }

  /**
   * Creates a new registry with all builtin transforms registered.
   *
   * @return a new TransformRegistry with builtins
   */
  public static TransformRegistry createRegistry() {
    return registerAll(new TransformRegistry());
  }
}
