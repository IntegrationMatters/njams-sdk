# Facades: `Njams` and `Job`

Tickets: SDK-359, SDK-448, SDK-465, SDK-468, SDK-482

`Njams` and `Job` are facades. Each keeps only its lifecycle on its own surface and groups everything else into
*facets*: small public classes reached through no-prefix accessors (`njams.jobs()`, `job.activities()`). Engine
mechanics that clients must not see live in package-private *collaborators* that have no accessor.

## The pattern

1. **Accessor style.** Facet accessors have no `get` prefix (`model()`, not `getModel()`). This separates them from
   ordinary getters and avoids clashes such as a `getJobs()` getter next to a `jobs()` facet.
2. **Facets are created once and held in `final` fields.** An accessor is a field read; nothing is allocated per
   access. This keeps the runtime path (`jobs().add`, `job.activities()`) as cheap as a direct method.
3. **Facet visibility.** A facet is a `public` class in the facade's package with a **package-private constructor**:
   client code can hold and use one, but can only obtain it from the accessor. Facets are concrete classes, not
   interface + hidden implementation, because no alternative implementation is needed and it keeps the type count
   low. They are deliberately **not `final`**, so client tests can mock them.
4. **Cross-facet needs are passed explicitly** as constructor parameters, in dependency order, never looked up
   through the facade. Collaboration that is not client API goes through package-private methods.
5. **Strict public method, lenient internal variant.** A guarded public mutator has a package-private unguarded
   variant for SDK code in the same package that must legitimately bypass the phase guard — e.g. the replay handler
   toggling `Feature.REPLAY`, `model().send()` adding default images after start, extract handling setting job
   metadata during end processing. SDK code in another package, which cannot reach the variant, calls the public
   facet method and suppresses the exception at debug level; the exception is not expected to occur there.
6. **Owner-aware creation.** Where a facet creates an object holding a back-reference to the facade (process models
   created by `model()`, builders created by `activities()`), the facade passes itself as the owner, so a Mockito
   spy of the facade stays the owner.
7. **Chainable setters only for typical setup blocks**: `NjamsModel.addGlobalVariables`/`setGlobalVariablesPattern`
   and `NjamsMetadata.setRuntimeVersion` on the `Njams` side, the `JobMetadata` setters on the `Job` side.
   Everywhere else mutators return `void` or an informative value (`NjamsSerializers.add/remove` return the
   previous/removed serializer, `NjamsModel.create` returns the model).

## `Njams`

`Njams` itself keeps: the constructor `Njams(Path, String version, String category, ClientSettings)`, `start()`,
`startup()`, `stop()`, `isStarted()`, `getSettings()`, `equals`/`hashCode`, and the nested `Feature` enum. The
constructor takes required parameters only; optional data is set on facets before `start()` (e.g.
`metadata().setRuntimeVersion(...)`).

| Accessor | Facet | Owns |
|---|---|---|
| `metadata()` | `NjamsMetadata` | client identity: client path, category, client/SDK version, runtime version (get/set), machine, client session id; version-file reading and startup banner (internal) |
| `features()` | `NjamsFeatures` | feature list (`list`/`add`/`remove`/`has`), container mode |
| `model()` | `NjamsModel` | everything defining the process picture announced in the project message: process models (`create`/`add`/`get`/`has`/`getAll`), images, tree-element types, global variables and their pattern; layouter and diagram factory; `send()` (full project message); `additionalResources()` (builder for announcing resources after start) |
| `jobs()` | `NjamsJobs` | registry of running jobs (`add`/`remove`/`get`/`getAll`); replay-marker bookkeeping (internal) |
| `serializers()` | `NjamsSerializers` | serializer registry (`add`/`remove`/`get`/`find`), `serialize(t)`, `serialize(t, sizeLimit)` |
| `replay()` | `NjamsReplay` | replay handler (`getHandler`/`setHandler`); replay-instruction handling (internal) |
| `commands()` | `NjamsCommands` | instruction listeners (`list`/`add`/`remove`); dispatch of the built-in commands send-project-message, ping, replay, get-request-handler (internal) |
| `argos()` | `NjamsArgos` | Argos collectors (`add`/`remove`); `ArgosSender` wiring (internal) |
| `configuration()` | `NjamsConfiguration` | `get()` (the `Configuration`), `getLogMode()`, `isExcluded(path)`, `dataMasking()`; provider loading and data-masking initialization (internal) |

