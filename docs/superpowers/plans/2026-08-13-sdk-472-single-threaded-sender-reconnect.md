# SDK-472 Single-Threaded Sender Reconnect — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move connection-failure handling out of the individual sender and into the `SenderPool`, so exactly one reconnect runs per sender group per outage, no sender is handed out while reconnecting, and an in-flight message survives its sender being retired.

**Architecture:** A new package-private `SenderConnector` owns both the startup connect and the single reconnect loop for a group. `SenderPool` gains `acquire()` / `release(...)` / `reportFailure(...)` guarded by an explicit lock object (so callers can block without holding a `synchronized` method), plus a `retired` set so an in-use sender is flagged rather than closed under its borrowing thread. `NjamsSender`'s executor task becomes a retention loop: the message stays on the worker stack and a fresh sender is acquired on failure. The lifecycle members leave `AbstractSender`, reducing it to a plain connect / send / close component.

**Tech Stack:** Java 11, Maven 3.8+, JUnit 4, Mockito, SLF4J.

**Design reference:** `docs/superpowers/specs/2026-08-11-sdk-472-single-threaded-sender-reconnect-design.md`. Section numbers below (§5.1, D2.4, …) refer to that spec. Read it before starting; this plan implements it and does not restate its rationale.

**Ticket:** SDK-472 (In Progress, fix version 6.0.0). Parent SDK-375. Follow-ups: SDK-473 (receiver trigger, must land after this), SDK-474 (failure classification), SDK-475 (Kafka defects, documentation-only).

---

## Global Constraints

- **Branch:** `SDK-375`. Commit directly to it; do not create branches. Never push without an explicit per-action request.
- **Commit messages:** `SDK-472 <description>`. Intermediate commits must **not** carry `#comment`; only a final significant commit may.
- **Copyright header** required verbatim on every new production source file (see `.claude/rules/code-quality-general.md`). Not required on test files.
- **Javadoc on all `public` and `protected` members.** Javadoc build must pass with zero errors before any commit.
- **Verification commands** (run from repo root):
  - `mvn test -pl njams-sdk` — full suite
  - `mvn test -Dtest=<Class>#<method> -pl njams-sdk` — single test
  - `mvn validate -Pcheckstyle -pl njams-sdk` — checkstyle (never the bare `checkstyle:check` goal)
  - `mvn javadoc:javadoc -pl njams-sdk` — Javadoc errors are hard failures
- **No new third-party dependencies.** JDK + already-present libraries only.
- **Relocated-type rule:** no `public`/`protected` signature may reference a type relocated by the shade plugin (check against `checkstyle.xml`). All new types here are package-private or JDK types, so this is satisfied by construction — but re-verify on `AbstractSender.notifyConnectionFailure`.
- **`communication/` is a runtime hot path** (`.claude/rules/runtime-performance-hotpath.md`): no per-message settings reads, no new allocation in `send`/`acquire`/`release` beyond what is specified.
- **Test isolation for shared state** (`.claude/rules/testing-conventions.md`): a test must never assume state a previous test left behind. `DiscardMonitor`/`ThrottleMonitor` hold a JVM-wide static instance (Task 2a) — any test that observes discards or throttling installs a **fresh** counting instance in `@Before` and restores the real one in `@After`, and asserts absolute counts rather than a `before`/`after` delta. Never install twice in one test: the second install silently replaces the first, leaving one reference counting nothing.
- **Existing test assertions must not be modified without explicit user permission** (`.claude/rules/testing-conventions.md`). Task 5 migrates test *wiring* only; assertions stay byte-for-byte identical. If an assertion cannot survive the move, **stop and raise it** — that is a behaviour change, not a test to adjust.
- **Kafka is deprecated** (`.claude/rules/kafka-argos-deprecated.md`): keep `KafkaSender` compiling and behaviourally identical; add no tests for it.
- **`breaking-change` label** applies to SDK-472 (SPI Contract change). Set it in Task 10.

---

## Decisions Taken Before Implementation

Points where the spec was silent or where an existing test contradicted the intended behaviour. **B1 and B2 are settled — the user decided them on 2026-08-13.** B3 proceeds on the recommendation below unless objected to.

### B1. `NjamsSenderTest.testReconnectingSenders` asserts the behaviour being removed — RESOLVED: delete

`njams-sdk/src/test/java/com/im/njams/sdk/communication/NjamsSenderTest.java:172-197` plus its inner `ExceptionSender` (lines 372-416) test that a **single sender** retries `connect()` `TRIES` times and then succeeds, driven through `AbstractSender.onException(null)` from an overridden `send(CommonMessage, String)`. It calls three members this ticket removes: `setConnectionCoordinator(...)` (line 181), `onException(...)` (line 389), and relies on `AbstractSender.send`'s retry loop.

Its premise — a sender driving its own reconnect loop — is exactly what D2.1/D2.9 delete. The assertion `assertEquals(counter.get(), ExceptionSender.TRIES + 1)` cannot survive the move in any form, because after this change no sender reconnects itself.

**Decision (user, 2026-08-13): delete both.** Remove `testReconnectingSenders` (L172-197) and the inner `ExceptionSender` class (L372-416) in Task 6 Step 6. Its surviving intent — the group recovers after repeated connect failures, now with one loop instead of N — is carried by `SenderPoolAcquireSpecTest#exactlyOneReconnectorIsElectedUnderConcurrentFailures` (Task 3 Step 1). The permission `testing-conventions.md` requires is hereby on record; cite this line in the commit message.

Also delete the now-unused `counter` static field (`NjamsSenderTest.java:58`) if nothing else references it, and drop imports left dangling by the removal (`ConnectionCoordinator`, `ConnectionStatus`, `CommonMessage`, `ProjectMessage`, `TraceMessage`, `AtomicInteger` — check each before removing; several are still used by other tests in the class).

### B2. `create()` calls `connect()` inside the pool monitor — RESOLVED: keep under the lock

§5.2's `acquire()` pseudocode calls `takeUnlockedOrCreate()` inside `synchronized (lock)`, and §5.2 also states `create()` now calls `connect()` explicitly. A blocking `connect()` (HTTP/JMS against a slow endpoint) would therefore stall every `acquire()` and `release()` in the group — the same hazard §5.2 deliberately avoids for `close()`.

**Decision (user, 2026-08-13): implement it as the spec writes it — connect stays inside the lock — and flag the residual risk in Task 10.** Rationale for the record:

- `acquire()` only reaches `create()`+`connect()` when no idle sender is available *and* the group is not reconnecting. After an outage the connector publishes an already-connected sender (§5.1), so the reconnect path never connects under the lock.
- The remaining case is pool growth while the group is healthy, bounded by `maxSenderThreads` (default 8) and normally fast.
- The residual risk is a *partially degraded* endpoint: reachable enough that the group is not flagged failed, but slow to accept new connections. There one worker's `connect()` stalls every other `acquire()`/`release()` for its duration. JMS is the worst case, having no standard connect timeout — `njams.sdk.communication.connect.timeout` bounds the *startup* path, not `create()`.

Do **not** implement the reserve-a-slot / connect-outside-the-lock variant in this ticket. Task 10 Step 6 records the risk so a follow-up can pick it up if it shows up in practice.

### B4. Monitor testability — RESOLVED: exchangeable singleton, both monitors, baseline waived

`DiscardMonitor` is unobservable from a test (private static counter, no accessor, no reset, and `discard()` skips counting entirely when WARN is off), so SDK-472's relocated discard branch could not be asserted on. **Decisions (user, 2026-08-13):**

1. **Contract boundary confirmed safe.** `discard()` stays `public static` with identical semantics and the new seam is package-private, so no contract boundary moves. `communication-layer.md` classes `DiscardMonitor` as Internal, though in practice it is a facility SPI implementers call to apply `ON_CONNECTION_LOSS` — which is exactly why its signature must not change.
2. **`ThrottleMonitor` gets the same treatment**, as its structural twin, so the pair does not drift apart.
3. **Baseline tests are exceptionally waived for this refactor.** `njams-safe-modification` normally requires establishing coverage *before* changing existing code; the user has explicitly waived that here because the class is untestable by construction today, and post-refactor coverage is sufficient. **This waiver applies to Task 2a only** — it does not extend to any other task in this plan. Record it in the Task 2a commit message.
4. **Committed under SDK-472 as enabling work** (not a separate ticket). The commit message should say so explicitly, since "single threaded sender reconnect" does not obviously cover a monitor refactor and `commit-conventions.md` requires the ticket to match the diff.

### B3. How `notifyConnectionFailure` reaches the pool — proceeding on the recommendation

§6 adds `protected final void notifyConnectionFailure(Exception)` to `AbstractSender` and §5.2 says retirement adds "no bookkeeping state to `AbstractSender`". But the method must route to the owning pool, which needs *some* injected reference, and `setConnectionCoordinator(...)` — today's injection point — is being removed.

**Recommended resolution:** one package-private field plus a package-private setter, invisible to SPI implementers:

```java
/** Package-private callback so a sender can report a failure to its owning pool. Not SPI. */
interface SenderFailureSink {
    void onConnectionFailure(AbstractSender sender, Exception cause);
}
```

`AbstractSender` holds `private volatile SenderFailureSink failureSink;` with package-private `void setFailureSink(SenderFailureSink)`, set by `SenderPool.create()`. This is state, but not retirement bookkeeping, and it is not visible on the SPI surface.

**Status: not explicitly confirmed.** Task 4 proceeds on this design because it is the minimum that makes §6's `notifyConnectionFailure` functional, and because it is entirely package-private — an SPI implementer sees only the `protected final` method §6 already sanctions. If a reviewer objects to any new field on `AbstractSender`, raise it before Task 4 rather than working around it in Task 4.

---

## File Structure

**Production — created:**

| File | Responsibility |
|---|---|
| `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderConnector.java` | One per group. Owns the startup connect (re-enterable by late callers) and the single reconnect loop. Holds its own private `AbstractSender` until connected, then transfers it to the pool. Package-private. |
| `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderFailureSink.java` | Package-private callback interface letting a sender report an async failure to its pool (B3). |

**Production — modified:**

| File | Change |
|---|---|
| `communication/SenderPool.java` | Explicit `lock` + `wait`/`notifyAll`; `acquire()`, `release(...)`, `reportFailure(...)`, `onReconnected(...)`; `retired` set; `failed` flag behind `isConnectionFailure()`; thread-safe listener set fired once per outage; `validate()`, `get()`, `close(AbstractSender)` removed. |
| `communication/AbstractSender.java` | Lifecycle members removed (§6 table); `notifyConnectionFailure` added; `send(CommonMessage, String)` reduced to `instanceof` dispatch; new SPI invariant documented. |
| `communication/NjamsSender.java` | Executor task becomes the retention loop; `beginConnect()`/`startWithTimeout(...)` delegate to the connector; `startupSender` borrow-and-re-arm dance removed. |
| `communication/ConnectionCoordinator.java` | Reconnect counter collapses to a boolean; `isGroupConnected()` accessor added for the connector. |
| `communication/DiscardMonitor.java` | Becomes an exchangeable singleton: static counters become instance fields, `discard()` delegates to `getInstance().recordDiscard()`, package-private `getInstance()`/`setInstance(...)` test seam. Public surface unchanged (Task 2a). |
| `communication/ThrottleMonitor.java` | Same treatment as its structural twin, for consistency (Task 2a). |
| `communication/jms/JmsSender.java` | `onException(JMSException)` calls `notifyConnectionFailure`. Discard check unchanged. |
| `communication/http/HttpSender.java`, `communication/kafka/KafkaSender.java` | Compilation only. Discard checks unchanged (§8.1). |

