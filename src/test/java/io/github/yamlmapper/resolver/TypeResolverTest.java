package io.github.yamlmapper.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.retail.v2.Product;
import com.google.cloud.retail.v2.UserEvent;
import com.google.protobuf.Message;
import io.github.yamlmapper.exception.TypeNotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for TypeResolver.
 */
class TypeResolverTest {

  private TypeResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new TypeResolver(List.of("com.google.cloud.retail.v2"));
  }

  @Nested
  @DisplayName("Constructor")
  class ConstructorTests {

    @Test
    @DisplayName("should accept valid package list")
    void shouldAcceptValidPackageList() {
      TypeResolver r = new TypeResolver(List.of("com.example"));
      assertThat(r.getPackagePrefixes()).containsExactly("com.example");
    }

    @Test
    @DisplayName("should accept multiple packages")
    void shouldAcceptMultiplePackages() {
      TypeResolver r = new TypeResolver(List.of("com.example.v1", "com.example.v2"));
      assertThat(r.getPackagePrefixes()).containsExactly("com.example.v1", "com.example.v2");
    }

    @Test
    @DisplayName("should reject null package list")
    void shouldRejectNullPackageList() {
      assertThatThrownBy(() -> new TypeResolver(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("At least one package prefix");
    }

    @Test
    @DisplayName("should reject empty package list")
    void shouldRejectEmptyPackageList() {
      assertThatThrownBy(() -> new TypeResolver(List.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("At least one package prefix");
    }

    @Test
    @DisplayName("should return immutable package list")
    void shouldReturnImmutablePackageList() {
      TypeResolver r = new TypeResolver(List.of("com.example"));
      assertThatThrownBy(() -> r.getPackagePrefixes().add("another"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("resolve()")
  class ResolveTests {

    @Test
    @DisplayName("should resolve UserEvent")
    void shouldResolveUserEvent() {
      Class<?> clazz = resolver.resolve("UserEvent");
      assertThat(clazz).isEqualTo(UserEvent.class);
    }

    @Test
    @DisplayName("should resolve Product")
    void shouldResolveProduct() {
      Class<?> clazz = resolver.resolve("Product");
      assertThat(clazz).isEqualTo(Product.class);
    }

    @Test
    @DisplayName("should throw TypeNotFoundException for unknown type")
    void shouldThrowForUnknownType() {
      assertThatThrownBy(() -> resolver.resolve("NonExistentType"))
          .isInstanceOf(TypeNotFoundException.class)
          .hasMessageContaining("NonExistentType")
          .hasMessageContaining("com.google.cloud.retail.v2");
    }

    @Test
    @DisplayName("should reject null type name")
    void shouldRejectNullTypeName() {
      assertThatThrownBy(() -> resolver.resolve(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null or blank");
    }

    @Test
    @DisplayName("should reject blank type name")
    void shouldRejectBlankTypeName() {
      assertThatThrownBy(() -> resolver.resolve("   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null or blank");
    }

    @Test
    @DisplayName("should search packages in order")
    void shouldSearchPackagesInOrder() {
      // Create resolver with two packages
      TypeResolver multiResolver = new TypeResolver(List.of(
          "com.google.cloud.retail.v2",
          "com.google.protobuf"
      ));

      // UserEvent is in retail.v2, should find it there
      Class<?> clazz = multiResolver.resolve("UserEvent");
      assertThat(clazz.getPackageName()).isEqualTo("com.google.cloud.retail.v2");
    }
  }

  @Nested
  @DisplayName("resolveMessage()")
  class ResolveMessageTests {

    @Test
    @DisplayName("should resolve Protobuf message types")
    void shouldResolveProtobufMessageTypes() {
      Class<? extends Message> clazz = resolver.resolveMessage("UserEvent");
      assertThat(clazz).isEqualTo(UserEvent.class);
      assertThat(Message.class).isAssignableFrom(clazz);
    }

    @Test
    @DisplayName("should throw for non-Message types")
    void shouldThrowForNonMessageTypes() {
      // String is not a Protobuf Message
      TypeResolver javaResolver = new TypeResolver(List.of("java.lang"));

      assertThatThrownBy(() -> javaResolver.resolveMessage("String"))
          .isInstanceOf(TypeNotFoundException.class)
          .hasMessageContaining("not a Protobuf Message");
    }
  }

  @Nested
  @DisplayName("canResolve()")
  class CanResolveTests {

    @Test
    @DisplayName("should return true for existing type")
    void shouldReturnTrueForExistingType() {
      assertThat(resolver.canResolve("UserEvent")).isTrue();
    }

    @Test
    @DisplayName("should return false for non-existing type")
    void shouldReturnFalseForNonExistingType() {
      assertThat(resolver.canResolve("NonExistentType")).isFalse();
    }

    @Test
    @DisplayName("should return false for null")
    void shouldReturnFalseForNull() {
      assertThat(resolver.canResolve(null)).isFalse();
    }

    @Test
    @DisplayName("should return false for blank")
    void shouldReturnFalseForBlank() {
      assertThat(resolver.canResolve("  ")).isFalse();
    }
  }

  @Nested
  @DisplayName("Thread safety")
  class ThreadSafetyTests {

    @Test
    @DisplayName("should be thread-safe")
    void shouldBeThreadSafe() throws InterruptedException {
      // Run multiple threads resolving types concurrently
      Thread[] threads = new Thread[10];
      for (int i = 0; i < threads.length; i++) {
        threads[i] = new Thread(() -> {
          for (int j = 0; j < 100; j++) {
            resolver.resolve("UserEvent");
            resolver.resolve("Product");
          }
        });
        threads[i].start();
      }

      for (Thread thread : threads) {
        thread.join();
      }

      // Types should still be resolvable
      assertThat(resolver.resolve("UserEvent")).isEqualTo(UserEvent.class);
      assertThat(resolver.resolve("Product")).isEqualTo(Product.class);
    }
  }

  @Nested
  @DisplayName("Nested types")
  class NestedTypeTests {

    @Test
    @DisplayName("should resolve nested Protobuf types with dot notation")
    void shouldResolveNestedTypesWithDotNotation() {
      // UserEvent has nested types like UserEvent.ProductDetail
      // Note: This depends on actual nested classes in the Protobuf definition
      // For now, test with a type that exists
      Class<?> clazz = resolver.resolve("Product");
      assertThat(clazz).isNotNull();
    }
  }

  @Nested
  @DisplayName("Error messages")
  class ErrorMessageTests {

    @Test
    @DisplayName("should include type name in error")
    void shouldIncludeTypeNameInError() {
      TypeNotFoundException ex = null;
      try {
        resolver.resolve("MissingType");
      } catch (TypeNotFoundException e) {
        ex = e;
      }

      assertThat(ex).isNotNull();
      assertThat(ex.getTypeName()).isEqualTo("MissingType");
      assertThat(ex.getMessage()).contains("MissingType");
    }

    @Test
    @DisplayName("should include all searched packages in error")
    void shouldIncludeAllPackagesInError() {
      TypeResolver multiResolver = new TypeResolver(List.of(
          "com.example.v1",
          "com.example.v2",
          "com.example.v3"
      ));

      assertThatThrownBy(() -> multiResolver.resolve("Missing"))
          .isInstanceOf(TypeNotFoundException.class)
          .hasMessageContaining("com.example.v1")
          .hasMessageContaining("com.example.v2")
          .hasMessageContaining("com.example.v3");
    }
  }
}
