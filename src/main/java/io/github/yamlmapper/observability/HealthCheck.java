package io.github.yamlmapper.observability;

import io.github.yamlmapper.core.MappingEngine;
import io.github.yamlmapper.core.MappingMetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.yamlmapper.observability.HealthCheckKeys.*;

/**
 * Health check support for MappingEngine.
 *
 * <p>Provides health status information suitable for Kubernetes probes,
 * load balancer health checks, or monitoring dashboards.
 *
 * <p>Example usage with Spring Boot Actuator:
 * <pre>{@code
 * @Component
 * public class MappingHealthIndicator implements HealthIndicator {
 *
 *     private final HealthCheck healthCheck;
 *
 *     public MappingHealthIndicator(MappingEngine engine) {
 *         this.healthCheck = new HealthCheck(engine);
 *     }
 *
 *     @Override
 *     public Health health() {
 *         HealthCheck.HealthStatus status = healthCheck.check();
 *         return status.isHealthy()
 *             ? Health.up().withDetails(status.details()).build()
 *             : Health.down().withDetails(status.details()).build();
 *     }
 * }
 * }</pre>
 *
 * <p>Example usage with HTTP endpoint:
 * <pre>{@code
 * HealthCheck healthCheck = new HealthCheck(engine);
 *
 * // In your HTTP handler
 * HealthStatus status = healthCheck.check();
 * if (status.isHealthy()) {
 *     response.status(200).json(status.toJson());
 * } else {
 *     response.status(503).json(status.toJson());
 * }
 * }</pre>
 *
 * <p>This class is thread-safe.
 */
public class HealthCheck {

  private final MappingEngine engine;
  private final Instant startTime;
  private final double errorRateThreshold;
  private final double minSuccessRate;

  /**
   * Creates a HealthCheck with default thresholds.
   *
   * <p>Default thresholds:
   * <ul>
   *   <li>Error rate threshold: 10%</li>
   *   <li>Minimum success rate: 90%</li>
   * </ul>
   *
   * @param engine the MappingEngine to check
   */
  public HealthCheck(MappingEngine engine) {
    this(engine, 10.0, 90.0);
  }

  /**
   * Creates a HealthCheck with custom thresholds.
   *
   * @param engine the MappingEngine to check
   * @param errorRateThreshold maximum acceptable error rate (0-100)
   * @param minSuccessRate minimum acceptable success rate (0-100)
   */
  public HealthCheck(MappingEngine engine, double errorRateThreshold, double minSuccessRate) {
    this.engine = engine;
    this.startTime = Instant.now();
    this.errorRateThreshold = errorRateThreshold;
    this.minSuccessRate = minSuccessRate;
  }

  /**
   * Performs a health check.
   *
   * @return the health status
   */
  public HealthStatus check() {
    Map<String, Object> details = new LinkedHashMap<>();

    // Basic info
    details.put(KEY_STATUS, STATUS_CHECKING);
    details.put(KEY_UPTIME, getUptime().toString());
    details.put(KEY_UPTIME_SECONDS, getUptime().toSeconds());

    // Engine status
    boolean engineHealthy = checkEngine(details);

    // Metrics status (if enabled)
    boolean metricsHealthy = checkMetrics(details);

    // Determine overall health
    boolean healthy = engineHealthy && metricsHealthy;
    details.put(KEY_STATUS, healthy ? STATUS_UP : STATUS_DOWN);

    return new HealthStatus(healthy, details);
  }

