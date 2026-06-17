package io.github.yamlmapper.transform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for TransformContextImpl.
 */
class TransformContextImplTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
  }

  @Nested
  @DisplayName("Basic accessors")
  class BasicAccessorTests {

    @Test
    @DisplayName("should return root node")
    void shouldReturnRootNode() throws Exception {
      JsonNode root = mapper.readTree("{\"key\": \"value\"}");

      TransformContext ctx = TransformContextImpl.builder()
          .rootNode(root)
          .build();

      assertThat(ctx.getRootNode()).isSameAs(root);
    }

    @Test
    @DisplayName("should return object mapper")
    void shouldReturnObjectMapper() {
      TransformContext ctx = TransformContextImpl.builder()
          .objectMapper(mapper)
          .build();

      assertThat(ctx.getObjectMapper()).isSameAs(mapper);
    }
  }

  @Nested
  @DisplayName("String parameter access")
  class StringParamTests {

    @Test
    @DisplayName("should get string param")
    void shouldGetStringParam() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("key", "value"))
          .build();

      assertThat(ctx.getParam("key")).isEqualTo("value");
    }

    @Test
    @DisplayName("should return null for missing param")
    void shouldReturnNullForMissingParam() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of())
          .build();

      assertThat(ctx.getParam("missing")).isNull();
    }

    @Test
    @DisplayName("should return default for missing param")
    void shouldReturnDefaultForMissingParam() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of())
          .build();

      assertThat(ctx.getParam("missing", "default")).isEqualTo("default");
    }

    @Test
    @DisplayName("should convert non-string to string")
    void shouldConvertNonStringToString() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("number", 42))
          .build();

      assertThat(ctx.getParam("number")).isEqualTo("42");
    }
  }

  @Nested
  @DisplayName("Integer parameter access")
  class IntParamTests {

    @Test
    @DisplayName("should get int param from number")
    void shouldGetIntFromNumber() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("maxLength", 500))
          .build();

      assertThat(ctx.getParamAsInt("maxLength", 0)).isEqualTo(500);
    }

    @Test
    @DisplayName("should get int param from string")
    void shouldGetIntFromString() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("maxLength", "500"))
          .build();

      assertThat(ctx.getParamAsInt("maxLength", 0)).isEqualTo(500);
    }

    @Test
    @DisplayName("should return default for missing int param")
    void shouldReturnDefaultForMissingInt() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of())
          .build();

      assertThat(ctx.getParamAsInt("missing", 100)).isEqualTo(100);
    }

    @Test
    @DisplayName("should return default for invalid int")
    void shouldReturnDefaultForInvalidInt() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("value", "not-a-number"))
          .build();

      assertThat(ctx.getParamAsInt("value", 42)).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Boolean parameter access")
  class BooleanParamTests {

    @Test
    @DisplayName("should get boolean param")
    void shouldGetBooleanParam() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("enabled", true))
          .build();

      assertThat(ctx.getParamAsBoolean("enabled", false)).isTrue();
    }

    @Test
    @DisplayName("should parse 'yes' as true")
    void shouldParseYesAsTrue() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("enabled", "yes"))
          .build();

      assertThat(ctx.getParamAsBoolean("enabled", false)).isTrue();
    }

    @Test
    @DisplayName("should parse '1' as true")
    void shouldParse1AsTrue() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("enabled", "1"))
          .build();

      assertThat(ctx.getParamAsBoolean("enabled", false)).isTrue();
    }

    @Test
    @DisplayName("should return default for missing boolean")
    void shouldReturnDefaultForMissingBoolean() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of())
          .build();

      assertThat(ctx.getParamAsBoolean("missing", true)).isTrue();
    }
  }

  @Nested
  @DisplayName("Double parameter access")
  class DoubleParamTests {

    @Test
    @DisplayName("should get double param from number")
    void shouldGetDoubleFromNumber() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("threshold", 0.75))
          .build();

      assertThat(ctx.getParamAsDouble("threshold", 0.0)).isEqualTo(0.75);
    }

    @Test
    @DisplayName("should get double param from string")
    void shouldGetDoubleFromString() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("threshold", "0.5"))
          .build();

      assertThat(ctx.getParamAsDouble("threshold", 0.0)).isEqualTo(0.5);
    }
  }

  @Nested
  @DisplayName("Map parameter access")
  class MapParamTests {

    @Test
    @DisplayName("should get map param")
    void shouldGetMapParam() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("mapping", Map.of("a", "1", "b", "2")))
          .build();

      Map<String, String> result = ctx.getParamAsMap("mapping");

      assertThat(result).containsEntry("a", "1").containsEntry("b", "2");
    }

    @Test
    @DisplayName("should return empty map for missing param")
    void shouldReturnEmptyMapForMissing() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of())
          .build();

      assertThat(ctx.getParamAsMap("missing")).isEmpty();
    }

    @Test
    @DisplayName("should return empty map for non-map param")
    void shouldReturnEmptyMapForNonMap() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("notAMap", "string"))
          .build();

      assertThat(ctx.getParamAsMap("notAMap")).isEmpty();
    }

    @Test
    @DisplayName("should convert map values to strings")
    void shouldConvertMapValuesToStrings() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of("mapping", Map.of("num", 42, "bool", true)))
          .build();

      Map<String, String> result = ctx.getParamAsMap("mapping");

      assertThat(result).containsEntry("num", "42").containsEntry("bool", "true");
    }
  }

  @Nested
  @DisplayName("Null safety")
  class NullSafetyTests {

    @Test
    @DisplayName("should handle null params")
    void shouldHandleNullParams() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(null)
          .build();

      assertThat(ctx.getParam("any")).isNull();
      assertThat(ctx.getParamAsInt("any", 5)).isEqualTo(5);
    }

    @Test
    @DisplayName("should handle empty params")
    void shouldHandleEmptyParams() {
      TransformContext ctx = TransformContextImpl.builder()
          .params(Map.of())
          .build();

      assertThat(ctx.getParam("any")).isNull();
    }
  }
}
