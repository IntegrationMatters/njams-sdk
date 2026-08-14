# SDK-472 — Single Threaded Sender Reconnect: Design Spec

**Ticket:** SDK-472 — *Single threaded sender reconnect*
**Branch:** `SDK-375` (based on `6.0-dev`)
**Status of this doc:** design agreed in brainstorming; pending user review, then an implementation plan
(`writing-plans`).
**Related tickets:** SDK-375 (*Revise sender lifecycle handling* — parent), SDK-473 (*Failing sender should
trigger receiver reconnect* — depends on this one, see §8), SDK-475 (*Kafka sender double-counts discards and
disables producer retries under any discard policy* — documentation-only, see §8.2).
**Companion docs:** `docs/superpowers/specs/2026-07-02-sdk-375-sender-lifecycle-design.md`,
`docs/SDK-375-sender-lifecycle-analysis.md`, `docs/SDK-375-working-agreement.md`.

---

## 1. Problem

A connection failure is currently detected and handled by each pooled sender independently and in parallel. All
senders in a group use the same kind of connection to the same endpoint, so the connection works either for all
of them or for none — the parallelism is pure redundancy.

Verified current behaviour:

- `AbstractSender.send(CommonMessage, String)` (`AbstractSender.java:276-325`) runs a retry loop. On a send
  failure it notifies the `SenderExceptionListener`s, then calls `onException(e)` → `close()` +
  `reconnect(e)`.
- `reconnect(Exception)` (`AbstractSender.java:201-217`) spawns a **per-sender** daemon thread running
  `doReconnect` (`AbstractSender.java:223-246`), which loops `connect()` every second until connected or the
  group shuts down. With `maxSenderThreads` senders in flight, that is up to `maxSenderThreads` concurrent
  reconnect loops hammering the same endpoint.
- The `SenderExceptionListener`s fire once per failed send attempt per sender, i.e. a storm during an outage.
- `SenderPool.validate(AbstractSender)` (`SenderPool.java:109-112`) unconditionally returns `true`, carrying the
  comment `// TODO: there must be a better solution!`. There is currently no way for the pool to refuse to hand
  out a sender whose connection is known to be dead.
- `SenderPool.isConnectionFailure()` (`SenderPool.java:88-90`) determines the group's failure state by scanning
  live sender instances. It feeds `MaxQueueLengthHandler`'s `ON_CONNECTION_LOSS` branch.

The ticket asks that the pool take responsibility: one sender reconnects, the rest are shut down, the pool hands
out nothing until the reconnect succeeds, and messages held by shut-down senders survive.

## 2. Goals

- Exactly one reconnect attempt runs per sender group per outage.
- The pool owns the decision, not the individual sender.
- No sender is handed out while a reconnect is in progress.
- A message in flight when its sender is retired is not lost.
- The lifecycle logic leaves the sender SPI, so a transport implementation is a plain
  connect / send / close component.

## 3. Non-goals

- **Duplicate delivery and restarted chunk sequences.** If a connection dies mid-message, the retry re-sends the
  whole `CommonMessage`, including chunks the server may already hold. This is pre-existing behaviour, identical
  before and after this change. Out of scope.
- **Re-serialization on retry.** See D2.7 — accepted as pre-existing, with a follow-up ticket to be proposed
  separately.
- **Reconnect backoff strategy.** The sender's flat 1 s retry is kept as-is; see D2.8.
- **Send-failure classification, and the transport-local `ON_CONNECTION_LOSS` checks.** Distinguishing connection
  failures from transient back-pressure and from permanent message-level failures — and correcting the
  transport-local discard checks that depend on that distinction — is deferred to a follow-up ticket
  (D2.10, D2.11, §8.2).
- **Receiver-side changes.** SDK-473 covers the sender→receiver trigger. The receiver's own reconnect loop is
  untouched here.
- **Kafka-specific verification.** Per the project's test-scope agreement, `communication/kafka` is out of scope
  for testing. The Kafka sender is kept compiling and behaviourally consistent, but is not covered by new tests.

## 4. Decisions

