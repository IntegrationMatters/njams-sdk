# SDK-375 Part 4 — Sender/Receiver Criticality Decoupling (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the receiver back its own independent `ConnectionCoordinator` (undoing Part 3's sharing with the
sender), make `Njams.start()` depend only on the sender, hardcode the receiver's connection-loss/reconnect
behavior with specific WARN/INFO log wording, and add a new "assume-and-cycle" cross-side connection-verification
trigger so a failure on either side (sender/receiver) prompts the other to proactively cycle its own connection.

**Architecture:** `ConnectionCoordinator` gains a set of cross-side trigger callbacks invoked once per newly
detected failure (from inside the existing `beginReconnect()`), reused identically by both sides — no new
detection logic. It is a **set**, not a single overwritable slot: with `njams.sdk.communication.shared=true` on
HTTP, one shared sender pool has a *dedicated* receiver per `Njams` instance, so its coordinator may need to
notify several different receivers, not just the last one wired (mirrors the existing
`SenderPool`/`AbstractSender` add-only `exceptionListeners` pattern already in this codebase for the same reason).
`NjamsSender.wireReceiver(Receiver)` (Part 3's coordinator-sharing hook) is repurposed to wire this callback in
both directions instead of sharing a coordinator instance: sender failure calls
`AbstractReceiver.onException(Exception)` (existing, public, unchanged); receiver failure calls a new
`SenderPool.triggerConnectionCheck()` that calls `AbstractSender.onException(Exception)` (existing, protected,
unchanged) on every pooled sender. `Receiver.startWithTimeout(long, boolean)` (Part 3, unreleased) and its
`AbstractReceiver` override are removed, along with the `shouldReconnect()`/`allowReconnectBeforeConnected()`
consultation in `AbstractReceiver.beginConnect()` — both are now dead once the receiver's retry behavior is
unconditional.

**Tech Stack:** Java 11, JUnit 4 + Mockito, SLF4J, the existing `LifecycleTestSender`/`LifecycleTestReceiver`
controllable fake transport (already supports independent sender/receiver `ConnectMode`s from Part 3 Task 4).

## Global Constraints

- **Base branch:** `SDK-375` (off `6.0-dev`, reopened, `In Progress`). Fix version: `6.0.0`.
- **Ticket:** all commits reference `SDK-375`. Intermediate commits carry **no** `#comment`; only the finalizing
  commit (Task 7) uses `SDK-375 #comment …`.
- **`breaking-change` label is already present** on the ticket (added when it was reopened for this revision,
  since `Njams.start()`'s observable behavior changes for the receiver-only-failure case). Task 7 re-verifies it
  is still present before declaring done — do not remove it.
- **Baseline stays green at every commit**: full `njams-sdk` module suite (`mvn -pl njams-sdk test`), plus every
  spec test this plan touches.
- **No relocated/shaded third-party type** on any `public`/`protected` member (check against `checkstyle.xml`).
- **`njams-safe-modification`:** every existing member touched (`AbstractReceiver`, `Receiver`, `NjamsSender`,
  `SenderPool`, `ConnectionCoordinator`, `Njams`) must be covered by a green test before and after the change.
- **No timing-based tests.** Drive phases with latches/barriers/injected seams and poll for conditions; never
  `Thread.sleep` to "wait for" a state.
- **Clean, accurate, non-flooding logging.** Every task that changes behavior must leave existing log
  statements/comments/Javadoc accurate (no references to the coordinator-sharing this plan reverts) and keep new
  logging to one line per state transition, never per-attempt/per-poll.
- **Production source files** need the Salesfive copyright header; test files do not. This plan only modifies
  existing files — verify none needs a new file with a missing header.
- **Javadoc** on every new/changed `public`/`protected` member; `mvn -pl njams-sdk -Pcheckstyle checkstyle:check`
  and `mvn -pl njams-sdk javadoc:javadoc` must pass before the finalizing commit.
- **Removing Part 3's unreleased API is safe without deprecation.** None of `Receiver.startWithTimeout(long,
  boolean)`, `AbstractReceiver.setConnectionCoordinator(ConnectionCoordinator)`, or
  `SenderPool.getConnectionCoordinator()` has shipped in a release (still `6.0.0-SNAPSHOT`) — remove them outright,
  do not mark `@Deprecated`.
- **Verify current line numbers/state against the live file before editing** — this plan cites the file state at
  the time it was written; re-verify before each edit per the "No Unsupported Assumptions" rule.

---

## Current state (verified against live code before writing this plan)

- `AbstractReceiver.java`: `private volatile ConnectionCoordinator coordinator = new ConnectionCoordinator();`
  (line ~95) with a package-private `setConnectionCoordinator(ConnectionCoordinator)` (line ~124) called only by
  `NjamsSender.wireReceiver(Receiver)`. `beginConnect()` (line ~228) self-triggers `reconnect(e)` on failure only
  `if (coordinator.shouldReconnect())` (line ~242). `startWithTimeout(long, boolean)` (line ~322) applies the
  fail-fast/reconnect policy via `coordinator.allowReconnectBeforeConnected()`/gating. `reconnect(Exception)`
  (line ~372) logs `LOG.info("Initialized receiver reconnect, because of : {}", ...)` and
  `LOG.info("Reconnected receiver {}", ...)`.
- `Receiver.java`: `default boolean startWithTimeout(long timeoutMs, boolean reconnectOnFailure)` (line ~117).
- `NjamsSender.java`: `public void wireReceiver(Receiver receiver)` (line ~299) calls
  `((AbstractReceiver) receiver).setConnectionCoordinator(senderPool.getConnectionCoordinator())`.
- `SenderPool.java`: package-private `ConnectionCoordinator getConnectionCoordinator()` (line ~84), used only by
  `wireReceiver`. `beginShutdown()` (line ~181) already shows the established pattern for a pool-wide
  `streamAll().forEach(...)` operation this plan's `triggerConnectionCheck()` will mirror.
- `ConnectionCoordinator.java`: package-private, `synchronized int beginReconnect()` (line ~50) is the single
  call site (from both `AbstractSender.doReconnect()` and `AbstractReceiver.reconnect()`) marking "a new failure
  is being handled" — the natural, already-existing hook point for the new cross-side trigger.
- `AbstractSender.java`: `protected void onException(Exception exception)` (line ~334) does `close();
  reconnect(exception);` — already the sender's own "cycle the connection" entry point, same shape as
  `AbstractReceiver.onException(Exception)` (public, line ~491).
- `Njams.java`: `beginConnect()` (line ~657), `startReceiver(boolean reconnectOnFailure)` (line ~692, returns
  `boolean`), `start()` (line ~731), `stopReceiverAfterStartupFailure(Receiver)` (line ~782), `stop()` (line
  ~838) — all per the file excerpts embedded in each task below.
- Tests: `CoordinatorSharingSpecTest` (proves coordinator *sharing* — its entire premise is reverted by this
  plan), `ReceiverStartGatingSpecTest` (proves receiver failure *gates* `start()` — also reverted),
  `ReceiverShutdownSpecTest`, `ReceiverLoggingSpecTest` (asserts the old log wording), `AbstractReceiverTest`'s
  `startWithTimeoutTwoArg*`/`defaultTwoArgStartWithTimeoutOnPlainReceiverIgnoresReconnectOnFailure` tests (exercise
  the method being removed), `AbstractReceiverStaticStateTest` (unaffected, do not touch),
  `SenderPoolTest`'s `exposesItsConnectionCoordinator`/`exposesTheSameCoordinatorInstanceGivenAtConstruction`
  (exercise the method being removed), `NjamsSenderTest`'s `wireReceiverSetsCoordinatorOnAbstractReceiver`/
  `wireReceiverIsANoOpForNonAbstractReceiverImplementations` (exercise the old `wireReceiver` behavior).

## File structure

- **Modify** `communication/ConnectionCoordinator.java` — add `addCrossSideTrigger(Runnable)` (add-only, multiple
  callbacks); invoke all of them once per new failure from `beginReconnect()`.
- **Modify** `communication/AbstractReceiver.java` — touched by three tasks in sequence, split so every commit
  stays green: Task 2 removes the `shouldReconnect()` gate in `beginConnect()` (always self-trigger `reconnect(e)`
  on failure), adds `addCrossSideTrigger(Runnable)`, and changes `reconnect(Exception)`'s log wording/level —
  `setConnectionCoordinator`/`startWithTimeout(long, boolean)`/the field's mutability are deliberately left alone
  since their last callers aren't fixed yet; Task 3 removes `setConnectionCoordinator(...)` (dead once its own
  `wireReceiver` change lands) and tightens the `coordinator` field to `private final`; Task 4 removes the
  `startWithTimeout(long, boolean)` override (dead once `Njams.startReceiver()` stops calling it).
- **Modify** `communication/Receiver.java` — remove `startWithTimeout(long, boolean)` default method (Task 4,
  once its only caller is gone).
- **Modify** `communication/SenderPool.java` — remove `getConnectionCoordinator()`; add
  `addCrossSideTrigger(Runnable)` and `triggerConnectionCheck()` (Task 3).
- **Modify** `communication/NjamsSender.java` — repurpose `wireReceiver(Receiver)`'s body (Task 3).
- **Modify** `Njams.java` — simplify `startReceiver(boolean)` → `startReceiver()` (void, never fails, and now the
  *only* call site of `wireReceiver`); `beginConnect()` no longer calls `wireReceiver` at all; `start()` no
  longer gates on the receiver; `stop()`/`stopReceiverAfterStartupFailure(...)` call `setShouldShutdown(true)` on
  the receiver alongside `cancelReconnect()` (Task 4).
- **Delete (test)** `communication/lifecycle/CoordinatorSharingSpecTest.java` (premise reverted, Task 4).
- **Modify (test)** `communication/lifecycle/ReceiverStartGatingSpecTest.java` — rewritten: receiver failure never
  gates `start()` (Task 4).
- **Create (test)** `communication/lifecycle/CrossSideVerificationSpecTest.java` — the new D1.7 behavior (Task 5).
- **Modify (test)** `communication/lifecycle/ReceiverShutdownSpecTest.java` — add an assertion that
  `setShouldShutdown(true)` reaches the receiver directly (no longer inferred via a shared coordinator) (Task 4).
- **Modify (test)** `communication/lifecycle/ReceiverLoggingSpecTest.java` — new WARN/INFO wording and levels
  (Task 5).
- **Modify (test)** `communication/AbstractReceiverTest.java` — split across the same three tasks as the
  production file above: Task 2 adds the new unconditional-retry/trigger tests and deletes exactly the one
  existing test whose premise the `beginConnect()` change falsifies; Task 4 deletes the remaining four 2-arg
  `startWithTimeout` tests/fixtures once the method itself is removed.
- **Modify (test)** `communication/SenderPoolTest.java` — remove the `getConnectionCoordinator()` tests; add
  `triggerConnectionCheck()`/`addCrossSideTrigger(...)` tests (Task 3).
- **Modify (test)** `communication/NjamsSenderTest.java` — remove the old `wireReceiver` coordinator-sharing
  tests; add tests for the new cross-trigger wiring.
- **Create (test)** `communication/ConnectionCoordinatorTest.java` additions — `addCrossSideTrigger`/
  `beginReconnect` fires every registered trigger once.
- **Modify (docs)** `wiki/FAQ.md`, `njams-sdk-sample-client/src/main/resources/settings_full.properties`.

---

### Task 1: `ConnectionCoordinator` — cross-side trigger

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/ConnectionCoordinator.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/ConnectionCoordinatorTest.java`

**Interfaces:**
- Produces: `void addCrossSideTrigger(Runnable trigger)` (package-private, add-only — multiple callbacks may be
  registered and all fire). `beginReconnect()`'s existing signature/return value is unchanged; it now
  additionally invokes every registered trigger once per call where the result is `1` (i.e. this is the first
  concurrent reconnect for the group — the "a new failure was just detected" moment).

This is a self-contained, testable-in-isolation change with no dependency on Tasks 2-4.

- [ ] **Step 1: Write the failing tests**

Read the live `ConnectionCoordinatorTest.java` first to match its existing style (constructor, helper usage), then
append:

```java
    @Test
    public void beginReconnectInvokesEveryRegisteredCrossSideTriggerOnTheFirstConcurrentReconnect() {
        ConnectionCoordinator coordinator = new ConnectionCoordinator();
        AtomicInteger firstTrigger = new AtomicInteger();
        AtomicInteger secondTrigger = new AtomicInteger();
        coordinator.addCrossSideTrigger(firstTrigger::incrementAndGet);
        coordinator.addCrossSideTrigger(secondTrigger::incrementAndGet);

        coordinator.beginReconnect();
        assertEquals("both registered triggers must fire exactly once for the first reconnect", 1, firstTrigger.get());
        assertEquals("both registered triggers must fire exactly once for the first reconnect", 1, secondTrigger.get());
    }

    @Test
    public void beginReconnectDoesNotReinvokeCrossSideTriggersForAConcurrentSecondReconnect() {
        ConnectionCoordinator coordinator = new ConnectionCoordinator();
        AtomicInteger invocations = new AtomicInteger();
        coordinator.addCrossSideTrigger(invocations::incrementAndGet);

        coordinator.beginReconnect();
        coordinator.beginReconnect(); // e.g. a second pooled sender's independent failure while the first still retries
        assertEquals("must not fire again while a reconnect from this group is already in flight",
            1, invocations.get());
    }

    @Test
    public void beginReconnectWithNoTriggersRegisteredDoesNotThrow() {
        ConnectionCoordinator coordinator = new ConnectionCoordinator();
        coordinator.beginReconnect(); // must not NPE
    }
```

Add the import `java.util.concurrent.atomic.AtomicInteger` if not already present.

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=ConnectionCoordinatorTest`
Expected: FAIL to compile — `addCrossSideTrigger` does not exist.

- [ ] **Step 3: Implement**

In `ConnectionCoordinator.java`, add a field next to the existing ones (use the same add-only,
`IdentityHashMap`-backed `Set` idiom already established by `AbstractSender.exceptionListeners`/
`SenderPool.exceptionListeners` — read either for the exact pattern before adding this):

```java
    private final Collection<Runnable> crossSideTriggers = Collections.newSetFromMap(new IdentityHashMap<>());
```

Add the imports `java.util.Collection`, `java.util.Collections`, `java.util.IdentityHashMap` if not already
present. Add the registration method and update `beginReconnect()`:

```java
    /**
     * Registers a callback invoked once per newly detected failure for this group (see {@link #beginReconnect()}).
     * Used to implement the cross-side connection-verification trigger between a sender group and its wired
     * receiver(s): when this side detects a failure, every registered side is prompted to proactively cycle its
     * own connection too, since a mostly-idle side could otherwise be slow to notice a real loss on its own.
     * Add-only — there is deliberately no corresponding removal, mirroring
     * {@link SenderPool#addSenderExceptionListener(SenderExceptionListener)}'s existing shape. Multiple triggers
     * may be registered: with {@code njams.sdk.communication.shared=true} on HTTP, one shared sender pool's
     * coordinator may need to notify several different per-instance receivers, not just the most recently wired
     * one.
     *
     * @param trigger the callback to add.
     */
    void addCrossSideTrigger(Runnable trigger) {
        crossSideTriggers.add(trigger);
    }

    /**
     * Marks the group as currently disconnected and beginning a reconnect: clears the connected flag and
     * increments the reconnecting count. Fires every registered cross-side trigger exactly once — on the
     * transition into the first concurrently in-progress reconnect for this group, not on every call.
     *
     * @return the reconnecting count after incrementing (for logging).
     */
    synchronized int beginReconnect() {
        hasConnected.set(false);
        int result = connecting.incrementAndGet();
        if (result == 1) {
            crossSideTriggers.forEach(Runnable::run);
        }
        return result;
    }
```

Update the class Javadoc's last sentence ("Internal SDK infrastructure — not public API.") to also mention the
trigger:

```java
/**
 * Owns the reconnect-counting, shutdown, and "was ever connected" / reconnect-gating state for one shared-transport
 * group of senders (all senders handed out by a single {@link SenderPool}), or independently for one receiver
 * group. Replaces the former JVM-global {@code static} reconnect state on {@link AbstractSender} and
 * {@link AbstractReceiver} so that unrelated {@link com.im.njams.sdk.Njams} instances no longer share connection
 * state. Also carries optional cross-side triggers (see {@link #addCrossSideTrigger(Runnable)}) so a failure on
 * one side of a wired sender/receiver pair can prompt the other (or others, for a shared sender pool with
 * multiple per-instance receivers) to verify its own connection, without the two sides sharing any other state.
 * Internal SDK infrastructure — not public API.
 */
```

- [ ] **Step 4: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=ConnectionCoordinatorTest`
Expected: PASS.

- [ ] **Step 5: Regression check**

Run: `mvn -q -pl njams-sdk test -Dtest="AbstractSenderStaticStateTest,NjamsSenderTest,SenderPoolTest,AbstractReceiverTest"`
Expected: PASS (this task only adds behavior gated behind a newly-added, not-yet-called setter; nothing existing
calls it yet, so nothing changes for any current caller of `beginReconnect()`).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/ConnectionCoordinator.java njams-sdk/src/test/java/com/im/njams/sdk/communication/ConnectionCoordinatorTest.java
git commit -m "SDK-375 Add cross-side connection-verification trigger to ConnectionCoordinator"
```

---

### Task 2: `AbstractReceiver` — unconditional retry, new log wording, cross-side trigger (additive only)

**Sequencing note (why this task is additive-only, not a straight port of the design doc):** `AbstractReceiver
.setConnectionCoordinator(ConnectionCoordinator)` is still called by `NjamsSender.wireReceiver(Receiver)` (removed
only in Task 3) and `AbstractReceiver`'s `startWithTimeout(long, boolean)` override is still called by
`Njams.startReceiver(boolean)` (removed only in Task 4). Removing either method here, before its caller is fixed,
would leave this task's own commit **not compiling** — a direct violation of "baseline stays green at every
commit." This task therefore only *adds* the new behavior (unconditional retry, new log wording,
`addCrossSideTrigger`) and leaves `setConnectionCoordinator`/`startWithTimeout(long, boolean)`/the mutable
`coordinator` field in place, untouched, to be removed later: `setConnectionCoordinator` in Task 3 (once
`wireReceiver` stops calling it) and `startWithTimeout(long, boolean)` plus the field's final-ification in Task 4
(once `Njams.java` stops calling it). If you are the implementer and you already deleted these as part of a
straight reading of the design spec, revert that part and keep only the additive changes below.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java`

**Interfaces:**
- Consumes: `ConnectionCoordinator.addCrossSideTrigger(Runnable)` (Task 1).
- Produces: `AbstractReceiver.addCrossSideTrigger(Runnable)` (package-private, forwards to its own coordinator).
- Leaves in place for now (removed later, see above): `setConnectionCoordinator(ConnectionCoordinator)`,
  `startWithTimeout(long, boolean)`, the `private volatile ConnectionCoordinator coordinator` field's current
  mutability.

- [ ] **Step 1: Write the failing tests**

Do **not** delete `startWithTimeoutTwoArgReturnsTrueOnSuccess`,
`startWithTimeoutTwoArgReconnectPolicyReturnsTrueAndEntersBackgroundReconnect`,
`defaultTwoArgStartWithTimeoutOnPlainReceiverIgnoresReconnectOnFailure`,
`startWithTimeoutTwoArgReconnectPolicyReturnsQuicklyDespiteMultipleRetries`, or their fixtures
(`FlakyThenSucceedsReceiverImpl`, `MultiFailThenSucceedsReceiverImpl`) — they exercise `startWithTimeout(long,
boolean)`, which this task does **not** remove, and tracing each one confirms this task's `beginConnect()` change
does not alter their behavior: for `reconnectOnFailure=true`, `coordinator.allowReconnectBeforeConnected()` is
already called *before* `startWithTimeout(long)` runs, which already makes `coordinator.shouldReconnect()` return
`true` regardless of the gate this task removes — so the three `reconnectOnFailure=true`/success-path tests are
unaffected either way.

**One test's premise is genuinely falsified by this task's change and must be adjusted now, not left broken:**
`startWithTimeoutTwoArgFailFastReturnsFalseAndCancelsOnFailure` calls `startWithTimeout(200L, false)` — with
`reconnectOnFailure=false`, `allowReconnectBeforeConnected()` is never called, so today `coordinator
.shouldReconnect()` is `false` when `beginConnect()`'s catch block checks it, and no reconnect is ever attempted;
the test asserts exactly that ("fail-fast must not leave a reconnect loop running"). Once this task removes that
gate, `beginConnect()`'s catch block calls `reconnect(e)` *unconditionally* — so under the exact same
`reconnectOnFailure=false` scenario, a reconnect attempt now **does** start (this is the intended, approved
behavior change: the receiver retries unconditionally, with no fail-fast/reconnect distinction left at all). The
test's core assertion becomes false by design, not by bug. Delete this one test (and, if `SlowConnectReceiverImpl`
has no other caller after removing it, its fixture) — do not weaken it to accept the new behavior silently; delete
it outright, since the concept it tested ("fail-fast prevents any reconnect") no longer exists for the receiver
after this task. The other four tests/fixtures for `startWithTimeout(long, boolean)` stay, to be removed together
with the method itself in Task 4.

