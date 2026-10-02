# SDK-482 — Remove code deprecated in 6.0.0 and earlier (design)

**Status (2026-10-02):** sections A, B (including the flush narrowing and helpers), D, F, G and H are implemented; C stays as kept. Section E is delivered by Task 11 (Jackson-typed `JsonSerializerFactory` methods behind `_internal()`). The `ActivityBuilder` strict started-job check (internally created builders) was restored in the final fix round; only facet-created builders may build before start. The FAQ section "Breaking changes in 6.1" is delivered; the `breaking-change` label is handled separately.

Ticket: SDK-482 (fix version 6.1.0). Source of decisions: the decision document reviewed on 2026-10-01
(candidate tables A–H) plus the answers recorded below. Interpretation of "remove": delete if possible,
otherwise reduce visibility. Version suffixes (RC vs. final) are ignored: everything with `since <= 6.0.0` counts.

## 1. Scope

| Section | Content | Outcome |
|---|---|---|
| A | `Njams` legacy facade members, `implements InstructionListener`, `onInstruction` | Delete (after internal callers migrated) |
| B | `Job`/`JobImpl`/`JobActivities` legacy members, `Job.end()` | Delete; `Job.setStatus`/`JobImpl.setStatus` stay (keep mark) |
| B (flush) | `JobImpl.flush()`/`timerFlush()` | Narrow to package-private; `LogMessageFlushTask`/`LMFTEntry` move into `logmessage` |
| B (helpers) | Remaining `JobImpl` helpers | Delete `setInstrumented` (public facet replacement since SDK-448), `setTraces`, `getLastFlush`; narrow `getNjams`, `limitLength`, `setActivityErrorEvent`, `addToEstimatedSize`, `getEstimatedSize`, `isActiveTracepoint`, `getActivityConfiguration`, `isRecording` to package-private. `JobTracing` methods stay public |
| C | `ProcessConfiguration.exclude` | Keep |
| D | `JsonSerializerFactory.addLocalDateTimeSerializer`, `AbstractReplayHandler` 2-arg overloads | Delete; 3-arg replay methods become abstract |
| E | 9 `JsonSerializerFactory` forRemoval=false methods | **Delivered by Task 11** (`docs/superpowers/plans/2026-10-02-sdk-482-json-serializer-factory-internal.md`): the four SDK-used Jackson-typed methods moved behind `JsonSerializerFactory._internal()`; `createDefaultMapper` and the 3-arg `addSerializer` are private; the 2-arg `addSerializer`, `createDefaultWriter` and `propertiesToJsonString` are deleted |
| F | Settings-provider layer (`Settings`, `SettingsProvider`, `SettingsProviderFactory`, 4 providers, SPI services file, `Transformer.decode(Properties)`) | Delete, including the five provider keys in `NjamsSettings` (they become no-ops) |
| G | Legacy `common.Path`, `Path.of(common.Path)`, `Path.toLegacyPath()`, legacy-`Path` overloads on `Configuration`/`ProcessFilter` | Delete (SDK-205 provided the new-`Path` overloads; merged 2026-10-01) |
| H | `ArgosCollector.collect()`, `ShareableReceiver.onInstruction(Instruction, Njams)`, `DataMasking.addPatterns(Properties)` | Delete. `SimpleProcessModelLayouter` and `Configuration.dataMasking` stay (keep marks; `dataMasking` is persisted) |
| Excluded | Deprecated client settings (`NjamsSettings`) | Stay while they work. Rule: a setting whose code is removed and that becomes a no-op is removed too — this applies to the five settings-provider keys only. `PROPERTY_SERVER_COMPATIBILITY` stays |

## 2. Decisions

1. `getSender()`: removed from `Njams`. The sender is stored per client in the existing `LogMessageFlushTask` and
   `CleanTracepointsTask` registries (`start(njams, sender)`); `JobFlusher` reads it through a package-private
   lookup (`LogMessageFlushTask.senderOf`) after the task moved into `logmessage`. `NjamsModel` (same package)
   uses a package-private `Njams.sender()`. A job ending after `stop()` is dropped with a warning.
   `NJAMS_INSTANCES` becomes a `ConcurrentHashMap`. No new member on any client-visible type.
