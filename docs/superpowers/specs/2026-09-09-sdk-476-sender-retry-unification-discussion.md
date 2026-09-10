# Sender Retry/Discard-Policy Unification — Discussion Notes

**Ticket:** SDK-476 — *Unify sender retry and discard-policy handling across transports*
**Branch:** `SDK-375` (based on `6.0-dev`)
**Status of this doc:** design has fully converged through discussion — all points raised during discussion are
settled (section 5 is a carry-forward to-do list, not open questions). Ticket created and started; still needs
a `writing-plans` pass before implementation (section 6).
**Origin:** came up while reviewing the `AbstractSender.isCongestion`/`isMessageRejected` split introduced by
SDK-474 (see recent commits on this branch, and
`docs/superpowers/specs/2026-08-11-sdk-472-single-threaded-sender-reconnect-design.md` /
`.../2026-07-02-sdk-375-sender-lifecycle-design.md` for the surrounding SPI-redesign context).

---

## 1. Problem / question being explored

`HttpSender`, `JmsSender`, and (partially) `KafkaSender` each implement their own local send-retry loop
(`tryToSend`) inside the SPI Contract type `AbstractSender`. These loops look structurally similar (bounded
retry, sleep, give up and throw) but differ in real ways — the question was whether they should be merged into a
single, transport-independent implementation living in `AbstractSender`, with each transport reduced to a
minimal "single attempt + classify" contract.

This is explicitly in-scope territory: `public-api-design.md` already calls the sender/receiver SPI "especially
sensitive" but under active redesign for SDK-375 and its follow-ups (SDK-472, SDK-473, SDK-474).

A concrete bug surfaced while working through this (section 4) that became the strongest argument for doing it:
today's per-transport loops don't just duplicate code, they produce genuinely different and in HTTP's case
incorrect behavior for the same conceptual situation.

## 2. What already exists (verified from source, not assumed)

- **Message survival across a failed send is already guaranteed one layer up**, independent of anything inside
  a sender's own retry loop: `NjamsSender.dispatch` (`NjamsSender.java:210-226`) keeps the message on the
  worker thread, retiring the failed sender and acquiring a fresh one for the *same* message, in a loop. A
  sender that did zero local retrying would still never lose a message because of this.
- **HttpSender.tryToSend** (`HttpSender.java:395-451`): catches any exception *and* any non-2xx/204 status,
  retries up to `MAX_TRIES=20` at `EXCEPTION_IDLE_TIME=50ms`, consulting `isMessageRejected`/`isCongestion` only
  inside the `ON_CONNECTION_LOSS` branch. Under `ON_CONNECTION_LOSS` + non-congestion it discards by `break`ing
  the loop — **without throwing**. This is the bug described in section 4.
- **JmsSender.tryToSend** (`JmsSender.java:305-334`): only catches `ResourceAllocationException` (JMS's
  provider-independent congestion signal, see `isCongestion` at `JmsSender.java:404-414`) — any other
  `JMSException` is a single honest attempt that escalates immediately. Retries up to `MAX_TRIES=100` at
  `EXCEPTION_IDLE_TIME=50ms`.
- **KafkaSender** (`KafkaSender.java:298-326`) has no local retry loop at all — one attempt, classify, always
  rethrow (even after discarding — a separately tracked pre-existing defect, see the class's own Javadoc
  reference to **SDK-475**). It already relies entirely on the Kafka producer's own internal retry
  configuration. Structurally, Kafka is already closest to the proposed unified shape, except that it currently
  over-reports (always throws) where the new design wants it to sometimes not.
- **DiscardPolicy** (`DiscardPolicy.java`) has three values, and `DiscardPolicy.DEFAULT = DISCARD` (not `NONE`):
  - `NONE` — never discard, block if it can't be sent.
  - `ON_CONNECTION_LOSS` — discard only on a confirmed connection loss; blocks (retries) for anything else,
    including congestion.
  - `DISCARD` — discard on any issue, whenever a message can't be sent directly.
