package io.github.yamlmapper.core;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight metrics collection for mapping operations.
 *
 * <p>Provides basic counters and timing statistics without external dependencies.
 * For production use with Prometheus/Grafana, consider wrapping with Micrometer.
 *
 * <p>Example usage:
 * <pre>{@code
 * MappingMetrics metrics = new MappingMetrics();
 *
 * long start = System.nanoTime();
 * try {
 *     // mapping operation
 *     metrics.recordSuccess(System.nanoTime() - start);
 * } catch (Exception e) {
 *     metrics.recordError();
 *     throw e;
 * }
 *
 * // Later: check metrics
 * System.out.println("Avg latency: " + metrics.getAverageLatencyMs() + "ms");
 * System.out.println("Error rate: " + metrics.getErrorRate() + "%");
 * }</pre>
 *
 * <p>This class is thread-safe.
 */
public class MappingMetrics {

  private final AtomicLong totalMappings = new AtomicLong();
  private final AtomicLong successfulMappings = new AtomicLong();
  private final AtomicLong failedMappings = new AtomicLong();
  private final AtomicLong validationErrors = new AtomicLong();
  private final LongAdder totalNanos = new LongAdder();
  private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
  private final AtomicLong maxNanos = new AtomicLong(0);

  /**
   * Records a successful mapping operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  public void recordSuccess(long durationNanos) {
    totalMappings.incrementAndGet();
    successfulMappings.incrementAndGet();
    recordTiming(durationNanos);
  }

  /**
   * Records a failed mapping operation.
   */
  public void recordError() {
    totalMappings.incrementAndGet();
    failedMappings.incrementAndGet();
  }

  /**
   * Records a validation error (separate from mapping errors).
   */
  public void recordValidationError() {
    validationErrors.incrementAndGet();
  }

  /**
   * Records timing for a mapping operation.
   */
  private void recordTiming(long durationNanos) {
    totalNanos.add(durationNanos);
    updateMin(durationNanos);
    updateMax(durationNanos);
  }

  private void updateMin(long value) {
    long current;
    do {
      current = minNanos.get();
      if (value >= current) return;
    } while (!minNanos.compareAndSet(current, value));
  }

  private void updateMax(long value) {
    long current;
    do {
      current = maxNanos.get();
      if (value <= current) return;
    } while (!maxNanos.compareAndSet(current, value));
  }

  // ==================== Getters ====================

  /**
   * Gets the total number of mapping attempts.
   */
  public long getTotalMappings() {
    return totalMappings.get();
  }

  /**
   * Gets the number of successful mappings.
   */
  public long getSuccessfulMappings() {
    return successfulMappings.get();
  }

  /**
   * Gets the number of failed mappings.
   */
  public long getFailedMappings() {
    return failedMappings.get();
  }

  /**
   * Gets the number of validation errors.
   */
  public long getValidationErrors() {
    return validationErrors.get();
  }

  /**
   * Gets the average latency in milliseconds.
   *
   * @return average latency, or 0 if no successful mappings
   */
  public double getAverageLatencyMs() {
    long successful = successfulMappings.get();
    if (successful == 0) return 0.0;
    return (totalNanos.sum() / (double) successful) / 1_000_000.0;
  }

  /**
   * Gets the average latency in microseconds.
   *
   * @return average latency, or 0 if no successful mappings
   */
  public double getAverageLatencyMicros() {
    long successful = successfulMappings.get();
    if (successful == 0) return 0.0;
    return (totalNanos.sum() / (double) successful) / 1_000.0;
  }

  /**
   * Gets the minimum latency in milliseconds.
   *
   * @return minimum latency, or 0 if no successful mappings
   */
  public double getMinLatencyMs() {
    long min = minNanos.get();
    return min == Long.MAX_VALUE ? 0.0 : min / 1_000_000.0;
  }

  /**
   * Gets the maximum latency in milliseconds.
   *
   * @return maximum latency
   */
  public double getMaxLatencyMs() {
    return maxNanos.get() / 1_000_000.0;
  }

  /**
   * Gets the error rate as a percentage.
   *
   * @return error rate (0-100), or 0 if no mappings
   */
  public double getErrorRate() {
    long total = totalMappings.get();
    if (total == 0) return 0.0;
    return (failedMappings.get() * 100.0) / total;
  }

  /**
   * Gets the success rate as a percentage.
   *
   * @return success rate (0-100), or 100 if no mappings
   */
  public double getSuccessRate() {
    long total = totalMappings.get();
    if (total == 0) return 100.0;
    return (successfulMappings.get() * 100.0) / total;
  }

  /**
   * Resets all metrics to zero.
   */
  public void reset() {
    totalMappings.set(0);
    successfulMappings.set(0);
    failedMappings.set(0);
    validationErrors.set(0);
    totalNanos.reset();
    minNanos.set(Long.MAX_VALUE);
    maxNanos.set(0);
  }

  /**
   * Returns a snapshot of current metrics as a formatted string.
   */
  public String snapshot() {
    return String.format(
        "MappingMetrics{total=%d, success=%d, failed=%d, validationErrors=%d, " +
        "avgLatency=%.2fms, minLatency=%.2fms, maxLatency=%.2fms, errorRate=%.1f%%}",
        getTotalMappings(),
        getSuccessfulMappings(),
        getFailedMappings(),
        getValidationErrors(),
        getAverageLatencyMs(),
        getMinLatencyMs(),
        getMaxLatencyMs(),
        getErrorRate()
    );
  }

  @Override
  public String toString() {
    return snapshot();
  }
}
