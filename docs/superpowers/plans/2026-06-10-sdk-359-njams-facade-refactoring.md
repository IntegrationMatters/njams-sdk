# SDK-359: Njams Facade Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the ~1490-line `Njams` god class into nine focused facet classes behind no-prefix accessors (`njams.jobs().add(job)`), keeping every existing public method as a deprecated, delegating one-liner.

**Architecture:** Per `docs/superpowers/specs/2026-06-10-sdk-359-njams-facade-design.md`. Three stages: (0) full baseline test coverage against the unmodified class, (1) extraction of facet classes with pure delegation — baseline suite must pass unchanged, (2+3) public accessors, phase guards on the new API only, and `@Deprecated` annotations whose Javadoc explains the replacement usage. Facets are `public final` classes with package-private constructors in `com.im.njams.sdk`, created once in the `Njams` constructor and held in `final` fields (zero-overhead delegation).

**Tech Stack:** Java 11, Maven, JUnit 4 + Mockito, existing `TestSender`/`TestReceiver` mock transports.

**Jira:** Every commit message uses `SDK-359 #comment <description>`.

**Validation commands (used throughout):**
- Full module tests: `mvn test -pl njams-sdk`
- Single class: `mvn test -Dtest=NjamsFacadeBaselineTest -pl njams-sdk`
- Coverage report: `mvn clean test -Psonar -pl njams-sdk` → open `njams-sdk/target/site/jacoco/com.im.njams.sdk/Njams.java.html`
- Checkstyle: `mvn validate -Pcheckstyle -pl njams-sdk`
- Javadoc (must be error-free): `mvn javadoc:javadoc -pl njams-sdk`

---

## File structure

New production files (all in `njams-sdk/src/main/java/com/im/njams/sdk/`, all start with the standard copyright header from CLAUDE.md):

| File | Responsibility |
|---|---|
| `LifecycleState.java` (package-private) | Shared started-flag holder + uniform phase-guard checks |
| `NjamsSerializers.java` | Serializer registry + `serialize(...)` + lookup cache |
| `NjamsArgos.java` | Argos collector registration, `ArgosSender` wiring |
| `NjamsFeatures.java` | Feature list, container mode |
| `NjamsMetadata.java` | Client path/category/versions/machine/session id, global variables (+ pattern), version-file reading, startup banner |
| `NjamsJobs.java` | Job registry, replay-marker bookkeeping |
| `NjamsReplay.java` | Replay handler, replay instruction handling |
| `NjamsConfiguration.java` | `Configuration` access, provider loading, log mode, exclusion check, data-masking init |
| `NjamsProcesses.java` | Process model registry, tree elements, images, layouter, diagram factory, project message assembly/sending |
| `NjamsCommands.java` | Instruction listener registry, built-in command dispatch (send-project-message, ping, replay, get-request-handler) |

Modified: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java` (shrinks to facade).
New test files (no copyright header needed) in `njams-sdk/src/test/java/com/im/njams/sdk/`:
`NjamsFacadeBaselineTest.java` (Task 1), `NjamsFacetApiTest.java` (Tasks 13–21).

**Source line references** below refer to `Njams.java` as of commit `3fa179dd`. "Move unchanged" means: cut the method/field from `Njams.java`, paste into the facet, keep the body literally identical except for renamed field/parameter references explicitly listed in the step.

---

## Stage 0 — Baseline coverage

### Task 1: Baseline tests pinning current `Njams` behavior

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacadeBaselineTest.java`

- [ ] **Step 1: Write the baseline test class**

This class pins the current observable behavior of every public `Njams` method that Tasks 3–21 migrate and that `NjamsTest` does not already cover — including the *lenient* after-start behaviors that the deprecated API must keep forever.

