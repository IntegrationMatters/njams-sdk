# SDK-473 — Failing Sender Triggers Receiver Reconnect (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a receiver that passively waits for messages re-verify its own connection when the sender group
recovers from a *real* connection outage, so it stops silently missing connection losses.

**Architecture:** The signal is **evidence-gated and recovery-timed**. `SenderPool` records whether the failure
that opened the current outage indicated a broken connection (asked of the failing sender through a new
`AbstractSender.isConnectionBroken(Throwable)` template method, default `true`), and `SenderConnector` records
whether any `connect()` attempt in its reconnect loop actually failed. When the group's connection is
re-established, `SenderPool.onReconnected(...)` fires a new one-way `SenderRecoveryListener` **once per outage**,
but only if *both* hold. `AbstractReceiver` implements that listener and cycles its own connection through its
existing `onException(Exception)` machinery, guarded so it never fights its own lifecycle. `Njams` registers and —
new — **deregisters** the receiver on the group.

**Tech Stack:** Java 11, JUnit 4 + Mockito, SLF4J, the existing `LifecycleTest{Sender,Receiver,Transport}` +
`SharedLifecycleTestReceiver` controllable fake transport and `SenderPoolTestAccess` bridge.

**Spec:** No standalone spec file. The design was settled in conversation on 2026-08-18 and is reproduced in full
under "Design decisions" below; the upstream context is
`docs/superpowers/specs/2026-08-11-sdk-472-single-threaded-sender-reconnect-design.md` (§5.4 shared
communications, §8.2 known-wrong failure classification, §8.3 the SDK-473 seam) and
`docs/superpowers/specs/2026-07-02-sdk-375-sender-lifecycle-design.md` §0 (the D1.7 cut this ticket replaces).

---

## Global Constraints

- **Ticket:** `SDK-473` — "Failing sender should trigger receiver reconnect". Status `In Progress`, assigned,
  fix version `6.0.0`. Every commit references `SDK-473`. Intermediate commits carry **no** `#comment`; only the
  finalizing commit (Task 11) uses `SDK-473 #comment …`.
- **Base branch:** `SDK-375` (current working branch). Do not create branches. Never push.
- **Baseline stays green at every commit:** `mvn -pl njams-sdk test` — the full module suite, not just the touched
  tests.
- **`njams-safe-modification` applies.** Every existing member touched (`SenderPool.onReconnected`,
  `SenderPool.reportFailure`, `SenderPool.restartConnectInBackground`, `SenderConnector.runReconnectLoop`,
  `SenderConnector.runStartupConnect`, `Njams.startReceiver`, `Njams.stop`,
  `Njams.stopReceiverAfterStartupFailure`, `AbstractReceiver`) must be covered by a green test before the change.
  Task 0 establishes and records that baseline.
- **Existing tests must not be modified** without explicit user permission. If one fails, the change is wrong.
  The only permitted edits to existing test files are *additive*: new methods on `SenderPoolTestAccess`.
- **No relocated/shaded third-party type** on any `public`/`protected` member — check every new signature against
  the `<relocations>` list in `checkstyle.xml`. All types introduced here are JDK or SDK-owned, so this is a
  verification step, not a design constraint.
- **Javadoc on every new/changed `public`/`protected` member**, and it must be *concise* per
  `.claude/rules/public-api-design.md` → "Conciseness": state the contract, no numeric or implementation
  specifics of concrete subclasses, state restrictions without justifying them.
- **Copyright header** (2026 Salesfive, exact text in `.claude/rules/code-quality-general.md`) on the one new
  production file (`SenderRecoveryListener.java`). Test files do not get it.
- **No timing-based tests.** Drive phases with the transport's latches/gates and poll for conditions; never
  `Thread.sleep` to "wait for" a state. Note the sender reconnect loop sleeps `RECONNECT_INTERVAL_MS = 1000`
  between attempts, so poll timeouts must be generous (5–10 s).
- **Logging hygiene:** one line per state transition, never per attempt. Keep every comment and Javadoc the change
  touches accurate — including on members that merely *reference* a changed one.
- **Verify line numbers against the live file before editing.** This plan cites the file state as of
  2026-08-18 (branch `SDK-375`, HEAD `44ef1581`); re-read before each edit per CLAUDE.md's "No Unsupported
  Assumptions".

---

## Design decisions

These were confirmed with the user before this plan was written. They are binding; do not re-litigate them
mid-execution, but **do stop and raise** anything that contradicts them.

- **D473.1 — One-way only.** Sender outage → receiver. The receiver→sender direction stays cut (it was D1.7 in
  SDK-375, removed by commit `100f643f` because it tore down a healthy sender pool on a receiver-only hiccup).
  Do not add it back, in any form.
- **D473.2 — Recovery-timed, not outage-timed.** The signal fires when the group's connection is re-established,
  not when the outage starts. Rationale: at recovery the endpoint is provably reachable, so the receiver's cycle
  succeeds on its first attempt instead of spinning its own 500 ms→60 s backoff loop alongside the sender's for
  the whole outage. During an outage a dead receiver could not have received anything anyway.
- **D473.3 — Evidence gate.** Signal only if at least one `connect()` attempt inside the group's reconnect loop
  actually failed. This is what keeps a transient send failure against a reachable endpoint (an HTTP 503, a JMS
  `ResourceAllocationException` load peak — see SDK-472 spec §8.2) from cycling a healthy receiver, *without*
  waiting for SDK-474.
- **D473.4 — Classification stub now, per-transport implementations in SDK-474.**
  `AbstractSender.isConnectionBroken(Throwable)` defaults to `true`, which reproduces today's behavior exactly.
  SDK-474 reduces to overriding it per transport.
- **D473.5 — Gate composition is AND, never OR.** `classifiedBroken && aConnectAttemptFailed`. With the default
  `true`, the evidence gate alone decides today; as SDK-474 lands, classification can only ever *tighten* the
  signal. OR would re-admit exactly the false positive D473.3 exists to remove.
- **D473.6 — Classification is consumed only for this signal.** It must **not** change whether the group flips to
  reconnecting, message fate, retirement, or discard accounting. Those remain SDK-474's decisions. This keeps
  SDK-473's sender-side diff behavior-neutral.
- **D473.7 — The receiver object itself is the listener.** `AbstractReceiver implements SenderRecoveryListener`,
  registered as `receiver`, not as a lambda. This is load-bearing: `SenderPool`'s listener collection is
  identity-based (`IdentityHashMap`), so registering the receiver object makes a **shared** receiver collapse to
  one entry (one signal, one cycle) while **dedicated** per-instance receivers on a shared group each register
  their own (N signals, one each — which is correct). A lambda per `Njams` instance would create N distinct
  listener objects wrapping the *same* shared receiver and cycle it N times.
- **D473.8 — Deregistration is part of this ticket.** The listener collections are add-only today and nothing ever
  unregisters. A JVM-wide shared group therefore keeps a hard reference to every stopped instance's receiver for
  as long as any sibling lives, and would signal each of them on every outage. Latent today only because nothing
  registers; live the moment this ticket lands.
- **D473.9 — No new setting.** The behavior is hardcoded, consistent with the rest of the receiver's reconnect
  behavior after SDK-375 Part 4.
- **D473.11 — The HTTP/SSE dedicated-receiver fallback is normal, not a problem.** HTTP/SSE deliberately has no
  shareable receiver: a shared receiver brings no benefit there, so it is simply not needed. The message
  `CommunicationFactory.findReceiverType` logs when sharing was requested but no shareable receiver exists must
  therefore be **INFO**, not WARN, and must not be worded as a shortcoming. Task 8 fixes this.
- **D473.10 — The receiver's existing WARN is accepted.** The cycle runs through `onException(...)` →
  `reconnect(...)`, which logs `WARN "Receiver connection lost. …"`. That is truthful (the receiver *has* just been
  disconnected) and fires at most once per real outage, so it is not suppressed. The new INFO line logged
  immediately before it supplies the reason. Do not add a signature overload just to change this log level.

### Verified facts this design rests on

Re-verified against the live code at plan time; do not re-derive, but do not silently rely on them either if an
edit makes one false:

| Fact | Evidence |
|---|---|
| Any exception escaping `sender.send(...)` reports a group failure | `NjamsSender.java:210-225` (`dispatch`) |
| `SenderPool` listener collections are identity sets | `SenderPool.java:91-98` |
| `onReconnected` has exactly two callers | `SenderConnector.java:149` (startup), `:223` (reconnect loop) |
| `shared=true` + HTTP yields **dedicated** receivers (no shareable `HttpSseReceiver` exists), logged today at WARN — see D473.11 | `CommunicationFactory.java:108-123` |
| Cycling a shared receiver preserves its instance registry and message selector | `messageSelector` and `njamsInstances` are fields; `JmsReceiver.closeAll()` clears neither; only `removeNjams` does |
| `AbstractReceiver.reconnect(Exception)` is `synchronized`, so a second loop parks for the whole first loop | `AbstractReceiver.java:243` |
| `NjamsSender.close()` shuts the pool down but never nulls `senderPool` | `NjamsSender.java:298-324`, field at `:98` |
| The receiver's `ConnectionCoordinator` is marked connected by `beginConnect()`/`reconnect()`, but **not** by plain `start()` | `AbstractReceiver.beginConnect()`, `reconnect()`, `start()` |

---

## File structure

