# SDK-476 Sender Retry/Discard-Policy Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three per-transport send-retry loops (`HttpSender`, `JmsSender`, `KafkaSender`) with one shared retry/discard-policy mechanism in `AbstractSender`, so every transport behaves identically for the same failure category under a given discard policy — and fix the `HttpSender` defect where a genuine connection loss is discarded without ever being reported to the sender pool.

**Architecture:** `AbstractSender` gains a `protected final sendWithRetry(SendAttempt)` template method that each transport calls once per chunk, passing its single-attempt send as a lambda. The template owns a bounded inner smoothing loop (50/200/750ms, skipped entirely under `DISCARD`) and an outer loop that re-enters the inner loop indefinitely for congestion under `NONE`/`ON_CONNECTION_LOSS`. Everything else is rethrown unchanged to `NjamsSender.dispatch`, which classifies the failure with the sender's own `isMessageRejected`/`isCongestion` to decide `release` (connection fine, drop the message) versus `reportFailure` (retire, reconnect, fire listeners). Because a send can outlive its own connection, two staleness holes are closed in the same pass: `SenderPool.reportFailure` absorbs a failure reported for a sender the group already retired, and the congestion wait unwinds via an internal `SenderRetiredException` so `dispatch` re-sends the message on a current sender instead of reporting a phantom outage. Serialization and chunking stay transport-specific; the SPI change is purely additive, so a third-party transport keeping its own loop still compiles.

**Tech Stack:** Java 11, Maven, SLF4J, OkHttp (HTTP), javax.jms/ActiveMQ (JMS), Kafka client, JUnit 4 + Mockito.

**Spec:** `docs/superpowers/specs/2026-09-09-sdk-476-sender-retry-unification-discussion.md` — read it before Task 1. It carries the full design rationale, the classification × policy table, and the cross-layer invariant this plan implements.

## Global Constraints

- Java 11+, Maven 3.8+. Build: `mvn clean install -pl njams-sdk`. Tests: `mvn test -pl njams-sdk`.
- **No new third-party dependencies.** Solve with what the project already has or the JDK.
- Every new production source file starts with the exact copyright header from `.claude/rules/code-quality-general.md` (2026 Salesfive Integration Services GmbH).
- All `public` and `protected` members need Javadoc. Verify: `mvn validate -Pcheckstyle -pl njams-sdk` and `mvn javadoc:javadoc -pl njams-sdk` (a broken `{@link}` is a hard error, not a warning).
- Javadoc states intent, not numbers: never document concrete retry counts or delays in `AbstractSender`'s Javadoc ("a bounded number of quick retries", not "three retries at 50/200/750ms").
- `communication/` is on the runtime monitoring path: no per-message settings reads (snapshot at `init`), no avoidable allocation in the retry path.
- `AbstractSender` is **SPI Contract** (`.claude/rules/public-api-design.md`). This plan keeps the change additive — no existing abstract method signature changes, no member removed — so it stays source-compatible for third-party transports.
- `DiscardPolicy` values and their meaning are fixed: `NONE` = never discard; `ON_CONNECTION_LOSS` = discard only on a confirmed connection loss; `DISCARD` = discard on any issue that cannot be sent directly. `DiscardPolicy.DEFAULT == DISCARD`.
- Commit messages: `SDK-476 <description>`, no `#comment` except the final commit. Attribution footer per the session's instructions.
- Do not modify any existing test without the permission recorded in the Gate section below.
- **Retry per chunk, never per message.** A transport that splits a message must call `sendWithRetry` once per
  chunk, inside its chunk loop. `HttpSender` computes the chunk group's `messageKey` once *before* the loop
  (`HttpSender.java:368-393`), so retrying a whole message would re-send already-delivered chunks under a new key
  and leave the server holding an incomplete chunk group — a Wire Contract break governed by
  `message-format-changes.md`. Do not "simplify" the per-chunk call into a single wrapping call.
- **`SenderPool` changes in exactly two places, both in Task 2** — the `reportFailure` stale-report guard and the
  retirement marking in `failGroup`. Everything else in that file stays as it is: `failGroup` already sets
  `reconnecting = true` synchronously inside the lock (`SenderPool.java:561-579`), so `acquire()`'s existing
  fast-discard for `DISCARD`/`ON_CONNECTION_LOSS` (`:369-389`) keeps working unchanged, and `classifyQuietly`'s
  `catch (RuntimeException)` (`:586-593`) stays as defense-in-depth behind the "classifiers must never throw"
  contract Task 5 documents. Do not restructure the locking.
- **Never detect the retirement signal with `instanceof`.** `HttpSender` re-throws a `RuntimeException` unwrapped
  (`HttpSender.java:302-304`) but `JmsSender` wraps *unconditionally* (`JmsSender.java:192-194`), so an
  `instanceof` check works on HTTP and silently fails on JMS — the primary production transport. Always walk the
  cause chain.

---

## Gate: existing tests that change by design — RESOLVED, APPROVED 2026-09-10

`.claude/rules/testing-conventions.md`: *"Existing test cases must never be modified without explicit user permission — if a fix causes a test to fail, the fix is wrong."* These five tests assert today's behavior, which this design deliberately changes.

**Status: the user explicitly approved changing all five on 2026-09-10.** No further permission is needed for the changes in the table below, and only for those. Any *other* existing test that turns red during execution is out of scope for this approval: stop and report it rather than editing it.

| Test | Asserts today | New behavior |
|---|---|---|
| `HttpSenderTest.sendDiscardsOnConnectionLossPolicy` (`HttpSenderTest.java:136-145`) | `ON_CONNECTION_LOSS` + I/O failure returns normally (silent discard) | Throws after the smoothing window, so the pool retires the sender and reports the outage — this *is* the bug being fixed |
| `HttpSenderTest.sendDiscardsImmediatelyOnPayloadTooLargeRegardlessOfDiscardPolicy` (`:233-242`) | 413 returns normally after 1 attempt | Still 1 attempt, but now throws; `dispatch` classifies it as message-rejected and drops the message |
| `HttpSenderTest.sendDiscardsImmediatelyOnServiceUnavailableUnderConnectionLossPolicy` (`:266-276`) | 503 under `ON_CONNECTION_LOSS` returns normally after exactly 1 attempt | 4 attempts (smoothing applies, since the policy is not `DISCARD`), then throws |
| `JmsSenderTest.queueIsFullTest` (`JmsSenderTest.java:115-128`) | Default policy (`DISCARD`) retries congestion twice, then succeeds — 3 sends | `DISCARD` never retries: 1 send, then throws |
| `JmsSenderTest.queueIsFullMaxTriesTest` (`:130-147`) | Exactly 100 sends before throwing under `DISCARD` | 1 send, then throws |

One further test keeps passing but becomes slow: `HttpSenderTest.sendRetriesPastTransportRetryBudgetOnCongestionUnderConnectionLossPolicy` (`:245-263`) drives 25 congestion responses then a 200. Its `times(26)` expectation still holds exactly: six smoothing windows run to exhaustion at 4 attempts each (attempts 1-24), then a seventh window sees attempt 25 fail and attempt 26 succeed. The wall time, though, goes from ~1.25s (25 × 50ms) to ~6.35s (6 × 1000ms of exhausted windows, plus six 50ms congestion pauses between them, plus one 50ms retry in the final window). **Approved 2026-09-10:** keep it fast by overriding the Task 1 delay hooks in this test — Task 3 Step 3 carries the change. Its assertions, including `times(26)`, stay exactly as they are.

Four `HttpSenderTest` tests were checked and **keep passing unchanged**, so they are deliberately not in this table: `sendThrowsHttpSendExceptionAfterRetriesOnIoError`, `sendThrowsRuntimeExceptionAfterRetriesOnErrorStatus`, `sendThrowsHttpStatusExceptionCarryingTheCodeAfterRetriesOnErrorStatus` (all three run under the default `DISCARD` policy, which now throws on attempt 1 instead of attempt 20 — they assert the throw, not the count), and `JmsSenderTest.queueFullUnderConnectionLossPolicyRetriesInsteadOfDiscardingImmediately`.

---

## File Structure

**Created:**
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/SendAttempt.java` — functional interface for one honest send attempt.
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderRetiredException.java` — package-private signal: this sender was replaced mid-send.
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderRetryLoopTest.java` — specifies the shared retry machinery against a test-double sender.
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderDispatchClassificationTest.java` — specifies `dispatch`'s retire-or-not decision.
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/ClassifyingFailureSender.java` — test sender that fails with a self-classified failure.
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/RetiredThenSucceedingSender.java` — test sender that reports a mid-send retirement once, then succeeds.
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/StaleFailureReportSpecTest.java` — specifies that a report arriving after recovery is absorbed (Race A).

**Modified:**
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java` — adds the retry template, delay hooks, `logError`, the outcome enum, the `retired` flag; updates class Javadoc.
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java:210-226` — `dispatch`'s catch block becomes classification-driven, handles a mid-send retirement, and owns discard counting.
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java:470-489, 561-579` — absorbs a stale failure report; marks retired senders.
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/TestSender.java` — delegates its classifiers to the injected mock.
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/http/HttpSender.java:395-451` — `tryToSend` retry loop removed; single attempt throws; classifiers walk the cause chain.
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/JmsSender.java:305-334` — `tryToSend` retry loop removed.
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/kafka/KafkaSender.java:298-326` — discard/throw handling removed in favor of the shared path.
- `wiki/FAQ.md` — discard-policy behavior section.

---

