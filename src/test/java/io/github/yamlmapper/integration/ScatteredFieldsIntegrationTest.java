package io.github.yamlmapper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.Product;
import io.github.yamlmapper.core.MappingEngine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for mapping JSON with scattered fields.
 *
 * <p>Tests the scenario where data for a nested object (like PriceInfo)
 * is distributed across different nodes in the JSON root:
 *
 * <pre>{@code
 * {
 *   "price": { "current": 999.99 },
 *   "past": { "price": 1199.99 },
 *   "currency": { "code": "USD" }
 * }
 * }</pre>
 *
 * <p>Solution: Use {@code source: ["."]} to keep the root context
 * and access fields using dot notation paths.
 */
@DisplayName("Scattered Fields Integration Test")
class ScatteredFieldsIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(ScatteredFieldsIntegrationTest.class);

  private static ObjectMapper objectMapper;
  private static MappingEngine engine;
  private static JsonNode productJson;

  @BeforeAll
  static void setupAll() throws IOException {
    objectMapper = new ObjectMapper();

    productJson = loadJson("integration/json/product-scattered-fields.json");

    engine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withConfig("classpath:integration/mapping/product-scattered.yaml")
        .injectEventType(false)
        .enablePostMappingValidation(false)
        .build();
  }

  @Test
  @DisplayName("should map id and title from root")
  void shouldMapIdAndTitle() {
    Product result = engine.map(productJson, "product-scattered", Product.class);

    assertThat(result.getId()).isEqualTo("PROD-123");
    assertThat(result.getTitle()).isEqualTo("Gaming Laptop");
  }

  @Test
  @DisplayName("should map priceInfo.price from price.current")
  void shouldMapPriceFromPriceCurrent() {
    Product result = engine.map(productJson, "product-scattered", Product.class);

    assertThat(result.hasPriceInfo()).isTrue();
    assertThat(result.getPriceInfo().getPrice()).isEqualTo(999.99f);
  }

  @Test
  @DisplayName("should map priceInfo.originalPrice from past.price")
  void shouldMapOriginalPriceFromPastPrice() {
    Product result = engine.map(productJson, "product-scattered", Product.class);

    assertThat(result.hasPriceInfo()).isTrue();
    assertThat(result.getPriceInfo().getOriginalPrice()).isEqualTo(1199.99f);
  }

  @Test
  @DisplayName("should map priceInfo.currencyCode from currency.code")
  void shouldMapCurrencyCodeFromCurrencyCode() {
    Product result = engine.map(productJson, "product-scattered", Product.class);

    assertThat(result.hasPriceInfo()).isTrue();
    assertThat(result.getPriceInfo().getCurrencyCode()).isEqualTo("USD");
  }

  @Test
  @DisplayName("should map availability from stock.status")
  void shouldMapAvailabilityFromStockStatus() {
    Product result = engine.map(productJson, "product-scattered", Product.class);

    assertThat(result.getAvailability()).isEqualTo(Product.Availability.IN_STOCK);
  }

  @Test
  @DisplayName("should print complete Product with scattered fields")
  void shouldPrintCompleteProduct() {
    Product result = engine.map(productJson, "product-scattered", Product.class);

    log.info("\n========== SCATTERED FIELDS TEST ==========");
    log.info("Input JSON has fields scattered across nodes:");
    log.info("  - price.current -> priceInfo.price");
    log.info("  - past.price -> priceInfo.originalPrice");
    log.info("  - currency.code -> priceInfo.currencyCode");
    log.info("");
    log.info("Result Product:");
    log.info("{}", result);
    log.info("============================================\n");

    assertThat(result).isNotNull();
  }

  private static JsonNode loadJson(String resourcePath) throws IOException {
    try (InputStream is = ScatteredFieldsIntegrationTest.class.getClassLoader()
        .getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Resource not found: " + resourcePath);
      }
      String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return objectMapper.readTree(content);
    }
  }
}