**Why `model()` owns global variables.** `model()` holds exactly the client-side content of the `ProjectMessage`
(processes, images, tree elements, global variables + pattern), so it is one cohesive "what is announced" facet,
and `metadata()` is pure client identity, whose only mutable wire content is the runtime version.

**`NjamsModel` collaborators** (package-private in `com.im.njams.sdk`, no accessor):

1. `TaxonomyTree` — the `TreeElement` list and all tree operations (register, set type, mark starters, default
   images in use).
2. `ProjectMessageAssembler` — builds the full and the additional `ProjectMessage` from metadata, features,
   configuration, global variables and the tree.
3. `GlobalVariables` — the variables map, the pattern, and pattern validation (the pattern must declare the named
   groups `full` and `name`).

They exist to keep `NjamsModel` a thin public surface and to confine the wire types `TreeElement` and
`ProjectMessage` to internal code: neither may appear on a `public`/`protected` signature of a facet.

**Kept internal, no facet:** all communication wiring — sender/receiver creation, connecting, receiver startup,
shared-communication handling. `NjamsModel` reaches the sender through package-private access on `Njams`; no
transport, sender or receiver type appears on any facet. `Njams` does not implement `InstructionListener`; it
registers a separate dispatcher with `NjamsCommands` at start and removes it on stop or failed startup.

**Shared state and wiring.**

1. `LifecycleState` (package-private) holds the started flag and the phase guards; one instance per `Njams`, shared
   by all facets that guard.
2. One lock per `Njams` guards all project-message resources — process models, images, global variables, tree
   elements — and is passed into `NjamsModel` and its three collaborators. No other lock protects them.
3. `Njams.equals`/`hashCode` compare the client path held by `Njams` itself (the same `Path` instance as
   `metadata().getClientPath()`), not through the facet.

### `Njams` phase contracts

The project message is sent once, at `start()` (after the instance is marked started), and must already contain
everything; the server may request a resend (`NjamsCommands` → `model().send()`), which carries the then-current
state. Resources added after start reach the server only through `model().additionalResources()`.

| Operation | Phase |
|---|---|
| `metadata().setRuntimeVersion` | before `start()` only |
| `features().add/remove/setContainerMode` | before `start()` only |
| `replay().setHandler` | before `start()` only (it toggles `Feature.REPLAY`, announced at start) |
| `model().add(ProcessModel)` | before `start()` only; after start use `create(...)` + `additionalResources()` |
| `model().addImage`, `setTreeElementType`, `addGlobalVariables`, `setGlobalVariablesPattern` | before `start()` only |
| `model().create`, `setLayouter`, `setDiagramFactory` | both phases |
| `model().additionalResources().build()` | requires a started instance |
| `jobs().add` | requires a started instance; `remove`/`get`/`getAll` unguarded |
| `serializers()`, `argos()`, `commands()`, `configuration()` | no phase rules — not part of the project message |

Guard violations throw `NjamsSdkRuntimeException` whose message names the operation. Read methods are never
guarded. A guard exists because data changed in the wrong phase would silently never reach the server; a new
mutator whose data is announced at start gets the before-start guard.

**Additional resources** (`AdditionalResources`): stages process models, global variables and images and sends
them in one additional project message. By default a resource whose identifier (process path, image name,
global-variable name) was already announced — at start or by an earlier `build()` — is omitted, so a message never
repeats data the server already has, and nothing is sent if nothing new remains. `asReplacement()` sends every
staged resource and replaces the stored copy, so that a process model whose structure changed after start can be
updated. `NjamsModel` tracks the announced identifiers from both the full and the additional sends.

## `Job`

