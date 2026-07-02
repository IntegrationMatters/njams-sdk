# SDK-375 Part 1 — Sender Connection-State Foundation (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the JVM-global `static` reconnect state (and the scattered per-instance connection flags) on the **sender** side with a single per-shared-transport-group `ConnectionCoordinator` instance, wired through `NjamsSender` → `SenderPool` → `AbstractSender`, without changing any observable connect/reconnect/shutdown behavior.

**Architecture:** Introduce an internal `ConnectionCoordinator` that owns the four pieces of connection lifecycle state that are today either `static` (`hasConnected`, `connecting`) or per-instance-but-group-wide-in-effect (`hasConnectionFailure`, `shouldShutdown`) on `AbstractSender`. One coordinator is created per `NjamsSender` (i.e. per shared sender pool when communications are shared, else per `Njams` instance) and injected into every `AbstractSender` the pool creates. The reconnect *algorithm* and the per-instance reconnector thread stay in `AbstractSender` for this part — only the *state* moves. This removes cross-`Njams`-instance coupling while preserving behavior.

**Tech Stack:** Java 11, JUnit 4 + Mockito, the existing embedded-broker/JDK-HttpServer baseline harness.

## Global Constraints

- **Behavior-preserving.** No change to observable connect/reconnect/shutdown behavior. The ONLY intended observable change: two unrelated `Njams` instances (separate, non-shared communications) no longer share reconnect state.
- **Baseline stays green at every commit** (`JmsSenderBaselineIT`, `JmsClientEndToEndBaselineIT`, `HttpSenderBaselineIT`). A baseline red = real regression → stop.
- **Runtime-path performance:** no added allocation on the hot send path; no new locks beyond what replaces the existing `synchronized (hasConnected)`; no I/O/blocking on message threads (`CLAUDE.md`).
- **API:** `ConnectionCoordinator` is package-private (`com.im.njams.sdk.communication`), not public API. No relocated/shaded types on any `public`/`protected` member. `Njams.start()` signature unchanged. Adding the coordinator field/setter to `AbstractSender` is additive; `AbstractSender` is communication-internal (D1.5) so signature changes there are allowed if needed.
- **`njams-safe-modification`:** existing code (`AbstractSender`, `NjamsSender`, `SenderPool`) is modified — test coverage (the baseline + the new isolation test) must be green before and after each change.
- **Production source files** need the copyright header; test files do not.
- Commit after each task. Commit messages reference `SDK-375`.

## Current state (what we are replacing)

`AbstractSender` (`njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java`):
- `private static final AtomicBoolean hasConnected` (line 63) — JVM-global "group has an active connection" flag; monitor for the reconnect log-once + counter block.
- `private static final AtomicInteger connecting` (line 65) — JVM-global count of reconnecting senders (debug logging only).
- `protected boolean hasConnectionFailure` (line 59) — per-instance; set true entering reconnect, false on success; read by `SenderPool.isConnectionFailure()` (line 78) → `MaxQueueLengthHandler`.
- `private final AtomicBoolean shouldShutdown` (line 70) — per-instance; set via `setShouldShutdown(boolean)` (called by `SenderPool.declareShutdown()`/`destroy()`); checked in `reconnect()` (line 161), `doReconnect()` loop (line 191), and `send()` loop (line 288).
- `private Thread reconnector` (line 60) — per-instance reconnect daemon. **Stays per-instance in Part 1.**

`SenderPool.isConnectionFailure()` (line 77-79) streams all senders and `anyMatch(AbstractSender::hasConnectionFailure)`.
`NjamsSender.init()` (line 149) creates the `SenderPool`.

## File structure

