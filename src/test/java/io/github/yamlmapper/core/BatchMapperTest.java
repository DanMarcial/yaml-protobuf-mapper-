package io.github.yamlmapper.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.UserEvent;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchMapper")
class BatchMapperTest {

  private MappingEngine engine;
  private BatchMapper batchMapper;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();

    MappingSchema schema = new MappingSchema(
        "UserEvent",
        Map.of(
            "visitorId", FieldConfig.of("visitorId", "string", List.of("visitor_id")),
            "attributionToken", FieldConfig.of("attributionToken", "string", List.of("attribution_token"))
        )
    );

    engine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withSchema("test", schema)
        .build();

    batchMapper = new BatchMapper(engine);
  }

  @Nested
  @DisplayName("Batch Mapping")
  class BatchMapping {

    @Test
    @DisplayName("should map empty batch")
    void shouldMapEmptyBatch() {
      BatchMapper.BatchResult<UserEvent> result = batchMapper.mapBatch(
          List.of(), "test", UserEvent.class
      );

      assertTrue(result.successful().isEmpty());
      assertTrue(result.failures().isEmpty());
      assertEquals(0, result.totalProcessed());
    }

    @Test
    @DisplayName("should map single item batch")
    void shouldMapSingleItemBatch() throws IOException {
      JsonNode json = objectMapper.readTree("""
          {"visitor_id": "user-1", "attribution_token": "token-1"}
          """);

      BatchMapper.BatchResult<UserEvent> result = batchMapper.mapBatch(
          List.of(json), "test", UserEvent.class
      );

      assertEquals(1, result.successful().size());
      assertTrue(result.failures().isEmpty());
      assertEquals("user-1", result.successful().get(0).getVisitorId());
    }

    @Test
    @DisplayName("should map multiple items in batch")
    void shouldMapMultipleItemsInBatch() throws IOException {
      List<JsonNode> batch = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        batch.add(objectMapper.readTree(String.format(
            "{\"visitor_id\": \"user-%d\", \"attribution_token\": \"token-%d\"}", i, i
        )));
      }

      BatchMapper.BatchResult<UserEvent> result = batchMapper.mapBatch(
          batch, "test", UserEvent.class
      );

      assertEquals(100, result.successful().size());
      assertTrue(result.failures().isEmpty());
      assertEquals(100.0, result.successRate());
    }

    @Test
    @DisplayName("should handle null batch")
    void shouldHandleNullBatch() {
      BatchMapper.BatchResult<UserEvent> result = batchMapper.mapBatch(
          null, "test", UserEvent.class
      );

      assertTrue(result.successful().isEmpty());
      assertTrue(result.failures().isEmpty());
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("should capture mapping failures")
    void shouldCaptureMappingFailures() throws IOException {
      // Valid JSON but missing config
      JsonNode json = objectMapper.readTree("{\"visitor_id\": \"user-1\"}");

      BatchMapper.BatchResult<UserEvent> result = batchMapper.mapBatch(
          List.of(json), "nonexistent-config", UserEvent.class
      );

      assertTrue(result.successful().isEmpty());
      assertEquals(1, result.failures().size());
      assertEquals(0, result.failures().get(0).index());
    }

    @Test
    @DisplayName("should continue processing after failures")
    void shouldContinueProcessingAfterFailures() throws IOException {
      List<JsonNode> batch = new ArrayList<>();

      // Mix of valid and invalid (will fail due to wrong config)
      for (int i = 0; i < 10; i++) {
        batch.add(objectMapper.readTree(String.format(
            "{\"visitor_id\": \"user-%d\"}", i
        )));
      }

      // All should succeed with correct config
      BatchMapper.BatchResult<UserEvent> result = batchMapper.mapBatch(
          batch, "test", UserEvent.class
      );

      assertEquals(10, result.successful().size());
      assertTrue(result.failures().isEmpty());
    }
  }

  @Nested
  @DisplayName("BatchResult")
  class BatchResultTests {

    @Test
    @DisplayName("should calculate success rate correctly")
    void shouldCalculateSuccessRateCorrectly() throws IOException {
      List<JsonNode> batch = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        batch.add(objectMapper.readTree(String.format(
            "{\"visitor_id\": \"user-%d\"}", i
        )));
      }

      BatchMapper.BatchResult<UserEvent> result = batchMapper.mapBatch(
          batch, "test", UserEvent.class
      );

      assertEquals(100.0, result.successRate());
      assertEquals(4, result.totalProcessed());
    }

    @Test
    @DisplayName("should report duration in milliseconds")
    void shouldReportDurationInMilliseconds() throws IOException {
      List<JsonNode> batch = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        batch.add(objectMapper.readTree("{\"visitor_id\": \"user-1\"}"));
      }

      BatchMapper.BatchResult<UserEvent> result = batchMapper.mapBatch(
          batch, "test", UserEvent.class
      );

      assertTrue(result.durationNanos() > 0);
      assertTrue(result.durationMs() > 0);
    }

    @Test
    @DisplayName("should calculate throughput")
    void shouldCalculateThroughput() throws IOException {
      List<JsonNode> batch = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        batch.add(objectMapper.readTree("{\"visitor_id\": \"user-1\"}"));
      }

      BatchMapper.BatchResult<UserEvent> result = batchMapper.mapBatch(
          batch, "test", UserEvent.class
      );

      assertTrue(result.throughputPerSecond() > 0);
    }

    @Test
    @DisplayName("empty result should have 100% success rate")
    void emptyResultShouldHave100PercentSuccessRate() {
      BatchMapper.BatchResult<UserEvent> empty = BatchMapper.BatchResult.empty();

      assertEquals(100.0, empty.successRate());
      assertEquals(0, empty.totalProcessed());
    }
  }

  @Nested
  @DisplayName("Virtual Threads")
  class VirtualThreadsTests {

    @Test
    @DisplayName("should work with virtual threads enabled")
    void shouldWorkWithVirtualThreadsEnabled() throws IOException {
      BatchMapper vtMapper = new BatchMapper(engine, true);

      List<JsonNode> batch = new ArrayList<>();
      for (int i = 0; i < 50; i++) {
        batch.add(objectMapper.readTree(String.format(
            "{\"visitor_id\": \"user-%d\"}", i
        )));
      }

      BatchMapper.BatchResult<UserEvent> result = vtMapper.mapBatch(
          batch, "test", UserEvent.class
      );

      assertEquals(50, result.successful().size());
    }

    @Test
    @DisplayName("should work with platform threads")
    void shouldWorkWithPlatformThreads() throws IOException {
      BatchMapper ptMapper = new BatchMapper(engine, false);

      List<JsonNode> batch = new ArrayList<>();
      for (int i = 0; i < 50; i++) {
        batch.add(objectMapper.readTree(String.format(
            "{\"visitor_id\": \"user-%d\"}", i
        )));
      }

      BatchMapper.BatchResult<UserEvent> result = ptMapper.mapBatch(
          batch, "test", UserEvent.class
      );

      assertEquals(50, result.successful().size());
    }
  }
}
