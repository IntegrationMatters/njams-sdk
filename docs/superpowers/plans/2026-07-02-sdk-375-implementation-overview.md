# SDK-375 Implementation — Decomposition Overview

**Ticket:** SDK-375 — Revise sender lifecycle handling
**Design spec:** `docs/superpowers/specs/2026-07-02-sdk-375-sender-lifecycle-design.md`
**Baseline regression net (already in place & green):** `docs/superpowers/plans/2026-07-02-sdk-375-baseline-tests.md`
**Working agreement:** `docs/SDK-375-working-agreement.md`

## Why split, and the guiding rule

The change is large and lives in the concurrency-sensitive runtime path (`communication/`). To keep it safe, it is split into **three sequential parts**, each of which:

- produces working, testable software on its own,
- keeps the **baseline integration suite green at every commit** (the regression net proves no observable-contract regression),
- adds its own deterministic **spec tests** (controllable fake transport) for the behavior it introduces,
- introduces **no new public API** except the one documented setting (Part 2), and changes no relocated/shaded types on public/protected members.

Parts are done **strictly in order** (each depends on the previous). Each part gets its own detailed plan authored when it is reached.

## The three parts

### Part 1 — Sender connection-state foundation *(this is the first plan)*
**Goal:** Replace the JVM-global `static` reconnect state and scattered per-instance flags on the **sender** side with a single, per-shared-transport-group `ConnectionCoordinator` instance, and wire it through `NjamsSender` / `SenderPool` / `AbstractSender`. **Behavior-preserving** (lazy connect on first message, infinite quiet reconnect) — the only observable change is that two unrelated `Njams` instances no longer share reconnect state.
**Delivers:** the `ConnectionCoordinator` abstraction + fixes the cross-instance-coupling defect for senders.
**Proof:** baseline green; new instance-isolation spec test; coordinator unit tests.
**Explicitly NOT in Part 1:** startup/shutdown/reconnect *semantics* changes; the receiver; the new setting.

### Part 2 — Sender startup & shutdown semantics
**Goal:** On the foundation from Part 1, implement the ticket's sender-side lifecycle requirements:
- **Phase 1 (Startup):** eagerly connect one sender at `Njams.start()` within `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT`; introduce the new setting `njams.sdk.communication.startup.failbehavior` (`fail` default | `reconnect`); on the first-attempt failure, fail-fast makes `Njams.start()` return `false` (SDK inactive), reconnect enters the background loop.
- **Phase 2 (Processing):** gate reconnect on `wasEverConnected` (reconnect only after a prior successful connect).
- **Phase 3 (Shutdown):** set the coordinator's shutdown flag **before** the executor drain in `NjamsSender.close()` / `Njams.stop()`; a failing final send during drain must not reconnect; actively interrupt an in-progress reconnect worker.
**Delivers:** the ticket's sender behavior + the configurable startup policy.
**Proof:** baseline green; deterministic spec tests for fail-fast/reconnect startup, reconnect-only-after-connected, no-reconnect-during/after-shutdown, in-flight-reconnect-cancelled; new setting documented in `settings_full.properties` and `wiki/FAQ.md`.

### Part 3 — Receiver unification (shared transport group)
**Goal:** Realize D1.1/D1.2 fully — make the **receiver** share the *same* `ConnectionCoordinator` as the sender group for one shared-transport group, remove `AbstractReceiver`'s `static hasConnected`/`connecting`, unify to **one coordinated reconnect** for the group (a failure on either side flips the whole group), and make `Njams.start()` gate on **both** the receiver and a sender connecting (D1.6). Route the receiver's existing `beginConnect`/`startWithTimeout`/`reconnect` through the coordinator.
**Delivers:** "sender and receiver go hand in hand" — one transport, one fate, correctly scoped per group.
**Proof:** baseline green; spec tests for shared-fate (one side's failure triggers a single group reconnect) and cross-group isolation; receiver instance-isolation.

## Sequencing & dependencies

```
Part 1 (foundation) ──▶ Part 2 (sender semantics) ──▶ Part 3 (receiver unification)
```

- Part 2 needs Part 1's coordinator (it stores `wasEverConnected`, the shutdown flag, and the reconnect worker).
- Part 3 needs Part 2's finished sender semantics before sharing the coordinator with the receiver (so the shared reconnect has well-defined phase semantics to adopt).
- After each part: run the full baseline suite (`JmsSenderBaselineIT`, `JmsClientEndToEndBaselineIT`, `HttpSenderBaselineIT`) — all must stay green.

## Cross-cutting constraints (apply to every part)

- **Baseline stays green.** Any baseline red is a real regression — stop, do not weaken the baseline.
- **Runtime-path performance:** no added allocation/synchronisation beyond what correctness requires; no I/O/blocking on message-processing threads (per `CLAUDE.md` performance rules).
- **API:** sender/receiver contracts *may* change (D1.5) but `Njams.start()`'s public signature stays; no relocated/shaded types on public/protected members; manage the `breaking-change` label.
- **Thread-safety contract** (`logmessage/`) is untouched; this work is confined to `communication/` + `Njams` wiring.
- **`njams-safe-modification`** skill governs every change to existing code (test coverage before modifying).
- **Follow-up:** `SDK-467` tracks the pre-existing JMS-API dependency conflict (out of scope here).

## First plan

`docs/superpowers/plans/2026-07-02-sdk-375-part1-lifecycle-foundation.md` (Part 1).
