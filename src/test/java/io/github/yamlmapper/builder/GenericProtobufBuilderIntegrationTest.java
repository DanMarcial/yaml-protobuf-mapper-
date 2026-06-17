package io.github.yamlmapper.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.Product;
import com.google.cloud.retail.v2.ProductDetail;
import com.google.cloud.retail.v2.UserEvent;
import com.google.cloud.retail.v2.UserInfo;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.exception.MappingException;
import io.github.yamlmapper.extractor.PathResolver;
import io.github.yamlmapper.resolver.TypeResolver;
import io.github.yamlmapper.transform.TransformRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration test for GenericProtobufBuilder covering all supported types,
 * default values, required field validation, and nested objects.
 */
class GenericProtobufBuilderIntegrationTest {

  private static GenericProtobufBuilder protobufBuilder;
  private static ObjectMapper objectMapper;

  @BeforeAll
  static void setUp() {
    objectMapper = new ObjectMapper();

    protobufBuilder = new GenericProtobufBuilder(
        new PathResolver(),
        new TransformRegistry(),
        objectMapper,
        new TypeConverter(),
        new SetterResolver(),
        new TypeResolver(List.of("com.google.cloud.retail.v2")),
        new BuilderFactory()
    );
  }

  // Helper to build UserEvent
  private static UserEvent buildUserEvent(JsonNode json, Map<String, FieldConfig> fields) {
    return protobufBuilder.build(UserEvent.newBuilder(), json, fields);
  }

  @Nested
  @DisplayName("All Types Integration")
  class AllTypesTest {

