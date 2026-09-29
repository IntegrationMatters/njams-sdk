# SDK-483 — Docker-Based Communication Resilience Test Module: Design Spec

**Ticket:** SDK-483 — *Add Docker-based manual integration/resilience test module for communication transports*
**Branch:** `SDK-375`
**Status of this doc:** design agreed in brainstorming; pending user review, then an implementation plan
(`writing-plans`).
**Related tickets:** SDK-375 (*Revise sender lifecycle handling*), SDK-472 (*Single threaded sender reconnect*),
SDK-476 (*Unify sender retry and discard-policy handling across transports*) — all `Relates` links; this module
exists to give their behavior a real-infrastructure regression harness.

---

## 1. Problem

SDK-375/472/476 established and unified the sender lifecycle (startup/processing/shutdown phases), the
single-reconnector pool behavior, and the transport-independent retry/discard-policy classification. All of this
is covered by unit and mocked-transport tests, but none of it has been exercised against a *real* JMS broker or a
real HTTP server under actual network fault conditions (dropped connections, slow/degraded endpoints, real
broker-side producer/session close semantics, real HTTP status-code responses). Mocks cannot reproduce the timing
and provider-specific behavior that this lifecycle logic depends on for correctness.

There is currently no module in this repository set up to run this kind of test at all.

## 2. Goals

- A new Maven module that drives a real `Njams` instance against a real ActiveMQ broker (JMS) and a real HTTP
  stub server (WireMock), through a real network fault-injection layer (Toxiproxy), to regression-test the
  sender/receiver lifecycle behavior established in SDK-375/472/476.
- A minimal, deliberately simple message-generation harness whose own logic is not a source of test flakiness —
  the tests are about communication, not about client-side business logic.
- A scenario catalog scoped to the mechanisms actually introduced or changed by SDK-375/472/476, not an
  exhaustive fault × policy × transport matrix.
- A regression checklist for resource/state cleanup (connections, threads, listener registrations, in-flight
  messages) that a mocked test cannot observe realistically.
- The suite must not run as part of the default build (`mvn clean install`) — it requires Docker and is
  explicitly a manual/on-demand suite.

## 3. Non-goals

- **Kafka.** Deferred as a future extension to this module. Not built now.
- **Diagnosing or fixing any defect this suite finds, or adding that fix's regression coverage here.** If a
  scenario surfaces a real bug (see §7, the `HttpSender.close()` finding), root-causing and fixing it is a
  separate ticket, worked with the project's normal JUnit/mocked-IT TDD approach — including that fix's own
  regression guard, which lives in `njams-sdk`'s own test suite, not in this module. This module's sole purpose
  is exercising documented behavior under virtually-real-life conditions; it does not grow in response to a bug
  fix. See §9 for what does warrant a new scenario here.
- **Client-side business-logic variety.** The process/activity model driving these tests is fixed and trivial by
  design (§4). No scenario varies it.
- **Discard-mode combinations that do not exist.** Every fault scenario is verified under each discard mode
  (§6.1), but scenarios where the SDK's behavior is policy-independent by design (startup with `fail`, degraded
  connect) are not repeated per mode. Startup with `reconnect` is *not* policy-independent and does run per mode
  (scenario 1b).
- **CI integration.** Out of scope for this ticket; this is a manually invoked profile.

## 4. Module design

- **Name/location:** `njams-sdk-communication-it`, a new sibling module at the repo root (alongside `njams-sdk`,
  `njams-sdk-sample-client`, `njams-sdk-sample-app`), added to the root `pom.xml`'s `<modules>`. Depends on
  `njams-sdk`.
- **Build tooling:** fabric8 `docker-maven-plugin` (container lifecycle) + `maven-failsafe-plugin` (`*IT.java`
  execution), both bound to a **non-default** Maven profile (`docker-it`). Plain `mvn clean install` at the root
  never activates this profile and never requires Docker.