Append new tests proving the unconditional-retry behavior and the cross-side trigger wiring:

```java
    @Test
    public void beginConnectSelfTriggersReconnectOnFailureUnconditionally() throws InterruptedException {
        // No coordinator gating call of any kind — connect() fails once, then the receiver must be found
        // reconnecting (not just left DISCONNECTED) shortly after, with no fail-behavior setting involved at all.
        FlakyOnceReceiverImpl impl = new FlakyOnceReceiverImpl();
        impl.beginConnect();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!impl.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue("receiver must self-trigger reconnect on its own initial connect failure, unconditionally",
            impl.isConnected());
    }

    @Test
    public void addCrossSideTriggerForwardsToTheReceiversOwnCoordinator() throws Exception {
        // White-box: the only way to observe this is that a reconnect (which internally calls
        // coordinator.beginReconnect()) fires the registered trigger — proving addCrossSideTrigger(...) actually
        // reached the receiver's own coordinator instance, not a no-op.
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        java.util.concurrent.atomic.AtomicBoolean triggered = new java.util.concurrent.atomic.AtomicBoolean(false);
        impl.addCrossSideTrigger(() -> triggered.set(true));
        impl.throwManyExceptionsForTest = true;
        Thread t = new Thread(() -> impl.reconnect(new NjamsSdkRuntimeException("test")));
        t.setDaemon(true);
        t.start();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (!triggered.get() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        impl.setShouldShutdown(true);
        impl.cancelReconnect();
        t.join(2000);
        assertTrue("addCrossSideTrigger must reach this receiver's own coordinator", triggered.get());
    }

    private class FlakyOnceReceiverImpl extends AbstractReceiver {
        private final java.util.concurrent.atomic.AtomicBoolean failedOnce =
            new java.util.concurrent.atomic.AtomicBoolean(false);

        @Override
        public String getName() { return "FlakyOnceReceiver"; }

        @Override
        public void init(ClientSettings settings) {}

        @Override
        protected Response extendRequest(Request req) { return null; }

        @Override
        public void connect() {
            if (failedOnce.compareAndSet(false, true)) {
                throw new NjamsSdkRuntimeException("first attempt fails");
            }
            connectionStatus = ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {}
    }
```

