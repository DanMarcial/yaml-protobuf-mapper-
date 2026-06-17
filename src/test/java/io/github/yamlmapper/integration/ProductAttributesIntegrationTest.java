package io.github.yamlmapper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.CustomAttribute;
import com.google.cloud.retail.v2.Product;
import io.github.yamlmapper.core.MappingEngine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for Product with attributes map using fieldsToAttributeMap transform.
 *
 * <p>Tests the complete flow:
 * <ul>
 *   <li>JSON with literal colon field names (e.g., "items:group_ids")</li>
 *   <li>fieldsToAttributeMap transform converts to CustomAttribute structure</li>
 *   <li>Map builder creates map&lt;string, CustomAttribute&gt;</li>
 * </ul>
 */
@DisplayName("Product Attributes Integration Test")
class ProductAttributesIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(ProductAttributesIntegrationTest.class);

  private static ObjectMapper objectMapper;
  private static MappingEngine engine;
  private static JsonNode productJson;

  @BeforeAll
  static void setupAll() throws IOException {
    objectMapper = new ObjectMapper();

    // Load test JSON
    productJson = loadJson("integration/json/product-attributes.json");

    // Build engine with attributes mapping
    engine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withConfig("classpath:integration/mapping/product-attributes.yaml")
        .injectEventType(false)
        .build();
  }

  @Test
  @DisplayName("should map id field from colon-named source")
  void shouldMapIdField() {
    Product result = engine.map(productJson, "product-attributes", Product.class);

    assertThat(result.getId()).isEqualTo("PROD-12345");
  }

  @Test
  @DisplayName("should create attributes map with CustomAttribute objects")
  void shouldCreateAttributesMap() {
    Product result = engine.map(productJson, "product-attributes", Product.class);

    Map<String, CustomAttribute> attributes = result.getAttributesMap();
    assertThat(attributes).isNotEmpty();
  }

  @Test
  @DisplayName("should map items:group_ids to text attribute")
  void shouldMapGroupIdsAttribute() {
    Product result = engine.map(productJson, "product-attributes", Product.class);

    CustomAttribute groupIds = result.getAttributesOrDefault("items:group_ids", null);
    assertThat(groupIds).isNotNull();
    assertThat(groupIds.getTextList()).containsExactly("GROUP-A", "GROUP-B", "GROUP-C");
  }

  @Test
  @DisplayName("should map items:keywords to text attribute")
  void shouldMapKeywordsAttribute() {
    Product result = engine.map(productJson, "product-attributes", Product.class);

    CustomAttribute keywords = result.getAttributesOrDefault("items:keywords", null);
    assertThat(keywords).isNotNull();
    assertThat(keywords.getTextList()).containsExactly("laptop", "gaming", "portable", "high-performance");
  }

  @Test
  @DisplayName("should map variations:facet:availability to text attribute")
  void shouldMapAvailabilityAttribute() {
    Product result = engine.map(productJson, "product-attributes", Product.class);

    CustomAttribute availability = result.getAttributesOrDefault("variations:facet:availability", null);
    assertThat(availability).isNotNull();
    assertThat(availability.getTextList()).containsExactly("in_stock", "online", "store");
  }

  @Test
  @DisplayName("should map variations:facet:PTC_OMNI_BRAND_SUB_BRND to text attribute")
  void shouldMapBrandAttribute() {
    Product result = engine.map(productJson, "product-attributes", Product.class);

    CustomAttribute brand = result.getAttributesOrDefault("variations:facet:PTC_OMNI_BRAND_SUB_BRND", null);
    assertThat(brand).isNotNull();
    assertThat(brand.getTextList()).containsExactly("Dell", "Alienware");
  }

  @Test
  @DisplayName("should have correct number of attributes")
  void shouldHaveCorrectNumberOfAttributes() {
    Product result = engine.map(productJson, "product-attributes", Product.class);

    // 4 attributes: items:group_ids, items:keywords, variations:facet:availability, variations:facet:PTC_OMNI_BRAND_SUB_BRND
    assertThat(result.getAttributesCount()).isEqualTo(4);
  }

  @Test
  @DisplayName("should print complete Product object")
  void shouldPrintCompleteProductObject() {
    Product result = engine.map(productJson, "product-attributes", Product.class);

    log.info("\n========== COMPLETE PRODUCT OBJECT ==========");
    log.info("{}", result);
    log.info("==============================================\n");

    // Also print as JSON for better readability
    log.info("========== AS JSON ==========");
    try {
      String json = com.google.protobuf.util.JsonFormat.printer()
          .includingDefaultValueFields()
          .print(result);
      log.info("{}", json);
    } catch (Exception e) {
      log.warn("Error printing JSON: {}", e.getMessage());
    }
    log.info("=============================\n");

    assertThat(result).isNotNull();
  }

  private static JsonNode loadJson(String resourcePath) throws IOException {
    try (InputStream is = ProductAttributesIntegrationTest.class.getClassLoader()
        .getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Resource not found: " + resourcePath);
      }
      String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return objectMapper.readTree(content);
    }
  }
}
