package io.github.yamlmapper.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the exception hierarchy.
 */
class ExceptionHierarchyTest {

  // ============================================
  // Parameterized tests for common behaviors
  // ============================================

  static Stream<Arguments> allExceptionTypes() {
    return Stream.of(
        Arguments.of(new MappingException("test"), "MappingException"),
        Arguments.of(new TypeNotFoundException("Type", "not found"), "TypeNotFoundException"),
        Arguments.of(new FieldExtractionException("field", List.of("src")), "FieldExtractionException"),
        Arguments.of(new ConfigurationException("error"), "ConfigurationException")
    );
  }

  @ParameterizedTest(name = "{1} extends RuntimeException")
  @MethodSource("allExceptionTypes")
  @DisplayName("All exceptions should be RuntimeExceptions")
  void allExceptionsShouldBeRuntimeExceptions(MappingException ex, String name) {
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }

  static Stream<Arguments> subclassExceptionTypes() {
    return Stream.of(
        Arguments.of(new TypeNotFoundException("Type", "not found"), "TypeNotFoundException"),
        Arguments.of(new FieldExtractionException("field", List.of("src")), "FieldExtractionException"),
        Arguments.of(new ConfigurationException("error"), "ConfigurationException")
    );
  }

  @ParameterizedTest(name = "{1} extends MappingException")
  @MethodSource("subclassExceptionTypes")
  @DisplayName("Subclasses should extend MappingException")
  void subclassesShouldExtendMappingException(Exception ex, String name) {
    assertThat(ex).isInstanceOf(MappingException.class);
  }

  @Test
  @DisplayName("MappingException should catch all subclasses")
  void mappingExceptionShouldCatchAllSubclasses() {
    Throwable caught = catchThrowable(() -> {
      throw new TypeNotFoundException("Test", "not found");
    });
    assertThat(caught).isInstanceOf(MappingException.class);
  }

  // ============================================
  // TypeNotFoundException specific tests
  // ============================================

  @Nested
  @DisplayName("TypeNotFoundException")
  class TypeNotFoundExceptionTests {

    @Test
    @DisplayName("should store type name")
    void shouldStoreTypeName() {
      TypeNotFoundException ex = new TypeNotFoundException("UserEvent", "Type 'UserEvent' not found");

      assertThat(ex.getTypeName()).isEqualTo("UserEvent");
      assertThat(ex.getMessage()).contains("UserEvent");
    }

    @Test
    @DisplayName("should preserve cause")
    void shouldPreserveCause() {
      ClassNotFoundException cause = new ClassNotFoundException("class not found");
      TypeNotFoundException ex = new TypeNotFoundException("Product", "not found", cause);

      assertThat(ex.getCause()).isSameAs(cause);
    }
  }

  // ============================================
  // FieldExtractionException specific tests
  // ============================================

  @Nested
  @DisplayName("FieldExtractionException")
  class FieldExtractionExceptionTests {

    @Test
    @DisplayName("should build descriptive message")
    void shouldBuildDescriptiveMessage() {
      List<String> sources = List.of("visitorId", "visitor_id", "vid");
      FieldExtractionException ex = new FieldExtractionException("visitorId", sources);

      assertThat(ex.getMessage())
          .contains("visitorId")
          .contains("visitor_id")
          .contains("vid");
    }

    @Test
    @DisplayName("should handle empty sources")
    void shouldHandleEmptySources() {
      FieldExtractionException ex = new FieldExtractionException("field", List.of());
      assertThat(ex.getMessage()).contains("no sources configured");
    }

    @Test
    @DisplayName("should handle null sources")
    void shouldHandleNullSources() {
      FieldExtractionException ex = new FieldExtractionException("field", null);

      assertThat(ex.getMessage()).contains("no sources configured");
      assertThat(ex.getTriedSources()).isEmpty();
    }

    @Test
    @DisplayName("should store field name and sources")
    void shouldStoreFieldNameAndSources() {
      List<String> sources = List.of("a", "b");
      FieldExtractionException ex = new FieldExtractionException("visitorId", sources);

      assertThat(ex.getFieldName()).isEqualTo("visitorId");
      assertThat(ex.getTriedSources()).containsExactly("a", "b");
    }
  }

  // ============================================
  // ConfigurationException specific tests
  // ============================================

  @Nested
  @DisplayName("ConfigurationException")
  class ConfigurationExceptionTests {

    @Test
    @DisplayName("should format message with configId")
    void shouldFormatMessageWithConfigId() {
      ConfigurationException ex = new ConfigurationException("search", "missing rootType");

      assertThat(ex.getMessage()).isEqualTo("Config 'search': missing rootType");
      assertThat(ex.getConfigId()).isEqualTo("search");
    }

    @Test
    @DisplayName("should work without configId")
    void shouldWorkWithoutConfigId() {
      ConfigurationException ex = new ConfigurationException("general error");

      assertThat(ex.getMessage()).isEqualTo("general error");
      assertThat(ex.getConfigId()).isNull();
    }

    @Test
    @DisplayName("should preserve cause with configId")
    void shouldPreserveCauseWithConfigId() {
      Exception cause = new RuntimeException("parse error");
      ConfigurationException ex = new ConfigurationException("search", "invalid YAML", cause);

      assertThat(ex.getCause()).isSameAs(cause);
      assertThat(ex.getConfigId()).isEqualTo("search");
    }
  }

}
