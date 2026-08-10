# SDK-375 — Working Agreement & Quality Plan (branch-local)

> **Scope:** This document applies **only** to the `SDK-375` branch ("Revise sender lifecycle handling")
> and any tickets created for or related to it. It *sharpens* — it does not replace — the general rules in
> `CLAUDE.md` and the njams skills. Where this document and `CLAUDE.md` agree, `CLAUDE.md` wins; where this
> document is stricter, the stricter rule applies for this branch.

## 1. Why this branch is special

Communication is the crucial and most relevant task of the SDK. A regression in the sender/receiver lifecycle
is high-impact and hard to detect, so the bar for confidence on this branch is raised above the general bar.

## 2. Sharpened working rules (this branch only)

1. **Baseline before any change.** The current observable behavior must be pinned down *before* any production
   change. Because this is communication, that means **real-behavior integration tests**, not only unit-level
   mocks. No production code changes until the relevant baseline exists and is green.
2. **Evidence-driven and double-checked.** Every change must be grounded in concrete evidence (code, tests,
   the analysis doc, the spec) and verified twice. **No assumptions, no guessing, no trial-and-error.** A fix is
   only "done" when it is *guaranteed* to work and that guarantee is demonstrated — not asserted.
3. **Ask when unclear.** If the spec is ambiguous, ask. Never decide unilaterally or invent requirements.

## 3. Branch facts

- **Base branch:** `6.0-dev` (not `master`). Any "is the base ahead?" / merge check is done against
  `origin/6.0-dev`. At session start (2026-07-02) the branch equalled `origin/6.0-dev`, so no merge was needed.
- **Fix version** (for any ticket): version from root `pom.xml` with `-SNAPSHOT` stripped → `6.0.0`.

## 4. Ticket scope (SDK-375)

The sender cannot distinguish three lifecycle phases that have different requirements:

1. **Startup** — SDK triggers the initial connect. On failure: **log once** and **propagate the failure to the
   client implementation**, which decides whether to keep trying or cancel. Only the *very first* attempt is
   special; a second attempt is equivalent to reconnect.
2. **Processing** — phase 1 has succeeded once. On connection loss, perform **infinite, quiet reconnect** until
   shutdown. Reconnect must be **gated on having been connected before**.
3. **Shutdown** — finish the in-flight (final) message if the connection is valid. A failing final send **must
   not** trigger reconnect; an in-progress reconnect **must be cancelled**. Connection issues during shutdown are
   ignored.

**Affected components:** `NjamsSender`, `AbstractSender` + implementations, `SenderPool`, possibly the receiver
connection.

### 4.1 Current-state gaps (from the evidence-backed analysis)

> **Evidence base:** `docs/SDK-375-sender-lifecycle-analysis.md` (committed at `f14dd4e4`, now in this branch).
> The points below summarize it. Per branch rule 2, the analysis itself must still be **re-validated against the
> live code** at the moment any specific change is made — it is the starting evidence, not a substitute for
> reading the code being changed.

- Lifecycle logic lives entirely in `AbstractSender`, shared by all three transports → gaps are identical for
  HTTP, JMS, Kafka (transports differ only in *how* connect/send fail).
- **Connect is lazy and indirect:** the sender does not connect at `Njams.start()`; it connects on the first
  message via `NjamsSender.send()` → `SenderPool.get()` → `init()` → `startup()` → `connect()`.
- **Phase 1 (Startup):** largely unmet — `startup()` catches the connect exception, logs once, and calls
  `reconnect()` (infinite background `doReconnect()` loop) instead of rethrowing; `CommunicationFactory`'s
  try/catch only catches instantiation/init errors, so the client is never informed and cannot
  cancel/continue. No first-attempt vs reconnect distinction. ("Logged once" roughly holds.)
- **Phase 2 (Processing):** mostly met (infinite reconnect until shutdown, quiet log), **but** reconnect is
  **not** gated on prior success — there is no per-instance "was ever connected" state.
- **Phase 3 (Shutdown):** unmet — `NjamsSender.close()` runs `executor.shutdown()` + `awaitTermination(10s)`
  *before* `senderPool.declareShutdown()` sets `shouldShutdown`; during that drain a failing in-flight send →
  `onException()` → `close()` + `reconnect()` spawns a **new** reconnect thread. In-progress reconnect is only
  weakly cancelled: reconnector daemons aren't interrupted by `shutdownNow()`, so a reconnect blocked in a
  blocking `connect()` stops only after that call returns.
- **Cross-cutting:** no explicit lifecycle phase model (`startup()` and `reconnect()` both funnel into
  `doReconnect()`); JVM-global `static hasConnected`/`connecting` shared across all senders and `Njams`
  instances; non-volatile `hasConnectionFailure` written on the reconnect thread and read elsewhere;
  `onException()` is unconditional (always `close()` + `reconnect()`).
- The **receiver** already implements the intended startup model (`beginConnect` / `startWithTimeout` with
  `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT` + rethrow so `Njams.startReceiver()` marks the SDK inactive) — a
  useful template for the sender.