    @Test
    @DisplayName("should map all primitive types correctly")
    void shouldMapAllPrimitiveTypes() throws Exception {
      String json = """
          {
            "visitor_id": "VIS-123",
            "session_id": "SESS-456",
            "page_offset": 20,
            "query": "laptop",
            "categories": ["electronics", "computers"],
            "event_time": "2024-01-15T10:30:00Z",
            "request_uri": "/search"
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      Map<String, FieldConfig> fields = Map.of(
          "visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .required(true)
              .build(),
          "sessionId", FieldConfig.builder("sessionId")
              .type("string")
              .source("session_id")
              .build(),
          "offset", FieldConfig.builder("offset")
              .type("int32")
              .source("page_offset")
              .build(),
          "searchQuery", FieldConfig.builder("searchQuery")
              .type("string")
              .source("query")
              .build(),
          "pageCategories", FieldConfig.builder("pageCategories")
              .type("array")
              .itemType("string")
              .source("categories")
              .build(),
          "eventTime", FieldConfig.builder("eventTime")
              .type("timestamp")
              .source("event_time")
              .format("iso8601")
              .build(),
          "uri", FieldConfig.builder("uri")
              .type("string")
              .source("request_uri")
              .required(true)
              .build()
      );

      UserEvent event = buildUserEvent(jsonNode, fields);

      assertThat(event.getVisitorId()).isEqualTo("VIS-123");
      assertThat(event.getSessionId()).isEqualTo("SESS-456");
      assertThat(event.getOffset()).isEqualTo(20);
      assertThat(event.getSearchQuery()).isEqualTo("laptop");
      assertThat(event.getPageCategoriesList()).containsExactly("electronics", "computers");
      assertThat(event.getUri()).isEqualTo("/search");

      // Verify timestamp
      Instant expectedTime = Instant.parse("2024-01-15T10:30:00Z");
      assertThat(event.getEventTime().getSeconds()).isEqualTo(expectedTime.getEpochSecond());
    }
  }

  @Nested
  @DisplayName("Default Values")
  class DefaultValuesTest {

    @Test
    @DisplayName("should apply default string value when field is missing")
    void shouldApplyDefaultStringValue() throws Exception {
      String json = """
          {
            "visitor_id": "VIS-123",
            "request_uri": "/page"
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      Map<String, FieldConfig> fields = Map.of(
          "visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .build(),
          "sessionId", FieldConfig.builder("sessionId")
              .type("string")
              .source("session_id")
              .defaultValue("default-session")
              .build(),
          "uri", FieldConfig.builder("uri")
              .type("string")
              .source("request_uri")
              .build()
      );

      UserEvent event = buildUserEvent(jsonNode, fields);

      assertThat(event.getSessionId()).isEqualTo("default-session");
    }

    @Test
    @DisplayName("should apply default int32 value when field is missing")
    void shouldApplyDefaultInt32Value() throws Exception {
      String json = """
          {
            "visitor_id": "VIS-123"
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      Map<String, FieldConfig> fields = Map.of(
          "visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .build(),
          "offset", FieldConfig.builder("offset")
              .type("int32")
              .source("page_offset")
              .defaultValue(10)
              .build()
      );

      UserEvent event = buildUserEvent(jsonNode, fields);

      assertThat(event.getOffset()).isEqualTo(10);
    }

    @Test
    @DisplayName("should NOT apply default when field has value")
    void shouldNotApplyDefaultWhenFieldHasValue() throws Exception {
      String json = """
          {
            "visitor_id": "VIS-123",
            "session_id": "actual-session"
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      Map<String, FieldConfig> fields = Map.of(
          "visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .build(),
          "sessionId", FieldConfig.builder("sessionId")
              .type("string")
              .source("session_id")
              .defaultValue("default-session")
              .build()
      );

      UserEvent event = buildUserEvent(jsonNode, fields);

      assertThat(event.getSessionId()).isEqualTo("actual-session");
    }
  }

  @Nested
  @DisplayName("Required Fields Validation")
  class RequiredFieldsTest {

    @Test
    @DisplayName("should throw when required field is missing")
    void shouldThrowWhenRequiredFieldIsMissing() throws Exception {
      String json = """
          {
            "some_other_field": "value"
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      Map<String, FieldConfig> fields = Map.of(
          "visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .required(true)
              .build()
      );

      assertThatThrownBy(() -> buildUserEvent(jsonNode, fields))
          .isInstanceOf(MappingException.class)
          .hasMessageContaining("Required field 'visitorId' not found");
    }

    @Test
    @DisplayName("should use default value instead of throwing for required field with default")
    void shouldUseDefaultForRequiredFieldWithDefault() throws Exception {
      String json = """
          {
            "other": "value"
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      Map<String, FieldConfig> fields = Map.of(
          "visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .required(true)
              .defaultValue("default-visitor")
              .build()
      );

      UserEvent event = buildUserEvent(jsonNode, fields);

      assertThat(event.getVisitorId()).isEqualTo("default-visitor");
    }
  }

  @Nested
  @DisplayName("Nested Objects")
  class NestedObjectsTest {

    @Test
    @DisplayName("should map nested object correctly")
    void shouldMapNestedObject() throws Exception {
      String json = """
          {
            "visitor_id": "VIS-123",
            "user": {
              "id": "USER-456",
              "agent": "Mozilla/5.0"
            }
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      Map<String, FieldConfig> userFields = Map.of(
          "userId", FieldConfig.builder("userId")
              .type("string")
              .source("id")
              .build(),
          "userAgent", FieldConfig.builder("userAgent")
              .type("string")
              .source("agent")
              .build()
      );

      Map<String, FieldConfig> fields = Map.of(
          "visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .build(),
          "userInfo", FieldConfig.builder("userInfo")
              .type("object")
              .objectType("UserInfo")
              .source("user")
              .fields(userFields)
              .build()
      );

      UserEvent event = buildUserEvent(jsonNode, fields);

      assertThat(event.getVisitorId()).isEqualTo("VIS-123");
      assertThat(event.getUserInfo().getUserId()).isEqualTo("USER-456");
      assertThat(event.getUserInfo().getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("should apply defaults in nested objects")
    void shouldApplyDefaultsInNestedObjects() throws Exception {
      String json = """
          {
            "visitor_id": "VIS-123",
            "user": {
              "id": "USER-456"
            }
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      Map<String, FieldConfig> userFields = Map.of(
          "userId", FieldConfig.builder("userId")
              .type("string")
              .source("id")
              .build(),
          "userAgent", FieldConfig.builder("userAgent")
              .type("string")
              .source("agent")
              .defaultValue("unknown-agent")
              .build()
      );

      Map<String, FieldConfig> fields = Map.of(
          "visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .build(),
          "userInfo", FieldConfig.builder("userInfo")
              .type("object")
              .objectType("UserInfo")
              .source("user")
              .fields(userFields)
              .build()
      );

      UserEvent event = buildUserEvent(jsonNode, fields);

      assertThat(event.getUserInfo().getUserAgent()).isEqualTo("unknown-agent");
    }
  }

  @Nested
  @DisplayName("Object Arrays")
  class ObjectArraysTest {

    @Test
    @DisplayName("should map array of objects with nested structure")
    void shouldMapArrayOfObjectsWithNestedStructure() throws Exception {
      String json = """
          {
            "visitor_id": "VIS-123",
            "products": [
              {
                "product": {
                  "id": "PROD-1",
                  "name": "Laptop"
                },
                "qty": 2
              },
              {
                "product": {
                  "id": "PROD-2",
                  "name": "Mouse"
                },
                "qty": 1
              }
            ]
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      Map<String, FieldConfig> productFields = Map.of(
          "id", FieldConfig.builder("id")
              .type("string")
              .source("id")
              .build(),
          "title", FieldConfig.builder("title")
              .type("string")
              .source("name")
              .build()
      );

      Map<String, FieldConfig> productDetailFields = Map.of(
          "product", FieldConfig.builder("product")
              .type("object")
              .objectType("Product")
              .source("product")
              .fields(productFields)
              .build(),
          "quantity", FieldConfig.builder("quantity")
              .type("int32")
              .source("qty")
              .build()
      );

      Map<String, FieldConfig> fields = Map.of(
          "visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .build(),
          "productDetails", FieldConfig.builder("productDetails")
              .type("array")
              .itemType("ProductDetail")
              .source("products")
              .fields(productDetailFields)
              .build()
      );

      UserEvent event = buildUserEvent(jsonNode, fields);

      List<ProductDetail> products = event.getProductDetailsList();
      assertThat(products).hasSize(2);

      assertThat(products.get(0).getProduct().getId()).isEqualTo("PROD-1");
      assertThat(products.get(0).getProduct().getTitle()).isEqualTo("Laptop");
      assertThat(products.get(0).getQuantity().getValue()).isEqualTo(2);

      assertThat(products.get(1).getProduct().getId()).isEqualTo("PROD-2");
      assertThat(products.get(1).getProduct().getTitle()).isEqualTo("Mouse");
      assertThat(products.get(1).getQuantity().getValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("should apply defaults in array items")
    void shouldApplyDefaultsInArrayItems() throws Exception {
      String json = """
          {
            "visitor_id": "VIS-123",
            "products": [
              {
                "product": {
                  "id": "PROD-1",
                  "name": "Laptop"
                }
              }
            ]
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      Map<String, FieldConfig> productFields = Map.of(
          "id", FieldConfig.builder("id")
              .type("string")
              .source("id")
              .build()
      );

      Map<String, FieldConfig> productDetailFields = Map.of(
          "product", FieldConfig.builder("product")
              .type("object")
              .objectType("Product")
              .source("product")
              .fields(productFields)
              .build(),
          "quantity", FieldConfig.builder("quantity")
              .type("int32")
              .source("qty")
              .defaultValue(1)
              .build()
      );

      Map<String, FieldConfig> fields = Map.of(
          "visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .build(),
          "productDetails", FieldConfig.builder("productDetails")
              .type("array")
              .itemType("ProductDetail")
              .source("products")
              .fields(productDetailFields)
              .build()
      );

      UserEvent event = buildUserEvent(jsonNode, fields);

      assertThat(event.getProductDetailsList().get(0).getQuantity().getValue()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Complete Scenario")
  class CompleteScenarioTest {

    @Test
    @DisplayName("should handle complete mapping scenario with all features")
    void shouldHandleCompleteScenarioWithAllFeatures() throws Exception {
      String json = """
          {
            "visitor_id": "VIS-COMPLETE-123",
            "event_time": "2024-06-15T14:30:00Z",
            "request_uri": "/checkout",
            "categories": ["cart", "checkout"],
            "user": {
              "id": "USER-789"
            },
            "products": [
              {
                "product": {
                  "id": "SKU-001",
                  "name": "Premium Widget"
                },
                "qty": 3
              },
              {
                "product": {
                  "id": "SKU-002",
                  "name": "Basic Gadget"
                }
              }
            ]
          }
          """;

      JsonNode jsonNode = objectMapper.readTree(json);

      // Build complete field configuration
      Map<String, FieldConfig> productFields = Map.of(
          "id", FieldConfig.builder("id")
              .type("string")
              .source("id")
              .required(true)
              .build(),
          "title", FieldConfig.builder("title")
              .type("string")
              .source("name")
              .build()
      );

      Map<String, FieldConfig> productDetailFields = Map.of(
          "product", FieldConfig.builder("product")
              .type("object")
              .objectType("Product")
              .source("product")
              .fields(productFields)
              .build(),
          "quantity", FieldConfig.builder("quantity")
              .type("int32")
              .source("qty")
              .defaultValue(1)
              .build()
      );

      Map<String, FieldConfig> userFields = Map.of(
          "userId", FieldConfig.builder("userId")
              .type("string")
              .source("id")
              .required(true)
              .build(),
          "userAgent", FieldConfig.builder("userAgent")
              .type("string")
              .source("agent")
              .defaultValue("default-agent")
              .build()
      );

      Map<String, FieldConfig> fields = Map.ofEntries(
          Map.entry("visitorId", FieldConfig.builder("visitorId")
              .type("string")
              .source("visitor_id")
              .required(true)
              .build()),
          Map.entry("sessionId", FieldConfig.builder("sessionId")
              .type("string")
              .source("session_id")
              .defaultValue("default-session")
              .build()),
          Map.entry("eventTime", FieldConfig.builder("eventTime")
              .type("timestamp")
              .source("event_time")
              .format("iso8601")
              .build()),
          Map.entry("uri", FieldConfig.builder("uri")
              .type("string")
              .source("request_uri")
              .required(true)
              .build()),
          Map.entry("pageCategories", FieldConfig.builder("pageCategories")
              .type("array")
              .itemType("string")
              .source("categories")
              .build()),
          Map.entry("userInfo", FieldConfig.builder("userInfo")
              .type("object")
              .objectType("UserInfo")
              .source("user")
              .fields(userFields)
              .build()),
          Map.entry("productDetails", FieldConfig.builder("productDetails")
              .type("array")
              .itemType("ProductDetail")
              .source("products")
              .fields(productDetailFields)
              .build())
      );

      UserEvent event = buildUserEvent(jsonNode, fields);

      // Verify all mappings
      assertThat(event.getVisitorId()).isEqualTo("VIS-COMPLETE-123");
      assertThat(event.getSessionId()).isEqualTo("default-session"); // default applied
      assertThat(event.getUri()).isEqualTo("/checkout");
      assertThat(event.getPageCategoriesList()).containsExactly("cart", "checkout");

      // Verify timestamp
      Instant expectedTime = Instant.parse("2024-06-15T14:30:00Z");
      assertThat(event.getEventTime().getSeconds()).isEqualTo(expectedTime.getEpochSecond());

      // Verify nested user
      assertThat(event.getUserInfo().getUserId()).isEqualTo("USER-789");
      assertThat(event.getUserInfo().getUserAgent()).isEqualTo("default-agent"); // default applied

      // Verify product array
      List<ProductDetail> products = event.getProductDetailsList();
      assertThat(products).hasSize(2);

      // First product has quantity
      assertThat(products.get(0).getProduct().getId()).isEqualTo("SKU-001");
      assertThat(products.get(0).getProduct().getTitle()).isEqualTo("Premium Widget");
      assertThat(products.get(0).getQuantity().getValue()).isEqualTo(3);

      // Second product uses default quantity
      assertThat(products.get(1).getProduct().getId()).isEqualTo("SKU-002");
      assertThat(products.get(1).getQuantity().getValue()).isEqualTo(1); // default applied
    }
  }
}
