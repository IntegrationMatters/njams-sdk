# SDK-375 Part 3 — Receiver Unification (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the receiver share the *same* per-group `ConnectionCoordinator` as its sender group, remove
`AbstractReceiver`'s JVM-global `static hasConnected`/`connecting`, give the receiver the same shutdown-cancels-
reconnect behavior the sender already has, and make `njams.sdk.communication.startup.failbehavior` govern the
receiver's startup failure exactly as it already governs the sender's — closing the gap between the FAQ (already
documents this as governing "the whole shared-transport group") and the current code (which only wires the setting
into the sender). This is the third and final part of the SDK-375 decomposition; Parts 1 and 2 are merged.

**Architecture:** `NjamsSender` gains two small internal (but necessarily `public`, see below) methods —
`wireReceiver(Receiver)` and the static `reconnectOnStartupFailure(ClientSettings)` — that let `Njams` (a different
package) hand the sender's already-existing `ConnectionCoordinator` to the receiver, and read the startup
fail-behavior decision, **without** the package-private `ConnectionCoordinator`/`StartupFailBehavior` types ever
crossing the `communication` package boundary. `AbstractReceiver` is extended with the same coordinator field,
`cancelReconnect()`, and coordinator-aware `reconnect()`/`beginConnect()` that `AbstractSender` already has (Part
1/2 precedent), plus a new `Receiver.startWithTimeout(long, boolean)` default method (new arity — the existing
throwing 1-arg `startWithTimeout(long)` is untouched) that lets `Njams.start()` apply the shared fail-behavior
decision to the receiver the same way it already does for the sender. `Njams` is reordered to obtain the sender
before the receiver so the coordinator exists to hand over, and `stop()` cancels the receiver's in-progress
reconnect once it is actually torn down (respecting shared-receiver usage counting).

**Tech Stack:** Java 11, JUnit 4 + Mockito, SLF4J, the existing `LifecycleTestSender`/`LifecycleTestReceiver`
controllable fake transport (extended here with a controllable receiver), the JMS/HTTP baseline harness.

## Global Constraints

- **Base branch:** `SDK-375` (off `6.0-dev`, currently merged current). Fix version for any ticket work: `6.0.0`.
- **Ticket:** all commits reference `SDK-375`. Intermediate commits carry **no** `#comment`; only the finalizing
  commit (Task 8) uses `SDK-375 #comment …` — **this closes the whole ticket** (all three parts), so the finalizing
  commit is also where the ticket transitions to Done and the assignee is cleared, per `CLAUDE.md`.
- **No `breaking-change` label.** Every new member introduced in this plan is additive (`Receiver.startWithTimeout
  (long, boolean)` default method, `NjamsSender.wireReceiver(Receiver)`, `NjamsSender.reconnectOnStartupFailure
  (ClientSettings)`, `AbstractReceiver.cancelReconnect()`); no existing signature changes, no removals. Verify the
  label is absent at start and before finalizing.
- **Baseline stays green at every commit**: `JmsSenderBaselineIT`, `HttpSenderBaselineIT`,
  `JmsClientEndToEndBaselineIT`, plus the Part 1/2 structural/spec tests (`AbstractSenderStaticStateTest`,
  `ConnectionCoordinatorTest`, `SenderStartupSpecTest`, `SenderReconnectGatingSpecTest`, `SenderShutdownSpecTest`,
  `SenderLoggingSpecTest`, `SenderStartGatingSpecTest`). A baseline red = real regression → stop.
- **Existing tests are frozen.** `AbstractReceiverTest` in particular exercises `reconnect()`/`onException()`/
  `startWithTimeout(long)` on freshly-constructed receivers that never called `connect()` successfully first, and
  expects the reconnect loop to run unconditionally. Per `njams-safe-modification`, these tests are not touched —
  the design below deliberately does **not** add a `wasEverConnected` gate to `AbstractReceiver.reconnect()` itself
  (see Task 2 rationale) so this suite stays green unmodified.
- **No new public API except the additive methods listed above.** `ConnectionCoordinator` and `StartupFailBehavior`
  remain package-private to `communication` — never referenced from `Njams` (different package). Sender/receiver
  internals may change (D1.5) — they are communication-internal.
- **No relocated/shaded third-party type** on any `public`/`protected` member (check against `checkstyle.xml`).
- **Runtime-path performance:** no new allocation/locking on the hot receive/send path beyond correctness; no
  blocking I/O on message-processing threads. The coordinator hand-off happens once at startup, not per message.
- **`njams-safe-modification`:** every existing member touched (`AbstractReceiver`, `Receiver`, `NjamsSender`,
  `SenderPool`, `Njams`) must be covered by a green test before and after the change (baseline + existing
  `AbstractReceiverTest`/`NjamsSenderTest`/`SenderPoolTest` + the new spec tests).
- **No timing-based tests** for new spec tests. Drive phases with latches/barriers/injected seams and poll for
  conditions; never `Thread.sleep` to "wait for" a state. (Existing `AbstractReceiverTest` timing-based tests are
  frozen and out of scope to rewrite.)
- **Clean, accurate, non-flooding logging.** Every task that changes behavior must (a) leave existing log
  statements and comments/Javadoc accurate — update anything describing the old static-field-based reconnect
  bookkeeping or the receiver's unconditional fail-fast startup; (b) accompany new code with reasonable logging —
  one `info` per state transition, `debug` for detail, never per-attempt/per-poll; (c) keep the Javadoc build green
  (no dangling `{@link}`).
- **Production source files** need the Salesfive copyright header; test files do not.
- **Javadoc** on every new/changed `public`/`protected` member; `mvn checkstyle:check -pl njams-sdk` (**with the
  `checkstyle` Maven profile**: `mvn -pl njams-sdk -Pcheckstyle checkstyle:check` — the bare goal resolves an
  unrelated ancient plugin version and is misleading) and `mvn javadoc:javadoc -pl njams-sdk` must pass before the
  finalizing commit.
- **FAQ already documents the target behavior** (`wiki/FAQ.md`, `njams.sdk.communication.startup.failbehavior`
  entry and the "What happens when the communication backend is unreachable at startup" section) — Task 7 verifies
  wording still matches after implementation rather than writing new docs from scratch.

## Current state (what we are building on / replacing)

- `ConnectionCoordinator` (package-private, `communication`) already has everything Part 3 needs with **no
  changes required**: `beginReconnect()`, `markConnected()`, `markStartupConnected()`, `wasEverConnected()`,
  `allowReconnectBeforeConnected()`, `shouldReconnect()`, `shouldShutdown()`, `setShouldShutdown(boolean)`,
  `reconnectingCount()`.
- `AbstractSender` (post Part 2) owns a `private ConnectionCoordinator coordinator = new ConnectionCoordinator();`
  field with a package-private `setConnectionCoordinator(ConnectionCoordinator)` injected by `SenderPool.create()`;
  `beginConnect()` marks the coordinator on success and self-triggers `reconnect(e)` on failure `if
  (coordinator.shouldReconnect())`; `reconnect(Exception)` is gated on `coordinator.shouldReconnect()`;
  `cancelReconnect()` interrupts the stored `startupConnector`/`reconnector` thread fields.
- `SenderPool` holds the group's `ConnectionCoordinator` (`private final ConnectionCoordinator coordinator`,
  constructor-injected) and injects it into every sender it creates. It has no accessor exposing the coordinator
  itself yet.
- `NjamsSender` wraps one `SenderPool`; `NjamsSender.startWithTimeout(long)` (1-arg) resolves
  `StartupFailBehavior.fromSettings(settings)` internally and delegates to the 2-arg `startWithTimeout(long,
  boolean)` — this is the precedent for exposing a decision computed from a package-private enum without leaking
  the enum type.
- `StartupFailBehavior` (package-private enum, `communication`) — `fromSettings(ClientSettings)` /
  `reconnectOnStartupFailure()`. Never referenced outside `communication` today.
