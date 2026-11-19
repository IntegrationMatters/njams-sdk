# Communication Resilience Test Module

Tickets: SDK-483, SDK-485

`njams-sdk-communication-it` drives a real `Njams` instance against a real ActiveMQ broker and a WireMock stand-in for
the nJAMS Server HTTP API, through Toxiproxy for network fault injection. It verifies documented sender/receiver
behavior under real timing, real broker connection semantics and real HTTP status codes, which the mocked tests in
`njams-sdk` cannot reproduce.

Where to find what this spec does not repeat:

1. `njams-sdk-communication-it/README.md`: architecture sketch, containers and ports, how to run, expected duration,
   and the full scenario catalog with expected behavior per discard mode (scenarios 1a–10, receiver scenarios
   R1–R7, resource checks, smoke checks). The catalog lives there and only there.
2. `.claude/rules/communication-it-module.md`: scope rules (manual profile only, Kafka out of scope, detects and
   does not fix, stable catalog, fixed client model, SDK-owned threads only in leak checks).
3. `.claude/rules/message-sending-control.md`: the send-cadence invariant the harness accounting depends on.
4. `.claude/rules/communication-layer.md`: transport relevance and the two JMS namespace trees.

## Build Wiring

1. The module is listed only in the root `pom.xml` profile `docker-it` (`activeByDefault=false`). Without `-Pdocker-it`
   it is not part of the reactor at all, so no other build needs Docker.
2. `maven-failsafe-plugin` comes from the root `pom.xml` build plugins, with `skipITs` routed through
   `skip.integration.tests`; the module sets that property to `false`. The module pom adds only fabric8
   `docker-maven-plugin`, bound to `pre-integration-test` (start) and `post-integration-test` (stop).
3. Host ports are dynamic (`+name:port` mappings) and written to `target/docker-it.properties`; `DockerEnvironment`
   is the only reader. Never hardcode a container port in a test (`DockerEnvironmentSmokeIT` guards this).
4. The module consumes the `njams-sdk` jar and its test-jar. The test-jar registers Mockito-based fixtures as
   service providers, so `mockito-core` must stay a test dependency even though no IT uses Mockito.

## Message Harness

1. `harness.FixedProcessModel.build(Njams)` creates the one process shape every scenario drives: one starter
   activity, no transitions, no groups.
2. `harness.MessageDriver.run(model, count, payloadBytes, concurrency)` is the only way scenarios produce traffic.
   It returns the SDK-assigned `logId` of every driven job, which is the key for all delivery accounting.
   - *count*: jobs, each started and ended at once.
   - *size*: business-data payload bytes; large values trigger fragmentation.
   - *concurrency*: number of submitting threads, used to provoke pool and reconnect races, not to speed up tests.
3. **Accounting invariant:** a short-running job is flushed exactly once, at `end()`, so one driven job is one
   outbound log message. This holds only as long as the send-cadence invariant in `message-sending-control.md`
   holds; a change to `JobFlusher`/`LogMessageFlushTask` cadence breaks every count assertion here.
4. The project message sent at `Njams.start()` is a fixed background event outside the driven accounting. It is
   observed but never asserted in the startup-reconnect scenario, because dropping it under `onconnectionloss` /
   `discard` is expected (the server requests a resend).
5. `MessageDriverTest` is the module's only plain unit test; it keeps the harness itself correct.

## Discard Modes and Delivery Accounting

1. `support.DiscardMode` (`NONE`, `ON_CONNECTION_LOSS`, `DISCARD`) parameterizes every scenario whose outcome depends
   on the discard policy. Scenarios that are policy-independent by design (startup with `fail`, degraded connect,
   pool bookkeeping, receiver scenarios) do not repeat per mode; those not about delivery pin the policy to `none`
   through `DockerEnvironment.disableMessageDiscarding`, so a small dispatch queue blocks instead of dropping.
2. `support.DeliveryAssertions.assertOutcome` is the shared outcome rule:
   - nothing is delivered that was not driven;
   - no `logId` is delivered more often than the scenario's bound;
   - `none`: every driven job is delivered and the discard count is 0;
   - other modes: `undelivered <= discarded`.
3. **Delivery is at-least-once, not exactly-once.** The per-`logId` bound states how many legitimate attempts can
   reach the target. JMS: one attempt plus one ambiguous-outcome retry. HTTP: the initial attempt, the bounded local
   smoothing retries of `AbstractSender`, and one resend after reconnect, because each of them can reach WireMock
   while the toxic suppresses only the response. A bound is derived from the SDK's retry behavior; if that changes,
   recompute the bound instead of loosening it blindly.
