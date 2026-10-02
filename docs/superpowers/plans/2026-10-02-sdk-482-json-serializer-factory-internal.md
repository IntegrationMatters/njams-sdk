# SDK-482 — JsonSerializerFactory: `_internal()` facet (section E) Implementation Plan

> Draft for user review. Not yet approved; nothing implemented. Part of SDK-482 (same ticket, see
> `docs/superpowers/specs/2026-10-01-sdk-482-remove-deprecated-code-design.md`, section E, postponed until now).

**Goal:** The nine deprecated, `forRemoval = false` `JsonSerializerFactory` methods that expose shaded Jackson types
are no longer part of the factory's regular public surface. Those the SDK itself needs move behind a public
`_internal()` entry point; unused ones are dropped; harmless ones stay.

**Decisions (user, 2026-10-02):**
1. `public static Internal _internal()` returns a nested public holder class `JsonSerializerFactory.Internal`
   (singleton constant, no per-call cost) carrying the four Jackson-typed methods under their current names.
   The method and the holder carry Javadoc stating: SDK-internal, not for client code, Jackson types are relocated.
2. Jackson types on this public holder remain a known exception to rule 1 of `checkstyle.xml` (manual review
   rule), moved from "JsonSerializerFactory deprecated methods" to "`JsonSerializerFactory.Internal`".
3. Tests that only use a mapper as a plain read/write helper move to `JsonUtils` (setup-only change, no assertion
   changes); `JsonSerializerFactoryTest` goes through `_internal()`.

## Classification (verified by grep over main, test, samples, comm-it)

| Method | Users | Outcome |
|---|---|---|
| `getFastMapper()` | `AbstractReceiver:189`, `JmsReceiver:132`, `KafkaReceiver:98`, `JsonSerializer:72`, `JsonUtils:60,84`, tests | Move to `Internal` |
| `getDefaultMapper()` | `MessageDebugDumper:111`, `ConfigurationInstructionListener:574`, `FileConfigurationProvider:73`, `ExtractHandler:233`, `JsonSerializer:70`, tests | Move to `Internal` |
| `getMapper(skip, pretty)` | `ActivityMapping:195`, `JsonUtils:133` | Move to `Internal` |
| `createWriter(skip, pretty)` | `ArgosSender:65` | Move to `Internal` |
| `createDefaultMapper(JsonFactory)` | only `createMapper` inside the factory | `private` |
| `addSerializer(JsonSerializer, JsonDeserializer, boolean)` | only `addSerializer(Converter, boolean)` | `private` |
| `addSerializer(JsonSerializer, JsonDeserializer)` | none | Delete |
| `createDefaultWriter()` | `JsonSerializerFactoryTest:55,73` only | Delete; test retargeted |
| `propertiesToJsonString(Properties)` | none | Delete |
| `addSerializer(Converter, boolean)`, `hasSerializer`, `removeSerializer` | public, no Jackson types | Stay unchanged |

## Global Constraints

- Same as the SDK-482 plan: no `mvn install`; offline compile/test goals only; poms carry the temporary version;
  stage explicit paths; commits `SDK-482 <description>` with the Co-Authored-By trailer; no `#comment` except the
  final commit; existing test assertions untouched (setup/accessor changes only, per the mapping rules below).
- Hot-path: `_internal()` returns a constant; the mappers stay cached exactly as now. No new allocation per call.
- Javadoc on every new public member; relocated-type exposure is the documented exception above.

## Task 11: Introduce `Internal`, migrate callers, narrow/drop the rest

**Files:**
- Modify: `common/JsonSerializerFactory.java`, callers listed above (main), `src/main/resources/checkstyle.xml`
  (comment lines 46-47), `.claude/rules/public-api-design.md` only if it names the factory as exception
  (verify by grep), `wiki/FAQ.md` ("Breaking changes in 6.1" section only; the 6.0 table at line ~52 is in the
  protected section and stays), spec status note.
- Tests: `JsonSerializerFactoryTest`, `JmsSenderTest:70`, `ProcessFilterTest:65,183,184,410`.

- [ ] **Step 1: Failing test.** `JsonSerializerFactoryTest`: `JsonSerializerFactory._internal()` returns the same
  instance on every call; `_internal().getFastMapper()`/`getDefaultMapper()`/`getMapper(...)` return the cached
  mappers (same instance on repeated calls; fast = compact, default = pretty, skip-null respected);
  `_internal().createWriter(true, true)` writes the same JSON as `JsonUtils.serialize(obj, true, true)`.
- [ ] **Step 2:** Run; expected FAIL (compile error).
- [ ] **Step 3:** Add `Internal` (nested `public static final class`, private constructor, private static final
  `INSTANCE`) with the four methods (bodies = the current ones, delegating to the private cached-mapper code),
  add `public static Internal _internal()`. Remove the four methods from the factory's own surface in the same
  change (callers are migrated in Step 4; one commit, compile-clean).
- [ ] **Step 4:** Migrate callers: `JsonSerializerFactory.getX(...)` → `JsonSerializerFactory._internal().getX(...)`
  in `AbstractReceiver`, `JmsReceiver`, `KafkaReceiver`, `MessageDebugDumper`, `ConfigurationInstructionListener`,
  `FileConfigurationProvider`, `ExtractHandler`, `ActivityMapping`, `JsonSerializer`, `ArgosSender`, `JsonUtils`.
  Keep each site's existing `@SuppressWarnings("deprecation")` only if it still applies (the `Internal` methods are
  not deprecated → remove the suppressions and the explanatory "@Deprecated flags external API consumers only"
  comments, they become untrue). Update `JsonSerializer` Javadoc links (lines ~59-61).
- [ ] **Step 5:** Narrow/drop: `createDefaultMapper` and the 3-arg `addSerializer(JsonSerializer, JsonDeserializer,
  boolean)` → `private`; delete the 2-arg `addSerializer`, `createDefaultWriter`, `propertiesToJsonString` and the
  imports/Javadoc made unused. Fix the class-level Javadoc (lines ~60-65) that describes the deprecation.
- [ ] **Step 6:** Tests: `JsonSerializerFactoryTest` `createDefaultWriter()` → `_internal().getDefaultMapper().writer()`
  (identical call chain, setup-only); `JmsSenderTest`, `ProcessFilterTest` use `JsonUtils.parse`/`serialize(…,
  true, true)` where they only read/write (setup-only; expected values untouched; verify identical output by running
  them).
- [ ] **Step 7:** `checkstyle.xml` exception comment → `JsonSerializerFactory.Internal`; FAQ "Breaking changes in 6.1"
  gets one line; spec status note.
- [ ] **Step 8:** Verify: reactor `mvn -o test-compile`, comm-it `-Pdocker-it test-compile -pl
  njams-sdk,njams-sdk-communication-it`, `mvn -o test -pl njams-sdk`, `mvn -o validate -Pcheckstyle -pl njams-sdk`,
  `mvn -o javadoc:javadoc -pl njams-sdk` (no errors). Expected test delta: 0 (new Step-1 tests only add).
- [ ] **Step 9:** Commit `SDK-482 Move the Jackson-typed JsonSerializerFactory methods behind _internal()`.

## Risks / open points

1. Breaking for any client that called the four methods (they could not name the shaded types, so only
   same-classloader/unshaded users are affected) or the dropped ones — accepted by decision; covered by the
   existing `breaking-change` label.
2. A leading-underscore method name is unconventional; it is intentional as a "do not use" marker.
3. The leftover-`@Deprecated` question is closed: after this task no `JsonSerializerFactory` method is deprecated.