> `AbstractReceiverImpl` and `throwManyExceptionsForTest` already exist in this file from Part 3 Task 2 — reuse
> them, do not redeclare. `setShouldShutdown(boolean)` is already `public` on `AbstractReceiver`.

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=AbstractReceiverTest`
Expected: FAIL to compile — `addCrossSideTrigger` does not exist yet, and the deleted tests' absence should not
by itself cause a failure (deleting tests never causes RED; confirm the *new* tests fail to compile/run).

- [ ] **Step 3: Implement — `AbstractReceiver.java`**

**Do not touch** the `coordinator` field declaration or `setConnectionCoordinator(ConnectionCoordinator)` (currently
~line 88-126) — both stay exactly as they are today (`private volatile ConnectionCoordinator coordinator = new
ConnectionCoordinator();` plus the package-private setter). Per this task's sequencing note above, they are only
removed/tightened in Task 3 (the setter) and Task 4 (the field's final-ification), once nothing calls the setter
any more.

Add, near `cancelReconnect()`:

```java
    /**
     * Registers a callback invoked once when this receiver detects a new connection failure of its own — used to
     * prompt a wired sender group to verify its own connection too (see
     * {@link NjamsSender#wireReceiver(Receiver)}). This receiver's own coordinator, reconnect behavior, and
     * shutdown state remain entirely independent of the sender's; this is a one-way trigger, not shared state.
     * Add-only, mirroring {@link ConnectionCoordinator#addCrossSideTrigger(Runnable)}.
     *
     * @param trigger the callback to add.
     */
    void addCrossSideTrigger(Runnable trigger) {
        coordinator.addCrossSideTrigger(trigger);
    }
```

Replace `beginConnect()`'s catch block (remove the `shouldReconnect()` gate — always self-trigger):

```java
            } catch (Exception e) {
                LOG.debug("Receiver {}: connection attempt failed.", getName(), e);
                startupError.set(e);
                startupLatch.countDown();
                reconnect(e);
                return;
            }
```

Leave the `startWithTimeout(long, boolean)` override exactly as it is — do **not** touch it in this task (it is
removed in Task 4, once `Njams.java` stops calling it).

In `reconnect(Exception ex)`, replace the two `LOG.info` call sites with the new wording/levels:

```java
            int reconnecting = coordinator.beginReconnect();
            LOG.warn("Receiver connection lost. The client will not receive any commands from the server "
                + "until reconnected.");
            LOG.debug("{} receivers are reconnecting now.", reconnecting);
```

(This drops the `if (LOG.isInfoEnabled() && ex != null) { ... }` block entirely — the new message is a fixed
string with no exception detail, per the exact wording given; keep the exception itself available at `DEBUG` if
useful:)

```java
            int reconnecting = coordinator.beginReconnect();
            LOG.warn("Receiver connection lost. The client will not receive any commands from the server "
                + "until reconnected.");
            if (LOG.isDebugEnabled() && ex != null) {
                LOG.debug("Receiver reconnect triggered by: {}", ex.toString());
            }
            LOG.debug("{} receivers are reconnecting now.", reconnecting);
```

And the success log:

```java
                    if (coordinator.markConnected()) {
                        LOG.info("Receiver reconnected. Handling server commands resumed.");
                        resetReconnectInterval();
                    }
```

- [ ] **Step 4: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=AbstractReceiverTest`
Expected: PASS — all frozen tests plus the new ones.

- [ ] **Step 5: Regression check**

Run: `mvn -q -pl njams-sdk test -Dtest="AbstractReceiverStaticStateTest,JmsReceiverTest,HttpSseReceiverClassReferencesTest"`
Expected: PASS (no concrete receiver implementation is affected by the `beginConnect()`/logging changes).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java
git commit -m "SDK-375 Make AbstractReceiver retry unconditionally with new WARN/INFO wording; add cross-side trigger"
```

---

### Task 3: `SenderPool` and `NjamsSender` — cross-trigger wiring, remove dead coordinator accessor

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java` (remove
  `setConnectionCoordinator`, now dead — see Step 4b below)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderPoolTest.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/NjamsSenderTest.java`

**Interfaces:**
- Consumes: `ConnectionCoordinator.addCrossSideTrigger(Runnable)` (Task 1); `AbstractReceiver.addCrossSideTrigger
  (Runnable)`, `AbstractReceiver.onException(Exception)` (Task 2, existing/unchanged).
- Produces: `SenderPool.addCrossSideTrigger(Runnable)` (package-private, add-only), `SenderPool
  .triggerConnectionCheck()` (package-private).
- Removes: `SenderPool.getConnectionCoordinator()`; `AbstractReceiver.setConnectionCoordinator(ConnectionCoordinator)`
  (dead once this task's new `wireReceiver` body no longer calls it — see Step 4b).

- [ ] **Step 1: Write/adjust the failing tests**

In `SenderPoolTest.java`, **delete** `exposesItsConnectionCoordinator` and
`exposesTheSameCoordinatorInstanceGivenAtConstruction` (they exercise the method being removed). Append:

```java
    @Test
    public void triggerConnectionCheckCallsOnExceptionOnEveryPooledSender() {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        AbstractSender first = mock(AbstractSender.class);
        AbstractSender second = mock(AbstractSender.class);
        java.util.Iterator<AbstractSender> senders = java.util.List.of(first, second).iterator();
        SenderPool pool = new SenderPool(mockedCF) {
            @Override
            protected AbstractSender create() {
                return senders.next();
            }
        };
        AbstractSender s1 = pool.get();
        AbstractSender s2 = pool.get();
        pool.triggerConnectionCheck();
        verify(s1, times(1)).onException(any());
        verify(s2, times(1)).onException(any());
    }

    @Test
    public void addCrossSideTriggerAllowsMultipleTriggersToAllFireOnTheSameFailure() {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        when(mockedCF.getSender()).thenReturn(mock(AbstractSender.class));
        SenderPool pool = new SenderPool(mockedCF);
        AtomicInteger firstTrigger = new AtomicInteger();
        AtomicInteger secondTrigger = new AtomicInteger();
        // Two calls, as would happen when a shared sender pool (njams.sdk.communication.shared=true, HTTP) is
        // wired to two different per-Njams-instance receivers — both must still fire, not just the last one.
        pool.addCrossSideTrigger(firstTrigger::incrementAndGet);
        pool.addCrossSideTrigger(secondTrigger::incrementAndGet);

        AbstractSender sender = pool.get();
        sender.setShouldShutdown(false); // ensure a real (non-mocked) sender's coordinator.shouldReconnect() path is reachable
        sender.reconnect(new com.im.njams.sdk.common.NjamsSdkRuntimeException("test"));
        // AbstractSender.reconnect(...) requires isConnected()==false and coordinator.shouldReconnect()==true to
        // actually call beginReconnect(); a freshly created real AbstractSender starts DISCONNECTED, and its
        // coordinator defaults reconnectBeforeConnected=false/wasEverConnected=false, so shouldReconnect() would
        // be false too. Use a minimal real AbstractSender fixture (not the default `create()` mock in most other
        // tests in this file) whose connect() throws once, and drive it through beginConnect() first so
        // markStartupConnected()/wasEverConnected becomes true — read AbstractSenderTest or SenderStartupSpecTest
        // for the established fixture shape before writing this, so the coordinator is genuinely in a state
        // where reconnect() reaches beginReconnect() and both triggers actually fire. Assert both counters reach 1.
    }