| # | Decision |
|---|---|
| D2.1 | The `SenderPool` owns connection-failure handling for its group. A sender never initiates its own reconnect. |
| D2.2 | Exactly one reconnector per group per outage, elected by the first failure to arrive. |
| D2.3 | An in-flight message stays on its worker thread and is re-attempted with a freshly acquired sender. It is **never** re-submitted to the executor. |
| D2.4 | A sender that is checked out is *retired* (flagged), never closed by another thread. Its borrowing worker closes it on release. |
| D2.5 | `acquire()` is the single place where the discard policy decides between waiting and dropping. |
| D2.6 | Under `DiscardPolicy.NONE`, `acquire()` blocks **indefinitely** during a reconnect — matching today's behaviour, where a worker retries forever and back-pressure reaches the application. Under shared communications this parks every `Njams` instance in the JVM, not just one client (§5.4). |
| D2.7 | Serialization stays inside the sender SPI. Re-serialization on retry is pre-existing and unchanged in frequency; hoisting it above the SPI is a separate ticket to be proposed. |
| D2.8 | The reconnect interval stays a flat 1 s. The receiver's exponential 500 ms → 60 s backoff (`AbstractReceiver.java:329-351`) is not adopted here; unifying them is a follow-up candidate. |
| D2.9 | The lifecycle members listed in §6 are **removed** from `AbstractSender`, not deprecated. They are template methods the SDK calls into; a deprecated-but-uninvoked override is silently dead at runtime. |
| D2.10 | Only the **base-class** discard branch (`AbstractSender.java:301-308`) relocates, to `acquire()` — it has to, because its host method is being dismantled. The **transport-local** `ON_CONNECTION_LOSS` checks stay exactly where they are (§8.1). |
| D2.11 | Failure **classification** (connection vs. transient vs. permanent-message) and the correctness of the transport-local checks are deferred to a follow-up ticket. SDK-472 keeps today's "any escaping send exception is a connection failure" rule, with the known consequences recorded in §8.2. |

## 5. Architecture

### 5.1 `SenderConnector` (new, package-private)

One per sender group, owned by the `SenderPool`. Owns **both** the startup connect and the reconnect loop —
splitting them across two classes is worse than either extreme, since they share the same gating state in
`ConnectionCoordinator`.

It holds its own private `AbstractSender`, created through the `CommunicationFactory`. While the connector is
using it — during a startup connect or a reconnect loop — that instance is exclusively the connector's and is
never reachable through `acquire()`. Only once it is successfully connected is ownership transferred to the pool
(see below). Responsibilities:

- `beginConnect()` / `awaitStartup(long)` — relocated verbatim in behaviour from `AbstractSender.java:119-171`.
- `startReconnect(Exception)` — the single reconnect loop, relocated from `AbstractSender.doReconnect`.
- `cancelReconnect()` — interrupts the startup and reconnect threads.

**Startup must be re-enterable by late callers.** `AbstractSender.beginConnect()` is one-shot per instance
(`startupBegun.compareAndSet(false, true)`, `AbstractSender.java:120`), and `awaitStartup` awaits a latch created
by that single call. This works today only because each `NjamsSender.startWithTimeout(...)` borrows a *fresh*
sender and discards it afterwards. With one long-lived connector per group that no longer holds: a second
`Njams` starting later against an already-connected group must get `awaitStartup(...)` returning `true`
immediately, rather than blocking on a spent latch or re-running a connect. The connector must therefore treat
"already connected" as an immediate success for any number of later callers, and only genuinely start a connect
when the group is not connected and no connect is already in flight. This is reachable in the default
(non-shared) configuration too, but shared communications makes it the normal case (§5.4).

On reconnect success it transfers its now-connected sender to the pool as the first available one, clears the
pool's `reconnecting`/`failed` flags, and wakes all waiters. Waiters therefore wake with a working sender already
available rather than each racing to `connect()` on a worker thread. The connector creates a fresh private sender
at the next outage.

This also dissolves the `startupSender` borrow-and-re-arm dance in `NjamsSender`
(`NjamsSender.java:107-108, 221-230, 283-286`): `NjamsSender.beginConnect()` / `startWithTimeout(...)` delegate
straight through. Because the connector never goes through `acquire()`, the hazard of `Njams.start()` blocking on
a reconnecting pool disappears by construction rather than needing a special case.

### 5.2 `SenderPool` (internal infrastructure, not SPI)

