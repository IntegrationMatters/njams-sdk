# Sender Lifecycle

Tickets: SDK-375, SDK-472, SDK-476

How the SDK connects, sends through, recovers and shuts down its sender group and its receiver. All classes are in
`com.im.njams.sdk.communication` unless a package is given. Not covered here, see the rules instead:
transports, their production weight, the two JMS namespace trees and the SPI/Wire contract boundaries
(`communication-layer.md`); when log and project messages are sent at all (`message-sending-control.md`); the
runtime-path performance rules (`runtime-performance-hotpath.md`).

## 1. Model

1. **Two independent sides.** The *sender group* and the *receiver* each own their own `ConnectionCoordinator`;
   they never share one. Every transport uses separate physical connections for the two sides, and a failure on
   one side is no evidence about the other (e.g. a JMS queue present but its topic missing).
2. **The sender is critical, the receiver is not.** Without a sender the client cannot deliver monitoring data;
   without a receiver it only misses server commands. Only the sender's connect outcome decides
   `Njams.startup()`'s result. The receiver's outcome, at startup or later, never fails startup and is not
   configurable.
3. **One deliberate coupling, sender → receiver only.** When a sender group recovers from a real outage it signals
   the receiver to cycle its connection (§6.4), because a receiver that waits passively for messages can silently
   miss a connection loss. Nothing flows from receiver to sender.
4. **A sender group** is one `NjamsSender` with one executor, one `SenderPool`, one `SenderConnector` and one
   sender-side `ConnectionCoordinator`. It is per `Njams` instance, or per JVM under shared communications (§8).

| Class | Role |
|---|---|
| `NjamsSender` | Executor and per-message dispatch; startup policy (`startWithTimeout`); `close()`; reference counting of the shared group |
| `SenderPool` | Hands out connected senders; owns failure handling, retirement, discard-on-reconnect, listeners and shutdown states |
| `SenderConnector` | The group's single startup connect and single reconnect loop, on a private sender (package-private) |
| `ConnectionCoordinator` | Per-side gating: connected, shutting down, reconnect allowed (package-private) |
| `AbstractSender` | Sender SPI; owns the retry/discard machinery (`sendWithRetry`) |
| `AbstractReceiver` | Receiver SPI (the only one); owns its own coordinator and reconnect loop |
| `MaxQueueLengthHandler` | Discard policy when the executor queue is full |
| `SenderRetiredException` | Package-private signal that moves a message off a retired sender (§3.2) |
| `SenderRecoveryListener` / `SenderExceptionListener` | Once-per-recovery / once-per-outage group notifications |
| `DiscardMonitor` | Counts discarded messages |
| `logmessage.LogMessageFlushTask`, `client.CleanTracepointsTask` | Per-client registry of the sender used by flushes (§9) |

## 2. Startup

1. **Pre-warm.** The `Njams` constructor obtains the sender group, creates the receiver and starts its background
   connect, and begins the sender group's startup connect. Failures there are logged at DEBUG only;
   `Njams.startup()` is the site that reports them.
2. **Decision.** `Njams.startup()` registers the receiver with the sender group (§6.4), then calls
   `NjamsSender.startWithTimeout(timeout, reconnectOnFailure)` with `njams.sdk.communication.connect.timeout`
   (default 30 s) and the policy from `njams.sdk.communication.startup.failbehavior` (`fail` default, `exit`,
   `reconnect`).
   1. Connected within the timeout → proceed.
   2. `reconnect` → only the startup connect is cancelled, the group is failed through the normal outage path
      (§4.1), and startup succeeds. Without failing the group, the pool would still believe it healthy, a worker
      would start a second, competing connect, and the connection-failure state that `ON_CONNECTION_LOSS`
      relies on would be wrong.
   3. `fail`/`exit` → the startup connect is cancelled; `startup()` releases everything it acquired (receiver,
      command listeners, pre-warmed sender) and returns `FAIL` or `EXIT` (the two differ only in the result, see
      `testing-conventions.md`).
