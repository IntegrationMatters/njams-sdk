# SDK-482 Remove Deprecated Code Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove (delete, or reduce visibility of) all SDK code deprecated in 6.0.0 or earlier, except section C (kept) and section E (postponed, to be brought back before the ticket is resolved).

**Architecture:** First prepare the removal (sender hand-off, instruction dispatcher, flush-task move, caller migration to the existing facets), then delete the legacy members, then delete the settings layer, then update docs. Every deletion is preceded by equivalent test coverage of the replacement.

**Tech Stack:** Java 11, Maven, JUnit 4 + Mockito, checkstyle profile.

**Spec:** `docs/superpowers/specs/2026-10-01-sdk-482-remove-deprecated-code-design.md`

## Global Constraints

1. Every commit that touches `njams-sdk/src/main/**` or `njams-sdk/src/test/**` starts with `SDK-482 <description>`; no `#comment` except on the final commit. Commits end with the Co-Authored-By line from the session attribution.
2. Existing test assertions are changed or deleted only as listed in the approved mapping (Task 1). Tests that only cover removed API are deleted; mixed tests are reduced. Deletion only after equivalent facet tests exist.
3. Out of scope (do not touch): section C (keep), section E `JsonSerializerFactory` (postponed, to be brought back before the ticket is resolved), `Job.setStatus`/`JobImpl.setStatus`, `SimpleProcessModelLayouter`, `Configuration.dataMasking`, `PROPERTY_SERVER_COMPATIBILITY`, and the `JobTracing` methods (`setInstrumented`/`isInstrumented` are public client API since SDK-448 and must stay public). Sections B-helpers and G are IN scope since the 6.0.1-dev merge (Tasks 7 and 7b).
4. No new public/protected member that is visible to clients (see public-api-design.md). Any new public member on an impl class needs the user's confirmation first.
5. Message-sending invariant (message-sending-control.md): `flush()`/`timerFlush()` keep their only callers `LogMessageFlushTask`, `JobFlusher`, `end()`; no new callers.
6. Hot path: no live settings reads, no new allocation per job/activity (runtime-performance-hotpath.md).
7. Build commands: unit tests `mvn test -pl njams-sdk`; Javadoc `mvn javadoc:javadoc -pl njams-sdk`; checkstyle `mvn validate -Pcheckstyle -pl njams-sdk`; comm-it compile `mvn -Pdocker-it test-compile -pl njams-sdk-communication-it`.
8. Wiki edits only in `wiki/` drafts; never push to the public wiki. In `wiki/FAQ.md` the section "What changed in 6.0" (lines 3-49) must not be touched; the new "Breaking changes in 6.1" section is additional and only references the 6.0 table by link. Do not push or merge anything.
9. Each batch (see "Batches") starts only after the user confirms it, with a duration estimate.

## Review Focus

1. A server instruction (PING, REPLAY, SEND_PROJECTMESSAGE, GET_REQUEST_HANDLER) arriving after `Njams` dropped `InstructionListener` is still answered (dispatcher registered); after `stop()` or failed startup nothing stays registered.
2. A job that ends while `LogMessageFlushTask`/`CleanTracepointsTask` run still sends via the sender stored in the task registry; a job that ends after `Njams.stop()` is dropped with a warning instead of using a closed sender; a restart of a stopped instance does not reuse a closed sender (SDK-375 commit `8a88d8c5`); two `Njams` instances never share a sender entry.
3. `ReplayHandler.markAsReplayed` / `NjamsJobs.add` on an already finished job must not throw out of the instruction/job-add path.
4. A client `AbstractReplayHandler` subclass that only overrides the removed 2-arg methods fails to compile (abstract 3-arg methods) rather than silently doing nothing.
5. Users still setting `njams.sdk.settings.*` keys: startup must not fail because of unknown keys.

---

## Post-merge notes (origin/6.0.1-dev merged into SDK-375 on 2026-10-01; folded in by user decision)