- **Containers:**
  - ActiveMQ (JMS broker).
  - Toxiproxy in front of ActiveMQ (JMS fault injection).
  - WireMock, standing in for the real nJAMS Server HTTP ingest API — stubbing both the `HEAD /{dpendpoint}`
    availability check and the `POST /{dpendpoint}` ingest endpoint (the real API surface, per the pasted
    `nJAMS Server` controller definition). Stubs must match both `application/json` and `text/plain` bodies,
    since a message fragment may be sent as a plain-text chunk (`HttpSender`'s `SplitSupport`).
  - Toxiproxy in front of WireMock (HTTP fault injection).
- **Kafka:** not part of this module; noted as a future extension (a Kafka broker container + the same Toxiproxy
  pattern), out of scope here.
- **Source layout:**

```
njams-sdk-communication-it/
  pom.xml
  src/test/java/com/im/njams/sdk/it/
    jms/
      JmsBrokerOutageIT.java
      JmsDegradedConnectIT.java
    http/
      HttpConnectionLossIT.java
      HttpRejectAndCongestionIT.java
    support/
      DockerEnvironment.java        # container lifecycle (JUnit rule/extension)
      ToxiproxyControl.java         # thin wrapper over the Toxiproxy HTTP API
  src/test/resources/
    wiremock/mappings/*.json        # stubbed HEAD/POST responses (413/429/503, availability 200/404)
    activemq/activemq.xml           # broker config, if defaults are insufficient
    logback-test.xml
```

## 5. Message-generation harness

A single fixed, trivial `ProcessModel` with one `ActivityModel` — no branching, no groups — defined once for the
whole suite. Client-side logic is deliberately minimal so it cannot itself be a source of test flakiness.

Three independent knobs, orthogonal to each other:

- **count** — number of jobs run in a loop, each started and ended immediately. Per the SDK's documented
  send-cadence invariant, a short-running job flushes exactly once at `end()`, so job count maps 1:1 to outbound
  `LogMessage` count with no flush-timing guesswork required.
- **size** — a single padding field (business data or one activity attribute) filled to a caller-supplied byte
  length, used to trigger or avoid message fragmentation (`communication/fragments/`, `SplitSupport`).
- **concurrency** — sequential loop vs. N parallel threads submitting jobs, used specifically to exercise race
  conditions in the pool/reconnect-election logic (SDK-472), not to make tests "faster."

The one-time project message sent at `Njams.start()` is treated as a fixed, unparameterized +1 background event
— never controlled by a scenario, and either accounted for as "+1 always-present message" or ignored where the
assertion only cares about the scenario's own driven messages.

## 6. Fault-injection scenario catalog

| # | Scenario | Mechanism | Transport | Verifies |
|---|---|---|---|---|
| 1a | Startup outage, `startup.failbehavior=fail` | Toxiproxy `down` before `start()`; HTTP also tested via `HEAD → 404` ("no active dataprovider found") | JMS + HTTP | Phase 1: `start()` returns `false` and the SDK is shut down fully — instance inactive, no `Sender-Startup-*`/`Sender-Reconnector-*`/`Receiver-*` thread left, and once the target is usable again nothing reconnects or sends on its own (JMS: broker connection count unchanged; HTTP: WireMock journal unchanged). Policy-independent |
| 1b | Startup outage, `startup.failbehavior=reconnect` | Toxiproxy `down` before `start()`, short `connect.timeout`; jobs driven while still down, then the target is restored | JMS + HTTP | `start()` returns `true` and the SDK is initialized; the sender then behaves as for a later connection problem per discard mode (§6.1): background reconnect runs on its own and ends once the target is back, jobs driven afterwards are delivered |
| 2 | Mid-processing outage + recovery | Toxiproxy `down` while jobs are running, then removed | JMS + HTTP | Phase 2 core: single sender elected to reconnect, others drained, pool refuses new senders meanwhile, in-flight message survives and resends after reconnect (SDK-472) |
| 3 | Degraded/slow connect | Toxiproxy `latency`/`timeout` on new connections only, established traffic unaffected | JMS | The accepted-risk fix (SDK-472 decision B2): a slow-but-reachable broker doesn't stall unrelated `acquire()`/`release()` calls in the pool |
| 4 | Shutdown during outage | Toxiproxy `down`, then `Njams.stop()` mid-reconnect | JMS + HTTP | Phase 3: shutdown cancels the reconnect loop promptly, no new attempts |
| 5 | Repeated flap | Toxiproxy toggling `down`/up rapidly, combined with the concurrency knob | JMS | Regression guard: no reconnect-thread pileup, single-reconnector invariant holds, no log flooding |
| 6 | Fragmentation under outage | Large payload (size knob) + Toxiproxy `down` mid-send; WireMock stub matches both JSON and `text/plain` chunk bodies | JMS + HTTP | A chunked message never partially/corruptly delivers — full resend or clean discard, never a fragment gap |
| 7 | Rejected message | WireMock `POST` stub → **413** | HTTP | Discard immediately, connection/pool untouched, regardless of discard policy |
| 8 | Congestion | WireMock `POST` stub → **429**, repeated | HTTP | Local retry without retiring/reconnecting under `none`/`onconnectionloss`; immediate no-delay discard under `discard` — the one case where policy actually changes behavior |
| 9a | Connection problem (application-level) | WireMock `POST` stub → **503** | HTTP | Classified as a connection problem, not congestion — direct regression test for the exact defect class SDK-476 fixed |
| 9b | Connection problem (transport-level) | Toxiproxy `down` in front of WireMock | HTTP | Same retire/reconnect/listener path as 9a, confirming both failure origins land in the same handling |
| 10 | Dispatch-queue saturation | One sender thread and a 2-slot queue (`maxsenderthreads`/`maxqueuelength`), 8 jobs driven while the transport is either *slow* (Toxiproxy `latency` on every exchange, connection stays up) or *down* (Toxiproxy `timeout`) | JMS + HTTP | Discard-mode-dependent blocking vs. dropping of the submitter, see §6.1 |

Scenarios 1b, 2, 4, 5, 6, 7, 8, 9a, 9b and 10 run once per discard mode (§6.1); 1a and 3 are policy-independent.

**Fault-injection caveat:** Toxiproxy's `timeout` toxic (used for "down") blocks only the response direction of an
existing exchange. For HTTP, a request the client reports as failed can therefore still reach WireMock, so a
`logId` may legitimately be seen more than once; delivery-count bounds account for that (at-least-once, server
dedups by `logId`).

### 6.1 Discard-mode matrix

The discard policy (`njams.sdk.discardpolicy`: `none`, `onconnectionloss`, `discard`) defines the SDK's behavior
under connection problems, which is what this module verifies. Each fault scenario therefore runs under **every**
discard mode and asserts that mode's expected outcome. Expected behavior (from `NjamsSender.dispatch`,
`SenderPool.acquire`, `AbstractSender.sendWithRetry`, `MaxQueueLengthHandler`):