## 5. Quality / correctness strategy

1. **Spec → executable acceptance tests first.** Translate each phase-requirement above into a behavioral test
   (startup failure propagates; reconnect only after prior success; shutdown never reconnects; in-flight reconnect
   is cancelled; "logged once"). These tests are the definition of done — TDD against the *spec*, not against
   current (partly-broken) behavior.
2. **Two test layers, separating determinism from realism:**
   - **Deterministic lifecycle tests** via a *controllable fake transport* whose connect can be told to
     succeed / fail / hang on demand. Because the lifecycle lives in `AbstractSender`, these exercise the real
     logic with no broker and no timing.
   - **Real-transport integration tests** proving messages are actually **sent and received** end-to-end, and
     that real senders honor the same observable contract. These assert observable send/receive behavior and
     **must not pin implementation details slated to change** in this ticket.
3. **No timing-based tests.** Drive phases with latches / barriers / injected seams and poll for conditions.
   `Thread.sleep`-based timing is banned — it makes "guaranteed to work" hollow and tests flaky.
4. **Assert the logging requirements explicitly.** Use a capturing SLF4J appender to verify "logged once" and
   "must not fill the log" — these are real acceptance criteria.
5. **Prove instance isolation.** Add a test with two `Njams`/sender instances to characterize today's shared
   `static` reconnect state and lock in isolation after the fix.
6. **Pin the API surface up front.** Capture current `NjamsSender` / `AbstractSender` / `SenderPool` signatures
   before touching them. A behavior change here is observable → almost certainly a `breaking-change`; the
   public-API approach must be agreed with the user **before** writing code (per `CLAUDE.md` API rules).
7. **Adversarial double-check before "done."** Independent review pass + full-suite re-run before any completion
   claim. Evidence before assertions.

## 6. Integration-test tooling decisions (confirmed)

- **Simplest setup wins.** Prefer an **embedded in-process broker** where one exists; Testcontainers is the
  fallback (not needed given the choices below).
  - **JMS / ActiveMQ:** **embedded `vm://` broker** via `org.apache.activemq:activemq-broker` (**new test-scope
    dependency** — confirmed allowed). `activemq-client` is already on the test classpath; the broker artifact
    is the only addition.
  - **HTTP:** the JDK built-in `com.sun.net.httpserver.HttpServer` — **no new dependency**.
  - **Kafka:** **deprecated but must remain in place**; testing **relaxed** — *no real broker*. Covered only by
    the controllable fake transport (layer 2) and existing unit mocks.
- **Dependencies:** new **test-scope** dependencies are allowed; **no new production dependencies**. Use the
  latest stable version when adding one.
- **Evidence:** no real-broker / real-HTTP-server test exists today — all current transport tests are mock/unit
  level — so these integration tests are genuinely new.

## 7. Resolved decisions

### Decision 1 — Sender lifecycle & connection-state model (public API)

> **Revision note (post-Part 3):** Parts 1-3 implemented D1.1-D1.5 below as first written — sender(s) and
> receiver shared one connection coordinator ("one fate"). That is now revised: **sender and receiver are not
> equally critical.** Sender failure remains fatal (client cannot push data to nJAMS — unchanged priority from
> the original ticket); receiver failure must never be fatal (client keeps working; it only cannot receive
> commands from the server, which is rare). D1.1-D1.4 are marked **REVISED** below. D1.6 (`start()` depends only
> on the sender) was introduced later and is not duplicated here — see the design spec's §3 decisions table
> (`docs/superpowers/specs/2026-07-02-sdk-375-sender-lifecycle-design.md`) for its authoritative text, to avoid
> the two documents drifting apart again. D1.7 (a cross-side "assume-and-cycle" trigger, also introduced later)
> was briefly designed and implemented in Part 4, then **cut after review**: it tore down a healthy sender pool
> on a receiver-only hiccup and did not solve the problem it targeted. It is deferred to SDK-473 (one-directional:
> failing sender triggers receiver reconnect) — see the design spec's §0 for the full account.

- **D1.1 — Connection state, per side (REVISED).** Sender(s) and receiver each own **independent** connectivity
  state — a connection coordinator (lifecycle phase, "was ever connected", reconnect loop, failure signal) is no
  longer shared between them; each side consults/updates only its own. It still replaces the per-`AbstractSender`
  flags **and** the JVM-global `static hasConnected`/`connecting` on both `AbstractSender` and `AbstractReceiver`
  — that part of the original decision stands, only the *sharing between sides* is reverted.
- **D1.2 — Scope = per shared-transport group, per side (REVISED).** With `PROPERTY_SHARED_COMMUNICATIONS=true`,
  all `Njams` instances sharing the sender pool share one connection state, and independently, all instances
  sharing a receiver share their *own* connection state — the two are no longer the same state. Otherwise the
  boundary is the single `Njams` instance, for each side independently. This still fixes the
  cross-instance-coupling defect from the analysis; it no longer also couples the two sides to each other.