- **JMS has an independent, asynchronous broken-connection channel** that HTTP lacks: `JmsSender` registers
  itself as the JMS `Connection`'s `ExceptionListener` (`JmsSender.java:119, 383-385`) and calls
  `notifyConnectionFailure` from `onException` — decoupled entirely from what any single `send()` call does with
  a given message. HTTP has no equivalent; the SDK only ever learns about HTTP's health from inside an actual
  `send()` attempt. HTTP does still model a connection abstraction (`connect()`/`testConnection()`,
  `HttpSender.java:206-217`), so "HTTP is stateless" does not mean the SDK's own connection-health bookkeeping
  is meaningless for it — see section 4.
- **`SenderPool.reportFailure`** (`SenderPool.java:470-489`) is the only path that retires a sender, runs
  `SenderConnector`'s reconnect, and fires `SenderRecoveryListener`/`SenderExceptionListener` (public API via
  `NjamsSender.addSenderRecoveryListener`/`addSenderExceptionListener`). It calls `failGroup(brokenConnection)`
  (`SenderPool.java:561-579`), which sets `reconnecting = true` **synchronously**, before `reportFailure`
  returns — this is what makes the resolution in section 4 work.
- **`SenderPool.acquire()`** (`SenderPool.java:369-389`) already fast-discards a message when
  `reconnecting == true` and the policy is `DISCARD` or `ON_CONNECTION_LOSS` (`discardsOnFailure`), and
  otherwise blocks waiting for reconnect (`NONE`). This existing, already policy-aware logic is reused as-is by
  the final design — it needed no changes.

## 3. Final agreed design

Reduce each transport's *sending*-related contract to four members: `sendOnce(...)`, `isCongestion(Throwable)`,
`isMessageRejected(Throwable)` (name confirmed — stays as the existing method, not renamed to `isBadMessage`,
which was only ever informal discussion shorthand), and a `logError(Throwable)` callback for transport-specific
diagnostics (exact signature not fixed — see section 5).
`AbstractSender` owns the entire retry/backoff state machine as a template method. This is not the transport's
whole contract — lifecycle members (`connect()`, `close()`, `getName()`, and anything else `AbstractSender`
already declares outside of sending a message) are unaffected by this design and remain exactly as they are
today; only the four send-path members above are new/changed. `sendOnce` never special-cases
anything itself — on any failure it always throws (re-throwing, not wrapping, so e.g. HTTP's existing distinction
between `HttpSendException`/`HttpStatusException` survives without the shared loop needing to know about it) —
all classification and control-flow decisions live in exactly one place, split across `AbstractSender` (the
retry loop) and `NjamsSender.dispatch` (the retire-or-not decision), never inside a transport's own `sendOnce`.

### Inner loop (`AbstractSender`, every attempt) — bounded, always ends in success or a throw

- Invoke `sendOnce()`.
- On failure: `isMessageRejected` true → throw immediately, no retry (retrying a bad message can never help).
- On failure: `discardPolicy == DISCARD` → throw immediately, no retry either. **Resolved (was open point 1):**
  `DISCARD` skips the smoothing window entirely and discards on the very first failure. This makes `DISCARD` the
  policy with the least impact on the monitored application — it never introduces any delay or blocking from
  retrying, which is exactly what choosing `DISCARD` is meant to buy. `NONE` and `ON_CONNECTION_LOSS` are
  unaffected and still get the smoothing window below.
- Otherwise (`NONE`/`ON_CONNECTION_LOSS`), retry at **50ms, 200ms, 750ms** (three attempts, ~1 second total) — a
  short smoothing window absorbing a single transient blip (e.g. a network hiccup that immediately clears).
  Congestion and a generic connection issue are *not* distinguished at this stage — the inner loop does no
  classification beyond the two immediate-throw checks above.
- If all three retries fail, **throw** — unconditionally. The inner loop itself never decides to keep retrying
  forever; that decision belongs to the outer loop below.

### Outer loop (`AbstractSender`, wraps the inner loop) — the congestion decision

- Catches whatever the inner loop throws and classifies it with `isCongestion`.
- **Congestion, and `discardPolicy != DISCARD`:** sleep briefly, then invoke the inner loop again from the top
  (a fresh `sendOnce` + its own three smoothing attempts). This is what "retry indefinitely" actually means
  mechanically — repeated inner-loop invocations, not an unbounded loop that suppresses the throw. It never
  escalates past this point: congestion means the connection itself is fine, so retiring/reconnecting it would
  be pure overhead for no benefit, and retrying on a fresh connection wouldn't help a target that's merely busy.
