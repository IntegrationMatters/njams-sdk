# Commit Message Convention & Branching

Every commit must reference the related Jira ticket using the Jira Smart Commits format:

```
SDK-XXX <description>
```

Every commit that references a ticket must include a description. The `#comment` token (`SDK-XXX #comment <description>`) causes the commit message to be posted automatically as a comment on the Jira ticket — use it **only on significant commits**, for instance the commit that finalizes a ticket. Intermediate commits (e.g. the individual steps while executing an implementation plan) must NOT carry the `#comment` tag, so the Jira comment history is not cluttered: not every commit needs to be mentioned in Jira, only the relevant ones. If no ticket exists for the work being committed, ask the user to provide one before committing.

**Before every commit, verify that the referenced ticket actually matches the change.** Check the ticket's summary (and description if needed) against the diff. If the ticket does not describe what is being committed, propose a recent ticket that better fits. If no suitable ticket exists, ask the user before committing.

**Exception:** Commits that touch only `CLAUDE.md` or files under `docs/`, `.claude/skills/`, or `.claude/rules/` (e.g. plans, notes, skill/rule definitions) do not need a Jira ticket reference. Use a plain commit message for those, though referencing `SDK-XXX` is still appropriate when the change obviously relates to that ticket (e.g. a plan or spec written for it, or a skill/rule update made as part of a ticket's work).

## Branching

All current work is committed directly to the `6.0-dev` branch. Do not create additional branches unless explicitly requested. The `master` branch is the stable release baseline, must never be merged into, and is not the target for ongoing development.

**At the start of each session on a non-`master` branch, check whether `master` has commits not yet merged into the current branch.** Run `git fetch origin master` then `git log --oneline HEAD..origin/master`. If the list is non-empty, summarize what is missing and ask the user whether to merge `master` into the current branch before doing further work. Do not merge without confirmation. Skip the check if already done earlier in the same session.

## Pushing

Never push on your own initiative. Wait for an explicit, per-action "push" request — a prior push does not authorize the next one.
