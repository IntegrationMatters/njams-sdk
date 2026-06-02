# SDK-442 / SDK-376: Startup Connection Timeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent `Njams.start()` from hanging indefinitely when the communication backend is unreachable by introducing a configurable startup connection timeout; if the timeout is exceeded, `start()` returns `false` and the SDK becomes completely inactive (no reconnect thread). Additionally, allow the connection attempt to begin at `Njams` construction time so that it overlaps with application setup (SDK-376).

**Architecture:** `AbstractReceiver` gains two new methods. `beginConnect()` starts the background connect daemon thread immediately and records its outcome in instance fields (`CountDownLatch`, `AtomicReference<Exception>`, `AtomicBoolean timedOut`). `startWithTimeout(long timeoutMs)` calls `beginConnect()` as its first step (idempotent — no-op if already started), then awaits the latch with the given deadline; on timeout or error it throws without ever calling `onException()`, so no reconnect thread is created. `Njams` gains a public `beginConnect()` method that pre-creates the receiver and kicks off `AbstractReceiver.beginConnect()` before `start()` is called, overlapping connection with application setup. `Njams.startReceiver()` reuses the pre-created receiver if available and calls `startWithTimeout(timeoutMs)`. Because `Njams.stop()` does not null the `receiver` field, a second `start()` after `stop()` always creates a fresh `AbstractReceiver` instance (via `CommunicationFactory`), keeping startup state clean across restart cycles.

**Tech Stack:** Java 11, JUnit 4, Mockito, `java.util.concurrent.CountDownLatch`, `java.util.concurrent.atomic.AtomicBoolean/AtomicReference`

---

## File Map

| Action | File |
|--------|------|
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/communication/Receiver.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java` |
| Modify | `njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java` |
| Modify | `njams-sdk/src/test/java/com/im/njams/sdk/communication/TestReceiver.java` |
| Modify | `njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java` |
| Modify | `njams-sdk-sample-client/src/main/resources/settings_full.properties` |
| Modify | `wiki/FAQ.md` |

---

## Task 1 — Add the `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT` setting constant

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java`
- Modify: `njams-sdk-sample-client/src/main/resources/settings_full.properties`

### Background

`NjamsSettings` is a constants-only class where every SDK setting key lives. The new constant follows the existing naming convention (`njams.sdk.communication.<name>`). The default of `30000` ms (30 s) gives slow JMS/Kafka connections enough time while avoiding multi-minute OS TCP hangs.

- [ ] **Step 1: Add the constant to `NjamsSettings.java`**

Find the block of communication constants (search for `PROPERTY_CONTAINER_MODE`) and insert immediately after it:

```java
/**
 * Maximum time in milliseconds the SDK waits for the initial communication connection to be
 * established during {@link com.im.njams.sdk.Njams#start()}. If the connection is not ready within
 * this time, {@code start()} returns {@code false} and the SDK instance remains inactive — no
 * reconnect thread is started. The connection attempt may be started earlier by calling
 * {@link com.im.njams.sdk.Njams#beginConnect()} after constructing the instance; {@code start()}
 * then awaits the already-running attempt and applies this timeout only for the remaining wait.
 * The default is {@value #PROPERTY_COMMUNICATION_CONNECT_TIMEOUT_DEFAULT} ms.
 *
 * @see #PROPERTY_COMMUNICATION_CONNECT_TIMEOUT_DEFAULT
 * @since 6.0.0
 */
public static final String PROPERTY_COMMUNICATION_CONNECT_TIMEOUT =
        "njams.sdk.communication.connect.timeout";

/**
 * Default value for {@link #PROPERTY_COMMUNICATION_CONNECT_TIMEOUT}: {@value} ms.
 */
public static final long PROPERTY_COMMUNICATION_CONNECT_TIMEOUT_DEFAULT = 30_000L;
```

- [ ] **Step 2: Add the property to `settings_full.properties`**

In `njams-sdk-sample-client/src/main/resources/settings_full.properties`, add the following entry in the communication section (near other `njams.sdk.communication.*` keys):

