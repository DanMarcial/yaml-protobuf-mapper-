package io.github.yamlmapper.demo;

import com.google.cloud.retail.v2.Product;
import com.google.cloud.retail.v2.UserEvent;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.core.MappingEngine;
import io.github.yamlmapper.loader.YamlConfigLoader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Quick test utility for testing JSON + YAML mapping directly.
 *
 * <p>Usage:
 * <pre>{@code
 * // Option 1: From classpath
 * mvn test-compile exec:java \
 *   -Dexec.mainClass="io.github.yamlmapper.demo.QuickTest" \
 *   -Dexec.classpathScope=test
 *
 * // Option 2: Edit the JSON and YAML strings below and run
 * }</pre>
 */
public class QuickTest {

    private static final String PROTOBUF_PACKAGE = "com.google.cloud.retail.v2";

    // =====================================================================
    // EDIT THESE TO TEST YOUR JSON AND YAML
    // =====================================================================

    private static final String YAML_CONFIG = """
            rootType: Product

            fields:
              id:
                type: string
                source: ["items:id"]
                required: true
              name:
                type: string
                source: ["variations:metadata:PTC_OMNI_PET_TYPE_PRIMARY"]
            """;

    private static final String JSON_INPUT = """
            {
              "item_groups:parent_id": "<string>",
              "item_groups:id": "bird",
              "item_groups:name": "Bird Supplies",
              "item_groups:data": "{\\"url\\":\\"/shop/en/acmestore/category/bird\\"}",
              "items:id": "2239",
              "items:item_name": "Premium Brand Healthy Aging Chicken & Whol...",
              "items:group_ids": "dry-dog-food|repeat-delivery-eligible-products|hig...",
              "items:keywords": "<string>",
              "items:url": "/shop/en/acmestore/product/premium-brand-health-...",
              "items:image_url": "https://assets.example.com/images/upload/f_auto...",
              "items:facets": "Health Feature:Scientific Formula|Health Feature:H...",
              "items:metadata:PTC_OMNI_PDP_BEHAVIOR_TEMPLATE": "<string>",
              "items:metadata:PTC_OMNI_BRAND_PRIMARY": "AcmeBrand",
              "items:metadata:PTC_OMNI_IN_STORE_ONLY_FLAG": "No",
              "items:metadata:PTC_OMNI_SAME_DAY_DELIVERY_FG": "Yes",
              "items:metadata:PTC_OMNI_REPEAT_DELIVERY_FL": "Yes",
              "items:metadata:PTC_OMNI_PERSONALIZED_ITEM_FL": "No",
              "items:metadata:PTC_OMNI_RX_FOOD_IND": "No",
              "items:metadata:PTC_OMNI_LIVE_FOOD_FL": "<string>",
              "items:metadata:PTC_OMNI_BOPUS_FLAG": "Yes",
              "items:metadata:PTC_OMNI_PRIMARY_ITEM_FLAG": "Yes",
              "items:metadata:PTC_OMNI_DEEP_LINK_URL": "<string>",
              "items:metadata:PTC_OMNI_PET_TYPE_PRIMARY": "Dog",
              "items:metadata:PTC_OMNI_PROP_65_FLAG": "No",
              "items:metadata:PTC_OMNI_PROP_65_DESC": "<string>",
              "items:metadata:PTC_OMNI_TAXONOMY": "Consumables|Food|Household Pet",
              "items:metadata:PTC_OMNI_ISREPLACEMENTREQUIRED": "<string>",
              "items:metadata:PTC_OMNI_FLAVOR_PRIMARY": "Chicken",
              "items:metadata:AverageRating": "4.5444",
              "items:metadata:TotalReviewCount": "586",
              "items:metadata:TopSold": "<string>",
              "items:metadata:startDate": "27917265",
              "items:metadata:mfName": "AcmeBrand",
              "items:metadata:bogoPromoName": "<string>",
              "items:metadata:parentCatEntryID": "11501",
              "items:metadata:json:parentCatEntryIDAsString": "\\"11501\\"",
              "items:metadata:itemname": "Premium Brand Healthy Aging Chicken & Whol...",
              "items:metadata:itemurl": "/shop/en/acmestore/product/premium-brand-health-...",
              "items:metadata:itemimg": "https://assets.example.com/images/upload/f_auto...",
              "items:facet:PTC_OMNI_BRAND_SUB_BRND": "Healthy Aging",
              "items:metadata:json:UPC_NUMBER": "\\"019014700684\\"",
              "items:description": "Veterinarians Recommend ACME: This ACME Proactive ...",
              "variations:item_id": "2239",
              "variations:item_name": "ACME Proactive Health Healthy Aging Chicken & Whol...",
              "variations:group_ids": "repeat-delivery-eligible-products|high-fiber-dog-f...",
              "variations:keywords": "<string>",
              "variations:url": "/shop/en/acmestore/product/premium-brand-health-...",
              "variations:image_url": "https://assets.example.com/images/upload/f_auto...",
              "variations:facets": "Price:56.99|Health Feature:Scientific Formula|Heal...",
              "variations:metadata:PTC_OMNI_PDP_BEHAVIOR_TEMPLATE": "<string>",
              "variations:metadata:PTC_OMNI_BRAND_PRIMARY": "AcmeBrand",
              "variations:metadata:PTC_OMNI_IN_STORE_ONLY_FLAG": "No",
              "variations:metadata:PTC_OMNI_SAME_DAY_DELIVERY_FG": "Yes",
              "variations:metadata:PTC_OMNI_REPEAT_DELIVERY_FL": "Yes",
              "variations:metadata:PTC_OMNI_PERSONALIZED_ITEM_FL": "No",
              "variations:metadata:PTC_OMNI_RX_FOOD_IND": "No",
              "variations:metadata:PTC_OMNI_LIVE_FOOD_FL": "<string>",
              "variations:metadata:PTC_OMNI_BOPUS_FLAG": "Yes",
              "variations:metadata:PTC_OMNI_PRIMARY_ITEM_FLAG": "Yes",
              "variations:metadata:PTC_OMNI_DEEP_LINK_URL": "<string>",
              "variations:metadata:PTC_OMNI_PET_TYPE_PRIMARY": "Dog",
              "variations:metadata:PTC_OMNI_PROP_65_FLAG": "No",
              "variations:metadata:PTC_OMNI_PROP_65_DESC": "<string>",
              "variations:metadata:PTC_OMNI_TAXONOMY": "Consumables|Food|Household Pet",
              "variations:metadata:PTC_OMNI_ISREPLACEMENTREQUIRED": "<string>",
              "variations:metadata:PTC_OMNI_FLAVOR_PRIMARY": "Chicken",
              "variations:metadata:AverageRating": "<string>",
              "variations:metadata:TotalReviewCount": "<string>",
              "variations:metadata:TopSold": "14763.0",
              "variations:metadata:listprice": "56.99",
              "variations:metadata:offerprice": "56.99",
              "variations:metadata:rdprice": "54.14",
              "variations:metadata:startDate": "<string>",
              "variations:metadata:mfName": "<string>",
              "variations:metadata:bogoPromoName": "<string>",
              "variations:metadata:json:catEntryID": "\\"11502\\"",
              "variations:variation_id": "2143363",
              "variations:metadata:flairImageurl": "https://assets.example.com/images/upload/f_auto...",
              "variations:facet:onlineinventory": "133.0",
              "variations:facet:onlinestock": "InStock",
              "variations:metadata:catHierarchy": "3074457345616878670|3074457345617004679|10049|1000...",
              "variations:facet:availability": "2793|1461|1460|2791|2790|1459|1458|2788|1456|2787|...",
              "variations:facet:PTC_OMNI_BRAND_SUB_BRND": "Healthy Aging",
              "variations:metadata:json:UPC_NUMBER": "\\"019014700684\\""
            }
            """;

