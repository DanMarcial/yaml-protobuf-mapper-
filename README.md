# YAML Protobuf Mapper

Sistema de mapeo YAML-driven para convertir JSON a mensajes Protobuf sin codigo hardcodeado.

## Problema que Resuelve

En sistemas que procesan eventos JSON hacia Protobuf, los cambios frecuentes requieren modificar codigo y redesplegar. Este modulo permite configurar mapeos via YAML.

| Enfoque Tradicional | Con YAML Mapper |
|---------------------|-----------------|
| Modificar codigo Java | Editar archivo YAML |
| Recompilar proyecto | Sin recompilacion |
| Redesplegar aplicacion | Cambio de config |

## Quickstart

### 1. Agregar Dependencia

```xml
<dependency>
    <groupId>io.github.yamlmapper</groupId>
    <artifactId>yaml-protobuf-mapper</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configurar Engine

```java
MappingEngine engine = MappingEngine.builder()
    .withProtobufPackage("com.google.cloud.retail.v2")
    .withConfig("classpath:mapping/search.yaml")
    .enableMetrics(true)
    .enablePostMappingValidation(true)
    .build();
```

### 3. Crear Config YAML

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

### 4. Mapear

```java
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

## Referencia YAML

### Estructura Base

```yaml
rootType: MessageType    # Tipo Protobuf destino

fields:
  fieldName:
    type: string|int32|int64|float|double|boolean|timestamp|enum|object|array|map
    source: [campo1, campo2]    # Fallback sources
    required: true|false
    default: "valor"
    transform: transformName
    transformParams:
      param1: value1
```

### Tipos Soportados

| Tipo | Descripcion | Ejemplo |
|------|-------------|---------|
| `string` | Texto | `type: string` |
| `int32` | Entero 32-bit | `type: int32` |
| `int64` | Entero 64-bit | `type: int64` |
| `float` | Decimal 32-bit | `type: float` |
| `double` | Decimal 64-bit | `type: double` |
| `boolean` | true/false | `type: boolean` |
| `timestamp` | Fecha/hora | `type: timestamp` + `format: iso8601` |
| `enum` | Enumeracion | `type: enum` + `enumType: Product.Availability` |
| `object` | Objeto anidado | `type: object` + `objectType: PriceInfo` |
| `array` | Lista | `type: array` + `itemType: string` |
| `map` | Mapa clave-valor | `type: map` + `keyType: string` + `valueType: string` |

### Formatos de Timestamp

```yaml
eventTime:
  type: timestamp
  source: [timestamp]
  format: iso8601        # "2024-03-15T14:30:00Z"

eventTime:
  type: timestamp
  source: [timestamp_ms]
  format: unix_millis    # 1710510600000
```

### Objetos Anidados

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

### Arrays de Objetos

```yaml
productDetails:
  type: array
  itemType: ProductDetail
  source: [items, products]
  fields:
    product:
      type: object
      objectType: Product
      source: ["."]      # "." = contexto actual
      fields:
        id:
          source: [sku, product_id, id]
    quantity:
      type: int32
      source: [qty, quantity]
      default: 1
```

## Transforms

### Transforms Disponibles

| Transform | Descripcion | Parametros |
|-----------|-------------|------------|
| `singleItemToArray` | Envuelve valor en array | - |
| `splitToArray` | Divide string en array | `delimiter` |
| `mapValue` | Mapea valores con diccionario | `mapping`, `default` |
| `stringsToImages` | Strings a objetos Image | `uriField`, `defaultWidth`, `defaultHeight` |
| `zipArrays` | Combina arrays paralelos | `merge`, `lookupKey` |
| `truncate` | Trunca string | `maxLength` |
| `uppercase` | Convierte a mayusculas | - |
| `lowercase` | Convierte a minusculas | - |
| `trim` | Elimina espacios | - |
| `filterBlank` | Filtra strings vacios | - |
| `replaceChars` | Reemplaza caracteres | `from`, `to` |

### Ejemplos de Transforms

```yaml
# Split string to array
categories:
  type: array
  itemType: string
  source: [category_breadcrumb]
  transform: splitToArray
  transformParams:
    delimiter: " > "

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

# Merge parallel arrays
productDetails:
  type: array
  source: [items]
  transform: zipArrays
  transformParams:
    merge:
      qty: "quantities"
      price: "prices"
```

## Batch Processing

```java
List<String> jsonList = // miles de JSONs
BatchMapper batch = engine.batchMapper();

BatchResult<UserEvent> result = batch.mapBatch(
    jsonList,
    "config-id",
    UserEvent.class
);

result.successful();  // Lista de exitosos
result.failed();      // Lista de fallidos con errores
result.successRate(); // Porcentaje de exito
```