### Task 1: Shared retry machinery in `AbstractSender`

Purely additive: nothing calls `sendWithRetry` yet, so no existing behavior changes and the full suite must stay green.

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SendAttempt.java`
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderRetiredException.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderRetryLoopTest.java`

**Interfaces:**
- Consumes: `DiscardPolicy` (existing), `AbstractSender.discardPolicy` field (existing, set in `init`), `isCongestion`/`isMessageRejected` (existing abstract), `NjamsSdkRuntimeException` (existing).
- Produces:
  - `SendAttempt` — `void send() throws Exception`
  - `SenderRetiredException` — package-private, extends `NjamsSdkRuntimeException`; `static boolean isIn(Throwable)`
  - `AbstractSender.SendFailureOutcome` — enum `RETRYING`, `ESCALATING`, `MESSAGE_REJECTED`, `DISCARDED_BY_POLICY`, `SENDER_RETIRED`
  - `protected final void AbstractSender.sendWithRetry(SendAttempt attempt) throws Exception`
  - `void AbstractSender.setRetired()` — package-private, called by `SenderPool.failGroup` in Task 2
  - `protected long[] AbstractSender.getSmoothingDelaysMs()` — default `{50, 200, 750}`
  - `protected long AbstractSender.getCongestionRetryDelayMs()` — default `50`
  - `protected void AbstractSender.logError(SendFailureOutcome outcome, Throwable failure)` — non-abstract default

- [ ] **Step 1: Write the failing test**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderRetryLoopTest.java`:

```java
package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Specifies the shared retry machinery in {@link AbstractSender}: the bounded smoothing window, the
 * congestion-only indefinite retry, and which failures are propagated to the caller. See
 * docs/superpowers/specs/2026-09-09-sdk-476-sender-retry-unification-discussion.md.
 */
public class SenderRetryLoopTest {

    /** Congestion marker for this test's classifier. */
    private static class Congested extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** Permanently-unsendable marker for this test's classifier. */
    private static class Rejected extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** Sender with no transport: counts attempts, fails them on demand, and uses zero delays. */
    private static class RetrySender extends AbstractSender {
        private int attempts = 0;
        private int failuresToProduce = 0;
        private RuntimeException failureToThrow = new RuntimeException("other");
        private final List<SendFailureOutcome> logged = new ArrayList<>();

        RetrySender(String policy) {
            Properties props = new Properties();
            props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, policy);
            init(ClientSettings.from(props));
        }

        @Override
        public String getName() {
            return "retry-loop";
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

        @Override
        protected boolean isCongestion(Throwable failure) {
            return failure instanceof Congested;
        }

        @Override
        protected boolean isMessageRejected(Throwable failure) {
            return failure instanceof Rejected;
        }

        @Override
        protected long[] getSmoothingDelaysMs() {
            return new long[] { 0, 0, 0 };
        }

        @Override
        protected long getCongestionRetryDelayMs() {
            return 0;
        }

        @Override
        protected void logError(SendFailureOutcome outcome, Throwable failure) {
            logged.add(outcome);
        }

        /** Attempt number at which the pool is simulated to retire this sender mid-send; 0 = never. */
        private int retireAtAttempt = 0;

        void retireAtAttempt(int attempt) {
            retireAtAttempt = attempt;
        }

        /** Runs one send through the shared machinery, failing the first {@code failures} attempts. */
        void run(int failures, RuntimeException failure) throws Exception {
            failuresToProduce = failures;
            failureToThrow = failure;
            sendWithRetry(() -> {
                attempts++;
                if (retireAtAttempt > 0 && attempts >= retireAtAttempt) {
                    // Exactly what SenderPool.failGroup does to a sender that is checked out when the group fails.
                    setRetired();
                }
                if (attempts <= failuresToProduce) {
                    throw failureToThrow;
                }
            });
        }
    }

    @Test
    public void aSingleTransientFailureIsSmoothedAwayAndNeverReachesTheCaller() throws Exception {
        RetrySender sender = new RetrySender("none");
        sender.run(1, new RuntimeException("blip"));
        assertEquals("one retry must be enough to absorb a single blip", 2, sender.attempts);
    }

    @Test
    public void theSmoothingWindowIsBoundedToThreeRetries() throws Exception {
        RetrySender sender = new RetrySender("none");
        try {
            sender.run(Integer.MAX_VALUE, new RuntimeException("down"));
            fail("expected the failure to be propagated once the smoothing window is exhausted");
        } catch (RuntimeException expected) {
            assertEquals("one initial attempt plus three smoothing retries", 4, sender.attempts);
        }
    }

    @Test
    public void aRejectedMessageIsNeverRetried() throws Exception {
        RetrySender sender = new RetrySender("none");
        try {
            sender.run(Integer.MAX_VALUE, new Rejected());
            fail("expected a rejected message to be propagated immediately");
        } catch (Rejected expected) {
            assertEquals("retrying a rejected message can never help", 1, sender.attempts);
        }
    }

    @Test
    public void discardPolicyNeverRetriesAtAll() throws Exception {
        RetrySender sender = new RetrySender("discard");
        try {
            sender.run(Integer.MAX_VALUE, new RuntimeException("down"));
            fail("expected the failure to be propagated on the first attempt under discard");
        } catch (RuntimeException expected) {
            assertEquals("discard must not delay the monitored application at all", 1, sender.attempts);
        }
    }

    @Test
    public void congestionIsRetriedIndefinitelyUnderNone() throws Exception {
        RetrySender sender = new RetrySender("none");
        // far more failures than the smoothing window: proves the outer loop keeps re-entering it
        sender.run(50, new Congested());
        assertEquals(51, sender.attempts);
    }

    @Test
    public void congestionIsRetriedIndefinitelyUnderOnConnectionLoss() throws Exception {
        RetrySender sender = new RetrySender("onconnectionloss");
        sender.run(50, new Congested());
        assertEquals(51, sender.attempts);
    }

    @Test
    public void congestionUnderDiscardIsPropagatedImmediately() throws Exception {
        RetrySender sender = new RetrySender("discard");
        Congested congestion = new Congested();
        try {
            sender.run(Integer.MAX_VALUE, congestion);
            fail("expected congestion to be propagated under the discard policy");
        } catch (Congested expected) {
            assertSame("the original failure must be rethrown, not wrapped", congestion, expected);
            assertEquals(1, sender.attempts);
        }
    }

    @Test
    public void theFinalFailureIsRethrownUnchanged() throws Exception {
        RetrySender sender = new RetrySender("none");
        RuntimeException failure = new RuntimeException("socket closed");
        try {
            sender.run(Integer.MAX_VALUE, failure);
            fail("expected the original failure");
        } catch (RuntimeException expected) {
            assertSame("classification at the catch site depends on the same throwable", failure, expected);
        }
    }

    @Test
    public void escalationIsLoggedThroughTheTransportHook() throws Exception {
        RetrySender sender = new RetrySender("none");
        try {
            sender.run(Integer.MAX_VALUE, new RuntimeException("down"));
            fail("expected escalation");
        } catch (RuntimeException expected) {
            assertEquals(1, sender.logged.stream().filter(o -> o == AbstractSender.SendFailureOutcome.ESCALATING)
                .count());
        }
    }

    // --- Race B: the congestion loop must not ride a connection the group has already replaced ---

    @Test
    public void congestionStopsBeingWaitedOutOnceTheGroupRetiredThisSender() throws Exception {
        // Without this, the outer loop under a never-discard policy would retry forever on a sender the pool has
        // already given up on: the message would never reach the healthy connection and the retired sender would
        // never be closed.
        RetrySender sender = new RetrySender("none");
        sender.retireAtAttempt(1);
        try {
            sender.run(Integer.MAX_VALUE, new Congested());
            fail("expected the retry to unwind once this sender was retired");
        } catch (SenderRetiredException expected) {
            assertEquals("at most one smoothing window, then unwind - not an unbounded loop", 4, sender.attempts);
        }
    }

    @Test
    public void retirementPartWayThroughWaitingOutCongestionStopsTheLoopPromptly() throws Exception {
        RetrySender sender = new RetrySender("onconnectionloss");
        sender.retireAtAttempt(6);
        try {
            sender.run(Integer.MAX_VALUE, new Congested());
            fail("expected the retry to unwind once this sender was retired");
        } catch (SenderRetiredException expected) {
            assertTrue("must unwind within the window it was retired in, not keep going: " + sender.attempts,
                sender.attempts <= 8);
        }
    }

    @Test
    public void aHealthySenderNeverProducesTheRetirementSignal() throws Exception {
        // Contrast case: the abort must be caused by retirement, not merely by congestion persisting.
        RetrySender sender = new RetrySender("none");
        sender.run(50, new Congested());
        assertEquals(51, sender.attempts);
    }

    @Test
    public void theRetirementSignalIsFoundThroughAWrappingException() {
        // JmsSender.send wraps unconditionally (JmsSender.java:192-194), so dispatch can only ever find this
        // signal by walking the cause chain.
        SenderRetiredException signal = new SenderRetiredException(new Congested());
        assertTrue(SenderRetiredException.isIn(signal));
        assertTrue(SenderRetiredException.isIn(
            new NjamsSdkRuntimeException("Unable to send LogMessage", signal)));
        assertFalse(SenderRetiredException.isIn(new RuntimeException("socket closed")));
        assertFalse(SenderRetiredException.isIn(null));
    }