```java
package com.im.njams.sdk;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.ReplayResponse;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.layout.SimpleProcessModelLayouter;
import com.im.njams.sdk.model.svg.NjamsProcessDiagramFactory;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;

/**
 * Pins the current behavior of all public Njams methods migrated by SDK-359,
 * BEFORE the refactoring. Must pass unchanged against the refactored class.
 */
public class NjamsFacadeBaselineTest {

    private Njams njams;

    @Before
    public void setUp() {
        njams = new Njams(Path.of("SDK4", "TEST"), "4.1.1", "sdk4", TestReceiver.getSettings());
    }

    @After
    public void tearDown() {
        if (njams.isStarted()) {
            njams.stop();
        }
        TestSender.setSenderMock(null);
    }

    // --- metadata ---

    @Test
    public void categoryIsUppercased() {
        assertEquals("SDK4", njams.getCategory());
    }

    @Test
    public void clientPathIsReturned() {
        assertEquals(Path.of("SDK4", "TEST"), njams.getClientPath());
    }

    @Test
    public void clientSessionIdAndCommunicationSessionIdAreTheSame() {
        assertNotNull(njams.getClientSessionId());
        assertEquals(njams.getClientSessionId(), njams.getCommunicationSessionId());
    }

    @Test
    public void clientVersionComesFromConstructorWhenNoVersionFile() {
        assertEquals("4.1.1", njams.getClientVersion());
    }

    @Test
    public void sdkVersionIsNeverNull() {
        assertNotNull(njams.getSdkVersion());
    }

    @Test
    public void machineIsNeverNull() {
        assertNotNull(njams.getMachine());
    }

    @Test
    public void runtimeVersionIsSettable() {
        assertNull(njams.getRuntimeVersion());
        njams.setRuntimeVersion("rt-1");
        assertEquals("rt-1", njams.getRuntimeVersion());
    }

    @Test
    public void addGlobalVariablesMergesIntoExisting() {
        Map<String, String> first = new HashMap<>();
        first.put("a", "1");
        njams.addGlobalVariables(first);
        Map<String, String> second = new HashMap<>();
        second.put("b", "2");
        second.put("a", "overwritten");
        njams.addGlobalVariables(second);
        assertEquals("overwritten", njams.getGlobalVariables().get("a"));
        assertEquals("2", njams.getGlobalVariables().get("b"));
    }

    @Test
    public void addGlobalVariablesAfterStartIsLenient() {
        njams.start();
        Map<String, String> late = new HashMap<>();
        late.put("late", "x");
        njams.addGlobalVariables(late); // must NOT throw (pinned lenient behavior)
        assertEquals("x", njams.getGlobalVariables().get("late"));
    }

    @Test
    public void setRuntimeVersionAfterStartIsLenient() {
        njams.start();
        njams.setRuntimeVersion("late-rt"); // must NOT throw
        assertEquals("late-rt", njams.getRuntimeVersion());
    }

    @Test
    public void setGlobalVariablesPatternAfterStartIsLenient() {
        njams.start();
        njams.setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)"); // must NOT throw
        assertNotNull(njams.getGlobalVariablesPattern());
    }

    // --- features / container mode ---

    @Test
    public void inherentFeaturesArePresentByDefault() {
        assertTrue(njams.hasFeature(Njams.Feature.EXPRESSION_TEST));
        assertTrue(njams.hasFeature(Njams.Feature.PING));
        assertTrue(njams.hasFeature(Njams.Feature.COMMANDS_SPLIT));
    }

    @Test
    public void addFeatureIsIdempotent() {
        njams.addFeature(Njams.Feature.INJECTION);
        njams.addFeature(Njams.Feature.INJECTION);
        assertEquals(1, njams.getFeatures().stream()
            .filter(f -> f == Njams.Feature.INJECTION).count());
    }

    @Test
    public void removeFeatureRemoves() {
        njams.addFeature(Njams.Feature.INJECTION);
        njams.removeFeature(Njams.Feature.INJECTION);
        assertFalse(njams.hasFeature(Njams.Feature.INJECTION));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void removingInherentFeatureThrows() {
        njams.removeFeature(Njams.Feature.PING);
    }

    @Test
    public void getFeaturesReturnsACopy() {
        List<Njams.Feature> features = njams.getFeatures();
        features.clear();
        assertTrue(njams.hasFeature(Njams.Feature.PING));
    }

    @Test
    public void addFeatureAfterStartIsLenient() {
        njams.start();
        njams.addFeature(Njams.Feature.INJECTION); // must NOT throw
        assertTrue(njams.hasFeature(Njams.Feature.INJECTION));
    }

    @Test
    public void containerModeIsOnByDefaultAndSettableBeforeStart() {
        assertTrue(njams.isContainerMode());
        njams.setContainerMode(false);
        assertFalse(njams.isContainerMode());
        assertFalse(njams.hasFeature(Njams.Feature.CONTAINER_MODE));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setContainerModeAfterStartThrows() {
        njams.start();
        njams.setContainerMode(false);
    }

    // --- replay handler ---

    @Test
    public void setReplayHandlerTogglesReplayFeature() {
        assertFalse(njams.hasFeature(Njams.Feature.REPLAY));
        njams.setReplayHandler(request -> new ReplayResponse());
        assertTrue(njams.hasFeature(Njams.Feature.REPLAY));
        assertNotNull(njams.getReplayHandler());
        njams.setReplayHandler(null);
        assertFalse(njams.hasFeature(Njams.Feature.REPLAY));
        assertNull(njams.getReplayHandler());
    }

    @Test
    public void setReplayHandlerAfterStartIsLenient() {
        njams.start();
        njams.setReplayHandler(request -> new ReplayResponse()); // must NOT throw
        assertTrue(njams.hasFeature(Njams.Feature.REPLAY));
    }

    // --- process models ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void getProcessModelThrowsWhenAbsent() {
        njams.getProcessModel(Path.of("MISSING"));
    }

    @Test
    public void createProcessRegistersModelUnderAbsolutePath() {
        ProcessModel created = njams.createProcess(Path.of("P1"));
        assertSame(created, njams.getProcessModel(Path.of("P1")));
        assertEquals(1, njams.getProcessModels().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getProcessModelsIsUnmodifiable() {
        njams.createProcess(Path.of("P1"));
        njams.getProcessModels().clear();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void addProcessModelOfForeignInstanceThrows() {
        Njams other = new Njams(Path.of("OTHER"), "1.0", "X", TestReceiver.getSettings());
        ProcessModel foreign = other.createProcess(Path.of("P1"));
        njams.addProcessModel(foreign);
    }

    @Test
    public void addProcessModelIgnoresNull() {
        njams.addProcessModel(null); // must NOT throw
        assertTrue(njams.getProcessModels().isEmpty());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setTreeElementTypeForUnknownPathThrows() {
        njams.setTreeElementType(Path.of("DOES", "NOT", "EXIST"), "some.type");
    }

    @Test
    public void setTreeElementTypeForClientPathWorks() {
        njams.setTreeElementType(Path.of("SDK4", "TEST"), "custom.type"); // client path exists in tree
    }

    @Test
    public void layouterAndDiagramFactoryAreReplaceable() {
        SimpleProcessModelLayouter layouter = new SimpleProcessModelLayouter();
        njams.setProcessModelLayouter(layouter);
        assertSame(layouter, njams.getProcessModelLayouter());

        ProcessDiagramFactory factory = new NjamsProcessDiagramFactory(njams);
        njams.setProcessDiagramFactory(factory);
        assertSame(factory, njams.getProcessDiagramFactory());
    }

    @Test
    public void addImageAfterStartIsLenient() {
        njams.start();
        njams.addImage("late.image", "images/root.png"); // must NOT throw
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void sendAdditionalProcessBeforeStartThrows() {
        ProcessModel model = njams.createProcess(Path.of("P1"));
        njams.sendAdditionalProcess(model);
    }

    @Test
    public void sendAdditionalProcessSendsProjectMessageWithThatProcess() throws InterruptedException {
        njams.start();
        ProcessModel model = njams.createProcess(Path.of("LAZY"));
        CapturingSender capturing = new CapturingSender();
        TestSender.setSenderMock(capturing);
        njams.sendAdditionalProcess(model);
        ProjectMessage sent = capturing.awaitProjectMessage();
        assertNotNull(sent);
        assertEquals(1, sent.getProcesses().size());
    }

    @Test
    public void sendProjectMessageContainsAddedImage() throws InterruptedException {
        njams.addImage("my.image", "images/root.png");
        njams.start();
        CapturingSender capturing = new CapturingSender();
        TestSender.setSenderMock(capturing);
        njams.sendProjectMessage();
        ProjectMessage sent = capturing.awaitProjectMessage();
        assertNotNull(sent);
        assertTrue(sent.getImages().containsKey("my.image"));
    }

    // --- jobs ---

    @Test
    public void jobLifecycleAfterStart() {
        njams.start();
        ProcessModel model = njams.createProcess(Path.of("P1"));
        Job job = model.createJob();
        assertSame(job, njams.getJobById(job.getJobId()));
        assertEquals(1, njams.getJobs().size());
        njams.removeJob(job.getJobId());
        assertNull(njams.getJobById(job.getJobId()));
        assertTrue(njams.getJobs().isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getJobsIsUnmodifiable() {
        njams.start();
        njams.getJobs().clear();
    }

    // --- instruction listeners / commands ---

    @Test
    public void instructionListenersAreAddableAndRemovable() {
        InstructionListener listener = instruction -> { };
        int before = njams.getInstructionListeners().size();
        njams.addInstructionListener(listener);
        assertEquals(before + 1, njams.getInstructionListeners().size());
        njams.removeInstructionListener(listener);
        assertEquals(before, njams.getInstructionListeners().size());
    }

    @Test
    public void getInstructionListenersReturnsACopy() {
        InstructionListener listener = instruction -> { };
        njams.addInstructionListener(listener);
        njams.getInstructionListeners().clear();
        assertTrue(njams.getInstructionListeners().contains(listener));
    }

    @Test
    public void pingInstructionIsAnswered() {
        Instruction inst = instructionFor(Command.PING);
        njams.onInstruction(inst);
        Response resp = inst.getResponse();
        assertEquals(0, resp.getResultCode());
        assertEquals("Pong", resp.getResultMessage());
        assertEquals(njams.getClientSessionId(), resp.getParameters().get("clientId"));
        assertEquals(njams.getCategory(), resp.getParameters().get("category"));
    }

    @Test
    public void getRequestHandlerInstructionReturnsClientId() {
        Instruction inst = instructionFor(Command.GET_REQUEST_HANDLER);
        njams.onInstruction(inst);
        assertEquals(0, inst.getResponse().getResultCode());
        assertEquals(njams.getClientSessionId(), inst.getResponseParameterByName("clientId"));
    }

    @Test
    public void unsupportedCommandIsRejected() {
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand("noSuchCommand");
        inst.setRequest(req);
        njams.onInstruction(inst);
        assertEquals(1, inst.getResponse().getResultCode());
    }

    // --- configuration ---

    @Test
    public void logModeDefaultsToComplete() {
        assertEquals(LogMode.COMPLETE, njams.getLogMode());
    }

    @Test
    public void isExcludedIsFalseByDefaultAndNullSafe() {
        assertFalse(njams.isExcluded(Path.of("P1")));
        assertFalse(njams.isExcluded(null));
    }

    @Test
    public void configurationIsNeverNull() {
        assertNotNull(njams.getConfiguration());
    }

    // --- argos ---

    @Test
    public void argosCollectorAddAndRemoveDoNotThrow() {
        com.im.njams.sdk.argos.ArgosMultiCollector<?> collector =
            new com.im.njams.sdk.argos.jvm.JVMCollector(njams,
                new com.im.njams.sdk.argos.ArgosComponent("id", "name", "container", "measurement", "type"));
        njams.addArgosCollector(collector);
        njams.removeArgosCollector(collector);
    }

    // --- sender ---

    @Test
    public void getSenderReturnsNonNullAndIsCached() {
        assertNotNull(njams.getSender());
        assertSame(njams.getSender(), njams.getSender());
    }

    // --- helpers ---

    private static Instruction instructionFor(Command command) {
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(command.commandString());
        inst.setRequest(req);
        return inst;
    }

    /** Same pattern as NjamsTest.CapturingSender. */
    private static final class CapturingSender extends AbstractSender {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile ProjectMessage lastProjectMessage;

        @Override
        public String getName() {
            return "CAPTURING";
        }

        @Override
        public void send(CommonMessage msg, String clientSessionId) {
            if (msg instanceof ProjectMessage) {
                lastProjectMessage = (ProjectMessage) msg;
                latch.countDown();
            }
        }

        ProjectMessage awaitProjectMessage() throws InterruptedException {
            latch.await(5, TimeUnit.SECONDS);
            return lastProjectMessage;
        }

        @Override
        protected void send(LogMessage msg, String clientSessionId) {
        }

        @Override
        protected void send(ProjectMessage msg, String clientSessionId) {
        }

        @Override
        protected void send(TraceMessage msg, String clientSessionId) {
        }
    }
}
```

Compile-check notes for the implementer: verify the constructor signatures of `JVMCollector`/`ArgosComponent` and the existence of `SimpleProcessModelLayouter` before running (`Grep` for `class JVMCollector`, `class ArgosComponent`, `class SimpleProcessModelLayouter` under `njams-sdk/src/main/java`). If a signature differs, adapt the *test* to the real signature — never the production code. If `ReplayResponse` requires fields for the lambda, an empty `new ReplayResponse()` is fine (the handler is only registered, not invoked, except in the toggle tests).

- [ ] **Step 2: Run the new tests — they must pass against the UNMODIFIED class**

Run: `mvn test -Dtest=NjamsFacadeBaselineTest -pl njams-sdk`
Expected: all PASS. Any failure means the test mis-pins current behavior — fix the test (read the production code to learn the actual behavior), never the production code.

- [ ] **Step 3: Verify coverage of `Njams` is complete**

Run: `mvn clean test -Psonar -pl njams-sdk`
Open `njams-sdk/target/site/jacoco/com.im.njams.sdk/Njams.java.html`. Every *public* method that Tasks 3–21 migrate must show coverage (private helpers are covered transitively). If any migrated public method is red, add a pinning test for it to `NjamsFacadeBaselineTest` following the same style, and re-run.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacadeBaselineTest.java
git commit -m "SDK-359 #comment Pin baseline behavior of all Njams methods before facade refactoring"
```

---

## Stage 1 — Extraction with pure delegation

Rules for ALL extraction tasks (2–11):

- Facet classes are `public final`, live in package `com.im.njams.sdk`, have **package-private constructors**, carry the standard copyright header and full Javadoc on every public member.
- `Njams` keeps every existing public method; each body becomes a one-line delegation. **No signature, return type, or behavior changes.** No `@Deprecated` yet (that is Stage 2+3).
- After each task: `mvn test -pl njams-sdk` → all tests (incl. baseline) pass; then commit.
- Do not add the public accessor methods (`jobs()` etc.) yet.

### Task 2: `LifecycleState`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/LifecycleState.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java` (replace `started` field, lines 254, 256)

- [ ] **Step 1: Create the class** (package-private → no public-API impact, but still gets the copyright header)

