---
globs: "njams-sdk/src/main/java/com/im/njams/sdk/communication/**"
---

# Communication Layer

Transport is pluggable via `CommunicationFactory`. Built-in transports:
- **HTTP** (`communication/http/`) — Default, connects to nJAMS Server REST API
- **JMS** (`communication/jms/`) — ActiveMQ/JMS queue-based transport, shipped for both the `jakarta.jms` and the `javax.jms` API (see "Two JMS Namespace Trees" below)
- **Kafka** (`communication/kafka/`) — deprecated, see `kafka-argos-deprecated.md`

Each transport implements `AbstractSender` (outbound) and `AbstractReceiver` (inbound commands). The `communication/fragments/` package handles message chunking for payloads exceeding the flush size limit.

The sender thread pool (`maxSenderThreads`, default 8) asynchronously dispatches messages. Messages are batched by flush size (`flushsize`, default 5MB) or flush interval (`flush_interval`, default 30s).

## Transport Relevance

Not all three built-in transports carry equal production weight — this should shape how much scrutiny
and testing effort a change gets:

- **JMS is the primary production transport.** Most production nJAMS deployments run on JMS. Changes to
  `communication/jms/` deserve the most scrutiny and the most thorough testing.
- **HTTP is production-ready, but only for small/low-throughput environments.** It lacks buffering, so
  it is primarily used for development and testing. In production it is viable only where job/log-message
  volume is low — few processes, executing rarely, not sending log messages at a high rate. Do not assume
  an HTTP change needs to handle the same throughput as JMS when weighing its risk or urgency.
- **Kafka is deprecated but still supported** — see `kafka-argos-deprecated.md` for the standing policy
  (no new feature investment, tests best-effort/optional).

When deciding how much test coverage or design care a transport-specific change needs, weight JMS
highest, HTTP moderate (scoped to low-throughput scenarios), and Kafka lowest per the existing
deprecation policy.

## Two JMS Namespace Trees

Since 6.1.0 (SDK-495) the JMS transport ships twice from one artifact, once per JMS API namespace:
`communication/jms/` is the current `jakarta.jms` tree, `communication/jms/javax/` the deprecated `javax.jms`
one. Namespace-free logic lives once, in `AbstractJmsSender`/`AbstractJmsReceiver`; everything that names a JMS
type is hand-copied per tree. Both JMS APIs are `provided`, so a consumer may have either, both, or neither.

Four properties of this arrangement are load-bearing — a change to the selection path must preserve all of them:

- **Provider names need not be unique.** Both trees' senders and receivers answer `getName()` → `"JMS"`.
  `CommunicationFactory` therefore collects *every* implementation whose name matches the configured
  communication type, not just the first one found.
- **Candidates are tried in services-file order.** The jakarta entries are listed first in
  `META-INF/services/com.im.njams.sdk.communication.AbstractSender` and `…AbstractReceiver`, which is what makes
  jakarta the one chosen when both would work. Reordering those files changes the selection. Senders use that
  order as-is; **receivers are grouped first** by whether they match the configured wish for a shared receiver
  (`CommunicationFactory.findReceiverCandidates`), and services-file order applies only *within* each group —
  jakarta still wins because it is first in both groups, but a change here must preserve the grouping too.
- **An implementation declines by throwing `TransportNotApplicableException` from `init`.** Any `init` failure
  makes the SDK skip to the next candidate; the marker type is what keeps the reporting honest — "not this
  namespace" instead of a real error. Only all candidates failing is an error, and that error names every cause.
- **`communication.jms.factory.JmsFactory` can never move.** Third-party `javax` factories register under that
  exact name, which is why the jakarta SPI interface had to take the common package as
  `communication.jms.JmsFactory` — see `public-api-design.md`.

The `javax` tree is deprecated: keep it working, but put new JMS behavior in the shared bases or the jakarta tree.
The broker-less harnesses under `njams-sdk-jms-namespace-tests/` prove the selection on a javax-only, a
jakarta-only, a neither-API and a mixed classpath, and run on every build.

## API Contracts Inside This Package

This package is never Client Contract (client-facing) API — SDK users should never need to know or care which transport is active, how messages are structured on the wire, or how chunking works, and none of this must leak into the client-facing surface. But "not Client Contract" isn't the same as "not API at all": two other contracts from `public-api-design.md` live inside this package, and a fourth bucket genuinely isn't API.

**SPI Contract.** `AbstractSender`, `AbstractReceiver`, `communication.jms.JmsFactory` and its deprecated predecessor `communication.jms.factory.JmsFactory` are the extension points a new-transport implementation subclasses or implements. Stable, requires confirmation to change. `AbstractSender`'s template methods that the SDK itself calls into (e.g. `doReconnect`) can't use the normal deprecate-and-delegate pattern — see `public-api-design.md`. Despite being technically public API, the practical audience for these types is closed to nJAMS developers — extending them (e.g. adding a new transport) requires a coordinated nJAMS Server-side change, so this is not a self-service extension point for general SDK client implementations.

**Wire Contract.** `communication/fragments/` (message chunking) and `MessageHeaders`/properties implement the fixed contract with nJAMS Server, alongside the njams-messageformat types themselves. This is exactly the file set `message-format-changes.md` governs inside `communication/` — a change here goes through that rule's human-confirmation + `SER`-ticket gate, not the `breaking-change` label.

**Everything else** — connection handling, retry/reconnect internals not part of the SPI Contract, transport-specific plumbing — is genuinely Internal, regardless of Java visibility, and is free to change.

## Transport Independence

Business logic outside this package must not depend on a specific transport. All communication-specific code belongs here, under `communication/http`, `communication/jms`, or `communication/kafka` — never inline in `logmessage/` or `model/`.

## Performance

`communication/` is part of the runtime monitoring path — see `runtime-performance-hotpath.md`.

## Ticket completion checks

Read by `njams-ticket-finish` before it proposes resolving a ticket (see the "Ticket completion checks" convention in `CLAUDE.md`).

- **Resilience test suite.** If the ticket's diff contains a **significant** change to this package — sender/receiver lifecycle, `NjamsSender`/`SenderPool`/`SenderConnector` reconnect or retry logic, discard-policy handling, fragmentation, or a transport's connect/send/close path (not comment, logging or pure rename changes) — **propose** running the Docker-based `njams-sdk-communication-it` suite before proposing to resolve the ticket. Do not propose it mid-work or after individual edits. Command: `mvn -Pdocker-it verify -pl njams-sdk-communication-it` (about 6 minutes, needs Docker; see that module's `README.md`). It is a proposal, never an automatic step: state the duration up front and let the user decide. Never run two suites at once (they share `target/docker-it.properties`). A failure there is a defect finding, handled as described in `communication-it-module.md` — do not adapt the ITs to make it pass.