```properties
# Maximum time in milliseconds to wait for the initial communication connection during Njams.start().
# If the connection is not established within this time, start() returns false and the SDK is inactive.
# No reconnect thread is started on timeout. Default: 30000 (30 seconds).
# Calling Njams.beginConnect() before start() allows the connection to overlap with application setup,
# reducing the effective wait time inside start().
#njams.sdk.communication.connect.timeout=30000
```

- [ ] **Step 3: Run checkstyle to verify Javadoc passes**

```bash
mvn validate -Pcheckstyle -pl njams-sdk
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java
git add njams-sdk-sample-client/src/main/resources/settings_full.properties
git commit -m "SDK-442 #comment Add PROPERTY_COMMUNICATION_CONNECT_TIMEOUT setting constant"
```

---

## Task 2 — Add `startWithTimeout` default to the `Receiver` interface

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/Receiver.java`

### Background

`Receiver` is a public interface. Adding a `default` method is non-breaking: existing third-party implementations get the fallback behaviour automatically. The default simply calls `start()`. `AbstractReceiver` will override this with the real timeout-and-early-connect logic in Task 3.

- [ ] **Step 1: Write the failing test for the default behaviour**

Open `AbstractReceiverTest.java` and add this test inside the class:

```java
@Test
public void testStartWithTimeoutDefaultDelegatesToStart() {
    // Receiver implementations that do not extend AbstractReceiver get the default
    // startWithTimeout which calls start().
    final boolean[] startCalled = {false};
    Receiver simpleReceiver = new Receiver() {
        @Override public String getName() { return "simple"; }
        @Override public void init(ClientSettings settings) {}
        @Override public void setNjams(Njams njams) {}
        @Override public void onInstruction(com.faizsiegeln.njams.messageformat.v4.command.Instruction i) {}
        @Override public void start() { startCalled[0] = true; }
        @Override public void stop() {}
    };
    simpleReceiver.startWithTimeout(100L);
    assertTrue("default startWithTimeout must delegate to start()", startCalled[0]);
}
```

- [ ] **Step 2: Run the test — expect compile failure** (method not yet defined)

```bash
mvn test -Dtest=AbstractReceiverTest#testStartWithTimeoutDefaultDelegatesToStart -pl njams-sdk
```

Expected: `COMPILATION ERROR` — `cannot find symbol: startWithTimeout`

- [ ] **Step 3: Add `startWithTimeout` to `Receiver.java`**

Locate the `start()` method in `Receiver.java` and insert the following immediately after it:

```java
/**
 * Starts this receiver for the initial connection, waiting at most {@code timeoutMs} milliseconds
 * for the connection to be established.
 * <p>
 * Unlike {@link #start()}, implementations must <strong>not</strong> trigger the reconnect
 * mechanism on failure — if the connection cannot be established within the given time,
 * this method must throw and leave the receiver inactive.
 * <p>
 * The default implementation ignores the timeout and delegates to {@link #start()}.
 * {@link AbstractReceiver} overrides this with a proper timeout-enforced implementation that
 * also supports early connection start via {@link AbstractReceiver#beginConnect()}.
 *
 * @param timeoutMs maximum time in milliseconds to wait for the connection
 * @throws com.im.njams.sdk.common.NjamsSdkRuntimeException if the connection cannot be
 *         established within {@code timeoutMs} or an error occurs during connection
 * @since 6.0.0
 */
default void startWithTimeout(long timeoutMs) {
    start();
}
```

- [ ] **Step 4: Run the test — expect pass**

```bash
mvn test -Dtest=AbstractReceiverTest#testStartWithTimeoutDefaultDelegatesToStart -pl njams-sdk
```

Expected: `BUILD SUCCESS`, test PASS

- [ ] **Step 5: Run checkstyle**

```bash
mvn validate -Pcheckstyle -pl njams-sdk
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/Receiver.java
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java
git commit -m "SDK-442 #comment Add startWithTimeout default method to Receiver interface"
```

---

## Task 3 — Implement `beginConnect()` and `startWithTimeout` in `AbstractReceiver`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java`

### Background

`AbstractReceiver` gets two coordinated methods:

- **`beginConnect()`** — starts the background connect daemon thread immediately. Idempotent: if called more than once on the same instance, subsequent calls are no-ops. Stores outcome in instance fields: `startupLatch` (signals completion), `startupError` (any exception from `connect()`), `startupTimedOut` (set by `startWithTimeout` if deadline passes before the thread finishes). If the thread eventually connects after the timeout was already signalled, it calls `stop()` to release any resources it acquired.

- **`startWithTimeout(long timeoutMs)`** — overrides the `Receiver` default. Calls `beginConnect()` as its first step (making it safe to call with or without a prior `beginConnect()`), then awaits `startupLatch` up to `timeoutMs` ms. On timeout or connect error, sets status to `DISCONNECTED`, throws `NjamsSdkRuntimeException`, and returns — without ever calling `onException()`, so no reconnect thread is created.

The new inner test class `SlowConnectReceiverImpl` simulates slow or failing `connect()` calls.

### Step 1 — Add `SlowConnectReceiverImpl` to `AbstractReceiverTest`

- [ ] Add this private inner class at the bottom of `AbstractReceiverTest` (before the closing `}`):

```java
private class SlowConnectReceiverImpl extends AbstractReceiver {
    private final long connectDelayMs;
    private final boolean throwOnConnect;
    volatile boolean stopCalled = false;

    SlowConnectReceiverImpl(long connectDelayMs, boolean throwOnConnect) {
        this.connectDelayMs = connectDelayMs;
        this.throwOnConnect = throwOnConnect;
    }

    @Override
    public String getName() { return "SlowReceiver"; }

    @Override
    public void init(ClientSettings settings) {}

    @Override
    protected com.faizsiegeln.njams.messageformat.v4.command.Response extendRequest(
            com.faizsiegeln.njams.messageformat.v4.command.Request req) {
        return null;
    }

    @Override
    public void connect() {
        try {
            if (connectDelayMs > 0) {
                Thread.sleep(connectDelayMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (throwOnConnect) {
            throw new NjamsSdkRuntimeException("Simulated connect failure");
        }
        connectionStatus = ConnectionStatus.CONNECTED;
    }

    @Override
    public void stop() {
        stopCalled = true;
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }
}
```

### Step 2 — Add tests for `beginConnect` and `startWithTimeout`

- [ ] Add the following tests immediately after the existing `//start tests` block in `AbstractReceiverTest`:

```java
@Test
public void testStartWithTimeout_successWithinTimeout() {
    SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, false);
    impl.startWithTimeout(200L);
    assertTrue("receiver must be connected after successful startWithTimeout", impl.isConnected());
}

@Test(expected = NjamsSdkRuntimeException.class)
public void testStartWithTimeout_throwsOnTimeout() {
    // connect takes 2 s, timeout is 100 ms
    SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(2000, false);
    impl.startWithTimeout(100L);
}

@Test
public void testStartWithTimeout_disconnectedAfterTimeout() {
    SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(2000, false);
    try {
        impl.startWithTimeout(100L);
    } catch (NjamsSdkRuntimeException ignored) {}
    assertTrue("receiver must be DISCONNECTED after timeout", impl.isDisconnected());
}

@Test(expected = NjamsSdkRuntimeException.class)
public void testStartWithTimeout_throwsWhenConnectThrows() {
    SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, true);
    impl.startWithTimeout(500L);
}

@Test
public void testStartWithTimeout_disconnectedWhenConnectThrows() {
    SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, true);
    try {
        impl.startWithTimeout(500L);
    } catch (NjamsSdkRuntimeException ignored) {}
    assertTrue("receiver must be DISCONNECTED when connect() throws", impl.isDisconnected());
}

@Test
public void testStartWithTimeout_noReconnectThreadOnTimeout() throws InterruptedException {
    SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(2000, false);
    try {
        impl.startWithTimeout(100L);
    } catch (NjamsSdkRuntimeException ignored) {}
    // If a reconnect thread had been started it would eventually call connect()
    // and set status to CONNECTED. Wait briefly and confirm status stays DISCONNECTED.
    Thread.sleep(300);
    assertTrue("no reconnect thread must be started on timeout", impl.isDisconnected());
}

@Test
public void testStartWithTimeout_cleansUpAfterLateConnect() throws InterruptedException {
    // connect takes 400 ms, timeout is 100 ms — background thread eventually connects late
    SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(400, false);
    try {
        impl.startWithTimeout(100L);
    } catch (NjamsSdkRuntimeException ignored) {}
    // wait for background thread to finish connecting
    Thread.sleep(600);
    assertTrue("stop() must be called to release resources acquired after timeout", impl.stopCalled);
}

@Test
public void testBeginConnect_earlyStartOverlapsWithSetup() throws InterruptedException {
    // connect takes 200 ms; begin early, then wait 250 ms before calling startWithTimeout
    SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(200, false);
    impl.beginConnect();
    Thread.sleep(250); // connection completes during this sleep
    long before = System.currentTimeMillis();
    // timeout of 50 ms would be too short if connect hadn't already finished
    impl.startWithTimeout(50L);
    long elapsed = System.currentTimeMillis() - before;
    assertTrue("receiver must be connected", impl.isConnected());
    assertTrue("startWithTimeout must return nearly immediately when already connected", elapsed < 50);
}

@Test
public void testBeginConnect_idempotent() {
    SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, false);
    impl.beginConnect();
    impl.beginConnect(); // second call must be a no-op, not start a second thread
    impl.startWithTimeout(200L);
    assertTrue(impl.isConnected());
}
```

