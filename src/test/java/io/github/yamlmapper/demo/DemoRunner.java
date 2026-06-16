package io.github.yamlmapper.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.Product;
import com.google.cloud.retail.v2.UserEvent;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.github.yamlmapper.config.FieldConfig;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.core.MappingEngine;
import io.github.yamlmapper.extractor.EmbeddedJsonParser;
import io.github.yamlmapper.extractor.JsonNodeExtractor;
import io.github.yamlmapper.extractor.PathResolver;
import io.github.yamlmapper.extractor.TransformExecutor;
import io.github.yamlmapper.loader.YamlConfigLoader;
import io.github.yamlmapper.resolver.TypeResolver;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContextImpl;
import io.github.yamlmapper.transform.TransformRegistry;
import io.github.yamlmapper.transform.builtin.BuiltinTransforms;
import io.github.yamlmapper.validation.ProtobufConstraints;
import io.github.yamlmapper.validation.ProtobufMessageValidator;
import io.github.yamlmapper.validation.SchemaValidator;
import io.github.yamlmapper.validation.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/**
 * Interactive demo runner showcasing the YAML-Protobuf-Mapper library.
 *
 * <p>This demo shows:
 * <ul>
 *   <li>Complete mapping scenarios (clean and chaotic JSON structures)</li>
 *   <li>Individual component usage (transforms, extractors, validators)</li>
 *   <li>Different configuration options</li>
 * </ul>
 *
 * <p>Run with: {@code mvn test-compile exec:java -Dexec.mainClass="io.github.yamlmapper.demo.DemoRunner" -Dexec.classpathScope=test}
 * <p>Or run directly from IDE.
 */
public class DemoRunner {

    private static final String PROTOBUF_PACKAGE = "com.google.cloud.retail.v2";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ANSI colors for terminal output
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String RED = "\u001B[31m";
    private static final String DIM = "\u001B[2m";

    private static final Scanner scanner = new Scanner(System.in);

    private static final String[][] SCENARIOS = {
        // Part 1: Complete Mapping Scenarios
        {"1", "Minimal UserEvent", "Only required fields"},
        {"2", "Clean UserEvent", "JSON structure matches Protobuf (1:1 mapping)"},
        {"3", "Chaotic UserEvent", "Legacy JSON with fallbacks and transforms"},
        {"4", "Clean Product", "Product with clean structure"},
        {"5", "Chaotic Product", "Legacy product JSON with transforms"},
        // Part 2: Individual Components
        {"6", "Transforms Alone", "Using TransformRegistry directly"},
        {"7", "Extraction with Fallback", "Using JsonNodeExtractor"},
        {"8", "YAML Schema Validation", "Validating configuration before use"},
        {"9", "Post-Mapping Validation", "Validating Protobuf messages"},
        {"10", "Type Resolution", "Using TypeResolver to find classes"},
    };