```

> The `triggerConnectionCheckCallsOnExceptionOnEveryPooledSender` test (unchanged from the single-trigger design)
> is already a solid, real assertion — keep it as-is. The `addCrossSideTrigger...` test above is deliberately
> left with a concrete note on the exact fixture problem to solve (a mock's `reconnect()` is a no-op; a fresh real
> `AbstractSender` also short-circuits `shouldReconnect()` until it has connected once) rather than a
> hand-wavy "add appropriate assertions" — resolve it by driving a real minimal `AbstractSender` fixture through
> a successful `beginConnect()` first, then a failing `reconnect(...)`, mirroring the exact pattern
> `SenderReconnectGatingSpecTest`/`SenderStartupSpecTest` already use elsewhere in this codebase for the
> equivalent "must have connected once before a reconnect proceeds" gate. Do not weaken this to only proving "does
> not throw" — the multi-trigger fan-out is exactly what Task 1 fixed a real single-slot data-loss bug for, so
> losing coverage of it here would be a regression in disguise.

In `NjamsSenderTest.java`, **delete** `wireReceiverSetsCoordinatorOnAbstractReceiver` and
`wireReceiverIsANoOpForNonAbstractReceiverImplementations` (they exercise the old sharing behavior). Append:

```java
    @Test
    public void wireReceiverBidirectionallyTriggersOnExceptionOnBothSides() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        NjamsSender sender = new NjamsSender(settings);

        AtomicBoolean receiverStopped = new AtomicBoolean(false);
        AbstractReceiver receiver = new AbstractReceiver() {
            @Override public String getName() { return "wire-test-receiver"; }
            @Override public void connect() { connectionStatus = ConnectionStatus.CONNECTED; }
            @Override public void stop() { receiverStopped.set(true); connectionStatus = ConnectionStatus.DISCONNECTED; }
        };
        sender.wireReceiver(receiver);

        // Sender -> receiver direction: force one pooled sender's coordinator to report a failure directly
        // (bypassing real transport connect/send), and assert the receiver's onException(...) — which calls
        // stop() first — was genuinely invoked, not just that nothing threw.
        AbstractSender pooledSender = sender.senderPool.get();
        pooledSender.reconnect(new com.im.njams.sdk.common.NjamsSdkRuntimeException("forced failure"));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (!receiverStopped.get() && System.nanoTime() < deadline) {
            try { Thread.sleep(20); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
        assertTrue("a sender-side failure must reach the wired receiver's onException(...)", receiverStopped.get());
        sender.close();
    }

    @Test
    public void wireReceiverIsANoOpForNonAbstractReceiverImplementations() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        NjamsSender sender = new NjamsSender(settings);
        sender.wireReceiver(new TestReceiver()); // must not throw
        sender.close();
    }
```

> `wireReceiverBidirectionallyTriggersOnExceptionOnBothSides()`'s `pooledSender.reconnect(...)` call has the exact
> same "must have connected once, or the group's startup policy must permit it" precondition noted in
> `SenderPoolTest`'s equivalent test above — resolve it the same way (drive a successful connect first, e.g. via
> `pooledSender.connect()` directly since `TestSender`'s `connect()` succeeds trivially, so `coordinator
> .wasEverConnected()` becomes true) before calling `reconnect(...)`, so the call actually reaches
> `beginReconnect()` and fires the trigger. `senderPool` is currently `protected` on `NjamsSender` — confirm this
> test (same package) can reach it directly; if a later refactor narrows its visibility, add a package-private
> test accessor instead of widening production visibility for test convenience. This test only covers the
> sender-to-receiver direction; the symmetric receiver-to-sender direction is covered end-to-end in Task 5's
> `CrossSideVerificationSpecTest` through a real `Njams` — do not duplicate that here.

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderPoolTest,NjamsSenderTest`
Expected: FAIL to compile — `triggerConnectionCheck`/`addCrossSideTrigger` do not exist on `SenderPool` yet.

- [ ] **Step 3: Implement — `SenderPool.java`**

Replace `getConnectionCoordinator()` (currently ~line 80-86) with:

```java
    /**
     * Registers a callback invoked once when this pool's coordinator detects a new failure in any of its pooled
     * senders — used to prompt a wired receiver to verify its own connection (see
     * {@link NjamsSender#wireReceiver(Receiver)}). Add-only: multiple callbacks may be registered and all fire —
     * with {@code njams.sdk.communication.shared=true} on HTTP, one shared pool may be wired to several
     * different per-{@code Njams}-instance receivers.
     *
     * @param trigger the callback to add.
     */
    void addCrossSideTrigger(Runnable trigger) {
        coordinator.addCrossSideTrigger(trigger);
    }

    /**
     * Forces every currently pooled sender (locked and unlocked) to cycle its connection, regardless of its
     * apparent current state — the sender-side half of the cross-side "assume-and-cycle" connection verification
     * (see {@link NjamsSender#wireReceiver(Receiver)}). Reuses each sender's existing
     * {@link AbstractSender#onException(Exception)} entry point; no new detection logic.
     */
    void triggerConnectionCheck() {
        streamAll().forEach(s -> s.onException(new NjamsSdkRuntimeException(
            "Cross-side connection check: the wired receiver detected a connection failure.")));
    }
```

Add the import `com.im.njams.sdk.common.NjamsSdkRuntimeException` if not already present.

`AbstractSender.onException(Exception)` is currently `protected` — confirm it is callable from `SenderPool`
(same package `com.im.njams.sdk.communication`; `protected` grants same-package access regardless of
inheritance, so this compiles without any visibility change).

- [ ] **Step 4: Implement — `NjamsSender.java`**

Replace `wireReceiver(Receiver receiver)` (currently ~line 289-303):

```java
    /**
     * Wires this sender group and the given receiver together for cross-side connection verification: a failure
     * detected on either side prompts the other to proactively cycle its own connection ("assume-and-cycle" — no
     * active probing, just each side's existing reconnect machinery triggered from the other side too). This is
     * a one-way trigger in each direction, not shared state — the sender group and the receiver keep fully
     * independent {@code ConnectionCoordinator}s, reconnect loops, and shutdown signaling. No-op if
     * {@code receiver} is not an {@link AbstractReceiver} (custom {@link Receiver} implementations outside
     * {@code AbstractReceiver} have no reconnect mechanism to trigger). Safe to call repeatedly, including with
     * different receivers sharing this same sender group (e.g. {@code njams.sdk.communication.shared=true} on
     * HTTP, where each {@code Njams} instance has its own receiver but shares one sender pool) — every wired
     * receiver is notified, not just the most recently wired one.
     *
     * @param receiver the receiver to wire for cross-side verification with this sender group.
     * @since 6.0.0
     */
    public void wireReceiver(Receiver receiver) {
        if (receiver instanceof AbstractReceiver) {
            AbstractReceiver abstractReceiver = (AbstractReceiver) receiver;
            senderPool.addCrossSideTrigger(() -> abstractReceiver.onException(new NjamsSdkRuntimeException(
                "Cross-side connection check: the wired sender group detected a connection failure.")));
            abstractReceiver.addCrossSideTrigger(senderPool::triggerConnectionCheck);
        }
    }
```

