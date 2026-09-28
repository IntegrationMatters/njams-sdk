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
- **This module detects regressions; it does not diagnose or fix them.** If a scenario in this module surfaces
  a real defect (e.g. a resource-leak or duplicate-delivery finding), that is a separate Jira ticket with its own
  focused JUnit/mocked-IT reproduction — do not attempt to root-cause or fix the underlying SDK behavior as part
  of work in this module.
- **Keep the client-side driving logic trivial.** The process/activity model is intentionally fixed and simple
  (see the spec's harness section) so it cannot itself be a source of test flakiness — do not add branching,
  groups, or scenario-specific client logic beyond the three documented knobs.
