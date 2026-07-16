package io.github.yamlmapper.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.UserEvent;
import com.google.cloud.retail.v2.UserInfo;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.github.yamlmapper.benchmark.dto.UserEventDto;
import io.github.yamlmapper.benchmark.mapper.UserEventMapStructMapper;
import io.github.yamlmapper.core.MappingEngine;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fair performance benchmark comparing YAML-Protobuf-Mapper against alternatives.
 *
 * <p>All approaches map the SAME fields with NO transforms - pure mapping overhead.
 *
 * <p>Compares:
 * <ul>
 *   <li><b>Direct:</b> Hardcoded mapping using Protobuf builders directly</li>
 *   <li><b>YAML-Mapper:</b> This library with YAML configuration</li>
 *   <li><b>MapStruct (DTO→Proto):</b> Compile-time generated mapper (DTO to Protobuf only)</li>
 *   <li><b>MapStruct (Full):</b> Jackson JSON→DTO + MapStruct DTO→Proto</li>
 *   <li><b>JsonFormat:</b> Protobuf's built-in JSON parser</li>
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

    private static final int WARMUP_ITERATIONS = 100_000;
    private static final int BENCHMARK_ITERATIONS = 100_000;

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String DIM = "\u001B[2m";

    private static final DecimalFormat NUMBER_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        NUMBER_FORMAT = new DecimalFormat("#,###", symbols);
    }

    // JSON that matches Protobuf field names exactly - fair comparison
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

        // Pre-create DTO for MapStruct DTO-only benchmark
        final UserEventDto dto = createDto();

        // Initialize YAML-Mapper engine with MINIMAL config (same fields as benchmark)
        final MappingEngine engine = MappingEngine.builder()
                .withProtobufPackage(PROTOBUF_PACKAGE)
                .withConfig("classpath:benchmark/user-event-minimal.yaml")
                .build();

        // Warmup all approaches
        System.out.println(DIM + "Warming up (" + NUMBER_FORMAT.format(WARMUP_ITERATIONS) + " iterations each)..." + RESET);
        warmup(jsonNode, dto, engine);

        System.out.println(DIM + "Running benchmark (" + NUMBER_FORMAT.format(BENCHMARK_ITERATIONS) + " iterations each)..." + RESET);
        System.out.println();

        // Run benchmarks - order matters for display
        Map<String, BenchmarkResult> results = new LinkedHashMap<>();

        results.put("Direct (manual)", benchmarkDirect(jsonNode));
        results.put("YAML-Mapper", benchmarkYamlMapper(jsonNode, engine));
        results.put("MapStruct (DTO→Proto)", benchmarkMapStructDtoOnly(dto));
        results.put("MapStruct (Full)", benchmarkMapStructFull(jsonNode));
        results.put("JsonFormat", benchmarkJsonFormat(JSON_INPUT));
        results.put("Reflection", benchmarkReflection(jsonNode));

        // Print results
        printResults(results);
        printConclusion(results);
    }

    private static void warmup(final JsonNode jsonNode, final UserEventDto dto, final MappingEngine engine) throws Exception {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runDirect(jsonNode);
            runYamlMapper(jsonNode, engine);
            runMapStructDtoOnly(dto);
            runMapStructFull(jsonNode);
            runJsonFormat(JSON_INPUT);
            runReflection(jsonNode);
        }
    }

    private static UserEventDto createDto() {
        UserEventDto dto = new UserEventDto();
        dto.setEventType("search");
        dto.setVisitorId("visitor-benchmark-001");
        dto.setSearchQuery("gaming laptop");

        UserEventDto.UserInfoDto userInfo = new UserEventDto.UserInfoDto();
        userInfo.setUserId("user-12345");
        userInfo.setIpAddress("192.168.1.100");
        dto.setUserInfo(userInfo);

        dto.setPageCategories(java.util.List.of("Electronics", "Computers", "Laptops"));
        return dto;
    }

    // =========================================================================
    // APPROACH 1: Direct Mapping (Hardcoded) - Baseline
    // =========================================================================

    private static BenchmarkResult benchmarkDirect(final JsonNode jsonNode) {
        final long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runDirect(jsonNode);
        }
        return new BenchmarkResult("Direct", System.nanoTime() - startTime, BENCHMARK_ITERATIONS);
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
        return new BenchmarkResult("YAML-Mapper", System.nanoTime() - startTime, BENCHMARK_ITERATIONS);
    }

    private static UserEvent runYamlMapper(final JsonNode jsonNode, final MappingEngine engine) {
        return engine.map(jsonNode, "user-event-minimal", UserEvent.class);
    }

    // =========================================================================
    // APPROACH 3: MapStruct (DTO to Protobuf only)
    // =========================================================================

    private static BenchmarkResult benchmarkMapStructDtoOnly(final UserEventDto dto) {
        final long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runMapStructDtoOnly(dto);
        }
        return new BenchmarkResult("MapStruct (DTO)", System.nanoTime() - startTime, BENCHMARK_ITERATIONS);
    }

    private static UserEvent runMapStructDtoOnly(final UserEventDto dto) {
        return UserEventMapStructMapper.INSTANCE.toProtobuf(dto);
    }

    // =========================================================================
    // APPROACH 4: MapStruct (Full pipeline: JSON → DTO → Proto)
    // =========================================================================

    private static BenchmarkResult benchmarkMapStructFull(final JsonNode jsonNode) throws Exception {
        final long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runMapStructFull(jsonNode);
        }
        return new BenchmarkResult("MapStruct (Full)", System.nanoTime() - startTime, BENCHMARK_ITERATIONS);
    }

    private static UserEvent runMapStructFull(final JsonNode jsonNode) throws Exception {
        // Step 1: Jackson parses JSON to DTO
        UserEventDto dto = MAPPER.treeToValue(jsonNode, UserEventDto.class);
        // Step 2: MapStruct converts DTO to Protobuf
        return UserEventMapStructMapper.INSTANCE.toProtobuf(dto);
    }

    // =========================================================================
    // APPROACH 5: Protobuf JsonFormat
    // =========================================================================

    private static BenchmarkResult benchmarkJsonFormat(final String json) throws Exception {
        final long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runJsonFormat(json);
        }
        return new BenchmarkResult("JsonFormat", System.nanoTime() - startTime, BENCHMARK_ITERATIONS);
    }

    private static UserEvent runJsonFormat(final String json) throws Exception {
        UserEvent.Builder builder = UserEvent.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        return builder.build();
    }

    // =========================================================================
    // APPROACH 6: Pure Reflection
    // =========================================================================

    private static BenchmarkResult benchmarkReflection(final JsonNode jsonNode) throws Exception {
        final long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runReflection(jsonNode);
        }
        return new BenchmarkResult("Reflection", System.nanoTime() - startTime, BENCHMARK_ITERATIONS);
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
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════════════════" + RESET);
        System.out.println(BOLD + "              YAML-PROTOBUF-MAPPER FAIR COMPARISON BENCHMARK" + RESET);
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════════════════" + RESET);
        System.out.println();
        System.out.println(DIM + "All approaches map the SAME fields with NO transforms." + RESET);
        System.out.println(DIM + "This measures pure mapping overhead." + RESET);
        System.out.println();
    }

    private static void printResults(Map<String, BenchmarkResult> results) {
        System.out.println(BOLD + "Results:" + RESET);
        System.out.println();

        // Find baseline (Direct)
        double baselineNanos = results.get("Direct (manual)").avgNanos;

        // Table header
        System.out.println(BOLD + String.format("%-22s %12s %16s %10s",
                "Approach", "Avg (ns)", "Throughput", "vs Direct") + RESET);
        System.out.println("─".repeat(64));

        for (Map.Entry<String, BenchmarkResult> entry : results.entrySet()) {
            BenchmarkResult result = entry.getValue();
            double ratio = result.avgNanos / baselineNanos;
            String ratioStr = String.format("%.2fx", ratio);

            String color = ratio <= 2.0 ? GREEN : (ratio <= 5.0 ? YELLOW : RESET);

            System.out.println(String.format("%-22s %12s %16s %s%10s%s",
                    entry.getKey(),
                    NUMBER_FORMAT.format((long) result.avgNanos),
                    NUMBER_FORMAT.format((long) result.throughput) + "/s",
                    color, ratioStr, RESET));
        }

        System.out.println("─".repeat(64));
        System.out.println();
    }

    private static void printConclusion(Map<String, BenchmarkResult> results) {
        double directNanos = results.get("Direct (manual)").avgNanos;
        double yamlMapperNanos = results.get("YAML-Mapper").avgNanos;
        double mapStructFullNanos = results.get("MapStruct (Full)").avgNanos;
        double jsonFormatNanos = results.get("JsonFormat").avgNanos;
        double reflectionNanos = results.get("Reflection").avgNanos;

        System.out.println(BOLD + "Analysis:" + RESET);
        System.out.println();

        // Compare YAML-Mapper to similar approaches
        System.out.println(CYAN + "  YAML-Mapper comparison:" + RESET);

        if (yamlMapperNanos < mapStructFullNanos) {
            System.out.println(GREEN + "    ✓ " + String.format("%.1fx faster", mapStructFullNanos / yamlMapperNanos)
                    + " than MapStruct (Full pipeline)" + RESET);
        } else {
            System.out.println(YELLOW + "    • " + String.format("%.1fx slower", yamlMapperNanos / mapStructFullNanos)
                    + " than MapStruct (Full pipeline)" + RESET);
        }

        if (yamlMapperNanos < jsonFormatNanos) {
            System.out.println(GREEN + "    ✓ " + String.format("%.1fx faster", jsonFormatNanos / yamlMapperNanos)
                    + " than Protobuf JsonFormat" + RESET);
        } else {
            System.out.println(YELLOW + "    • " + String.format("%.1fx slower", yamlMapperNanos / jsonFormatNanos)
                    + " than Protobuf JsonFormat" + RESET);
        }

        if (yamlMapperNanos < reflectionNanos) {
            System.out.println(GREEN + "    ✓ " + String.format("%.1fx faster", reflectionNanos / yamlMapperNanos)
                    + " than pure Reflection" + RESET);
        } else {
            System.out.println(YELLOW + "    • " + String.format("%.1fx slower", yamlMapperNanos / reflectionNanos)
                    + " than pure Reflection" + RESET);
        }

        System.out.println();
        System.out.println(CYAN + "  Overhead vs hand-written code: " + RESET
                + BOLD + String.format("%.1fx", yamlMapperNanos / directNanos) + RESET);

        System.out.println();
        System.out.println(DIM + "  Note: MapStruct (DTO→Proto) excludes JSON parsing." + RESET);
        System.out.println(DIM + "        MapStruct (Full) includes Jackson JSON→DTO step." + RESET);
        System.out.println();
    }

    private static final class BenchmarkResult {
        final String name;
        final double avgNanos;
        final double throughput;

        BenchmarkResult(final String name, final long totalNanos, final int iterations) {
            this.name = name;
            this.avgNanos = (double) totalNanos / iterations;
            this.throughput = 1_000_000_000.0 / this.avgNanos;
        }
    }
}
