# SDK-393: Incorrect Flush-Size Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Because this modifies existing runtime code, also apply superpowers:njams-safe-modification — Task 1 establishes the baseline safety net before any production code changes.

**Goal:** Make the running message-size estimate that drives the flush-by-size decision track reality, so that size-heavy jobs are flushed early instead of growing to multi-megabyte messages that are only shipped on the 30-second interval.

**Architecture:** The flush-by-size check in `JobImpl.timerFlush` (run once per second by `LogMessageFlushTask`) compares a running `estimatedSize` counter against the configured flush size. That counter is incremented only by event-payload, stack-trace, start-data and traced input/output, and is fully recomputed only at flush time. As a result it omits (a) the per-activity base size for every activity added since the last flush, (b) event **message** and event **code** text, and (c) attributes. This plan extends the existing incremental accounting (the same pattern `setEventPayload` already uses) to those three contributions, keeping every update O(1) so the runtime hot path stays cheap. The estimate stays char-length based and is therefore still a slight under-count of the serialized wire bytes; that residual gap is intentionally accepted because newer servers re-derive the authoritative size on receipt (`LogMessageParser`) — the SDK's only job here is to flush *early enough*.

**Tech Stack:** Java 11, JUnit 4, Mockito, existing `AbstractTest` base class.

---

## Background — verified root cause

Confirmed against the code on branch `6.0-dev`:

- `LogMessageFlushTask` schedules `timerFlush` every 1000 ms (`LogMessageFlushTask.java:72`); `timerFlush` flushes when `getEstimatedSize() > flushSize` (`JobImpl.java:480`).
- `JobImpl.addActivity` (`JobImpl.java:333`) puts the activity into the map but **never** bumps the job's running `estimatedSize`. The per-activity base of `700L` (`ActivityImpl.java:82`) therefore enters the job estimate only when `calculateEstimatedSize()` recomputes it — and that runs **only at the end of `flush()`** (`JobImpl.java:515`, `:530`, `:688`).
- `setEventMessage` (`ActivityImpl.java:553`) and `setEventCode` (`ActivityImpl.java:567`) mask and store but do **not** call `addToEstimatedSize`. Only `setEventPayload`, `setStackTrace`, `setStartData` and `handleTracing` (traced input/output) increment the estimate.
- `JobImpl.addAttribute` (`JobImpl.java:1383`) — and `ActivityImpl.addAttribute`, which delegates to it (`ActivityImpl.java:666`) — do not count attribute size.

Net effect: a job that rapidly accumulates many activities (with event messages / codes / attributes but no payloads) keeps `estimatedSize` near its `1000` start value, so the size-based early flush never fires; the job is shipped whole on the 30 s interval → "surprisingly large log messages."

### Key correctness constraint (avoid double counting)

Activity content setters such as `setEventPayload` already call `job.addToEstimatedSize(...)`, and they run **before** `build()` calls `addActivity` (see `ActivityBuilder.build()` at `ActivityBuilder.java:66-75`, and `ActivityBuilder.setEventPayload` at `:220`). Therefore `addActivity` must add **only the fixed base constant**, never the activity's full current `getEstimatedSize()` — otherwise already-counted content would be counted twice.

Additionally, `build()` is also called for **reused** activities (loop iterations: `ActivityBuilder(ActivityImpl)` constructor at `:52`), which calls `addActivity` again on an activity already in the map. The base must be added only on first insertion. The implementation guards on the return value of `Map.put`.

### Consistency note (attributes)

`calculateEstimatedSize()` recomputes `estimatedSize = 1000 + Σ activity.getEstimatedSize()` and does not include job-level attributes. Counting attributes via `addToEstimatedSize` therefore only affects the running counter between flushes — which is exactly where the early-flush decision happens. After a flush the attributes have been sent and the recompute correctly excludes them. This asymmetry is intended.

---

## File Map

| Action | File |
|--------|------|
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java` |
| Modify | `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java` |
| Modify | `wiki/FAQ.md` |

---

## Task 1 — Baseline safety net (invariants that hold before and after the fix)

**Files:**
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java`

### Background

There are currently **no** tests referencing `getEstimatedSize`, `addToEstimatedSize`, `timerFlush`, or the size estimate (verified by grep). Before changing the runtime accounting, lock in the invariants that must remain true regardless of the fix. These tests pass on the current code and must still pass after every later task — they are the regression guard required by `njams-safe-modification`.

