# Jira Workflow

Issues and tasks for this project are tracked in Jira: **https://salesfive.atlassian.net** — space key **SDK**.

**When inspecting a Jira ticket, always follow its linked issues.** Read not only the explicitly linked tickets (the issue-link relationships such as "relates to", "blocks", "is caused by"), but also any tickets referenced inline in the ticket's text — summary, description, and comments (e.g. an `SDK-123` mentioned in a sentence). Both inline references and formal links may carry context, constraints, or prior decisions essential to the current work.

**When creating a new Jira ticket, always set the `fix version` field** to the current working copy's version with the `-SNAPSHOT` suffix stripped. Read the version from the root `pom.xml`. Example: working on `6.0.0-SNAPSHOT` → fix version `6.0.0`. Never create a ticket autonomously — propose it and wait for explicit confirmation first.

## Ticket Scope Is Fixed — Reframe Internally, Don't Split to a New Ticket

If a ticket's actual scope turns out larger than expected — discovered while writing a spec, drafting a
plan, or partway through implementation — **do not propose splitting the excess scope into a new Jira
ticket.** Jira tickets here track issues, not a backlog; carving out a "part 2" ticket defers work the
original ticket is supposed to cover and makes the original look done when it isn't.

All work described by (or discovered to belong to) a ticket stays under that same ticket, however many
sessions or specs it takes:

- Reframe the work as multiple specs and/or plans under `docs/superpowers/specs/` /
  `docs/superpowers/plans/` — one per sub-task is expected when the scope is too large for a single pass.
  Multiple spec/plan files sharing one ticket key is normal and encouraged (see
  `docs-superpowers-lifecycle.md`).
- Keep referencing the same ticket key on every commit for this work (see `commit-conventions.md`),
  regardless of how many sub-tasks or files it took.
- The ticket can be **assumed complete** only once every sub-task identified during reframing is actually
  done — not when the first slice lands. This still doesn't resolve it automatically: resolving a ticket
  always requires your explicit confirmation, same as any other ticket (see the `njams-ticket-finish`
  skill). "Assumed complete" just means: this is the point where proposing the resolve transition becomes
  appropriate, not before.

**Exception — a genuinely distinct issue.** If work on a ticket surfaces something that is not a larger
or reframed version of the same defect/feature but an unrelated problem in its own right (e.g. a separate
bug stumbled onto while fixing this one, an unrelated improvement idea), **propose creating a new Jira
ticket for it** rather than folding it into the current ticket's scope or silently fixing it on the side.
This follows the existing rule that a new ticket is never created without asking first — the addition
here is *when* to make that proposal: as soon as the distinct issue is recognized, not deferred to the
end of the session. The test for "distinct" vs. "part of this ticket's scope": would this still need
fixing if the current ticket did not exist? If yes, it's a separate ticket; if it only exists because of
how the current fix/feature turned out to be shaped, it's reframed sub-scope of the current one.

**When working on a Jira ticket, manage the `breaking-change` label.** If the work introduces a breaking change to public or protected API (signature / return-type / parameter-type change, removal, observable behaviour change), add the `breaking-change` label to the ticket. If the work does not break public API, remove the label if present. Adding new methods, classes, or overloads is not breaking. Check the label at the start of working on the ticket and again before declaring it done.

**When starting work on a Jira ticket, transition it to `In Progress`** (unless it is already in a started or done state) and **assign it to the current Atlassian plugin user** (call `atlassianUserInfo` to get the `account_id`, then set that as the assignee). If the ticket is already assigned to a different user, ask before changing the assignee. Do this after the ticket key is confirmed and before making any code changes. For tickets created on the spot, transition and assign immediately after creation.

**When resolving a Jira ticket, set the resolution to `Done` and the assignee to unassigned.** As part of transitioning a ticket to a resolved/done state, clear the assignee field.

**Jira ticket descriptions** focus on WHAT is needed, not HOW it is implemented. No design decisions or implementation details belong in the description, even when the ticket is created after the work is done. Structure every description in two parts:
1. **Brief summary** — a short abstract readable in ~30 seconds.
2. **Detail section** — context, constraints, and acceptance criteria needed to understand the task; still no implementation decisions.

**Closing comments** should be brief: state that the issue is resolved and optionally note the root cause. Do not include deep technical detail about how the solution was implemented — that belongs in commit messages or PR descriptions. Post closing/summary comments only when the user actually resolves the ticket, not as soon as the work feels done.

**While working on a ticket, end responses with the ticket key, title, and status.**

**Write all Jira bodies (descriptions, comments) in Markdown** with `contentFormat=markdown`. Never use wiki markup (`h2.`, `_..._`, `*..*`) — it renders broken.

All content posted to Jira by Claude Code must end with the following signature:

```
---
_Generated by Claude Code_
```
