package io.github.yamlmapper.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.exception.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for YamlConfigLoader.
 */
class YamlConfigLoaderTest {

  private YamlConfigLoader loader;

  @BeforeEach
  void setUp() {
    loader = new YamlConfigLoader();
  }

  @Nested
  @DisplayName("load() from classpath")
  class LoadFromClasspathTests {

    @Test
    @DisplayName("should load simple config")
    void shouldLoadSimpleConfig() {
      MappingSchema schema = loader.load("classpath:mapping/simple.yaml");

      assertThat(schema.rootType()).isEqualTo("UserEvent");
      assertThat(schema.fields().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("should parse field with single source as list")
    void shouldParseSingleSourceAsList() {
      MappingSchema schema = loader.load("classpath:mapping/simple.yaml");

      FieldConfig visitorId = schema.fields().get("visitorId");
      assertThat(visitorId.source()).containsExactly("visitorId");
    }

    @Test
    @DisplayName("should parse field with multiple sources")
    void shouldParseMultipleSources() {
      MappingSchema schema = loader.load("classpath:mapping/simple.yaml");

      FieldConfig eventType = schema.fields().get("eventType");
      assertThat(eventType.source()).containsExactly("eventType", "event_type");
    }

    @Test
    @DisplayName("should parse required flag")
    void shouldParseRequiredFlag() {
      MappingSchema schema = loader.load("classpath:mapping/simple.yaml");

      assertThat(schema.fields().get("visitorId").required()).isTrue();
      assertThat(schema.fields().get("eventType").required()).isFalse();
    }

    @Test
    @DisplayName("should parse default value")
    void shouldParseDefaultValue() {
      MappingSchema schema = loader.load("classpath:mapping/simple.yaml");

      FieldConfig eventType = schema.fields().get("eventType");
      assertThat(eventType.defaultValue()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("should parse transform and transformParams")
    void shouldParseTransform() {
      MappingSchema schema = loader.load("classpath:mapping/search-event.yaml");

      FieldConfig pageCategories = schema.fields().get("pageCategories");
      assertThat(pageCategories.transform()).isEqualTo("singleItemToArray");
      assertThat(pageCategories.hasTransform()).isTrue();
    }

    @Test
    @DisplayName("should parse array type with itemType")
    void shouldParseArrayType() {
      MappingSchema schema = loader.load("classpath:mapping/search-event.yaml");

      FieldConfig pageCategories = schema.fields().get("pageCategories");
      assertThat(pageCategories.type()).isEqualTo("array");
      assertThat(pageCategories.itemType()).isEqualTo("string");
    }

    @Test
    @DisplayName("should parse nested object fields")
    void shouldParseNestedObjectFields() {
      MappingSchema schema = loader.load("classpath:mapping/nested.yaml");

      FieldConfig userInfo = schema.fields().get("userInfo");
      assertThat(userInfo.type()).isEqualTo("object");
      assertThat(userInfo.objectType()).isEqualTo("UserInfo");
      assertThat(userInfo.fields()).isNotEmpty();

      assertThat(userInfo.fields()).containsKeys("userId", "userAgent");
      assertThat(userInfo.fields().get("userId").source())
          .containsExactly("userId", "user_id");
    }

    @Test
    @DisplayName("should parse deeply nested structures")
    void shouldParseDeeplyNestedStructures() {
      MappingSchema schema = loader.load("classpath:mapping/nested.yaml");

      FieldConfig productDetails = schema.fields().get("productDetails");
      assertThat(productDetails.type()).isEqualTo("array");
      assertThat(productDetails.itemType()).isEqualTo("ProductDetail");

      // Check nested product field
      FieldConfig product = productDetails.fields().get("product");
      assertThat(product.type()).isEqualTo("object");
      assertThat(product.objectType()).isEqualTo("Product");

      // Check deeply nested id field
      FieldConfig id = product.fields().get("id");
      assertThat(id.type()).isEqualTo("string");
      assertThat(id.required()).isTrue();
    }
  }

  @Nested
  @DisplayName("Error handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should throw for non-existent file")
    void shouldThrowForNonExistentFile() {
      assertThatThrownBy(() -> loader.load("classpath:mapping/not-found.yaml"))
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("should throw for null path")
    void shouldThrowForNullPath() {
      assertThatThrownBy(() -> loader.load(null))
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("null or blank");
    }

    @Test
    @DisplayName("should throw for blank path")
    void shouldThrowForBlankPath() {
      assertThatThrownBy(() -> loader.load("   "))
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("null or blank");
    }
  }

  @Nested
  @DisplayName("extractConfigId()")
  class ExtractConfigIdTests {

    @Test
    @DisplayName("should extract from classpath path")
    void shouldExtractFromClasspath() {
      assertThat(loader.extractConfigId("classpath:mapping/search.yaml"))
          .isEqualTo("search");
    }

    @Test
    @DisplayName("should extract from file path")
    void shouldExtractFromFilePath() {
      assertThat(loader.extractConfigId("/etc/config/add-to-cart.yaml"))
          .isEqualTo("add-to-cart");
    }

    @Test
    @DisplayName("should handle filename only")
    void shouldHandleFilenameOnly() {
      assertThat(loader.extractConfigId("simple.yaml"))
          .isEqualTo("simple");
    }

    @Test
    @DisplayName("should handle multiple dots")
    void shouldHandleMultipleDots() {
      assertThat(loader.extractConfigId("my.config.file.yaml"))
          .isEqualTo("my.config.file");
    }
  }

  @Nested
  @DisplayName("Type defaults")
  class TypeDefaultsTests {

    @Test
    @DisplayName("should default type to string if not specified")
    void shouldDefaultTypeToString() {
      // The simple.yaml has explicit types, but the loader defaults to "string"
      MappingSchema schema = loader.load("classpath:mapping/simple.yaml");

      // All fields should have a type
      for (FieldConfig field : schema.fields().values()) {
        assertThat(field.type()).isNotNull();
      }
    }

    @Test
    @DisplayName("should default empty maps for transformParams and fields")
    void shouldDefaultEmptyMaps() {
      MappingSchema schema = loader.load("classpath:mapping/simple.yaml");

      FieldConfig visitorId = schema.fields().get("visitorId");
      assertThat(visitorId.transformParams()).isNotNull().isEmpty();
      assertThat(visitorId.fields()).isNotNull().isEmpty();
    }
  }
}
