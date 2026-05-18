package io.github.yamlmapper.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("ValidatorRegistry")
class ValidatorRegistryTest {

  private ValidatorRegistry registry;
  private ProtobufMessageValidator mockValidator1;
  private ProtobufMessageValidator mockValidator2;

  @BeforeEach
  void setUp() {
    registry = new ValidatorRegistry();
    mockValidator1 = mock(ProtobufMessageValidator.class);
    mockValidator2 = mock(ProtobufMessageValidator.class);
  }

  @Nested
  @DisplayName("Registration")
  class Registration {

    @Test
    @DisplayName("should register validator successfully")
    void shouldRegisterValidatorSuccessfully() {
      registry.register("UserEvent", mockValidator1);

      assertTrue(registry.hasValidator("UserEvent"));
      assertEquals(mockValidator1, registry.get("UserEvent"));
    }

    @Test
    @DisplayName("should support fluent chaining")
    void shouldSupportFluentChaining() {
      ValidatorRegistry result = registry
          .register("UserEvent", mockValidator1)
          .register("Product", mockValidator2);

      assertSame(registry, result);
      assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("should replace existing validator")
    void shouldReplaceExistingValidator() {
      registry.register("UserEvent", mockValidator1);
      registry.register("UserEvent", mockValidator2);

      assertEquals(mockValidator2, registry.get("UserEvent"));
      assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("should throw on null message type")
    void shouldThrowOnNullMessageType() {
      assertThrows(IllegalArgumentException.class,
          () -> registry.register(null, mockValidator1));
    }

    @Test
    @DisplayName("should throw on blank message type")
    void shouldThrowOnBlankMessageType() {
      assertThrows(IllegalArgumentException.class,
          () -> registry.register("   ", mockValidator1));
    }

    @Test
    @DisplayName("should throw on null validator")
    void shouldThrowOnNullValidator() {
      assertThrows(IllegalArgumentException.class,
          () -> registry.register("UserEvent", null));
    }
  }

  @Nested
  @DisplayName("Retrieval")
  class Retrieval {

    @Test
    @DisplayName("should return null for unregistered type")
    void shouldReturnNullForUnregisteredType() {
      assertNull(registry.get("UnknownType"));
    }

    @Test
    @DisplayName("should return null for null type")
    void shouldReturnNullForNullType() {
      assertNull(registry.get(null));
    }

    @Test
    @DisplayName("should return registered validator")
    void shouldReturnRegisteredValidator() {
      registry.register("UserEvent", mockValidator1);

      assertEquals(mockValidator1, registry.get("UserEvent"));
    }
  }

  @Nested
  @DisplayName("hasValidator")
  class HasValidator {

    @Test
    @DisplayName("should return true for registered type")
    void shouldReturnTrueForRegisteredType() {
      registry.register("UserEvent", mockValidator1);

      assertTrue(registry.hasValidator("UserEvent"));
    }

    @Test
    @DisplayName("should return false for unregistered type")
    void shouldReturnFalseForUnregisteredType() {
      assertFalse(registry.hasValidator("UnknownType"));
    }

    @Test
    @DisplayName("should return false for null type")
    void shouldReturnFalseForNullType() {
      assertFalse(registry.hasValidator(null));
    }
  }

  @Nested
  @DisplayName("Removal")
  class Removal {

    @Test
    @DisplayName("should remove registered validator")
    void shouldRemoveRegisteredValidator() {
      registry.register("UserEvent", mockValidator1);

      ProtobufMessageValidator removed = registry.remove("UserEvent");

      assertEquals(mockValidator1, removed);
      assertFalse(registry.hasValidator("UserEvent"));
    }

    @Test
    @DisplayName("should return null when removing unregistered type")
    void shouldReturnNullWhenRemovingUnregisteredType() {
      assertNull(registry.remove("UnknownType"));
    }

    @Test
    @DisplayName("should return null when removing null type")
    void shouldReturnNullWhenRemovingNullType() {
      assertNull(registry.remove(null));
    }
  }

  @Nested
  @DisplayName("Size and Clear")
  class SizeAndClear {

    @Test
    @DisplayName("should return correct size")
    void shouldReturnCorrectSize() {
      assertEquals(0, registry.size());

      registry.register("UserEvent", mockValidator1);
      assertEquals(1, registry.size());

      registry.register("Product", mockValidator2);
      assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("should clear all validators")
    void shouldClearAllValidators() {
      registry.register("UserEvent", mockValidator1);
      registry.register("Product", mockValidator2);

      registry.clear();

      assertEquals(0, registry.size());
      assertFalse(registry.hasValidator("UserEvent"));
      assertFalse(registry.hasValidator("Product"));
    }
  }

  @Nested
  @DisplayName("Thread Safety")
  class ThreadSafety {

    @Test
    @DisplayName("should handle concurrent registrations")
    void shouldHandleConcurrentRegistrations() throws InterruptedException {
      int threads = 10;
      Thread[] threadArray = new Thread[threads];

      for (int i = 0; i < threads; i++) {
        final int index = i;
        threadArray[i] = new Thread(() -> {
          ProtobufMessageValidator validator = mock(ProtobufMessageValidator.class);
          registry.register("Type" + index, validator);
        });
      }

      for (Thread t : threadArray) t.start();
      for (Thread t : threadArray) t.join();

      assertEquals(threads, registry.size());
    }
  }
}
