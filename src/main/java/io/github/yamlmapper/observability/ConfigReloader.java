package io.github.yamlmapper.observability;

import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.loader.YamlConfigLoader;
import io.github.yamlmapper.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Hot configuration reload support for MappingEngine.
 *
 * <p>Monitors configuration files for changes and notifies listeners
 * when configs need to be reloaded. Supports both manual and automatic
 * file-watching modes.
 *
 * <p>Example usage with manual reload:
 * <pre>{@code
 * ConfigReloader reloader = new ConfigReloader();
 *
 * // Register config files
 * reloader.registerConfig("search", "/etc/app/mapping/search.yaml");
 * reloader.registerConfig("cart", "/etc/app/mapping/cart.yaml");
 *
 * // Add reload listener
 * reloader.addListener(event -> {
 *     if (event.isSuccess()) {
 *         // Rebuild engine with new config
 *         engine = rebuildEngine(event.configId(), event.schema());
 *     } else {
 *         log.error("Reload failed: {}", event.error());
 *     }
 * });
 *
 * // Trigger reload (e.g., from admin endpoint)
 * reloader.reload("search");
 * }</pre>
 *
 * <p>Example usage with file watching:
 * <pre>{@code
 * ConfigReloader reloader = new ConfigReloader();
 * reloader.registerConfig("search", "/etc/app/mapping/search.yaml");
 *
 * // Start watching for file changes
 * reloader.startWatching();
 *
 * // ... configs will be automatically reloaded on file changes ...
 *
 * // Stop watching on shutdown
 * reloader.stopWatching();
 * }</pre>
 *
 * <p>This class is thread-safe.
 */
