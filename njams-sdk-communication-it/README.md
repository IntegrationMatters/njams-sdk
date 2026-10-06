# njams-sdk-communication-it — Communication Resilience Tests

Manual, Docker-based integration tests that drive a **real `Njams` instance** against a **real ActiveMQ broker** (JMS)
and a **real HTTP stub server** (WireMock) while a **network fault injector** (Toxiproxy) breaks the connection in
controlled ways. Design spec: `docs/superpowers/specs/2026-09-28-sdk-483-communication-it-module-design.md`.

## Purpose and intent

The sender/receiver lifecycle (startup / processing / shutdown phases), the single-reconnector sender pool and the
transport-independent retry / discard-policy handling are covered by unit and
mocked-transport tests in `njams-sdk`. Mocks cannot reproduce real timing, broker-side connection semantics or real
HTTP status codes, so this module verifies the **documented behavior under virtually-real-life conditions**.

The core idea: the SDK's behavior under connection problems is defined by the **discard policy**
(`njams.sdk.discardpolicy` = `none` | `onconnectionloss` | `discard`). Every fault scenario therefore runs under
**each** discard mode and asserts that mode's expected outcome (see [Test matrix](#test-matrix)).

## Architecture

```
 JUnit ITs (in-process Njams, the SDK under test)
    │  JMS  tcp://localhost:<toxiproxy.jms>   ──► Toxiproxy :20000 ──► ActiveMQ :61616
    │  HTTP http://localhost:<toxiproxy.http> ──► Toxiproxy :20001 ──► WireMock :8080
    │
    └─ verification bypasses the proxies: drains ActiveMQ queues via the direct OpenWire port, reads WireMock's
       request journal via its admin API, reads broker state via Jolokia, and drives Toxiproxy via its control API
```

- **Message harness** (`harness/`): one fixed, trivial process model (a single activity, no branching/groups) and
  three independent knobs — *count* (jobs), *size* (payload bytes; triggers fragmentation) and *concurrency*
  (submitting threads). A short-running job flushes exactly once at `end()`, so job count maps 1:1 to outbound log
  messages. Keep it that simple so the client side is never a source of flakiness.
- **Support code** (`support/`): `DockerEnvironment` (JUnit rule: ports, per-test reset of toxics and WireMock),
  `ToxiproxyControl`, `WireMockJournal`, `DiscardMode` (the three policies), `DiscardObserver` (counts discards
  per test through `com.im.njams.sdk.communication.CountingDiscardMonitor`, which lives in the SDK's package
  because the monitor's test seam is package-private), `Deliveries` / `DeliveryAssertions` (delivery accounting per
  mode) and `QueueSaturationScenario` (shared base of the two queue-saturation ITs). Receiver ITs additionally use
  `ReceiverSettings` (JMS/HTTP settings through the proxies), `JmsCommandClient` (publishes commands on the broker's
  commands topic and awaits the reply), `WireMockStubs` (serves a Server-Sent-Events command stream) and `SdkThreads`.
- **Accounting rule for the discard modes:** every driven job must either be delivered or be covered by a counted
  discard (`undelivered <= discards`); `none` must deliver everything and discard nothing.

## Limitations and scope rules

- **Manual / on-demand only.** Not part of the default build; needs Docker. Never wire it into
  `mvn clean install`.
- **Detects, does not fix — and is not where bugs are regression-tested.** If a scenario surfaces a real SDK defect
  it is tracked and fixed separately in `njams-sdk` with the normal JUnit/mocked-IT workflow; that fix's
  regression guard lives in `njams-sdk`'s own test suite, never here. Known, tracked findings may be *pinned* here
  with an explicit expected value (see `HttpRepeatedStartStopLeakIT`), to be updated when the fix ships.
- **The scenario catalog is stable.** A bug fix does not add a scenario; a new one is a deliberate, separate decision.
- **Kafka is out of scope** (deprecated transport; a possible future extension).
- **Fixed client model.** No scenario varies the process model beyond the three knobs.
- **Fault-injection caveat:** Toxiproxy's `timeout` toxic (used for "connection down") blocks only the response
  direction of an exchange. A request the HTTP client reports as failed can still reach WireMock, so a `logId` may
  legitimately be delivered more than once (at-least-once; the server dedups by `logId`). Delivery-count bounds
  account for this.
- Timing-based scenarios use fixed outage windows (`DockerEnvironment.OUTAGE_MS`, 3 s — longer than the ~1 s
  quick-retry window so a failure escalates). They are deliberately paced, not fast.

## Docker environment

All containers are managed by fabric8 `docker-maven-plugin` (started in `pre-integration-test`, stopped in
`post-integration-test`) on the custom network `njams-communication-it`. Host ports are assigned dynamically and
written to `target/docker-it.properties`, which `DockerEnvironment` reads.