4. Discards are counted by `com.im.njams.sdk.communication.CountingDiscardMonitor`, installed and always restored by
   `support.DiscardObserver`.

## SDK Access Seams

The ITs need two package-private SDK hooks. Both are reached through classes that live in the SDK's own packages
inside this module's test tree, never by widening SDK visibility:

1. `com.im.njams.sdk.SenderProbe.of(Njams)`: the instance's `NjamsSender` (package-private `Njams.sender()`).
2. `com.im.njams.sdk.communication.CountingDiscardMonitor`: uses the package-private `DiscardMonitor.setInstance`
   test seam.

A new need for SDK internals follows the same pattern; it never justifies a `public` member in `njams-sdk`.

## Fault Injection

1. Toxiproxy proxies `jms` and `http` sit between the SDK and the broker / WireMock; the SDK under test always
   connects through them, while verification (queue draining, WireMock journal, Jolokia) bypasses them.
2. **Toxics apply to every open link.** Toxiproxy cannot make only new connections slow. The degraded-connect
   scenario (`jms.DegradedConnectIT`) therefore applies `latency` to all traffic and asserts that concurrent pool
   operations are not serialized behind one slow connect, not that established traffic is unaffected. Its latency
   stays far below ActiveMQ's wire-format negotiation timeout; above it (15 s was measured) the connect fails
   outright instead of running slowly.
3. **`timeout` (connection down) blocks only the response direction.** An HTTP request the client sees as failed may
   still have reached WireMock. This is why delivery is at-least-once (above) and why the HTTP fragmentation
   scenario asserts a contiguous chunk prefix, while JMS asserts complete-or-empty.
4. `DockerEnvironment.OUTAGE_MS` must outlast the sender's quick-retry window, otherwise local retries alone would
   save a message and the reconnect / discard-policy path under test would never run.
5. HTTP receiver outages use a `reset_peer` toxic; it proves recovery of the SSE stream, not which layer (SSE client
   library or the SDK's reconnect loop) re-established it.

## Test Isolation

`support.DockerEnvironment` is a JUnit `ExternalResource` used as `@Rule` by every IT. Before each test it:

1. removes all toxics, clears the WireMock request journal and resets WireMock to its file-backed default mappings;
   on-demand fault stubs are loaded per test from `src/test/resources/wiremock/on-demand/`;
2. once per JVM, checks with repeated probes that the `http` proxy reaches this run's WireMock and fails if a stale
   container set from an earlier, unclean run shares the network alias (it would silently absorb part of the
   traffic);
3. waits until the broker's commands topic has no consumers, because the broker reaps connections cut through the
   proxy only after its inactivity timeout, and a predecessor's consumer would distort receiver assertions.

The whole suite shares the containers and `target/docker-it.properties`; only one run may be active at a time.

## Transport-Specific Facts

1. The HTTP status classification is pinned by scenarios 7, 8 and 9a and must match `HttpSender.isCongestion` /
   `isMessageRejected`: only `429` is congestion, only `413` is a rejected message, everything else, including
   `502`/`503`/`504` and I/O failures, is a connection problem.
2. WireMock's default `POST` stub matches method and path only, so both JSON messages and `text/plain` fragment
   chunks are accepted.
3. The HTTP command stream is a WireMock SSE stub (`support.WireMockStubs`) using `chunkedDribbleDelay`, with the
   event followed by comment-line padding so that the first dribbled chunk already holds the complete event (an SSE
   event is dispatched only at its terminating blank line).
4. HTTP has no shareable receiver and its sender keeps no connection, so receiver scenarios that depend on a shared
   receiver (R6) or on a recovering sender group (R5) exist for JMS only.

## Smoke Tier

The `*SmokeIT` classes check the environment (containers reachable, toxics work and reset, SDK JMS factory SPI
resolves against the broker), not SDK resilience. A failing scenario with green smoke tests can be read as a real
finding rather than a broken fixture. They are not part of the scenario catalog.

## Rejected Alternatives

1. An exhaustive fault × policy × transport matrix: the catalog covers the mechanisms the SDK actually has;
   policy-independent behavior is not repeated per mode.

Running only on demand and keeping the process model fixed are scope rules in `communication-it-module.md`.
