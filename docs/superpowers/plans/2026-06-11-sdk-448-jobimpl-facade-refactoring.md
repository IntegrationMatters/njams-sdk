# SDK-448: JobImpl Facade Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the ~1480-line `JobImpl` god class into five public facets on the `Job` interface (`job.activities().add(a)` instead of `job.addActivity(a)`) plus five internal collaborators, keeping every existing public method as a deprecated, delegating one-liner — with the approved flush-invariant fix (never-started jobs are never flushed).

**Architecture:** Per `docs/superpowers/specs/2026-06-11-sdk-448-jobimpl-facade-design.md` (all review points resolved). Same staging as the executed SDK-359 plan: (0) baseline coverage against the unmodified class, (1) extraction with pure delegation, (1b) the approved flush-invariant fix, (2+3) accessors on `Job`, guards, deprecations with usage-explaining comments, `_viaFacet` parity mirrors.

**Tech Stack:** Java 11, Maven, JUnit 4 + Mockito, `TestSender`/`TestReceiver` mock transports, `AbstractTest` helper.

**Jira:** Every commit message references the ticket as `SDK-448 <description>` — WITHOUT the `#comment` tag (reserved for significant commits). The only `#comment` commit is the finalizing one in Task 16.

**Validation commands:**
- Full module tests: `mvn test -pl njams-sdk`
- Single class: `mvn test -Dtest=JobFacadeBaselineTest -pl njams-sdk`
- Coverage: `mvn clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report -pl njams-sdk` → parse `njams-sdk/target/site/jacoco/jacoco.xml` for class `com/im/njams/sdk/logmessage/JobImpl` (the `-Psonar` profile does NOT produce a report — verified during SDK-359)
- Checkstyle: `mvn validate -Pcheckstyle -pl njams-sdk`
- Javadoc (must be error-free): `mvn javadoc:javadoc -pl njams-sdk`

**Hard rules carried over from SDK-359 execution (read before starting):**
1. **Frozen tests are immutable.** If a refactoring step fails an existing test, the step is wrong — except the two approved contract changes in Task 8 (flush invariant), whose old behavior the baseline deliberately does not pin.
2. **Mockito constraint:** tests spy/mock `Njams` and construct `JobImpl` directly. Internal SDK callers (`ActivityImpl`, builders, `ExtractHandler`, `LogMessageFlushTask`) keep calling the legacy `JobImpl` surface — do NOT migrate them to the facet API (that happens with the deprecated-API removal ticket).
3. **Owner threading:** facade methods that construct objects holding a `JobImpl` back-reference (the builders) must pass `this` through, so Mockito spies stay the owner (`ActivityBuilder(this, model)` pattern — see Task 12).
4. **Watch line endings:** never use `sed` for bulk edits on files stored with CRLF (`git diff --stat` showing a whole-file change = EOL accident; restore with `sed -i 's/$/\r/'`).
5. **Hot path:** moved bodies stay literal; no streams/allocation added to per-activity code; facets are final fields, accessors are field reads.

**Source line references** refer to `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java` as of commit `6de790af`. "Move unchanged" = cut, paste, keep body literally identical except the explicitly listed reference renames.

---

## File structure

New production files in `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/` (all with the standard copyright header from CLAUDE.md):

| File | Visibility | Responsibility |
|---|---|---|
| `JobActivities.java` | `public final`, pkg-priv ctor | builders, add, lookups, start activity, sequence counter, flushed-activity bookkeeping |
| `JobAttributes.java` | `public final`, pkg-priv ctor | attribute add/get/getAll/has + flushed-attributes interplay |
| `JobMetadata.java` | `public final`, pkg-priv ctor | correlation/parent/external log ids, business service/object/start/end (chainable setters), jobId/logId reads |
| `JobProperties.java` | `public final`, pkg-priv ctor | internal properties map get/has/set/remove |
| `JobTracing.java` | `public final`, pkg-priv ctor | deepTrace (public), traces/instrumented (internal) |
| `JobFlusher.java` | package-private | flush, timerFlush, log-message assembly, suppression checks, flush counter, lastFlush, estimated size, plugin data items |
| `JobTruncation.java` | package-private | checkTruncating state machine + limits |
| `JobErrorHandling.java` | package-private | error-event storage, commit, update |
| `JobRuntimeConfig.java` | package-private | per-job config snapshot (logMode, logLevel, exclude, recording), needsData support, tracepoint/activity-config lookups |
| `JobSettings.java` | package-private | immutable per-client settings snapshot (allErrors, truncate, payload limits) with a static per-`ClientSettings` cache |

Modified: `Job.java` (accessors + `hasStarted()`, deprecations), `JobImpl.java` (shrinks to facade).
New test files (no copyright header): `JobFacadeBaselineTest.java` (Task 1), `JobFacetApiTest.java` (Tasks 10–15), both in `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/`.

**Lock structure (unchanged semantics):** the `activities` monitor becomes one explicit `final Object activitiesLock = new Object();` on `JobImpl` (package-private), shared by `JobActivities`, `JobFlusher`, and `JobTruncation`. The `attributes` monitor (the map object itself) moves with the maps into `JobAttributes`; `errorLock` moves into `JobErrorHandling`. No new synchronization.

**State guards:** facets/collaborators hold the owning `JobImpl` reference (same package) and use `jobImpl.hasStarted()` / `jobImpl.isFinished()` directly — no separate lifecycle holder needed (unlike SDK-359, the state lives in `lastStatus`/`finished` which stay on `JobImpl`).

---

## Stage 0 — Baseline coverage

