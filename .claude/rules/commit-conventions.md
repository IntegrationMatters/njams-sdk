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

As of 2026-09-17, `6.0-dev` was merged into `master` (PR #45) and retired. As of 2026-09-22, `6.0.1-dev` was created
from `master` for the 6.0.1 release line; all current work is committed directly to `6.0.1-dev`. It is a longer-lived
branch — it stays in place until the 6.0.1 release ships, not merged back after each change. Do not create additional
branches unless explicitly requested. The `master` branch is the stable release baseline, must never be merged into,
and is not the target for ongoing development.

**At the start of each session on a non-`master` branch, check whether `master` has commits not yet merged into the
current branch.** Run `git fetch origin master` then `git log --oneline HEAD..origin/master`. If the list is
non-empty, summarize what is missing and ask the user whether to merge `master` into the current branch before doing
further work. Do not merge without confirmation. Skip the check if already done earlier in the same session.

## Pushing

Never push on your own initiative. Wait for an explicit, per-action "push" request — a prior push does not authorize the next one.