**Not modified:** `MaxQueueLengthHandler.java` (no code change; its `isConnectionLost` supplier already points at `senderPool::isConnectionFailure` at `NjamsSender.java:168`, and that method's *implementation* changes underneath it).

**Tests — modified (Task 5, wiring only):** `lifecycle/SenderReconnectGatingSpecTest`, `lifecycle/SenderStartupSpecTest`, `lifecycle/SenderStartGatingSpecTest`, `lifecycle/SenderShutdownSpecTest`, `lifecycle/SenderLoggingSpecTest`, `SenderCloseOrderingSpecTest`, `AbstractSenderStaticStateTest`, `SenderPoolTest`, `NjamsSenderTest`, `lifecycle/LifecycleTestSender`.

**Tests — created (Tasks 6-9):** `SenderPoolAcquireSpecTest`, `SenderRetirementSpecTest`, `SenderConnectorStartupSpecTest`, `MessageRetentionSpecTest`, `SharedSenderOutageSpecTest`, `SenderDeadlockRegressionTest` (all under `communication/lifecycle/`).

---

## How this task order satisfies spec §10

§10 requires two strictly separate steps in separate commits, with relocation never blended with new assertions. This plan satisfies that by ordering, not by deferring all tests:

- **New code gets its tests as it is written** (Tasks 2, 3, 4, 5) — that is TDD, and those tests describe genuinely new API (`SenderConnector`, `acquire`/`release`/`reportFailure`, `notifyConnectionFailure`, the retention loop) that has no prior behaviour to preserve.
- **The relocation commit (Task 6) contains no new assertions at all** — only removals plus test rewiring with byte-identical assertions. That is the property §10 actually protects: assertion parity across the move is the evidence the move preserved behaviour.
- **Tasks 7 and 8 add the remaining new-behaviour tests** in their own commits.

So the relocation is never blended with new assertions, and every commit is independently green. If a reviewer would rather see all new tests after Task 6, that is a sequencing preference — raise it before starting rather than reshuffling mid-plan.

---

## Task 0: Baseline

**Files:** none modified.

- [ ] **Step 1: Confirm the suite is green before touching anything**

```bash
mvn test -pl njams-sdk
```

Record the total test count and the fact that it passes. Every later task compares against this baseline. If anything already fails, stop and report — do not start refactoring on a red suite.

- [ ] **Step 2: Confirm checkstyle and Javadoc are clean**

```bash
mvn validate -Pcheckstyle -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```

Expected: both succeed. Note any pre-existing warnings so they are not mistaken for regressions later.

---

## Task 1: `ConnectionCoordinator` — collapse the reconnect counter, expose group-connected

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/ConnectionCoordinator.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/ConnectionCoordinatorTest.java`

**Interfaces:**
- Produces: `boolean isGroupConnected()`, `boolean beginReconnect()` (was `int`), `boolean markConnected()`, `boolean markStartupConnected()`, `boolean wasEverConnected()`, `boolean shouldReconnect()`, `void setShouldShutdown(boolean)`, `boolean shouldShutdown()`, `void allowReconnectBeforeConnected()`. `reconnectingCount()` is gone.
- Consumed by: `SenderConnector` (Task 2), `SenderPool` (Task 3).

Per §5.3: the `connecting` counter and `reconnectingCount()` exist only to coordinate parallel reconnects and collapse to a boolean with one reconnector. The gating state (`wasEverConnected`, `reconnectBeforeConnected`, `shouldShutdown`) is load-bearing and **stays exactly as-is**.

- [ ] **Step 1: Read the existing test to see what parity means here**

Read `ConnectionCoordinatorTest.java` in full. Any test asserting on `reconnectingCount()` is asserting the parallel-reconnect coordination this ticket deletes — list those methods and report them before changing them (they fall under the same rule as B1).

- [ ] **Step 2: Write the failing test for the new accessor**

Add to `ConnectionCoordinatorTest`:

```java
@Test
public void isGroupConnectedTracksConnectAndReconnect() {
    ConnectionCoordinator c = new ConnectionCoordinator();
    assertFalse("a fresh coordinator is not connected", c.isGroupConnected());
    assertTrue(c.markStartupConnected());
    assertTrue("connected after a startup connect", c.isGroupConnected());
    c.beginReconnect();
    assertFalse("beginReconnect clears the connected flag", c.isGroupConnected());
    assertTrue(c.markConnected());
    assertTrue("connected again after a successful reconnect", c.isGroupConnected());
}
```

- [ ] **Step 3: Run it to verify it fails**

```bash
mvn test -Dtest=ConnectionCoordinatorTest#isGroupConnectedTracksConnectAndReconnect -pl njams-sdk
```

Expected: compile error — `isGroupConnected()` does not exist.

- [ ] **Step 4: Implement**

Replace the `AtomicInteger connecting` field and its two users. In `ConnectionCoordinator.java`:

- Delete `private final AtomicInteger connecting = new AtomicInteger(0);` and the `java.util.concurrent.atomic.AtomicInteger` import.
- Delete `reconnectingCount()` entirely.
- Change `beginReconnect()` to return `boolean` (whether this call performed the connected→disconnected transition) and drop the counter:

```java
    /**
     * Marks the group as currently disconnected and beginning a reconnect.
     *
     * @return {@code true} if this call cleared the connected flag, {@code false} if the group was already
     *         marked disconnected.
     */
    synchronized boolean beginReconnect() {
        return hasConnected.compareAndSet(true, false);
    }
```

- Drop the `connecting.decrementAndGet();` line from `markConnected()`, leaving its `wasEverConnected` and `compareAndSet` behaviour untouched.
- Add the accessor:

```java
    /** @return {@code true} while the group is currently believed to be connected. */
    boolean isGroupConnected() {
        return hasConnected.get();
    }
```

- Update the class Javadoc: it currently says "Owns the reconnect-counting, shutdown, and …". Reword to "Owns the connection, shutdown, and …" — per `code-quality-general.md`, a comment made inaccurate by a change must be fixed, not left.

- [ ] **Step 5: Run the new test and the whole coordinator test class**

```bash
mvn test -Dtest=ConnectionCoordinatorTest -pl njams-sdk
```

Expected: PASS. `AbstractSender.doReconnect` still references `beginReconnect()`/`reconnectingCount()` — fix its two call sites minimally so the module still compiles (assign the boolean, drop the `{N} senders are reconnecting now` and `{N} senders still need to reconnect.` log lines). That code is deleted wholesale in Task 6; this is only to keep the build green between tasks.

- [ ] **Step 6: Full suite + commit**

```bash
mvn test -pl njams-sdk
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/ConnectionCoordinator.java njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java njams-sdk/src/test/java/com/im/njams/sdk/communication/ConnectionCoordinatorTest.java
git commit -m "SDK-472 Collapse the reconnect counter in ConnectionCoordinator to a boolean"
```

---

## Task 2: `SenderConnector` — the single connect/reconnect owner

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderConnector.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderConnectorStartupSpecTest.java`

**Interfaces:**
- Consumes: `ConnectionCoordinator.isGroupConnected()`, `markStartupConnected()`, `markConnected()`, `beginReconnect()`, `shouldReconnect()`, `shouldShutdown()` (Task 1); `CommunicationFactory.getSender()`; `SenderPool.onReconnected(AbstractSender)` (Task 3 — declare the call now, implement in Task 3).
- Produces, all package-private:
  - `SenderConnector(CommunicationFactory factory, ConnectionCoordinator coordinator, SenderPool pool, ClientSettings settings)`
  - `void beginConnect()`
  - `boolean awaitStartup(long timeoutMs)`
  - `void startReconnect(Exception cause)`
  - `void cancelReconnect()`

This task is additive — nothing calls the new class yet, so the build stays green. The critical requirement is §5.1's **re-enterable startup**: a late caller must get `awaitStartup(...) == true` immediately against an already-connected group, rather than blocking on a spent latch or re-running a connect.

- [ ] **Step 1: Write the failing tests**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderConnectorStartupSpecTest.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderConnectorTestAccess;

public class SenderConnectorStartupSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void startupConnectsOnceAndReportsSuccess() {
        SenderConnectorTestAccess c = SenderConnectorTestAccess.create();
        assertTrue("startup must succeed against a SUCCEED transport", c.awaitStartup(5000));
        assertEquals("exactly one connect for one startup", 1, LifecycleTestTransport.senderConnectCount());
    }

    @Test
    public void lateCallerGetsImmediateSuccessWithoutASecondConnect() {
        SenderConnectorTestAccess c = SenderConnectorTestAccess.create();
        assertTrue(c.awaitStartup(5000));
        int afterFirst = LifecycleTestTransport.senderConnectCount();
        // A second Njams starting later against the already-connected group (spec 5.1)
        assertTrue("a late caller must succeed immediately", c.awaitStartup(0));
        assertEquals("no second connect for a late caller", afterFirst,
            LifecycleTestTransport.senderConnectCount());
    }

    @Test
    public void startupFailureIsReportedAndDoesNotReconnectBeforeEverConnected() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        SenderConnectorTestAccess c = SenderConnectorTestAccess.create();
        assertFalse("a failed startup connect must report false", c.awaitStartup(2000));
    }
}
```

Because `SenderConnector` is package-private in `com.im.njams.sdk.communication` and the lifecycle tests live in `...communication.lifecycle`, add a tiny package-private-bridging helper in the sender package. Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderConnectorTestAccess.java`:

```java
package com.im.njams.sdk.communication;

import com.im.njams.sdk.communication.lifecycle.LifecycleTestTransport;
import com.im.njams.sdk.settings.ClientSettings;

/** Test bridge exposing the package-private SenderConnector to tests in other packages. */
public class SenderConnectorTestAccess {

    private final SenderConnector connector;

    private SenderConnectorTestAccess(SenderConnector connector) {
        this.connector = connector;
    }

    public static SenderConnectorTestAccess create() {
        ClientSettings settings = ClientSettings.from(LifecycleTestTransport.settings().getAllProperties());
        CommunicationFactory factory = new CommunicationFactory(settings);
        ConnectionCoordinator coordinator = new ConnectionCoordinator();
        SenderPool pool = new SenderPool(factory, coordinator);
        return new SenderConnectorTestAccess(new SenderConnector(factory, coordinator, pool, settings));
    }

    public boolean awaitStartup(long timeoutMs) {
        return connector.awaitStartup(timeoutMs);
    }

    public void startReconnect(Exception cause) {
        connector.startReconnect(cause);
    }

    public void cancelReconnect() {
        connector.cancelReconnect();
    }

    /** The group's connected state, replacing per-sender {@code isConnected()} assertions (see Appendix A.1). */
    public boolean isGroupConnected() {
        return coordinator.isGroupConnected();
    }

    /** Forces the group disconnected, standing in for the old {@code LifecycleTestSender.forceDisconnect()}. */
    public void forceGroupDisconnected() {
        coordinator.beginReconnect();
    }
}
```

Hold the `ConnectionCoordinator` in a field so those last two methods can reach it. **Note for Task 3:** that task changes `SenderPool`'s constructor to take `ClientSettings`; update this bridge's `create()` to match when you get there — it is the only caller outside production code.

- [ ] **Step 2: Run to verify failure**

```bash
mvn test -Dtest=SenderConnectorStartupSpecTest -pl njams-sdk
```

Expected: compile error — `SenderConnector` does not exist.

- [ ] **Step 3: Implement `SenderConnector`**

Create the file with the mandatory copyright header, then:

```java
package com.im.njams.sdk.communication;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.settings.ClientSettings;

/**
 * Owns the startup connect and the single reconnect loop for one sender group. Exactly one instance exists per
 * {@link SenderPool}, which creates and owns it.
 * <p>
 * The connector works on its own private {@link AbstractSender}: while a startup connect or a reconnect loop is
 * running, that instance is exclusively the connector's and is never reachable through {@link SenderPool#acquire()}.
 * Only once it is connected is it transferred to the pool via {@link SenderPool#onReconnected(AbstractSender)}, so
 * waiters wake with a working sender already available instead of each racing to connect on a worker thread.
 * <p>
 * Startup is re-enterable: any number of later callers awaiting an already-connected group get an immediate
 * success rather than blocking on a spent latch or triggering a second connect. This is what lets several
 * {@link com.im.njams.sdk.Njams} instances share one group (shared communications).
 * <p>
 * Internal SDK infrastructure — not public API.
 */
class SenderConnector {

    private static final long RECONNECT_INTERVAL_MS = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(SenderConnector.class);

    private final CommunicationFactory factory;
    private final ConnectionCoordinator coordinator;
    private final SenderPool pool;
    private final ClientSettings settings;

    /** Guards startup/reconnect thread creation and the latch handoff below. */
    private final Object gate = new Object();
    private boolean connectInFlight = false;
    private CountDownLatch startupLatch = new CountDownLatch(0);
    private Thread startupThread;
    private Thread reconnectThread;
    private volatile Exception startupError;

    SenderConnector(CommunicationFactory factory, ConnectionCoordinator coordinator, SenderPool pool,
        ClientSettings settings) {
        this.factory = factory;
        this.coordinator = coordinator;
        this.pool = pool;
        this.settings = settings;
    }

    /**
     * Starts one background connect attempt if the group is not already connected and no connect is in flight.
     * Idempotent and safe to call from any number of threads.
     */
    void beginConnect() {
        synchronized (gate) {
            if (coordinator.isGroupConnected() || connectInFlight) {
                return;
            }
            connectInFlight = true;
            startupError = null;
            startupLatch = new CountDownLatch(1);
            startupThread = new Thread(this::runStartupConnect);
            startupThread.setDaemon(true);
            startupThread.setName("Sender-Startup-" + pool.getSenderName());
            startupThread.start();
        }
    }

    /**
     * Waits up to {@code timeoutMs} for the group to be connected, starting a connect if none is running.
     * Returns immediately for a late caller against an already-connected group.
     *
     * @param timeoutMs maximum time to wait, in milliseconds.
     * @return {@code true} iff the group is connected.
     */
    boolean awaitStartup(long timeoutMs) {
        if (coordinator.isGroupConnected()) {
            return true;
        }
        final CountDownLatch latch;
        synchronized (gate) {
            beginConnect();
            latch = startupLatch;
        }
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return coordinator.isGroupConnected() && startupError == null;
    }

    private void runStartupConnect() {
        AbstractSender sender = null;
        try {
            sender = createSender();
            sender.connect();
            coordinator.markStartupConnected();
            pool.onReconnected(sender);
            sender = null; // ownership transferred
        } catch (Exception e) {
            startupError = e;
            LOG.debug("Startup connect failed.", e);
            if (coordinator.shouldReconnect()) {
                startReconnect(e);
            }
        } finally {
            closeQuietly(sender);
            synchronized (gate) {
                connectInFlight = false;
                startupLatch.countDown();
            }
        }
    }

    /**
     * Starts the group's single reconnect loop, unless one is already running, the group is already connected, or
     * a reconnect is not permitted yet (see {@link ConnectionCoordinator#shouldReconnect()}).
     *
     * @param cause the failure that triggered the reconnect; may be {@code null}.
     */
    void startReconnect(Exception cause) {
        synchronized (gate) {
            if (coordinator.isGroupConnected() || !coordinator.shouldReconnect()) {
                return;
            }
            if (reconnectThread != null && reconnectThread.isAlive()) {
                return;
            }
            coordinator.beginReconnect();
            reconnectThread = new Thread(() -> runReconnectLoop(cause));
            reconnectThread.setDaemon(true);
            reconnectThread.setName("Sender-Reconnector-" + pool.getSenderName());
            reconnectThread.start();
        }
    }

    private void runReconnectLoop(Exception cause) {
        if (LOG.isInfoEnabled() && cause != null) {
            LOG.info("Initialized reconnect, because of: {}", getExceptionWithCauses(cause));
        }
        while (!coordinator.isGroupConnected() && !coordinator.shouldShutdown()) {
            AbstractSender sender = null;
            try {
                sender = createSender();
                sender.connect();
                if (coordinator.markConnected()) {
                    LOG.info("Reconnected sender {}", sender.getName());
                }
                pool.onReconnected(sender);
                return;
            } catch (Exception e) {
                closeQuietly(sender);
                try {
                    Thread.sleep(RECONNECT_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Interrupts the startup and reconnect threads so a blocking connect is cancelled promptly on shutdown. */
    void cancelReconnect() {
        final Thread startup;
        final Thread reconnect;
        synchronized (gate) {
            startup = startupThread;
            reconnect = reconnectThread;
        }
        if (startup != null) {
            startup.interrupt();
        }
        if (reconnect != null) {
            reconnect.interrupt();
        }
    }

    private AbstractSender createSender() {
        final AbstractSender sender = factory.getSender();
        sender.init(settings);
        return sender;
    }

    private void closeQuietly(AbstractSender sender) {
        if (sender == null) {
            return;
        }
        try {
            sender.close();
        } catch (Exception e) {
            LOG.debug("Failed to close a sender after an unsuccessful connect.", e);
        }
    }

    private static String getExceptionWithCauses(final Throwable t) {
        Throwable current = t;
        StringBuilder sb = new StringBuilder();
        while (current != null) {
            if (sb.length() > 1) {
                sb.append(", caused by: ");
            }
            sb.append(current.toString());
            current = current.getCause();
        }
        return sb.toString();
    }
}
```

