package io.github.yamlmapper.observability;

/**
 * Constants for health check JSON keys and status values.
 *
 * <p>These constants define the keys used in health check responses
 * and the possible status values for different health states.
 */
public final class HealthCheckKeys {

    // ============================================
    // JSON Keys
    // ============================================

    /** Key for overall status. */
    public static final String KEY_STATUS = "status";

    /** Key for uptime duration. */
    public static final String KEY_UPTIME = "uptime";

    /** Key for uptime in seconds. */
    public static final String KEY_UPTIME_SECONDS = "uptimeSeconds";

    /** Key for metrics object. */
    public static final String KEY_METRICS = "metrics";

    /** Key for metrics status. */
    public static final String KEY_METRICS_STATUS = "metricsStatus";

    /** Key for metrics enabled flag. */
    public static final String KEY_METRICS_ENABLED = "metricsEnabled";

    /** Key for metrics error message. */
    public static final String KEY_METRICS_ERROR = "metricsError";

    /** Key for warning messages. */
    public static final String KEY_WARNING = "warning";

    /** Key for engine error message. */
    public static final String KEY_ENGINE_ERROR = "engineError";

    /** Key for post-mapping validation flag. */
    public static final String KEY_POST_MAPPING_VALIDATION = "postMappingValidation";

    /** Key for success rate metric. */
    public static final String KEY_SUCCESS_RATE = "successRate";

    /** Key for error rate metric. */
    public static final String KEY_ERROR_RATE = "errorRate";

    /** Key for average latency metric. */
    public static final String KEY_AVG_LATENCY_MS = "avgLatencyMs";

    /** Key for minimum latency metric. */
    public static final String KEY_MIN_LATENCY_MS = "minLatencyMs";

    /** Key for maximum latency metric. */
    public static final String KEY_MAX_LATENCY_MS = "maxLatencyMs";

    // ============================================
    // Status Values
    // ============================================

    /** Status value: system is up and healthy. */
    public static final String STATUS_UP = "UP";

    /** Status value: system is down. */
    public static final String STATUS_DOWN = "DOWN";

    /** Status value: health check in progress. */
    public static final String STATUS_CHECKING = "checking";

    /** Status value: component is healthy. */
    public static final String STATUS_HEALTHY = "healthy";

    /** Status value: component is degraded but functional. */
    public static final String STATUS_DEGRADED = "degraded";

    /** Status value: component is disabled. */
    public static final String STATUS_DISABLED = "disabled";

    /** Status value: component has error. */
    public static final String STATUS_ERROR = "error";

    private HealthCheckKeys() {
        // Utility class
    }
}