1. **SDK-448 (`6ce91f84`):** `JobTracing.setInstrumented()/isInstrumented()` are now public client API (documented: clients must call it for events the SDK cannot see). `JobImpl.setInstrumented()` is deprecated with that replacement, and `ActivityImpl`/`ExtractHandler` already call the facet. Folded into Task 7 (Step 0); never narrow the `JobTracing` methods; reduce the legacy parts of `JobInstrumentedTest` (lines ~79, ~113).
2. **SDK-205 (`cc2eb815`):** new-`Path` overloads exist on `Configuration`/`ProcessFilter`; the legacy-`Path` overloads are `forRemoval`. Main code no longer calls `toLegacyPath()`; remaining `common.Path` users are the legacy overloads, the three `Njams` legacy methods (section A) and `Path.of(common.Path)`/`toLegacyPath()`. Section G is therefore a pure deletion plus test reduction (`PathTest`, `ConfigurationPathOverloadsTest`, `ProcessFilterTest`, `ExtractHandlerTest`, `JobImplTest`, `NjamsTest`). Folded in as Task 7b (user confirmed). Task 4's old note is obsolete; `ProcessModel` now calls `hasProcessExcludeFilter(path)` with the new `Path`.
3. **`179f8113`:** `SimpleProcessModelLayouter` deprecation reformatted only. It is marked KEEP in the decision document (the user confirmed the keep mark is intended), so it is NOT removed by this plan.
4. **Second merge (`f0d7704a`, SDK-205 follow-up):** the `CleanTracepointsTask`/`LogMessageFlushTask` registries are now keyed by `Path` (Task 2 adjusted), `ProcessModel`/`JobRuntimeConfig` call `getProcess(Path)` with the `Path` object, and `LogMessageFlushTaskTest` gained tests that Task 2 must keep green. Line numbers in Task 4 are approximate; take exact sites from the `-Xlint:deprecation,removal` output.
5. **Merge fix-up (committed as `a75698b9`):** `JobInstrumentedTest.CountingSender` (from 6.0.1-dev) lacked the abstract `AbstractSender.isCongestion`/`isMessageRejected` added on SDK-375; both were added (returning false).

---

## Batches (each needs separate user confirmation, estimate = wall clock with agents)

1. **Batch 1 — Preparation (Tasks 1–3):** about 2 h.
2. **Batch 2 — Caller migration and deletion of A/B (Tasks 4–7b):** about 5–6 h.
3. **Batch 3 — D/H, settings layer, docs, verification (Tasks 8–10):** about 3 h.
4. docker-it resilience suite (Task 10, step 6): about 6 min, only after separate confirmation.

---

### Task 1: Baseline and test mapping (gate)

**Files:**
- Create: `docs/superpowers/plans/2026-10-01-sdk-482-test-mapping.md` (working document, deleted with the plan)

**Interfaces:**
- Produces: the approved list "legacy test → replacement test / delete / reduce" used by Tasks 6–9.

- [ ] **Step 1:** Run `mvn test -pl njams-sdk`, record pass/fail counts as the baseline (expect all green; if not, stop and report).
- [ ] **Step 2:** For every test class using removed API (compile with `-Xlint:deprecation,removal` to list them; known: `NjamsFacadeBaselineTest`, `JobFacadeBaselineTest`, `NjamsTest`, `NjamsSampleTest`, `NjamsFacetApiTest`, `JobFacetApiTest`, `JobImplTest`, `JobActivitiesTest`, `NjamsJobsTest`, `PathTest` (G, later), `FailedStartupCleanupTest`, `AbstractReceiverTest`, `SharedReceiverSupportTest`, `ConfigurationInstructionListenerTest`, `CleanTracepointsTaskTest`, `JobRuntimeConfigTest`, `DataMaskingTest`, `AbstractReplayHandlerTest`, settings tests), write a table row per test method group: removed member → facet replacement → existing facet test or new test to write → action (delete / reduce / retarget).
- [ ] **Step 3:** Mark rows where a replacement test is missing; those become "write test first" steps in Tasks 4–8.
- [ ] **Step 4:** Present the mapping to the user. **Stop; continue only after approval.**

### Task 2: Move the flush task and hand the sender over via the task registries

