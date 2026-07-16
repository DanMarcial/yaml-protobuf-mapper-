# Creating Custom Transforms

This guide covers how to create, test, and register custom transforms for the YAML Protobuf Mapper.

## Table of Contents

- [Overview](#overview)
- [Basic Structure](#basic-structure)
- [Step-by-Step Guide](#step-by-step-guide)
- [Working with Parameters](#working-with-parameters)
- [Working with Context](#working-with-context)
- [Return Types](#return-types)
- [Error Handling](#error-handling)
- [Testing](#testing)
- [Best Practices](#best-practices)
- [Examples](#examples)

---

## Overview

A transform is a function that modifies JSON data during the mapping process. Transforms are:

- **Stateless**: No instance variables that change between calls
- **Thread-safe**: Called concurrently from multiple threads
- **Null-safe**: Must handle null inputs gracefully

```
JSON Input → Extract Field → Transform → Type Conversion → Protobuf Field
                                ↑
                          Your code here
```

---

## Basic Structure

Every transform implements the `Transform` interface:

```java
package com.example.transforms;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

public class MyTransform implements Transform {

    @Override
    public JsonNode apply(JsonNode node, TransformContext context) {
        // Transform logic here
        return node;
    }

    @Override
    public String getName() {
        return "myTransform";  // Name used in YAML
    }
}
```

---

## Step-by-Step Guide

### Step 1: Create the Transform Class

```java
package com.example.transforms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

/**
 * Normalizes phone numbers by removing non-numeric characters
 * and optionally adding a country code prefix.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code countryCode} - Country code to prepend (default: none)</li>
 *   <li>{@code minLength} - Minimum valid length (default: 10)</li>
 * </ul>
 *
 * <p>Example YAML:
 * <pre>{@code
 * phone:
 *   type: string
 *   source: [phone, mobile]
 *   transform: normalizePhone
 *   transformParams:
 *     countryCode: "+1"
 *     minLength: 10
 * }</pre>
 */
public class NormalizePhoneTransform implements Transform {

    @Override
    public JsonNode apply(JsonNode node, TransformContext context) {
        // 1. Handle null/invalid input
        if (node == null || node.isNull() || !node.isTextual()) {
            return node;
        }

        // 2. Get parameters
        String countryCode = context.getParam("countryCode", "");
        int minLength = context.getParamAsInt("minLength", 10);

        // 3. Process the value
        String phone = node.asText().replaceAll("[^0-9]", "");

        // 4. Validate
        if (phone.length() < minLength) {
            return node;  // Return original if invalid
        }

        // 5. Return transformed value
        String normalized = countryCode + phone;
        return new TextNode(normalized);
    }

    @Override
    public String getName() {
        return "normalizePhone";
    }
}
```

### Step 2: Register the Transform

```java
MappingEngine engine = MappingEngine.builder()
    .withProtobufPackage("com.google.cloud.retail.v2")
    .registerTransform(new NormalizePhoneTransform())
    .withConfig("classpath:mapping/config.yaml")
    .build();
```

### Step 3: Use in YAML

```yaml
fields:
  phoneNumber:
    type: string
    source: [phone, mobile, contact_phone]
    transform: normalizePhone
    transformParams:
      countryCode: "+1"
      minLength: 10
```

---

## Working with Parameters

### Available Parameter Methods

| Method | Return Type | Example YAML |
|--------|-------------|--------------|
| `getParam(name)` | `String` | `param: "value"` |
| `getParam(name, default)` | `String` | - |
| `getParamAsInt(name, default)` | `int` | `param: 42` |
| `getParamAsBoolean(name, default)` | `boolean` | `param: true` |
| `getParamAsDouble(name, default)` | `double` | `param: 3.14` |
| `getParamAsMap(name)` | `Map<String, String>` | `param: {a: "1", b: "2"}` |
| `getParamAsList(name)` | `List<String>` | `param: [a, b, c]` |

### Parameter Examples

**Simple parameters:**

```java
String prefix = context.getParam("prefix", "");
int maxLength = context.getParamAsInt("maxLength", 100);
boolean uppercase = context.getParamAsBoolean("uppercase", false);
```

```yaml
transform: myTransform
transformParams:
  prefix: "ID-"
  maxLength: 50
  uppercase: true
```

**Map parameters:**

```java
Map<String, String> mapping = context.getParamAsMap("mapping");
String mapped = mapping.getOrDefault(inputValue, inputValue);
```

```yaml
transform: mapValue
transformParams:
  mapping:
    active: "ACTIVE"
    inactive: "INACTIVE"
    pending: "PENDING"
```

**List parameters:**

```java
List<String> allowedValues = context.getParamAsList("allowed");
if (!allowedValues.contains(value)) {
    return null;
}
```

```yaml
transform: filterAllowed
transformParams:
  allowed:
    - "US"
    - "CA"
    - "MX"
```

---

## Working with Context

### Accessing Root JSON

Use `getRootNode()` to access the complete input JSON:

```java
@Override
public JsonNode apply(JsonNode node, TransformContext context) {
    JsonNode root = context.getRootNode();

    // Read another field from the input
    JsonNode countryNode = root.get("country");
    String country = countryNode != null ? countryNode.asText() : "US";

    // Use it in your transform logic
    if ("MX".equals(country)) {
        return new TextNode(node.asText() + " (Mexico)");
    }
    return node;
}
```

### Using Path Resolution

Use `resolvePath()` for nested field access with caching:

```java
@Override
public JsonNode apply(JsonNode node, TransformContext context) {
    // Resolves "user.address.country" with caching
    JsonNode country = context.resolvePath("user.address.country");

    // Supports array indexing
    JsonNode firstItem = context.resolvePath("items[0].name");

    return node;
}
```

### Creating New JSON Nodes

Use `getObjectMapper()` to create new JSON structures:

```java
@Override
public JsonNode apply(JsonNode node, TransformContext context) {
    ObjectMapper mapper = context.getObjectMapper();

    // Create array
    ArrayNode array = mapper.createArrayNode();
    array.add("value1");
    array.add("value2");

    // Create object
    ObjectNode object = mapper.createObjectNode();
    object.put("key", "value");
    object.set("nested", array);

    return object;
}
```

---

## Return Types

### Returning a String

```java
return new TextNode("transformed value");
```

### Returning a Number

```java
return new IntNode(42);
return new LongNode(123456789L);
return new DoubleNode(3.14);
```

### Returning a Boolean

```java
return BooleanNode.TRUE;
return BooleanNode.FALSE;
```

### Returning an Array

```java
ArrayNode array = context.getObjectMapper().createArrayNode();
array.add("item1");
array.add("item2");
return array;
```

### Returning an Object

```java
ObjectNode object = context.getObjectMapper().createObjectNode();
object.put("field1", "value1");
object.put("field2", 123);
return object;
```

### Returning Null

```java
return NullNode.getInstance();
// or
return null;  // Field will use default value if configured
```

---

## Error Handling

### Recommended Approach

```java
@Override
public JsonNode apply(JsonNode node, TransformContext context) {
    // 1. Handle null input
    if (node == null || node.isNull()) {
        return node;
    }

    // 2. Validate input type
    if (!node.isTextual()) {
        // Log warning but don't fail
        return node;
    }

    try {
        // 3. Transform logic
        String result = process(node.asText());
        return new TextNode(result);

    } catch (Exception e) {
        // 4. Return original on error (fail-safe)
        return node;
    }
}
```

### When to Throw Exceptions

Only throw when the error is unrecoverable and should stop the mapping:

```java
@Override
public JsonNode apply(JsonNode node, TransformContext context) {
    String requiredParam = context.getParam("requiredField");

    if (requiredParam == null) {
        throw new IllegalArgumentException(
            "Transform 'myTransform' requires parameter 'requiredField'");
    }

    // ...
}
```

---

## Testing

### Unit Test Structure

```java
package com.example.transforms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.yamlmapper.transform.TransformContext;
import io.github.yamlmapper.transform.TransformContextImpl;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NormalizePhoneTransform")
class NormalizePhoneTransformTest {

    private NormalizePhoneTransform transform;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        transform = new NormalizePhoneTransform();
        objectMapper = new ObjectMapper();
    }

    private TransformContext createContext(Map<String, Object> params) {
        return new TransformContextImpl(null, objectMapper, params);
    }

    @Nested
    @DisplayName("Basic functionality")
    class BasicTests {

        @Test
        @DisplayName("should remove non-numeric characters")
        void shouldRemoveNonNumericCharacters() {
            JsonNode input = new TextNode("(555) 123-4567");
            TransformContext context = createContext(Map.of());

            JsonNode result = transform.apply(input, context);

            assertThat(result.asText()).isEqualTo("5551234567");
        }

        @Test
        @DisplayName("should add country code when specified")
        void shouldAddCountryCode() {
            JsonNode input = new TextNode("5551234567");
            TransformContext context = createContext(Map.of("countryCode", "+1"));

            JsonNode result = transform.apply(input, context);

            assertThat(result.asText()).isEqualTo("+15551234567");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should return null input unchanged")
        void shouldHandleNullInput() {
            TransformContext context = createContext(Map.of());

            JsonNode result = transform.apply(null, context);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return non-text nodes unchanged")
        void shouldHandleNonTextInput() {
            JsonNode input = objectMapper.valueToTree(123);
            TransformContext context = createContext(Map.of());

            JsonNode result = transform.apply(input, context);

            assertThat(result).isEqualTo(input);
        }
    }

    @Test
    @DisplayName("should have correct name")
    void shouldHaveCorrectName() {
        assertThat(transform.getName()).isEqualTo("normalizePhone");
    }
}
```

### Integration Test

```java
@Test
@DisplayName("should work in full mapping pipeline")
void shouldWorkInPipeline() throws Exception {
    String yaml = """
        rootType: UserEvent
        fields:
          visitorId:
            type: string
            source: [phone]
            transform: normalizePhone
            transformParams:
              countryCode: "+1"
        """;

    String json = """
        {"phone": "(555) 123-4567"}
        """;

    MappingEngine engine = MappingEngine.builder()
        .withProtobufPackage("com.google.cloud.retail.v2")
        .registerTransform(new NormalizePhoneTransform())
        .withSchema("test", loadSchema(yaml))
        .build();

    UserEvent result = engine.map(json, "test", UserEvent.class);

    assertThat(result.getVisitorId()).isEqualTo("+15551234567");
}
```

---

## Best Practices

### Do

```java
// ✓ Handle null safely
if (node == null || node.isNull()) {
    return node;
}

// ✓ Validate input type
if (!node.isTextual()) {
    return node;
}

// ✓ Use default values for optional parameters
String prefix = context.getParam("prefix", "");

// ✓ Document parameters in Javadoc
/**
 * @param prefix - Optional prefix to add (default: "")
 */

// ✓ Keep transforms focused on one task
public class UppercaseTransform { }
public class TrimTransform { }

// ✓ Make transforms stateless
public JsonNode apply(JsonNode node, TransformContext context) {
    // All state comes from parameters
}
```

### Don't

```java
// ✗ Don't store state in instance variables
private String lastValue;  // BAD - not thread-safe

// ✗ Don't throw exceptions for invalid input
if (!node.isTextual()) {
    throw new IllegalArgumentException();  // BAD
}

// ✗ Don't modify the input node
((ObjectNode) node).put("field", "value");  // BAD - mutates input

// ✗ Don't do expensive operations without caching
Pattern.compile(regex);  // BAD if called on every invocation

// ✗ Don't ignore the return value
node.asText().toUpperCase();  // BAD - result is discarded
return node;
```

### Performance Tips

```java
// ✓ Compile patterns once (static final)
private static final Pattern PHONE_PATTERN = Pattern.compile("[^0-9]");

// ✓ Use resolvePath() for nested access (cached)
context.resolvePath("user.address.city");

// ✓ Pre-size collections when size is known
List<String> result = new ArrayList<>(inputList.size());

// ✓ Use primitive methods when possible
context.getParamAsInt("max", 100);  // Better than parsing manually
```

---

## Examples

### Example 1: Value Mapping

Maps input values to output values using a dictionary.

```java
public class MapValueTransform implements Transform {

    @Override
    public JsonNode apply(JsonNode node, TransformContext context) {
        if (node == null || !node.isTextual()) {
            return node;
        }

        Map<String, String> mapping = context.getParamAsMap("mapping");
        String defaultValue = context.getParam("default");
        String input = node.asText();

        String output = mapping.getOrDefault(input,
            defaultValue != null ? defaultValue : input);

        return new TextNode(output);
    }

    @Override
    public String getName() {
        return "mapValue";
    }
}
```

```yaml
status:
  type: string
  source: [status_code]
  transform: mapValue
  transformParams:
    mapping:
      A: "ACTIVE"
      I: "INACTIVE"
      P: "PENDING"
    default: "UNKNOWN"
```

### Example 2: Split to Array

Splits a delimited string into an array.

```java
public class SplitToArrayTransform implements Transform {

    @Override
    public JsonNode apply(JsonNode node, TransformContext context) {
        if (node == null || !node.isTextual()) {
            return context.getObjectMapper().createArrayNode();
        }

        String delimiter = context.getParam("delimiter", ",");
        boolean trim = context.getParamAsBoolean("trim", true);
        boolean filterEmpty = context.getParamAsBoolean("filterEmpty", true);

        ArrayNode result = context.getObjectMapper().createArrayNode();

        for (String part : node.asText().split(Pattern.quote(delimiter))) {
            String value = trim ? part.trim() : part;
            if (!filterEmpty || !value.isEmpty()) {
                result.add(value);
            }
        }

        return result;
    }

    @Override
    public String getName() {
        return "splitToArray";
    }
}
```

```yaml
tags:
  type: array
  itemType: string
  source: [tags_csv]
  transform: splitToArray
  transformParams:
    delimiter: ","
    trim: true
    filterEmpty: true
```

### Example 3: Conditional Transform

Returns different values based on other fields in the JSON.

```java
public class ConditionalCurrencyTransform implements Transform {

    private static final Map<String, String> COUNTRY_CURRENCIES = Map.of(
        "US", "USD",
        "MX", "MXN",
        "CA", "CAD",
        "GB", "GBP"
    );

    @Override
    public JsonNode apply(JsonNode node, TransformContext context) {
        String countryPath = context.getParam("countryField", "country");
        String defaultCurrency = context.getParam("default", "USD");

        JsonNode countryNode = context.resolvePath(countryPath);
        String country = countryNode != null ? countryNode.asText() : "";

        String currency = COUNTRY_CURRENCIES.getOrDefault(country, defaultCurrency);

        return new TextNode(currency);
    }

    @Override
    public String getName() {
        return "currencyByCountry";
    }
}
```

```yaml
currencyCode:
  type: string
  source: ["."]  # Use root context
  transform: currencyByCountry
  transformParams:
    countryField: "shipping.country"
    default: "USD"
```

---

## Checklist

Before submitting a new transform:

- [ ] Implements `Transform` interface
- [ ] Has a unique, descriptive `getName()`
- [ ] Handles null input
- [ ] Handles wrong input types (non-text when expecting text)
- [ ] Uses parameter defaults for optional params
- [ ] Is stateless (no mutable instance variables)
- [ ] Has Javadoc with parameter documentation
- [ ] Has unit tests for happy path
- [ ] Has unit tests for edge cases (null, empty, invalid)
- [ ] Has integration test with real YAML config
