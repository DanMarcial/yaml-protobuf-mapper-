package io.github.yamlmapper.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.UserEvent;
import com.google.cloud.retail.v2.UserInfo;
import com.google.protobuf.Message;
import io.github.yamlmapper.core.MappingEngine;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Performance benchmark comparing YAML-Protobuf-Mapper against alternatives.
 *
 * <p>Compares three approaches:
 * <ul>
 *   <li><b>Direct:</b> Hardcoded mapping using Protobuf builders directly</li>
 *   <li><b>YAML-Mapper:</b> This library with YAML configuration</li>
 *   <li><b>Reflection:</b> Pure reflection-based mapping</li>
 * </ul>
 *
 * <p>Run with:
 * <pre>{@code
 * mvn test-compile exec:java \
 *   -Dexec.mainClass="io.github.yamlmapper.benchmark.PerformanceBenchmark" \
 *   -Dexec.classpathScope=test
 * }</pre>
 */
public class PerformanceBenchmark {

    private static final String PROTOBUF_PACKAGE = "com.google.cloud.retail.v2";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int WARMUP_ITERATIONS = 1_000;
    private static final int BENCHMARK_ITERATIONS = 10_000;

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String DIM = "\u001B[2m";

    private static final DecimalFormat NUMBER_FORMAT;
    private static final DecimalFormat DECIMAL_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        NUMBER_FORMAT = new DecimalFormat("#,###", symbols);
        DECIMAL_FORMAT = new DecimalFormat("#,###.##", symbols);
    }

    private static final String JSON_INPUT = """
            {
              "eventType": "search",
              "visitorId": "visitor-benchmark-001",
              "searchQuery": "gaming laptop",
              "userInfo": {
                "userId": "user-12345",
                "ipAddress": "192.168.1.100"
              },
              "pageCategories": ["Electronics", "Computers", "Laptops"]
            }
            """;

    public static void main(final String[] args) throws Exception {
        printHeader();

        // Pre-parse JSON for fair comparison
        final JsonNode jsonNode = MAPPER.readTree(JSON_INPUT);

        // Initialize YAML-Mapper engine
        final MappingEngine engine = MappingEngine.builder()
                .withProtobufPackage(PROTOBUF_PACKAGE)
                .withConfig("classpath:integration/mapping/user-event-simple.yaml")
                .build();

        // Warmup all approaches
        System.out.println(DIM + "Warming up (" + NUMBER_FORMAT.format(WARMUP_ITERATIONS) + " iterations each)..." + RESET);
        warmup(jsonNode, engine);

        System.out.println(DIM + "Running benchmark (" + NUMBER_FORMAT.format(BENCHMARK_ITERATIONS) + " iterations each)..." + RESET);
        System.out.println();

        // Run benchmarks
        final BenchmarkResult directResult = benchmarkDirect(jsonNode);
        final BenchmarkResult yamlMapperResult = benchmarkYamlMapper(jsonNode, engine);
        final BenchmarkResult reflectionResult = benchmarkReflection(jsonNode);

        // Print results
        printResults(directResult, yamlMapperResult, reflectionResult);
        printConclusion(directResult, yamlMapperResult, reflectionResult);
    }

    private static void warmup(final JsonNode jsonNode, final MappingEngine engine) throws Exception {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runDirect(jsonNode);
            runYamlMapper(jsonNode, engine);
            runReflection(jsonNode);
        }
    }

    // =========================================================================
    // APPROACH 1: Direct Mapping (Hardcoded)
    // =========================================================================

    private static BenchmarkResult benchmarkDirect(final JsonNode jsonNode) {
        final long startTime = System.nanoTime();

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runDirect(jsonNode);
        }

        final long totalTime = System.nanoTime() - startTime;
        return new BenchmarkResult("Direct", totalTime, BENCHMARK_ITERATIONS);
    }

    private static UserEvent runDirect(final JsonNode jsonNode) {
        final UserEvent.Builder builder = UserEvent.newBuilder();

        builder.setEventType(jsonNode.path("eventType").asText());
        builder.setVisitorId(jsonNode.path("visitorId").asText());
        builder.setSearchQuery(jsonNode.path("searchQuery").asText());

        final JsonNode userInfoNode = jsonNode.path("userInfo");
        if (!userInfoNode.isMissingNode()) {
            builder.setUserInfo(UserInfo.newBuilder()
                    .setUserId(userInfoNode.path("userId").asText())
                    .setIpAddress(userInfoNode.path("ipAddress").asText())
                    .build());
        }

        final JsonNode categoriesNode = jsonNode.path("pageCategories");
        if (categoriesNode.isArray()) {
            for (final JsonNode category : categoriesNode) {
                builder.addPageCategories(category.asText());
            }
        }

        return builder.build();
    }

    // =========================================================================
    // APPROACH 2: YAML-Mapper
    // =========================================================================

    private static BenchmarkResult benchmarkYamlMapper(final JsonNode jsonNode, final MappingEngine engine) {
        final long startTime = System.nanoTime();

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runYamlMapper(jsonNode, engine);
        }

        final long totalTime = System.nanoTime() - startTime;
        return new BenchmarkResult("YAML-Mapper", totalTime, BENCHMARK_ITERATIONS);
    }

    private static UserEvent runYamlMapper(final JsonNode jsonNode, final MappingEngine engine) {
        return engine.map(jsonNode, "user-event-simple", UserEvent.class);
    }

    // =========================================================================
    // APPROACH 3: Pure Reflection
    // =========================================================================

    private static BenchmarkResult benchmarkReflection(final JsonNode jsonNode) throws Exception {
        final long startTime = System.nanoTime();

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runReflection(jsonNode);
        }

        final long totalTime = System.nanoTime() - startTime;
        return new BenchmarkResult("Reflection", totalTime, BENCHMARK_ITERATIONS);
    }

    private static UserEvent runReflection(final JsonNode jsonNode) throws Exception {
        final Message.Builder builder = UserEvent.newBuilder();
        final Class<?> builderClass = builder.getClass();

        // Set eventType via reflection
        final Method setEventType = builderClass.getMethod("setEventType", String.class);
        setEventType.invoke(builder, jsonNode.path("eventType").asText());

        // Set visitorId via reflection
        final Method setVisitorId = builderClass.getMethod("setVisitorId", String.class);
        setVisitorId.invoke(builder, jsonNode.path("visitorId").asText());

        // Set searchQuery via reflection
        final Method setSearchQuery = builderClass.getMethod("setSearchQuery", String.class);
        setSearchQuery.invoke(builder, jsonNode.path("searchQuery").asText());

        // Set userInfo via reflection
        final JsonNode userInfoNode = jsonNode.path("userInfo");
        if (!userInfoNode.isMissingNode()) {
            final Message.Builder userInfoBuilder = UserInfo.newBuilder();
            final Class<?> userInfoBuilderClass = userInfoBuilder.getClass();

            final Method setUserId = userInfoBuilderClass.getMethod("setUserId", String.class);
            setUserId.invoke(userInfoBuilder, userInfoNode.path("userId").asText());

            final Method setIpAddress = userInfoBuilderClass.getMethod("setIpAddress", String.class);
            setIpAddress.invoke(userInfoBuilder, userInfoNode.path("ipAddress").asText());

            final Method buildUserInfo = userInfoBuilderClass.getMethod("build");
            final Object userInfo = buildUserInfo.invoke(userInfoBuilder);

            final Method setUserInfo = builderClass.getMethod("setUserInfo", UserInfo.class);
            setUserInfo.invoke(builder, userInfo);
        }

        // Set pageCategories via reflection
        final JsonNode categoriesNode = jsonNode.path("pageCategories");
        if (categoriesNode.isArray()) {
            final Method addPageCategories = builderClass.getMethod("addPageCategories", String.class);
            for (final JsonNode category : categoriesNode) {
                addPageCategories.invoke(builder, category.asText());
            }
        }

        final Method build = builderClass.getMethod("build");
        return (UserEvent) build.invoke(builder);
    }

    // =========================================================================
    // Results and Formatting
    // =========================================================================

    private static void printHeader() {
        System.out.println();
        System.out.println(BOLD + "========================================================================" + RESET);
        System.out.println(BOLD + "         YAML-PROTOBUF-MAPPER PERFORMANCE BENCHMARK" + RESET);
        System.out.println(BOLD + "========================================================================" + RESET);
        System.out.println();
    }

    private static void printResults(final BenchmarkResult direct,
                                     final BenchmarkResult yamlMapper,
                                     final BenchmarkResult reflection) {
        System.out.println(BOLD + "Results:" + RESET);
        System.out.println();

        // Table header
        System.out.println(BOLD + String.format("%-15s %15s %18s %12s",
                "Approach", "Avg (ns/op)", "Throughput (ops/s)", "vs Direct") + RESET);
        System.out.println("─".repeat(62));

        // Direct
        printResultRow(direct, direct.avgNanos);

        // YAML-Mapper
        printResultRow(yamlMapper, direct.avgNanos);

        // Reflection
        printResultRow(reflection, direct.avgNanos);

        System.out.println("─".repeat(62));
        System.out.println();
    }

    private static void printResultRow(final BenchmarkResult result, final double baselineNanos) {
        final double ratio = result.avgNanos / baselineNanos;
        final String ratioStr = String.format("%.1fx", ratio);

        String color = GREEN;
        if (ratio > 5.0) {
            color = YELLOW;
        }

        System.out.println(String.format("%-15s %15s %18s %12s",
                result.name,
                NUMBER_FORMAT.format((long) result.avgNanos),
                NUMBER_FORMAT.format((long) result.throughput),
                color + ratioStr + RESET));
    }

    private static void printConclusion(final BenchmarkResult direct,
                                        final BenchmarkResult yamlMapper,
                                        final BenchmarkResult reflection) {
        final double yamlVsDirect = yamlMapper.avgNanos / direct.avgNanos;
        final double reflectionVsDirect = reflection.avgNanos / direct.avgNanos;

        System.out.println(BOLD + "Conclusion:" + RESET);
        System.out.println();

        // Compare all approaches to Direct
        System.out.println(CYAN + "  Relative to Direct (baseline):" + RESET);
        System.out.println(String.format("    - YAML-Mapper:  %s%.1fx%s overhead",
                YELLOW, yamlVsDirect, RESET));
        System.out.println(String.format("    - Reflection:   %s%.1fx%s overhead",
                YELLOW, reflectionVsDirect, RESET));

        System.out.println();

        // Compare YAML-Mapper to Reflection
        if (yamlMapper.avgNanos < reflection.avgNanos) {
            final double speedup = reflection.avgNanos / yamlMapper.avgNanos;
            System.out.println(GREEN + "  YAML-Mapper is " + BOLD + String.format("%.1fx FASTER", speedup)
                    + RESET + GREEN + " than pure Reflection" + RESET);
        } else {
            final double slowdown = yamlMapper.avgNanos / reflection.avgNanos;
            System.out.println(YELLOW + "  YAML-Mapper is " + BOLD + String.format("%.1fx slower", slowdown)
                    + RESET + YELLOW + " than pure Reflection" + RESET);
            System.out.println(DIM + "  (Reflection test uses pre-resolved method lookups)" + RESET);
        }

        System.out.println();
        System.out.println(DIM + "  Note: This benchmark compares simple field mapping." + RESET);
        System.out.println(DIM + "  YAML-Mapper provides additional features:" + RESET);
        System.out.println(DIM + "    - Fallback source paths" + RESET);
        System.out.println(DIM + "    - Data transformations" + RESET);
        System.out.println(DIM + "    - Nested object/array handling" + RESET);
        System.out.println(DIM + "    - Configuration-driven (no code changes needed)" + RESET);
        System.out.println();

        // Trade-off summary
        System.out.println(BOLD + "Trade-off:" + RESET);
        System.out.println(GREEN + "  Configuration-driven flexibility with " + BOLD
                + String.format("%.1fx", yamlVsDirect)
                + RESET + GREEN + " overhead vs hardcoded mapping" + RESET);
        System.out.println();
    }

    private static final class BenchmarkResult {
        final String name;
        final long totalNanos;
        final int iterations;
        final double avgNanos;
        final double throughput;

        BenchmarkResult(final String name, final long totalNanos, final int iterations) {
            this.name = name;
            this.totalNanos = totalNanos;
            this.iterations = iterations;
            this.avgNanos = (double) totalNanos / iterations;
            this.throughput = 1_000_000_000.0 / this.avgNanos;
        }
    }
}