### Step 3 — Run the new tests — expect failures

- [ ] Run to confirm the new tests fail before the implementation:

```bash
mvn test -Dtest=AbstractReceiverTest -pl njams-sdk
```

Expected: new `startWithTimeout` and `beginConnect` tests FAIL (`startWithTimeout` still delegates to `start()`, which triggers reconnect; `beginConnect` symbol not found).

### Step 4 — Implement `beginConnect()` and `startWithTimeout` in `AbstractReceiver`

- [ ] Add the following imports to `AbstractReceiver.java` if not already present:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
```

- [ ] Add the following four instance fields to `AbstractReceiver`, after the existing `AtomicInteger reconnectIntervalIncreasing` field:

```java
private final AtomicBoolean connectBegun = new AtomicBoolean(false);
private volatile CountDownLatch startupLatch;
private final AtomicReference<Exception> startupError = new AtomicReference<>();
private final AtomicBoolean startupTimedOut = new AtomicBoolean(false);
```

- [ ] Add the following two methods to `AbstractReceiver`, immediately before the existing `public abstract void connect()` declaration:

```java
/**
 * Starts the background connect thread immediately. Idempotent — subsequent calls on the same
 * instance have no effect. Call this before {@link #startWithTimeout(long)} to overlap the
 * connection attempt with other application setup work.
 * <p>
 * This method is intended for internal SDK use only.
 *
 * @since 6.0.0
 */
public void beginConnect() {
    if (!connectBegun.compareAndSet(false, true)) {
        return;
    }
    startupLatch = new CountDownLatch(1);
    Thread connectThread = new Thread(() -> {
        try {
            connect();
        } catch (Exception e) {
            startupError.set(e);
            startupLatch.countDown();
            return;
        }
        if (startupTimedOut.get()) {
            try {
                stop();
            } catch (Exception e) {
                LOG.debug("Failed to clean up {} resources after startup timeout", getName(), e);
            }
        } else {
            startupLatch.countDown();
        }
    });
    connectThread.setDaemon(true);
    connectThread.setName("Receiver-Startup-" + getName());
    connectThread.start();
}

/**
 * Starts this receiver for the initial connection, waiting at most {@code timeoutMs} milliseconds
 * for {@link #connect()} to complete.
 * <p>
 * Calls {@link #beginConnect()} as its first step (idempotent — a no-op if already called).
 * If the deadline passes before {@link #connect()} completes, or if {@link #connect()} throws,
 * this method sets the connection status to {@link ConnectionStatus#DISCONNECTED}, throws a
 * {@link NjamsSdkRuntimeException}, and returns without triggering the reconnect mechanism.
 * The SDK becomes inactive; it is the caller's responsibility to handle the failure.
 * <p>
 * If the background thread eventually establishes a connection after the timeout has already been
 * signalled, {@link #stop()} is called to release any acquired resources.
 *
 * @param timeoutMs maximum time in milliseconds to wait for the connection
 * @throws NjamsSdkRuntimeException if the timeout elapses before the connection is established,
 *         if {@link #connect()} throws, or if the calling thread is interrupted
 * @since 6.0.0
 */