```java
package com.im.njams.sdk;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Holds the started-state of an {@link Njams} instance and provides the uniform
 * phase-guard checks shared by all facets.
 */
final class LifecycleState {

    static final String NOT_STARTED_EXCEPTION_MESSAGE = "The instance needs to be started first!";

    private volatile boolean started = false;

    boolean isStarted() {
        return started;
    }

    void setStarted(boolean started) {
        this.started = started;
    }

    /** Throws if the instance has not been started yet (message identical to current behavior). */
    void requireStarted() {
        if (!started) {
            throw new NjamsSdkRuntimeException(NOT_STARTED_EXCEPTION_MESSAGE);
        }
    }

    /**
     * Throws if the instance has already been started. Used by design-time facet operations
     * whose data is announced to the nJAMS server at start and cannot be changed afterwards.
     *
     * @param operation description used in the error message, e.g. "NjamsFeatures.add"
     */
    void requireNotStarted(String operation) {
        if (started) {
            throw new NjamsSdkRuntimeException(
                operation + " is not allowed after start(): this information is announced to the nJAMS server"
                    + " when the client starts and cannot be changed afterwards.");
        }
    }
}
```

- [ ] **Step 2: Use it in `Njams`**

In `Njams.java`: delete `private boolean started = false;` (line 254) and the `NOT_STARTED_EXCEPTION_MESSAGE` constant (line 256). Add field `private final LifecycleState lifecycle = new LifecycleState();`. Replace:
- `isStarted()` body → `return lifecycle.isStarted();`
- `started = true;` in `start()` → `lifecycle.setStarted(true);`
- `started = false;` in `stop()` → `lifecycle.setStarted(false);`
- the `!isStarted()` throw in `addJob` → `lifecycle.requireStarted();`
- the `!isStarted()` throw in `stop()` → `lifecycle.requireStarted();`
- references to `NOT_STARTED_EXCEPTION_MESSAGE` → `LifecycleState.NOT_STARTED_EXCEPTION_MESSAGE`
- the `started` read in `beginConnect()` (line 582) → `lifecycle.isStarted()`

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl njams-sdk`
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/LifecycleState.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Introduce LifecycleState as shared phase-guard for the facade split"
```

### Task 3: Extract `NjamsSerializers`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsSerializers.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`

- [ ] **Step 1: Create the facet** — complete code (bodies moved unchanged from `Njams.java:1250-1382`, constants from lines 189-190; only the holder class changes):

```java
package com.im.njams.sdk;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.serializer.StringSerializer;

/**
 * Owns the {@link Serializer} registry of an {@link Njams} client and serializes
 * arbitrary objects to strings for activity data. Obtain via {@code njams.serializers()}.
 */
public final class NjamsSerializers {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsSerializers.class);

    private static final Serializer<Object> DEFAULT_SERIALIZER = new StringSerializer<>();
    private static final Serializer<Object> NO_SERIALIZER = (o, sizeLimit) -> null;

    private final HashMap<Class<?>, Serializer<?>> serializers = new HashMap<>();
    private final HashMap<Class<?>, Serializer<?>> cachedSerializers = new HashMap<>();

    NjamsSerializers() {
        // created by Njams only
    }

    // public methods add / remove / get / find / serialize(T) / serialize(T, int):
    // move the bodies of Njams.addSerializer, removeSerializer, getSerializer,
    // findSerializer, serialize(T), serialize(T, int) here UNCHANGED
    // (Njams.java:1250-1382), renaming only the method names:
    //   addSerializer -> add, removeSerializer -> remove, getSerializer -> get,
    //   findSerializer -> find, serialize stays serialize.
    // Internal self-calls inside the moved bodies are renamed accordingly
    // (findSerializer -> find, getSerializer -> get).
    // Keep the existing Javadoc of each method, adjusting {@link} self-references
    // to the new names.
}
```

- [ ] **Step 2: Delegate from `Njams`**

In `Njams.java`: delete fields `serializers`, `cachedSerializers` (lines 237-240) and constants `DEFAULT_SERIALIZER`/`NO_SERIALIZER` (189-190). Add `private final NjamsSerializers serializers = new NjamsSerializers();` (initialize at field declaration). Replace the six method bodies with delegations, keeping signatures and Javadoc:

```java
public <T> Serializer<T> addSerializer(final Class<T> key, final Serializer<? super T> serializer) {
    return serializers.add(key, serializer);
}

public <T> Serializer<T> removeSerializer(final Class<T> key) {
    return serializers.remove(key);
}

public <T> Serializer<T> getSerializer(final Class<T> key) {
    return serializers.get(key);
}

public <T> Serializer<? super T> findSerializer(final Class<T> clazz) {
    return serializers.find(clazz);
}

public <T> String serialize(final T t) {
    return serializers.serialize(t);
}

public <T> String serialize(final T t, final int sizeLimit) {
    return serializers.serialize(t, sizeLimit);
}
```

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl njams-sdk`
Expected: all PASS — `NjamsTest.testSerializer`, `serializeWithSizeLimit*` and the baseline suite pin this behavior.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsSerializers.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Extract NjamsSerializers facet, Njams delegates"
```

### Task 4: Extract `NjamsArgos`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsArgos.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`

- [ ] **Step 1: Create the facet** (state from `Njams.java:262-263`, init from 298-299, methods from 319-327, stop-cleanup from 679-681):

```java
package com.im.njams.sdk;

import java.util.ArrayList;
import java.util.Collection;

import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.argos.ArgosSender;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Owns Argos metric collector registration for an {@link Njams} client.
 * Obtain via {@code njams.argos()}.
 */
public final class NjamsArgos {

    private final ArgosSender argosSender;
    private final Collection<ArgosMultiCollector<?>> collectors = new ArrayList<>();

    NjamsArgos(ClientSettings settings) {
        argosSender = ArgosSender.getInstance();
        argosSender.init(settings);
    }

    /**
     * Adds a collector that will create statistics.
     *
     * @param collector The collector that collects statistics
     */
    public void add(ArgosMultiCollector<?> collector) {
        collectors.add(collector);
        argosSender.addArgosCollector(collector);
    }

    /**
     * Removes the given collector.
     *
     * @param collector The collector to remove
     */
    public void remove(ArgosMultiCollector<?> collector) {
        collectors.remove(collector);
        argosSender.removeArgosCollector(collector);
    }

    /** Deregisters all collectors; called from Njams.stop(). */
    void stop() {
        collectors.forEach(argosSender::removeArgosCollector);
        collectors.clear();
    }
}
```

Note the raw-type difference: today `addArgosCollector(ArgosMultiCollector collector)` is raw. The deprecated `Njams` method keeps the raw signature; the facet uses `ArgosMultiCollector<?>`.

- [ ] **Step 2: Delegate from `Njams`**

Delete fields `argosSender`, `argosCollectors` (262-263) and the two init lines in the constructor (298-299); add `private final NjamsArgos argos;` initialized in the constructor (`argos = new NjamsArgos(settings);` at the former init position). Replace bodies of `addArgosCollector`/`removeArgosCollector` with `argos.add(collector);` / `argos.remove(collector);`. In `stop()`, replace lines 679-681 with `argos.stop();`.

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl njams-sdk` — Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsArgos.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Extract NjamsArgos facet, Njams delegates"
```

### Task 5: Extract `NjamsFeatures`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsFeatures.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`

- [ ] **Step 1: Create the facet** (state from `Njams.java:243,255`; methods from 310-312, 507-526, 1420-1449). The `Feature` enum **stays nested in `Njams`** (moving it would break every external reference).

```java
package com.im.njams.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.im.njams.sdk.Njams.Feature;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Owns the optional-feature list and the container-mode flag of an {@link Njams} client.
 * The feature list is announced to the nJAMS server in the project message at start.
 * Obtain via {@code njams.features()}.
 */
public final class NjamsFeatures {

    private final LifecycleState lifecycle;
    private final List<Feature> features = new CopyOnWriteArrayList<>(Feature.INHERENT_FEATURES);
    private boolean containerMode = true;

    NjamsFeatures(LifecycleState lifecycle) {
        this.lifecycle = lifecycle;
    }

    /**
     * Returns the features of this client.
     *
     * @return copy of the current feature list
     */
    public List<Feature> list() {
        return new ArrayList<>(features);
    }

    /**
     * Adds a feature to the feature list.
     *
     * @param feature to add
     */
    public void add(Feature feature) {
        if (!has(feature)) {
            features.add(feature);
        }
    }

    /**
     * Removes a feature from the feature list. Inherent SDK features cannot be removed.
     *
     * @param feature to remove
     */
    public void remove(Feature feature) {
        if (Feature.INHERENT_FEATURES.contains(feature)) {
            throw new NjamsSdkRuntimeException("Cannot remove inherent feature " + feature);
        }
        features.remove(feature);
    }

    /**
     * Returns whether the given feature is set.
     *
     * @param feature to check
     * @return true if present
     */
    public boolean has(Feature feature) {
        return features.contains(feature);
    }

    /**
     * Returns whether container-mode is enabled.
     *
     * @return true if container-mode is enabled
     */
    public boolean isContainerMode() {
        return containerMode;
    }

    /**
     * Enables or disables container-mode. Only allowed before the client is started.
     *
     * @param enabled true to enable
     */
    public void setContainerMode(boolean enabled) {
        if (lifecycle.isStarted()) {
            throw new NjamsSdkRuntimeException("Client is already started.");
        }
        containerMode = enabled;
        if (containerMode) {
            add(Feature.CONTAINER_MODE);
        } else {
            remove(Feature.CONTAINER_MODE);
        }
    }
}
```

`Feature.INHERENT_FEATURES` is currently `private static final` inside the enum (`Njams.java:129-130`) — change it to package-private (remove `private`): same package, no public-API change.

- [ ] **Step 2: Delegate from `Njams`**

