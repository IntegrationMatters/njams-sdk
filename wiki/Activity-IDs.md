# Activity IDs

When you build a `ProcessModel`, every step is an `ActivityModel` (or `GroupModel` /
`SubProcessActivityModel`) created with three author-supplied values: an **id**, a **name**, and a
**type**:

```java
ProcessModel process = njams.createProcess(processPath);
ActivityModel receive = process.createActivity("receiveOrder", "Receive Order", "Start");
receive.transitionTo("validateOrder", "Validate Order", "Task")
       .transitionTo("persistOrder", "Persist Order", "Task");
```

The **id** (first argument) is the activity's stable identity inside the process model. The SDK never
generates it for you — you choose it, and you are responsible for choosing it well. This page explains
what the id is used for, why the choice matters, and how to pick a good one.

> **id vs. name.** The **name** is the human-facing label shown in the nJAMS UI; it may change freely.
> The **id** is the machine identity used for matching and linking; it must stay stable. Do not conflate
> the two — a descriptive label belongs in `name`, not in `id`.

## What the activity ID is used for

The activity id is not just a display detail. It is the key the SDK and the nJAMS server use to identify
an activity across several mechanisms:

- **Identity within the process model.** The id is the map key under which `ProcessModel` stores the
  activity, and it backs `ActivityModel`'s `equals`/`hashCode`. Two activities in the same process model
  must not share an id.
- **Transitions.** A transition's id is derived from the ids of the activities it connects, as
  `fromId::toId`. The diagram edges and the predecessor links in every log message are built from these.
- **Runtime instance ids.** Each time the activity executes, the running `Activity` instance gets an
  instance id derived from the model id (`modelId$sequence`). These instance ids appear in the log
  message and in every predecessor link that points at the activity.
- **Activity configuration matching (tracepoints and extracts).** This is the most consequential use.
  Tracepoints and per-activity data extracts configured in the nJAMS UI are persisted **keyed by process
  path + activity id**. At runtime the SDK looks up the configuration for a running activity by its model
  id. If the id does not match the id the configuration was stored against, the lookup finds nothing and
  **no input/output data is captured** — silently, with no error.
- **Process-diagram (SVG) identity.** The generated SVG embeds the activity ids. The nJAMS server detects
  when a process's SVG is unchanged and skips storing it redundantly, but only when repeated builds produce
  an identical diagram. Ids that vary between runs make every build's SVG differ and force the server to
  store a fresh copy each time.

## Why a stable, well-chosen ID is relevant

Because configuration is keyed by `process path + activity id`, the id must denote *the same logical
activity every time the process model is built*. The process path is normally stable. The activity id is
only as stable as you make it.

If activity ids are **not reproducible across restarts**, persisted tracepoints and extracts become
orphaned: they still reference the old ids, the rebuilt model uses new ids, and the configuration matches
nothing. The visible symptom is that tracing or data extraction *stops working after a restart* even
though the process, the tracepoint, and the extract are all unchanged.

A typical way this goes wrong is deriving ids from a value that varies between runs — for instance a
counter that increments as the process model is assembled. The same step then gets one id in one session
and a different id after a restart, because the counter depends on how many other processes were built
first and in which order. Every persisted tracepoint silently breaks. The remedy is always the same: make
the id **derive from a stable property of the step**, not from anything that varies between runs.

## How to pick a good activity ID

Choose ids that satisfy all of the following:

| Property                | Guidance                                                                                                                                                                                                                                |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Reproducible**        | The same logical step must get the same id on every build — independent of JVM/context restart, of how many other processes exist, of load order, and of wall-clock time. Derive it from a stable, authored property of the step (a design-time step id, a node name, a position within the process). |
| **Unique**              | No two activities in the same process model may share an id. Uniqueness must also hold within the *virtually-joined* model — see Constraints below.                                                                                       |
| **Compact**             | The id appears many times in every message — once per activity, embedded in each instance id, and twice in every predecessor link (`fromId::toId`). Keep ids short to keep messages small. Put descriptive text in `name`, not in `id`. |
| **Stable across versions** | Where a step is logically "the same" across releases of the process, keep its id the same, so existing tracepoints and extracts survive the upgrade.                                                                                  |

**Do** derive ids from something the author controls and that is intrinsic to the step:

```java
// Good: id derived from a stable, design-time step identifier.
process.createActivity(step.getDesignId(), step.getLabel(), step.getKind());
```

**Avoid** deriving ids from anything that changes between runs:

```java
// Bad: a counter — not reproducible across restarts; its value depends on build order.
process.createActivity("node" + GLOBAL_COUNTER.incrementAndGet(), label, kind);

// Bad: UUIDs, identity hash codes, timestamps — all vary run-to-run.
process.createActivity(UUID.randomUUID().toString(), label, kind);
```

## Constraints

- **Unique within the process model.** Adding a second activity with an existing id, or creating a
  transition whose source and destination ids are equal, is rejected (`transitionTo` requires the
  destination id to differ from the source).
- **Unique within the virtually-joined model.** When an inline sub-process runs in the same job as its
  caller — its activities joined into the caller's job rather than tracked as a separate job — the
  activities of both share a single message's id namespace. Their ids must not collide.
- **Non-null.** The id is used as a map key and must not be `null`.
- **Treat the id as part of your client's persisted contract.** Because nJAMS stores configuration against
  it, changing the id of an existing activity orphans any tracepoint or extract configured for it; the
  configuration must be re-created against the new id.
