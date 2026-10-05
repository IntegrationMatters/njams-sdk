---
globs: "njams-sdk-communication-it/**"
---

# Communication Resilience Test Module (`njams-sdk-communication-it`)

Before making any change under this module's path, read
`docs/superpowers/specs/2026-09-28-sdk-483-communication-it-module-design.md` — it defines the module's
structure, the fixed message-generation harness (count/size/concurrency knobs), the fault-injection scenario
catalog, and the stale-structure/leak regression checklist. Keep changes consistent with that design; if a
change would deviate from it, update the spec and confirm with the user rather than drifting silently.

- **This module is manual/on-demand, not part of the default build.** It must stay bound to the non-default
  `docker-it` Maven profile (fabric8 `docker-maven-plugin` + `maven-failsafe-plugin`). Never wire it into the
  default `mvn clean install` lifecycle.
- **Kafka is out of scope.** Treated as a noted future extension only — do not add Kafka containers or test
  scenarios here without the user explicitly asking for that extension.
- **This module's sole purpose is exercising documented behavior under virtually-real-life conditions — it does
  not diagnose, fix, or regression-test defects.** If a scenario here surfaces a real defect (e.g. a
  resource-leak or duplicate-delivery finding), that becomes a separate Jira ticket, fixed with the project's
  normal JUnit/mocked-IT TDD approach (`njams-bug-fix`) — and that fix's regression guard is added to
  `njams-sdk`'s own test suite, never to this module.
- **This module's scenario catalog is stable by default and does not grow in response to bug fixes.** Do not add
  a new scenario here just because a fix landed for something this suite found — the existing scenario already
  covers it once the fix ships. Add a new scenario only on a deliberate, separate decision that some
  transport behavior genuinely needs additional real-life verification.
- **Keep the client-side driving logic trivial.** The process/activity model is intentionally fixed and simple
  (see the spec's harness section) so it cannot itself be a source of test flakiness — do not add branching,
  groups, or scenario-specific client logic beyond the three documented knobs.
- **Leak checks cover SDK-owned threads only.** Thread-count and leak assertions must ignore threads owned by
  third-party libraries (e.g. `OkHttp*`, `ActiveMQ*` pool threads) — their pooling is not under the SDK's control.
  Only the SDK's own threads (`Sender-*`, `Receiver-*`, ...) must not leak.
