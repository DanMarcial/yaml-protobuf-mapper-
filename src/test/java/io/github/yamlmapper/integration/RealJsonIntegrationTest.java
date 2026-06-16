package io.github.yamlmapper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.UserEvent;
import io.github.yamlmapper.core.MappingEngine;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests using real JSON samples from production-like events.
 *
 * <p>These tests verify the complete mapping pipeline with realistic data
 * including all field types, transforms, and edge cases.
 */
class RealJsonIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(RealJsonIntegrationTest.class);

  private static MappingEngine engine;
  private static ObjectMapper objectMapper;

  @BeforeAll
  static void setUpEngine() {
    objectMapper = new ObjectMapper();

    engine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withConfig("classpath:mapping/search-event.yaml")
        .withConfig("classpath:mapping/add-to-cart.yaml")
        .withConfig("classpath:mapping/detail-page-view.yaml")
            .withConfig("classpath:mapping/search.yaml")
        .injectEventType(true)
        .build();
  }

  private static String loadJsonResource(String path) throws Exception {
    try (InputStream is = RealJsonIntegrationTest.class.getClassLoader()
        .getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalArgumentException("Resource not found: " + path);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static JsonNode loadJsonArrayFirstElement(String path) throws Exception {
    String content = loadJsonResource(path);
    JsonNode array = objectMapper.readTree(content);
    return array.get(0);
  }

  @Nested
  @DisplayName("Search Event Mapping")
  class SearchEventTests {

    @Test
    @DisplayName("should map complete search event from real JSON")
    void shouldMapCompleteSearchEvent() throws Exception {
      JsonNode json = loadJsonArrayFirstElement("json/search - obfuscated.json");

      UserEvent event = engine.map(json, "search-event", UserEvent.class);

      // Basic fields
      assertThat(event.getVisitorId()).isEqualTo("visitor-123");
      assertThat(event.getEventType()).isEqualTo("search-event");

      // Search-specific fields
      assertThat(event.getSearchQuery()).isEqualTo("nike running shoes");
      assertThat(event.getOffset()).isEqualTo(0);
      assertThat(event.getOrderBy()).isEqualTo("price desc");

      // URI and referrer
      assertThat(event.getUri()).isEqualTo("https://example.com/search?q=nike+running+shoes");
      assertThat(event.getReferrerUri()).isEqualTo("https://google.com");

      // Attribution
      assertThat(event.getAttributionToken()).isEqualTo("token-xyz");

      // Categories (singleItemToArray transform)
      assertThat(event.getPageCategoriesList())
          .containsExactly("Sales > Black Friday > Shoes");
    }

    @Test
    @DisplayName("should inject eventType from configId")
    void shouldInjectEventType() throws Exception {
      JsonNode json = loadJsonArrayFirstElement("json/search - obfuscated.json");

      UserEvent event = engine.map(json, "search-event", UserEvent.class);

      assertThat(event.getEventType()).isEqualTo("search-event");
    }

    @Test
    @DisplayName("should handle productsIds JSON string array")
    void shouldHandleProductsIdsJsonStringArray() throws Exception {
      // The productsIds field contains a JSON string that needs parsing
      // "[\"PROD001\",\"PROD002\",\"PROD003\",\"PROD004\"]"
      // This would need a custom transform to parse, for now we test the raw mapping
      JsonNode json = loadJsonArrayFirstElement("json/search - obfuscated.json");

      UserEvent event = engine.map(json, "search-event", UserEvent.class);

      // With stringArrayToObjectArray transform, but input is a string not array
      // This tests graceful handling of unexpected input format
      assertThat(event).isNotNull();
    }
  }

  @Nested
  @DisplayName("Add to Cart Event Mapping")
  class AddToCartEventTests {

    @Test
    @DisplayName("should map add-to-cart event from UPP")
    void shouldMapAddToCartEventFromUpp() throws Exception {
      JsonNode json = loadJsonArrayFirstElement("json/upp - add to cart - sample - obfuscated.json");

      UserEvent event = engine.map(json, "add-to-cart", UserEvent.class);

      // Basic fields
      assertThat(event.getVisitorId()).isEqualTo("2a2bd**************************b0925");
      assertThat(event.getEventType()).isEqualTo("add-to-cart");

      // URI
      assertThat(event.getUri()).isEqualTo("/upp/add_to_cart_action");

      // Note: productDetails is empty because test JSON has flat item_id,
      // not the productDetails array structure expected by the YAML config.
      // A custom transform would be needed to convert flat item_id to ProductDetail objects.
      assertThat(event.getProductDetailsList()).isEmpty();
    }

    @Test
    @DisplayName("should map add-to-cart event from VIP")
    void shouldMapAddToCartEventFromVip() throws Exception {
      JsonNode json = loadJsonArrayFirstElement("json/vip - add to cart - sample - obfuscated.json");

      UserEvent event = engine.map(json, "add-to-cart", UserEvent.class);

      assertThat(event.getVisitorId()).isNotEmpty();
      assertThat(event.getEventType()).isEqualTo("add-to-cart");
    }

    @Test
    @DisplayName("should handle null cartId gracefully")
    void shouldHandleNullCartIdGracefully() throws Exception {
      JsonNode json = loadJsonArrayFirstElement("json/upp - add to cart - sample - obfuscated.json");

      // cartId is null in the JSON
      UserEvent event = engine.map(json, "add-to-cart", UserEvent.class);

      assertThat(event.getCartId()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Detail Page View Event Mapping")
  class DetailPageViewEventTests {

    @Test
    @DisplayName("should map PDP (Product Detail Page) event")
    void shouldMapPdpEvent() throws Exception {
      JsonNode json = loadJsonArrayFirstElement("json/pdp - sample v2 - obfuscated.json");

      UserEvent event = engine.map(json, "detail-page-view", UserEvent.class);

      assertThat(event.getVisitorId()).isEqualTo("0AE53**************************09091");
      assertThat(event.getEventType()).isEqualTo("detail-page-view");
      assertThat(event.getUri()).isEqualTo("/pdp");

      // Note: productDetails is empty because test JSON has flat item_id,
      // not the productDetails array structure expected by the YAML config.
      assertThat(event.getProductDetailsList()).isEmpty();
    }

    @Test
    @DisplayName("should map VIP (View Item Page) event")
    void shouldMapVipEvent() throws Exception {
      JsonNode json = loadJsonArrayFirstElement("json/vip - sample v2-obfuscated.json");

      UserEvent event = engine.map(json, "detail-page-view", UserEvent.class);

      assertThat(event.getVisitorId()).isEqualTo("CF55A**************************7E039");
      assertThat(event.getEventType()).isEqualTo("detail-page-view");
      assertThat(event.getUri()).isEqualTo("/vip");
    }

    @Test
    @DisplayName("should handle flat JSON structure gracefully")
    void shouldHandleFlatJsonStructureGracefully() throws Exception {
      JsonNode json = loadJsonArrayFirstElement("json/pdp - sample v2 - obfuscated.json");

      UserEvent event = engine.map(json, "detail-page-view", UserEvent.class);

      // Note: The test JSON has flat item_id field, not productDetails array.
      // Without a custom transform to convert flat fields to ProductDetail objects,
      // productDetails remains empty. This test verifies graceful handling.
      assertThat(event.getProductDetailsList()).isEmpty();

      // But basic fields should still be mapped correctly
      assertThat(event.getVisitorId()).isNotEmpty();
      assertThat(event.getEventType()).isEqualTo("detail-page-view");
    }
  }

  @Nested
  @DisplayName("Purchase Complete Event Mapping")
  class PurchaseCompleteEventTests {

    @Test
    @DisplayName("should map purchase complete event basic fields")
    void shouldMapPurchaseCompleteBasicFields() throws Exception {
      JsonNode json = loadJsonArrayFirstElement("json/purchase complete -obfuscated.json");

      // Use detail-page-view config for basic mapping (purchase would need its own config)
      UserEvent event = engine.map(json, "detail-page-view", UserEvent.class);

      assertThat(event.getVisitorId()).isEqualTo("7478A**************************F660C");
      assertThat(event.getUri()).isEqualTo("/cart/checkout/congrats");
    }
  }

  @Nested
  @DisplayName("Cross-cutting Concerns")
  class CrossCuttingTests {

    @Test
    @DisplayName("should consistently inject eventType for all event types")
    void shouldConsistentlyInjectEventType() throws Exception {
      JsonNode searchJson = loadJsonArrayFirstElement("json/search - obfuscated.json");
      JsonNode addToCartJson = loadJsonArrayFirstElement("json/upp - add to cart - sample - obfuscated.json");
      JsonNode pdpJson = loadJsonArrayFirstElement("json/pdp - sample v2 - obfuscated.json");

      UserEvent searchEvent = engine.map(searchJson, "search-event", UserEvent.class);
      UserEvent addToCartEvent = engine.map(addToCartJson, "add-to-cart", UserEvent.class);
      UserEvent pdpEvent = engine.map(pdpJson, "detail-page-view", UserEvent.class);

      assertThat(searchEvent.getEventType()).isEqualTo("search-event");
      assertThat(addToCartEvent.getEventType()).isEqualTo("add-to-cart");
      assertThat(pdpEvent.getEventType()).isEqualTo("detail-page-view");
    }

    @Test
    @DisplayName("should handle obfuscated data without errors")
    void shouldHandleObfuscatedDataWithoutErrors() throws Exception {
      // All JSON files contain obfuscated/masked data - ensure mapping works
      String[] jsonFiles = {
          "json/search - obfuscated.json",
          "json/purchase complete -obfuscated.json",
          "json/upp - add to cart - sample - obfuscated.json",
          "json/pdp - sample v2 - obfuscated.json",
          "json/vip - sample v2-obfuscated.json"
      };

      for (String jsonFile : jsonFiles) {
        JsonNode json = loadJsonArrayFirstElement(jsonFile);
        // Just verify no exceptions are thrown
        UserEvent event = engine.map(json, "detail-page-view", UserEvent.class);
        assertThat(event.getVisitorId()).isNotEmpty();
      }
    }

    @Test
    @DisplayName("should map sessionId consistently across all events")
    void shouldMapSessionIdConsistently() throws Exception {
      JsonNode searchJson = loadJsonArrayFirstElement("json/search - obfuscated.json");
      JsonNode addToCartJson = loadJsonArrayFirstElement("json/upp - add to cart - sample - obfuscated.json");

      UserEvent searchEvent = engine.map(searchJson, "search-event", UserEvent.class);
      UserEvent addToCartEvent = engine.map(addToCartJson, "add-to-cart", UserEvent.class);

      assertThat(searchEvent.getSessionId()).isEqualTo("session-456");
      assertThat(addToCartEvent.getSessionId()).isEqualTo("75e9d**************************93bd7");
    }
  }

  @Nested
  @DisplayName("Edge Cases from Real Data")
  class EdgeCasesTests {

    @Test
    @DisplayName("should handle empty string fields")
    void shouldHandleEmptyStringFields() throws Exception {
      // purchase complete has empty http_referer and httpUrl
      JsonNode json = loadJsonArrayFirstElement("json/purchase complete -obfuscated.json");

      UserEvent event = engine.map(json, "detail-page-view", UserEvent.class);

      // Empty strings should not cause errors
      assertThat(event).isNotNull();
    }

    @Test
    @DisplayName("should handle experimentsIds as JSON string")
    void shouldHandleExperimentsIdsAsJsonString() throws Exception {
      // experimentsIds is a JSON string containing an array
      // "[{\"key\":\"exp-search-ranking\",\"value\":\"variant-a\"}...]"
      JsonNode json = loadJsonArrayFirstElement("json/search - obfuscated.json");

      UserEvent event = engine.map(json, "search-event", UserEvent.class);

      // Should map without errors even if experimentsIds isn't directly mapped
      assertThat(event).isNotNull();
    }

    @Test
    @DisplayName("should handle timezone in eventTime")
    void shouldHandleTimezoneInEventTime() throws Exception {
      // eventTime: "2025-01-15T10:30:00.000-0300" has timezone offset
      JsonNode json = loadJsonArrayFirstElement("json/search - obfuscated.json");

      // eventTime could be mapped to eventTime field with timestamp type
      // For now, just ensure the JSON parses
      UserEvent event = engine.map(json, "search-event", UserEvent.class);
      assertThat(event).isNotNull();
    }
  }

  @Nested
  @DisplayName("Performance with Real Data")
  class PerformanceTests {

    @Test
    @DisplayName("should map events efficiently")
    void shouldMapEventsEfficiently() throws Exception {
      JsonNode json = loadJsonArrayFirstElement("json/search - obfuscated.json");

      // Warm up
      for (int i = 0; i < 100; i++) {
        engine.map(json, "search-event", UserEvent.class);
      }

      // Measure
      long start = System.nanoTime();
      int iterations = 10000;
      for (int i = 0; i < iterations; i++) {
        engine.map(json, "search-event", UserEvent.class);
      }
      long elapsed = System.nanoTime() - start;

      double avgNanos = (double) elapsed / iterations;
      log.info("Average mapping time: {} ns", String.format("%.2f", avgNanos));

      // Should be well under 1ms per mapping
      assertThat(avgNanos).isLessThan(1_000_000);
    }
  }
}