    // Target class: UserEvent or Product
    private static final Class<? extends Message> TARGET_CLASS = Product.class;

    // =====================================================================

    public static void main(final String[] args) throws Exception {
        System.out.println("\n=== QUICK TEST ===\n");

        // Show inputs
        System.out.println("YAML Config:");
        System.out.println("─".repeat(60));
        System.out.println(YAML_CONFIG);
        System.out.println("─".repeat(60));

        System.out.println("\nJSON Input:");
        System.out.println("─".repeat(60));
        System.out.println(JSON_INPUT);
        System.out.println("─".repeat(60));

        // Load YAML from string
        final YamlConfigLoader loader = new YamlConfigLoader();
        final MappingSchema schema = loader.load(
                new ByteArrayInputStream(YAML_CONFIG.getBytes(StandardCharsets.UTF_8)),
                "quick-test"
        );

        // Build engine
        final MappingEngine engine = MappingEngine.builder()
                .withProtobufPackage(PROTOBUF_PACKAGE)
                .withSchema("quick-test", schema)
                .injectEventType(false)
                .build();

        // Map
        System.out.println("\nMapping...");
        final Message result = engine.map(JSON_INPUT, "quick-test", TARGET_CLASS);

        // Show result
        System.out.println("\nResult (" + TARGET_CLASS.getSimpleName() + "):");
        System.out.println("─".repeat(60));
        System.out.println(JsonFormat.printer().print(result));
        System.out.println("─".repeat(60));

        System.out.println("\n✓ Success!\n");
    }

    /**
     * Programmatic API for testing with custom JSON and YAML.
     */
    public static <T extends Message> T test(
            final String yaml,
            final String json,
            final Class<T> targetClass) throws Exception {

        final YamlConfigLoader loader = new YamlConfigLoader();
        final MappingSchema schema = loader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                "test"
        );

        final MappingEngine engine = MappingEngine.builder()
                .withProtobufPackage(PROTOBUF_PACKAGE)
                .withSchema("test", schema)
                .injectEventType(false)
                .build();

        return engine.map(json, "test", targetClass);
    }
}
