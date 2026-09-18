# Commit Message Convention & Branching

Every commit must reference the related Jira ticket using the Jira Smart Commits format:

```
SDK-XXX <description>
```

Every commit that references a ticket must include a description. The `#comment` token (`SDK-XXX #comment <description>`) causes the commit message to be posted automatically as a comment on the Jira ticket — use it **only on significant commits**, for instance the commit that finalizes a ticket. Intermediate commits (e.g. the individual steps while executing an implementation plan) must NOT carry the `#comment` tag, so the Jira comment history is not cluttered: not every commit needs to be mentioned in Jira, only the relevant ones. If no ticket exists for the work being committed, ask the user to provide one before committing.

**Before every commit, verify that the referenced ticket actually matches the change.** Check the ticket's summary (and description if needed) against the diff. If the ticket does not describe what is being committed, propose a recent ticket that better fits. If no suitable ticket exists, ask the user before committing.

**Exception:** Commits that touch only `CLAUDE.md`, `README.md`, or files under `docs/`, `.claude/skills/`, `.claude/rules/`, or `wiki/` (e.g. plans, notes, skill/rule definitions, wiki drafts) do not need a Jira ticket reference. Use a plain commit message for those, though referencing `SDK-XXX` is still appropriate when the change obviously relates to that ticket (e.g. a plan or spec written for it, or a skill/rule update made as part of a ticket's work).

## Branching

As of 2026-09-17, `6.0-dev` was merged into `master` (PR #45) and retired. For now, all current work is committed directly to `master`; no separate development branch exists and no merging back is required. Do not create additional branches unless explicitly requested. This is the current arrangement, not necessarily permanent — if a new development branch is introduced later, this section should be updated to name it and restore the appropriate master-sync check below.

## Pushing

Never push on your own initiative. Wait for an explicit, per-action "push" request — a prior push does not authorize the next one.
