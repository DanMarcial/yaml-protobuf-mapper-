# YAML Protobuf Mapper

Lightweight library for mapping JSON to Protobuf messages using YAML configuration. Designed for data pipelines (Google Dataflow/Beam).

## Problem It Solves

When processing JSON events to Protobuf, changes in data structure require code modifications and redeployment. This library allows configuring mappings via YAML:

| Without YAML Mapper | With YAML Mapper |
|---------------------|------------------|
| Modify Java code | Edit YAML file |
| Recompile project | No recompilation |
| Redeploy application | Configuration change |

## Quickstart

### 1. Add Dependency

```xml
<dependency>
    <groupId>io.github.yamlmapper</groupId>
    <artifactId>yaml-protobuf-mapper</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Create YAML Configuration

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

### 3. Configure and Use the Engine

```java
// Build the engine
MappingEngine engine = MappingEngine.builder()
    .withProtobufPackage("com.google.cloud.retail.v2")
    .withConfig("classpath:mapping/search.yaml")
    .build();

// Map JSON to Protobuf
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

### Maps

```yaml
attributes:
  type: map
  keyType: string
  valueType: string
  source: [custom_attributes]
```

## Transforms

### Available Transforms (13)

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

# Convert breadcrumb to categories
categories:
  type: array
  itemType: string
  source: [category_breadcrumb]
  transform: splitToArray
  transformParams:
    delimiter: " > "

# Convert URLs to Image objects
images:
  type: array
  itemType: Image
  source: [image_urls]
  transform: stringsToImages
  transformParams:
    defaultWidth: 1200
    defaultHeight: 800

# Replace characters (locale format)
languageCode:
  type: string
  source: [locale]
  transform: replaceChars
  transformParams:
    from: "_"
    to: "-"

# Convert fields to CustomAttribute map (Google Retail API)
attributes:
  type: map
  source: ["."]
  objectType: CustomAttribute
  transform: fieldsToAttributeMap
  transformParams:
    fields:
      - vendor
      - lengths_cm
      - colors

# Parse key:value pairs string to attribute map
facets:
  type: map
  source: [facet_string]
  objectType: CustomAttribute
  transform: parseKeyValuePairs
  transformParams:
    pairDelimiter: "|"
    keyValueDelimiter: ":"
# Input: "Price:56.99|Brand:Nike|Brand:Adidas"
# Output: {"Price": {"numbers": [56.99]}, "Brand": {"text": ["Nike", "Adidas"]}}
```

## Multiple Sources with Merge

A single field can combine data from multiple sources. Instead of the last value overwriting previous ones, all sources are merged together. This is especially useful for maps:

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

  # Third source: facet fields
  - type: map
    source: ["."]
    objectType: CustomAttribute
    transform: fieldsToAttributeMap
    transformParams:
      fields:
        - "facet:color"
        - "facet:size"
```

**Result:** All three sources are merged into a single `attributes` map containing keys from facets string, metadata fields, and facet fields combined.

This pattern works for:
- **Maps**: Entries from all sources are merged
- **Arrays**: Elements from all sources are concatenated
- **Objects**: Fields from all sources are combined

## Scattered Fields

When your JSON has related data spread across different nodes, use `source: ["."]` to keep the root context:

```json
{
  "id": "PROD-123",
  "price": { "current": 999.99 },
  "past": { "price": 1199.99 },
  "currency": { "code": "USD" }
}
```

```yaml
priceInfo:
  type: object
  source: ["."]           # Keep root context
  objectType: PriceInfo
  fields:
    price:
      type: float
      source: [price.current]      # Access nested path
    originalPrice:
      type: float
      source: [past.price]         # From different node
    currencyCode:
      type: string
      source: [currency.code]      # From another node
```

### Path Syntax

| Format | Example | Description |
|--------|---------|-------------|
| Simple | `fieldName` | Direct field access |
| Nested | `user.address.city` | Dot notation for nested objects |
| Array | `items[0]` | Array index access |
| Combined | `users[0].address.street` | Mix of all formats |
| Root context | `"."` or `"$"` | Reference current context |
| Literal colon | `items:group_ids` | Field names containing `:` |

## Validation

### Configuration Validation (Build Time)

The engine automatically validates YAML configuration when built:

```java
MappingEngine engine = MappingEngine.builder()
    .withProtobufPackage("com.google.cloud.retail.v2")
    .withConfig("classpath:mapping/search.yaml")
    .build();  // Throws ConfigurationException if errors
```

Included validations:
- Valid types (string, int32, array, object, etc.)
- Transforms exist in registry
- Sources are not empty
- Objects have `fields` defined
- Arrays have `itemType`
- `objectType` is resolvable in Protobuf package

## Configuration Options

### Builder Options

```java
MappingEngine engine = MappingEngine.builder()
    // Protobuf package(s) for type auto-discovery
    .withProtobufPackage("com.google.cloud.retail.v2")
    .withProtobufPackages("com.example.proto", "com.other.proto")

    // Load YAML configurations
    .withConfig("classpath:mapping/search.yaml")
    .withConfig("classpath:mapping/add-to-cart.yaml")

    // Inject configId as eventType automatically (default: true)
    .injectEventType(true)

    // Register custom transform
    .registerTransform(new MyCustomTransform())

    // Custom ObjectMapper
    .withObjectMapper(customObjectMapper)

    .build();
```

### Embedded JSON Parsing (Opt-in)

If your JSON contains strings with embedded JSON, you can parse it:

```json
{
  "data": "{\"visitorId\": \"ABC\", \"items\": [{...}]}"
}
```

```yaml
data:
  type: object
  source: [data]
  parseEmbeddedJson: true  # Parse string as JSON
  fields:
    visitorId:
      type: string
      source: [visitorId]
```