- **Create** `njams-sdk/src/main/java/com/im/njams/sdk/communication/ConnectionCoordinator.java` — the per-group state holder.
- **Create** `njams-sdk/src/test/java/com/im/njams/sdk/communication/ConnectionCoordinatorTest.java` — unit tests.
- **Modify** `AbstractSender.java` — add coordinator field + setter; route the four state items through it; remove the two `static` fields and the `hasConnectionFailure`/`shouldShutdown` instance fields.
- **Modify** `SenderPool.java` — accept a coordinator, inject it on `create()`, and back `isConnectionFailure()` with it.
- **Modify** `NjamsSender.java` — create one coordinator in `init()` and pass it to the `SenderPool`.
- **Create** `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/SenderInstanceIsolationIT.java` — proves two non-shared `Njams` sender groups have independent connection state.

---

### Task 1: `ConnectionCoordinator` state holder + unit tests

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/communication/ConnectionCoordinator.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/ConnectionCoordinatorTest.java`

**Interfaces:**
- Produces (all package-private): class `ConnectionCoordinator` with
  - `int beginReconnect()` — marks the group disconnected, sets the connection-failure flag, increments and returns the reconnecting count.
  - `boolean markConnected()` — clears the connection-failure flag, decrements the reconnecting count, and returns `true` exactly once per disconnected→connected transition (for log-once).
  - `int reconnectingCount()` — current reconnecting count (for debug logging).
  - `boolean isConnectionFailure()` / `void setConnectionFailure(boolean)`
  - `boolean shouldShutdown()` / `void setShouldShutdown(boolean)`

- [ ] **Step 1: Write the failing unit test**

Create `ConnectionCoordinatorTest.java`:

```java
package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConnectionCoordinatorTest {

    @Test
    public void freshCoordinatorIsNotFailedAndNotShuttingDown() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        assertFalse(c.isConnectionFailure());
        assertFalse(c.shouldShutdown());
        assertEquals(0, c.reconnectingCount());
    }

    @Test
    public void beginReconnectMarksFailureAndCounts() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        assertEquals(1, c.beginReconnect());
        assertTrue(c.isConnectionFailure());
        assertEquals(2, c.beginReconnect());
        assertEquals(2, c.reconnectingCount());
    }

    @Test
    public void markConnectedSignalsTransitionOnceAndClearsFailure() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        c.beginReconnect();
        assertTrue("first markConnected is the disconnected->connected transition", c.markConnected());
        assertFalse(c.isConnectionFailure());
        assertFalse("second markConnected while already connected is not a transition", c.markConnected());
    }

    @Test
    public void shutdownFlagRoundTrips() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        c.setShouldShutdown(true);
        assertTrue(c.shouldShutdown());
        c.setShouldShutdown(false);
        assertFalse(c.shouldShutdown());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -pl njams-sdk test -Dtest=ConnectionCoordinatorTest`
Expected: FAIL — `ConnectionCoordinator` does not exist (compilation error).

- [ ] **Step 3: Implement `ConnectionCoordinator`**

Create `ConnectionCoordinator.java` (include the standard copyright header — production file):

```java
package com.im.njams.sdk.communication;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the connection lifecycle state for one shared-transport group of senders (all senders handed out by a
 * single {@link SenderPool}). Replaces the former JVM-global {@code static} reconnect state on
 * {@link AbstractSender} so that unrelated {@link com.im.njams.sdk.Njams} instances no longer share connection
 * state. Internal SDK infrastructure — not public API.
 */
class ConnectionCoordinator {

    private final AtomicBoolean hasConnected = new AtomicBoolean(false);
    private final AtomicInteger connecting = new AtomicInteger(0);
    private volatile boolean connectionFailure = false;
    private final AtomicBoolean shouldShutdown = new AtomicBoolean(false);

    /**
     * Marks the group as currently disconnected and beginning a reconnect: clears the connected flag, raises the
     * connection-failure flag, and increments the reconnecting count.
     *
     * @return the reconnecting count after incrementing (for logging).
     */
    synchronized int beginReconnect() {
        hasConnected.set(false);
        connectionFailure = true;
        return connecting.incrementAndGet();
    }