`Job` is the public type (`ProcessModel.createJob*` return it) and the intended API boundary; `JobImpl` is its only
implementation. Facet accessors are declared on the `Job` interface. The facade keeps the lifecycle and status:
`start()`, `end(boolean)`, `discard()`, `hasStarted()`, `isFinished()`, `getStatus()`, `getMaxSeverity()`,
`setStatus` (deprecated), start/end time get/set, `getJobId()`, `getLogId()`, `needsData(ActivityModel)`,
`addPluginDataItem(...)`.

| Accessor | Facet | Owns |
|---|---|---|
| `activities()` | `JobActivities` | builders (`create`, `createGroup`, `createSubProcess`), `add(Activity)`, lookups (`getByInstanceId`, `getByModelId`), `getStart()`, `getAll()`; sequence counter and flushed-activity bookkeeping (internal) |
| `attributes()` | `JobAttributes` | **wire data**, sent with the next log message: `add`, `get`, `getAll`, `has` |
| `metadata()` | `JobMetadata` | correlation/parent/external log id, business service/object (String or `Path`), business start/end — chainable setters; read-only `getJobId()`, `getLogId()` |
| `properties()` | `JobProperties` | **client-local** key/value storage, never sent: `get`, `has`, `set`, `remove` |
| `tracing()` | `JobTracing` | `setDeepTrace`/`isDeepTrace`, `isTraces`, `setInstrumented`/`isInstrumented`; the traces flag is set by the SDK only |

`attributes()` and `properties()` are separate facets on purpose: the boundary makes the wire-data vs. local-state
difference visible in the API. `tracing()` is small but kept so the facade stays lifecycle-only. `job.metadata()`
mirrors `njams.metadata()` in name.

**Collaborators** (package-private in `com.im.njams.sdk.logmessage`, no accessor):

1. `JobFlusher` — flushing and the flush-due decision, log-message assembly, suppression by log mode/level/
   exclusion, flush counter, estimated-size accounting, plugin data items.
2. `JobTruncation` — the truncation state machine.
3. `JobErrorHandling` — captured activity error and its commit at `end`.
4. `JobRuntimeConfig` — per-job snapshot of log mode, log level, exclusion, recording; tracepoint/extract lookups.
5. `JobSettings` — the per-client settings snapshot shared by all jobs of a client (see
   `runtime-performance-hotpath.md`).

When a job is flushed is decided by the SDK alone; see `message-sending-control.md`.

### `Job` phase contracts

Governing invariant: **a job that was never started is never flushed.** Everything else is sequencing freedom.

1. A flush skips (WARN) a job that has not started, and never sends a discarded job; an unstarted job is never due
   for a flush.
2. `end(boolean)` on a never-started job logs an ERROR, sends nothing and does not throw — defensive `end()` calls
   in client `finally` blocks keep working. A second `end` throws.
3. `discard()` finishes the job without sending anything; it is idempotent.
4. `activities().add` and builders created through `activities()` work **before** `start()`; the activities go
   out with the first flush after start. Builders the SDK creates itself (group children, `stepTo`) still require
   a started job. A second start activity throws.
5. `attributes().add` and every `metadata()` setter throw after `end`, because the final log message has already
   been sent. `properties()` and `tracing()` are unguarded (no wire impact once ended).

### Concurrency and hot path

The concurrency contract is `.claude/rules/job-thread-safety.md`; the split into facets and collaborators added no
synchronization — the job's single lock is shared by `JobActivities`, `JobFlusher` and `JobTruncation`.
`JobImpl` is created per process execution, so its facets and collaborators are small objects created once in its
constructor; the allocation and settings-snapshot rules of `runtime-performance-hotpath.md` apply.

## Rejected alternatives

1. **One flat `NjamsModel` without collaborators** — would have moved the god-class smell into one facet and put
   wire types next to public methods.

## Related

1. `.claude/rules/public-api-design.md` — contracts, visibility, relocated types.
2. `.claude/rules/job-thread-safety.md` — `Job`/`Activity`/`Group` concurrency contract.
3. `.claude/rules/message-sending-control.md` — send cadence; why flush stays SDK-internal.
4. `.claude/rules/runtime-performance-hotpath.md` — settings-snapshot invariant behind `JobSettings`.