Delete fields `features` (243), `containerMode` (255). Add `private final NjamsFeatures features = new NjamsFeatures(lifecycle);` (declare AFTER the `lifecycle` field so initialization order is valid). Delegate: `getFeatures()` → `return features.list();`, `addFeature(f)` → `features.add(f);`, `removeFeature(f)` → `features.remove(f);`, `hasFeature(f)` → `return features.has(f);`, `isContainerMode()` → `return features.isContainerMode();`, `setContainerMode(b)` → `features.setContainerMode(b);`. `initContainerMode()` (310-312) stays as-is (it calls `setContainerMode`). The two `features.stream()` usages in `prepareProjectMessage()` (822) and `createPingResponse()` (1209) become `features.list().stream()`. `setReplayHandler` keeps calling `addFeature`/`removeFeature`.

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl njams-sdk` — Expected: all PASS (baseline pins idempotent add, inherent-feature throw, copy semantics, container-mode guard).

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsFeatures.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Extract NjamsFeatures facet, Njams delegates"
```

### Task 6: Extract `NjamsMetadata`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsMetadata.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`

- [ ] **Step 1: Create the facet.** Moves: fields `category`, `globalVariables`, `globalVariablesPattern`, `versions`, `clientPath`, `clientSessionId`, `startTime`, `machine`, `runtimeVersion`, constant `NAMED_GROUP_DECLARATION`, constant `VERSION_FILES` (`Njams.java:168,192-227,203,206-211,252-253`); methods `getCategory`, `getClientPath`, `getCommunicationSessionId`→drop (Njams delegates both old names to one facet method), `getClientVersion`, `getSdkVersion`, `getRuntimeVersion`, `setRuntimeVersion`, `getGlobalVariables`, `addGlobalVariables`, `getGlobalVariablesPattern`, `setGlobalVariablesPattern`, `validateGlobalVariablesPattern` (358-477), `getClientSessionId` (662-664), `readVersionsFromVersionFile` (1054-1085), `printStartupBanner` (1087-1102), `setMachine` (1387-1394), `getMachine` (1413-1415), `getStartTime` (new package-private getter; `prepareProjectMessage` needs it).

Class skeleton (move method bodies unchanged; constructor performs what the `Njams` constructor did for these fields):

```java
package com.im.njams.sdk;

// imports as needed by the moved code

/**
 * Owns the identifying metadata of an {@link Njams} client: path, category, versions,
 * machine, session id, and the global variables announced in the project message.
 * Obtain via {@code njams.metadata()}.
 */
public final class NjamsMetadata {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsMetadata.class);

    // moved constants: VERSION_FILES, NAMED_GROUP_DECLARATION
    // moved fields: category, clientPath, clientSessionId, startTime, versions,
    //               globalVariables, globalVariablesPattern, machine, runtimeVersion
    private final Object projectMessageLock;

    NjamsMetadata(Path clientPath, String version, String runtimeVersion, String category,
        Object projectMessageLock) {
        this.clientPath = clientPath;
        this.category = category == null ? null : category.toUpperCase();
        this.runtimeVersion = runtimeVersion;
        this.projectMessageLock = projectMessageLock;
        startTime = DateTimeUtility.now();
        clientSessionId = UUID.randomUUID().toString();
        readVersionsFromVersionFile(version);
        setMachine();
    }

    // public: getClientPath(), getCategory(), getClientVersion(), getSdkVersion(),
    //         getRuntimeVersion(), setRuntimeVersion(String), getMachine(),
    //         getClientSessionId(), getGlobalVariables(), addGlobalVariables(Map),
    //         getGlobalVariablesPattern(), setGlobalVariablesPattern(String)
    // package-private: getStartTime(), getVersions() (for project message + banner + ping),
    //                  printStartupBanner(ClientSettings)
    // private: readVersionsFromVersionFile(String), setMachine(),
    //          static validateGlobalVariablesPattern(String)
}
```

`addGlobalVariables` synchronized today on the `processModels` map; in the facet it synchronizes on `projectMessageLock` (the shared lock object introduced here and reused by `NjamsProcesses` in Task 10 — see Step 2).

- [ ] **Step 2: Wire in `Njams`**

Add field `final Object projectMessageLock = new Object();` (package-private — `NjamsProcesses` reuses it in Task 10). Add `private final NjamsMetadata metadata;` constructed early in the `Njams` constructor:

```java
metadata = new NjamsMetadata(path, version, runtimeVersion, category, projectMessageLock);
```

Remove the moved fields/constants/methods, then delegate (every old method keeps its exact signature and Javadoc):

```java
public String getCategory() { return metadata.getCategory(); }
public Path getClientPath() { return metadata.getClientPath(); }
public String getCommunicationSessionId() { return metadata.getClientSessionId(); }
public String getClientSessionId() { return metadata.getClientSessionId(); }
public String getClientVersion() { return metadata.getClientVersion(); }
public String getSdkVersion() { return metadata.getSdkVersion(); }
public String getRuntimeVersion() { return metadata.getRuntimeVersion(); }
public void setRuntimeVersion(String runtimeVersion) { metadata.setRuntimeVersion(runtimeVersion); }
public Map<String, String> getGlobalVariables() { return metadata.getGlobalVariables(); }
public void addGlobalVariables(Map<String, String> globalVariables) { metadata.addGlobalVariables(globalVariables); }
public String getGlobalVariablesPattern() { return metadata.getGlobalVariablesPattern(); }
public void setGlobalVariablesPattern(String p) { metadata.setGlobalVariablesPattern(p); }
public String getMachine() { return metadata.getMachine(); }
```

Update internal users inside `Njams`: `prepareProjectMessage` (813-827), `sendProjectMessage`, `createPingResponse`, `equals`/`hashCode` (use `metadata.getClientPath()`), `start()` log line, constructor banner call → `metadata.printStartupBanner(settings);`. Constructor lines that moved (291-294, 302-303, 305-306) are removed. **Synchronization change:** all `synchronized (processModels)` blocks that guard global variables/images/tree elements switch to `synchronized (projectMessageLock)` in this task, and the map operations keep working (same lock everywhere = same mutual exclusion as before; `processModels` accesses switch to the same lock object too — do this globally in this task to keep a single lock):
replace every `synchronized (processModels)` in `Njams.java` with `synchronized (projectMessageLock)`.

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl njams-sdk` — Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsMetadata.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Extract NjamsMetadata facet incl. global variables, Njams delegates"
```

### Task 7: Extract `NjamsJobs`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsJobs.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`

- [ ] **Step 1: Create the facet.** Moves: `jobs` map (232), `replayedLogIds` (260), `addJob` (1022-1036), `removeJob` (1043-1046), `getJobById` (751-753), `getJobs` (761-763), `setReplayMarker` (1218-1235).

```java
package com.im.njams.sdk;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Owns the registry of currently running {@link Job}s of an {@link Njams} client.
 * Obtain via {@code njams.jobs()}. This facet is part of the runtime monitoring path.
 */
public final class NjamsJobs {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsJobs.class);

    private final LifecycleState lifecycle;
    private final ConcurrentMap<String, Job> jobs = new ConcurrentHashMap<>();
    // logId of replayed job -> deep-trace flag from the replay request
    private final Map<String, Boolean> replayedLogIds = new HashMap<>();

    NjamsJobs(LifecycleState lifecycle) {
        this.lifecycle = lifecycle;
    }

    // public add(Job)    <- body of Njams.addJob (1022-1036); the started check is
    //                       lifecycle.requireStarted()
    // public remove(String jobId)  <- body of Njams.removeJob (1043-1046)
    // public get(String jobId)     <- body of Njams.getJobById (751-753)
    // public getAll()              <- body of Njams.getJobs (761-763)
    // package-private setReplayMarker(String logId, boolean deepTrace)
    //                              <- body of Njams.setReplayMarker (1218-1235);
    //                                 its internal getJobs() call becomes getAll()
}
```

- [ ] **Step 2: Delegate from `Njams`**

Add `private final NjamsJobs jobs = new NjamsJobs(lifecycle);` (declare after `lifecycle`). Delegate: `addJob(job)` → `jobs.add(job);`, `removeJob(id)` → `jobs.remove(id);`, `getJobById(id)` → `return jobs.get(id);`, `getJobs()` → `return jobs.getAll();`. The internal caller `handleReplayRequest` → `setReplayMarker` becomes `jobs.setReplayMarker(...)`. Delete moved members.

`LogMessageFlushTask`/`CleanTracepointsTask` call `njams.getJobs()` — unchanged, still works via delegation.

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl njams-sdk` — Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsJobs.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Extract NjamsJobs facet with replay markers, Njams delegates"
```

### Task 8: Extract `NjamsReplay`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsReplay.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`

- [ ] **Step 1: Create the facet.** Moves: `replayHandler` field (258), `getReplayHandler` (484-486), `setReplayHandler` (494-501), `handleReplayRequest` (1176-1195).

```java
package com.im.njams.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams.Feature;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.ReplayRequest;
import com.im.njams.sdk.communication.ReplayResponse;

/**
 * Owns the {@link ReplayHandler} of an {@link Njams} client and processes replay
 * instructions from the nJAMS server. Obtain via {@code njams.replay()}.
 */
public final class NjamsReplay {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsReplay.class);

    private final NjamsFeatures features;
    private final NjamsJobs jobs;
    private ReplayHandler replayHandler = null;

    NjamsReplay(NjamsFeatures features, NjamsJobs jobs) {
        this.features = features;
        this.jobs = jobs;
    }

    // public getHandler()  <- body of Njams.getReplayHandler (484-486)
    // public setHandler(ReplayHandler)  <- body of Njams.setReplayHandler (494-501);
    //        removeFeature/addFeature calls become features.remove(...)/features.add(...)
    // package-private handleReplayRequest(Instruction)  <- body of Njams.handleReplayRequest
    //        (1176-1195); setReplayMarker(...) call becomes jobs.setReplayMarker(...)
}
```

- [ ] **Step 2: Delegate from `Njams`**