> **Double-registration, resolved (not deferred):** today's `Njams.java` calls `wireReceiver(...)` *twice* for the
> same receiver in the normal startup path — once from `beginConnect()` (construction-time pre-warming) and once
> from `startReceiver(boolean)` (at `start()`), since `receiver` is reassigned from `earlyReceiver` (the exact
> same instance) between the two calls. With Part 3's old `setConnectionCoordinator(...)`-based `wireReceiver`
> this was harmless (a `set` simply re-assigns the same reference). With this task's add-only
> `addCrossSideTrigger`, it would register the trigger **twice**, double-firing `onException(...)` on every
> single sender failure even in the common, non-shared case — not an edge case, the default path. Task 4 resolves
> this at the root rather than adding de-duplication here: `Njams.beginConnect()`'s `earlySender.wireReceiver
> (earlyReceiver)` call is **removed** entirely. It existed in Part 3 only so the receiver's coordinator was ready
> to consult `allowReconnectBeforeConnected()`/`shouldReconnect()` before its first pre-warm failure — Task 2
> already removes that consultation (the receiver retries unconditionally now), so the early wiring has no
> remaining purpose. `wireReceiver` is called exactly once per receiver, from `startReceiver()`, for the whole
> normal lifecycle. Confirm this against the live `Njams.java` in Task 4 before assuming it — this note describes
> the intended fix, not yet-verified live behavior.

- [ ] **Step 4b: Remove the now-dead `AbstractReceiver.setConnectionCoordinator`**

With Step 4's new `wireReceiver` body above, nothing calls `AbstractReceiver.setConnectionCoordinator
(ConnectionCoordinator)` any more anywhere in the codebase — Task 2 deliberately left it in place only because
this task's own `wireReceiver` change is what retires its last caller. Grep the whole `njams-sdk` tree for
`setConnectionCoordinator` to confirm zero remaining call sites (only the method's own declaration and its
Javadoc `{@link}` cross-references should remain), then remove the method entirely from `AbstractReceiver.java`.
Once it's gone, tighten the `coordinator` field it used to feed: change
`private volatile ConnectionCoordinator coordinator = new ConnectionCoordinator();` to
`private final ConnectionCoordinator coordinator = new ConnectionCoordinator();` (drop `volatile`, add `final`) —
safe now that nothing ever reassigns it. Update the field's Javadoc (it currently describes being "shared with the
sender group, if wired via `NjamsSender#wireReceiver(Receiver)`" — that description is now wrong; it is never
shared, only the narrow `addCrossSideTrigger` signal is) to describe the current reality: an independent,
never-reassigned coordinator. Do **not** remove `startWithTimeout(long, boolean)` here — `Njams.java` still calls
it until Task 4.

- [ ] **Step 5: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderPoolTest,NjamsSenderTest,AbstractReceiverTest`
Expected: PASS.

- [ ] **Step 6: Regression check**

Run: `mvn -q -pl njams-sdk test -Dtest="AbstractSenderStaticStateTest,ConnectionCoordinatorTest,AbstractReceiverStaticStateTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderPoolTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/NjamsSenderTest.java
git commit -m "SDK-375 Repurpose NjamsSender.wireReceiver for bidirectional cross-side connection verification"
```

---

### Task 4: `Njams.java` — `start()` depends only on the sender; independent shutdown signaling; remove the now-dead 2-arg `startWithTimeout`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java` (remove the
  `startWithTimeout(long, boolean)` override — its only caller, `Njams.startReceiver(boolean)`, is removed by
  this same task; the `coordinator` field itself was already tightened to `private final` in Task 3, nothing
  further to do there)
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/Receiver.java` (remove the
  `startWithTimeout(long, boolean)` interface default)
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java` (delete the four
  remaining 2-arg `startWithTimeout` tests/fixtures Task 2 deliberately left alone)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverStartGatingSpecTest.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverShutdownSpecTest.java`
- Delete: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/CoordinatorSharingSpecTest.java`

**Interfaces:**
- Consumes: `NjamsSender.wireReceiver(Receiver)` (Task 3 — its call site moves: no longer called from
  `beginConnect()`, only from `startReceiver()`, see Step 3 below); `AbstractReceiver.setShouldShutdown(boolean)`,
  `cancelReconnect()` (existing, unchanged).
- Produces: `Njams.startReceiver()` (was `startReceiver(boolean)`, now no parameter, `void` return — replaces the
  private method entirely, not an overload).
- Removes: `Receiver.startWithTimeout(long, boolean)` (interface default); `AbstractReceiver`'s override of it.

- [ ] **Step 1: Write the failing/rewritten spec tests**

**Delete** `CoordinatorSharingSpecTest.java` entirely — its premise (`Njams.stop()` never calls
`setShouldShutdown` directly; the receiver only learns of shutdown via a shared coordinator instance) is reverted
by this task.

**Rewrite** `ReceiverStartGatingSpecTest.java` completely — receiver failure must never gate `start()` under
either sender policy:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.settings.Settings;