    /**
     * Records a successful (re)connect: clears the connection-failure flag and decrements the reconnecting count.
     *
     * @return {@code true} exactly once for the disconnected&rarr;connected transition (so the caller can log the
     *         reconnect a single time), {@code false} if the group was already marked connected.
     */
    synchronized boolean markConnected() {
        connectionFailure = false;
        connecting.decrementAndGet();
        return hasConnected.compareAndSet(false, true);
    }

    /** @return the current number of in-progress reconnects in this group. */
    synchronized int reconnectingCount() {
        return connecting.get();
    }

    /** @return {@code true} while the group is in a connection-failure state. */
    boolean isConnectionFailure() {
        return connectionFailure;
    }

    /** Sets the connection-failure flag for the group. */
    void setConnectionFailure(boolean value) {
        connectionFailure = value;
    }

    /** @return {@code true} once the group is shutting down. */
    boolean shouldShutdown() {
        return shouldShutdown.get();
    }

    /** Sets the shutdown flag for the group. */
    void setShouldShutdown(boolean value) {
        shouldShutdown.set(value);
    }
}
```

- [ ] **Step 4: Run the unit test to verify it passes**

Run: `mvn -q -pl njams-sdk test -Dtest=ConnectionCoordinatorTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/ConnectionCoordinator.java njams-sdk/src/test/java/com/im/njams/sdk/communication/ConnectionCoordinatorTest.java
git commit -m "SDK-375 Add ConnectionCoordinator to own per-group sender connection state"
```

---

### Task 2: Wire the coordinator through `NjamsSender` and `SenderPool` into `AbstractSender`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java`

**Interfaces:**
- Consumes: `ConnectionCoordinator` (Task 1).
- Produces:
  - `AbstractSender.setConnectionCoordinator(ConnectionCoordinator)` and a `protected ConnectionCoordinator coordinator` field.
  - `SenderPool(CommunicationFactory factory, ConnectionCoordinator coordinator)` constructor; `SenderPool` injects the coordinator into every sender it creates.

This task only *injects* the coordinator; `AbstractSender` does not yet *use* it (Task 3 switches usage). Behavior is unchanged.

- [ ] **Step 1: Add the field + setter to `AbstractSender`**

In `AbstractSender.java`, add near the other fields (after line 61):

```java
    private ConnectionCoordinator coordinator = new ConnectionCoordinator();

    /**
     * Injects the shared connection coordinator for this sender's group. Called by the {@link SenderPool} right
     * after creation. Defaults to a private coordinator so a stand-alone sender still works.
     *
     * @param coordinator the group coordinator; must not be {@code null}.
     */
    void setConnectionCoordinator(ConnectionCoordinator coordinator) {
        this.coordinator = coordinator;
    }
```

(The default assignment keeps existing unit tests that construct a bare sender working.)

- [ ] **Step 2: Add the constructor + injection to `SenderPool`**

In `SenderPool.java`, add a field and change the constructor (lines 57, 64-66) to:

```java
    private final CommunicationFactory factory;
    private final ConnectionCoordinator coordinator;
```

```java
    public SenderPool(CommunicationFactory factory, ConnectionCoordinator coordinator) {
        this.factory = factory;
        this.coordinator = coordinator;
    }
```

And in `create()` (after `final AbstractSender sender = factory.getSender();`, line 85), inject the coordinator before returning:

```java
        final AbstractSender sender = factory.getSender();
        sender.setConnectionCoordinator(coordinator);
        if (!exceptionListeners.isEmpty()) {
            exceptionListeners.forEach(sender::addExceptionListener);
        }
        return sender;
```

- [ ] **Step 3: Create the coordinator in `NjamsSender` and pass it in**

In `NjamsSender.java` `init()` (line 160), change the `SenderPool` construction to create and pass a coordinator:

```java
        final CommunicationFactory communicationFactory = new CommunicationFactory(settings);
        senderPool = new SenderPool(communicationFactory, new ConnectionCoordinator());
```

- [ ] **Step 4: Compile and run the full sender/communication tests**