| Container (alias) | Image | Purpose |
|---|---|---|
| `activemq` | `apache/activemq-classic:5.19.2` | The JMS broker (OpenWire `61616`). ITs drain `njams.event` directly to count what actually arrived; the web console's Jolokia endpoint (`8161`) exposes broker-side connection counts (used by `PoolBookkeepingIT`) and the consumer count of the commands topic `njams.commands` (used by the receiver ITs). Default broker config. |
| `toxiproxy` | `ghcr.io/shopify/toxiproxy:2.12.0` | Fault injector. Proxy `jms` (`20000` → `activemq:61616`) and proxy `http` (`20001` → `wiremock:8080`); control API on `8474`. The SDK under test connects **through** the proxies; toxics (`timeout` = down, `latency` = slow) are added/removed per scenario and reset after every test. |
| `wiremock` | `wiremock/wiremock:3.13.2` | Stand-in for the nJAMS Server HTTP ingest API: `HEAD`/`POST /api/processing/ingest/dataprovider`. Default stubs live in `src/test/resources/wiremock/mappings/` (`head-available.json`, `post-ok.json`); fault stubs (`413`, `429`, `503`, `HEAD 404`) are loaded on demand from `wiremock/on-demand/`. The request journal is the assertion source for HTTP. |

Between tests `DockerEnvironment` clears all toxics, the WireMock request journal, and reloads the default stub
mappings (`POST /__admin/mappings/reset` keeps the mapping files; **never** use `DELETE /__admin/mappings`, it deletes
the mounted files).

## How to run

Prerequisites: a running Docker daemon, Java 11+, Maven 3.8+, and the `njams-sdk` snapshot artifacts (including its
test-jar) resolvable — e.g. run `mvn clean install -DskipTests` at the repo root once.

```bash
# whole suite (from the repo root)
mvn -Pdocker-it verify -pl njams-sdk-communication-it

# a single IT class
mvn -Pdocker-it verify -pl njams-sdk-communication-it -Dit.test=HttpRejectAndCongestionIT
```

- The `docker-it` profile is what adds this module to the build; without it nothing here runs.
- First run pulls the three images. Containers are stopped again after the run.
- Reports: `njams-sdk-communication-it/target/failsafe-reports/`.

**What to expect (as of 2026-10-05):** 32 IT classes, 76 tests (parameterized runs counted individually), all green
in isolation; about **7 minutes** wall-clock for the full suite on a developer workstation. The slowest classes are the two queue-saturation ITs (~45-50 s each), the fragmentation ITs
(~27 s each), `DegradedConnectIT` and `PoolBookkeepingIT` (~25 s each). Expect noisy SDK reconnect/discard warnings
in the console — they are the behavior under test, not failures.

Run only one suite at a time: all runs share `target/docker-it.properties` and the same container aliases, so two
concurrent runs (or a leftover one) make each other's toxics and stubs disappear mid-test. The tests are also
timing-based; heavy CPU load from other processes on the machine can make them fail spuriously.

## Test matrix

"×3" = parameterized over the discard modes `none` / `onconnectionloss` / `discard` (`DiscardMode`). Abbreviations:
**N** = `none`, **OCL** = `onconnectionloss`, **D** = `discard`. `discarded` = counted by `DiscardObserver`.

### Scenarios

Scenarios marked "×3" in the test-class column run once per discard mode; the others are policy-independent.

