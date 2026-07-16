# YAML Protobuf Mapper

A configuration-driven library for mapping JSON to Protocol Buffer messages. Define your mappings in YAML instead of writing Java code.

## Why This Exists

Data pipeline teams frequently change how JSON events map to Protobuf messages. Without this library, every field mapping change requires:

1. Code modification
2. Recompilation
3. Redeployment

With YAML Protobuf Mapper, you edit a YAML file and reload. No code changes needed.

| Without YAML Mapper | With YAML Mapper |
|---------------------|------------------|
| Modify Java code | Edit YAML file |
| Recompile project | No recompilation |
| Redeploy application | Configuration change |

## Key Techniques

This library uses several performance-focused approaches:

- **[MethodHandles](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandle.html)** for near-native reflection speed (~3ns vs ~15ns for traditional reflection). See [BuilderFactory.java](./src/main/java/io/github/yamlmapper/builder/BuilderFactory.java) and [SetterResolver.java](./src/main/java/io/github/yamlmapper/builder/SetterResolver.java).

- **[Java Records](https://docs.oracle.com/en/java/javase/21/language/records.html)** for immutable configuration objects with auto-generated `equals()`, `hashCode()`, and constructors. Used in [FieldConfig.java](./src/main/java/io/github/yamlmapper/config/FieldConfig.java) and [MappingConfig.java](./src/main/java/io/github/yamlmapper/config/MappingConfig.java).

- **[Pattern Matching for Switch](https://docs.oracle.com/en/java/javase/21/language/pattern-matching-switch-expressions-and-statements.html)** for type-based dispatch in [GenericProtobufBuilder.java](./src/main/java/io/github/yamlmapper/builder/GenericProtobufBuilder.java).

- **Lazy HashMap allocation** for oneof field tracking - only allocates when actually needed.

- **Fast-path optimization** for single-source fields to avoid iterator allocation in the common case.

- **Pre-sized ArrayLists** when array size is known upfront to eliminate resize operations.

## Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| [Caffeine](https://github.com/ben-manes/caffeine) | 3.2.0 | High-performance caching for paths, types, and MethodHandles |
| [Jackson](https://github.com/FasterXML/jackson) | 2.17.2 | JSON/YAML parsing and manipulation |
| [Protocol Buffers](https://protobuf.dev/getting-started/javatutorial/) | 3.25.9 | Target message format |
| [SLF4J](https://www.slf4j.org/) + [Logback](https://logback.qos.ch/) | 2.0.16 / 1.5.8 | Logging |

**Test-only dependencies:**

- [JUnit 5](https://junit.org/junit5/) - Testing framework
- [AssertJ](https://assertj.github.io/doc/) - Fluent assertions
- [JMH](https://github.com/openjdk/jmh) - Microbenchmarking
- [MapStruct](https://mapstruct.org/) - Benchmark comparison

## Project Structure

```
yaml-protobuf-mapper/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/io/github/yamlmapper/
    │   │   ├── builder/
    │   │   ├── cache/
    │   │   ├── config/
    │   │   ├── core/
    │   │   ├── exception/
    │   │   ├── extractor/
    │   │   ├── loader/
    │   │   ├── resolver/
    │   │   ├── transform/
    │   │   └── validation/
    │   └── resources/
    └── test/
        ├── java/io/github/yamlmapper/
        │   ├── benchmark/
        │   └── integration/
        └── resources/
            ├── json/
            ├── mapping/
            └── integration/
```

**Notable directories:**

- [builder/](./src/main/java/io/github/yamlmapper/builder/) - Core mapping logic including `GenericProtobufBuilder`, `TypeConverter`, and `SetterResolver`
- [transform/](./src/main/java/io/github/yamlmapper/transform/) - 13 built-in transforms plus the extensibility API
- [config/](./src/main/java/io/github/yamlmapper/config/) - Configuration records (`FieldConfig`, `MappingConfig`, `MappingSchema`)
- [benchmark/](./src/test/java/io/github/yamlmapper/benchmark/) - JMH performance benchmarks comparing against MapStruct and reflection

## How to Use

### Installation

```xml
<dependency>
    <groupId>io.github.yamlmapper</groupId>
    <artifactId>yaml-protobuf-mapper</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Quick Example

**1. Create a YAML mapping file:**

```yaml
# src/main/resources/mapping/search.yaml
rootType: UserEvent

fields:
  visitorId:
    type: string
    source: [visitor_id, visitorId, vid]
    required: true

  searchQuery:
    type: string
    source: [query, search_query, q]

  pageCategories:
    type: array
    itemType: string
    source: [category]
    transform: singleItemToArray
```

**2. Build the engine and map:**

```java
MappingEngine engine = MappingEngine.builder()
    .withProtobufPackage("com.google.cloud.retail.v2")
    .withConfig("classpath:mapping/search.yaml")
    .build();

String json = """
    {
        "visitor_id": "abc-123",
        "query": "laptop gaming",
        "category": "Electronics"
    }
    """;

UserEvent event = engine.map(json, "search", UserEvent.class);

event.getVisitorId();       // "abc-123"
event.getSearchQuery();     // "laptop gaming"
event.getPageCategories(0); // "Electronics"
```

### Production Configuration

For production deployments, use input size limits and depth protection:

```java
MappingEngine engine = MappingEngine.builder()
    .withConfig(MappingConfig.PRODUCTION)  // 10MB limit, depth 64
    .withProtobufPackage("com.google.cloud.retail.v2")
    .withConfig("classpath:mapping/search.yaml")
    .build();
```

Or customize further:

```java
MappingConfig config = MappingConfig.builder()
    .maxJsonInputBytes(50 * 1024 * 1024)  // 50MB
    .maxNestingDepth(100)
    .treatBlankAsNull(false)
    .build();

MappingEngine engine = MappingEngine.builder()
    .withConfig(config)
    .withProtobufPackage("com.google.cloud.retail.v2")
    .withConfig("classpath:mapping/search.yaml")
    .build();
```

## YAML Reference

### Base Structure

```yaml
rootType: MessageType    # Target Protobuf type

fields:
  fieldName:
    type: string|int32|int64|float|double|boolean|timestamp|enum|object|array|map
    source: [field1, field2]    # Fallback sources (tries in order)
    required: true|false
    default: "value"
    transform: transformName
    transformParams:
      param1: value1
```

### Supported Types

| Type | Description | Configuration |
|------|-------------|---------------|
| `string` | Text | `type: string` |
| `int32` | 32-bit integer | `type: int32` |
| `int64` | 64-bit integer | `type: int64` |
| `float` | 32-bit decimal | `type: float` |
| `double` | 64-bit decimal | `type: double` |
| `boolean` | true/false | `type: boolean` |
| `timestamp` | Date/time | `type: timestamp` + `format: iso8601\|unix_millis` |
| `enum` | Enumeration | `type: enum` + `enumType: Product.Availability` |
| `object` | Nested object | `type: object` + `objectType: PriceInfo` + `fields: {...}` |
| `array` | List | `type: array` + `itemType: string\|int32\|ObjectType` |
| `map` | Key-value map | `type: map` + `keyType: string` + `valueType: string` |

### Timestamps

```yaml
# ISO 8601 format
eventTime:
  type: timestamp
  source: [timestamp]
  format: iso8601        # "2024-03-15T14:30:00Z"

# Unix milliseconds
eventTime:
  type: timestamp
  source: [timestamp_ms]
  format: unix_millis    # 1710510600000
```

Supported ISO 8601 formats:
- With Z suffix: `"2024-01-15T10:30:00Z"`
- With offset: `"2024-01-15T10:30:00+05:30"`
- Non-standard offset (auto-normalized): `"2024-01-15T10:30:00-0300"` -> `-03:00`

### Nested Objects

```yaml
priceInfo:
  type: object
  objectType: PriceInfo
  source: [pricing, priceInfo]
  fields:
    price:
      type: float
      source: [current_price, price]
    currencyCode:
      type: string
      default: "USD"
```

### Object Arrays

```yaml
productDetails:
  type: array
  itemType: ProductDetail
  source: [items, products]
  fields:
    product:
      type: object
      objectType: Product
      source: ["."]      # "." = use current element as context
      fields:
        id:
          type: string
          source: [sku, product_id, id]
    quantity:
      type: int32
      source: [qty, quantity]
      default: 1
```

### Enums

```yaml
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
```

### Path Syntax

| Format | Example | Description |
|--------|---------|-------------|
| Simple | `fieldName` | Direct field access |
| Nested | `user.address.city` | Dot notation for nested objects |
| Array | `items[0]` | Array index access |
| Combined | `users[0].address.street` | Mix of all formats |
| Root context | `"."` or `"$"` | Reference current context |

## Transforms

### Available Transforms

| Transform | Description | Parameters |
|-----------|-------------|------------|
| `singleItemToArray` | Wraps single value in array | - |
| `splitToArray` | Splits string into array | `delimiter` |
| `mapValue` | Maps values with dictionary | `mapping`, `default` |
| `stringsToImages` | URLs to Image objects | `uriField`, `defaultWidth`, `defaultHeight` |
| `zipArrays` | Combines parallel arrays into objects | `merge`, `lookupKey` |
| `truncate` | Truncates string to max length | `maxLength` |
| `uppercase` | Converts to uppercase | - |
| `lowercase` | Converts to lowercase | - |
| `trim` | Removes leading/trailing whitespace | - |
| `filterBlank` | Filters empty strings from arrays | - |
| `replaceChars` | Replaces characters | `from`, `to` |
| `fieldsToAttributeMap` | Converts fields to CustomAttribute map | `fields` |
| `parseKeyValuePairs` | Parses "key:value\|key:value" to map | `pairDelimiter`, `keyValueDelimiter` |

### Transform Examples

```yaml
# Split CSV string into array
experimentIds:
  type: array
  itemType: string
  source: [ab_tests]
  transform: splitToArray
  transformParams:
    delimiter: ","

# Map values
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

# Merge parallel arrays into objects (zipArrays)
# JSON: {"items": [{"sku": "A"}], "quantities": [2], "prices": [100]}
productDetails:
  type: array
  source: [items]
  transform: zipArrays
  transformParams:
    merge:
      qty: "quantities"        # quantities[i] -> item.qty
      unitPrice: "prices"      # prices[i] -> item.unitPrice
# Result: [{"sku": "A", "qty": 2, "unitPrice": 100}]
```

### Creating Custom Transforms

```java
public class MyUpperCaseTransform implements Transform {

    @Override
    public JsonNode apply(JsonNode node, TransformContext context) {
        if (node == null || !node.isTextual()) {
            return node;
        }
        return new TextNode(node.asText().toUpperCase());
    }

    @Override
    public String getName() {
        return "myUpperCase";
    }
}

// Register in the engine
MappingEngine engine = MappingEngine.builder()
    .withProtobufPackage("com.google.cloud.retail.v2")
    .registerTransform(new MyUpperCaseTransform())
    .withConfig("classpath:mapping/search.yaml")
    .build();
```

For comprehensive guidance on creating transforms including parameters, error handling, testing, and best practices, see **[Creating Custom Transforms](./docs/creating-transforms.md)**.

## Multiple Sources with Merge

A single field can combine data from multiple sources using array syntax:

```yaml
attributes:
  # First source: Parse "Key:Value|Key:Value" string
  - type: map
    source: [facets]
    objectType: CustomAttribute
    transform: parseKeyValuePairs
    transformParams:
      pairDelimiter: "|"
      keyValueDelimiter: ":"

  # Second source: metadata fields
  - type: map
    source: ["."]
    objectType: CustomAttribute
    transform: fieldsToAttributeMap
    transformParams:
      fields:
        - "metadata:brand"
        - "metadata:category"
```

All sources are merged into a single map.

## Configuration Options

```java
MappingEngine engine = MappingEngine.builder()
    // Protobuf package(s) for type auto-discovery
    .withProtobufPackage("com.google.cloud.retail.v2")
    .withProtobufPackages("com.example.proto", "com.other.proto")

    // Load YAML configurations
    .withConfig("classpath:mapping/search.yaml")
    .withConfig("classpath:mapping/add-to-cart.yaml")

    // Production config (size limits, depth protection)
    .withConfig(MappingConfig.PRODUCTION)

    // Register custom transform
    .registerTransform(new MyCustomTransform())

    // Custom ObjectMapper
    .withObjectMapper(customObjectMapper)

    .build();
```

## Complete Example

### Input JSON

```json
{
  "user_visitor_id": "VIS_987654",
  "session": "SESS-2024-03-15-ABC",
  "timestamp": 1710510600000,
  "ab_tests": "exp_checkout_v2,exp_ui_dark",
  "customer": {
    "id": "CUST-987654",
    "ip": "10.0.0.55"
  },
  "items": [
    {
      "sku": "LEGACY-SKU-001",
      "name": "Gaming Laptop",
      "category_path": "Computers > Laptops > Gaming",
      "price_usd": 2499.00,
      "qty": 2
    }
  ]
}
```

### YAML Configuration

```yaml
rootType: UserEvent

fields:
  visitorId:
    type: string
    source: [user_visitor_id, visitor_id, visitorId]
    required: true

  sessionId:
    type: string
    source: [session, session_id]

  eventTime:
    type: timestamp
    source: [timestamp]
    format: unix_millis

  experimentIds:
    type: array
    itemType: string
    source: [ab_tests]
    transform: splitToArray
    transformParams:
      delimiter: ","

  userInfo:
    type: object
    objectType: UserInfo
    source: [customer]
    fields:
      userId:
        type: string
        source: [id]
      ipAddress:
        type: string
        source: [ip]

  productDetails:
    type: array
    itemType: ProductDetail
    source: [items]
    fields:
      product:
        type: object
        objectType: Product
        source: ["."]
        fields:
          id:
            type: string
            source: [sku]
          title:
            type: string
            source: [name]
          categories:
            type: array
            itemType: string
            source: [category_path]
            transform: splitToArray
            transformParams:
              delimiter: " > "
      quantity:
        type: int32
        source: [qty]
        default: 1
```

### Result

```java
MappingEngine engine = MappingEngine.builder()
    .withProtobufPackage("com.google.cloud.retail.v2")
    .withConfig("classpath:mapping/user-event.yaml")
    .build();

UserEvent event = engine.map(json, "user-event", UserEvent.class);

event.getVisitorId();                    // "VIS_987654"
event.getSessionId();                    // "SESS-2024-03-15-ABC"
event.getEventTime().getSeconds();       // 1710510600
event.getExperimentIdsList();            // ["exp_checkout_v2", "exp_ui_dark"]
event.getUserInfo().getUserId();         // "CUST-987654"
event.getProductDetails(0).getProduct().getId();           // "LEGACY-SKU-001"
event.getProductDetails(0).getProduct().getCategoriesList(); // ["Computers", "Laptops", "Gaming"]
```

## Known Limitations

- **Map keys**: Only `string` keys are supported
- **OneOf fields**: Last value wins (warning logged)
- **Protobuf extensions**: Not supported
- **google.protobuf.Any**: Not supported
- **Blank strings**: Treated as `null` by default (configurable via `treatBlankAsNull`)

## Requirements

- Java 21+
- Protobuf 3.x

## License

Apache License 2.0