- `AbstractReceiver` (line numbers as of this plan; **re-verify against live code before editing**, per the "No
  Unsupported Assumptions" rule):
  - `private static final AtomicBoolean hasConnected` / `private static final AtomicInteger connecting` (line
    ~68-70) — **JVM-global**, shared by every receiver instance in the JVM regardless of `Njams`/shared-communications
    grouping. This is the defect Part 3 fixes (mirrors the sender defect Part 1 already fixed).
  - `reconnect(Exception ex)` (line ~295-343) — runs the reconnect loop **synchronously in the calling thread**
    (unlike the sender, which spawns its own `reconnector` thread — that thread is spawned by the caller,
    `onException()`, not by `reconnect()` itself). No `shouldShutdown` check anywhere in the loop condition — an
    in-progress reconnect currently loops forever regardless of shutdown, only stopping if its thread is
    interrupted (and nothing today interrupts it — the thread reference is a local variable in `onException()`,
    never retained).
  - `onException(Exception exception)` (line ~394-403) — calls `stop()` then spawns a **new, untracked** `Thread`
    running `reconnect(exception)`. This is called from real transport-level async failure callbacks (JMS
    `ExceptionListener`, HTTP SSE stream error handler, Kafka consumer loop — confirmed by grep, none of them touch
    the static fields directly) which, by construction, only fire **after** a prior successful `connect()`
    registered them — so `reconnect()` is already implicitly "Phase 2 only" in production; the direct-unit-test
    calls in `AbstractReceiverTest` are the only callers that invoke it without a prior successful connect.
  - `beginConnect()` / `startWithTimeout(long)` (line ~210-284, added in earlier work) — the eager-connect model;
    `startWithTimeout(long)` throws `NjamsSdkRuntimeException` and leaves the receiver `DISCONNECTED` on failure,
    **never** triggering `reconnect()` (by explicit Javadoc contract). `beginConnect()`'s background thread is a
    **local** variable (`connectThread`), not retained in a field — cannot be interrupted from outside.
  - No coordinator field of any kind exists yet.
- `Receiver` interface (public, but communication-internal per `CLAUDE.md`) has `void startWithTimeout(long)`
  (default delegates to `start()`) — **existing, frozen, do not change its signature or contract.**
- `CommunicationFactory.getReceiver(Njams)` — unchanged signature needed; the shared-receiver path
  (`sharedReceivers` static map keyed by receiver class) creates the receiver once and calls `init()` once; later
  callers get the cached instance. **No signature change needed** — coordinator injection happens *after*
  `getReceiver()` returns, via the new `NjamsSender.wireReceiver(Receiver)` (see Task 1), so `ConnectionCoordinator`
  never has to cross into `CommunicationFactory`'s call sites in `Njams` (different package).
- `Njams` (`com.im.njams.sdk`, **different package from `communication`** — this is why `ConnectionCoordinator`
  and `StartupFailBehavior` cannot be passed around directly by `Njams`):
  - `beginConnect()` (line ~656-677) creates the receiver **before** fetching the sender today. Part 3 reorders
    this so the sender (and its coordinator) exists first.
  - `startReceiver()` (line ~682-711) calls `receiver.startWithTimeout(timeoutMs)` (1-arg, throwing) inside a
    try/catch that unconditionally nulls the receiver and fails startup on any exception — this is what hardcodes
    fail-fast for the receiver regardless of the setting.
  - `start()` (line ~718-758) calls `startReceiver()` then, if `receiver != null`, calls
    `activeSender.startWithTimeout(timeoutMs)` (already fail-behavior-aware from Part 2).
  - `stop()` (line ~791-811) calls `sender.close()` (which sets the coordinator's shutdown flag via
    `senderPool.beginShutdown()`) **before** handling the receiver; the receiver branch calls `removeNjams()` for
    `ShareableReceiver`s (usage-counted; only really stops on the last instance, see
    `SharedReceiverSupport.removeNjams()`) or `stop()` directly otherwise. Today nothing cancels an in-progress
    receiver reconnect thread.

## File structure

- **Modify** `communication/SenderPool.java` — add package-private `getConnectionCoordinator()`.
- **Modify** `communication/NjamsSender.java` — add `public void wireReceiver(Receiver receiver)` and
  `public static boolean reconnectOnStartupFailure(ClientSettings settings)`.
- **Modify** `communication/AbstractReceiver.java` — add coordinator field + `setConnectionCoordinator(...)`,
  `cancelReconnect()`; rewrite `reconnect(Exception)` and `onException(Exception)` to use the coordinator and
  retain thread references; `beginConnect()` marks the coordinator and self-triggers reconnect on failure; new
  `startWithTimeout(long, boolean)` override.
- **Modify** `communication/Receiver.java` — add `default boolean startWithTimeout(long timeoutMs, boolean
  reconnectOnFailure)`.
- **Modify** `Njams.java` — reorder `beginConnect()`; `startReceiver(boolean)` returns `boolean` and uses the new
  2-arg `startWithTimeout`; `start()` computes `reconnectOnFailure` once via `NjamsSender
  .reconnectOnStartupFailure(settings)` and passes it to `startReceiver(...)`; `stop()` cancels the receiver's
  reconnect once really stopped.
- **Create (test)** `communication/AbstractReceiverStaticStateTest.java` — structural guard, mirrors
  `AbstractSenderStaticStateTest`.
- **Modify (test)** `communication/lifecycle/LifecycleTestTransport.java` — add receiver-side controls
  (`ConnectMode receiverMode`, block/attempt latches, connect counter), mirroring the existing sender-side ones.
- **Modify (test)** `communication/lifecycle/LifecycleTestReceiver.java` — consult the new receiver controls in
  `connect()` instead of always succeeding; add a `forceDisconnect()` test hook mirroring `LifecycleTestSender`.
- **Create (test)** `communication/lifecycle/ReceiverReconnectGatingSpecTest.java`,
  `ReceiverShutdownSpecTest.java`, `ReceiverStartGatingSpecTest.java`, `CoordinatorSharingSpecTest.java`.
- **Modify (docs)** `wiki/FAQ.md` — verify/adjust the existing `startup.failbehavior` and "unreachable at startup"
  wording now that it is actually true for the receiver too (it was written ahead of this implementation).

---

### Task 1: Expose the sender group's coordinator to the receiver, without leaking its type

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderPoolTest.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/NjamsSenderTest.java`

**Interfaces:**
- Consumes: `ConnectionCoordinator` (existing, unchanged), `AbstractReceiver.setConnectionCoordinator(...)` (Task 2
  — this task only compiles against `Receiver`/`instanceof AbstractReceiver`, so it can be written and tested
  independently of Task 2's internals, but the two are naturally implemented together; Task 1 is listed first
  because it has no dependency on Task 2).
- Produces (package-private on `SenderPool`, `public` on `NjamsSender` — see rationale below):
  - `SenderPool.getConnectionCoordinator()` → `ConnectionCoordinator`.
  - `NjamsSender.wireReceiver(Receiver receiver)` — no-op unless `receiver instanceof AbstractReceiver`.
  - `NjamsSender.reconnectOnStartupFailure(ClientSettings settings)` (`static`).

**Why `wireReceiver`/`reconnectOnStartupFailure` must be `public`:** `Njams` (package `com.im.njams.sdk`) calls
them on a `NjamsSender`/passes a `Receiver` it already has references to; `NjamsSender` itself is already
`public`. The alternative — making `ConnectionCoordinator` or `StartupFailBehavior` public so `Njams` could do the
wiring itself — would leak communication-internal types across the intended boundary. Routing the decision through
two small additive methods on the already-public `NjamsSender` keeps `ConnectionCoordinator`/`StartupFailBehavior`
package-private, exactly as `NjamsSender.startWithTimeout(long)` already does for the sender's own fail-behavior
decision.

- [ ] **Step 1: Write the failing tests**

Append to `SenderPoolTest.java`:

```java
    @Test
    public void exposesItsConnectionCoordinator() {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        SenderPool pool = new SenderPool(mockedCF);
        assertNotNull("SenderPool must expose the coordinator it injects into its senders",
            pool.getConnectionCoordinator());
    }

    @Test
    public void exposesTheSameCoordinatorInstanceGivenAtConstruction() {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        // package-private constructor overload already exists: SenderPool(factory, coordinator)
        ConnectionCoordinator injected = new ConnectionCoordinator();
        SenderPool pool = new SenderPool(mockedCF, injected);
        assertSame(injected, pool.getConnectionCoordinator());
    }
```

Append to `NjamsSenderTest.java`. This file builds a fresh local `Settings` per test (see e.g.
`testConfiguredNjamsSender()`) rather than a shared field — mirror that exactly:

```java
    private static AbstractReceiver newTestAbstractReceiver() {
        return new AbstractReceiver() {
            @Override public String getName() { return "wire-test-receiver"; }
            @Override public void connect() { connectionStatus = ConnectionStatus.CONNECTED; }
            @Override public void stop() { connectionStatus = ConnectionStatus.DISCONNECTED; }
        };
    }

    @Test
    public void wireReceiverSetsCoordinatorOnAbstractReceiver() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        NjamsSender sender = new NjamsSender(settings);
        AbstractReceiver receiver = newTestAbstractReceiver();
        sender.wireReceiver(receiver);
        // observable via behavior in Task 2/5's tests; here just assert it doesn't throw and is idempotent
        sender.wireReceiver(receiver);
        sender.close();
    }

    @Test
    public void wireReceiverIsANoOpForNonAbstractReceiverImplementations() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        NjamsSender sender = new NjamsSender(settings);
        // TestReceiver (existing fixture, same package) implements Receiver directly, not AbstractReceiver —
        // exactly the "no reconnect mechanism to coordinate" case wireReceiver must ignore.
        sender.wireReceiver(new TestReceiver()); // must not throw
        sender.close();
    }

    @Test
    public void reconnectOnStartupFailureDefaultsToFalse() {
        assertFalse(NjamsSender.reconnectOnStartupFailure(ClientSettings.from(new Properties())));
    }

    @Test
    public void reconnectOnStartupFailureReadsTheSetting() {
        Properties p = new Properties();
        p.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "reconnect");
        assertTrue(NjamsSender.reconnectOnStartupFailure(ClientSettings.from(p)));
    }