| # | Scenario / fault | Expected behavior | Test class(es) | Notes |
|---|---|---|---|---|
| 1a | Startup with the target unreachable, `startup.failbehavior=fail` | `Njams.start()` returns `false` and the SDK shuts down fully: instance inactive, no `Sender-Startup-*` / `Sender-Reconnector-*` / `Receiver-*` thread survives, and once the target is usable again nothing reconnects or sends on its own. HTTP additionally: `HEAD` → `404` ("no active dataprovider"). | `jms.StartupOutageIT`, `http.HttpStartupOutageIT` | Policy-independent. JMS observes the broker's connection count (Jolokia) staying unchanged, HTTP the WireMock journal staying unchanged. The `reconnect` counterpart is scenario 1b. |
| 1b | Startup with the target unreachable, `startup.failbehavior=reconnect` (short `connect.timeout`) | `start()` returns `true`, the SDK is initialized, a `Sender-Reconnector-*` thread reconnects in the background on its own and ends once the target is back; from then on the sender behaves as for a later connection problem. **N:** nothing discarded; jobs driven while down are held and delivered after the reconnect (HTTP: the startup project message too). **OCL / D:** the startup project message and every job driven while down are discarded (counted) and never delivered later. **All modes:** jobs driven after the reconnect are delivered. | `jms.StartupReconnectIT`, `http.HttpStartupReconnectIT` (×3) | Common logic in `support.StartupReconnectScenario`. Dropping the startup project message under **OCL / D** is expected and no worse than any other discarded message: the server requests a resend if it misses it. Whether it reached the server is observed (console line `[startup-reconnect] ...`) but not asserted. |
| 2 | Connection lost while jobs are running, then restored (Toxiproxy `timeout`) | **N:** in-flight message held and resent after reconnect, new sends block; every job arrives, nothing discarded. **OCL / D:** the failed message is retired and dropped while the group reconnects, new sends during the outage are dropped; delivered ⊆ driven, undelivered ≤ discarded, ≥ 1 discard, not every outage job survives. **D** additionally skips the quick-retry smoothing. | `jms.MidProcessingOutageRecoveryIT`, `http.HttpMidProcessingOutageRecoveryIT` (×3) | Duplicate bound: 2 per `logId` (JMS), 5 (HTTP, see caveat above). Under **D**, queue-full discards can also drop jobs outside the outage, so the pre-outage batch is not assumed complete. |
| 3 | Degraded (slow) connect while established traffic runs | A slow-but-reachable broker does not stall unrelated pool `acquire()`/`release()` calls: concurrent traffic is not serialized behind one slow connect. | `jms.DegradedConnectIT` | Pinned to `none`; the pool is not reconnecting, so the policy does not apply. |
| 4 | `Njams.stop()` during an outage/reconnect | `stop()` returns promptly (< 10 s) and the job-driving thread is not left stuck in every mode — under **N** including callers blocked on a full queue. | `jms.ShutdownDuringOutageIT`, `http.HttpShutdownDuringOutageIT` (×3) | The test timeout is the primary assertion. |
| 5 | Repeated connection flapping (10 cycles, concurrent submitters) | No reconnect-thread pile-up: growth bounded (N: up to the sender-pool maximum; OCL/D: +2), at most two `*Reconnector*` threads alive. **N:** nothing discarded; **OCL / D:** ≥ 1 discard. | `jms.RepeatedFlapIT` (×3) | Thread names are compared with digits masked. Two warm-up flaps establish the baseline. |
| 6 | Large (fragmented) message while the connection is down | **N:** complete chunk set, nothing discarded. **OCL / D:** ≥ 1 discard, and the chunk set is either complete or empty — never a partial set (no fragment gap). | `jms.FragmentationUnderOutageIT`, `http.HttpFragmentationUnderOutageIT` (×3) | 200 KB payload against a clamped ~10 KB chunk size; the outage is armed before the driver starts. |
| 7 | Server rejects the message (`413`) | One attempt, then dropped, connection untouched (no reconnect / HEAD), exactly one discard — **same in all modes**; the next job is delivered on the same connection. | `http.HttpRejectAndCongestionIT#rejectedMessageIsDiscardedWithoutAffectingTheConnection` (×3) | Classification: only `413` is "rejected". |
| 8 | Server congestion (`429`) | **N / OCL:** retried locally on the same connection until the target recovers; no discard, no reconnect. **D:** one attempt in < 3 s, then dropped (exactly one discard), never reconnects. | `http.HttpRejectAndCongestionIT#congestionIsRetriedOrDiscardedAccordingToTheDiscardMode` (×3) | Classification: only `429` is "congestion". |
| 9a | Application-level connection problem (`503`) | Classified as a *connection problem*, not congestion: a reconnect (`HEAD`) happens. **N:** delivered after recovery, no discard. **OCL / D:** delivered or counted as discarded (either is valid — the group may be reconnecting or a fresh sender may re-send while the stub recovers). | `http.HttpRejectAndCongestionIT#applicationLevel503IsTreatedAsConnectionProblemNotCongestion` (×3) | Pins the classification that `502`/`503`/`504` are connection problems, not congestion. |
| 9b | Transport-level connection problem (Toxiproxy down in front of WireMock) | Same retire/reconnect path as 9a. **N:** delivered after recovery. **OCL / D:** ≥ 1 discard; the dropped job is POSTed at most 4 times. | `http.HttpConnectionProblemIT` (×3) | Warm-up job is delivered before the outage. |
| 10 | Dispatch-queue saturation (1 sender thread, 2-slot queue, 8 jobs) with a **slow** (`latency` toxic) or **down** (`timeout` toxic) transport | **Slow:** N and OCL block the submitter and deliver everything, nothing discarded; D never blocks and drops what does not fit (counted). **Down:** N blocks until recovery and delivers everything; OCL and D never block and drop (counted). | `jms.QueueSaturationIT`, `http.HttpQueueSaturationIT` (each mode × slow/down = 6) | Common logic in `support.QueueSaturationScenario`. |