2. Flush: `LogMessageFlushTask`/`LMFTEntry` move to `com.im.njams.sdk.logmessage`; `JobImpl.flush()`/`timerFlush()`
   become package-private; `JobFlusher.timerFlush` is replaced by `isFlushDue` so `JobImpl.timerFlush` calls `flush()`
   directly (no `JobFlusher` → `owner.flush()` → `JobFlusher` round trip). Their deprecation goes away with the visibility reduction; `message-sending-control.md`
   and the Javadoc/FAQ text are updated.
3. `Job.end()` is removed; `end(boolean)` and `discard()` remain.
4. `AbstractReplayHandler.executeReplay(Path,String,String)` and `testReplay(Path,String,String)` become abstract
   (compile-time break instead of silently ignored overrides).
5. Instruction handling: `Njams` registers a stored dispatcher object (not `this`) with `NjamsCommands`; it is
   removed from the same places (`releaseStartupRegistrations`, `clear()`). Receivers iterate `commands().list()`.
6. Internal callers in other packages that used lenient legacy job methods (`ReplayHandler.markAsReplayed`:
   `addAttribute`; `NjamsJobs`: `setDeepTrace`) call the throwing facet through `utils.ExceptionSupport.suppressException`
   (debug-level log); the exception is not expected to occur. Within `logmessage`, the package-private `*Internal`/owner-aware variants keep the
   existing semantics.
7. `Njams.equals`: a private final field holds the same `Path` instance as the metadata facet; `equals` compares
   against `other.<field>`.
8. Orphans removed with their last caller: `Njams.warnIfStarted`, `NjamsMetadata.setRuntimeVersionInternal` and
   `NjamsReplay.setHandlerInternal` (inlined).
9. Tests: tests that only cover removed API are deleted; mixed tests are reduced. Deletion happens only after the
   remaining/replacement functionality is covered by equivalent tests; the legacy→facet test mapping is reviewed with
   the user before any deletion. Changes to existing assertions in `NjamsSampleTest`, `FailedStartupCleanupTest`
   and `NjamsJobsTest` are covered by this permission only as stated in the mapping.
10. Docs: `wiki/FAQ.md` gets a new additional section "Breaking changes in 6.1" (placed before "What changed in 6.0", i.e. on top of the FAQ)
    naming, at summary level, the removal of code deprecated in 6.0 or before; it only references the 6.0
    "Deprecations and replacements" table by link. The section "What changed in 6.0" (lines 3-49) is not touched.
    Elsewhere in the FAQ only passages describing or showing removed API are updated. Parent-file chaining gets a
    short `HierarchicalSettings` example. Wiki edits stay in the `wiki/` drafts; no push to the public wiki.
11. The ticket gets the `breaking-change` label.

## 3. Verified facts the design rests on

1. `NjamsCommands.dispatch` (package-private) already implements SEND_PROJECTMESSAGE, PING, REPLAY and
   GET_REQUEST_HANDLER; `Njams.onInstruction` only forwards to it. `Njams.startup()` registers `this` (line ~805)
   and `releaseStartupRegistrations` removes it. `AbstractReceiver:143` and `SharedReceiverSupport:164` iterate the
   deprecated `getInstructionListeners()`.
2. `LogMessageFlushTask` (package `client`) calls `((JobImpl) job).flush()`/`timerFlush(...)`; `Njams` calls its
   static `start`/`stop` (lines ~827, ~976).
3. `Njams.getSender()` callers outside `Njams`: `JobFlusher:128`, `CleanTracepointsTask:146` (other packages),
   `NjamsModel:432,584` (same package), about 12 test files and two comm-it ITs
   (`HttpRepeatedStartStopLeakIT`, `PoolBookkeepingIT`).
4. `SettingsProvider` is a registered SPI type; main code does not use the settings layer outside its package.
5. Since SDK-205 (merged 2026-10-01) main code no longer calls `toLegacyPath()`; remaining `common.Path` users are the legacy overloads, the three legacy `Njams` methods and `Path.of`/`toLegacyPath`, all deleted by this plan.

## 4. Risks

1. Breaking change for clients and SPI implementers (`SettingsProvider`, `AbstractReplayHandler`,
   `ShareableReceiver`) — accepted by decision.
2. The sender is looked up per flush from the task registry; it must stay a lock-free `ConcurrentHashMap` read, and a job ending after `stop()` now drops its message (previously it used the closed sender).
3. Mockito-heavy tests stub the deprecated getters; they need facet mocks.
4. The comm-it module compiles only under the `docker-it` profile; needs its own compile check.

## 5. Out of scope here

Section C (keep). Section E was postponed at first and later delivered by Task 11 (see the table above).