public class ConfigReloader implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(ConfigReloader.class);

  private final YamlConfigLoader loader;
  private final Map<String, Path> configPaths;
  private final Map<String, Instant> lastModified;
  private final List<Consumer<ReloadEvent>> listeners;
  private final AtomicBoolean watching;
  private final AtomicReference<Thread> watcherThread;

  /**
   * Creates a new ConfigReloader.
   */
  public ConfigReloader() {
    this.loader = new YamlConfigLoader();
    this.configPaths = new ConcurrentHashMap<>();
    this.lastModified = new ConcurrentHashMap<>();
    this.listeners = new CopyOnWriteArrayList<>();
    this.watching = new AtomicBoolean(false);
    this.watcherThread = new AtomicReference<>();
  }

  /**
   * Registers a configuration file for reloading.
   *
   * @param configId the configuration identifier
   * @param filePath the path to the YAML file
   * @return this reloader for chaining
   */
  public ConfigReloader registerConfig(String configId, String filePath) {
    Path path = Paths.get(filePath);
    configPaths.put(configId, path);

    // Track initial modification time
    try {
      if (Files.exists(path)) {
        lastModified.put(configId, Files.getLastModifiedTime(path).toInstant());
      }
    } catch (IOException e) {
      log.warn("Could not get modification time for {}: {}", filePath, e.getMessage());
    }

    log.info("Registered config '{}' at: {}", configId, filePath);
    return this;
  }

  /**
   * Adds a reload listener.
   *
   * @param listener the listener to notify on reload events
   * @return this reloader for chaining
   */
  public ConfigReloader addListener(Consumer<ReloadEvent> listener) {
    listeners.add(listener);
    return this;
  }

  /**
   * Removes a reload listener.
   *
   * @param listener the listener to remove
   * @return this reloader for chaining
   */
  public ConfigReloader removeListener(Consumer<ReloadEvent> listener) {
    listeners.remove(listener);
    return this;
  }

  /**
   * Manually reloads a specific configuration.
   *
   * @param configId the configuration to reload
   * @return the reload result
   */
  public ReloadEvent reload(String configId) {
    Path path = configPaths.get(configId);

    if (path == null) {
      ReloadEvent event = ReloadEvent.failure(configId, null,
          "Config not registered: " + configId);
      notifyListeners(event);
      return event;
    }

    return reloadFromPath(configId, path);
  }

  /**
   * Reloads all registered configurations.
   *
   * @return list of reload events
   */
  public List<ReloadEvent> reloadAll() {
    return configPaths.keySet().stream()
        .map(this::reload)
        .toList();
  }

  /**
   * Starts watching for file changes.
   *
   * <p>Creates a background thread that monitors registered config
   * directories for changes. When a change is detected, the config
   * is automatically reloaded.
   */
  public void startWatching() {
    if (watching.compareAndSet(false, true)) {
      Thread thread = Thread.ofVirtual()
          .name("config-watcher")
          .start(this::watchLoop);
      watcherThread.set(thread);
      log.info("Started config file watching");
    }
  }

  /**
   * Stops watching for file changes.
   */
  public void stopWatching() {
    if (watching.compareAndSet(true, false)) {
      Thread thread = watcherThread.getAndSet(null);
      if (thread != null) {
        thread.interrupt();
      }
      log.info("Stopped config file watching");
    }
  }

  /**
   * Checks if file watching is active.
   *
   * @return true if watching
   */
  public boolean isWatching() {
    return watching.get();
  }

  /**
   * Gets the registered config IDs.
   *
   * @return set of config IDs
   */
  public java.util.Set<String> getRegisteredConfigs() {
    return java.util.Set.copyOf(configPaths.keySet());
  }

  @Override
  public void close() {
    stopWatching();
    configPaths.clear();
    listeners.clear();
  }

  private ReloadEvent reloadFromPath(String configId, Path path) {
    log.debug("Reloading config '{}' from: {}", configId, path);

    try {
      if (!Files.exists(path)) {
        ReloadEvent event = ReloadEvent.failure(configId, path,
            "File not found: " + path);
        notifyListeners(event);
        return event;
      }

      // Load the schema
      MappingSchema schema = loader.load("file:" + path.toAbsolutePath());

      // Update modification time
      lastModified.put(configId, Files.getLastModifiedTime(path).toInstant());

      ReloadEvent event = ReloadEvent.success(configId, path, schema);
      notifyListeners(event);

      log.info("Successfully reloaded config '{}' from: {}", configId, path);
      return event;

    } catch (Exception e) {
      log.error("Failed to reload config '{}': {}", configId, e.getMessage());
      ReloadEvent event = ReloadEvent.failure(configId, path, e.getMessage());
      notifyListeners(event);
      return event;
    }
  }

  private void watchLoop() {
    try {
      // Collect all directories to watch
      Map<Path, WatchKey> watchKeys = new ConcurrentHashMap<>();

      try (WatchService watcher = FileSystems.getDefault().newWatchService()) {

        // Register directories
        for (Map.Entry<String, Path> entry : configPaths.entrySet()) {
          Path dir = entry.getValue().getParent();
          if (dir != null && Files.isDirectory(dir)) {
            try {
              WatchKey key = dir.register(watcher,
                  StandardWatchEventKinds.ENTRY_MODIFY,
                  StandardWatchEventKinds.ENTRY_CREATE);
              watchKeys.put(dir, key);
              log.debug("Watching directory: {}", dir);
            } catch (IOException e) {
              log.warn("Could not watch directory {}: {}", dir, e.getMessage());
            }
          }
        }

        // Watch loop
        while (watching.get()) {
          WatchKey key;
          try {
            key = watcher.poll(1, java.util.concurrent.TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }

          if (key == null) {
            continue;
          }

          for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
              continue;
            }

            @SuppressWarnings("unchecked")
            WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
            Path fileName = pathEvent.context();

            // Find matching config
            for (Map.Entry<String, Path> configEntry : configPaths.entrySet()) {
              if (configEntry.getValue().getFileName().equals(fileName)) {
                // Debounce - check if actually modified
                try {
                  Instant newModified = Files.getLastModifiedTime(
                      configEntry.getValue()).toInstant();
                  Instant oldModified = lastModified.get(configEntry.getKey());

                  if (oldModified == null || newModified.isAfter(oldModified)) {
                    log.info("Detected change in config: {}", configEntry.getKey());
                    reload(configEntry.getKey());
                  }
                } catch (IOException e) {
                  log.warn("Error checking modification time: {}", e.getMessage());
                }
              }
            }
          }

          key.reset();
        }

      }
    } catch (IOException e) {
      log.error("Watch service error: {}", e.getMessage());
    }
  }

  private void notifyListeners(ReloadEvent event) {
    for (Consumer<ReloadEvent> listener : listeners) {
      try {
        listener.accept(event);
      } catch (Exception e) {
        log.error("Reload listener error: {}", e.getMessage());
      }
    }
  }

  /**
   * Event emitted when a configuration is reloaded.
   *
   * @param configId the configuration identifier
   * @param path the file path (may be null on error)
   * @param schema the loaded schema (null on failure)
   * @param error error message (null on success)
   * @param timestamp when the reload occurred
   */
  public record ReloadEvent(
      String configId,
      Path path,
      MappingSchema schema,
      String error,
      Instant timestamp
  ) {

    /**
     * Creates a successful reload event.
     */
    public static ReloadEvent success(String configId, Path path, MappingSchema schema) {
      return new ReloadEvent(configId, path, schema, null, Instant.now());
    }

    /**
     * Creates a failed reload event.
     */
    public static ReloadEvent failure(String configId, Path path, String error) {
      return new ReloadEvent(configId, path, null, error, Instant.now());
    }

    /**
     * Checks if the reload was successful.
     */
    public boolean isSuccess() {
      return error == null && schema != null;
    }

    /**
     * Checks if the reload failed.
     */
    public boolean isFailure() {
      return !isSuccess();
    }
  }
}