    @Test
    public void aSelfReferentialCauseChainDoesNotHangTheDetection() {
        // A custom transport exception whose getCause() returns itself must not spin the runtime path forever.
        RuntimeException looping = new RuntimeException("loop") {
            private static final long serialVersionUID = 1L;

            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertFalse(SenderRetiredException.isIn(looping));
    }
}
```

The test file needs `com.im.njams.sdk.common.NjamsSdkRuntimeException` imported, plus
`org.junit.Assert.assertFalse`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=SenderRetryLoopTest -pl njams-sdk`
Expected: compilation failure — `sendWithRetry`, `getSmoothingDelaysMs`, `getCongestionRetryDelayMs`, `logError`, `SendFailureOutcome` do not exist yet.

- [ ] **Step 3: Create the `SendAttempt` interface**

Create `njams-sdk/src/main/java/com/im/njams/sdk/communication/SendAttempt.java` with the standard copyright header, then:

```java
package com.im.njams.sdk.communication;

/**
 * One honest attempt to hand a single, already-built message unit to a transport.
 * <p>
 * An implementation performs exactly one send and reports any failure by throwing — it must never retry, never
 * swallow a failure, and never apply the discard policy itself. Retrying, classification and the discard policy
 * are owned by {@link AbstractSender#sendWithRetry(SendAttempt)}.
 *
 * @since 6.0.0
 */
@FunctionalInterface
public interface SendAttempt {

    /**
     * Performs one send attempt.
     *
     * @throws Exception if the attempt failed for any reason.
     */
    void send() throws Exception;
}
```

- [ ] **Step 3b: Create the retirement signal**

Create `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderRetiredException.java` with the standard
copyright header, then:

```java
package com.im.njams.sdk.communication;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Internal signal: the sender a send was running on was retired by its pool before that send finished, so the
 * failure it ended on says nothing about the group's current connection.
 * <p>
 * Deliberately package-private — no transport ever throws or catches this; it travels from
 * {@link AbstractSender#sendWithRetry(SendAttempt)} to {@link NjamsSender}, which retries the message on a fresh
 * sender instead of reporting an outage. It extends {@link NjamsSdkRuntimeException} so a transport's own
 * {@code send} wrapping cannot turn it into a checked exception.
 */
class SenderRetiredException extends NjamsSdkRuntimeException {

    private static final long serialVersionUID = 1L;

    /** Guards against a pathological transport exception whose cause chain loops back on itself. */
    private static final int MAX_CAUSE_DEPTH = 32;

    SenderRetiredException(Throwable cause) {
        super("Sender was retired while the message was still being retried", cause);
    }

    /**
     * Detects the signal anywhere in a failure's cause chain. A chain walk rather than an {@code instanceof} on
     * purpose: {@code JmsSender.send} wraps every failure unconditionally, so the signal does not arrive at the
     * top level on every transport.
     *
     * @param failure the failure to inspect; may be {@code null}.
     * @return {@code true} if this signal is the failure or one of its causes.
     */
    static boolean isIn(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SenderRetiredException) {
                return true;
            }
            final Throwable cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
        }
        return false;
    }
}
```

- [ ] **Step 4: Add the machinery to `AbstractSender`**

In `AbstractSender.java`, add the imports `com.im.njams.sdk.communication.DiscardPolicy` is already in-package (no import needed). Add after the `send(TraceMessage, String)` abstract declaration (currently `AbstractSender.java:163`):

```java
    /** Delays before each smoothing retry, ~1s in total. Bounded on purpose: this runs on a sender thread. */
    private static final long[] SMOOTHING_DELAYS_MS = { 50, 200, 750 };
    /** Pause between two smoothing windows while waiting out congestion. */
    private static final long CONGESTION_RETRY_DELAY_MS = 50;

    /**
     * Set by this sender's pool when the group fails while this sender is checked out. Monotonic: a retired
     * sender is always closed rather than recycled, so it never becomes current again.
     */
    private volatile boolean retired = false;

    /**
     * What the shared retry machinery decided about a failure, so a transport can log it in its own terms.
     *
     * @since 6.0.0
     */
    public enum SendFailureOutcome {
        /** The same message is about to be attempted again; nothing has been lost. */
        RETRYING,
        /** The failure is being propagated to the SDK, which decides the message's fate. */
        ESCALATING,
        /** The message was permanently unsendable and has been dropped; the connection is unaffected. */
        MESSAGE_REJECTED,
        /** The message was dropped because the configured discard policy gave up on it. */
        DISCARDED_BY_POLICY,
        /** This sender was replaced by its pool mid-send; the SDK retries the message on a fresh one. */
        SENDER_RETIRED
    }

    /**
     * Marks this sender as retired, so a send still retrying on it stops waiting and lets the SDK move the
     * message to a current sender. Called by {@link SenderPool} when the group fails; package-private, not part
     * of the sender SPI.
     */
    void setRetired() {
        retired = true;
    }

    /**
     * Sends one already-built message unit, applying this SDK's retry and discard-policy handling.
     * <p>
     * Call this once per unit a transport actually puts on the wire — per chunk when a message is split, so a
     * failure resumes at the chunk that failed instead of re-sending the ones that already arrived. The attempt
     * must be a single honest send that throws on failure; everything else is handled here.
     * <p>
     * A failure this method cannot resolve locally is rethrown unchanged, so the SDK can classify the very same
     * throwable this method classified. Congestion is the one failure retried indefinitely, on this same
     * connection, and only while the configured policy still wants the message sent — it never reaches the
     * caller in that case, because retiring and reconnecting a connection that is merely busy would be pure
     * overhead. Waiting out congestion stops early if this sender's pool replaces it in the meantime: the
     * message then belongs on the group's current connection, not on this one.
     *
     * @param attempt one honest send attempt.
     * @throws Exception the failure that could not be resolved locally, unchanged.
     * @since 6.0.0
     */
    protected final void sendWithRetry(SendAttempt attempt) throws Exception {
        while (true) {
            try {
                attemptWithSmoothing(attempt);
                return;
            } catch (InterruptedException ie) {
                // Keep the flag: the dispatching loop uses it to stop retrying during shutdown.
                Thread.currentThread().interrupt();
                throw ie;
            } catch (Exception failure) {
                if (!isCongestion(failure) || discardPolicy == DiscardPolicy.DISCARD) {
                    logError(SendFailureOutcome.ESCALATING, failure);
                    throw failure;
                }
                if (retired) {
                    // The group gave up on this connection and has one of its own again. Waiting out congestion
                    // here would pin the message to a connection nobody else uses any more, so hand it back and
                    // let the SDK put it on a current sender. Not reported as a failure: this says nothing about
                    // whether the group's connection is healthy.
                    logError(SendFailureOutcome.SENDER_RETIRED, failure);
                    throw new SenderRetiredException(failure);
                }
                logError(SendFailureOutcome.RETRYING, failure);
                Thread.sleep(getCongestionRetryDelayMs());
            }
        }
    }

    /**
     * One attempt plus a bounded window of quick retries, so a blip that clears immediately never becomes a
     * visible failure. Skipped entirely under {@link DiscardPolicy#DISCARD}, which trades that resilience for
     * never delaying the monitored application, and for a failure that can never succeed on a retry.
     */
    private void attemptWithSmoothing(SendAttempt attempt) throws Exception {
        final long[] delays = getSmoothingDelaysMs();
        int retry = 0;
        while (true) {
            try {
                attempt.send();
                return;
            } catch (InterruptedException ie) {
                throw ie;
            } catch (Exception failure) {
                if (isMessageRejected(failure) || discardPolicy == DiscardPolicy.DISCARD
                    || retry >= delays.length) {
                    throw failure;
                }
                logError(SendFailureOutcome.RETRYING, failure);
                Thread.sleep(delays[retry++]);
            }
        }
    }

    /**
     * Delays before each quick retry of a failed attempt; the array length is the number of retries.
     * Override only to tune this transport or to remove the waiting in a test.
     *
     * @return the delay in milliseconds before each retry.
     * @since 6.0.0
     */
    protected long[] getSmoothingDelaysMs() {
        return SMOOTHING_DELAYS_MS;
    }

    /**
     * Delay between two rounds of attempts while waiting out congestion.
     *
     * @return the delay in milliseconds.
     * @since 6.0.0
     */
    protected long getCongestionRetryDelayMs() {
        return CONGESTION_RETRY_DELAY_MS;
    }

    /**
     * Reports a send failure and what was decided about it, so a transport can log it with the detail only it
     * has — a status code, a destination, a broker limit. Called for every failure the SDK acts on, including
     * the ones it retries, so an implementation must keep a repeated failure quiet (debug/trace) and reserve
     * warn/error for an outcome that actually loses or escalates a message.
     * <p>
     * The default logs at debug without any transport detail. Overriding is optional.
     *
     * @param outcome what the SDK decided about this failure.
     * @param failure the failure itself; may be {@code null}.
     * @since 6.0.0
     */
    protected void logError(SendFailureOutcome outcome, Throwable failure) {
        LOG.debug("Send failure on sender {} classified as {}.", getName(), outcome, failure);
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=SenderRetryLoopTest -pl njams-sdk`
Expected: PASS, all fourteen tests.

- [ ] **Step 6: Verify nothing else broke and the API surface is clean**

Run: `mvn test -pl njams-sdk` — expected: PASS. Nothing calls `sendWithRetry` yet, and `setRetired()` has no
caller until Task 2, so no existing behavior changes.
Run: `mvn validate -Pcheckstyle -pl njams-sdk` — expected: no Javadoc violations on the new `public`/`protected` members. `SenderRetiredException` and `setRetired()` are package-private and need no Javadoc by the rule, but keep the Javadoc shown above anyway — it carries the reasoning.
Run: `mvn javadoc:javadoc -pl njams-sdk` — expected: no errors. Note `SenderRetiredException`'s Javadoc `{@link}`s
a package-private type from a package-private type, which is fine; the Javadoc build does not process it.

- [ ] **Step 7: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/SendAttempt.java \
        njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderRetiredException.java \
        njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java \
        njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderRetryLoopTest.java
git commit -m "SDK-476 Add shared send-retry machinery to AbstractSender"
```

---

### Task 2: The SDK-side decision about a failed send — classification, and stale evidence

Three things that together decide what the SDK does with a failure, reviewed as one unit: which failures retire
the sender, absorbing a report that arrives after the group already recovered (**Race A**), and handing a
mid-send retirement back for a retry rather than treating it as an outage (**Race B**'s other half).

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java:210-226`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java:470-489` (`reportFailure` guard) and `:561-579` (`failGroup` marking)
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/TestSender.java` (delegate the classifiers — see Step 1)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderDispatchClassificationTest.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/StaleFailureReportSpecTest.java`

**Interfaces:**
- Consumes: `AbstractSender.isCongestion`/`isMessageRejected`/`logError` + `SendFailureOutcome` (Task 1), `SenderPool.acquire`/`release`/`reportFailure` (existing), `DiscardMonitor.discard()` (existing), `CountingDiscardMonitor` (existing test helper), `NjamsSender(ClientSettings)` + `Settings` (which implements `ClientSettings`), `SenderExceptionListener.onException(Exception, CommonMessage)`.
- Also consumes: `SenderRetiredException.isIn(Throwable)` and `AbstractSender.setRetired()` (both Task 1), `SenderPoolTestAccess` + `AbstractLifecycleSpecTest` (existing harness).
- Produces: `dispatch` semantics every later task depends on —
  1. a mid-send retirement (`SenderRetiredException` anywhere in the cause chain) releases the sender and re-sends the same message on a fresh one, reporting nothing;
  2. otherwise, a failure classified as congestion or message-rejected releases the sender and drops the message;
  3. anything else calls `reportFailure`, which now ignores a report for an already-retired sender.

  Checked in that order. Later tasks must not reorder them: the retirement signal usually wraps a congestion failure, so testing the classifiers first would drop the message.

- [ ] **Step 1: Make `TestSender` delegate its classifiers**

`dispatch` classifies by asking **the pooled sender**, which is the `TestSender` the SPI created — not the delegate
injected with `setSenderMock`. `TestSender.isCongestion`/`isMessageRejected` currently hard-return `false`
(`TestSender.java:119-125`), so without this step every test below lands in the "other" branch and the task cannot
be tested at all.

This is an additive change to shared test *infrastructure*, not a modification of an existing test case, and it is
behavior-preserving for every current user: with no mock installed the answer stays `false`. `isCongestion` is
`protected`, and `TestSender` sits in the same package as `AbstractSender`, so calling it on another instance
compiles. Replace both classifier overrides in `TestSender.java` with:

```java
    @Override
    protected boolean isCongestion(Throwable failure) {
        return sender != null && sender.isCongestion(failure);
    }