New internal API: `acquire()`, `release(AbstractSender)`, `reportFailure(AbstractSender, Exception)`, alongside
the existing `beginShutdown()` / `declareShutdown()` / `shutdown()`.

**Locking.** Today `get()`, `close(...)` and `expireAll()` are all `synchronized` on the pool instance
(`SenderPool.java:127, 160, 183`). Blocking inside a `synchronized get()` would deadlock the pool outright — no
worker could ever return a sender, and `shutdown()` could never run. The pool moves to an explicit `lock` object
with `wait()`/`notifyAll()`, which releases the monitor while a caller waits.

```java
// acquire()
// discardsOnFailure == (discardPolicy is DISCARD or ON_CONNECTION_LOSS)
synchronized (lock) {
    if (reconnecting && discardsOnFailure) {
        DiscardMonitor.discard();
        return null;
    }
    while (reconnecting && !shutdown) {
        lock.wait();                     // releases the monitor
    }
    if (shutdown) {
        return null;
    }
    return takeUnlockedOrCreate();       // returns a CONNECTED sender
}
```

**`acquire()` returns a connected sender.** Today `create()` hands back a `DISCONNECTED` sender and relies on the
send-loop's reconnect machinery to connect it lazily. Now `create()` calls `connect()` explicitly; a failure
there routes into `reportFailure` like any other.

**Retirement** uses the pool's existing collections, adding no bookkeeping state to `AbstractSender` (which is
SPI and must not grow internal fields):

`retired` is a new identity-based set on the pool, alongside the existing `locked` and `unlocked`. `drain(...)`
empties a set and returns its former contents.

```java
// reportFailure(sender, cause)
List<AbstractSender> toDestroy = List.of();
boolean elected;
synchronized (lock) {
    locked.remove(sender);
    elected = !reconnecting;
    if (elected) {
        reconnecting = true;
        failed = true;                   // what isConnectionFailure() now reads
        toDestroy = drain(unlocked);     // idle senders: nobody holds them
        retired.addAll(locked);          // in-use senders: flag only
    }
}
destroy(sender);                         // OUTSIDE the lock
toDestroy.forEach(this::destroy);        // OUTSIDE the lock
if (elected) {
    fireExceptionListeners(cause);       // once per outage
    connector.startReconnect(cause);
}
```

`release(s)` checks `retired.remove(s)`: if it was retired, destroy it; otherwise return it to `unlocked`. This is
the whole of the ticket's "shut down all sender instances except one", without any thread ever closing a sender
another thread is inside — which matters because closing a JMS session or a `KafkaProducer` under a thread
sitting in `producer.send(...)` is undefined.

This also answers the standing `validate()` TODO (`SenderPool.java:109-112`): retirement is the validation the
comment was asking for, so `validate()` is removed.

**Closing happens outside the lock, deliberately.** `KafkaProducer.close()` and JMS `connection.close()` can
block for seconds. Holding the pool monitor across them would stall every `acquire()` and `release()` in the
group, turning a connection blip into a pool-wide freeze.

**Group failure state.** `isConnectionFailure()` returns the pool's `failed` flag instead of scanning live
senders. Without this, the flag would go blind exactly when it matters — the failing senders are destroyed — and
`MaxQueueLengthHandler`'s `ON_CONNECTION_LOSS` branch would silently stop discarding.

**Exception listeners move to the pool.** They are fired once per outage from `reportFailure` (§8) rather than
per sender per failed attempt, so the pool no longer copies them onto each sender instance
(`SenderPool.java:83-86, 103-105`) — which matters because senders are created and destroyed constantly during an
outage. While rewriting this, the listener collection also gets fixed: it is currently a plain
`IdentityHashMap`-backed set mutated without synchronization (`SenderPool.java:61-62`) while `create()` iterates
it. That race is latent and pre-existing — only reachable with concurrently constructing `Njams` instances under
shared communications — but the code path is being rewritten anyway, so it is made thread-safe here rather than
left behind.

### 5.3 `ConnectionCoordinator`

The `connecting` counter, `beginReconnect()`, `reconnectingCount()` and the "{N} senders are reconnecting now"
logging exist only to coordinate parallel reconnects; with one reconnector they collapse to a boolean. The gating
state encoding SDK-375's decisions — `wasEverConnected`, `reconnectBeforeConnected`, `shouldShutdown` — is
load-bearing and stays as-is.

