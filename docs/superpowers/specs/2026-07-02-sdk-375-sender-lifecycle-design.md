# SDK-375 — Sender Lifecycle Handling: Design Spec

**Ticket:** SDK-375 — *Revise sender lifecycle handling*
**Branch:** `SDK-375` (based on `6.0-dev`)
**Status of this doc:** revised after Parts 1-3 were implemented, reviewed, and merged → pending a Part 4
implementation plan (`writing-plans`).
**Companion docs:** `docs/SDK-375-sender-lifecycle-analysis.md` (evidence of current behavior),
`docs/SDK-375-working-agreement.md` (branch rules, decisions, test strategy).

---

## 0. Revision note (post-Part 3)

Parts 1-3 implemented and shipped the design as originally written below: sender(s) and receiver share one
`ConnectionCoordinator` per transport group ("one fate" — D1.1/D1.3 as first written), and `Njams.start()`
requires both sides to connect (or be allowed to background-reconnect) for success (D1.6 as first written).

That assumption is now revised: **sender and receiver are not equally critical**, and their fates must be
decoupled. If the sender cannot connect, the client cannot push monitoring data to nJAMS — a major issue. If the
receiver cannot connect, the client keeps working without limitation; it only cannot receive commands from the
server, which is rare. Sender and receiver can also differ in transport type and required resources (e.g. a JMS
queue present for one side but missing for the other), so a failure on one side is not always evidence about the
other.

Sections below are updated in place to reflect the revised decisions (marked **REVISED**). Everything not marked
**REVISED** is unchanged from the original design and already implemented.

**D1.7 cut (post-review, Part 4):** an earlier version of this revision additionally introduced D1.7, a
cross-side "assume-and-cycle" trigger where a connection failure detected on either side would proactively cycle
the *other* side's connection too. A code review found this tears down a healthy sender pool whenever the
receiver alone hiccups, and further analysis showed it does not actually solve the problem it was designed for.
D1.7 has been removed from this ticket's scope in its entirety and is deferred to a future, separate
reconnect-handling design. Remaining mentions of D1.7 below are historical context from when it was designed and
briefly implemented, not a description of current or planned behavior.

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

## 2. Goal — **REVISED**

An explicit lifecycle model for **each side independently** (sender group; receiver), scoped correctly, that
satisfies all three phases and removes the JVM-global coupling — without breaking public API beyond the accepted,
intentional change in D1.6. Sender and receiver are not equally critical: the sender's connection failure must
remain fatal to `Njams.start()` (unchanged priority from the original ticket), while the receiver's must never be
fatal, at startup or later. The two sides are otherwise fully independent — neither side's connection state or
failures affect the other's (an earlier revision of this design introduced one deliberate cross-side coupling,
D1.7, which was subsequently cut; see §0).

## 3. Decisions driving this design (from brainstorming; updated per §0)

| # | Decision |
|---|---|
| D1.1 **(REVISED)** | Connectivity is a property of each **side** (sender group, receiver) independently — each owns its own coordination/phase layer (its own `ConnectionCoordinator`). Physical connections were always separate (see Evidence row); the *coordination* state is no longer shared either. |
| D1.2 **(REVISED)** | Scope = **per side, per shared-transport group**: the sender group's coordinator scope is unchanged (shared-communications group when sharing is on, else the single `Njams` instance); the receiver now has its own, independently-scoped coordinator at the same granularity, not the sender's instance. |
| D1.3 **(REVISED)** | Sender and receiver no longer share one fate. Each side's own connection failure triggers only its own reconnect loop, gated on its own prior success. |
| D1.4 **(REVISED — scope narrowed)** | Startup-failure behavior is **configurable, but now governs the sender only**: default **fail-fast** (`Njams.start()` returns `false`, SDK inactive); alternative **reconnect** in background. The receiver is no longer governed by this setting — see D1.6. |
| D1.5 | Unchanged. Sender/receiver **contracts & interfaces may change** on this branch when it supports a clean, safe implementation (they are communication-internal). Constraints: `Njams.start()` public signature unchanged; no relocated/shaded types on public/protected members; manage `breaking-change`; baseline-first before modifying existing members. Note: as of this revision, none of Part 1-3's new public API has shipped in a release yet (still `6.0.0-SNAPSHOT`), so simplifying or removing it does not need deprecation ceremony. |
| D1.6 **(REVISED)** | At `start()`, success depends **only** on the sender (at least one, per the existing pooling model). The receiver's connection outcome — at startup or later — never affects `start()`'s return value, unconditionally (not governed by D1.4 or any setting). This is an observable behavior change to `Njams.start()`'s contract (a receiver-only failure that previously failed startup under fail-fast no longer does); accepted as intentional. |
| Evidence | All three transports use **separate** physical connections for sender vs. receiver (HTTP ingest vs. SSE; JMS queues vs. topic; Kafka producer vs. consumer). This was always true and is unaffected by this revision — it is *why* independent coordinators (D1.1) are viable in the first place. |