Notes for the implementer:
- **The two `LOG.info` strings must stay byte-identical** — `"Initialized reconnect, because of: {}"` and `"Reconnected sender {}"`. `SenderLoggingSpecTest` asserts on them by prefix, and those assertions are the parity evidence for the whole relocation. See Appendix A.4. Do not reword them, and do not change their level from `INFO`.
- `getExceptionWithCauses` is moved verbatim from `AbstractSender.java:248-259`; it is deleted there in Task 6.
- `pool.getSenderName()` and `pool.onReconnected(...)` do not exist yet. Add both as package-private stubs on `SenderPool` in this task so the module compiles — `getSenderName()` returning `factory` -derived name, `onReconnected(AbstractSender)` temporarily just adding to `unlocked`. Task 3 gives `onReconnected` its real body.
- Does `CommunicationFactory.getSender()` already call `init(...)`? Read `CommunicationFactory` and do **not** double-init. Match whatever `SenderPool.create()` does today.

- [ ] **Step 4: Run the tests**

```bash
mvn test -Dtest=SenderConnectorStartupSpecTest -pl njams-sdk
```

Expected: all three PASS. The `lateCallerGetsImmediateSuccessWithoutASecondConnect` test is the §5.1 requirement — if it fails, the re-enterable-startup logic is wrong; fix it here, not later.

- [ ] **Step 5: Full suite, checkstyle, Javadoc, commit**

```bash
mvn test -pl njams-sdk
mvn validate -Pcheckstyle -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderConnector.java njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderConnectorTestAccess.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderConnectorStartupSpecTest.java
git commit -m "SDK-472 Add SenderConnector owning the group's startup connect and single reconnect loop"
```

---

## Task 2a: Make `DiscardMonitor` and `ThrottleMonitor` exchangeable (enabling work)

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/DiscardMonitor.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/ThrottleMonitor.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/CountingDiscardMonitor.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/CountingThrottleMonitor.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/MonitorExchangeTest.java`

**Interfaces:**
- Produces (both classes, all package-private except where noted): `static DiscardMonitor getInstance()`, `static void setInstance(DiscardMonitor)`, `protected void recordDiscard()`, `protected DiscardMonitor()`; `public static void discard()` **unchanged**. Likewise `ThrottleMonitor.getInstance()`, `setInstance(ThrottleMonitor)`, `protected void recordThrottle(long ms)`, `protected ThrottleMonitor()`, with `public static void throttle(long)` unchanged.
- Consumed by: Task 3's `SenderPoolTestAccess` (discard assertions) and every later task that asserts a discard.

**Per decision B4: baseline tests are waived for this task only.** Go straight to the refactor, then cover it. Do not extend the waiver to any other task.

- [ ] **Step 1: Refactor `DiscardMonitor`**

Keep the copyright header and class Javadoc (extend the Javadoc to mention the test seam). The four `private static` counters become instance fields; the body of today's `discard()` moves verbatim into `recordDiscard()`, including the `isWarnEnabled()` early-out and the throttled `synchronized` block — now synchronising on `this` instead of `DiscardMonitor.class`.

```java
    /** The real implementation, restored whenever the override is cleared. */
    private static final DiscardMonitor DEFAULT = new DiscardMonitor();
    private static volatile DiscardMonitor instance = DEFAULT;

    private long lastMessage = System.currentTimeMillis();
    private long nextMessage = 0;
    private int discardCount = 0;
    private int lastDiscardCount = 0;

    /** Subclassable so a test can inject an observing instance; not intended for production subclassing. */
    protected DiscardMonitor() {
    }

    /** @return the active monitor: the real implementation, or a test instance if one was injected. */
    static DiscardMonitor getInstance() {
        return instance;
    }

    /**
     * Injects a monitor, or restores the real one when passed {@code null}. Package-private test seam.
     * The field is JVM-wide, so a test that injects MUST restore it in teardown.
     *
     * @param override the monitor to install, or {@code null} to restore the real implementation.
     */
    static void setInstance(DiscardMonitor override) {
        instance = override != null ? override : DEFAULT;
    }

    /**
     * Increments discard counter and issues a warning if it's time. To be called for message that is discarded.
     */
    public static void discard() {
        // A volatile read plus a call site that is monomorphic in production, so the JIT inlines it. Do not
        // "optimize" this back to a static body: the indirection is what makes discarding observable in tests.
        getInstance().recordDiscard();
    }

    /**
     * Records one discarded message, logging a throttled summary. Overridden by test instances to observe
     * discards without depending on log configuration.
     */
    protected void recordDiscard() {
        // ... body moved verbatim from the old static discard(), synchronized (this)
    }
```

**Do not change `discard()`'s signature, name, visibility or observable behaviour** — it has six production call sites (`MaxQueueLengthHandler:59,66`, `HttpSender:412`, `JmsSender:321`, `KafkaSender:316,320`, and `AbstractSender:304` which Task 6 relocates), and a custom transport may call it too.

- [ ] **Step 2: Refactor `ThrottleMonitor` identically**

Same transformation: `lastMessage`/`nextMessage`/`throttleCount`/`lastThrottleCount` become instance fields, `throttle(long ms)` becomes `getInstance().recordThrottle(ms)`, the body moves verbatim into `protected void recordThrottle(long ms)` keeping the `isInfoEnabled()` early-out and synchronising on `this`. Its one call site is `MaxQueueLengthHandler:88`.

- [ ] **Step 3: Add the counting test instances**

```java
package com.im.njams.sdk.communication;

import java.util.concurrent.atomic.AtomicInteger;

/** Observing DiscardMonitor for tests: counts every discard, with no log-level dependency or throttling. */
public class CountingDiscardMonitor extends DiscardMonitor {

    private final AtomicInteger count = new AtomicInteger();

    @Override
    protected void recordDiscard() {
        count.incrementAndGet();
    }

    /** @return how many discards were recorded since this instance was installed. */
    public int count() {
        return count.get();
    }

