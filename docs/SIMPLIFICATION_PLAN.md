# Simplification Plan - YAML-Protobuf-Mapper

**Date:** 2025-05-20
**Status:** Completed

---

## Executive Summary

Exhaustive project analysis to identify core vs removable functionality.
The goal is to simplify the tool keeping only what's necessary for JSON → Protobuf mapping in Google pipelines.

---

## Usage Context

- **Environment:** Google Pipelines (Dataflow/Beam)
- **Processing:** One message at a time (workers managed by Google)
- **Configuration:** Predefined YAMLs, programmer chooses which one to use based on type
- **Observability:** Managed by Google infrastructure

---

## Decisions by Functionality

### CORE - Keep

| Functionality | LOC | Justification |
|--------------|-----|---------------|
| MappingEngine | ~800 | Entry point, orchestration |
| GenericProtobufBuilder | ~700 | Builds Protobuf recursively |
| JsonNodeExtractor + PathResolver | ~170 | JSON extraction with fallback |
| YamlConfigLoader | ~150 | Loads YAML configuration |
| FieldConfig + MappingSchema | ~200 | Configuration models |
| TypeResolver + BuilderFactory | ~250 | Protobuf type resolution |
| TypeConverter | ~200 | Type conversion |
| SchemaValidator | ~200 | YAML validation at build-time |

### TRANSFORMS - Keep

| Transform | Description | Example |
|-----------|-------------|---------|
| splitToArray | Splits string by delimiter | `"a,b,c"` → `["a","b","c"]` |
| mapValue | Maps values with dictionary | `"available"` → `"IN_STOCK"` |
| singleItemToArray | Wraps value in array | `"Nike"` → `["Nike"]` |
| trim | Removes whitespace | `"  text  "` → `"text"` |
| lowercase | Converts to lowercase | `"ABC"` → `"abc"` |
| uppercase | Converts to uppercase | `"abc"` → `"ABC"` |
| truncate | Cuts string to maxLength | `"largo..."` → `"lar..."` |
| filterBlank | Removes empty strings from arrays | `["a","","b"]` → `["a","b"]` |
| replaceChars | Replaces characters | `"en_US"` → `"en-US"` |
| zipArrays | Combines parallel arrays | Separate arrays → combined objects |
| stringsToImages | URLs → Image objects | **Improve: add custom H/W** |

### VALIDATION - Keep

| Functionality | Status | Notes |
|--------------|--------|-------|
| ProtobufMessageValidator | Keep | POST-mapping validation |
| ProtobufConstraints | Keep | Loads constraints from JSON |

### UTILITIES - Keep with changes

| Functionality | Status | Required change |
|--------------|--------|------------------|
| EmbeddedJsonParser | Keep | **Make opt-in** (disabled by default) |

---

## Functionality to REMOVE

| Functionality | LOC | Removal reason |
|--------------|-----|---------------------|
| BatchMapper | ~300 | Google pipelines handle workers |
| MappingMetrics | ~220 | Not needed in Google pipelines |
| HealthCheck + HealthCheckKeys | ~350 | Lives in Google infrastructure |
| ConfigReloader | ~380 | Predefined YAMLs, no hot-reload |
| TracingSupport | ~80 | Not really implemented |
| MappingResult | ~150 | Simple `map()` is sufficient |
| MdcKeys | ~20 | Without metrics, not needed |
| StringArrayToObjectArrayTransform | ~60 | No use case in Google Retail |
| ObjectKeysToArrayTransform | ~90 | No use case in Google Retail |

**Total to remove:** ~1,650 LOC (18% of code)

---

## Required Changes

### 1. StringsToImagesTransform - Custom H/W support

**File:** `src/main/java/io/github/yamlmapper/transform/builtin/StringsToImagesTransform.java`

**Support already existed.** Usage:
```yaml
images:
  source: [image_urls]
  transform: stringsToImages
  transformParams:
    defaultHeight: 800
    defaultWidth: 1200
```

**Status:** [x] Already implemented

---

### 2. EmbeddedJsonParser - Make opt-in

**Modified files:**
- `src/main/java/io/github/yamlmapper/config/FieldConfig.java` - Added `parseEmbeddedJson` field
- `src/main/java/io/github/yamlmapper/loader/YamlConfigLoader.java` - Loads the new field
- `src/main/java/io/github/yamlmapper/extractor/JsonNodeExtractor.java` - Only parses if enabled

**Usage:**
```yaml
metadata:
  source: [raw_metadata]
  parseEmbeddedJson: true  # Only if explicit (default: false)
```