Add `private final NjamsReplay replay = new NjamsReplay(features, jobs);` (after `features` and `jobs`). Delegate `getReplayHandler()` → `return replay.getHandler();`, `setReplayHandler(h)` → `replay.setHandler(h);`. In `onInstruction`, `handleReplayRequest(instruction)` → `replay.handleReplayRequest(instruction)`. Delete moved members.

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl njams-sdk` — Expected: all PASS (`NjamsTest.testOn*ReplayMessageInstruction` pins this).

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsReplay.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Extract NjamsReplay facet, Njams delegates"
```

### Task 9: Extract `NjamsConfiguration`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsConfiguration.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`

- [ ] **Step 1: Create the facet.** Moves: `configuration` field (251), `DEFAULT_CACHE_PROVIDER` (93), `loadConfigurationProvider` (332-342), `loadConfiguration` (347-352), `getLogMode` (1399-1401), `getConfiguration` (1406-1408), `isExcluded` (1465-1467), `initializeDataMasking` (1472-1487).

```java
package com.im.njams.sdk;

// imports as needed by the moved code

/**
 * Owns the server-driven runtime {@link Configuration} of an {@link Njams} client
 * (log mode, process exclusions, tracepoints) and its provider.
 * Obtain via {@code njams.configuration()}.
 */
public final class NjamsConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsConfiguration.class);

    private static final String DEFAULT_CACHE_PROVIDER = FileConfigurationProvider.NAME;

    private final ClientSettings settings;
    private Configuration configuration;

    NjamsConfiguration(ClientSettings settings, Njams njams) {
        this.settings = settings;
        loadConfigurationProvider(njams);
    }

    /**
     * Returns the runtime configuration received from the nJAMS server.
     *
     * @return the configuration, never null
     */
    public Configuration get() {
        return configuration;
    }

    // public getLogMode()       <- body of Njams.getLogMode (uses get())
    // public isExcluded(Path)   <- body of Njams.isExcluded
    // package-private load()                 <- body of Njams.loadConfiguration
    // package-private initializeDataMasking() <- body of Njams.initializeDataMasking
    //                                            (reads settings field + configuration)
    // private loadConfigurationProvider(Njams) <- body of Njams.loadConfigurationProvider;
    //         "new ConfigurationProviderFactory(settings, this)" becomes
    //         "new ConfigurationProviderFactory(settings, njams)"
}
```

The `ConfigurationProviderFactory` and `FileConfigurationProvider` APIs require the `Njams` instance — the facet receives it in the constructor for exactly that call and does not store it.

- [ ] **Step 2: Delegate from `Njams`**

Add `private final NjamsConfiguration configuration;`, constructed in the `Njams` constructor at the position of the former `loadConfigurationProvider()` call: `configuration = new NjamsConfiguration(settings, this);`. Delegate `getConfiguration()` → `return configuration.get();`, `getLogMode()` → `return configuration.getLogMode();`, `isExcluded(p)` → `return configuration.isExcluded(p);`. In `start()`: `loadConfiguration();` → `configuration.load();` and `initializeDataMasking();` → `configuration.initializeDataMasking();`. `prepareProjectMessage` uses `configuration.get().getLogMode()` / `configuration.get().isRecording()`. `ConfigurationInstructionListener` keeps receiving `this` (it calls public `Njams` methods — unchanged). Delete moved members.

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl njams-sdk` — Expected: all PASS (data-masking tests in `NjamsTest` pin `initializeDataMasking`).

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsConfiguration.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Extract NjamsConfiguration facet, Njams delegates"
```

### Task 10: Extract `NjamsProcesses`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsProcesses.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`

- [ ] **Step 1: Create the facet.** Moves: fields `processModels` (197), `images` (200), `treeElements` (226), `processDiagramFactory` (228), `processModelLayouter` (230), taxonomy constants (84-91); methods `addImage` both overloads (534-547), `setProcessDiagramFactory` (552-554), `getProcessModel` (703-713), `hasProcessModel` (721-732), `getProcessModels` (739-743), `createProcess` (772-780), `addProcessModel` (788-807), `prepareProjectMessage` (813-827), `sendProjectMessage` (833-848), `sendAdditionalProcess` (856-865), `addDefaultImagesIfNeededAndAbsent` both (871-892), `createTreeElements` (898-914), `getTreeElementDefaultType` (924-934), `setTreeElementType` (942-951), `setStarters` (956-961), `addTreeElements` (963-982), `getProcessModelLayouter`/`setProcessModelLayouter` (987-996), `getProcessDiagramFactory` (1133-1135).

```java
package com.im.njams.sdk;

// imports as needed by the moved code

/**
 * Owns the process models, taxonomy tree, images and process diagram tooling of an
 * {@link Njams} client, and assembles and sends project messages.
 * Obtain via {@code njams.processes()}.
 */
public final class NjamsProcesses {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsProcesses.class);

    // moved taxonomy constants (DEFAULT_TAXONOMY_*)

    private final Njams njams; // required by ProcessModel's constructor and getSender()
    private final LifecycleState lifecycle;
    private final NjamsMetadata metadata;
    private final NjamsFeatures features;
    private final NjamsConfiguration configuration;
    private final Object projectMessageLock;

    // moved fields: processModels, images, treeElements, processDiagramFactory,
    //               processModelLayouter

    NjamsProcesses(Njams njams, LifecycleState lifecycle, NjamsMetadata metadata,
        NjamsFeatures features, NjamsConfiguration configuration, Object projectMessageLock) {
        this.njams = njams;
        this.lifecycle = lifecycle;
        this.metadata = metadata;
        this.features = features;
        this.configuration = configuration;
        this.projectMessageLock = projectMessageLock;
        processDiagramFactory = new NjamsProcessDiagramFactory(njams);
        processModelLayouter = new CommonBfsModelLayouter();
    }

    // public:  create(Path)            <- createProcess body; clientPath -> metadata.getClientPath()
    //          add(ProcessModel)       <- addProcessModel body; same substitution
    //          get(Path)               <- getProcessModel body; same substitution
    //          has(Path)               <- hasProcessModel body; same substitution
    //          getAll()                <- getProcessModels body
    //          addImage(String,String) / addImage(ImageSupplier)
    //          setTreeElementType(Path,String)
    //          getLayouter()/setLayouter(...)         <- get/setProcessModelLayouter bodies
    //          getDiagramFactory()/setDiagramFactory(...) <- get/setProcessDiagramFactory bodies
    //          send()                  <- sendProjectMessage body
    //          announce(ProcessModel)  <- sendAdditionalProcess body; the not-started check
    //                                     becomes lifecycle.requireStarted() wrapped to keep
    //                                     the EXACT current message:
    //                                     if (!lifecycle.isStarted()) { throw new
    //                                       NjamsSdkRuntimeException("Njams is not started. Please use createProcess Method instead"); }
    // package-private: createTreeElements(Path, TreeElementType)  (called by create/add)
    // private: prepareProjectMessage, addDefaultImagesIfNeededAndAbsent (both),
    //          getTreeElementDefaultType, setStarters, addTreeElements
    //
    // Substitutions inside moved bodies:
    //   clientPath            -> metadata.getClientPath()
    //   versions.get(...)     -> metadata.getVersions().get(...)
    //   getCategory()         -> metadata.getCategory()
    //   startTime             -> metadata.getStartTime()
    //   getMachine()          -> metadata.getMachine()
    //   clientSessionId       -> metadata.getClientSessionId()
    //   features.stream()     -> features.list().stream()
    //   configuration.getLogMode() -> configuration.get().getLogMode()
    //   getConfiguration().isRecording() -> configuration.get().isRecording()
    //   globalVariables / globalVariablesPattern -> metadata.getGlobalVariables() /
    //                                               metadata.getGlobalVariablesPattern()
    //   getSender().send(...) -> njams.getSender().send(...)
    //   synchronized (processModels) -> synchronized (projectMessageLock)
    //   getClientPath() in sendAdditionalProcess -> metadata.getClientPath()
    //   new ProcessModel(fullClientPath, this) -> new ProcessModel(fullClientPath, njams)
    //   processModel.getNjams() != this -> processModel.getNjams() != njams
}
```

- [ ] **Step 2: Delegate from `Njams`**

Add `private final NjamsProcesses processes;` constructed in the `Njams` constructor AFTER `metadata`, `features`, `configuration`:

```java
processes = new NjamsProcesses(this, lifecycle, metadata, features, configuration, projectMessageLock);
```

The constructor line `createTreeElements(path, TreeElementType.CLIENT);` becomes `processes.createTreeElements(path, TreeElementType.CLIENT);` and the diagram-factory/layouter init lines (296-297) are removed (now inside the facet). Delegations:

```java
public ProcessModel createProcess(final Path path) { return processes.create(path); }
public void addProcessModel(final ProcessModel processModel) { processes.add(processModel); }
public ProcessModel getProcessModel(final Path relativePath) { return processes.get(relativePath); }
public boolean hasProcessModel(final Path relativePath) { return processes.has(relativePath); }
public Collection<ProcessModel> getProcessModels() { return processes.getAll(); }
public void addImage(final String key, final String resourcePath) { processes.addImage(key, resourcePath); }
public void addImage(final ImageSupplier imageSupplier) { processes.addImage(imageSupplier); }
public void setTreeElementType(Path path, String type) { processes.setTreeElementType(path, type); }
public ProcessModelLayouter getProcessModelLayouter() { return processes.getLayouter(); }
public void setProcessModelLayouter(ProcessModelLayouter l) { processes.setLayouter(l); }
public ProcessDiagramFactory getProcessDiagramFactory() { return processes.getDiagramFactory(); }
public void setProcessDiagramFactory(ProcessDiagramFactory f) { processes.setDiagramFactory(f); }
public void sendProjectMessage() { processes.send(); }
public void sendAdditionalProcess(final ProcessModel model) { processes.announce(model); }
```