Run: `mvn -q -pl njams-sdk test -Dtest="NjamsSenderTest,SenderPoolTest,CommunicationFactoryTest"`
Expected: PASS (existing tests still green — injection is behavior-neutral).

- [ ] **Step 5: Run the baseline suite**

Run: `mvn -q -pl njams-sdk test -Dtest="JmsSenderBaselineIT,HttpSenderBaselineIT,JmsClientEndToEndBaselineIT"`
Expected: PASS (behavior unchanged).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java
git commit -m "SDK-375 Inject a per-group ConnectionCoordinator into pooled senders"
```

---

### Task 3: Route `AbstractSender` connection state through the coordinator (remove statics)

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/SenderInstanceIsolationIT.java`

**Interfaces:**
- Consumes: `AbstractSender.coordinator` (Task 2), `ConnectionCoordinator` API (Task 1).
- Produces: `AbstractSender` no longer declares `static hasConnected`, `static connecting`, `hasConnectionFailure`, or `shouldShutdown`; `hasConnectionFailure()` and `setShouldShutdown(boolean)` delegate to the coordinator. `SenderPool.isConnectionFailure()` reads the coordinator.

- [ ] **Step 1: Write the failing instance-isolation test**

Create `SenderInstanceIsolationIT.java` — two independent (non-shared) sender groups must not share reconnect state. It uses the JMS baseline harness; one group points at a *stopped* broker (so it enters reconnect / connection-failure) while a second group points at a *running* broker (so it must report healthy):

```java
package com.im.njams.sdk.communication.it;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.jms.JmsSender;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Two independent sender groups (separate NjamsSender instances, non-shared communications) must keep their
 * connection-failure state independent — a failing group must not flip a healthy group's failure flag, and vice
 * versa. This fails while the reconnect state is JVM-global {@code static}.
 */
public class SenderInstanceIsolationIT {

    @Rule
    public EmbeddedActiveMqBroker broker = new EmbeddedActiveMqBroker();

    private static ClientSettings jmsSettings(String brokerUrlOverride) {
        Properties p = new Properties();
        p.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        p.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, EmbeddedActiveMqJmsFactory.NAME);
        p.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        p.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "onconnectionloss");
        p.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "1");
        p.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "1");
        return ClientSettings.from(p);
    }

    private static LogMessage logMessage(String logId) {
        LogMessage m = new LogMessage();
        m.setLogId(logId);
        m.setPath(">a>b>");
        return m;
    }

    @Test
    public void healthyGroupIsUnaffectedByAFailingGroup() throws Exception {
        // Group A: healthy — connect and deliver against the running broker.
        NjamsSender healthy = new NjamsSender(jmsSettings(null));
        // Group B: failing — point at a broker URL that is down (no broker on this vm name).
        NjamsSender failing = new NjamsSender(failingSettings());
        try {
            healthy.send(logMessage("healthy-1"), "session-h");
            // let the healthy group connect+deliver
            assertTrue(Await.until(() -> !healthy.senderPoolConnectionFailure(), 5000));

            failing.send(logMessage("failing-1"), "session-f");
            // the failing group must eventually report a connection failure
            assertTrue("failing group must report its own connection failure",
                Await.until(failing::senderPoolConnectionFailure, 10000));

            // the healthy group must STILL report no connection failure (independent state)
            assertFalse("healthy group must not inherit the failing group's connection-failure state",
                healthy.senderPoolConnectionFailure());
        } finally {
            healthy.close();
            failing.close();
        }
    }

    private static ClientSettings failingSettings() {
        Properties p = new Properties();
        p.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        // A vm broker URL that will NOT auto-create and has no running broker -> connect fails.
        p.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, DownBrokerJmsFactory.NAME);
        p.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        p.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "onconnectionloss");
        p.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "1");
        p.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "1");
        return ClientSettings.from(p);
    }
}
```

This test needs two small test-support additions:
1. A test-visible accessor on `NjamsSender` for the pool's connection-failure flag.
2. A `DownBrokerJmsFactory` (SPI) whose connection factory points at a non-existent broker so connect always fails.