### Task 1: Baseline tests pinning current `Job`/`JobImpl` behavior

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobFacadeBaselineTest.java`

- [ ] **Step 1: Write the baseline test class**

Pins every public `Job`/`JobImpl` method migrated by Tasks 2–15 that `JobImplTest` does not already cover. **Deliberately NOT pinned** (approved contract changes, Task 8): the warn-and-send behavior of `flush()`/`end(boolean)` on a never-started job.

```java
package com.im.njams.sdk.logmessage;

import static org.junit.Assert.*;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Pins the current behavior of all public Job/JobImpl methods migrated by SDK-448,
 * BEFORE the refactoring. Must pass unchanged against the refactored class.
 * Deliberately does NOT pin flush()/end() on never-started jobs (approved fix, see design doc).
 */
public class JobFacadeBaselineTest {

    private Njams njams;
    private ProcessModel process;

    @Before
    public void setUp() {
        njams = new Njams(Path.of("SDK4", "TEST"), "1.0", "sdk4", TestReceiver.getSettings());
        process = njams.processes().create(Path.of("SDK4", "TEST", "PROCESSES"));
        process.createActivity("act", "Act", null);
        njams.start();
    }

    @After
    public void tearDown() {
        if (njams.isStarted()) {
            njams.stop();
        }
        TestSender.setSenderMock(null);
    }

    private JobImpl createJob() {
        return (JobImpl) process.createJob();
    }

    private JobImpl createStartedJob() {
        JobImpl job = createJob();
        job.start();
        return job;
    }

    private ActivityModel actModel() {
        return process.getActivity("act");
    }

    // --- lifecycle / status ---

    @Test
    public void newJobIsCreatedNotStartedNotFinished() {
        JobImpl job = createJob();
        assertEquals(JobStatus.CREATED, job.getStatus());
        assertFalse(job.hasStarted());
        assertFalse(job.isFinished());
    }

    @Test
    public void startSetsRunningAndStartTime() {
        JobImpl job = createJob();
        job.start();
        assertTrue(job.hasStarted());
        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertNotNull(job.getStartTime());
    }

    @Test
    public void explicitStartTimeSurvivesStart() {
        JobImpl job = createJob();
        LocalDateTime explicit = DateTimeUtility.now().minusDays(1);
        job.setStartTime(explicit);
        job.start();
        assertEquals(explicit, job.getStartTime());
    }

    @Test
    public void setStartTimeNullIsIgnoredWithWarning() {
        JobImpl job = createJob();
        LocalDateTime before = job.getStartTime();
        job.setStartTime(null); // must NOT throw
        assertEquals(before, job.getStartTime());
    }

    @Test
    public void setStatusBeforeStartOnlyWarns() {
        JobImpl job = createJob();
        job.setStatus(JobStatus.ERROR); // pinned lenient behavior: WARN, no throw, no change
        assertEquals(JobStatus.CREATED, job.getStatus());
    }

    @Test
    public void setStatusNullOrCreatedIsIgnored() {
        JobImpl job = createStartedJob();
        job.setStatus(null);
        job.setStatus(JobStatus.CREATED);
        assertEquals(JobStatus.RUNNING, job.getStatus());
    }

    @Test
    public void maxSeverityEscalatesButNeverDecreases() {
        JobImpl job = createStartedJob();
        job.setStatus(JobStatus.ERROR);
        job.setStatus(JobStatus.SUCCESS);
        assertEquals(JobStatus.ERROR, job.getMaxSeverity());
    }

    @Test
    public void endTrueWithoutStatusYieldsSuccess() {
        JobImpl job = createStartedJob();
        job.end(true);
        assertTrue(job.isFinished());
        assertEquals(JobStatus.SUCCESS, job.getStatus());
        assertNotNull(job.getEndTime());
    }

    @Test
    public void endFalseYieldsError() {
        JobImpl job = createStartedJob();
        job.end(false);
        assertEquals(JobStatus.ERROR, job.getStatus());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void endTwiceThrows() {
        JobImpl job = createStartedJob();
        job.end(true);
        job.end(true);
    }

    @Test
    public void endRemovesJobFromRegistry() {
        JobImpl job = createStartedJob();
        String jobId = job.getJobId();
        job.end(true);
        assertNull(njams.jobs().get(jobId));
    }

    @Test
    public void deprecatedEndDelegatesToEndTrue() {
        JobImpl job = createStartedJob();
        job.end();
        assertEquals(JobStatus.SUCCESS, job.getStatus());
    }

    // --- activities ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void addActivityBeforeStartThrows() {
        JobImpl job = createJob();
        job.createActivity(actModel()).build(); // build() -> addActivity -> throws
    }

    @Test
    public void activityLifecycleAfterStart() {
        JobImpl job = createStartedJob();
        Activity activity = job.createActivity(actModel()).build();
        assertSame(activity, job.getActivityByInstanceId(activity.getInstanceId()));
        assertSame(activity, job.getActivityByModelId("act"));
        assertSame(activity, job.getRunningActivityByModelId("act"));
        assertNull(job.getCompletedActivityByModelId("act"));
        activity.end();
        assertSame(activity, job.getCompletedActivityByModelId("act"));
        assertNull(job.getRunningActivityByModelId("act"));
        assertEquals(1, job.getActivities().size());
    }

