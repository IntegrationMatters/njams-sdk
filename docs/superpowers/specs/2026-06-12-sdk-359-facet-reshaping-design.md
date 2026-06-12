# SDK-359: Facet Re-shaping — `model()` consolidation — Design

**Ticket:** [SDK-359](https://salesfive.atlassian.net/browse/SDK-359) — Refactor Njams class
**Date:** 2026-06-12
**Status:** Draft for review

## Background

The facade refactoring of `Njams` (SDK-359, finalized commit `1893f1e0`) split the class
into nine `public final` facets reachable through no-prefix accessors (`njams.jobs()`,
`njams.metadata()`, …). This follow-up re-shapes two of those facets. Both are unreleased
(6.0.0 is still `-SNAPSHOT`), so the change is intra-SNAPSHOT churn, **not** a breaking
change, and the ticket does **not** carry the `breaking-change` label.

## Goal

Give the process-model definition layer a single, well-named home. Rename the
`processes()` accessor to **`model()`** and let it own everything that defines the process
picture announced to the nJAMS server:

- **process models** — `create / add / get / has / getAll`
- **images** — `addImage`
- **taxonomy** — `setTreeElementType` (+ internal tree building)
- **global variables** — `getGlobalVariables / addGlobalVariables / getGlobalVariablesPattern / setGlobalVariablesPattern`

The only concern that physically moves is **global variables**, today owned by
`NjamsMetadata`. Taxonomy and images already live in today's `NjamsProcesses`.

## Organizing principle

The four responsibilities above are exactly the *client-side content of the
`ProjectMessage`* (`processes`, `images`, `treeElements`, `globalVariables` +
`globalVariablesPattern`). Grouping them makes `model()` cohesive: it owns "what defines
the process model picture," while `metadata()` shrinks to pure client *identity* (path,
category, client/SDK/runtime versions, machine, session id, startup banner). After the
move `metadata()` has no mutable wire-content except `runtimeVersion`.

## New layout

| Accessor | Class | Owns | Change |
|---|---|---|---|
| **`model()`** | `NjamsModel` (rename of `NjamsProcesses`) | process models, images, taxonomy, **global variables**, layouter, diagram factory, `send()` / `announce()` | renamed; global vars moved in; internals extracted |
| **`metadata()`** | `NjamsMetadata` | path, category, versions, machine, session id, startup banner | global vars moved **out** |
| `features()` / `jobs()` / `serializers()` / `replay()` / `commands()` / `argos()` / `configuration()` | unchanged | — | none |

### Internal collaborators (package-private, no accessor, not public API)

Consolidating four responsibilities under one facet would make `NjamsModel` ~590 lines and
~23 public methods — nearly 3× the next facet (see Size evaluation). To keep the facet a
thin public surface and to fully contain the wire-format types (`TreeElement`,
`ProjectMessage`) inside internal code, `NjamsModel` delegates to three package-private
collaborators in `com.im.njams.sdk`:

- **`TaxonomyTree`** — owns the `List<TreeElement>` and all tree operations
  (`createTreeElements`, `setType`, `markStarters`, `snapshot`, the standalone element list
  for `announce`, and the "is this default type used?" check). Confines the `TreeElement`
  wire type.
- **`ProjectMessageAssembler`** — builds the `ProjectMessage`: the header from
  `metadata` / `features` / `configuration`, then fills processes, images, global variables
  + pattern, and tree elements. Confines the `ProjectMessage` wire type and the
  `prepareProjectMessage` / `addDefaultImagesIfNeededAndAbsent` logic.
- **`GlobalVariables`** — value holder for the `Map<String,String>` + pattern `String` +
  pattern validation (the `NAMED_GROUP_DECLARATION` regex and `validate…`). Travels as one
  unit out of `NjamsMetadata`.

`NjamsModel` itself keeps the process-model registry (`Map<Path,ProcessModel>`), the image
set, the layouter / diagram-factory fields, and the public methods that guard lifecycle and
delegate to the collaborators.

## Public API decisions

- **Method names are preserved** across the move. Global-variable methods keep their exact
  signatures; only their owning facet changes (`metadata()` → `model()`).
- **Chaining is preserved.** The global-variable mutators were deliberately made chainable
  (returning the facet). On `NjamsModel` they return `NjamsModel` so
  `njams.model().addGlobalVariables(v).setGlobalVariablesPattern(p)` works.
- **Deprecated legacy methods on `Njams` stay.** `Njams.getGlobalVariables()`,
  `addGlobalVariables(...)`, `getGlobalVariablesPattern()`, `setGlobalVariablesPattern(...)`
  remain `@Deprecated(since="6.0.0", forRemoval=true)` and unchanged in behavior; their
  delegation target changes from the `metadata` field to the `model` field, and their
  `@deprecated` Javadoc retargets from `metadata().…` to `model().…`. The process-model
  legacy methods likewise keep delegating, with Javadoc retargeted `processes().…` →
  `model().…`.
- **No relocated or wire-format type on the public surface.** Global variables are JDK
  types (`Map<String,String>`, `String`). `TreeElement` and `ProjectMessage` stay inside
  the internal collaborators, never on a `public`/`protected` signature.
- **Lifecycle contract unchanged.** The pre-start guards (`requireNotStarted`) on
  `addImage`, `setTreeElementType`, and the global-variable mutators carry over verbatim;
  global variables and pattern are still announced once, at `start()`, inside `send()`.

## Performance

The hot runtime path (`createJob`, activity recording) does not touch these facets. Facet
accessors stay `final`-field reads. Global variables are read only during `send()` (at/around
start), so relocating them adds no per-job cost. Collaborators are created once in the
`NjamsModel` constructor and held in `final` fields.

## Size evaluation

| Facet | Now (lines) | After, flat | After, with collaborators |
|---|---:|---:|---:|
| `NjamsModel` (was `NjamsProcesses`) | 504 | ~590 | **~300** |
| `NjamsMetadata` | 342 | ~250 | ~250 |
| `TaxonomyTree` (internal, new) | — | — | ~140 |
| `ProjectMessageAssembler` (internal, new) | — | — | ~90 |
| `GlobalVariables` (internal, new) | — | — | ~95 |
| next-largest facet (`NjamsSerializers`) | 198 | 198 | 198 |

A flat consolidation trades the old "1400-line `Njams`" smell for a "590-line `NjamsModel`"
smell with ~23 public methods. Backing it with the three internal collaborators brings the
facet to ~300 lines / ~23 thin public methods, each collaborator independently testable, and
no facet dominates the package.

## Out of scope

- No change to the seven untouched facets, to `Njams` lifecycle (`start`/`stop`), or to the
  wire format / `ProjectMessage` contents.
- No removal of any deprecated method.
- If a dedicated Jira ticket is preferred over continuing SDK-359, the work can be split
  out unchanged; this design is ticket-agnostic.
