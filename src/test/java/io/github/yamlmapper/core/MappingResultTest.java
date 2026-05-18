package io.github.yamlmapper.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.UserEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MappingResultTest {

  private static MappingEngine engine;
  private static ObjectMapper objectMapper;

  @BeforeAll
  static void setUp() {
    objectMapper = new ObjectMapper();
    engine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withConfig("classpath:mapping/search-event.yaml")
        .injectEventType(true)
        .build();
  }

  @Nested
  @DisplayName("mapWithDetails")
  class MapWithDetailsTests {

    @Test
    @DisplayName("should return MappingResult with message")
    void shouldReturnMappingResultWithMessage() throws Exception {
      String json = """
          {
            "visitorId": "visitor-123",
            "sessionId": "session-456"
          }
          """;
      JsonNode jsonNode = objectMapper.readTree(json);

      MappingResult<UserEvent> result = engine.mapWithDetails(
          jsonNode, "search-event", UserEvent.class);

      assertThat(result.message()).isNotNull();
      assertThat(result.message().getVisitorId()).isEqualTo("visitor-123");
      assertThat(result.message().getSessionId()).isEqualTo("session-456");
    }

    @Test
    @DisplayName("should track mapped fields")
    void shouldTrackMappedFields() throws Exception {
      String json = """
          {
            "visitorId": "visitor-123",
            "sessionId": "session-456"
          }
          """;
      JsonNode jsonNode = objectMapper.readTree(json);

      MappingResult<UserEvent> result = engine.mapWithDetails(
          jsonNode, "search-event", UserEvent.class);

      assertThat(result.fieldStatuses()).containsKey("visitorId");
      assertThat(result.fieldStatuses().get("visitorId"))
          .isEqualTo(MappingResult.FieldStatus.MAPPED);
    }

    @Test
    @DisplayName("should track fields that used default values")
    void shouldTrackDefaultUsedFields() throws Exception {
      String json = """
          {
            "visitorId": "visitor-123"
          }
          """;
      JsonNode jsonNode = objectMapper.readTree(json);

      MappingResult<UserEvent> result = engine.mapWithDetails(
          jsonNode, "search-event", UserEvent.class);

      // offset has default: 0 in search-event.yaml
      assertThat(result.fieldStatuses().get("offset"))
          .isEqualTo(MappingResult.FieldStatus.DEFAULT_USED);
      assertThat(result.warnings()).isNotEmpty();
      assertThat(result.warnings()).anyMatch(w -> w.contains("offset"));
    }

    @Test
    @DisplayName("should track skipped optional fields")
    void shouldTrackSkippedOptionalFields() throws Exception {
      String json = """
          {
            "visitorId": "visitor-123"
          }
          """;
      JsonNode jsonNode = objectMapper.readTree(json);

      MappingResult<UserEvent> result = engine.mapWithDetails(
          jsonNode, "search-event", UserEvent.class);

      // sessionId is optional without default
      assertThat(result.fieldStatuses().get("sessionId"))
          .isEqualTo(MappingResult.FieldStatus.SKIPPED_OPTIONAL);
    }

    @Test
    @DisplayName("should track transform applied fields")
    void shouldTrackTransformAppliedFields() throws Exception {
      String json = """
          {
            "visitorId": "visitor-123",
            "category": "electronics"
          }
          """;
      JsonNode jsonNode = objectMapper.readTree(json);

      MappingResult<UserEvent> result = engine.mapWithDetails(
          jsonNode, "search-event", UserEvent.class);

      // pageCategories has transform: singleItemToArray
      assertThat(result.fieldStatuses().get("pageCategories"))
          .isEqualTo(MappingResult.FieldStatus.TRANSFORM_APPLIED);
    }

    @Test
    @DisplayName("should measure mapping time")
    void shouldMeasureMappingTime() throws Exception {
      String json = """
          {
            "visitorId": "visitor-123"
          }
          """;
      JsonNode jsonNode = objectMapper.readTree(json);

      MappingResult<UserEvent> result = engine.mapWithDetails(
          jsonNode, "search-event", UserEvent.class);

      assertThat(result.mappingTime()).isNotNull();
      assertThat(result.mappingTime().toNanos()).isGreaterThan(0);
    }

    @Test
    @DisplayName("should track field statuses correctly")
    void shouldTrackFieldStatusesCorrectly() throws Exception {
      String json = """
          {
            "visitorId": "visitor-123",
            "category": "electronics"
          }
          """;
      JsonNode jsonNode = objectMapper.readTree(json);

      MappingResult<UserEvent> result = engine.mapWithDetails(
          jsonNode, "search-event", UserEvent.class);

      // Check that we have mapped fields
      assertThat(result.fieldStatuses()).isNotEmpty();
      assertThat(result.fieldStatuses().values())
          .contains(MappingResult.FieldStatus.MAPPED);
    }
  }
}
