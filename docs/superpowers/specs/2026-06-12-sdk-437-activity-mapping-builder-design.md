# SDK-437 — Activity mapping builder design

## Summary

`ActivityModel.mapping` accepts a specific tree-viewer JSON structure that the nJAMS UI renders as an
interactive tree (documented in `wiki/FAQ.md`, "How to set the activity mapping"). Today callers must
assemble and serialise that JSON by hand. This feature adds an SDK-owned, public helper that produces the
correct JSON via two paths — a fluent builder and an automatic from-object converter — without callers
knowing the wire format or touching JSON serialisation.

The work is purely additive (one new public class plus one new `ActivityModel` overload). It does not change
the message format and is not a breaking change.

## Goals

- Construct the tree-viewer structure programmatically (leaf and branch nodes, arbitrary depth).
- Derive the same structure automatically from an arbitrary object (scalars → leaves, nested objects and
  collections → branches, recursively).
- Both paths yield a JSON `String` that `ActivityModel.setMapping(String)` accepts and the nJAMS UI
  recognises as the tree viewer format.
- No relocated (shaded) third-party type appears on the public API surface.
- Full test coverage.

## Tree-viewer format (reference)

Root object: `{ "name": <root label>, "entries": [ ... ] }`. Each entry is either:
- a **leaf** — `{ "name": <string>, "value": <string> }`, or
- a **branch** — `{ "name": <string>, "entries": [ ... ] }`.

The presence of `name` + `entries` at the root is what triggers the tree viewer.

## Public API

New class `com.im.njams.sdk.model.ActivityMapping`:

- `static Builder builder(String rootName)` — entry point for the programmatic path.
- `static ActivityMapping fromObject(String rootName, Object source)` — Jackson-backed automatic path.
- `String toJson()` — the tree-viewer JSON string.
- `String toString()` — delegates to `toJson()`.

Nested fluent builder `ActivityMapping.Builder` (Option A — lambdas for branches, chaining for siblings):

- `Builder leaf(String name, Object value)` — adds a leaf to the current level; returns `this` for chaining.
- `Builder branch(String name, Consumer<Builder> children)` — adds a branch; the `Consumer` populates the
  branch's children on a fresh sub-builder; returns `this` for sibling chaining.
- `ActivityMapping build()` — produces the immutable `ActivityMapping`.

Example:

```java
String json = ActivityMapping.builder("stylesheet")
    .leaf("version", "2.0")
    .branch("template", t -> t
        .leaf("match", "/")
        .branch("ActivityInput", a -> a.leaf("message", "...")))
    .toJson();
```

New `ActivityModel` overload (delegates to the existing String setter):

```java
public void setMapping(ActivityMapping mapping) {
    setMapping(mapping == null ? null : mapping.toJson());
}
```

Usage covers both paths:

```java
activity.setMapping(ActivityMapping.builder("root").leaf("a", "1").build());
activity.setMapping(ActivityMapping.fromObject("root", someObject));
```

## Internals (all hidden)

- A private/package-private node POJO carrying `name`, `value`, `entries`. Serialised with the existing
  `com.im.njams.sdk.utils.JsonUtils.serialize(...)`, whose fast mapper already applies `Include.NON_NULL` —
  so a leaf (null `entries`) omits `entries` and a branch (null `value`) omits `value` automatically.
- `fromObject` obtains a `JsonNode` tree via the internal `JsonSerializerFactory` (Jackson
  `ObjectMapper.valueToTree(source)`) and walks it:
  - object node → branch; each field becomes an entry named by the field name.
  - array / collection node → branch; each element becomes an entry named by its index (`"0"`, `"1"`, …).
  - scalar node → leaf with `value = node.asText()`.
- `JsonNode`, `ObjectMapper`, and every other Jackson type stay strictly internal; no public or protected
  signature references a relocated type.

## Edge-case rules

- Non-string scalars (numbers, booleans) → leaf value is their textual form (`asText()`).
- `null` scalar → leaf with empty-string value.
- `fromObject` with a scalar top-level → the root stays a branch wrapping a single leaf, so the UI still
  triggers the tree viewer.
- Empty object / empty collection → branch with an empty `entries` array.
- Cyclic object graphs are unsupported (behaviour follows Jackson); documented as a limitation, not guarded.

## Testing

- Builder: leaf/branch nesting to arbitrary depth produces the expected JSON; sibling chaining; empty tree.
- `fromObject`: nested object, collection (index naming), scalar top-level wrapping, `null` handling,
  non-string scalars.
- `ActivityModel.setMapping(ActivityMapping)` delegation, including the `null` argument case.
- Verification that no relocated type appears on the `ActivityMapping` / `ActivityModel` public signatures.

## Documentation

Extend the existing `wiki/FAQ.md` "How to set the activity mapping" section to introduce the
`ActivityMapping` builder and `fromObject` helpers as the recommended way to produce the tree-viewer JSON.

## Out of scope

- Any change to the `config` attribute (helpers target `mapping` only).
- Any message-format change.