    @Test
    public void getActivitiesReturnsDetachedCopy() {
        JobImpl job = createStartedJob();
        job.createActivity(actModel()).build();
        job.getActivities().clear();
        assertEquals(1, job.getActivities().size());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void secondStartActivityThrows() {
        JobImpl job = createStartedJob();
        ActivityModel starter = process.createActivity("starter", "Starter", null);
        starter.setStarter(true);
        job.createActivity(starter).setStarter().build();
        job.createActivity(starter).setStarter().build();
    }

    @Test
    public void startActivityIsTracked() {
        JobImpl job = createStartedJob();
        ActivityModel starter = process.createActivity("starter2", "Starter2", null);
        starter.setStarter(true);
        Activity activity = job.createActivity(starter).setStarter().build();
        assertSame(activity, job.getStartActivity());
    }

    // --- attributes ---

    @Test
    public void attributesArePutAndQueried() {
        JobImpl job = createStartedJob();
        job.addAttribute("k", "v");
        assertEquals("v", job.getAttribute("k"));
        assertTrue(job.hasAttribute("k"));
        assertFalse(job.hasAttribute("missing"));
        Map<String, String> all = job.getAttributes();
        assertEquals("v", all.get("k"));
        all.clear(); // detached copy
        assertTrue(job.hasAttribute("k"));
    }

    @Test
    public void nullAttributeValueIsIgnored() {
        JobImpl job = createStartedJob();
        job.addAttribute("k", null); // must NOT throw
        assertFalse(job.hasAttribute("k"));
    }

    @Test
    public void recordingAddsNjamsRecordedAttribute() {
        JobImpl job = createJob();
        assertEquals("true", job.getAttribute("$njams_recorded"));
        assertTrue(job.isRecording());
    }

    // --- metadata (descriptive fields) ---

    @Test
    public void correlationLogIdDefaultsToLogIdAndIsSettable() {
        JobImpl job = createJob();
        assertEquals(job.getLogId(), job.getCorrelationLogId());
        job.setCorrelationLogId("corr-1");
        assertEquals("corr-1", job.getCorrelationLogId());
    }

    @Test
    public void parentAndExternalLogIdAreSettable() {
        JobImpl job = createJob();
        job.setParentLogId("p-1");
        job.setExternalLogId("e-1");
        assertEquals("p-1", job.getParentLogId());
        assertEquals("e-1", job.getExternalLogId());
    }

    @Test
    public void businessServiceAndObjectAcceptStringAndPath() {
        JobImpl job = createJob();
        job.setBusinessService("svc");
        job.setBusinessObject(Path.of("obj"));
        assertNotNull(job.getBusinessService());
        assertNotNull(job.getBusinessObject());
        job.setBusinessService((Path) null); // null Path ignored, must NOT throw
    }

    @Test
    public void businessStartAndEndAreSettable() {
        JobImpl job = createJob();
        LocalDateTime t = DateTimeUtility.now();
        job.setBusinessStart(t);
        job.setBusinessEnd(t);
        assertEquals(t, job.getBusinessStart());
        assertEquals(t, job.getBusinessEnd());
    }

    @Test
    public void overlongFieldValueIsTruncated() {
        JobImpl job = createJob();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < JobImpl.MAX_VALUE_LIMIT + 100; i++) {
            sb.append('x');
        }
        job.setParentLogId(sb.toString());
        assertEquals(JobImpl.MAX_VALUE_LIMIT - 1, job.getParentLogId().length());
    }

    @Test
    public void limitLengthTruncatesToMaxMinusOne() {
        assertEquals("abc", JobImpl.limitLength("f", "abc", 10));
        assertEquals("abcd", JobImpl.limitLength("f", "abcdef", 5));
        assertNull(JobImpl.limitLength("f", null, 5));
    }

    // --- properties ---

    @Test
    public void propertiesRoundTrip() {
        JobImpl job = createJob();
        assertFalse(job.hasProperty("p"));
        job.setProperty("p", 42);
        assertTrue(job.hasProperty("p"));
        assertEquals(42, job.getProperty("p"));
        assertEquals(42, job.removeProperty("p"));
        assertNull(job.getProperty("p"));
        assertNull(job.removeProperty("p"));
    }

    // --- tracing flags ---

    @Test
    public void deepTraceAndTracesFlags() {
        JobImpl job = createJob();
        assertFalse(job.isDeepTrace());
        job.setDeepTrace(true);
        assertTrue(job.isDeepTrace());
        assertFalse(job.isTraces());
        job.setTraces(true);
        assertTrue(job.isTraces());
    }

    @Test
    public void needsDataIsTrueForDeepTraceAndStarterModels() {
        JobImpl job = createJob();
        ActivityModel plain = actModel();
        assertFalse(job.needsData(plain));
        job.setDeepTrace(true);
        assertTrue(job.needsData(plain));
    }

    // --- flushing infrastructure (kept behavior only) ---

    @Test
    public void timerFlushBeforeStartIsSkippedSilently() {
        JobImpl job = createJob();
        job.timerFlush(DateTimeUtility.now().plusDays(1), 0); // must NOT throw, must not flush
        assertFalse(job.hasStarted());
    }

    @Test
    public void estimatedSizeGrowsWithContent() {
        JobImpl job = createStartedJob();
        long before = job.getEstimatedSize();
        job.addAttribute("k", "some-value");
        assertTrue(job.getEstimatedSize() > before);
        job.addToEstimatedSize(100);
        assertEquals(before + "k".length() + "some-value".length() + 100, job.getEstimatedSize());
    }

    @Test
    public void lastFlushIsInitialized() {
        assertNotNull(createJob().getLastFlush());
    }

    @Test
    public void getNjamsReturnsOwner() {
        assertSame(njams, createJob().getNjams());
    }

    // --- payload limiting (no limit configured in test settings) ---