- [ ] **Step 1: Add baseline tests to `JobImplTest`**

Add these tests inside `JobImplTest`:

```java
@Test
public void freshJobHasBaseEstimatedSize() {
    JobImpl job = createDefaultStartedJob();
    // A job with no activities starts at the message base of 1000.
    assertEquals(1000L, job.getEstimatedSize());
}

@Test
public void eventPayloadIncreasesEstimatedSize() {
    JobImpl job = createDefaultStartedJob();
    ActivityImpl act = (ActivityImpl) job.createActivity(getDefaultActivityModelForTest()).build();
    long before = job.getEstimatedSize();
    act.setEventPayload("0123456789"); // 10 chars
    assertTrue("event payload must increase the estimate",
            job.getEstimatedSize() >= before + 10);
}

@Test
public void timerFlushDoesNotFlushBeforeSizeOrIntervalReached() {
    JobImpl job = spy(createDefaultStartedJob());
    job.createActivity(getDefaultActivityModelForTest()).build();
    // last flush just happened; size well below limit -> no flush
    job.timerFlush(DateTimeUtility.now().minusSeconds(1), 5_000_000L);
    Mockito.verify(job, Mockito.never()).flush();
}
```

- [ ] **Step 2: Add the shared test helper used above**

`getDefaultActivityModel()` in `AbstractTest` is `private`. Add a small package-local helper to `JobImplTest` so each test can obtain distinct activity models when needed:

```java
private ActivityModel getDefaultActivityModelForTest() {
    ActivityModel model = process.getActivity("baseSizeAct");
    if (model == null) {
        model = process.createActivity("baseSizeAct", "BaseSizeAct", null);
    }
    return model;
}
```

- [ ] **Step 3: Run the baseline tests — expect pass on current code**

```bash
mvn test -Dtest=JobImplTest#freshJobHasBaseEstimatedSize+eventPayloadIncreasesEstimatedSize+timerFlushDoesNotFlushBeforeSizeOrIntervalReached -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all three PASS.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java
git commit -m "SDK-393 #comment Add baseline tests for job size estimate and timer flush"
```

---

## Task 2 — Count the per-activity base in the running estimate

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java`

### Background

This is the highest-impact change. `addActivity` must add the fixed base size (currently the literal `700L`) to the job's running estimate on first insertion only, so that activity-heavy jobs grow the counter as activities are added rather than only at flush. The literal is promoted to a named constant `ActivityImpl.BASE_ESTIMATED_SIZE` (package-private, same package as `JobImpl`).

- [ ] **Step 1: Write the failing test in `JobImplTest`**

```java
@Test
public void addingActivitiesGrowsRunningEstimateByBase() {
    JobImpl job = createDefaultStartedJob();
    long before = job.getEstimatedSize(); // 1000
    ActivityModel m1 = process.createActivity("growA1", "GrowA1", null);
    ActivityModel m2 = process.createActivity("growA2", "GrowA2", null);
    job.createActivity(m1).build();
    job.createActivity(m2).build();
    // Each plain activity must contribute its base size to the running estimate
    // *before* any flush recompute.
    assertEquals(before + 2 * ActivityImpl.BASE_ESTIMATED_SIZE, job.getEstimatedSize());
}
```

- [ ] **Step 2: Run the test — expect failure**

```bash
mvn test -Dtest=JobImplTest#addingActivitiesGrowsRunningEstimateByBase -pl njams-sdk
```

Expected: COMPILATION ERROR (`BASE_ESTIMATED_SIZE` not defined) then, after Step 3 partially, assertion failure on current `addActivity`.

- [ ] **Step 3: Introduce the base constant in `ActivityImpl`**

In `ActivityImpl.java`, replace the field initialization at line 82:

```java
    private long estimatedSize = 700L;
```

with:

```java
    // Base size estimate (in characters) attributed to every activity regardless of content.
    static final long BASE_ESTIMATED_SIZE = 700L;

    private long estimatedSize = BASE_ESTIMATED_SIZE;
```

- [ ] **Step 4: Count the base on first insertion in `JobImpl.addActivity`**

In `JobImpl.java`, change the `activities.put(...)` line in `addActivity` (line 339) from:

```java
            activities.put(activity.getInstanceId(), activity);
