package io.github.yamlmapper.transform.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.transform.TransformContext;
import io.github.yamlmapper.transform.TransformContextImpl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FieldsToAttributeMapTransform")
class FieldsToAttributeMapTransformTest {

  private FieldsToAttributeMapTransform transform;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    transform = new FieldsToAttributeMapTransform();
    objectMapper = new ObjectMapper();
  }

  private FieldConfig createConfig(List<String> fields) {
    return FieldConfig.builder("attributes")
        .type("map")
        .source(".")
        .transform("fieldsToAttributeMap")
        .transformParams(Map.of("fields", fields))
        .build();
  }

  @Nested
  @DisplayName("Basic Functionality")
  class BasicTests {

    @Test
    @DisplayName("should convert string arrays to text attributes")
    void shouldConvertStringArraysToTextAttributes() throws Exception {
      String json = """
          {
            "vendor": ["vendor123", "vendor456"],
            "tags": ["premium", "sale"]
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of("vendor", "tags"));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      assertThat(result.isObject()).isTrue();
      assertThat(result.has("vendor")).isTrue();
      assertThat(result.has("tags")).isTrue();

      // Verify vendor attribute
      JsonNode vendorAttr = result.get("vendor");
      assertThat(vendorAttr.has("text")).isTrue();
      assertThat(vendorAttr.get("text").isArray()).isTrue();
      assertThat(vendorAttr.get("text").get(0).asText()).isEqualTo("vendor123");
      assertThat(vendorAttr.get("text").get(1).asText()).isEqualTo("vendor456");

      // Verify tags attribute
      JsonNode tagsAttr = result.get("tags");
      assertThat(tagsAttr.get("text").get(0).asText()).isEqualTo("premium");
      assertThat(tagsAttr.get("text").get(1).asText()).isEqualTo("sale");
    }

    @Test
    @DisplayName("should convert number arrays to numbers attributes")
    void shouldConvertNumberArraysToNumbersAttributes() throws Exception {
      String json = """
          {
            "lengths_cm": [2.3, 15.4],
            "heights_cm": [8.1, 6.4]
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of("lengths_cm", "heights_cm"));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      // Verify lengths_cm attribute
      JsonNode lengthsAttr = result.get("lengths_cm");
      assertThat(lengthsAttr.has("numbers")).isTrue();
      assertThat(lengthsAttr.get("numbers").get(0).asDouble()).isEqualTo(2.3);
      assertThat(lengthsAttr.get("numbers").get(1).asDouble()).isEqualTo(15.4);

      // Verify heights_cm attribute
      JsonNode heightsAttr = result.get("heights_cm");
      assertThat(heightsAttr.get("numbers").get(0).asDouble()).isEqualTo(8.1);
      assertThat(heightsAttr.get("numbers").get(1).asDouble()).isEqualTo(6.4);
    }

    @Test
    @DisplayName("should wrap single values in arrays")
    void shouldWrapSingleValuesInArrays() throws Exception {
      String json = """
          {
            "color": "red",
            "weight_kg": 5.5
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of("color", "weight_kg"));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      // Verify color (string -> text array)
      JsonNode colorAttr = result.get("color");
      assertThat(colorAttr.has("text")).isTrue();
      assertThat(colorAttr.get("text").size()).isEqualTo(1);
      assertThat(colorAttr.get("text").get(0).asText()).isEqualTo("red");

      // Verify weight_kg (number -> numbers array)
      JsonNode weightAttr = result.get("weight_kg");
      assertThat(weightAttr.has("numbers")).isTrue();
      assertThat(weightAttr.get("numbers").size()).isEqualTo(1);
      assertThat(weightAttr.get("numbers").get(0).asDouble()).isEqualTo(5.5);
    }
  }

  @Nested
  @DisplayName("Mixed Types")
  class MixedTypeTests {

    @Test
    @DisplayName("should convert mixed arrays to text (all values as strings)")
    void shouldConvertMixedArraysToText() throws Exception {
      String json = """
          {
            "mixed": ["text", 123, "another", 456]
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of("mixed"));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      JsonNode mixedAttr = result.get("mixed");
      assertThat(mixedAttr.has("text")).isTrue();
      assertThat(mixedAttr.has("numbers")).isFalse();

      JsonNode textArray = mixedAttr.get("text");
      assertThat(textArray.size()).isEqualTo(4);
      assertThat(textArray.get(0).asText()).isEqualTo("text");
      assertThat(textArray.get(1).asText()).isEqualTo("123");
      assertThat(textArray.get(2).asText()).isEqualTo("another");
      assertThat(textArray.get(3).asText()).isEqualTo("456");
    }

    @Test
    @DisplayName("should handle integer arrays as numbers")
    void shouldHandleIntegerArraysAsNumbers() throws Exception {
      String json = """
          {
            "quantities": [1, 2, 3, 4, 5]
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of("quantities"));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      JsonNode quantitiesAttr = result.get("quantities");
      assertThat(quantitiesAttr.has("numbers")).isTrue();
      assertThat(quantitiesAttr.get("numbers").size()).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("should skip null fields")
    void shouldSkipNullFields() throws Exception {
      String json = """
          {
            "vendor": ["vendor123"],
            "missing": null
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of("vendor", "missing", "nonexistent"));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      assertThat(result.has("vendor")).isTrue();
      assertThat(result.has("missing")).isFalse();
      assertThat(result.has("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("should return empty object when no fields specified")
    void shouldReturnEmptyObjectWhenNoFieldsSpecified() throws Exception {
      String json = """
          {
            "vendor": ["vendor123"]
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = FieldConfig.builder("attributes")
          .type("map")
          .source(".")
          .transform("fieldsToAttributeMap")
          .transformParams(Map.of())  // No fields
          .build();

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      assertThat(result.isObject()).isTrue();
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should return empty object for null root node")
    void shouldReturnEmptyObjectForNullRootNode() {
      FieldConfig config = createConfig(List.of("vendor"));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(null)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(null, context);

      assertThat(result.isObject()).isTrue();
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should skip null values within arrays")
    void shouldSkipNullValuesWithinArrays() throws Exception {
      String json = """
          {
            "values": ["a", null, "b"]
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of("values"));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      JsonNode valuesAttr = result.get("values");
      assertThat(valuesAttr.get("text").size()).isEqualTo(2);
      assertThat(valuesAttr.get("text").get(0).asText()).isEqualTo("a");
      assertThat(valuesAttr.get("text").get(1).asText()).isEqualTo("b");
    }
  }

  @Nested
  @DisplayName("Full Example")
  class FullExampleTest {

    @Test
    @DisplayName("should handle complete product attributes scenario")
    void shouldHandleCompleteProductAttributesScenario() throws Exception {
      String json = """
          {
            "vendor": ["vendor123", "vendor456"],
            "lengths_cm": [2.3, 15.4],
            "heights_cm": [8.1, 6.4],
            "color": "red",
            "weight_kg": 5.5,
            "tags": ["premium", "outlet"],
            "rating": 4.5
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of(
          "vendor", "lengths_cm", "heights_cm", "color", "weight_kg", "tags", "rating"
      ));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      // Verify structure
      assertThat(result.size()).isEqualTo(7);

      // Text attributes
      assertThat(result.get("vendor").has("text")).isTrue();
      assertThat(result.get("color").has("text")).isTrue();
      assertThat(result.get("tags").has("text")).isTrue();

      // Number attributes
      assertThat(result.get("lengths_cm").has("numbers")).isTrue();
      assertThat(result.get("heights_cm").has("numbers")).isTrue();
      assertThat(result.get("weight_kg").has("numbers")).isTrue();
      assertThat(result.get("rating").has("numbers")).isTrue();
    }
  }

  @Test
  @DisplayName("should have correct name")
  void shouldHaveCorrectName() {
    assertThat(transform.getName()).isEqualTo("fieldsToAttributeMap");
  }

  @Nested
  @DisplayName("Literal Colon Fields")
  class LiteralColonFieldTests {

    @Test
    @DisplayName("should extract fields with literal colon in name")
    void shouldExtractFieldsWithLiteralColonInName() throws Exception {
      // JSON with literal colon characters in field names
      String json = """
          {
            "items:group_ids": ["GRP-A", "GRP-B"],
            "items:keywords": ["laptop", "gaming", "portable"],
            "variations:facet:availability": ["in_stock", "online"],
            "variations:facet:PTC_OMNI_BRAND_SUB_BRND": ["Dell", "Alienware"]
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of(
          "items:group_ids",
          "items:keywords",
          "variations:facet:availability",
          "variations:facet:PTC_OMNI_BRAND_SUB_BRND"
      ));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      // Verify fields were extracted
      assertThat(result.has("items:group_ids")).isTrue();
      assertThat(result.get("items:group_ids").get("text").size()).isEqualTo(2);
      assertThat(result.get("items:group_ids").get("text").get(0).asText()).isEqualTo("GRP-A");

      assertThat(result.has("items:keywords")).isTrue();
      assertThat(result.get("items:keywords").get("text").size()).isEqualTo(3);

      assertThat(result.has("variations:facet:availability")).isTrue();
      assertThat(result.get("variations:facet:availability").get("text").get(0).asText()).isEqualTo("in_stock");

      assertThat(result.has("variations:facet:PTC_OMNI_BRAND_SUB_BRND")).isTrue();
      assertThat(result.get("variations:facet:PTC_OMNI_BRAND_SUB_BRND").get("text").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("should handle mixed regular and colon fields")
    void shouldHandleMixedRegularAndColonFields() throws Exception {
      String json = """
          {
            "vendor": ["vendor123"],
            "details:color": "red",
            "details:dimensions:width": 10.5
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of(
          "vendor",
          "details:color",
          "details:dimensions:width"
      ));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      // Regular field
      assertThat(result.has("vendor")).isTrue();
      assertThat(result.get("vendor").get("text").get(0).asText()).isEqualTo("vendor123");

      // Colon fields (literal names)
      assertThat(result.has("details:color")).isTrue();
      assertThat(result.get("details:color").get("text").get(0).asText()).isEqualTo("red");

      assertThat(result.has("details:dimensions:width")).isTrue();
      assertThat(result.get("details:dimensions:width").get("numbers").get(0).asDouble()).isEqualTo(10.5);
    }

    @Test
    @DisplayName("should skip non-existent colon fields")
    void shouldSkipNonExistentColonFields() throws Exception {
      String json = """
          {
            "items:id": "PROD-001"
          }
          """;

      JsonNode rootNode = objectMapper.readTree(json);
      FieldConfig config = createConfig(List.of(
          "items:id",
          "items:nonexistent",
          "nonexistent:path:deep"
      ));

      TransformContext context = new TransformContextImpl.Builder()
          .fieldName("attributes")
          .fieldConfig(config)
          .rootNode(rootNode)
          .objectMapper(objectMapper)
          .build();

      JsonNode result = transform.apply(rootNode, context);

      // Only items:id should exist
      assertThat(result.has("items:id")).isTrue();
      assertThat(result.has("items:nonexistent")).isFalse();
      assertThat(result.has("nonexistent:path:deep")).isFalse();
    }
  }
}