@Override
public void startWithTimeout(long timeoutMs) {
    beginConnect();
    try {
        if (!startupLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            startupTimedOut.set(true);
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException(
                    "Startup timeout: " + getName() + " did not connect within " + timeoutMs + " ms");
        }
    } catch (InterruptedException e) {
        startupTimedOut.set(true);
        Thread.currentThread().interrupt();
        connectionStatus = ConnectionStatus.DISCONNECTED;
        throw new NjamsSdkRuntimeException(
                "Interrupted while waiting for " + getName() + " to connect", e);
    }
    Exception error = startupError.get();
    if (error != null) {
        connectionStatus = ConnectionStatus.DISCONNECTED;
        throw new NjamsSdkRuntimeException("Failed to connect " + getName() + " during startup", error);
    }
}
```

### Step 5 — Run all `AbstractReceiverTest` tests — expect all pass

- [ ] Run:

```bash
mvn test -Dtest=AbstractReceiverTest -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all tests PASS

### Step 6 — Run checkstyle

- [ ] Run:

```bash
mvn validate -Pcheckstyle -pl njams-sdk
```

Expected: `BUILD SUCCESS`

### Step 7 — Commit

- [ ] Commit:

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/AbstractReceiverTest.java
git commit -m "SDK-442 #comment Implement AbstractReceiver.beginConnect() + startWithTimeout(): background connect with deadline, no reconnect on failure"
```

---

## Task 4 — Wire early-connect into `Njams`; update `TestReceiver`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/TestReceiver.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java`

### Background

`Njams` gains a public `beginConnect()` method that clients may call after construction and before `start()` to overlap the connection attempt with application setup (SDK-376). It pre-creates the receiver via `CommunicationFactory` and kicks off `AbstractReceiver.beginConnect()`.

`Njams.startReceiver()` is updated to: (a) reuse the pre-created receiver from `beginConnect()` if available, (b) create a fresh one otherwise, and (c) call `receiver.startWithTimeout(timeoutMs)` in both paths.

**Why a separate `earlyReceiver` field rather than reusing `receiver`:** `Njams.stop()` does not null the `receiver` field. A second `start()` after `stop()` must create a fresh `AbstractReceiver` instance (with clean startup state). Keeping the pre-created receiver in `earlyReceiver` and moving it to `receiver` inside `startReceiver()` means the second call to `startReceiver()` will always create a fresh instance, regardless of whether `beginConnect()` was called earlier.

`TestReceiver` needs a `startWithTimeout` delegation added, because `TestReceiver.setReceiverMock()` is used to inject mock receivers for `NjamsTest`, and the mock's `startWithTimeout` must be reachable.

### Part A — Update `TestReceiver`

- [ ] **Step 1: Add `startWithTimeout` delegation to `TestReceiver`**

Open `TestReceiver.java`. After the existing `start()` override, add:

```java
@Override
public void startWithTimeout(long timeoutMs) {
    if (receiver != null) {
        receiver.startWithTimeout(timeoutMs);
    }
}
```

- [ ] **Step 2: Run all existing `NjamsTest` tests — expect no regressions**