### 5.4 Shared communications

`njams.sdk.communication.shared` (`NjamsSettings.java:216`, default `false`) decides in `Njams.getSender()`
(`Njams.java:638-644`) whether an instance gets the JVM-wide singleton via `NjamsSender.takeSharedSender(...)` or
constructs its own `NjamsSender`. Since one `NjamsSender` owns exactly one pool, one executor and one coordinator
(`NjamsSender.java:164-169`), the setting decides whether **a sender group is per-`Njams`-instance or per-JVM**.
Two properties of the shared instance matter here: it is reference-counted, with the real `close()` deferred
until the last user leaves (`NjamsSharedSender`, `NjamsSender.java:54-88`); and it is constructed with the
settings of whichever `Njams` took it first, later takers' settings being ignored (`NjamsSender.java:138`).

Consequences for this design:

- **Shared mode is where the ticket pays off most.** It is today's worst case — N clients through one pool, each
  failing sender spawning its own reconnect loop against one endpoint. After this change: one reconnector for the
  whole JVM.
- **Blocking is JVM-wide.** Under `DiscardPolicy.NONE`, a blocked `acquire()` parks every instance sharing the
  group. This is not a regression — they already share the executor and its bounded queue — but the blast radius
  is the JVM, not one client.
- **Discard policy is first-wins.** `acquire()` applies the pool's policy, which came from the first instance's
  settings. Also not a regression (`MaxQueueLengthHandler` is built from the same settings,
  `NjamsSender.java:168`), but the per-client policy does **not** apply in shared mode and this spec must not
  imply otherwise.
- **Shutdown ordering must not move earlier.** `beginShutdown()` runs from `NjamsSender.close()`, which
  `NjamsSharedSender` overrides to defer until the last user leaves. Waking `acquire()` waiters (§9) must stay
  hung off that same call — moving it any earlier would let one client's `stop()` tear the group down under its
  siblings.
- **Listener fan-out is N receivers per outage.** Each instance registers its receiver on the shared pool
  (`Njams.java:704-705`), so a single outage notifies every instance's listener. This is correct — a shared
  sender failing does mean all those clients lost their sender — but SDK-473 must be designed for N notifications
  per event, not one.
- **Startup is re-entered by every later instance**, which is what makes the connector requirement in §5.1
  load-bearing rather than theoretical.

## 6. Sender SPI reshape (SPI Contract)

`AbstractSender` is a registered SPI type
(`njams-sdk/src/main/resources/META-INF/services/com.im.njams.sdk.communication.AbstractSender`) and therefore
part of the SPI Contract per `public-api-design.md` / `communication-layer.md` — stable, requires confirmation
to change, but distinct from the Client Contract surface SDK users write against. This reshape is an intended
change within SDK-375's mandate and is confirmed for SDK-472.

**Unchanged — everything a custom transport must implement:** `protected abstract void send(LogMessage, String)`,
`send(ProjectMessage, String)`, `send(TraceMessage, String)`, plus `init(ClientSettings)`, `connect()`,
`close()`, `getName()`, the `isConnected()`/`isDisconnected()`/`isConnecting()` accessors, and the
`setConnectionStatus`/`getConnectionStatus` pair. A custom sender implementing only these compiles and runs
unchanged.

**Removed (per D2.9):**

| Member | Replacement |
|---|---|
| `reconnect(Exception)` | `SenderConnector` |
| `doReconnect(Exception)` | `SenderConnector` |
| `onException(Exception)` | `notifyConnectionFailure(Exception)` (below) + `SenderPool.reportFailure` |
| `beginConnect()`, `awaitStartup(long)` | `SenderConnector` |
| `setShouldShutdown(boolean)`, `cancelReconnect()` | `SenderPool` / `SenderConnector` |
| `hasConnectionFailure()` and its field | `SenderPool.isConnectionFailure()` |
| `setConnectionCoordinator(...)` | `SenderPool` owns the coordinator |
| `addExceptionListener(SenderExceptionListener)` and the `exceptionListeners` field | `SenderPool` owns and fires the listeners (§5.2, §8) |

**Reduced:** `send(CommonMessage, String)` keeps only the `instanceof` dispatch to the three abstract methods.
The retry/discard loop (`AbstractSender.java:276-325`) moves to `NjamsSender`'s worker task and `acquire()`. A
custom sender's `send` becomes a single honest attempt that throws on failure.

