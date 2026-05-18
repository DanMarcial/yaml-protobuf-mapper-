package io.github.yamlmapper.exception;

/**
 * Thrown when a YAML configuration is invalid or cannot be loaded.
 *
 * <p>This covers various configuration errors including:
 * <ul>
 *   <li>YAML syntax errors</li>
 *   <li>Missing required fields (rootType)</li>
 *   <li>Invalid field configurations</li>
 *   <li>Circular inheritance detected</li>
 *   <li>Referenced config file not found</li>
 * </ul>
 *
 * <p>Example scenarios:
 * <pre>{@code
 * // Missing rootType:
 * ConfigurationException: Config 'search.yaml' missing required 'rootType'
 *
 * // Circular inheritance:
 * ConfigurationException: Circular inheritance detected: a.yaml -> b.yaml -> a.yaml
 *
 * // File not found:
 * ConfigurationException: Config file not found: classpath:mapping/unknown.yaml
 * }</pre>
 */
public class ConfigurationException extends MappingException {

  private final String configId;

  /**
   * Creates a new ConfigurationException.
   *
   * @param message the error message
   */
  public ConfigurationException(String message) {
    super(message);
    this.configId = null;
  }

  /**
   * Creates a new ConfigurationException for a specific config.
   *
   * @param configId the config identifier (e.g., "search")
   * @param message the error message
   */
  public ConfigurationException(String configId, String message) {
    super(String.format("Config '%s': %s", configId, message));
    this.configId = configId;
  }

  /**
   * Creates a new ConfigurationException with a cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public ConfigurationException(String message, Throwable cause) {
    super(message, cause);
    this.configId = null;
  }

  /**
   * Creates a new ConfigurationException for a specific config with a cause.
   *
   * @param configId the config identifier
   * @param message the error message
   * @param cause the underlying cause
   */
  public ConfigurationException(String configId, String message, Throwable cause) {
    super(String.format("Config '%s': %s", configId, message), cause);
    this.configId = configId;
  }

  /**
   * Gets the config identifier if available.
   *
   * @return the config ID, or null if not specific to a config
   */
  public String getConfigId() {
    return configId;
  }
}
