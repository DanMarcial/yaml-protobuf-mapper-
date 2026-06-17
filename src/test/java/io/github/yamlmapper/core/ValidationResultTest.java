package io.github.yamlmapper.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.exception.ConfigurationException;
import io.github.yamlmapper.validation.ValidationResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

  private static MappingEngine engine;

  @BeforeAll
  static void setUp() {
    engine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withConfig("classpath:mapping/search-event.yaml")
        .injectEventType(true)
        .build();
  }

  @Nested
  @DisplayName("ValidationResult record")
  class ValidationResultRecordTests {

    @Test
    @DisplayName("success() should create valid result with no issues")
    void successShouldCreateValidResult() {
      ValidationResult result = ValidationResult.success();

      assertThat(result.isValid()).isTrue();
      assertThat(result.errors()).isEmpty();
      assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("invalid() should create invalid result with errors")
    void invalidShouldCreateInvalidResult() {
      ValidationResult result = ValidationResult.invalid(List.of("Error 1", "Error 2"));

      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).isNotEmpty();
      assertThat(result.errors()).containsExactly("Error 1", "Error 2");
    }

    @Test
    @DisplayName("builder should create valid result with warnings only")
    void builderShouldCreateValidWithWarnings() {
      ValidationResult result = ValidationResult.builder()
          .addWarning("Warning 1")
          .build();

      assertThat(result.isValid()).isTrue();
      assertThat(result.warnings()).isNotEmpty();
      assertThat(result.warnings()).containsExactly("Warning 1");
    }

    @Test
    @DisplayName("builder should accumulate errors and warnings")
    void builderShouldAccumulate() {
      ValidationResult result = ValidationResult.builder()
          .addError("Error 1")
          .addError("Error 2")
          .addWarning("Warning 1")
          .build();

      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).hasSize(2);
      assertThat(result.warnings()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("validateConfig")
  class ValidateConfigTests {

    @Test
    @DisplayName("should return valid for existing valid config")
    void shouldReturnValidForExistingConfig() {
      ValidationResult result = engine.validateConfig("search-event");

      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should return error for null configId")
    void shouldReturnErrorForNullConfigId() {
      ValidationResult result = engine.validateConfig(null);

      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.contains("null or blank"));
    }

    @Test
    @DisplayName("should return error for blank configId")
    void shouldReturnErrorForBlankConfigId() {
      ValidationResult result = engine.validateConfig("   ");

      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.contains("null or blank"));
    }

    @Test
    @DisplayName("should return error for unknown configId")
    void shouldReturnErrorForUnknownConfigId() {
      ValidationResult result = engine.validateConfig("nonexistent");

      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.contains("No configuration found"));
    }

    @Test
    @DisplayName("should throw at build time when field type is missing")
    void shouldThrowWhenFieldTypeIsMissing() {
      FieldConfig invalidField = FieldConfig.builder("badField")
          .type(null)  // Missing type
          .source(List.of("source"))
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("badField", invalidField)
          .build();

      assertThatThrownBy(() -> MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build())
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("type is required");
    }

    @Test
    @DisplayName("should throw at build time when object type is missing objectType")
    void shouldThrowWhenObjectTypeMissingObjectType() {
      FieldConfig objectField = FieldConfig.builder("nestedObject")
          .type("object")
          .source(List.of("nested"))
          .objectType(null)  // Missing objectType
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("nestedObject", objectField)
          .build();

      assertThatThrownBy(() -> MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build())
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("requires 'objectType'");
    }

    @Test
    @DisplayName("should throw at build time when array type is missing itemType")
    void shouldThrowWhenArrayTypeMissingItemType() {
      FieldConfig arrayField = FieldConfig.builder("items")
          .type("array")
          .source(List.of("items"))
          .itemType(null)  // Missing itemType
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("items", arrayField)
          .build();

      assertThatThrownBy(() -> MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build())
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("requires 'itemType'");
    }

    @Test
    @DisplayName("should throw at build time when transform does not exist")
    void shouldThrowWhenTransformNotExists() {
      FieldConfig fieldWithBadTransform = FieldConfig.builder("field")
          .type("string")
          .source(List.of("field"))
          .transform("nonExistentTransform")
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("field", fieldWithBadTransform)
          .build();

      assertThatThrownBy(() -> MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build())
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("not registered");
    }

    @Test
    @DisplayName("should build successfully with warning for field without source")
    void shouldBuildWithWarningForFieldWithoutSource() {
      FieldConfig fieldWithoutSource = FieldConfig.builder("field")
          .type("string")
          .source(List.of())  // Empty source
          .defaultValue("default")
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("field", fieldWithoutSource)
          .build();

      // Should build successfully - warnings don't prevent build
      MappingEngine testEngine = MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build();

      ValidationResult result = testEngine.validateConfig("test-config");

      assertThat(result.isValid()).isTrue();  // Warnings don't make it invalid
      assertThat(result.warnings()).isNotEmpty();
      assertThat(result.warnings()).anyMatch(w -> w.contains("no source defined"));
    }

    @Test
    @DisplayName("should throw at build time when objectType cannot be resolved")
    void shouldThrowWhenObjectTypeCannotBeResolved() {
      FieldConfig objectField = FieldConfig.builder("obj")
          .type("object")
          .source(List.of("obj"))
          .objectType("NonExistentType")
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("obj", objectField)
          .build();

      assertThatThrownBy(() -> MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build())
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("cannot be resolved");
    }

    @Test
    @DisplayName("should accept primitive itemTypes for arrays")
    void shouldAcceptPrimitiveItemTypes() {
      FieldConfig arrayField = FieldConfig.builder("tags")
          .type("array")
          .source(List.of("tags"))
          .itemType("string")  // Primitive type - should be valid
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("tags", arrayField)
          .build();

      MappingEngine testEngine = MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build();

      ValidationResult result = testEngine.validateConfig("test-config");

      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should throw at build time for invalid configuration")
    void shouldThrowAtBuildTimeForInvalidConfig() {
      FieldConfig invalidField = FieldConfig.builder("badField")
          .type("array")
          .source(List.of("source"))
          .itemType(null)  // Missing itemType - invalid
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("badField", invalidField)
          .build();

      assertThatThrownBy(() -> MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build())
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("Schema validation failed")
          .hasMessageContaining("requires 'itemType'");
    }

    @Test
    @DisplayName("should throw at build time for incompatible default value")
    void shouldThrowForIncompatibleDefaultValue() {
      FieldConfig fieldWithBadDefault = FieldConfig.builder("count")
          .type("int32")
          .source(List.of("count"))
          .defaultValue("not-a-number")  // Invalid default for int32
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("count", fieldWithBadDefault)
          .build();

      assertThatThrownBy(() -> MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build())
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("not compatible with type");
    }

    @Test
    @DisplayName("should throw at build time when object type is missing fields")
    void shouldThrowWhenObjectTypeMissingFields() {
      FieldConfig objectField = FieldConfig.builder("product")
          .type("object")
          .source(List.of("product"))
          .objectType("Product")  // Valid type
          .fields(Map.of())  // Empty fields - should fail
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("product", objectField)
          .build();

      assertThatThrownBy(() -> MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build())
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("requires 'fields' mapping");
    }

    @Test
    @DisplayName("should throw at build time when complex array itemType is missing fields")
    void shouldThrowWhenComplexArrayItemTypeMissingFields() {
      FieldConfig arrayField = FieldConfig.builder("products")
          .type("array")
          .source(List.of("products"))
          .itemType("ProductDetail")  // Complex type
          .fields(Map.of())  // Empty fields - should fail
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("products", arrayField)
          .build();

      assertThatThrownBy(() -> MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build())
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("requires 'fields' mapping");
    }

    @Test
    @DisplayName("should throw at build time when map is missing objectType")
    void shouldThrowWhenMapMissingObjectType() {
      FieldConfig mapField = FieldConfig.builder("attributes")
          .type("map")
          .source(List.of("attrs"))
          .objectType(null)  // Missing objectType
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("attributes", mapField)
          .build();

      assertThatThrownBy(() -> MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build())
          .isInstanceOf(ConfigurationException.class)
          .hasMessageContaining("requires 'objectType'");
    }

    @Test
    @DisplayName("should accept valid map with objectType")
    void shouldAcceptValidMapWithObjectType() {
      FieldConfig mapField = FieldConfig.builder("attributes")
          .type("map")
          .source(List.of("attrs"))
          .objectType("CustomAttribute")
          .build();

      MappingSchema schema = MappingSchema.builder()
          .field("attributes", mapField)
          .build();

      MappingEngine testEngine = MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test-config", schema)
          .build();

      ValidationResult result = testEngine.validateConfig("test-config");

      assertThat(result.isValid()).isTrue();
    }
  }
}