**Added (additive, therefore safe):**

```java
/** Reports a connection failure detected outside a send call. */
protected final void notifyConnectionFailure(Exception cause)
```

JMS is the only transport that detects failure asynchronously: `JmsSender implements ExceptionListener` and
registers itself on the connection (`JmsSender.java:120`), so failures arrive on a JMS-internal thread with no
send call in flight. `JmsSender.onException(JMSException)` (`JmsSender.java:382-384`) calls
`notifyConnectionFailure` instead of the inherited `onException(Exception)`. Any custom transport with async
failure detection gets the same facility.

**New SPI invariant:** a sender's `send` must not block indefinitely. Retirement converges only because transport
sends are time-bounded — HTTP gives up after 20 × 50 ms (`HttpSender.java:415-425`), JMS after 100 × 50 ms
(`JmsSender.java:310-329`), Kafka waits `requestTimeoutMs` ≤ 6 s (`KafkaSender.java:133, 302`). A custom sender
that blocks forever would stall retirement for its whole group. This must be documented in the `AbstractSender`
Javadoc.

**Migration.** Only a custom sender that overrode `doReconnect`, `onException`, or `beginConnect` breaks, and
those cannot be preserved either way (D2.9). Removal produces a compile error the implementor must confront,
which is the honest outcome for a major release.

**`breaking-change` label:** applies. The SPI Contract changes.

## 7. Message retention ("re-feeding")

The ticket asks that a dying sender re-feed its current message into pooled processing. The design satisfies this
by moving the boundary rather than by moving the message: **the message never leaves the worker thread**; the
sender is the replaceable resource.

```java
// NjamsSender — the executor task
private void dispatch(CommonMessage msg, String clientSessionId) {
    while (!Thread.currentThread().isInterrupted()) {
        AbstractSender sender = senderPool.acquire();   // blocks while the group reconnects
        if (sender == null) {
            return;                                     // shutting down, or discard policy gave up
        }
        try {
            sender.send(msg, clientSessionId);          // one attempt; throws on failure
            senderPool.release(sender);
            return;
        } catch (Exception e) {
            senderPool.reportFailure(sender, e);        // retire; maybe elect the reconnector
            // msg is still on this stack — loop and acquire a fresh sender
        }
    }
}
```

**Why not re-enqueue.** Re-submitting via `executor.execute(...)` from a worker thread is a hard, permanent
deadlock. The executor has a bounded queue (`maxQueueLength`, default 8) and `MaxQueueLengthHandler` blocks the
*calling* thread on a full queue (`MaxQueueLengthHandler.java:83-92`, reached via `NjamsSender.java:155-168`).
During an outage all workers are busy and the queue is full; a worker that re-submits blocks in `queue.put(...)`.
Once every worker does so, nobody is left to drain the queue and the pool never recovers — **even after the
connection returns**. Retention makes this impossible by construction rather than by tuning.

Retention also has no downside relative to re-enqueueing: it consumes no queue slot (preserving back-pressure
semantics), and it introduces no reordering, whereas a re-enqueued message would land behind newer ones.

## 8. Discard policy and the SDK-473 seam

### 8.1 What relocates, and what deliberately does not

Discard-policy decisions live in three distinct kinds of place today. Only one of them is affected by this
ticket.

| Site | Policies handled | Fate in SDK-472 |
|---|---|---|
| `MaxQueueLengthHandler.rejectedExecution` (queue full) | `DISCARD`, `ON_CONNECTION_LOSS`, `NONE` | **Unchanged.** Only its `isConnectionLost` supplier changes source, from a sender scan to the pool's group flag (§5.2). |
| `AbstractSender.java:301-308` (base class, connection state) | `ON_CONNECTION_LOSS` **and** `DISCARD` | **Relocates to `acquire()`** — forced, because the host method's retry loop is dismantled by §7. |
| `HttpSender.java:410`, `JmsSender.java:318`, `KafkaSender.java:318` (transport-local) | `ON_CONNECTION_LOSS` only | **Unchanged.** Left exactly as they are. |