## 4. Architecture

### 4.1 `ConnectionCoordinator` (internal) — **REVISED: one per side, not one per group**

A package-private/internal type in `com.im.njams.sdk.communication` owning the **per-side** lifecycle state:

- `phase`: `STARTING → CONNECTED → RECONNECTING → CONNECTED … → SHUTDOWN`
- `wasEverConnected` flag (gates reconnect — Phase 2 only after a prior `CONNECTED`)
- the reconnect orchestration for that side (one reconnect worker per sender group, and independently one for the
  receiver — no longer a single worker shared across both)
- the failure signal currently exposed as `hasConnectionFailure` (consumed by `MaxQueueLengthHandler` via
  `SenderPool::isConnectionFailure`) — sender-side only, unaffected by this revision
- the `shouldShutdown` flag, now set **independently** per side

It **replaces**:
- per-`AbstractSender`: `hasConnectionFailure`, the `reconnector` thread, `shouldShutdown`, and the
  `static hasConnected`/`connecting` (unchanged from the original design — already implemented in Parts 1-2);
- per-`AbstractReceiver`: the `static hasConnected`/`connecting` and the reconnect bookkeeping (unchanged — the
  static removal from Part 3 stays; only the *sharing* of the instance with the sender is reverted).

Senders and the receiver keep their own `connect()`/`close()`/`stop()` and their own `ConnectionStatus`, exactly
as before; each side **consults its own** coordinator for phase decisions and **reports** connect success/failure
into it. `NjamsSender.wireReceiver(Receiver)` (built in Part 3 specifically to hand the sender's coordinator
instance to the receiver) is removed outright, with no replacement cross-side hook — it must no longer assign
the *same* coordinator to both sides (D1.7, which would have repurposed this hook, was cut; see §0).

### 4.2 Ownership & scope (D1.2) — **REVISED**

Each side's coordinator is owned at the granularity that side is already shared at:

- **Sender:** unchanged from Parts 1-2. Shared communications ON → the JVM-singleton shared sender's coordinator
  covers every `Njams` instance using it. Shared communications OFF → one coordinator per `Njams` instance's
  `NjamsSender`/`SenderPool`.
- **Receiver:** independently, the same granularity rule applies to whichever receiver instance is in play
  (shared `ShareableReceiver` vs. per-instance), but resolved against the receiver's **own** coordinator, never
  against the sender's.

## 5. Phase behavior

### 5.1 Startup (Phase 1) — **REVISED**

- `Njams.start()`'s success depends **only on the sender** (D1.6): it connects at least one sender within
  `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT` and reports failure per the setting (§7) exactly as before this
  revision — that part of Phase 1 is unchanged.
- The receiver's startup connect is **fire-and-forget**, kicked off in the background (as it already is via
  `beginConnect()`'s pre-warming) but never awaited by `start()` and never able to make `start()` return `false`.
  There is no fail-fast/reconnect branch for the receiver any more — its outcome is always handled the same way,
  unconditionally (see §5.1.1).
- On sender success → sender's `CONNECTED`, `wasEverConnected = true`, `start()` proceeds per its own unchanged
  contract.
- On sender failure, behavior per the setting (§7, now sender-scoped) unchanged from before this revision.

