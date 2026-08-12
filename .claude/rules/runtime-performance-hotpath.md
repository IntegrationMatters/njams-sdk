---
globs: "njams-sdk/src/main/java/com/im/njams/sdk/logmessage/**, njams-sdk/src/main/java/com/im/njams/sdk/communication/**, njams-sdk/src/main/java/com/im/njams/sdk/argos/**"
---

# Performance Requirements — Runtime Monitoring Path

Memory consumption and CPU usage must be kept small throughout the SDK. This is especially critical in the **runtime
monitoring path** — the code executed for every job and activity during process execution — where overhead accumulates
directly on the instrumented application.

The runtime monitoring path includes:

- `logmessage/` — `Job`, `Activity`, `Group` lifecycle and data collection
- `communication/` — message batching, flushing, and dispatch
- `argos/` — metrics collection and transmission

## Rules for performance-sensitive code

- **Avoid unnecessary object allocation.** Reuse objects, use primitives where possible, prefer lazy initialisation over
  eager construction.
- **Avoid reflection, dynamic proxies, and classpath scanning** in hot paths.
- **Do not add synchronisation overhead** beyond what is required for correctness.
- **Prefer simple data structures.** Avoid heavy frameworks or abstractions where a straightforward implementation
  suffices.
- **Do not perform I/O or blocking operations** on threads that process monitoring data.
- **Never read settings live on the hot path — snapshot once.** `HierarchicalSettings.getProperty` (and any
  `ClientSettings`/`ReadOnlyClientSettings` read) is a linear scan over the ordered layers: it allocates an iterator,
  does a double lookup on the hitting layer, scans every layer on a miss, and for system/environment layers adds a
  filter predicate, key transform, and `System.getenv()`/`System.getProperties()` access. This cost stays off the
  runtime path only because callers snapshot: `JobSettings.of(settings)` caches an immutable per-client snapshot (the
  layer scan runs once, at first job), and senders read settings at init, not in `send(...)`. This is an unenforced
  invariant — `HierarchicalSettings` has no per-key cache. In `logmessage/`, `communication/` send/flush, and `argos/`,
  always read from a snapshot taken once (the `JobSettings` pattern); never call `njams.getSettings().getX(...)` per
  job, activity, or message. Flag any live per-execution settings read in review.

When implementing new functionality that touches the runtime monitoring path, consider memory and CPU impact explicitly.
If a design choice has a meaningful performance trade-off, raise it before implementing.

Note: `communication/kafka/` and `argos/` are deprecated (see `kafka-argos-deprecated.md`) — these rules still apply to
any code still running there, but performance investment should not be prioritized on this path going forward.
