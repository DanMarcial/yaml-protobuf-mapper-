package io.github.yamlmapper.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Batch mapping with Java 21 Virtual Threads support.
 *
 * <p>Provides efficient batch processing of JSON to Protobuf mappings
 * using Virtual Threads for high-throughput scenarios.
 *
 * <p>Example usage:
 * <pre>{@code
 * MappingEngine engine = MappingEngine.builder()
 *     .withProtobufPackage("com.google.cloud.retail.v2")
 *     .withConfig("classpath:mapping/search.yaml")
 *     .build();
 *
 * BatchMapper batchMapper = new BatchMapper(engine);
 *
 * List<JsonNode> jsonBatch = ...; // 10,000 events
 *
 * // Process with Virtual Threads (non-blocking, high throughput)
 * BatchResult<UserEvent> results = batchMapper.mapBatch(
 *     jsonBatch,
 *     "search",
 *     UserEvent.class
 * );
 *
 * System.out.println("Successful: " + results.successful().size());
 * System.out.println("Failed: " + results.failures().size());
 * }</pre>
 *
 * <p>Virtual Threads (Project Loom) benefits:
 * <ul>
 *   <li>Lightweight threads (~1KB vs ~1MB for platform threads)</li>
 *   <li>Can spawn millions without exhausting memory</li>
 *   <li>Ideal for I/O-bound operations or when processing large batches</li>
 *   <li>Automatic work-stealing scheduler</li>
 * </ul>
 *
 * <p>This class is thread-safe.
 */
public class BatchMapper {

  private static final Logger log = LoggerFactory.getLogger(BatchMapper.class);

  private final MappingEngine engine;
  private final boolean useVirtualThreads;

  /**
   * Creates a BatchMapper with Virtual Threads enabled.
   *
   * @param engine the MappingEngine to use for mappings
   */
  public BatchMapper(MappingEngine engine) {
    this(engine, true);
  }

  /**
   * Creates a BatchMapper with configurable thread mode.
   *
   * @param engine the MappingEngine to use
   * @param useVirtualThreads true to use Virtual Threads, false for platform threads
   */
  public BatchMapper(MappingEngine engine, boolean useVirtualThreads) {
    this.engine = engine;
    this.useVirtualThreads = useVirtualThreads;
  }