    @Override
    protected boolean isMessageRejected(Throwable failure) {
        return sender != null && sender.isMessageRejected(failure);
    }
```

- [ ] **Step 2: Write the failing test**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderDispatchClassificationTest.java`:

```java
package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Specifies which failures make the dispatching loop retire a sender and which only drop the message. A
 * connection is only ever retired for a failure the sender could not attribute to congestion or to the message
 * itself. See docs/superpowers/specs/2026-09-09-sdk-476-sender-retry-unification-discussion.md.
 * <p>
 * Retirement is observed through the sender-exception listener, which only ever fires via
 * SenderPool.reportFailure. The absence checks are sound rather than racy: once the discard has been counted the
 * dispatching task for that message has finished, and no other code path can report a failure for it afterwards.
 */
public class SenderDispatchClassificationTest {

    @After
    public void afterEach() {
        CountingDiscardMonitor.restore();
        TestSender.setSenderMock(null);
    }

    private static NjamsSender senderWithPolicy(String policy) {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        settings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, policy);
        settings.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "1");
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "1");
        return new NjamsSender(settings);
    }

    private static LogMessage logMessage() {
        LogMessage msg = new LogMessage();
        msg.setLogId("log-1");
        msg.setPath(">a>b>");
        return msg;
    }

    @Test
    public void aRejectedMessageIsDroppedWithoutRetiringTheSender() throws Exception {
        CountingDiscardMonitor monitor = CountingDiscardMonitor.install();
        TestSender.setSenderMock(new ClassifyingFailureSender(ClassifyingFailureSender.Kind.REJECTED));
        NjamsSender sender = senderWithPolicy("none");
        try {
            AtomicBoolean retired = new AtomicBoolean();
            sender.addSenderExceptionListener((exception, msg) -> retired.set(true));
            sender.send(logMessage(), "session-1");
            assertTrue("the rejected message must be counted as discarded",
                monitor.awaitAtLeast(1, 5, TimeUnit.SECONDS));
            assertFalse("a rejected message must not retire the connection", retired.get());
            assertEquals("exactly one discard for one dropped message", 1, monitor.count());
        } finally {
            sender.close();
        }
    }

    @Test
    public void aCongestionFailureIsDroppedWithoutRetiringTheSender() throws Exception {
        CountingDiscardMonitor monitor = CountingDiscardMonitor.install();
        TestSender.setSenderMock(new ClassifyingFailureSender(ClassifyingFailureSender.Kind.CONGESTION));
        // Only the discard policy lets congestion reach dispatch at all; see the invariant in Task 5.
        NjamsSender sender = senderWithPolicy("discard");
        try {
            AtomicBoolean retired = new AtomicBoolean();
            sender.addSenderExceptionListener((exception, msg) -> retired.set(true));
            sender.send(logMessage(), "session-1");
            assertTrue(monitor.awaitAtLeast(1, 5, TimeUnit.SECONDS));
            assertFalse("congestion must never retire the connection", retired.get());
        } finally {
            sender.close();
        }
    }

    @Test
    public void anUnclassifiedFailureRetiresTheSenderAndReportsTheOutage() throws Exception {
        CountingDiscardMonitor.install();
        TestSender.setSenderMock(new ClassifyingFailureSender(ClassifyingFailureSender.Kind.OTHER));
        NjamsSender sender = senderWithPolicy("discard");
        try {
            AtomicBoolean retired = new AtomicBoolean();
            sender.addSenderExceptionListener((exception, msg) -> retired.set(true));
            sender.send(logMessage(), "session-1");
            long deadline = System.currentTimeMillis() + 5000;
            while (!retired.get() && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }
            assertTrue("a real connection problem must be reported to the pool's listeners", retired.get());
        } finally {
            sender.close();
        }
    }

    @Test
    public void aSenderRetiredMidSendHandsTheMessageToAFreshSenderInsteadOfReportingAnOutage() throws Exception {
        // Race B, dispatch's half: the retirement signal is not evidence about the current connection, so it must
        // neither discard the message nor open an outage - it must put the same message on a current sender.
        CountingDiscardMonitor monitor = CountingDiscardMonitor.install();
        RetiredThenSucceedingSender delegate = new RetiredThenSucceedingSender();
        TestSender.setSenderMock(delegate);
        NjamsSender sender = senderWithPolicy("none");
        try {
            AtomicBoolean outageReported = new AtomicBoolean();
            sender.addSenderExceptionListener((exception, msg) -> outageReported.set(true));
            sender.send(logMessage(), "session-1");
            assertTrue("the message must be re-sent and succeed",
                delegate.awaitSuccessfulSend(5, TimeUnit.SECONDS));
            assertFalse("a mid-send retirement is not an outage", outageReported.get());
            assertEquals("the message must not be discarded", 0, monitor.count());
            assertEquals("exactly one retry on a fresh sender", 2, delegate.sendCount());
        } finally {
            sender.close();
        }
    }
}
```

And the delegate it uses, `njams-sdk/src/test/java/com/im/njams/sdk/communication/RetiredThenSucceedingSender.java`:

```java
package com.im.njams.sdk.communication;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;

/**
 * Fails its first send the way a sender retired mid-send does, then succeeds — standing in for "the pool
 * replaced this connection while the send was still retrying".
 */
public class RetiredThenSucceedingSender extends AbstractSender {

    private final AtomicInteger sends = new AtomicInteger();
    private final CountDownLatch succeeded = new CountDownLatch(1);

    public int sendCount() {
        return sends.get();
    }

    public boolean awaitSuccessfulSend(long timeout, TimeUnit unit) throws InterruptedException {
        return succeeded.await(timeout, unit);
    }

    @Override
    public String getName() {
        return "retired-then-succeeding";
    }

    @Override
    protected void send(LogMessage msg, String clientSessionId) {
        attempt();
    }

    @Override
    protected void send(ProjectMessage msg, String clientSessionId) {
        attempt();
    }

    @Override
    protected void send(TraceMessage msg, String clientSessionId) {
        attempt();
    }

    private void attempt() {
        if (sends.incrementAndGet() == 1) {
            // Wrapped the way JmsSender.send would wrap it, so this also proves dispatch's chain walk works.
            throw new com.im.njams.sdk.common.NjamsSdkRuntimeException("Unable to send LogMessage",
                new SenderRetiredException(new IllegalStateException("queue full")));
        }
        succeeded.countDown();
    }

    @Override
    protected boolean isCongestion(Throwable failure) {
        return false;
    }

    @Override
    protected boolean isMessageRejected(Throwable failure) {
        return false;
    }
}
```

Note it deliberately classifies as neither congestion nor rejected: if `dispatch` checked the classifiers before
the retirement signal, this failure would fall through to `reportFailure` and the test would catch it.

Create the sender it injects as its own file
`njams-sdk/src/test/java/com/im/njams/sdk/communication/ClassifyingFailureSender.java`:

```java
package com.im.njams.sdk.communication;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;

/** Sender whose sends always fail with a failure of a chosen, self-classified kind. */
public class ClassifyingFailureSender extends AbstractSender {

    /** Which classification the produced failure carries. */
    public enum Kind {
        /** Classified by {@link #isCongestion(Throwable)}. */
        CONGESTION,
        /** Classified by {@link #isMessageRejected(Throwable)}. */
        REJECTED,
        /** Classified by neither, i.e. treated as a real connection problem. */
        OTHER
    }

    private final Kind kind;

    public ClassifyingFailureSender(Kind kind) {
        this.kind = kind;
    }

    @Override
    public String getName() {
        return "classifying-failure";
    }

    @Override
    protected void send(LogMessage msg, String clientSessionId) {
        throw failure();
    }

    @Override
    protected void send(ProjectMessage msg, String clientSessionId) {
        throw failure();
    }

    @Override
    protected void send(TraceMessage msg, String clientSessionId) {
        throw failure();
    }

    private RuntimeException failure() {
        return new IllegalStateException(kind.name());
    }

    @Override
    protected boolean isCongestion(Throwable failure) {
        return kind == Kind.CONGESTION && failure != null && Kind.CONGESTION.name().equals(failure.getMessage());
    }

    @Override
    protected boolean isMessageRejected(Throwable failure) {
        return kind == Kind.REJECTED && failure != null && Kind.REJECTED.name().equals(failure.getMessage());
    }
}
```

Note the classifiers match on the message rather than the type, because all three kinds throw
`IllegalStateException` — this keeps the failure indistinguishable to anything except the classifier itself,
which is exactly what the production code is supposed to rely on.

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn test -Dtest=SenderDispatchClassificationTest -pl njams-sdk`
Expected: the two "dropped without retiring" tests FAIL — today every failure goes to `reportFailure`, so the
listener fires and no discard is counted for them. `anUnclassifiedFailureRetiresTheSenderAndReportsTheOutage`
already passes; it is the contrast case that must keep passing.

- [ ] **Step 4: Rewrite `dispatch`'s catch block**

In `NjamsSender.java`, replace the body of `dispatch` (currently lines 210-226) with:

```java
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
                if (SenderRetiredException.isIn(e)) {
                    // The pool replaced this connection while the send was still retrying. That says nothing
                    // about the group's current connection, so this must not be reported as a failure: hand the
                    // sender back (release closes it, since it is retired) and put the same message on a current
                    // one. Checked before the classifiers on purpose — the underlying failure is usually
                    // congestion, and the congestion branch below would drop the message.
                    LOG.debug("Sender {} was retired mid-send; retrying the message on a current sender.",
                        sender.getName());
                    senderPool.release(sender);
                    continue;
                }
                if (dropWithoutRetiring(sender, e)) {
                    return;
                }
                LOG.debug("Send failed on sender {}; retiring it and retrying the message.", sender.getName(), e);
                senderPool.reportFailure(sender, e);
            }
        }
    }

    /**
     * Decides a failed send that the sender itself could attribute to the message or to congestion: the
     * connection is fine in both cases, so the sender goes back into the pool untouched and only this message
     * is dropped. Retiring it would cost a needless reconnect, and for a rejected message would blame the
     * connection for a payload it will never accept.
     *
     * @return {@code true} if the message was dropped and this send is finished.
     */
    private boolean dropWithoutRetiring(AbstractSender sender, Exception failure) {
        final AbstractSender.SendFailureOutcome outcome;
        if (sender.isMessageRejected(failure)) {
            outcome = AbstractSender.SendFailureOutcome.MESSAGE_REJECTED;
        } else if (sender.isCongestion(failure)) {
            outcome = AbstractSender.SendFailureOutcome.DISCARDED_BY_POLICY;
        } else {
            return false;
        }
        sender.logError(outcome, failure);
        DiscardMonitor.discard();
        senderPool.release(sender);
        return true;
    }