### 5.1.1 Receiver connection outcome (Phase 1 & 2, unified) — **NEW**

Because the receiver is never fatal to startup, there is no longer a meaningful distinction between "receiver
failed its first connect" and "receiver lost its connection later" — both are handled identically:

- On a receiver connection failure (first attempt or later): log once at **WARN** — *"Receiver connection lost.
  The client will not receive any commands from the server until reconnected."* — then enter background
  reconnect unconditionally (no setting governs this; see D1.6).
- On the receiver reconnecting successfully: log once at **INFO** — *"Receiver reconnected. Handling server
  commands resumed."*

### 5.2 Processing (Phase 2) — **REVISED**

- A transport error on the **sender** → sender's own coordinator transitions to `RECONNECTING` **iff
  `wasEverConnected`** for the sender, exactly as before this revision (unaffected — sender behavior is
  unchanged).
- A transport error on the **receiver** → handled per §5.1.1, independently of the sender's state.
- Each side runs its **own** reconnect worker against its **own** coordinator; there is no longer a single
  shared worker. Quiet logging for the sender is unchanged ("logged once" per the original design); the receiver
  uses the wording in §5.1.1 instead of a generic "logged once" info pair.
- Reconnect continues infinitely until connected or shutdown, per side.

### 5.3 Shutdown (Phase 3) — fixes the ordering bug; **REVISED to be per-side**

- Each side's own coordinator has `shouldShutdown` set to `true` **before** that side's own teardown — for the
  sender, before the executor drain (`executor.shutdown()` + `awaitTermination`), exactly as originally designed
  and already implemented; for the receiver, independently, before/around `Njams.stop()`'s receiver handling
  (`ShareableReceiver.removeNjams()` or direct `.stop()`) — no longer inherited implicitly from the sender's
  shared coordinator, since there isn't one.
- An in-progress reconnect worker on either side is **actively interrupted** by that side's own coordinator on
  its own shutdown, independently — not by virtue of the other side shutting down first.

## 6. Failure propagation to the client (public-facing) — **REVISED**

- The public-facing change is `Njams.start()`'s connect-gating: it now depends **only on the sender** (§5.1); a
  receiver-only failure — at startup or later — never affects `start()`'s return value, unconditionally. This
  narrows the original design's "reflects the shared transport" language: there is no shared transport gate any
  more, only the sender's.
- This is an accepted, intentional behavior change to `Njams.start()`'s observable contract (see D1.6). Per this
  revision's own review of D1.5: none of Part 1-3's new public API has shipped in a release yet, so this and any
  other simplification enabled by the revision does not need deprecation handling.
- Sender/receiver classes (`NjamsSender`, `AbstractSender`, `SenderPool`, `CommunicationFactory`,
  `AbstractReceiver`, `Receiver`, `SenderExceptionListener`) remain communication-internal (per `CLAUDE.md`;
  `Njams.getSender()` is already `@Deprecated(forRemoval=true)`). Per **D1.5**, their contracts/interfaces **may be
  changed** on this branch where it yields a clean, safe implementation. We still: keep `Njams.start()`'s
  signature, manage the `breaking-change` label (now applied — this revision changes public-facing behavior),
  verify no relocated/shaded types leak through any `public`/`protected` member, and establish test coverage
  before modifying an existing member.

## 7. New setting — **REVISED: scope narrowed to the sender**

- **Key:** `njams.sdk.communication.startup.failbehavior` (constant `PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR`
  in `NjamsSettings`), consistent with the existing `njams.sdk.communication.*` keys.
- **Values:** `fail` (default) | `reconnect`.
- **Scope:** governs the **sender only**. The receiver's startup/reconnect behavior is hardcoded (§5.1.1) and not
  governed by this or any other setting — there is deliberately no receiver-equivalent setting.
- **Default constant** lives in the consuming class (not in `NjamsSettings`), per the project rule.
- **Docs:** `njams-sdk-sample-client/src/main/resources/settings_full.properties` and the communication section of
  `wiki/FAQ.md` need re-wording to drop the "governs the whole shared-transport group" language from Part 2/3 and
  state the sender-only scope plus the receiver's unconditional best-effort behavior instead.