    /**
     * Waits for at least {@code target} discards. Message dispatch happens on an executor worker, so a bare
     * assertion on {@link #count()} would race it.
     *
     * @return {@code true} if the count reached {@code target} within the timeout.
     */
    public boolean awaitAtLeast(int target, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (count.get() < target && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        return count.get() >= target;
    }

    /** Installs a fresh counter and returns it. */
    public static CountingDiscardMonitor install() {
        CountingDiscardMonitor monitor = new CountingDiscardMonitor();
        DiscardMonitor.setInstance(monitor);
        return monitor;
    }

    /** Restores the real implementation. Call from test teardown. */
    public static void restore() {
        DiscardMonitor.setInstance(null);
    }
}
```

`CountingThrottleMonitor` mirrors this, overriding `recordThrottle(long ms)` and accumulating `ms` in an `AtomicLong` (mirroring the real implementation, which adds durations rather than counting calls), with `install()`/`restore()` and `totalMs()`.

- [ ] **Step 4: Write the coverage tests**

```java
package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MonitorExchangeTest {

    /**
     * Establishes a known monitor state before every test. A test must never assume what the previous test left
     * behind: the instance is a JVM-wide static, so both a stale override and a stale counter would leak.
     */
    @Before
    public void resetMonitors() {
        CountingDiscardMonitor.restore();
        CountingThrottleMonitor.restore();
    }

    @After
    public void restoreMonitors() {
        CountingDiscardMonitor.restore();
        CountingThrottleMonitor.restore();
    }

    @Test
    public void discardIsRoutedToTheInjectedInstance() {
        CountingDiscardMonitor monitor = CountingDiscardMonitor.install();
        DiscardMonitor.discard();
        DiscardMonitor.discard();
        assertEquals("every discard must reach the injected monitor", 2, monitor.count());
    }

    @Test
    public void throttleIsRoutedToTheInjectedInstance() {
        CountingThrottleMonitor monitor = CountingThrottleMonitor.install();
        ThrottleMonitor.throttle(30);
        ThrottleMonitor.throttle(12);
        assertEquals("throttle durations must accumulate on the injected monitor", 42L, monitor.totalMs());
    }

    @Test
    public void clearingTheOverrideRestoresTheRealImplementation() {
        DiscardMonitor real = DiscardMonitor.getInstance();
        assertNotNull(real);
        CountingDiscardMonitor monitor = CountingDiscardMonitor.install();
        assertSame("the override must be active", monitor, DiscardMonitor.getInstance());
        CountingDiscardMonitor.restore();
        assertSame("clearing must restore the same real instance", real, DiscardMonitor.getInstance());
    }

    @Test
    public void theRealImplementationStillCountsAndDoesNotThrow() {
        // Exercises the production path itself: the default instance must remain usable and self-consistent.
        CountingDiscardMonitor.restore();
        DiscardMonitor.discard();
        ThrottleMonitor.throttle(5);
        assertNotNull(DiscardMonitor.getInstance());
        assertNotNull(ThrottleMonitor.getInstance());
    }
}
```

- [ ] **Step 5: Verify**

```bash
mvn test -Dtest=MonitorExchangeTest -pl njams-sdk
mvn test -pl njams-sdk
mvn validate -Pcheckstyle -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```

Expected: all pass. Confirm the public API really is unchanged — `discard()` and `throttle(long)` must still be `public static` with the same names and parameters, and `getInstance`/`setInstance` must **not** be public (checkstyle's Javadoc rules would also start demanding Javadoc on them if they were).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/DiscardMonitor.java njams-sdk/src/main/java/com/im/njams/sdk/communication/ThrottleMonitor.java njams-sdk/src/test/java/com/im/njams/sdk/communication/CountingDiscardMonitor.java njams-sdk/src/test/java/com/im/njams/sdk/communication/CountingThrottleMonitor.java njams-sdk/src/test/java/com/im/njams/sdk/communication/MonitorExchangeTest.java
git commit -m "SDK-472 Make DiscardMonitor and ThrottleMonitor exchangeable singletons

Enabling work for SDK-472: the relocated discard branch could not be asserted on,
because DiscardMonitor kept a private static counter with no accessor and skipped
counting entirely when warn logging was disabled. Public API is unchanged - discard()
and throttle(long) keep their signatures and behaviour; the injection seam is
package-private. Baseline coverage was waived by explicit agreement, as the class was
untestable by construction; coverage is added here instead."
```

---

## Task 3: `SenderPool` — `acquire` / `release` / `reportFailure`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderPoolAcquireSpecTest.java` (new)

**Interfaces:**
- Consumes: `SenderConnector` (Task 2), `ConnectionCoordinator` (Task 1), `DiscardMonitor.discard()`, `DiscardPolicy`.
- Produces:
  - `AbstractSender acquire()` — returns a **connected** sender, or `null` when shutting down or when the discard policy gave up.
  - `void release(AbstractSender sender)` — returns it to `unlocked`, or destroys it if retired.
  - `void reportFailure(AbstractSender sender, Exception cause)` — retires, elects at most one reconnector, fires listeners once.
  - `void onReconnected(AbstractSender connected)` — publishes the connector's connected sender and wakes waiters.
  - `boolean isConnectionFailure()` — now the group `failed` flag.
  - `String getSenderName()`.
  - Connector delegations used by `NjamsSender` in Task 5: `void beginConnect()`, `boolean awaitStartup(long)`, `void restartConnectInBackground(long)`, `void cancelConnect()`.
  - Test-only package-private accessors (Step 1b): `AbstractSender awaitPublishedSenderForTest()`, `int exceptionListenerFireCountForTest()`, `boolean isRetiredForTest(AbstractSender)`.
  - Retained: `beginShutdown()`, `declareShutdown()`, `shutdown()`, `addSenderExceptionListener(...)`, `allowReconnectBeforeConnected()`.
  - `get()`, `close(AbstractSender)`, `validate(AbstractSender)` are **kept in this task** (so `NjamsSender` still compiles) and removed in Task 6.

This is the heart of the ticket. Implement per §5.2 exactly, honouring B2's confirmed answer.

- [ ] **Step 1: Write the failing tests**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderPoolAcquireSpecTest.java`. These are **new behaviour** tests (spec §10 Step 2) but they belong with the code they describe, so they land here rather than in Task 7:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderPoolTestAccess;

public class SenderPoolAcquireSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void acquireReturnsAConnectedSender() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object sender = pool.acquire();
        assertNotNull("acquire must hand out a sender", sender);
        assertTrue("acquire must hand out a CONNECTED sender", pool.isConnected(sender));
    }

    @Test
    public void exactlyOneReconnectorIsElectedUnderConcurrentFailures() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        int failures = 6;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(failures);
        for (int i = 0; i < failures; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("boom"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals("only the elected failure fires the listeners", 1, pool.exceptionListenerFireCount());
        assertTrue("the group is flagged as failed", pool.isConnectionFailure());
    }

    @Test
    public void acquireDiscardsWhileReconnectingUnderDiscardPolicy() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("discard");
        pool.forceReconnecting();
        assertNull("acquire must give up under DISCARD while reconnecting", pool.acquire());
        // discardMonitor is installed fresh by AbstractLifecycleSpecTest, so this count is absolute.
        assertEquals("exactly one message counted as discarded", 1, discardMonitor.count());
    }

    @Test
    public void acquireDiscardsWhileReconnectingUnderOnConnectionLoss() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("onconnectionloss");
        pool.forceReconnecting();
        assertNull("acquire must give up under ON_CONNECTION_LOSS while reconnecting", pool.acquire());
        assertEquals("exactly one message counted as discarded", 1, discardMonitor.count());
    }

    @Test
    public void acquireDoesNotDiscardWhileTheGroupIsHealthy() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("discard");
        assertNotNull("a healthy group hands out a sender", pool.acquire());
        assertEquals("nothing may be discarded while healthy", 0, discardMonitor.count());
    }

    @Test
    public void acquireBlocksUnderNoneAndIsReleasedByReconnect() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.forceReconnecting();
        AtomicReference<Object> acquired = new AtomicReference<>();
        CountDownLatch returned = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            acquired.set(pool.acquire());
            returned.countDown();
        });
        t.setDaemon(true);
        t.start();
        assertNull("acquire must not return while the group reconnects", acquired.get());
        assertEquals("still parked", 1, returned.getCount());
        Object connected = pool.publishConnectedSender();
        assertTrue("the waiter must wake once a sender is published", returned.await(5, TimeUnit.SECONDS));
        assertSame("the waiter gets the reconnected sender", connected, acquired.get());
    }

    @Test
    public void acquireReturnsNullPromptlyOnShutdown() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.forceReconnecting();
        AtomicReference<Object> acquired = new AtomicReference<>();
        CountDownLatch returned = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            acquired.set(pool.acquire());
            returned.countDown();
        });
        t.setDaemon(true);
        t.start();
        assertEquals(1, returned.getCount());
        pool.beginShutdown();
        assertTrue("shutdown must wake blocked acquirers", returned.await(5, TimeUnit.SECONDS));
        assertNull("a woken acquirer gets null on shutdown", acquired.get());
    }

    @Test
    public void releaseReturnsAHealthySenderToThePool() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object s1 = pool.acquire();
        pool.release(s1);
        assertSame("a released healthy sender is reused", s1, pool.acquire());
    }
}
```

> **Discards are asserted through the Task 2a seam**, which is why Task 2a must land first. `discardMonitor` is a `CountingDiscardMonitor` that `AbstractLifecycleSpecTest`'s `@Before` installs **fresh for every test** and its `@After` restores (Step 1b). Because it is fresh, the counts are absolute — no `before`/`after` delta, and no dependence on test execution order. The counting instance also has no log-level dependency and no 60 s throttle, so the counts are exact regardless of test log configuration.

- [ ] **Step 1b: Add the test bridge**

`SenderPool`, `SenderConnector` and `AbstractSender`'s package-private members are all in `com.im.njams.sdk.communication`, while the lifecycle tests live in `...communication.lifecycle`. Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderPoolTestAccess.java` as the one bridge across that boundary — every later task reuses it, so write it completely now:

```java
package com.im.njams.sdk.communication;

import java.util.concurrent.TimeUnit;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.lifecycle.LifecycleTestTransport;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Test bridge exposing the package-private {@link SenderPool} surface to tests in other packages.
 * Thin by design: it adds no logic, only visibility.
 */
public class SenderPoolTestAccess {

    private final SenderPool pool;

    private SenderPoolTestAccess(SenderPool pool) {
        this.pool = pool;
    }

    /**
     * @param discardPolicy the value for {@code njams.sdk.discardpolicy} ("none", "discard", "onconnectionloss").
     * @return a pool over the LIFECYCLE_TEST transport, with reconnect-before-connected allowed so a
     *         reconnect can be exercised without first completing a real startup connect.
     */
    public static SenderPoolTestAccess create(String discardPolicy) {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, discardPolicy);
        ClientSettings settings = ClientSettings.from(s.getAllProperties());
        SenderPool pool = new SenderPool(new CommunicationFactory(settings), new ConnectionCoordinator(), settings);
        pool.allowReconnectBeforeConnected();
        return new SenderPoolTestAccess(pool);
    }

    public Object acquire() {
        return pool.acquire();
    }

    public void release(Object sender) {
        pool.release((AbstractSender) sender);
    }

    public void reportFailure(Object sender, Exception cause) {
        pool.reportFailure((AbstractSender) sender, cause);
    }

    public void beginShutdown() {
        pool.beginShutdown();
    }

    public boolean isConnectionFailure() {
        return pool.isConnectionFailure();
    }

    public boolean isConnected(Object sender) {
        return ((AbstractSender) sender).isConnected();
    }

    /** @return a sender that was never connected, for driving {@link SenderPool#reportFailure} directly. */
    public Object newUnconnectedSender() {
        return new com.im.njams.sdk.communication.lifecycle.LifecycleTestSender();
    }

    /**
     * Drives the group into the {@code reconnecting} state through the real code path: the transport is set to
     * BLOCK so the elected reconnect loop parks inside {@code connect()}, leaving the pool genuinely
     * reconnecting rather than having a flag poked.
     */
    public void forceReconnecting() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        pool.reportFailure((AbstractSender) newUnconnectedSender(), new IllegalStateException("test-induced loss"));
    }

    /** Completes the blocked reconnect and returns the sender the connector published to the pool. */
    public Object publishConnectedSender() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        LifecycleTestTransport.releaseBlockedConnect();
        return pool.awaitPublishedSenderForTest();
    }

    public boolean awaitConnectionFailure(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (pool.isConnectionFailure()) {
                return true;
            }
            Thread.sleep(25);
        }
        return pool.isConnectionFailure();
    }

    public int exceptionListenerFireCount() {
        return pool.exceptionListenerFireCountForTest();
    }

    public boolean isRetired(Object sender) {
        return pool.isRetiredForTest((AbstractSender) sender);
    }

    public boolean wasClosed(Object sender) {
        return ((com.im.njams.sdk.communication.lifecycle.LifecycleTestSender) sender).wasClosed();
    }

    public void notifyConnectionFailureOn(Object sender, Exception cause) {
        ((com.im.njams.sdk.communication.lifecycle.LifecycleTestSender) sender).reportAsyncFailure(cause);
    }
}
```

**The shared harness owns the monitor lifecycle.** A test must never assume the monitor's initial state — the instance is a JVM-wide static, so both a stale override and a stale counter leak between tests. Install a **fresh** instance before each test in `AbstractLifecycleSpecTest` (`AbstractLifecycleSpecTest.java:13-25`) and restore the real one after, so every inheriting test starts from a zeroed counter it can assert absolutely:

```java
public abstract class AbstractLifecycleSpecTest {

    /** Fresh per test, so counts start at zero and no test depends on another's leftovers. */
    protected CountingDiscardMonitor discardMonitor;
    protected CountingThrottleMonitor throttleMonitor;

    @Before
    public void resetLifecycleTransport() {
        LifecycleTestTransport.reset();
        discardMonitor = CountingDiscardMonitor.install();
        throttleMonitor = CountingThrottleMonitor.install();
    }

    @After
    public void stopLifecycleSendersAndReceivers() {
        LifecycleTestTransport.shutdownAllSenders();
        LifecycleTestTransport.shutdownAllReceivers();
        CountingDiscardMonitor.restore();
        CountingThrottleMonitor.restore();
    }
}
```

`CountingDiscardMonitor`/`CountingThrottleMonitor` are `public` (in `com.im.njams.sdk.communication`) precisely so the `lifecycle` package can install them; only `DiscardMonitor.setInstance` stays package-private, and the `install()`/`restore()` statics are the sanctioned way in.

Because the harness installs the monitor, `SenderPoolTestAccess` needs **no** discard plumbing at all — tests read `discardMonitor.count()` directly. Do not also install one inside `SenderPoolTestAccess.create(...)`: two installs per test means the second silently replaces the first and one of the two references counts nothing.

Note `SenderCloseOrderingSpecTest` does **not** extend that base class and has no `@After` at all (Appendix A.5), so it gets neither the install nor the restore. It asserts nothing about discards today; if it ever needs to, give it its own `@Before`/`@After` pair rather than reaching for the base class.

Three small test-only hooks this needs:
- `SenderPool.awaitPublishedSenderForTest()` — package-private; waits (bounded, e.g. 5 s) for `onReconnected(...)` to publish a sender and returns it. Implement it on the pool with the same `lock`/`wait` pair rather than polling.
- `SenderPool.exceptionListenerFireCountForTest()` and `SenderPool.isRetiredForTest(AbstractSender)` — package-private accessors over the fire counter and the `retired` set. Add an `int listenerFireCount` incremented in `reportFailure` when the elected branch fires listeners.
- `LifecycleTestSender.wasClosed()` and `LifecycleTestSender.reportAsyncFailure(Exception)` — a `volatile boolean closed` set in `close()`, and a public hook calling the `protected notifyConnectionFailure(...)` (added in Task 4; add the hook then and leave a compile-time stub now, or land Task 4 before this test).

Marking production methods `…ForTest` is a smell, but the alternative is either widening real API or reflection. Prefer these three narrowly-named package-private accessors, and say so in a comment on each.

- [ ] **Step 2: Run to verify failure**

```bash
mvn test -Dtest=SenderPoolAcquireSpecTest -pl njams-sdk
```

Expected: compile errors — `acquire`, `release`, `reportFailure` do not exist.

- [ ] **Step 3: Implement the pool**

In `SenderPool.java`:

1. **Fields.** Replace the two `ConcurrentHashMap`-backed sets with plain `IdentityHashMap`-backed sets guarded by the new lock, and add the rest:

```java
    private final Object lock = new Object();
    private final Set<AbstractSender> locked = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<AbstractSender> unlocked = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<AbstractSender> retired = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Collection<SenderExceptionListener> exceptionListeners =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final DiscardPolicy discardPolicy;
    private final SenderConnector connector;
    private boolean reconnecting = false;
    private volatile boolean failed = false;
    private boolean shutdown = false;
```

`exceptionListeners` keeps its `IdentityHashMap` identity semantics but is from now on **only ever touched under `lock`**, and fired from a copy taken under `lock` — that is the §5.2 race fix. Do not switch it to a `ConcurrentHashMap`-backed set: that would silently change identity comparison to `equals`.

2. **Constructor.** The pool needs the settings to read the discard policy once (never per message — hot-path rule) and to build the connector. Change the two constructors so `SenderPool(CommunicationFactory, ConnectionCoordinator, ClientSettings)` is the real one, and have `NjamsSender.init()` pass its `settings`. Read the policy with `DiscardPolicy.byValue(settings.getProperty(NjamsSettings.PROPERTY_DISCARD_POLICY))` — identical to `AbstractSender.init` and `MaxQueueLengthHandler`.

3. **`acquire()`** — per §5.2 and §8.3:

```java
    /**
     * Hands out a connected sender, applying the group's discard policy while a reconnect is in progress.
     *
     * @return a CONNECTED sender, or {@code null} if the group is shutting down or the discard policy chose to
     *         drop the message rather than wait.
     */
    AbstractSender acquire() {
        final boolean discardsOnFailure =
            discardPolicy == DiscardPolicy.DISCARD || discardPolicy == DiscardPolicy.ON_CONNECTION_LOSS;
        synchronized (lock) {
            while (true) {
                if (shutdown) {
                    return null;
                }
                if (reconnecting) {
                    if (discardsOnFailure) {
                        DiscardMonitor.discard();
                        LOG.debug("Applying discard policy [{}]. Message discarded.", discardPolicy);
                        return null;
                    }
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue;
                }
                final AbstractSender sender = takeUnlockedOrCreate();
                if (sender != null) {
                    return sender;
                }
                // create/connect failed and routed into reportFailure; re-evaluate under the new state
            }
        }
    }
```

`takeUnlockedOrCreate()` must be called with `lock` held: take from `unlocked` if non-empty, else `create()` + `connect()`. On a connect failure it closes the half-built sender, sets the failure state inline (it already holds the lock — do **not** call `reportFailure` re-entrantly), and returns `null` so the loop re-evaluates. Hoist the actual `connector.startReconnect(...)` and listener firing to after the `synchronized` block, or the connect-failure path will fire listeners under the monitor.

> **B2 is settled: `connect()` stays inside `synchronized (lock)`.** Do not restructure this to connect outside the lock — that variant was explicitly declined. Add a comment on `takeUnlockedOrCreate()` recording that the connect is deliberately inside the monitor, that this is bounded by `maxSenderThreads` and only reached while the group is healthy, and that a partially-degraded endpoint is the known residual risk (flagged in Task 10 Step 6). Without that comment the next reader will "fix" it.

4. **`reportFailure(...)`** — copy §5.2's shape exactly, keeping every `destroy` outside the lock:

```java
    void reportFailure(AbstractSender sender, Exception cause) {
        List<AbstractSender> toDestroy = Collections.emptyList();
        List<SenderExceptionListener> listeners = Collections.emptyList();
        final boolean elected;
        synchronized (lock) {
            locked.remove(sender);
            retired.remove(sender);
            elected = !reconnecting;
            if (elected) {
                reconnecting = true;
                failed = true;
                toDestroy = drain(unlocked);
                retired.addAll(locked);
                listeners = new ArrayList<>(exceptionListeners);
            }
        }
        destroy(sender);
        toDestroy.forEach(this::destroy);
        if (elected) {
            listeners.forEach(l -> l.onException(cause, null));
            connector.startReconnect(cause);
        }
    }
```

`drain(Set)` is a new private helper: copy the contents to a `List`, `clear()` the set, return the list.

**Note the `onException(exception, msg)` signature.** `SenderExceptionListener.onException(Exception, CommonMessage)` takes the message, but a per-outage notification has no single message. Passing `null` is the honest encoding of "the group failed, not this message". Flag this in the Task 10 review: SDK-473 consumes this listener and must tolerate a `null` message. Do **not** change the `SenderExceptionListener` interface here — it is public API.

5. **`release(...)`**:

```java
    void release(AbstractSender sender) {
        final boolean wasRetired;
        synchronized (lock) {
            locked.remove(sender);
            wasRetired = retired.remove(sender);
            if (!wasRetired && !shutdown) {
                unlocked.add(sender);
            }
        }
        if (wasRetired || isShutdown()) {
            destroy(sender);
        }
    }
```

6. **`onReconnected(...)`**:

```java
    void onReconnected(AbstractSender connected) {
        synchronized (lock) {
            reconnecting = false;
            failed = false;
            unlocked.add(connected);
            lock.notifyAll();
        }
    }
```

7. **`isConnectionFailure()`** becomes `return failed;` — delete the `streamAll().anyMatch(AbstractSender::hasConnectionFailure)` scan. Give it Javadoc explaining it is the group flag feeding `MaxQueueLengthHandler`'s `ON_CONNECTION_LOSS` branch, and why a scan would go blind (§5.2).

8. **`beginShutdown()`** must now also wake waiters:

```java
    public void beginShutdown() {
        LOG.debug("Beginning sender group shutdown; cancelling reconnects.");
        coordinator.setShouldShutdown(true);
        connector.cancelReconnect();
        synchronized (lock) {
            shutdown = true;
            lock.notifyAll();
        }
    }
```

Careful: today `beginShutdown()` deliberately does **not** block new sender creation (its Javadoc says so, so in-flight sends can borrow while the executor drains), while `declareShutdown()` does. Setting `shutdown = true` here changes that. Per §9 the wake is required, but preserve the "drain can still send" property: use a **separate** `boolean draining` flag for the wake-and-refuse-waiters behaviour, or keep `shutdown` for `declareShutdown()` and have `acquire()` check `coordinator.shouldShutdown()` for the wake condition. Pick one, and update both methods' Javadoc to describe the real behaviour. Verify against `SenderShutdownSpecTest` and `SenderCloseOrderingSpecTest` — those encode the current ordering contract.

9. **`addSenderExceptionListener(...)`** stops copying onto senders:

```java
    public void addSenderExceptionListener(SenderExceptionListener listener) {
        synchronized (lock) {
            exceptionListeners.add(listener);
        }
    }
```

10. **`create()`** — inject the failure sink (B3), drop the coordinator injection and the listener fan-out, and connect:

```java
    protected AbstractSender create() {
        if (shutdown) {
            return null;
        }
        final AbstractSender sender = factory.getSender();
        sender.setFailureSink(this::reportFailure);
        return sender;
    }
```

`setFailureSink` lands in Task 4; add a temporary no-op or defer this one line until then, whichever keeps the build green.

11. Keep `get()`, `close(AbstractSender)`, `validate(...)`, `expireAll()`, `destroy(...)`, `streamAll()` for now — Task 6 removes the first three. `expireAll()` must also drain `retired`.

12. **Update the class Javadoc.** Its long "virtually bounded" paragraph references `get()` and `close(AbstractSender)`; rewrite it in terms of `acquire()`/`release(...)` and add the retirement contract.

- [ ] **Step 4: Run the new tests**

```bash
mvn test -Dtest=SenderPoolAcquireSpecTest -pl njams-sdk
```

Expected: all PASS.

- [ ] **Step 5: Full suite + commit**

```bash
mvn test -pl njams-sdk
mvn validate -Pcheckstyle -pl njams-sdk
git add -A njams-sdk/src
git commit -m "SDK-472 Give SenderPool acquire/release/reportFailure with single-reconnector election"
```

---

## Task 4: `AbstractSender.notifyConnectionFailure` + the failure sink

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderFailureSink.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/JmsSender.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/AsyncFailureReportingSpecTest.java` (new)

**Interfaces:**
- Produces: `protected final void AbstractSender.notifyConnectionFailure(Exception cause)`; package-private `void AbstractSender.setFailureSink(SenderFailureSink)`; package-private interface `SenderFailureSink.onConnectionFailure(AbstractSender, Exception)`.
- Consumed by: `JmsSender.onException(JMSException)`, `SenderPool.create()` (Task 3).

Purely additive to the SPI (§6 "Added"), so it can land before the removals.

- [ ] **Step 1: Write the failing test**

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderPoolTestAccess;

public class AsyncFailureReportingSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void notifyConnectionFailureReachesThePool() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object sender = pool.acquire();
        // Simulate JMS's async ExceptionListener path: a failure with no send call in flight.
        pool.notifyConnectionFailureOn(sender, new IllegalStateException("async loss"));
        assertTrue("the pool must see the group as failed",
            pool.awaitConnectionFailure(5, TimeUnit.SECONDS));
    }
}
```

Add `notifyConnectionFailureOn(Object, Exception)` and `awaitConnectionFailure(long, TimeUnit)` to `SenderPoolTestAccess`. `notifyConnectionFailureOn` needs to invoke a `protected` method from the same package — expose it via a tiny package-private test subclass of `AbstractSender`, or have `SenderPoolTestAccess` build a `LifecycleTestSender` and add a `public void reportAsyncFailure(Exception)` hook to `LifecycleTestSender` that calls `notifyConnectionFailure(...)`. Prefer the latter — `LifecycleTestSender` is already the designated controllable fake.

- [ ] **Step 2: Run to verify failure**

```bash
mvn test -Dtest=AsyncFailureReportingSpecTest -pl njams-sdk
```

Expected: compile error — `notifyConnectionFailure` does not exist.

- [ ] **Step 3: Create `SenderFailureSink`**

With the copyright header:

```java
package com.im.njams.sdk.communication;

/**
 * Callback through which a sender reports a connection failure to the {@link SenderPool} that owns it.
 * Internal SDK infrastructure — deliberately package-private so it never becomes part of the sender SPI.
 */
interface SenderFailureSink {

    /**
     * Reports that the given sender's connection has failed.
     *
     * @param sender the sender whose connection failed.
     * @param cause  the failure; may be {@code null}.
     */
    void onConnectionFailure(AbstractSender sender, Exception cause);
}
```

- [ ] **Step 4: Add the sink and the notify method to `AbstractSender`**

```java
    private volatile SenderFailureSink failureSink;

    /**
     * Injects the callback through which this sender reports connection failures to its owning pool. Called by
     * {@link SenderPool} right after creation. Package-private: not part of the sender SPI.
     *
     * @param failureSink the sink to report failures to.
     */
    void setFailureSink(SenderFailureSink failureSink) {
        this.failureSink = failureSink;
    }

    /**
     * Reports a connection failure that was detected outside a {@code send} call, so the owning
     * {@link SenderPool} can retire this sender and run a single reconnect for its group.
     * <p>
     * Implement this call only if your transport can detect a broken connection asynchronously — for example
     * from a listener callback on a transport-internal thread, with no send in progress. A failure that surfaces
     * from a {@code send} must simply be thrown: the SDK reports it for you.
     *
     * @param cause the failure that was detected; may be {@code null}.
     */
    protected final void notifyConnectionFailure(Exception cause) {
        final SenderFailureSink sink = failureSink;
        if (sink != null) {
            sink.onConnectionFailure(this, cause);
        } else {
            LOG.debug("No failure sink set on sender {}; connection failure not reported.", getName(), cause);
        }
    }
```

Verify against `checkstyle.xml`'s relocated-package list: the signature uses only `Exception`, so it is clean.

- [ ] **Step 5: Point `JmsSender` at it**

`JmsSender.java:381-384` currently forwards to the inherited `onException(Exception)`. Change to:

```java
    @Override
    public synchronized void onException(JMSException exception) {
        notifyConnectionFailure(new NjamsSdkRuntimeException("JMS Exception", exception));
    }
```

Do not touch `JmsSender.tryToSend`'s discard check (§8.1 — it stays exactly as it is).

- [ ] **Step 6: Run the test, then the full suite, then commit**

```bash
mvn test -Dtest=AsyncFailureReportingSpecTest -pl njams-sdk
mvn test -pl njams-sdk
mvn validate -Pcheckstyle -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
git add -A njams-sdk/src
git commit -m "SDK-472 Add AbstractSender.notifyConnectionFailure for async transport failures"
```

---

## Task 5: `NjamsSender` — the retention loop

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java:191-213` (send/executor task), `:107-108` (`startupSender` field), `:221-230` (`beginConnect`), `:253-287` (`startWithTimeout`), `:154-170` (`init`)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/MessageRetentionSpecTest.java` (new)

**Interfaces:**
- Consumes: `SenderPool.acquire()`, `release(...)`, `reportFailure(...)` (Task 3); `SenderConnector.beginConnect()`, `awaitStartup(long)` reached through the pool.
- Produces: unchanged public surface — `send(CommonMessage, String)`, `beginConnect()`, `startWithTimeout(long)`, `startWithTimeout(long, boolean)`, `close()`, `getName()`, `addSenderExceptionListener(...)`.

This is the switch-over: after it, the SDK no longer drives `AbstractSender`'s lifecycle machinery at all, which is what makes Task 6's removals mechanical.

- [ ] **Step 1: Write the failing test**

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.settings.ClientSettings;

public class MessageRetentionSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void aMessageSurvivesItsSenderBeingRetiredAndIsSentOnce() throws Exception {
        NjamsSender sender = new NjamsSender(
            ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        assertTrue(sender.startWithTimeout(5000));

        // Arm the first send to block, then fail and drop the connection.
        LifecycleTestTransport.armSendBlocksThenFails();
        sender.send(new LogMessage(), "session-1");
        assertTrue("the send must reach the transport",
            LifecycleTestTransport.sendEnteredLatch().await(5, TimeUnit.SECONDS));

        // Let the send fail; the worker must retain the message and retry on a fresh sender.
        LifecycleTestTransport.disarmSendFailure();
        LifecycleTestTransport.releaseSend();

        assertTrue("the retained message must be delivered by a fresh sender",
            LifecycleTestTransport.awaitSuccessfulSends(1, 10, TimeUnit.SECONDS));
        assertEquals("delivered exactly once", 1, LifecycleTestTransport.successfulSendCount());
        sender.close();
    }