**Create (production, 1 file):**
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderRecoveryListener.java` — the one-way hook.

**Modify (production, 7 files):**
- `communication/AbstractSender.java` — add `isConnectionBroken(Throwable)`.
- `communication/SenderPool.java` — record the outage's classification; recovery-listener collection, add/remove,
  and fan-out from `onReconnected(...)`.
- `communication/SenderConnector.java` — track failed connect attempts; pass the flag to `onReconnected(...)`.
- `communication/NjamsSender.java` — delegate add/remove of the recovery listener.
- `Njams.java` — register the receiver; deregister it in both teardown paths.
- `communication/AbstractReceiver.java` — implement the listener with the guarded cycle.
- `communication/CommunicationFactory.java` — the dedicated-receiver fallback logs at INFO, not WARN (D473.11).

**Create (test, 4 files):**
- `communication/lifecycle/SenderRecoverySignalSpecTest.java` — the gate, at pool level.
- `communication/lifecycle/ReceiverRecoveryCycleSpecTest.java` — the receiver's guards and cycle.
- `communication/lifecycle/SharedSenderRecoverySignalSpecTest.java` — shared-group end-to-end + deregistration.
- `communication/lifecycle/SharedReceiverSelectionSpecTest.java` — `shared=true` really yields **one** receiver
  instance for all clients (the property every shared-mode assumption in this plan rests on, and which nothing
  currently proves).

**Modify (test, 2 files, additive only):**
- `communication/SenderPoolTestAccess.java` — expose the recovery-listener surface and a classifying sender.
- `communication/CommunicationFactoryTest.java` — one new method asserting the fallback's log level.

**Modify (docs):**
- `wiki/FAQ.md` — the "receiver is independent" paragraph becomes inaccurate and must be corrected.

---

## Task 0: Establish and record the baseline

No production change. This exists because `njams-safe-modification` requires proven-green coverage of every
existing member before it is touched, and because a pre-existing failure must never be mistaken for one this
plan caused.

- [ ] **Step 1: Run the full module suite**

```bash
mvn -pl njams-sdk test
```

Expected: BUILD SUCCESS. Record the test/failure/error counts from the summary line.

- [ ] **Step 2: Confirm the specific baseline classes covering the members this plan modifies are among them and green**

```bash
mvn -pl njams-sdk test -Dtest='SenderPoolAcquireSpecTest+SenderRetirementSpecTest+SharedSenderOutageSpecTest+SenderConnectorReconnectGatingSpecTest+SenderReconnectGatingSpecTest+MessageRetentionSpecTest+ReceiverShutdownSpecTest+ReceiverStartupSpecTest+SharedReceiverRestartSpecTest+AbstractReceiverTest+SenderPoolTest+NjamsSenderTest'
```

Expected: BUILD SUCCESS, zero failures. These are the baseline for `SenderPool.onReconnected`/`reportFailure`,
`SenderConnector`'s reconnect loop, `AbstractReceiver`, and `Njams`'s receiver wiring/teardown.

- [ ] **Step 3: Record the result**

State the counts in the execution log. If anything is red *before* any change, stop and report it rather than
proceeding — do not "fix" a pre-existing failure as part of this ticket.

No commit for this task.

---

## Task 1: `AbstractSender.isConnectionBroken(Throwable)` classification stub

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java` (append after
  `notifyConnectionFailure(Exception)`, which ends around line 221)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderFailureClassificationTest.java` (create)

**Interfaces:**
- Consumes: nothing.
- Produces: `protected boolean AbstractSender.isConnectionBroken(Throwable failure)` — returns `true` by default;
  overridable by transports. Task 2 calls it and its tests override it.

- [ ] **Step 1: Write the failing test**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderFailureClassificationTest.java`:

```java
package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;

/**
 * Specifies the SDK-473/SDK-474 failure-classification seam: a sender says whether a reported failure indicates a
 * broken connection, and the default must keep the pre-SDK-473 behaviour of assuming it does.
 */
public class SenderFailureClassificationTest {

    /** Minimal concrete sender that does not override the classification. */
    private static class DefaultSender extends AbstractSender {
        @Override
        public String getName() {
            return "classification-default";
        }

        @Override
        public void connect() {
            // no transport
        }

        @Override
        public void close() {
            // no transport
        }

        @Override
        protected void send(LogMessage msg, String clientSessionId) {
            // not exercised
        }

        @Override
        protected void send(ProjectMessage msg, String clientSessionId) {
            // not exercised
        }

        @Override
        protected void send(TraceMessage msg, String clientSessionId) {
            // not exercised
        }

        /** Exposes the protected classification to this test. */
        boolean classify(Throwable failure) {
            return isConnectionBroken(failure);
        }
    }

    /** A transport that can rule a load peak out, the way SDK-474 will. */
    private static class ClassifyingSender extends DefaultSender {
        @Override
        protected boolean isConnectionBroken(Throwable failure) {
            return !(failure instanceof IllegalStateException);
        }
    }

    @Test
    public void defaultClassificationAssumesTheConnectionIsBroken() {
        DefaultSender sender = new DefaultSender();
        assertTrue("a transport that cannot classify must assume a broken connection",
            sender.classify(new RuntimeException("boom")));
        assertTrue("a null cause must be treated the same as any unclassifiable failure",
            sender.classify(null));
    }

    @Test
    public void anOverridingTransportCanRuleOutAConnectionLoss() {
        ClassifyingSender sender = new ClassifyingSender();
        assertFalse("an overriding transport must be able to rule out a connection loss",
            sender.classify(new IllegalStateException("queue full")));
        assertTrue("an unrecognised failure must still count as a broken connection",
            sender.classify(new RuntimeException("socket closed")));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -pl njams-sdk test -Dtest=SenderFailureClassificationTest
```

Expected: COMPILATION FAILURE — `isConnectionBroken` does not exist.

- [ ] **Step 3: Add the template method**

In `AbstractSender.java`, immediately after the closing brace of `notifyConnectionFailure(Exception)` and before
the class's final `}`:

```java
    /**
     * Classifies a failure reported for this sender. Return {@code false} for a failure that does not indicate a
     * broken connection — a load peak, throttling, or a per-message reject on a working connection.
     * <p>
     * A transport that cannot tell the difference must leave this alone: the default answer is that the
     * connection may be broken.
     *
     * @param failure the failure that was reported; may be {@code null}.
     * @return {@code true} unless this transport can rule out a connection loss.
     * @since 6.0.0
     */
    protected boolean isConnectionBroken(Throwable failure) {
        return true;
    }
```

Do **not** call it from anywhere yet — that is Task 2.

- [ ] **Step 4: Run the test to verify it passes**

```bash
mvn -pl njams-sdk test -Dtest=SenderFailureClassificationTest
```

Expected: PASS, 2 tests.

- [ ] **Step 5: Verify no relocated type leaked into the new signature**

`Throwable` and `boolean` are JDK types. Confirm by eye against `checkstyle.xml`'s `<relocations>` list, then run:

```bash
mvn -pl njams-sdk validate -Pcheckstyle
```

Expected: BUILD SUCCESS (Javadoc on the new protected member is present).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderFailureClassificationTest.java
git commit -m "SDK-473 Add AbstractSender.isConnectionBroken failure-classification seam"
```

---

## Task 2: `SenderPool` records the outage's classification

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java` — `failGroup()` (~line 487),
  `reportFailure(AbstractSender, Exception)` (~line 423), `restartConnectInBackground(long)` (~line 228)
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderPoolTestAccess.java` (additive)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderRecoverySignalSpecTest.java`
  (create; extended again in Task 4)

**Interfaces:**
- Consumes: `AbstractSender.isConnectionBroken(Throwable)` (Task 1).
- Produces: `private boolean SenderPool.outageIndicatesBrokenConnection`, set by
  `failGroup(boolean brokenConnection)`; readable in tests via
  `SenderPoolTestAccess.outageIndicatesBrokenConnection()`. Task 4 consumes the field in `onReconnected(...)`.

- [ ] **Step 1: Write the failing test**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderRecoverySignalSpecTest.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderPoolTestAccess;

/**
 * Specifies SDK-473's evidence gate: the pool records whether the failure that opened an outage indicated a
 * broken connection, and only signals recovery when that classification and a genuinely failed reconnect attempt
 * agree.
 */
public class SenderRecoverySignalSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void anUnclassifiedOutageCountsAsABrokenConnection() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("boom"));
        assertTrue("a transport that cannot classify must leave the outage counted as a broken connection",
            pool.outageIndicatesBrokenConnection());
    }

    @Test
    public void anOutageTheSenderRuledOutIsRecordedAsSuch() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.reportFailure(pool.newSenderRulingOutConnectionLoss(), new IllegalStateException("queue full"));
        assertFalse("a transport that ruled out a connection loss must be recorded as such",
            pool.outageIndicatesBrokenConnection());
    }

    @Test
    public void aStartupTimeoutCountsAsABrokenConnection() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        pool.restartConnectInBackground(1);
        assertTrue("a startup timeout has no sender to ask and must count as a broken connection",
            pool.outageIndicatesBrokenConnection());
    }

    @Test
    public void aSecondFailureDuringTheSameOutageDoesNotRewriteTheClassification() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("real loss"));
        // The group is already reconnecting; a later report belongs to the same outage and must not re-classify it.
        pool.reportFailure(pool.newSenderRulingOutConnectionLoss(), new IllegalStateException("queue full"));
        assertTrue("only the electing failure classifies the outage",
            pool.outageIndicatesBrokenConnection());
    }
}
```

- [ ] **Step 2: Add the test-access methods**

In `SenderPoolTestAccess.java`, add these members (additive only — do not change existing ones). Put the nested
class just below the `POOLS` field, and the methods next to `newUnconnectedSender()`:

```java
    /**
     * A sender that rules out a connection loss for every failure, standing in for the per-transport
     * classification SDK-474 will implement.
     */
    public static class SenderRulingOutConnectionLoss extends LifecycleTestSender {
        @Override
        protected boolean isConnectionBroken(Throwable failure) {
            return false;
        }
    }

    /** @return a sender whose classification rules out a connection loss, for driving the evidence gate. */
    public Object newSenderRulingOutConnectionLoss() {
        return new SenderRulingOutConnectionLoss();
    }

    /** @return whether the failure that opened the current outage was classified as a broken connection. */
    public boolean outageIndicatesBrokenConnection() {
        return pool.outageIndicatesBrokenConnectionForTest();
    }

    public void restartConnectInBackground(long timeoutMs) {
        pool.restartConnectInBackground(timeoutMs);
    }
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
mvn -pl njams-sdk test -Dtest=SenderRecoverySignalSpecTest
```

Expected: COMPILATION FAILURE — `outageIndicatesBrokenConnectionForTest` does not exist.

- [ ] **Step 4: Add the field and thread the classification through `failGroup`**

In `SenderPool.java`, add the field next to `private boolean reconnecting = false;` (~line 100):

```java
    /**
     * Whether the failure that opened the current outage indicated a broken connection, as classified by the
     * failing sender. Only ever touched under {@link #lock}. Consumed by
     * {@link #onReconnected(AbstractSender, boolean)} and by nothing else — it must not influence retirement,
     * message fate or the discard policy (SDK-474 owns those).
     */
    private boolean outageIndicatesBrokenConnection = true;
