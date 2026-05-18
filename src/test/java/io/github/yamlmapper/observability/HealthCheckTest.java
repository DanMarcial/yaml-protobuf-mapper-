package io.github.yamlmapper.observability;

import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.core.MappingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HealthCheck")
class HealthCheckTest {

  private MappingEngine engine;
  private MappingEngine engineWithMetrics;
  private HealthCheck healthCheck;

  @BeforeEach
  void setUp() {
    MappingSchema schema = new MappingSchema(
        "UserEvent",
        Map.of("visitorId", FieldConfig.of("visitorId", "string", List.of("visitor_id")))
    );

    engine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withSchema("test", schema)
        .build();

    engineWithMetrics = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .withSchema("test", schema)
        .enableMetrics(true)
        .build();

    healthCheck = new HealthCheck(engine);
  }

  @Nested
  @DisplayName("Health Status")
  class HealthStatusTests {

    @Test
    @DisplayName("should report healthy for new engine")
    void shouldReportHealthyForNewEngine() {
      HealthCheck.HealthStatus status = healthCheck.check();

      assertTrue(status.healthy());
      assertEquals("UP", status.details().get("status"));
    }

    @Test
    @DisplayName("should include uptime in details")
    void shouldIncludeUptimeInDetails() {
      HealthCheck.HealthStatus status = healthCheck.check();

      assertNotNull(status.details().get("uptime"));
      assertNotNull(status.details().get("uptimeSeconds"));
    }

    @Test
    @DisplayName("should report metrics status as disabled when not enabled")
    void shouldReportMetricsStatusAsDisabled() {
      HealthCheck.HealthStatus status = healthCheck.check();

      assertEquals("disabled", status.details().get("metricsStatus"));
    }

    @Test
    @DisplayName("should include metrics when enabled")
    void shouldIncludeMetricsWhenEnabled() {
      HealthCheck hc = new HealthCheck(engineWithMetrics);
      HealthCheck.HealthStatus status = hc.check();

      assertTrue(status.details().containsKey("metrics"));
      assertEquals("healthy", status.details().get("metricsStatus"));
    }
  }

  @Nested
  @DisplayName("Liveness and Readiness")
  class LivenessReadinessTests {

    @Test
    @DisplayName("isAlive should return true for valid engine")
    void isAliveShouldReturnTrueForValidEngine() {
      assertTrue(healthCheck.isAlive());
    }

    @Test
    @DisplayName("isReady should return true when healthy")
    void isReadyShouldReturnTrueWhenHealthy() {
      assertTrue(healthCheck.isReady());
    }
  }

  @Nested
  @DisplayName("Uptime")
  class UptimeTests {

    @Test
    @DisplayName("should track uptime")
    void shouldTrackUptime() throws InterruptedException {
      Thread.sleep(10);

      assertTrue(healthCheck.getUptime().toMillis() >= 10);
    }
  }

  @Nested
  @DisplayName("JSON Output")
  class JsonOutputTests {

    @Test
    @DisplayName("should generate valid JSON")
    void shouldGenerateValidJson() {
      HealthCheck.HealthStatus status = healthCheck.check();
      String json = status.toJson();

      assertTrue(json.contains("\"healthy\": true"));
      assertTrue(json.contains("\"status\": \"UP\""));
    }
  }

  @Nested
  @DisplayName("Custom Thresholds")
  class CustomThresholdsTests {

    @Test
    @DisplayName("should use custom error rate threshold")
    void shouldUseCustomErrorRateThreshold() {
      HealthCheck hc = new HealthCheck(engine, 5.0, 95.0);

      // Should still be healthy with no errors
      assertTrue(hc.check().healthy());
    }
  }
}