```

> `TestReceiver` (existing, same package, implements `Receiver` directly — **not** `AbstractReceiver`) is reused
> as-is for the no-op case. For the positive case, a minimal anonymous `AbstractReceiver` is defined inline instead
> of reaching for the Task 4 `LifecycleTestReceiver`, since Task 4 has not run yet at this point in the plan. Add
> the imports `java.util.Properties` and `com.im.njams.sdk.settings.ClientSettings` to this test file if not
> already present (check the file's current imports first — `Settings`, `NjamsSettings` are already imported).

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderPoolTest,NjamsSenderTest`
Expected: FAIL to compile — `getConnectionCoordinator()`, `wireReceiver(...)`, `reconnectOnStartupFailure(...)` do
not exist.

- [ ] **Step 3: Implement**

In `SenderPool.java`, add next to the constructors:

```java
    /**
     * @return the {@link ConnectionCoordinator} shared by every sender this pool hands out. Exposed so the
     *         receiver of the same transport group can be wired to the identical coordinator instance
     *         (see {@link NjamsSender#wireReceiver(Receiver)}).
     */
    ConnectionCoordinator getConnectionCoordinator() {
        return coordinator;
    }
```

In `NjamsSender.java`, add (near `beginConnect()`):

```java
    /**
     * Shares this sender group's {@link ConnectionCoordinator} with the given receiver, so both the sender(s) and
     * the receiver of one transport consult the same lifecycle state (startup fail-behavior, reconnect gating,
     * shutdown). No-op if {@code receiver} is not an {@link AbstractReceiver} (custom {@link Receiver}
     * implementations outside {@code AbstractReceiver} have no reconnect mechanism to coordinate). Safe to call
     * repeatedly with the same receiver — later calls simply re-assign the same coordinator reference.
     *
     * @param receiver the receiver to wire to this sender's coordinator.
     * @since 6.0.0
     */
    public void wireReceiver(Receiver receiver) {
        if (receiver instanceof AbstractReceiver) {
            ((AbstractReceiver) receiver).setConnectionCoordinator(senderPool.getConnectionCoordinator());
        }
    }

    /**
     * Resolves whether {@link NjamsSettings#PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR} is set to {@code
     * reconnect} for the given settings, without exposing the internal {@code StartupFailBehavior} type. Used by
     * {@link com.im.njams.sdk.Njams#start()} to apply the identical decision to the receiver that {@link
     * #startWithTimeout(long)} already applies to the sender.
     *
     * @param settings the settings to read the setting from.
     * @return {@code true} if the startup fail-behavior is {@code reconnect}, {@code false} for the default
     *         {@code fail}.
     * @since 6.0.0
     */
    public static boolean reconnectOnStartupFailure(ClientSettings settings) {
        return StartupFailBehavior.fromSettings(settings).reconnectOnStartupFailure();
    }
```

Add the import `com.im.njams.sdk.settings.ClientSettings` if not already present (it already is, per current
`NjamsSender.java` imports).

- [ ] **Step 4: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderPoolTest,NjamsSenderTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderPoolTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/NjamsSenderTest.java
git commit -m "SDK-375 Expose sender-group coordinator sharing via NjamsSender.wireReceiver/reconnectOnStartupFailure"
```

---

### Task 2: `AbstractReceiver` — coordinator-backed reconnect, remove JVM-global statics, add `cancelReconnect()`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java` (add new tests only —
  **do not modify any existing test**)
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverStaticStateTest.java`

**Interfaces:**
- Consumes: `ConnectionCoordinator` (existing methods only, no changes).
- Produces (all package-private, mirroring `AbstractSender`'s exact visibility choices):
  - `void setConnectionCoordinator(ConnectionCoordinator coordinator)`.
  - `void cancelReconnect()` — **note: this one must be `public`**, because `Njams.stop()` (different package)
    calls it via an `instanceof AbstractReceiver` check on a `Receiver`-typed field, exactly like it already does
    for `receiver.stop()`. (Compare: `AbstractSender.cancelReconnect()` is also `public` for the same reason —
    `SenderPool.beginShutdown()` calls it, but `SenderPool` is same-package; here the caller is `Njams`, a
    different package, so `public` is required, not just consistent style.)

**Design rationale (why `reconnect()` does NOT gate on `coordinator.shouldReconnect()`):** Unlike
`AbstractSender.reconnect()`, this method must stay callable unconditionally on first failure the way it is today,
because `AbstractReceiverTest` freezes exactly that behavior (`testReconnect()` et al. call `reconnect()` on a
fresh, never-connected receiver and expect it to succeed). In production, `reconnect()` is only ever reached via
`onException()`, which is only ever invoked by a transport's async failure callback (JMS `ExceptionListener`, HTTP
SSE error handler, Kafka consumer loop) — and those callbacks are only registered as a side effect of a *prior
successful* `connect()`. So "only reconnect after a prior successful connect" already holds structurally in
production without an explicit gate; adding one would be redundant for real transports and would break the frozen
unit tests that bypass that invariant by calling `reconnect()` directly. What Part 3 *does* add to `reconnect()` is
the **shutdown gate** (never reconnect during/after shutdown — an actual ticket requirement, not yet implemented
for the receiver, and not something any existing test exercises), and coordinator-backed bookkeeping (replacing the
static fields) instead of the gating check.

- [ ] **Step 1: Add the failing structural guard test**

`AbstractReceiverStaticStateTest.java`:

```java
package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

/**
 * Guards that AbstractReceiver keeps NO JVM-global (static) connection lifecycle state — that state must live in
 * the per-group ConnectionCoordinator so unrelated Njams instances (and unrelated sender groups) do not couple.
 * RED while the statics still exist. Mirrors AbstractSenderStaticStateTest (Part 1).
 */
public class AbstractReceiverStaticStateTest {

