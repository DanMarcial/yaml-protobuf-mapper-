package io.github.yamlmapper.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * OpenTelemetry tracing support for mapping operations.
 *
 * <p>Provides distributed tracing integration using OpenTelemetry API.
 * Traces are automatically propagated and can be visualized in tools
 * like Jaeger, Zipkin, or any OpenTelemetry-compatible backend.
 *
 * <p>Example usage:
 * <pre>{@code
 * TracingSupport tracing = TracingSupport.create("yaml-mapper");
 *
 * // Trace a mapping operation
 * UserEvent result = tracing.trace("map-user-event", () -> {
 *     return engine.map(json, "search", UserEvent.class);
 * });
 *
 * // Trace with attributes
 * tracing.traceWithAttributes("map-batch",
 *     Map.of("batch.size", batchSize, "config.id", configId),
 *     () -> batchMapper.mapBatch(items, configId, UserEvent.class)
 * );
 * }</pre>
 *
 * <p>Integration with OpenTelemetry SDK:
 * <pre>{@code
 * // In your application bootstrap
 * SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
 *     .addSpanProcessor(BatchSpanProcessor.builder(
 *         OtlpGrpcSpanExporter.builder().build()
 *     ).build())
 *     .build();
 *
 * OpenTelemetrySdk.builder()
 *     .setTracerProvider(tracerProvider)
 *     .buildAndRegisterGlobal();
 *
 * // Now TracingSupport will automatically use the configured tracer
 * }</pre>
 *
 * <p>This class is thread-safe.
 */
public class TracingSupport {

  private static final Logger log = LoggerFactory.getLogger(TracingSupport.class);

  private final Tracer tracer;
  private final String serviceName;
  private final boolean enabled;

  /**
   * Creates a TracingSupport with the given service name.
   *
   * @param serviceName the service name for traces
   */
  private TracingSupport(String serviceName, boolean enabled) {
    this.serviceName = serviceName;
    this.enabled = enabled;

    if (enabled) {
      this.tracer = GlobalOpenTelemetry.getTracer(serviceName, "1.0.0");
      log.info("OpenTelemetry tracing enabled for service: {}", serviceName);
    } else {
      this.tracer = null;
      log.debug("OpenTelemetry tracing disabled");
    }
  }

  /**
   * Creates a TracingSupport instance with tracing enabled.
   *
   * @param serviceName the service name for traces
   * @return a new TracingSupport instance
   */
  public static TracingSupport create(String serviceName) {
    return new TracingSupport(serviceName, true);
  }

  /**
   * Creates a disabled TracingSupport (no-op).
   *
   * @return a disabled TracingSupport instance
   */
  public static TracingSupport disabled() {
    return new TracingSupport("disabled", false);
  }

  /**
   * Executes a supplier within a traced span.
   *
   * @param spanName the name of the span
   * @param operation the operation to trace
   * @param <T> the return type
   * @return the result of the operation
   */
  public <T> T trace(String spanName, Supplier<T> operation) {
    if (!enabled) {
      return operation.get();
    }

    Span span = tracer.spanBuilder(spanName)
        .setSpanKind(SpanKind.INTERNAL)
        .startSpan();

    try (Scope scope = span.makeCurrent()) {
      T result = operation.get();
      span.setStatus(StatusCode.OK);
      return result;
    } catch (Exception e) {
      span.setStatus(StatusCode.ERROR, e.getMessage());
      span.recordException(e);
      throw e;
    } finally {
      span.end();
    }
  }

  /**
   * Executes a runnable within a traced span.
   *
   * @param spanName the name of the span
   * @param operation the operation to trace
   */
  public void trace(String spanName, Runnable operation) {
    trace(spanName, () -> {
      operation.run();
      return null;
    });
  }

  /**
   * Executes a supplier within a traced span with custom attributes.
   *
   * @param spanName the name of the span
   * @param attributes attributes to add to the span
   * @param operation the operation to trace
   * @param <T> the return type
   * @return the result of the operation
   */
  public <T> T traceWithAttributes(
      String spanName,
      java.util.Map<String, Object> attributes,
      Supplier<T> operation) {

    if (!enabled) {
      return operation.get();
    }

    Span span = tracer.spanBuilder(spanName)
        .setSpanKind(SpanKind.INTERNAL)
        .startSpan();

    // Add attributes
    if (attributes != null) {
      attributes.forEach((key, value) -> {
        if (value instanceof String s) {
          span.setAttribute(key, s);
        } else if (value instanceof Long l) {
          span.setAttribute(key, l);
        } else if (value instanceof Integer i) {
          span.setAttribute(key, i.longValue());
        } else if (value instanceof Double d) {
          span.setAttribute(key, d);
        } else if (value instanceof Boolean b) {
          span.setAttribute(key, b);
        } else if (value != null) {
          span.setAttribute(key, value.toString());
        }
      });
    }

    try (Scope scope = span.makeCurrent()) {
      T result = operation.get();
      span.setStatus(StatusCode.OK);
      return result;
    } catch (Exception e) {
      span.setStatus(StatusCode.ERROR, e.getMessage());
      span.recordException(e);
      throw e;
    } finally {
      span.end();
    }
  }

  /**
   * Creates a child span for nested operations.
   *
   * @param spanName the name of the child span
   * @return a SpanScope that must be closed when done
   */
  public SpanScope startSpan(String spanName) {
    if (!enabled) {
      return SpanScope.NOOP;
    }

    Span span = tracer.spanBuilder(spanName)
        .setSpanKind(SpanKind.INTERNAL)
        .startSpan();

    Scope scope = span.makeCurrent();
    return new SpanScope(span, scope);
  }

  /**
   * Checks if tracing is enabled.
   *
   * @return true if tracing is enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Gets the service name.
   *
   * @return the service name
   */
  public String getServiceName() {
    return serviceName;
  }

  /**
   * Represents an active span scope that must be closed.
   */
  public static class SpanScope implements AutoCloseable {

    private static final SpanScope NOOP = new SpanScope(null, null);

    private final Span span;
    private final Scope scope;

    private SpanScope(Span span, Scope scope) {
      this.span = span;
      this.scope = scope;
    }

    /**
     * Sets the span status to OK.
     */
    public void setOk() {
      if (span != null) {
        span.setStatus(StatusCode.OK);
      }
    }

    /**
     * Sets the span status to ERROR.
     *
     * @param message the error message
     */
    public void setError(String message) {
      if (span != null) {
        span.setStatus(StatusCode.ERROR, message);
      }
    }

    /**
     * Records an exception on the span.
     *
     * @param exception the exception to record
     */
    public void recordException(Throwable exception) {
      if (span != null) {
        span.recordException(exception);
        span.setStatus(StatusCode.ERROR, exception.getMessage());
      }
    }

    /**
     * Adds an attribute to the span.
     *
     * @param key the attribute key
     * @param value the attribute value
     */
    public void setAttribute(String key, String value) {
      if (span != null) {
        span.setAttribute(key, value);
      }
    }

    /**
     * Adds a numeric attribute to the span.
     *
     * @param key the attribute key
     * @param value the attribute value
     */
    public void setAttribute(String key, long value) {
      if (span != null) {
        span.setAttribute(key, value);
      }
    }

    @Override
    public void close() {
      if (scope != null) {
        scope.close();
      }
      if (span != null) {
        span.end();
      }
    }
  }
}
