---
globs: "njams-sdk/src/main/java/com/im/njams/sdk/communication/**"
---

# Communication Layer

Transport is pluggable via `CommunicationFactory`. Built-in transports:
- **HTTP** (`communication/http/`) — Default, connects to nJAMS Server REST API
- **JMS** (`communication/jms/`) — ActiveMQ/JMS queue-based transport
- **Kafka** (`communication/kafka/`) — deprecated, see `kafka-argos-deprecated.md`

Each transport implements `AbstractSender` (outbound) and `Receiver` (inbound commands). The `communication/fragments/` package handles message chunking for payloads exceeding the flush size limit.

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

## API Contracts Inside This Package

This package is never Client Contract (client-facing) API — SDK users should never need to know or care which transport is active, how messages are structured on the wire, or how chunking works, and none of this must leak into the client-facing surface. But "not Client Contract" isn't the same as "not API at all": two other contracts from `public-api-design.md` live inside this package, and a fourth bucket genuinely isn't API.

**SPI Contract.** `AbstractSender`, `Receiver`, and `communication.jms.factory.JmsFactory` are the extension points a new-transport implementation subclasses or implements. Stable, requires confirmation to change. `AbstractSender`'s template methods that the SDK itself calls into (e.g. `doReconnect`) can't use the normal deprecate-and-delegate pattern — see `public-api-design.md`.

**Wire Contract.** `communication/fragments/` (message chunking) and `MessageHeaders`/properties implement the fixed contract with nJAMS Server, alongside the njams-messageformat types themselves. This is exactly the file set `message-format-changes.md` governs inside `communication/` — a change here goes through that rule's human-confirmation + `SER`-ticket gate, not the `breaking-change` label.

**Everything else** — connection handling, retry/reconnect internals not part of the SPI Contract, transport-specific plumbing — is genuinely Internal, regardless of Java visibility, and is free to change.

## Transport Independence

Business logic outside this package must not depend on a specific transport. All communication-specific code belongs here, under `communication/http`, `communication/jms`, or `communication/kafka` — never inline in `logmessage/` or `model/`.

## Performance

`communication/` is part of the runtime monitoring path — see `runtime-performance-hotpath.md`.