```

Note on the congestion branch: a congestion failure only reaches here under `DiscardPolicy.DISCARD`, because
`sendWithRetry` retries it indefinitely under every other policy. That is the cross-layer invariant from the
spec — Task 5 documents and tests it.

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=SenderDispatchClassificationTest -pl njams-sdk`
Expected: PASS.

- [ ] **Step 5b: Write the failing stale-report test (Race A)**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/StaleFailureReportSpecTest.java`,
following the harness pattern of `SenderRetirementSpecTest` in the same package:

```java
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderPoolTestAccess;

/**
 * Specifies that a failure reported about a sender the group already retired is absorbed rather than treated as a
 * new outage. A slow thread can finish retrying long after its connection was replaced; its verdict is evidence
 * about a connection generation that no longer exists.
 */
public class StaleFailureReportSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void aFailureReportedAfterRecoveryDoesNotOpenASecondOutage() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object stale = pool.acquire();          // held by the thread that is still retrying

        // Another sender fails: one reconnector is elected and `stale` is retired underneath its borrower.
        pool.forceReconnecting();
        assertTrue("the checked-out sender must be retired by the group failure", pool.isRetired(stale));
        assertEquals("one outage, one notification", 1, pool.exceptionListenerFireCount());

        // The reconnect completes and publishes a healthy sender.
        Object recovered = pool.publishConnectedSender();
        assertNotNull("the connector must publish a sender", recovered);
        assertTrue("the group must be healthy again", pool.awaitRecovered(5, TimeUnit.SECONDS));

        // Only now does the slow thread report the failure it ended on.
        pool.reportFailure(stale, new IllegalStateException("stale failure from the replaced connection"));

        assertEquals("a stale report must not elect a second reconnector or re-notify listeners",
            1, pool.exceptionListenerFireCount());
        assertFalse("the group must stay healthy", pool.isConnectionFailure());
        assertFalse("the freshly published sender must not be destroyed by a stale report",
            pool.wasClosed(recovered));
        assertTrue("the stale sender itself is still closed", pool.wasClosed(stale));
    }

    @Test
    public void aFailureOnACurrentSenderStillOpensAnOutage() throws Exception {
        // Contrast case: the guard must absorb only stale reports, never a real one.
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object current = pool.acquire();

        pool.reportFailure(current, new IllegalStateException("genuine connection loss"));

        assertEquals("a failure on a current sender must elect a reconnector", 1,
            pool.exceptionListenerFireCount());
        assertTrue(pool.awaitConnectionFailure(5, TimeUnit.SECONDS));
    }
}
```

- [ ] **Step 5c: Run it to verify it fails**

Run: `mvn test -Dtest=StaleFailureReportSpecTest -pl njams-sdk`
Expected: `aFailureReportedAfterRecoveryDoesNotOpenASecondOutage` FAILS — today the stale report calls `failGroup`
a second time, so the fire count is 2, the group is failed again, and `recovered` has been closed by
`drain(unlocked)`. `aFailureOnACurrentSenderStillOpensAnOutage` already passes.

- [ ] **Step 5d: Add the stale-report guard and the retirement marking to `SenderPool`**

In `reportFailure` (`SenderPool.java:470-489`), keep the `retired.remove` result instead of discarding it:

```java
        final boolean wasRetired;
        synchronized (lock) {
            locked.remove(sender);
            // A retired sender never returns to `unlocked` - release() and this method both destroy it - so a
            // failure it reports is always about a connection generation the group has already given up on.
            // Acting on it would re-fail a healthy group, destroy the sender the connector just published, and
            // notify the listeners a second time for one outage.
            wasRetired = retired.remove(sender);
            if (!reconnecting && !wasRetired) {
                toDestroy = failGroup(brokenConnection);
                listeners = new ArrayList<>(exceptionListeners);
            }
        }
```

Then, in `failGroup` (`:561-579`), tell the retired senders so a send still running on one stops waiting (Task 1's
outer loop reads this):

```java
        final List<AbstractSender> toDestroy = drain(unlocked);
        retired.addAll(locked);
        // Let a send still retrying on one of these unwind instead of waiting out congestion on a connection the
        // group no longer uses. Safe under the lock: it only sets a volatile flag and calls no transport code.
        locked.forEach(AbstractSender::setRetired);
        return toDestroy;
