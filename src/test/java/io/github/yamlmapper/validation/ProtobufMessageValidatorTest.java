package io.github.yamlmapper.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.retail.v2.Product;
import com.google.cloud.retail.v2.ProductDetail;
import com.google.cloud.retail.v2.UserEvent;
import com.google.protobuf.Int32Value;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for ProtobufMessageValidator.
 */
class ProtobufMessageValidatorTest {

  private static ProtobufConstraints userEventConstraints;
  private static ProtobufConstraints productConstraints;
  private static ProtobufMessageValidator userEventValidator;
  private static ProtobufMessageValidator productValidator;

  @BeforeAll
  static void setup() throws IOException {
    userEventConstraints = ProtobufConstraints.fromClasspath("schemas/user-event.schema.json");
    productConstraints = ProtobufConstraints.fromClasspath("schemas/product.schema.json");
    userEventValidator = new ProtobufMessageValidator(userEventConstraints);
    productValidator = new ProtobufMessageValidator(productConstraints);
  }

  @Nested
  @DisplayName("Null Message Validation")
  class NullMessageTests {

    @Test
    @DisplayName("should return error for null message")
    void shouldReturnErrorForNullMessage() {
      ValidationResult result = userEventValidator.validate(null);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).contains("Message cannot be null");
    }
  }

  @Nested
  @DisplayName("Always Required Fields")
  class AlwaysRequiredTests {

    @Test
    @DisplayName("should pass when all required fields are present")
    void shouldPassWhenAllRequiredFieldsPresent() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .setVisitorId("visitor-123")
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should fail when visitorId is missing")
    void shouldFailWhenVisitorIdMissing() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.contains("visitorId") && e.contains("missing"));
    }

    @Test
    @DisplayName("should fail when eventType is missing")
    void shouldFailWhenEventTypeMissing() {
      UserEvent event = UserEvent.newBuilder()
          .setVisitorId("visitor-123")
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.contains("eventType") && e.contains("missing"));
    }

    @Test
    @DisplayName("should pass Product when id is present")
    void shouldPassProductWhenIdPresent() {
      Product product = Product.newBuilder()
          .setId("prod-123")
          .build();

      ValidationResult result = productValidator.validate(product);
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should fail Product when id is missing")
    void shouldFailProductWhenIdMissing() {
      Product product = Product.newBuilder()
          .setTitle("Test Product")
          .build();

      ValidationResult result = productValidator.validate(product);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.contains("id") && e.contains("missing"));
    }
  }

  @Nested
  @DisplayName("Conditional Required Fields")
  class ConditionalRequiredTests {

    @Test
    @DisplayName("should pass search event with searchQuery")
    void shouldPassSearchEventWithSearchQuery() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("search")
          .setVisitorId("visitor-123")
          .setSearchQuery("laptop")
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should pass search event with pageCategories")
    void shouldPassSearchEventWithPageCategories() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("search")
          .setVisitorId("visitor-123")
          .addPageCategories("electronics")
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should fail search event without searchQuery or pageCategories")
    void shouldFailSearchEventWithoutSearchQueryOrPageCategories() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("search")
          .setVisitorId("visitor-123")
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("search") && e.contains("searchQuery") && e.contains("pageCategories"));
    }

    @Test
    @DisplayName("should pass add-to-cart event with productDetails")
    void shouldPassAddToCartWithProductDetails() {
      Product product = Product.newBuilder()
          .setId("prod-123")
          .build();

      ProductDetail detail = ProductDetail.newBuilder()
          .setProduct(product)
          .setQuantity(Int32Value.of(2))
          .build();

      UserEvent event = UserEvent.newBuilder()
          .setEventType("add-to-cart")
          .setVisitorId("visitor-123")
          .addProductDetails(detail)
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should fail add-to-cart event without productDetails")
    void shouldFailAddToCartWithoutProductDetails() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("add-to-cart")
          .setVisitorId("visitor-123")
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("add-to-cart") && e.contains("productDetails"));
    }

    @Test
    @DisplayName("should fail detail-page-view event without productDetails")
    void shouldFailDetailPageViewWithoutProductDetails() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("detail-page-view")
          .setVisitorId("visitor-123")
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("detail-page-view") && e.contains("productDetails"));
    }

    @Test
    @DisplayName("should fail category-page-view without pageCategories")
    void shouldFailCategoryPageViewWithoutPageCategories() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("category-page-view")
          .setVisitorId("visitor-123")
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("category-page-view") && e.contains("pageCategories"));
    }

    @Test
    @DisplayName("should pass home-page-view without conditional requirements")
    void shouldPassHomePageViewWithoutConditionalRequirements() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .setVisitorId("visitor-123")
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isTrue();
    }
  }

  @Nested
  @DisplayName("MaxLength Constraints")
  class MaxLengthTests {

    @Test
    @DisplayName("should pass when string is within maxLength")
    void shouldPassWhenStringWithinMaxLength() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .setVisitorId("v".repeat(128)) // exactly 128 chars
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should fail when visitorId exceeds maxLength")
    void shouldFailWhenVisitorIdExceedsMaxLength() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .setVisitorId("v".repeat(129)) // exceeds 128
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("visitorId") && e.contains("maxLength"));
    }

    @Test
    @DisplayName("should fail when searchQuery exceeds maxLength")
    void shouldFailWhenSearchQueryExceedsMaxLength() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("search")
          .setVisitorId("visitor-123")
          .setSearchQuery("q".repeat(5001)) // exceeds 5000
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("searchQuery") && e.contains("maxLength"));
    }

    @Test
    @DisplayName("should fail when Product id exceeds maxLength")
    void shouldFailWhenProductIdExceedsMaxLength() {
      Product product = Product.newBuilder()
          .setId("p".repeat(129)) // exceeds 128
          .build();

      ValidationResult result = productValidator.validate(product);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("id") && e.contains("maxLength"));
    }
  }

  @Nested
  @DisplayName("Range Constraints")
  class RangeTests {

    @Test
    @DisplayName("should pass when offset is within range")
    void shouldPassWhenOffsetWithinRange() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .setVisitorId("visitor-123")
          .setOffset(0)
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should fail when offset is negative")
    void shouldFailWhenOffsetNegative() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("home-page-view")
          .setVisitorId("visitor-123")
          .setOffset(-1)
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("offset") && e.contains("out of range"));
    }

    @Test
    @DisplayName("should pass when availableQuantity is within range")
    void shouldPassWhenAvailableQuantityWithinRange() {
      Product product = Product.newBuilder()
          .setId("prod-123")
          .setAvailableQuantity(Int32Value.of(0))
          .build();

      ValidationResult result = productValidator.validate(product);
      assertThat(result.isValid()).isTrue();
    }
  }

  @Nested
  @DisplayName("Enum Value Validation")
  class EnumValueTests {

    @Test
    @DisplayName("should pass when eventType is valid enum value")
    void shouldPassWhenEventTypeValid() {
      UserEvent event = UserEvent.newBuilder()
          .setEventType("add-to-cart")
          .setVisitorId("visitor-123")
          .addProductDetails(ProductDetail.newBuilder()
              .setProduct(Product.newBuilder().setId("prod-1").build())
              .build())
          .build();

      ValidationResult result = userEventValidator.validate(event);
      // We don't validate enum against schema since Protobuf handles enum validation
      // This test verifies our validator doesn't falsely reject valid enums
      assertThat(result.errors()).noneMatch(e ->
          e.contains("eventType") && e.contains("invalid"));
    }
  }

  @Nested
  @DisplayName("Nested Object Validation")
  class NestedObjectTests {

    @Test
    @DisplayName("should validate nested ProductDetail objects")
    void shouldValidateNestedProductDetailObjects() {
      Product product = Product.newBuilder()
          .setId("prod-123")
          .build();

      ProductDetail detail = ProductDetail.newBuilder()
          .setProduct(product)
          .setQuantity(Int32Value.of(2))
          .build();

      UserEvent event = UserEvent.newBuilder()
          .setEventType("add-to-cart")
          .setVisitorId("visitor-123")
          .addProductDetails(detail)
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should validate ProductDetail.product is required")
    void shouldValidateProductDetailProductRequired() {
      ProductDetail detail = ProductDetail.newBuilder()
          .setQuantity(Int32Value.of(2))
          // product is missing
          .build();

      UserEvent event = UserEvent.newBuilder()
          .setEventType("add-to-cart")
          .setVisitorId("visitor-123")
          .addProductDetails(detail)
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).anyMatch(e ->
          e.contains("product") && e.contains("missing") && e.contains("ProductDetail"));
    }
  }

  @Nested
  @DisplayName("Complete Validation Scenario")
  class CompleteScenarioTests {

    @Test
    @DisplayName("should validate complete valid UserEvent")
    void shouldValidateCompleteValidUserEvent() {
      Product product = Product.newBuilder()
          .setId("prod-123")
          .setTitle("Test Product")
          .build();

      ProductDetail detail = ProductDetail.newBuilder()
          .setProduct(product)
          .setQuantity(Int32Value.of(2))
          .build();

      UserEvent event = UserEvent.newBuilder()
          .setEventType("add-to-cart")
          .setVisitorId("visitor-123")
          .setSessionId("session-456")
          .setSearchQuery("test query")
          .setOffset(10)
          .addProductDetails(detail)
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isTrue();
      assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("should collect multiple validation errors")
    void shouldCollectMultipleValidationErrors() {
      UserEvent event = UserEvent.newBuilder()
          // missing eventType
          // missing visitorId
          .setSearchQuery("q".repeat(5001)) // exceeds maxLength
          .setOffset(-5) // negative
          .build();

      ValidationResult result = userEventValidator.validate(event);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).hasSizeGreaterThanOrEqualTo(3);
    }
  }
}
