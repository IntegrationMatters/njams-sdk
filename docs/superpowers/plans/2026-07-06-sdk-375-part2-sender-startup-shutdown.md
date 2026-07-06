# SDK-375 Part 2 — Sender Startup & Shutdown Semantics (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the Part 1 `ConnectionCoordinator` foundation, implement the ticket's three sender-lifecycle phases — eager (early, overlapping) startup connect gated by a configurable fail-behavior, reconnect gated on prior success, and a shutdown that never reconnects and cancels any in-flight reconnect.

**Architecture:** Mirror the receiver's proven early-connect model on the sender side. The `Njams` constructor already calls `beginConnect()` to start the receiver connecting in the background so a slow connect overlaps application setup; Part 2 adds a symmetric sender `beginConnect()` right beside it. `Njams.start()` then awaits *one* pre-warmed sender within `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT` (concurrently with the receiver) and applies the new `njams.sdk.communication.startup.failbehavior` setting: `fail` (default) → `start()` returns `false`, no reconnect; `reconnect` → background reconnect, `start()` returns `true`. The swallowing `AbstractSender.startup()` (and its unconditional call from `CommunicationFactory.getSender()`) is removed; the connection lifecycle is driven explicitly by `beginConnect()` at startup and by the (now `wasEverConnected`-gated) reconnect path during processing. Shutdown sets the coordinator's shutdown flag *before* the executor drain and actively interrupts the reconnect/startup worker threads.

**Tech Stack:** Java 11, JUnit 4 + Mockito, SLF4J, the existing embedded-broker / JDK-HttpServer baseline harness, a new controllable in-process fake transport for deterministic lifecycle tests.

## Global Constraints

- **Base branch:** `SDK-375` (off `6.0-dev`). Fix version for any ticket work: `6.0.0`.
- **Ticket:** all commits reference `SDK-375`. Intermediate commits carry **no** `#comment`; only the finalizing commit uses `SDK-375 #comment …`.
- **No `breaking-change` label.** Decision (confirmed with user): gating `start()` on the sender is **not** an observable change, because the sender and receiver share the *same* transport — if the transport is reachable both connect; if it is not, the receiver already fails `start()` today. `Njams.start()`'s signature is unchanged. Verify the label is absent at start and before finalizing.
- **Baseline stays green at every commit** (`JmsSenderBaselineIT`, `HttpSenderBaselineIT`, `JmsClientEndToEndBaselineIT`) and the Part 1 `SenderInstanceIsolationIT` / `AbstractSenderStaticStateTest` stay green. A baseline red = real regression → stop.
- **No new public API except the one setting.** `PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR` is a new `public` key constant in `NjamsSettings` (additive, allowed). Its default value lives in the consuming class, **not** in `NjamsSettings` (project rule). Sender/receiver internals may change (D1.5) — they are communication-internal.
- **No relocated/shaded third-party type** on any `public`/`protected` member (check against `checkstyle.xml`).
- **Runtime-path performance:** no new allocation/locking on the hot `send` path beyond correctness; no blocking I/O on message threads. The startup connect runs on its own daemon thread (like the receiver), not on a message thread.
- **`njams-safe-modification`:** every existing member touched (`AbstractSender`, `NjamsSender`, `SenderPool`, `CommunicationFactory`, `Njams`, `ConnectionCoordinator`) must be covered by a green test before and after the change (baseline + the new spec tests).
- **No timing-based tests.** Drive phases with latches/barriers/injected seams and poll for conditions; never `Thread.sleep` to “wait for” a state.
- **Clean, accurate, non-flooding logging.** Every task that changes behavior must (a) leave existing log statements and code/Javadoc comments *accurate* — update or remove any that describe the old lifecycle (e.g. the removed `startup()` error log, `reconnect()`/`setShouldShutdown` Javadoc, the `CommunicationFactory` comment); (b) accompany new code with *reasonable* logging at the right level — a single `info` per state transition (startup connect result, reconnect start, reconnect success, shutdown), `debug` for per-instance detail, and **never** a per-attempt or per-message log on a retry/poll loop; (c) fix any dangling `{@link}`/`@see` to removed members (a hard Javadoc-build error). Log levels: transitions → `info`; routine/internal detail → `debug`; genuine failure the client must act on → `error` (once, not per retry). The "logged once" test (Task 8) and the Javadoc build (Task 10) enforce this.
- **Production source files** need the Salesfive copyright header; test files do not.
- **Javadoc** on every new/changed `public`/`protected` member; `mvn checkstyle:check -pl njams-sdk` and `mvn javadoc:javadoc -pl njams-sdk` must pass before the finalizing commit.

## Current state (what we are building on / replacing)

- `ConnectionCoordinator` (Part 1) owns `hasConnected`, `connecting` (reconnect count), `shouldShutdown`. It does **not** track "was ever connected" and has no reconnect-gating helper.
- `AbstractSender`:
  - `startup()` (line ~112) calls `connect()` and, on failure, **swallows** into `reconnect(e)` (infinite background loop) — the startup failure never reaches the client.
  - `reconnect(Exception)` (line ~161) guards on `isConnecting() || isConnected() || coordinator.shouldShutdown()` — **not** gated on prior success.
  - `doReconnect(Exception)` (line ~183) uses the coordinator counters; sets the per-instance `hasConnectionFailure` flag.
  - `send(...)` loop condition (line ~284) already reads `coordinator.shouldShutdown()`.
  - `setShouldShutdown(boolean)` (line ~360) delegates to the coordinator; the `reconnector` daemon thread is **not** interrupted on shutdown.
- `CommunicationFactory.getSender()` (line ~166-196) calls `newInstance.init(settings); newInstance.startup();` — every pooled sender auto-connects (swallowing failures) at creation.
- `NjamsSender`:
  - `init()` (line ~149) builds the `SenderPool` with a fresh `ConnectionCoordinator`.
  - `close()` (line ~215-241) runs `executor.shutdown()` + `awaitTermination(10s)` **before** `senderPool.declareShutdown()` sets the shutdown flag — the ordering bug that lets a failing final send spawn a reconnect.
- `SenderPool.declareShutdown()` (line ~161) sets pool `shutdown=true` and `setShouldShutdown(true)` on all senders; `create()` returns `null` once `shutdown`.
- `Njams`:
  - constructor (line ~209-222) calls `beginConnect()` (line ~656) → pre-creates the receiver and starts `AbstractReceiver.beginConnect()` in the background.
  - `startReceiver()` (line ~674) transfers the early receiver and calls `receiver.startWithTimeout(PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, default 30_000ms)`; `start()` (line ~710) returns `false` if `receiver == null`.
  - `getSender()` (line ~637) lazily creates the (possibly shared) `NjamsSender`.
  - `stop()` (line ~750) calls `sender.close()`.
- `AbstractReceiver.beginConnect()` / `startWithTimeout(long)` (lines ~210-284) are the **template** for the sender: a daemon connect thread + `CountDownLatch` + timeout; on late connect after timeout it releases resources.
- `NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT = "njams.sdk.communication.connect.timeout"` (line ~129); `Njams.DEFAULT_CONNECT_TIMEOUT_MS = 30_000L` (line ~74).
- Test transport: `TestSender` (NAME `TEST_COMMUNICATION`) is a no-op that delegates to an injected mock but does **not** override `connect()` — **not controllable**; a new controllable fake transport is required.

## File structure

