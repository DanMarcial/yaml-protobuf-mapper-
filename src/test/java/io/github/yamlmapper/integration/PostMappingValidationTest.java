package io.github.yamlmapper.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.Product;
import com.google.cloud.retail.v2.UserEvent;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.core.MappingEngine;
import io.github.yamlmapper.core.MappingResult;
import io.github.yamlmapper.validation.ValidationResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for POST-mapping validation feature.
 */
class PostMappingValidationTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
  }

  @Nested
  @DisplayName("Validation Disabled (Default)")
  class ValidationDisabledTests {

    @Test
    @DisplayName("should not perform validation when disabled")
    void shouldNotValidateWhenDisabled() throws Exception {
      MappingEngine engine = MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test", createMinimalSchema())
          .build();

      assertThat(engine.isPostMappingValidationEnabled()).isFalse();
    }

    @Test
    @DisplayName("should throw when calling validateMessage without enabling")
    void shouldThrowWhenValidateMessageCalledWithoutEnabling() throws Exception {
      MappingEngine engine = MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("test", createMinimalSchema())
          .build();

      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .setVisitorId("visitor-123")
          .build();

      assertThatThrownBy(() -> engine.validateMessage(event))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("POST-mapping validation is not enabled");
    }

    @Test
    @DisplayName("mapWithDetails should not include validation when disabled")
    void mapWithDetailsShouldNotIncludeValidation() throws Exception {
      MappingEngine engine = MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("home-page-view", createMinimalSchema())
          .build();

      String json = """
          {
            "visitor_id": "VIS-123"
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);
      MappingResult<UserEvent> result = engine.mapWithDetails(jsonNode, "home-page-view", UserEvent.class);

      assertThat(result.hasValidation()).isFalse();
      assertThat(result.validationResult()).isNull();
      assertThat(result.isValid()).isTrue(); // Should be true when no validation
    }
  }

  @Nested
  @DisplayName("Validation Enabled")
  class ValidationEnabledTests {

    private MappingEngine engine;

    @BeforeEach
    void setupEngine() {
      engine = MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("home-page-view", createMinimalSchema())
          .withSchema("search", createSearchSchema())
          .withSchema("add-to-cart", createAddToCartSchema())
          .enablePostMappingValidation(true)
          .build();
    }

    @Test
    @DisplayName("should enable validation")
    void shouldEnableValidation() {
      assertThat(engine.isPostMappingValidationEnabled()).isTrue();
    }

    @Test
    @DisplayName("should validate valid UserEvent successfully")
    void shouldValidateValidUserEvent() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .setVisitorId("visitor-123")
          .build();

      ValidationResult result = engine.validateMessage(event);

      assertThat(result.isValid()).isTrue();
      assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("should detect missing required fields")
    void shouldDetectMissingRequiredFields() {
      UserEvent event = UserEvent.newBuilder()
          .setVisitorId("visitor-123")
          // missing eventType
          .build();

      ValidationResult result = engine.validateMessage(event);

      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.contains("eventType"));
    }

    @Test
    @DisplayName("should detect maxLength violations")
    void shouldDetectMaxLengthViolations() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .setVisitorId("v".repeat(200)) // exceeds 128 limit
          .build();

      ValidationResult result = engine.validateMessage(event);

      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("visitorId") && e.contains("maxLength"));
    }

    @Test
    @DisplayName("should detect range violations")
    void shouldDetectRangeViolations() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .setVisitorId("visitor-123")
          .setOffset(-5) // must be >= 0
          .build();

      ValidationResult result = engine.validateMessage(event);

      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("offset") && e.contains("out of range"));
    }

    @Test
    @DisplayName("should detect conditional required violations for search")
    void shouldDetectConditionalRequiredForSearch() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("search")
          .setVisitorId("visitor-123")
          // missing searchQuery or pageCategories
          .build();

      ValidationResult result = engine.validateMessage(event);

      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("search") &&
          (e.contains("searchQuery") || e.contains("pageCategories")));
    }

    @Test
    @DisplayName("should pass conditional required when searchQuery provided")
    void shouldPassConditionalRequiredWithSearchQuery() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("search")
          .setVisitorId("visitor-123")
          .setSearchQuery("laptop")
          .build();

      ValidationResult result = engine.validateMessage(event);

      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should validate Product messages")
    void shouldValidateProductMessages() {
      Product product = Product.newBuilder()
          .setId("prod-123")
          .setTitle("Test Product")
          .build();

      ValidationResult result = engine.validateMessage(product);

      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should detect missing Product id")
    void shouldDetectMissingProductId() {
      Product product = Product.newBuilder()
          .setTitle("Test Product")
          // missing id
          .build();

      ValidationResult result = engine.validateMessage(product);

      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.contains("id"));
    }
  }

  @Nested
  @DisplayName("mapWithDetails with Validation")
  class MapWithDetailsValidationTests {

    @Test
    @DisplayName("should include validation result in mapWithDetails")
    void shouldIncludeValidationInMapWithDetails() throws Exception {
      MappingEngine engine = MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("home-page-view", createMinimalSchema())
          .enablePostMappingValidation(true)
          .build();

      String json = """
          {
            "visitor_id": "VIS-123"
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);
      MappingResult<UserEvent> result = engine.mapWithDetails(jsonNode, "home-page-view", UserEvent.class);

      assertThat(result.hasValidation()).isTrue();
      assertThat(result.validationResult()).isNotNull();
      assertThat(result.isValid()).isTrue();
      assertThat(result.validationErrors()).isEmpty();
    }

    @Test
    @DisplayName("should include validation errors in mapWithDetails")
    void shouldIncludeValidationErrorsInMapWithDetails() throws Exception {
      MappingEngine engine = MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("search", createSearchSchema())
          .enablePostMappingValidation(true)
          .build();

      // Search without searchQuery or pageCategories
      String json = """
          {
            "visitor_id": "VIS-123"
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);
      MappingResult<UserEvent> result = engine.mapWithDetails(jsonNode, "search", UserEvent.class);

      assertThat(result.hasValidation()).isTrue();
      assertThat(result.isValid()).isFalse();
      assertThat(result.validationErrors()).isNotEmpty();
      assertThat(result.validationErrors()).anyMatch(e ->
          e.contains("search") && e.contains("searchQuery"));
    }

    @Test
    @DisplayName("should detect maxLength violations during mapping")
    void shouldDetectMaxLengthViolationsDuringMapping() throws Exception {
      MappingEngine engine = MappingEngine.builder()
          .withProtobufPackage("com.google.cloud.retail.v2")
          .withSchema("home-page-view", createMinimalSchema())
          .enablePostMappingValidation(true)
          .build();

      String json = """
          {
            "visitor_id": "%s"
          }
          """.formatted("v".repeat(200));

      JsonNode jsonNode = objectMapper.readTree(json);
      MappingResult<UserEvent> result = engine.mapWithDetails(jsonNode, "home-page-view", UserEvent.class);

      assertThat(result.hasValidation()).isTrue();
      assertThat(result.isValid()).isFalse();
      assertThat(result.validationErrors()).anyMatch(e ->
          e.contains("visitorId") && e.contains("maxLength"));
    }
  }

  // Helper methods to create test schemas

  private MappingSchema createMinimalSchema() {
    return new MappingSchema(
        "UserEvent",
        Map.of(
            "visitorId", FieldConfig.builder("visitorId")
                .type("string")
                .source("visitor_id")
                .build()
        )
    );
  }

  private MappingSchema createSearchSchema() {
    return new MappingSchema(
        "UserEvent",
        Map.of(
            "visitorId", FieldConfig.builder("visitorId")
                .type("string")
                .source("visitor_id")
                .build(),
            "searchQuery", FieldConfig.builder("searchQuery")
                .type("string")
                .source("query")
                .build()
        )
    );
  }

  private MappingSchema createAddToCartSchema() {
    return new MappingSchema(
        "UserEvent",
        Map.of(
            "visitorId", FieldConfig.builder("visitorId")
                .type("string")
                .source("visitor_id")
                .build()
        )
    );
  }
}
