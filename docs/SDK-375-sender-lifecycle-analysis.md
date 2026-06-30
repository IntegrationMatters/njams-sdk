# SDK-375 — Sender Lifecycle Handling: Behavior Analysis

**Ticket:** SDK-375 — *Revise sender lifecycle handling* (Status: Open)
**Scope:** Analysis only. No code or ticket changes.

## 1. What the ticket expects

The ticket defines three distinct phases in a sender's lifecycle and complains that
*the SDK cannot currently distinguish startup / shutdown from reconnect.*

1. **Startup** — initial connect. On failure: log **once** and **propagate the failure to the
   client implementation** so it can decide how to react (cancel startup, continue without nJAMS, …).
   Only the *very first* connect attempt is special; a second attempt is already equivalent to reconnect.
2. **Processing** — only reachable *after* a successful initial connect. On connection loss, reconnect
   implicitly and infinitely until shutdown is requested — without flooding the log or blocking shutdown.
3. **Shutdown** — once shutdown is requested, finish the current task if a valid connection exists.
   A failing final message must **not** trigger a reconnect, and an in-progress reconnect must be **cancelled**.

Affected components named in the ticket: `NjamsSender`, `AbstractSender` and implementations,
`SenderPool`, and possibly the receiver connection.

## 2. How the sender path actually works today

All three transports (`HttpSender`, `JmsSender`, `KafkaSender`) extend `AbstractSender` and override only
`connect()` / `close()` / `send(...)`. **The whole lifecycle logic lives in `AbstractSender` and is shared**,
so the findings below apply to every transport equally; transport differences are only in *how* connect/send fail.

The connect flow is **lazy and indirect** — the sender does not connect at `Njams.start()`:

```
Njams.start()                          // sender NOT connected here
first message → NjamsSender.send()     // runs on an internal executor thread
   → SenderPool.get() → CommunicationFactory.getSender()
      → newInstance.init() → newInstance.startup()
         → connect()                   // first real connect, on a Sender-Thread
```

`AbstractSender.startup()`:

```java
try { connect(); }
catch (Exception e) { LOG.error(...); reconnect(e); }   // swallows, never rethrows
```

`reconnect()` spawns a daemon thread running `doReconnect()`, which loops
`while (!isConnected() && !shouldShutdown.get())` with a 1 s backoff.

## 3. Phase-by-phase verdict

### Phase 1 — Startup ❌ largely unmet

- **No connect at `start()`.** The sender connect is deferred to the first message and runs on the
  send executor thread, whose task body catches everything and only logs `"could not send message"`.
- **Failure never reaches the client.** `startup()` swallows the exception and immediately starts the
  infinite background reconnect. `CommunicationFactory.getSender()` wraps `startup()` in try/catch, but
  since `startup()` never rethrows on a connect failure, that catch only fires for *instantiation/init*
  errors. The client cannot "cancel startup" or "continue without nJAMS" — it is never informed.
- **No "first attempt is special" distinction.** The very first failure drops straight into the same
  `doReconnect()` loop used for runtime reconnect.
- **Only partially met:** "logged once" roughly holds — `startup()` logs one error and retries are silent.
- **Asymmetry with the receiver:** the receiver was already reworked — `beginConnect()` +
  `startWithTimeout()` connect in the background, enforce `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT`, and
  **rethrow**, so `Njams.startReceiver()` marks the SDK inactive. The sender has no equivalent.

### Phase 2 — Processing ⚠️ partially met

- **Infinite reconnect until shutdown:** ✅ `doReconnect()` loops while not connected and not shutting down.
- **Doesn't flood the log:** ✅ retry failures are silent; one `info` on reconnect start, one on success.
- **"Reconnect only if successfully connected before":** ❌ not enforced. There is no per-instance
  "was ever connected" state, so a *startup* failure and a *runtime* loss are indistinguishable and both
  reconnect identically.

### Phase 3 — Shutdown ❌ unmet on the key points

