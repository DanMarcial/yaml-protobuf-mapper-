package io.github.yamlmapper.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.UserEvent;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.core.BatchMapper;
import io.github.yamlmapper.core.MappingEngine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for MappingEngine performance.
 *
 * <p>Run benchmarks with:
 * <pre>
 * mvn test -Dtest=MappingBenchmark#runBenchmarks
 * </pre>
 *
 * <p>Or run a quick benchmark (fewer iterations):
 * <pre>
 * mvn test -Dtest=MappingBenchmark#runQuickBenchmarks
 * </pre>
 *
 * <p>Benchmark results help identify:
 * <ul>
 *   <li>Single mapping throughput (ops/sec)</li>
 *   <li>Batch mapping performance with Virtual Threads</li>
 *   <li>Cache effectiveness</li>
 *   <li>Memory allocation rates</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class MappingBenchmark {

  private MappingEngine engine;
  private ObjectMapper objectMapper;
  private JsonNode simpleJson;
  private JsonNode complexJson;
  private List<JsonNode> batchSmall;   // 100 items
  private List<JsonNode> batchMedium;  // 1,000 items
  private List<JsonNode> batchLarge;   // 10,000 items
  private BatchMapper batchMapper;
  private BatchMapper platformThreadMapper;

  @Setup(Level.Trial)
  public void setup() throws IOException {
    objectMapper = new ObjectMapper();

    // Create simple test schema (avoiding nested types that require complex validation)
    MappingSchema searchSchema = new MappingSchema(
        "UserEvent",
        Map.of(
            "visitorId", FieldConfig.of("visitorId", "string", List.of("visitor_id")),
            "eventTime", FieldConfig.builder("eventTime")
                .type("timestamp")
                .source("event_time")
                .format("iso8601")
                .build(),
            "attributionToken", FieldConfig.of("attributionToken", "string", List.of("attribution_token")),
            "searchQuery", FieldConfig.of("searchQuery", "string", List.of("query")),
            "uri", FieldConfig.of("uri", "string", List.of("page_url"))
        )
    );

    engine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withSchema("search", searchSchema)
        .enableMetrics(true)
        .build();

    batchMapper = new BatchMapper(engine, true);  // Virtual threads
    platformThreadMapper = new BatchMapper(engine, false);  // Platform threads

    // Simple JSON (minimal fields)
    simpleJson = objectMapper.readTree("""
        {
          "visitor_id": "user-12345",
          "event_time": "2024-01-15T10:30:00Z",
          "attribution_token": "abc123"
        }
        """);

    // Complex JSON (with more fields)
    complexJson = objectMapper.readTree("""
        {
          "visitor_id": "user-67890",
          "event_time": "2024-01-15T10:30:00Z",
          "attribution_token": "xyz789",
          "query": "laptop gaming",
          "page_url": "https://example.com/search?q=laptop"
        }
        """);

    // Create batch data
    batchSmall = createBatch(100);
    batchMedium = createBatch(1_000);
    batchLarge = createBatch(10_000);
  }

  private List<JsonNode> createBatch(int size) throws IOException {
    List<JsonNode> batch = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      batch.add(objectMapper.readTree(String.format("""
          {
            "visitor_id": "user-%d",
            "event_time": "2024-01-15T10:30:00Z",
            "attribution_token": "token-%d"
          }
          """, i, i)));
    }
    return batch;
  }

  // ==================== Single Mapping Benchmarks ====================

  @Benchmark
  public void mapSimpleJson(Blackhole bh) {
    UserEvent result = engine.map(simpleJson, "search", UserEvent.class);
    bh.consume(result);
  }

  @Benchmark
  public void mapComplexJson(Blackhole bh) {
    UserEvent result = engine.map(complexJson, "search", UserEvent.class);
    bh.consume(result);
  }

  // ==================== Batch Mapping Benchmarks ====================

  @Benchmark
  public void batchMapSmall_VirtualThreads(Blackhole bh) {
    var result = batchMapper.mapBatch(batchSmall, "search", UserEvent.class);
    bh.consume(result);
  }

  @Benchmark
  public void batchMapSmall_PlatformThreads(Blackhole bh) {
    var result = platformThreadMapper.mapBatch(batchSmall, "search", UserEvent.class);
    bh.consume(result);
  }

  @Benchmark
  public void batchMapMedium_VirtualThreads(Blackhole bh) {
    var result = batchMapper.mapBatch(batchMedium, "search", UserEvent.class);
    bh.consume(result);
  }

  @Benchmark
  public void batchMapLarge_VirtualThreads(Blackhole bh) {
    var result = batchMapper.mapBatch(batchLarge, "search", UserEvent.class);
    bh.consume(result);
  }

  // ==================== Test Runners ====================

  /**
   * Run full benchmarks (takes ~5-10 minutes).
   */
  @org.junit.jupiter.api.Test
  @org.junit.jupiter.api.Disabled("Run manually: mvn test -Dtest=MappingBenchmark#runBenchmarks")
  public void runBenchmarks() throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(MappingBenchmark.class.getSimpleName())
        .forks(1)
        .build();

    new Runner(opt).run();
  }

  /**
   * Run quick benchmarks for development iteration (~1 minute).
   */
  @org.junit.jupiter.api.Test
  @org.junit.jupiter.api.Disabled("Run manually: mvn test -Dtest=MappingBenchmark#runQuickBenchmarks")
  public void runQuickBenchmarks() throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(MappingBenchmark.class.getSimpleName() + ".mapSimpleJson")
        .include(MappingBenchmark.class.getSimpleName() + ".mapComplexJson")
        .warmupIterations(1)
        .measurementIterations(2)
        .forks(0)  // No fork for faster iteration
        .build();

    new Runner(opt).run();
  }

  /**
   * Quick sanity check that benchmarks compile and run.
   */
  @org.junit.jupiter.api.Test
  public void verifyBenchmarkSetup() throws IOException {
    setup();

    // Verify single mapping works
    UserEvent simple = engine.map(simpleJson, "search", UserEvent.class);
    org.junit.jupiter.api.Assertions.assertNotNull(simple);
    org.junit.jupiter.api.Assertions.assertEquals("user-12345", simple.getVisitorId());

    UserEvent complex = engine.map(complexJson, "search", UserEvent.class);
    org.junit.jupiter.api.Assertions.assertNotNull(complex);
    org.junit.jupiter.api.Assertions.assertEquals("user-67890", complex.getVisitorId());

    // Verify batch mapping works
    var batchResult = batchMapper.mapBatch(batchSmall, "search", UserEvent.class);
    org.junit.jupiter.api.Assertions.assertEquals(100, batchResult.successful().size());
    org.junit.jupiter.api.Assertions.assertTrue(batchResult.failures().isEmpty());
  }
}