    /**
     * Spec 10: the relocated base-class discard branch must behave through acquire() exactly as it did inside
     * AbstractSender.send - a message dispatched while the group is reconnecting is discarded, not retained.
     */
    @Test
    public void aMessageDispatchedWhileReconnectingIsDiscardedUnderOnConnectionLoss() throws Exception {
        assertMessageDiscardedWhileReconnecting("onconnectionloss");
    }

    @Test
    public void aMessageDispatchedWhileReconnectingIsDiscardedUnderDiscard() throws Exception {
        assertMessageDiscardedWhileReconnecting("discard");
    }

    private void assertMessageDiscardedWhileReconnecting(String discardPolicy) throws Exception {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, discardPolicy);
        NjamsSender sender = new NjamsSender(ClientSettings.from(s.getAllProperties()));
        assertTrue(sender.startWithTimeout(5000));

        try {
            // Drive the group into a reconnect that cannot complete, then dispatch into it.
            LifecycleTestTransport.armSendBlocksThenFails();
            LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
            sender.send(new LogMessage(), "session-1");
            assertTrue(LifecycleTestTransport.sendEnteredLatch().await(5, TimeUnit.SECONDS));
            LifecycleTestTransport.releaseSend();

            int successesBefore = LifecycleTestTransport.successfulSendCount();
            sender.send(new LogMessage(), "session-2");

            // discardMonitor is installed fresh by AbstractLifecycleSpecTest's @Before; do not install another.
            assertTrue("the dispatched message must be discarded, not retained",
                discardMonitor.awaitAtLeast(1, 10, TimeUnit.SECONDS));
            assertEquals("a discarded message must never be delivered", successesBefore,
                LifecycleTestTransport.successfulSendCount());
        } finally {
            sender.close();
        }
    }
}
```

`awaitAtLeast(...)` rather than an absolute `assertEquals` here: dispatch happens on an executor worker, so the count is reached asynchronously. The absolute form is right in Task 3, where `acquire()` is called directly on the test thread. Imports needed: `NjamsSettings`, `Settings`.

`LifecycleTestTransport` needs three additions for this: `disarmSendFailure()`, a `successfulSendCount()` counter incremented in `LifecycleTestSender`'s typed `send` methods when the fail mode is not armed, and `awaitSuccessfulSends(int, long, TimeUnit)`. Add them alongside the existing controls and reset them in `reset()` — that file is test infrastructure, not an assertion, so extending it is in scope.

- [ ] **Step 2: Run to verify failure**

```bash
mvn test -Dtest=MessageRetentionSpecTest -pl njams-sdk
```

Expected: fails (or errors on the missing transport controls). Before implementing, confirm it fails for the *right* reason — the message being dropped, not a harness gap.

- [ ] **Step 3: Replace the executor task with the retention loop**

Per §7, replacing `NjamsSender.java:197-212`:

```java
        executor.execute(() -> dispatch(msg, clientSessionId));