```

Update `reportFailure`'s Javadoc to say that a report for an already-retired sender is absorbed, and extend the
class Javadoc's "Failure handling and retirement" paragraph (`:56-66`), which currently says only that "later
reports for the same outage are absorbed" — that now also covers reports arriving *after* the outage closed.

- [ ] **Step 5e: Run it to verify it passes**

Run: `mvn test -Dtest=StaleFailureReportSpecTest+SenderRetirementSpecTest -pl njams-sdk`
Expected: PASS. `SenderRetirementSpecTest` must stay green — it pins the retirement behavior this builds on.

- [ ] **Step 6: Run the surrounding suites**

Run the whole `communication` surface, since this task changes the pool every lifecycle spec runs against:

Run: `mvn test -Dtest='NjamsSenderTest+SenderPoolTest+SenderFailureClassificationTest+SenderCloseOrderingSpecTest+StartupFailBehaviorTest+SenderRecoverySignalSpecTest+SharedSenderOutageSpecTest+SenderPoolAcquireSpecTest+ConnectionCoordinatorTest' -pl njams-sdk`
Expected: PASS. If one fails, it is asserting the old blanket-`reportFailure` behavior — stop and report it rather than editing it; it is not covered by the Gate approval, which names five specific tests and no others.

Then the full suite: `mvn test -pl njams-sdk` — expected: PASS.

- [ ] **Step 7: Commit**

Two commits, so the pre-existing pool defect is reviewable on its own:

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java \
        njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/StaleFailureReportSpecTest.java
git commit -m "SDK-476 Absorb a send failure reported after the group already recovered"

git add njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java \
        njams-sdk/src/test/java/com/im/njams/sdk/communication/TestSender.java \
        njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderDispatchClassificationTest.java \
        njams-sdk/src/test/java/com/im/njams/sdk/communication/ClassifyingFailureSender.java \
        njams-sdk/src/test/java/com/im/njams/sdk/communication/RetiredThenSucceedingSender.java
git commit -m "SDK-476 Decide sender retirement from the failure classification"
```

---

### Task 3: Migrate `HttpSender` onto the shared machinery

**Requires the Gate above to be resolved.** Three `HttpSenderTest` tests change here.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/http/HttpSender.java:368-451, 490-507`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/http/HttpSenderTest.java`

**Interfaces:**
- Consumes: `sendWithRetry(SendAttempt)`, `SendFailureOutcome`, `logError` (Task 1); `dispatch`'s classification (Task 2).
- Produces: `HttpSender` no longer has `MAX_TRIES`/`EXCEPTION_IDLE_TIME`; its per-chunk attempt throws `HttpStatusException` for any non-2xx status and `HttpSendException` for a client-side I/O failure, on every attempt rather than only after exhaustion.

- [ ] **Step 1: Write the failing tests**

Add to `HttpSenderTest.java`:

```java
    @Test
    public void payloadTooLargeThrowsOnTheFirstAttemptSoTheSdkCanDropTheMessage() throws IOException {
        Map<String, String> props = validProps();
        props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        HttpSender sender = new HttpSender();
        sender.init(settings(props));
        sender.client = mockClientReturning(response(sender, 413));
        try {
            sender.send(logMessage(), "session-1");
            fail("expected the rejected message to be reported to the SDK");
        } catch (HttpStatusException expected) {
            assertEquals(413, expected.getStatusCode());
        }
        verify(sender.client, times(1)).newCall(any(Request.class));
    }

    @Test
    public void connectionLossUnderConnectionLossPolicyIsReportedInsteadOfSilentlyDiscarded() throws IOException {
        Map<String, String> props = validProps();
        props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "onconnectionloss");
        HttpSender sender = new HttpSender();
        sender.init(settings(props));
        sender.client = mockClientThrowing();
        try {
            sender.send(logMessage(), "session-1");
            fail("a genuine connection loss must reach the SDK, not be swallowed by the sender");
        } catch (HttpSendException expected) {
            // expected: the pool retires the sender, reconnects and notifies listeners
        }
        // one attempt plus the bounded smoothing window
        verify(sender.client, times(4)).newCall(any(Request.class));
    }

    @Test
    public void discardPolicyDoesNotDelayTheApplicationOnAConnectionLoss() throws IOException {
        HttpSender sender = initializedSender();
        sender.client = mockClientThrowing();
        try {
            sender.send(logMessage(), "session-1");
            fail("expected the failure to be reported");
        } catch (HttpSendException expected) {
            // expected
        }
        verify(sender.client, times(1)).newCall(any(Request.class));
    }

    @Test
    public void isCongestionWalksTheCauseChain() {
        HttpSender sender = initializedSender();
        assertTrue("a wrapped 429 must still classify as congestion", sender.isCongestion(
            new NjamsSdkRuntimeException("Failed to send log message", new HttpStatusException(sender.url, 429))));
    }

    @Test
    public void isMessageRejectedWalksTheCauseChain() {
        HttpSender sender = initializedSender();
        assertTrue("a wrapped 413 must still classify as rejected", sender.isMessageRejected(
            new NjamsSdkRuntimeException("Failed to send log message", new HttpStatusException(sender.url, 413))));
    }
```

Also add this helper next to the existing `initializedSender()`, so a test that drives many congestion responses
does not spend seconds asleep:

```java
    /** A sender with the retry delays removed, for tests that drive many failed attempts. */
    private static HttpSender fastSender(Map<String, String> props) {
        HttpSender sender = new HttpSender() {
            @Override
            protected long[] getSmoothingDelaysMs() {
                return new long[] { 0, 0, 0 };
            }

            @Override
            protected long getCongestionRetryDelayMs() {
                return 0;
            }
        };
        sender.init(settings(props));
        return sender;
    }
```

Then apply the Gate-approved changes to the existing tests:
- `sendDiscardsOnConnectionLossPolicy` — delete it; `connectionLossUnderConnectionLossPolicyIsReportedInsteadOfSilentlyDiscarded` above replaces it with the corrected expectation.
- `sendDiscardsImmediatelyOnPayloadTooLargeRegardlessOfDiscardPolicy` — delete it; `payloadTooLargeThrowsOnTheFirstAttemptSoTheSdkCanDropTheMessage` replaces it.
- `sendDiscardsImmediatelyOnServiceUnavailableUnderConnectionLossPolicy` — change `times(1)` to `times(4)` and wrap the `send` call in the same `try { ... fail(...) } catch (HttpStatusException expected) {}` shape as the new tests, keeping its comment about 503 deliberately not being congestion.
- `sendRetriesPastTransportRetryBudgetOnCongestionUnderConnectionLossPolicy` — **assertions unchanged**; only build its sender with `fastSender(props)` instead of `new HttpSender()` + `init(...)`. This is the approved speed-up: `times(congestionAttempts + 1)` still holds and still proves congestion is never discarded or escalated.

The new `connectionLossUnderConnectionLossPolicyIsReportedInsteadOfSilentlyDiscarded` test deliberately does *not*
use `fastSender` — it asserts the 4-attempt smoothing window, and running it against the real delays (~1s) is
worth having one test that exercises the production timing.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=HttpSenderTest -pl njams-sdk`
Expected: the five new tests fail (413 returns instead of throwing; 4 attempts expected but 20 or 1 seen; wrapped classification returns false).

- [ ] **Step 3: Replace HTTP's retry loop with one honest attempt**

In `HttpSender.java`, delete the constants `EXCEPTION_IDLE_TIME` and `MAX_TRIES` (lines 119-120). Replace `tryToSend(final String json, final Map<String, String> headers)` (lines 395-452) with:

```java
    private void sendChunk(final String json, final Map<String, String> headers) throws Exception {
        sendWithRetry(() -> {
            final int responseStatus;
            try {
                responseStatus = send(json, headers);
            } catch (Exception ex) {
                LOG.trace("Failed to send to {}:\n{}\nheaders={}", url, json, headers, ex);
                // A client-side failure: wrapping it in HttpSendException is what tells the SDK to reconnect
                // the command receiver too, which a mere error status must not do.
                throw new HttpSendException(url, ex);
            }
            if (responseStatus != OK && responseStatus != NO_CONTENT) {
                throw new HttpStatusException(url, responseStatus);
            }
        });
    }
```

Rename the two call sites of the old per-chunk method in `tryToSend(final CommonMessage, final Map)` (lines 387 and 390) from `tryToSend(chunk, headers)` / `tryToSend(data, headers)` to `sendChunk(chunk, headers)` / `sendChunk(data, headers)`, and widen that method's `throws InterruptedException` to `throws Exception`.

Add the transport's own logging by overriding `logError` (place it next to the classifiers):

```java
    /**
     * Logs a send failure with the HTTP detail the shared retry handling does not have: the status code and the
     * target URL, plus the setting to check when the target rejected the payload outright.
     *
     * @param outcome what the SDK decided about this failure.
     * @param failure the failure itself.
     */
    @Override
    protected void logError(SendFailureOutcome outcome, Throwable failure) {
        final int status = failure instanceof HttpStatusException ? ((HttpStatusException) failure).getStatusCode()
            : -1;
        switch (outcome) {
        case MESSAGE_REJECTED:
            LOG.error("Server permanently rejected the message with status {} from {}; discarding it. "
                + "Check {} or the target's payload size limit.", status, url,
                NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE);
            break;
        case DISCARDED_BY_POLICY:
            LOG.debug("Applying discard policy [{}] after status {} from {}. Message discarded.", discardPolicy,
                status, url);
            break;
        case ESCALATING:
            LOG.warn("The nJAMS server HTTP endpoint {} could not be reached (status {}).", url, status);
            break;
        default:
            LOG.debug("Retrying send to {} after failure (status {}).", url, status, failure);
            break;
        }
    }
```

Make both classifiers walk the cause chain, so classification survives the wrapping the `send(...)` methods
apply. Replace the bodies at lines 490-507:

```java
    @Override
    protected boolean isCongestion(Throwable failure) {
        return statusOf(failure) == TOO_MANY_REQUESTS;
    }

    @Override
    protected boolean isMessageRejected(Throwable failure) {
        return statusOf(failure) == PAYLOAD_TOO_LARGE;
    }

    /** @return the status code reported anywhere in the failure's cause chain, or -1 if none was. */
    private static int statusOf(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof HttpStatusException) {
                return ((HttpStatusException) current).getStatusCode();
            }
        }
        return -1;
    }
