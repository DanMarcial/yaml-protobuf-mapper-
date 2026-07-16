package io.github.yamlmapper.transform.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;
import io.github.yamlmapper.transform.TransformContextImpl;
import io.github.yamlmapper.transform.TransformRegistry;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for all builtin transforms.
 */
class BuiltinTransformsTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
  }

  private TransformContext contextWithParams(Map<String, Object> params) {
    return TransformContextImpl.builder()
        .params(params)
        .objectMapper(mapper)
        .build();
  }

  private TransformContext emptyContext() {
    return TransformContextImpl.builder()
        .params(Map.of())
        .objectMapper(mapper)
        .build();
  }

  // ============================================
  // Parameterized tests for common behaviors
  // ============================================

  static Stream<Arguments> allTransformsWithNames() {
    return Stream.of(
        Arguments.of(new SingleItemToArrayTransform(), "singleItemToArray"),
        Arguments.of(new TruncateTransform(), "truncate"),
        Arguments.of(new FilterBlankTransform(), "filterBlank"),
        Arguments.of(new TrimTransform(), "trim"),
        Arguments.of(new LowercaseTransform(), "lowercase"),
        Arguments.of(new UppercaseTransform(), "uppercase")
    );
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("allTransformsWithNames")
  @DisplayName("Transform should have correct name")
  void transformShouldHaveCorrectName(Transform transform, String expectedName) {
    assertThat(transform.getName()).isEqualTo(expectedName);
  }

  static Stream<Arguments> stringTransformsThatReturnNullForNull() {
    return Stream.of(
        Arguments.of(new TrimTransform(), "trim"),
        Arguments.of(new TruncateTransform(), "truncate"),
        Arguments.of(new LowercaseTransform(), "lowercase"),
        Arguments.of(new UppercaseTransform(), "uppercase")
    );
  }

  @ParameterizedTest(name = "{1} returns null for null input")
  @MethodSource("stringTransformsThatReturnNullForNull")
  @DisplayName("String transform should return null for null input")
  void stringTransformShouldReturnNullForNullInput(Transform transform, String name) {
    JsonNode result = transform.apply(null, emptyContext());
    assertThat(result).isNull();
  }

  @ParameterizedTest(name = "{1} returns input unchanged for non-string")
  @MethodSource("stringTransformsThatReturnNullForNull")
  @DisplayName("String transform should return non-string unchanged")
  void stringTransformShouldReturnNonStringUnchanged(Transform transform, String name) {
    JsonNode input = JsonNodeFactory.instance.numberNode(42);
    JsonNode result = transform.apply(input, emptyContext());
    assertThat(result).isSameAs(input);
  }

  static Stream<Arguments> arrayTransformsThatReturnEmptyArrayForNull() {
    return Stream.of(
        Arguments.of(new SingleItemToArrayTransform(), "singleItemToArray"),
        Arguments.of(new FilterBlankTransform(), "filterBlank")
    );
  }

  @ParameterizedTest(name = "{1} returns empty array for null input")
  @MethodSource("arrayTransformsThatReturnEmptyArrayForNull")
  @DisplayName("Array transform should return empty array for null input")
  void arrayTransformShouldReturnEmptyArrayForNullInput(Transform transform, String name) {
    JsonNode result = transform.apply(null, emptyContext());
    assertThat(result.isArray()).isTrue();
    assertThat(result.isEmpty()).isTrue();
  }

  // ============================================
  // BuiltinTransforms utility tests
  // ============================================

  @Nested
  @DisplayName("BuiltinTransforms utility")
  class BuiltinTransformsUtilityTests {

    @Test
    @DisplayName("registerAll should register all 11 transforms")
    void registerAllShouldRegisterAllTransforms() {
      TransformRegistry registry = new TransformRegistry();
      BuiltinTransforms.registerAll(registry);

      assertThat(registry.get("singleItemToArray")).isNotNull();
      assertThat(registry.get("truncate")).isNotNull();
      assertThat(registry.get("filterBlank")).isNotNull();
      assertThat(registry.get("trim")).isNotNull();
      assertThat(registry.get("lowercase")).isNotNull();
      assertThat(registry.get("uppercase")).isNotNull();
      assertThat(registry.get("splitToArray")).isNotNull();
      assertThat(registry.get("mapValue")).isNotNull();
      assertThat(registry.get("replaceChars")).isNotNull();
      assertThat(registry.get("stringsToImages")).isNotNull();
      assertThat(registry.get("zipArrays")).isNotNull();
    }

    @Test
    @DisplayName("createRegistry should return registry with all transforms")
    void createRegistryShouldReturnRegistryWithAllTransforms() {
      TransformRegistry registry = BuiltinTransforms.createRegistry();

      assertThat(registry.get("singleItemToArray")).isNotNull();
      assertThat(registry.get("uppercase")).isNotNull();
    }

    @Test
    @DisplayName("registerAll should return registry for chaining")
    void registerAllShouldReturnRegistryForChaining() {
      TransformRegistry registry = new TransformRegistry();
      TransformRegistry returned = BuiltinTransforms.registerAll(registry);

      assertThat(returned).isSameAs(registry);
    }
  }

  // ============================================
  // Transform-specific tests
  // ============================================

  @Nested
  @DisplayName("SingleItemToArrayTransform")
  class SingleItemToArrayTransformTests {

    private SingleItemToArrayTransform transform;

    @BeforeEach
    void setUp() {
      transform = new SingleItemToArrayTransform();
    }

    @Test
    @DisplayName("should wrap single string in array")
    void shouldWrapSingleStringInArray() {
      JsonNode input = new TextNode("value");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.isArray()).isTrue();
      assertThat(result.size()).isEqualTo(1);
      assertThat(result.get(0).asText()).isEqualTo("value");
    }

    @Test
    @DisplayName("should wrap single number in array")
    void shouldWrapSingleNumberInArray() {
      JsonNode input = JsonNodeFactory.instance.numberNode(42);
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.isArray()).isTrue();
      assertThat(result.size()).isEqualTo(1);
      assertThat(result.get(0).asInt()).isEqualTo(42);
    }

    @Test
    @DisplayName("should return array unchanged")
    void shouldReturnArrayUnchanged() throws Exception {
      JsonNode input = mapper.readTree("[\"a\", \"b\", \"c\"]");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result).isSameAs(input);
      assertThat(result.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("should return empty array for NullNode")
    void shouldReturnEmptyArrayForNullNode() {
      JsonNode result = transform.apply(NullNode.getInstance(), emptyContext());

      assertThat(result.isArray()).isTrue();
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should wrap object in array")
    void shouldWrapObjectInArray() throws Exception {
      JsonNode input = mapper.readTree("{\"key\": \"value\"}");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.isArray()).isTrue();
      assertThat(result.size()).isEqualTo(1);
      assertThat(result.get(0).get("key").asText()).isEqualTo("value");
    }
  }

  @Nested
  @DisplayName("TruncateTransform")
  class TruncateTransformTests {

    private TruncateTransform transform;

    @BeforeEach
    void setUp() {
      transform = new TruncateTransform();
    }

    @Test
    @DisplayName("should truncate string to max length")
    void shouldTruncateStringToMaxLength() {
      JsonNode input = new TextNode("Hello World");
      TransformContext ctx = contextWithParams(Map.of("maxLength", 5));

      JsonNode result = transform.apply(input, ctx);

      assertThat(result.asText()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("should not truncate shorter string")
    void shouldNotTruncateShorterString() {
      JsonNode input = new TextNode("Hi");
      TransformContext ctx = contextWithParams(Map.of("maxLength", 10));

      JsonNode result = transform.apply(input, ctx);

      assertThat(result.asText()).isEqualTo("Hi");
    }

    @Test
    @DisplayName("should add suffix when truncating")
    void shouldAddSuffixWhenTruncating() {
      JsonNode input = new TextNode("Hello World!");
      TransformContext ctx = contextWithParams(Map.of("maxLength", 8, "suffix", "..."));

      JsonNode result = transform.apply(input, ctx);

      assertThat(result.asText()).isEqualTo("Hello...");
      assertThat(result.asText().length()).isEqualTo(8);
    }

    @Test
    @DisplayName("should handle suffix longer than max length")
    void shouldHandleSuffixLongerThanMaxLength() {
      JsonNode input = new TextNode("Hello");
      TransformContext ctx = contextWithParams(Map.of("maxLength", 2, "suffix", "..."));

      JsonNode result = transform.apply(input, ctx);

      assertThat(result.asText()).isEqualTo("...");
    }

    @Test
    @DisplayName("should use default max length of 5000")
    void shouldUseDefaultMaxLength() {
      String longString = "a".repeat(6000);
      JsonNode input = new TextNode(longString);

      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.asText().length()).isEqualTo(5000);
    }
  }

  @Nested
  @DisplayName("FilterBlankTransform")
  class FilterBlankTransformTests {

    private FilterBlankTransform transform;

    @BeforeEach
    void setUp() {
      transform = new FilterBlankTransform();
    }

    @Test
    @DisplayName("should filter empty strings")
    void shouldFilterEmptyStrings() throws Exception {
      JsonNode input = mapper.readTree("[\"a\", \"\", \"b\"]");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.size()).isEqualTo(2);
      assertThat(result.get(0).asText()).isEqualTo("a");
      assertThat(result.get(1).asText()).isEqualTo("b");
    }

    @Test
    @DisplayName("should filter whitespace-only strings")
    void shouldFilterWhitespaceOnlyStrings() throws Exception {
      JsonNode input = mapper.readTree("[\"a\", \"   \", \"b\", \"\\t\\n\"]");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.size()).isEqualTo(2);
      assertThat(result.get(0).asText()).isEqualTo("a");
      assertThat(result.get(1).asText()).isEqualTo("b");
    }

    @Test
    @DisplayName("should filter null elements")
    void shouldFilterNullElements() {
      ArrayNode input = JsonNodeFactory.instance.arrayNode();
      input.add("a");
      input.addNull();
      input.add("b");

      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("should keep non-string elements")
    void shouldKeepNonStringElements() throws Exception {
      JsonNode input = mapper.readTree("[\"a\", 42, \"\", true]");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.size()).isEqualTo(3);
      assertThat(result.get(0).asText()).isEqualTo("a");
      assertThat(result.get(1).asInt()).isEqualTo(42);
      assertThat(result.get(2).asBoolean()).isTrue();
    }

    @Test
    @DisplayName("should return non-array unchanged")
    void shouldReturnNonArrayUnchanged() {
      JsonNode input = new TextNode("not an array");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result).isSameAs(input);
    }
  }

  @Nested
  @DisplayName("TrimTransform")
  class TrimTransformTests {

    private TrimTransform transform;

    @BeforeEach
    void setUp() {
      transform = new TrimTransform();
    }

    @Test
    @DisplayName("should trim leading whitespace")
    void shouldTrimLeadingWhitespace() {
      JsonNode input = new TextNode("   hello");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.asText()).isEqualTo("hello");
    }

    @Test
    @DisplayName("should trim trailing whitespace")
    void shouldTrimTrailingWhitespace() {
      JsonNode input = new TextNode("hello   ");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.asText()).isEqualTo("hello");
    }

    @Test
    @DisplayName("should trim both ends")
    void shouldTrimBothEnds() {
      JsonNode input = new TextNode("  hello world  ");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.asText()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("should handle tabs and newlines")
    void shouldHandleTabsAndNewlines() {
      JsonNode input = new TextNode("\t\nhello\n\t");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.asText()).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("LowercaseTransform")
  class LowercaseTransformTests {

    private LowercaseTransform transform;

    @BeforeEach
    void setUp() {
      transform = new LowercaseTransform();
    }

    @Test
    @DisplayName("should convert to lowercase")
    void shouldConvertToLowercase() {
      JsonNode input = new TextNode("HELLO WORLD");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.asText()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("should handle mixed case")
    void shouldHandleMixedCase() {
      JsonNode input = new TextNode("HeLLo WoRLd");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.asText()).isEqualTo("hello world");
    }
  }

  @Nested
  @DisplayName("UppercaseTransform")
  class UppercaseTransformTests {

    private UppercaseTransform transform;

    @BeforeEach
    void setUp() {
      transform = new UppercaseTransform();
    }

    @Test
    @DisplayName("should convert to uppercase")
    void shouldConvertToUppercase() {
      JsonNode input = new TextNode("hello world");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.asText()).isEqualTo("HELLO WORLD");
    }

    @Test
    @DisplayName("should handle mixed case")
    void shouldHandleMixedCase() {
      JsonNode input = new TextNode("HeLLo WoRLd");
      JsonNode result = transform.apply(input, emptyContext());

      assertThat(result.asText()).isEqualTo("HELLO WORLD");
    }
  }
}