```

and adding:

```java
    /**
     * Sends one message, holding it on this worker thread across sender failures. The message is never
     * re-submitted to the executor: doing so would deadlock, because a full queue makes
     * {@link MaxQueueLengthHandler} block the submitting thread, and during an outage every worker is a
     * submitter. Instead the sender is treated as the replaceable resource — on failure the sender is retired
     * and a fresh, connected one is acquired for the same message.
     *
     * @param msg             the message to send.
     * @param clientSessionId the session ID of the sending {@link Njams} instance.
     */
    private void dispatch(CommonMessage msg, String clientSessionId) {
        while (!Thread.currentThread().isInterrupted()) {
            final AbstractSender sender = senderPool.acquire();
            if (sender == null) {
                // shutting down, or the discard policy gave up on this message
                return;
            }
            try {
                sender.send(msg, clientSessionId);
                senderPool.release(sender);
                return;
            } catch (Exception e) {
                LOG.debug("Send failed on sender {}; retiring it and retrying the message.", sender.getName(), e);
                senderPool.reportFailure(sender, e);
            }
        }
    }
```

Keep the `executor.isShutdown() || executor.isTerminating()` guard and the `debugDumper.dump(...)` call in `send(...)` exactly as they are.

- [ ] **Step 4: Delegate startup to the connector**

- Delete the `startupSender` field (`:107-108`).
- `beginConnect()` becomes a straight delegation and no longer needs `synchronized` (the connector is idempotent and thread-safe):

```java
    /**
     * Pre-warms the group's connection in the background so a slow connect overlaps application setup.
     * Idempotent and thread-safe; delegates to the group's single connector.
     */
    public void beginConnect() {
        senderPool.beginConnect();
    }
```

- `startWithTimeout(long, boolean)` loses the borrow / `finally { senderPool.close(s); startupSender = null; }` dance and the `s.cancelReconnect()` + `s.reconnect(...)` pair. The `reconnectOnFailure` semantics must be preserved exactly: on timeout under the `reconnect` policy, allow reconnect-before-connected, kick the reconnect loop, return `true`; under fail-fast, cancel and return `false`.

```java
    public boolean startWithTimeout(long timeoutMs, boolean reconnectOnFailure) {
        if (reconnectOnFailure) {
            senderPool.allowReconnectBeforeConnected();
        }
        if (senderPool.awaitStartup(timeoutMs)) {
            LOG.debug("Sender connected during startup within {} ms.", timeoutMs);
            return true;
        }
        if (reconnectOnFailure) {
            LOG.info("Initial connect did not complete within {} ms; retrying in the background "
                + "(startup fail-behavior 'reconnect').", timeoutMs);
            senderPool.restartConnectInBackground(timeoutMs);
            return true;
        }
        LOG.debug("Sender did not connect within {} ms; cancelling startup connect (fail-fast).", timeoutMs);
        senderPool.cancelConnect();
        return false;
    }
```

Add `beginConnect()`, `awaitStartup(long)`, `restartConnectInBackground(long)` and `cancelConnect()` to `SenderPool` as thin delegations to its connector. `restartConnectInBackground` reproduces today's two-step (`cancelReconnect()` to unblock a still-blocked connect, then `startReconnect(...)`) — read the comment at `NjamsSender.java:272-274`, it explains exactly why both calls are needed, and carry that reasoning into the new method's Javadoc.

- Pass `settings` to the pool in `init()` so it can read the discard policy (Task 3, item 2).

- [ ] **Step 5: Run the new test and the startup/shutdown spec tests**

```bash
mvn test -Dtest=MessageRetentionSpecTest -pl njams-sdk
mvn test -Dtest=SenderStartupSpecTest+SenderStartGatingSpecTest+SenderShutdownSpecTest+SenderCloseOrderingSpecTest -pl njams-sdk
```

The lifecycle spec tests are the parity check on startup/shutdown behaviour. If one fails, decide honestly whether the *wiring* changed (fix the test in Task 6) or the *behaviour* changed (stop and raise it).

- [ ] **Step 6: Full suite + commit**

```bash
mvn test -pl njams-sdk
git add -A njams-sdk/src
git commit -m "SDK-472 Retain the message on the worker thread and acquire a fresh sender on failure"
```

---

## Task 6: Remove the lifecycle members from `AbstractSender`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java` (drop `get`, `close(AbstractSender)`, `validate`)
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTestSender.java`
- Modify: the test classes listed under "Tests — modified" above

**Interfaces:**
- Removes, per §6/D2.9: `reconnect(Exception)`, `doReconnect(Exception)`, `onException(Exception)`, `beginConnect()`, `awaitStartup(long)`, `setShouldShutdown(boolean)`, `cancelReconnect()`, `hasConnectionFailure()` + `hasConnectionFailure` field, `setConnectionCoordinator(...)`, `addExceptionListener(SenderExceptionListener)` + `exceptionListeners` field, plus the now-unused `reconnector`/`startupBegun`/`startupLatch`/`startupConnector`/`startupError`/`coordinator` fields and `getExceptionWithCauses`.
- Reduces: `send(CommonMessage, String)` to `instanceof` dispatch only.
- Keeps: the three abstract `send` methods, `init`, `connect`, `close`, `getName`, `isConnected`/`isDisconnected`/`isConnecting`, `setConnectionStatus`/`getConnectionStatus`, the `discardPolicy` and `settings` protected fields, and Task 4's `notifyConnectionFailure`/`setFailureSink`.

- [ ] **Step 1: Reduce `send(CommonMessage, String)`**

Replace `AbstractSender.java:269-325` with:

```java
    /**
     * Dispatches the given message to the matching typed {@code send} method. One honest attempt: any failure
     * propagates to the caller, which retires this sender and retries the message on a fresh one.
     * <p>
     * The connection is guaranteed to be established — {@link SenderPool#acquire()} only hands out connected
     * senders — so this method does not check the connection status, retry, or apply the discard policy. Those
     * are the pool's responsibility.
     *
     * @param msg             the message to send
     * @param clientSessionId the session ID of the {@link com.im.njams.sdk.Njams} instance that sends the message
     */
    public void send(CommonMessage msg, String clientSessionId) {
        LOG.trace("Sending message {}, state={}", msg, getConnectionStatus());
        if (msg instanceof LogMessage) {
            send((LogMessage) msg, clientSessionId);
        } else if (msg instanceof ProjectMessage) {
            send((ProjectMessage) msg, clientSessionId);
        } else if (msg instanceof TraceMessage) {
            send((TraceMessage) msg, clientSessionId);
        }
    }
