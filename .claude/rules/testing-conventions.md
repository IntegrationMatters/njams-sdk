---
globs: "njams-sdk/src/test/**"
---

# Testing

Tests use JUnit 4 + Mockito. The `AbstractTest` base class provides common test setup. Transport-specific tests (JMS)
may require mocking of connection infrastructure. Kafka and Argos/JVM metrics tests are out of scope — see
`kafka-argos-deprecated.md`.

Test reports are generated in `njams-sdk/target/surefire-reports/`.

Code quality and documentation rules (`code-quality-general.md`, `public-api-design.md`) do not apply to test code.

## Test support tooling

Prefer the existing in-repo test infrastructure over reinventing it when a test needs a working `Njams` instance or
needs to inspect what would be sent:

- **No-op / capturing sender (`TestSender`).** `com.im.njams.sdk.communication.TestSender` (in `src/test`) is a
  transport that is registered via SPI and selected by `TestSender.getSettings()` (it sets
  `PROPERTY_COMMUNICATION=TEST_COMMUNICATION` and the in-memory configuration provider). By default it **discards** all
  messages, so a real `Njams` can be started and exercised with no server and no real I/O — this is exactly what
  `AbstractTest` does. When a test needs to assert on the outbound messages, inject a delegate with
  `TestSender.setSenderMock(AbstractSender)` (e.g. a Mockito mock or spy) and capture the `CommonMessage` passed to
  `send(...)`. Reach for a real `Njams` over the no-op sender when the facet under test is tightly coupled to `Njams`/
  `ProcessModel`; use plain mocks for isolated collaborators.
- **Message dump for debugging (`njams.sdk.debug.messagedir`).** Setting `NjamsSettings.PROPERTY_DEBUG_MESSAGE_DIR`
  makes `MessageDebugDumper` write every outbound message (project/log/trace) as an individual `.json` file (headers +
  body) under a per-run timestamped subdirectory. Useful when diagnosing what the SDK actually emits without standing up
  a server. It is inert when the setting is absent — do not enable it in normal unit tests; use a captured/mocked
  `TestSender` for assertions instead.

## Test Isolation for Shared State

A test must never assume the state a previous test left behind. If a test observes or depends on shared state —
a singleton, a JVM-wide static field, a shared fixture — it must establish that state itself in `@Before` (or
equivalent setup) rather than relying on the default/initial condition or on what ran earlier in the suite, and
restore it in `@After`. Test execution order must never be load-bearing for correctness.

In practice this means: install a fresh instance/value before each test rather than reading whatever is
currently installed, and assert on absolute values afterward rather than computing a before/after delta. A
delta computation is a symptom of not trusting the starting state — fixing the setup is the correct response,
not compensating for it in the assertion.

## Test Isolation for Shared State

A test must never assume the state a previous test left behind. If a test observes or depends on shared state —
a singleton, a JVM-wide static field, a shared fixture — it must establish that state itself in `@Before` (or
equivalent setup) rather than relying on the default/initial condition or on what ran earlier in the suite, and
restore it in `@After`. Test execution order must never be load-bearing for correctness.

In practice this means: install a fresh instance/value before each test rather than reading whatever is
currently installed, and assert on absolute values afterward rather than computing a before/after delta. A
delta computation is a symptom of not trusting the starting state — fixing the setup is the correct response,
not compensating for it in the assertion.

## Coverage

Cover all new/changed code with tests. For modifications to existing code, invoke the `njams-safe-modification` skill
FIRST to establish baseline coverage (see `development-workflow-skills.md`).

## Bug fixes

**Existing test cases must never be modified without explicit user permission** — if a fix causes a test to fail, the
fix is wrong. If there is a genuine reason to believe a test is incorrect, explain the specific conflict and ask the
user before changing anything. See `development-workflow-skills.md` for the `njams-bug-fix` skill this workflow
requires.

When code relocates without behavior change, move its tests as-is with identical assertions — that parity is the proof
the move preserved behavior.