```

Change `failGroup()`'s signature and first lines:

```java
    /**
     * Flips the group into the failed/reconnecting state and retires everything currently checked out. Must be
     * called with {@link #lock} held and only while {@code !reconnecting}.
     *
     * @param brokenConnection whether the failure opening this outage indicated a broken connection.
     * @return the idle senders the caller must close <em>after</em> releasing the lock.
     */
    private List<AbstractSender> failGroup(boolean brokenConnection) {
        outageIndicatesBrokenConnection = brokenConnection;
        reconnecting = true;
```

(leave the rest of the method body untouched)

In `reportFailure(AbstractSender, Exception)`, classify *before* taking the lock and pass it in:

```java
    void reportFailure(AbstractSender sender, Exception cause) {
        // Classified outside the lock: this calls into transport code, which must never run while the group's
        // lock is held.
        final boolean brokenConnection = sender == null || classifyQuietly(sender, cause);
        List<AbstractSender> toDestroy = Collections.emptyList();
        List<SenderExceptionListener> listeners = null;
        synchronized (lock) {
            locked.remove(sender);
            retired.remove(sender);
            if (!reconnecting) {
                toDestroy = failGroup(brokenConnection);
                listeners = new ArrayList<>(exceptionListeners);
            }
        }
```

(the remainder of `reportFailure` is unchanged)

Add the helper next to `failGroup`:

```java
    /**
     * Asks the sender to classify the failure. A classifier that throws is treated as "cannot tell", so a broken
     * implementation can never make the group behave differently than it did before classification existed.
     */
    private boolean classifyQuietly(AbstractSender sender, Exception cause) {
        try {
            return sender.isConnectionBroken(cause);
        } catch (RuntimeException e) {
            LOG.debug("Sender {} failed to classify a connection failure; assuming a broken connection.",
                sender.getName(), e);
            return true;
        }
    }
```

In `restartConnectInBackground(long)`, pass `true` — a startup timeout has no sender to ask, and an initial
connect that did not complete is itself evidence the endpoint was not reachable:

```java
            if (!reconnecting && !coordinator.isGroupConnected()) {
                toDestroy = failGroup(true);
                listeners = new ArrayList<>(exceptionListeners);
            }
```

Add the test accessor next to `exceptionListenerFireCountForTest()`:

```java
    /** Test-only: see {@link #outageIndicatesBrokenConnection}. */
    boolean outageIndicatesBrokenConnectionForTest() {
        synchronized (lock) {
            return outageIndicatesBrokenConnection;
        }
    }
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
mvn -pl njams-sdk test -Dtest=SenderRecoverySignalSpecTest
```

Expected: PASS, 4 tests.

- [ ] **Step 6: Run the baseline classes for the members just modified**

```bash
mvn -pl njams-sdk test -Dtest='SenderPoolAcquireSpecTest+SenderRetirementSpecTest+SharedSenderOutageSpecTest+SenderPoolTest+SenderConnectorStartupSpecTest'
```

Expected: PASS, unchanged from Task 0.

- [ ] **Step 7: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderPoolTestAccess.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderRecoverySignalSpecTest.java
git commit -m "SDK-473 Record whether an outage indicated a broken connection in SenderPool"
```

---

## Task 3: `SenderConnector` reports whether a connect attempt failed

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderConnector.java` — `runStartupConnect()`
  (~line 142, the `pool.onReconnected(sender)` at 149) and `runReconnectLoop(Exception)` (~line 211, the
  `pool.onReconnected(sender)` at 223)
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java` — `onReconnected` (~line 467)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderRecoverySignalSpecTest.java`
  (extend)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `void SenderPool.onReconnected(AbstractSender connected, boolean afterFailedConnectAttempt)`
  — replaces the one-argument form. Task 4 adds the fan-out that consumes the new parameter; here it is recorded
  only, so this task is behavior-neutral.

- [ ] **Step 1: Write the failing test**

Append to `SenderRecoverySignalSpecTest`:

```java
    @Test
    public void aReconnectThatSucceedsImmediatelyIsNotRecordedAsAFailedConnect() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        // Connect mode stays SUCCEED: the outage flips the group, but the very first reconnect attempt works —
        // the transient-failure-against-a-reachable-endpoint case.
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("one bad send"));
        assertTrue("the group must recover", pool.awaitRecovered(10, TimeUnit.SECONDS));
        assertFalse("a reconnect that succeeded on its first attempt is no evidence of unreachability",
            pool.recoveredAfterFailedConnectAttempt());
    }

    @Test
    public void aReconnectThatHadToRetryIsRecordedAsAFailedConnect() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("real loss"));
        assertTrue("the reconnect loop must have attempted at least one connect",
            LifecycleTestTransport.awaitConnectAttempts(1, 10, TimeUnit.SECONDS));
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue("the group must recover once connects succeed again", pool.awaitRecovered(10, TimeUnit.SECONDS));
        assertTrue("a reconnect that had to retry is evidence the endpoint was unreachable",
            pool.recoveredAfterFailedConnectAttempt());
    }

    @Test
    public void aStartupConnectIsNeverRecordedAsRecoveryFromAFailedConnect() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        assertTrue("the startup connect must succeed", pool.awaitStartup(10_000));
        assertFalse("a startup connect follows no outage at all",
            pool.recoveredAfterFailedConnectAttempt());
    }
```

Add the imports `java.util.concurrent.TimeUnit` and (already present) `assertFalse`/`assertTrue` at the top of the
file if not there yet.

Add to `SenderPoolTestAccess`:

```java
    /** @return whether the last publish to the pool followed at least one failed connect attempt. */
    public boolean recoveredAfterFailedConnectAttempt() {
        return pool.recoveredAfterFailedConnectAttemptForTest();
    }

    /** Polls until the group is connected again after an outage. */
    public boolean awaitRecovered(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (!pool.isConnectionFailure()) {
                return true;
            }
            Thread.sleep(25);
        }
        return !pool.isConnectionFailure();
    }

    public boolean awaitStartup(long timeoutMs) {
        pool.beginConnect();
        return pool.awaitStartup(timeoutMs);
    }
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -pl njams-sdk test -Dtest=SenderRecoverySignalSpecTest
```

Expected: COMPILATION FAILURE — `recoveredAfterFailedConnectAttemptForTest` does not exist.

- [ ] **Step 3: Add the parameter and record it**

In `SenderPool.java`, add the field beside `outageIndicatesBrokenConnection`:

```java
    /**
     * Whether the publish that made the group healthy again followed at least one failed connect attempt, i.e.
     * whether the endpoint was demonstrably unreachable. Only ever touched under {@link #lock}.
     */
    private boolean recoveredAfterFailedConnectAttempt = false;
```

Change `onReconnected`:

```java
    /**
     * Publishes a sender the {@link SenderConnector} has just connected: the group is healthy again, the sender
     * becomes available to the next caller, and everyone parked in {@link #acquire()} is woken.
     *
     * @param connected the freshly connected sender; ownership transfers to this pool.
     * @param afterFailedConnectAttempt whether the connector had to retry before this connect succeeded, which is
     *         the only reliable evidence that the endpoint really was unreachable.
     */
    void onReconnected(AbstractSender connected, boolean afterFailedConnectAttempt) {
        synchronized (lock) {
            recoveredAfterFailedConnectAttempt = afterFailedConnectAttempt;
            reconnecting = false;
            failed = false;
            lastPublished = connected;
            unlocked.add(connected);
            lock.notifyAll();
        }
    }
```

Add the accessor next to `outageIndicatesBrokenConnectionForTest()`:

```java
    /** Test-only: see {@link #recoveredAfterFailedConnectAttempt}. */
    boolean recoveredAfterFailedConnectAttemptForTest() {
        synchronized (lock) {
            return recoveredAfterFailedConnectAttempt;
        }
    }
