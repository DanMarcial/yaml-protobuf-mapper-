package io.github.yamlmapper.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for ProtobufConstraints loading and querying.
 */
class ProtobufConstraintsTest {

  private static ProtobufConstraints userEventConstraints;
  private static ProtobufConstraints productConstraints;

  @BeforeAll
  static void loadSchemas() throws IOException {
    userEventConstraints = ProtobufConstraints.fromClasspath("schemas/user-event.schema.json");
    productConstraints = ProtobufConstraints.fromClasspath("schemas/product.schema.json");
  }

  @Nested
  @DisplayName("Schema Loading")
  class SchemaLoadingTests {

    @Test
    @DisplayName("should load user-event schema")
    void shouldLoadUserEventSchema() {
      assertThat(userEventConstraints).isNotNull();
      assertThat(userEventConstraints.getAlwaysRequired()).isNotEmpty();
    }

    @Test
    @DisplayName("should load product schema")
    void shouldLoadProductSchema() {
      assertThat(productConstraints).isNotNull();
      assertThat(productConstraints.getAlwaysRequired()).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("MaxLength Constraints")
  class MaxLengthTests {

    @Test
    @DisplayName("should have maxLength for visitorId")
    void shouldHaveMaxLengthForVisitorId() {
      Optional<Integer> maxLength = userEventConstraints.getMaxLength("visitorId");
      assertThat(maxLength).isPresent().hasValue(128);
    }

    @Test
    @DisplayName("should have maxLength for sessionId")
    void shouldHaveMaxLengthForSessionId() {
      Optional<Integer> maxLength = userEventConstraints.getMaxLength("sessionId");
      assertThat(maxLength).isPresent().hasValue(128);
    }

    @Test
    @DisplayName("should have maxLength for searchQuery")
    void shouldHaveMaxLengthForSearchQuery() {
      Optional<Integer> maxLength = userEventConstraints.getMaxLength("searchQuery");
      assertThat(maxLength).isPresent().hasValue(5000);
    }

    @Test
    @DisplayName("should have maxLength for filter")
    void shouldHaveMaxLengthForFilter() {
      Optional<Integer> maxLength = userEventConstraints.getMaxLength("filter");
      assertThat(maxLength).isPresent().hasValue(1000);
    }

    @Test
    @DisplayName("should have maxLength for uri")
    void shouldHaveMaxLengthForUri() {
      Optional<Integer> maxLength = userEventConstraints.getMaxLength("uri");
      assertThat(maxLength).isPresent().hasValue(5000);
    }

    @Test
    @DisplayName("should have maxLength for product id")
    void shouldHaveMaxLengthForProductId() {
      Optional<Integer> maxLength = productConstraints.getMaxLength("id");
      assertThat(maxLength).isPresent().hasValue(128);
    }

    @Test
    @DisplayName("should return empty for field without maxLength")
    void shouldReturnEmptyForFieldWithoutMaxLength() {
      Optional<Integer> maxLength = userEventConstraints.getMaxLength("cartId");
      assertThat(maxLength).isEmpty();
    }
  }

  @Nested
  @DisplayName("Range Constraints")
  class RangeTests {

    @Test
    @DisplayName("should have minimum for offset")
    void shouldHaveMinimumForOffset() {
      Optional<ProtobufConstraints.Range> range = userEventConstraints.getRange("offset");
      assertThat(range).isPresent();
      assertThat(range.get().minimum()).isEqualTo(0);
      assertThat(range.get().maximum()).isNull();
    }

    @Test
    @DisplayName("should have minimum for quantity in ProductDetail")
    void shouldHaveMinimumForQuantity() {
      Optional<ProtobufConstraints.Range> range = userEventConstraints.getRange("ProductDetail.quantity");
      assertThat(range).isPresent();
      assertThat(range.get().minimum()).isEqualTo(1);
    }

    @Test
    @DisplayName("should have minimum for availableQuantity in product")
    void shouldHaveMinimumForAvailableQuantity() {
      Optional<ProtobufConstraints.Range> range = productConstraints.getRange("availableQuantity");
      assertThat(range).isPresent();
      assertThat(range.get().minimum()).isEqualTo(0);
    }

    @Test
    @DisplayName("range validation should work correctly")
    void rangeValidationShouldWork() {
      ProtobufConstraints.Range minZero = ProtobufConstraints.Range.min(0);
      assertThat(minZero.isValid(0)).isTrue();
      assertThat(minZero.isValid(100)).isTrue();
      assertThat(minZero.isValid(-1)).isFalse();

      ProtobufConstraints.Range minOne = ProtobufConstraints.Range.min(1);
      assertThat(minOne.isValid(0)).isFalse();
      assertThat(minOne.isValid(1)).isTrue();
    }
  }

  @Nested
  @DisplayName("Always Required Fields")
  class AlwaysRequiredTests {

    @Test
    @DisplayName("should have eventType and visitorId as always required")
    void shouldHaveEventTypeAndVisitorIdRequired() {
      List<String> required = userEventConstraints.getAlwaysRequired();
      assertThat(required).contains("eventType", "visitorId");
    }

    @Test
    @DisplayName("should have product as required in ProductDetail")
    void shouldHaveProductRequiredInProductDetail() {
      List<String> required = userEventConstraints.getAlwaysRequired();
      assertThat(required).contains("ProductDetail.product");
    }

    @Test
    @DisplayName("should have id as required for Product")
    void shouldHaveIdRequiredForProduct() {
      List<String> required = productConstraints.getAlwaysRequired();
      assertThat(required).contains("id");
    }

    @Test
    @DisplayName("should have id as required for PurchaseTransaction")
    void shouldHaveIdRequiredForPurchaseTransaction() {
      List<String> required = userEventConstraints.getAlwaysRequired();
      assertThat(required).contains("PurchaseTransaction.id");
    }
  }

  @Nested
  @DisplayName("Conditional Required Fields")
  class ConditionalRequiredTests {

    @Test
    @DisplayName("should have conditional requirements for search")
    void shouldHaveConditionalRequirementsForSearch() {
      List<ProtobufConstraints.ConditionalRequired> rules =
          userEventConstraints.getRequiredForEventType("search");

      assertThat(rules).isNotEmpty();
      // search requires searchQuery OR pageCategories
      ProtobufConstraints.ConditionalRequired searchRule = rules.get(0);
      assertThat(searchRule.isOrCondition()).isTrue();
      assertThat(searchRule.requiredFields()).contains("searchQuery", "pageCategories");
    }

    @Test
    @DisplayName("should have conditional requirements for add-to-cart")
    void shouldHaveConditionalRequirementsForAddToCart() {
      List<ProtobufConstraints.ConditionalRequired> rules =
          userEventConstraints.getRequiredForEventType("add-to-cart");

      assertThat(rules).isNotEmpty();
      ProtobufConstraints.ConditionalRequired rule = rules.get(0);
      assertThat(rule.isOrCondition()).isFalse();
      assertThat(rule.requiredFields()).contains("productDetails");
    }

    @Test
    @DisplayName("should have conditional requirements for purchase-complete")
    void shouldHaveConditionalRequirementsForPurchaseComplete() {
      List<ProtobufConstraints.ConditionalRequired> rules =
          userEventConstraints.getRequiredForEventType("purchase-complete");

      assertThat(rules).isNotEmpty();
      ProtobufConstraints.ConditionalRequired rule = rules.get(0);
      assertThat(rule.isOrCondition()).isFalse();
      assertThat(rule.requiredFields()).contains("productDetails", "purchaseTransaction");
    }

    @Test
    @DisplayName("should have conditional requirements for category-page-view")
    void shouldHaveConditionalRequirementsForCategoryPageView() {
      List<ProtobufConstraints.ConditionalRequired> rules =
          userEventConstraints.getRequiredForEventType("category-page-view");

      assertThat(rules).isNotEmpty();
      ProtobufConstraints.ConditionalRequired rule = rules.get(0);
      assertThat(rule.requiredFields()).contains("pageCategories");
    }

  }

  @Nested
  @DisplayName("Enum Values")
  class EnumValuesTests {

    @Test
    @DisplayName("should have enum values for eventType")
    void shouldHaveEnumValuesForEventType() {
      Optional<List<String>> values = userEventConstraints.getEnumValues("eventType");
      assertThat(values).isPresent();
      assertThat(values.get()).contains(
          "add-to-cart", "search", "detail-page-view",
          "purchase-complete", "home-page-view"
      );
    }

    @Test
    @DisplayName("should have enum values for availability")
    void shouldHaveEnumValuesForAvailability() {
      Optional<List<String>> values = productConstraints.getEnumValues("availability");
      assertThat(values).isPresent();
      assertThat(values.get()).contains(
          "IN_STOCK", "OUT_OF_STOCK", "PREORDER", "BACKORDER"
      );
    }

    @Test
    @DisplayName("should have enum values for product type")
    void shouldHaveEnumValuesForProductType() {
      Optional<List<String>> values = productConstraints.getEnumValues("type");
      assertThat(values).isPresent();
      assertThat(values.get()).contains("PRIMARY", "VARIANT", "COLLECTION");
    }
  }

}