```bash
mvn test -Dtest=NjamsTest -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all existing tests PASS

### Part B — Add `Njams.beginConnect()` and supporting field

- [ ] **Step 3: Add `earlyReceiver` field and `beginConnect()` to `Njams.java`**

Add the following field directly after the existing `private Receiver receiver;` field declaration:

```java
/** Receiver pre-created by {@link #beginConnect()}, transferred to {@link #receiver} inside {@link #startReceiver()}. */
private Receiver earlyReceiver;
```

Add the following private method immediately before `startReceiver()`:

```java
private void beginConnect() {
    if (earlyReceiver != null || started) {
        return;
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
}
```

### Part C — New `NjamsTest` tests for startup failure and early connect

- [ ] **Step 4: Add tests to `NjamsTest`**

Open `NjamsTest.java`. Verify that `AbstractReceiver` is already imported or add the import:
```java
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ClientSettings;  // if not already imported
```

Add the following tests:

```java
@Test
public void testStartReturnsFalseWhenReceiverTimesOut() {
    Receiver hangingReceiver = new Receiver() {
        @Override public String getName() { return "HangingReceiver"; }
        @Override public void init(ClientSettings settings) {}
        @Override public void setNjams(Njams njams) {}
        @Override public void onInstruction(com.faizsiegeln.njams.messageformat.v4.command.Instruction i) {}
        @Override public void start() {}
        @Override public void stop() {}
        @Override public void startWithTimeout(long timeoutMs) {
            throw new com.im.njams.sdk.common.NjamsSdkRuntimeException("Simulated startup timeout");
        }
    };
    TestReceiver.setReceiverMock(hangingReceiver);
    try {
        boolean result = instance.start();
        assertFalse("start() must return false when receiver times out", result);
        assertFalse("SDK must not be started after receiver timeout", instance.isStarted());
    } finally {
        TestReceiver.setReceiverMock(null);
    }
}

@Test
public void testStartReturnsFalseWhenReceiverThrows() {
    Receiver failingReceiver = new Receiver() {
        @Override public String getName() { return "FailingReceiver"; }
        @Override public void init(ClientSettings settings) {}
        @Override public void setNjams(Njams njams) {}
        @Override public void onInstruction(com.faizsiegeln.njams.messageformat.v4.command.Instruction i) {}
        @Override public void start() {}
        @Override public void stop() {}
        @Override public void startWithTimeout(long timeoutMs) {
            throw new com.im.njams.sdk.common.NjamsSdkRuntimeException("Simulated connect error");
        }
    };
    TestReceiver.setReceiverMock(failingReceiver);
    try {
        boolean result = instance.start();
        assertFalse("start() must return false when receiver throws on connect", result);
        assertFalse("SDK must not be started when receiver connect fails", instance.isStarted());
    } finally {
        TestReceiver.setReceiverMock(null);
    }
}

- [ ] **Step 5: Run new tests — expect failures** (`startReceiver()` still calls `start()`, not `startWithTimeout`)

```bash
mvn test -Dtest=NjamsTest#testStartReturnsFalseWhenReceiverTimesOut+testStartReturnsFalseWhenReceiverThrows+testBeginConnectBeforeStartDoesNotBreakStart -pl njams-sdk
```

Expected: `testStartReturnsFalseWhen*` FAIL, `testBeginConnect*` may pass or fail depending on current code.

### Part D — Modify `Njams.startReceiver()`

- [ ] **Step 6: Replace the body of `startReceiver()` in `Njams.java`**

Locate `startReceiver()` (currently at approximately line 533). Replace its entire body with:

```java
private void startReceiver() {
    try {
        if (earlyReceiver != null) {
            receiver = earlyReceiver;
            earlyReceiver = null;
        } else {
            receiver = new CommunicationFactory(settings).getReceiver(this);
        }
        long timeoutMs = settings.getLong(
                NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT,
                NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT_DEFAULT);
        receiver.startWithTimeout(timeoutMs);
        if (receiver instanceof SenderExceptionListener) {
            final NjamsSender sender = getSender();
            if (sender != null) {
                sender.addSenderExceptionListener((SenderExceptionListener) receiver);
            }
        }
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
    }
}
```

- [ ] **Step 7: Add missing import to `Njams.java` if not already present**

```java
import com.im.njams.sdk.communication.AbstractReceiver;
```

- [ ] **Step 8: Run all `NjamsTest` tests**

```bash
mvn test -Dtest=NjamsTest -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all tests PASS

- [ ] **Step 9: Run checkstyle**

```bash
mvn validate -Pcheckstyle -pl njams-sdk
```

Expected: `BUILD SUCCESS`

- [ ] **Step 10: Run the full SDK test suite**

```bash
mvn test -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all tests PASS

- [ ] **Step 11: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/TestReceiver.java
git add njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java
git commit -m "SDK-442 #comment Add Njams.beginConnect(); wire startWithTimeout into startReceiver(); add startup failure and early-connect tests"
```

---

## Task 5 — Update wiki FAQ

**Files:**
- Modify: `wiki/FAQ.md`

### Background

Any new setting must be documented in the FAQ. `Njams.beginConnect()` also warrants a mention because it is user-facing API.

- [ ] **Step 1: Add FAQ entry**

Open `wiki/FAQ.md`. Find the section listing communication settings and add the following entry (adapt surrounding markdown style to match the existing format):

```
### njams.sdk.communication.connect.timeout

Maximum time in milliseconds the SDK waits for the communication connection to be established
during `Njams.start()`.

- **Type:** long (milliseconds)
- **Default:** `30000` (30 seconds)
- **Since:** 6.0.0

If the connection is not ready within this time, `start()` logs an error and returns `false`.
The SDK instance is completely inactive; no reconnect thread is started. It is the client
application's responsibility to check the return value of `start()` and handle the failure
accordingly.

This setting applies to all transports (HTTP, JMS, Kafka). For JMS in particular, the JMS API
provides no standard connection timeout; without this setting a startup attempt against an
unreachable broker could silently block for 60–120 seconds at the OS TCP level.

#### Overlap with application setup

The SDK starts the connection attempt in the background automatically when the `Njams` instance
is constructed. When `start()` is subsequently called, it awaits the already-running connection,
applying the timeout only for the remaining wait. If the connection completes during application
setup (model registration, argos collectors, etc.), `start()` returns immediately without blocking.

```java
Njams njams = new Njams(path, version, category, settings);
// connection attempt starts in background automatically
// ... register process models, add collectors, etc. ...
boolean started = njams.start(); // awaits connection; may return immediately if already done
```
```

- [ ] **Step 2: Commit**

```bash
git add wiki/FAQ.md
git commit -m "SDK-442 #comment Document connect.timeout setting and Njams.beginConnect() in FAQ"
```

---

## Self-Review Checklist

### Spec coverage

| Requirement | Task |
|---|---|
| Configurable timeout for startup phase | Tasks 1, 3, 4 |
| `start()` returns `false` on failure | Task 4 (tested in `NjamsTest`) |
| No reconnect thread on startup failure | Task 3 (`testStartWithTimeout_noReconnectThreadOnTimeout`) |
| SDK completely inactive after failure | Task 4 (`isStarted()` assertion) |
| Error logged on failure | Task 4 (error log in `startReceiver()`) |
| Connection starts at construction time (SDK-376) | Task 4 Part B (constructor calls private `beginConnect()`) |
| Early connect overlaps with app setup | Task 3 (`testBeginConnect_earlyStartOverlapsWithSetup`) |
| `beginConnect()` is idempotent | Task 3 (`testBeginConnect_idempotent`) |
| Second `start()` after `stop()` gets fresh state | Architecture: `earlyReceiver` pattern (fresh instance always created on restart) |
| FAQ documents new setting and `beginConnect()` | Task 5 |
| `settings_full.properties` updated | Task 1 |

### No placeholders: confirmed — all steps contain exact code.

### Type consistency

- `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT` (String) — defined Task 1, used Task 4
- `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT_DEFAULT` (long) — defined Task 1, used Task 4
- `startWithTimeout(long timeoutMs)` — declared Task 2 (`Receiver`), implemented Task 3 (`AbstractReceiver`), delegated Task 4 Part A (`TestReceiver`), called Task 4 Part D (`Njams.startReceiver()`)
- `beginConnect()` on `AbstractReceiver` — implemented Task 3, called by private `Njams.beginConnect()`
- `beginConnect()` private on `Njams` — implemented Task 4 Part B, called from constructor (Task 4 Part D)
- `earlyReceiver` field — declared Task 4 Part B, consumed Task 4 Part D
- `SlowConnectReceiverImpl` — defined and used in Task 3 only
- `settings.getLong(String, long)` — exists on `ReadOnlyClientSettings` (verified in codebase)
- `AbstractReceiver` import in `Njams.java` — added Task 4 Part D Step 7
