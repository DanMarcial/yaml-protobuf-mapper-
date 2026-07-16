package io.github.yamlmapper.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.retail.v2.UserEvent;
import io.github.yamlmapper.core.MappingEngine;

/**
 * Micro-benchmark to profile individual components.
 */
public class ProfileBenchmark {

    private static final int ITERATIONS = 100_000;

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        String json = """
            {
              "eventType": "search",
              "visitorId": "visitor-001",
              "searchQuery": "laptop",
              "userInfo": { "userId": "user-123", "ipAddress": "1.2.3.4" },
              "pageCategories": ["Electronics", "Computers"]
            }
            """;
        
        JsonNode jsonNode = mapper.readTree(json);
        
        MappingEngine engine = MappingEngine.builder()
            .withProtobufPackage("com.google.cloud.retail.v2")
            .withConfig("classpath:benchmark/user-event-minimal.yaml")
            .build();

        // Warmup
        for (int i = 0; i < 10_000; i++) {
            engine.map(jsonNode, "user-event-minimal", UserEvent.class);
        }

        // Profile
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            UserEvent event = engine.map(jsonNode, "user-event-minimal", UserEvent.class);
            if (event == null) throw new RuntimeException();
        }
        long elapsed = System.nanoTime() - start;
        
        System.out.println("Total: " + (elapsed / 1_000_000) + " ms");
        System.out.println("Per op: " + (elapsed / ITERATIONS) + " ns");
        System.out.println("Throughput: " + (1_000_000_000L * ITERATIONS / elapsed) + " ops/s");
    }
}