## Validacion

### Validacion de Config (Build Time)

```java
MappingEngine engine = MappingEngine.builder()
    .withConfig("classpath:mapping/events.yaml")
    .validateOnBuild(true)  // Valida al construir
    .build();
```

### Validacion Post-Mapping (Runtime)

```java
MappingResult<UserEvent> result = engine.mapWithDetails(
    json, "config-id", UserEvent.class);

if (!result.isValid()) {
    result.validationErrors().forEach(System.out::println);
}
```

## Observabilidad

### Metricas

```java
MappingMetrics metrics = engine.getMetrics();

metrics.getSuccessfulMappings();
metrics.getFailedMappings();
metrics.getSuccessRate();
metrics.getAverageLatencyMs();
```

### Health Check

```java
HealthCheck health = new HealthCheck(engine.getMetrics());

health.isLive();     // Liveness probe
health.isReady();    // Readiness probe
health.toJson();     // JSON para endpoints
```

### Tracing (OpenTelemetry)

```java
MappingEngine engine = MappingEngine.builder()
    .withTracer(openTelemetry.getTracer("mapper"))
    .build();
```

## Hot Reload

```java
ConfigReloader reloader = new ConfigReloader();
reloader.register(Paths.get("/config/mapping.yaml"), newConfig -> {
    // Reload logic
});
reloader.startWatching();
```

## Caracteristicas

- **Auto-descubrimiento de tipos**: Solo declaras el package, el engine encuentra las clases
- **Fallback de campos**: `source: [new_name, legacy_name, old_name]`
- **Transforms extensibles**: 13 transforms built-in + custom
- **Objetos anidados**: Soporte recursivo para estructuras complejas
- **Alto rendimiento**: MethodHandles cacheados (~300ns por mensaje)
- **Virtual Threads**: Batch processing con Java 21 virtual threads
- **Observabilidad**: Metricas, health checks, OpenTelemetry

## Requisitos

- Java 21+
- Protobuf 3.x

## Limitaciones Conocidas

### Map Keys

Solo se soporta `string` como tipo de clave en mapas Protobuf.

```yaml
# ✅ Soportado
attributes:
  type: map
  keyType: string
  valueType: string

# ❌ No soportado
intKeyMap:
  type: map
  keyType: int32    # Error: solo string permitido
  valueType: string
```

**Workaround**: Convertir claves numericas a string en el JSON de entrada.

### OneOf Fields

Los campos `oneof` de Protobuf son mutuamente exclusivos. Si el YAML configura multiples campos del mismo oneof, el ultimo valor establecido sobrescribe los anteriores.

```yaml
# Product tiene oneof "expiration" con campos expire_time y ttl
# Si ambos se configuran:
expireTime:
  type: timestamp
  source: [expire_time]
ttl:
  type: object
  source: [ttl]
# Warning: "OneOf conflict: field 'ttl' overwrites 'expireTime' in oneof 'expiration'"
```

**Comportamiento**: Se emite un warning en logs y en `MappingResult.warnings()`, pero el mapeo continua.

**Recomendacion**: Configurar solo uno de los campos del oneof, o usar logica condicional en el JSON de entrada.

### Protobuf Extensions

Las extensiones de Protobuf (proto2) no estan soportadas. El mapper trabaja exclusivamente con campos definidos en el schema base.

### Any y Dynamic Messages

El tipo `google.protobuf.Any` y mensajes dinamicos no estan soportados. Todos los tipos deben ser conocidos en tiempo de compilacion.

### Validacion de Tipos en Runtime

La validacion de tipos se realiza en runtime. Errores de tipo (ej: string donde se espera int) se detectan durante el mapeo, no en la carga del YAML.

```yaml
# Este YAML carga sin error, pero fallara en runtime si "count" no es numerico
count:
  type: int32
  source: [count]
  default: "not-a-number"  # Error en runtime, no en carga
```

**Recomendacion**: Usar `validateOnBuild(true)` en el builder para detectar errores de configuracion temprano.

### Campos Recursivos

Estructuras recursivas (mensaje que se contiene a si mismo) tienen soporte limitado. Se recomienda limitar la profundidad de anidacion.

### Precision Numerica

- `float`: Precision de 32-bit IEEE 754
- `double`: Precision de 64-bit IEEE 754
- Valores JSON muy grandes pueden perder precision al convertir

```yaml
# Valores > 2^53 pueden perder precision en JavaScript/JSON
bigNumber:
  type: int64
  source: [big_value]  # "9007199254740993" puede llegar como 9007199254740992
```

**Workaround**: Enviar numeros grandes como strings y usar transform custom para convertir.

## Licencia

Apache License 2.0