## Standalone API Usage

Library classes are public and can be used independently for testing or custom integration.

### Testing Transforms

```java
import io.github.yamlmapper.transform.*;
import io.github.yamlmapper.transform.builtin.BuiltinTransforms;
import io.github.yamlmapper.config.FieldConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

// Get registry with builtin transforms
TransformRegistry registry = BuiltinTransforms.createRegistry();

// Get a specific transform
Transform splitTransform = registry.get("splitToArray");

// Prepare input
ObjectMapper mapper = new ObjectMapper();
JsonNode input = mapper.readTree("\"a,b,c\"");

// Create context with parameters
FieldConfig config = FieldConfig.builder("test")
    .transformParams(Map.of("delimiter", ","))
    .build();

TransformContext context = TransformContextImpl.builder()
    .fieldName("test")
    .fieldConfig(config)
    .objectMapper(mapper)
    .build();

// Execute transform
JsonNode result = splitTransform.apply(input, context);
// result = ["a", "b", "c"]
```

### Creating Custom Transforms

```java
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

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

### Loading and Validating YAML Configuration

```java
import io.github.yamlmapper.loader.YamlConfigLoader;
import io.github.yamlmapper.config.MappingSchema;
import io.github.yamlmapper.validation.SchemaValidator;
import io.github.yamlmapper.resolver.TypeResolver;
import io.github.yamlmapper.transform.builtin.BuiltinTransforms;

// Load YAML
YamlConfigLoader loader = new YamlConfigLoader();
MappingSchema schema = loader.load("classpath:mapping/search.yaml");

// Validate structure
TypeResolver typeResolver = new TypeResolver(List.of("com.google.cloud.retail.v2"));
TransformRegistry registry = BuiltinTransforms.createRegistry();
SchemaValidator validator = new SchemaValidator(typeResolver, registry);

ValidationResult result = validator.validate(schema, "search");

if (!result.isValid()) {
    result.errors().forEach(System.out::println);
}
if (!result.warnings().isEmpty()) {
    result.warnings().forEach(System.out::println);
}
```

### Extracting Values from JSON

```java
import io.github.yamlmapper.extractor.PathResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

ObjectMapper mapper = new ObjectMapper();
JsonNode root = mapper.readTree(jsonString);

// Use PathResolver to extract values with dot notation
PathResolver pathResolver = new PathResolver();

// Simple path
JsonNode visitorId = pathResolver.resolve(root, "visitorId");

// Nested path
JsonNode customerId = pathResolver.resolve(root, "customer.id");

// Array access
JsonNode firstItem = pathResolver.resolve(root, "items[0]");
```

## Features

- **Type auto-discovery**: Just declare the package, the engine finds the classes
- **Field fallback**: `source: [new_name, legacy_name, old_name]` - tries in order
- **Extensible transforms**: 13 built-in transforms + custom support
- **Scattered fields**: Map data from different JSON nodes using `source: ["."]`
- **Nested objects**: Recursive support for complex structures
- **High performance**: Cached MethodHandles and Caffeine cache for paths
- **Thread-safe**: Immutable instance after construction

## Requirements

- Java 21+
- Protobuf 3.x

## Known Limitations

### Map Keys

Only `string` as key type in Protobuf maps:

```yaml
# Supported
attributes:
  type: map
  keyType: string
  valueType: string

# Not supported
intKeyMap:
  type: map
  keyType: int32    # Error: only string allowed
```

### OneOf Fields

`oneof` fields are mutually exclusive. If you configure multiple fields from the same oneof, the last one overwrites the previous (warning emitted in logs):

```yaml
# Product has oneof "expiration" with fields expire_time and ttl
expireTime:
  type: timestamp
  source: [expire_time]
ttl:
  type: object
  source: [ttl]
# Warning: "OneOf conflict: 'ttl' overwrites 'expireTime' in oneof 'expiration'"
```

### Protobuf Extensions and Any

- Protobuf extensions (proto2) not supported
- `google.protobuf.Any` not supported
- All types must be known at compile-time

### Type Validation

Type errors are detected at runtime during mapping, not when loading YAML:

```yaml
count:
  type: int32
  source: [count]
  default: "not-a-number"  # Error at runtime, not at load
```

### Numeric Precision

- JSON values > 2^53 may lose precision
- Workaround: send large numbers as strings

## Complete Example

### Input JSON (legacy structure)

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
          priceInfo:
            type: object
            objectType: PriceInfo
            source: ["."]
            fields:
              price:
                type: float
                source: [price_usd]
              currencyCode:
                type: string
                default: "USD"
      quantity:
        type: int32
        source: [qty]
        default: 1
```

### Java Code

```java
MappingEngine engine = MappingEngine.builder()
    .withProtobufPackage("com.google.cloud.retail.v2")
    .withConfig("classpath:mapping/user-event.yaml")
    .build();

UserEvent event = engine.map(json, "user-event", UserEvent.class);

// Result:
event.getVisitorId();                    // "VIS_987654"
event.getSessionId();                    // "SESS-2024-03-15-ABC"
event.getEventTime().getSeconds();       // 1710510600
event.getExperimentIdsList();            // ["exp_checkout_v2", "exp_ui_dark"]
event.getUserInfo().getUserId();         // "CUST-987654"
event.getProductDetails(0).getProduct().getId();        // "LEGACY-SKU-001"
event.getProductDetails(0).getProduct().getTitle();     // "Gaming Laptop"
event.getProductDetails(0).getProduct().getCategoriesList(); // ["Computers", "Laptops", "Gaming"]
```

## License

Apache License 2.0
