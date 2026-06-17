package io.github.yamlmapper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.Product;
import com.google.cloud.retail.v2.UserEvent;
import io.github.yamlmapper.core.MappingEngine;
import io.github.yamlmapper.validation.ValidationResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * End-to-End integration tests for the complete mapping pipeline.
 *
 * <p>Tests the full flow: JSON input -> YAML config -> Protobuf output
 *
 * <p>Test scenarios:
 * <ul>
 *   <li>Clean JSON (structure matches Protobuf) + Simple YAML</li>
 *   <li>Chaotic JSON (legacy/different structure) + Complex YAML with transforms</li>
 * </ul>
 */
@DisplayName("End-to-End Mapping Integration Tests")
class EndToEndMappingTest {

  private static ObjectMapper objectMapper;
  private static MappingEngine simpleEngine;
  private static MappingEngine complexEngine;

  // Test data
  private static JsonNode userEventClean;
  private static JsonNode userEventChaotic;
  private static JsonNode productClean;
  private static JsonNode productChaotic;

  @BeforeAll
  static void setupAll() throws IOException {
    objectMapper = new ObjectMapper();

    // Load test JSON files
    userEventClean = loadJson("integration/json/user-event-clean.json");
    userEventChaotic = loadJson("integration/json/user-event-chaotic.json");
    productClean = loadJson("integration/json/product-clean.json");
    productChaotic = loadJson("integration/json/product-chaotic.json");

    // Build engines with simple mappings
    // Disable eventType injection since we're testing multiple message types
    simpleEngine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withConfig("classpath:integration/mapping/user-event-simple.yaml")
        .withConfig("classpath:integration/mapping/product-simple.yaml")
        .injectEventType(false)
        .build();

    // Build engine with complex mappings
    complexEngine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withConfig("classpath:integration/mapping/user-event-complex.yaml")
        .withConfig("classpath:integration/mapping/product-complex.yaml")
        .injectEventType(false)
        .build();
  }

  // ==================== UserEvent Tests ====================

  @Nested
  @DisplayName("UserEvent - Clean JSON + Simple YAML")
  class UserEventCleanTests {

    @Test
    @DisplayName("should map all basic fields correctly")
    void shouldMapBasicFields() {
      UserEvent result = simpleEngine.map(userEventClean, "user-event-simple", UserEvent.class);

      assertThat(result.getVisitorId()).isEqualTo("visitor-abc-123");
      assertThat(result.getSessionId()).isEqualTo("session-xyz-789");
      assertThat(result.getSearchQuery()).isEqualTo("laptop gaming rtx 4080");
      assertThat(result.getAttributionToken()).isEqualTo("attr-token-12345");
      assertThat(result.getUri()).isEqualTo("https://store.example.com/search?q=laptop+gaming");
      assertThat(result.getReferrerUri()).isEqualTo("https://store.example.com/");
    }

    @Test
    @DisplayName("should map timestamp correctly")
    void shouldMapTimestamp() {
      UserEvent result = simpleEngine.map(userEventClean, "user-event-simple", UserEvent.class);

      assertThat(result.hasEventTime()).isTrue();
      // 2024-03-15T14:30:00Z
      assertThat(result.getEventTime().getSeconds()).isEqualTo(1710513000L);
    }

    @Test
    @DisplayName("should map arrays correctly")
    void shouldMapArrays() {
      UserEvent result = simpleEngine.map(userEventClean, "user-event-simple", UserEvent.class);

      assertThat(result.getExperimentIdsList()).containsExactly("exp-001", "exp-002");
      assertThat(result.getPageCategoriesList()).containsExactly("Electronics", "Computers", "Laptops");
    }

    @Test
    @DisplayName("should map nested UserInfo object")
    void shouldMapUserInfo() {
      UserEvent result = simpleEngine.map(userEventClean, "user-event-simple", UserEvent.class);

      assertThat(result.hasUserInfo()).isTrue();
      assertThat(result.getUserInfo().getUserId()).isEqualTo("user-12345");
      assertThat(result.getUserInfo().getIpAddress()).isEqualTo("192.168.1.100");
      assertThat(result.getUserInfo().getDirectUserRequest()).isTrue();
    }

