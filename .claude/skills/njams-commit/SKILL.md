---
name: njams-commit
description: Use immediately before running `git commit` in this repository, for every commit — not just the first one in a session. Verifies the staged diff actually matches the Jira ticket being referenced, decides whether the commit is significant enough to carry the #comment tag, and formats the Smart Commit message. Trigger whenever the user says "commit this", "let's commit", "create a commit", "commit these changes", or similar, and also apply it proactively whenever you are about to commit as part of finishing a task, even if the user didn't explicitly ask for a commit in that exact message.
---

# Commit Crafting for nJAMS SDK

## Overview

Every commit in this repo carries a Jira ticket reference, and that reference is only useful if it's actually correct — a ticket key copy-pasted from three commits ago because "it's probably still the same ticket" quietly breaks the audit trail. The `#comment` tag is a second decision that's easy to get wrong in either direction: use it on every commit and the Jira comment history turns into unreadable noise; forget it on the commit that actually finishes a ticket and there's no record of what shipped. This skill exists to make both of those checks deliberate every single time, rather than assuming last commit's answer still holds.

## Hard Rules

**Verify the diff matches the ticket before writing the message.** Check the ticket's summary (and description if needed) against what's actually staged. If they don't match — because the ticket changed scope, or this commit is really unrelated cleanup — propose a better-fitting recent ticket, or ask the user if none fits. Don't default to whatever ticket was mentioned earlier in the conversation without checking.

**If no ticket has been established for this work at all, ask before committing** — don't invent one and don't commit without a reference.

**Format every ticket-referencing commit as:**
```
SDK-XXX <description>
```
The description is required — never a bare ticket key.

**Add `#comment` only on significant commits** — the one that finalizes a ticket, or another clear milestone. Intermediate commits (individual steps of a plan, incremental progress) must NOT carry it, so the Jira comment history stays readable. When in doubt, leave it off: a missing comment can always be added later by referencing the ticket again; a cluttered comment history can't be cleaned up.

**Exception: commits touching only `CLAUDE.md` or files under `docs/`** don't need a ticket reference — use a plain commit message. Still reference `SDK-XXX` when the doc directly belongs to that ticket (e.g. a plan or spec written for it), since that's more informative than omitting it.

**Follow the repo's standard git safety practices**: create a new commit rather than amending, never skip hooks, don't force-push, and stage specific files rather than `git add -A`.

## Steps in Detail

**1. Look at what's actually staged (or about to be staged).**
Run `git status` / `git diff` and read the real change — not your memory of what the task was supposed to be.

**2. Identify the ticket.**
If one was given for this task, confirm the diff still matches its summary. If the work drifted, say so and propose the ticket that actually fits, or ask.

**3. Decide on `#comment`.**
Ask: is this commit the one that finishes the ticket, or a meaningful milestone worth surfacing in Jira? If it's just another step along the way, leave the tag off.

**4. Write the message.**
`SDK-XXX <description>` or `SDK-XXX #comment <description>`, with a real, specific description — not a restatement of the ticket title.

**5. Commit.**
Stage the specific files involved (not a blanket `git add -A`), then commit with the message via a heredoc so formatting survives.

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Reusing the same ticket key across commits without re-checking | Verify the diff matches that specific commit's ticket every time |
| Tagging every commit with #comment | Reserve it for the commit that finishes the ticket or a clear milestone |
| Committing without any ticket when one hasn't been established | Ask the user for one first |
| Bare `SDK-XXX` with no description | Always include a real description after the ticket key |
| Referencing a ticket for a docs-only CLAUDE.md/docs/ commit that doesn't need one | Use a plain message, unless the doc belongs to a specific ticket |