- **Everything else** (congestion + `DISCARD`, or a real connection issue that survived the inner loop's
  smoothing) → rethrow past the outer loop, out of `send()`, to `NjamsSender.dispatch`.

### Retire-or-not decision (`NjamsSender.dispatch`, at the catch site)

```java
catch (Exception e) {
    if (sender.isMessageRejected(e) || sender.isCongestion(e)) {
        senderPool.release(sender);   // connection is fine — drop the message, keep the sender
        return;
    }
    senderPool.reportFailure(sender, e);   // genuine connection issue — retire, reconnect, let acquire() apply policy
}
```

No new exception type or marker is needed: `isCongestion`/`isMessageRejected` are already `protected` on
`AbstractSender`, and `NjamsSender` shares the package. The two predicates are called a second time here
(already evaluated once inside the inner loop to decide whether to escalate) — cheap, and correct as long as
they stay pure functions of the throwable, which their existing contract already requires.

Because `SenderPool.reportFailure` sets `reconnecting = true` synchronously via `failGroup` before returning,
looping back to `senderPool.acquire()` after `reportFailure` immediately hits the pool's existing, already
policy-aware discard/block logic — no changes needed there. This is what closes the HTTP gap from section 4: the
fix is not new plumbing, it's *removing* HTTP's special-cased `break` and making it flow through the same
mechanism the pool already had.

### Resulting behavior per classification × policy

| classification | `DISCARD` | `NONE` | `ON_CONNECTION_LOSS` |
|---|---|---|---|
| bad message | discard, sender kept (never reaches the retire decision as "retire") | same | same |
| congestion | discard, sender kept | retry infinitely, same sender, never escalates | retry infinitely, same sender, never escalates |
| other (connection issue) | throws → discard via pool, sender retired | throws → pool blocks for reconnect, then retries same message | throws → discard via pool, sender retired |

### A cross-layer invariant this design depends on

This subsection is fundamentally a **test-coverage requirement for new code**, not a hypothetical risk: this
outer-loop/policy interaction is entirely new (`testing-conventions.md`: "cover all new/changed code with
tests"), and its failure mode is easy to miss by accident — it doesn't throw, log an error, or crash anything; it
silently drops a message. A test suite that only checks "does it eventually succeed" or "does it throw on a real
failure" would never catch this, so it needs a test written for it deliberately.

`AbstractSender`'s outer loop must **never** let a congestion failure escape past it (into `dispatch`) unless
`discardPolicy == DISCARD`. If it did (e.g. a future bug skips re-invoking the inner loop under `NONE`),
`dispatch`'s `isCongestion(e) == true` check would silently discard a message that `NONE`/`ON_CONNECTION_LOSS`
promise to never give up on — reintroducing the same class of bug this design set out to fix, just relocated.
This needs to be stated explicitly as a documented invariant on the outer loop (not left as an emergent property
of "the loop happens to work this way"), and covered by a dedicated test: congestion under
`NONE`/`ON_CONNECTION_LOSS` never throws past the outer loop; only under `DISCARD` does.

**`isCongestion`/`isMessageRejected` must never throw — full stop.** Not just "return `false` when the transport
can't positively identify congestion" (which `AbstractSender`'s existing Javadoc already says), but "an internal
failure while deciding is itself just another case of 'cannot decide,' so it also resolves to `false`." This
needs to be stated as an explicit, hardened part of the contract in the Javadoc for both methods, not left
implicit. It also narrows the causes below: a throwing classifier is a contract violation, not a design gap the
outer loop needs to defensively guard against — the same way a caller isn't expected to guard against `equals()`
throwing. `SenderPool.classifyQuietly` (`SenderPool.java:586-593`) already defensively catches a throwing
classifier today; with the contract now explicit, that catch becomes defense-in-depth against a violation rather
than the primary mechanism, and can stay as cheap insurance without needing to be duplicated at the new call
sites.

**More detail on what this requires:**

