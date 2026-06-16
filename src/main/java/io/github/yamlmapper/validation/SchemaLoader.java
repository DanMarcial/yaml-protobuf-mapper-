package io.github.yamlmapper.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Utility class for loading validation schemas from various sources.
 *
 * <p>This class provides flexible schema loading for scenarios where the default
 * classpath-based loading is insufficient, such as:
 * <ul>
 *   <li>Loading updated schemas when Google API structures change</li>
 *   <li>Loading schemas from external configuration servers</li>
 *   <li>Loading schemas from cloud storage (GCS, S3, etc.)</li>
 *   <li>Loading schemas dynamically at runtime</li>
 * </ul>
 *
 * <p>Example - loading a schema from a URL when Google Retail API changes:
 * <pre>{@code
 * SchemaLoader loader = new SchemaLoader();
 *
 * // Load updated schema from cloud storage
 * ProtobufConstraints updatedConstraints = loader.fromUrl(
 *     "https://storage.googleapis.com/my-schemas/user-event-v2.schema.json");
 *
 * // Use with MappingEngine
 * MappingEngine engine = MappingEngine.builder()
 *     .withProtobufPackage("com.google.cloud.retail.v2")
 *     .withValidationSchema("UserEvent", updatedConstraints)
 *     .enablePostMappingValidation(true)
 *     .build();
 * }</pre>
 *
 * <p>This class is thread-safe.
 */
public class SchemaLoader {

    private static final Logger log = LoggerFactory.getLogger(SchemaLoader.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration timeout;

    /**
     * Creates a SchemaLoader with default settings.
     */
    public SchemaLoader() {
        this(new ObjectMapper(), Duration.ofSeconds(30));
    }

    /**
     * Creates a SchemaLoader with custom ObjectMapper and timeout.
     *
     * @param objectMapper the ObjectMapper for JSON parsing
     * @param timeout the HTTP request timeout
     */
    public SchemaLoader(final ObjectMapper objectMapper, final Duration timeout) {
        this.objectMapper = objectMapper;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Loads constraints from a classpath resource.
     *
     * @param classpathPath the path within the classpath (e.g., "schemas/user-event.schema.json")
     * @return the parsed constraints
     * @throws IOException if the resource cannot be read
     */
    public ProtobufConstraints fromClasspath(final String classpathPath) throws IOException {
        log.debug("Loading schema from classpath: {}", classpathPath);
        return ProtobufConstraints.fromClasspath(classpathPath);
    }

    /**
     * Loads constraints from a filesystem path.
     *
     * @param filePath the path to the schema file
     * @return the parsed constraints
     * @throws IOException if the file cannot be read
     */
    public ProtobufConstraints fromFile(final Path filePath) throws IOException {
        log.debug("Loading schema from file: {}", filePath);
        return ProtobufConstraints.fromPath(filePath);
    }

    /**
     * Loads constraints from a URL (HTTP/HTTPS).
     *
     * <p>This method is useful for loading schemas from:
     * <ul>
     *   <li>Cloud storage (GCS, S3, Azure Blob)</li>
     *   <li>Configuration servers</li>
     *   <li>GitHub raw URLs</li>
     *   <li>Any HTTP endpoint</li>
     * </ul>
     *
     * <p>Example:
     * <pre>{@code
     * // From Google Cloud Storage
     * loader.fromUrl("https://storage.googleapis.com/my-bucket/schemas/user-event.json");
     *
     * // From GitHub
     * loader.fromUrl("https://raw.githubusercontent.com/my-org/schemas/main/user-event.json");
     * }</pre>
     *
     * @param url the URL to fetch the schema from
     * @return the parsed constraints
     * @throws IOException if the URL cannot be fetched or parsed
     */
    public ProtobufConstraints fromUrl(final String url) throws IOException {
        log.info("Loading schema from URL: {}", sanitizeUrlForLogging(url));

        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException(String.format(
                        "Failed to fetch schema from %s: HTTP %d", sanitizeUrlForLogging(url), response.statusCode()));
            }

            final String body = response.body();
            log.debug("Fetched {} bytes from {}", body.length(), sanitizeUrlForLogging(url));

            return fromString(body);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching schema from: " + sanitizeUrlForLogging(url), e);
        }
    }

