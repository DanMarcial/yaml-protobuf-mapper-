package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

import java.util.Map;

import static io.github.yamlmapper.transform.TransformParams.PARAM_LOOKUP_KEY;
import static io.github.yamlmapper.transform.TransformParams.PARAM_MERGE;

/**
 * Merges data from parallel arrays into a single array of objects.
 *
 * <p>Solves the common legacy data problem where related data is split
 * across multiple arrays or maps in different JSON nodes.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code merge} - Map of targetField -> sourcePath for parallel arrays</li>
 *   <li>{@code lookupKey} - Field name to use as key for map lookups (optional)</li>
 * </ul>
 *
 * <p>Example YAML for parallel arrays:
 * <pre>{@code
 * # JSON: { "items": [{sku, name}], "quantities": [2, 1], "prices": [100, 50] }
 * productDetails:
 *   type: array
 *   source: [items]
 *   transform: zipArrays
 *   transformParams:
 *     merge:
 *       qty: "quantities"      # quantities[i] -> item.qty
 *       unitPrice: "prices"    # prices[i] -> item.unitPrice
 * }</pre>
 *
 * <p>Example YAML for map lookups:
 * <pre>{@code
 * # JSON: { "items": [{sku: "A", name: "X"}], "discounts": {"A": 10, "B": 5} }
 * productDetails:
 *   type: array
 *   source: [items]
 *   transform: zipArrays
 *   transformParams:
 *     lookupKey: "sku"
 *     merge:
 *       discount: "discounts"  # discounts[item.sku] -> item.discount
 * }</pre>
 *
 * <p>Result: Each item in the source array is enriched with values from
 * parallel arrays (by index) or maps (by lookup key).
 */
public class ZipArraysTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode input, TransformContext context) {
    if (input == null || input.isNull() || !input.isArray()) {
      return context.getObjectMapper().createArrayNode();
    }

    if (context.getRootNode() == null) {
      return input;
    }

    Map<String, String> mergeMap = context.getParamAsMap(PARAM_MERGE);
    if (mergeMap.isEmpty()) {
      return input;
    }

    String lookupKey = context.getParam(PARAM_LOOKUP_KEY);
    ArrayNode result = context.getObjectMapper().createArrayNode();

    int index = 0;
    for (JsonNode item : input) {
      ObjectNode enrichedItem;

      // Clone the item if it's an object, or wrap primitive in object
      if (item.isObject()) {
        enrichedItem = item.deepCopy();
      } else {
        enrichedItem = context.getObjectMapper().createObjectNode();
        enrichedItem.set("value", item);
      }

      // Merge data from parallel sources
      for (Map.Entry<String, String> entry : mergeMap.entrySet()) {
        String targetField = entry.getKey();
        String sourcePath = entry.getValue();

        // Use context.resolvePath() to leverage caching and support array indexing
        JsonNode sourceNode = context.resolvePath(sourcePath);
        if (sourceNode == null || sourceNode.isNull()) {
          continue;
        }

        JsonNode valueToMerge = null;

        if (sourceNode.isArray()) {
          // Parallel array: get value by index
          if (index < sourceNode.size()) {
            valueToMerge = sourceNode.get(index);
          }
        } else if (sourceNode.isObject() && lookupKey != null) {
          // Map lookup: get value by key from current item
          JsonNode keyNode = enrichedItem.get(lookupKey);
          if (keyNode != null && keyNode.isTextual()) {
            valueToMerge = sourceNode.get(keyNode.asText());
          }
        }

        if (valueToMerge != null && !valueToMerge.isNull()) {
          enrichedItem.set(targetField, valueToMerge);
        }
      }

      result.add(enrichedItem);
      index++;
    }

    return result;
  }

  @Override
  public String getName() {
    return "zipArrays";
  }
}