Delete moved members. `onInstruction`'s `sendProjectMessage();` call keeps working (delegates).

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl njams-sdk` — Expected: all PASS. This is the highest-risk task; if a project-message test fails, diff the moved `prepareProjectMessage`/`send` bodies against `git show 3fa179dd:njams-sdk/src/main/java/com/im/njams/sdk/Njams.java` line by line.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsProcesses.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Extract NjamsProcesses facet incl. project message assembly, Njams delegates"
```

### Task 11: Extract `NjamsCommands`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsCommands.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`

- [ ] **Step 1: Create the facet.** Moves: `instructionListeners` (234), `getInstructionListeners` (1107-1109), `addInstructionListener` (1117-1119), `removeInstructionListener` (1126-1128), the body of `onInstruction` (1143-1174), `createPingResponse` (1197-1211).

```java
package com.im.njams.sdk;

// imports as needed by the moved code

/**
 * Owns the {@link InstructionListener} registry of an {@link Njams} client and handles
 * the SDK built-in server commands (send-project-message, ping, replay, get-request-handler).
 * Obtain via {@code njams.commands()}.
 */
public final class NjamsCommands {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsCommands.class);

    private final NjamsProcesses processes;
    private final NjamsReplay replay;
    private final NjamsMetadata metadata;
    private final NjamsFeatures features;
    private final List<InstructionListener> instructionListeners = new ArrayList<>();

    NjamsCommands(NjamsProcesses processes, NjamsReplay replay, NjamsMetadata metadata,
        NjamsFeatures features) {
        this.processes = processes;
        this.replay = replay;
        this.metadata = metadata;
        this.features = features;
    }

    // public list()    <- body of getInstructionListeners (returns copy)
    // public add(InstructionListener)    <- body of addInstructionListener
    // public remove(InstructionListener) <- body of removeInstructionListener
    // package-private dispatch(Instruction) <- body of Njams.onInstruction; substitutions:
    //          sendProjectMessage() -> processes.send()
    //          handleReplayRequest(i) -> replay.handleReplayRequest(i)
    //          clientSessionId -> metadata.getClientSessionId()
    // private createPingResponse() <- body; substitutions:
    //          clientPath.toString() -> metadata.getClientPath().toString()
    //          getClientVersion()/getSdkVersion()/getRuntimeVersion()/getCategory()/getMachine()
    //              -> metadata.<same>()
    //          clientSessionId -> metadata.getClientSessionId()
    //          features.stream() -> features.list().stream()
}
```

- [ ] **Step 2: Delegate from `Njams`**

Add `private final NjamsCommands commands;` constructed after `processes` and `replay`:
`commands = new NjamsCommands(processes, replay, metadata, features);`. Delegate:

```java
public List<InstructionListener> getInstructionListeners() { return commands.list(); }
public void addInstructionListener(InstructionListener listener) { commands.add(listener); }
public void removeInstructionListener(InstructionListener listener) { commands.remove(listener); }

@Override
public void onInstruction(Instruction instruction) { commands.dispatch(instruction); }
```

`Njams` keeps `implements InstructionListener` (public API). In `start()`, `instructionListeners.add(this); instructionListeners.add(new ConfigurationInstructionListener(this));` becomes `commands.add(this); commands.add(new ConfigurationInstructionListener(this));` and in `stop()`, `instructionListeners.clear();` becomes a new package-private `commands.clear();` (one-line method: `void clear() { instructionListeners.clear(); }`). Delete moved members.

- [ ] **Step 3: Run all tests, then the full build once**

Run: `mvn test -pl njams-sdk` — Expected: all PASS.
Run: `mvn clean install -DskipTests` — Expected: BUILD SUCCESS (sample modules still compile — Stage 1 changed no public API).

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsCommands.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Extract NjamsCommands facet, Njams becomes a pure facade"
```

---

## Stage 2+3 — Accessors, phase guards, deprecations

Rules for Tasks 12–21:

- New public members get full Javadoc (checkstyle enforces it).
- Phase guards go ONLY into the facet's public methods. The deprecated `Njams` methods bypass them via package-private facet internals and log a WARN instead (lenient legacy contract — pinned by the baseline tests, which must keep passing untouched).
- Every deprecation comment must explain the replacement **usage** (accessor chain + call) and any contract difference. Template:

```java
/**
 * <original summary line kept>
 *
 * @param ... <kept>
 * @deprecated Use {@code njams.<facet>().<method>(...)} instead — obtain the facet via
 *             {@link #<facet>()} and call {@link <FacetType>#<method>}. <Contract difference
 *             sentence if any, e.g.: "Unlike this method, the replacement throws an
 *             NjamsSdkRuntimeException when called after start(), because this data is
 *             announced to the nJAMS server at start and a later change is never sent.">
 */
@Deprecated
```

- New tests go into `NjamsFacetApiTest.java` (created in Task 13, extended by every later task).

### Task 12: Facet accessors on `Njams`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`

- [ ] **Step 1: Add the nine accessors** (after the constructors; full Javadoc each — pattern shown for two, repeat for all nine: `metadata()`, `features()`, `processes()`, `jobs()`, `serializers()`, `replay()`, `commands()`, `argos()`, `configuration()`):

```java
/**
 * Provides access to the jobs of this client: the registry of currently running
 * {@link com.im.njams.sdk.logmessage.Job} instances.
 *
 * @return the jobs facet of this client, never <code>null</code>
 */
public NjamsJobs jobs() {
    return jobs;
}

/**
 * Provides access to the identifying metadata of this client: path, category, versions,
 * machine, session id, and the global variables announced to the nJAMS server at start.
 *
 * @return the metadata facet of this client, never <code>null</code>
 */
public NjamsMetadata metadata() {
    return metadata;
}
```

Note: the `configuration` field currently has type `NjamsConfiguration` already (Task 9) — accessor `configuration()` returns it directly.

- [ ] **Step 2: Run tests + Javadoc**

Run: `mvn test -pl njams-sdk` then `mvn javadoc:javadoc -pl njams-sdk`
Expected: tests PASS, Javadoc builds without errors.

- [ ] **Step 3: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-359 #comment Expose the nine facade facet accessors on Njams"
```

### Task 13: Guards + deprecations — metadata

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsMetadata.java`, `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java`

- [ ] **Step 1: Write failing guard tests**

```java
package com.im.njams.sdk;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.TestReceiver;

/**
 * Tests the new facet API of SDK-359: accessor identity, phase guards, and the
 * lenient behavior of the deprecated legacy methods.
 */
public class NjamsFacetApiTest {

    private Njams njams;

    @Before
    public void setUp() {
        njams = new Njams(Path.of("SDK4", "TEST"), "4.1.1", "sdk4", TestReceiver.getSettings());
    }

    @After
    public void tearDown() {
        if (njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void accessorsReturnTheSameInstanceEveryTime() {
        assertSame(njams.metadata(), njams.metadata());
        assertSame(njams.jobs(), njams.jobs());
        assertSame(njams.processes(), njams.processes());
        assertSame(njams.features(), njams.features());
        assertSame(njams.serializers(), njams.serializers());
        assertSame(njams.replay(), njams.replay());
        assertSame(njams.commands(), njams.commands());
        assertSame(njams.argos(), njams.argos());
        assertSame(njams.configuration(), njams.configuration());
    }

    // --- metadata guards ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newAddGlobalVariablesThrowsAfterStart() {
        njams.start();
        Map<String, String> vars = new HashMap<>();
        vars.put("late", "x");
        njams.metadata().addGlobalVariables(vars);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetGlobalVariablesPatternThrowsAfterStart() {
        njams.start();
        njams.metadata().setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetRuntimeVersionThrowsAfterStart() {
        njams.start();
        njams.metadata().setRuntimeVersion("late");
    }

    @Test
    public void newMetadataMutatorsWorkBeforeStart() {
        njams.metadata().setRuntimeVersion("rt");
        Map<String, String> vars = new HashMap<>();
        vars.put("a", "1");
        njams.metadata().addGlobalVariables(vars);
        njams.metadata().setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
        assertEquals("rt", njams.metadata().getRuntimeVersion());
        assertEquals("1", njams.metadata().getGlobalVariables().get("a"));
    }

    @Test
    public void deprecatedMetadataMutatorsStayLenientAfterStart() {
        njams.start();
        njams.setRuntimeVersion("late"); // WARN, no throw
        Map<String, String> vars = new HashMap<>();
        vars.put("late", "x");
        njams.addGlobalVariables(vars); // WARN, no throw
        assertEquals("late", njams.getRuntimeVersion());
        assertEquals("x", njams.getGlobalVariables().get("late"));
    }
}
```

Run: `mvn test -Dtest=NjamsFacetApiTest -pl njams-sdk`
Expected: FAIL — guard tests fail (no guards yet); `accessorsReturnTheSameInstanceEveryTime` passes already.

- [ ] **Step 2: Implement guards in `NjamsMetadata`**

`NjamsMetadata` gains a `LifecycleState lifecycle` constructor parameter (Njams passes `lifecycle`). For each mutator, split into guarded public + package-private internal:

```java
/**
 * Adds the given global variables to this instance's global variables. Global variables are
 * announced to the nJAMS server in the project message when the client starts.
 *
 * @param globalVariables The global variables to be added to this instance.
 * @throws NjamsSdkRuntimeException if the client has already been started — a later change
 *                                  would never reach the server
 */
public void addGlobalVariables(Map<String, String> globalVariables) {
    lifecycle.requireNotStarted("NjamsMetadata.addGlobalVariables");
    addGlobalVariablesInternal(globalVariables);
}

void addGlobalVariablesInternal(Map<String, String> globalVariables) {
    synchronized (projectMessageLock) {
        this.globalVariables.putAll(globalVariables);
    }
}
```