**Status:** [x] Completed (2025-05-20)

---

### 3. Remove functionality

| File to remove | Status |
|-------------------|--------|
| `core/BatchMapper.java` | [x] Removed |
| `core/MappingMetrics.java` | [x] Removed |
| `core/MappingResult.java` | [x] Removed |
| `core/MdcKeys.java` | [x] Removed |
| `observability/HealthCheck.java` | [x] Removed |
| `observability/HealthCheckKeys.java` | [x] Removed |
| `observability/ConfigReloader.java` | [x] Removed |
| `observability/TracingSupport.java` | [x] Removed |
| `transform/builtin/StringArrayToObjectArrayTransform.java` | [x] Removed |
| `transform/builtin/ObjectKeysToArrayTransform.java` | [x] Removed |

---

### 4. Update MappingEngine

Remove references to:
- `enableMetrics()` / `getMetrics()`
- `mapWithDetails()` (returns MappingResult)
- `withTracer()` (TracingSupport)
- `batchMapper()`

**Status:** [x] Completed

---

### 5. Update BuiltinTransforms

Remove registration of:
- `stringArrayToObjectArray`
- `objectKeysToArray`

**Status:** [x] Completed

---

### 6. Update Tests

- Remove tests for removed functionality
- Update tests that use `mapWithDetails()`
- Update tests that use metrics

**Status:** [x] Completed

---

### 7. Update README.md

- Remove Batch Processing sections
- Remove Metrics sections
- Remove Health Checks sections
- Remove Hot Reload sections
- Update examples

**Status:** [x] Completed

---

## Impact Achieved

| Metric | Before | After | Change |
|---------|-------|---------|--------|
| Total LOC | ~8,900 | 6,503 | -27% |
| Java files | 57 | 46 | -11 files |
| Transforms | 13 | 11 | -2 |
| Tests | ~467 | 373 | -94 tests removed |
| Complexity | Medium | Low | |

---

## Execution Order

1. [x] ~~Create branch `simplification`~~ (not required, direct changes)
2. [x] Add custom H/W to StringsToImagesTransform (already implemented)
3. [x] Make EmbeddedJsonParser opt-in
4. [x] Remove listed files
5. [x] Update MappingEngine and references
6. [x] Update BuiltinTransforms
7. [x] Update/remove tests
8. [x] Update README.md
9. [x] Run all tests - **373 tests passing**
10. [ ] Commit and merge

---

## Additional Notes

### SchemaValidator - How it works

Validates YAML configuration at build-time:

```java
MappingEngine engine = MappingEngine.builder()
    .validateOnBuild(true)  // Enables validation
    .build();
```

**Validations:**
- Valid types (string, int32, array, object, etc.)
- Transforms exist in registry
- Sources not empty
- Objects have fields defined
- Arrays have itemType
- ObjectType is resolvable in Protobuf package

**Compatible with:** Any Protobuf, not specific to Google Retail.

---

### EmbeddedJsonParser - Use case

Google middleware sometimes sends JSON as escaped string:

```json
{
  "data": "{\"visitorId\": \"ABC\", \"items\": [{...}]}"
}
```

With opt-in:
```yaml
data:
  source: [data]
  parseEmbeddedJson: true  # Parses the string as JSON
```

---

## Change History

| Date | Change |
|-------|--------|
| 2025-05-20 | Document created |
| 2025-05-20 | EmbeddedJsonParser made opt-in (parseEmbeddedJson: true) |
| 2025-05-20 | Verified StringsToImagesTransform already supports custom H/W |
| 2025-05-20 | Removed 10 files of unnecessary functionality |
| 2025-05-20 | Updated MappingEngine (removed metrics, mapWithDetails, etc.) |
| 2025-05-20 | Updated BuiltinTransforms (removed 2 transforms) |
| 2025-05-20 | Removed 8 test files for removed functionality |
| 2025-05-20 | Updated integration tests (EndToEndMappingTest, PostMappingValidationTest) |
| 2025-05-20 | **First simplification completed - 379 tests passing** |
| 2025-05-20 | Second cleanup round: |
| | - Removed `TransformException.java` (never used) |
| | - `FieldExtractionException` now used in GenericProtobufBuilder:108 |
| | - Removed unused methods from `CacheFactory` (createDefault, createPathCache, createTypeCache) |
| | - Removed unused method `TypeResolver.getPackagePrefixes()` |
| | - Removed constant `ERR_REQUIRED_FIELD_MISSING` (replaced by FieldExtractionException) |
| | - **373 tests passing** |