```

- [ ] **Step 2: Delete the lifecycle members and their now-unused fields and imports**

Remove every member in the Interfaces list above. Then drop the imports that became unused: `java.util.Collection`, `java.util.Collections`, `java.util.IdentityHashMap`, `java.util.concurrent.CountDownLatch`, `java.util.concurrent.TimeUnit`, `java.util.concurrent.atomic.AtomicBoolean`, and `DiscardMonitor`/`DiscardPolicy` if no longer referenced (`discardPolicy` the field stays, so `DiscardPolicy` does too).

- [ ] **Step 3: Document the new SPI invariant on the class Javadoc**

Per §6 and open item §12.2. Append to the `AbstractSender` class Javadoc:

```java
 * <p>
 * <b>A {@code send} implementation must not block indefinitely.</b> When a connection fails, the SDK retires the
 * group's senders and lets one reconnect; that only converges because every send is time-bounded. The built-in
 * transports bound themselves explicitly (HTTP retries 20 times at 50 ms, JMS 100 times at 50 ms, Kafka waits at
 * most its request timeout, capped at 6 s). A sender that can block forever stalls retirement for its whole
 * group, so bound your own retry loops the same way and throw once the bound is reached.
 * <p>
 * The SDK also drives the connection lifecycle: implement {@link #connect()}, {@link #close()} and the typed
 * {@code send} methods as single honest attempts that throw on failure, and do not implement reconnect logic —
 * the SDK runs exactly one reconnect per sender group. If your transport detects a broken connection
 * asynchronously, report it with {@link #notifyConnectionFailure(Exception)}.
```

- [ ] **Step 4: Drop the superseded pool methods**

Delete `SenderPool.get()`, `SenderPool.close(AbstractSender)` and `SenderPool.validate(AbstractSender)` — the last one is the standing `// TODO: there must be a better solution!` at `SenderPool.java:109-112`, answered by retirement (§5.2). Also remove the `sender.setShouldShutdown(true)` calls in `destroy(...)` and `declareShutdown()`, which no longer compile.

- [ ] **Step 5: Fix `LifecycleTestSender`**

Its `shutdownAll()` calls `setShouldShutdown(true)` and `cancelReconnect()` on each sender (lines 34-40) — both gone. The equivalent now lives on the pool/connector, and per-instance daemon threads no longer exist, so the registry's purpose largely disappears. Reduce `shutdownAll()` to closing each registered sender and clearing the list. Add the `reportAsyncFailure(Exception)` hook from Task 4 Step 1 and the `successfulSendCount` increments from Task 5 Step 1 if not already added.

- [ ] **Step 6: Migrate the affected test classes — wiring only**

Work through the classes below. **Assertions stay byte-for-byte identical**; only the calls that reach the relocated code change. Per spec §10: *if an assertion cannot survive the move, stop and raise it rather than adjusting the assertion.*

- `lifecycle/SenderReconnectGatingSpecTest` — both tests build a bare sender via `freshSender()` and call `s.reconnect(...)` / `s.beginConnect()` / `s.awaitStartup(...)` (lines 16-46). Drive a `SenderConnector` through `SenderConnectorTestAccess` instead. `neverConnectedSenderDoesNotReconnect` → `connector.startReconnect(...)` with the coordinator never having connected; `reconnectRunsAfterAPriorSuccessfulConnect` → `awaitStartup(5000)` then force the group disconnected and `startReconnect(...)`. Keep both `assertFalse`/`assertTrue` messages and timeouts exactly.
- `lifecycle/SenderStartupSpecTest`, `lifecycle/SenderStartGatingSpecTest`, `lifecycle/SenderShutdownSpecTest`, `lifecycle/SenderLoggingSpecTest`, `SenderCloseOrderingSpecTest`, `AbstractSenderStaticStateTest` — see the per-file catalogue appended as **Appendix A**; migrate each listed call site to its replacement there.
- `SenderPoolTest` — all three tests use `op.get()` / `op.close(s)` and override `create()`. Rewire to `acquire()` / `release(...)`. Note `expireAll` and `getReturnsNullAfterShutdownDeclared` rely on `create()` returning an unconnected mock; `acquire()` now connects, so the mock must report `isConnected()` — `when(mockedAC.isConnected()).thenReturn(true)`. `getReusesUnlockedSenderInsteadOfCreatingNew`'s `assertEquals(1, created.get())` is exactly the reuse guarantee and must still hold.
- `NjamsSenderTest` — **delete `testReconnectingSenders` (L172-197) and the inner `ExceptionSender` (L372-416)** per resolved decision B1; see B1 for the follow-up cleanup (the `counter` field and dangling imports). Everything else in that class (the `IllegalArgument*` tests, `testConfiguredNjamsSender`, the discard-policy tests, the alternative-key tests, `reconnectOnStartupFailure*`) drives `NjamsSender`'s public surface and should pass unchanged — the discard-policy tests in particular spy `NjamsSender.send` and never reach the pool, so they are unaffected.

- [ ] **Step 7: Verify — this is the parity gate**

```bash
mvn test -pl njams-sdk
mvn validate -Pcheckstyle -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```

Expected: the same test count as the Task 0 baseline minus only whatever B1 removed, all passing. Checkstyle and Javadoc clean — the Javadoc build will catch any `{@link}` still pointing at a removed member, which is a hard error.

- [ ] **Step 8: Commit**

```bash
git add -A njams-sdk/src
git commit -m "SDK-472 Remove the reconnect lifecycle from AbstractSender, leaving connect/send/close"
```

---

## Task 7: Retirement and shared-communications behaviour tests

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderRetirementSpecTest.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SharedSenderOutageSpecTest.java`

These are the remaining §10 Step 2 items not already covered by Tasks 2-5. Separate commit from the relocation, per §10.

- [ ] **Step 1: Write the retirement test**

Covers D2.4 — an in-use sender is flagged, never closed by another thread:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderPoolTestAccess;

public class SenderRetirementSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void anInUseSenderIsNotClosedUntilItsBorrowerReleasesIt() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object borrowed = pool.acquire();
        Object other = pool.acquire();

        // A failure on `other` retires `borrowed` without closing it.
        pool.reportFailure(other, new IllegalStateException("group loss"));

        assertFalse("a checked-out sender must not be closed by the failing thread", pool.wasClosed(borrowed));
        assertTrue("but it must be flagged retired", pool.isRetired(borrowed));

        pool.release(borrowed);
        assertTrue("its borrower closes it on release", pool.wasClosed(borrowed));
    }

    @Test
    public void idleSendersAreDestroyedImmediatelyOnFailure() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object idle = pool.acquire();
        pool.release(idle);                       // now idle in `unlocked`
        Object failing = pool.acquire();

        pool.reportFailure(failing, new IllegalStateException("group loss"));

        assertTrue("nobody holds an idle sender, so it is closed at once", pool.wasClosed(idle));
    }
}
```

Add `isRetired(Object)` and `wasClosed(Object)` to `SenderPoolTestAccess` (the latter by tracking `LifecycleTestSender.close()` calls, e.g. a `closed` flag on the fake).

- [ ] **Step 2: Write the shared-communications test**

Covers §5.4's two load-bearing claims — one reconnector across two `Njams` instances, and one instance's `stop()` not tearing down the group:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.SenderExceptionListener;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

public class SharedSenderOutageSpecTest extends AbstractLifecycleSpecTest {

    /** Counts notifications and records that the listener was reached, per registered instance. */
    private static final class CountingListener implements SenderExceptionListener {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void onException(Exception exception, CommonMessage msg) {
            count.incrementAndGet();
        }
    }

    private final List<NjamsSender> taken = new ArrayList<>();

    /**
     * The shared sender is a JVM-wide static (NjamsSender:92, 141-148) that is reference-counted, so it must be
     * closed exactly as often as it was taken or it leaks into later tests.
     */
    @After
    public void releaseSharedSenders() {
        taken.forEach(NjamsSender::close);
        taken.clear();
    }

    private NjamsSender take() {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
        NjamsSender sender = NjamsSender.takeSharedSender(ClientSettings.from(s.getAllProperties()));
        taken.add(sender);
        return sender;
    }

    @Test
    public void oneOutageElectsOneReconnectorAndNotifiesEveryRegisteredListener() throws Exception {
        NjamsSender first = take();
        NjamsSender second = take();
        assertSame("both takers must share one group", first, second);

        CountingListener a = new CountingListener();
        CountingListener b = new CountingListener();
        first.addSenderExceptionListener(a);
        second.addSenderExceptionListener(b);
        assertTrue(first.startWithTimeout(5000));

        // One outage: every send fails, and the connect that follows blocks so the group stays reconnecting.
        int connectsBeforeOutage = LifecycleTestTransport.senderConnectCount();
        LifecycleTestTransport.armSendBlocksThenFails();
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        for (int i = 0; i < 4; i++) {
            first.send(new LogMessage(), "session-" + i);
        }
        assertTrue("a send must reach the transport",
            LifecycleTestTransport.sendEnteredLatch().await(5, TimeUnit.SECONDS));
        LifecycleTestTransport.releaseSend();

        assertTrue("exactly one reconnect must be attempted for the whole JVM group",
            LifecycleTestTransport.awaitConnectAttempts(connectsBeforeOutage + 1, 10, TimeUnit.SECONDS));
        assertEquals("one reconnector, not one per failing sender", connectsBeforeOutage + 1,
            LifecycleTestTransport.senderConnectCount());

        // Listener fan-out is N receivers per outage (spec 5.4), one per registered instance, fired once each.
        assertEquals("the first instance's listener is notified exactly once", 1, a.count.get());
        assertEquals("the second instance's listener is notified exactly once", 1, b.count.get());
    }

    @Test
    public void oneInstancesCloseDoesNotShutTheGroupDownForItsSibling() {
        NjamsSender first = take();
        NjamsSender second = take();
        assertTrue(first.startWithTimeout(5000));

        first.close();                    // reference-counted: must NOT really close the group
        taken.remove(first);              // already closed; do not close it twice in teardown

        assertTrue("the sibling's group must still be connected and usable", second.startWithTimeout(0));
    }
}
```

Imports needed beyond those shown: `java.util.ArrayList`, `java.util.List`, `java.util.concurrent.TimeUnit`, `java.util.concurrent.atomic.AtomicInteger`, `com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage`, and `assertEquals`/`assertSame`/`assertTrue` statics.

`LifecycleTestTransport.awaitConnectAttempts(int target, long timeout, TimeUnit unit)` is one more control to add: poll `senderConnectCount() >= target` against a deadline. Add it next to the counters from Task 5.

**Both assertions on the connect count are the point of the ticket** — before this change, N failing senders spawned N reconnect loops. If `senderConnectCount()` exceeds `connectsBeforeOutage + 1`, the election in `reportFailure` is leaking.

- [ ] **Step 3: Run and commit**

```bash
mvn test -Dtest=SenderRetirementSpecTest+SharedSenderOutageSpecTest -pl njams-sdk
mvn test -pl njams-sdk
git add -A njams-sdk/src/test
git commit -m "SDK-472 Add retirement and shared-communications outage tests"
```

---

## Task 8: Deadlock regression test

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderDeadlockRegressionTest.java`

§7's whole argument is that re-enqueueing deadlocks permanently. This test is the guard: it hangs against a naive re-enqueue implementation, which is exactly what makes it worth having. Give it a hard timeout so a regression fails instead of hanging CI.

- [ ] **Step 1: Write the test**

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

public class SenderDeadlockRegressionTest extends AbstractLifecycleSpecTest {

    /** Fails rather than hanging CI if the pool cannot make progress after a reconnect. */
    @Test(timeout = 60_000)
    public void theGroupRecoversWithAFullQueueAndEverySenderFailing() throws Exception {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "2");
        s.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "4");
        s.put(NjamsSettings.PROPERTY_MAX_QUEUE_LENGTH, "4");
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        NjamsSender sender = new NjamsSender(ClientSettings.from(s.getAllProperties()));
        assertTrue(sender.startWithTimeout(5000));

        // Every send fails and every connect fails: all workers busy, queue full, group reconnecting.
        LifecycleTestTransport.armSendBlocksThenFails();
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        for (int i = 0; i < 20; i++) {
            sender.send(new LogMessage(), "session-1");
        }
        LifecycleTestTransport.releaseSend();

        // Now let the connection come back and assert the pool actually drains.
        LifecycleTestTransport.disarmSendFailure();
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue("the pool must make progress once reconnected",
            LifecycleTestTransport.awaitSuccessfulSends(1, 30, TimeUnit.SECONDS));
        sender.close();
    }
}
```

- [ ] **Step 2: Run it, then run it again to check for flakiness**

```bash
mvn test -Dtest=SenderDeadlockRegressionTest -pl njams-sdk
mvn test -Dtest=SenderDeadlockRegressionTest -pl njams-sdk
```

Expected: PASS both times. A concurrency test that passes once is not passing. If it is flaky, fix the test's synchronisation (latches, not sleeps) rather than loosening the assertion.

- [ ] **Step 3: Full suite + commit**

```bash
mvn test -pl njams-sdk
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderDeadlockRegressionTest.java
git commit -m "SDK-472 Add deadlock regression test for message retention under a full queue"
```

---

## Task 9: Documentation review

**Files:**
- Review: `wiki/FAQ.md`
- Modify: only if the review finds a genuine inaccuracy

Resolves spec §12's last two open items. Both are expected to be **no-ops** — verify rather than assume, and do not invent documentation.

- [ ] **Step 1: Check the custom-transport paragraph**

`wiki/FAQ.md:793-799` is the only place the FAQ mentions `AbstractSender`. It describes providing a minimal implementation whose `send(...)` discards or records messages. The three abstract `send` methods are unchanged by this ticket, so this paragraph stays accurate. Confirm by reading it, and leave it alone unless it says something the reshape falsified.

- [ ] **Step 2: Check the discard-policy and startup-failbehavior entries**

`wiki/FAQ.md:255-267` documents `njams.sdk.communication.startup.failbehavior` and states that messages produced before the connection is established "are subject to the configured discard policy (`njams.sdk.discardpolicy`), which by default discards them while disconnected". Under §8.3 that remains true — `acquire()` discards under `DISCARD`/`ON_CONNECTION_LOSS` while reconnecting, and `DISCARD` is still the default (`DiscardPolicy.DEFAULT`). No behaviour change, so per `wiki-drafts.md` ("do not document a fix that doesn't change a property's original intention") **no edit**.

- [ ] **Step 3: Record the outcome**

No settings were added, changed, or removed by this ticket, so `njams-settings-sync` does not apply and `settings_full.properties` needs no change. State this explicitly in the commit or the ticket comment so the next reader does not re-derive it. If Steps 1-2 produced no edits, there is nothing to commit — say so rather than making a cosmetic change.

---

## Task 10: Close-out review

**Files:** none — this is a verification and Jira task.

- [ ] **Step 1: Full verification from a clean build**

```bash
mvn clean install -pl njams-sdk
mvn validate -Pcheckstyle -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```

All three must succeed. Do not claim completion on a partial run — per `superpowers:verification-before-completion`, paste the actual output.

- [ ] **Step 2: Re-verify the relocated-type constraint**

Check every `public`/`protected` member added or changed in `AbstractSender` and `SenderPool` against the `<relocations>` list in `checkstyle.xml`. `notifyConnectionFailure(Exception)` is the only new `protected` member; `Exception` is a JDK type, so it is clean. Confirm nothing else crept in.

- [ ] **Step 3: Confirm the SPI surface is what §6 promised**

Write out the final list of `public`/`protected` members on `AbstractSender` and diff it against §6's "Unchanged" + "Added" lists. A member present in neither list is an accident — either restore it or raise it.

Do the same check for the two monitors from Task 2a: `DiscardMonitor.discard()` and `ThrottleMonitor.throttle(long)` must still be the *only* public members, with unchanged names and signatures. If `getInstance`/`setInstance` ended up public, restrict them — they are a test seam, not API.

- [ ] **Step 4: Apply the `breaking-change` label to SDK-472**

The SPI Contract changed (§6). Add the label. Note in the ticket that this is an SPI Contract break, not a Client Contract one, and not a Wire Contract change — the Wire Contract (`communication/fragments/`, `MessageHeaders`) was untouched, so `message-format-changes.md`'s gate never applied.

- [ ] **Step 5: Use the `njams-ticket-finish` skill to close out**

It sequences the closing comment, resolution, assignee clearing, and the deletion of this plan file. Do not delete this plan manually — and do not delete the spec, which is kept permanently (`docs-superpowers-lifecycle.md`).

- [ ] **Step 6: Record the deliberately-not-decided items**

Spec §12 leaves these open on purpose, and B2 leaves one accepted risk. Do not resolve them in this ticket; record the current state so the follow-up does not have to rediscover it:

- **`acquire()` connects while holding the pool monitor (accepted risk, decision B2).** Propose a follow-up ticket describing the exposure precisely: a partially-degraded endpoint — reachable enough that the group is not flagged `failed`, but slow to accept new connections — lets one worker's `connect()` inside `takeUnlockedOrCreate()` stall every other `acquire()` and `release()` in the group for that connect's duration. Worst case is JMS, which has no standard connect timeout; `njams.sdk.communication.connect.timeout` bounds only the startup path, not `create()`. The fix, if ever needed, is to reserve a creation slot under the lock and connect outside it. **Propose the ticket, do not create it unprompted.**
- **Whether `SenderConnector` should be shaped for the receiver to adopt later** (SDK-473 or beyond). This plan does not shape it for reuse — it takes a `SenderPool` directly. Note on SDK-473 that adopting it would first require extracting a pool-agnostic interface.
- **Post-migration test overlap:** `SenderStartupSpecTest` and the new `SenderConnectorStartupSpecTest` end up testing nearly the same thing (Appendix A.1), and `SenderShutdownSpecTest`'s third test is a weaker duplicate of `SenderCloseOrderingSpecTest` (Appendix A.3). Consolidating them is a scope decision for the user.

- [ ] **Step 7: Note the SDK-473 dependency**

Per §8.3, SDK-472 lands before SDK-473. Leave a comment on SDK-473 recording that the seam it needs is now `SenderPool.reportFailure` firing `SenderExceptionListener`s once per outage with a `null` message, and that under shared communications it will receive N notifications per outage (one per registered instance), not one (§5.4).

---

## Appendix A: Per-file test migration catalogue

Audited against the current working tree. Line numbers are as-of this plan's writing — re-confirm before editing, since earlier tasks touch some of these files.

### A.0 The shared teardown breaks all four lifecycle files at once

`LifecycleTestSender.shutdownAll()` (`LifecycleTestSender.java:34-40`) calls `s.setShouldShutdown(true)` (L36) and `s.cancelReconnect()` (L37). It is reached from `AbstractLifecycleSpecTest`'s `@After` → `LifecycleTestTransport.shutdownAllSenders()`, so **files A.1-A.4 fail to compile from this alone**, regardless of their own bodies. Fix this first (Task 6 Step 5) or nothing else in this appendix can be verified.

With per-instance daemon reconnect threads gone, the registry's original purpose (stopping a thread that would outlive the test and count down the next test's latches) mostly evaporates. Reduce `shutdownAll()` to closing each registered sender and clearing the list.

### A.1 `lifecycle/SenderStartupSpecTest` — rewire wholesale

Every one of its three tests is *only* `beginConnect()` + `awaitStartup(long)` on a bare sender built by `freshSender()` (L13-17). There is no other subject in the file.

| Test | Removed calls | Assertions to preserve verbatim |
|---|---|---|
| `awaitStartupReturnsTrueWhenConnectSucceeds` (L19-26) | `s.beginConnect()` L23, `s.awaitStartup(5000)` L24 | `assertTrue(s.awaitStartup(5000))`, `assertTrue(s.isConnected())` |
| `awaitStartupReturnsFalseWhenConnectFails` (L28-35) | `beginConnect()` L32, `awaitStartup(5000)` L33 | `assertFalse(s.awaitStartup(5000))`, `assertFalse(s.isConnected())` |
| `awaitStartupTimesOutWhileConnectBlocks` (L37-45) | `beginConnect()` L41, `awaitStartup(200)` L42 | `assertFalse("blocked connect must not report success within the timeout", s.awaitStartup(200))` |

**Rewiring:** drive `SenderConnectorTestAccess` (Task 2) instead of a bare sender. The `isConnected()` assertions become the group's connected state — expose `isGroupConnected()` on the test access and keep the assertion text identical. `LifecycleTestTransport.setSenderMode(...)` and `releaseBlockedConnect()` usage is unchanged.

Note the overlap with `SenderConnectorStartupSpecTest` (Task 2): after migration these two files test nearly the same thing. Do **not** delete either as "duplicate" — that is a scope decision for the user, not a migration step. Flag it in the Task 10 review.

### A.2 `lifecycle/SenderStartGatingSpecTest` — comments only, no API change

All four tests drive a **real `Njams`** end to end and assert only on `njams.start()` / `njams.isStarted()` plus `LifecycleTestTransport.senderConnectCount()` / `connectAttemptedLatch()`. **No test calls a removed member.** This file should pass untouched — which makes it the single best parity signal in the suite. Run it early and often.

Two comments name the old internals and must be updated (`code-quality-general.md`: a comment made inaccurate by a change gets fixed):
- L70 (Javadoc of `slowThenFailedInitialConnectUnderReconnectPolicyRunsBackgroundLoop`): "…reconnect loop, which makes at least one further connect attempt."
- L80: `// Latch-driven proof that doReconnect is looping: …` → the loop now lives in `SenderConnector`.