    @Test
    @DisplayName("should map productDetails array with nested objects")
    void shouldMapProductDetails() {
      UserEvent result = simpleEngine.map(userEventClean, "user-event-simple", UserEvent.class);

      assertThat(result.getProductDetailsCount()).isEqualTo(2);

      // First product
      var detail1 = result.getProductDetails(0);
      assertThat(detail1.getProduct().getId()).isEqualTo("SKU-LAPTOP-001");
      assertThat(detail1.getProduct().getTitle()).isEqualTo("Gaming Laptop RTX 4080");
      assertThat(detail1.getQuantity().getValue()).isEqualTo(1);

      // Price info
      assertThat(detail1.getProduct().getPriceInfo().getPrice()).isEqualTo(1899.99f);
      assertThat(detail1.getProduct().getPriceInfo().getOriginalPrice()).isEqualTo(2199.99f);
      assertThat(detail1.getProduct().getPriceInfo().getCurrencyCode()).isEqualTo("USD");

      // Second product
      var detail2 = result.getProductDetails(1);
      assertThat(detail2.getProduct().getId()).isEqualTo("SKU-LAPTOP-002");
    }

  }

  @Nested
  @DisplayName("UserEvent - Chaotic JSON + Complex YAML")
  class UserEventChaoticTests {

    @Test
    @DisplayName("should map fields with different names using fallbacks")
    void shouldMapFieldsWithFallbacks() {
      UserEvent result = complexEngine.map(userEventChaotic, "user-event-complex", UserEvent.class);

      // user_visitor_id -> visitorId
      assertThat(result.getVisitorId()).isEqualTo("VIS_987654");

      // session -> sessionId
      assertThat(result.getSessionId()).isEqualTo("SESS-2024-03-15-ABC");

      // tracking_token -> attributionToken
      assertThat(result.getAttributionToken()).isEqualTo("TRK-ABCDEF-123456");

      // page_url -> uri
      assertThat(result.getUri()).isEqualTo("https://legacy-store.com/product/gaming-laptop");

      // cart_identifier -> cartId
      assertThat(result.getCartId()).isEqualTo("CART-USER-987654");
    }

    @Test
    @DisplayName("should transform unix_millis timestamp")
    void shouldTransformUnixMillisTimestamp() {
      UserEvent result = complexEngine.map(userEventChaotic, "user-event-complex", UserEvent.class);

      assertThat(result.hasEventTime()).isTrue();
      // 1710510600000 ms = 2024-03-15T14:30:00Z
      assertThat(result.getEventTime().getSeconds()).isEqualTo(1710510600L);
    }

    @Test
    @DisplayName("should transform CSV string to array using splitToArray")
    void shouldTransformCsvToArray() {
      UserEvent result = complexEngine.map(userEventChaotic, "user-event-complex", UserEvent.class);

      // "experiment_checkout_v2,experiment_ui_dark" -> ["experiment_checkout_v2", "experiment_ui_dark"]
      assertThat(result.getExperimentIdsList())
          .containsExactly("experiment_checkout_v2", "experiment_ui_dark");
    }

    @Test
    @DisplayName("should map nested customer object to userInfo")
    void shouldMapNestedCustomerToUserInfo() {
      UserEvent result = complexEngine.map(userEventChaotic, "user-event-complex", UserEvent.class);

      assertThat(result.hasUserInfo()).isTrue();
      assertThat(result.getUserInfo().getUserId()).isEqualTo("CUST-987654");
      assertThat(result.getUserInfo().getIpAddress()).isEqualTo("10.0.0.55");
      assertThat(result.getUserInfo().getUserAgent()).isEqualTo("Firefox/123.0");
    }

    @Test
    @DisplayName("should restructure items[] to productDetails[]")
    void shouldRestructureItemsToProductDetails() {
      UserEvent result = complexEngine.map(userEventChaotic, "user-event-complex", UserEvent.class);

      assertThat(result.getProductDetailsCount()).isEqualTo(2);

      // First item: sku -> id, name -> title, qty -> quantity
      var detail1 = result.getProductDetails(0);
      assertThat(detail1.getProduct().getId()).isEqualTo("LEGACY-SKU-001");
      assertThat(detail1.getProduct().getTitle()).isEqualTo("Ultra Gaming Laptop 17\"");
      assertThat(detail1.getQuantity().getValue()).isEqualTo(2);

      // Price mapping: price_usd -> price
      assertThat(detail1.getProduct().getPriceInfo().getPrice()).isEqualTo(2499.00f);
      assertThat(detail1.getProduct().getPriceInfo().getOriginalPrice()).isEqualTo(2999.00f);
    }

