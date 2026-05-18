package io.github.yamlmapper.transform.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.transform.TransformContext;
import io.github.yamlmapper.transform.TransformContextImpl;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ZipArraysTransform")
class ZipArraysTransformTest {

  private ZipArraysTransform transform;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    transform = new ZipArraysTransform();
    objectMapper = new ObjectMapper();
  }

  private FieldConfig createConfig(Map<String, Object> params) {
    return FieldConfig.builder("testField")
        .type("array")
        .source("items")
        .transform("zipArrays")
        .transformParams(params)
        .build();
  }

  @Nested
  @DisplayName("Parallel Arrays")
  class ParallelArraysTests {

    @Test
    @DisplayName("should merge parallel arrays by index")
    void shouldMergeParallelArraysByIndex() throws Exception {
      // JSON with data split across parallel arrays
      String json = """
          {
            "items": [
              {"sku": "SKU-001", "name": "Product 1"},
              {"sku": "SKU-002", "name": "Product 2"}
            ],
            "quantities": [2, 1],
            "prices": [100.00, 50.00]
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      JsonNode itemsArray = rootNode.get("items");

      FieldConfig config = createConfig(Map.of(
          "merge", Map.of("qty", "quantities", "unitPrice", "prices")
      ));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("productDetails")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      // Apply transform
      JsonNode result = transform.apply(itemsArray, context);

      // Verify merged result
      assertThat(result.isArray()).isTrue();
      assertThat(result.size()).isEqualTo(2);

      // First item should have merged qty and unitPrice
      JsonNode item1 = result.get(0);
      assertThat(item1.get("sku").asText()).isEqualTo("SKU-001");
      assertThat(item1.get("name").asText()).isEqualTo("Product 1");
      assertThat(item1.get("qty").asInt()).isEqualTo(2);
      assertThat(item1.get("unitPrice").asDouble()).isEqualTo(100.00);

      // Second item
      JsonNode item2 = result.get(1);
      assertThat(item2.get("sku").asText()).isEqualTo("SKU-002");
      assertThat(item2.get("qty").asInt()).isEqualTo(1);
      assertThat(item2.get("unitPrice").asDouble()).isEqualTo(50.00);
    }

    @Test
    @DisplayName("should handle arrays of different lengths")
    void shouldHandleArraysOfDifferentLengths() throws Exception {
      String json = """
          {
            "items": [
              {"sku": "A"},
              {"sku": "B"},
              {"sku": "C"}
            ],
            "quantities": [1, 2]
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      JsonNode itemsArray = rootNode.get("items");

      FieldConfig config = createConfig(Map.of(
          "merge", Map.of("qty", "quantities")
      ));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("productDetails")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(itemsArray, context);

      assertThat(result.size()).isEqualTo(3);
      assertThat(result.get(0).get("qty").asInt()).isEqualTo(1);
      assertThat(result.get(1).get("qty").asInt()).isEqualTo(2);
      // Third item has no corresponding quantity
      assertThat(result.get(2).has("qty")).isFalse();
    }
  }

  @Nested
  @DisplayName("Map Lookups")
  class MapLookupTests {

    @Test
    @DisplayName("should merge data from map using lookup key")
    void shouldMergeFromMapUsingLookupKey() throws Exception {
      String json = """
          {
            "items": [
              {"sku": "SKU-A", "name": "Product A"},
              {"sku": "SKU-B", "name": "Product B"}
            ],
            "discounts": {
              "SKU-A": 10,
              "SKU-B": 15
            },
            "stock": {
              "SKU-A": 100,
              "SKU-B": 50
            }
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      JsonNode itemsArray = rootNode.get("items");

      FieldConfig config = createConfig(Map.of(
          "lookupKey", "sku",
          "merge", Map.of("discount", "discounts", "stockCount", "stock")
      ));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("productDetails")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(itemsArray, context);

      assertThat(result.size()).isEqualTo(2);

      JsonNode item1 = result.get(0);
      assertThat(item1.get("sku").asText()).isEqualTo("SKU-A");
      assertThat(item1.get("discount").asInt()).isEqualTo(10);
      assertThat(item1.get("stockCount").asInt()).isEqualTo(100);

      JsonNode item2 = result.get(1);
      assertThat(item2.get("sku").asText()).isEqualTo("SKU-B");
      assertThat(item2.get("discount").asInt()).isEqualTo(15);
      assertThat(item2.get("stockCount").asInt()).isEqualTo(50);
    }

    @Test
    @DisplayName("should handle missing lookup keys in map")
    void shouldHandleMissingLookupKeys() throws Exception {
      String json = """
          {
            "items": [
              {"sku": "SKU-A"},
              {"sku": "SKU-C"}
            ],
            "discounts": {
              "SKU-A": 10,
              "SKU-B": 15
            }
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      JsonNode itemsArray = rootNode.get("items");

      FieldConfig config = createConfig(Map.of(
          "lookupKey", "sku",
          "merge", Map.of("discount", "discounts")
      ));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("productDetails")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(itemsArray, context);

      // SKU-A has discount, SKU-C doesn't
      assertThat(result.get(0).get("discount").asInt()).isEqualTo(10);
      assertThat(result.get(1).has("discount")).isFalse();
    }
  }

  @Nested
  @DisplayName("Nested Paths")
  class NestedPathTests {

    @Test
    @DisplayName("should resolve nested paths in source")
    void shouldResolveNestedPaths() throws Exception {
      String json = """
          {
            "items": [{"sku": "A"}, {"sku": "B"}],
            "metadata": {
              "inventory": {
                "counts": [50, 75]
              }
            }
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      JsonNode itemsArray = rootNode.get("items");

      FieldConfig config = createConfig(Map.of(
          "merge", Map.of("stock", "metadata.inventory.counts")
      ));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("productDetails")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(itemsArray, context);

      assertThat(result.get(0).get("stock").asInt()).isEqualTo(50);
      assertThat(result.get(1).get("stock").asInt()).isEqualTo(75);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("should return empty array for null input")
    void shouldReturnEmptyArrayForNullInput() {
      FieldConfig config = createConfig(Map.of(
          "merge", Map.of("qty", "quantities")
      ));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldConfig(config)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(null, context);

      assertThat(result.isArray()).isTrue();
      assertThat(result.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("should return input unchanged when no merge params")
    void shouldReturnInputWhenNoMergeParams() throws Exception {
      String json = """
          {
            "items": [{"sku": "A"}]
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      JsonNode itemsArray = rootNode.get("items");

      FieldConfig config = FieldConfig.builder("testField")
          .type("array")
          .source("items")
          .transform("zipArrays")
          .transformParams(Map.of())  // No merge params
          .build();

      TransformContext context = new TransformContextImpl.Builder()
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(itemsArray, context);

      // Should return input unchanged
      assertThat(result).isEqualTo(itemsArray);
    }
  }

  @Test
  @DisplayName("should have correct name")
  void shouldHaveCorrectName() {
    assertThat(transform.getName()).isEqualTo("zipArrays");
  }
}