```

to:

```java
            final Activity previous = activities.put(activity.getInstanceId(), activity);
            if (previous == null) {
                // Count the per-activity base size in the running estimate as soon as the activity
                // is added, so flush-by-size reflects activity-heavy jobs between flushes. Content
                // (payload, stack trace, etc.) is already counted by the activity's own setters, so
                // only the fixed base is added here to avoid double counting. Reused activities
                // (loop iterations) re-enter addActivity with previous != null and must not re-add it.
                addToEstimatedSize(ActivityImpl.BASE_ESTIMATED_SIZE);
            }
```

- [ ] **Step 5: Run the new test and the Task 1 baseline — expect all pass**

```bash
mvn test -Dtest=JobImplTest#addingActivitiesGrowsRunningEstimateByBase+freshJobHasBaseEstimatedSize+eventPayloadIncreasesEstimatedSize+timerFlushDoesNotFlushBeforeSizeOrIntervalReached -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all PASS.

- [ ] **Step 6: Run the full `JobImplTest` and `ActivityImplTest` (if present) — expect no regressions**

```bash
mvn test -Dtest=JobImplTest -pl njams-sdk
```

Expected: `BUILD SUCCESS`. If any pre-existing test fails, the fix is wrong — do not modify existing tests; revisit the implementation.

- [ ] **Step 7: Run checkstyle**

```bash
mvn validate -Pcheckstyle -pl njams-sdk
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java
git add njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java
git add njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java
git commit -m "SDK-393 #comment Count per-activity base size in the running flush-size estimate"
```

---

## Task 3 — Count event message and event code

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java`

### Background

`setEventMessage` and `setEventCode` are the two content fields that were never counted (each capped at `MAX_VALUE_LIMIT = 2000` chars, so up to ~2 kB per activity each). Mirror the existing `setEventPayload` pattern: add the masked-and-limited length to both the activity and the job estimate.

- [ ] **Step 1: Write the failing tests in `JobImplTest`**

```java
@Test
public void eventMessageIncreasesEstimatedSize() {
    JobImpl job = createDefaultStartedJob();
    ActivityImpl act = (ActivityImpl) job.createActivity(
            process.createActivity("evtMsgAct", "EvtMsgAct", null)).build();
    long before = job.getEstimatedSize();
    act.setEventMessage("0123456789"); // 10 chars
    assertEquals(before + 10, job.getEstimatedSize());
}

@Test
public void eventCodeIncreasesEstimatedSize() {
    JobImpl job = createDefaultStartedJob();
    ActivityImpl act = (ActivityImpl) job.createActivity(
            process.createActivity("evtCodeAct", "EvtCodeAct", null)).build();
    long before = job.getEstimatedSize();
    act.setEventCode("ABCDE"); // 5 chars
    assertEquals(before + 5, job.getEstimatedSize());
}
```

> Note: these tests assume no data-masking patterns are registered (the `JobImplTest.cleanUp()` `@After` clears them) and no payload limit is configured, so the stored length equals the input length.

- [ ] **Step 2: Run the tests — expect failure**

```bash
mvn test -Dtest=JobImplTest#eventMessageIncreasesEstimatedSize+eventCodeIncreasesEstimatedSize -pl njams-sdk
```

Expected: assertion failures (`before + 10` vs `before`).

- [ ] **Step 3: Update `setEventMessage` in `ActivityImpl`**

Replace the method body at `ActivityImpl.java:553`:

```java
    @Override
    public void setEventMessage(String message) {
        setExecutionIfNotSet();
        super.setEventMessage(DataMasking.maskString(limitLength("eventMessage", message, MAX_VALUE_LIMIT)));
        if (StringUtils.isNotBlank(message)) {
            job.setInstrumented();
        }
    }
```

with:

```java
    @Override
    public void setEventMessage(String message) {
        setExecutionIfNotSet();
        final String limited = DataMasking.maskString(limitLength("eventMessage", message, MAX_VALUE_LIMIT));
        super.setEventMessage(limited);
        if (StringUtils.isNotBlank(message)) {
            final int size = limited == null ? 0 : limited.length();
            addToEstimatedSize(size);
            job.addToEstimatedSize(size);
            job.setInstrumented();
        }
    }
