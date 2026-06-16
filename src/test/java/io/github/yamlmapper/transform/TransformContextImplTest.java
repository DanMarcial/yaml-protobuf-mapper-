package io.github.yamlmapper.transform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yamlmapper.config.FieldConfig;
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
    @DisplayName("should return field name")
    void shouldReturnFieldName() {
      TransformContext ctx = TransformContextImpl.builder()
          .fieldName("visitorId")
          .build();

      assertThat(ctx.getFieldName()).isEqualTo("visitorId");
    }

    @Test
    @DisplayName("should return field config")
    void shouldReturnFieldConfig() {
      FieldConfig config = FieldConfig.builder("test")
          .type("string")
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getFieldConfig()).isSameAs(config);
    }

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
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("key", "value"))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParam("key")).isEqualTo("value");
    }

    @Test
    @DisplayName("should return null for missing param")
    void shouldReturnNullForMissingParam() {
      FieldConfig config = FieldConfig.builder("test").build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParam("missing")).isNull();
    }

    @Test
    @DisplayName("should return default for missing param")
    void shouldReturnDefaultForMissingParam() {
      FieldConfig config = FieldConfig.builder("test").build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParam("missing", "default")).isEqualTo("default");
    }

    @Test
    @DisplayName("should convert non-string to string")
    void shouldConvertNonStringToString() {
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("number", 42))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
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
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("maxLength", 500))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParamAsInt("maxLength", 0)).isEqualTo(500);
    }

    @Test
    @DisplayName("should get int param from string")
    void shouldGetIntFromString() {
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("maxLength", "500"))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParamAsInt("maxLength", 0)).isEqualTo(500);
    }

    @Test
    @DisplayName("should return default for missing int param")
    void shouldReturnDefaultForMissingInt() {
      FieldConfig config = FieldConfig.builder("test").build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParamAsInt("missing", 100)).isEqualTo(100);
    }

    @Test
    @DisplayName("should return default for invalid int")
    void shouldReturnDefaultForInvalidInt() {
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("value", "not-a-number"))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
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
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("enabled", true))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParamAsBoolean("enabled", false)).isTrue();
    }

    @Test
    @DisplayName("should parse 'yes' as true")
    void shouldParseYesAsTrue() {
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("enabled", "yes"))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParamAsBoolean("enabled", false)).isTrue();
    }

    @Test
    @DisplayName("should parse '1' as true")
    void shouldParse1AsTrue() {
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("enabled", "1"))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParamAsBoolean("enabled", false)).isTrue();
    }

    @Test
    @DisplayName("should return default for missing boolean")
    void shouldReturnDefaultForMissingBoolean() {
      FieldConfig config = FieldConfig.builder("test").build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
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
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("threshold", 0.75))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParamAsDouble("threshold", 0.0)).isEqualTo(0.75);
    }

    @Test
    @DisplayName("should get double param from string")
    void shouldGetDoubleFromString() {
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("threshold", "0.5"))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
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
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of(
              "mapping", Map.of("a", "1", "b", "2")
          ))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      Map<String, String> result = ctx.getParamAsMap("mapping");

      assertThat(result).containsEntry("a", "1").containsEntry("b", "2");
    }

    @Test
    @DisplayName("should return empty map for missing param")
    void shouldReturnEmptyMapForMissing() {
      FieldConfig config = FieldConfig.builder("test").build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParamAsMap("missing")).isEmpty();
    }

    @Test
    @DisplayName("should return empty map for non-map param")
    void shouldReturnEmptyMapForNonMap() {
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of("notAMap", "string"))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParamAsMap("notAMap")).isEmpty();
    }

    @Test
    @DisplayName("should convert map values to strings")
    void shouldConvertMapValuesToStrings() {
      FieldConfig config = FieldConfig.builder("test")
          .transformParams(Map.of(
              "mapping", Map.of("num", 42, "bool", true)
          ))
          .build();

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      Map<String, String> result = ctx.getParamAsMap("mapping");

      assertThat(result).containsEntry("num", "42").containsEntry("bool", "true");
    }
  }

  @Nested
  @DisplayName("Null safety")
  class NullSafetyTests {

    @Test
    @DisplayName("should handle null field config")
    void shouldHandleNullFieldConfig() {
      TransformContext ctx = TransformContextImpl.builder()
          .fieldName("test")
          .fieldConfig(null)
          .build();

      assertThat(ctx.getParam("any")).isNull();
      assertThat(ctx.getParamAsInt("any", 5)).isEqualTo(5);
    }

    @Test
    @DisplayName("should handle null transform params")
    void shouldHandleNullTransformParams() {
      FieldConfig config = new FieldConfig(
          "test", "string", null, null, null, null, null,
          null, null, null, null, null, false, null, false, null
      );

      TransformContext ctx = TransformContextImpl.builder()
          .fieldConfig(config)
          .build();

      assertThat(ctx.getParam("any")).isNull();
    }
  }
}