    /**
     * Loads constraints from a JSON string.
     *
     * <p>This method is useful for:
     * <ul>
     *   <li>Testing with inline schemas</li>
     *   <li>Loading schemas from databases</li>
     *   <li>Loading schemas from environment variables</li>
     *   <li>Loading schemas from custom sources</li>
     * </ul>
     *
     * <p>Example:
     * <pre>{@code
     * String schemaJson = """
     *     {
     *       "title": "UserEvent",
     *       "required": ["visitorId", "eventType"],
     *       "properties": {
     *         "visitorId": { "maxLength": 128 }
     *       }
     *     }
     *     """;
     * ProtobufConstraints constraints = loader.fromString(schemaJson);
     * }</pre>
     *
     * @param jsonContent the JSON schema content
     * @return the parsed constraints
     * @throws IOException if the JSON cannot be parsed
     */
    public ProtobufConstraints fromString(final String jsonContent) throws IOException {
        log.debug("Parsing schema from string ({} chars)", jsonContent.length());

        try (InputStream is = new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8))) {
            return ProtobufConstraints.fromInputStream(is);
        }
    }

    /**
     * Loads constraints from a parsed JsonNode.
     *
     * @param jsonNode the root JSON node of the schema
     * @return the parsed constraints
     */
    public ProtobufConstraints fromJsonNode(final JsonNode jsonNode) {
        log.debug("Loading schema from JsonNode");
        return ProtobufConstraints.fromJsonNode(jsonNode);
    }

    /**
     * Loads constraints from an InputStream.
     *
     * @param inputStream the input stream containing the JSON schema
     * @return the parsed constraints
     * @throws IOException if the stream cannot be read
     */
    public ProtobufConstraints fromInputStream(final InputStream inputStream) throws IOException {
        log.debug("Loading schema from InputStream");
        return ProtobufConstraints.fromInputStream(inputStream);
    }

    /**
     * Attempts to load a schema from multiple sources in order.
     *
     * <p>This method tries each source in sequence, returning the first successful load.
     * Useful for implementing fallback strategies.
     *
     * <p>Example:
     * <pre>{@code
     * ProtobufConstraints constraints = loader.fromFirstAvailable(
     *     () -> loader.fromUrl("https://config-server/schemas/user-event.json"),
     *     () -> loader.fromFile(Paths.get("/local-cache/user-event.json")),
     *     () -> loader.fromClasspath("schemas/user-event.schema.json")
     * );
     * }</pre>
     *
     * @param sources the schema sources to try in order
     * @return the first successfully loaded constraints
     * @throws IOException if all sources fail
     */
    @SafeVarargs
    public final ProtobufConstraints fromFirstAvailable(
            final SchemaSource... sources) throws IOException {

        IOException lastException = null;

        for (int i = 0; i < sources.length; i++) {
            try {
                final ProtobufConstraints result = sources[i].load();
                log.info("Successfully loaded schema from source #{}", i + 1);
                return result;
            } catch (IOException e) {
                log.debug("Source #{} failed: {}", i + 1, e.getMessage());
                lastException = e;
            }
        }

        throw new IOException("All schema sources failed", lastException);
    }

    /**
     * Sanitizes a URL for safe logging by removing sensitive information.
     *
     * <p>This method removes:
     * <ul>
     *   <li>Query parameters (may contain tokens, API keys)</li>
     *   <li>User info (username:password in URL)</li>
     *   <li>Fragment identifiers</li>
     * </ul>
     *
     * @param url the URL to sanitize
     * @return the sanitized URL safe for logging
     */
    private String sanitizeUrlForLogging(final String url) {
        if (url == null || url.isBlank()) {
            return "[empty]";
        }

        try {
            final URI uri = URI.create(url);
            final StringBuilder sanitized = new StringBuilder();

            // Add scheme
            if (uri.getScheme() != null) {
                sanitized.append(uri.getScheme()).append("://");
            }

            // Add host (without user info)
            if (uri.getHost() != null) {
                sanitized.append(uri.getHost());
                if (uri.getPort() != -1) {
                    sanitized.append(":").append(uri.getPort());
                }
            }

            // Add path only (no query params or fragment)
            if (uri.getPath() != null) {
                sanitized.append(uri.getPath());
            }

            // Indicate if query params were removed
            if (uri.getQuery() != null) {
                sanitized.append("?[REDACTED]");
            }

            return sanitized.toString();
        } catch (IllegalArgumentException e) {
            // If URL parsing fails, just indicate it's invalid
            return "[invalid-url]";
        }
    }

    /**
     * Functional interface for schema loading strategies.
     */
    @FunctionalInterface
    public interface SchemaSource {
        /**
         * Loads a schema from this source.
         *
         * @return the loaded constraints
         * @throws IOException if loading fails
         */
        ProtobufConstraints load() throws IOException;
    }
}
