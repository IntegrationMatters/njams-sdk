---
globs: "njams-sdk/src/main/java/com/im/njams/sdk/logmessage/**"
---

# Job / Activity / Group Thread-Safety Contract (since 6.0.0, SDK-457)

A documented thread-safety contract holds for the runtime classes and **must be preserved** by any change to `logmessage/`:

- **A `Job` is the shared concurrency unit.** Multiple threads may concurrently create activities/groups in one job and record into their *own* instances. The job-level state that recording updates as a side effect — status/maximum severity, the `JobTracing` flags, the `JobFlusher` estimated size, attributes, the captured activity error, and `JobMetadata` — is synchronized internally (the single job lock is `JobImpl.activitiesLock`, also used by `JobFlusher`; independent flags use `volatile`). Keep new shared job-level state safe the same way.
- **An `Activity`/`Group` instance is thread-confined.** One instance is used by one thread; the SDK does **not** synchronize mutation of a single instance (data setters, `addPredecessor`, `Group.iterate()`). Do not add per-instance locking — it adds hot-path cost for a case that does not occur.
- When changing this contract or anything it covers, update the Javadoc on the `Job`/`Activity`/`Group` interfaces and the "Can a single job be used from multiple threads" FAQ entry to match.
