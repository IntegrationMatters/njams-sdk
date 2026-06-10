# SDK-359: Njams Facade Refactoring — Design

**Ticket:** [SDK-359](https://salesfive.atlassian.net/browse/SDK-359) — Refactor Njams class
**Date:** 2026-06-10
**Status:** Draft for review

## Goal

`Njams` is a ~1490-line god class mixing lifecycle, client metadata, process modeling,
job tracking, serialization, command handling, replay, Argos, configuration, and
communication wiring. It shall become a real facade: a thin entry point exposing
focused *facet* objects, each owning one functional area. The public API gains a
grouped, discoverable shape (`njams.jobs().add(job)` instead of `njams.addJob(job)`),
while every existing public method remains in place, deprecated, delegating to the new
implementation.

## Constraints

- **No breaking change.** All existing `public`/`protected` members of `Njams` keep
  their signatures and observable behavior. New API is additive. The ticket therefore
  does not carry the `breaking-change` label.
- **Zero-overhead delegation.** Facets are created once in the constructor and held in
  `final` fields. A facet accessor is a field read; a deprecated old method is a single
  delegating call that the JIT inlines. No allocation per access. This keeps the hot
  path (`addJob`, `removeJob`) unaffected.
- **Communication stays internal.** No transport, sender, or receiver type appears in
  the new API surface.

## New public API shape

`Njams` keeps: constructors, `start()`, `stop()`, `isStarted()`, `getSettings()`,
`equals`/`hashCode`, the nested `Feature` enum, and the public `String` constants.
Everything else moves into one of nine facets, reachable through no-prefix accessors
(the no-prefix style deliberately distinguishes facet accessors from legacy getters and
avoids clashes such as `getJobs()`):

| Accessor | Facet type | Owns |
|---|---|---|
| `njams.metadata()` | `NjamsMetadata` | client path, category, client/SDK version, runtime version (get/set), machine, client session id, global variables (get/add), global-variables pattern (get/set) |
| `njams.features()` | `NjamsFeatures` | feature list (add/remove/has/list), container mode (get/set) |
| `njams.processes()` | `NjamsProcesses` | model registry (create/add/get/has/getAll), images, tree-element types, layouter (get/set), diagram factory (get/set), `send()` (full project message), `announce(model)` (additional project message) |
| `njams.jobs()` | `NjamsJobs` | job registry (add/remove/get/getAll), replay-marker bookkeeping (internal) |
| `njams.serializers()` | `NjamsSerializers` | serializer registry (add/remove/get/find), `serialize(t)`, `serialize(t, sizeLimit)`, lookup cache |
| `njams.replay()` | `NjamsReplay` | replay handler (get/set), replay instruction handling (internal) |
| `njams.commands()` | `NjamsCommands` | instruction listeners (add/remove/list), built-in command dispatch: send-project-message, ping, replay, get-request-handler (internal) |
| `njams.argos()` | `NjamsArgos` | Argos collectors (add/remove), `ArgosSender` wiring (internal) |
| `njams.configuration()` | `NjamsConfiguration` | access to `Configuration`, `getLogMode()`, `isExcluded(path)`, provider loading (internal) |

Internal only — no facet, hidden behind the facade:

- Communication wiring: sender/receiver creation, `beginConnect()`, receiver startup,
  shared-communication handling. `getSender()` is deprecated and delegates to the
  internal holder; it is already outside the intended public API per the project's API
  boundary rules.
- Data masking initialization (consumed by `start()`).
- Tree-element bookkeeping (inside `NjamsProcesses`).
- Version-file reading and startup banner (inside `NjamsMetadata`).

### Facet visibility

Facets are `public final` classes with **package-private constructors** in
`com.im.njams.sdk` (same package as `Njams`). External code can hold and use a facet
reference but can never instantiate one; the only source is the accessor on a `Njams`
instance. This was chosen over interface + hidden implementation because the facets
need no alternative implementations, and final classes keep the type count and
indirection minimal.

### Old API mapping (excerpt; the full list follows the same pattern)

| Deprecated on `Njams` | Replacement |
|---|---|
| `addJob(job)` / `removeJob(id)` / `getJobById(id)` / `getJobs()` | `jobs().add(job)` / `jobs().remove(id)` / `jobs().get(id)` / `jobs().getAll()` |
| `createProcess(path)` / `addProcessModel(m)` / `getProcessModel(path)` / `hasProcessModel(path)` / `getProcessModels()` | `processes().create(path)` / `.add(m)` / `.get(path)` / `.has(path)` / `.getAll()` |
| `sendProjectMessage()` / `sendAdditionalProcess(m)` | `processes().send()` / `processes().announce(m)` |
| `addImage(key, path)` / `addImage(supplier)` / `setTreeElementType(path, type)` | `processes().addImage(...)` / `processes().setTreeElementType(...)` |
| `getProcessModelLayouter()` / `setProcessModelLayouter(l)` / `getProcessDiagramFactory()` / `setProcessDiagramFactory(f)` | `processes().getLayouter()` / `.setLayouter(l)` / `.getDiagramFactory()` / `.setDiagramFactory(f)` |
| `addSerializer(...)` / `removeSerializer(...)` / `getSerializer(...)` / `findSerializer(...)` / `serialize(...)` | `serializers().add(...)` / `.remove(...)` / `.get(...)` / `.find(...)` / `.serialize(...)` |
| `getFeatures()` / `addFeature(f)` / `removeFeature(f)` / `hasFeature(f)` / `isContainerMode()` / `setContainerMode(b)` | `features().list()` / `.add(f)` / `.remove(f)` / `.has(f)` / `.isContainerMode()` / `.setContainerMode(b)` |
| `getGlobalVariables()` / `addGlobalVariables(m)` / `getGlobalVariablesPattern()` / `setGlobalVariablesPattern(p)` | `metadata().getGlobalVariables()` / `.addGlobalVariables(m)` / `.getGlobalVariablesPattern()` / `.setGlobalVariablesPattern(p)` |
| `getClientPath()` / `getCategory()` / `getClientVersion()` / `getSdkVersion()` / `getRuntimeVersion()` / `setRuntimeVersion(v)` / `getMachine()` | `metadata().…` |
| `getClientSessionId()` / `getCommunicationSessionId()` (duplicates) | `metadata().getClientSessionId()` (single method) |
| `getReplayHandler()` / `setReplayHandler(h)` | `replay().getHandler()` / `.setHandler(h)` |
| `getInstructionListeners()` / `addInstructionListener(l)` / `removeInstructionListener(l)` | `commands().…` |
| `addArgosCollector(c)` / `removeArgosCollector(c)` | `argos().add(c)` / `.remove(c)` |
| `getConfiguration()` / `getLogMode()` / `isExcluded(path)` | `configuration().get()` / `.getLogMode()` / `.isExcluded(path)` |
| `onInstruction(i)` (from `InstructionListener`) | internal dispatch in `NjamsCommands`; `Njams` keeps implementing `InstructionListener` for compatibility, the override delegates |

## Lifecycle and phase contracts

The project message is sent **once, at `start()`**, and must already contain all
relevant information. The server may request a re-send (rare, recovery only — the
re-send carries the then-current state, as today). The client may announce *processes*
added after start via additional project messages (lazy loading); additional messages
carry only the process and its tree path.

This yields per-facet phase contracts, enforced uniformly by a small internal lifecycle
guard shared by all facets:

| Facet | Phase contract (new API) |
|---|---|
| `metadata()` mutators (`setRuntimeVersion`, global variables) | **before `start()` only** — there is no later channel to the server; calling after start throws |
| `features()` (add/remove, container mode) | **before `start()` only** — announced in the initial project message; throws after start (container mode already throws today) |
| `replay()` `setHandler` | **before `start()` only** — toggles the `REPLAY` feature, which is announced at start |
| `processes()` models, layouter, diagram factory | **open in both phases** — models added after start must be announced explicitly via `announce(model)`; models present at start are announced automatically |
| `processes()` images, tree-element types | **before `start()` only, for now** — conceptually process-level, but additional project messages do not currently transport images, so a late image would silently never reach the server; the guard makes that explicit (see Deferred) |
| `processes().send()` / `announce(m)` | **require a started instance** (`sendAdditionalProcess` already throws today; `sendProjectMessage` gains this guard in the new API only — `start()` invokes the send internally after the instance is marked started) |
| `jobs()` `add` | **requires a started instance** (as today); `remove`/`get` unguarded |
| `serializers()`, `argos()`, `commands()`, `configuration()` | no phase rules — not part of the project message |

Guard violations throw `NjamsSdkRuntimeException` with a uniform message naming the
facet, the operation, and the required phase. Read methods are never guarded.

**Strictness applies to the new API only.** Deprecated old methods keep their current
lenient behavior: where the new API would throw (e.g. `addGlobalVariables` after
start), the old method logs a WARN and proceeds as before. Deprecation must not
introduce new exceptions into working client code. Old methods that already throw
(`setContainerMode`, `addJob`) keep throwing.

## Internal structure

- Each facet class lives in `com.im.njams.sdk` (package-private constructor) and holds
  its own state — the corresponding fields move out of `Njams` (e.g. the jobs map into
  `NjamsJobs`, the serializer maps into `NjamsSerializers`, tree elements and images
  into `NjamsProcesses`).
- The `Njams` constructor wires facets in dependency order. Cross-facet needs are
  passed explicitly (constructor parameters), not looked up via `Njams`, e.g.:
  - `NjamsReplay` ↔ `NjamsJobs`: replay markers set by replay handling, consumed by
    `jobs().add(...)` — exposed as package-private methods on `NjamsJobs`.
  - `NjamsProcesses` needs the metadata snapshot and the internal communication holder
    to assemble and send project messages.
  - `NjamsCommands` dispatches the built-in commands to `processes()` (re-send),
    `replay()` (replay), and `metadata()`/`features()` (ping response).
- Internal helpers that today reach into `Njams` (`LogMessageFlushTask`,
  `CleanTracepointsTask`, `ConfigurationInstructionListener`,
  `NjamsProcessDiagramFactory`) keep working against the public accessors or
  package-private facet methods; no new public surface is added for them.
- The synchronization currently piggybacking on the `processModels` map (project
  message resources) moves into `NjamsProcesses` as an explicit lock object. Lock
  granularity and semantics stay as today; no new synchronization is introduced.

## Migration plan

0. **Establish full baseline test coverage before any refactoring.** Every public
   `Njams` method that will be migrated must be covered by tests that pin its current
   observable behavior — including current phase behavior (what throws today, what is
   silently accepted today) and edge cases (`null` arguments, duplicate registration,
   unknown lookups). Gaps in the existing suite are closed with new tests against the
   *unmodified* class. Refactoring starts only when this baseline suite is green.
1. **Extract facets, delegate internally.** Move state and logic into the facet
   classes; every existing `Njams` method body becomes a one-line delegation. No public
   API change yet. The full baseline suite must pass unchanged — this proves the
   delegation is behavior-preserving.
2. **Expose accessors and new API.** Add the nine accessors and the new method names
   (with phase guards and full Javadoc), plus per-facet unit tests including guard
   tests.
3. **Deprecate old methods.** Add `@Deprecated` plus a `@deprecated` Javadoc tag on
   every migrated `Njams` method. Each deprecation comment must do more than point at
   the new member: it explains the replacement *usage* — the accessor chain and call,
   e.g. "Use {@code njams.jobs().add(job)} via {@link #jobs()} and
   {@link NjamsJobs#add(Job)} instead", including any contract difference the caller
   should know (e.g. the new method throws after {@code start()} where this method
   only logs a warning).

Steps 2 and 3 may land in one commit; steps 0 and 1 each stand alone and are
verifiable independently.

## Testing

- **Baseline first:** before any code is touched, the existing suite is audited for
  coverage of every `Njams` public method that will be migrated, and missing coverage
  is added against the unmodified class (migration plan step 0).
- Existing tests are not modified; they keep exercising the deprecated API and thereby
  pin the delegation behavior.
- **Behavioral parity:** every new facet method is covered by tests asserting the
  *same observable behavior* that the baseline suite pins for the corresponding
  deprecated method — same return values, same exceptions, same edge-case handling
  (null arguments, duplicates, unknown lookups, copy/unmodifiable semantics). The
  practical mechanism is mirroring: each baseline test relevant to a facet is repeated
  against the new API with identical assertions. The only permitted deviations are the
  contract changes this design makes *by intent* — the phase guards (strict throw in
  the new API vs lenient WARN in the deprecated method) and the unified
  `getClientSessionId()`. Every intentional deviation is covered by an explicit pair
  of tests (new-API-throws + deprecated-stays-lenient) so the divergence itself is
  pinned.
- New per-facet unit tests cover the new method names, the phase guards (throw after
  start where contracted; lenient WARN path of the corresponding deprecated method),
  and `processes().announce(...)`.
- Checkstyle and Javadoc builds must pass (`mvn validate -Pcheckstyle -pl njams-sdk`,
  `mvn javadoc:javadoc -pl njams-sdk`) — every new public member is documented.

## Documentation

- `wiki/FAQ.md` examples that call migrated methods (e.g. custom
  `ProcessModelLayouter` registration) are updated to the new facet API once the
  implementation lands. No settings are added or changed.

## Deferred / out of scope

- **Typestate / builder API** (compile-time phase enforcement): deferred. The facet
  cut is builder-compatible; a later `Njams.builder()` could reuse the design-time
  facets.
- **Images in additional project messages:** conceptually, images belonging to a
  lazily added process should travel with `announce(model)`; the wire handling does
  not support that today, hence the temporary pre-start guard on images. Lifting it is
  a separate ticket (requires server-side alignment).
- **Facet-style refactoring of `Job`/`ProcessModel`:** same pattern could apply, but
  is a separate ticket.
- **Removal of the deprecated `Njams` methods:** after the deprecation period, a
  separate (then breaking) ticket.