## 8. Test plan (maps to Decision 2) — **REVISED**

1. **Baseline / regression net (real-transport integration):** unchanged in intent — send/receive correctness
   while connected, delivery resumes after a transient mid-processing loss, clean shutdown flushes + terminates.
   Must additionally stay green with the receiver's coordinator now independent of the sender's.
2. **Spec / acceptance tests (controllable fake transport, deterministic, TDD) — the ones that change:**
   - `start()`'s return value depends only on the sender's outcome, under both settings values, regardless of
     the receiver's simulated outcome (§5.1) — this **replaces** the tests proving both sides gate `start()`.
   - Receiver connection loss/reconnect logs the exact WARN/INFO pair in §5.1.1, not the prior generic
     "logged once" INFO/INFO pair — replaces the existing receiver logging spec test.
   - Independent shutdown: stopping does not require the sender's coordinator to signal the receiver's, and
     vice versa (§5.3) — replaces the test that proved they *do* share a shutdown flag (that test's premise is
     now false and it must be inverted or deleted, not adjusted).
   - Instance isolation (two `Njams` instances do not share reconnect state) — unchanged in intent, now also
     covers "receiver of instance A and sender of instance A don't share state either."
   - **No timing-based tests** — unchanged rule.
3. **Real-transport smoke (JMS + HTTP):** unchanged in intent; must still pass with the receiver decoupled.
4. **Kafka:** unchanged — relaxed, no real broker.

## 9. Components touched — **REVISED**

- `AbstractSender`, `NjamsSender`, `SenderPool`, `CommunicationFactory` — unchanged from Parts 1-2 (sender
  lifecycle is not affected by this revision).
- `AbstractReceiver` — keeps its own coordinator (Part 3's static removal stays); no longer receives the
  sender's coordinator instance; startup/reconnect/shutdown logging and gating per §5.1.1/§5.3.
- `Njams` — `beginConnect()`/`startReceiver(...)`/`start()`/`stop()` revised so the receiver's outcome never
  gates `start()` and shutdown signaling is independent per side (§5.1, §5.3); the Part 3 coordinator-sharing
  call (`wireReceiver`) is removed with no replacement (D1.7, which would have wired a cross-notification hook
  in its place, was cut; see §0).
- `NjamsSettings` — no new key; existing `PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR` scope narrows to sender
  only (documentation/semantic change, not a new constant).
- Docs: `settings_full.properties`, `wiki/FAQ.md` — reword the "governs the whole shared-transport group"
  language from Part 2/3.

## 10. Out of scope / non-goals — **REVISED**

- No change to the wire message format.
- Kafka behavior parity beyond "still works"; not integration-tested, same as before.
- No new public API beyond what already exists; `Njams.start()`'s signature unchanged (only its behavior for the
  receiver-only-failure case, per D1.6).
- Cross-side connection verification (D1.7) — briefly designed and implemented, then cut from this ticket's
  scope after review found it tears down a healthy sender pool on a receiver-only hiccup without solving the
  problem it targeted; deferred to a future, separate reconnect-handling design (see §0).
- No new/second startup-failure setting for the receiver; its behavior is deliberately hardcoded, not
  configurable (D1.6).

## 11. Open items carried into the implementation plan

- ~~Whether `Receiver.startWithTimeout(long)` (Part 3) is simplified/removed now that the receiver's
  `reconnectOnFailure` branch is always the same value, or left as dead-but-harmless API surface.~~ Resolved by
  the SDK-375 final-review fix for finding #1: it had zero production callers and has been removed outright
  (along with `AbstractReceiver.startWithTimeout(long)` and the startup-timeout bookkeeping — `startupLatch`,
  `startupError`, `startupTimedOut` — that it alone used), and `AbstractReceiver.beginConnect()`'s success path
  now also releases a connection that completes after `setShouldShutdown(true)` was already called, closing the
  leak that the dead `startupTimedOut` branch used to (nominally) guard.
- Exact wording/placement of the `wiki/FAQ.md` and `settings_full.properties` updates for the narrowed setting
  scope.