Same pattern for `setRuntimeVersion`/`setRuntimeVersionInternal` and `setGlobalVariablesPattern`/`setGlobalVariablesPatternInternal` (validation stays in the internal method so both paths validate).

- [ ] **Step 3: Deprecate the `Njams` legacy methods (lenient path + WARN)**

```java
/**
 * Adds the given global variables to this instance's global variables.
 *
 * @param globalVariables The global variables to be added to this instance.
 * @deprecated Use {@code njams.metadata().addGlobalVariables(globalVariables)} instead — obtain
 *             the facet via {@link #metadata()} and call
 *             {@link NjamsMetadata#addGlobalVariables(Map)}. Unlike this method, the replacement
 *             throws an {@link com.im.njams.sdk.common.NjamsSdkRuntimeException} when called
 *             after {@link #start()}, because global variables are announced to the nJAMS server
 *             at start and a later change is never sent.
 */
@Deprecated
public void addGlobalVariables(Map<String, String> globalVariables) {
    warnIfStarted("addGlobalVariables", "metadata().addGlobalVariables(...)");
    metadata.addGlobalVariablesInternal(globalVariables);
}
```

Add the shared helper once to `Njams`:

```java
private void warnIfStarted(String oldMethod, String replacement) {
    if (lifecycle.isStarted()) {
        LOG.warn("{} was called after start(); the change will not be sent to the nJAMS server."
            + " The replacement API {} rejects this call.", oldMethod, replacement);
    }
}
```

Apply the same pattern (deprecation Javadoc naming facet accessor + facet method + contract difference, lenient internal delegation) to: `setRuntimeVersion`, `setGlobalVariablesPattern`. Deprecate the pure getters too — they have no contract difference, so their tag is just the usage pointer, e.g.:

```java
/**
 * @return the clientPath
 * @deprecated Use {@code njams.metadata().getClientPath()} instead — obtain the facet via
 *             {@link #metadata()} and call {@link NjamsMetadata#getClientPath()}.
 */
@Deprecated
public Path getClientPath() { return metadata.getClientPath(); }
```

Deprecate this way: `getCategory`, `getClientPath`, `getClientVersion`, `getSdkVersion`, `getRuntimeVersion`, `getMachine`, `getGlobalVariables`, `getGlobalVariablesPattern`, `getClientSessionId`, and `getCommunicationSessionId` (both point to `NjamsMetadata#getClientSessionId()`; the comment for `getCommunicationSessionId` additionally notes it was a duplicate of `getClientSessionId`).

- [ ] **Step 4: Run the tests**

Run: `mvn test -pl njams-sdk`
Expected: ALL pass — including `NjamsFacadeBaselineTest.addGlobalVariablesAfterStartIsLenient` etc. (lenient path preserved) and the new guard tests.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsMetadata.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java
git commit -m "SDK-359 #comment Guard metadata mutators in new API, deprecate legacy metadata methods"
```

### Task 14: Guards + deprecations — features

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsFeatures.java`, `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java`

- [ ] **Step 1: Write failing tests** (append to `NjamsFacetApiTest`):

```java
@Test(expected = NjamsSdkRuntimeException.class)
public void newFeatureAddThrowsAfterStart() {
    njams.start();
    njams.features().add(Njams.Feature.INJECTION);
}

@Test(expected = NjamsSdkRuntimeException.class)
public void newFeatureRemoveThrowsAfterStart() {
    njams.features().add(Njams.Feature.INJECTION);
    njams.start();
    njams.features().remove(Njams.Feature.INJECTION);
}

@Test
public void deprecatedFeatureAddStaysLenientAfterStart() {
    njams.start();
    njams.addFeature(Njams.Feature.INJECTION); // WARN, no throw
    assertTrue(njams.hasFeature(Njams.Feature.INJECTION));
}
```

Run: `mvn test -Dtest=NjamsFacetApiTest -pl njams-sdk` — Expected: the two new guard tests FAIL.

- [ ] **Step 2: Implement** — in `NjamsFeatures`, split `add`/`remove` into guarded public + package-private `addInternal`/`removeInternal` (guard: `lifecycle.requireNotStarted("NjamsFeatures.add")` / `"NjamsFeatures.remove"`; the inherent-feature check stays in `removeInternal` so both paths enforce it). `setContainerMode` keeps its existing started-check (message `"Client is already started."` is pinned by baseline). `NjamsReplay.setHandler` and `Njams.setContainerMode`'s internal use switch to the internal variants (replay handler may legally toggle the feature on the lenient legacy path — see Task 15).

- [ ] **Step 3: Deprecate on `Njams`** — `getFeatures` → `features().list()`, `addFeature` → `features().add(...)` (lenient: `warnIfStarted("addFeature", "features().add(...)"); features.addInternal(feature);`), `removeFeature` → `features().remove(...)` (lenient analog), `hasFeature` → `features().has(...)`, `isContainerMode` → `features().isContainerMode()`, `setContainerMode` → `features().setContainerMode(...)` (no contract difference — it already throws; the deprecation tag says so). Each tag follows the Task 13 template with the accessor chain and `{@link NjamsFeatures#...}` reference.

- [ ] **Step 4: Run all tests** — `mvn test -pl njams-sdk` — Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsFeatures.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java
git commit -m "SDK-359 #comment Guard feature mutators in new API, deprecate legacy feature methods"
```

### Task 15: Guards + deprecations — replay

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsReplay.java`, `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java`

- [ ] **Step 1: Failing tests:**

```java
@Test(expected = NjamsSdkRuntimeException.class)
public void newReplaySetHandlerThrowsAfterStart() {
    njams.start();
    njams.replay().setHandler(request -> new com.im.njams.sdk.communication.ReplayResponse());
}

@Test
public void newReplaySetHandlerWorksBeforeStart() {
    njams.replay().setHandler(request -> new com.im.njams.sdk.communication.ReplayResponse());
    assertNotNull(njams.replay().getHandler());
    assertTrue(njams.features().has(Njams.Feature.REPLAY));
}
```

Run: `mvn test -Dtest=NjamsFacetApiTest -pl njams-sdk` — Expected: guard test FAILS.

- [ ] **Step 2: Implement** — `NjamsReplay` gains `LifecycleState` constructor param; `setHandler` = `lifecycle.requireNotStarted("NjamsReplay.setHandler"); setHandlerInternal(handler);`. Package-private `setHandlerInternal` holds the current body and calls `features.addInternal`/`removeInternal` (so the legacy lenient path can still toggle the feature after start, exactly as today — pinned by `NjamsFacadeBaselineTest.setReplayHandlerAfterStartIsLenient`).

- [ ] **Step 3: Deprecate on `Njams`** — `getReplayHandler` → `replay().getHandler()`; `setReplayHandler` → lenient: `warnIfStarted("setReplayHandler", "replay().setHandler(...)"); replay.setHandlerInternal(replayHandler);` with the template tag noting the new method throws after start because the REPLAY feature is announced at start.

- [ ] **Step 4: Run all tests** — `mvn test -pl njams-sdk` — Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsReplay.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java
git commit -m "SDK-359 #comment Guard replay handler in new API, deprecate legacy replay methods"
```

### Task 16: Guards + deprecations — processes

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsProcesses.java`, `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java`

- [ ] **Step 1: Failing tests:**

```java
@Test(expected = NjamsSdkRuntimeException.class)
public void newAddImageThrowsAfterStart() {
    njams.start();
    njams.processes().addImage("late.image", "images/root.png");
}

@Test(expected = NjamsSdkRuntimeException.class)
public void newSetTreeElementTypeThrowsAfterStart() {
    njams.start();
    njams.processes().setTreeElementType(Path.of("SDK4", "TEST"), "custom.type");
}

@Test
public void newProcessCreateIsAllowedAfterStartAndAnnouncable() {
    njams.start();
    com.im.njams.sdk.model.ProcessModel lazy = njams.processes().create(Path.of("LAZY"));
    njams.processes().announce(lazy); // must not throw
}

@Test(expected = NjamsSdkRuntimeException.class)
public void newAnnounceBeforeStartThrows() {
    com.im.njams.sdk.model.ProcessModel model = njams.processes().create(Path.of("P1"));
    njams.processes().announce(model);
}
```

Run: `mvn test -Dtest=NjamsFacetApiTest -pl njams-sdk` — Expected: the two image/tree guard tests FAIL (others pass already).

