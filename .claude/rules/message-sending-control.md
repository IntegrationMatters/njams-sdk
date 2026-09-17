---
globs: "njams-sdk/src/main/java/**"
---

# Message Sending Control (nJAMS Server Send Cadence)

A documented invariant governs when messages are sent to nJAMS Server, and it **must be preserved** by
any change to `logmessage/`, `communication/`, or `model/` (or any setting that influences flush cadence):

- **Project messages** are sent once at `Njams` startup, plus any additional incremental project
  messages a client explicitly sends for supplemental resources. The server may request a resend
  (`SEND_PROJECTMESSAGE`, handled in `NjamsCommands`), which sends the current full merged project
  message once per request.
- **Log messages are identified by `logId`** — one per job. A second message with the same `logId`
  updates that message on the server rather than creating a new one.
- **nJAMS Server tolerates high-frequency log messages across different jobs (`logId`s), but not
  high-frequency updates to the same job/`logId`.** This is why send cadence is SDK-controlled, not
  client-controlled: `JobFlusher`/`LogMessageFlushTask` decide when a job's accumulated data is sent —
  typically once, at job end, for a short-running job; an intermediate flush only when the job's
  estimated size or age exceeds `njams.sdk.flushsize`/`njams.sdk.flush_interval`.
- **`Job.flush()`/`Job.timerFlush(...)` are deprecated for removal (SDK-448, `forRemoval = true`) and
  must not gain new callers or be un-deprecated**, even though they remain technically callable until
  removed. They exist only for the SDK's own internal use (`LogMessageFlushTask`, `end()`).
- **Do not add any new public/protected hook — method, setting, or SPI extension point — that lets a
  client force or increase per-`logId` send frequency.** If a feature request seems to need this, raise
  it with the user before designing around it — the correct answer is almost always no, per the
  invariant above.
- Any change to `AbstractSender` implementations, `JobFlusher`, or `LogMessageFlushTask` must preserve
  this cadence contract. See `job-thread-safety.md` (same classes, concurrency contract) and
  `runtime-performance-hotpath.md` (same classes, performance contract).

## Public-Facing Documentation

The client-facing rationale for this invariant (why there is no supported way to force an intermediate
flush) is published in two places that must be kept consistent with any future change here:

- Javadoc on `Job` (class-level), `JobImpl.flush()`/`timerFlush()` (`@deprecated` text), and
  `AbstractSender` (class-level) in `njams-sdk/src/main/java/com/im/njams/sdk/`.
- `wiki/FAQ.md`, section "How does the SDK control sending messages to nJAMS Server" — see
  `wiki-drafts.md` for the editing workflow.