- **Modify** `communication/ConnectionCoordinator.java` — add `wasEverConnected`, startup-connect marker, reconnect-before-connected flag, `shouldReconnect()`.
- **Modify** `communication/ConnectionCoordinatorTest.java` — cover the new state.
- **Create** `communication/StartupFailBehavior.java` — package-private enum (`FAIL` default | `RECONNECT`) + `fromSettings(ClientSettings)`.
- **Create** `communication/StartupFailBehaviorTest.java`.
- **Modify** `NjamsSettings.java` — add `PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR` key.
- **Modify** `communication/AbstractSender.java` — add `beginConnect()`, `awaitStartup(long)`, `cancelReconnect()`; mark coordinator connected on startup connect; remove `startup()`; gate `reconnect()` on `coordinator.shouldReconnect()`.
- **Modify** `communication/CommunicationFactory.java` — stop calling `startup()` in `getSender()`.
- **Modify** `communication/SenderPool.java` — add `allowReconnectBeforeConnected()` and `beginShutdown()` (coordinator shutdown flag + cancel reconnects, without blocking sender creation).
- **Modify** `communication/NjamsSender.java` — add `beginConnect()` and `startWithTimeout(long, boolean)`; set shutdown before the drain in `close()`.
- **Modify** `Njams.java` — start the sender connect early in the constructor's `beginConnect()`; in `start()` await the sender and apply the fail-behavior; keep `stop()` delegating to `sender.close()`.
- **Create (test)** `communication/lifecycle/LifecycleTestSender.java`, `LifecycleTestReceiver.java`, `LifecycleTestTransport.java` (controls + settings helper) + SPI registration files.
- **Create (test)** `communication/lifecycle/SenderStartupSpecTest.java`, `SenderReconnectGatingSpecTest.java`, `SenderShutdownSpecTest.java`, `SenderLoggingSpecTest.java`.
- **Modify (docs)** `njams-sdk-sample-client/src/main/resources/settings_full.properties`, `wiki/FAQ.md`.

---

### Task 1: Extend `ConnectionCoordinator` with startup/reconnect-gating state

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/ConnectionCoordinator.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/ConnectionCoordinatorTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces (all package-private):
  - `boolean markStartupConnected()` — records a successful *startup* connect (sets `hasConnected` and the sticky `wasEverConnected`), returns `true` on the disconnected→connected transition. Does **not** touch the reconnect counter.
  - `boolean markConnected()` — unchanged signature; now **also** sets the sticky `wasEverConnected`.
  - `boolean wasEverConnected()` — `true` once any connect (startup or reconnect) has succeeded.
  - `void allowReconnectBeforeConnected()` — permit reconnect even before the first successful connect (startup `reconnect` policy).
  - `boolean shouldReconnect()` — `!shouldShutdown() && (wasEverConnected() || reconnectBeforeConnected)`.

- [ ] **Step 1: Add the failing unit tests**

Append to `ConnectionCoordinatorTest.java`:

```java
    @Test
    public void freshCoordinatorHasNeverConnectedAndMustNotReconnect() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        assertFalse(c.wasEverConnected());
        assertFalse("no reconnect before a first successful connect", c.shouldReconnect());
    }

    @Test
    public void startupConnectMarksEverConnectedAndEnablesReconnect() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        assertTrue("first startup connect is the transition", c.markStartupConnected());
        assertTrue(c.wasEverConnected());
        assertTrue("reconnect allowed after a prior success", c.shouldReconnect());
        assertFalse("second markStartupConnected while connected is not a transition", c.markStartupConnected());
    }

    @Test
    public void reconnectSuccessAlsoRecordsEverConnected() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        c.beginReconnect();
        assertTrue(c.markConnected());
        assertTrue(c.wasEverConnected());
    }

    @Test
    public void allowReconnectBeforeConnectedEnablesReconnectWithoutPriorSuccess() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        assertFalse(c.shouldReconnect());
        c.allowReconnectBeforeConnected();
        assertTrue(c.shouldReconnect());
    }

    @Test
    public void shutdownDisablesReconnectEvenAfterConnecting() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        c.markStartupConnected();
        c.setShouldShutdown(true);
        assertFalse("shutdown wins over wasEverConnected", c.shouldReconnect());
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=ConnectionCoordinatorTest`
Expected: FAIL — `markStartupConnected`, `wasEverConnected`, `allowReconnectBeforeConnected`, `shouldReconnect` do not compile.

- [ ] **Step 3: Implement the new state**

In `ConnectionCoordinator.java` add fields after `shouldShutdown` (line ~40):

```java
    private volatile boolean wasEverConnected = false;
    private volatile boolean reconnectBeforeConnected = false;
```

Change `markConnected()` to record the sticky flag:

```java
    synchronized boolean markConnected() {
        connecting.decrementAndGet();
        wasEverConnected = true;
        return hasConnected.compareAndSet(false, true);
    }
```

Add the new methods (with Javadoc):

```java
    /**
     * Records a successful startup connect (see {@link AbstractSender#beginConnect()}): marks the group connected
     * and, stickily, that it has connected at least once. Unlike {@link #markConnected()} this does not touch the
     * reconnect counter, because a startup connect is not preceded by {@link #beginReconnect()}.
     *
     * @return {@code true} on the disconnected&rarr;connected transition, {@code false} if already connected.
     */
    synchronized boolean markStartupConnected() {
        wasEverConnected = true;
        return hasConnected.compareAndSet(false, true);
    }

    /** @return {@code true} once any connect (startup or reconnect) has succeeded for this group. */
    boolean wasEverConnected() {
        return wasEverConnected;
    }

    /**
     * Permits reconnect attempts even before the first successful connect. Set when the startup fail-behavior is
     * {@code reconnect} so a failed initial connect enters the background reconnect loop instead of failing fast.
     */
    void allowReconnectBeforeConnected() {
        reconnectBeforeConnected = true;
    }

    /**
     * @return {@code true} if a reconnect may proceed now: the group is not shutting down and it has either
     *         connected before (Phase 2) or been told to reconnect from startup (Phase 1 {@code reconnect} policy).
     */
    boolean shouldReconnect() {
        return !shouldShutdown() && (wasEverConnected || reconnectBeforeConnected);
    }
```

Update the class Javadoc first sentence to note it also owns the "was ever connected" and reconnect-policy state.

- [ ] **Step 4: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=ConnectionCoordinatorTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/ConnectionCoordinator.java njams-sdk/src/test/java/com/im/njams/sdk/communication/ConnectionCoordinatorTest.java
git commit -m "SDK-375 Add was-ever-connected and reconnect-gating state to ConnectionCoordinator"
```

---

### Task 2: Controllable in-process fake transport (test infrastructure)

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTestTransport.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTestSender.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTestReceiver.java`
- Modify: `njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.AbstractSender`
- Modify: `njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.Receiver`

**Interfaces:**
- Produces: a transport named `LIFECYCLE_TEST` whose **sender** connect is controllable (succeed / fail / block-until-released) and whose **receiver** connect always succeeds, plus `LifecycleTestTransport.settings()` returning a `Settings` selecting it and the in-memory config provider, and static controls to drive and observe connects deterministically (latches + counters, no sleeps).

This task adds no production code; verify only that the SPI wiring resolves.

- [ ] **Step 1: Create the controls + settings helper**