- [ ] **Step 2: Add the test-support accessor and the down-broker factory**

In `NjamsSender.java`, add a package-visible accessor (used only by the isolation test):

```java
    boolean senderPoolConnectionFailure() {
        return senderPool != null && senderPool.isConnectionFailure();
    }
```

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/DownBrokerJmsFactory.java`:

```java
package com.im.njams.sdk.communication.it;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.settings.ClientSettings;

/** A JmsFactory pointing at a vm:// broker that does not exist and must not be auto-created, so connect fails. */
public class DownBrokerJmsFactory implements JmsFactory {
    public static final String NAME = "DownBroker";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(ClientSettings settings) {
        // nothing
    }

    @Override
    public ConnectionFactory createConnectionFactory() {
        return new ActiveMQConnectionFactory("vm://sdk375-down?create=false&broker.persistent=false");
    }
}
```

Append it to `njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory`:

```
com.im.njams.sdk.communication.jms.FailingJmsFactory
com.im.njams.sdk.communication.jms.NoopJmsFactory
com.im.njams.sdk.communication.it.EmbeddedActiveMqJmsFactory
com.im.njams.sdk.communication.it.DownBrokerJmsFactory
```

(`ServiceLoaderSupportTest.testGetAll` is already count-independent, so this extra SPI factory does not break it.)

- [ ] **Step 3: Run the isolation test to verify it FAILS on the current static-state code**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderInstanceIsolationIT`
Expected: FAIL — with the JVM-global `static hasConnected`/instance-flag design the failing group's reconnect can flip shared state observed by the healthy group (or the assertion that the healthy group stays failure-free is violated). Capture the observed failure in the task report as the RED evidence.

> If it unexpectedly passes on current code, STOP and report — the isolation defect may manifest differently than assumed and the test needs to target the actual shared-state path before proceeding.

- [ ] **Step 4: Refactor `AbstractSender` to use the coordinator**

In `AbstractSender.java` make these exact changes:

(a) Remove the fields (lines 59, 63, 65, 70):
```java
    protected boolean hasConnectionFailure = false;
    private static final AtomicBoolean hasConnected = new AtomicBoolean(false);
    private static final AtomicInteger connecting = new AtomicInteger(0);
    private final AtomicBoolean shouldShutdown = new AtomicBoolean(false);
```
Keep `private Thread reconnector = null;` and the `coordinator` field from Task 2. Remove the now-unused `AtomicBoolean`/`AtomicInteger` imports if nothing else uses them.

(b) `reconnect(Exception e)` — replace the `shouldShutdown.get()` guard (line 161) with `coordinator.shouldShutdown()`:
```java
        if (isConnecting() || isConnected() || coordinator.shouldShutdown()) {
            return;
        }
```

(c) `doReconnect(Exception ex)` — replace the body (lines 182-210) with the coordinator-backed version, preserving the log lines:
```java
    protected void doReconnect(Exception ex) {
        int reconnecting = coordinator.beginReconnect();
        if (LOG.isInfoEnabled() && ex != null) {
            LOG.info("Initialized reconnect, because of: {}", getExceptionWithCauses(ex));
        }
        LOG.debug("{} senders are reconnecting now", reconnecting);
        while (!isConnected() && !coordinator.shouldShutdown()) {
            try {
                connect();
                if (coordinator.markConnected()) {
                    LOG.info("Reconnected sender {}", getName());
                }
                LOG.debug("{} senders still need to reconnect.", coordinator.reconnectingCount());
            } catch (Exception e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    return;
                }
            }
        }
    }
```

(d) `send(...)` loop — replace the two `shouldShutdown.get()` reads (loop condition around line 288) with `coordinator.shouldShutdown()`:
```java
        } while (!isSent && !Thread.currentThread().isInterrupted() && !coordinator.shouldShutdown());
```

(e) `setShouldShutdown(boolean)` (line 362) — delegate:
```java
    public void setShouldShutdown(boolean shutdown) {
        coordinator.setShouldShutdown(shutdown);
    }
```