    public static void main(String[] args) throws Exception {
        boolean running = true;

        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim().toLowerCase();

            switch (choice) {
                case "1" -> runScenario1_MinimalUserEvent();
                case "2" -> runScenario2_CleanUserEvent();
                case "3" -> runScenario3_ChaoticUserEvent();
                case "4" -> runScenario4_CleanProduct();
                case "5" -> runScenario5_ChaoticProduct();
                case "6" -> runScenario6_TransformsAlone();
                case "7" -> runScenario7_ExtractionWithFallback();
                case "8" -> runScenario8_YamlSchemaValidation();
                case "9" -> runScenario9_PostMappingValidation();
                case "10" -> runScenario10_TypeResolution();
                case "a", "all" -> runAllScenarios();
                case "q", "quit", "exit", "0" -> {
                    running = false;
                    System.out.println("\n" + GREEN + "Goodbye!" + RESET + "\n");
                }
                default -> System.out.println(RED + "\nInvalid option. Please try again." + RESET);
            }

            if (running && !choice.equals("a") && !choice.equals("all") && isValidScenario(choice)) {
                waitForEnter();
            }
        }
    }

    private static boolean isValidScenario(String choice) {
        try {
            int num = Integer.parseInt(choice);
            return num >= 1 && num <= 10;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println(BOLD + "╔══════════════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(BOLD + "║           YAML-PROTOBUF-MAPPER DEMO                                  ║" + RESET);
        System.out.println(BOLD + "╠══════════════════════════════════════════════════════════════════════╣" + RESET);
        System.out.println(BOLD + "║  " + CYAN + "COMPLETE MAPPING SCENARIOS" + RESET + BOLD + "                                         ║" + RESET);
        System.out.println(BOLD + "║                                                                      ║" + RESET);

        for (int i = 0; i < 5; i++) {
            String[] s = SCENARIOS[i];
            System.out.printf(BOLD + "║" + RESET + "   [%2s] %-25s " + DIM + "%-30s" + RESET + BOLD + "║" + RESET + "%n",
                    s[0], s[1], truncateDesc(s[2], 30));
        }

        System.out.println(BOLD + "║                                                                      ║" + RESET);
        System.out.println(BOLD + "║  " + CYAN + "INDIVIDUAL COMPONENT USAGE" + RESET + BOLD + "                                         ║" + RESET);
        System.out.println(BOLD + "║                                                                      ║" + RESET);

        for (int i = 5; i < 10; i++) {
            String[] s = SCENARIOS[i];
            System.out.printf(BOLD + "║" + RESET + "   [%2s] %-25s " + DIM + "%-30s" + RESET + BOLD + "║" + RESET + "%n",
                    s[0], s[1], truncateDesc(s[2], 30));
        }

        System.out.println(BOLD + "║                                                                      ║" + RESET);
        System.out.println(BOLD + "╠══════════════════════════════════════════════════════════════════════╣" + RESET);
        System.out.println(BOLD + "║" + RESET + "   [" + GREEN + "A" + RESET + "]  Run ALL scenarios                                            " + BOLD + "║" + RESET);
        System.out.println(BOLD + "║" + RESET + "   [" + RED + "Q" + RESET + "]  Quit                                                          " + BOLD + "║" + RESET);
        System.out.println(BOLD + "╚══════════════════════════════════════════════════════════════════════╝" + RESET);
        System.out.print("\n" + BOLD + "Select an option: " + RESET);
    }

    private static String truncateDesc(String desc, int maxLen) {
        if (desc.length() <= maxLen) {
            return desc;
        }
        return desc.substring(0, maxLen - 3) + "...";
    }

    private static void waitForEnter() {
        System.out.println("\n" + DIM + "Press ENTER to return to menu..." + RESET);
        scanner.nextLine();
    }

    private static void runAllScenarios() throws Exception {
        clearScreen();
        printHeader();

        printSectionHeader("PART 1: COMPLETE MAPPING SCENARIOS");
        runScenario1_MinimalUserEvent();
        runScenario2_CleanUserEvent();
        runScenario3_ChaoticUserEvent();
        runScenario4_CleanProduct();
        runScenario5_ChaoticProduct();

        printSectionHeader("PART 2: INDIVIDUAL COMPONENT USAGE");
        runScenario6_TransformsAlone();
        runScenario7_ExtractionWithFallback();
        runScenario8_YamlSchemaValidation();
        runScenario9_PostMappingValidation();
        runScenario10_TypeResolution();

        printFooter();
        waitForEnter();
    }

    private static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PART 1: COMPLETE MAPPING SCENARIOS
    // ═══════════════════════════════════════════════════════════════════════════

    private static void runScenario1_MinimalUserEvent() throws Exception {
        printScenarioHeader(1, "Minimal UserEvent", "Only required fields");

        String yaml = loadResource("demo/minimal-user-event.yaml");

        String json = """
            {
              "eventType": "home-page-view",
              "visitorId": "visitor-minimal-001"
            }
            """;

        MappingEngine engine = MappingEngine.builder()
                .withProtobufPackage(PROTOBUF_PACKAGE)
                .withConfig("classpath:demo/minimal-user-event.yaml")
                .build();

        UserEvent event = engine.map(json, "minimal-user-event", UserEvent.class);

        printYaml(yaml);
        printJson(json);
        printProtobuf(event);
        printSuccess();
    }

    private static void runScenario2_CleanUserEvent() throws Exception {
        printScenarioHeader(2, "Clean UserEvent", "JSON structure matches Protobuf (1:1 mapping)");

        String yaml = loadResource("integration/mapping/user-event-simple.yaml");
        String json = loadResource("integration/json/user-event-clean.json");

        MappingEngine engine = MappingEngine.builder()
                .withProtobufPackage(PROTOBUF_PACKAGE)
                .withConfig("classpath:integration/mapping/user-event-simple.yaml")
                .build();

        UserEvent event = engine.map(json, "user-event-simple", UserEvent.class);

        printYaml(yaml);
        printJson(json);
        printProtobuf(event);
        printSuccess();
    }

    private static void runScenario3_ChaoticUserEvent() throws Exception {
        printScenarioHeader(3, "Chaotic UserEvent",
                "Legacy JSON with different field names, nested differently, CSV strings");

        String yaml = loadResource("integration/mapping/user-event-complex.yaml");
        String json = loadResource("integration/json/user-event-chaotic.json");

        printYaml(yaml);
        printJson(json);

        printNote("Key transformations:\n" +
                "  - user_visitor_id → visitorId (fallback)\n" +
                "  - timestamp (unix_millis) → eventTime (Timestamp)\n" +
                "  - ab_tests CSV → experimentIds array (splitToArray)\n" +
                "  - customer.is_direct \"yes\" → userInfo.directUserRequest true\n" +
                "  - items[].category_path → categories array (splitToArray \" > \")");

        MappingEngine engine = MappingEngine.builder()
                .withProtobufPackage(PROTOBUF_PACKAGE)
                .withConfig("classpath:integration/mapping/user-event-complex.yaml")
                .build();

        UserEvent event = engine.map(json, "user-event-complex", UserEvent.class);

        printProtobuf(event);
        printSuccess();
    }

    private static void runScenario4_CleanProduct() throws Exception {
        printScenarioHeader(4, "Clean Product", "Product with clean structure");

        String yaml = loadResource("integration/mapping/product-simple.yaml");
        String json = loadResource("integration/json/product-clean.json");

        MappingEngine engine = MappingEngine.builder()
                .withProtobufPackage(PROTOBUF_PACKAGE)
                .withConfig("classpath:integration/mapping/product-simple.yaml")
                .injectEventType(false)  // Product doesn't have eventType field
                .build();

        Product product = engine.map(json, "product-simple", Product.class);

        printYaml(yaml);
        printJson(json);
        printProtobuf(product);
        printSuccess();
    }

    private static void runScenario5_ChaoticProduct() throws Exception {
        printScenarioHeader(5, "Chaotic Product",
                "Legacy product JSON with transforms");

        String yaml = loadResource("integration/mapping/product-complex.yaml");
        String json = loadResource("integration/json/product-chaotic.json");

        printYaml(yaml);
        printJson(json);

        printNote("Key transformations:\n" +
                "  - sku → id (fallback)\n" +
                "  - brand_name → brands array (singleItemToArray)\n" +
                "  - category_breadcrumb → categories (splitToArray \" / \")\n" +
                "  - stock_status \"available\" → availability IN_STOCK (mapValue)\n" +
                "  - image_gallery strings → images Image[] (stringsToImages)\n" +
                "  - locale \"en_US\" → languageCode \"en-US\" (replaceChars)");

        MappingEngine engine = MappingEngine.builder()
                .withProtobufPackage(PROTOBUF_PACKAGE)
                .withConfig("classpath:integration/mapping/product-complex.yaml")
                .injectEventType(false)  // Product doesn't have eventType field
                .build();

        Product product = engine.map(json, "product-complex", Product.class);

        printProtobuf(product);
        printSuccess();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PART 2: INDIVIDUAL COMPONENT USAGE
    // ═══════════════════════════════════════════════════════════════════════════

    private static void runScenario6_TransformsAlone() throws Exception {
        printScenarioHeader(6, "Transforms Alone",
                "Using TransformRegistry directly without MappingEngine");

        TransformRegistry registry = BuiltinTransforms.createRegistry();

        System.out.println(box("Available transforms: splitToArray, mapValue, singleItemToArray,\n" +
                "trim, lowercase, uppercase, truncate, filterBlank, replaceChars,\n" +
                "stringsToImages, zipArrays"));

        // Example 1: splitToArray
        System.out.println("\n" + CYAN + "▸ Transform: splitToArray" + RESET);
        String splitYaml = """
            # YAML equivalent:
            categories:
              type: array
              itemType: string
              source: [category_csv]
              transform: splitToArray
              transformParams:
                delimiter: ","
            """;
        printYamlCompact(splitYaml);

        Transform splitTransform = registry.get("splitToArray");
        JsonNode csvInput = MAPPER.readTree("\"electronics,computers,laptops\"");
        FieldConfig splitConfig = FieldConfig.builder("categories")
                .transformParams(Map.of("delimiter", ","))
                .build();
        TransformContextImpl splitCtx = TransformContextImpl.builder()
                .fieldName("categories")
                .fieldConfig(splitConfig)
                .objectMapper(MAPPER)
                .build();

        JsonNode splitResult = splitTransform.apply(csvInput, splitCtx);
        System.out.println("\n  Input:  " + YELLOW + csvInput + RESET);
        System.out.println("  Output: " + GREEN + splitResult + RESET);

        // Example 2: mapValue
        System.out.println("\n" + CYAN + "▸ Transform: mapValue" + RESET);
        String mapYaml = """
            # YAML equivalent:
            availability:
              type: enum
              enumType: Product.Availability
              source: [stock_status]
              transform: mapValue
              transformParams:
                mapping:
                  available: "IN_STOCK"
                  out_of_stock: "OUT_OF_STOCK"
                default: "IN_STOCK"
            """;
        printYamlCompact(mapYaml);

        Transform mapTransform = registry.get("mapValue");
        JsonNode statusInput = MAPPER.readTree("\"available\"");
        FieldConfig mapConfig = FieldConfig.builder("availability")
                .transformParams(Map.of(
                        "mapping", Map.of(
                                "available", "IN_STOCK",
                                "out_of_stock", "OUT_OF_STOCK"
                        ),
                        "default", "IN_STOCK"
                ))
                .build();
        TransformContextImpl mapCtx = TransformContextImpl.builder()
                .fieldName("availability")
                .fieldConfig(mapConfig)
                .objectMapper(MAPPER)
                .build();

        JsonNode mapResult = mapTransform.apply(statusInput, mapCtx);
        System.out.println("\n  Input:  " + YELLOW + statusInput + RESET);
        System.out.println("  Output: " + GREEN + mapResult + RESET);

        // Example 3: truncate
        System.out.println("\n" + CYAN + "▸ Transform: truncate" + RESET);
        String truncateYaml = """
            # YAML equivalent:
            title:
              type: string
              source: [description]
              transform: truncate
              transformParams:
                maxLength: 30
            """;
        printYamlCompact(truncateYaml);

        Transform truncateTransform = registry.get("truncate");
        JsonNode longText = MAPPER.readTree("\"This is a very long product description that exceeds the limit\"");
        FieldConfig truncateConfig = FieldConfig.builder("title")
                .transformParams(Map.of("maxLength", 30))
                .build();
        TransformContextImpl truncateCtx = TransformContextImpl.builder()
                .fieldName("title")
                .fieldConfig(truncateConfig)
                .objectMapper(MAPPER)
                .build();

        JsonNode truncateResult = truncateTransform.apply(longText, truncateCtx);
        System.out.println("\n  Input:  " + YELLOW + longText + RESET);
        System.out.println("  Output: " + GREEN + truncateResult + RESET);

        // Example 4: replaceChars
        System.out.println("\n" + CYAN + "▸ Transform: replaceChars" + RESET);
        String replaceYaml = """
            # YAML equivalent:
            languageCode:
              type: string
              source: [locale]
              transform: replaceChars
              transformParams:
                from: "_"
                to: "-"
            """;
        printYamlCompact(replaceYaml);

        Transform replaceTransform = registry.get("replaceChars");
        JsonNode localeInput = MAPPER.readTree("\"en_US\"");
        FieldConfig replaceConfig = FieldConfig.builder("languageCode")
                .transformParams(Map.of("from", "_", "to", "-"))
                .build();
        TransformContextImpl replaceCtx = TransformContextImpl.builder()
                .fieldName("languageCode")
                .fieldConfig(replaceConfig)
                .objectMapper(MAPPER)
                .build();

        JsonNode replaceResult = replaceTransform.apply(localeInput, replaceCtx);
        System.out.println("\n  Input:  " + YELLOW + localeInput + RESET);
        System.out.println("  Output: " + GREEN + replaceResult + RESET);

        printSuccess();
    }

    private static void runScenario7_ExtractionWithFallback() throws Exception {
        printScenarioHeader(7, "Extraction with Fallback",
                "Using JsonNodeExtractor to find values in multiple paths");

        // Show YAML equivalent first
        String yamlEquivalent = """
            # YAML equivalent - fallback sources try paths in order:
            visitorId:
              type: string
              source: [visitorId, visitor_id, legacy_visitor_id, customer.user_id]
              required: true
            """;

        System.out.println(CYAN + "▸ YAML Configuration (equivalent):" + RESET);
        printYamlCompact(yamlEquivalent);

        String json = """
            {
              "legacy_visitor_id": "VIS-123",
              "customer": {
                "user_id": "USR-456"
              }
            }
            """;

        System.out.println("\n" + CYAN + "▸ Input JSON:" + RESET);
        printBoxedContent(json.trim(), 50);

        JsonNode root = MAPPER.readTree(json);

        PathResolver pathResolver = new PathResolver();
        EmbeddedJsonParser jsonParser = new EmbeddedJsonParser(MAPPER);
        TransformRegistry registry = BuiltinTransforms.createRegistry();
        TransformExecutor transformExecutor = new TransformExecutor(registry, MAPPER);
        JsonNodeExtractor extractor = new JsonNodeExtractor(pathResolver, jsonParser, transformExecutor);

        // Test fallback extraction
        FieldConfig config = FieldConfig.builder("visitorId")
                .type("string")
                .source(List.of("visitorId", "visitor_id", "legacy_visitor_id", "customer.user_id"))
                .build();

        System.out.println("\n" + CYAN + "▸ Extraction process:" + RESET);
        System.out.println("  Trying paths in order:");
        System.out.println("    1. visitorId          → " + RED + "not found" + RESET);
        System.out.println("    2. visitor_id         → " + RED + "not found" + RESET);
        System.out.println("    3. legacy_visitor_id  → " + GREEN + "FOUND!" + RESET);

        Optional<JsonNode> result = extractor.extract(root, config);
        System.out.println("\n  Result: " + GREEN + result.orElse(null) + RESET);

        // Test nested path extraction
        System.out.println("\n" + CYAN + "▸ Nested path extraction:" + RESET);
        System.out.println("  Path: customer.user_id");

        FieldConfig nestedConfig = FieldConfig.builder("userId")
                .type("string")
                .source(List.of("customer.user_id"))
                .build();

        Optional<JsonNode> nestedResult = extractor.extract(root, nestedConfig);
        System.out.println("  Result: " + GREEN + nestedResult.orElse(null) + RESET);

        printSuccess();
    }

    private static void runScenario8_YamlSchemaValidation() throws Exception {
        printScenarioHeader(8, "YAML Schema Validation",
                "Validating YAML configuration before use");

        TypeResolver typeResolver = new TypeResolver(List.of(PROTOBUF_PACKAGE));
        TransformRegistry registry = BuiltinTransforms.createRegistry();
        SchemaValidator validator = new SchemaValidator(typeResolver, registry);

        // Valid schema - load from file
        System.out.println(CYAN + "▸ Validating correct schema (user-event-simple.yaml):" + RESET);
        String validYaml = loadResource("integration/mapping/user-event-simple.yaml");
        printYamlCompact(validYaml.substring(0, Math.min(validYaml.length(), 500)) + "\n  ...");

        YamlConfigLoader loader = new YamlConfigLoader();
        MappingSchema validSchema = loader.load("classpath:integration/mapping/user-event-simple.yaml");
        ValidationResult validResult = validator.validate(validSchema, "user-event-simple");

        if (validResult.isValid()) {
            System.out.println(GREEN + "  ✓ Schema is valid" + RESET);
        }

        // Invalid schema - load from demo resources
        System.out.println("\n" + CYAN + "▸ Validating schema with intentional errors:" + RESET);
        String invalidYaml = loadResource("demo/invalid-schema.yaml");
        printYamlCompact(invalidYaml);

        MappingSchema invalidSchema = loader.load("classpath:demo/invalid-schema.yaml");
        ValidationResult invalidResult = validator.validate(invalidSchema, "invalid-config");

        if (!invalidResult.isValid()) {
            System.out.println(RED + "  ✗ Validation errors:" + RESET);
            for (String error : invalidResult.errors()) {
                System.out.println("    - " + error);
            }
        }
        if (!invalidResult.warnings().isEmpty()) {
            System.out.println(YELLOW + "  ⚠ Warnings:" + RESET);
            for (String warning : invalidResult.warnings()) {
                System.out.println("    - " + warning);
            }
        }

        printSuccess();
    }

    private static void runScenario9_PostMappingValidation() throws Exception {
        printScenarioHeader(9, "Post-Mapping Validation",
                "Validating Protobuf messages against schema constraints");

        // Show the validation schema constraints
        String schemaInfo = """
            Validation Schema: schemas/user-event.schema.json

            Constraints include:
            - visitorId: required, maxLength 128
            - eventType: required (search, detail-page-view, etc.)
            - For 'search' events: searchQuery OR pageCategories required
            """;
        System.out.println(box(schemaInfo.trim()));

        // Load constraints
        ProtobufConstraints constraints = ProtobufConstraints.fromClasspath(
                "schemas/user-event.schema.json");

        ProtobufMessageValidator validator = new ProtobufMessageValidator(constraints);

        MappingEngine engine = MappingEngine.builder()
                .withProtobufPackage(PROTOBUF_PACKAGE)
                .withConfig("classpath:integration/mapping/user-event-simple.yaml")
                .build();

        // Valid message
        System.out.println("\n" + CYAN + "▸ Test 1: Valid UserEvent" + RESET);

        String validJson = """
            {
              "eventType": "search",
              "visitorId": "visitor-123",
              "searchQuery": "laptop"
            }
            """;

        System.out.println("  Input JSON:");
        printBoxedContent(validJson.trim(), 50);

        UserEvent validEvent = engine.map(validJson, "user-event-simple", UserEvent.class);
        ValidationResult validResult = validator.validate(validEvent);

        System.out.println("\n  Mapped Protobuf:");
        System.out.println("    eventType: \"" + validEvent.getEventType() + "\"");
        System.out.println("    visitorId: \"" + validEvent.getVisitorId() + "\"");
        System.out.println("    searchQuery: \"" + validEvent.getSearchQuery() + "\"");

        if (validResult.isValid()) {
            System.out.println("\n" + GREEN + "  ✓ Validation passed" + RESET);
        }

        // Invalid message (missing required field for search event)
        System.out.println("\n" + CYAN + "▸ Test 2: Invalid UserEvent (missing required fields)" + RESET);

        String invalidJson = """
            {
              "eventType": "search",
              "visitorId": ""
            }
            """;

        System.out.println("  Input JSON:");
        printBoxedContent(invalidJson.trim(), 50);

        // Build message directly to simulate invalid state
        UserEvent.Builder invalidBuilder = UserEvent.newBuilder()
                .setEventType("search");
        // visitorId is empty, searchQuery is missing

        UserEvent invalidEvent = invalidBuilder.build();
        ValidationResult invalidResult = validator.validate(invalidEvent);

        System.out.println("\n  Mapped Protobuf:");
        System.out.println("    eventType: \"search\"");
        System.out.println("    visitorId: \"\" " + RED + "(empty!)" + RESET);
        System.out.println("    searchQuery: " + RED + "(missing!)" + RESET);

        if (!invalidResult.isValid()) {
            System.out.println("\n" + RED + "  ✗ Validation errors:" + RESET);
            for (String error : invalidResult.errors()) {
                System.out.println("    - " + error);
            }
        }

        printSuccess();
    }

    private static void runScenario10_TypeResolution() throws Exception {
        printScenarioHeader(10, "Type Resolution",
                "Using TypeResolver to find Protobuf classes");

        TypeResolver resolver = new TypeResolver(List.of(PROTOBUF_PACKAGE));

        System.out.println(box("Package: " + PROTOBUF_PACKAGE));

        // Resolve various types
        String[] typesToResolve = {
                "UserEvent",
                "Product",
                "UserInfo",
                "PriceInfo",
                "Product.Availability"  // Nested enum
        };

        System.out.println("\n" + CYAN + "▸ Resolving types:" + RESET);

        for (String typeName : typesToResolve) {
            try {
                Class<?> clazz = resolver.resolve(typeName);
                System.out.println("  " + typeName + " → " + GREEN + clazz.getSimpleName() + RESET);
                System.out.println("    " + DIM + clazz.getName() + RESET);
            } catch (Exception e) {
                System.out.println("  " + typeName + " → " + RED + "Not found" + RESET);
            }
        }

        // Check if types can be resolved
        System.out.println("\n" + CYAN + "▸ Checking type availability:" + RESET);
        System.out.println("  canResolve(\"UserEvent\"): " + resolver.canResolve("UserEvent"));
        System.out.println("  canResolve(\"NonExistentType\"): " + resolver.canResolve("NonExistentType"));

        printSuccess();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private static void printHeader() {
        System.out.println();
        System.out.println(BOLD + "╔══════════════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(BOLD + "║           YAML-PROTOBUF-MAPPER DEMO                                  ║" + RESET);
        System.out.println(BOLD + "║           Interactive showcase of library capabilities               ║" + RESET);
        System.out.println(BOLD + "╚══════════════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }

    private static void printFooter() {
        System.out.println();
        System.out.println(BOLD + "══════════════════════════════════════════════════════════════════════" + RESET);
        System.out.println(GREEN + "  Demo completed successfully!" + RESET);
        System.out.println(BOLD + "══════════════════════════════════════════════════════════════════════" + RESET);
        System.out.println();
    }

    private static void printSectionHeader(String title) {
        System.out.println();
        System.out.println(BOLD + "══════════════════════════════════════════════════════════════════════" + RESET);
        System.out.println(BOLD + CYAN + "  " + title + RESET);
        System.out.println(BOLD + "══════════════════════════════════════════════════════════════════════" + RESET);
    }

    private static void printScenarioHeader(int number, String title, String description) {
        System.out.println();
        System.out.println(BOLD + "┌──────────────────────────────────────────────────────────────────────" + RESET);
        System.out.println(BOLD + "│ SCENARIO " + number + ": " + title + RESET);
        System.out.println("│ " + DIM + description + RESET);
        System.out.println(BOLD + "└──────────────────────────────────────────────────────────────────────" + RESET);
    }

    private static void printYaml(String yaml) {
        System.out.println("\n" + CYAN + "▸ YAML Configuration:" + RESET);
        printBoxedContent(yaml.trim(), 70);
    }

    private static void printYamlCompact(String yaml) {
        String[] lines = yaml.trim().split("\n");
        for (String line : lines) {
            System.out.println("  " + DIM + line + RESET);
        }
    }

    private static void printJson(String json) {
        System.out.println("\n" + CYAN + "▸ Input JSON:" + RESET);
        printBoxedContent(json.trim(), 70);
    }

    private static void printProtobuf(Message message) throws Exception {
        System.out.println("\n" + CYAN + "▸ Mapped Protobuf (" + message.getClass().getSimpleName() + "):" + RESET);
        String protoText = JsonFormat.printer()
                .omittingInsignificantWhitespace()
                .print(message);

        // Pretty print the JSON
        JsonNode node = MAPPER.readTree(protoText);
        String prettyJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);

        printBoxedContent(prettyJson, 70);
    }

    private static void printNote(String note) {
        System.out.println("\n" + YELLOW + "▸ Note:" + RESET);
        for (String line : note.split("\n")) {
            System.out.println("  " + line);
        }
    }

    private static void printSuccess() {
        System.out.println("\n" + GREEN + "✓ Scenario completed successfully" + RESET);
    }

    private static void printBoxedContent(String content, int maxWidth) {
        String[] lines = content.split("\n");
        System.out.println("┌" + "─".repeat(maxWidth + 2) + "┐");
        for (String line : lines) {
            if (line.length() > maxWidth) {
                // Wrap long lines
                int start = 0;
                while (start < line.length()) {
                    int end = Math.min(start + maxWidth, line.length());
                    String segment = line.substring(start, end);
                    System.out.println("│ " + segment + " ".repeat(maxWidth - segment.length()) + " │");
                    start = end;
                }
            } else {
                System.out.println("│ " + line + " ".repeat(maxWidth - line.length()) + " │");
            }
        }
        System.out.println("└" + "─".repeat(maxWidth + 2) + "┘");
    }

    private static String box(String content) {
        StringBuilder sb = new StringBuilder();
        String[] lines = content.split("\n");
        int maxLen = 0;
        for (String line : lines) {
            maxLen = Math.max(maxLen, line.length());
        }
        sb.append("┌").append("─".repeat(maxLen + 2)).append("┐\n");
        for (String line : lines) {
            sb.append("│ ").append(line).append(" ".repeat(maxLen - line.length())).append(" │\n");
        }
        sb.append("└").append("─".repeat(maxLen + 2)).append("┘");
        return sb.toString();
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream is = DemoRunner.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