### A.3 `lifecycle/SenderShutdownSpecTest` — rewire tests 1 and 2

Its `connectedSender()` helper (L16-22) calls `beginConnect()` L19 + `awaitStartup(5000)` L20.

| Test | Removed calls | Assertions to preserve verbatim |
|---|---|---|
| `reconnectIsSuppressedOnceShutdownRequested` (L24-34) | `s.setShouldShutdown(true)` L27, `s.reconnect(...)` L31, + helper | `assertFalse("no reconnect once shutdown is requested", attempted.await(500, TimeUnit.MILLISECONDS))` |
| `cancelReconnectInterruptsABlockedReconnect` (L36-48) | `s.reconnect(...)` L43, `s.cancelReconnect()` L45, + helper | `assertTrue("reconnect started and blocked in connect()", attempted.await(2000, TimeUnit.MILLISECONDS))` |
| `closeSetsShutdownBeforeDrainingSoAFailingFinalSendDoesNotReconnect` (L50-63) | none — drives a real `NjamsSender` | `assertFalse("shutdown-first ordering must prevent reconnect during drain", …await(500, …))` |

**Rewiring for tests 1-2:** `setShouldShutdown(true)` → `pool.beginShutdown()` (or `coordinator.setShouldShutdown(true)` via the test access, whichever matches what Task 3 Step 3 item 8 settled on); `reconnect(...)` → `connector.startReconnect(...)`; `cancelReconnect()` → `connector.cancelReconnect()`.

Two observations to report but **not** silently act on:
- `cancelReconnectInterruptsABlockedReconnect`'s only assertion sits *before* the `cancelReconnect()` call (L44 vs L45), so the cancellation itself is never asserted. The test proves the reconnect thread started and blocked, nothing more. Preserve it exactly as-is; if a real assertion on cancellation is wanted, that is new behaviour for Task 7, not a migration edit.
- `L58 int before = LifecycleTestTransport.senderConnectCount();` is assigned and never read, and test 3 is a weaker duplicate of `SenderCloseOrderingSpecTest` (same assertion message, but it never arms a failing send). Leave both alone; note them in Task 10.

### A.4 `lifecycle/SenderLoggingSpecTest` — the logger category must move

**This is the migration with the real trap.** The capture point is bound to the *class* whose logger emits the lines:

```java
38	        senderLogger = Logger.getLogger(AbstractSender.class);
```

but both asserted INFO strings are emitted **inside `doReconnect`** (`AbstractSender.java:226` `"Initialized reconnect, because of: {}"` and `:234` `"Reconnected sender {}"`), which relocates to `SenderConnector.runReconnectLoop`. So after Task 2 the logger `com.im.njams.sdk.communication.AbstractSender` no longer emits them and both tests fail — not because behaviour changed, but because the capture point is stale.

**Rewiring:** change L38 to capture the connector's category. `SenderConnector` is package-private, so `Logger.getLogger(SenderConnector.class)` will not compile from the `lifecycle` package — use the string form:

```java
        senderLogger = Logger.getLogger("com.im.njams.sdk.communication.SenderConnector");
```

Everything else stays: the `CapturingAppender` (L126-147), `countMessagesStartingWith(Level, String)` (L114-119), `countAtLevel(Level)` (L121-123), and all four assertions byte-for-byte:

```java
66	        assertEquals("exactly one reconnect-start info", 1,
67	            countMessagesStartingWith(Level.INFO, "Initialized reconnect"));
68	        assertEquals("exactly one reconnect-success info", 1,
69	            countMessagesStartingWith(Level.INFO, "Reconnected sender"));
```
```java
93	        assertEquals("reconnect-start info must stay bounded regardless of retry count", 1,
94	            countMessagesStartingWith(Level.INFO, "Initialized reconnect"));
95	        assertEquals("routine retries must never log at warn level", 0, countAtLevel(Level.WARN));
96	        assertEquals("routine retries must never log at error level", 0, countAtLevel(Level.ERROR));
```

**The log strings themselves must therefore not be reworded in `SenderConnector`.** Task 2 Step 3 keeps `"Initialized reconnect, because of: {}"` and `"Reconnected sender {}"` verbatim for exactly this reason — these assertions are the parity evidence.

Per-test removed calls: `beginConnect()` L53/L76, `awaitStartup(5000)` L54/L77, `reconnect(...)` L59/L81 → connector equivalents. `assertTrue("sender must eventually reconnect once failures stop", s.isConnected())` (L91) becomes the group's connected state.

Also update the Javadoc of the private helper `awaitReconnectSuccessLogged()` (L99-106), which describes the `connect()` / `doReconnect()` log-ordering race by name (L101-102). The race still exists, in the connector — reword, don't delete.

### A.5 `SenderCloseOrderingSpecTest` — behaviourally load-bearing, no direct calls

Lives in `com.im.njams.sdk.communication` (not `lifecycle`) to reach package-private `NjamsSender.getExecutor()`, and **does not extend `AbstractLifecycleSpecTest`** — it has its own `@Before` (L23-26) and no `@After`, so A.0's teardown does not apply here.

It calls no removed member, but it is **the only test that exercises the removed `send` → `onException` → `reconnect` chain end-to-end**, as its own comment says: `L57 LifecycleTestTransport.releaseSend(); // held send now fails during the drain -> onException -> reconnect`. Under the new design that same failure travels `dispatch(...)` → `senderPool.reportFailure(...)` → `connector.startReconnect(...)`, and the assertion — that the shutdown-first ordering prevents any reconnect during the drain — must still hold:

```java
59	        assertFalse("shutdown-first ordering must prevent reconnect during drain",
60	            attempted.await(1000, TimeUnit.MILLISECONDS));
```

**This is the single most important parity assertion in the whole migration.** It is exactly where Task 3 Step 3 item 8's `beginShutdown()` question gets decided: if `beginShutdown()` now refuses waiters too early, or if the retention loop re-acquires instead of returning on shutdown, this test fails. Update only the L57 comment to name the new route; change no assertion. If it fails, stop and raise it — do not adjust the timeout.

### A.6 `AbstractSenderStaticStateTest` — passes unchanged

Pure reflection over `AbstractSender.class.getDeclaredFields()`, asserting no `static` field named `hasConnected` or `connecting` (L25-28). It calls no removed member, checks only those two names, and does not look at the instance field `hasConnectionFailure` — so removing that field is invisible to it. `AbstractSender` declares exactly one static field today (`LOG`), so the test is green now and stays green.

One stale comment to fix: the class Javadoc's `L15 * … RED while the statics still exist.` is already untrue today and stays untrue. Correct it while in the file.

### A.7 Coverage of removed members by the six files

| Removed member | Direct callers |
|---|---|
| `beginConnect()` | A.1 (L23, 32, 41), A.3 (helper L19), A.4 (L53, 76) |
| `awaitStartup(long)` | A.1 (L24, 33, 42), A.3 (helper L20), A.4 (L54, 77) |
| `reconnect(Exception)` | A.3 (L31, 43), A.4 (L59, 81) |
| `setShouldShutdown(boolean)` | A.3 (L27) + `LifecycleTestSender.shutdownAll()` L36 → A.0 |
| `cancelReconnect()` | A.3 (L45) + `LifecycleTestSender.shutdownAll()` L37 → A.0 |
| `doReconnect(Exception)` | none — named in comments only: A.2 (L80), A.4 (L102) |
| `onException(Exception)` | none in these six — relied on behaviourally by A.5; called directly by `NjamsSenderTest.ExceptionSender:389` (decision **B1**) |
| `send(CommonMessage, String)` retry/discard loop | none directly — load-bearing for A.5 |
| `hasConnectionFailure()` + field | none of the six |
| `setConnectionCoordinator(...)` | none of the six — only `SenderPool.java:102` and `NjamsSenderTest:181` (decision **B1**) |
| `addExceptionListener(...)` + field | none of the six — only `SenderPool.java:85, 104` |

**No rewiring needed:** A.2 (comments), A.6 (reflection). **Real rewiring:** A.0, A.1, A.3, A.4. **Comment-only but behaviourally critical:** A.5.