(f) `hasConnectionFailure()` (line 370) — delegate:
```java
    public boolean hasConnectionFailure() {
        return coordinator.isConnectionFailure();
    }
```

- [ ] **Step 5: Point `SenderPool.isConnectionFailure()` at the coordinator**

In `SenderPool.java` replace `isConnectionFailure()` (lines 77-79):
```java
    public boolean isConnectionFailure() {
        return coordinator.isConnectionFailure();
    }
```

- [ ] **Step 6: Run the isolation test — now GREEN**

Run: `mvn -q -pl njams-sdk test -Dtest=SenderInstanceIsolationIT`
Expected: PASS — the two groups have independent coordinators, so the healthy group's failure flag is unaffected by the failing group.

- [ ] **Step 7: Run the sender unit tests + the whole baseline suite**

Run: `mvn -q -pl njams-sdk test -Dtest="AbstractSenderTest,NjamsSenderTest,SenderPoolTest,DiscardPolicyTest"`
Expected: PASS.
Run: `mvn -q -pl njams-sdk test -Dtest="JmsSenderBaselineIT,HttpSenderBaselineIT,JmsClientEndToEndBaselineIT"`
Expected: PASS (behavior preserved).

- [ ] **Step 8: Full module suite**

Run: `mvn -pl njams-sdk test`
Expected: `Failures: 0, Errors: 0` (watch for the pre-existing flaky `AbstractReceiverTest`/`JmsReceiverTest`; if either flakes, re-run it isolated to confirm it is the known pre-existing flakiness, not a regression from this change).

- [ ] **Step 9: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java njams-sdk/src/test/java/com/im/njams/sdk/communication/it/SenderInstanceIsolationIT.java njams-sdk/src/test/java/com/im/njams/sdk/communication/it/DownBrokerJmsFactory.java njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory
git commit -m "SDK-375 Move sender connection state to per-group ConnectionCoordinator (remove JVM-global statics)"
```

---

## Self-Review

**Spec coverage (Part 1 scope):**
- Remove JVM-global `static` sender reconnect state → Task 3. ✅
- Per-shared-transport-group scoping (one coordinator per `NjamsSender`/pool) → Tasks 2–3. ✅
- Behavior-preserving (baseline green) → Tasks 2, 3 (baseline runs). ✅
- Instance isolation proven → Task 3 (`SenderInstanceIsolationIT`, RED→GREEN). ✅
- Receiver statics, startup/shutdown/reconnect *semantics*, and the new setting → deliberately **out of Part 1** (Parts 2–3). ✅

**Placeholder scan:** no TBD/TODO; every code step shows the exact code and command. ✅

**Type consistency:** `ConnectionCoordinator` methods (`beginReconnect`, `markConnected`, `reconnectingCount`, `isConnectionFailure`/`setConnectionFailure`, `shouldShutdown`/`setShouldShutdown`) are defined in Task 1 and used identically in Tasks 2–3; `setConnectionCoordinator`, `SenderPool(factory, coordinator)`, and `NjamsSender.senderPoolConnectionFailure()` are referenced consistently. ✅

**Open items to confirm before executing (design choices worth a nod):**
1. **`markConnected()` count semantics.** The refactor decrements the reconnecting count once per successful `connect()` iteration and reports it via `reconnectingCount()` — matching the current debug-log intent. Confirm the debug-count wording need not be byte-identical (it is debug-level and not asserted anywhere).
2. **`SenderInstanceIsolationIT` failure mechanism.** The test asserts the *healthy* group's `isConnectionFailure()` stays false while a *separate* failing group reconnects. On current code the two groups share instance-level failure flags only via the pool (not static), while `hasConnected`/`connecting` are static — so the precise RED manifestation should be confirmed at Step 3; if the exact defect is only observable via the `static` counters, the test may need to assert on a reconnect-log/counter signal instead. Confirm the RED before implementing GREEN.