`LifecycleTestTransport.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.settings.Settings;

/** Test-only controls shared by {@link LifecycleTestSender} and {@link LifecycleTestReceiver}. */
public final class LifecycleTestTransport {

    public static final String NAME = "LIFECYCLE_TEST";

    /** How the next sender connect attempt behaves. */
    public enum ConnectMode { SUCCEED, FAIL, BLOCK }

    private static volatile ConnectMode senderMode = ConnectMode.SUCCEED;
    /** Released to let a BLOCK-ing connect proceed (to SUCCEED). Recreated by {@link #reset()}. */
    private static volatile CountDownLatch blockRelease = new CountDownLatch(1);
    /** Counts down once per sender connect entry (so tests can await that a connect was attempted). */
    private static volatile CountDownLatch connectAttempted = new CountDownLatch(1);
    private static final AtomicInteger senderConnectCount = new AtomicInteger(0);

    private LifecycleTestTransport() {
    }

    /** Resets all controls to the default (SUCCEED) state. Call in @Before. */
    public static void reset() {
        senderMode = ConnectMode.SUCCEED;
        blockRelease = new CountDownLatch(1);
        connectAttempted = new CountDownLatch(1);
        senderConnectCount.set(0);
    }

    public static void setSenderMode(ConnectMode mode) {
        senderMode = mode;
    }

    /** Lets a BLOCK-ing sender connect finish successfully. */
    public static void releaseBlockedConnect() {
        blockRelease.countDown();
    }

    /** @return a fresh latch that fires the next time a sender connect is attempted. */
    public static CountDownLatch connectAttemptedLatch() {
        return connectAttempted;
    }

    public static int senderConnectCount() {
        return senderConnectCount.get();
    }

    // called by LifecycleTestSender.connect()
    static void onSenderConnect() throws InterruptedException {
        senderConnectCount.incrementAndGet();
        connectAttempted.countDown();
        connectAttempted = new CountDownLatch(1);
        switch (senderMode) {
        case FAIL:
            throw new IllegalStateException("LIFECYCLE_TEST: connect configured to FAIL");
        case BLOCK:
            blockRelease.await();
            return;
        case SUCCEED:
        default:
            return;
        }
    }

    /** Settings selecting this transport with the in-memory configuration provider. */
    public static Settings settings() {
        Settings s = new Settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION, NAME);
        s.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, "memory");
        return s;
    }
}
```

`LifecycleTestSender.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.ConnectionStatus;

/** Controllable fake sender for deterministic lifecycle tests. Connect behavior is driven by
 * {@link LifecycleTestTransport}. */
public class LifecycleTestSender extends AbstractSender {

    @Override
    public String getName() {
        return LifecycleTestTransport.NAME;
    }

    @Override
    public synchronized void connect() throws NjamsSdkRuntimeException {
        if (isConnected()) {
            return;
        }
        setConnectionStatus(ConnectionStatus.CONNECTING);
        try {
            LifecycleTestTransport.onSenderConnect();
            setConnectionStatus(ConnectionStatus.CONNECTED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
            throw new NjamsSdkRuntimeException("interrupted during connect", e);
        } catch (RuntimeException e) {
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
            throw new NjamsSdkRuntimeException("connect failed", e);
        }
    }

    @Override
    protected void send(LogMessage msg, String clientSessionId) {
        // no-op: lifecycle tests assert on connection state, not payload delivery
    }

    @Override
    protected void send(ProjectMessage msg, String clientSessionId) {
        // no-op
    }

    @Override
    protected void send(TraceMessage msg, String clientSessionId) {
        // no-op
    }
}
```

`LifecycleTestReceiver.java` (always connects; enough to pass the receiver gate in `Njams.start()`):

```java
package com.im.njams.sdk.communication.lifecycle;

import java.util.Properties;

import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;

/** Fake receiver whose connect always succeeds — pairs with {@link LifecycleTestSender} under the same
 * transport name so a real Njams can start with a controllable sender. */
public class LifecycleTestReceiver extends AbstractReceiver {

    @Override
    public String getName() {
        return LifecycleTestTransport.NAME;
    }

    @Override
    public void connect() {
        connectionStatus = ConnectionStatus.CONNECTED;
    }

    @Override
    public void init(Properties properties) {
        // nothing to configure
    }

    @Override
    public void stop() {
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }
}
```

> Note: confirm `AbstractReceiver`'s abstract method set against the current source when implementing (method names/`init` parameter type). Match the existing `TestReceiver` for the exact overrides required; `connectionStatus` is the inherited field the receiver template uses.

- [ ] **Step 2: Register both via SPI**

Append `com.im.njams.sdk.communication.lifecycle.LifecycleTestSender` to
`njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.AbstractSender`.

Append `com.im.njams.sdk.communication.lifecycle.LifecycleTestReceiver` to
`njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.Receiver`.

(`ServiceLoaderSupportTest` is already count-independent, so extra SPI entries do not break it — confirm it still passes in Step 4.)

- [ ] **Step 3: Smoke test the wiring**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTransportWiringTest.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.settings.ClientSettings;

public class LifecycleTransportWiringTest {

    @Before
    public void setUp() {
        LifecycleTestTransport.reset();
    }

    @Test
    public void factoryResolvesTheControllableSender() {
        ClientSettings cs = ClientSettings.from(LifecycleTestTransport.settings().getAllProperties());
        // getSender() must return our controllable sender, resolved by transport name
        AbstractSender sender = new CommunicationFactory(cs).getSender();
        assertNotNull(sender);
        assertEquals(LifecycleTestTransport.NAME, sender.getName());
    }
}
```

> If `ClientSettings.from(...)` / `getAllProperties()` differ in the current API, mirror how the baseline ITs build `ClientSettings` from a `Properties`/`Settings`; the assertion only needs a resolvable sender.

- [ ] **Step 4: Run wiring + SPI tests**

Run: `mvn -q -pl njams-sdk test -Dtest="LifecycleTransportWiringTest,ServiceLoaderSupportTest,CommunicationFactoryTest"`
Expected: PASS. (Before Task 4, `getSender()` still calls `startup()→connect()`, which SUCCEEDs by default, so this passes.)

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.AbstractSender njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.Receiver
git commit -m "SDK-375 Add controllable lifecycle test transport (sender+receiver)"
```

---

### Task 3: New setting `PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR` + parse helper

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java`
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/communication/StartupFailBehavior.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/StartupFailBehaviorTest.java`

**Interfaces:**
- Produces:
  - `NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR = "njams.sdk.communication.startup.failbehavior"`.
  - package-private enum `StartupFailBehavior { FAIL, RECONNECT }` with `static StartupFailBehavior fromSettings(ClientSettings)` (default `FAIL`, case-insensitive, unknown → `FAIL` with a warn log) and `boolean reconnectOnStartupFailure()`.

- [ ] **Step 1: Write the failing parse test**

`StartupFailBehaviorTest.java`:

```java
package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

public class StartupFailBehaviorTest {

    private static ClientSettings withFailBehavior(String value) {
        Properties p = new Properties();
        if (value != null) {
            p.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, value);
        }
        return ClientSettings.from(p);
    }

    @Test
    public void defaultsToFailWhenAbsent() {
        assertEquals(StartupFailBehavior.FAIL, StartupFailBehavior.fromSettings(withFailBehavior(null)));
        assertFalse(StartupFailBehavior.fromSettings(withFailBehavior(null)).reconnectOnStartupFailure());
    }

    @Test
    public void parsesReconnectCaseInsensitively() {
        assertEquals(StartupFailBehavior.RECONNECT, StartupFailBehavior.fromSettings(withFailBehavior("reconnect")));
        assertEquals(StartupFailBehavior.RECONNECT, StartupFailBehavior.fromSettings(withFailBehavior("RECONNECT")));
        assertTrue(StartupFailBehavior.fromSettings(withFailBehavior("reconnect")).reconnectOnStartupFailure());
    }

    @Test
    public void unknownValueFallsBackToFail() {
        assertEquals(StartupFailBehavior.FAIL, StartupFailBehavior.fromSettings(withFailBehavior("bogus")));
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=StartupFailBehaviorTest`
Expected: FAIL — `PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR` and `StartupFailBehavior` do not exist.

- [ ] **Step 3: Add the key + enum**

In `NjamsSettings.java`, next to `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT`, add (with Javadoc, `@since 6.0.0`):

```java
    /**
     * Controls how {@code Njams.start()} reacts when the transport cannot be connected on the very first attempt.
     * Values: {@code fail} (default) — start() returns {@code false} and the SDK stays inactive; {@code reconnect}
     * — start() returns {@code true} and the connection is retried in the background. Governs the whole
     * shared-transport group.
     *
     * @since 6.0.0
     */
    public static final String PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR =
            "njams.sdk.communication.startup.failbehavior";
```

Create `StartupFailBehavior.java` (production file → copyright header):