    @Test
    @DisplayName("should transform category_path to categories array")
    void shouldTransformCategoryPathToArray() {
      UserEvent result = complexEngine.map(userEventChaotic, "user-event-complex", UserEvent.class);

      // "Computers > Laptops > Gaming" -> ["Computers", "Laptops", "Gaming"]
      var categories = result.getProductDetails(0).getProduct().getCategoriesList();
      assertThat(categories).containsExactly("Computers", "Laptops", "Gaming");
    }
  }

  // ==================== Product Tests ====================

  @Nested
  @DisplayName("Product - Clean JSON + Simple YAML")
  class ProductCleanTests {

    @Test
    @DisplayName("should map all basic fields correctly")
    void shouldMapBasicFields() {
      Product result = simpleEngine.map(productClean, "product-simple", Product.class);

      assertThat(result.getId()).isEqualTo("PROD-LAPTOP-RTX4080");
      assertThat(result.getTitle()).isEqualTo("ASUS ROG Strix G18 Gaming Laptop");
      assertThat(result.getDescription()).contains("18-inch gaming laptop");
      assertThat(result.getUri()).isEqualTo("https://store.example.com/products/asus-rog-strix-g18");
      assertThat(result.getLanguageCode()).isEqualTo("en-US");
    }

    @Test
    @DisplayName("should map price info correctly")
    void shouldMapPriceInfo() {
      Product result = simpleEngine.map(productClean, "product-simple", Product.class);

      assertThat(result.hasPriceInfo()).isTrue();
      assertThat(result.getPriceInfo().getPrice()).isEqualTo(2499.99f);
      assertThat(result.getPriceInfo().getOriginalPrice()).isEqualTo(2999.99f);
      assertThat(result.getPriceInfo().getCurrencyCode()).isEqualTo("USD");
      assertThat(result.getPriceInfo().getCost()).isEqualTo(1800.00f);
    }

    @Test
    @DisplayName("should map arrays correctly")
    void shouldMapArrays() {
      Product result = simpleEngine.map(productClean, "product-simple", Product.class);

      assertThat(result.getCategoriesList()).hasSize(2);
      assertThat(result.getBrandsList()).containsExactly("ASUS", "ROG");
      assertThat(result.getTagsList()).contains("gaming", "rtx4080");
      assertThat(result.getMaterialsList()).containsExactly("Aluminum", "Plastic");
    }

    @Test
    @DisplayName("should map enum correctly")
    void shouldMapEnum() {
      Product result = simpleEngine.map(productClean, "product-simple", Product.class);

      assertThat(result.getAvailability()).isEqualTo(Product.Availability.IN_STOCK);
      assertThat(result.getAvailableQuantity().getValue()).isEqualTo(150);
    }

    @Test
    @DisplayName("should map images array")
    void shouldMapImages() {
      Product result = simpleEngine.map(productClean, "product-simple", Product.class);

      assertThat(result.getImagesCount()).isEqualTo(2);
      assertThat(result.getImages(0).getUri()).contains("rog-strix-g18-front.jpg");
      assertThat(result.getImages(0).getWidth()).isEqualTo(1200);
      assertThat(result.getImages(0).getHeight()).isEqualTo(800);
    }

    @Test
    @DisplayName("should map rating object")
    void shouldMapRating() {
      Product result = simpleEngine.map(productClean, "product-simple", Product.class);

      assertThat(result.hasRating()).isTrue();
      assertThat(result.getRating().getRatingCount()).isEqualTo(245);
      assertThat(result.getRating().getAverageRating()).isEqualTo(4.7f);
    }

    @Test
    @DisplayName("should map colorInfo")
    void shouldMapColorInfo() {
      Product result = simpleEngine.map(productClean, "product-simple", Product.class);

      assertThat(result.hasColorInfo()).isTrue();
      assertThat(result.getColorInfo().getColorFamiliesList()).containsExactly("Black");
      assertThat(result.getColorInfo().getColorsList()).containsExactly("Eclipse Gray");
    }
  }