```

Keep the existing Javadoc on both classifiers; extend each with one sentence noting the classification holds
anywhere in the cause chain.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=HttpSenderTest -pl njams-sdk`
Expected: PASS, including the Gate-adjusted tests.

- [ ] **Step 5: Verify the whole suite and the build gates**

Run: `mvn test -pl njams-sdk` — expected: PASS.
Run: `mvn validate -Pcheckstyle -pl njams-sdk` and `mvn javadoc:javadoc -pl njams-sdk` — expected: clean.

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/http/HttpSender.java \
        njams-sdk/src/test/java/com/im/njams/sdk/communication/http/HttpSenderTest.java
git commit -m "SDK-476 Move HttpSender onto the shared retry handling"
```

---

### Task 4: Migrate `JmsSender` onto the shared machinery

**Requires the Gate above.** Two `JmsSenderTest` tests change here.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/JmsSender.java:280, 305-334`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/JmsSenderTest.java`

**Interfaces:**
- Consumes: `sendWithRetry(SendAttempt)`, `SendFailureOutcome`, `logError` (Task 1).
- Produces: `JmsSender` without its own `tryToSend` retry loop and without local `MAX_TRIES`/`EXCEPTION_IDLE_TIME` constants; `ResourceAllocationException` propagates unchanged when the policy gives up on it.

- [ ] **Step 1: Write the failing tests**

Add to `JmsSenderTest.java`:

```java
    @Test
    public void queueFullIsSmoothedThenRetriedIndefinitelyUnderNonePolicy()
        throws JMSException, InterruptedException {
        Properties props = new Properties();
        props.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        JmsSender noneSender = spy(new JmsSender() {
            @Override
            String serialize(CommonMessage msg) {
                return "dummy data";
            }

            @Override
            protected long[] getSmoothingDelaysMs() {
                return new long[] { 0, 0, 0 };
            }

            @Override
            protected long getCongestionRetryDelayMs() {
                return 0;
            }
        });
        noneSender.init(ClientSettings.from(props));
        final MessageProducer producer = noneSender.eventProducer = mock(MessageProducer.class);
        final Session session = noneSender.session = mock(Session.class);
        when(session.createTextMessage(any())).thenReturn(mock(TextMessage.class));
        // more failures than the smoothing window: proves congestion is retried past it, never discarded
        final int[] attempt = { 0 };
        doAnswer(invocation -> {
            if (++attempt[0] <= 10) {
                throw new ResourceAllocationException("Queue limit exceeded");
            }
            return null;
        }).when(producer).send(any());
        final CommonMessage msg = mock(CommonMessage.class);
        when(msg.getPath()).thenReturn("path");
        noneSender.sendMessage(producer, msg, "messageType", null);
        verify(producer, times(11)).send(any());
    }

    @Test
    public void queueFullUnderDiscardPolicyGivesUpOnTheFirstAttempt() throws JMSException, InterruptedException {
        final MessageProducer producer = sender.eventProducer = mock(MessageProducer.class);
        final Session session = sender.session = mock(Session.class);
        when(session.createTextMessage(any())).thenReturn(mock(TextMessage.class));
        doThrow(new ResourceAllocationException("Queue limit exceeded")).when(producer).send(any());
        final CommonMessage msg = mock(CommonMessage.class);
        when(msg.getPath()).thenReturn("path");
        try {
            sender.sendMessage(producer, msg, "messageType", null);
            fail("expected the congestion failure to be reported under the discard policy");
        } catch (ResourceAllocationException expected) {
            // expected: the SDK drops the message without retiring the connection
        }
        verify(producer, times(1)).send(any());
    }
```

Add the imports `org.junit.Assert.fail` and `org.mockito.Mockito.doAnswer` if not already present.

Then apply the Gate-approved changes to the two existing tests:
- `queueIsFullTest` — delete it; `queueFullIsSmoothedThenRetriedIndefinitelyUnderNonePolicy` covers the
  retry-then-succeed path under a policy that actually wants retries.
- `queueIsFullMaxTriesTest` — delete it; `queueFullUnderDiscardPolicyGivesUpOnTheFirstAttempt` replaces it.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=JmsSenderTest -pl njams-sdk`
Expected: FAIL — under `DISCARD` today the producer is called 100 times, not once.

- [ ] **Step 3: Replace JMS's retry loop with one honest attempt**

In `JmsSender.java`, replace `tryToSend(MessageProducer producer, TextMessage textMessage)` (lines 305-334)
with:

```java
    private void tryToSend(MessageProducer producer, TextMessage textMessage) throws Exception {
        sendWithRetry(() -> producer.send(textMessage));
    }
```

Widen the `throws` clauses along the call path so the shared machinery's `Exception` propagates to the existing
wrapping in `send(LogMessage, String)` / `send(ProjectMessage, String)` / `send(TraceMessage, String)`, which
already turn it into `NjamsSdkRuntimeException`:
- `sendChunks(...)` (line 254): `throws JMSException, InterruptedException` → `throws Exception`
- `sendMessage(...)` (line 229): `throws JMSException, InterruptedException` → `throws Exception`

Add JMS's own logging next to its classifiers:

```java
    /**
     * Logs a send failure with the JMS detail the shared retry handling does not have — that a
     * {@link ResourceAllocationException} means the destination is momentarily full rather than unreachable.
     *
     * @param outcome what the SDK decided about this failure.
     * @param failure the failure itself.
     */
    @Override
    protected void logError(SendFailureOutcome outcome, Throwable failure) {
        switch (outcome) {
        case DISCARDED_BY_POLICY:
            LOG.debug("JMS destination limit exceeded; applying discard policy [{}].", discardPolicy);
            break;
        case ESCALATING:
            LOG.warn("Failed to send to the JMS destination; treating the connection as broken.", failure);
            break;
        default:
            LOG.debug("Retrying JMS send after failure.", failure);
            break;
        }
    }
```

Remove the now-unused `ResourceAllocationException` import only if nothing else in the file uses it — note
`isCongestion` still does, so it stays.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=JmsSenderTest -pl njams-sdk`
Expected: PASS.

- [ ] **Step 5: Verify the whole suite and the build gates**

Run: `mvn test -pl njams-sdk` — expected: PASS.
Run: `mvn validate -Pcheckstyle -pl njams-sdk` and `mvn javadoc:javadoc -pl njams-sdk` — expected: clean.

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/JmsSender.java \
        njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/JmsSenderTest.java
git commit -m "SDK-476 Move JmsSender onto the shared retry handling"
```

---

### Task 5: Harden the classifier contract and pin the cross-layer invariant

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java:37-56, 223-266`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderRetryLoopTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1-4.
- Produces: no new API — documentation plus the test that keeps the invariant honest.

- [ ] **Step 1: Write the failing test**

Add to `SenderRetryLoopTest.java`:

```java
    @Test
    public void congestionNeverEscapesTheOuterLoopExceptUnderDiscard() throws Exception {
        // The invariant the dispatching loop depends on: if a congestion failure escaped under a policy that
        // never discards, the SDK would drop a message those policies promise to keep retrying forever.
        // Retirement is the one other way out, and it does not violate this: it unwinds with
        // SenderRetiredException, which dispatch retries rather than classifying as congestion.
        for (String policy : new String[] { "none", "onconnectionloss" }) {
            RetrySender sender = new RetrySender(policy);
            sender.run(200, new Congested());
            assertEquals("congestion must be retried, not escalated, under " + policy, 201, sender.attempts);
        }
        RetrySender discarding = new RetrySender("discard");
        try {
            discarding.run(Integer.MAX_VALUE, new Congested());
            fail("only the discard policy may let congestion reach the caller");
        } catch (Congested expected) {
            assertEquals(1, discarding.attempts);
        }
    }

    @Test
    public void anUnclassifiedFailureIsStillEscalatedUnderPoliciesThatNeverDiscard() throws Exception {
        // Contrast case: the invariant is specific to congestion, not a blanket "these policies never throw".
        for (String policy : new String[] { "none", "onconnectionloss" }) {
            RetrySender sender = new RetrySender(policy);
            try {
                sender.run(Integer.MAX_VALUE, new RuntimeException("socket closed"));
                fail("a real connection problem must be escalated under " + policy);
            } catch (RuntimeException expected) {
                assertEquals(4, sender.attempts);
            }
        }
    }
```

- [ ] **Step 2: Run the test to verify it passes or fails honestly**

Run: `mvn test -Dtest=SenderRetryLoopTest -pl njams-sdk`
Expected: PASS if Task 1 implemented the loop correctly — this test exists to keep it that way, so a pass here
is the intended outcome. If it fails, the loop is wrong; fix `sendWithRetry`, not the test.

- [ ] **Step 3: Harden the classifier Javadoc**

In `AbstractSender.java`, add this paragraph to the Javadoc of **both** `isCongestion` (line ~223) and
`isMessageRejected` (line ~249), before the `@param` block:

```java
     * <p>
     * <b>This method must never throw.</b> It has to reach a decision: when it cannot — including when
     * inspecting the failure itself goes wrong — the answer is {@code false}, the safe fallback. Callers rely on
     * getting an answer, and the same failure is classified more than once while a send is being decided, so an
     * implementation must also be a pure function of its argument: same throwable in, same answer out.
     * <p>
     * Classify by inspecting the whole cause chain, not just the throwable itself: a transport's own
     * {@code send} may wrap the failure before the SDK gets to classify it.