    @Test
    public void noPayloadLimitConfiguredMeansPassThrough() {
        JobImpl job = createJob();
        assertEquals(0, job.getSerializeSizeHint());
        assertEquals("payload", job.limitPayload("payload"));
        assertNull(job.limitPayload(null));
    }

    @Test
    public void toStringContainsLogAndJobId() {
        JobImpl job = createJob();
        assertTrue(job.toString().contains(job.getLogId()));
        assertTrue(job.toString().contains(job.getJobId()));
    }
}
```

Compile-check notes: verify `ActivityModel.setStarter(boolean)`, `ActivityBuilder.setStarter()`, and `process.getActivity("act")` signatures before running (Grep in `njams-sdk/src/main/java`). If a signature differs, adapt the *test*, never production code. `limitPayload`/`getSerializeSizeHint` are package-private — the test lives in the same package, so direct calls work.

- [ ] **Step 2: Run — must pass against the UNMODIFIED class**

Run: `mvn test -Dtest=JobFacadeBaselineTest -pl njams-sdk`
Expected: all PASS. Failures mean the test mis-pins behavior — read the production code and fix the test.

- [ ] **Step 3: Coverage check**

Run the JaCoCo command from the header; parse `jacoco.xml` for `com/im/njams/sdk/logmessage/JobImpl` and list public methods with zero covered instructions (combined coverage from this class + `JobImplTest` + the rest of the suite). Add pinning tests for any uncovered migrated public method — EXCEPT the two unpinned-by-design paths (`flush`/`end` on never-started jobs).

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobFacadeBaselineTest.java
git commit -m "SDK-448 Pin baseline behavior of Job/JobImpl before facade refactoring"
```

---

## Stage 1 — Extraction with pure delegation

Rules (identical to SDK-359 Stage 1): each task creates the class, moves the listed members with literal bodies, turns the `JobImpl` originals into one-line delegations (signatures, Javadoc, and behavior unchanged), runs `mvn test -pl njams-sdk` (all green), commits. No accessors, no deprecations yet. Public facet classes get full Javadoc + copyright header; package-private collaborators get the header and concise Javadoc.

### Task 2: `JobSettings` (shared per-client snapshot)

**Files:** Create `JobSettings.java`; modify `JobImpl.java`.

- [ ] **Step 1: Create the snapshot class** — replaces the per-job parsing of `allErrors`, `truncateOnSuccess`, `truncateLimit` (ctor lines 224–226, 248–251) and `initPayloadLimit` (230–246):

```java
package com.im.njams.sdk.logmessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Immutable per-client snapshot of the job-related settings (error logging, truncation,
 * payload limits). Parsed once per {@link ClientSettings} instance instead of for every job.
 */
final class JobSettings {

    // one entry per Njams instance; instances are few and long-lived, no eviction needed
    private static final ConcurrentMap<ClientSettings, JobSettings> CACHE = new ConcurrentHashMap<>();

    final boolean allErrors;
    final boolean truncateOnSuccess;
    final int truncateLimit;
    /** null = no payload limit; key true = truncate, false = discard; value = limit */
    final java.util.Map.Entry<Boolean, Integer> payloadLimit;

    static JobSettings of(ClientSettings settings) {
        return CACHE.computeIfAbsent(settings, JobSettings::new);
    }

    private JobSettings(ClientSettings settings) {
        allErrors = settings.getBool(NjamsSettings.PROPERTY_LOG_ALL_ERRORS, false);
        truncateOnSuccess = settings.getBool(NjamsSettings.PROPERTY_TRUNCATE_ON_SUCCESS, false);
        final int limit = settings.getInt(NjamsSettings.PROPERTY_TRUNCATE_LIMIT, Integer.MAX_VALUE);
        truncateLimit = limit > 0 ? limit : Integer.MAX_VALUE;
        payloadLimit = parsePayloadLimit(settings);
    }

    // body of JobImpl.initPayloadLimit (230-246) unchanged, returning the entry instead of
    // assigning the field
    private static java.util.Map.Entry<Boolean, Integer> parsePayloadLimit(ClientSettings settings) {
        final String mode = settings.getProperty(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE);
        if (StringUtils.isBlank(mode)) {
            return null;
        }
        final int limit = settings.getInt(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, -1);
        if (limit < 0) {
            return null;
        }
        if (limit == 0 || "discard".equalsIgnoreCase(mode)) {
            return new java.util.AbstractMap.SimpleImmutableEntry<>(false, limit);
        }
        if ("truncate".equalsIgnoreCase(mode)) {
            return new java.util.AbstractMap.SimpleImmutableEntry<>(true, limit);
        }
        return null;
    }
}
```

- [ ] **Step 2: Use it in `JobImpl`** — replace fields `allErrors`, `truncateLimit`, `truncateOnSuccess` (187–189), `payloadLimit` (198) with `private final JobSettings jobSettings;`; in the ctor replace lines 224–227 with `jobSettings = JobSettings.of(njams.getSettings());`; delete `initPayloadLimit` and `getTruncateLimit`; rename field reads (`allErrors` → `jobSettings.allErrors`, `truncateOnSuccess` → `jobSettings.truncateOnSuccess`, `truncateLimit` → `jobSettings.truncateLimit`, `payloadLimit` → `jobSettings.payloadLimit`) in `checkTruncating`, `commitActivityError`, `setActivityErrorEvent`, `getSerializeSizeHint`, `limitPayload`.

- [ ] **Step 3:** `mvn test -pl njams-sdk` → all PASS. **Step 4: Commit** `SDK-448 Extract JobSettings snapshot, parse job settings once per client`.

### Task 3: `JobRuntimeConfig`

