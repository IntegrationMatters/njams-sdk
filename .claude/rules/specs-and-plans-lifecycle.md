---
paths:
  - "docs/superpowers/plans/**"
  - "docs/superpowers/specs/**"
  - "docs/specs/**"
---

# Specs and Plans Lifecycle

Three kinds of design documents exist, with different lifetimes:

| Kind | Location | Named | Lives until |
|---|---|---|---|
| Ticket spec | `docs/superpowers/specs/` | `YYYY-MM-DD-sdk-NNN-topic.md` | its ticket is resolved |
| Plan | `docs/superpowers/plans/` | `YYYY-MM-DD-sdk-NNN-topic.md` | its ticket is resolved |
| Topic spec | `docs/specs/` | `<topic>.md` (kebab-case, no date, no ticket key) | permanent |

Ticket specs and plans are written by the `brainstorming`/`writing-plans` skills while a ticket is worked on. A
ticket may have several of each. Since they are removed when their ticket is resolved, a ticket spec on disk
normally belongs to an open ticket. One that belongs to a resolved ticket is a leftover: consolidate it into the topic
specs and delete it the same way, with the user's confirmation.

## Topic Specs

A topic spec is the single permanent description of one area of the SDK (e.g. sender lifecycle, polyline routing,
JMS namespace selection). Its readers are SDK developers, and above all AI coding agents about to change that area.

**A topic spec states intended behavior and decisions only.** Ideally the implementation matches it. Gaps, possible
bugs and behavior nobody decided, even when found in the current code, do not belong in it; they are raised
separately or ignored.

**Rejected alternatives are listed only with a reason that was actually given.** A reason inferred afterwards does
not qualify; without a recorded reason the alternative is left out.

**Implementation details are left to the implementer.** Public API (classes, members and settings a client or SPI
implementer uses) and the classes that carry a design are named; internal methods, fields and constant names are
not. The choice of a specific algorithm or technique belongs in the spec when it was a design decision, its coding
does not. A value belongs in the spec when it is a requirement ("at least 100 px between columns"), never as a
constant (`MIN_SPACE_PX = 100`).

**Code never references a spec** — not by path, section or plan item — because the reference drifts. Rules, skills
and documentation may point to a topic spec.

It contains:

- the current design of the area and the invariants a change must preserve;
- the reasoning behind decisions that are not obvious from the code, so they are not reopened — including, in one
  sentence each, alternatives that were rejected for a reason that still holds;
- pointers to the classes involved and to related topic specs.

It does not contain ticket history, implementation steps, task lists, or content of `wiki/` — the wiki is public
documentation for SDK users and client developers, a different audience.

**It always describes the current state.** When a ticket changes the area, rewrite the affected parts so the spec
reads as if it had been written today. Never append "Update (SDK-123): …" sections; superseded content is removed,
not kept next to its replacement. A topic spec carries no ticket references; its history is in `git log`.

Diagrams a topic spec uses belong in `docs/specs/<topic>/` next to it. When the wiki uses the same diagram, both
keep their own copy — see `wiki-drafts.md`; do not deduplicate them. Diagrams only the wiki uses (e.g. user-facing
before/after examples) are not copied into the topic folder.

## Resolving a Ticket

Before a ticket is resolved, everything worth keeping from its ticket specs is merged into the affected topic specs
(or a new topic spec), and the topic specs are checked against the ticket's diff even when the ticket had no ticket
spec. Only after the ticket is resolved are its ticket specs and plans deleted. `njams-ticket-finish` carries the
procedure. If deleting a ticket spec or plan outside that flow, verify first that the ticket is resolved and the
consolidation was done.

## Plans

**Plans group their tasks under a dedicated `## Tasks` heading.** Every `### Task N` section sits below a level-2
`## Tasks` heading placed directly before the first task, so the tasks don't render as part of the preceding level-2
section (e.g. `## File Map`).

## Commits

Commits touching only these files don't need a Jira ticket reference (see `commit-conventions.md`), though
referencing the relevant `SDK-XXX` is still appropriate when the change belongs to a specific ticket.