```java
package com.im.njams.sdk.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Startup fail-behavior for the transport connection, parsed from
 * {@link NjamsSettings#PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR}. Internal SDK infrastructure — not public API.
 */
enum StartupFailBehavior {
    /** Initial connect failure fails startup: {@code Njams.start()} returns {@code false}, SDK inactive. */
    FAIL,
    /** Initial connect failure enters the background reconnect loop; {@code Njams.start()} returns {@code true}. */
    RECONNECT;

    /** Default when the setting is absent or unrecognised. */
    static final StartupFailBehavior DEFAULT = FAIL;

    private static final Logger LOG = LoggerFactory.getLogger(StartupFailBehavior.class);

    static StartupFailBehavior fromSettings(ClientSettings settings) {
        String value = settings.getProperty(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT;
        }
        String normalized = value.trim();
        if (RECONNECT.name().equalsIgnoreCase(normalized)) {
            return RECONNECT;
        }
        if (FAIL.name().equalsIgnoreCase(normalized)) {
            return FAIL;
        }
        LOG.warn("Unknown value '{}' for {}; defaulting to '{}'.", value,
            NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, DEFAULT.name().toLowerCase());
        return DEFAULT;
    }

    boolean reconnectOnStartupFailure() {
        return this == RECONNECT;
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=StartupFailBehaviorTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java njams-sdk/src/main/java/com/im/njams/sdk/communication/StartupFailBehavior.java njams-sdk/src/test/java/com/im/njams/sdk/communication/StartupFailBehaviorTest.java
git commit -m "SDK-375 Add startup fail-behavior setting and parser"
```

---

### Task 4: `AbstractSender` eager connect + remove swallowing `startup()`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/CommunicationFactory.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderStartupSpecTest.java`

**Interfaces:**
- Consumes: `ConnectionCoordinator.markStartupConnected()` (Task 1), `LifecycleTestTransport` controls (Task 2).
- Produces:
  - `AbstractSender.beginConnect()` — idempotent; starts a daemon thread that calls `connect()`, on success calls `coordinator.markStartupConnected()`, and counts down a startup latch (mirrors `AbstractReceiver.beginConnect()`).
  - `AbstractSender.awaitStartup(long timeoutMs)` — awaits the startup connect up to `timeoutMs`; returns `true` iff connected; never throws, never triggers reconnect.
  - `AbstractSender.cancelReconnect()` — interrupts the startup connect thread and the reconnect thread if alive.
  - `AbstractSender.startup()` **removed**; `CommunicationFactory.getSender()` no longer calls it.

- [ ] **Step 1: Verify no subclass overrides `startup()`**

Run: `grep -rn "void startup" njams-sdk/src/main/java`
Expected: only `AbstractSender.startup()`. If any transport overrides it, fold that logic into its `connect()` before removing — record findings in the task report.

- [ ] **Step 2: Write the failing startup spec test**

`SenderStartupSpecTest.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.settings.ClientSettings;

public class SenderStartupSpecTest {

    @Before
    public void setUp() {
        LifecycleTestTransport.reset();
    }

    private static AbstractSender freshSender() {
        AbstractSender s = new LifecycleTestSender();
        s.init(ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        return s;
    }

    @Test
    public void awaitStartupReturnsTrueWhenConnectSucceeds() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        AbstractSender s = freshSender();
        s.beginConnect();
        assertTrue(s.awaitStartup(5000));
        assertTrue(s.isConnected());
    }

    @Test
    public void awaitStartupReturnsFalseWhenConnectFails() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        AbstractSender s = freshSender();
        s.beginConnect();
        assertFalse(s.awaitStartup(5000));
        assertFalse(s.isConnected());
    }

    @Test
    public void awaitStartupTimesOutWhileConnectBlocks() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        AbstractSender s = freshSender();
        s.beginConnect();
        assertFalse("blocked connect must not report success within the timeout", s.awaitStartup(200));
        // releasing afterwards lets the daemon finish without affecting the (already-returned) result
        LifecycleTestTransport.releaseBlockedConnect();
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderStartupSpecTest`
Expected: FAIL — `beginConnect()` / `awaitStartup(long)` do not exist.

- [ ] **Step 4: Implement the eager-connect methods on `AbstractSender`**

Add imports `java.util.concurrent.CountDownLatch`, `java.util.concurrent.TimeUnit`, `java.util.concurrent.atomic.AtomicBoolean`.

Add fields near `reconnector` (line ~57):

```java
    private final AtomicBoolean startupBegun = new AtomicBoolean(false);
    private volatile CountDownLatch startupLatch;
    private volatile Thread startupConnector;
    private volatile Exception startupError;
```

Replace `startup()` (lines ~106-120) with `beginConnect()` + `awaitStartup(long)`:

```java
    /**
     * Starts one initial connection attempt in the background so a slow connect overlaps application setup, then
     * marks the group connected on success. Idempotent — subsequent calls have no effect. Mirrors the receiver's
     * {@code beginConnect()} model. Intended for internal SDK use at {@code Njams.start()}.
     */
    public void beginConnect() {
        if (!startupBegun.compareAndSet(false, true)) {
            return;
        }
        startupLatch = new CountDownLatch(1);
        startupConnector = new Thread(() -> {
            try {
                connect();
                coordinator.markStartupConnected();
            } catch (Exception e) {
                startupError = e;
                LOG.debug("Startup connect of sender {} failed.", getName(), e);
            } finally {
                startupLatch.countDown();
            }
        });
        startupConnector.setDaemon(true);
        startupConnector.setName("Sender-Startup-" + getName());
        startupConnector.start();
    }

    /**
     * Waits up to {@code timeoutMs} for the {@link #beginConnect()} attempt to complete. Calls
     * {@link #beginConnect()} first (idempotent). Never throws and never triggers reconnect — the caller decides
     * how to react to a {@code false} result (fail-fast vs. background reconnect).
     *
     * @param timeoutMs maximum time to wait, in milliseconds.
     * @return {@code true} iff the sender is connected within the timeout.
     */
    public boolean awaitStartup(long timeoutMs) {
        beginConnect();
        try {
            if (!startupLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return isConnected() && startupError == null;
    }
```

Add `cancelReconnect()` next to `setShouldShutdown` (line ~360):

```java
    /**
     * Interrupts the startup connect thread and any in-progress reconnect thread of this sender, so a blocking
     * {@code connect()} is cancelled promptly on shutdown rather than only at the next loop check.
     */
    public void cancelReconnect() {
        final Thread startup = startupConnector;
        if (startup != null) {
            startup.interrupt();
        }
        final Thread rc = reconnector;
        if (rc != null) {
            rc.interrupt();
        }
    }
```

In `CommunicationFactory.getSender()` remove the `newInstance.startup();` line (keep `newInstance.init(settings);`). Update the surrounding comment if it mentions startup.

- [ ] **Step 4b: Logging & comment hygiene for the removal**

The removed `startup()` carried `LOG.error("Startup of sender {} failed ...")` and the `getDiscardPolicyMessage()` helper it used. This error is now redundant (startup failure is reported once by `Njams.start()` in Task 6). Actions:
- Remove `getDiscardPolicyMessage()` if `startup()` was its only caller (grep to confirm); otherwise leave it.
- The new `beginConnect()` logs the connect failure at `debug` only (the daemon thread records it in `startupError`; the client-facing `error` is emitted once by `start()`), so a failed startup does not produce duplicate `error` lines.
- Grep for stale references and fix them so the Javadoc build stays green:
  - Run `grep -rn "startup()" njams-sdk/src/main/java` — fix every `{@link #startup()}`/`{@link AbstractSender#startup()}`/prose mention (e.g. in `AbstractSender.reconnect(...)` Javadoc, `AbstractReceiver`/`Receiver` comments, `CommunicationFactory`). Point them at `beginConnect()`/`awaitStartup(long)` or drop them.
  - Re-check the `AbstractSender` class Javadoc and the `connect()`/`reconnect()` Javadoc for any "initial startup" wording that no longer matches.