**Files:** Create `JobRuntimeConfig.java`; modify `JobImpl.java`.

- [ ] **Step 1:** Move `initFromConfiguration` (257–284, becomes the constructor; the `addAttribute("$njams_recorded", ...)` call stays in `JobImpl`'s ctor: `if (runtimeConfig.recording) { addAttribute("$njams_recorded", "true"); }`), fields `logMode`/`logLevel`/`exclude`/`recording` (147–149, 160), `isActiveTracepoint` (1286–1295) and `getActivityConfiguration` (1303–1321) as package-private methods. Class skeleton:

```java
final class JobRuntimeConfig {
    final LogMode logMode;     // and logLevel, exclude, recording — all final, set in ctor
    ...
    JobRuntimeConfig(ProcessModel processModel) {
        // body of initFromConfiguration(processModel) (257-284) unchanged, minus the
        // addAttribute call; njams.isExcluded(...) call stays as-is (legacy surface, mock-safe)
    }
    boolean isActiveTracepoint(TracepointExt tracepoint) { /* body 1286-1295 unchanged */ }
    ActivityConfiguration getActivityConfiguration(ActivityModel activityModel) { /* 1303-1321 */ }
}
```

- [ ] **Step 2:** `JobImpl` field `private final JobRuntimeConfig runtimeConfig;` (ctor: `runtimeConfig = new JobRuntimeConfig(processModel);`); delegate `isActiveTracepoint`/`getActivityConfiguration`/`isRecording`; rename internal reads (`logMode` → `runtimeConfig.logMode` etc. in the suppression checks and `needsData`). Note: the config defaults handling (`configuration == null` branch) keeps the current field defaults (`COMPLETE`/`INFO`/`false`/`true`).

- [ ] **Step 3:** full suite green. **Step 4: Commit** `SDK-448 Extract JobRuntimeConfig collaborator`.

### Task 4: `JobErrorHandling`

- [ ] **Step 1:** Move `errorLock`/`errorActivity`/`errorEvent` (184–186), `setActivityErrorEvent` body (788–803), `commitActivityError` (760–777), `updateActivityErrorEvent` (811–823). The collaborator gets `JobImpl jobImpl` + `JobSettings jobSettings` ctor params (it calls `jobImpl.getActivityByInstanceId` and `jobImpl.addActivity` — keep those calls on the facade so the behavior path is unchanged).
- [ ] **Step 2:** `JobImpl.setActivityErrorEvent` delegates; `end(boolean)`'s `commitActivityError()` call → `errorHandling.commitActivityError();`.
- [ ] **Step 3:** full suite green. **Step 4: Commit** `SDK-448 Extract JobErrorHandling collaborator`.

### Task 5: `JobTruncation`

- [ ] **Step 1:** Move `isTruncatingActivities`/`isTruncatingEvents`/`activityIds` (190–194), `checkTruncating` (662–698, package-private as today), `hasEvent` (359–364). Ctor params: `JobSettings`, the shared `activitiesLock` (synchronization note in the file header: callers hold the lock — exactly as today, where `checkTruncating` is only called under `synchronized (activities)`).
- [ ] **Step 2:** `JobImpl.checkTruncating` stays package-private delegating (it is test-relevant), internal caller in `addToLogMessageAndCleanup` switches to `truncation.checkTruncating(...)`.
- [ ] **Step 3:** full suite green. **Step 4: Commit** `SDK-448 Extract JobTruncation collaborator`.

### Task 6: `JobAttributes` + `JobProperties`

- [ ] **Step 1:** `JobAttributes`: move `attributes`/`flushedAttributes` maps (126–127), bodies of `addAttribute` (1394–1407, renamed `add`; the `addToEstimatedSize` call goes through the `JobImpl` reference), `getAttribute` → `get` (1087–1094), `getAttributes` → `getAll` (1102–1107), `hasAttribute` → `has` (1115–1118); package-private `flushInto(LogMessage)` taking over the attributes part of `addToLogMessageAndCleanup` (621–627). `JobProperties`: move `properties` map (155) and the four methods (1180–1218) as `get`/`has`/`set`/`remove`.
- [ ] **Step 2:** `JobImpl` delegates all eight legacy methods; `addToLogMessageAndCleanup` calls `attributes.flushInto(logMessage)`; `timerFlush`'s `!attributes.isEmpty()` check → package-private `attributes.isEmpty()`.
- [ ] **Step 3:** full suite green. **Step 4: Commit** `SDK-448 Extract JobAttributes and JobProperties facets, JobImpl delegates`.

### Task 7: `JobMetadata`, `JobTracing`, `JobActivities`, `JobFlusher`

Largest extraction step — may be split into four commits, one per class, each with a green suite:

- [ ] **Step 1: `JobMetadata`** — move fields `correlationLogId`/`parentLogId`/`externalLogId`/`businessService`/`businessObject`/`businessStart`/`businessEnd` (162–171, 180–182) and their getters/setters (893–1010, 1353–1376). Setter bodies stay literal (incl. `limitLength` calls); signatures already in final chainable form `JobMetadata setCorrelationLogId(String)` returning `this` (the facade delegates ignore the return). `getJobId()`/`getLogId()` are exposed read-only (fields stay on `JobImpl`, the facet reads via the back-reference).
- [ ] **Step 2: `JobTracing`** — move `deepTrace` (143), `traces` (152), `instrumented` (151); public `setDeepTrace`/`isDeepTrace`/`isTraces`; package-private `setTraces`/`setInstrumented`/`isInstrumented`. `JobImpl` delegates the five legacy methods; suppression checks read via the collaborator.
- [ ] **Step 3: `JobActivities`** — move the `activities` map (116, lock note above), `sequenceCounter` (121), `startActivity`/`hasOrHadStartActivity` (139–141), `flushedActivities` (196); methods `addActivity` → `add` (332–357), the four lookups → `getByInstanceId`/`getByModelId`/`getRunningByModelId`/`getCompletedByModelId` (372–442), `getStartActivity` → `getStart` (449–452), `getActivities` → `getAll` (457–462), `getNextSequence` (469–471, package-private), `removeNotRunningActivities` (1225–1250, package-private), `hasActivityToSend`/`shouldFlush` (495–503, 648–653, package-private). Builder factories stay on `JobImpl` for now (they need owner threading — Task 12). The started-check inside `add` reads `jobImpl.hasStarted()`.
- [ ] **Step 4: `JobFlusher`** — move `flushCounter` (137), `lastFlush` (178), `estimatedSize` (158), `pluginDataItems` (132); methods `flush` (510–530), `timerFlush` (479–493), `createLogMessage` (591–618), `addToLogMessageAndCleanup` (620–646), the suppression checks (532–583), `calculateEstimatedSize` (700–705), `getLastFlush`/`getEstimatedSize`/`addToEstimatedSize` (1147–1149, 1255–1266), `addPluginDataItem` (1342–1346). The sender call stays `processModel.getNjams().getSender().send(logMessage, njams.getClientSessionId())` — legacy surface, required by the `JobImplTest` spy (hard rule 3 of SDK-359: mock-safe).
- [ ] **Step 5:** All `JobImpl` originals are one-line delegates; full suite green after each class. **Commits:** `SDK-448 Extract JobMetadata facet` / `... JobTracing facet` / `... JobActivities facet` / `... JobFlusher collaborator, JobImpl becomes a facade`.

---

## Stage 1b — Approved flush-invariant fix

### Task 8: Never-started jobs are never flushed

**Files:** Modify `JobFlusher.java`, `JobImpl.java`; extend `JobFacadeBaselineTest.java` (new tests pinning the FIXED behavior — allowed, since the old behavior was deliberately left unpinned).

- [ ] **Step 1: Write failing tests** (in `JobFacadeBaselineTest`, section "flush invariant — fixed behavior"):

```java
@Test
public void flushOnNeverStartedJobSendsNothing() throws InterruptedException {
    JobImpl job = createJob();
    job.addAttribute("k", "v");
    CapturingLogMessageSender capturing = new CapturingLogMessageSender();
    TestSender.setSenderMock(capturing);
    try {
        job.flush(); // WARN + skip; previously sent a status -1 message
        assertNull(capturing.poll(500));
    } finally {
        TestSender.setSenderMock(null);
    }
}

@Test
public void endOnNeverStartedJobLogsErrorAndSendsNothing() throws InterruptedException {
    JobImpl job = createJob();
    CapturingLogMessageSender capturing = new CapturingLogMessageSender();
    TestSender.setSenderMock(capturing);
    try {
        job.end(true); // ERROR log, no throw, nothing sent
        assertTrue(job.isFinished());
        assertNull(capturing.poll(500));
    } finally {
        TestSender.setSenderMock(null);
    }
}
```

`CapturingLogMessageSender` follows the `CapturingSender` pattern from `NjamsFacadeBaselineTest` but captures `LogMessage` instances and offers `LogMessage poll(long millis)` (await with timeout, return null on none). Run: expected FAIL (messages are currently sent).

- [ ] **Step 2: Implement** — in `JobFlusher.flush()`, replace the warn-and-proceed branch:

```java
if (!started) {
    LOG.warn("Not flushing job with logId: {}: the job has not been started"
        + " - a job that was never started is never sent to the nJAMS server.", jobImpl.getLogId());
    return;
}
```

In `JobImpl.end(boolean)`, replace the `LOG.warn("Job has been finished before it started.")` with `LOG.error("Job {} has been finished before it was started - it will NOT be sent to the nJAMS server.", getLogId());` (no throw; `finished` is still set, the registry entry still removed, `flush()` is still called and now skips).

- [ ] **Step 3:** New tests PASS, full suite green (no frozen test pins the old path — verified in Task 1). **Step 4: Commit** `SDK-448 Fix flush invariant: never-started jobs are never flushed, end() logs error`.

---

## Stage 2+3 — Accessors on `Job`, guards, deprecations, parity

Rules: identical to SDK-359 Stage 2+3 — new public members fully documented; guards ONLY in the new facet methods (legacy delegates use package-private internals + `warnIfFinished` helper on `JobImpl`); every deprecation comment names the accessor chain, target method, and contract difference; parity mirrors `_viaFacet` with identical assertions; deviations as explicit test pairs. New tests in `JobFacetApiTest` (same package).

### Task 9: Accessors on `Job` and `JobImpl`

- [ ] **Step 1:** Add to the `Job` interface (full Javadoc each): `JobActivities activities();`, `JobAttributes attributes();`, `JobMetadata metadata();`, `JobProperties properties();`, `JobTracing tracing();`, and `boolean hasStarted();` (lifted, undeprecated — Javadoc moved from `JobImpl`). `JobImpl` implements the accessors as final-field reads (`hasStarted()` already exists).
- [ ] **Step 2:** `JobFacetApiTest` with `accessorsReturnTheSameInstanceEveryTime` (assertSame on all five, via a `Job`-typed reference) and `hasStartedIsOnTheInterface` (call through `Job` without cast).
- [ ] **Step 3:** tests + `mvn javadoc:javadoc -pl njams-sdk` green. **Step 4: Commit** `SDK-448 Expose job facet accessors and hasStarted on the Job interface`.

### Task 10: Guards + deprecations — metadata

- [ ] **Step 1: Failing tests:** `newMetadataSettersThrowAfterEnd` (each mutator after `end(true)` → `NjamsSdkRuntimeException` via `requireNotFinished`), `metadataMutatorsAreChainable` (assertSame on the full chain), `deprecatedMetadataSettersStayLenientAfterEnd` (legacy setters WARN + still store).
- [ ] **Step 2: Implement:** each `JobMetadata` mutator gains the guard `jobImpl.requireNotFinished("JobMetadata.setX")` (new package-private helper on `JobImpl`: throws `NjamsSdkRuntimeException` when `finished`, message pattern as in SDK-359's `LifecycleState.requireNotStarted`) + package-private `setXInternal` variants; the public methods return `this`.
- [ ] **Step 3: Deprecate** the 13 legacy metadata methods on `Job`/`JobImpl` (`setCorrelationLogId` … `getBusinessEnd`) with the template tag; mutator tags note the after-`end` contract difference; the legacy delegates call `warnIfFinished(...)` + `setXInternal`. Add `warnIfFinished` once to `JobImpl` (mirror of `Njams.warnIfStarted`).
- [ ] **Step 4: Parity mirrors** `_viaFacet`: `correlationLogIdDefaultsToLogIdAndIsSettable`, `parentAndExternalLogIdAreSettable`, `businessServiceAndObjectAcceptStringAndPath`, `businessStartAndEndAreSettable`, `overlongFieldValueIsTruncated` (truncation must run on the guarded path too).
- [ ] **Step 5:** full suite green. **Commit** `SDK-448 Guard metadata mutators after end, deprecate legacy metadata methods`.

### Task 11: Guards + deprecations — attributes

- [ ] **Step 1: Failing tests:** `newAttributesAddThrowsAfterEnd`, `deprecatedAddAttributeStaysLenientAfterEnd` (WARN + stores). Plus mirrors: `attributesArePutAndQueried_viaFacet`, `nullAttributeValueIsIgnored_viaFacet`.
- [ ] **Step 2: Implement:** `JobAttributes.add` = `jobImpl.requireNotFinished("JobAttributes.add"); addInternal(...)`. The extract path (`ExtractHandler`/`ActivityImpl` call `job.addAttribute` during end-processing) keeps working through the legacy lenient delegate — verify with a Grep for `addAttribute` callers in `njams-sdk/src/main/java` that all internal callers use the `JobImpl` method, not the facet.
- [ ] **Step 3: Deprecate** `addAttribute`/`getAttribute`/`getAttributes`/`hasAttribute` with template tags (add notes the after-`end` difference).
- [ ] **Step 4:** full suite green. **Commit** `SDK-448 Guard attribute mutation after end, deprecate legacy attribute methods`.

### Task 12: Deprecations + inverted deviation — activities

- [ ] **Step 1: Tests:** `newActivitiesAddWorksBeforeStart` — THE inverted deviation: `job.activities().add(activity)` (and builder-based creation via `activities().create(model).build()` once builders route through the facet) succeeds on a CREATED job; paired with the pinned `addActivityBeforeStartThrows` baseline test for the deprecated method. Plus `prestartActivityIsFlushedAfterStart` (create pre-start, then `start()`, `end(true)`, capture the log message, assert the activity is contained) and mirrors: `activityLifecycleAfterStart_viaFacet`, `getActivitiesReturnsDetachedCopy_viaFacet`, `secondStartActivityThrows_viaFacet` (the second-starter check must hold on the new path).
- [ ] **Step 2: Implement:** `JobActivities.add` loses the started-check (it lives only in the deprecated `JobImpl.addActivity`, which keeps throwing — pinned); the second-start-activity check stays in the shared internal path. **Builder factories move to the facet with owner threading:** `JobActivities.create(ActivityModel)` → `create(model, jobImpl)` package-private variant constructing `new ActivityBuilder(owner, model)`; deprecated `JobImpl.createActivity(model)` calls `activities.create(model, this)` so spies stay the owner (hard rule 3). Same for `createGroup`/`createSubProcess`.
- [ ] **Step 3:** Verify the `ActivityBuilder.build()` path: it calls `activity.getJob().addActivity(activity)` — the deprecated, throwing method. For the new contract, `build()` must route through the non-throwing internal add when the builder was created via the facet. Simplest compliant approach: `build()` switches to the package-private `((JobImpl) activity.getJob()).activitiesInternalAdd(activity)`-style call ONLY IF no frozen test pins the throw through the builder — `JobFacadeBaselineTest.addActivityBeforeStartThrows` DOES pin builder-based throwing for the legacy `job.createActivity(...)` entry. Therefore: the internal add carries a `boolean requireStarted` flag — `true` when entered via deprecated `createActivity`/`addActivity`, `false` via `activities().create`/`activities().add`. Document this in the facet Javadoc.
- [ ] **Step 4: Deprecate** the 10 legacy activity methods with template tags; `addActivity`'s tag explicitly states the inverted difference ("the replacement does NOT require the job to be started; activities created before start are flushed with the first log message after the job starts").
- [ ] **Step 5:** full suite green. **Commit** `SDK-448 Allow pre-start activities in new API, deprecate legacy activity methods`.

### Task 13: Deprecations — properties + tracing

- [ ] **Step 1: Mirrors:** `propertiesRoundTrip_viaFacet`, `deepTraceFlag_viaFacet` (no guards — both facets are phase-free).
- [ ] **Step 2: Deprecate** `getProperty`/`hasProperty`/`setProperty`/`removeProperty` → `properties().get/has/set/remove` and `setDeepTrace`/`isDeepTrace`/`isTraces` → `tracing().…` with template tags (no contract differences).
- [ ] **Step 3:** full suite green. **Commit** `SDK-448 Deprecate legacy property and tracing methods`.

### Task 14: Deprecations — internal mechanics on `JobImpl`

- [ ] **Step 1: Deprecate** as SDK-internal (template: "SDK-internal mechanics, not part of the public API; there is no replacement — <one sentence what handles it now>"): `flush`, `timerFlush`, `setActivityErrorEvent`, `setInstrumented`, `setTraces`, `getLastFlush`, `getEstimatedSize`, `addToEstimatedSize`, `isActiveTracepoint`, `getActivityConfiguration`, `isRecording`, `getNjams`, `limitLength`. NOT deprecated: `hasStarted` (lifted to `Job`), `checkTruncating`/`getNextSequence`/`getSerializeSizeHint`/`limitPayload` (already package-private). Internal callers (`LogMessageFlushTask`, `ActivityImpl`, `ExtractHandler`, builders) intentionally keep calling them (hard rule 2).
- [ ] **Step 2:** full suite green + `mvn javadoc:javadoc -pl njams-sdk` error-free. **Commit** `SDK-448 Deprecate SDK-internal JobImpl mechanics`.

### Task 15: Migrate sample clients

- [ ] **Step 1:** Grep `njams-sdk-sample-client/src/main/java` and `njams-sdk-sample-app/src/main/java` for the deprecated `Job` method names (`createActivity`, `addAttribute`, `setBusinessService`, …); replace per the mapping table in the design doc (e.g. `job.createActivity(m)` → `job.activities().create(m)`, `job.addAttribute(k, v)` → `job.attributes().add(k, v)`, `job.setBusinessService(s)` → `job.metadata().setBusinessService(s)`). Use Edit per file, NOT sed (hard rule 4). Samples must compile without deprecation warnings in their main code.
- [ ] **Step 2:** `mvn clean install -DskipTests` → BUILD SUCCESS. **Commit** `SDK-448 Migrate sample clients to the job facet API`.

### Task 16: Final validation, docs, wiki

- [ ] **Step 1: Quality gates** — all must succeed: `mvn clean install -pl njams-sdk`, `mvn validate -Pcheckstyle -pl njams-sdk`, `mvn javadoc:javadoc -pl njams-sdk`.
- [ ] **Step 2: Parity completeness check** — every `JobFacadeBaselineTest` test falls into one of: (a) `_viaFacet` mirror exists, (b) intentional-deviation pair (`addActivityBeforeStartThrows` ↔ `newActivitiesAddWorksBeforeStart`; the after-`end` guard pairs; the Task 8 flush-invariant tests), or (c) explicitly legacy-only (`getNjamsReturnsOwner`, `limitLengthTruncatesToMaxMinusOne`, `timerFlushBeforeStartIsSkippedSilently`, `estimatedSizeGrowsWithContent`, `lastFlushIsInitialized`, `noPayloadLimitConfiguredMeansPassThrough`, `toStringContainsLogAndJobId` — all deprecated-internal or facade-level). Any unclassified test = parity gap, add the mirror.
- [ ] **Step 3: Wiki check** — Grep `wiki/*.md` for migrated `Job` method names; update examples to the facet API with a one-line "old methods deprecated" note (pattern from SDK-359). No settings changed → no FAQ settings entries.
- [ ] **Step 4: Design doc status** — set to `Implemented (see plan 2026-06-11-sdk-448-jobimpl-facade-refactoring.md)`.
- [ ] **Step 5: Jira hygiene** — verify the `breaking-change` label is ABSENT (the flush-invariant change is classified as a fix per the design review). Do NOT resolve the ticket or post a closing comment.
- [ ] **Step 6: Final commit** (the single `#comment` commit):

```bash
git add -A
git commit -m "SDK-448 #comment Finalize JobImpl facade refactoring: job facets, flush-invariant fix, deprecations, docs and wiki updated"
```

---

## Self-review notes

- Spec coverage: design sections map to tasks — baseline (Task 1 = step 0), collaborators/facets extraction (Tasks 2–7 = step 1), flush-invariant fix (Task 8, the approved contract change), accessors/guards/deprecations/parity (Tasks 9–14 = steps 2+3), samples + gates (Tasks 15–16). Deferred items (internal-caller migration, JobImpl sealing, lazy maps, ActivityImpl) intentionally absent.
- The inverted deviation (legacy strict / new lenient on `activities().add`) is enforced by the pinned `addActivityBeforeStartThrows` + new `newActivitiesAddWorksBeforeStart` pair; the builder dual-path (`requireStarted` flag) is the one structurally delicate spot — Task 12 Step 3 spells it out.
- After-`end` guards exist on `JobMetadata` and `JobAttributes` only (review decision 2); `properties()`/`tracing()` are phase-free.
- Chainability: `JobMetadata` mutators only (review decision 4 + SDK-359 precedent) — do not add `return this` elsewhere.
- `JobSettings` static cache keyed by `ClientSettings`: no eviction by design (few long-lived instances); flagged here so the reviewer sees the trade-off.
- Known judgment calls for the implementer: exact `ActivityModel`/builder signatures in Task 1 (verify before running), and whether `getStatus()`-related tests collide with `JobImplTest` — if a baseline test duplicates an existing assertion, keep both (baseline must be self-contained).
