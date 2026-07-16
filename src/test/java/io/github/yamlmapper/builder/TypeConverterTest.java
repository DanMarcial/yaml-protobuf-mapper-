package io.github.yamlmapper.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yamlmapper.exception.MappingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TypeConverterTest {

  private TypeConverter converter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    converter = new TypeConverter();
    objectMapper = new ObjectMapper();
  }

  private JsonNode toNode(Object value) throws Exception {
    return objectMapper.valueToTree(value);
  }

  @Nested
  @DisplayName("Integer conversion")
  class IntegerConversionTests {

    @Test
    @DisplayName("should convert valid integer")
    void shouldConvertValidInteger() throws Exception {
      JsonNode node = toNode(42);
      assertThat(converter.convertToInteger(node)).isEqualTo(42);
    }

    @Test
    @DisplayName("should convert string to integer")
    void shouldConvertStringToInteger() throws Exception {
      JsonNode node = toNode("123");
      assertThat(converter.convertToInteger(node)).isEqualTo(123);
    }

    @Test
    @DisplayName("should convert Integer.MAX_VALUE")
    void shouldConvertMaxValue() throws Exception {
      JsonNode node = toNode(Integer.MAX_VALUE);
      assertThat(converter.convertToInteger(node)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("should convert Integer.MIN_VALUE")
    void shouldConvertMinValue() throws Exception {
      JsonNode node = toNode(Integer.MIN_VALUE);
      assertThat(converter.convertToInteger(node)).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    @DisplayName("should throw on overflow above MAX_VALUE")
    void shouldThrowOnOverflowAboveMax() throws Exception {
      long overflowValue = (long) Integer.MAX_VALUE + 1;
      JsonNode node = toNode(overflowValue);

      assertThatThrownBy(() -> converter.convertToInteger(node))
          .isInstanceOf(MappingException.class)
          .hasMessageContaining("Integer overflow")
          .hasMessageContaining(String.valueOf(overflowValue));
    }

    @Test
    @DisplayName("should throw on overflow below MIN_VALUE")
    void shouldThrowOnOverflowBelowMin() throws Exception {
      long overflowValue = (long) Integer.MIN_VALUE - 1;
      JsonNode node = toNode(overflowValue);

      assertThatThrownBy(() -> converter.convertToInteger(node))
          .isInstanceOf(MappingException.class)
          .hasMessageContaining("Integer overflow")
          .hasMessageContaining(String.valueOf(overflowValue));
    }

    @Test
    @DisplayName("should throw on overflow from string")
    void shouldThrowOnOverflowFromString() throws Exception {
      String overflowValue = "9999999999999";
      JsonNode node = toNode(overflowValue);

      assertThatThrownBy(() -> converter.convertToInteger(node))
          .isInstanceOf(MappingException.class)
          .hasMessageContaining("Integer overflow");
    }

    @Test
    @DisplayName("should return null for null node")
    void shouldReturnNullForNullNode() {
      assertThat(converter.convertToInteger(null)).isNull();
    }
  }

  @Nested
  @DisplayName("Long conversion")
  class LongConversionTests {

    @Test
    @DisplayName("should convert valid long")
    void shouldConvertValidLong() throws Exception {
      JsonNode node = toNode(9223372036854775807L);
      assertThat(converter.convertToLong(node)).isEqualTo(9223372036854775807L);
    }

    @Test
    @DisplayName("should convert string to long")
    void shouldConvertStringToLong() throws Exception {
      JsonNode node = toNode("9223372036854775807");
      assertThat(converter.convertToLong(node)).isEqualTo(9223372036854775807L);
    }
  }

  @Nested
  @DisplayName("Boolean conversion")
  class BooleanConversionTests {

    @Test
    @DisplayName("should convert true")
    void shouldConvertTrue() throws Exception {
      JsonNode node = toNode(true);
      assertThat(converter.convertToBoolean(node)).isTrue();
    }

    @Test
    @DisplayName("should convert 'yes' to true")
    void shouldConvertYesToTrue() throws Exception {
      JsonNode node = toNode("yes");
      assertThat(converter.convertToBoolean(node)).isTrue();
    }

    @Test
    @DisplayName("should convert '1' to true")
    void shouldConvertOneToTrue() throws Exception {
      JsonNode node = toNode("1");
      assertThat(converter.convertToBoolean(node)).isTrue();
    }

    @Test
    @DisplayName("should convert 'false' to false")
    void shouldConvertFalseStringToFalse() throws Exception {
      JsonNode node = toNode("false");
      assertThat(converter.convertToBoolean(node)).isFalse();
    }
  }
}