- Verify the per-attempt reconnect loop in `doReconnect()` still logs *nothing* on a failed attempt (only the 1s sleep) — no new log added there; the "one info on start, one on success" pattern is preserved (asserted in Task 8).

- [ ] **Step 4c: Verify Javadoc builds after the removal**

Run: `mvn -q -pl njams-sdk javadoc:javadoc`
Expected: BUILD SUCCESS — no dangling `{@link #startup()}`.

- [ ] **Step 5: Run the startup spec test**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderStartupSpecTest`
Expected: PASS.

- [ ] **Step 6: Run sender + baseline suites (behavior preserved for connected path)**

Run: `mvn -q -pl njams-sdk test -Dtest="AbstractSenderStaticStateTest,NjamsSenderTest,SenderPoolTest,CommunicationFactoryTest,DiscardPolicyTest,SenderInstanceIsolationIT,JmsSenderBaselineIT,HttpSenderBaselineIT,JmsClientEndToEndBaselineIT"`
Expected: PASS. (Removing auto-`startup()` means pooled senders connect via the send→reconnect path; the baselines prove send/receive still works. If a baseline reveals a first-message latency regression, note it — Task 6 pre-warms one sender at start().)

- [ ] **Step 7: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java njams-sdk/src/main/java/com/im/njams/sdk/communication/CommunicationFactory.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderStartupSpecTest.java
git commit -m "SDK-375 Add eager beginConnect/awaitStartup to AbstractSender; drop swallowing startup()"
```

---

### Task 5: Phase 2 — gate reconnect on prior success

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderReconnectGatingSpecTest.java`

**Interfaces:**
- Consumes: `ConnectionCoordinator.shouldReconnect()` / `allowReconnectBeforeConnected()` (Task 1).
- Produces: `AbstractSender.reconnect(Exception)` no longer spawns a reconnect thread unless `coordinator.shouldReconnect()`.

- [ ] **Step 1: Write the failing gating spec test**

`SenderReconnectGatingSpecTest.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.settings.ClientSettings;

public class SenderReconnectGatingSpecTest {

    @Before
    public void setUp() {
        LifecycleTestTransport.reset();
    }

    private static AbstractSender freshSender() {
        AbstractSender s = new LifecycleTestSender();
        s.init(ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        return s;
    }

    @Test
    public void neverConnectedSenderDoesNotReconnect() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        AbstractSender s = freshSender();
        // has never connected and reconnect-before-connected was not allowed
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        s.reconnect(new IllegalStateException("boom"));
        assertFalse("no reconnect attempt before a first successful connect",
            attempted.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void reconnectRunsAfterAPriorSuccessfulConnect() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        AbstractSender s = freshSender();
        s.beginConnect();
        assertTrue(s.awaitStartup(5000));
        // now simulate a mid-processing loss: fail future connects and force disconnect
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        s.onExceptionForTest(new IllegalStateException("lost")); // helper drives close()+reconnect()
        assertTrue("reconnect must attempt after prior success",
            attempted.await(2000, TimeUnit.MILLISECONDS));
    }
}
```

> `onExceptionForTest` keeps the test in-package? `AbstractSender.onException(Exception)` is `protected`; this test is in package `...communication.lifecycle`, not `...communication`, so it cannot call it directly. Add a package-private test seam only if needed — **prefer** driving reconnect through the public `reconnect(Exception)` after forcing `setConnectionStatus(DISCONNECTED)` via a subclass hook on `LifecycleTestSender`. Simplest: add a public `forceDisconnect()` to `LifecycleTestSender` (test class, no API concern) that calls `setConnectionStatus(ConnectionStatus.DISCONNECTED)`, then call `s.reconnect(...)`. Replace the `onExceptionForTest` line accordingly:

```java
        ((LifecycleTestSender) s).forceDisconnect();
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        s.reconnect(new IllegalStateException("lost"));
```

Add to `LifecycleTestSender`:

```java
    /** Test hook: forces DISCONNECTED so reconnect() can be exercised. */
    public void forceDisconnect() {
        setConnectionStatus(com.im.njams.sdk.communication.ConnectionStatus.DISCONNECTED);
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderReconnectGatingSpecTest`
Expected: FAIL — `neverConnectedSenderDoesNotReconnect` fails because today `reconnect()` spawns a loop regardless of prior success.

- [ ] **Step 3: Gate `reconnect()`**

In `AbstractSender.reconnect(Exception)` (line ~161), change the guard:

```java
    public synchronized void reconnect(Exception e) {
        if (isConnecting() || isConnected() || !coordinator.shouldReconnect()) {
            return;
        }
        if (reconnector != null && reconnector.isAlive()) {
            return;
        }
        // ... unchanged thread creation ...
    }
```

Update the method Javadoc to state reconnect only runs when the group has connected before (or startup `reconnect` policy is active) and is not shutting down.

- [ ] **Step 4: Run to verify pass**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderReconnectGatingSpecTest`
Expected: PASS.

- [ ] **Step 5: Baseline re-run**

Run: `mvn -q -pl njams-sdk test -Dtest="JmsSenderBaselineIT,HttpSenderBaselineIT,JmsClientEndToEndBaselineIT"`
Expected: PASS (Phase-2 reconnect-after-transient-loss still works because the group `wasEverConnected`).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderReconnectGatingSpecTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/LifecycleTestSender.java
git commit -m "SDK-375 Gate sender reconnect on prior successful connect (Phase 2)"
```

---

### Task 6: Wire eager sender startup into `NjamsSender` and `Njams.start()` (Phase 1)

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderStartGatingSpecTest.java`

**Interfaces:**
- Consumes: `AbstractSender.beginConnect()/awaitStartup(long)/cancelReconnect()` (Task 4); `ConnectionCoordinator.allowReconnectBeforeConnected()` (Task 1); `StartupFailBehavior.fromSettings(...)` (Task 3).
- Produces:
  - `SenderPool.allowReconnectBeforeConnected()` — delegates to the coordinator.
  - `NjamsSender.beginConnect()` — pre-warms one sender's connection in the background (idempotent).
  - `NjamsSender.startWithTimeout(long timeoutMs, boolean reconnectOnFailure)` → `boolean` — awaits the pre-warmed sender; on success returns `true`; on failure returns `true` (and enters background reconnect) when `reconnectOnFailure`, else cancels the connect and returns `false`.
  - `Njams` constructor pre-warms the sender in `beginConnect()`; `Njams.start()` awaits it and applies the fail-behavior.

- [ ] **Step 1: Write the failing start()-gating spec test**

`SenderStartGatingSpecTest.java` (real `Njams`, `LIFECYCLE_TEST` transport; receiver always connects, sender is controllable):

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;

public class SenderStartGatingSpecTest {

    private Njams njams;

    @Before
    public void setUp() {
        LifecycleTestTransport.reset();
    }

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
        return new Njams(new Path(">test>"), "1.0", "test", s.getClientSettings()); // adapt to real ctor/settings API
    }

    @Test
    public void failFastStartReturnsFalseWhenSenderCannotConnect() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("fail");
        assertFalse("fail-fast: start() must return false when the sender cannot connect", njams.start());
        assertFalse(njams.isStarted());
    }

    @Test
    public void reconnectStartReturnsTrueDespiteInitialFailure() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("reconnect");
        assertTrue("reconnect policy: start() succeeds and retries in the background", njams.start());
        assertTrue(njams.isStarted());
    }

    @Test
    public void startSucceedsWhenSenderConnects() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        njams = newNjams("fail");
        assertTrue(njams.start());
        assertTrue(njams.isStarted());
    }
}
```

> Adapt `newNjams(...)` to the real `Njams` constructor and to how the baseline ITs build `ClientSettings`/`Settings`. Mirror `JmsClientEndToEndBaselineIT` for the exact bootstrap.

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderStartGatingSpecTest`
Expected: FAIL — today `start()` returns `true` even when the sender cannot connect (only the receiver gates start()).