```

Update the two Javadoc references to the old one-argument form — `SenderPool.java:64` and `:617`, and
`SenderConnector.java:40` — to `{@link SenderPool#onReconnected(AbstractSender, boolean)}`. A stale `{@link}`
is a hard Javadoc error, so this is not optional.

In `SenderConnector.runStartupConnect()`, replace line 149:

```java
            // A startup connect follows no outage, so it is never evidence that the endpoint was unreachable.
            pool.onReconnected(sender, false);
```

In `SenderConnector.runReconnectLoop(Exception)`, track the failures:

```java
    private void runReconnectLoop(Exception cause) {
        if (LOG.isInfoEnabled() && cause != null) {
            LOG.info("Initialized reconnect, because of: {}", getExceptionWithCauses(cause));
        }
        // Whether any attempt in this loop actually failed. A group that reconnects on its first attempt was
        // never unreachable — the failure that opened the outage came from something else (see SDK-474).
        boolean connectAttemptFailed = false;
        while (!coordinator.isGroupConnected() && !coordinator.shouldShutdown()) {
            AbstractSender sender = null;
            try {
                sender = createSender();
                sender.connect();
                if (coordinator.markConnected()) {
                    LOG.info("Reconnected sender {}", sender.getName());
                }
                pool.onReconnected(sender, connectAttemptFailed);
                return;
            } catch (Exception e) {
                connectAttemptFailed = true;
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
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
mvn -pl njams-sdk test -Dtest=SenderRecoverySignalSpecTest
```

Expected: PASS, 7 tests.

- [ ] **Step 5: Run the baseline classes and the Javadoc build**

```bash
mvn -pl njams-sdk test -Dtest='SenderPoolAcquireSpecTest+SenderRetirementSpecTest+SenderConnectorReconnectGatingSpecTest+SenderConnectorStartupSpecTest+SenderReconnectGatingSpecTest+MessageRetentionSpecTest+SenderDeadlockRegressionTest+SharedSenderOutageSpecTest'
mvn -pl njams-sdk javadoc:javadoc
```

Expected: tests PASS unchanged; Javadoc build with **no errors** (warnings tolerated).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderConnector.java njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderPoolTestAccess.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderRecoverySignalSpecTest.java
git commit -m "SDK-473 Record whether the group's reconnect had to retry before succeeding"
```

---

## Task 4: `SenderRecoveryListener` and the gated fan-out

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderRecoveryListener.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java` (~line 347, next to
  `addSenderExceptionListener`)
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderPoolTestAccess.java` (additive)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderRecoverySignalSpecTest.java`
  (extend)

**Interfaces:**
- Consumes: `SenderPool.outageIndicatesBrokenConnection` (Task 2),
  `SenderPool.onReconnected(AbstractSender, boolean)` (Task 3).
- Produces:
  - `public interface SenderRecoveryListener { void onSenderGroupRecovered(); }`
  - `void SenderPool.addSenderRecoveryListener(SenderRecoveryListener)`,
    `void SenderPool.removeSenderRecoveryListener(SenderRecoveryListener)`
  - `public void NjamsSender.addSenderRecoveryListener(SenderRecoveryListener)`,
    `public void NjamsSender.removeSenderRecoveryListener(SenderRecoveryListener)`
  Tasks 6–8 consume these.

- [ ] **Step 1: Write the failing test**

Append to `SenderRecoverySignalSpecTest`:

```java
    /** Counts recovery notifications reaching this listener. */
    private static final class CountingRecoveryListener implements SenderRecoveryListener {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void onSenderGroupRecovered() {
            count.incrementAndGet();
        }
    }

    @Test
    public void recoveryFromARealOutageSignalsEveryRegisteredListenerOnce() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        CountingRecoveryListener a = new CountingRecoveryListener();
        CountingRecoveryListener b = new CountingRecoveryListener();
        pool.addRecoveryListener(a);
        pool.addRecoveryListener(b);

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("real loss"));
        assertTrue(LifecycleTestTransport.awaitConnectAttempts(1, 10, TimeUnit.SECONDS));
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue(pool.awaitRecovered(10, TimeUnit.SECONDS));

        // Mirrors the shared-communications HTTP shape: one group, several dedicated receivers, each signalled.
        assertEquals("every registered listener is signalled exactly once per outage", 1, a.count.get());
        assertEquals("every registered listener is signalled exactly once per outage", 1, b.count.get());
    }

    @Test
    public void aTransientFailureAgainstAReachableEndpointSignalsNobody() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        CountingRecoveryListener listener = new CountingRecoveryListener();
        pool.addRecoveryListener(listener);

        // Connect mode stays SUCCEED, so the reconnect works on its first attempt.
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("one bad send"));
        assertTrue(pool.awaitRecovered(10, TimeUnit.SECONDS));

        assertEquals("a healthy receiver must not be cycled when the endpoint was reachable all along",
            0, listener.count.get());
    }

    @Test
    public void anOutageTheSenderRuledOutSignalsNobody() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        CountingRecoveryListener listener = new CountingRecoveryListener();
        pool.addRecoveryListener(listener);

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        pool.reportFailure(pool.newSenderRulingOutConnectionLoss(), new IllegalStateException("queue full"));
        assertTrue(LifecycleTestTransport.awaitConnectAttempts(1, 10, TimeUnit.SECONDS));
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue(pool.awaitRecovered(10, TimeUnit.SECONDS));

        assertEquals("classification and connect evidence are combined with AND, not OR",
            0, listener.count.get());
    }

    @Test
    public void aListenerRegisteredTwiceIsSignalledOnce() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        CountingRecoveryListener shared = new CountingRecoveryListener();
        // Mirrors two Njams instances registering the same shared receiver object.
        pool.addRecoveryListener(shared);
        pool.addRecoveryListener(shared);

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("real loss"));
        assertTrue(LifecycleTestTransport.awaitConnectAttempts(1, 10, TimeUnit.SECONDS));
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue(pool.awaitRecovered(10, TimeUnit.SECONDS));

        assertEquals("a shared receiver registered by several instances must be cycled once, not once per instance",
            1, shared.count.get());
    }

    @Test
    public void aRemovedListenerIsNotSignalled() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        CountingRecoveryListener listener = new CountingRecoveryListener();
        pool.addRecoveryListener(listener);
        pool.removeRecoveryListener(listener);

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("real loss"));
        assertTrue(LifecycleTestTransport.awaitConnectAttempts(1, 10, TimeUnit.SECONDS));
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue(pool.awaitRecovered(10, TimeUnit.SECONDS));

        assertEquals("a deregistered receiver must never be signalled again", 0, listener.count.get());
    }

    @Test
    public void aThrowingListenerDoesNotStopTheOthers() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        CountingRecoveryListener good = new CountingRecoveryListener();
        pool.addRecoveryListener(() -> {
            throw new IllegalStateException("misbehaving listener");
        });
        pool.addRecoveryListener(good);

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("real loss"));
        assertTrue(LifecycleTestTransport.awaitConnectAttempts(1, 10, TimeUnit.SECONDS));
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue(pool.awaitRecovered(10, TimeUnit.SECONDS));

        assertEquals("one misbehaving listener must not stop the others", 1, good.count.get());
    }
```

Add these imports to the test file: `static org.junit.Assert.assertEquals`,
`java.util.concurrent.atomic.AtomicInteger`, `com.im.njams.sdk.communication.SenderRecoveryListener`.

Add to `SenderPoolTestAccess`:

```java
    public void addRecoveryListener(SenderRecoveryListener listener) {
        pool.addSenderRecoveryListener(listener);
    }

    public void removeRecoveryListener(SenderRecoveryListener listener) {
        pool.removeSenderRecoveryListener(listener);
    }
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -pl njams-sdk test -Dtest=SenderRecoverySignalSpecTest
```

Expected: COMPILATION FAILURE — `SenderRecoveryListener` does not exist.

- [ ] **Step 3: Create the listener interface**

Create `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderRecoveryListener.java` with the exact
2026 Salesfive copyright header from `.claude/rules/code-quality-general.md`, followed by:

```java
package com.im.njams.sdk.communication;

/**
 * Callback for parties that need to know when a sender group has re-established its connection after a genuine
 * connection outage.
 * <p>
 * The SDK registers a client's receiver here, so a receiver that passively waits for messages — and can therefore
 * miss a connection loss entirely — re-verifies its own connection once the transport's endpoint is reachable
 * again. Called once per outage, on a background thread. Not part of the user-facing API; client code must not
 * call this directly.
 *
 * @since 6.0.0
 */
public interface SenderRecoveryListener {

    /**
     * Called after the sender group reconnected following an outage in which at least one connect attempt failed.
     */
    void onSenderGroupRecovered();
}
```

- [ ] **Step 4: Add the collection, the add/remove pair, and the gated fan-out to `SenderPool`**

Add the collection immediately after `exceptionListeners` (~line 98):

```java
    /**
     * Identity semantics for the same reason as {@link #exceptionListeners}: a shared receiver registered by
     * several {@code Njams} instances must be signalled once, not once per instance. Add-only would leak a
     * stopped instance's receiver into a JVM-wide shared group, hence {@link #removeSenderRecoveryListener}.
     */
    private final Collection<SenderRecoveryListener> recoveryListeners =
        Collections.newSetFromMap(new IdentityHashMap<>());
```

Add the registration pair next to `addSenderExceptionListener` (~line 156):

```java
    /**
     * Adds a listener notified once per outage, when the group's connection is re-established after at least one
     * failed connect attempt.
     *
     * @param listener the listener to add.
     */
    void addSenderRecoveryListener(SenderRecoveryListener listener) {
        synchronized (lock) {
            recoveryListeners.add(listener);
        }
    }

    /**
     * Removes a previously added recovery listener. Required because a shared group outlives the individual
     * clients using it.
     *
     * @param listener the listener to remove; unknown listeners are ignored.
     */
    void removeSenderRecoveryListener(SenderRecoveryListener listener) {
        synchronized (lock) {
            recoveryListeners.remove(listener);
        }
    }
```

Replace `onReconnected(AbstractSender, boolean)`'s body with the gated version:

```java
    void onReconnected(AbstractSender connected, boolean afterFailedConnectAttempt) {
        final List<SenderRecoveryListener> toNotify;
        synchronized (lock) {
            // Both halves of the gate, evaluated while the outage's state is still intact: the failure that
            // opened it looked like a broken connection, AND the connector really could not reach the endpoint.
            final boolean recoveredFromOutage = afterFailedConnectAttempt && outageIndicatesBrokenConnection;
            recoveredAfterFailedConnectAttempt = afterFailedConnectAttempt;
            reconnecting = false;
            failed = false;
            lastPublished = connected;
            unlocked.add(connected);
            lock.notifyAll();
            toNotify = recoveredFromOutage ? new ArrayList<>(recoveryListeners) : null;
            if (recoveredFromOutage) {
                recoveryListenerFireCount++;
            }
        }
        if (toNotify != null) {
            notifyRecovered(toNotify);
        }
    }

    /**
     * Signals the listeners <em>outside</em> {@link #lock}: a listener cycles a receiver's connection, which must
     * never run while the group's lock is held.
     */
    private void notifyRecovered(List<SenderRecoveryListener> listeners) {
        for (SenderRecoveryListener listener : listeners) {
            try {
                listener.onSenderGroupRecovered();
            } catch (RuntimeException e) {
                // One misbehaving listener must not stop the others from learning about the recovery.
                LOG.error("Sender recovery listener {} failed while handling the group's reconnect.",
                    listener.getClass().getName(), e);
            }
        }
    }
```

Add the counter field beside `listenerFireCount` and its accessor beside `exceptionListenerFireCountForTest()`:

```java
    private int recoveryListenerFireCount = 0;
```

```java
    /** Test-only: how often the recovery listeners were fired, i.e. how many outages passed the gate. */
    int recoveryListenerFireCountForTest() {
        synchronized (lock) {
            return recoveryListenerFireCount;
        }
    }
```

- [ ] **Step 5: Add the `NjamsSender` delegation**

In `NjamsSender.java`, next to `addSenderExceptionListener` (~line 347). Note the existing
`addSenderExceptionListener` has no Javadoc despite being public; add one while here, since a public member
without Javadoc is a checkstyle violation and this is a doc-only fix:

```java
    /**
     * Adds a listener notified whenever a message could not be sent through this group.
     *
     * @param listener the listener to add.
     * @throws IllegalStateException if this sender was not initialized.
     */
    public void addSenderExceptionListener(SenderExceptionListener listener) {
        if (senderPool == null) {
            throw new IllegalStateException("Sender not initialized.");
        }
        senderPool.addSenderExceptionListener(listener);
    }

    /**
     * Adds a listener notified once whenever this group recovers from a connection outage.
     *
     * @param listener the listener to add.
     * @throws IllegalStateException if this sender was not initialized.
     * @since 6.0.0
     */
    public void addSenderRecoveryListener(SenderRecoveryListener listener) {
        if (senderPool == null) {
            throw new IllegalStateException("Sender not initialized.");
        }
        senderPool.addSenderRecoveryListener(listener);
    }

    /**
     * Removes a listener added with {@link #addSenderRecoveryListener(SenderRecoveryListener)}. A shared group
     * outlives the clients using it, so a stopped client must take its listener with it.
     *
     * @param listener the listener to remove; unknown listeners and an uninitialized sender are ignored.
     * @since 6.0.0
     */
    public void removeSenderRecoveryListener(SenderRecoveryListener listener) {
        if (senderPool != null) {
            senderPool.removeSenderRecoveryListener(listener);
        }
    }
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
mvn -pl njams-sdk test -Dtest=SenderRecoverySignalSpecTest
```

Expected: PASS, 13 tests.

- [ ] **Step 7: Run the baseline classes, checkstyle and Javadoc**

```bash
mvn -pl njams-sdk test -Dtest='SenderPoolAcquireSpecTest+SenderRetirementSpecTest+SharedSenderOutageSpecTest+SenderPoolTest+NjamsSenderTest+SenderDeadlockRegressionTest+MessageRetentionSpecTest'
mvn -pl njams-sdk validate -Pcheckstyle
mvn -pl njams-sdk javadoc:javadoc
```

Expected: all PASS / BUILD SUCCESS with no Javadoc errors.

- [ ] **Step 8: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderRecoveryListener.java njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderPoolTestAccess.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderRecoverySignalSpecTest.java
git commit -m "SDK-473 Signal registered listeners once when a sender group recovers from a real outage"
```

---

## Task 5: `AbstractReceiver` cycles its connection on the signal

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java` — the `class`
  declaration (~line 50) and a new method after `onException(Exception)` (~line 355)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverRecoveryCycleSpecTest.java`
  (create)

**Interfaces:**
- Consumes: `SenderRecoveryListener` (Task 4).
- Produces: `public void AbstractReceiver.onSenderGroupRecovered()` — the guarded cycle. Task 6 registers the
  receiver as the listener; Task 7's end-to-end tests drive it through a real `Njams`.

- [ ] **Step 1: Write the failing test**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverRecoveryCycleSpecTest.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.Test;

/**
 * Specifies what a receiver does with SDK-473's recovery signal: a connected receiver cycles its connection, and
 * a receiver whose own lifecycle already owns its state ignores the signal entirely.
 */
public class ReceiverRecoveryCycleSpecTest extends AbstractLifecycleSpecTest {

    private static void awaitTrue(String message, long timeoutMs, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting: " + message, e);
            }
        }
        assertTrue(message, condition.getAsBoolean());
    }

    /** A receiver connected through its normal background startup path. */
    private LifecycleTestReceiver connectedReceiver() {
        LifecycleTestReceiver receiver = new LifecycleTestReceiver();
        receiver.beginConnect();
        awaitTrue("the receiver must connect", 5000, receiver::isConnected);
        return receiver;
    }

    @Test
    public void aConnectedReceiverCyclesItsConnection() {
        LifecycleTestReceiver receiver = connectedReceiver();
        int connectsBefore = LifecycleTestTransport.receiverConnectCount();

        receiver.onSenderGroupRecovered();

        awaitTrue("the receiver must reconnect after being cycled", 5000,
            () -> LifecycleTestTransport.receiverConnectCount() > connectsBefore && receiver.isConnected());
        assertTrue("the cycle must have gone through stop()", receiver.callOrder().contains("stop()"));
    }

    @Test
    public void aReceiverThatWasNeverConnectedIgnoresTheSignal() {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.FAIL);
        LifecycleTestReceiver receiver = new LifecycleTestReceiver();
        int connectsBefore = LifecycleTestTransport.receiverConnectCount();

        receiver.onSenderGroupRecovered();

        assertEquals("a receiver that never connected must be left to its own startup/reconnect path",
            connectsBefore, LifecycleTestTransport.receiverConnectCount());
    }

    @Test
    public void aShuttingDownReceiverIgnoresTheSignal() {
        LifecycleTestReceiver receiver = connectedReceiver();
        receiver.setShouldShutdown(true);
        int connectsBefore = LifecycleTestTransport.receiverConnectCount();

        receiver.onSenderGroupRecovered();

        assertEquals("a receiver being torn down must not be revived by the signal",
            connectsBefore, LifecycleTestTransport.receiverConnectCount());
    }

    @Test
    public void aReceiverAlreadyReconnectingIgnoresTheSignal() throws Exception {
        LifecycleTestReceiver receiver = connectedReceiver();
        // Park the receiver's own reconnect loop inside connect(), so it is demonstrably in flight.
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.BLOCK);
        // Capture the latch BEFORE triggering: onReceiverConnect() counts the current latch down and immediately
        // replaces it, so a latch fetched afterwards may be the fresh, uncounted one and would never fire.
        CountDownLatch reconnectEnteredConnect = LifecycleTestTransport.receiverConnectAttemptedLatch();
        receiver.onException(new IllegalStateException("its own detected loss"));
        assertTrue("the receiver's own reconnect must have entered connect()",
            reconnectEnteredConnect.await(5, TimeUnit.SECONDS));
        int connectsBefore = LifecycleTestTransport.receiverConnectCount();

        receiver.onSenderGroupRecovered();

        assertEquals("a receiver already running its own reconnect must not be cycled a second time",
            connectsBefore, LifecycleTestTransport.receiverConnectCount());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -pl njams-sdk test -Dtest=ReceiverRecoveryCycleSpecTest
```

Expected: COMPILATION FAILURE — `onSenderGroupRecovered` does not exist.

- [ ] **Step 3: Implement the guarded cycle**

Change the class declaration in `AbstractReceiver.java`:

```java
public abstract class AbstractReceiver implements Receiver, SenderRecoveryListener {
```

Add the method after `onException(Exception)`:

```java
    /**
     * {@inheritDoc}
     * <p>
     * Cycles this receiver's connection: a receiver that passively waits for messages can miss a connection loss
     * entirely, so the sender group's recovery is taken as the moment to re-verify. Does nothing while this
     * receiver is shutting down, has never been connected, is currently connecting, or is already running its own
     * reconnect — in each of those cases its own lifecycle already owns its connection state.
     *
     * @since 6.0.0
     */
    @Override
    public void onSenderGroupRecovered() {
        // wasEverConnected() alone is not enough: it is set by beginConnect()/reconnect(), but not by a plain
        // start(), so a receiver started directly would look like it had never connected.
        final boolean neverConnected = !coordinator.wasEverConnected() && !isConnected();
        if (coordinator.shouldShutdown() || neverConnected || isConnecting() || isReconnectInFlight()) {
            LOG.debug("Receiver {}: ignoring the sender group's recovery; this receiver's own connection "
                + "lifecycle already owns its state.", getName());
            return;
        }
        LOG.info("Receiver {}: cycling the connection because the sender group recovered from a connection "
            + "outage.", getName());
        onException(new NjamsSdkRuntimeException(
            "Cycling the receiver after the sender group recovered from a connection outage"));
    }

    /** @return {@code true} while this receiver's own reconnect loop is running. */
    private boolean isReconnectInFlight() {
        final Thread rc = reconnectThread;
        return rc != null && rc.isAlive();
    }
```

Add the import for `SenderRecoveryListener` only if the file needs it — it is in the same package, so it does
not. `NjamsSdkRuntimeException` is already imported.

- [ ] **Step 4: Run the test to verify it passes**

```bash
mvn -pl njams-sdk test -Dtest=ReceiverRecoveryCycleSpecTest
```

Expected: PASS, 4 tests.

- [ ] **Step 5: Run the receiver baseline classes**

```bash
mvn -pl njams-sdk test -Dtest='AbstractReceiverTest+AbstractReceiverStaticStateTest+ReceiverStartupSpecTest+ReceiverShutdownSpecTest+ReceiverStartGatingSpecTest+ReceiverLoggingSpecTest+SharedReceiverRestartSpecTest+SharedReceiverSupportTest'
```

Expected: PASS, unchanged from Task 0.

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/ReceiverRecoveryCycleSpecTest.java
git commit -m "SDK-473 Cycle a connected receiver when its sender group recovers from an outage"
```

---

## Task 6: Wire the receiver in, and deregister it on stop

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java` — `startReceiver(NjamsSender)` (~line 698-712),
  `stopReceiverAfterStartupFailure(Receiver)` (~line 819-846) and its call site (~line 780), `stop()` (~line
  899-921)
- Test: covered end-to-end by Task 7; this task's own verification is the existing `Njams` baseline plus the
  compile.

**Interfaces:**
- Consumes: `NjamsSender.addSenderRecoveryListener`/`removeSenderRecoveryListener` (Task 4),
  `AbstractReceiver implements SenderRecoveryListener` (Task 5).
- Produces: `private void Njams.stopReceiverAfterStartupFailure(Receiver failedReceiver, NjamsSender activeSender)`
  — signature change on a private method. No public surface changes.

- [ ] **Step 1: Register the receiver in `startReceiver`**

In `startReceiver(NjamsSender activeSender)`, directly after the existing `SenderExceptionListener` branch:

```java
            if (receiver instanceof SenderExceptionListener && activeSender != null) {
                activeSender.addSenderExceptionListener((SenderExceptionListener) receiver);
            }
            if (receiver instanceof SenderRecoveryListener && activeSender != null) {
                // The receiver object itself is the listener, never a lambda: the pool's listener set has identity
                // semantics, so a shared receiver registered by several instances collapses to one entry (one
                // cycle per outage), while dedicated per-instance receivers on a shared group each get their own.
                activeSender.addSenderRecoveryListener((SenderRecoveryListener) receiver);
            }
```

Update that method's Javadoc, which currently says only "if the receiver reports send exceptions, registers it as
a listener on the given sender group" — it must also mention the recovery registration, and that `stop()` removes
it again.

Add the import `com.im.njams.sdk.communication.SenderRecoveryListener` to `Njams.java`.

- [ ] **Step 2: Deregister in `stop()`**

In `stop()`, inside `if (receiver != null) { … }`, after the existing `if (reallyStopped && receiver instanceof
AbstractReceiver) { … }` block:

```java
            if (reallyStopped && receiver instanceof SenderRecoveryListener && sender != null) {
                // A shared sender group outlives the instances using it: leaving a stopped receiver registered
                // would keep it referenced for the group's lifetime and signal it on every later outage. Gated on
                // reallyStopped so a shared receiver a sibling still uses stays registered. Removing after
                // sender.close() is safe — close() shuts the pool down but keeps it reachable.
                sender.removeSenderRecoveryListener((SenderRecoveryListener) receiver);
            }
```

- [ ] **Step 3: Deregister in the startup-failure path**

Change the signature and add the same block to `stopReceiverAfterStartupFailure`:

```java
    private void stopReceiverAfterStartupFailure(Receiver failedReceiver, NjamsSender activeSender) {
```

…and after its own `if (reallyStopped && failedReceiver instanceof AbstractReceiver) { … }` block:

```java
            if (reallyStopped && failedReceiver instanceof SenderRecoveryListener && activeSender != null) {
                // Same reasoning as in stop(): a shared group must not keep a dead receiver registered.
                activeSender.removeSenderRecoveryListener((SenderRecoveryListener) failedReceiver);
            }
```

Update the call site in `start()` (~line 780) to `stopReceiverAfterStartupFailure(receiver, activeSender);`, and
extend that method's Javadoc with a sentence on the deregistration.

- [ ] **Step 4: Compile and run the `Njams` baseline**

```bash
mvn -pl njams-sdk test -Dtest='NjamsTest+NjamsFacadeBaselineTest+ReceiverStartupSpecTest+ReceiverStartGatingSpecTest+ReceiverShutdownSpecTest+SharedReceiverRestartSpecTest+SenderCloseOrderingSpecTest'
```

Expected: PASS, unchanged from Task 0.

- [ ] **Step 5: Checkstyle and Javadoc**

```bash
mvn -pl njams-sdk validate -Pcheckstyle
mvn -pl njams-sdk javadoc:javadoc
```

Expected: BUILD SUCCESS, no Javadoc errors.

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git commit -m "SDK-473 Register and deregister the receiver as its sender group's recovery listener"
```

---

## Task 7: Shared-group end-to-end and deregistration specs

**Files:**
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SharedSenderRecoverySignalSpecTest.java`
  (create)

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces: nothing consumed later.

**Coverage note — one case is deliberately not end-to-end.** The `shared=true` + HTTP shape (one JVM-wide group,
N *dedicated* receivers) cannot be built with this harness: `SharedLifecycleTestReceiver` is registered under the
same transport name as `LifecycleTestReceiver`, so `CommunicationFactory.findReceiverType(name, true)` always
selects the shareable one. That shape is covered at unit level instead, by Task 4's
`recoveryFromARealOutageSignalsEveryRegisteredListenerOnce` (two distinct listeners, one group, each signalled
once). Do not add a second fake transport just to close this gap — but do not quietly drop the note either.

- [ ] **Step 1: Write the test**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SharedSenderRecoverySignalSpecTest.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Specifies SDK-473 end-to-end under shared communications: one outage of the JVM-wide sender group cycles the
 * one shared receiver exactly once, and a stopped {@code Njams} instance takes its receiver out of the group's
 * listener set instead of leaving it registered for the group's lifetime.
 */
public class SharedSenderRecoverySignalSpecTest extends AbstractLifecycleSpecTest {

    private final List<NjamsSender> taken = new ArrayList<>();
    private final List<Njams> clients = new ArrayList<>();

    /**
     * The shared sender is a reference-counted JVM-wide static: it must be closed exactly as often as it was
     * taken, or it leaks into later tests. See {@code SharedSenderOutageSpecTest}'s equivalent teardown.
     */
    @After
    public void releaseClientsAndSharedSenders() {
        clients.forEach(c -> {
            if (c.isStarted()) {
                c.stop();
            }
        });
        clients.clear();
        taken.forEach(NjamsSender::close);
        taken.clear();
    }

    private static Settings sharedSettings() {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
        s.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "2");
        return s;
    }

    /** Takes a test-held reference on the shared group, so it survives every client stopping. */
    private NjamsSender takeSharedSender() {
        NjamsSender sender =
            NjamsSender.takeSharedSender(ClientSettings.from(sharedSettings().getAllProperties()));
        taken.add(sender);
        return sender;
    }

    private Njams startClient(String name) {
        Njams njams = new Njams(Path.of("test", name), "1.0", "test", sharedSettings());
        clients.add(njams);
        assertTrue("the client must start", njams.start());
        return njams;
    }

    private static void awaitTrue(String message, long timeoutMs, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting: " + message, e);
            }
        }
        assertTrue(message, condition.getAsBoolean());
    }

    /**
     * Drives one real outage through the given group: a send fails, the group's reconnect loop fails at least
     * once (so the evidence gate is satisfied), then connects again.
     */
    private void driveOutageAndRecovery(NjamsSender group) throws InterruptedException {
        LifecycleTestTransport.armSendBlocksThenFails();
        group.send(new LogMessage(), "session");
        assertTrue("a send must reach the transport",
            LifecycleTestTransport.sendEnteredLatch().await(5, TimeUnit.SECONDS));

        int connectsBeforeOutage = LifecycleTestTransport.senderConnectCount();
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        LifecycleTestTransport.releaseSend();
        LifecycleTestTransport.disarmSendFailure();

        assertTrue("the group's reconnect must fail at least once, or the evidence gate is not exercised",
            LifecycleTestTransport.awaitConnectAttempts(connectsBeforeOutage + 1, 15, TimeUnit.SECONDS));
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
    }

    @Test
    public void oneOutageCyclesTheSharedReceiverExactlyOnce() throws Exception {
        NjamsSender group = takeSharedSender();
        startClient("sharedRecoveryFirst");
        startClient("sharedRecoverySecond");

        SharedLifecycleTestReceiver receiver = SharedLifecycleTestReceiver.lastCreated();
        assertNotNull("both clients must share one receiver instance", receiver);
        awaitTrue("the shared receiver must connect", 5000, receiver::isConnected);
        int receiverConnectsBefore = LifecycleTestTransport.receiverConnectCount();

        driveOutageAndRecovery(group);

        awaitTrue("the shared receiver must be cycled once the group recovers", 20_000,
            () -> LifecycleTestTransport.receiverConnectCount() > receiverConnectsBefore);
        awaitTrue("the shared receiver must be connected again after the cycle", 10_000, receiver::isConnected);
        assertEquals("a shared receiver registered by two clients must be cycled once, not twice",
            receiverConnectsBefore + 1, LifecycleTestTransport.receiverConnectCount());
    }

    @Test
    public void aStoppedClientsReceiverIsNoLongerCycled() throws Exception {
        NjamsSender group = takeSharedSender();
        Njams first = startClient("sharedRecoveryStopFirst");
        Njams second = startClient("sharedRecoveryStopSecond");

        SharedLifecycleTestReceiver receiver = SharedLifecycleTestReceiver.lastCreated();
        assertNotNull(receiver);
        awaitTrue("the shared receiver must connect", 5000, receiver::isConnected);

        // Stopping one of two users must NOT deregister the shared receiver: the sibling still uses it.
        first.stop();
        int connectsAfterFirstStop = LifecycleTestTransport.receiverConnectCount();
        driveOutageAndRecovery(group);
        awaitTrue("a shared receiver a sibling still uses must still be cycled", 20_000,
            () -> LifecycleTestTransport.receiverConnectCount() > connectsAfterFirstStop);
        awaitTrue("the shared receiver must be connected again", 10_000, receiver::isConnected);

        // Stopping the last user really stops the receiver, and must take it out of the group's listener set.
        second.stop();
        int connectsAfterLastStop = LifecycleTestTransport.receiverConnectCount();
        int successfulSendsBefore = LifecycleTestTransport.successfulSendCount();
        LifecycleTestTransport.rearmSendGate();
        driveOutageAndRecovery(group);
        // Proving the group really recovered — rather than asserting a condition that is already true — is what
        // makes the receiver-count assertion below meaningful: the message retained across the outage is sent by
        // the fresh sender only once the group is connected again.
        assertTrue("the group must recover a second time and deliver the retained message",
            LifecycleTestTransport.awaitSuccessfulSends(successfulSendsBefore + 1, 20, TimeUnit.SECONDS));
        assertEquals("a stopped client's receiver must no longer be cycled by the group it left",
            connectsAfterLastStop, LifecycleTestTransport.receiverConnectCount());
    }
}
```

- [ ] **Step 2: Run the test**

```bash
mvn -pl njams-sdk test -Dtest=SharedSenderRecoverySignalSpecTest
```

Expected: PASS, 2 tests. If `oneOutageCyclesTheSharedReceiverExactlyOnce` reports **two** cycles, the
registration went in as a per-instance lambda instead of the receiver object — re-check Task 6 Step 1 against
D473.7 rather than relaxing the assertion.

- [ ] **Step 3: Run the whole lifecycle package plus the shared baseline**

```bash
mvn -pl njams-sdk test -Dtest='com.im.njams.sdk.communication.lifecycle.*Test+SharedSenderOutageSpecTest+SharedReceiverSupportTest+CommunicationFactoryTest'
```

Expected: PASS. Watch specifically for cross-test pollution: the shared sender is a JVM-wide static, so a leak
here shows up as a *different* test failing.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SharedSenderRecoverySignalSpecTest.java
git commit -m "SDK-473 Add shared-communications specs for the receiver recovery signal"
```

