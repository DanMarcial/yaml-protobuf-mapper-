package io.github.yamlmapper.transform.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.transform.TransformContext;
import io.github.yamlmapper.transform.TransformContextImpl;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ParseKeyValuePairsTransform")
class ParseKeyValuePairsTransformTest {

  private ParseKeyValuePairsTransform transform;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    transform = new ParseKeyValuePairsTransform();
    objectMapper = new ObjectMapper();
  }

  private FieldConfig createConfig(Map<String, Object> params) {
    return FieldConfig.builder("facets")
        .type("map")
        .source("facets")
        .transform("parseKeyValuePairs")
        .transformParams(params)
        .build();
  }

  private TransformContext createContext(FieldConfig config) {
    return TransformContextImpl.builder()
        .params(config.transformParams())
        .objectMapper(objectMapper)
        .build();
  }

  @Nested
  @DisplayName("Basic Parsing")
  class BasicParsingTests {

    @Test
    @DisplayName("should parse simple key:value pairs")
    void shouldParseSimpleKeyValuePairs() {
      String input = "Color:Red|Size:Large|Brand:Nike";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.isObject()).isTrue();
      assertThat(result.has("Color")).isTrue();
      assertThat(result.has("Size")).isTrue();
      assertThat(result.has("Brand")).isTrue();

      assertThat(result.get("Color").get("text").get(0).asText()).isEqualTo("Red");
      assertThat(result.get("Size").get("text").get(0).asText()).isEqualTo("Large");
      assertThat(result.get("Brand").get("text").get(0).asText()).isEqualTo("Nike");
    }

    @Test
    @DisplayName("should group duplicate keys")
    void shouldGroupDuplicateKeys() {
      String input = "Health Feature:Scientific Formula|Health Feature:Hip and Joint|Health Feature:Natural";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.has("Health Feature")).isTrue();
      JsonNode healthFeature = result.get("Health Feature");
      assertThat(healthFeature.has("text")).isTrue();
      assertThat(healthFeature.get("text").size()).isEqualTo(3);
      assertThat(healthFeature.get("text").get(0).asText()).isEqualTo("Scientific Formula");
      assertThat(healthFeature.get("text").get(1).asText()).isEqualTo("Hip and Joint");
      assertThat(healthFeature.get("text").get(2).asText()).isEqualTo("Natural");
    }

    @Test
    @DisplayName("should detect numeric values")
    void shouldDetectNumericValues() {
      String input = "Price:56.99|Weight:29.1|Count:5";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      // Price should be numbers
      assertThat(result.get("Price").has("numbers")).isTrue();
      assertThat(result.get("Price").get("numbers").get(0).asDouble()).isEqualTo(56.99);

      // Weight should be numbers
      assertThat(result.get("Weight").has("numbers")).isTrue();
      assertThat(result.get("Weight").get("numbers").get(0).asDouble()).isEqualTo(29.1);

      // Count should be numbers
      assertThat(result.get("Count").has("numbers")).isTrue();
      assertThat(result.get("Count").get("numbers").get(0).asDouble()).isEqualTo(5.0);
    }
  }

  @Nested
  @DisplayName("Mixed Types")
  class MixedTypeTests {

    @Test
    @DisplayName("should use text when any value is non-numeric")
    void shouldUseTextWhenAnyValueIsNonNumeric() {
      // Same key with mixed values - should all become text
      String input = "Rating:5|Rating:Excellent|Rating:4";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.get("Rating").has("text")).isTrue();
      assertThat(result.get("Rating").has("numbers")).isFalse();
      assertThat(result.get("Rating").get("text").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("should handle real e-commerce facets data")
    void shouldHandleRealEcommerceFacetsData() {
      String input = "Price:56.99|Health Feature:Scientific Formula|Health Feature:Hip and Joint|" +
          "Dietary Preference:With Grain|Weight:29.1|Killed Item flag:No|Primary Flavor:Chicken";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      // Price - numeric
      assertThat(result.get("Price").has("numbers")).isTrue();
      assertThat(result.get("Price").get("numbers").get(0).asDouble()).isEqualTo(56.99);

      // Weight - numeric
      assertThat(result.get("Weight").has("numbers")).isTrue();
      assertThat(result.get("Weight").get("numbers").get(0).asDouble()).isEqualTo(29.1);

      // Health Feature - grouped text
      assertThat(result.get("Health Feature").has("text")).isTrue();
      assertThat(result.get("Health Feature").get("text").size()).isEqualTo(2);

      // Dietary Preference - text
      assertThat(result.get("Dietary Preference").has("text")).isTrue();

      // Primary Flavor - text
      assertThat(result.get("Primary Flavor").get("text").get(0).asText()).isEqualTo("Chicken");
    }
  }

  @Nested
  @DisplayName("Custom Delimiters")
  class CustomDelimiterTests {

    @Test
    @DisplayName("should use custom pair delimiter")
    void shouldUseCustomPairDelimiter() {
      String input = "Color:Red;Size:Large;Brand:Nike";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of("pairDelimiter", ";"));
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.size()).isEqualTo(3);
      assertThat(result.get("Color").get("text").get(0).asText()).isEqualTo("Red");
    }

    @Test
    @DisplayName("should use custom key-value delimiter")
    void shouldUseCustomKeyValueDelimiter() {
      String input = "Color=Red|Size=Large|Brand=Nike";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of("keyValueDelimiter", "="));
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.size()).isEqualTo(3);
      assertThat(result.get("Color").get("text").get(0).asText()).isEqualTo("Red");
    }

    @Test
    @DisplayName("should handle value containing delimiter")
    void shouldHandleValueContainingDelimiter() {
      // Value contains colon - should only split on FIRST colon
      String input = "URL:https://example.com:8080/path|Name:Test";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.get("URL").get("text").get(0).asText())
          .isEqualTo("https://example.com:8080/path");
      assertThat(result.get("Name").get("text").get(0).asText()).isEqualTo("Test");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("should return empty object for null input")
    void shouldReturnEmptyObjectForNullInput() {
      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(null, context);

      assertThat(result.isObject()).isTrue();
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should return empty object for empty string")
    void shouldReturnEmptyObjectForEmptyString() {
      JsonNode inputNode = new TextNode("");

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.isObject()).isTrue();
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should skip empty pairs")
    void shouldSkipEmptyPairs() {
      String input = "Color:Red||Size:Large|||Brand:Nike|";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("should skip pairs without delimiter")
    void shouldSkipPairsWithoutDelimiter() {
      String input = "Color:Red|InvalidPair|Size:Large";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.size()).isEqualTo(2);
      assertThat(result.has("InvalidPair")).isFalse();
    }

    @Test
    @DisplayName("should skip empty keys or values")
    void shouldSkipEmptyKeysOrValues() {
      String input = ":Red|Size:|:";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should trim whitespace")
    void shouldTrimWhitespace() {
      String input = " Color : Red | Size : Large ";
      JsonNode inputNode = new TextNode(input);

      FieldConfig config = createConfig(Map.of());
      TransformContext context = createContext(config);

      JsonNode result = transform.apply(inputNode, context);

      assertThat(result.get("Color").get("text").get(0).asText()).isEqualTo("Red");
      assertThat(result.get("Size").get("text").get(0).asText()).isEqualTo("Large");
    }
  }

  @Test
  @DisplayName("should have correct name")
  void shouldHaveCorrectName() {
    assertThat(transform.getName()).isEqualTo("parseKeyValuePairs");
  }
}