### Receiver scenarios

The receiver ITs run with discard policy `none` (delivery is not the subject), and every test starts from an empty commands topic (`DockerEnvironment` waits for stale consumers to be reaped). JMS commands are published straight
onto the broker's commands topic; HTTP commands are served by a WireMock SSE stub and answered by the receiver with a
reply `POST` that is observed in the WireMock journal.

| # | Scenario / fault | Expected behavior | Test class(es) | Notes |
|---|---|---|---|---|
| R1 | Receiver connection lost at runtime, then restored | The receiver reconnects on its own and answers commands again. JMS: the consumer on the commands topic drops to 0 during the outage and is exactly 1 afterwards; HTTP: the subscribe `GET` count increases and a command with a *new* id, served only after the outage, is answered. | `jms.ReceiverReconnectIT`, `http.HttpReceiverReconnectIT#receiverResubscribesAfterConnectionLossAndAnswersAgain` | HTTP: the outage is a `reset_peer` toxic; whether the SSE client library or the SDK's own reconnect loop re-establishes the stream is not observable, so this proves recovery, not the layer. | |
| R2 | Startup with the target unreachable, `startup.failbehavior=reconnect` | `start()` returns `true`; the receiver connects in the background once the target is back and answers commands. | `jms.ReceiverStartupOutageIT`, `http.HttpReceiverReconnectIT#receiverConnectsInTheBackgroundOnceTheServerIsBack` | The receiver's connect starts in the `Njams` constructor, so the outage must exist before it. |
| R3 | Command round trip | A command is handled and answered. | `jms.JmsCommandRoundTripIT`, `http.HttpReceiverCommandRoundTripIT` | JMS topic is non-durable: the client retries until the consumer is attached. |
| R4 | `Njams.stop()` while the receiver is reconnecting | `stop()` returns in < 10 s; no `Receiver-*` thread survives; JMS: no consumer stays attached. | `jms.ReceiverShutdownDuringReconnectIT`, `http.HttpReceiverShutdownDuringReconnectIT` | JMS waits for the consumer drop and the reconnector thread; HTTP can only wait out the outage window. |
| R5 | Sender group recovered from an outage | The receiver ends up with exactly one connection (cycled, not duplicated) and keeps answering; no recovery-cycle thread is left. | `jms.ReceiverAfterSenderOutageIT` | JMS only: the HTTP sender keeps no connection, so its group never recovers without driven jobs. The recovery cycle is transient, so the test asserts its end, not that it ran. |
| R6 | Several clients sharing one JMS receiver | One shared consumer; commands are routed by exact path (an ancestor path gets result code 99); stopping one instance keeps the receiver, the last stop releases the consumer; a restart builds a fresh receiver. | `jms.SharedReceiverIT` | |
| R7 | Repeated start/stop (10 cycles) | No `Receiver-*` thread and no consumer on the commands topic left behind. | `jms.ReceiverStartStopLeakIT` | HTTP counterpart: the receiver-threads row below. |

### Resource / state regression checks

| Check | Expected behavior | Test class(es) | Notes |
|---|---|---|---|
| Sender-pool bookkeeping vs. the broker | Pooled-sender count stays ≤ `maxSenderThreads` under concurrent load and agrees with the broker's own connection count (Jolokia). | `jms.PoolBookkeepingIT` | Pinned to `none` (delivery is not the subject). |
| Receiver threads across start/stop | 10 start/stop cycles leave no `Receiver-*` threads running. | `http.HttpRepeatedStartStopLeakIT#repeatedStartStopDoesNotLeaveReceiverThreadsRunning` | |
| Listener registrations on a shared sender group | `SenderRecoveryListener` count returns to 0 after all instances stop. `SenderExceptionListener` registrations made by external client code are not tied to an instance's lifecycle: the count stays at 10 after stop and returns to 0 once each is removed via `removeSenderExceptionListener`. | `http.HttpRepeatedStartStopLeakIT#listenerRegistrationsOnASharedGroupReturnToZeroOnceRemovedOrTheirClientsStopped` | |

### Environment smoke checks

| Check | Test class |
|---|---|
| ActiveMQ reachable, one message round-trips | `jms.JmsBrokerSmokeIT` |
| JMS reachable through the proxy; a `down` toxic blocks it | `jms.JmsThroughProxySmokeIT` |
| HTTP `POST` through the proxy reaches the WireMock stub | `http.HttpThroughProxySmokeIT` |
| Dynamic ports, per-proxy toxic isolation, reset between tests | `support.DockerEnvironmentSmokeIT` |