public class ReceiverStartGatingSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    private Njams newNjams(String senderFailBehavior) {
        Settings s = LifecycleTestTransport.settings();
        if (senderFailBehavior != null) {
            s.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, senderFailBehavior);
        }
        return new Njams(Path.of("test", "receiverGatingRemoved"), "1.0", "test", s);
    }

    @Test
    public void receiverFailureNeverFailsStartUnderTheDefaultFailPolicy() {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("fail");
        assertTrue("start() must depend only on the sender; the receiver failing must not fail it",
            njams.start());
        assertTrue(njams.isStarted());
    }

    @Test
    public void receiverFailureNeverFailsStartUnderTheReconnectPolicy() {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("reconnect");
        assertTrue(njams.start());
        assertTrue(njams.isStarted());
    }

    @Test
    public void senderFailureStillFailsStartRegardlessOfTheReceiver() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("fail");
        assertFalse("the sender remains critical: its failure must still fail start()", njams.start());
        assertFalse(njams.isStarted());
    }

    @Test
    public void startSucceedsWhenBothSenderAndReceiverConnect() {
        njams = newNjams("fail");
        assertTrue(njams.start());
        assertTrue(njams.isStarted());
    }
}
```

**Modify** `ReceiverShutdownSpecTest.java` — add a second test proving the receiver's shutdown signal is now
sent directly (no longer inferred only via a shared coordinator instance, since there is not one). Keep the
existing `stopCancelsAnInProgressReceiverReconnect` test and its class Javadoc exactly as-is (still accurate: it
is still a deliberately narrow "no hang" proof), and append:

```java
    @Test
    public void stopSetsShouldShutdownDirectlyOnTheReceiver() throws Exception {
        njams = new Njams(Path.of("test", "receiverShutdownDirect"), "1.0", "test", LifecycleTestTransport.settings());
        assertTrue(njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull("Njams must have constructed a receiver reachable through the test registry", receiver);

        assertTrue(njams.stop());

        // With independent coordinators, a stray reconnect after stop() can only see shouldShutdown() == true if
        // Njams.stop() told this receiver directly — there is no shared instance to observe it via a side effect.
        CountDownLatch attempted = LifecycleTestTransport.receiverConnectAttemptedLatch();
        receiver.reconnect(new IllegalStateException("late failure observed after stop()"));
        assertFalse("stop() must have set shouldShutdown directly on this receiver's own coordinator",
            attempted.await(500, TimeUnit.MILLISECONDS));
    }
```

(Add the `assertNotNull`/`CountDownLatch`/`TimeUnit` imports if not already present in this file — check first.)

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=ReceiverStartGatingSpecTest,ReceiverShutdownSpecTest`
Expected: `ReceiverStartGatingSpecTest`'s `receiverFailureNeverFailsStartUnderTheDefaultFailPolicy` and
`...UnderTheReconnectPolicy` FAIL (today's code still gates on the receiver). `stopSetsShouldShutdownDirectlyOnTheReceiver`
FAILS (today's `stop()` never calls `setShouldShutdown` on the receiver at all — it relies entirely on the shared
coordinator side effect Task 2/3 just removed, so this reconnect will actually attempt to connect).

- [ ] **Step 3: Implement — `Njams.java`**

Replace `startReceiver(boolean reconnectOnFailure)` (currently lines ~684-724) with a simplified, non-failing
version:

```java
    /**
     * Best-effort receiver setup: resolves the receiver (from the pre-warmed {@code earlyReceiver} or newly
     * created) and wires it to the active sender group for cross-side connection verification (see
     * {@link NjamsSender#wireReceiver(Receiver)}). The receiver's connection outcome never affects {@code
     * start()} — a construction or connection failure is logged and the SDK proceeds without a working receiver;
     * only the sender is critical to startup (see {@link #start()}). The receiver itself was already told to
     * begin connecting in the background by {@link #beginConnect()}; this method does not wait for it.
     */
    private void startReceiver() {
        try {
            final NjamsSender activeSender = getSender();
            if (earlyReceiver != null) {
                receiver = earlyReceiver;
                earlyReceiver = null;
            } else {
                receiver = new CommunicationFactory(settings).getReceiver(this);
            }
            if (activeSender != null) {
                activeSender.wireReceiver(receiver);
            }
            if (receiver instanceof SenderExceptionListener && activeSender != null) {
                activeSender.addSenderExceptionListener((SenderExceptionListener) receiver);
            }
        } catch (Exception e) {
            LOG.warn("Failed to initialize the receiver; the SDK will operate without receiving server "
                + "commands until this is resolved.", e);
            receiver = null;
        }
    }
```

Replace `start()` (currently lines ~731-765):

```java
    public boolean start() {
        if (!isStarted()) {
            if (settings == null) {
                throw new NjamsSdkRuntimeException("Settings not set");
            }
            configuration.load();
            configuration.initializeDataMasking();
            commands.add(this);
            commands.add(new ConfigurationInstructionListener(this));
            startReceiver();
            final NjamsSender activeSender = getSender();
            if (activeSender != null) {
                long timeoutMs = settings.getLong(
                    NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_MS);
                boolean reconnectOnFailure = NjamsSender.reconnectOnStartupFailure(settings);
                if (!activeSender.startWithTimeout(timeoutMs, reconnectOnFailure)) {
                    LOG.error("SDK startup failed: sender could not connect and startup fail-behavior is 'fail'. "
                        + "The SDK instance is inactive.");
                    stopReceiverAfterStartupFailure(receiver);
                    receiver = null;
                    releasePrewarmedSender();
                    return false;
                }
            }
            LogMessageFlushTask.start(this);
            CleanTracepointsTask.start(this);
            lifecycle.setStarted(true);
            sendProjectMessage();
            LOG.info("SDK instance {} started (client-session={})", getClientPath(), metadata.getClientSessionId());
        }
        return isStarted();
    }
```

> Note the call `activeSender.startWithTimeout(timeoutMs, reconnectOnFailure)` replaces the previous
> `activeSender.startWithTimeout(timeoutMs)` (1-arg) — this is equivalent (the 1-arg sender-side overload
> internally resolves the identical `reconnectOnFailure` via `StartupFailBehavior.fromSettings(settings)`, see
> `NjamsSender.startWithTimeout(long)`), but computing it explicitly here means `NjamsSender.reconnectOnStartupFailure
> (settings)` (Task 1's original method, still present, sender-only now) has a real caller again, matching the
> plan's own architecture note. Either form compiles and behaves identically; using the 2-arg form here makes the
> "sender-only, still configurable" scope explicit at the call site — keep it.

Update `stopReceiverAfterStartupFailure(Receiver failedReceiver)` (currently lines ~767-800) — add the direct
shutdown signal alongside the existing `cancelReconnect()`, and update its Javadoc (the old rationale referenced
the now-reverted shared-coordinator self-trigger path):

```java
    /**
     * Stops the given receiver after a sender startup failure, unregistering this instance from a shared receiver
     * via {@link ShareableReceiver#removeNjams(Njams)} where applicable instead of stopping it outright for every
     * instance still using it — mirroring {@link #stop()}'s own gating exactly. Only once that determines this
     * really was the last user (trivially true for a non-{@code ShareableReceiver} {@link AbstractReceiver}) does
     * this signal shutdown and cancel any reconnect the receiver may already be running on its own — the receiver
     * always retries its own connection unconditionally in the background (see {@link AbstractReceiver#beginConnect()}),
     * independently of this startup's sender-side fail-fast decision, so it may already be reconnecting by the
     * time the sender's own failure is discovered. Signalling/cancelling unconditionally instead would wrongly
     * stop a reconnect a still-registered sharing instance depends on.
     *
     * @param failedReceiver the receiver to stop; {@code null} is a no-op.
     */
    private void stopReceiverAfterStartupFailure(Receiver failedReceiver) {
        if (failedReceiver == null) {
            return;
        }
        try {
            boolean reallyStopped;
            if (failedReceiver instanceof ShareableReceiver) {
                reallyStopped = ((ShareableReceiver<?>) failedReceiver).removeNjams(this);
            } else {
                failedReceiver.stop();
                reallyStopped = true;
            }
            if (reallyStopped && failedReceiver instanceof AbstractReceiver) {
                ((AbstractReceiver) failedReceiver).setShouldShutdown(true);
                ((AbstractReceiver) failedReceiver).cancelReconnect();
            }
        } catch (Exception ex) {
            LOG.debug("Unable to stop receiver after startup failure", ex);
        }
    }
```

Update `stop()` (currently lines ~828-863) the same way, and correct its Javadoc:

```java
    /**
     * Stop a client; it stop processing and release the connections. It can't
     * be stopped before it started. (NjamsSdkRuntimeException)
     * <p>
     * The sender and receiver are signalled independently: closing the sender marks its own {@code
     * ConnectionCoordinator} as shutting down; separately, once the receiver is really stopped — i.e. once the
     * last {@code Njams} instance using a shared receiver has stopped it (see {@link ShareableReceiver#removeNjams})
     * — its own, independent coordinator is told to shut down and its in-progress reconnect is cancelled.
     *
     * @return true is stopping was successful.
     */
    public boolean stop() {
        lifecycle.requireStarted();
        LogMessageFlushTask.stop(this);
        CleanTracepointsTask.stop(this);

        argos.stop();

        if (sender != null) {
            sender.close();
        }
        if (receiver != null) {
            boolean reallyStopped;
            if (receiver instanceof ShareableReceiver) {
                reallyStopped = ((ShareableReceiver<?>) receiver).removeNjams(this);
            } else {
                receiver.stop();
                reallyStopped = true;
            }
            if (reallyStopped && receiver instanceof AbstractReceiver) {
                ((AbstractReceiver) receiver).setShouldShutdown(true);
                ((AbstractReceiver) receiver).cancelReconnect();
            }
        }
        commands.clear();
        lifecycle.setStarted(false);
        return !isStarted();
    }
```

**Remove the `earlySender.wireReceiver(earlyReceiver)` call from `beginConnect()`** (currently lines ~650-682) —
per Task 3's note, this is required, not optional: with the new add-only `addCrossSideTrigger`, wiring here AND
again in `startReceiver()` would register the receiver's trigger twice, double-firing `onException(...)` on every
sender failure in the default (non-shared) path. The call existed in Part 3 only so the receiver's coordinator
was ready to consult `allowReconnectBeforeConnected()`/`shouldReconnect()` before its own first pre-warm failure
— Task 2 already removed that consultation entirely (the receiver retries unconditionally now regardless of any
coordinator flag), so early wiring has no remaining purpose. `wireReceiver` is now called exactly once per
receiver, from `startReceiver()` (below).

Replace the whole method:

```java
    /**
     * Pre-creates the sender and receiver and starts both their connection attempts in the background, so the
     * connections overlap with the remaining application setup. Called automatically at construction time.
     * Idempotent and best-effort: any failure is swallowed and {@link #startReceiver()} will retry creating the
     * receiver. Cross-side connection verification (see {@link NjamsSender#wireReceiver(Receiver)}) is wired
     * later, in {@link #startReceiver()} — not here — since it depends on nothing this early pre-warming needs.
     */
    private void beginConnect() {
        if (earlyReceiver != null || lifecycle.isStarted()) {
            return;
        }
        NjamsSender earlySender = null;
        try {
            earlySender = getSender();
        } catch (Exception e) {
            LOG.warn("beginConnect() failed to pre-warm sender; start() will retry.", e);
        }
        try {
            earlyReceiver = new CommunicationFactory(settings).getReceiver(this);
            if (earlyReceiver instanceof AbstractReceiver) {
                ((AbstractReceiver) earlyReceiver).beginConnect();
            }
        } catch (Exception e) {
            LOG.warn("beginConnect() failed to pre-initialize receiver; start() will retry.", e);
            earlyReceiver = null;
        }
        if (earlySender != null) {
            earlySender.beginConnect();
        }
    }
```

(This is the exact pre-Part-4 body with only the `earlySender.wireReceiver(earlyReceiver)` line removed from the
middle `try` block — everything else, including the try/catch structure, is unchanged.)

- [ ] **Step 3b: Remove the now-dead 2-arg `startWithTimeout`**

With `Njams.startReceiver()` above no longer calling `receiver.startWithTimeout(timeoutMs, reconnectOnFailure)`,
that method has no remaining caller anywhere in the codebase. Grep the whole `njams-sdk` tree for
`startWithTimeout(` to confirm (you should find only the 1-arg method's declaration/callers, and the 2-arg
method's own now-orphaned declaration), then:

1. In `AbstractReceiver.java`, remove the entire `@Override public boolean startWithTimeout(long timeoutMs,
   boolean reconnectOnFailure) { ... }` method and its Javadoc.
2. In `Receiver.java`, remove the entire `default boolean startWithTimeout(long timeoutMs, boolean
   reconnectOnFailure) { ... }` method and its Javadoc. Check whether the `NjamsSdkRuntimeException` import is
   still needed (the 1-arg `startWithTimeout(long)`'s `@throws` tag references it) — keep the import if so, remove
   it if it becomes genuinely unused.
3. In `AbstractReceiverTest.java`, delete the four tests/fixtures Task 2 deliberately left in place because the
   method still existed at that point: `startWithTimeoutTwoArgReturnsTrueOnSuccess`,
   `startWithTimeoutTwoArgReconnectPolicyReturnsTrueAndEntersBackgroundReconnect`,
   `defaultTwoArgStartWithTimeoutOnPlainReceiverIgnoresReconnectOnFailure`,
   `startWithTimeoutTwoArgReconnectPolicyReturnsQuicklyDespiteMultipleRetries`, plus `MultiFailThenSucceedsReceiverImpl`
   and the anonymous plain-`Receiver` fixture inside the "OnPlainReceiver" test. Leave `FlakyThenSucceedsReceiverImpl`
   only if something else in the file still uses it after the deletions — check before removing it; `SlowConnectReceiverImpl`
   is unrelated (used by other, unaffected tests) and must stay regardless.

- [ ] **Step 4: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=ReceiverStartGatingSpecTest,ReceiverShutdownSpecTest,AbstractReceiverTest`
Expected: PASS.

- [ ] **Step 5: Full baseline + lifecycle + Njams regression run**

Run: `mvn -q -pl njams-sdk test -Dtest="JmsClientEndToEndBaselineIT,JmsSenderBaselineIT,HttpSenderBaselineIT,NjamsTest,NjamsSenderTest,SenderStartGatingSpecTest,SenderStartupSpecTest,SenderReconnectGatingSpecTest,SenderShutdownSpecTest,SenderLoggingSpecTest,AbstractReceiverTest,AbstractReceiverStaticStateTest,ConnectionCoordinatorTest,AbstractSenderStaticStateTest,ReceiverLoggingSpecTest,JmsReceiverTest,HttpSseReceiverClassReferencesTest"`
Expected: PASS across the board. (`ReceiverLoggingSpecTest` is expected to still fail here — that is Task 5's job;
if it already fails, confirm the failure is exactly the log-wording mismatch and not something else.)

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/Njams.java njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java njams-sdk/src/main/java/com/im/njams/sdk/communication/Receiver.java njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverStartGatingSpecTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverShutdownSpecTest.java
git rm njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/CoordinatorSharingSpecTest.java
git commit -m "SDK-375 Make Njams.start() depend only on the sender; signal receiver shutdown independently; remove dead 2-arg startWithTimeout"
```

---

### Task 5: New cross-side verification spec test + `ReceiverLoggingSpecTest` rewording

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/CrossSideVerificationSpecTest.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverLoggingSpecTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1-4 (the full wiring must already be in place for this task's tests to compile
  and pass).

- [ ] **Step 1: Write the failing tests**

`CrossSideVerificationSpecTest.java` — black-box, through a real `Njams`, proving D1.7 end-to-end:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;

/**
 * Proves the D1.7 cross-side connection-verification trigger end-to-end through a real {@code Njams}: a
 * connection failure detected on one side (sender or receiver) prompts the other to cycle its own connection,
 * even though the two sides otherwise keep fully independent lifecycle state (see
 * {@code SenderReceiverIndependenceSpecTest}-equivalent coverage already established across Tasks 2-4's own
 * tests — this class covers only the trigger itself).
 */
public class CrossSideVerificationSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void receiverFailureTriggersTheSenderToCycleItsConnection() throws Exception {
        njams = new Njams(Path.of("test", "crossVerifyReceiverToSender"), "1.0", "test",
            LifecycleTestTransport.settings());
        assertTrue(njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull(receiver);

        // Sender is currently connected and idle. A receiver-side failure must prompt the sender to cycle even
        // though the sender itself observed nothing wrong.
        CountDownLatch senderConnectAttempted = LifecycleTestTransport.connectAttemptedLatch();
        receiver.onException(new IllegalStateException("receiver connection lost"));

        assertTrue("a receiver failure must trigger the sender to cycle (re-attempt) its own connection",
            senderConnectAttempted.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void senderFailureTriggersTheReceiverToCycleItsConnection() throws Exception {
        njams = new Njams(Path.of("test", "crossVerifySenderToReceiver"), "1.0", "test",
            LifecycleTestTransport.settings());
        assertTrue(njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull(receiver);
        receiver.forceDisconnect(); // so the receiver's own subsequent connect() attempt is meaningful, not a no-op

        // Sender is connected; force it to report a failure via its own real onException path.
        LifecycleTestSender sender = LifecycleTestSender.lastCreated();
        assertNotNull("a real LifecycleTestSender must have been pooled by Njams.start()", sender);
        sender.forceDisconnect();

        CountDownLatch receiverConnectAttempted = LifecycleTestTransport.receiverConnectAttemptedLatch();
        sender.onExceptionForTest(new IllegalStateException("sender connection lost"));

        assertTrue("a sender failure must trigger the receiver to cycle (re-attempt) its own connection",
            receiverConnectAttempted.await(2, TimeUnit.SECONDS));
    }
}
```

> `LifecycleTestSender` needs a `lastCreated()` accessor mirroring `LifecycleTestReceiver.lastCreated()` (Part 3)
> and a way to invoke `onException(Exception)` for a test — that method is `protected` on `AbstractSender`, not
> callable from a test in a different package. Add both to `LifecycleTestSender` in this same step (test-only
> infrastructure, no production code):
>
> ```java
>     private static final java.util.List<LifecycleTestSender> INSTANCES2 = ...
> ```
>
> Actually reuse the **existing** `INSTANCES` registry already in `LifecycleTestSender` (Part 2) rather than
> adding a second one — add:
>
> ```java
>     static LifecycleTestSender lastCreated() {
>         return INSTANCES.isEmpty() ? null : INSTANCES.get(INSTANCES.size() - 1);
>     }
>
>     /** Test hook: exposes the protected onException(Exception) for tests in this package. */
>     public void onExceptionForTest(Exception e) {
>         onException(e);
>     }
> ```
>
> Read the live `LifecycleTestSender.java` first (Part 3 already may have changed it further) before adding —
> verify `INSTANCES` is still the exact field name and still package-visible from this test.

`ReceiverLoggingSpecTest.java` — update the assertions for the new wording/level (Task 2 already changed the
production code; this step catches the test up):

```java
    @Test
    public void reconnectLogsOnceOnLossAndOnceOnRestore() throws Exception {
        FlakyReceiver receiver = new FlakyReceiver();
        receiver.reconnect(new NjamsSdkRuntimeException("lost"));

        long lossWarnings = appender.events().stream()
            .filter(e -> e.getLevel().equals(Level.WARN))
            .filter(e -> String.valueOf(e.getRenderedMessage()).startsWith("Receiver connection lost"))
            .count();
        long restoreInfos = appender.events().stream()
            .filter(e -> e.getLevel().equals(Level.INFO))
            .filter(e -> String.valueOf(e.getRenderedMessage()).startsWith("Receiver reconnected"))
            .count();
        assertEquals("exactly one connection-lost warning", 1, lossWarnings);
        assertEquals("exactly one reconnected info", 1, restoreInfos);
    }
```

Rename the old `reconnectLogsOnceOnStartAndOnceOnSuccess` test to `reconnectLogsOnceOnLossAndOnceOnRestore` (or
replace it in place) — do not keep both, the old name/assertions describe wording that no longer exists. Update
`receiverLogger.setLevel(Level.DEBUG)` in `@Before` — confirm `Level.DEBUG` still captures `WARN` and `INFO`
(log4j levels are ordered `DEBUG < INFO < WARN`, so a `DEBUG` threshold already captures both; no change needed
there, just verify while editing).

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=CrossSideVerificationSpecTest,ReceiverLoggingSpecTest`
Expected: `CrossSideVerificationSpecTest` FAILS to compile until `LifecycleTestSender.lastCreated()`/
`onExceptionForTest(...)` are added. `ReceiverLoggingSpecTest` FAILS on the old assertions (or compiles and fails
if Task 2 already shipped the new wording — confirm which).

- [ ] **Step 3: Add the `LifecycleTestSender` test hooks (see note in Step 1) and re-run**

Run: `mvn -q -pl njams-sdk test -Dtest=CrossSideVerificationSpecTest,ReceiverLoggingSpecTest`
Expected: PASS.

- [ ] **Step 4: Full regression**

Run: `mvn -q -pl njams-sdk test -Dtest="SenderStartGatingSpecTest,SenderStartupSpecTest,SenderReconnectGatingSpecTest,SenderShutdownSpecTest,SenderLoggingSpecTest,ReceiverStartGatingSpecTest,ReceiverShutdownSpecTest,LifecycleTransportWiringTest"`
Expected: PASS (confirms the new `LifecycleTestSender`/`LifecycleTestReceiver` additions did not disturb any
existing lifecycle spec test).

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/CrossSideVerificationSpecTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverLoggingSpecTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTestSender.java
git commit -m "SDK-375 Add cross-side connection-verification spec test; update receiver logging spec test wording"
```

---

### Task 6: Documentation — `wiki/FAQ.md` and `settings_full.properties`

**Files:**
- Modify: `wiki/FAQ.md`
- Modify: `njams-sdk-sample-client/src/main/resources/settings_full.properties`

- [ ] **Step 1: Update the `startup.failbehavior` and `connect.timeout` rows in `wiki/FAQ.md`'s settings table**

Read the live file first — line numbers below are as of this plan's writing (`wiki/FAQ.md` around lines 159, 163).

Replace the `njams.sdk.communication.connect.timeout` row's description:

```
Maximum time in milliseconds the SDK waits for the *sender's* initial communication connection during `Njams.start()`. What happens on timeout depends on `njams.sdk.communication.startup.failbehavior`: under the default `fail` policy, `start()` returns `false` and the SDK instance stays inactive; under `reconnect`, `start()` returns `true` instead and the connection is retried in the background. Applies to all transports. The receiver's own connection is independent of this timeout — see [What happens when the communication backend is unreachable at startup](#what-happens-when-the-communication-backend-is-unreachable-at-startup).
```

Replace the `njams.sdk.communication.startup.failbehavior` row's description:

```
Controls how `Njams.start()` reacts when the *sender's* initial transport connect attempt does not succeed within `njams.sdk.communication.connect.timeout`. `fail` (default): `start()` returns `false` and the SDK instance stays inactive, leaving it to the client to decide whether to continue without nJAMS. `reconnect`: `start()` returns `true` and the connection is retried in the background until it succeeds. Governs the **sender only** — the receiver's connection outcome never affects `start()`'s return value, at startup or later; it always retries in the background unconditionally. See [What happens when the communication backend is unreachable at startup](#what-happens-when-the-communication-backend-is-unreachable-at-startup).
```

- [ ] **Step 2: Rewrite the "What happens when the communication backend is unreachable at startup" section**

Replace the section (currently starting around line 250) with:

```markdown
## What happens when the communication backend is unreachable at startup

`Njams.start()`'s success depends only on the **sender**. It does not block indefinitely when the sender's
communication backend cannot be reached: the maximum wait is bounded by the
[`njams.sdk.communication.connect.timeout`](#communication) setting (default `30000` ms). What happens once that
time elapses without a successful sender connection is controlled by
[`njams.sdk.communication.startup.failbehavior`](#communication):

- `fail` (default): `start()` logs an error and returns `false`. The SDK instance is then completely inactive,
  and no sender reconnect thread is started. It is the client application's responsibility to check the return
  value of `start()` and decide whether to continue without nJAMS.
- `reconnect`: `start()` returns `true` and the sender connection is retried in the background until it succeeds.
  The SDK instance is considered started; messages produced before the connection is established are subject to
  the configured discard policy (`njams.sdk.discardpolicy`), which by default discards them while disconnected.

This applies to all transports (HTTP, JMS, Kafka). For JMS in particular, the JMS API provides no standard
connection timeout; without this bound a startup attempt against an unreachable broker could silently block for
60–120 seconds at the OS TCP level.

**The receiver is independent and never blocks or fails startup.** Unlike the sender, the receiver's connection
outcome — whether at startup or later — never affects `start()`'s return value, and is not governed by
`njams.sdk.communication.startup.failbehavior` or any other setting: it always retries in the background
unconditionally. This is intentional — if the receiver cannot connect, the client can still push monitoring data
to nJAMS normally; it can only not receive commands from the server, which is a rare occurrence. On a receiver
connection loss the SDK logs a warning (`Receiver connection lost. The client will not receive any commands from
the server until reconnected.`) and an info once it reconnects (`Receiver reconnected. Handling server commands
resumed.`).

**Cross-side connection verification.** A connection failure detected on either side (sender or receiver)
prompts the *other* side to proactively cycle its own connection too, even if that side had not itself detected
any problem. This exists because the receiver is idle most of the time and could otherwise be slow to notice a
real connection loss on its own; the signal is symmetric and does not affect either side's independent
startup/shutdown/reconnect-gating behavior described above.

**Overlap with application setup.** The SDK starts both connection attempts in the background automatically when
the `Njams` instance is constructed. When `start()` is subsequently called, it awaits only the sender's
already-running connection, applying the timeout to the remaining wait.
```

> Check the paragraph immediately following this section in the live file (it continues past what's quoted
> above, e.g. discussing what happens if the connection completes after the timeout) — preserve it if still
> accurate, adjust if it refers to the receiver in a way this revision changes.

- [ ] **Step 3: Update `settings_full.properties`**

Replace the comment block above `#njams.sdk.communication.startup.failbehavior=fail`:

```properties
#njams.sdk.communication.connect.timeout=30000
# Startup fail-behavior for the SENDER's transport connection (the receiver is independent — see the FAQ).
# fail (default): njams.start() returns false and the SDK stays inactive if the sender's initial connect fails.
# reconnect: njams.start() returns true and the sender connection is retried in the background until it succeeds.
#njams.sdk.communication.startup.failbehavior=fail
```

- [ ] **Step 4: Commit**

```bash
git add wiki/FAQ.md njams-sdk-sample-client/src/main/resources/settings_full.properties
git commit -m "SDK-375 Document sender-only startup.failbehavior scope and the receiver's independent, unconditional behavior"
```

---

### Task 7: Full verification, self-review, finalize

**Files:** none (verification only), then the finalizing commit.

- [ ] **Step 1: Checkstyle + Javadoc**

Run: `mvn -pl njams-sdk -Pcheckstyle checkstyle:check`
Run: `mvn -pl njams-sdk javadoc:javadoc`
Expected: both pass. Confirm no dangling `{@link}` to anything removed this plan (`Receiver#startWithTimeout
(long, boolean)`, `AbstractReceiver#setConnectionCoordinator`, `SenderPool#getConnectionCoordinator`) — grep the
whole `njams-sdk/src` tree for these three names; any remaining reference outside this plan's own removed code is
a real bug to fix before proceeding.

- [ ] **Step 2: Full module test suite**

Run: `mvn -pl njams-sdk test`
Expected: `Failures: 0, Errors: 0`, same pre-existing skips as every prior baseline run
(`AMQInitTest`, `FileConfigurationProviderTest`). Investigate anything new.

- [ ] **Step 3: Relocated-type check**

Confirm none of this plan's new/changed public members reference any relocated package from `pom.xml`'s
Maven-shade `<relocations>` list: `NjamsSender.wireReceiver(Receiver)` (unchanged signature, `Receiver` is an SDK
type). No other public signature changed or was added by this plan — `SenderPool.addCrossSideTrigger`/
`triggerConnectionCheck`, `AbstractReceiver.addCrossSideTrigger`, `ConnectionCoordinator.addCrossSideTrigger` are
all package-private.

- [ ] **Step 4: `breaking-change` label**

Confirm the label is present on SDK-375 (it was added when the ticket was reopened for this revision, since
`Njams.start()`'s observable behavior changes for the receiver-only-failure case). Do not remove it.

- [ ] **Step 5: Logging accuracy & no-flood sweep**

Re-read every touched log statement and adjacent Javadoc/comment in `AbstractReceiver`, `NjamsSender`,
`SenderPool`, `ConnectionCoordinator`, `Receiver`, `Njams` — confirm none still describes the reverted
coordinator-sharing model (grep for "shared coordinator", "share this sender group's", "same coordinator
instance", "shared with the sender group" across these six files; every remaining hit must describe the current,
independent-coordinators-plus-one-trigger model, not the old shared-instance model). Confirm no per-attempt/
per-poll log was introduced anywhere (spot-check `AbstractReceiver.reconnect()`'s loop and `SenderPool
.triggerConnectionCheck()` — the latter logs nothing itself, it only calls existing `onException` paths which
already have their own established, non-flooding logging).

- [ ] **Step 6: Self-review against the spec**

Go through `docs/superpowers/specs/2026-07-02-sdk-375-sender-lifecycle-design.md`'s revised §3 decisions
(D1.1-D1.7) and confirm each holds against the actual current code, not just this plan's intent:
- **D1.1/D1.2** (independent coordinators, per-side scope) — ✅ via Task 2/3.
- **D1.3** (independent fate, one deliberate cross-check) — ✅ via Task 1/3 (`ConnectionCoordinator
  .addCrossSideTrigger`, `NjamsSender.wireReceiver`).
- **D1.4** (setting scope narrowed to sender) — ✅ via Task 4/6.
- **D1.5** (contracts may change; pre-release API removed without deprecation) — ✅ throughout.
- **D1.6** (`start()` depends only on the sender) — ✅ via Task 4.
- **D1.7** (cross-side trigger, symmetric, transport-uniform) — ✅ via Task 1/3/5.

- [ ] **Step 7: Jira — transition the ticket**

Per `CLAUDE.md`: transition `SDK-375` to its resolved/done state and clear the assignee. Post a closing comment
(brief: resolved, root cause/summary of this revision, no implementation detail) ending with the required
`_Generated by Claude Code_` signature. Re-verify the `breaking-change` label is still present as part of this
transition.

- [ ] **Step 8: Finalizing commit**

```bash
git add -A
git commit -m "SDK-375 #comment Decouple sender/receiver connection criticality; add cross-side verification"
```

> **Do not use `git add -A` blindly if the working tree has unrelated uncommitted changes at the time this step
> runs** (e.g. a local, unrelated `pom.xml` version-string experiment has been present throughout this ticket's
> history) — check `git status` first and stage only files this plan actually touched if anything unrelated is
> present.

---

## Self-Review (plan author)

**Spec coverage:** every decision in the revised design spec's §3 (D1.1-D1.7) has a task: independent coordinators
(Task 2/3), the cross-side trigger (Task 1/3, tested end-to-end in Task 5), the sender-only setting scope (Task
4/6), `start()` depending only on the sender (Task 4), the new WARN/INFO wording (Task 2/5), and the dead-API
removal the user approved (Task 2/3).

**A real design gap was found and fixed during this self-review, not left in the plan:** the first draft of
Task 1/3 modeled the cross-side trigger as a single overwritable `Runnable` slot (`setCrossSideTrigger`). Two
independent problems with that shape were caught by re-reading the actual call sites rather than trusting the
design in the abstract:
1. With `njams.sdk.communication.shared=true` on HTTP, one shared sender pool can be wired to several different
   per-`Njams`-instance receivers — a single overwritable slot would silently lose all but the last-wired
   receiver's notification. Fixed by making it an add-only collection (`addCrossSideTrigger`), mirroring the
   `exceptionListeners` pattern already established in this exact file (`SenderPool`/`AbstractSender`) for the
   identical reason.
2. `Njams.java`'s existing, unchanged call pattern wires the *same* receiver twice in the normal startup path
   (`beginConnect()` at construction, `startReceiver()` at `start()`) — harmless for Part 3's old `set`-based
   sharing, but a real double-registration (and double `onException(...)` firing on every single sender failure,
   in the default non-shared case) once the trigger is add-only. Fixed by removing the now-purposeless early
   `wireReceiver` call from `beginConnect()` (Task 4) rather than adding de-duplication logic — the call only
   existed to prepare a coordinator consultation that Task 2 already deletes.

Both fixes are reflected in Task 1/3/4's code and Javadoc as written above, not left as follow-up notes.

**Placeholder scan:** two notes remain deliberately open for the implementer, both naming the exact problem to
solve rather than hand-waving: `SenderPoolTest.addCrossSideTriggerAllowsMultipleTriggersToAllFireOnTheSameFailure`
and `NjamsSenderTest.wireReceiverBidirectionallyTriggersOnExceptionOnBothSides` both need a real
`AbstractSender`/`TestSender` fixture driven through a successful connect before the failing `reconnect(...)`
call, because a mock or a never-connected sender short-circuits `coordinator.shouldReconnect()` before reaching
`beginReconnect()` — this is a concrete fixture-sequencing detail for the implementer to resolve by reading
`SenderStartupSpecTest`'s established pattern, not an unresolved design question.

**Type consistency:** `addCrossSideTrigger(Runnable)` is named identically across `ConnectionCoordinator`,
`AbstractReceiver`, and `SenderPool`, and is add-only everywhere (no `remove`/`clear`, matching
`exceptionListeners`'s existing shape). `triggerConnectionCheck()` is defined once (`SenderPool`) and consumed
once (inside `NjamsSender.wireReceiver`'s registered lambda). `startReceiver()` (no-arg, `void`) consistently
replaces `startReceiver(boolean)` (was returning `boolean`) at its one call site in `start()`, and is now also
`wireReceiver`'s only call site (Task 4 removes the other one in `beginConnect()`).