```

- [ ] **Step 4: Correct the class Javadoc, which now describes the old model**

`AbstractSender`'s class Javadoc currently tells implementers to do exactly what this ticket takes away from
them. Replace both the second paragraph (lines 42-44: *"A `send` implementation must not block indefinitely. The
built-in transports each bound their own retries and throw once exhausted; do the same in your own
implementation."*) and the third (lines 45-49, which repeats the connect/close/reconnect instruction) with a
single paragraph that keeps the still-true facts — one reconnect per group, and the async-failure hook:

```java
 * <b>Do not implement retrying or discard-policy handling.</b> Implement {@link #connect()}, {@link #close()}
 * and the typed {@code send} methods as single honest attempts that throw on failure, and route each unit you
 * actually put on the wire through {@link #sendWithRetry(SendAttempt)} — it owns the bounded retrying, the
 * discard policy and when a failure is escalated, consistently for every transport.
 * <p>
 * The SDK also drives the connection lifecycle: do not implement reconnect logic — it runs exactly one reconnect
 * per sender group. If your transport detects a broken connection asynchronously, report it with
 * {@link #notifyConnectionFailure(Exception)}.
```

- [ ] **Step 5: Fix the two other Javadoc blocks this change makes untrue**

`code-quality-general.md` requires every nearby comment to still be correct after a change. Two are not:

1. **`send(CommonMessage, String)` (lines 119-129)** claims *"any failure propagates to the caller, which retires
   this sender and retries the message on a fresh one."* Retirement is now conditional. Replace that sentence
   with: *"any failure propagates to the caller, which decides from this sender's own classification whether to
   retire it or merely drop the message."* Leave the rest of the block — the `acquire()`-guarantees-connected
   paragraph is still exactly right.

2. **`isCongestion`'s last paragraph (lines 236-240)** says the classification is consumed *"by a transport's own
   send-retry loop"* — that loop no longer exists. Replace that paragraph with:

```java
     * Consumed to decide whether a registered {@link SenderRecoveryListener} is notified when the group recovers,
     * by {@link #sendWithRetry(SendAttempt)} to decide whether a failed send keeps applying back pressure instead
     * of being escalated, and by the SDK to decide whether a failure it could not send retires the connection or
     * only drops the message. It does not influence the group's own failed/reconnecting state.
```

Re-read both blocks after editing and confirm no other sentence in them still describes per-transport retrying.

- [ ] **Step 6: Verify the build gates**

Run: `mvn test -pl njams-sdk` — expected: PASS.
Run: `mvn validate -Pcheckstyle -pl njams-sdk` and `mvn javadoc:javadoc -pl njams-sdk` — expected: clean, with
every `{@link}` in the new Javadoc resolving. `{@link SenderRecoveryListener}` and `{@link SendAttempt}` are both
in this package, so neither needs an import.

- [ ] **Step 7: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java \
        njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderRetryLoopTest.java
git commit -m "SDK-476 Pin the congestion escalation invariant and harden the classifier contract"
```

---

### Task 6: Migrate `KafkaSender` last

Kafka is deprecated (`.claude/rules/kafka-argos-deprecated.md`): no new feature investment, tests best-effort.
This task exists so Kafka stops being the one transport with its own discard handling, not to invest in it.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/kafka/KafkaSender.java:244-326`

**Interfaces:**
- Consumes: `sendWithRetry(SendAttempt)` (Task 1), `dispatch`'s classification (Task 2).
- Produces: `KafkaSender` with no `DiscardMonitor` call and no unconditional rethrow of its own.

- [ ] **Step 1: Replace Kafka's send handling with one honest attempt**

In `KafkaSender.java`, replace `tryToSend(final ProducerRecord<String, String> producerRecord)` (lines 298-326)
with:

```java
    /**
     * One honest attempt per record. Kafka's own client-internal retrying still applies underneath, controlled by
     * the producer settings; the SDK's retry and discard handling sits on top of whatever that reports back.
     *
     * @param producerRecord the record to send.
     * @throws Exception if the record could not be sent.
     */
    private void tryToSend(final ProducerRecord<String, String> producerRecord) throws Exception {
        sendWithRetry(() -> {
            final long start = System.currentTimeMillis();
            try {
                final Future<RecordMetadata> future = producer.send(producerRecord);
                final RecordMetadata result = future.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Send record result: {} after {}ms\n{}", result, System.currentTimeMillis() - start,
                        producerRecord);
                } else if (LOG.isDebugEnabled()) {
                    LOG.debug("Send record result: {} after {}ms", result, System.currentTimeMillis() - start);
                }
            } catch (KafkaException | IllegalStateException | ExecutionException | TimeoutException e) {
                LOG.debug("Failed to send record after {}ms", System.currentTimeMillis() - start, e);
                throw getAsyncCause(e);
            }
        });
    }
```

This removes both defects the old body carried: the two `DiscardMonitor.discard()` calls (discard counting now
happens once, in `NjamsSender.dispatch`) and the unconditional `throw cause` that fired even after a discard had
already been counted — Kafka over-reported where HTTP under-reported.

Then delete the two imports this leaves unused — `org.apache.kafka.common.errors.RecordTooLargeException`
(line 46) and `com.im.njams.sdk.communication.DiscardMonitor` (line 60). Keep the `DiscardPolicy` import: line
102 still uses it. Keep `Future` and `TimeUnit`: the body above still does.

- [ ] **Step 2: Verify it compiles and nothing regressed**

Run: `mvn test -pl njams-sdk` — expected: PASS. There are no Kafka sender tests by policy; the compile plus the
unchanged rest of the suite is the bar here.
Run: `mvn validate -Pcheckstyle -pl njams-sdk` and `mvn javadoc:javadoc -pl njams-sdk` — expected: clean.

- [ ] **Step 3: Check whether this closes SDK-475**

Read SDK-475 (*Kafka sender double-counts discards and disables producer retries under any discard policy*) and
report — do not decide alone — whether its discard-double-counting half is now fixed by this change, and whether
the `ProducerConfig.RETRIES_CONFIG` half (`KafkaSender.java:102`) is still open. If it is fully fixed, propose
resolving SDK-475 to the user rather than resolving it as a side effect of this ticket.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/kafka/KafkaSender.java
git commit -m "SDK-476 Move KafkaSender onto the shared retry handling"
```

---

### Task 7: Document the changed discard-policy behavior

`njams.sdk.discardpolicy` behaves observably differently after this work, which
`.claude/rules/settings-management.md` and `.claude/rules/wiki-drafts.md` require documenting. Invoke the
`njams-settings-sync` skill for this task: the setting's behavior changed, even though no key was added or
renamed.

**Files:**
- Modify: `wiki/FAQ.md`
- Check (likely unchanged): `njams-sdk-sample-client/src/main/resources/settings_full.properties`

**Interfaces:**
- Consumes: the final behavior from Tasks 1-6.
- Produces: documentation only.

- [ ] **Step 1: Find the discard-policy documentation**

Run: `grep -n "discardpolicy\|discard policy\|onconnectionloss" wiki/FAQ.md njams-sdk-sample-client/src/main/resources/settings_full.properties`
Read every hit before editing.

- [ ] **Step 2: Update `wiki/FAQ.md`**

Rewrite the discard-policy explanation so it describes the three policies by what they now do, keeping the
existing page's voice and structure:
- `none` — never discards. A momentarily full destination is waited out on the same connection; a broken
  connection is retried until it comes back.
- `onconnectionloss` — waits out a momentarily full destination exactly like `none`, and discards only once a
  failure is confirmed to be a connection problem. That confirmation is now always reported, so the SDK
  reconnects and any registered sender listener is notified.
- `discard` (default) — never delays the monitored application: a send that does not succeed immediately is
  given up on at once, and the message is dropped.

State that all three transports behave identically here, since the handling is no longer per-transport. Do not
document the concrete retry counts or delays — per `.claude/rules/public-api-design.md` those are implementation
values that would go stale in documentation.

- [ ] **Step 3: Verify `settings_full.properties` still matches**

The key, its allowed values and its default are unchanged, so this file most likely needs no edit. If its
inline comment describes retry behavior that is now wrong, correct just that comment.

- [ ] **Step 4: Commit**

```bash
git add wiki/FAQ.md
git commit -m "SDK-476 #comment Document the unified discard-policy behavior"
```

---

## Closing out

- [ ] Run the full build once more: `mvn clean install -pl njams-sdk`.
- [ ] Propose an update to SDK-476's description so its acceptance criteria cover the two reconnect races folded
      into this ticket (they were found after the ticket was written). Keep it WHAT-level per
      `jira-workflow.md` — "a failure reported for a connection the group already replaced must not be treated as
      a new outage, and a message must not be pinned to a replaced connection" — with no implementation detail.
      Do not edit the ticket without the user's confirmation.
- [ ] Confirm every acceptance criterion on SDK-476 is met, naming the test that proves each.
- [ ] Decide the `breaking-change` label against the real diff via the `njams-ticket-finish` skill. Expect
      **yes**, on observable-behavior grounds (`njams.sdk.discardpolicy` acts differently for all transports),
      even though the change is source-compatible for a third-party transport — no abstract method signature
      changed and nothing was removed, so an existing custom sender keeping its own retry loop still compiles.
- [ ] Remove this plan file when SDK-476 is actually resolved in Jira, per
      `.claude/rules/docs-superpowers-lifecycle.md`. The spec stays permanently.