```

- [ ] **Step 4: Update `setEventCode` in `ActivityImpl`**

Replace the method body at `ActivityImpl.java:567`:

```java
    @Override
    public void setEventCode(String code) {
        setExecutionIfNotSet();
        super.setEventCode(DataMasking.maskString(limitLength("eventCode", code, MAX_VALUE_LIMIT)));
        if (StringUtils.isNotBlank(code)) {
            job.setInstrumented();
        }
    }
```

with:

```java
    @Override
    public void setEventCode(String code) {
        setExecutionIfNotSet();
        final String limited = DataMasking.maskString(limitLength("eventCode", code, MAX_VALUE_LIMIT));
        super.setEventCode(limited);
        if (StringUtils.isNotBlank(code)) {
            final int size = limited == null ? 0 : limited.length();
            addToEstimatedSize(size);
            job.addToEstimatedSize(size);
            job.setInstrumented();
        }
    }
```

- [ ] **Step 5: Run the new tests plus Task 1/2 tests — expect all pass**

```bash
mvn test -Dtest=JobImplTest -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all PASS (no pre-existing test regressions).

- [ ] **Step 6: Run checkstyle**

```bash
mvn validate -Pcheckstyle -pl njams-sdk
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java
git add njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java
git commit -m "SDK-393 #comment Count event message and event code in the flush-size estimate"
```

---

## Task 4 — Count attributes

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java`

### Background

`JobImpl.addAttribute` is the single choke point for both job-level attributes and activity attributes (`ActivityImpl.addAttribute` delegates to it). Counting the masked key+value length here covers both. As noted in the consistency note above, this affects the running counter between flushes only, which is where the early-flush decision is made.

- [ ] **Step 1: Write the failing test in `JobImplTest`**

```java
@Test
public void attributeIncreasesEstimatedSize() {
    JobImpl job = createDefaultStartedJob();
    long before = job.getEstimatedSize();
    job.addAttribute("key", "value"); // 3 + 5 = 8 chars
    assertEquals(before + 8, job.getEstimatedSize());
}
```

- [ ] **Step 2: Run the test — expect failure**

```bash
mvn test -Dtest=JobImplTest#attributeIncreasesEstimatedSize -pl njams-sdk
```

Expected: assertion failure (`before + 8` vs `before`).

- [ ] **Step 3: Update `JobImpl.addAttribute`**

Replace the method at `JobImpl.java:1382`:

```java
    @Override
    public void addAttribute(final String key, String value) {
        if (value == null) {
            return;
        }
        String limitKey = limitLength("attributeName", key, 500);
        synchronized (attributes) {
            attributes.put(limitKey, DataMasking.maskString(limitPayload(value)));
        }
    }
```

with:

```java
    @Override
    public void addAttribute(final String key, String value) {
        if (value == null) {
            return;
        }
        String limitKey = limitLength("attributeName", key, 500);
        String maskedValue = DataMasking.maskString(limitPayload(value));
        synchronized (attributes) {
            attributes.put(limitKey, maskedValue);
        }
        final int size = (limitKey == null ? 0 : limitKey.length())
                + (maskedValue == null ? 0 : maskedValue.length());
        addToEstimatedSize(size);
    }
```

- [ ] **Step 4: Run the new test plus full `JobImplTest` — expect all pass**

```bash
mvn test -Dtest=JobImplTest -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all PASS.

- [ ] **Step 5: Run checkstyle**

```bash
mvn validate -Pcheckstyle -pl njams-sdk
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java
git add njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java
git commit -m "SDK-393 #comment Count attributes in the flush-size estimate"
```

---

## Task 5 — Prove flush-by-size now triggers early

