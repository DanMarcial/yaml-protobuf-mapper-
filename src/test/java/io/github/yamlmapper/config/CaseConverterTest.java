package io.github.yamlmapper.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for CaseConverter utility.
 */
class CaseConverterTest {

  @Nested
  @DisplayName("camelToSnake()")
  class CamelToSnakeTests {

    @Test
    @DisplayName("should convert simple camelCase")
    void shouldConvertSimpleCamelCase() {
      assertThat(CaseConverter.camelToSnake("visitorId")).isEqualTo("visitor_id");
      assertThat(CaseConverter.camelToSnake("userInfo")).isEqualTo("user_info");
      assertThat(CaseConverter.camelToSnake("eventType")).isEqualTo("event_type");
    }

    @Test
    @DisplayName("should handle multiple uppercase letters")
    void shouldHandleMultipleUppercase() {
      assertThat(CaseConverter.camelToSnake("pageURLPath")).isEqualTo("page_u_r_l_path");
      assertThat(CaseConverter.camelToSnake("XMLParser")).isEqualTo("x_m_l_parser");
    }

    @Test
    @DisplayName("should preserve already snake_case")
    void shouldPreserveSnakeCase() {
      assertThat(CaseConverter.camelToSnake("already_snake")).isEqualTo("already_snake");
      assertThat(CaseConverter.camelToSnake("visitor_id")).isEqualTo("visitor_id");
    }

    @Test
    @DisplayName("should handle lowercase only")
    void shouldHandleLowercaseOnly() {
      assertThat(CaseConverter.camelToSnake("lowercase")).isEqualTo("lowercase");
    }

    @Test
    @DisplayName("should handle single character")
    void shouldHandleSingleChar() {
      assertThat(CaseConverter.camelToSnake("a")).isEqualTo("a");
      assertThat(CaseConverter.camelToSnake("A")).isEqualTo("a");
    }

    @Test
    @DisplayName("should handle null and empty")
    void shouldHandleNullAndEmpty() {
      assertThat(CaseConverter.camelToSnake(null)).isNull();
      assertThat(CaseConverter.camelToSnake("")).isEmpty();
    }
  }

  @Nested
  @DisplayName("snakeToCamel()")
  class SnakeToCamelTests {

    @Test
    @DisplayName("should convert simple snake_case")
    void shouldConvertSimpleSnakeCase() {
      assertThat(CaseConverter.snakeToCamel("visitor_id")).isEqualTo("visitorId");
      assertThat(CaseConverter.snakeToCamel("user_info")).isEqualTo("userInfo");
      assertThat(CaseConverter.snakeToCamel("event_type")).isEqualTo("eventType");
    }

    @Test
    @DisplayName("should handle multiple underscores")
    void shouldHandleMultipleUnderscores() {
      assertThat(CaseConverter.snakeToCamel("page_view_event_type")).isEqualTo("pageViewEventType");
    }

    @Test
    @DisplayName("should preserve already camelCase")
    void shouldPreserveCamelCase() {
      assertThat(CaseConverter.snakeToCamel("alreadyCamel")).isEqualTo("alreadyCamel");
      assertThat(CaseConverter.snakeToCamel("visitorId")).isEqualTo("visitorId");
    }

    @Test
    @DisplayName("should handle no underscores")
    void shouldHandleNoUnderscores() {
      assertThat(CaseConverter.snakeToCamel("lowercase")).isEqualTo("lowercase");
    }

    @Test
    @DisplayName("should handle single character")
    void shouldHandleSingleChar() {
      assertThat(CaseConverter.snakeToCamel("a")).isEqualTo("a");
    }

    @Test
    @DisplayName("should handle null and empty")
    void shouldHandleNullAndEmpty() {
      assertThat(CaseConverter.snakeToCamel(null)).isNull();
      assertThat(CaseConverter.snakeToCamel("")).isEmpty();
    }

    @Test
    @DisplayName("should handle trailing underscore")
    void shouldHandleTrailingUnderscore() {
      assertThat(CaseConverter.snakeToCamel("visitor_")).isEqualTo("visitor");
    }
  }

  @Nested
  @DisplayName("Round-trip conversion")
  class RoundTripTests {

    @Test
    @DisplayName("snake -> camel -> snake should be consistent")
    void snakeCamelSnakeShouldBeConsistent() {
      String original = "visitor_id";
      String camel = CaseConverter.snakeToCamel(original);
      String backToSnake = CaseConverter.camelToSnake(camel);

      assertThat(backToSnake).isEqualTo(original);
    }

    @Test
    @DisplayName("camel -> snake -> camel should be consistent for simple cases")
    void camelSnakeCamelShouldBeConsistent() {
      String original = "visitorId";
      String snake = CaseConverter.camelToSnake(original);
      String backToCamel = CaseConverter.snakeToCamel(snake);

      assertThat(backToCamel).isEqualTo(original);
    }
  }
}
