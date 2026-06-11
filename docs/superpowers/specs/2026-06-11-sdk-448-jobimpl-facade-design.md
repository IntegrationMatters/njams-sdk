# SDK-448: JobImpl Facade Refactoring — Design

**Ticket:** [SDK-448](https://salesfive.atlassian.net/browse/SDK-448) — Refactor JobImpl class
**Reference:** SDK-359 design (`2026-06-10-sdk-359-njams-facade-design.md`) and plan
(`2026-06-10-sdk-359-njams-facade-refactoring.md`) — this design applies the same pattern
and constraints.
**Date:** 2026-06-11
**Status:** Draft for review

## Goal

`JobImpl` (~1480 lines) mixes activity management, log-message flushing, truncation,
payload limiting, lifecycle/status handling, error commit logic, attributes, internal
properties, and business metadata. It shall become a facade following the SDK-359
pattern: the public API is regrouped into focused *facets* (`job.activities().add(a)`
instead of `job.addActivity(a)`), and the internal engine mechanics move into
package-private collaborators with **no** public facet. Every existing public method
stays in place, deprecated, delegating to the new implementation.

## Constraints (inherited from SDK-359, plus one)

- **No breaking change.** All existing `public` members of `Job` and `JobImpl` keep
  their signatures and observable behavior. New API is additive; the ticket carries no
  `breaking-change` label.
- **Baseline coverage before refactoring.** Every public `Job`/`JobImpl` method to be
  migrated is pinned by tests against the *unmodified* class — including lenient
  behaviors (e.g. `setStatus` before `start()` only warns) — verified complete via a
  JaCoCo method-level check. Tests are frozen afterwards.
- **Behavioral parity.** Every new facet method is covered by `_viaFacet` mirror tests
  with identical assertions; intentional contract deviations only as explicit,
  documented test pairs.
- **Deprecation comments explain replacement usage.** Each `@deprecated` tag names the
  accessor chain and target method (e.g. "Use {@code job.activities().add(activity)}
  via {@link Job#activities()} and {@link JobActivities#add(Activity)}") plus any
  contract difference.
- **Hot path (stricter than SDK-359).** `JobImpl` is instantiated for every process
  execution. The split must not add meaningful per-job allocation or CPU cost. See
  "Performance budget" below.

## Lessons carried over from SDK-359 execution

Two findings from the SDK-359 implementation become upfront constraints here:

1. **Frozen tests mock the legacy surface.** Tests mock/spy `Job`, `JobImpl`, and
   `Njams` with Mockito and stub legacy getters. Facet accessors return `null` on such
   mocks. Therefore: internal SDK callers (`ActivityImpl`, `GroupImpl`, the builders,
   `LogMessageFlushTask`, extraction/tracing code) **keep calling the legacy
   `JobImpl` surface**; only *new* code and samples use the facet API. Migration of
   internal callers happens together with the eventual removal of the deprecated
   methods.
2. **Spy identity matters.** Where a facet creates objects that hold a back-reference
   (in SDK-359: `ProcessModel` → owner `Njams`), the facade method must thread `this`
   through an owner-aware package-private variant so Mockito spies stay the owner.
   In this refactoring the analogous spot is the **builders**: `createActivity(...)`
   constructs `ActivityBuilder(this, model)` — the facet must accept the owning
   `JobImpl` from the facade call.

## What `JobImpl` owns today (seams)

| Area | Members today |
|---|---|
| Activities | `createActivity`/`createGroup`/`createSubProcess` (builders), `addActivity`, `getActivityByInstanceId`, `getActivityByModelId`, `getRunningActivityByModelId`, `getCompletedActivityByModelId`, `getStartActivity`, `getActivities`, `getNextSequence`, `removeNotRunningActivities`, `flushedActivities`, sequence counter |
| Lifecycle / status | `start`, `end(boolean)`, `end()` (already deprecated), `setStatus`, `getStatus`, `getMaxSeverity`, `isFinished`, `hasStarted`, `startTime`/`endTime` (+ setters), `setStatusAndSeverity` |
| Attributes | `addAttribute`, `getAttribute`, `getAttributes`, `hasAttribute`, `flushedAttributes` interplay |
| Descriptive metadata | `jobId`, `logId`, `correlationLogId`, `parentLogId`, `externalLogId`, `businessService` (×2 setters), `businessObject` (×2 setters), `businessStart`/`businessEnd` |
| Internal properties | `getProperty`, `hasProperty`, `setProperty`, `removeProperty` |
| Tracing flags | `setDeepTrace`/`isDeepTrace`, `isTraces`/`setTraces`, `setInstrumented`, `needsData`, `isActiveTracepoint`, `getActivityConfiguration` |
| Flushing | `flush`, `timerFlush`, `createLogMessage`, `addToLogMessageAndCleanup`, `shouldFlush`, `mustBeSuppressed` + log-mode checks, `flushCounter`, `getLastFlush`, `getEstimatedSize`, `addToEstimatedSize`, `calculateEstimatedSize`, `addPluginDataItem` |
| Truncation | `checkTruncating`, `truncateLimit`, `truncateOnSuccess`, `isTruncatingActivities`/`Events`, `activityIds` |
| Payload limiting | `initPayloadLimit`, `getSerializeSizeHint`, `limitPayload`, `limitLength` (static), `PAYLOAD_*` constants |
| Config-derived state | `initFromConfiguration`, `logMode`, `logLevel`, `exclude`, `recording`, `isRecording` |
| Error handling | `setActivityErrorEvent`, `commitActivityError`, `updateActivityErrorEvent`, `errorLock`/`errorActivity`/`errorEvent`, `allErrors` |

## New public API shape

Facet accessors are added to the **`Job` interface** (no-prefix style, as on `Njams`).
Adding methods to `Job` is treated as additive per project convention: `Job` instances
are produced exclusively by `ProcessModel.createJob()`; external implementations of
`Job` are not a supported use case. *(Decision to confirm in review.)*

`Job`/`JobImpl` keep: `start()`, `end(boolean)`, `setStatus`, `getStatus`,
`getMaxSeverity`, `isFinished`, `getStartTime`/`setStartTime`, `getEndTime`/`setEndTime`,
`getJobId`, `getLogId`, `needsData(ActivityModel)`, and `toString` — the lifecycle is
the core of the facade, exactly like `start()`/`stop()` on `Njams`.

| Accessor | Facet type | Owns |
|---|---|---|
| `job.activities()` | `JobActivities` | builders (`create(ActivityModel)`, `createGroup(GroupModel)`, `createSubProcess(SubProcessActivityModel)`), `add(Activity)`, `getByInstanceId`, `getByModelId`, `getRunningByModelId`, `getCompletedByModelId`, `getStart()`, `getAll()`; internal: sequence counter, flushed-activity bookkeeping, `removeNotRunning` |
| `job.attributes()` | `JobAttributes` | `add(key, value)`, `get(name)`, `getAll()`, `has(name)`; internal: flushed-attributes interplay with the flusher |
| `job.metadata()` | `JobMetadata` | `correlationLogId`, `parentLogId`, `externalLogId`, `businessService` (String/Path), `businessObject` (String/Path), `businessStart`, `businessEnd` (get/set each); read-only `getJobId()`, `getLogId()`. **Setters are chainable** (return the facet) — the same single-facet chainability decision as `NjamsMetadata` in SDK-359; all other facets stay non-chainable. |
| `job.properties()` | `JobProperties` | `get(key)`, `has(key)`, `set(key, value)`, `remove(key)` |
| `job.tracing()` | `JobTracing` | `setDeepTrace`/`isDeepTrace`, `isTraces`; internal: `setTraces`, `setInstrumented`, tracepoint/extract checks (`isActiveTracepoint`, `getActivityConfiguration` become internal — see mapping) |

Internal only — package-private collaborators in `com.im.njams.sdk.logmessage`, no
public facet (the SDK-359 "communication layer" analogy — these are engine mechanics
that must stay invisible to SDK users):

- **`JobFlusher`**: `flush`, `timerFlush`, log-message assembly
  (`createLogMessage`, `addToLogMessageAndCleanup`, `shouldFlush`), suppression checks
  (`mustBeSuppressed` and the four mode checks), flush counter, `lastFlush`,
  estimated-size accounting, plugin data items.
- **`JobTruncation`**: the `checkTruncating` state machine with its limits and maps.
- **`JobErrorHandling`**: `setActivityErrorEvent` storage, `commitActivityError`,
  `updateActivityErrorEvent`, `errorLock` state.
- **`JobRuntimeConfig`**: the per-job snapshot read in `initFromConfiguration`
  (`logMode`, `logLevel`, `exclude`, `recording`) plus `needsData` support.
- **`PayloadLimits`** (shared, see Performance): `limitPayload`,
  `getSerializeSizeHint`, truncate/discard mode, `limitLength`.

`JobImpl`-only public methods that are de-facto internal (`flush`, `timerFlush`,
`setActivityErrorEvent`, `setInstrumented`, `setTraces`, `getLastFlush`,
`getEstimatedSize`, `addToEstimatedSize`, `isActiveTracepoint`,
`getActivityConfiguration`, `isRecording`, `hasStarted`, `getNjams`,
`limitLength`) remain public-but-deprecated delegates on `JobImpl`, with deprecation
comments stating they are SDK-internal mechanics (no public replacement) — mirroring
the `Njams.getSender()` treatment. `hasStarted` is genuinely useful and gets a public
replacement on the facade: it is referenced by `Job` Javadoc semantics already;
proposal: keep `hasStarted()` undeprecated on `JobImpl` and add it to `Job`
*(to confirm in review)*.

### Old API mapping (excerpt; all migrated members follow the pattern)

| Deprecated on `Job`/`JobImpl` | Replacement |
|---|---|
| `createActivity(m)` / `createGroup(m)` / `createSubProcess(m)` | `activities().create(m)` / `.createGroup(m)` / `.createSubProcess(m)` |
| `addActivity(a)` | `activities().add(a)` — same contract: throws if the job has not been started, or on a second start activity |
| `getActivityByInstanceId(id)` / `getActivityByModelId(id)` / `getRunningActivityByModelId(id)` / `getCompletedActivityByModelId(id)` | `activities().getByInstanceId(id)` / `.getByModelId(id)` / `.getRunningByModelId(id)` / `.getCompletedByModelId(id)` |
| `getStartActivity()` / `getActivities()` | `activities().getStart()` / `.getAll()` |
| `addAttribute(k, v)` / `getAttribute(n)` / `getAttributes()` / `hasAttribute(n)` | `attributes().add(k, v)` / `.get(n)` / `.getAll()` / `.has(n)` |
| `setCorrelationLogId(s)` / `getCorrelationLogId()` (and parent/external log id, business service/object, business start/end) | `metadata().setCorrelationLogId(s)` (chainable) / `.getCorrelationLogId()` etc. |
| `getProperty(k)` / `hasProperty(k)` / `setProperty(k, v)` / `removeProperty(k)` | `properties().get(k)` / `.has(k)` / `.set(k, v)` / `.remove(k)` |
| `setDeepTrace(b)` / `isDeepTrace()` / `isTraces()` | `tracing().setDeepTrace(b)` / `.isDeepTrace()` / `.isTraces()` |
| `end()` (already deprecated) | unchanged, keeps pointing at `end(boolean)` |
| `addPluginDataItem(i)` | stays on the facade undeprecated (message content contributed by plugins, analogous to lifecycle) *(to confirm in review)* |
| `flush()`, `timerFlush(...)`, `setActivityErrorEvent(...)`, `setInstrumented()`, `setTraces(...)`, `getLastFlush()`, `getEstimatedSize()`, `addToEstimatedSize(...)`, `isActiveTracepoint(...)`, `getActivityConfiguration(...)`, `isRecording()`, `getNjams()`, `limitLength(...)` | deprecated as SDK-internal, no public replacement (internal collaborators take over) |

### Facet visibility

Same as SDK-359: facets are `public final` classes with package-private constructors
in `com.im.njams.sdk.logmessage`, obtainable only via the accessors on a `Job`. The
internal collaborators are package-private classes.

## Phase contracts

The job state machine is `CREATED` → (`start()`) → `RUNNING` → (`end(boolean)`) →
finished. Today's enforcement is inconsistent: `addActivity` throws before start,
`setStatus` before start only warns, `end` twice throws, attribute/metadata mutation
after `end` is silently accepted but only flushed if another flush happens. The
SDK-359 rule applies: **strictness only in the new API, pinned lenient behavior in the
deprecated one** — but conservatively:

| Facet | Phase contract (new API) |
|---|---|
| `activities().add` | requires a started job (as today — same exception) |
| `activities()` builders, getters | unguarded (as today) |
| `attributes().add` | unguarded (as today — attributes flushed with the next log message) |
| `metadata()` mutators | unguarded before `end`; **after `end(boolean)` the new API throws** (`requireNotFinished`), because a change after the final flush is never sent — the deprecated setters keep silently accepting (WARN log) |
| `properties()`, `tracing()` | unguarded (internal-use data, no wire impact) |

The only *new* strictness is the `metadata()`-after-end guard; it is the analog of the
"never reaches the server" rule from SDK-359 and gets the explicit
`new*ThrowsAfterEnd` / `deprecated*StaysLenientAfterEnd` test pairs.
*(To confirm in review; dropping it makes the whole refactoring contract-neutral.)*

## Performance budget (hot path)

`JobImpl` construction today already allocates ~10 containers (activity map, two
attribute maps, plugin list, properties map, truncation map, flushed-activities set,
two atomic counters) plus per-job settings parsing (`getBool`/`getInt`/`getProperty`
×6 for error/truncate/payload settings). Design rules:

- Facets and collaborators are tiny final objects created once in the `JobImpl`
  constructor; accessors are field reads; deprecated methods are single delegating
  calls (JIT-inlined). Expected addition: ~5 small allocations per job — bounded and
  measurable against the existing ~10 containers.
- **Net win — shared settings snapshot:** `allErrors`, `truncateOnSuccess`,
  `truncateLimit`, and the payload-limit entry are constants per `Njams` instance but
  are parsed from settings *for every job*. They move into one immutable
  `PayloadLimits`/job-settings snapshot created once per `Njams` (held by `NjamsJobs`)
  and shared by all jobs — removing per-job string parsing and the
  `AbstractMap.SimpleImmutableEntry` allocation.
- Lock structure unchanged: the `activities` monitor (guarding activities + truncation
  state) becomes one explicit lock object shared between `JobActivities`,
  `JobFlusher`, and `JobTruncation`; `attributes` and `errorLock` monitors stay as
  they are. No new synchronization.
- No reflection, no streams added in per-activity paths; moved bodies stay literal.

## Internal structure and wiring

- `JobImpl` constructor creates collaborators in dependency order; cross-facet needs
  are passed explicitly (e.g. `JobFlusher` gets the activities lock, the attributes
  maps, `JobTruncation`, and the sender access via the owning `Njams`).
- `LogMessageFlushTask` keeps calling `jobImpl.timerFlush(...)`/`flush()` (deprecated
  delegates) — frozen tests and the task itself rely on that surface.
- `ActivityImpl`/builders keep calling the legacy `JobImpl` methods
  (`getNextSequence`, `limitPayload`, `getSerializeSizeHint`, `addToEstimatedSize`,
  `setActivityErrorEvent`) — package-private access can later be re-pointed to the
  collaborators in the removal ticket.

## Migration plan (same staging as SDK-359)

0. **Baseline coverage** of every public `Job`/`JobImpl` method against the unmodified
   class (`JobFacadeBaselineTest`), incl. lenient behaviors; JaCoCo method check;
   frozen afterwards.
1. **Extract collaborators with pure delegation** — one commit per collaborator/facet
   (suggested order: `PayloadLimits` → `JobRuntimeConfig` → `JobErrorHandling` →
   `JobTruncation` → `JobAttributes` → `JobProperties` → `JobMetadata` →
   `JobActivities` → `JobFlusher`), full suite green after each.
2. **Expose accessors + new API** on `Job`, with guards, Javadoc, parity mirrors and
   guard-pair tests.
3. **Deprecate** the legacy methods with usage-explaining comments; migrate sample
   clients; wiki examples; quality gates (checkstyle, Javadoc, full build).

Commit convention: `SDK-448 <description>` per commit; `#comment` only on the final
finalizing commit.

## Testing

- Baseline-first, behavioral parity via `_viaFacet` mirrors, deviation test pairs, and
  a completeness check — identical methodology to the SDK-359 plan, reusing
  `JobImplTest`/`AbstractTest` infrastructure where it already pins behavior.
- A micro-benchmark is *not* required; instead the review checks the allocation budget
  statically (count of new objects per job construction).

## Deferred / out of scope

- Internal-caller migration to the facet API (blocked by frozen mock-based tests; done
  with the deprecated-API removal ticket).
- Lazy initialization of rarely used per-job maps (`properties`, truncation map) — a
  separate, behavior-neutral performance ticket if desired.
- The same facet treatment for `ActivityImpl`/`GroupImpl` — separate ticket if wanted.
- Removal of deprecated members — separate (breaking) ticket after the deprecation
  period, shared with SDK-359's removal.
- **End state: `JobImpl` disappears behind the `Job` interface.** The interface is the
  intended API boundary, but it leaks today: `JobImpl` is `public` with a public
  constructor, `LogMessageFlushTask` casts `((JobImpl) job)` to reach `flush()`/
  `timerFlush()`, and `ExtractHandler.handleExtract(JobImpl, ...)` is public taking
  the implementation type. This refactoring deprecates the leaked mechanics; the
  removal ticket then completes the seal: make `JobImpl` package-private (clients only
  ever receive `Job` from `ProcessModel.createJob`), change `ExtractHandler` to the
  internal collaborator types, and drop the casts in `LogMessageFlushTask` in favor of
  package-internal access to the flusher. Each of these is breaking for code that
  references `JobImpl` directly and therefore belongs to the post-deprecation ticket.

## Open points for review

1. ~~Facet accessors land on the `Job` interface~~ — **confirmed.** Analysis (2026-06-11):
   `Job` is the actual public surface (`createJob` returns it, all samples and
   `ReplayHandler` are typed against it, exactly one implementation exists), and it
   hides ~15 engine-mechanics methods that are public on `JobImpl` only. The interface
   stays, receives the accessors, and the refactoring works toward sealing it (see the
   end-state item under Deferred).
2. `metadata()`-after-`end` strictness (the only intentional contract change) — confirm
   or drop.
3. `hasStarted()` and `addPluginDataItem(...)` remain undeprecated on the facade —
   confirm.
4. Naming: `tracing()` vs folding deep-trace into the facade; `JobMetadata` vs
   `JobInfo`.