- [ ] **Step 3: `SenderPool.allowReconnectBeforeConnected()`**

In `SenderPool.java` add:

```java
    /** Permits reconnect before the first successful connect for this group (startup {@code reconnect} policy). */
    public void allowReconnectBeforeConnected() {
        coordinator.allowReconnectBeforeConnected();
    }
```

- [ ] **Step 4: `NjamsSender.beginConnect()` + `startWithTimeout(...)`**

Add a field and methods to `NjamsSender.java`:

```java
    /** The single sender pre-warmed at startup; held until start() awaits it, then returned to the pool. */
    private volatile AbstractSender startupSender;

    /**
     * Pre-warms one sender connection in the background so it overlaps application setup. Idempotent.
     */
    public void beginConnect() {
        if (startupSender != null) {
            return;
        }
        final AbstractSender s = senderPool.get();
        if (s != null) {
            startupSender = s;
            s.beginConnect();
        }
    }

    /**
     * Awaits the pre-warmed startup connection up to {@code timeoutMs}.
     *
     * @param timeoutMs        maximum time to wait for the initial connect.
     * @param reconnectOnFailure {@code true} for the {@code reconnect} startup policy: on failure the group enters
     *                           the background reconnect loop and this returns {@code true}. {@code false} for
     *                           fail-fast: on failure the connect is cancelled and this returns {@code false}.
     * @return {@code true} if the SDK may proceed (connected, or reconnecting in the background); {@code false} to
     *         fail startup.
     */
    public boolean startWithTimeout(long timeoutMs, boolean reconnectOnFailure) {
        beginConnect();
        final AbstractSender s = startupSender;
        if (s == null) {
            return false;
        }
        if (reconnectOnFailure) {
            senderPool.allowReconnectBeforeConnected();
        }
        boolean connected = s.awaitStartup(timeoutMs);
        try {
            if (connected) {
                return true;
            }
            if (reconnectOnFailure) {
                s.reconnect(new NjamsSdkRuntimeException(
                    "Startup connect did not complete within " + timeoutMs + " ms; reconnecting in background"));
                return true;
            }
            s.cancelReconnect();
            return false;
        } finally {
            senderPool.close(s);
            startupSender = null;
        }
    }
```

Add the import `com.im.njams.sdk.common.NjamsSdkRuntimeException`.

- [ ] **Step 5: Wire into `Njams`**

In `Njams.beginConnect()` (line ~656), after kicking the receiver's `beginConnect()`, also pre-warm the sender (best-effort, mirrors the receiver's guard):

```java
        try {
            final NjamsSender earlySender = getSender();
            if (earlySender != null) {
                earlySender.beginConnect();
            }
        } catch (Exception e) {
            LOG.warn("beginConnect() failed to pre-warm sender; start() will retry.", e);
        }
```

In `Njams.start()` (line ~710), after the `if (receiver == null) return false;` gate and before `LogMessageFlushTask.start(this);`, await the sender and apply the fail-behavior:

```java
            final NjamsSender activeSender = getSender();
            if (activeSender != null) {
                long timeoutMs = settings.getLong(
                    NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_MS);
                boolean reconnectOnFailure =
                    StartupFailBehavior.fromSettings(settings).reconnectOnStartupFailure();
                if (!activeSender.startWithTimeout(timeoutMs, reconnectOnFailure)) {
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
                    return false;
                }
            }
```

> `StartupFailBehavior` is package-private in `com.im.njams.sdk.communication`; `Njams` is in `com.im.njams.sdk`. Since it is not visible cross-package, expose the decision through the sender instead: add a package-private `NjamsSender` overload `startWithTimeout(long timeoutMs)` that reads `StartupFailBehavior.fromSettings(settings)` itself and calls the two-arg method. Then `Njams.start()` calls `activeSender.startWithTimeout(timeoutMs)` and needs no reference to `StartupFailBehavior`. Prefer this — it keeps the enum internal to the communication package. Update Step 4 to add the one-arg overload and have `Njams` call it.

Revised one-arg overload in `NjamsSender`:

```java
    /**
     * Awaits the pre-warmed startup connection up to {@code timeoutMs}, applying the configured
     * {@link NjamsSettings#PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR}.
     *
     * @param timeoutMs maximum time to wait for the initial connect.
     * @return {@code true} if the SDK may proceed; {@code false} to fail startup (fail-fast policy).
     */
    public boolean startWithTimeout(long timeoutMs) {
        return startWithTimeout(timeoutMs, StartupFailBehavior.fromSettings(settings).reconnectOnStartupFailure());
    }
```

And `Njams.start()` uses:

```java
                if (!activeSender.startWithTimeout(timeoutMs)) {
```

- [ ] **Step 5b: Logging review for the start path**

- Fail-fast failure is logged **once** as `error` in `start()` (shown above) — mirror the wording/level of the existing receiver-failure `error` so the two read consistently. Do not also log `error` inside `startWithTimeout` for the fail-fast case (keep it `debug` there) to avoid a duplicate line.
- `reconnect` policy: log **once** at `info` when startup could not connect and the background reconnect takes over (e.g. `LOG.info("Initial connect did not complete within {} ms; retrying in the background (startup fail-behavior 'reconnect').", timeoutMs)`), so operators understand why `start()` returned `true` without an active connection. Emit it from `startWithTimeout(long, boolean)` on the reconnect branch, not per attempt.
- Successful startup connect: the existing `start()` success `info` ("SDK instance ... started") already covers it — do **not** add a separate sender-connected `info` (avoid noise). A `debug` on the connected sender is fine.
- The constructor `beginConnect()` sender pre-warm catch logs `warn` "failed to pre-warm sender; start() will retry." — confirm that message is accurate (start() does re-obtain and await the sender), matching the receiver's equivalent warn.

- [ ] **Step 6: Run the start-gating spec test**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderStartGatingSpecTest`
Expected: PASS (fail-fast → false; reconnect → true; success → true).

- [ ] **Step 7: Baseline + Njams tests**

Run: `mvn -q -pl njams-sdk test -Dtest="JmsClientEndToEndBaselineIT,JmsSenderBaselineIT,HttpSenderBaselineIT,NjamsTest,NjamsSenderTest"`
Expected: PASS. The end-to-end baseline now connects the sender at start() (pre-warmed) — confirm first-message delivery still works.

- [ ] **Step 8: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java njams-sdk/src/main/java/com/im/njams/sdk/Njams.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderStartGatingSpecTest.java
git commit -m "SDK-375 Eagerly connect one sender at start() with configurable fail-behavior (Phase 1)"
```

---

### Task 7: Phase 3 — shutdown sets flag before drain and cancels reconnect

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderShutdownSpecTest.java`

**Interfaces:**
- Consumes: `AbstractSender.cancelReconnect()` (Task 4), `ConnectionCoordinator.setShouldShutdown(boolean)` (Part 1).
- Produces: `SenderPool.beginShutdown()` — sets the coordinator shutdown flag and cancels in-progress reconnect/startup threads on all senders **without** blocking new-sender creation. `NjamsSender.close()` calls it **before** `executor.shutdown()`.

- [ ] **Step 1: Write the failing shutdown spec test**

`SenderShutdownSpecTest.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.settings.ClientSettings;

public class SenderShutdownSpecTest {

    @Before
    public void setUp() {
        LifecycleTestTransport.reset();
    }