Note that `DISCARD` and `NONE` never appear in a transport implementation at all — the only transport-local
policy check is `ON_CONNECTION_LOSS` (plus Kafka's init-time `!= NONE` guard on the producer retries config,
`KafkaSender.java:102`, also unchanged). So the relocation in this ticket is confined to the base class and does
not touch transport code.

**Why the transport-local checks stay.** They are, measured against the policy's intent, wrong — but fixing them
requires the failure classification this ticket defers (D2.11), and changing them without it would trade one
wrong behaviour for another. They are therefore left untouched here and are the subject of SDK-474.
The analysis is recorded in §8.2 so the follow-up does not have to rediscover it.

### 8.2 Known-wrong behaviour carried forward (SDK-474)

The intent of `ON_CONNECTION_LOSS` is to **distinguish load peaks from connection issues**: a temporarily
exhausted pool, many messages sent at once, or a momentarily slow target system must *not* discard — they must
apply back-pressure. Only a genuinely broken connection may discard.

`MaxQueueLengthHandler.rejectedExecution` implements that correctly (queue full *and* connection lost → discard;
queue full alone → block), and its comment (`MaxQueueLengthHandler.java:62-63`) records why the senders were
given a copy: the policy "should apply also before the buffers are exhausted". The reasoning was sound, but the
transport-local copies then conflated "connection lost" with "this send attempt failed":

| Site | Discards on | Assessment |
|---|---|---|
| `JmsSender.java:317-323` | `ResourceAllocationException` — "the MessageQueue hasn't got enough space" (its own log, line 326) | **Backwards.** That *is* the load peak; it discards exactly the case that must back-pressure. |
| `HttpSender.java:409-414` | POST threw **or** returned non-2xx | Wrong for 503/429/timeout — a slow or overloaded target discards instead of waiting. |
| `KafkaSender.java:318-321` | any Kafka exception, incl. `TimeoutException` | Same: a slow broker reads as connection loss. |

A transport-local `catch` can only *infer* a broken connection from one failed attempt, which is why all three
guess wrong. The pool does not infer: once it is `reconnecting`, a `connect()` attempt has actually failed and
keeps failing. That makes the pool the right long-term home for the decision — but only once senders can say
*what kind* of failure occurred, which is SDK-474's subject.

Two further defects — independent of the classification problem above, and specific to the Kafka sender rather
than shared with JMS/HTTP — are carried forward unchanged rather than fixed here. They are tracked in SDK-475
rather than SDK-474: since Kafka is a deprecated transport (`kafka-argos-deprecated.md`), that ticket exists to
document the defects, not to commit to fixing them.

- **Kafka double-counts discards.** `KafkaSender.tryToSend` calls `DiscardMonitor.discard()` **and then
  rethrows** (`KafkaSender.java:318-324`), so the connection-state branch discards the same message a second time
  — a third if it was also too large. HTTP and JMS `break` rather than throw, so they do not double-count. This
  behaviour is identical before and after SDK-472: the second discard simply moves from
  `AbstractSender.send` to `acquire()`.
- **Kafka disables producer retries whenever a discard policy is active** (`KafkaSender.java:102`), removing the
  very retries that would absorb a load peak.

### 8.3 Where the policy is applied

`acquire()` is the single decision point, replacing the branch at `AbstractSender.java:301-308`:

| Pool state | `DISCARD` / `ON_CONNECTION_LOSS` | `NONE` |
|---|---|---|
| Healthy | return sender | return sender |
| Reconnecting | `DiscardMonitor.discard()`, return `null` | block until reconnected (D2.6) |
| Shutting down | return `null` | return `null` |

The policy applied is the **pool's**, which under shared communications is the one from the first `Njams` that
took the shared sender (§5.4) — not the policy of the client whose message is being dispatched.

`SenderExceptionListener`s fire **once per outage** (from `reportFailure`, on election) instead of once per failed
send attempt per sender. The listener is wired at `Njams.java:704-705` but is currently dormant — no receiver
implements `SenderExceptionListener` today. It is the seam SDK-473 will use, so **SDK-472 should land before
SDK-473**: building the sender→receiver trigger on the current per-attempt storm would be considerably worse.

## 9. Shutdown

`beginShutdown()` sets the flag and `notifyAll()`s, so every thread blocked in `acquire()` wakes, receives
`null`, and drops its held message. The executor then drains promptly.