| Fault | `none` | `onconnectionloss` | `discard` |
|---|---|---|---|
| Connection problem during processing (2, 6, 9a, 9b, 5) | In-flight message is held and resent after reconnect; new sends block in `acquire()`. Every driven job arrives exactly once. | In-flight message is retired and dropped at `acquire()` while the group reconnects; new sends during the outage are dropped. Jobs sent outside the outage arrive exactly once; dropped jobs are counted by `DiscardMonitor`. | As `onconnectionloss`, and additionally without the quick-retry smoothing window. |
| Fragmented message under outage (6) | Full resend, no fragment gap. | Clean discard of the whole message, no partial delivery. | As `onconnectionloss`. |
| Shutdown during outage (4) | Callers blocked in `acquire()` are released promptly; `stop()` returns promptly. | `stop()` returns promptly. | `stop()` returns promptly. |
| Congestion, `429` (8) | Retried locally, no reconnect. | Retried locally, no reconnect. | One attempt, then dropped; never reconnects. |
| Rejected, `413` (7) | Dropped after one attempt; connection untouched. | As `none`. | As `none`. |
| Dispatch-queue saturation (10) | Submitting thread blocks until a slot frees. | Drops only while the connection is lost. | Always drops when the queue is full. |

**Startup (1a/1b):** with `startup.failbehavior=fail` the client does not become active: `start()` returns `false`
and the SDK shuts down fully (policy-independent, 1a). With `reconnect` `start()` succeeds and the SDK initializes;
the connection issue is then treated like any later connection problem, i.e. by discard mode (1b):

| Startup with `reconnect`, target down | `none` | `onconnectionloss` | `discard` |
|---|---|---|---|
| Startup project message (produced before any connection exists) | Held, delivered after the reconnect. | Discarded while the group reconnects (as the FAQ states for messages produced before the initial connection). Whether it is ever re-sent is not documented and not asserted. | As `onconnectionloss`. |
| Jobs driven while still down | Held, none discarded, delivered after the reconnect. | Every one discarded (counted), none delivered later. | As `onconnectionloss`. |
| Jobs driven after the reconnect | Delivered. | Delivered. | Delivered. |