  /**
   * Maps a batch of JSON nodes to Protobuf messages in parallel.
   *
   * <p>Uses Virtual Threads by default for efficient parallel processing.
   * Each mapping runs in its own virtual thread, allowing high throughput
   * with minimal memory overhead.
   *
   * @param jsonNodes the JSON nodes to map
   * @param configId the configuration ID
   * @param targetClass the target Protobuf message class
   * @param <T> the message type
   * @return BatchResult containing successful mappings and failures
   */
  public <T extends Message> BatchResult<T> mapBatch(
      List<JsonNode> jsonNodes,
      String configId,
      Class<T> targetClass) {

    if (jsonNodes == null || jsonNodes.isEmpty()) {
      return BatchResult.empty();
    }

    log.debug("Starting batch mapping of {} items using {} threads",
        jsonNodes.size(),
        useVirtualThreads ? "virtual" : "platform");

    long startTime = System.nanoTime();

    List<T> successful = new ArrayList<>();
    List<BatchResult.Failure> failures = new ArrayList<>();

    // Use Virtual Thread executor for Java 21+
    try (ExecutorService executor = createExecutor()) {

      List<Future<MappingAttempt<T>>> futures = new ArrayList<>(jsonNodes.size());

      // Submit all mapping tasks
      for (int i = 0; i < jsonNodes.size(); i++) {
        final int index = i;
        final JsonNode node = jsonNodes.get(i);

        futures.add(executor.submit(() -> {
          try {
            T result = engine.map(node, configId, targetClass);
            return new MappingAttempt<>(index, result, null);
          } catch (Exception e) {
            return new MappingAttempt<>(index, null, e);
          }
        }));
      }

      // Collect results
      for (Future<MappingAttempt<T>> future : futures) {
        try {
          MappingAttempt<T> attempt = future.get();

          if (attempt.result != null) {
            successful.add(attempt.result);
          } else {
            failures.add(new BatchResult.Failure(
                attempt.index,
                attempt.error.getClass().getSimpleName(),
                attempt.error.getMessage()
            ));
          }

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Batch mapping interrupted");
          break;
        } catch (ExecutionException e) {
          log.error("Unexpected error during batch mapping", e.getCause());
        }
      }

      // Shutdown executor gracefully
      executor.shutdown();
      try {
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    long duration = System.nanoTime() - startTime;
    double durationMs = duration / 1_000_000.0;

    log.info("Batch mapping completed: {} successful, {} failed in {:.2f}ms ({:.2f} items/sec)",
        successful.size(),
        failures.size(),
        durationMs,
        (jsonNodes.size() * 1000.0) / durationMs);

    return new BatchResult<>(successful, failures, duration);
  }

  /**
   * Maps a batch of JSON strings to Protobuf messages.
   *
   * @param jsonStrings the JSON strings to map
   * @param configId the configuration ID
   * @param targetClass the target Protobuf message class
   * @param <T> the message type
   * @return BatchResult containing successful mappings and failures
   */
  public <T extends Message> BatchResult<T> mapBatchStrings(
      List<String> jsonStrings,
      String configId,
      Class<T> targetClass) {

    if (jsonStrings == null || jsonStrings.isEmpty()) {
      return BatchResult.empty();
    }

    List<JsonNode> nodes = new ArrayList<>(jsonStrings.size());
    List<BatchResult.Failure> parseFailures = new ArrayList<>();
    ObjectMapper mapper = engine.getObjectMapper();

    // Pre-parse JSON strings
    for (int i = 0; i < jsonStrings.size(); i++) {
      try {
        JsonNode node = engine.getConfig(configId) != null
            ? mapper.readTree(jsonStrings.get(i))
            : null;

        if (node != null) {
          nodes.add(node);
        } else {
          parseFailures.add(new BatchResult.Failure(i, "ParseError", "Config not found: " + configId));
        }

      } catch (Exception e) {
        parseFailures.add(new BatchResult.Failure(i, "ParseError", e.getMessage()));
      }
    }

    // Map valid nodes
    BatchResult<T> mapResult = mapBatch(nodes, configId, targetClass);

    // Combine parse failures with mapping failures
    List<BatchResult.Failure> allFailures = new ArrayList<>(parseFailures);
    allFailures.addAll(mapResult.failures());

    return new BatchResult<>(mapResult.successful(), allFailures, mapResult.durationNanos());
  }

  private ExecutorService createExecutor() {
    if (useVirtualThreads) {
      return Executors.newVirtualThreadPerTaskExecutor();
    }
    // Fallback to cached thread pool for platform threads
    return Executors.newCachedThreadPool();
  }

  // Internal record for tracking mapping attempts
  private record MappingAttempt<T>(int index, T result, Exception error) {}

  /**
   * Result of a batch mapping operation.
   *
   * @param successful list of successfully mapped messages
   * @param failures list of failures with details
   * @param durationNanos total duration in nanoseconds
   * @param <T> the message type
   */
  public record BatchResult<T>(
      List<T> successful,
      List<Failure> failures,
      long durationNanos
  ) {

    /**
     * Creates an empty result.
     */
    public static <T> BatchResult<T> empty() {
      return new BatchResult<>(List.of(), List.of(), 0);
    }

    /**
     * Gets the total number of items processed.
     */
    public int totalProcessed() {
      return successful.size() + failures.size();
    }

    /**
     * Gets the success rate as a percentage.
     */
    public double successRate() {
      int total = totalProcessed();
      return total == 0 ? 100.0 : (successful.size() * 100.0) / total;
    }

    /**
     * Gets the duration in milliseconds.
     */
    public double durationMs() {
      return durationNanos / 1_000_000.0;
    }

    /**
     * Gets the throughput in items per second.
     */
    public double throughputPerSecond() {
      double seconds = durationNanos / 1_000_000_000.0;
      return seconds == 0 ? 0 : totalProcessed() / seconds;
    }

    /**
     * Details about a failed mapping.
     *
     * @param index the index in the original batch
     * @param errorType the type of error
     * @param message the error message
     */
    public record Failure(int index, String errorType, String message) {}
  }
}
