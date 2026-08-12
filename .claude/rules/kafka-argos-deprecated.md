---
globs: "njams-sdk/src/main/java/com/im/njams/sdk/communication/kafka/**, njams-sdk/src/main/java/com/im/njams/sdk/argos/**"
---

# Kafka Transport & Argos Metrics — Deprecated

`communication/kafka/` and `argos/` (including `argos/jvm/`) are treated as virtually deprecated. This is a standing policy, not a one-off judgment call for a specific past task:

- **No new feature investment.** Do not add new capabilities to the Kafka transport or the Argos metrics collectors.
- **Tests are best-effort / optional.** These packages are out of scope for the usual test-coverage requirements (see `testing-conventions.md`). Do not block work on adding or fixing tests here unless specifically asked to.
- Bug fixes and safe modifications to existing behavior are still permitted when explicitly requested, following the usual `njams-bug-fix` / `njams-safe-modification` workflow (see `development-workflow-skills.md`) — this policy only relaxes new-feature and test-coverage expectations, it does not license careless changes.
- Existing public API in these areas is still bound by the immutability rule in `public-api-design.md` — deprecated status here refers to investment priority, not license to break compatibility silently.