Status-code classification is taken directly from `HttpSender.isCongestion`/`isMessageRejected`
(`HttpSender.java:479-495`): only `429` is congestion, only `413` is message-rejected, and everything else
(including `502`/`503`/`504` and client-side I/O failures) is a genuine connection problem — this is deliberate
per the existing Javadoc rationale and scenarios 8/9a are written to pin that classification down.

## 7. Stale-structure / leak regression checklist

Layered onto scenarios 2, 4, 5, and 6 above, plus one new lightweight scenario (repeated `Njams` start/stop):

| Structure | Where | Assertion |
|---|---|---|
| Pool bookkeeping (`locked`/`unlocked`/`retired`) | `SenderPool` | Collection sizes return to baseline after each reconnect cycle, across many repeated cycles — no monotonic growth |
| Per-sender transport resource | `JmsSender.close()` closes producer+session+connection; `HttpSender.close()` (`HttpSender.java:288`) only flips connection status, does not touch the `OkHttpClient` or cancel an in-flight call | Broker-side connection count (ActiveMQ JMX) / socket count returns to baseline after retire. The HTTP gap is a known finding (see below) — this assertion exists to catch it as a regression, not to fix it here |
| Reconnect/executor threads | `SenderConnector`'s reconnect thread, `NjamsSender`'s executor | Thread count returns to baseline after each cycle |
| `SenderRecoveryListener` registration | `SenderPool` (has both add and remove, tied to receiver stop per the JVM-wide shared group model) | Repeated start/stop of `Njams` instances sharing a group does not grow the listener collection |
| `SenderExceptionListener` registration | `Njams.java:707` registers the receiver on `start()`; no remove method exists at all | Same repeated start/stop probe — flags unbounded growth as a regression signal |
| In-flight message hand-off | `NjamsSender.dispatch()` / `SenderRetiredException` path (`NjamsSender.java:210-263`) | No duplicate, no drop, across a reconnect-mid-send — every driven job gets a unique correlation id; the broker/WireMock must receive exactly one instance per job |
| Fragmented-send partial state | `SplitSupport` | An outage mid-fragment-sequence does not leave a stuck partial delivery after resend |
| Receiver-side mirror | `AbstractReceiver` (own coordinator/thread/connection since SDK-375's decoupling) | Same thread/connection-count regression checks, receiver side |

**Known finding, not fixed here:** `HttpSender.close()` does not cancel an in-flight `OkHttpClient` call or
dispose its dispatcher when a sender is retired mid-send. This is exactly the "stale sender still holding/
delivering a message a new sender also sent" risk. Per §9, if this suite's scenario 2/6 regression checks confirm
real duplicate/leaked delivery, that becomes a **new, separate ticket**, fixed with the project's normal
JUnit/mocked-IT TDD approach — including the regression guard for that fix, which is added to `njams-sdk`'s own
test suite, not to this module.

## 8. Testing this module itself

The module's own value is the regression assertions in §6/§7 running green against real infrastructure. There is
no separate unit-test layer for the harness itself beyond what's needed to keep the fixed process/activity model
and the three knobs correct — kept intentionally minimal per §2's flakiness goal.

## 9. Scope stability — this module does not grow in response to bug fixes

This module's sole purpose is exercising the SDK's documented communication behavior under virtually-real-life
conditions (real broker, real HTTP responses, real network faults). It is explicitly **not** where defects get
diagnosed, fixed, or regression-tested:

- A defect this suite's assertions surface is fixed in `njams-sdk` itself, through the project's normal
  JUnit/mocked-IT TDD workflow (`njams-bug-fix`). That fix's own regression guard is a unit test or mocked-IT
  in `njams-sdk`'s test tree — never a new scenario added here.
- **This module's scenario catalog (§6/§7) is stable by default.** Fixing a bug found via this suite does not,
  by itself, justify adding a new scenario here — the existing scenario that surfaced the defect already covers
  it once the underlying fix lands, and the suite goes back to green.
- A new scenario is added to this module only on a **deliberate, separate decision** that some transport
  behavior genuinely needs additional real-life verification beyond what §6/§7 already cover — not as a routine
  side effect of any given bug fix or feature change.