- **Where it's documented:** Javadoc on the outer-loop's method in `AbstractSender` should state the invariant
  directly (e.g. "a congestion failure is only ever propagated out of this method when `discardPolicy ==
  DISCARD`; for every other policy it is retried indefinitely on the same connection and never reaches the
  caller") — not just described in this spec, since this is exactly the kind of behavioral contract that would
  otherwise silently rot if a future change touches the outer loop's condition (e.g. someone adds a fourth
  `DiscardPolicy` value and forgets to extend the `!= DISCARD` check, or refactors the condition and inverts it
  by mistake).
- **What could go wrong if it's violated:** the retire decision itself is never at risk — `isCongestion(e) ==
  true` always means "don't retire" at `dispatch`, correctly, in every scenario, since congestion should never
  retire the sender. The actual risk is message loss. `dispatch`'s "don't retire" branch does exactly one
  thing — `senderPool.release(sender); return;` — which drops the message permanently; there is no "don't
  retire, but keep retrying this message" option at that layer, because that capability is only supposed to
  exist inside the outer loop's own internal retry. A congestion failure under `NONE`/`ON_CONNECTION_LOSS` is
  supposed to *never reach* `dispatch` at all — the outer loop is supposed to swallow it and keep retrying
  forever. If the invariant breaks and one escapes anyway, `dispatch` still correctly declines to retire the
  sender, but its only remaining option is to drop the message — which is exactly what those two policies must
  never do to a congestion failure. So the bug isn't "wrong retirement," it's "a message that should have been
  retried forever is silently lost instead" — the same class of problem section 4 fixed for HTTP, just
  relocated into the shared outer loop, where it would affect every transport at once instead of just HTTP.
- **How this could actually happen — two realistic causes, now that a throwing classifier is precluded by
  contract:**
  1. **A transport's `isCongestion` returns a wrong answer** (not a throw — that's now a contract violation, not
     a design gap). Contained to that one transport.
  2. **A bug in `AbstractSender`'s own outer-loop condition** — the shared code this design adds, not any
     transport's code (e.g. the `discardPolicy != DISCARD` check gets inverted, or a future fourth
     `DiscardPolicy` value isn't added to it). This is the bigger risk of the two: it's new code with no track
     record, and a bug here breaks the invariant for HTTP, JMS, and Kafka simultaneously rather than just one
     transport.
- **What the test should look like:** a test against `AbstractSender`'s shared outer loop (using a test/mock
  subclass whose `sendOnce` always fails with a congestion-classified failure — no real transport needed, since
  this logic is entirely transport-independent), asserting:
  - Under `NONE` and `ON_CONNECTION_LOSS`: `send()` does not throw and does not return control to the caller
    even after many (e.g. hundreds of) consecutive congestion failures from `sendOnce` — proving it keeps
    retrying rather than giving up after some bounded count — and does eventually succeed once `sendOnce` is
    made to succeed.
  - Under `DISCARD`: the same congestion failure causes `send()` to return immediately (per section 3's
    `DISCARD` resolution, on the very first failure, without ever reaching the outer loop at all).
  - As a contrast case: a **non-congestion** ("other") failure under `NONE`/`ON_CONNECTION_LOSS` *does* throw
    once the inner loop's three attempts are exhausted — proving the invariant is specific to congestion, not a
    blanket "these policies never throw."
  - This belongs at the `AbstractSender` level, once, not duplicated per concrete transport — the outer loop
    being tested is shared, not transport-specific (see `testing-conventions.md` on coverage expectations).

### Fast-discard for messages arriving during a known outage (verified consequence, not a new mechanism)

Once a sender reports a genuine connection issue (the "other" row above), every message dispatched *afterward*
— already queued, or newly submitted via `NjamsSender.send()` while the outage lasts — is discarded before ever
entering a sender's inner loop, under `DISCARD`/`ON_CONNECTION_LOSS`. This isn't new plumbing; it's the existing
`SenderPool.acquire()` fast-path (`SenderPool.java:369-389`) becoming reachable for HTTP for the first time:

1. `reportFailure` sets `reconnecting = true` synchronously (`SenderPool.java:563`, section 3).
2. `NjamsSender.dispatch` (`NjamsSender.java:210-226`) calls `senderPool.acquire()` *before* handing a sender to
   any message — including brand-new ones.
3. `acquire()` sees `reconnecting == true`; under `DISCARD`/`ON_CONNECTION_LOSS` (`discardsOnFailure`) it returns
   `null` immediately, and `dispatch` returns without ever calling `sender.send(...)` — no `sendOnce`, no
   smoothing retries, nothing transport-specific runs at all. Under `NONE`, `discardsOnFailure` is false, so
   `acquire()` blocks waiting for reconnect instead, consistent with `NONE` never discarding.

This applies uniformly to all three transports — it lives entirely in the shared pool, not in any transport's
own code. HTTP only needed to stop opting out of it: its `ConnectionStatus`/`testConnection()` model
(`HttpSender.java:206-217`) already gives it a well-defined "broken" state to report, exactly like JMS's
connection object — it just wasn't reporting through it before.

## 4. The HTTP silent-discard bug that motivated this (resolved)

Today's shipped `HttpSender.tryToSend`, under `ON_CONNECTION_LOSS` + a non-congestion failure, discards by
`break`ing the loop and returning normally (`HttpSender.java:424-429`) — **without throwing**. Since
`NjamsSender.dispatch` only calls `senderPool.reportFailure` when `send()` throws, this means: the sender is
never retired, `testConnection()` is never re-run, `SenderRecoveryListener`/`SenderExceptionListener` never fire,
and `SenderPool.acquire()`'s already-existing fast-discard-while-reconnecting logic never engages for subsequent
messages — every message during an HTTP outage independently pays the full local retry budget before being
silently dropped, for as long as the outage lasts. This is a **pre-existing gap in shipped code**, not something
this redesign introduces — the redesign would otherwise have widened its blast radius from the opt-in
`ON_CONNECTION_LOSS` policy to the default `DISCARD` policy.

Working theory floated and then refined: HTTP is stateless, so it doesn't need a *reconnect* operation the way
JMS needs to recreate a `Connection`/`Session`/`MessageProducer` — true, but that only justifies skipping the
reconnect operation, not the reporting. `SenderRecoveryListener`/`SenderExceptionListener` are a business-level
"is the server currently reachable" signal that client code can depend on regardless of transport statefulness,
and `SenderPool.acquire()`'s fast-discard-once-already-known-down behavior is likewise independent of whether
there's a socket to recreate.

**Resolution:** the design in section 3 fixes this without any HTTP-specific change — it fixes the general
mechanism (never silently return from a failure that indicates a real connection problem) that HTTP's `break`
was violating. Once `sendOnce` always throws and classification always happens at `dispatch`'s catch site, HTTP
automatically gets the same reporting behavior as JMS/Kafka for free.

## 5. Settled points — carried into implementation, not further design decisions

Everything below is resolved; none of it blocks moving to implementation. Kept here as a to-do list for whoever
implements this, not as open questions.

1. **`logError(Throwable)` signature is a placeholder, not fixed.** It was proposed as `logError(Throwable)`
   during discussion, but that shape isn't a commitment — if accurate, non-regressing per-outcome logging (see
   the logging-hygiene guidance: keep existing logs accurate, don't flood, don't regress) needs more than the
   bare throwable (e.g. which classification/outcome applied), extending the signature to carry that is already
   accepted. Not a design question to resolve before implementing — just build the signature implementation
   actually needs.

2. **Kafka's opposite-direction defect needs reconciling with this design, but sequenced last.** `KafkaSender.tryToSend`
   currently throws unconditionally, even after discarding (`KafkaSender.java:310-325`, tracked separately as
   SDK-475's discard-counting defect). Under the design above, Kafka's `sendOnce`/classification would need to
   stop doing that for the "discard, don't retire" cases (bad message, congestion) the same way HTTP needs to
   stop `break`ing silently for the "other" case — two pre-existing, opposite-direction bugs converging on one
   fix. **Decision: fix this last, once the shared `AbstractSender`/`NjamsSender.dispatch` mechanism is in place
   and proven on HTTP/JMS** — Kafka is deprecated (`kafka-argos-deprecated.md`, no new feature investment, tests
   best-effort) and already closest to the target shape, so there's no reason to let it gate or complicate the
   rest of the rollout.

3. **Document and test the cross-layer invariant from section 3** (congestion only ever throws past the outer
   loop under `DISCARD`) — see section 3's "cross-layer invariant" subsection for the concrete Javadoc and test
   shape this needs.

## 6. Status

- Decision to proceed: made — tracked as **SDK-476**, created and started (`In Progress`, assigned).
- Still needed before implementation: a `writing-plans` pass turning this design into an implementation plan.
