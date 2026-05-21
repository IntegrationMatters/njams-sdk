# SDK Path Migration — As-Shipped (6.0.0)

**Status:** Migration **complete** on branch `6.0-dev`, 10 commits ahead of `origin/6.0-dev`, not pushed.

**Goal:** Migrate SDK-internal code and SDK public API from `com.im.njams.sdk.common.Path` (legacy) and path-as-`String` usage to the new singleton `com.im.njams.sdk.Path`.

**Out of scope:** Wire-format boundaries (messageformat `LogMessage`/`ProjectMessage`/`TraceMessage`, HTTP/JMS/Kafka headers, settings property files, instruction-protocol payloads) stay `String` on purpose.

**Cross-cutting decisions applied:**
- **Breaking API changes accepted** for 6.0.0 (no `@Deprecated` overload chains for API switched in this work).
- External callers migrate via the two converter bridges on the new `Path` class: `Path.of(com.im.njams.sdk.common.Path)` and `Path.toLegacyPath()`. The legacy class stays in the codebase, already `@Deprecated`, so external code holding legacy instances still compiles.
- **All commits reference SDK-205.**
- **Prefer imports over fully-qualified names** (rule added to CLAUDE.md mid-migration). Where a same-simple-name conflict forces a choice, import new `Path` and fully qualify legacy.

---

## What shipped

| # | Phase | Commit | Files | Notes |
|---|-------|--------|-------|-------|
| 0 | Rename `Path.get(...)` → `Path.of(...)` | `359622a5` | 2 | Aligned with JDK `List.of` / `Map.of` / `java.nio.file.Path.of` convention. Came before the migration phases. |
| 1 | `Njams.processModels` internal map keyed by new `Path` | `13c9fd35` | 1 | No public API change. |
| 1-fix | Apply prefer-imports rule to `Njams.java`; document in CLAUDE.md | `f1b6a441` | 2 | Initial Phase 1 used FQN for new `Path`; swapped to imported new + FQN'd legacy. |
| 6 | `Job.setBusinessService/Object` parameter type | `fc4dd86c` | 2 | `Job` interface + `JobImpl`. `String` overloads now use `Path.resolve` instead of `new com.im.njams.sdk.common.Path(s)`. |
| 4+5 | `Njams` + `ProcessModel` + `SubProcessActivityModel` public API on new `Path` | `6585b17d` | 18 | Combined for cleaner test churn (less cross-import in tests). Added `Path.getSegments()` helper. Includes test-base migration (`AbstractTest`) and dependent tests. |
| 2 | HTTP / JMS / Kafka receivers on new `Path` | `a2c92901` | 8 | `ShareableReceiver` + `SharedReceiverSupport` + 6 receiver impls. Header→Path uses `Path.resolve(...)`; comparison with `clientPath` uses identity (`==`). JmsReceiver got an `ancestorPathStrings` helper that replaced the temporary `toLegacyPath().getAllPaths()` bridges. |
| 8 | Sample modules (compile-only verification) | `9e1d28f9` | 11 | 9 sample-client + 2 sample-app files. `LogMessageResource` keeps `com.im.njams.sdk.Path` fully-qualified because `javax.ws.rs.Path` owns the simple-name slot. |

`mvn test -pl njams-sdk`: 385 tests, 102 errors — same 102 pre-existing Mockito/cglib JDK incompatibility errors as the pre-migration baseline. No regressions introduced by any phase.

---

## What was deferred or dropped

### Phase 3 — `Configuration` public API — **DROPPED**

Per user decision, `Configuration`, `ProcessConfiguration`, `ProcessFilter`, and `ConfigurationInstructionListener` are **not** migrated. The `Map<String, ProcessConfiguration> processes` field stays `String`-keyed (preserves on-disk JSON shape), and `Configuration`'s existing API (taking legacy `Path` / `String`) is preserved as-is.

### Legacy `com.im.njams.sdk.common.Path` class removal — **not in this release**

The class stays in the codebase, `@Deprecated`. External consumers migrate at their own pace via the converter bridges. Removal would be a future major-version task.

---

## Residual legacy-`Path` references in production code

These are intentional bridges at the `Configuration` boundary (consequence of dropping Phase 3) and at the `messageformat` / wire boundary (out of scope by design):

| Site | Why it stays |
|------|-------------|
| `Njams.isExcluded` → `configuration.isProcessExcluded(processPath.toLegacyPath())` | Configuration kept on legacy Path |
| `ProcessModel.getSerializableProcessModel` → `configuration.hasProcessExcludeFilter(path.toLegacyPath())` | Configuration boundary |
| `ConfigurationInstructionListener.getReording` → `configuration.hasProcess/getProcess(model.getPath().toLegacyPath())` (2 sites) | Listener interacts only with Configuration |
| `ConfigurationInstructionListener.exclude/hasExcludeFilter` → `new com.im.njams.sdk.common.Path(processPath)` (2 sites) | Builds legacy Path from instruction-protocol String to call legacy-typed Configuration |
| All `msg.setPath(path.toString())` / `msg.setSubProcessPath(...)` etc. | Messageformat boundary |
| Receiver header puts / gets | Wire-format boundary |

`configuration/` package, `configuration/ProcessFilter`, and `ConfigurationInstructionListenerTest` / `ProcessFilterTest` still import and construct legacy `com.im.njams.sdk.common.Path` — by design.

---

## Path API additions made during the migration

These were added as needed and may be useful beyond this work:

- `Path.isRoot()` — convenience predicate (commit `8d8a1518`).
- `Path.resolve(String...)` — lenient static producer that splits each argument at `>` and drops empties; cache-backed (commit `8d8a1518`).
- `Path.of(com.im.njams.sdk.common.Path)` — `@Deprecated` legacy → new bridge (commit `8d8a1518`).
- `Path.toLegacyPath()` — `@Deprecated` new → legacy bridge (commit `0ce6ffbc`).
- `Path.getChild/hasChild/getOrCreateChild(String...)` — varargs walk for multi-segment relative paths (commit `8d8a1518`).
- `Path.resolveChild/resolveOrCreateChild(String...)` — instance counterparts of `resolve` (commit `8d8a1518`).
- `Path.getSegments()` — ordered immutable list of segment names, root first (commit `6585b17d`; added in Phase 4+5 for use by `Njams`'s tree-building helpers).
- `Path.getSegmentName()` — this node's own segment, `null` for ROOT (commit `8d8a1518`).
