---
globs: "docs/superpowers/plans/**, docs/superpowers/specs/**"
---

# docs/superpowers Lifecycle

`docs/superpowers/plans/` and `docs/superpowers/specs/` accumulate one file per ticket worked on with the `writing-plans`/`brainstorming` skills (named `YYYY-MM-DD-sdk-NNN-topic.md`).

- **Specs are kept permanently as reference.** Do not delete or archive a spec after its ticket ships.
- **Plans are removed only once the implementation has shipped AND, if the plan is tied to a Jira ticket, that ticket has actually been resolved in Jira.** Since plans are named `YYYY-MM-DD-sdk-NNN-topic.md`, the common case is that a ticket is tied — code being merged is not enough by itself; check the ticket's status before deleting. `njams-ticket-finish` sequences this correctly as part of closing a ticket; if deleting a plan outside that flow, verify the ticket is resolved first.
- **Diagram/asset duplication into a spec folder is intentional.** A spec that introduces diagrams (e.g. `docs/superpowers/specs/sdk-452-polyline-routing/*.svg`) is expected to keep its own copy even after the same diagrams also land in `wiki/` — see `wiki-drafts.md`. Do not deduplicate these away.
- Commits touching only these files don't need a Jira ticket reference (see `commit-conventions.md`), though referencing the relevant `SDK-XXX` is still appropriate since these docs belong to a specific ticket.