  @Nested
  @DisplayName("Product - Chaotic JSON + Complex YAML")
  class ProductChaoticTests {

    @Test
    @DisplayName("should map fields with different names")
    void shouldMapFieldsWithDifferentNames() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      // sku -> id
      assertThat(result.getId()).isEqualTo("LEGACY-12345-XYZ");

      // product_name -> title
      assertThat(result.getTitle()).isEqualTo("Dell XPS 15 Laptop");

      // long_description -> description
      assertThat(result.getDescription()).contains("15.6-inch premium laptop");

      // product_url -> uri
      assertThat(result.getUri()).isEqualTo("https://legacy-store.com/dell-xps-15");
    }

    @Test
    @DisplayName("should transform pipe_categories to categories array")
    void shouldTransformCategoryBreadcrumb() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      // "electronics|computers|laptops|business|premium" -> array (pipe delimiter)
      assertThat(result.getCategoriesList())
          .containsExactly("electronics", "computers", "laptops", "business", "premium");
    }

    @Test
    @DisplayName("should transform brand_name to brands array")
    void shouldTransformBrandToArray() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      // Single "Dell" -> ["Dell"]
      assertThat(result.getBrandsList()).containsExactly("Dell");
    }

    @Test
    @DisplayName("should map pricing object to priceInfo")
    void shouldMapPricingToPriceInfo() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      assertThat(result.hasPriceInfo()).isTrue();
      // current_price -> price
      assertThat(result.getPriceInfo().getPrice()).isEqualTo(1599.00f);
      // list_price -> originalPrice
      assertThat(result.getPriceInfo().getOriginalPrice()).isEqualTo(1899.00f);
      assertThat(result.getPriceInfo().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("should transform stock_status to availability enum")
    void shouldTransformStockStatusToEnum() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      // "available" -> IN_STOCK
      assertThat(result.getAvailability()).isEqualTo(Product.Availability.IN_STOCK);
      assertThat(result.getAvailableQuantity().getValue()).isEqualTo(75);
    }

    @Test
    @DisplayName("should transform image_gallery strings to images array")
    void shouldTransformImageGalleryToImages() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      assertThat(result.getImagesCount()).isEqualTo(3);
      assertThat(result.getImages(0).getUri()).isEqualTo("https://legacy-cdn.com/dell-xps-front.png");
    }

    @Test
    @DisplayName("should transform product_materials CSV to array")
    void shouldTransformMaterialsCsvToArray() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      // "Carbon Fiber, Aluminum" -> ["Carbon Fiber", "Aluminum"]
      assertThat(result.getMaterialsList()).containsExactly("Carbon Fiber", "Aluminum");
    }

    @Test
    @DisplayName("should transform keywords to tags array")
    void shouldTransformKeywordsToTags() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      assertThat(result.getTagsList())
          .contains("business laptop", "ultrabook", "xps", "dell", "professional");
    }

    @Test
    @DisplayName("should transform locale format using replaceChars")
    void shouldTransformLocaleFormat() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      // "en_US" -> "en-US"
      assertThat(result.getLanguageCode()).isEqualTo("en-US");
    }

    @Test
    @DisplayName("should map customer_reviews to rating")
    void shouldMapCustomerReviewsToRating() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      assertThat(result.hasRating()).isTrue();
      // total_reviews -> ratingCount
      assertThat(result.getRating().getRatingCount()).isEqualTo(892);
      // avg_rating -> averageRating
      assertThat(result.getRating().getAverageRating()).isEqualTo(4.5f);
    }

    @Test
    @DisplayName("should transform color fields to colorInfo")
    void shouldTransformColorFieldsToColorInfo() {
      Product result = complexEngine.map(productChaotic, "product-complex", Product.class);

      assertThat(result.hasColorInfo()).isTrue();
      assertThat(result.getColorInfo().getColorFamiliesList()).containsExactly("Silver");
      assertThat(result.getColorInfo().getColorsList()).containsExactly("Platinum Silver");
    }
  }

  // ==================== Validation Tests ====================

  // ==================== Helper Methods ====================

  private static JsonNode loadJson(String resourcePath) throws IOException {
    try (InputStream is = EndToEndMappingTest.class.getClassLoader()
        .getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Resource not found: " + resourcePath);
      }
      String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return objectMapper.readTree(content);
    }
  }
}