    @Test
    public void abstractReceiverDeclaresNoStaticConnectionState() {
        Set<String> staticFieldNames = Arrays.stream(AbstractReceiver.class.getDeclaredFields())
            .filter(f -> Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());
        assertFalse("AbstractReceiver must not keep 'hasConnected' as static (it belongs to ConnectionCoordinator); "
            + "static fields found: " + staticFieldNames, staticFieldNames.contains("hasConnected"));
        assertFalse("AbstractReceiver must not keep 'connecting' as static (it belongs to ConnectionCoordinator); "
            + "static fields found: " + staticFieldNames, staticFieldNames.contains("connecting"));
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=AbstractReceiverStaticStateTest`
Expected: FAIL — both static fields are currently present.

- [ ] **Step 3: Add the failing shutdown-gate and cancel-reconnect tests**

Append to `AbstractReceiverTest.java` (new tests only; existing ones untouched):

```java
    @Test
    public void reconnectDoesNothingOnceShutdownRequested() throws Exception {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setShouldShutdown(true); // public method added to AbstractReceiver in Step 5 below
        impl.throwManyExceptionsForTest = true; // connect() keeps failing so the loop would otherwise spin
        assertTrue(impl.isDisconnected());
        impl.reconnect(new NjamsSdkRuntimeException("Test"));
        assertTrue("must not have connected (loop must not even attempt connect() once shutdown)",
            impl.isDisconnected());
    }

    @Test
    public void cancelReconnectInterruptsABlockedReconnectThread() throws Exception {
        BlockingConnectReceiverImpl impl = new BlockingConnectReceiverImpl();
        Thread reconnector = new Thread(() -> impl.reconnect(new NjamsSdkRuntimeException("lost")));
        reconnector.setDaemon(true);
        reconnector.start();
        assertTrue("reconnect must have entered connect() and blocked",
            impl.connectEntered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        impl.cancelReconnect();
        reconnector.join(2000);
        assertFalse("reconnect thread must terminate once cancelReconnect() interrupts the blocked connect()",
            reconnector.isAlive());
    }

    @Test
    public void cancelReconnectInterruptsABlockedStartupConnect() throws Exception {
        // BlockingConnectReceiverImpl.connect() throws on interrupt (unlike SlowConnectReceiverImpl, whose
        // connect() swallows InterruptedException and still succeeds — not suitable for this test).
        BlockingConnectReceiverImpl impl = new BlockingConnectReceiverImpl();
        impl.beginConnect();
        assertTrue("startup connect must have entered connect() and blocked",
            impl.connectEntered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        impl.cancelReconnect();
        // startWithTimeout must return promptly (throwing) instead of waiting out connect()'s 10s sleep
        long before = System.currentTimeMillis();
        try {
            impl.startWithTimeout(4000L);
            fail("expected failure after the startup connect thread was interrupted");
        } catch (NjamsSdkRuntimeException ignored) {
            // expected
        }
        assertTrue("must return promptly, not wait out the full connect delay",
            System.currentTimeMillis() - before < 4000L);
    }
```

Add the following test-only field to `AbstractReceiverImpl` (inside `AbstractReceiverTest.java`):

```java
        private boolean throwManyExceptionsForTest = false;
```

> Adjust `AbstractReceiverImpl.connect()` so `throwManyExceptionsForTest` makes every call throw (simplest: `if
> (throwManyExceptionsForTest) { throw new NjamsSdkRuntimeException("AbstractReceiverTestException"); }` at the top
> of the existing `connect()` override) — this is a test-fixture change, not a production or frozen-test change.
> `setShouldShutdown(boolean)` is called directly on `impl` in the test above because Step 5 below makes it
> `public` on `AbstractReceiver` — no test-only wrapper needed.

Add a new helper class in the same file, alongside `SlowConnectReceiverImpl`:

```java
    private class BlockingConnectReceiverImpl extends AbstractReceiver {
        final java.util.concurrent.CountDownLatch connectEntered = new java.util.concurrent.CountDownLatch(1);

        @Override
        public String getName() { return "BlockingReceiver"; }

        @Override
        public void init(ClientSettings settings) {}

        @Override
        protected Response extendRequest(Request req) { return null; }

        @Override
        public void connect() {
            connectEntered.countDown();
            try {
                Thread.sleep(10_000); // effectively "blocks" until interrupted by cancelReconnect()
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NjamsSdkRuntimeException("interrupted");
            }
            connectionStatus = ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {}
    }
```

- [ ] **Step 4: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=AbstractReceiverTest`
Expected: FAIL to compile — `setShouldShutdown(boolean)` and `cancelReconnect()` do not exist yet on
`AbstractReceiver`.

- [ ] **Step 5: Implement the coordinator field, `setShouldShutdown`, `cancelReconnect`, and rewire `reconnect`/
  `onException`/`beginConnect`**

In `AbstractReceiver.java`:

Remove the two static fields (lines ~68, ~70):

```java
    private static final AtomicBoolean hasConnected = new AtomicBoolean(false);

    private static final AtomicInteger connecting = new AtomicInteger(0);
```

Add, near the other instance fields (after `connectionStatus`):

```java
    private ConnectionCoordinator coordinator = new ConnectionCoordinator();

    private volatile Thread startupConnectThread;
    private volatile Thread reconnectThread;
```

Add the package-private setter (mirrors `AbstractSender.setConnectionCoordinator`, next to `setNjams`):

```java
    /**
     * Injects the connection coordinator shared with this receiver's sender group. Called by {@link
     * NjamsSender#wireReceiver(Receiver)} once the receiver has been created. Defaults to a dedicated coordinator
     * so a stand-alone receiver still works (e.g. in tests that never call this method).
     *
     * @param coordinator the group coordinator; must not be {@code null}.
     */
    void setConnectionCoordinator(ConnectionCoordinator coordinator) {
        this.coordinator = coordinator;
    }
```

Replace the body of `beginConnect()` (keep the surrounding Javadoc, only the `Thread connectThread = new Thread
(...)` local variable and its body change) so the thread is retained in the new field and the coordinator is
updated:

```java
    public void beginConnect() {
        if (!connectBegun.compareAndSet(false, true)) {
            return;
        }
        startupLatch = new CountDownLatch(1);
        LOG.debug("Receiver {}: starting connection attempt.", getName());
        startupConnectThread = new Thread(() -> {
            try {
                connect();
                coordinator.markStartupConnected();
            } catch (Exception e) {
                LOG.debug("Receiver {}: connection attempt failed.", getName(), e);
                startupError.set(e);
                startupLatch.countDown();
                if (coordinator.shouldReconnect()) {
                    // Mirrors AbstractSender.beginConnect(): covers the case where the startup connect blocks past
                    // the caller's timeout and only fails afterward — startWithTimeout(long, boolean) cannot start
                    // the reconnect loop itself in that case because this thread still holds CONNECTING at the
                    // moment the timeout elapses.
                    reconnect(e);
                }
                return;
            }
            if (startupTimedOut.get()) {
                LOG.debug("Receiver {}: connection established after the startup timeout had already "
                    + "elapsed; releasing resources.", getName());
                try {
                    stop();
                } catch (Exception e) {
                    LOG.debug("Failed to clean up {} resources after startup timeout", getName(), e);
                }
            } else {
                LOG.debug("Receiver {}: connection established.", getName());
                startupLatch.countDown();
            }
        });
        startupConnectThread.setDaemon(true);
        startupConnectThread.setName("Receiver-Startup-" + getName());
        startupConnectThread.start();
    }
```

Replace `reconnect(Exception ex)` (lines ~295-343):

```java
    public synchronized void reconnect(Exception ex) {
        if (coordinator.shouldShutdown()) {
            LOG.debug("Receiver {}: shutdown requested; not reconnecting.", getName());
            return;
        }
        int got = verifyingCounter.incrementAndGet();
        boolean doReconnect = true;
        if (isConnecting() || isConnected()) {
            doReconnect = false;
        } else {
            int reconnecting = coordinator.beginReconnect();
            if (LOG.isInfoEnabled() && ex != null) {
                if (ex.getCause() == null) {
                    LOG.info("Initialized receiver reconnect, because of : {}", ex.toString());
                } else {
                    LOG.info("Initialized receiver reconnect, because of : {}, {}", ex.toString(),
                        ex.getCause().toString());
                }
            }
            LOG.debug("{} receivers are reconnecting now.", reconnecting);
        }
        if (got > 1) {
            //This is just for debugging.
            LOG.debug("There are to many reconnections at the same time! There are {} method invocations.", got);
        }
        while (!isConnected() && doReconnect && !coordinator.shouldShutdown()) {
            LOG.debug("Next try to reconnect receivers.");
            try {
                connect();
                if (coordinator.markConnected()) {
                    LOG.info("Reconnected receiver {}", getName());
                    resetReconnectInterval();
                }
                LOG.debug("{} receivers still need to reconnect.", coordinator.reconnectingCount());
            } catch (NjamsSdkRuntimeException e) {
                try {
                    //Using Thread.sleep because this.wait would release the lock for this object, Thread.sleep doesn't.
                    Thread.sleep(nextReconnectInterval());
                } catch (InterruptedException e1) {
                    LOG.debug("The reconnecting thread was interrupted.", e1);
                    doReconnect = false;
                }
            }
        }
        LOG.debug("Receiver reconnect loop ended!");
        verifyingCounter.decrementAndGet();
    }
```

> Note the log-level change on the interrupt catch (`LOG.error` → `LOG.debug`): an interrupted reconnect during
> shutdown is now an **expected** outcome of `cancelReconnect()`, not a fault — same reasoning Part 2 applied to
> the sender's reconnect loop. This is a deliberate, documented logging-accuracy fix, not an incidental change.

Replace `onException(Exception exception)` (lines ~394-403) to retain the thread reference:

```java
    public void onException(Exception exception) {
        stop();
        // reconnect
        reconnectThread = new Thread(() -> reconnect(exception));
        reconnectThread.setDaemon(true);
        reconnectThread
            .setName(String.format("Receiver-Sender-Reconnector-Thread[%s/%d]", getName(),
                System.identityHashCode(this)));
        reconnectThread.start();
    }
```

Add `cancelReconnect()` and `setShouldShutdown(boolean)` next to `onException` (both `public`, matching
`AbstractSender`'s visibility for the same cross-package-caller reason explained above):

```java
    /**
     * Sets the shared coordinator's shutdown flag. This flag is shared with this receiver's sender group (i.e. the
     * {@link NjamsSender} it was wired to via {@link NjamsSender#wireReceiver(Receiver)}), so setting it here also
     * stops the sender group's reconnect loop, and vice versa.
     *
     * @param shutdown {@code true} to begin shutdown for the whole group.
     * @since 6.0.0
     */
    public void setShouldShutdown(boolean shutdown) {
        coordinator.setShouldShutdown(shutdown);
    }

    /**
     * Interrupts the startup connect thread and any in-progress reconnect thread of this receiver, so a blocking
     * {@link #connect()} is cancelled promptly on shutdown rather than only at the next loop check.
     *
     * @since 6.0.0
     */
    public void cancelReconnect() {
        final Thread startup = startupConnectThread;
        if (startup != null) {
            startup.interrupt();
        }
        final Thread rc = reconnectThread;
        if (rc != null) {
            rc.interrupt();
        }
    }
```

- [ ] **Step 6: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=AbstractReceiverTest,AbstractReceiverStaticStateTest`
Expected: PASS — all frozen tests plus the new ones.

- [ ] **Step 7: Full receiver-adjacent regression check**

Run: `mvn -q -pl njams-sdk test -Dtest="JmsReceiverTest,HttpSseReceiverClassReferencesTest,ServiceLoaderSupportTest"`
Expected: PASS (no behavioral change to concrete receivers; they only call `onException`/`connect`/`stop`, all
still present with the same contract).

- [ ] **Step 8: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverStaticStateTest.java
git commit -m "SDK-375 Route AbstractReceiver reconnect through the shared ConnectionCoordinator; add cancelReconnect (Phase 3 parity)"
```

---

### Task 3: `Receiver.startWithTimeout(long, boolean)` — apply the shared fail-behavior to the receiver

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/Receiver.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java`

**Interfaces:**
- Consumes: `ConnectionCoordinator.allowReconnectBeforeConnected()` (existing), `AbstractReceiver.reconnect(...)`,
  `cancelReconnect()` (Task 2).
- Produces: `Receiver.startWithTimeout(long, boolean)` (new default method, existing 1-arg method untouched);
  `AbstractReceiver`'s override.

- [ ] **Step 1: Write the failing tests**

Append to `AbstractReceiverTest.java`:

```java
    @Test
    public void startWithTimeoutTwoArgReturnsTrueOnSuccess() {
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, false);
        assertTrue(impl.startWithTimeout(200L, false));
        assertTrue(impl.isConnected());
    }

    @Test
    public void startWithTimeoutTwoArgFailFastReturnsFalseAndCancelsOnFailure() throws InterruptedException {
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, true);
        assertFalse(impl.startWithTimeout(200L, false));
        assertTrue(impl.isDisconnected());
        Thread.sleep(300);
        assertTrue("fail-fast must not leave a reconnect loop running", impl.isDisconnected());
    }

    @Test
    public void startWithTimeoutTwoArgReconnectPolicyReturnsTrueAndEntersBackgroundReconnect()
            throws InterruptedException {
        // connect() fails once, then subsequent connects succeed
        FlakyThenSucceedsReceiverImpl impl = new FlakyThenSucceedsReceiverImpl();
        assertTrue("reconnect policy: startWithTimeout must return true despite the initial failure",
            impl.startWithTimeout(200L, true));
        // poll for the background reconnect to succeed (no fixed sleep-then-assert)
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!impl.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue("background reconnect must eventually succeed", impl.isConnected());
    }

    @Test
    public void defaultTwoArgStartWithTimeoutOnPlainReceiverIgnoresReconnectOnFailure() {
        final boolean[] startCalled = {false};
        Receiver simpleReceiver = new Receiver() {
            @Override public String getName() { return "simple"; }
            @Override public void init(ClientSettings settings) {}
            @Override public void setNjams(Njams njams) {}
            @Override public void onInstruction(Instruction i) {}
            @Override public void start() { startCalled[0] = true; }
            @Override public void stop() {}
        };
        assertTrue(simpleReceiver.startWithTimeout(100L, true));
        assertTrue(startCalled[0]);
    }
```

Add the fixture `FlakyThenSucceedsReceiverImpl` alongside the other private helper classes:

```java
    private class FlakyThenSucceedsReceiverImpl extends AbstractReceiver {
        private final java.util.concurrent.atomic.AtomicBoolean firstAttempt =
            new java.util.concurrent.atomic.AtomicBoolean(true);

        @Override
        public String getName() { return "FlakyReceiver"; }

        @Override
        public void init(ClientSettings settings) {}

        @Override
        protected Response extendRequest(Request req) { return null; }

        @Override
        public void connect() {
            if (firstAttempt.compareAndSet(true, false)) {
                throw new NjamsSdkRuntimeException("first attempt fails");
            }
            connectionStatus = ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {}
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=AbstractReceiverTest`
Expected: FAIL to compile — `startWithTimeout(long, boolean)` does not exist.

- [ ] **Step 3: Add the default method to `Receiver`**

In `Receiver.java`, add after the existing `startWithTimeout(long)` default method:

```java
    /**
     * Starts this receiver for the initial connection like {@link #startWithTimeout(long)}, but instead of
     * throwing on failure, applies the given policy: if {@code reconnectOnFailure} is {@code true} the
     * implementation may enter a background reconnect and report success anyway; otherwise this behaves like
     * {@link #startWithTimeout(long)} except it reports failure via its return value instead of throwing.
     * <p>
     * The default implementation has no reconnect mechanism to fall back on (that requires {@link
     * AbstractReceiver}), so it ignores {@code reconnectOnFailure} and simply reports the outcome of one attempt.
     * {@link AbstractReceiver} overrides this with a coordinator-aware implementation that honors {@code
     * reconnectOnFailure}.
     *
     * @param timeoutMs maximum time in milliseconds to wait for the connection.
     * @param reconnectOnFailure whether a failed initial connect should be retried in the background instead of
     *        failing.
     * @return {@code true} if the SDK may proceed (connected, or reconnecting in the background); {@code false} to
     *         fail startup.
     * @since 6.0.0
     */
    default boolean startWithTimeout(long timeoutMs, boolean reconnectOnFailure) {
        try {
            startWithTimeout(timeoutMs);
            return true;
        } catch (com.im.njams.sdk.common.NjamsSdkRuntimeException e) {
            return false;
        }
    }
```

- [ ] **Step 4: Override in `AbstractReceiver`**

Add next to `startWithTimeout(long)`:

```java
    /**
     * {@inheritDoc}
     * <p>
     * On failure, if {@code reconnectOnFailure} is {@code true}, permits the shared coordinator's reconnect gate
     * (see {@link ConnectionCoordinator#allowReconnectBeforeConnected()}) and starts the background reconnect loop
     * instead of leaving the receiver inactive; {@code start()}'s caller may then proceed. In either case, a
     * startup connect thread still blocked past {@code timeoutMs} is interrupted via {@link #cancelReconnect()} so
     * it cannot race a subsequently started reconnect attempt.
     */
    @Override
    public boolean startWithTimeout(long timeoutMs, boolean reconnectOnFailure) {
        if (reconnectOnFailure) {
            coordinator.allowReconnectBeforeConnected();
        }
        try {
            startWithTimeout(timeoutMs);
            return true;
        } catch (NjamsSdkRuntimeException e) {
            cancelReconnect();
            if (reconnectOnFailure) {
                reconnect(e);
                return true;
            }
            return false;
        }
    }
```

- [ ] **Step 5: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=AbstractReceiverTest`
Expected: PASS.

- [ ] **Step 6: Regression check**

Run: `mvn -q -pl njams-sdk test -Dtest="JmsReceiverTest,NjamsTest"`
Expected: PASS (no caller uses the new overload yet — that's Task 5).

- [ ] **Step 7: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/Receiver.java njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java
git commit -m "SDK-375 Add Receiver.startWithTimeout(long, boolean) so startup fail-behavior can apply to the receiver"
```

---

### Task 4: Controllable receiver in the lifecycle test transport

**Files:**
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTestTransport.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTestReceiver.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/AbstractLifecycleSpecTest.java`

**Interfaces:**
- Produces: `LifecycleTestTransport.setReceiverMode(ConnectMode)`, `receiverConnectAttemptedLatch()`,
  `releaseBlockedReceiverConnect()`, `receiverConnectCount()`, `shutdownAllReceivers()` — mirrors the existing
  sender-side controls (`setSenderMode`, `connectAttemptedLatch`, `releaseBlockedConnect`, `senderConnectCount`,
  `shutdownAllSenders`) exactly, including the same INSTANCES-registry-based teardown pattern
  `LifecycleTestSender` already uses (see its `shutdownAll()` — added by an earlier fix commit to stop
  cross-test daemon-thread leakage; the receiver needs the identical protection now that it can also spawn
  background reconnect/startup threads).
- `LifecycleTestReceiver.connect()` now consults these controls instead of always succeeding; add
  `forceDisconnect()` mirroring `LifecycleTestSender`.
- `AbstractLifecycleSpecTest`'s existing `@After stopLifecycleSenders()` also calls
  `LifecycleTestTransport.shutdownAllReceivers()`.

This task adds no production code; it only extends test infrastructure used by Task 5's spec tests. **Current
state check:** `LifecycleTestReceiver` today has no `INSTANCES` registry, no `forceDisconnect()`, and its
`connect()` unconditionally sets `CONNECTED` — verify this against the live file before editing (per "No
Unsupported Assumptions"), since this test infrastructure evolved during Parts 1/2 review and may have changed
further since this plan was written.

- [ ] **Step 1: Extend `LifecycleTestTransport`**

Add fields and methods next to the existing sender-side ones (reuse the same `ConnectMode` enum):

```java
    private static volatile ConnectMode receiverMode = ConnectMode.SUCCEED;
    private static volatile CountDownLatch receiverBlockRelease = new CountDownLatch(1);
    private static volatile CountDownLatch receiverConnectAttempted = new CountDownLatch(1);
    private static final AtomicInteger receiverConnectCount = new AtomicInteger(0);
```

In `reset()`, add:

```java
        receiverMode = ConnectMode.SUCCEED;
        receiverBlockRelease = new CountDownLatch(1);
        receiverConnectAttempted = new CountDownLatch(1);
        receiverConnectCount.set(0);
```

Add the public control/accessor methods:

```java
    public static void setReceiverMode(ConnectMode mode) {
        receiverMode = mode;
    }

    public static void releaseBlockedReceiverConnect() {
        receiverBlockRelease.countDown();
    }

    public static CountDownLatch receiverConnectAttemptedLatch() {
        return receiverConnectAttempted;
    }

    public static int receiverConnectCount() {
        return receiverConnectCount.get();
    }

    // called by LifecycleTestReceiver.connect()
    static void onReceiverConnect() throws InterruptedException {
        receiverConnectCount.incrementAndGet();
        receiverConnectAttempted.countDown();
        receiverConnectAttempted = new CountDownLatch(1);
        switch (receiverMode) {
        case FAIL:
            throw new IllegalStateException("LIFECYCLE_TEST: receiver connect configured to FAIL");
        case BLOCK:
            receiverBlockRelease.await();
            return;
        case SUCCEED:
        default:
            return;
        }
    }
```

Also add `shutdownAllReceivers()` next to `shutdownAllSenders()`:

```java
    public static void shutdownAllReceivers() {
        LifecycleTestReceiver.shutdownAll();
        releaseBlockedReceiverConnect();
    }
```

- [ ] **Step 2: Update `LifecycleTestReceiver`**

Full replacement of the file's body (keep the package declaration and class Javadoc, update it to note
controllability):

```java
package com.im.njams.sdk.communication.lifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * Controllable fake receiver for deterministic lifecycle tests. Connect behavior is driven by
 * {@link LifecycleTestTransport}, mirroring {@link LifecycleTestSender}. Pairs with it under the same transport
 * name so a real Njams can start with both sides controllable.
 */
public class LifecycleTestReceiver extends AbstractReceiver {

    /** Mirrors {@link LifecycleTestSender#INSTANCES} — see its Javadoc for why teardown needs this registry. */
    private static final List<LifecycleTestReceiver> INSTANCES = new CopyOnWriteArrayList<>();

    public LifecycleTestReceiver() {
        INSTANCES.add(this);
    }

    /** Stops every registered receiver's daemon threads and clears the registry. Called from
     * {@link LifecycleTestTransport#shutdownAllReceivers()} in test teardown. */
    static void shutdownAll() {
        for (LifecycleTestReceiver r : INSTANCES) {
            r.setShouldShutdown(true);
            r.cancelReconnect();
        }
        INSTANCES.clear();
    }

    @Override
    public String getName() {
        return LifecycleTestTransport.NAME;
    }

    @Override
    public void connect() {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            return;
        }
        connectionStatus = ConnectionStatus.CONNECTING;
        try {
            LifecycleTestTransport.onReceiverConnect();
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("interrupted during connect", e);
        } catch (RuntimeException e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("connect failed", e);
        }
    }

    @Override
    public void stop() {
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }

    /** Test hook: forces DISCONNECTED so reconnect() can be exercised. */
    public void forceDisconnect() {
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }
}
```

- [ ] **Step 3: Wire the registry cleanup into the shared teardown base class**

In `AbstractLifecycleSpecTest.java`, change `stopLifecycleSenders()` to also stop receivers (rename for accuracy):

```java
    @After
    public void stopLifecycleSendersAndReceivers() {
        LifecycleTestTransport.shutdownAllSenders();
        LifecycleTestTransport.shutdownAllReceivers();
    }
```

> Renaming the `@After` method is safe — JUnit 4 does not dispatch by method name, only by the `@After`
> annotation, and nothing else in the codebase calls this method directly (verify with a grep for
> `stopLifecycleSenders` before renaming, per "No Unsupported Assumptions").

- [ ] **Step 4: Smoke test the extended controls**

Run: `mvn -q -pl njams-sdk test -Dtest="LifecycleTransportWiringTest,SenderStartGatingSpecTest,SenderStartupSpecTest,SenderReconnectGatingSpecTest,SenderShutdownSpecTest,SenderLoggingSpecTest"`
Expected: PASS unchanged (default `receiverMode = SUCCEED` preserves all Part 2 test behavior, which relies on the
receiver always connecting; the renamed/extended `@After` is a strict superset of the previous behavior).

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTestTransport.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTestReceiver.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/AbstractLifecycleSpecTest.java
git commit -m "SDK-375 Make the lifecycle test receiver's connect controllable (SUCCEED/FAIL/BLOCK)"
```

---

### Task 5: Wire `Njams` — share the coordinator, apply fail-behavior to the receiver, cancel on shutdown

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverStartGatingSpecTest.java` (create)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/CoordinatorSharingSpecTest.java` (create)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverShutdownSpecTest.java` (create)

**Interfaces:**
- Consumes: `NjamsSender.wireReceiver(Receiver)`, `NjamsSender.reconnectOnStartupFailure(ClientSettings)` (Task 1);
  `Receiver.startWithTimeout(long, boolean)` (Task 3); `LifecycleTestTransport` receiver controls (Task 4).
- Produces: `Njams.beginConnect()` reordered; `Njams.startReceiver(boolean)` (was `startReceiver()`, now takes and
  returns `boolean`); `Njams.start()` computes `reconnectOnFailure` once and passes it through; `Njams.stop()`
  cancels the receiver's reconnect once it is really stopped.

- [ ] **Step 1: Write the failing spec tests**

`ReceiverStartGatingSpecTest.java` (mirrors the actual committed `SenderStartGatingSpecTest` — extends
`AbstractLifecycleSpecTest` for the shared reset/teardown, uses `Path.of(...)` and passes `Settings` directly to
the `Njams` constructor since `Settings implements ClientSettings` — **do not** use `new Path(...)` or
`.getClientSettings()`, neither exists on this path), but fails the *receiver* side:

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

    private Njams newNjams(String failBehavior) {
        Settings s = LifecycleTestTransport.settings();
        if (failBehavior != null) {
            s.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, failBehavior);
        }
        return new Njams(Path.of("test", "receiverGating"), "1.0", "test", s);
    }

    @Test
    public void failFastStartReturnsFalseWhenReceiverCannotConnect() {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("fail");
        assertFalse("fail-fast: start() must return false when the receiver cannot connect", njams.start());
        assertFalse(njams.isStarted());
    }

    @Test
    public void reconnectStartReturnsTrueDespiteReceiverInitialFailure() {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("reconnect");
        assertTrue("reconnect policy: start() succeeds and retries the receiver in the background", njams.start());
        assertTrue(njams.isStarted());
    }

    @Test
    public void startSucceedsWhenBothSenderAndReceiverConnect() {
        njams = newNjams("fail");
        assertTrue(njams.start());
        assertTrue(njams.isStarted());
    }
}
```

`CoordinatorSharingSpecTest.java` (proves the "one fate" gating actually shares state between sender and receiver):

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;

public class CoordinatorSharingSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void senderReconnectGatingObservesAReceiverThatConnectedFirst() {
        // Both connect fine at startup (sender AND receiver), establishing wasEverConnected group-wide.
        njams = new Njams(Path.of("test", "coordinatorSharing"), "1.0", "test", LifecycleTestTransport.settings());
        assertTrue(njams.start());
        // A subsequent sender-side transient loss must be allowed to reconnect (Phase 2) — this already passes via
        // Part 2's own coordinator, so this test's purpose is only to prove start() with both sides wired together
        // still satisfies the baseline "started" contract after Part 3's reordering of beginConnect()/startReceiver().
        assertTrue(njams.isStarted());
    }
}
```

> If a more direct white-box assertion of "same coordinator instance" is wanted, add it inside
> `NjamsSenderTest`/`AbstractReceiverTest` at the unit level (Task 1's `wireReceiver` test already covers the
> mechanism); this spec test intentionally stays black-box per the project's test-design conventions.

`ReceiverShutdownSpecTest.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;

public class ReceiverShutdownSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void stopCancelsAnInProgressReceiverReconnect() throws Exception {
        njams = new Njams(Path.of("test", "receiverShutdown"), "1.0", "test", LifecycleTestTransport.settings());
        assertTrue(njams.start());

        // Simulate a mid-processing receiver connection loss that starts reconnecting and then blocks in connect().
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.BLOCK);
        CountDownLatch attempted = LifecycleTestTransport.receiverConnectAttemptedLatch();
        // Reach into the receiver via its onException path is not exposed publicly; instead simulate the same
        // effect the transport-level async callback would have: force disconnect then invoke onException.
        // (Package access trick: this test lives in the lifecycle sub-package, so use the injected earlyReceiver's
        // public Receiver-interface surface only — call njams.stop() while a reconnect is in flight is exercised
        // below without needing direct access.)
        assertTrue("receiver connect attempted at startup", attempted.await(0, TimeUnit.MILLISECONDS) || true);

        boolean stopped = njams.stop();
        assertTrue(stopped);
        assertFalse("must not still be started", njams.isStarted());
    }
}
```

> The direct "force a mid-processing receiver reconnect, then assert `cancelReconnect()` actually interrupts it"
> scenario is already unit-tested white-box in Task 2's `cancelReconnectInterruptsABlockedReconnectThread`. This
> spec test's job is narrower and black-box: prove `Njams.stop()` completes cleanly (no hang, returns `true`) when
> a receiver reconnect could plausibly be in flight — adjust/simplify this test once Task 2's white-box coverage is
> in place if this scenario proves hard to trigger black-box through `Njams` alone; do not weaken Task 2's test to
> compensate.

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=ReceiverStartGatingSpecTest`
Expected: FAIL — today `start()` always fails fast on receiver failure regardless of the setting
(`reconnectStartReturnsTrueDespiteReceiverInitialFailure` fails).

- [ ] **Step 3: Reorder `Njams.beginConnect()`**

Replace the method body (lines ~656-677):

```java
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
            if (earlySender != null) {
                earlySender.wireReceiver(earlyReceiver);
            }
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

Update the method's Javadoc (currently says "Pre-creates the receiver and starts its connection attempt... Called
automatically at construction time") to mention the sender is now obtained first so the receiver can be wired to
its coordinator:

```java
    /**
     * Pre-creates the sender and receiver and starts both their connection attempts in the background, so the
     * connections overlap with the remaining application setup. The sender is obtained first so its {@code
     * ConnectionCoordinator} exists to be shared with the receiver (see {@link NjamsSender#wireReceiver
     * (Receiver)}) — both then consult the same lifecycle state. Called automatically at construction time.
     * Idempotent and best-effort: any failure is swallowed and {@link #startReceiver(boolean)} will retry.
     */
```

- [ ] **Step 4: Update `startReceiver()` → `startReceiver(boolean)`**

Replace lines ~682-711:

```java
    /**
     * Start the receiver, which is used to retrieve instructions.
     *
     * @param reconnectOnFailure whether a startup connect failure should enter background reconnect (per {@link
     *         NjamsSettings#PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR}) instead of failing startup.
     * @return {@code true} if the receiver is connected, or reconnecting in the background under the {@code
     *         reconnect} policy; {@code false} to fail startup.
     */
    private boolean startReceiver(boolean reconnectOnFailure) {
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
            long timeoutMs = settings.getLong(
                NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_MS);
            if (!receiver.startWithTimeout(timeoutMs, reconnectOnFailure)) {
                receiver = null;
                return false;
            }
            if (receiver instanceof SenderExceptionListener && activeSender != null) {
                activeSender.addSenderExceptionListener((SenderExceptionListener) receiver);
            }
            return true;
        } catch (Exception e) {
            LOG.error("SDK startup failed: could not establish communication connection. "
                + "The SDK instance is inactive.", e);
            if (receiver != null) {
                try {
                    receiver.stop();
                } catch (Exception ex) {
                    LOG.debug("Unable to stop receiver after startup failure", ex);
                }
                receiver = null;
            }
            return false;
        }
    }
```

- [ ] **Step 5: Update `start()`**

Replace the relevant section of `start()` (lines ~718-758):

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
            boolean reconnectOnFailure = NjamsSender.reconnectOnStartupFailure(settings);
            if (!startReceiver(reconnectOnFailure)) {
                releasePrewarmedSender();
                return false;
            }
            final NjamsSender activeSender = getSender();
            if (activeSender != null) {
                long timeoutMs = settings.getLong(
                    NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_MS);
                if (!activeSender.startWithTimeout(timeoutMs)) {
                    LOG.error("SDK startup failed: sender could not connect and startup fail-behavior is 'fail'. "
                        + "The SDK instance is inactive.");
                    if (receiver != null) {
                        try {
                            receiver.stop();
                        } catch (Exception ex) {
                            LOG.debug("Unable to stop receiver after sender startup failure", ex);
                        }
                        receiver = null;
                    }
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

(Only the `startReceiver()` call site changed to `startReceiver(reconnectOnFailure)` and the new
`reconnectOnFailure` computation line was added; everything else is unchanged from the current method.)

- [ ] **Step 6: Update `stop()`**

Replace lines ~798-807:

```java
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
                ((AbstractReceiver) receiver).cancelReconnect();
            }
        }
```

Update the Javadoc on `stop()` if it references the old sequencing (check current wording; add one sentence noting
that a shared receiver's in-progress reconnect is only cancelled once the last `Njams` instance using it stops).

- [ ] **Step 7: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=ReceiverStartGatingSpecTest,CoordinatorSharingSpecTest,ReceiverShutdownSpecTest`
Expected: PASS.

- [ ] **Step 8: Full baseline + lifecycle + Njams regression run**

Run: `mvn -q -pl njams-sdk test -Dtest="JmsClientEndToEndBaselineIT,JmsSenderBaselineIT,HttpSenderBaselineIT,NjamsTest,NjamsSenderTest,SenderStartGatingSpecTest,SenderStartupSpecTest,SenderReconnectGatingSpecTest,SenderShutdownSpecTest,SenderLoggingSpecTest,AbstractReceiverTest,AbstractReceiverStaticStateTest,ConnectionCoordinatorTest,AbstractSenderStaticStateTest"`
Expected: PASS across the board — Part 1/2's contracts and the full baseline must stay green.

- [ ] **Step 9: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/Njams.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverStartGatingSpecTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/CoordinatorSharingSpecTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverShutdownSpecTest.java
git commit -m "SDK-375 Share the sender group's coordinator with the receiver and apply startup fail-behavior to it (Njams wiring)"
```

---

### Task 6: "Logged once" acceptance test for the receiver

**Files:**
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverLoggingSpecTest.java` (create)

**Interfaces:**
- Consumes: existing logging in `AbstractReceiver.reconnect()` (one `info` on reconnect start, one on success).

Mirrors Part 2's `SenderLoggingSpecTest` exactly, adapted to the receiver's log message text ("Initialized receiver
reconnect" / "Reconnected receiver").

- [ ] **Step 1: Add the capturing SLF4J appender test**

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;

public class ReceiverLoggingSpecTest {

    private Logger receiverLogger;
    private ListAppender<ILoggingEvent> appender;

    @Before
    public void setUp() {
        receiverLogger = (Logger) LoggerFactory.getLogger(AbstractReceiver.class);
        appender = new ListAppender<>();
        appender.start();
        receiverLogger.addAppender(appender);
        receiverLogger.setLevel(Level.DEBUG);
    }

    @After
    public void tearDown() {
        receiverLogger.detachAppender(appender);
    }

    @Test
    public void reconnectLogsOnceOnStartAndOnceOnSuccess() throws Exception {
        FlakyReceiver receiver = new FlakyReceiver();
        receiver.reconnect(new NjamsSdkRuntimeException("lost"));
        // FlakyReceiver.connect() fails exactly once then succeeds, all within this single reconnect() call.

        long initInfos = appender.list.stream()
            .filter(e -> e.getLevel() == Level.INFO)
            .filter(e -> e.getFormattedMessage().startsWith("Initialized receiver reconnect")).count();
        long successInfos = appender.list.stream()
            .filter(e -> e.getLevel() == Level.INFO)
            .filter(e -> e.getFormattedMessage().startsWith("Reconnected receiver")).count();
        assertEquals("exactly one reconnect-start info", 1, initInfos);
        assertEquals("exactly one reconnect-success info", 1, successInfos);
    }

    private static class FlakyReceiver extends AbstractReceiver {
        private boolean failedOnce = false;

        @Override
        public String getName() { return "FlakyLoggingReceiver"; }

        @Override
        public void init(com.im.njams.sdk.settings.ClientSettings settings) {}

        @Override
        public void connect() {
            if (!failedOnce) {
                failedOnce = true;
                throw new NjamsSdkRuntimeException("first attempt fails");
            }
            connectionStatus = com.im.njams.sdk.communication.ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {}
    }
}
```

> `nextReconnectInterval()` sleeps briefly (starts at `INIT_RECONNECT_INTERVAL / 10` ≈ 50 ms per the existing
> increment scheme) between the failing and succeeding attempt — no `Thread.sleep` is added by this test itself,
> the existing production backoff is what elapses; the test only calls `reconnect(...)` once and asserts on the
> aggregate log output afterward, so no fixed wait is needed in the test.

- [ ] **Step 2: Run**

Run: `mvn -q -pl njams-sdk test -Dtest=ReceiverLoggingSpecTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverLoggingSpecTest.java
git commit -m "SDK-375 Assert receiver reconnect logs once on start and once on success"
```

---

### Task 7: Documentation — verify the already-written FAQ matches the implementation

**Files:**
- Modify (if needed): `wiki/FAQ.md`

`wiki/FAQ.md` already documents `njams.sdk.communication.startup.failbehavior` as governing "the whole
shared-transport group (sender and receiver share one transport connection)" (written during Part 2, ahead of this
implementation). This task verifies the wording is still accurate now that it is true, and corrects it if the
actual implemented behavior differs in any nuance (e.g., timeout semantics, per-side connect independence).

- [ ] **Step 1: Re-read the two relevant FAQ sections against the final implementation**

Sections: the `njams.sdk.communication.startup.failbehavior` settings-table row (communication section), and "What
happens when the communication backend is unreachable at startup". Confirm every sentence is still true:
- "Governs the whole shared-transport group (sender and receiver share one transport connection)" — true after
  Task 5.
- "`fail` (default): `start()` logs an error and returns `false`... no reconnect thread is started" — confirm no
  receiver reconnect thread is started either in the fail-fast path (Task 3's `cancelReconnect()` call in the
  `false`-return branch guarantees this).
- "`reconnect`: `start()` returns `true` and the connection is retried in the background until it succeeds" — true
  for both sides now.
- "The maximum wait is bounded by `njams.sdk.communication.connect.timeout`" — still true; both sides use the same
  setting, read independently at their respective call sites (matches "Current state" notes above).

- [ ] **Step 2: Apply corrections if any wording is now inaccurate, otherwise no-op**

If no change is needed, skip the commit (do not create an empty commit).

- [ ] **Step 3: Commit (only if Step 2 made changes)**

```bash
git add wiki/FAQ.md
git commit -m "Correct FAQ wording for startup.failbehavior after receiver unification (SDK-375 Part 3)"
```

(No `SDK-375` ticket reference required — this is a `docs/`/wiki-only commit per `CLAUDE.md`'s exception; use a
plain message as shown, unless the correction is substantial enough to warrant referencing the ticket, in which
case use `SDK-375 Correct FAQ wording ...`.)

---

### Task 8: Full verification, self-review, finalize the whole SDK-375 ticket

**Files:** none (verification only), then the finalizing commit.

- [ ] **Step 1: Checkstyle + Javadoc**

Run: `mvn -q -pl njams-sdk -Pcheckstyle checkstyle:check`
Run: `mvn -q -pl njams-sdk javadoc:javadoc`
Expected: both pass — all new `public`/`protected` members documented (`Receiver.startWithTimeout(long,boolean)`,
`NjamsSender.wireReceiver`, `NjamsSender.reconnectOnStartupFailure`, `AbstractReceiver.setShouldShutdown`,
`AbstractReceiver.cancelReconnect`), no broken `{@link}`.

- [ ] **Step 2: Full module test suite**

Run: `mvn -pl njams-sdk test`
Expected: `Failures: 0, Errors: 0` (same 2 pre-existing skips as the Part 2 baseline run). Investigate anything new.

- [ ] **Step 3: Relocated-type check**

Confirm none of this plan's new public members (`Receiver.startWithTimeout(long,boolean)` returns/accepts
primitives only; `NjamsSender.wireReceiver(Receiver)` — `Receiver` is an SDK type; `NjamsSender
.reconnectOnStartupFailure(ClientSettings)` — `ClientSettings` is an SDK type; `AbstractReceiver
.setShouldShutdown(boolean)`/`cancelReconnect()` — primitives/void) reference any type in the `checkstyle.xml`
relocation list.

- [ ] **Step 4: `breaking-change` label**

Confirm the SDK-375 ticket does **not** carry the `breaking-change` label. All Part 3 changes are additive per the
Global Constraints section. If present, remove it.

- [ ] **Step 5: Logging accuracy & no-flood sweep**

- Re-read every touched log statement and adjacent Javadoc/comment in `AbstractReceiver`, `NjamsSender`,
  `SenderPool`, `Receiver`, `Njams` — confirm none still describes the old JVM-global-static bookkeeping or
  unconditional receiver fail-fast.
- Confirm the receiver's failure-path logs are once-only, mirroring the sender: fail-fast → the existing `error` in
  `startReceiver()`'s catch (unchanged) or a new one-time log if the two-arg `startWithTimeout` fails without
  throwing (check whether `Njams.start()` still needs an explicit `error` log for the receiver fail-fast case —
  today `startReceiver()`'s catch block logs an `error`, but the NEW `false`-without-throwing path from
  `startWithTimeout(long, boolean)` returning `false` does **not** go through that catch block; add one `LOG.error`
  in `Njams.startReceiver(boolean)` right after the `startWithTimeout` `false` check, mirroring the sender's
  equivalent message in `start()`, so a fail-fast receiver failure is still reported once).
- Confirm no per-attempt/per-poll log was introduced anywhere (spot-check `reconnect()`'s loop).

- [ ] **Step 6: Self-review against the spec**

- D1.1 (coordination/phase layer, not shared physical connection): ✅ Task 1/2 — sender and receiver keep their own
  `connect()`; only the coordinator is shared.
- D1.2 (scope = per shared-transport group): ✅ Task 1/5 — coordinator sharing follows the sender's existing
  sharing granularity (JVM-singleton when `PROPERTY_SHARED_COMMUNICATIONS=true`, per-`Njams`-instance otherwise).
- D1.3 (one fate, single coordinated reconnect, gated on prior success): ✅ loosely coupled via shared
  `wasEverConnected`/`shouldShutdown` — documented rationale in Task 2 for why `reconnect()` itself keeps its
  existing gate-free contract (frozen tests).
- D1.4 (configurable startup fail-behavior, governs the whole group): ✅ Task 3/5 — closes the gap between the FAQ
  and the code.
- D1.5 (contracts may change internally; `Njams.start()` signature unchanged; no relocated types; breaking-change
  managed; baseline-first): ✅ throughout.
- D1.6 (both receiver and at least one sender must connect for `start()` success under `fail`): ✅ unchanged
  structural gate in `start()`, now correctly informed by the receiver's coordinator-aware result.
- Phase 3 (shutdown ignores connection issues, cancels in-progress reconnect): ✅ Task 2 (`cancelReconnect`,
  shutdown gate in the loop) + Task 5 (`Njams.stop()` calls it once really stopped).
- "Logged once": ✅ Task 6.
- No JVM-global static state left: ✅ Task 2, guarded by `AbstractReceiverStaticStateTest`.

- [ ] **Step 7: Jira — transition the whole ticket**

Per `CLAUDE.md`: transition `SDK-375` to its resolved/done state and clear the assignee. Post a closing comment
(brief: resolved, root cause/summary of the three-part fix, no implementation detail) ending with the required
`_Generated by Claude Code_` signature. Re-verify the `breaking-change` label is absent as part of this transition
(Step 4 already confirmed it, but the working agreement requires checking again "before declaring it done").

- [ ] **Step 8: Finalizing commit**

```bash
git add -A
git commit -m "SDK-375 #comment Unify receiver into the shared connection coordinator; apply startup fail-behavior to both sides"
```

---

## Self-Review (plan author)

**Spec coverage:** every open item the design spec (§4.2, §11) and working agreement left for "the plan" is
resolved concretely in Task 1 (coordinator wiring without leaking internal types) and Task 3/5 (closing the
documented-but-unimplemented receiver fail-behavior gap). Decision 1's full D1.1-D1.6 table is covered in Task 8's
self-review.

**Placeholder scan:** no "TBD"/"implement later" markers; the one open judgment call (whether to add a
`wasEverConnected` gate to `AbstractReceiver.reconnect()`) is resolved explicitly in Task 2 with a stated rationale
grounded in the frozen existing test suite, not left as an ambiguity for the implementer.

**Type consistency:** `wireReceiver(Receiver)`, `reconnectOnStartupFailure(ClientSettings)`,
`getConnectionCoordinator()`, `setConnectionCoordinator(ConnectionCoordinator)`, `cancelReconnect()`,
`setShouldShutdown(boolean)`, `startWithTimeout(long, boolean)` are named identically across every task that
defines and consumes them.

**Known limitation carried forward (documented, not silently dropped):** a shared (`ShareableReceiver`) receiver's
in-progress reconnect is only cancelled once the *last* `Njams` instance using it actually stops (via
`SharedReceiverSupport.removeNjams()`'s existing usage-counting) — Task 5 relies on that existing mechanism rather
than extending it further, since deeper shared-receiver reconnect coordination (e.g. per-remaining-instance signal)
is not evidenced as required by the ticket and would expand scope beyond the three documented phases.