- [ ] **Step 2: Implement** — in `NjamsProcesses`: split `addImage(String,String)`, `addImage(ImageSupplier)`, `setTreeElementType` into guarded public (`lifecycle.requireNotStarted("NjamsProcesses.addImage")` / `"NjamsProcesses.setTreeElementType"`) + package-private `addImageInternal(...)`/`setTreeElementTypeInternal(...)`. Internal caller `addDefaultImagesIfNeededAndAbsent` uses `addImageInternal` (it runs during `send()` — after start). The Javadoc of the guarded methods documents WHY: additional project messages do not transport images yet, so a late image would silently never reach the server (reference the design doc's deferred item).

- [ ] **Step 3: Deprecate on `Njams`** — all 14 process-related methods listed in Task 10 Step 2 get `@Deprecated` + template tag: `createProcess`→`processes().create(path)`, `addProcessModel`→`processes().add(model)`, `getProcessModel`→`processes().get(path)`, `hasProcessModel`→`processes().has(path)`, `getProcessModels`→`processes().getAll()`, `addImage` (both)→`processes().addImage(...)` (lenient via `addImageInternal` + `warnIfStarted`; tag notes the new method throws after start), `setTreeElementType`→`processes().setTreeElementType(...)` (lenient analog), `getProcessModelLayouter`/`setProcessModelLayouter`→`processes().getLayouter()`/`.setLayouter(...)`, `getProcessDiagramFactory`/`setProcessDiagramFactory`→`processes().getDiagramFactory()`/`.setDiagramFactory(...)`, `sendProjectMessage`→`processes().send()` (tag notes: new method requires a started instance), `sendAdditionalProcess`→`processes().announce(model)`.

- [ ] **Step 4: Run all tests** — `mvn test -pl njams-sdk` — Expected: all PASS (baseline `addImageAfterStartIsLenient` proves the lenient legacy path).

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsProcesses.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java
git commit -m "SDK-359 #comment Guard image/tree mutators in new API, deprecate legacy process methods"
```

### Task 17: Deprecations — jobs

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java`

- [ ] **Step 1: New-API test (passes immediately — no new guard, contract identical):**

```java
@Test
public void newJobsApiMatchesLegacyBehavior() {
    njams.start();
    com.im.njams.sdk.model.ProcessModel model = njams.processes().create(Path.of("P1"));
    com.im.njams.sdk.logmessage.Job job = model.createJob();
    assertSame(job, njams.jobs().get(job.getJobId()));
    assertEquals(1, njams.jobs().getAll().size());
    njams.jobs().remove(job.getJobId());
    assertNull(njams.jobs().get(job.getJobId()));
}

@Test(expected = NjamsSdkRuntimeException.class)
public void newJobsAddBeforeStartThrows() {
    com.im.njams.sdk.model.ProcessModel model = njams.processes().create(Path.of("P1"));
    model.createJob(); // createJob calls jobs().add internally
}
```

- [ ] **Step 2: Deprecate** `addJob`/`removeJob`/`getJobById`/`getJobs` with template tags (`jobs().add(job)`, `jobs().remove(jobId)`, `jobs().get(jobId)`, `jobs().getAll()`); no contract difference, tags state "The replacement has the same contract." `JobImpl`/`ProcessModel` internal callers of `njams.addJob(...)`/`removeJob(...)` are switched to `njams.jobs().add(...)`/`njams.jobs().remove(...)` to keep the SDK free of deprecation warnings (search: `Grep` for `addJob|removeJob|getJobById|getJobs()` in `njams-sdk/src/main/java`, update each production call site to the facet call).

- [ ] **Step 3: Run all tests** — `mvn test -pl njams-sdk` — Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add -A njams-sdk/src/main/java njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java
git commit -m "SDK-359 #comment Deprecate legacy job methods, internal callers use jobs() facet"
```

### Task 18: Deprecations — serializers

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java`

- [ ] **Step 1: New-API smoke test:**

```java
@Test
public void newSerializersApiWorks() {
    njams.serializers().add(String.class, (value, sizeLimit) -> "X" + value);
    assertEquals("Xhello", njams.serializers().serialize("hello"));
    assertNotNull(njams.serializers().remove(String.class));
}
```

- [ ] **Step 2: Deprecate** `addSerializer`, `removeSerializer`, `getSerializer`, `findSerializer`, `serialize(T)`, `serialize(T,int)` with template tags pointing at `serializers().add(...)` etc.; no contract differences. Update internal production callers of `njams.serialize(...)` (search `Grep` for `\.serialize\(` in `njams-sdk/src/main/java` — e.g. `ActivityImpl`/`JobImpl`) to `njams.serializers().serialize(...)`.

- [ ] **Step 3: Run all tests** — `mvn test -pl njams-sdk` — Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add -A njams-sdk/src/main/java njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java
git commit -m "SDK-359 #comment Deprecate legacy serializer methods, internal callers use serializers() facet"
```

### Task 19: Deprecations — commands, argos, configuration

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java`

- [ ] **Step 1: New-API smoke tests:**

```java
@Test
public void newCommandsApiWorks() {
    com.im.njams.sdk.communication.InstructionListener listener = instruction -> { };
    int before = njams.commands().list().size();
    njams.commands().add(listener);
    assertEquals(before + 1, njams.commands().list().size());
    njams.commands().remove(listener);
    assertEquals(before, njams.commands().list().size());
}

@Test
public void newConfigurationApiWorks() {
    assertNotNull(njams.configuration().get());
    assertEquals(com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode.COMPLETE,
        njams.configuration().getLogMode());
    assertFalse(njams.configuration().isExcluded(Path.of("P1")));
}
```

- [ ] **Step 2: Deprecate** with template tags, no contract differences:
`getInstructionListeners`→`commands().list()`, `addInstructionListener`→`commands().add(listener)`, `removeInstructionListener`→`commands().remove(listener)`, `addArgosCollector`→`argos().add(collector)`, `removeArgosCollector`→`argos().remove(collector)`, `getConfiguration`→`configuration().get()`, `getLogMode`→`configuration().getLogMode()`, `isExcluded`→`configuration().isExcluded(path)`. Also deprecate `getSender()` pointing to nothing public: its tag states the sender is communication-internal and not part of the public API (no replacement offered), per the design doc. `onInstruction` gets a tag pointing out it is SDK-internal dispatch (`NjamsCommands`) and not meant to be called by clients. Update internal production callers (`CleanTracepointsTask` etc. — `Grep` for `getConfiguration\(\)|getInstructionListeners\(\)` in `njams-sdk/src/main/java`) to the facet calls.

- [ ] **Step 3: Run all tests** — `mvn test -pl njams-sdk` — Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add -A njams-sdk/src/main/java njams-sdk/src/test/java/com/im/njams/sdk/NjamsFacetApiTest.java
git commit -m "SDK-359 #comment Deprecate legacy command/argos/configuration methods on Njams"
```

### Task 20: Migrate internal SDK and sample-client callers

**Files:**
- Modify: production classes in `njams-sdk/src/main/java` still calling deprecated `Njams` methods
- Modify: `njams-sdk-sample-client/src/main/java/**` (samples must demonstrate the new API)

- [ ] **Step 1: Find remaining internal callers**

Run: `mvn clean compile -pl njams-sdk 2>&1 | findstr /i deprecat` (or build and check warnings). Also `Grep` in `njams-sdk/src/main/java` for each deprecated method name. Update every *production* call site to the facet API using the mapping table from the design doc. **Do not touch test code** — tests intentionally keep exercising the deprecated API.

- [ ] **Step 2: Migrate the sample clients**

`Grep` in `njams-sdk-sample-client/src/main/java` and `njams-sdk-sample-app/src/main/java` for the deprecated method names; replace each with the facet call per the mapping table (e.g. `njams.addSerializer(...)` → `njams.serializers().add(...)`, `njams.setReplayHandler(...)` → `njams.replay().setHandler(...)`). Samples are documentation — they must show the new API.

- [ ] **Step 3: Full build incl. samples**

Run: `mvn clean install -DskipTests` then `mvn test -pl njams-sdk`
Expected: BUILD SUCCESS, all tests PASS, no deprecation warnings from `njams-sdk` or sample main code.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "SDK-359 #comment Migrate internal SDK and sample clients to the facet API"
```

### Task 21: Final validation, docs, wiki

**Files:**
- Modify: `wiki/FAQ.md` (only if its examples call migrated methods)
- Modify: `docs/superpowers/specs/2026-06-10-sdk-359-njams-facade-design.md` (status only)

- [ ] **Step 1: Quality gates**

Run, all must succeed without errors:
```bash
mvn clean install -pl njams-sdk
mvn validate -Pcheckstyle -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```
Checkstyle validates Javadoc on all new public members; the Javadoc build hard-fails on any broken `{@link}` in the ~60 new deprecation tags — fix every error.

- [ ] **Step 2: Wiki draft check**

`Grep` `wiki/*.md` for migrated method names (`setProcessModelLayouter`, `addSerializer`, `setReplayHandler`, `addImage`, ...). Update each example to the facet API (keep a one-line note that the old methods still exist but are deprecated). No settings changed, so no FAQ settings entries are touched.

- [ ] **Step 3: Update design doc status**

Change `**Status:** Draft for review` to `**Status:** Implemented (see plan 2026-06-10-sdk-359-njams-facade-refactoring.md)`.

- [ ] **Step 4: Commit**

```bash
git add wiki docs
git commit -m "SDK-359 #comment Update wiki examples and design doc status for facade refactoring"
```

- [ ] **Step 5: Jira hygiene** — verify the `breaking-change` label is absent on SDK-359 (the change is additive + deprecations only). Do NOT resolve the ticket or post a closing comment — that happens only when the user resolves it.

---

## Self-review notes

- Spec coverage: design-doc sections map to tasks — baseline (Task 1 = migration step 0), extraction (Tasks 2–11 = step 1), accessors/guards/deprecations (Tasks 12–19 = steps 2+3), internal/sample migration + docs (Tasks 20–21). The deferred items (typestate, images-in-announce, Job refactoring, removal) are intentionally absent.
- Lenient-legacy vs strict-new is enforced by paired tests: `NjamsFacadeBaselineTest.*Lenient*` (never modified) + `NjamsFacetApiTest.*ThrowsAfterStart`.
- Type names used consistently: `LifecycleState`, `NjamsSerializers`, `NjamsArgos`, `NjamsFeatures`, `NjamsMetadata`, `NjamsJobs`, `NjamsReplay`, `NjamsConfiguration`, `NjamsProcesses`, `NjamsCommands`; facet methods per the design-doc mapping table.
- Known judgment calls for the implementer: exact constructor signatures of Argos test helpers (Task 1 note), and JaCoCo report path may vary with the `sonar` profile config — if `target/site/jacoco` is absent, check the profile in the root `pom.xml` for the configured output directory.
