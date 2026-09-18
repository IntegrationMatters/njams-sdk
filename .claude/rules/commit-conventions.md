# Commit Message Convention & Branching

## Scope of the ticket-reference requirement

The Jira ticket-reference requirement below applies only to code changes in the core SDK module's production and
test code (`njams-sdk/src/main/**` and `njams-sdk/src/test/**`). A commit needs a ticket reference if it touches any
file under either of those paths, regardless of what else it also touches.

Commits that touch **only** other files — sample modules (`njams-sdk-sample-client/**`, `njams-sdk-sample-app/**`),
documentation (`CLAUDE.md`, `README.md`, `docs/**`, `wiki/**`, `.claude/**`), or build-related files (any `pom.xml`,
`Jenkinsfile`, CI/build scripts) — do not need a Jira ticket reference. Use a plain commit message for those, though
referencing `SDK-XXX` is still appropriate when the change obviously relates to that ticket (e.g. a plan or spec
written for it, a sample update demonstrating a ticket's new feature, or a skill/rule update made as part of a
ticket's work).

## Format

Every commit that needs a ticket reference (per the scope above) must reference the related Jira ticket using the
Jira Smart Commits format:

```
SDK-XXX <description>
```

Every commit that references a ticket must include a description. The `#comment` token (`SDK-XXX #comment <description>`) causes the commit message to be posted automatically as a comment on the Jira ticket — use it **only on significant commits**, for instance the commit that finalizes a ticket. Intermediate commits (e.g. the individual steps while executing an implementation plan) must NOT carry the `#comment` tag, so the Jira comment history is not cluttered: not every commit needs to be mentioned in Jira, only the relevant ones. If no ticket exists for the work being committed, ask the user to provide one before committing.

**Before every commit, verify that the referenced ticket actually matches the change.** Check the ticket's summary (and description if needed) against the diff. If the ticket does not describe what is being committed, propose a recent ticket that better fits. If no suitable ticket exists, ask the user before committing.

## Branching

As of 2026-09-17, `6.0-dev` was merged into `master` (PR #45) and retired. For now, all current work is committed directly to `master`; no separate development branch exists and no merging back is required. Do not create additional branches unless explicitly requested. This is the current arrangement, not necessarily permanent — if a new development branch is introduced later, this section should be updated to name it and restore the appropriate master-sync check below.

## Pushing

Never push on your own initiative. Wait for an explicit, per-action "push" request — a prior push does not authorize the next one.
