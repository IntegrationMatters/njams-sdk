# SDK-375 — Sender Lifecycle Handling: Design Spec

**Ticket:** SDK-375 — *Revise sender lifecycle handling*
**Branch:** `SDK-375` (based on `6.0-dev`)
**Status of this doc:** design, pending user review → then implementation plan (`writing-plans`).
**Companion docs:** `docs/SDK-375-sender-lifecycle-analysis.md` (evidence of current behavior),
`docs/SDK-375-working-agreement.md` (branch rules, decisions, test strategy).

---

## 1. Problem

The sender has no lifecycle phase model, so **startup**, **reconnect**, and **shutdown** are conflated. Concretely
(from the analysis):

- **Startup (Phase 1):** the sender connects lazily on the first message, never at `start()`; a startup connect
  failure is swallowed into an infinite background reconnect and never propagated to the client.
- **Processing (Phase 2):** infinite quiet reconnect works, but it is **not** gated on having been connected
  before.
- **Shutdown (Phase 3):** the shutdown flag is set *after* the executor drain, so a failing final send can spawn a
  new reconnect; an in-progress reconnect is not actively cancelled.
- **Cross-cutting:** no phase model; `static hasConnected`/`connecting` on both `AbstractSender` and
  `AbstractReceiver` are JVM-global, coupling unrelated `Njams` instances.

## 2. Goal

A single, explicit lifecycle model **shared between the sender(s) and the receiver of one transport**, scoped
correctly, that satisfies all three phases and removes the JVM-global coupling — without breaking public API.

## 3. Decisions driving this design (from brainstorming)

| # | Decision |
|---|---|
| D1.1 | Connectivity is a property of the **transport**, shared by sender(s) + receiver — modeled as a shared coordination/phase layer, **not** a shared physical connection. |
| D1.2 | Scope = **per shared-transport group**: shared-communications group when sharing is on, else the single `Njams` instance. |
| D1.3 | One transport, one fate: a failure on either side flips the whole group to reconnect; a **single** coordinated reconnect; gated on prior success. |
| D1.4 | Startup-failure behavior is **configurable**; default **fail-fast** (`Njams.start()` returns `false`, SDK inactive); alternative **reconnect** in background. Governs the whole group. |
| D1.5 | Sender/receiver **contracts & interfaces may change** on this branch when it supports a clean, safe implementation (they are communication-internal). Constraints: `Njams.start()` public signature unchanged; no relocated/shaded types on public/protected members; manage `breaking-change`; baseline-first before modifying existing members. |
| D1.6 | At `start()` (fail-fast), **both** the receiver **and** at least one sender must connect for success. |
| Evidence | All three transports use **separate** physical connections for sender vs. receiver (HTTP ingest vs. SSE; JMS queues vs. topic; Kafka producer vs. consumer). So each side keeps its own connect; the coordinator only shares phase/orchestration. |

## 4. Architecture

### 4.1 `ConnectionCoordinator` (new, internal)

A package-private/internal type in `com.im.njams.sdk.communication` owning the **group-level** lifecycle state:

- `phase`: `STARTING → CONNECTED → RECONNECTING → CONNECTED … → SHUTDOWN`
- `wasEverConnected` flag (gates reconnect — Phase 2 only after a prior `CONNECTED`)
- the **single** reconnect orchestration (one reconnect worker per group, not per sender instance)
- the failure signal currently exposed as `hasConnectionFailure` (consumed by `MaxQueueLengthHandler` via
  `SenderPool::isConnectionFailure`)
- the `shouldShutdown` flag

It **replaces**:
- per-`AbstractSender`: `hasConnectionFailure`, the `reconnector` thread, `shouldShutdown`, and the
  `static hasConnected`/`connecting`;
- per-`AbstractReceiver`: the `static hasConnected`/`connecting` and the reconnect bookkeeping.

Senders and the receiver keep their own `connect()`/`close()`/`stop()` and their own `ConnectionStatus`; they
**consult** the coordinator for phase decisions and **report** connect success / failure into it.

### 4.2 Ownership & scope (D1.2)

The coordinator is owned at the same granularity the sender pool + receiver are already shared:

- **Shared communications ON:** the shared sender (today a JVM-singleton via `NjamsSender.takeSharedSender`) and
  the shared receiver share **one** coordinator → one group.
- **Shared communications OFF:** each `Njams` owns its own `NjamsSender`/receiver pair → **one coordinator per
  instance**.

`Njams.start()` obtains/creates the coordinator and passes the **same reference** to the receiver and to the
`NjamsSender`/`SenderPool`. Removing the `static` fields in favor of this instance is what eliminates the
cross-instance coupling defect.

> Open implementation detail (for the plan): exact wiring/registry keying so the shared sender and shared receiver
> resolve the same coordinator instance; how `SenderPool` pre-warms one sender connection at startup.

## 5. Phase behavior

### 5.1 Startup (Phase 1)

- `Njams.start()` brings the group up within `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT` (the existing receiver
  timeout, now applied group-wide): connect the **receiver** and **one sender** (D1.6).