3. **Single-shot startup connect.** Waiting for startup never starts a second connect when one was already
   attempted, so a pre-warm that failed fast does not cause a second connect against the same endpoint and a
   second report of the same failure. A late caller against an already-connected group (a second `Njams` on a
   shared group) succeeds immediately.
4. **Gating.** A sender group reconnects only when it is not shutting down and either was connected before or the
   `reconnect` policy allowed it. That permission is granted only by `startWithTimeout`, so a failed pre-warm
   never starts a reconnect on its own: the fail-behavior decision stays with the client. A failed startup connect
   is reported once, at ERROR if no reconnect follows and at WARN if one does, with the reason in the message.
5. After success, `startup()` registers the client in the flush registries (§9), marks itself started and sends
   the project message.

**Rules**

1. Startup timeout handling cancels only the startup connect, never the reconnect loop: that loop is the group's
   only reconnector (possibly a sibling client's), and cancelling it would leave the group permanently unable to
   recover — the exact case the `reconnect` policy exists for. Only shutdown cancels both.
2. At most one of startup connect and reconnect loop is in flight per group; a failed startup connect hands off
   to the reconnect loop atomically, so there is never a window in which a second connect could start.

## 3. Sending a message

`NjamsSender.send` submits the message to the executor. The worker loops: acquire a connected sender from the
pool, send, release. The message stays on the worker thread for its whole life; the sender is the replaceable
resource.

### 3.1 Inside the sender: `AbstractSender.sendWithRetry(SendAttempt)`

A transport implements `connect()`, `close()`, the typed `send` methods and the two classifiers
`isCongestion`/`isMessageRejected`, and routes every unit it puts on the wire (each chunk of a split message)
through the `final` `sendWithRetry`. A `SendAttempt` is one honest send that throws on failure — no retrying, no
swallowing, no discard policy in transport code.

1. **Smoothing window:** one attempt plus up to three retries after 50, 200 and 750 ms
   (`getSmoothingDelaysMs()`), absorbing a single transient blip. Skipped — fail on the first failure — when
   `isMessageRejected` is true (retrying a bad message can never help) or the policy is `DISCARD` (it then never
   delays or blocks the application, which is what choosing `DISCARD` buys).
2. **Escalate or wait out congestion:** a failure that is not congestion, or any failure under `DISCARD`, is
   rethrown unchanged. Congestion under `NONE`/`ON_CONNECTION_LOSS` is retried indefinitely on the same sender
   (pause `getCongestionRetryDelayMs()`, then a fresh smoothing window) — congestion means the connection is fine,
   so reconnecting would not help — unless the sender has been retired meanwhile, then it throws the retirement
   signal.
3. `logError(SendFailureOutcome, Throwable)` reports every decision to the transport for logging in its own terms.

### 3.2 At the dispatch site

1. The retirement signal anywhere in the cause chain → release (which closes the retired sender) and resend the
   same message on a fresh sender. Checked before the classifiers, because the underlying failure is usually
   congestion and the congestion branch would drop it.
2. `isMessageRejected` → drop, count in `DiscardMonitor`, keep the sender.
3. `isCongestion` (reachable only under `DISCARD`) → drop, count, keep the sender.
4. Anything else → report the failure to the pool (§4) and loop; acquiring the next sender applies the policy.

### 3.3 Resulting behavior

| Failure | `DISCARD` (default) | `ON_CONNECTION_LOSS` | `NONE` |
|---|---|---|---|
| message rejected | dropped at once, sender kept | same | same |
| congestion | dropped at once, sender kept | retried indefinitely on the same sender | same as `ON_CONNECTION_LOSS` |
| anything else | no retry; group fails; message dropped | smoothing; group fails; message dropped | smoothing; group fails; blocks until reconnected, then the same message is resent |

While the group is reconnecting, every message dispatched under `DISCARD`/`ON_CONNECTION_LOSS` is discarded before
it reaches a sender. Executor queue full (`MaxQueueLengthHandler`): `DISCARD` drops; `ON_CONNECTION_LOSS` drops
only while the group has a connection failure, else blocks the submitting thread; `NONE` blocks.

Built-in classifiers: HTTP — congestion is status 429 only (502/504 signal an unreachable backend and 503 carries no
reliable "temporary" guarantee, so they count as connection loss), rejected is 413; JMS (both trees) — congestion
is `ResourceAllocationException` in the cause chain, never rejected, as JMS has no portable signal for a
permanently oversized message; Kafka — `false` for both (deprecated, see `kafka-argos-deprecated.md`).

**Rules**

1. **Congestion never leaves `sendWithRetry` unless the policy is `DISCARD` or the sender is retired.** If it did
   under `NONE`/`ON_CONNECTION_LOSS`, the dispatch site's congestion branch would silently drop a message those
   policies promise to keep — for every transport at once. Any new `DiscardPolicy` value must be placed on the
   correct side of this condition.
2. **`isCongestion`/`isMessageRejected` never throw and are pure functions of the throwable.** They are evaluated
   several times for one failure. When they cannot decide — including an internal failure while deciding — the
   answer is `false`. They inspect the whole cause chain, because JMS wraps every send failure. The pool's guard
   against a throwing classifier is defence in depth only.
3. **Detect the retirement signal by walking the cause chain, never by `instanceof`** — HTTP rethrows a
   `RuntimeException` unwrapped, JMS wraps unconditionally; `instanceof` would silently fail on JMS.
4. **Never re-submit a message to the executor from a worker.** With a full queue `MaxQueueLengthHandler` blocks
   the submitter; during an outage every worker would block and nothing drains the queue, permanently, even after
   the connection returns. Keeping the message on the worker also uses no queue slot and causes no reordering.
5. **A transport's single attempt must be time-bounded.** Retirement converges only because sends return: a
   retired sender is closed only when its borrower releases it, so an attempt that never returns stalls retirement
   for its whole group.
6. Retry, classification and discard decisions live only in `AbstractSender`, the dispatch site and the pool. A
   transport that discards by returning normally after a failure hides the outage from the pool, the listeners and
   the fast-discard path — the defect this design removed from HTTP.

## 4. Outage and reconnect (sender group)

1. **Election.** The first failure report of an outage (a failed send, a failed connect of a new pooled sender, or
   the `reconnect` startup path) fails the group: the group is marked reconnecting and failed, the coordinator
   leaves "connected", idle senders are closed and every checked-out sender is retired. The outage is logged once
   at WARN, the single reconnect is started, and only then the `SenderExceptionListener`s are notified once.
2. **Later reports are absorbed**: while the group is reconnecting, and also for a sender that is retired — a
   retired sender is never recycled, so its report is about a connection generation the group has already
   replaced. Acting on it would re-fail a recovered group, close the sender the connector just published and
   notify listeners twice.
3. **Asynchronous detection.** A transport that learns of a broken connection outside a send (JMS
   `ExceptionListener`) calls `AbstractSender.notifyConnectionFailure(cause)`, which reaches the same failure
   handling. Every sender the pool or the connector creates is wired for it.
4. **Reconnect loop** (`SenderConnector`): a fresh private sender per attempt, at a flat 1 s interval, attempts
   logged at DEBUG, success at INFO, until connected or shutdown.
5. **Publish.** On success the connector hands its connected sender to the pool, which is the only step that
   clears the failed state, and wakes every waiting acquirer. If the reconnect needed at least one failed connect
   attempt *and* the failure that opened the outage was not congestion, the `SenderRecoveryListener`s are notified
   once, on the reconnect thread. This keeps a transient failure against a reachable endpoint from cycling a
   healthy receiver.
6. **Acquiring a sender** while the group is reconnecting: `DISCARD`/`ON_CONNECTION_LOSS` count a discard and give
   up on the message; `NONE` waits until reconnected. A healthy group serves a pooled sender or grows; a new
   sender is connected outside the pool lock, and closed instead of published if a sibling failed the group
   meanwhile.

**Rules**

1. Exactly one reconnect loop per group per outage; a sender never reconnects itself. All senders of a group share
   one endpoint, so parallel loops would only hammer it.
2. A checked-out sender is never closed by another thread — it is retired and closed by its borrower. Closing a
   JMS session or Kafka producer under a thread inside `send` is undefined.
3. No transport code runs under the pool lock: no `connect()`, `close()`, classifier or listener call. A real
   `close()` or a slow connect can block for seconds and would freeze the whole group.
4. The group's connection-failure state is a group flag; it must not be derived by scanning pooled senders, which
   are closed exactly when the flag matters.
5. The coordinator leaves "connected" in the same locked step that fails the group, so a sibling waiting for
   startup never sees "connected" while the pool is already reconnecting.
6. The reconnect is started before listeners are notified: only a successful reconnect clears the failed state,
   so a throwing listener ahead of it would leave the group parked or discarding forever.

## 5. Shutdown (sender group)

`NjamsSender.close()` runs, in this order:

1. Begin shutdown: set the coordinator's shutdown flag, wake acquirers waiting for a reconnect, cancel the startup
   connect and the reconnect loop. A healthy group still serves senders so in-flight sends complete; only a caller
   that would wait for a reconnect gets none, and that drop is not counted as a discard.
2. Shut the executor down and wait up to 10 s for it to drain.
3. Declare the pool shut down: no more senders are created or handed out.
4. Interrupt the executor if it has not terminated.
5. Close every sender the pool still holds.

**Rules**

1. The shutdown flag is set before the executor drains, so a failing final send cannot start a reconnect.
2. Draining (in-flight sends may still borrow) and shut down (nothing is handed out) are distinct states.
3. Waking the waiters stays tied to `NjamsSender.close()`. The shared group defers the real close until the last
   user leaves; moving the wake to an earlier per-client path would let one client tear down the group under its
   siblings.

## 6. Receiver

1. **Startup** (`AbstractReceiver.beginConnect()`, one-shot): connects on a background thread, never awaited by
   `Njams.startup()`. On failure the same thread enters the reconnect loop.
2. **Reconnect** (`AbstractReceiver.reconnect(Exception)`): blocks its caller for the whole loop; backoff from
   500 ms to 60 s. A failed first connect and a later loss are handled alike: WARN once with the cause, saying
   that no server commands are received until connected, and INFO on success. Transports call
   `onException(e)` on loss, which stops the receiver and reconnects on a new thread.
3. **SDK-owned connects.** A failed `connect()` that left the status `CONNECTING` is reset to `DISCONNECTED`,
   since a receiver stuck in `CONNECTING` is never reconnected and implementations the SDK does not know cannot be
   relied on to reset it. After every successful connect, startup or reconnect, the receiver stops itself again if
   shutdown was requested meanwhile, because an uninterruptible `connect()` may finish after `stop()` already ran.
4. **Recovery coupling.** `AbstractReceiver` implements `SenderRecoveryListener`; `Njams.startup()` registers the
   receiver object itself (and as `SenderExceptionListener` if it implements it; no built-in receiver does). The
   signal is recovery-timed: at recovery the endpoint is provably reachable, so the cycle succeeds at once instead
   of running a second backoff loop through the outage. `onSenderGroupRecovered()` ignores it while shutting down,
   never connected, connecting or already reconnecting; otherwise it cycles the connection via `onException` on
   its own thread, because the caller is the sender group's only reconnect thread.
5. **Listener identity.** Listener sets compare by identity, and the receiver object itself is registered, never a
   per-client wrapper: a shared receiver is then one entry (one signal, one cycle), while dedicated receivers on a
   shared group each get their own. A wrapper per client would cycle a shared receiver once per client.
6. **Shared receiver.** With shared communications a `ShareableReceiver` is one instance per receiver class per
   JVM, so its coordinator is JVM-wide too. A receiver that is not shareable (e.g. HTTP/SSE, where sharing brings
   no benefit) is used per client, logged at INFO; sharing then applies to the sender only.
7. **Shutdown** (`Njams.stop()`, and a failed `startup()`): a non-shareable receiver is signalled to shut down
   *before* `stop()`; a shareable one is removed from the shared cache via
   `CommunicationFactory.removeNjamsFromSharedReceiver`, and only if that was the last user is it signalled and
   its reconnect cancelled. Listeners are deregistered from the sender group only when the receiver really
   stopped, so a stopped receiver is neither kept alive nor signalled by a shared group.

**Rules**

1. No setting governs the receiver's startup or reconnect behavior, and none may be added.
2. A shared receiver is signalled to shut down only once the last user is gone; earlier would stop a receiver
   siblings still use.
3. Last-user removal and eviction from the shared-receiver cache are one atomic step. A stopped receiver can never
   connect again, so eviction is the only way a later client gets a working one, and a gap between the two steps
   would hand a concurrently starting client the receiver being torn down.
4. A `SenderRecoveryListener` must return promptly: it runs on the group's single reconnect thread.

## 7. `Njams.stop()` order

1. `LogMessageFlushTask.stop(this)` (final flush of all jobs through the registered sender, §9), then
   `CleanTracepointsTask.stop(this)`.
2. Argos stop.
3. `NjamsSender.close()` (§5).
4. Receiver shutdown and listener deregistration (§6.7).
5. The sender group is released: a later start obtains a new one.

## 8. Shared communications

`njams.sdk.communication.shared` (default `false`) decides whether a client gets its own `NjamsSender` or the
reference-counted JVM singleton from `NjamsSender.takeSharedSender(settings)`.

1. It is built with the settings of the first client that takes it; later clients' settings, including the discard
   policy and thread/queue sizes, do not apply. Do not imply a per-client policy in shared mode.
2. Under `NONE`, a blocked acquirer parks every client in the JVM.
3. One outage elects one reconnector for the JVM and notifies each distinct registered listener once.
4. Every later client re-enters startup; §2.3 makes that an immediate success against a connected group.

## 9. Sender lookup on the flush path

1. `Njams` exposes no sender getter. `Njams.startup()` hands the sender to `LogMessageFlushTask.start(njams,
   sender)` and `CleanTracepointsTask.start(njams, sender)`, which register it per client.
2. `JobFlusher` looks the sender up on every flush. That lookup must stay a lock-free read — it is on the runtime
   monitoring path.
3. `LogMessageFlushTask.stop` removes the entry only after the final flush, because that flush looks the sender up.
4. A job that flushes after its client stopped finds no sender; its log message is dropped with a WARN.

## 10. Decisions not to reopen

1. When a send escalates, the whole `CommonMessage` is resent on a fresh sender, including chunks the server may
   already hold; duplicate delivery is outside this design.
2. The sender reconnects at a flat 1 s; the receiver's exponential backoff was not adopted for it.
3. No cross-side "assume-and-cycle" (a failure on either side cycling the other): it tore down a healthy sender
   pool on a receiver-only hiccup without solving the problem it targeted; only the one-directional recovery
   signal (§6.4) was kept.
4. The former `AbstractSender` lifecycle template methods (`reconnect`, `doReconnect`, `onException`,
   `beginConnect`) were removed, not deprecated: the SDK no longer calls them, so a deprecated override would be
   silently dead; removal makes implementers face it at compile time.
5. Congestion under `NONE` is not escalated to the dispatch site to move it off a retired sender — the dispatch
   site can only drop it; the retirement signal (§3.2) moves the message instead.

## 11. Tests

The lifecycle behavior is covered by the `*SpecTest` classes in
`njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/` and by the sender, dispatch and pool tests in
`communication/`; races such as stale failure reports and the startup/reconnect handoff are tested
deterministically. Real-transport resilience is exercised by `njams-sdk-communication-it` (see
`communication-it-module.md`).