---

## Task 8: Log the dedicated-receiver fallback at INFO, not WARN

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/CommunicationFactory.java:109-123`
  (`findReceiverType`, the fallback branch at 115-120)
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/CommunicationFactoryTest.java` (one new method,
  additive)

**Interfaces:**
- Consumes: nothing. Independent of Tasks 1-7 — it may be executed before or after them.
- Produces: nothing consumed later.

**Why (D473.11).** HTTP/SSE has no shareable receiver *by design*: a shared receiver brings no benefit for
HTTP/SSE, so it was deliberately never built. The current WARN reports that intended, entirely normal
configuration outcome as if something were wrong, and its wording ("does not support sharing … Creating a
dedicated instance instead") reads as a shortcoming rather than a design decision. INFO is sufficient.

This surfaced while verifying SDK-473's shared-mode paths, and it is a one-line logging correction to a behavior
this ticket's design depends on understanding, so it rides along here rather than becoming its own ticket. If you
would rather it were tracked separately, say so before this task is executed — do not create a ticket for it
unprompted.

**Note on `njams-safe-modification`:** the log statement being changed has no existing test coverage (verified: no
test in the module asserts on this message or its level). The new test in Step 1 *is* that coverage, and it is
written to pass against the new level, so run it against the old code first to see it fail — that failure is the
proof it actually observes the statement rather than passing vacuously.

- [ ] **Step 1: Write the failing test**

Append to `njams-sdk/src/test/java/com/im/njams/sdk/communication/CommunicationFactoryTest.java`, directly after
the existing `httpReceiverWithSharingRequestedFallsBackToHttpSseReceiver()` method (which already sets up exactly
this scenario). Add the nested appender class at the end of the class body — `ReceiverLoggingSpecTest` has its own
`private static final class CapturingAppender`, so this follows the established pattern rather than sharing one:

```java
    @Test
    public void theDedicatedReceiverFallbackIsLoggedAtInfoNotWarn() {
        Logger factoryLogger = Logger.getLogger(CommunicationFactory.class);
        CapturingAppender appender = new CapturingAppender();
        Level originalLevel = factoryLogger.getLevel();
        factoryLogger.addAppender(appender);
        factoryLogger.setLevel(Level.DEBUG);
        try {
            Settings settings = createSettings("HTTP");
            settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, "http://localhost:8080/njams/");
            settings.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
            new CommunicationFactory(settings).getReceiver(njams);

            List<LoggingEvent> aboutSharing = appender.events().stream()
                .filter(e -> String.valueOf(e.getRenderedMessage()).contains("dedicated receiver instance"))
                .collect(Collectors.toList());
            assertEquals("the fallback must be reported exactly once", 1, aboutSharing.size());
            assertEquals("a deliberate design decision must not be logged as a warning",
                Level.INFO, aboutSharing.get(0).getLevel());
        } finally {
            factoryLogger.removeAppender(appender);
            factoryLogger.setLevel(originalLevel);
        }
    }
```

…and at the end of the class:

```java
    /** Captures log events so a test can assert on their level. Mirrors {@code ReceiverLoggingSpecTest}'s. */
    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<LoggingEvent> events = new CopyOnWriteArrayList<>();

        List<LoggingEvent> events() {
            return events;
        }

        @Override
        protected void append(LoggingEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
            // nothing to release
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
```

Add these imports to `CommunicationFactoryTest.java`: `static org.junit.Assert.assertEquals`, `java.util.List`,
`java.util.concurrent.CopyOnWriteArrayList`, `java.util.stream.Collectors`, `org.apache.log4j.AppenderSkeleton`,
`org.apache.log4j.Level`, `org.apache.log4j.Logger`, `org.apache.log4j.spi.LoggingEvent`.

Note the test restores the logger's original level in a `finally` block: the log4j `Logger` is a JVM-wide shared
object, so leaving `DEBUG` installed would leak into every later test in the same JVM (see
`.claude/rules/testing-conventions.md` → "Test Isolation for Shared State").

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -pl njams-sdk test -Dtest=CommunicationFactoryTest#theDedicatedReceiverFallbackIsLoggedAtInfoNotWarn
```

Expected: FAIL on the *first* assertion, `expected:<1> but was:<0>` — the current message does not contain
"dedicated receiver instance". This confirms the test really observes the statement.

- [ ] **Step 3: Change the level and the wording**

In `CommunicationFactory.findReceiverType`, replace the fallback branch:

```java
        if (found == null) {
            found = receivers.find(r -> r.getName().equalsIgnoreCase(name));
            if (wantsSharable && found != null) {
                LOG.info("Communication type '{}' uses a dedicated receiver instance per client; sharing applies "
                    + "to the sender only.", found.getName());
            }
        }
```

The wording states what happens, without implying a missing capability. Do not change anything else in this
method — the selection logic itself is correct and is relied upon by `CommunicationFactoryTest`'s existing
`httpReceiverWithSharingRequestedFallsBackToHttpSseReceiver`.

- [ ] **Step 4: Run the test to verify it passes**

```bash
mvn -pl njams-sdk test -Dtest=CommunicationFactoryTest
```

Expected: PASS, all methods in the class including the pre-existing ones.

- [ ] **Step 5: Check for stale references to the old wording**

```bash
grep -rn "does not support sharing" njams-sdk/src wiki docs
```

Expected: no hits. If the FAQ or a wiki page quoted the old WARN, update it in the same commit.

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/CommunicationFactory.java njams-sdk/src/test/java/com/im/njams/sdk/communication/CommunicationFactoryTest.java
git commit -m "SDK-473 Log the dedicated-receiver fallback at INFO instead of WARN"
```

---

## Task 9: Prove sharing is honored when a shareable receiver exists

**Files:**
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SharedReceiverSelectionSpecTest.java`
  (create)

**Interfaces:**
- Consumes: Task 8's new INFO message (one test asserts it does *not* fire for the honored case). Execute after
  Task 8; independent of Tasks 1-7.
- Produces: nothing consumed later.

**Why this is not already covered.** Verified against the live tests before adding this task:

| Existing test | What it covers | Why it is not coverage for this |
|---|---|---|
| `CommunicationFactoryTest` (7 tests) | HTTP with and without sharing requested, alternative keys, failures | No test at all for `shared=true` where a shareable receiver **does** exist |
| `SharedReceiverRestartSpecTest.firstOfTwoSharedUsersStoppingDoesNotShutDownTheSharedReceiver` | non-last-user `stop()` does not shut the receiver down | **Would still pass without sharing.** It reads one instance via `lastCreated()`; if each `Njams` had received its *own* `SharedLifecycleTestReceiver`, `lastCreated()` would be njamsB's, njamsA's `stop()` would stop njamsA's own instance, and njamsB's would still be connected and reconnectable — every assertion still green |
| `SharedReceiverRestartSpecTest.newInstanceAfterLastSharedUserStoppedGetsAFreshConnectedReceiver` | cache eviction after the last user stops | Asserts `assertNotSame` across a stop boundary — the opposite property; says nothing about two concurrent users |
| `SharedReceiverSupportTest` | `SharedReceiverSupport`'s own instance registry | Unit-tests the helper directly; never goes through `CommunicationFactory`, so it cannot show that the factory hands the *same* instance to two clients |
| `SharedSenderOutageSpecTest` | shared **sender** group via `takeSharedSender` | Sender side only; registers no receiver |

So the single property this plan leans on hardest under `shared=true` — one receiver instance for all clients,
hence one listener registration, hence one cycle per outage (D473.7) — is currently *assumed* everywhere and
*asserted* nowhere. Task 7's `oneOutageCyclesTheSharedReceiverExactlyOnce` does imply it (two receivers would be
cycled twice), but it conflates the sharing property with the dedupe property under test; if sharing silently
broke, that test's failure would point at the wrong thing. This task pins the property down on its own.

- [ ] **Step 1: Write the test**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SharedReceiverSelectionSpecTest.java`:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.Receiver;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Specifies what {@code njams.sdk.communication.shared=true} actually yields on the receiver side: for a transport
 * that has a shareable receiver, every client gets the very same instance, and that instance tracks all of them.
 * Nothing else in the suite asserts this — the other shared-receiver tests infer it from behaviour that would also
 * hold if each client had its own instance — yet SDK-473's "one cycle per outage for a shared receiver" (D473.7)
 * depends on it entirely.
 */
public class SharedReceiverSelectionSpecTest extends AbstractLifecycleSpecTest {

    @After
    public void clearSharedReceiverState() {
        // Both statics must be reset: the factory's cache, and the test receiver's own instance registry that the
        // lifecycle tests read through lastCreated(). Mirrors SharedReceiverRestartSpecTest's teardown.
        SharedLifecycleTestReceiver.clearInstanceRegistry();
        CommunicationFactory.clearSharedReceiversForTesting();
    }

    private static Settings settings(boolean shared) {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, String.valueOf(shared));
        return s;
    }

    /** A distinct client, since the shared receiver keys its registered instances by client path. */
    private static Njams client(String name) {
        Njams njams = mock(Njams.class);
        when(njams.getClientPath()).thenReturn(Path.of("test", name));
        return njams;
    }

    @Test
    public void everyClientGetsTheSameShareableReceiverInstance() {
        CommunicationFactory factory = new CommunicationFactory(settings(true));

        Receiver first = factory.getReceiver(client("sharedSelectionA"));
        Receiver second = factory.getReceiver(client("sharedSelectionB"));

        assertTrue("sharing must select the shareable receiver implementation",
            first instanceof SharedLifecycleTestReceiver);
        assertSame("both clients must be handed the very same receiver instance", first, second);
    }

    @Test
    public void theSharedInstanceTracksEveryClientThatTookIt() {
        CommunicationFactory factory = new CommunicationFactory(settings(true));
        Njams a = client("sharedTrackingA");
        Njams b = client("sharedTrackingB");

        ShareableReceiver<?> receiver = (ShareableReceiver<?>) factory.getReceiver(a);
        factory.getReceiver(b);

        // removeNjams reports "this was the last user". That it is false for the first and true for the second is
        // what proves both clients were really registered with this one instance, rather than each having its own.
        assertFalse("the first of two registered clients leaving must not be the last user", receiver.removeNjams(a));
        assertTrue("the second client leaving must be the last user", receiver.removeNjams(b));
    }

    @Test
    public void withoutSharingEachClientGetsItsOwnDedicatedReceiver() {
        CommunicationFactory factory = new CommunicationFactory(settings(false));

        Receiver first = factory.getReceiver(client("dedicatedSelectionA"));
        Receiver second = factory.getReceiver(client("dedicatedSelectionB"));

        // SharedLifecycleTestReceiver extends LifecycleTestReceiver, so the meaningful check is the interface.
        assertFalse("without sharing the shareable implementation must not be selected",
            first instanceof ShareableReceiver);
        assertNotSame("each client must get its own instance", first, second);
    }

    @Test
    public void honoredSharingLogsNoDedicatedFallbackMessage() {
        Logger factoryLogger = Logger.getLogger(CommunicationFactory.class);
        CapturingAppender appender = new CapturingAppender();
        Level originalLevel = factoryLogger.getLevel();
        factoryLogger.addAppender(appender);
        factoryLogger.setLevel(Level.DEBUG);
        try {
            new CommunicationFactory(settings(true)).getReceiver(client("sharedNoFallbackLog"));

            long fallbackMessages = appender.events().stream()
                .filter(e -> String.valueOf(e.getRenderedMessage()).contains("dedicated receiver instance"))
                .count();
            assertEquals("the dedicated-receiver fallback must not be reported when sharing was honored",
                0, fallbackMessages);
        } finally {
            // log4j Loggers are JVM-wide: leaving DEBUG installed would leak into every later test.
            factoryLogger.removeAppender(appender);
            factoryLogger.setLevel(originalLevel);
        }
    }

    /** Captures log events so a test can assert on their absence. Mirrors {@code ReceiverLoggingSpecTest}'s. */
    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<LoggingEvent> events = new CopyOnWriteArrayList<>();

        List<LoggingEvent> events() {
            return events;
        }

        @Override
        protected void append(LoggingEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
            // nothing to release
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
```

Two things to know before running it: `getReceiver(...)` on this transport only constructs, `init`s and
`setNjams`es the receiver — it never connects — so a mocked `Njams` is sufficient and no teardown of connections
is needed beyond what `AbstractLifecycleSpecTest` already does. And `ClientSettings` is imported only if the
final code needs it; drop the import if not (checkstyle flags unused imports).

- [ ] **Step 2: Run the test**

```bash
mvn -pl njams-sdk test -Dtest=SharedReceiverSelectionSpecTest
```

Expected: PASS, 4 tests. These assert current, already-correct behavior — they are regression pins, not a
red-green cycle, which is why there is no "verify it fails" step. If any of them fails, sharing is genuinely
broken today and that is a finding to report before continuing, not something to adjust the test around.

- [ ] **Step 3: Prove the pins actually bite**

A regression pin that cannot fail is worthless. Confirm each one observes something real by temporarily inverting
the production condition — in `CommunicationFactory.createReceiver`, change
`if (shared && ShareableReceiver.class.isAssignableFrom(clazz))` to `if (false && …)`:

```bash
mvn -pl njams-sdk test -Dtest=SharedReceiverSelectionSpecTest
```

Expected: `everyClientGetsTheSameShareableReceiverInstance` and `theSharedInstanceTracksEveryClientThatTookIt`
FAIL. Then **revert the production edit** (`git checkout -- njams-sdk/src/main/java/com/im/njams/sdk/communication/CommunicationFactory.java`
— careful, that also discards Task 8's change if it is already committed only in the working tree; prefer
`git diff` to confirm the tree is clean afterwards) and re-run to green.

- [ ] **Step 4: Run the shared-receiver neighbourhood for cross-test pollution**

The factory's shared-receiver cache and `SharedLifecycleTestReceiver`'s registry are JVM-wide statics, so a leak
here shows up as a *different* test failing:

```bash
mvn -pl njams-sdk test -Dtest='SharedReceiverSelectionSpecTest+SharedReceiverRestartSpecTest+SharedSenderRecoverySignalSpecTest+SharedReceiverSupportTest+CommunicationFactoryTest+ReceiverStartupSpecTest'
```

Expected: all PASS.

- [ ] **Step 5: Soften Task 7's overclaiming assertion message**

In `SharedSenderRecoverySignalSpecTest.oneOutageCyclesTheSharedReceiverExactlyOnce`, the message
`"both clients must share one receiver instance"` overstates what `assertNotNull` checks. Change it to:

```java
        assertNotNull("a shared receiver instance must have been created and wired", receiver);
```

The sharing property itself is pinned by this task instead. Nothing else in that test changes.

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SharedReceiverSelectionSpecTest.java njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SharedSenderRecoverySignalSpecTest.java
git commit -m "SDK-473 Pin down that shared communications really yields one receiver instance per JVM"
```

---

## Task 10: Correct the FAQ's receiver-independence claim

**Files:**
- Modify: `wiki/FAQ.md` — the "**The receiver is independent and never blocks or fails startup.**" paragraph in
  "What happens when the communication backend is unreachable at startup" (~lines 292-299)

The paragraph currently states the receiver's connection is independent of the sender's, full stop. After this
ticket that is no longer the whole truth: the receiver stays independent for *startup outcome* and for its own
retry policy, but it now re-verifies its connection when the sender group recovers from a real outage. Per
`.claude/rules/wiki-drafts.md`, documented behavior that changes must be updated when the work is declared
complete.

- [ ] **Step 1: Read the current paragraph**

```bash
sed -n '286,302p' wiki/FAQ.md
```

- [ ] **Step 2: Append the new behavior to that paragraph**

Add, directly after the sentence ending `Receiver reconnected. Handling server commands resumed.`):

```markdown
The receiver's connection is also re-verified when the *sender* group recovers from a connection outage. A
receiver spends nearly all of its time passively waiting for commands, so it can miss a connection loss entirely;
when the sender group reconnects after an outage in which its own connect attempts actually failed, the receiver
cycles its connection once to re-establish it. This does not happen for a send failure against a reachable
endpoint (a load peak, a throttled or briefly failing target), so a healthy receiver is not disturbed by ordinary
send retries. <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 6.0.0</kbd>
```

Match the surrounding paragraph's prose style and the existing `<kbd>` badge markup exactly — copy a badge from a
neighbouring line rather than retyping it.

- [ ] **Step 3: Verify no setting was added**

This ticket adds no setting (D473.9), so the FAQ's settings tables and
`njams-sdk-sample-client/src/main/resources/settings_full.properties` need **no** change. Confirm:

```bash
git diff --stat -- njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java njams-sdk-sample-client/src/main/resources/settings_full.properties
```

Expected: empty output. If either shows changes, a setting crept in — stop and raise it.

- [ ] **Step 4: Commit**

```bash
git add wiki/FAQ.md
git commit -m "SDK-473 Document the sender-recovery-triggered receiver reconnect in the FAQ"
```

---

## Task 11: Finalization

**Files:** none changed unless a check fails.

- [ ] **Step 1: Full module suite**

```bash
mvn -pl njams-sdk test
```

Expected: BUILD SUCCESS. Compare the counts against Task 0's baseline: the totals must be Task 0's plus the tests
added here — 25 new methods across 5 new classes (2 in `SenderFailureClassificationTest`, 13 in
`SenderRecoverySignalSpecTest`, 4 in `ReceiverRecoveryCycleSpecTest`, 2 in
`SharedSenderRecoverySignalSpecTest`, 4 in `SharedReceiverSelectionSpecTest`) plus 1 added to the existing
`CommunicationFactoryTest` — with zero failures and zero errors.

- [ ] **Step 2: Full build, checkstyle, Javadoc**

```bash
mvn clean install -DskipTests
mvn -pl njams-sdk validate -Pcheckstyle
mvn -pl njams-sdk javadoc:javadoc
```

Expected: BUILD SUCCESS for all three; **no** Javadoc errors.

- [ ] **Step 3: Re-verify the relocated-type constraint on every new/changed public or protected member**

```bash
git diff master...HEAD -- njams-sdk/src/main/java | grep -E '^\+\s+(public|protected)' | sort -u
```

Check every hit's return type, parameter types, type parameters and thrown exceptions against the
`<relocations>` list in `checkstyle.xml`. Expected: only JDK types (`boolean`, `Throwable`) and SDK-owned types
(`SenderRecoveryListener`, `AbstractSender`). Report the list explicitly rather than just asserting it is clean.

- [ ] **Step 4: Confirm the invariants this ticket must not break**

Re-read `.claude/rules/message-sending-control.md` and confirm against the actual diff:
- No new public/protected hook lets a client force or increase per-`logId` send frequency. (`SenderRecoveryListener`
  is a notification *out* of the SDK about connection state, and adds no send trigger.)
- `JobFlusher`/`LogMessageFlushTask` are untouched: `git diff master...HEAD --stat` must not list them.

Re-read `.claude/rules/runtime-performance-hotpath.md` and confirm:
- No settings read was added on the send path. `isConnectionBroken` is called only from `reportFailure`, i.e. once
  per failed send, never per successful message, and reads nothing.

- [ ] **Step 5: Decide the `breaking-change` label**

Assess the real diff. Expected outcome: **no label** — everything added is additive (a new interface, new methods,
a new protected template method with a default), the one signature change (`SenderPool.onReconnected`) is on a
package-private internal member, and `stopReceiverAfterStartupFailure` is private.

The one judgement call to make explicitly rather than assume: `AbstractReceiver` gaining `SenderRecoveryListener`
as a supertype is a source- and binary-compatible addition, but it does add a new `public` method to an SPI
Contract type that external transports subclass. A subclass that already declares its own no-argument
`onSenderGroupRecovered()` with a non-`void` return type would fail to compile. If uncertain whether that
counts here, ask the user rather than deciding — per `.claude/rules/jira-workflow.md`, this label is decided
against the diff, and per `njams-ticket-finish` an unclear contract question goes to the user.

- [ ] **Step 6: Propose (do not create) a note on SDK-474**

SDK-474 owns the per-transport failure classification. Its scope is now narrower: the seam exists, so it reduces
to overriding `AbstractSender.isConnectionBroken(Throwable)` in `JmsSender` and `HttpSender` (and optionally the
deprecated `KafkaSender`) per SDK-472's spec §8.2, plus deciding whether classification should additionally gate
the group flip (explicitly *not* done here, D473.6).

Draft that comment and **ask the user before posting it** — never post to Jira unprompted. Same for SDK-473's own
closing comment, which belongs to `njams-ticket-finish` at resolve time, not here.

- [ ] **Step 7: Finalizing commit**

Only if Steps 1-4 all passed and there is anything left uncommitted:

```bash
git status --short
git commit -m "SDK-473 #comment Trigger a receiver reconnect when the sender group recovers from a real connection outage"
```

If the working tree is already clean, amend nothing — instead note that the ticket's significant commit is the
last functional one, and let `njams-ticket-finish` post the closing comment.

- [ ] **Step 8: Hand back for `njams-ticket-finish`**

Do **not** resolve the ticket. Report: what landed, the test counts, the `breaking-change` assessment from Step 5,
and the SDK-474 comment draft from Step 6. Resolving requires the user's explicit confirmation.

---

## Self-review

**Design coverage.** Every decision has a task: D473.1 (one-way — nothing in any task adds a receiver→sender
path), D473.2 (recovery-timed — Task 4's fan-out sits in `onReconnected`), D473.3 (evidence gate — Task 3),
D473.4 (stub — Task 1), D473.5 (AND — Task 4 Step 4, asserted by `anOutageTheSenderRuledOutSignalsNobody`),
D473.6 (classification consumed only for the signal — Task 2's field Javadoc plus Task 11 Step 4's check),
D473.7 (receiver object as listener — Task 6 Step 1, asserted by
`oneOutageCyclesTheSharedReceiverExactlyOnce` and `aListenerRegisteredTwiceIsSignalledOnce`, with its underlying
one-instance-per-JVM premise pinned separately by Task 9), D473.8
(deregistration — Task 6 Steps 2-3, asserted by `aStoppedClientsReceiverIsNoLongerCycled`), D473.9 (no setting —
Task 10 Step 3), D473.10 (receiver's own WARN accepted — no task changes it, recorded so a reviewer does not
"fix" it), D473.11 (fallback logged at INFO — Task 8, asserted by
`theDedicatedReceiverFallbackIsLoggedAtInfoNotWarn`, and asserted *not* to fire for honored sharing by Task 9's
`honoredSharingLogsNoDedicatedFallbackMessage`).

**Type consistency.** `isConnectionBroken(Throwable)` is spelled identically in Tasks 1, 2, and the test-access
subclass. `onReconnected(AbstractSender, boolean)` is spelled identically in Task 3's implementation, Task 4's
rewrite, and both `SenderConnector` call sites. `onSenderGroupRecovered()` is spelled identically in the
interface (Task 4), the implementation (Task 5), and every test. `outageIndicatesBrokenConnection` /
`recoveredAfterFailedConnectAttempt` and their `…ForTest()` accessors match between Tasks 2, 3, and 4.

**Known gaps, recorded rather than hidden.**
- The `shared=true` + HTTP end-to-end shape is covered at unit level only — see Task 7's coverage note.
- The "exactly once" assertions await the expected count and then assert equality immediately; they would not
  catch a duplicate signal arriving much later. A duplicate from the same outage would be fired synchronously in
  the same `notifyRecovered` loop, so this is adequate — but it is not a proof of the general case.
- The rationale for the D1.7 cut ("does not actually solve the problem it was designed for", spec §0) was never
  recorded and the user has not confirmed it. D473.2 and D473.3 are this plan's reconstruction of it. If the real
  reason turns out to be something else, the trigger's timing or gate may need to change — raise it rather than
  working around it.
