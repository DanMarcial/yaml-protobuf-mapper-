package io.github.yamlmapper.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.retail.v2.Product;
import com.google.cloud.retail.v2.UserEvent;
import com.google.cloud.retail.v2.UserInfo;
import io.github.yamlmapper.exception.MappingException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for SetterResolver.
 */
class SetterResolverTest {

  private SetterResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new SetterResolver();
  }

  @Nested
  @DisplayName("setValue() for singular fields")
  class SingularFieldTests {

    @Test
    @DisplayName("should set String field")
    void shouldSetStringField() {
      UserEvent.Builder builder = UserEvent.newBuilder();

      resolver.setValue(builder, "visitorId", "visitor-123");

      assertThat(builder.getVisitorId()).isEqualTo("visitor-123");
    }

    @Test
    @DisplayName("should set eventType field")
    void shouldSetEventTypeField() {
      UserEvent.Builder builder = UserEvent.newBuilder();

      resolver.setValue(builder, "eventType", "search");

      assertThat(builder.getEventType()).isEqualTo("search");
    }

    @Test
    @DisplayName("should set nested object field")
    void shouldSetNestedObjectField() {
      UserEvent.Builder builder = UserEvent.newBuilder();
      UserInfo userInfo = UserInfo.newBuilder()
          .setUserId("user-123")
          .build();

      resolver.setValue(builder, "userInfo", userInfo);

      assertThat(builder.getUserInfo().getUserId()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("should skip null values silently")
    void shouldSkipNullValues() {
      UserEvent.Builder builder = UserEvent.newBuilder();
      builder.setVisitorId("original");

      resolver.setValue(builder, "visitorId", null);

      // Value should remain unchanged
      assertThat(builder.getVisitorId()).isEqualTo("original");
    }
  }

  @Nested
  @DisplayName("setValue() for repeated fields")
  class RepeatedFieldTests {

    @Test
    @DisplayName("should add all items to repeated field with collection")
    void shouldAddAllItemsWithCollection() {
      UserEvent.Builder builder = UserEvent.newBuilder();

      resolver.setValue(builder, "pageCategories", List.of("Electronics", "Computers"));

      assertThat(builder.getPageCategoriesList())
          .containsExactly("Electronics", "Computers");
    }

    @Test
    @DisplayName("should add single item to repeated field")
    void shouldAddSingleItemToRepeatedField() {
      UserEvent.Builder builder = UserEvent.newBuilder();

      resolver.setValue(builder, "pageCategories", "Electronics");

      assertThat(builder.getPageCategoriesList()).containsExactly("Electronics");
    }

    @Test
    @DisplayName("should handle empty collection")
    void shouldHandleEmptyCollection() {
      UserEvent.Builder builder = UserEvent.newBuilder();

      resolver.setValue(builder, "pageCategories", List.of());

      assertThat(builder.getPageCategoriesList()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Error handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should throw for unknown field")
    void shouldThrowForUnknownField() {
      UserEvent.Builder builder = UserEvent.newBuilder();

      assertThatThrownBy(() -> resolver.setValue(builder, "nonExistentField", "value"))
          .isInstanceOf(MappingException.class)
          .hasMessageContaining("nonExistentField")
          .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("should reject null builder")
    void shouldRejectNullBuilder() {
      assertThatThrownBy(() -> resolver.setValue(null, "visitorId", "value"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("should reject null field name")
    void shouldRejectNullFieldName() {
      UserEvent.Builder builder = UserEvent.newBuilder();

      assertThatThrownBy(() -> resolver.setValue(builder, null, "value"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null or blank");
    }

    @Test
    @DisplayName("should reject blank field name")
    void shouldRejectBlankFieldName() {
      UserEvent.Builder builder = UserEvent.newBuilder();

      assertThatThrownBy(() -> resolver.setValue(builder, "  ", "value"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null or blank");
    }

    @Test
    @DisplayName("should throw for type mismatch")
    void shouldThrowForTypeMismatch() {
      UserEvent.Builder builder = UserEvent.newBuilder();

      // visitorId expects String, not Integer
      assertThatThrownBy(() -> resolver.setValue(builder, "visitorId", 12345))
          .isInstanceOf(MappingException.class);
    }
  }

  @Nested
  @DisplayName("Thread safety")
  class ThreadSafetyTests {

    @Test
    @DisplayName("should be thread-safe for concurrent access")
    void shouldBeThreadSafe() throws InterruptedException {
      Thread[] threads = new Thread[10];

      for (int i = 0; i < threads.length; i++) {
        final int threadIndex = i;
        threads[i] = new Thread(() -> {
          for (int j = 0; j < 100; j++) {
            UserEvent.Builder builder = UserEvent.newBuilder();
            resolver.setValue(builder, "visitorId", "visitor-" + threadIndex + "-" + j);
            resolver.setValue(builder, "eventType", "search");

            assertThat(builder.getVisitorId()).startsWith("visitor-" + threadIndex);
          }
        });
        threads[i].start();
      }

      for (Thread thread : threads) {
        thread.join();
      }
    }
  }

  @Nested
  @DisplayName("Different builder types")
  class DifferentBuilderTypesTests {

    @Test
    @DisplayName("should work with Product builder")
    void shouldWorkWithProductBuilder() {
      Product.Builder builder = Product.newBuilder();

      resolver.setValue(builder, "id", "product-123");
      resolver.setValue(builder, "title", "Gaming Laptop");

      assertThat(builder.getId()).isEqualTo("product-123");
      assertThat(builder.getTitle()).isEqualTo("Gaming Laptop");
    }
  }
}