- **D1.3 — Independent fate (REVISED).** A connect failure on one side no longer flips the other side to "needs
  reconnect" as a shared fate. Each side runs its own coordinated reconnect, gated on having been connected
  before (Phase 2), fully independently — neither side's failures affect the other's. (An earlier revision of
  this design added one deliberate cross-side coupling here, D1.7; it was subsequently cut — see the design
  spec's §0.)
- **D1.4 — Startup-failure policy is configurable, sender-only (REVISED).** On the *sender's* initial connect
  failure, behavior is controlled by the existing setting:
  - **fail-fast (DEFAULT):** `Njams.start()` returns `false`, SDK inactive ("stop initialization" maps onto
    today's `return false` — `start()` is **not** changed to throw).
  - **reconnect:** `start()` succeeds and the sender enters the Phase-2 background reconnect loop.
  - The receiver is **not** governed by this setting any more — its behavior is unconditional and hardcoded (see
    D1.6 in the spec): a connection failure, at startup or later, is logged at WARN and always retried in the
    background, never fails `start()`. This is a narrower, more accurate fix for the original "receiver hard
    fail-fast vs. sender silent infinite reconnect" inconsistency than unifying both under one setting.
- **D1.5 — Sender/receiver contracts & interfaces MAY change on this branch.** Unchanged. `NjamsSender` /
  `AbstractSender` / `SenderPool` / `CommunicationFactory` / `AbstractReceiver` / `Receiver` /
  `SenderExceptionListener` are communication-internal (per `CLAUDE.md`; `Njams.getSender()` is already
  `@Deprecated(forRemoval=true)`). **Changing their contracts/interfaces (signatures, method sets, abstractions)
  is explicitly permitted for this branch, provided it supports a clean and safe implementation.** Constraints
  that still hold: `Njams.start()`'s **public signature stays** (only its connect-gating behavior changes, now to
  depend only on the sender); **no relocated/shaded third-party type** may appear on any `public`/`protected`
  member; still **manage the `breaking-change` label** (now applied — this revision changes `Njams.start()`'s
  observable behavior); and per `njams-safe-modification`, establish **test coverage before** modifying any
  existing member (baseline-first). Note: none of Part 1-3's new public API has shipped in a release yet (still
  `6.0.0-SNAPSHOT`), so simplifying or removing it for this revision does not need deprecation ceremony.
- **Resolved (was deferred to the design spec):** each side keeps its *own* physical connect (HTTP POST vs.
  receiver channel; JMS producer vs. consumer session) — confirmed true for all three transports. This revision
  goes further than the original deferral anticipated: not only is the physical connection separate, the
  coordination/phase state is now separate too, for the reasons in the revision note above.

### Decision 2 — Baseline test strategy

- **D2.1 — Three test roles.**
  1. **Baseline / regression net (real-transport integration):** pins only the *stable, surviving* contract —
     send/receive correctness while connected, delivery resumes after a transient mid-processing loss (Phase 2),
     clean shutdown flushes + terminates. **Green now, stays green.**
  2. **Spec / acceptance tests (controllable fake transport, deterministic):** the *changing* behaviors —
     startup-failure policy, now sender-only (D1.4), reconnect-only-after-prior-success, no-reconnect-during/
     after-shutdown, state scoped per side per group (D1.2), "logged once" (per side — see the spec's §5.1.1 for
     the receiver's specific wording), instance isolation. Written **TDD**, red until implemented. **Not**
     baseline.
  3. **Real-transport smoke (JMS + HTTP):** prove real senders honor the observable contract end-to-end after the
     rework.
- **D2.2 — Baseline pins the stable contract only** (not the to-be-changed Phase-1/3 internals).
- **D2.3 — Tooling** as in §6: embedded ActiveMQ `vm://` (new `activemq-broker` test dep), JDK `HttpServer`,
  Kafka relaxed (fake transport only).

## 8. Remaining open items

- [x] Obtain the full analysis document — done (`f14dd4e4`); §4.1 reflects it.
- [x] Public-API approach (Decision 1) — resolved above.
- [x] Baseline test strategy (Decision 2) — resolved above.
- [x] **Full implementation design spec** — drafted at
      `docs/superpowers/specs/2026-07-02-sdk-375-sender-lifecycle-design.md` (pending user review → then
      writing-plans). Resolves the D1 deferred sub-point: all transports use separate sender/receiver
      connections, so the shared layer is coordination/phase only.
- [x] New startup-failure setting named: `njams.sdk.communication.startup.failbehavior` = `fail` (default) |
      `reconnect`.
- [ ] **Post-Part-3 revision:** decouple sender/receiver criticality (D1.1-D1.4 revised above; new D1.6 in the
      design spec). D1.7 (cross-side connection verification) was designed, briefly implemented, and then cut
      after review — see the design spec's §0 — and is deferred to SDK-473.