**Files:**
- Move (git mv, update package + imports): `client/LogMessageFlushTask.java` → `logmessage/LogMessageFlushTask.java`, `client/LMFTEntry.java` → `logmessage/LMFTEntry.java`; update `Njams.java` import (~35) and calls (~827, ~976); move `LogMessageFlushTaskTest` accordingly
- Modify: `Njams.java` (replace public `getSender()` by package-private `NjamsSender sender()` — same lazy logic, lines ~636-650, callers at ~662, ~804; `startup()` passes `activeSender` to both tasks), `NjamsModel.java:432,584` (use `sender()`), `logmessage/LogMessageFlushTask.java` (registry + sender lookup), `client/CleanTracepointsTask.java:54-150`, `logmessage/JobFlusher.java:128`
- Modify (tests): `client/LogMessageFlushTaskTest` (moves with the class; `LogMessageFlushTask.start(njams)` calls become `start(njams, sender)`; keep its three tests' assertions: single registration on repeated start, per-instance stop flush, stop without start), and the test files using `njams.getSender()` listed by `grep -rl "getSender()" njams-sdk/src/test njams-sdk-communication-it/src/test`
- Create (comm-it test sources): `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/SenderProbe.java` — split-package accessor for `Njams.sender()` used by `HttpRepeatedStartStopLeakIT` and `PoolBookkeepingIT` (verify the split package works with the shaded/unsealed artifact)

**Interfaces:**
- Produces: `LogMessageFlushTask.start(Njams njams, NjamsSender sender)`, package-private `static NjamsSender LogMessageFlushTask.senderOf(Njams njams)` (null if no entry), `CleanTracepointsTask.start(Njams njams, NjamsSender sender)`. No new member on any client-visible type (`Njams`, `ProcessModel`, `NjamsJobs`, `Job`).

> **Design (decided with the user):** the sender is stored per client in the existing task registries (both are already keyed per client and started/stopped by `Njams`). `JobFlusher` (same package after the move) looks it up with the package-private `senderOf`. `NJAMS_INSTANCES` becomes a `ConcurrentHashMap` so a flush-time lookup does not take the class lock. A job that ends after `Njams.stop()` finds no entry: the message is dropped with a warning (stop() already flushed all jobs). `LogMessageFlushTask.stop` flushes the instance's jobs using the sender of the entry it removed.

- [ ] **Step 1: Failing tests.** `JobImplTest`/new `JobFlusherSenderTest`: a flushed job's `LogMessage` reaches the sender registered via `LogMessageFlushTask.start(njams, senderMock)`, with `clientSessionId` from metadata; a job ended after `LogMessageFlushTask.stop(njams)` sends nothing and logs a warning; `stop(njams)` flushes the instance's jobs to the sender of the removed entry; two `Njams` instances use their own senders. `CleanTracepointsTaskTest`: trace message goes to the sender passed to `start(njams, sender)`.
- [ ] **Step 2:** Run those tests; expected FAIL (compile error: new signatures).
- [ ] **Step 3:** Move the two classes; change `NJAMS_INSTANCES` to `ConcurrentHashMap`; store the sender in `LMFTEntry`; add `senderOf`; in `stop` take the sender from the removed entry for the final flush. `CleanTracepointsTask` stores a private holder `(Njams, NjamsSender)` per instance key; since SDK-205 both registries are keyed by the `Path` object `njams.metadata().getClientPath()` (not its string form) — keep that, and `senderOf` looks up by that `Path`. `Njams.startup()` passes `activeSender` to both `start` calls.
- [ ] **Step 4:** `JobFlusher.flush` sends via `LogMessageFlushTask.senderOf(njams)`; null → `LOG.warn` and drop (the instance is stopped; `stop()` already flushed all jobs).
- [ ] **Step 5:** Make `Njams.sender()` package-private; `NjamsModel` uses it; delete public `getSender()`.
- [ ] **Step 6:** Add `SenderProbe` in comm-it; migrate the two ITs; run `mvn -Pdocker-it test-compile -pl njams-sdk-communication-it`.
- [ ] **Step 7:** Migrate the ~12 test files; `NjamsSampleTest` flush calls come from package `client` and need the `JobFlushAccess` helper from Task 5 — if still blocked, leave those calls for Task 5 and note it in the commit. Run `mvn test -pl njams-sdk`; all green.
- [ ] **Step 8:** Commit `SDK-482 Hand the sender to the flush and trace tasks instead of exposing Njams.getSender()`.

### Task 3: Instruction dispatcher

**Files:**
- Modify: `Njams.java` (startup ~805, `releaseStartupRegistrations` ~855, class signature, `onInstruction` ~1283), `AbstractReceiver.java:143`, `SharedReceiverSupport.java:164`, `NjamsCommands.java`
- Modify (tests, as approved in Task 1): `FailedStartupCleanupTest:58`, dispatch tests using `njams.onInstruction(...)` (retarget to `njams.commands().dispatch(...)`), `AbstractReceiverTest`, `SharedReceiverSupportTest` (stub `commands()` instead of `getInstructionListeners()`)

**Interfaces:**
- Consumes: `NjamsCommands.dispatch(Instruction)` (package-private).
- Produces: field `private final InstructionListener commandDispatcher = commands::dispatch` in `Njams`, registered/unregistered by identity.

- [ ] **Step 1: Failing test.** In `NjamsTest` (package `sdk`): after `start`, `njams.commands().list()` contains a listener that answers a PING instruction (`resultCode 0`, message `Pong`); after failed startup (existing `FailedStartupCleanupTest` scenario) `list()` is empty; after `stop()` empty.
- [ ] **Step 2:** Run; expected FAIL (list contains `njams` itself, not a dispatcher).
- [ ] **Step 3: Implement.**
```java
// Njams: replaces "implements InstructionListener" and onInstruction
private final InstructionListener commandDispatcher = instruction -> commands.dispatch(instruction);
// startup(): commands.add(commandDispatcher); commands.add(configurationListener);
// releaseStartupRegistrations(): commands.remove(commandDispatcher); commands.remove(configurationListener);
```
`commands` is assigned in the constructor, so initialize `commandDispatcher` in the constructor after `commands`. Receivers: `njams.commands().list()` instead of `njams.getInstructionListeners()` (note: `list()` copies; acceptable per message, not per job).
- [ ] **Step 4:** Remove `implements InstructionListener` and `onInstruction` from `Njams` in Task 6 (keep deprecated until all tests are retargeted); this task only introduces the dispatcher and migrates callers/tests.
- [ ] **Step 5:** `mvn test -pl njams-sdk`; green. Commit `SDK-482 Register a dedicated instruction dispatcher instead of Njams itself`.

### Task 4: Migrate internal callers of the `Njams` legacy getters (A)

**Files (all `njams-sdk/src/main/java/com/im/njams/sdk/`):**
- `communication/CommunicationFactory.java:131`, `communication/AbstractReceiver.java:501,503`, `communication/http/HttpSseReceiver.java:225,239,241,245,247`, `communication/jms/JmsReceiver.java:159,179,474,476,485`, `communication/SharedReceiverSupport.java:70,72,86,87,132,148,164,204`, `communication/kafka/KafkaReceiver.java:228,241,251,253`
- `client/CleanTracepointsTask.java:76,84,115,118,140,146`, `client/LogMessageFlushTask.java:66,75,89,92,95,133`, `client/TraceMessageBuilder.java:60-63`
- `logmessage/JobFlusher.java:129,200,207,216-218`, `logmessage/JobRuntimeConfig.java:64,84,125`, `logmessage/JobImpl.java:403,430`
- `model/ProcessModel.java:101,104,107,124,126,427`, `model/svg/NjamsProcessDiagramFactory.java:226`
- `configuration/ConfigurationInstructionListener.java:312,674`
- sample-app: `NjamsStartup.java:70`, `LogMessageResource.java:49`

**Interfaces:**
- Consumes: facets — `metadata()` (`getClientPath`, `getCategory`, `getClientVersion`, `getSdkVersion`, `getRuntimeVersion`, `getMachine`, `getClientSessionId`, `getCommunicationSessionId`), `jobs()` (`getAll`, `add`, `remove`, `get`), `model()` (`getProcessModels`, `getDiagramFactory`, layouter), `configuration()` (`get`), `commands()`.
- Main code no longer calls `toLegacyPath()` since SDK-205; legacy-`Path` overloads are removed in Task 7b.

- [ ] **Step 1:** For each file, replace `njams.getX()` by the facet call listed in the `@deprecated` Javadoc of `getX` (the Javadoc names the exact replacement). Verify each replacement with `grep` on the deprecated method's Javadoc before editing.
- [ ] **Step 2:** `ProcessModel:101-107`: `njams.configuration().get()`; behavior for mocked `Njams` in tests handled in Step 4.
- [ ] **Step 3:** Compile with `-Xlint:deprecation,removal`; expected: no remaining main-code warnings for section A members.
- [ ] **Step 4:** Migrate Mockito stubs in the listed test classes to facet mocks per the approved mapping (`CleanTracepointsTaskTest`, `AbstractReceiverTest`, `CommunicationFactoryTest`, `JmsReceiverMock`, `SharedJmsReceiverTest`, `SharedReceiverSelectionSpecTest`, `SharedReceiverSupportTest`, `ConfigurationInstructionListenerTest`, `DataMaskingTest`, `JobRuntimeConfigTest`, `TruncatingTest`, `ExtractHandlerTest`, `ActivityImplExtractDataTest`, `JobImplTest`).
- [ ] **Step 5:** `mvn test -pl njams-sdk`; green. Commit `SDK-482 Use the facets instead of the deprecated Njams getters internally`.

### Task 5: Migrate internal callers of the legacy `Job` API (B) and narrow flush

**Files:**
- Modify: `utils/ExceptionSupport.java` (user-added utility, untracked: add the copyright header, Javadoc on the class and on `ThrowingSupplier`, `@since 6.1.0`, fix the `LOG` field indentation; it must stay public because `logmessage`, `communication` and `sdk` use it), test `utils/ExceptionSupportTest.java`
- Modify: `logmessage/GroupImpl.java:69,142,166`, `logmessage/ActivityImpl.java:156,210,364,374,387,396,412,741` (use `job.activities().getByModelId`, `job.tracing()`, package-private `addInternal`), `logmessage/ExtractHandler.java:395-407` (use package-private `*Internal` metadata setters), `logmessage/JobErrorHandling.java:87,89` (use owner-aware `add(activity, owner, true)` and `activities().getByInstanceId`), `communication/ReplayHandler.java:46`, `NjamsJobs.java:70,124`
- Modify: `logmessage/JobImpl.java:328,344` (`timerFlush`, `flush` → package-private, drop `@Deprecated`/`@deprecated` text, Javadoc "SDK-internal"), `logmessage/JobFlusher.java:88-103` (replace `timerFlush(owner, ...)` by `boolean isFlushDue(JobImpl owner, LocalDateTime sentBefore, long flushSize)`, same condition, no call to `owner.flush()`; fix the class Javadoc sentence about `owner.flush()`)
- Modify (tests): `NjamsSampleTest` (≈27 `((JobImpl) job).flush()` from package `client`) → test helper `JobFlushAccess` in package `com.im.njams.sdk.logmessage` (src/test), `JobImplTest`

**Interfaces:**
- Consumes: Task 2 (flush task already in `logmessage`).
- Produces: `JobImpl.flush()`/`timerFlush(LocalDateTime,long)` package-private; `JobImpl.timerFlush` = `if (flusher.isFlushDue(this, sentBefore, flushSize)) { LOG.debug("Flush by timer: {}", this); flush(); }` (spies still observe `flush()`); `JobFlusher.flush(JobImpl)` unchanged.

- [ ] **Step 1: Failing/guard tests.** `JobImplTest`: `flush()`/`timerFlush` still send once (existing tests, moved if needed). New test for `ReplayHandler.markAsReplayed` on a finished job: no exception propagates (plain `assertDoesNotThrow` pattern consistent with surrounding tests; the suppression is logged at debug level only). New test for `NjamsJobs.add` replay-marker path with finished job: same.
- [ ] **Step 2: Implement the cross-package callers (Decision 6) with `ExceptionSupport`.**
```java
// ReplayHandler.markAsReplayed (communication package)
ExceptionSupport.suppressException(() -> job.attributes().add(NJAMS_REPLAYED_ATTRIBUTE, "true"));
// NjamsJobs (lines 70, 124): ExceptionSupport.suppressException(() -> job.tracing().setDeepTrace(true));
```
`ExceptionSupport` logs suppressed exceptions at debug level only; the exception is not expected to occur, so debug is accepted (user can request a warn-level variant). Before this step: write `ExceptionSupportTest` (runnable swallows `RuntimeException`; callable returns null on exception and the value otherwise; `optionalOf` returns empty on exception, present otherwise, empty for a null value), finalize the file as listed under Files, and check that a lambda whose body returns a value resolves to the `Callable` overload without an ambiguity warning (the void-returning `add`/`setDeepTrace` calls resolve to `Runnable`).
- [ ] **Step 3:** Migrate the `logmessage` callers to package-private variants/facets (exact replacement per the `@deprecated` Javadoc of each called method).
- [ ] **Step 4:** Narrow `flush`/`timerFlush` and introduce `isFlushDue` (existing `JobImplTest` timer tests at lines ~625, ~703, ~729 stay unchanged and must still pass); update `message-sending-control.md` lines 21 and 37 (remove the `@deprecated`-text claim, state the methods are package-private SDK-internal), `Job` and `AbstractSender` Javadoc references.
- [ ] **Step 5:** Compile with `-Xlint:deprecation,removal`; expected: no remaining main-code uses of section B deprecated members except `setStatus` (the former deferred helpers are handled in Task 7).
- [ ] **Step 6:** `mvn test -pl njams-sdk`; green. Commit `SDK-482 Migrate job callers to the facets and keep flush SDK-internal`.

### Task 6: Delete the `Njams` legacy members (A)

**Files:**
- Modify: `Njams.java` (all section A members from `CURRENT_YEAR` to `isExcluded`, `implements InstructionListener`, `onInstruction`, 5-arg constructor, `warnIfStarted`; add `private final Path clientPath` initialized in the constructor from the same `Path` instance as `metadata.getClientPath()`; `equals`/`hashCode` use it)
- Modify: `NjamsMetadata.java` (inline `setRuntimeVersionInternal`), `NjamsReplay.java` (inline `setHandlerInternal`), `communication/ShareableReceiver.java:65` (Javadoc link)
- Delete/reduce tests per approved mapping: delete `NjamsFacadeBaselineTest`; reduce `NjamsTest`, `NjamsFacetApiTest` (3 legacy-lenient tests deleted), `NjamsSampleTest`
- Modify: sample modules (`njams-sdk-sample-client`, `njams-sdk-sample-app`) compile check

**Interfaces:**
- Consumes: Tasks 2–5 (no remaining callers).

- [ ] **Step 1: Failing test.** `NjamsTest`: two `Njams` with equal client paths are equal and have equal hash codes; an instance compared with a Mockito mock of `Njams` returns false without NPE.
```java
// Njams
private final Path clientPath; // same instance as metadata.getClientPath(), kept for equals/hashCode
...
public int hashCode() { return 83 * 5 + Objects.hashCode(clientPath); }
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    return Objects.equals(clientPath, ((Njams) obj).clientPath);
}
```
(Mockito mocks of `Njams` have a different class, so the `getClass()` check already returns false for them; the test pins that.)
- [ ] **Step 2:** Run; confirm it fails/passes as expected before changes, then delete members listed above. Remove imports that become unused (`Njams.java`, `Job` etc.).
- [ ] **Step 3:** Fix `{@link}` references that pointed at removed members (`mvn javadoc:javadoc -pl njams-sdk` must have no errors).
- [ ] **Step 4:** Compile whole reactor `mvn clean install -DskipTests` + comm-it `test-compile`; fix sample modules.
- [ ] **Step 5:** `mvn test -pl njams-sdk`; green. Commit `SDK-482 Remove deprecated Njams facade members`.

### Task 7: Delete the legacy `Job`/`JobImpl`/`JobActivities` members (B)

**Files:**
- Modify: `logmessage/Job.java` (all annotated members incl. `end()`; keep `setStatus`), `logmessage/JobImpl.java` (overrides of those, plus the former deferred helpers as listed below; keep `setStatus`), `logmessage/JobActivities.java:236,249` (`getRunningByModelId`, `getCompletedByModelId`; check `findLastByModelId` still used) and the `{@link}`s at lines 88,101,115,129,234,247
- Delete/reduce tests per mapping: `JobFacadeBaselineTest` (mostly deleted), `JobImplTest` (reduce), `JobActivitiesTest` (delete tests for removed methods), `JobFacetApiTest` (delete legacy-lenient tests), `NjamsJobsTest` (stub `tracing()` instead of `setDeepTrace`), `AbstractTest` (replace `end()` by `end(boolean)`/facet usage), `ActivityBuilderTest`, `ActivityImplTest`, `StartDataLimitTest`, others from `-Xlint` output
- Modify: samples — replace `job.end()` by `job.end(true)` and `getActivityByModelId` by `job.activities().getByModelId`: `GroupClient`, `SettingsFromFileClient`, `SimpleEndlessClient`, `SubProcessClient`, `SubProcessSpawnedClient`, sample-app `LogMessageResource:49,57`; comm-it `JmsClientEndToEndBaselineIT:74-75`

**Interfaces:**
- Consumes: Task 5.

- [ ] **Step 0 (former deferred helpers; proposal derived from the 6.0.1-dev merge, user to confirm at plan review):**
  1. Delete `JobImpl.setInstrumented()` (replacement `tracing().setInstrumented()` is public client API since SDK-448; callers already migrated) and reduce the legacy parts of `JobInstrumentedTest` (lines ~79, ~113).
  2. Delete `JobImpl.setTraces(boolean)` (the traces flag is SDK-maintained; `ActivityImpl:364` calls the package-private `tracing.setTraces`) and `JobImpl.getLastFlush()` (no main callers; one test use in `JobFacadeBaselineTest`).
  3. Narrow to package-private (SDK-internal, no client function, all main callers in `logmessage`): `getNjams`, `limitLength` (static), `setActivityErrorEvent`, `addToEstimatedSize`, `getEstimatedSize`, `isActiveTracepoint`, `getActivityConfiguration`, `isRecording`; drop their `@Deprecated`/`@deprecated`. Compile tests: any use from another package (e.g. `NjamsSampleTest`) must go through a helper in package `logmessage`.
- [ ] **Step 1:** Delete the members; remove unused imports; keep javadoc of kept members accurate (job-thread-safety.md: update `Job`/`Activity`/`Group` Javadoc only if text referred to removed API).
- [ ] **Step 2:** Compile reactor + comm-it test-compile; fix leftovers.
- [ ] **Step 3:** Check `end()` semantics wording in Javadoc of `end(boolean)`/`discard()` does not reference the removed `end()`.
- [ ] **Step 4:** `mvn test -pl njams-sdk`; `mvn javadoc:javadoc -pl njams-sdk`; green/no errors. Commit `SDK-482 Remove deprecated Job API`.

### Task 7b: Delete the legacy `Path` API (G)

**Files:**
- Modify: `configuration/Configuration.java:203,224,304,328,353` (legacy-`Path` overloads of `getProcess`, `hasProcess`, `isProcessExcluded`, `hasProcessExcludeFilter`, `setProcessExcluded`), `configuration/ProcessFilter.java:202,263,309` (`isSelected`, `setExcluded`, `hasExcludeFilter`), `Path.java:153,612` (`Path.of(common.Path)`, `toLegacyPath()`), `configuration/ProcessConfiguration.java` and `Path.java` Javadoc `{@link}`s pointing at legacy signatures (`ProcessConfiguration:45,66,75`; `Path:70,137-148,601-608`)
- Delete: `common/Path.java`, `PathTest.java`
- Reduce tests per mapping: `ConfigurationPathOverloadsTest` (drop legacy-overload cases, keep new-`Path` cases), `ProcessFilterTest:46`, `ExtractHandlerTest:85,142`, `JobImplTest:159,165`, `NjamsTest:435-446` (already handled in Task 6 if they used the removed `Njams` methods)

**Interfaces:**
- Consumes: Task 6 (the three `Njams` methods taking `common.Path` are gone).

- [ ] **Step 1:** Run `grep -rn "common.Path\|toLegacyPath" njams-sdk/src njams-sdk-sample-* njams-sdk-communication-it` and confirm only the items above remain.
- [ ] **Step 2:** Make sure each removed legacy overload has an equivalent new-`Path` test in `ConfigurationPathOverloadsTest` before deleting (mapping from Task 1); delete the members and `common.Path`.
- [ ] **Step 3:** `mvn clean install -DskipTests`, comm-it `test-compile`, `mvn test -pl njams-sdk`, `mvn javadoc:javadoc -pl njams-sdk`; green. Commit `SDK-482 Remove the legacy Path API`.

### Task 8: Delete small leftovers (D, H)

**Files:**
- Modify: `communication/AbstractReplayHandler.java:165,182` (remove 2-arg `executeReplay`/`testReplay`; 3-arg `executeReplay(Path,String,String)` and `testReplay(Path,String,String)` become `abstract`; update class Javadoc lines 36-37; remove the `resolveName` delegation if it becomes unused), `common/JsonSerializerFactory.java:243` (`addLocalDateTimeSerializer`), `argos/ArgosCollector.java:75` (`collect()`; confirm `ArgosSender` uses `collectAll`), `communication/ShareableReceiver.java:58` (`onInstruction(Instruction, Njams)`), `logmessage/DataMasking.java:141` (`addPatterns(Properties)`)
- Tests: `AbstractReplayHandlerTest:52,62` (test handler overrides 3-arg), `DataMaskingTest:155` (delete the properties-overload test; keep the `ClientSettings` one)

- [ ] **Step 1: Failing test.** `AbstractReplayHandlerTest`: a handler implementing the 3-arg methods is invoked through `NjamsReplay` request handling (existing behavior preserved).
- [ ] **Step 2:** Implement deletions/abstract change; fix compile errors in all modules (samples, comm-it, tests).
- [ ] **Step 3:** `mvn test -pl njams-sdk`; green. Commit `SDK-482 Remove deprecated replay overloads and small leftovers`.

### Task 9: Delete the settings-provider layer (F)

**Files:**
- Delete: `settings/Settings.java`, `settings/SettingsProvider.java`, `settings/SettingsProviderFactory.java`, `settings/provider/{File,Memory,PropertiesFile,SystemProperties}SettingsProvider.java`, `src/main/resources/META-INF/services/com.im.njams.sdk.settings.SettingsProvider`, `Transformer.decode(Properties)` (`settings/encoding/Transformer.java:342`)
- Modify: `NjamsSettings.java:309-346` (remove the five provider keys), `configuration/provider/FileConfigurationProvider.java:78` (fix comment typo)
- Delete tests: `SettingsTest`, `FileSettingsProviderTest`, `PropertiesFileSettingsProviderTest`
- Migrate (setup only): `AbstractTest.java:78` (take `ClientSettings`), `TestSender.getSettings()` (return `ClientSettings`), `LifecycleTestTransport.settings()`, `TestReceiver`, `SenderPoolTestAccess`, ≈30 test classes using `new Settings()` / `Settings x = TestSender.getSettings()` (`new Settings()` → `ClientSettings.from(new Properties())`; tests calling `getAllProperties()` pass the `ClientSettings` directly); sample-client (9 files incl. rewriting `SettingsFromFileClient` to `Properties.load` + `ClientSettings.from`), sample-app `NjamsStartup`, comm-it (23 files)
- Docs: `njams-sdk-sample-client/src/main/resources/settings_full.properties:43-53` (remove provider section), `wiki/FAQ.md` outside the "What changed in 6.0" section (settings provider migration and key table, lines ~151-189; invoke `njams-settings-sync` skill)

**Interfaces:**
- Consumes: `ClientSettings.from(Map|Properties)`, `ClientSettings.fromSystemProperties(filter)`, `HierarchicalSettings`.

- [ ] **Step 1:** Invoke `njams-settings-sync` before touching `NjamsSettings`.
- [ ] **Step 2:** Migrate test bases first (`AbstractTest`, `TestSender`, `LifecycleTestTransport`), then dependent tests, then samples and comm-it; `mvn test -pl njams-sdk` green with the old classes still present.
- [ ] **Step 3:** Delete the layer, keys and tests; remove the SPI services file.
- [ ] **Step 4:** Full compile incl. samples and comm-it `test-compile`; `mvn test -pl njams-sdk`; green.
- [ ] **Step 5:** FAQ (never touch the "What changed in 6.0" section, lines 3-49, incl. its "Breaking changes", "Deprecations and replacements" and "New guarantees" parts): replace the settings-provider migration/key sections further down by a migration note with a `HierarchicalSettings` parent-properties example. Commit `SDK-482 Remove the deprecated settings provider layer`.

### Task 10: Docs, rules, verification

**Files:**
- Modify: `wiki/FAQ.md` (new, additional top-level section "Breaking changes in 6.1" placed before "What changed in 6.0", i.e. on top of the FAQ (after the title): summary only — all code marked deprecated in 6.0 or before has been removed or reduced in visibility; it may only reference the 6.0 "Deprecations and replacements" table by link and must not copy or alter it. The whole "What changed in 6.0" section (lines 3-49) stays byte-for-byte unchanged. Outside that section, update only passages that describe or show removed API: examples at ~61, ~80, ~518, ~545, ~565, ~686, ~952 and the flush paragraph at ~379; confirm the list with `git diff wiki/FAQ.md` showing no hunk in lines 3-49), `wiki/Activity-IDs.md:8`, `CLAUDE.md:76`, `.claude/rules/message-sending-control.md` (done in Task 5, re-check), `docs/superpowers/specs/...-sdk-482-...-design.md` (mark delivered items)
- Not modified: sections C and E (E is brought back to the user before resolution)

- [ ] **Step 1:** Update docs as listed; remove legacy-`Path` mentions covered by Task 7b.
- [ ] **Step 2:** `mvn clean install` (all modules, with tests), `mvn javadoc:javadoc -pl njams-sdk`, `mvn validate -Pcheckstyle -pl njams-sdk`, comm-it `test-compile`; all pass.
- [ ] **Step 3:** `grep -rn "forRemoval = true" njams-sdk/src/main/java` and compare with the decision document: remaining entries must be exactly C and E.
- [ ] **Step 4:** Add `breaking-change` label to SDK-482 (user confirmed).
- [ ] **Step 5:** Report to the user: result, remaining open item: E (postponed — must be brought back before resolution).
- [ ] **Step 6 (separate confirmation):** propose `mvn -Pdocker-it verify -pl njams-sdk-communication-it` (≈6 min, Docker) because the communication layer changed; run only after the user confirms.
