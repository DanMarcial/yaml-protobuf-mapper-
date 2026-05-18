package io.github.yamlmapper.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MappingMetrics")
class MappingMetricsTest {

  private MappingMetrics metrics;

  @BeforeEach
  void setUp() {
    metrics = new MappingMetrics();
  }

  @Nested
  @DisplayName("Initial State")
  class InitialState {

    @Test
    @DisplayName("should start with zero counters")
    void shouldStartWithZeroCounters() {
      assertEquals(0, metrics.getTotalMappings());
      assertEquals(0, metrics.getSuccessfulMappings());
      assertEquals(0, metrics.getFailedMappings());
      assertEquals(0, metrics.getValidationErrors());
    }

    @Test
    @DisplayName("should return 0 for average latency when no mappings")
    void shouldReturnZeroAverageLatencyWhenNoMappings() {
      assertEquals(0.0, metrics.getAverageLatencyMs());
      assertEquals(0.0, metrics.getAverageLatencyMicros());
    }

    @Test
    @DisplayName("should return 0 for min latency when no mappings")
    void shouldReturnZeroMinLatencyWhenNoMappings() {
      assertEquals(0.0, metrics.getMinLatencyMs());
    }

    @Test
    @DisplayName("should return 100% success rate when no mappings")
    void shouldReturn100PercentSuccessRateWhenNoMappings() {
      assertEquals(100.0, metrics.getSuccessRate());
    }

    @Test
    @DisplayName("should return 0% error rate when no mappings")
    void shouldReturnZeroErrorRateWhenNoMappings() {
      assertEquals(0.0, metrics.getErrorRate());
    }
  }

  @Nested
  @DisplayName("Recording Success")
  class RecordingSuccess {

    @Test
    @DisplayName("should increment success and total counters")
    void shouldIncrementSuccessAndTotalCounters() {
      metrics.recordSuccess(1_000_000); // 1ms

      assertEquals(1, metrics.getTotalMappings());
      assertEquals(1, metrics.getSuccessfulMappings());
      assertEquals(0, metrics.getFailedMappings());
    }

    @Test
    @DisplayName("should track timing correctly")
    void shouldTrackTimingCorrectly() {
      metrics.recordSuccess(5_000_000); // 5ms

      assertEquals(5.0, metrics.getAverageLatencyMs(), 0.001);
      assertEquals(5.0, metrics.getMinLatencyMs(), 0.001);
      assertEquals(5.0, metrics.getMaxLatencyMs(), 0.001);
    }

    @Test
    @DisplayName("should calculate average latency correctly")
    void shouldCalculateAverageLatencyCorrectly() {
      metrics.recordSuccess(2_000_000); // 2ms
      metrics.recordSuccess(4_000_000); // 4ms
      metrics.recordSuccess(6_000_000); // 6ms

      assertEquals(4.0, metrics.getAverageLatencyMs(), 0.001);
    }

    @Test
    @DisplayName("should track min and max latency")
    void shouldTrackMinAndMaxLatency() {
      metrics.recordSuccess(10_000_000); // 10ms
      metrics.recordSuccess(5_000_000);  // 5ms
      metrics.recordSuccess(15_000_000); // 15ms

      assertEquals(5.0, metrics.getMinLatencyMs(), 0.001);
      assertEquals(15.0, metrics.getMaxLatencyMs(), 0.001);
    }
  }

  @Nested
  @DisplayName("Recording Errors")
  class RecordingErrors {

    @Test
    @DisplayName("should increment error and total counters")
    void shouldIncrementErrorAndTotalCounters() {
      metrics.recordError();

      assertEquals(1, metrics.getTotalMappings());
      assertEquals(0, metrics.getSuccessfulMappings());
      assertEquals(1, metrics.getFailedMappings());
    }

    @Test
    @DisplayName("should track validation errors separately")
    void shouldTrackValidationErrorsSeparately() {
      metrics.recordValidationError();
      metrics.recordValidationError();

      assertEquals(0, metrics.getTotalMappings());
      assertEquals(2, metrics.getValidationErrors());
    }
  }

  @Nested
  @DisplayName("Rate Calculations")
  class RateCalculations {

    @Test
    @DisplayName("should calculate success rate correctly")
    void shouldCalculateSuccessRateCorrectly() {
      metrics.recordSuccess(1_000_000);
      metrics.recordSuccess(1_000_000);
      metrics.recordSuccess(1_000_000);
      metrics.recordError();

      assertEquals(75.0, metrics.getSuccessRate(), 0.001);
    }

    @Test
    @DisplayName("should calculate error rate correctly")
    void shouldCalculateErrorRateCorrectly() {
      metrics.recordSuccess(1_000_000);
      metrics.recordError();

      assertEquals(50.0, metrics.getErrorRate(), 0.001);
    }
  }

  @Nested
  @DisplayName("Reset")
  class Reset {

    @Test
    @DisplayName("should reset all counters to initial state")
    void shouldResetAllCountersToInitialState() {
      // Record some data
      metrics.recordSuccess(5_000_000);
      metrics.recordError();
      metrics.recordValidationError();

      // Reset
      metrics.reset();

      // Verify all reset
      assertEquals(0, metrics.getTotalMappings());
      assertEquals(0, metrics.getSuccessfulMappings());
      assertEquals(0, metrics.getFailedMappings());
      assertEquals(0, metrics.getValidationErrors());
      assertEquals(0.0, metrics.getAverageLatencyMs());
      assertEquals(0.0, metrics.getMinLatencyMs());
      assertEquals(0.0, metrics.getMaxLatencyMs());
    }
  }

  @Nested
  @DisplayName("Snapshot")
  class Snapshot {

    @Test
    @DisplayName("should return formatted snapshot string")
    void shouldReturnFormattedSnapshotString() {
      metrics.recordSuccess(2_000_000);
      metrics.recordError();

      String snapshot = metrics.snapshot();

      assertTrue(snapshot.contains("total=2"));
      assertTrue(snapshot.contains("success=1"));
      assertTrue(snapshot.contains("failed=1"));
      assertTrue(snapshot.contains("errorRate=50.0%"));
    }

    @Test
    @DisplayName("toString should return snapshot")
    void toStringShouldReturnSnapshot() {
      assertEquals(metrics.snapshot(), metrics.toString());
    }
  }

  @Nested
  @DisplayName("Thread Safety")
  class ThreadSafety {

    @Test
    @DisplayName("should handle concurrent updates correctly")
    void shouldHandleConcurrentUpdatesCorrectly() throws InterruptedException {
      int threads = 10;
      int operationsPerThread = 1000;

      Thread[] threadArray = new Thread[threads];
      for (int i = 0; i < threads; i++) {
        threadArray[i] = new Thread(() -> {
          for (int j = 0; j < operationsPerThread; j++) {
            if (j % 2 == 0) {
              metrics.recordSuccess(1_000_000);
            } else {
              metrics.recordError();
            }
          }
        });
      }

      for (Thread t : threadArray) t.start();
      for (Thread t : threadArray) t.join();

      assertEquals(threads * operationsPerThread, metrics.getTotalMappings());
      assertEquals(threads * operationsPerThread / 2, metrics.getSuccessfulMappings());
      assertEquals(threads * operationsPerThread / 2, metrics.getFailedMappings());
    }
  }
}
