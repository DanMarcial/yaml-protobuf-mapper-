package io.github.yamlmapper.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TracingSupport")
class TracingSupportTest {

  @Nested
  @DisplayName("Disabled Tracing")
  class DisabledTracingTests {

    @Test
    @DisplayName("disabled tracing should execute operation without error")
    void disabledTracingShouldExecuteOperationWithoutError() {
      TracingSupport tracing = TracingSupport.disabled();

      String result = tracing.trace("test-span", () -> "hello");

      assertEquals("hello", result);
      assertFalse(tracing.isEnabled());
    }

    @Test
    @DisplayName("disabled tracing should execute runnable")
    void disabledTracingShouldExecuteRunnable() {
      TracingSupport tracing = TracingSupport.disabled();
      AtomicBoolean executed = new AtomicBoolean(false);

      tracing.trace("test-span", () -> executed.set(true));

      assertTrue(executed.get());
    }

    @Test
    @DisplayName("disabled tracing with attributes should work")
    void disabledTracingWithAttributesShouldWork() {
      TracingSupport tracing = TracingSupport.disabled();

      Integer result = tracing.traceWithAttributes(
          "test-span",
          Map.of("key", "value"),
          () -> 42
      );

      assertEquals(42, result);
    }

    @Test
    @DisplayName("disabled span scope should be no-op")
    void disabledSpanScopeShouldBeNoOp() {
      TracingSupport tracing = TracingSupport.disabled();

      try (TracingSupport.SpanScope scope = tracing.startSpan("test")) {
        scope.setAttribute("key", "value");
        scope.setAttribute("count", 1L);
        scope.setOk();
      }
      // Should not throw
    }
  }

  @Nested
  @DisplayName("Enabled Tracing")
  class EnabledTracingTests {

    @Test
    @DisplayName("should create enabled tracing support")
    void shouldCreateEnabledTracingSupport() {
      TracingSupport tracing = TracingSupport.create("test-service");

      assertTrue(tracing.isEnabled());
      assertEquals("test-service", tracing.getServiceName());
    }

    @Test
    @DisplayName("should execute supplier and return result")
    void shouldExecuteSupplierAndReturnResult() {
      TracingSupport tracing = TracingSupport.create("test-service");

      String result = tracing.trace("test-span", () -> "result");

      assertEquals("result", result);
    }

    @Test
    @DisplayName("should propagate exceptions")
    void shouldPropagateExceptions() {
      TracingSupport tracing = TracingSupport.create("test-service");

      assertThrows(RuntimeException.class, () ->
          tracing.trace("test-span", () -> {
            throw new RuntimeException("test error");
          })
      );
    }

    @Test
    @DisplayName("should execute with attributes")
    void shouldExecuteWithAttributes() {
      TracingSupport tracing = TracingSupport.create("test-service");

      Map<String, Object> attrs = Map.of(
          "string", "value",
          "long", 123L,
          "int", 456,
          "double", 1.5,
          "boolean", true
      );

      String result = tracing.traceWithAttributes("test-span", attrs, () -> "done");

      assertEquals("done", result);
    }

    @Test
    @DisplayName("should handle null attributes")
    void shouldHandleNullAttributes() {
      TracingSupport tracing = TracingSupport.create("test-service");

      String result = tracing.traceWithAttributes("test-span", null, () -> "done");

      assertEquals("done", result);
    }
  }

  @Nested
  @DisplayName("SpanScope")
  class SpanScopeTests {

    @Test
    @DisplayName("span scope should be closeable")
    void spanScopeShouldBeCloseable() {
      TracingSupport tracing = TracingSupport.create("test-service");
      AtomicInteger counter = new AtomicInteger(0);

      try (TracingSupport.SpanScope scope = tracing.startSpan("test")) {
        counter.incrementAndGet();
        scope.setAttribute("step", "processing");
        scope.setOk();
      }

      assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("span scope should record errors")
    void spanScopeShouldRecordErrors() {
      TracingSupport tracing = TracingSupport.create("test-service");

      try (TracingSupport.SpanScope scope = tracing.startSpan("test")) {
        try {
          throw new RuntimeException("test error");
        } catch (Exception e) {
          scope.recordException(e);
        }
      }
      // Should not throw
    }

    @Test
    @DisplayName("span scope should set error status")
    void spanScopeShouldSetErrorStatus() {
      TracingSupport tracing = TracingSupport.create("test-service");

      try (TracingSupport.SpanScope scope = tracing.startSpan("test")) {
        scope.setError("Something went wrong");
      }
      // Should not throw
    }
  }
}