This is an improvement: today a shutdown during an outage always burns the full 10 s wait in
`NjamsSender.close()` (`NjamsSender.java:310-337`) before falling through to `shutdownNow()`.

The wake must stay hung off `NjamsSender.close()` and must not be moved to any earlier per-instance stop path:
`NjamsSharedSender` overrides `close()` to defer the real shutdown until the last user leaves, and that deferral
is what stops one client's `stop()` from tearing the group down under its siblings (§5.4).

## 10. Test plan

Two strictly separate steps, in separate commits. Relocation must not be blended with new assertions — assertion
parity across the move is the evidence that the move preserved behaviour.

**Step 1 — relocation only.** Assertions stay byte-for-byte identical; only the wiring needed to reach the
relocated code changes (e.g. `SenderReconnectGatingSpecTest` currently calls `s.reconnect(...)` on a bare sender
and would drive the connector instead). Affected: `SenderReconnectGatingSpecTest`, `SenderStartupSpecTest`,
`SenderStartGatingSpecTest`, `SenderShutdownSpecTest`, `SenderLoggingSpecTest`, `SenderCloseOrderingSpecTest`,
`AbstractSenderStaticStateTest`, and the affected parts of `NjamsSenderTest` and `SenderPoolTest`.

*If an assertion cannot survive the move, that is a behaviour change: stop and raise it rather than adjusting the
assertion.*

**Step 2 — new behaviour only.** Fresh tests for:

- exactly one reconnector under N concurrent failures;
- in-use senders retired but not closed until released;
- `acquire()` blocking under `NONE` and released on reconnect;
- `acquire()` returning `null` with a `DiscardMonitor` increment under `DISCARD` / `ON_CONNECTION_LOSS`;
- a message surviving retirement and being sent exactly once by a fresh sender;
- shutdown releasing blocked acquirers promptly;
- a late caller's `awaitStartup(...)` returning `true` immediately against an already-connected group (§5.1);
- under `ON_CONNECTION_LOSS` and under `DISCARD`, a message dispatched while the group is `reconnecting` is
  discarded via `acquire()` — the relocated base-class branch behaving as it did in `AbstractSender.send`;
- shared communications: one outage across two `Njams` instances elects one reconnector and notifies both
  registered listeners; one instance's `stop()` does not shut the group down while the other still uses it;
- **deadlock regression:** fill the executor queue, fail every sender, assert the pool makes progress after
  reconnect within a timeout. This test hangs against a naive re-enqueue implementation, which is what makes it
  worth having.

The existing `LifecycleTestSender` / `LifecycleTestTransport` harness covers most of what is needed.

## 11. Components touched

| Component | Change |
|---|---|
| `communication/SenderConnector` | **New**, package-private |
| `communication/SenderPool` | `acquire`/`release`/`reportFailure`, explicit lock, group failure flag, pool-owned thread-safe listener set, `validate()` removed |
| `communication/AbstractSender` | Lifecycle members removed; `notifyConnectionFailure` added; `send(CommonMessage, …)` reduced |
| `communication/NjamsSender` | Worker task becomes the retention loop; startup delegates to the connector |
| `communication/ConnectionCoordinator` | Reconnect counter collapses to a boolean |
| `communication/jms/JmsSender` | `onException(JMSException)` calls `notifyConnectionFailure`. Its `tryToSend` discard check is **unchanged** (§8.1) |
| `communication/http/HttpSender`, `communication/kafka/KafkaSender` | No change expected beyond compilation; their discard checks are **unchanged** (§8.1) |
| `communication/MaxQueueLengthHandler` | No code change; its `isConnectionLost` supplier now reads the pool's group flag rather than a sender scan |
| `wiki/FAQ.md` | Review needed: no setting changes, but custom-transport guidance references `AbstractSender` |

## 12. Open items for the implementation plan

- Whether `SenderConnector` should be shaped so the receiver could adopt it later (SDK-473 or a follow-up)
  without committing to that now.
- Exact Javadoc wording for the new SPI invariant in §6.
- Whether the FAQ's custom-transport paragraph needs updating alongside the SPI change.
- The `wiki/FAQ.md` entry for the `onConnectionLoss` discard policy: no behaviour change is intended by this
  ticket (§8.1), so no edit is expected — but confirm rather than assume before closing.
