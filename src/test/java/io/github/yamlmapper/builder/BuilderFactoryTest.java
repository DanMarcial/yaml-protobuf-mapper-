package io.github.yamlmapper.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.retail.v2.Product;
import com.google.cloud.retail.v2.UserEvent;
import com.google.protobuf.Message;
import io.github.yamlmapper.exception.MappingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for BuilderFactory.
 */
class BuilderFactoryTest {

  private static final Logger log = LoggerFactory.getLogger(BuilderFactoryTest.class);

  private BuilderFactory factory;

  @BeforeEach
  void setUp() {
    factory = new BuilderFactory();
  }

  @Nested
  @DisplayName("createBuilder() with generic type")
  class CreateBuilderGenericTests {

    @Test
    @DisplayName("should create UserEvent.Builder")
    void shouldCreateUserEventBuilder() {
      Message.Builder builder = factory.createBuilder(UserEvent.class);

      assertThat(builder).isNotNull();
      assertThat(builder).isInstanceOf(UserEvent.Builder.class);
    }

    @Test
    @DisplayName("should create Product.Builder")
    void shouldCreateProductBuilder() {
      Message.Builder builder = factory.createBuilder(Product.class);

      assertThat(builder).isNotNull();
      assertThat(builder).isInstanceOf(Product.Builder.class);
    }

    @Test
    @DisplayName("should create independent builders")
    void shouldCreateIndependentBuilders() {
      UserEvent.Builder builder1 = (UserEvent.Builder) factory.createBuilder(UserEvent.class);
      UserEvent.Builder builder2 = (UserEvent.Builder) factory.createBuilder(UserEvent.class);

      builder1.setVisitorId("visitor1");

      assertThat(builder1.getVisitorId()).isEqualTo("visitor1");
      assertThat(builder2.getVisitorId()).isEmpty();
    }

    @Test
    @DisplayName("should reject null class")
    void shouldRejectNullClass() {
      assertThatThrownBy(() -> factory.createBuilder((Class<UserEvent>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null");
    }
  }

  @Nested
  @DisplayName("createBuilderFrom() with Class<?>")
  class CreateBuilderRawTests {

    @Test
    @DisplayName("should create builder from Class<?>")
    void shouldCreateBuilderFromRawClass() {
      Class<?> clazz = UserEvent.class;
      Message.Builder builder = factory.createBuilderFrom(clazz);

      assertThat(builder).isInstanceOf(UserEvent.Builder.class);
    }

    @Test
    @DisplayName("should reject non-Message class")
    void shouldRejectNonMessageClass() {
      assertThatThrownBy(() -> factory.createBuilderFrom(String.class))
          .isInstanceOf(MappingException.class)
          .hasMessageContaining("not a Protobuf Message");
    }
  }

  @Nested
  @DisplayName("Thread safety")
  class ThreadSafetyTests {

    @Test
    @DisplayName("should be thread-safe for concurrent builder creation")
    void shouldBeThreadSafe() throws InterruptedException {
      Thread[] threads = new Thread[10];
      Message.Builder[] builders = new Message.Builder[1000];

      for (int i = 0; i < threads.length; i++) {
        final int threadIndex = i;
        threads[i] = new Thread(() -> {
          for (int j = 0; j < 100; j++) {
            int index = threadIndex * 100 + j;
            builders[index] = factory.createBuilder(UserEvent.class);
          }
        });
        threads[i].start();
      }

      for (Thread thread : threads) {
        thread.join();
      }

      // All builders should be created successfully
      for (Message.Builder builder : builders) {
        assertThat(builder).isNotNull();
        assertThat(builder).isInstanceOf(UserEvent.Builder.class);
      }
    }
  }

  @Nested
  @DisplayName("Built message validation")
  class BuildValidationTests {

    @Test
    @DisplayName("should create functional builder that builds message")
    void shouldCreateFunctionalBuilder() {
      UserEvent.Builder builder = (UserEvent.Builder) factory.createBuilder(UserEvent.class);

      builder.setVisitorId("test-visitor");
      builder.setEventType("search");

      UserEvent event = builder.build();

      assertThat(event.getVisitorId()).isEqualTo("test-visitor");
      assertThat(event.getEventType()).isEqualTo("search");
    }

    @Test
    @DisplayName("should create builder that can be reused with clear")
    void shouldCreateReusableBuilder() {
      UserEvent.Builder builder = (UserEvent.Builder) factory.createBuilder(UserEvent.class);

      builder.setVisitorId("first");
      UserEvent first = builder.build();

      builder.clear();
      builder.setVisitorId("second");
      UserEvent second = builder.build();

      assertThat(first.getVisitorId()).isEqualTo("first");
      assertThat(second.getVisitorId()).isEqualTo("second");
    }
  }

  @Nested
  @DisplayName("Performance characteristics")
  class PerformanceTests {

    @Test
    @DisplayName("should create builders quickly after warmup")
    void shouldCreateBuildersQuickly() {
      // Warm up - multiple iterations for JIT
      for (int i = 0; i < 1000; i++) {
        factory.createBuilder(UserEvent.class);
      }

      // Time creation of 10000 builders
      long start = System.nanoTime();
      for (int i = 0; i < 10000; i++) {
        factory.createBuilder(UserEvent.class);
      }
      long elapsed = System.nanoTime() - start;

      // Average should be under 10 microseconds (10000ns) per builder
      // In practice MethodHandle achieves ~3-100ns depending on JIT state
      // We use a generous threshold to avoid CI flakiness
      double avgNanos = elapsed / 10000.0;
      assertThat(avgNanos).isLessThan(10000.0);

      log.info("Average builder creation time: {} ns", String.format("%.2f", avgNanos));
    }
  }
}
