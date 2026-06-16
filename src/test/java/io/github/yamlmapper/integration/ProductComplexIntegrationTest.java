package io.github.yamlmapper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.CustomAttribute;
import com.google.cloud.retail.v2.Product;
import com.google.protobuf.util.JsonFormat;
import io.github.yamlmapper.core.MappingEngine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comprehensive integration test for Product mapping with ALL transforms.
 *
 * <p>This test validates the complete mapping flow including:
 * <ul>
 *   <li>Basic field mappings with fallback sources</li>
 *   <li>splitToArray - pipe and comma delimited strings</li>
 *   <li>singleItemToArray - wrapping single values in arrays</li>
 *   <li>mapValue - mapping strings to enum values</li>
 *   <li>stringsToImages - converting URL strings to Image objects</li>
 *   <li>replaceChars - locale format conversion</li>
 *   <li>parseKeyValuePairs - parsing facets string to CustomAttribute map</li>
 *   <li>fieldsToAttributeMap - converting metadata:* fields to attributes</li>
 *   <li>MERGE DEFINITIONS - combining multiple attribute sources</li>
 * </ul>
 *
 * <p>Uses the chaotic JSON format representing real-world legacy data.
 */
@DisplayName("Product Complex Integration Test - ALL Transforms")
class ProductComplexIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(ProductComplexIntegrationTest.class);

  private static ObjectMapper objectMapper;
  private static MappingEngine engine;
  private static JsonNode chaoticJson;

  @BeforeAll
  static void setupAll() throws IOException {
    objectMapper = new ObjectMapper();
    chaoticJson = loadJson("integration/json/product-chaotic.json");

    engine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withConfig("classpath:integration/mapping/product-complex.yaml")
        .injectEventType(false)
        .enablePostMappingValidation(false)
        .build();
  }

  // ==================== Basic Fields ====================

  @Nested
  @DisplayName("Basic Field Mappings")
  class BasicFieldTests {

    @Test
    @DisplayName("should map id from sku (first fallback)")
    void shouldMapIdFromSku() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);
      assertThat(result.getId()).isEqualTo("LEGACY-12345-XYZ");
    }

    @Test
    @DisplayName("should map title from product_name")
    void shouldMapTitle() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);
      assertThat(result.getTitle()).isEqualTo("Dell XPS 15 Laptop");
    }

    @Test
    @DisplayName("should map description from long_description")
    void shouldMapDescription() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);
      assertThat(result.getDescription()).contains("15.6-inch premium laptop");
    }

    @Test
    @DisplayName("should map uri from product_url")
    void shouldMapUri() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);
      assertThat(result.getUri()).isEqualTo("https://legacy-store.com/dell-xps-15");
    }

    @Test
    @DisplayName("should map availableQuantity with default")
    void shouldMapAvailableQuantity() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);
      assertThat(result.getAvailableQuantity().getValue()).isEqualTo(75);
    }
  }

  // ==================== splitToArray Transform ====================

  @Nested
  @DisplayName("splitToArray Transform")
  class SplitToArrayTests {

    @Test
    @DisplayName("should split pipe_categories by pipe delimiter")
    void shouldSplitPipeCategories() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getCategoriesList())
          .containsExactly("electronics", "computers", "laptops", "business", "premium");
    }

    @Test
    @DisplayName("should split materials by comma")
    void shouldSplitMaterials() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getMaterialsList())
          .containsExactly("Carbon Fiber", "Aluminum");
    }

    @Test
    @DisplayName("should split keywords to tags")
    void shouldSplitKeywordsToTags() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getTagsList())
          .containsExactly("business laptop", "ultrabook", "xps", "dell", "professional");
    }
  }

  // ==================== singleItemToArray Transform ====================

  @Nested
  @DisplayName("singleItemToArray Transform")
  class SingleItemToArrayTests {

    @Test
    @DisplayName("should wrap brand_name in array")
    void shouldWrapBrandInArray() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getBrandsList()).containsExactly("Dell");
    }

    @Test
    @DisplayName("should wrap color_family in colorFamilies array")
    void shouldWrapColorFamilyInArray() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getColorInfo().getColorFamiliesList()).containsExactly("Silver");
    }

    @Test
    @DisplayName("should wrap color in colors array")
    void shouldWrapColorInArray() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getColorInfo().getColorsList()).containsExactly("Platinum Silver");
    }
  }

  // ==================== mapValue Transform ====================

  @Nested
  @DisplayName("mapValue Transform")
  class MapValueTests {

    @Test
    @DisplayName("should map 'available' to IN_STOCK enum")
    void shouldMapAvailableToInStock() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getAvailability()).isEqualTo(Product.Availability.IN_STOCK);
    }
  }

  // ==================== stringsToImages Transform ====================

  @Nested
  @DisplayName("stringsToImages Transform")
  class StringsToImagesTests {

    @Test
    @DisplayName("should convert image_gallery URLs to Image objects")
    void shouldConvertImageGalleryToImages() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getImagesCount()).isEqualTo(3);
      assertThat(result.getImages(0).getUri()).isEqualTo("https://legacy-cdn.com/dell-xps-front.png");
      assertThat(result.getImages(1).getUri()).isEqualTo("https://legacy-cdn.com/dell-xps-open.png");
      assertThat(result.getImages(2).getUri()).isEqualTo("https://legacy-cdn.com/dell-xps-ports.png");
    }
  }

  // ==================== replaceChars Transform ====================

  @Nested
  @DisplayName("replaceChars Transform")
  class ReplaceCharsTests {

    @Test
    @DisplayName("should convert locale format from en_US to en-US")
    void shouldConvertLocaleFormat() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getLanguageCode()).isEqualTo("en-US");
    }
  }

  // ==================== Nested Object Mapping ====================

  @Nested
  @DisplayName("Nested Object Mapping")
  class NestedObjectTests {

    @Test
    @DisplayName("should map priceInfo nested object")
    void shouldMapPriceInfo() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getPriceInfo().getPrice()).isEqualTo(1599.00f);
      assertThat(result.getPriceInfo().getOriginalPrice()).isEqualTo(1899.00f);
      assertThat(result.getPriceInfo().getCurrencyCode()).isEqualTo("USD");
      assertThat(result.getPriceInfo().getCost()).isEqualTo(1200.00f);
    }

    @Test
    @DisplayName("should map rating nested object")
    void shouldMapRating() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.getRating().getRatingCount()).isEqualTo(892);
      assertThat(result.getRating().getAverageRating()).isEqualTo(4.5f);
    }
  }

  // ==================== Timestamp Mapping ====================

  @Nested
  @DisplayName("Timestamp Mapping")
  class TimestampTests {

    @Test
    @DisplayName("should parse ISO8601 timestamp")
    void shouldParseIso8601Timestamp() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      assertThat(result.hasExpireTime()).isTrue();
      assertThat(result.getExpireTime().getSeconds()).isGreaterThan(0);
    }
  }

  // ==================== MERGED ATTRIBUTES ====================

  @Nested
  @DisplayName("Merged Attributes (parseKeyValuePairs + fieldsToAttributeMap)")
  class MergedAttributesTests {

    @Test
    @DisplayName("should merge attributes from all three sources")
    void shouldMergeAttributesFromAllSources() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      // From facets (parseKeyValuePairs)
      assertThat(result.getAttributesOrDefault("Price", null)).isNotNull();
      assertThat(result.getAttributesOrDefault("Display Size", null)).isNotNull();
      assertThat(result.getAttributesOrDefault("Processor", null)).isNotNull();

      // From metadata:* fields (fieldsToAttributeMap)
      assertThat(result.getAttributesOrDefault("metadata:PTC_OMNI_BRAND_PRIMARY", null)).isNotNull();
      assertThat(result.getAttributesOrDefault("metadata:PTC_OMNI_CATEGORY", null)).isNotNull();

      // From facet:* fields (fieldsToAttributeMap)
      assertThat(result.getAttributesOrDefault("facet:display_type", null)).isNotNull();
      assertThat(result.getAttributesOrDefault("facet:resolution", null)).isNotNull();
    }

    @Test
    @DisplayName("should parse Price from facets as numeric")
    void shouldParsePriceAsNumeric() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      CustomAttribute price = result.getAttributesOrDefault("Price", null);
      assertThat(price).isNotNull();
      assertThat(price.getNumbersList()).containsExactly(1599.00);
    }

    @Test
    @DisplayName("should parse Display Size as numeric")
    void shouldParseDisplaySizeAsNumeric() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      CustomAttribute displaySize = result.getAttributesOrDefault("Display Size", null);
      assertThat(displaySize).isNotNull();
      assertThat(displaySize.getNumbersList()).containsExactly(15.6);
    }

    @Test
    @DisplayName("should parse Weight as numeric")
    void shouldParseWeightAsNumeric() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      CustomAttribute weight = result.getAttributesOrDefault("Weight", null);
      assertThat(weight).isNotNull();
      assertThat(weight.getNumbersList()).containsExactly(4.5);
    }

    @Test
    @DisplayName("should group Feature values from facets")
    void shouldGroupFeatureValues() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      CustomAttribute feature = result.getAttributesOrDefault("Feature", null);
      assertThat(feature).isNotNull();
      assertThat(feature.getTextList())
          .containsExactly("Touchscreen", "Backlit Keyboard", "Thunderbolt 4");
    }

    @Test
    @DisplayName("should parse Processor as text")
    void shouldParseProcessorAsText() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      CustomAttribute processor = result.getAttributesOrDefault("Processor", null);
      assertThat(processor).isNotNull();
      assertThat(processor.getTextList()).containsExactly("Intel Core i7");
    }

    @Test
    @DisplayName("should map metadata:PTC_OMNI_BRAND_PRIMARY")
    void shouldMapMetadataBrand() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      CustomAttribute brand = result.getAttributesOrDefault("metadata:PTC_OMNI_BRAND_PRIMARY", null);
      assertThat(brand).isNotNull();
      assertThat(brand.getTextList()).containsExactly("Dell");
    }

    @Test
    @DisplayName("should map metadata:PTC_OMNI_CATEGORY")
    void shouldMapMetadataCategory() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      CustomAttribute category = result.getAttributesOrDefault("metadata:PTC_OMNI_CATEGORY", null);
      assertThat(category).isNotNull();
      assertThat(category.getTextList()).containsExactly("Laptops");
    }

    @Test
    @DisplayName("should map metadata:list_price as numeric")
    void shouldMapMetadataListPrice() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      CustomAttribute listPrice = result.getAttributesOrDefault("metadata:list_price", null);
      assertThat(listPrice).isNotNull();
      assertThat(listPrice.getNumbersList()).containsExactly(1899.00);
    }

    @Test
    @DisplayName("should map facet:display_type")
    void shouldMapFacetDisplayType() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      CustomAttribute displayType = result.getAttributesOrDefault("facet:display_type", null);
      assertThat(displayType).isNotNull();
      assertThat(displayType.getTextList()).containsExactly("OLED");
    }

    @Test
    @DisplayName("should map facet:resolution")
    void shouldMapFacetResolution() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      CustomAttribute resolution = result.getAttributesOrDefault("facet:resolution", null);
      assertThat(resolution).isNotNull();
      assertThat(resolution.getTextList()).containsExactly("3456x2160");
    }

    @Test
    @DisplayName("should have correct total number of attributes")
    void shouldHaveCorrectNumberOfAttributes() {
      Product result = engine.map(chaoticJson, "product-complex", Product.class);

      // facets: Price, Display Size, Weight, RAM, Storage, Processor, Graphics,
      //         Battery Life, Feature (3 values grouped), Condition, Warranty = 12 keys
      // metadata: 5 fields
      // facet: 3 fields
      // Total: at least 20 attributes
      assertThat(result.getAttributesCount()).isGreaterThanOrEqualTo(18);
    }
  }

  // ==================== Complete Product Validation ====================

  @Test
  @DisplayName("should create complete Product with all fields populated")
  void shouldCreateCompleteProduct() {
    Product result = engine.map(chaoticJson, "product-complex", Product.class);

    // Basic fields
    assertThat(result.getId()).isNotBlank();
    assertThat(result.getTitle()).isNotBlank();
    assertThat(result.getDescription()).isNotBlank();
    assertThat(result.getUri()).isNotBlank();

    // Arrays
    assertThat(result.getCategoriesCount()).isGreaterThan(0);
    assertThat(result.getBrandsCount()).isGreaterThan(0);
    assertThat(result.getImagesCount()).isGreaterThan(0);
    assertThat(result.getMaterialsCount()).isGreaterThan(0);
    assertThat(result.getTagsCount()).isGreaterThan(0);

    // Nested objects
    assertThat(result.hasPriceInfo()).isTrue();
    assertThat(result.hasColorInfo()).isTrue();
    assertThat(result.hasRating()).isTrue();

    // Enum
    assertThat(result.getAvailability()).isNotNull();

    // Timestamp
    assertThat(result.hasExpireTime()).isTrue();

    // Attributes map
    assertThat(result.getAttributesCount()).isGreaterThan(0);
  }

  @Test
  @DisplayName("should print complete Product object")
  void shouldPrintCompleteProductObject() {
    Product result = engine.map(chaoticJson, "product-complex", Product.class);

    log.info("\n================== COMPLETE PRODUCT (COMPLEX) ==================");
    log.info("{}", result);
    log.info("=================================================================");
    log.info("Total Categories: {}", result.getCategoriesCount());
    log.info("Total Brands: {}", result.getBrandsCount());
    log.info("Total Images: {}", result.getImagesCount());
    log.info("Total Attributes: {}", result.getAttributesCount());
    log.info("Total Tags: {}", result.getTagsCount());
    log.info("=================================================================\n");

    // Also print as JSON
    log.info("================== AS JSON ==================");
    try {
      String json = JsonFormat.printer()
          .includingDefaultValueFields()
          .print(result);
      log.info("{}", json);
    } catch (Exception e) {
      log.warn("Error printing JSON: {}", e.getMessage());
    }
    log.info("=============================================\n");

    assertThat(result).isNotNull();
  }

  private static JsonNode loadJson(String resourcePath) throws IOException {
    try (InputStream is = ProductComplexIntegrationTest.class.getClassLoader()
        .getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Resource not found: " + resourcePath);
      }
      String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return objectMapper.readTree(content);
    }
  }
}
