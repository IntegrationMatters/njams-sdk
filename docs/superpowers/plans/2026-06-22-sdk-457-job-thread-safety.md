# SDK-457: Thread-safety contract for Job / Activity / Group — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILLS: use `njams-bug-fix` (reproducer-first) and `njams-safe-modification` (establish coverage before changing existing code). Steps use checkbox (`- [ ]`) syntax for tracking. This plan documents an approach already drafted on `6.0-dev`; the steps double as a verifiable checklist.

**Goal:** Define and document the thread-safety contract of `Job`, `Activity`, and `Group`, and make the genuinely shared job-level state safe for concurrent mutation — without regressing the single-threaded path or its performance.

**Decisions (agreed with the user):**
1. **Concurrency model = contract.** A `Job` is the **shared** object: multiple threads may concurrently create activities/groups in one job and record into their **own** `Activity`/`Group` instances. An individual `Activity`/`Group` instance is **thread-confined** — accessed by one thread only.
2. **Hybrid fix.** Make the shared **job-level** state safe; leave instance-confined operations unsynchronized and **document** them. A single activity instance is never touched by multiple threads, so locking it would add overhead with no benefit.
3. **Reuse the existing job lock.** All shared job state consolidates on the existing `activitiesLock` (which is also `JobFlusher`'s `lock` and the lock `end()` already uses). Intrinsic locks are reentrant, so the `end()` → `flush()` → `getStatus()` chain is safe. Independent flags use `volatile` (no atomicity needed) to keep hot reads lock-free.
4. **No public API change.** Only field modifiers, one private method body, internal-class methods, and Javadoc change. No signature/return/parameter changes.

**Out of scope:** synchronizing per-`Activity`/`Group` instance state (`Group.iterate()`, `ActivityImpl.addPredecessor`, activity data setters' instance-local fields) — these are documented as caller-confined. `JobActivities`, `JobAttributes`, `JobErrorHandling` are already thread-safe and unchanged.

**breaking-change:** No — no public/protected signature, return type, parameter, or observable single-threaded behaviour changes. Confirm the label is absent on the ticket before closing.

**Fix version:** 6.0.0

**Tech stack:** Java 11, JUnit 4, Mockito, `java.util.concurrent` (`CyclicBarrier`), `volatile` / intrinsic locks.

---

## File Map

| Action | File |
|--------|------|
| Add | `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobConcurrencyTest.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobTracing.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobFlusher.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobMetadata.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/Job.java` (interface Javadoc) |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/Activity.java` (interface Javadoc) |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/Group.java` (interface Javadoc) |
| Modify | `wiki/FAQ.md` |

---

## Task 0 — Confirm ticket and establish baseline

**No production changes.**

- [ ] **Step 1: Ticket housekeeping.** Confirm key `SDK-457`. Transition to **In Progress** and assign to the current user. Verify the `breaking-change` label is **absent** (none needed).
- [ ] **Step 2: Establish coverage baseline** (`njams-safe-modification`). Run the full module suite and record the pass/fail state:

```bash
mvn test -pl njams-sdk
```

Expected: `BUILD SUCCESS` (a `FailingJmsFactory$IntentionalError` log line is an intentional test fixture, not a failure). Note the test count for later comparison (~974).

---

## Task 1 — Reproducer test first (TDD / `njams-bug-fix`)

**Files:** Add `JobConcurrencyTest.java`.

### Background
Only **lost-update** races are reliably reproducible in a unit test; pure **visibility** races (a plain `boolean`/reference field) are masked by the happens-before edge that `Thread#join()` establishes, so they cannot be made to fail deterministically. The reliable target is `JobImpl.addToEstimatedSize` (non-atomic `+=`).

- [ ] **Step 1: Write the reproducer.** Many threads (e.g. 8 × 100,000), started together via a `CyclicBarrier`, each calling `job.addToEstimatedSize(1L)`. Assert the final estimate equals `before + threads × increments`. Add a Javadoc note explaining why visibility-only races are not unit-reproducible.
- [ ] **Step 2: Confirm it fails for the right reason.**

```bash
mvn test -Dtest=JobConcurrencyTest -pl njams-sdk
```

Expected: failure showing a large undercount (e.g. expected `801019`, got `~134000`) — the lost-update race, not a setup error.

---

## Task 2 — Make job status safe (`JobImpl`)

**Files:** Modify `JobImpl.java`.

### Background
`setStatusAndSeverity` is a read-modify-write on shared `lastStatus`/`maxSeverity` (it even re-read the shared `lastStatus` field instead of the parameter). Concurrent escalation could lose a higher severity (e.g. ERROR). `end()` already mutates these under `activitiesLock`; align `setStatusAndSeverity` with it.

- [ ] **Step 1:** Make `lastStatus`, `maxSeverity` `volatile` (lock-free reads); make `finished` `volatile` (read without locking by `requireNotFinished`/`getStatus`; a reader observing `finished==true` also observes the final `lastStatus` via the volatile happens-before).
- [ ] **Step 2:** Wrap the body of `setStatusAndSeverity` in `synchronized (activitiesLock)` to make the read-modify-write atomic; compare against the `status` parameter, not the re-read field. Update the field comments to state the lock/volatile rationale.
- [ ] **Step 3:** No change to `getStatus`/`getMaxSeverity`/`hasStarted`/`isFinished` — volatile reads suffice.

---

## Task 3 — Make tracing flags safe (`JobTracing`)

**Files:** Modify `JobTracing.java`.

- [ ] Make `deepTrace`, `instrumented`, `traces` `volatile`. They are independent flags (no cross-field invariant), so `volatile` gives correct visibility with no locking — and keeps the hot `isDeepTrace()` read a plain volatile load. Add a comment stating this.

---

## Task 4 — Make the estimated-size accounting safe (`JobFlusher`)

**Files:** Modify `JobFlusher.java`.

### Background
`estimatedSize` is read/written under `lock` everywhere **except** `addToEstimatedSize` (a bare `+=`). Closing that gap fixes the reproducer.

- [ ] **Step 1:** Wrap `addToEstimatedSize`'s `+=` in `synchronized (lock)`; guard `getEstimatedSize()`'s read on the same `lock`.
- [ ] **Step 2:** In `timerFlush`, read a single locked snapshot (`final long currentSize = getEstimatedSize();`) and use it for both the log line and the size condition, removing the direct unlocked field reads.

---

## Task 5 — Make job metadata safe (`JobMetadata`)

**Files:** Modify `JobMetadata.java`.

- [ ] Make the seven fields (`correlationLogId`, `parentLogId`, `externalLogId`, `businessService`, `businessObject`, `businessStart`, `businessEnd`) `volatile`. Each is independent → `volatile` is sufficient and has zero lock cost. Add a comment stating this.

---

## Task 6 — Verify the fix

**No new production changes.**

- [ ] **Step 1: Reproducer passes.**

```bash
mvn test -Dtest=JobConcurrencyTest,JobImplTest -pl njams-sdk
```

Expected: all green (`JobImplTest` exercises the estimated-size and status assertions).

- [ ] **Step 2: Full suite, no regression.**

```bash
mvn test -pl njams-sdk
```

Expected: `BUILD SUCCESS`, same count as Task 0 baseline, 0 failures/errors.

---

## Task 7 — Document the thread-safety contract

**Files:** Modify `Job.java`, `Activity.java`, `Group.java`, `wiki/FAQ.md`.

### Background
The contract must be visible to SDK users, not just inferable from internals. Per the FAQ-update rule, document new behaviour.

- [ ] **Step 1:** Add a `<b>Thread safety.</b>` paragraph to the `Job` interface Javadoc: the job is the shared unit (concurrent activity/group creation and per-instance recording are safe; the listed job-level side-effect state is synchronized internally); an individual `Activity`/`Group` instance is thread-confined.
- [ ] **Step 2:** Add a matching paragraph to `Activity` (instance thread-confined) and `Group` (same, explicitly including `iterate()` and child-activity creation on the group).
- [ ] **Step 3:** Add a FAQ section ("Can a single job be used from multiple threads", `since 6.0.0`) with the one-activity-instance-per-thread example and the caller-synchronization note.
- [ ] **Step 4: Javadoc + Checkstyle.** Confirm the `{@link #iterate()}` / `{@link #metadata()}` references resolve.

```bash
mvn javadoc:javadoc -pl njams-sdk
mvn validate -Pcheckstyle -pl njams-sdk
```

Expected: `BUILD SUCCESS` for both.

---

## Task 8 — Finalize

- [ ] **Step 1:** Re-confirm the `breaking-change` label is absent (no public API change).
- [ ] **Step 2: Commit** the cohesive fix (code + reproducer + docs) to `6.0-dev`. This is the ticket-finalizing commit, so the `#comment` tag is appropriate:

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ \
        njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobConcurrencyTest.java \
        wiki/FAQ.md
git commit -m "SDK-457 #comment Make job-level state thread-safe; document Job/Activity/Group thread-safety contract"
```

- [ ] **Step 3:** Post the closing comment on the ticket **only when the user resolves it** (root cause + fix + verification), ending with the Claude Code signature. Do not resolve or comment-to-close on your own initiative.

---

## Self-Review Checklist

| Acceptance criterion (from ticket) | Task |
|---|---|
| Thread-safety contract defined and documented | Task 7 (interfaces + FAQ) |
| `GroupImpl.iterate()` — made safe or documented | Documented (caller-confined) — Task 7 |
| `ActivityImpl.addPredecessor` — made safe or documented | Documented (caller-confined) — Task 7 |
| Activity data setters — job-level reach-ins made safe | Tasks 2, 3, 4 (status / instrumented / estimatedSize) |
| `JobImpl` mutators (`setStatus`, `setInstrumented`, `addToEstimatedSize`) | Tasks 2, 3, 4 |
| `JobMetadata` setters | Task 5 |
| No regression to single-threaded path / performance | Task 6 (full suite); `volatile`/uncontended-lock cost is negligible |
| Reproducer proves the bug and guards regression | Task 1 |
| breaking-change label correct (absent) | Tasks 0, 8 |
