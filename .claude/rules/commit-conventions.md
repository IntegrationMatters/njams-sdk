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

**The ticket reference can be waived per commit, but only by an explicit statement from the user in the same
conversation that no ticket is needed for that specific commit.** Silence, a bare "commit this", or reasoning that
the change is "just" a Javadoc/comment/small edit does not waive it — the requirement above applies regardless of how
small or low-risk the change looks. Only the user's own explicit waiver (e.g. "commit without a ticket") skips it,
and it applies to that commit only, not to later ones in the same session.

## Format

Every commit that needs a ticket reference (per the scope above) must reference the related Jira ticket using the
Jira Smart Commits format:

```
SDK-XXX <description>
```

Every commit that references a ticket must include a description. The `#comment` token (`SDK-XXX #comment <description>`) causes the commit message to be posted automatically as a comment on the Jira ticket — use it **only on significant commits**, for instance the commit that finalizes a ticket. Intermediate commits (e.g. the individual steps while executing an implementation plan) must NOT carry the `#comment` tag, so the Jira comment history is not cluttered: not every commit needs to be mentioned in Jira, only the relevant ones. If no ticket exists for the work being committed, ask the user to provide one before committing.

**Before every commit, verify that the referenced ticket actually matches the change.** Check the ticket's summary (and description if needed) against the diff. If the ticket does not describe what is being committed, propose a recent ticket that better fits. If no suitable ticket exists, ask the user before committing.

## Branching

**A change belongs to the release defined by the current branch's root `pom.xml`** (version with `-SNAPSHOT`
stripped). Always read it from the pom, never infer it from the branch name. Before starting work on a ticket, check
that this version matches the ticket's fix version; if it doesn't, stop and ask which branch to use.

**Merging is exclusively the user's decision — HARD rule.** When to merge, what to merge, and into which target is
always decided by the user. Never decide on a merge, and never propose or suggest one — not at session start, not when
a branch is behind, not as an option in a menu. Merge only when the user explicitly asks for a specific merge.

**Before running a requested merge, check only its reasonability:**

- A fix may be backported into another major/minor line's branch, or forwarded into a future development branch.
- A fix must never go into an already released version. The target branch counts as released when its pom version
  (without `-SNAPSHOT`) already has a final release tag, i.e. `git tag -l "*-sdk-root-<version>"` returns a tag that
  is not an `-RC`/`-TEST` build. The prefix changed over time (`njams4-sdk-root-` up to 5.x, `njams-sdk-root-` from
  6.0).
- If the check fails, report why instead of merging; otherwise perform exactly the requested merge.

**Only exception: worktree branches.** A worktree branch is temporary and exists only to be merged back once its work
is complete. Merging it back may therefore be offered (e.g. in the finish-the-branch menu), but only into the branch
it was created from, never into any other target. **Always ask for explicit confirmation before actually merging it
back**, because the origin branch may have work in progress of its own.

Do not create additional branches unless explicitly requested.

## Pushing

Never push on your own initiative. Wait for an explicit, per-action "push" request — a prior push does not authorize the next one.