- **Reconnect can be triggered *during* shutdown.** `NjamsSender.close()` calls `executor.shutdown()` +
  `awaitTermination(10s)` **before** `senderPool.declareShutdown()` sets `shouldShutdown`. During that 10 s
  drain `shouldShutdown == false`, so a failing in-flight send → `send()` → `onException()` → `close()` +
  `reconnect()` → (flag still false) → **spawns a new reconnect thread**. This violates "no reconnect when
  shutting down" and "a failing final message must not reconnect".
- **In-progress reconnect only weakly cancelled.** `setShouldShutdown(true)` lets `doReconnect()` exit at
  its next loop check, but the reconnector threads are separate daemons that `shutdownNow()` does **not**
  interrupt (it interrupts only executor threads). A reconnect blocked inside a blocking `connect()`
  (socket/TCP timeout) is not actively cancelled — it stops only after the blocking call returns and the
  loop re-checks the flag.

## 4. Cross-cutting structural issues

- **No lifecycle state model.** `AbstractSender` tracks only `connectionStatus`
  (DISCONNECTED/CONNECTING/CONNECTED) + `hasConnectionFailure` + `shouldShutdown`. There is no notion of
  *initial-connect vs reconnect* — exactly the distinction the ticket says is missing. `startup()` and
  `reconnect()` both funnel into `doReconnect()`.
- **Static shared state across all senders and all `Njams` instances.** `hasConnected` (`AtomicBoolean`)
  and `connecting` (`AtomicInteger`) are `static` in `AbstractSender`. With pooling (many sender instances)
  and shared sender pools, these are JVM-global, so the "already logged reconnect" dedup and the
  "N senders reconnecting" counter are shared across unrelated clients, producing misleading logs and
  cross-instance coupling. `AbstractReceiver` has the same static pattern.
- **Visibility:** `hasConnectionFailure` is a plain non-volatile `boolean` written on the reconnect thread
  and read elsewhere (`SenderPool.isConnectionFailure()` via `MaxQueueLengthHandler`).
- **`onException()` is unconditional:** it always `close()` + `reconnect()`, regardless of shutdown state
  or whether the loss occurs during draining.

## 5. Transport-specific notes (all on top of the shared logic above)

- **HTTP** — `connect()` does a real reachability check (`HEAD`/legacy `GET`); `send` retries internally
  up to 20×50 ms before throwing `HttpSendException` → `onException`. `close()` only flips status to
  DISCONNECTED. Keeps a `static connectionTest` cached across instances.
- **JMS** — registers itself as JMS `ExceptionListener`; async `onException(JMSException)` routes into the
  same `close()` + `reconnect()`. `send` retries only on `ResourceAllocationException`.
- **Kafka** — same shared lifecycle; `connect()` validates topics + builds the producer. (Kafka is
  effectively deprecated and out of test scope, but it inherits every issue above unchanged.)

## 6. Summary table

| Requirement | HTTP | JMS | Kafka | Holds? |
|---|---|---|---|---|
| Startup connect at `start()` | lazy, on 1st send | lazy | lazy | ❌ |
| Startup failure logged once | yes | yes | yes | ✅ |
| Startup failure propagated to client | no | no | no | ❌ |
| First attempt distinct from reconnect | no | no | no | ❌ |
| Reconnect only after prior success | no | no | no | ❌ |
| Infinite reconnect till shutdown | yes | yes | yes | ✅ |
| Reconnect doesn't flood log | yes | yes | yes | ✅ |
| No reconnect during shutdown | no (10 s window) | no | no | ❌ |
| Failing final send doesn't reconnect | no | no | no | ❌ |
| In-progress reconnect cancelled on shutdown | weak/partial | weak | weak | ⚠️ |

## 7. Bottom line

The ticket's premise holds: there is no sender-side lifecycle phase model, so startup, reconnect, and
shutdown are conflated. Phase 2's "infinite, quiet reconnect" is the only phase mostly satisfied. Phase 1
(propagate startup failure to the client; treat the first attempt specially) and Phase 3 (no reconnect
during/after shutdown; cancel in-flight reconnect) are not met. Because the logic is entirely in
`AbstractSender`, all three transports share the same gaps. The receiver already has the intended startup
model (`beginConnect` / `startWithTimeout` with timeout + propagation), which is a useful template for the
sender.