**Files:**
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java`

### Background

End-to-end verification of the fix: a job that has only added activities (no payloads) must now exceed a small flush size and trigger a flush from `timerFlush`, where before the fix it would not. This is the behavioral assertion the ticket is really about.

- [ ] **Step 1: Write the test in `JobImplTest`**

```java
@Test
public void timerFlushTriggersOnSizeFromActivitiesAlone() {
    JobImpl job = spy(createDefaultStartedJob());
    job.setStatus(JobStatus.ERROR); // ensure the job is eligible to flush
    // Add enough plain activities that the running estimate crosses a small flush size,
    // even though none of them carry payloads/traces.
    for (int i = 0; i < 5; i++) {
        job.createActivity(process.createActivity("sizeAct" + i, "SizeAct" + i, null)).build();
    }
    // Flush size below the accumulated base sizes; interval not yet due.
    long smallFlushSize = 1000L + 2 * ActivityImpl.BASE_ESTIMATED_SIZE;
    assertTrue("estimate must exceed the small flush size after the fix",
            job.getEstimatedSize() > smallFlushSize);
    job.timerFlush(DateTimeUtility.now(), smallFlushSize);
    Mockito.verify(job, Mockito.atLeastOnce()).flush();
}
```

- [ ] **Step 2: Run the test — expect pass (fix already in place from Tasks 2-4)**

```bash
mvn test -Dtest=JobImplTest#timerFlushTriggersOnSizeFromActivitiesAlone -pl njams-sdk
```

Expected: `BUILD SUCCESS`, PASS. (If it fails, confirm the job is eligible to flush — `setStatus(JobStatus.ERROR)` and `hasUnsentActivity()` must hold.)

- [ ] **Step 3: Run the entire SDK test suite — expect no regressions**

```bash
mvn test -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java
git commit -m "SDK-393 #comment Verify flush-by-size triggers from activity count alone"
```

---

## Task 6 — Update the FAQ

**Files:**
- Modify: `wiki/FAQ.md`

### Background

The `njams.sdk.flushsize` setting's *effective behavior* changes: it now actually triggers early flushes for size-heavy jobs instead of being defeated by an under-counting estimate. Per the FAQ-update rule, document the corrected behavior. Do **not** push to the public wiki.

- [ ] **Step 1: Update the flush-size section of `wiki/FAQ.md`**

Find the section describing `njams.sdk.flushsize` / message flushing and add a clarifying note in the existing markdown style:

```
> **Note (6.0.0):** The SDK's internal message-size estimate now accounts for every activity's
> base size, event messages, event codes, and attributes, in addition to payloads, stack traces
> and start data. Previously these were omitted, so jobs that accumulated many activities could
> grow well beyond `njams.sdk.flushsize` before being sent on the flush interval. The estimate is
> still a conservative (slightly low) approximation of the serialized wire size; the server
> re-derives the authoritative size on receipt.
```

- [ ] **Step 2: Commit**

```bash
git add wiki/FAQ.md
git commit -m "SDK-393 #comment Document corrected flush-size estimate behavior in FAQ"
```

---

## Self-Review Checklist

### Spec / ticket coverage

| Finding | Task |
|---|---|
| Activities don't contribute to the running estimate until flush (ticket bullet 1) | Task 2 |
| Event message not counted (ticket bullet 2) | Task 3 |
| Event code not counted (related to bullet 2) | Task 3 |
| Attributes not counted (consistent under-count) | Task 4 |
| Flush-by-size never fires for activity-heavy jobs (root symptom) | Task 5 (behavioral proof) |
| Double-counting risk from setter-before-`addActivity` ordering | Task 2 Step 4 (adds base only, guarded on first insertion) |
| Reused-activity (loop) re-`addActivity` double count | Task 2 Step 4 (`previous == null` guard) |
| Documented behavior change | Task 6 |
| Baseline safety net before modifying runtime code | Task 1 |

### No placeholders: confirmed — every step contains exact code or an exact command.

### Type consistency

- `ActivityImpl.BASE_ESTIMATED_SIZE` (`static final long`, package-private) — defined Task 2 Step 3; used in Task 2 Step 4 (`JobImpl.addActivity`) and in Tasks 2/5 tests.
- `JobImpl.getEstimatedSize()` / `addToEstimatedSize(long)` — existing public methods, unchanged signatures.
- `process.createActivity(String, String, String)` and `job.createActivity(ActivityModel).build()` — existing test idioms (see `JobImplTest.java:565-566`, `AbstractTest.java:152`).
- `createDefaultStartedJob()` / `getDefaultActivityModelForTest()` — `AbstractTest` helper / new Task 1 helper.
- `DateTimeUtility.now()` — already imported in `JobImplTest`.

### API-impact / breaking-change assessment

No public/protected signature, return type, or removal. Only the internal *accuracy* of the size estimate changes (`getEstimatedSize`/`addToEstimatedSize` keep their contracts). Per the project's breaking-change definition this is **not** a breaking change — the `breaking-change` label should remain off. Confirm again before declaring the ticket done.

### Performance

All additions are O(1) integer increments on the existing runtime path (`addActivity`, event/attribute setters). No new allocation, synchronization, reflection, or I/O. Consistent with the runtime-monitoring-path performance rules.