- On success → `CONNECTED`, `wasEverConnected = true`, `start()` proceeds.
- On failure, behavior per the new setting (§7):
  - **fail-fast (default):** status `DISCONNECTED`; `Njams.start()` returns `false`; SDK inactive; **no**
    reconnect spawned. (Maps onto today's `return false` contract — `start()` is **not** changed to throw.)
  - **reconnect:** `start()` returns `true`; group enters `RECONNECTING` (background).
- Startup is explicitly **not** reconnect: the first attempt is distinct, satisfying the ticket.

### 5.2 Processing (Phase 2)

- A transport error reported by **any** sender or the receiver → coordinator transitions the group to
  `RECONNECTING` **iff `wasEverConnected`** (otherwise it is a startup failure, handled per §5.1).
- Exactly **one** coordinated reconnect worker per group runs; each side re-establishes its **own** physical
  connection. Quiet logging: one info on reconnect start, one on success, retries silent ("logged once").
- Reconnect continues infinitely until connected or shutdown.

### 5.3 Shutdown (Phase 3) — fixes the ordering bug

- The coordinator's `shouldShutdown` is set to `true` **before** the sender executor drain
  (`executor.shutdown()` + `awaitTermination`). Today it is set too late (in `senderPool.declareShutdown()`
  *after* `awaitTermination`), which is the root cause of a failing final send spawning a reconnect.
- With shutdown set first: a failing in-flight send during the drain takes the "shutting down" path and does
  **not** reconnect.
- An in-progress reconnect worker is **actively interrupted** by the coordinator on shutdown (today the daemon
  reconnector is not reached by `executor.shutdownNow()`), so a reconnect blocked in a blocking `connect()` is
  cancelled rather than only stopping at the next loop check.

## 6. Failure propagation to the client (public-facing)

- The **only** public-facing change is `Njams.start()`’s connect-gating: it now reflects the shared transport
  (receiver + sender) per §5.1, consistently. Signature unchanged; today it already returns `false` on receiver
  startup failure.
- Sender/receiver classes (`NjamsSender`, `AbstractSender`, `SenderPool`, `CommunicationFactory`,
  `AbstractReceiver`, `Receiver`, `SenderExceptionListener`) are communication-internal (per `CLAUDE.md`;
  `Njams.getSender()` is already `@Deprecated(forRemoval=true)`). Per **D1.5**, their contracts/interfaces **may be
  changed** on this branch where it yields a clean, safe implementation — the internal rework is not treated as a
  public-API break. We still: keep `Njams.start()`’s signature, manage the `breaking-change` label, verify no
  relocated/shaded types leak through any `public`/`protected` member, and establish test coverage before
  modifying an existing member.

## 7. New setting

- **Key:** `njams.sdk.communication.startup.failbehavior` (constant `PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR`
  in `NjamsSettings`), consistent with the existing `njams.sdk.communication.*` keys.
- **Values:** `fail` (default) | `reconnect`.
- **Scope:** governs the whole shared-transport group (sender + receiver).
- **Default constant** lives in the consuming class (not in `NjamsSettings`), per the project rule.
- **Docs:** add to `njams-sdk-sample-client/src/main/resources/settings_full.properties` and to the
  communication section of `wiki/FAQ.md`.

## 8. Test plan (maps to Decision 2)

1. **Baseline / regression net (real-transport integration):** pins only the stable, surviving contract —
   send/receive correctness while connected, delivery resumes after a transient mid-processing loss (Phase 2),
   clean shutdown flushes + terminates. Green now, stays green.
   - JMS: embedded ActiveMQ `vm://` broker (new test dep `org.apache.activemq:activemq-broker`).
   - HTTP: JDK `com.sun.net.httpserver.HttpServer` (no new dep).
2. **Spec / acceptance tests (controllable fake transport, deterministic, TDD):**
   - startup fail-fast → `start()` returns `false`; startup reconnect → `start()` returns `true` + background
     reconnect (§5.1);
   - reconnect only after prior success (§5.2);
   - no reconnect during/after shutdown; in-progress reconnect interrupted (§5.3);
   - shared state scoped per group + **instance isolation** (two `Njams` instances do not share reconnect state);
   - "logged once" via a capturing SLF4J appender.
   - **No timing-based tests** — latches/barriers/injected seams, poll for conditions.
3. **Real-transport smoke (JMS + HTTP):** real senders honor the observable contract end-to-end after the rework.
4. **Kafka:** relaxed — no real broker; covered by the fake transport + existing unit mocks only.

## 9. Components touched

- `AbstractSender`, `NjamsSender`, `SenderPool`, `CommunicationFactory` — route lifecycle through the coordinator;
  remove `static` state; fix shutdown ordering.
- `AbstractReceiver` — route `beginConnect`/`startWithTimeout`/`reconnect` through the coordinator; remove
  `static` state.
- `Njams` — create/own the coordinator; connect receiver + one sender at `start()`; apply the new setting; set
  shutdown on the coordinator before draining in `stop()`.
- `NjamsSettings` — new `PROPERTY_*` key.
- Docs: `settings_full.properties`, `wiki/FAQ.md`.

## 10. Out of scope / non-goals

- No change to the wire message format.
- Kafka behavior parity beyond "still works"; it inherits the shared model but is not integration-tested.
- No new public API beyond the one setting; no change to `Njams.start()`’s signature.

## 11. Open items carried into the implementation plan

- Exact coordinator wiring/registry keying for the shared-communications group.
- How `SenderPool` pre-warms exactly one sender connection at startup and reuses it.
- Precise `AbstractReceiver` refactor to delegate its existing timeout/connect model to the coordinator.
- Final reconnect-worker threading/interruption mechanics.