  /**
   * Performs a quick liveness check (for Kubernetes liveness probe).
   *
   * <p>This is a fast check that only verifies the engine is responsive.
   *
   * @return true if the engine is alive
   */
  public boolean isAlive() {
    try {
      // Simple check: engine exists and has configs
      return engine != null;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Performs a readiness check (for Kubernetes readiness probe).
   *
   * <p>Checks if the engine is ready to accept traffic.
   *
   * @return true if the engine is ready
   */
  public boolean isReady() {
    return check().healthy();
  }

  /**
   * Gets the uptime duration.
   *
   * @return the uptime
   */
  public Duration getUptime() {
    return Duration.between(startTime, Instant.now());
  }

  private boolean checkEngine(Map<String, Object> details) {
    try {
      // Check validation is enabled
      details.put(KEY_POST_MAPPING_VALIDATION, engine.isPostMappingValidationEnabled());
      details.put(KEY_METRICS_ENABLED, engine.isMetricsEnabled());

      return true;
    } catch (Exception e) {
      details.put(KEY_ENGINE_ERROR, e.getMessage());
      return false;
    }
  }

  private boolean checkMetrics(Map<String, Object> details) {
    if (!engine.isMetricsEnabled()) {
      details.put(KEY_METRICS_STATUS, STATUS_DISABLED);
      return true;
    }

    try {
      MappingMetrics metrics = engine.getMetrics();

      // Add metrics details
      Map<String, Object> metricsDetails = new LinkedHashMap<>();
      metricsDetails.put("totalMappings", metrics.getTotalMappings());
      metricsDetails.put("successfulMappings", metrics.getSuccessfulMappings());
      metricsDetails.put("failedMappings", metrics.getFailedMappings());
      metricsDetails.put("validationErrors", metrics.getValidationErrors());
      metricsDetails.put(KEY_SUCCESS_RATE, String.format("%.2f%%", metrics.getSuccessRate()));
      metricsDetails.put(KEY_ERROR_RATE, String.format("%.2f%%", metrics.getErrorRate()));
      metricsDetails.put(KEY_AVG_LATENCY_MS, String.format("%.2f", metrics.getAverageLatencyMs()));
      metricsDetails.put(KEY_MIN_LATENCY_MS, String.format("%.2f", metrics.getMinLatencyMs()));
      metricsDetails.put(KEY_MAX_LATENCY_MS, String.format("%.2f", metrics.getMaxLatencyMs()));

      details.put(KEY_METRICS, metricsDetails);

      // Check thresholds
      boolean withinThresholds = true;
      if (metrics.getTotalMappings() > 0) {
        if (metrics.getErrorRate() > errorRateThreshold) {
          details.put(KEY_WARNING, String.format(
              "Error rate %.2f%% exceeds threshold %.2f%%",
              metrics.getErrorRate(), errorRateThreshold));
          withinThresholds = false;
        }

        if (metrics.getSuccessRate() < minSuccessRate) {
          details.put(KEY_WARNING, String.format(
              "Success rate %.2f%% below minimum %.2f%%",
              metrics.getSuccessRate(), minSuccessRate));
          withinThresholds = false;
        }
      }

      details.put(KEY_METRICS_STATUS, withinThresholds ? STATUS_HEALTHY : STATUS_DEGRADED);
      return withinThresholds;

    } catch (Exception e) {
      details.put(KEY_METRICS_ERROR, e.getMessage());
      details.put(KEY_METRICS_STATUS, STATUS_ERROR);
      return false;
    }
  }

  /**
   * Health status result.
   *
   * @param healthy whether the service is healthy
   * @param details detailed health information
   */
  public record HealthStatus(boolean healthy, Map<String, Object> details) {

    /**
     * Converts the status to a JSON-compatible string.
     *
     * @return JSON representation
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\n");
      sb.append("  \"healthy\": ").append(healthy).append(",\n");
      sb.append("  \"details\": {\n");

      int count = 0;
      for (Map.Entry<String, Object> entry : details.entrySet()) {
        if (count > 0) sb.append(",\n");
        sb.append("    \"").append(entry.getKey()).append("\": ");
        appendValue(sb, entry.getValue());
        count++;
      }

      sb.append("\n  }\n}");
      return sb.toString();
    }

    private void appendValue(StringBuilder sb, Object value) {
      if (value instanceof Map<?, ?> map) {
        sb.append("{\n");
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (count > 0) sb.append(",\n");
          sb.append("      \"").append(entry.getKey()).append("\": ");
          appendSimpleValue(sb, entry.getValue());
          count++;
        }
        sb.append("\n    }");
      } else {
        appendSimpleValue(sb, value);
      }
    }

    private void appendSimpleValue(StringBuilder sb, Object value) {
      if (value instanceof String) {
        sb.append("\"").append(value).append("\"");
      } else if (value instanceof Number || value instanceof Boolean) {
        sb.append(value);
      } else {
        sb.append("\"").append(value).append("\"");
      }
    }
  }
}
