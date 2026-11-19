# Activity Mapping Builder

Tickets: SDK-437, SDK-482

`com.im.njams.sdk.model.ActivityMapping` produces the tree-viewer JSON that `ActivityModel.mapping` carries, so
callers never assemble or serialize that structure by hand. The user-facing description of the format and the
helpers is the "How to set the activity mapping" section of `wiki/FAQ.md`; this spec covers the design and the rules
a change must keep.

## Classes

1. `model/ActivityMapping` — `public final`, private constructor. Holds one Jackson `JsonNode` root.
   1. `static Builder builder(String rootName)` — programmatic path.
   2. `static ActivityMapping fromObject(String rootName, Object source)` — derives the tree from any object.
   3. `static ActivityMapping fromPlainJson(String rootName, String json)` — parses the string with
      `JsonUtils.parse(json, JsonNode.class)` and delegates to `fromObject`; invalid JSON surfaces as
      `NjamsSdkRuntimeException` from `JsonUtils.parse`.
   4. `String toJson()` — serializes the root with `JsonUtils.serialize(root)`; `toString()` returns `toJson()`.
2. `ActivityMapping.Builder` — `public static final`, private constructor. `leaf(String, Object)` and
   `branch(String, Consumer<Builder>)` add a node to the current level and return `this`, so siblings chain; the
   `Consumer` populates a fresh child builder. `build()` returns the `ActivityMapping`, `toJson()` is
   `build().toJson()`.
3. `model/ActivityModel.setMapping(ActivityMapping)` — overload that delegates to `setMapping(String)` with
   `mapping.toJson()`, or `null` for a `null` argument (clears the mapping). The model stores only the `String`;
   `ActivityMapping` never reaches the message format.

## Tree-viewer format

Root: `{"name": <rootName>, "entries": [...]}`. Each entry is a **leaf** `{"name", "value"}` (value always a string)
or a **branch** `{"name", "entries": [...]}`. The nJAMS UI picks the tree viewer when the root has `name` and
`entries`; this is the server/UI contract the class targets, not something the SDK defines.

## Invariants

1. **The root is always a branch.** `fromObject` with a scalar or `null` source wraps it as a single leaf named
   `"value"` under the root, so the UI still selects the tree viewer.
2. **A node is a leaf or a branch, never both.** Leaves never carry `entries`, branches never carry `value`.
3. **Leaf values are strings; `null` becomes `""`.** Builder: `String.valueOf(value)`. `fromObject`: `asText()` of the
   scalar node; `null`, JSON null and missing nodes become `""`.
4. **`fromObject` mapping rules:** object node → branch, one entry per field named by the field name, in field order;
   array node → branch, one entry per element named by its index (`"0"`, `"1"`, …); scalar → leaf. Empty object or
   array → branch with an empty `entries` array. Applied recursively to arbitrary depth.
5. **`null` properties are kept, not dropped.** `fromObject` converts the source with
   `JsonSerializerFactory._internal().getMapper(false, false)` (`Include.ALWAYS`, not pretty), held in the lazy
   `InclusiveMapperHolder`. Switching to a `NON_NULL` mapper (e.g. `getFastMapper()`) would silently remove
   `null` properties from the tree.
6. **No Jackson type on the public surface.** `JsonNode`, `ObjectMapper` and the node types are relocated at
   packaging (see `public-api-design.md` and `checkstyle.xml`); they stay in private members only. The root is
   held as a private `JsonNode`, and every public signature uses `String`, `Object` or SDK types. Access to the
   mapper goes through the SDK-internal `JsonSerializerFactory._internal()` accessor.
7. **Scope is `mapping` only.** The helpers do not target `ActivityModel.config`, and the class makes no change
   to the message format: its output is an ordinary string in the existing `mapping` field.

## Decisions

1. Branch children are populated through a `Consumer<Builder>` lambda, while siblings chain on the same builder.
2. The tree is built directly as a Jackson `ObjectNode`/`ArrayNode` tree; there is no separate node POJO.
3. Cyclic object graphs passed to `fromObject` are unsupported and deliberately not guarded; behavior is whatever
   Jackson's `valueToTree` does.

## Tests

`njams-sdk/src/test/java/com/im/njams/sdk/model/ActivityMappingTest.java` covers invariants 1 to 5 for the builder,
`fromObject` and `fromPlainJson`, plus the `ActivityModel` overload including `null`. Invariant 6 is guarded by
review against the relocation list in `checkstyle.xml`, not by a test.
