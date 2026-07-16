package io.github.yamlmapper.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for TransformRegistry.
 */
class TransformRegistryTest {

  private TransformRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new TransformRegistry();
  }

  @Nested
  @DisplayName("register()")
  class RegisterTests {

    @Test
    @DisplayName("should register transform by name")
    void shouldRegisterByName() {
      Transform transform = (node, ctx) -> node;

      registry.register("myTransform", transform);

      assertThat(registry.get("myTransform")).isSameAs(transform);
    }

    @Test
    @DisplayName("should register transform using getName()")
    void shouldRegisterUsingGetName() {
      Transform transform = new Transform() {
        @Override
        public JsonNode apply(JsonNode node, TransformContext context) {
          return node;
        }

        @Override
        public String getName() {
          return "customTransform";
        }
      };

      registry.register(transform);

      assertThat(registry.get("customTransform")).isNotNull();
    }

    @Test
    @DisplayName("should allow chaining registrations")
    void shouldAllowChaining() {
      registry
          .register("a", (n, c) -> n)
          .register("b", (n, c) -> n)
          .register("c", (n, c) -> n);

      assertThat(registry.get("a")).isNotNull();
      assertThat(registry.get("b")).isNotNull();
      assertThat(registry.get("c")).isNotNull();
    }

    @Test
    @DisplayName("should override existing transform")
    void shouldOverrideExisting() {
      Transform first = (n, c) -> new TextNode("first");
      Transform second = (n, c) -> new TextNode("second");

      registry.register("transform", first);
      registry.register("transform", second);

      assertThat(registry.get("transform")).isSameAs(second);
    }

    @Test
    @DisplayName("should reject null name")
    void shouldRejectNullName() {
      assertThatThrownBy(() -> registry.register(null, (n, c) -> n))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null or blank");
    }

    @Test
    @DisplayName("should reject blank name")
    void shouldRejectBlankName() {
      assertThatThrownBy(() -> registry.register("  ", (n, c) -> n))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null or blank");
    }

    @Test
    @DisplayName("should reject null transform")
    void shouldRejectNullTransform() {
      assertThatThrownBy(() -> registry.register("name", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null");
    }
  }

  @Nested
  @DisplayName("get()")
  class GetTests {

    @Test
    @DisplayName("should return null for missing transform")
    void shouldReturnNullForMissing() {
      assertThat(registry.get("missing")).isNull();
    }

    @Test
    @DisplayName("should return null for null name")
    void shouldReturnNullForNullName() {
      assertThat(registry.get(null)).isNull();
    }

    @Test
    @DisplayName("should return registered transform")
    void shouldReturnRegisteredTransform() {
      Transform transform = (node, ctx) -> node;
      registry.register("myTransform", transform);

      assertThat(registry.get("myTransform")).isSameAs(transform);
    }
  }

  @Nested
  @DisplayName("Thread safety")
  class ThreadSafetyTests {

    @Test
    @DisplayName("should be thread-safe for concurrent registration")
    void shouldBeThreadSafeForRegistration() throws InterruptedException {
      Thread[] threads = new Thread[10];

      for (int i = 0; i < threads.length; i++) {
        final int index = i;
        threads[i] = new Thread(() -> {
          for (int j = 0; j < 100; j++) {
            registry.register("transform-" + index + "-" + j, (n, c) -> n);
          }
        });
        threads[i].start();
      }

      for (Thread thread : threads) {
        thread.join();
      }

      // Verify all transforms were registered
      assertThat(registry.get("transform-0-0")).isNotNull();
      assertThat(registry.get("transform-9-99")).isNotNull();
    }
  }
}