    private static AbstractSender connectedSender() {
        AbstractSender s = new LifecycleTestSender();
        s.init(ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        s.beginConnect();
        s.awaitStartup(5000);
        return s;
    }

    @Test
    public void reconnectIsSuppressedOnceShutdownRequested() throws Exception {
        AbstractSender s = connectedSender();
        s.setShouldShutdown(true); // group is shutting down
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        ((LifecycleTestSender) s).forceDisconnect();
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        s.reconnect(new IllegalStateException("final send failed during shutdown"));
        assertFalse("no reconnect once shutdown is requested",
            attempted.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void cancelReconnectInterruptsABlockedReconnect() throws Exception {
        AbstractSender s = connectedSender();
        // force a reconnect that blocks inside connect()
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        ((LifecycleTestSender) s).forceDisconnect();
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        s.reconnect(new IllegalStateException("lost"));
        assertTrue("reconnect started and blocked in connect()", attempted.await(2000, TimeUnit.MILLISECONDS));
        s.cancelReconnect(); // must interrupt the blocked connect thread
        // releasing the latch is not required; interruption unblocks the daemon
        LifecycleTestTransport.releaseBlockedConnect();
    }
}
```

> The `BLOCK` mode waits on `blockRelease.await()` which is interruptible, so `cancelReconnect()`'s `interrupt()` throws `InterruptedException` inside `onSenderConnect()` and the reconnect loop's `catch (InterruptedException) { return; }` exits. Confirm the reconnect loop returns on interrupt.

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderShutdownSpecTest`
Expected: `reconnectIsSuppressedOnceShutdownRequested` PASSES already (Part 1 guarded reconnect on `shouldShutdown`), but keep it as a regression guard. `cancelReconnectInterruptsABlockedReconnect` may already pass given Task 4's `cancelReconnect()`. If both pass, this task's *production* change is only the `NjamsSender.close()` ordering + `beginShutdown()`; add the ordering test below which **fails** first.

Add the ordering test to `SenderShutdownSpecTest` (drives the real `NjamsSender.close()` sequence):

```java
    @Test
    public void closeSetsShutdownBeforeDrainingSoAFailingFinalSendDoesNotReconnect() throws Exception {
        // Build a real NjamsSender over the controllable transport, connect, then close while a send fails.
        com.im.njams.sdk.communication.NjamsSender sender =
            new com.im.njams.sdk.communication.NjamsSender(
                ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        sender.startWithTimeout(5000); // connect one sender
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        int before = LifecycleTestTransport.senderConnectCount();
        sender.close(); // shutdown flag must be set before the drain → no new reconnect connects
        // allow any (wrongly-spawned) reconnect a brief window; assert none happened
        assertFalse("shutdown-first ordering must prevent reconnect during drain",
            LifecycleTestTransport.connectAttemptedLatch().await(500, TimeUnit.MILLISECONDS));
    }
```

Run: `mvn -q -pl njams-sdk test -Dtest=SenderShutdownSpecTest`
Expected: the ordering test FAILS on current `close()` (declareShutdown after the drain).

- [ ] **Step 3: Add `SenderPool.beginShutdown()`**

In `SenderPool.java`:

```java
    /**
     * Begins shutdown for the group: sets the coordinator's shutdown flag and cancels any in-progress
     * reconnect/startup threads, so a failing final send during the executor drain does not spawn a reconnect.
     * Unlike {@link #declareShutdown()} this does <em>not</em> block new-sender creation, so in-flight sends can
     * still borrow a sender while the executor drains.
     */
    public void beginShutdown() {
        coordinator.setShouldShutdown(true);
        streamAll().forEach(AbstractSender::cancelReconnect);
    }
```

- [ ] **Step 4: Reorder `NjamsSender.close()`**

Change `close()` (line ~215) to call `beginShutdown()` first:

```java
    public void close() {
        final int waitTime = 10;
        final TimeUnit unit = TimeUnit.SECONDS;
        boolean terminated = false;
        senderPool.beginShutdown(); // set shutdown flag + cancel reconnects BEFORE draining
        try {
            LOG.info("Shutdown the sender's threadpool executor.");
            executor.shutdown();
            terminated = executor.awaitTermination(waitTime, unit);
            if (terminated) {
                LOG.debug("Shutdown of the sender's threadpool executor finished.");
            }
        } catch (InterruptedException ex) {
            LOG.error("The shutdown of the sender's threadpool has been interrupted. {}", ex);
            terminated = false;
        } finally {
            senderPool.declareShutdown();
            if (!terminated) {
                LOG.warn(
                    "The termination time of the sender's threadpool has been exceeded ({} {}). Forcing shutdown now.",
                    waitTime, unit);
                executor.shutdownNow();
            }
            senderPool.shutdown();
            LOG.debug("Expire all sender pools finished.");
        }
    }
```

(The pool-level `shutdown=true` that blocks creation still happens in `declareShutdown()` inside `finally`, after the drain — so in-flight sends can still borrow senders during the drain, while reconnect is already suppressed.)

- [ ] **Step 4b: Logging review for shutdown**

- Keep the existing `NjamsSender.close()` `info`/`warn`/`debug` lines (executor shutdown, forced-shutdown warning) — verify their wording is still accurate after the reorder (they describe the executor drain, which is unchanged in meaning).
- `beginShutdown()` may log a single `debug` (e.g. "Beginning sender group shutdown; cancelling reconnects.") — **not** `info` (shutdown is already announced by the executor `info` line) and **not** per sender. Do not log inside the `streamAll().forEach(cancelReconnect)` loop.
- `cancelReconnect()` (Task 4) must not log per call; interruption is expected control flow. If the interrupted reconnect thread logs on `InterruptedException`, ensure it is `debug`, not `warn`/`error` (a cancelled reconnect during shutdown is normal, not a fault).
- Confirm a failing final send during the drain does not emit an `error` per retry: with the shutdown flag set first, the `send()` loop exits promptly rather than looping and logging.

- [ ] **Step 5: Run the shutdown spec test**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderShutdownSpecTest`
Expected: PASS (all, including the ordering test).

- [ ] **Step 6: Baseline (clean-shutdown-flush) re-run**

Run: `mvn -q -pl njams-sdk test -Dtest="JmsSenderBaselineIT,JmsClientEndToEndBaselineIT,HttpSenderBaselineIT"`
Expected: PASS — the clean-shutdown-flushes-in-flight baseline still holds (final send during drain still succeeds when connected; only reconnect is suppressed).

- [ ] **Step 7: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderShutdownSpecTest.java
git commit -m "SDK-375 Set shutdown before draining and cancel in-flight reconnect (Phase 3)"
```

---

### Task 8: "Logged once" / no-flood acceptance test

**Files:**
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderLoggingSpecTest.java`

**Interfaces:**
- Consumes: existing logging in `AbstractSender.doReconnect()` (one `info` on reconnect start, one on success) and `beginConnect()` (debug on failure).

This task adds no production code unless the assertion reveals a logging defect; it locks in the "must not fill the log" acceptance criterion. Besides the exactly-once reconnect assertions below, add a **no-flood** assertion: after driving many failed reconnect attempts (mode `FAIL` across several attempt-latch cycles) the captured `info`/`error`/`warn` count stays bounded (e.g. `initInfos == 1` regardless of the number of failed attempts, and zero `error`/`warn` from routine retries). If any per-attempt log appears, treat it as a defect and fix the production log level (do not weaken the assertion without user confirmation).

- [ ] **Step 1: Add a capturing SLF4J appender test**

`SenderLoggingSpecTest.java` — attach a Logback `ListAppender` to `AbstractSender`'s logger, drive one connected→lost→reconnected cycle, assert exactly one "Initialized reconnect" info and one "Reconnected sender" info (retries silent). (Use the project's existing test logging backend; mirror any existing appender-capture test in the repo. If none exists, use `ch.qos.logback.classic.Logger` + `ListAppender`, available transitively via the test classpath.)

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

import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.settings.ClientSettings;

public class SenderLoggingSpecTest {

    private Logger senderLogger;
    private ListAppender<ILoggingEvent> appender;

    @Before
    public void setUp() {
        LifecycleTestTransport.reset();
        senderLogger = (Logger) LoggerFactory.getLogger(AbstractSender.class);
        appender = new ListAppender<>();
        appender.start();
        senderLogger.addAppender(appender);
        senderLogger.setLevel(Level.DEBUG);
    }

    @After
    public void tearDown() {
        senderLogger.detachAppender(appender);
    }

    @Test
    public void reconnectLogsOnceOnStartAndOnceOnSuccess() throws Exception {
        AbstractSender s = new LifecycleTestSender();
        s.init(ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        s.beginConnect();
        s.awaitStartup(5000);

        // lose the connection: fail a few reconnect attempts, then succeed
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        ((LifecycleTestSender) s).forceDisconnect();
        s.reconnect(new IllegalStateException("lost"));
        // allow a failing attempt to happen, then let the next attempt succeed
        LifecycleTestTransport.connectAttemptedLatch().await(2000, TimeUnit.MILLISECONDS);
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);

        // poll until connected (no fixed sleep)
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!s.isConnected() && System.nanoTime() < deadline) {
            LifecycleTestTransport.connectAttemptedLatch().await(200, TimeUnit.MILLISECONDS);
        }

        long initInfos = appender.list.stream()
            .filter(e -> e.getLevel() == Level.INFO)
            .filter(e -> e.getFormattedMessage().startsWith("Initialized reconnect")).count();
        long successInfos = appender.list.stream()
            .filter(e -> e.getLevel() == Level.INFO)
            .filter(e -> e.getFormattedMessage().startsWith("Reconnected sender")).count();
        assertEquals("exactly one reconnect-start info", 1, initInfos);
        assertEquals("exactly one reconnect-success info", 1, successInfos);
    }
}
```

> Remove the `Thread.sleep(0)` placeholder; it is a no-op marker. The test switches mode and polls on the attempt latch — no timing assumptions. If Logback is not the active backend, adapt to the backend on the test classpath (check `pom.xml` test deps). If exact-once cannot be guaranteed because of the `markConnected()` transition semantics, treat a failure here as a real logging defect and fix `doReconnect()` accordingly (do not weaken the assertion without user confirmation).

- [ ] **Step 2: Run**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderLoggingSpecTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderLoggingSpecTest.java
git commit -m "SDK-375 Assert reconnect logs once on start and once on success"
```

---

### Task 9: Documentation — new setting

**Files:**
- Modify: `njams-sdk-sample-client/src/main/resources/settings_full.properties`
- Modify: `wiki/FAQ.md`

- [ ] **Step 1: Document the setting in `settings_full.properties`**

Add near the other `njams.sdk.communication.*` entries:

```properties
# Startup fail-behavior for the transport connection.
# fail (default): njams.start() returns false and the SDK stays inactive if the initial connect fails.
# reconnect: njams.start() returns true and the connection is retried in the background until it succeeds.
#njams.sdk.communication.startup.failbehavior=fail
```

- [ ] **Step 2: Document in `wiki/FAQ.md`**

In the communication section, add an entry describing `njams.sdk.communication.startup.failbehavior`: purpose (how startup reacts to an initial connect failure), values (`fail` default | `reconnect`), that it governs the whole shared-transport group (sender + receiver share one transport), and its interaction with `njams.sdk.communication.connect.timeout` (the await budget at `start()`). Note that with `fail`, `Njams.start()` returns `false` so the client can decide to continue without nJAMS.

- [ ] **Step 3: Verify docs build (no code) and commit**

```bash
git add njams-sdk-sample-client/src/main/resources/settings_full.properties wiki/FAQ.md
git commit -m "SDK-375 Document startup.failbehavior setting in settings_full and FAQ"
```

---

### Task 10: Full verification, self-review, finalize

**Files:** none (verification only), then the finalizing commit.

- [ ] **Step 1: Checkstyle + Javadoc**

Run: `mvn -q -pl njams-sdk checkstyle:check`
Run: `mvn -q -pl njams-sdk javadoc:javadoc`
Expected: both pass (all new `public`/`protected` members documented; no broken `{@link}`).

- [ ] **Step 2: Full module test suite**

Run: `mvn -pl njams-sdk test`
Expected: `Failures: 0, Errors: 0`. Watch the known pre-existing flaky `AbstractReceiverTest`/`JmsReceiverTest`; if either flakes, re-run isolated to confirm it is the known flakiness, not a regression.

- [ ] **Step 3: Relocated-type check**

Verify no new `public`/`protected` member references a type in the `checkstyle.xml` relocation list. The new public surface is: `NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR` (String), `AbstractSender.beginConnect()/awaitStartup(long)/cancelReconnect()`, `NjamsSender.beginConnect()/startWithTimeout(long)/startWithTimeout(long, boolean)`, `SenderPool.allowReconnectBeforeConnected()/beginShutdown()` — all use only JDK/SDK types. Confirm.

- [ ] **Step 4: `breaking-change` label**

Confirm the SDK-375 ticket does **not** carry the `breaking-change` label (per the Global Constraints decision). If present, remove it.

- [ ] **Step 4b: Logging accuracy & no-flood sweep**

- `grep -rn "startup()" njams-sdk/src/main/java` → zero matches (all references retargeted/removed).
- Re-read the touched log statements and adjacent comments/Javadoc in `AbstractSender`, `NjamsSender`, `SenderPool`, `CommunicationFactory`, `Njams` — every one must describe the *new* three-phase behavior. No comment or log claims lazy first-message connect, auto-`startup()`, or "shutdown flag set after drain".
- Confirm the failure-path logs are once-only: fail-fast → one `error` (from `start()`); reconnect policy → one `info`; per reconnect attempt and per discarded message → no `info`/`warn`/`error`. The Task 8 no-flood test backs this; spot-check by scanning `doReconnect()`, `send()`, and `onException()` for logs inside loops.

- [ ] **Step 5: Self-review against the spec**

- Phase 1 startup (eager, overlapping connect + fail-behavior + fail-fast returns false / reconnect returns true): Tasks 4, 6. ✅
- Phase 2 reconnect gated on prior success: Tasks 1, 5. ✅
- Phase 3 shutdown before drain + cancel in-flight reconnect: Tasks 4, 7. ✅
- New setting + docs: Tasks 3, 9. ✅
- "Logged once": Task 8. ✅
- Instance isolation preserved: Part 1 tests still green (Tasks 4–7 baseline runs). ✅
- Out of scope (Part 3): receiver sharing the *same* coordinator, one coordinated group reconnect, gating start() through the unified coordinator. Sender keeps its own coordinator here. ✅

- [ ] **Step 6: Finalizing commit**

```bash
git add -A
git commit -m "SDK-375 #comment Implement three-phase sender lifecycle (startup fail-behavior, gated reconnect, safe shutdown)"
```

---

## Self-Review (plan author)

**Spec coverage:** every §5.1/§5.2/§5.3 requirement and §7 setting maps to a task (see Task 10 Step 5). Receiver unification (§4.2 shared coordinator, §5 "one fate") is intentionally deferred to Part 3.

**Placeholder scan:** test bootstraps that depend on the exact `Njams`/`ClientSettings` construction API are flagged inline ("adapt to real ctor/settings API") with the concrete reference to mirror (`JmsClientEndToEndBaselineIT`); these are integration-glue details, not logic placeholders. All production code steps show exact code.

**Type consistency:** `beginConnect()`, `awaitStartup(long)`, `cancelReconnect()`, `markStartupConnected()`, `wasEverConnected()`, `allowReconnectBeforeConnected()`, `shouldReconnect()`, `startWithTimeout(long)` / `startWithTimeout(long, boolean)`, `beginShutdown()` are named identically across the tasks that define and consume them.

**Open items intentionally left to the implementer (grounded, not guesses):** the exact `AbstractReceiver` abstract-method set for `LifecycleTestReceiver` (mirror `TestReceiver`); the exact `ClientSettings`/`Settings` construction in tests (mirror the baseline ITs); the active SLF4J backend for the logging test (check `pom.xml`).
