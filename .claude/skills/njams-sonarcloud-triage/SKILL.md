---
name: njams-sonarcloud-triage
description: Use whenever investigating, triaging, or resolving SonarCloud findings for njams-sdk — before fixing or dismissing any reported issue, and whenever the user asks about open Sonar issues, the quality gate, or a specific Sonar rule finding.
---

# SonarCloud Issue Triage

## Overview

Not every SonarCloud finding is a genuine defect — some are false positives given this project's
threading model, public-API constraints or deliberate design. Every issue gets evaluated individually
before any action. Consolidates `.claude/rules/sonarcloud-workflow.md`.

## Hard Rules

**Query SonarCloud fresh at the start of any Sonar work** — never rely on a previously fetched snapshot of
open issues. See `reference/query-recipe.md` for the exact API calls.

**Query the branch being worked on.** The GitHub build workflow analyses every branch it runs on under its own name; pass
`&branch=<current git branch>`. Without it the API answers for SonarCloud's main branch (`master`).

**Consult `docs/sonarcloud-decisions.md` before triaging anything.** If a rule is flagged again on a
construct already recorded there, re-apply the recorded decision (re-mark it *False Positive*/*Won't
Fix* with the same rationale) instead of re-litigating or "fixing" correct code — but only after
confirming the underlying rationale still holds against the current code.

**Never dismiss or suppress a finding unilaterally.** If you conclude a finding is a false positive,
present the reasoning to the user and wait for explicit confirmation before marking it as such. Include:
what the rule flags, why it doesn't apply here, and what risk (if any) remains.

**A fix never overrides the project's other rules.** Changing existing public/protected API to satisfy a
rule is a breaking change (`public-api-design.md`) — raise it instead. Wire Contract code goes through
`message-format-changes.md`. Kafka and Argos findings are not worked proactively
(`kafka-argos-deprecated.md`).

**Analysis covers only the `njams-sdk` module.** The sample modules are deliberately excluded (`sonar.skip`)
and never triaged; findings never surface for `njams-sdk-communication-it` or the JMS namespace harnesses
either. Issues still listed for a sample module come from an older analysis and are stale.

## Steps in Detail

**1. Query the current open issues** for the current branch, using `reference/query-recipe.md`. Don't
trust an earlier fetch from the same conversation if any code has changed since.

**2. Cross-check against the decision register.**
For each finding, check whether `docs/sonarcloud-decisions.md` already has an entry for that rule +
construct. If yes, re-verify that the argument still holds and its circumstances are unchanged — the construct,
its callers and usage, and any dependency version, setting or API constraint the entry names. If anything differs,
re-evaluate it as a new finding (step 3) and update or replace the entry. If it still holds, re-apply the
recorded verdict without further debate.

**3. Evaluate each new/unrecorded finding individually.**
Read the flagged code in context. Decide: is this a real risk given how the code is actually used, or
does a project-specific constraint (threading model, lifecycle guarantee, API immutability, intentional
design) make the rule inapplicable?

**4. Real bug → fix it via the normal bug-fix workflow** (`njams-bug-fix` skill). A code smell in existing
code → `njams-safe-modification`.

**5. Suspected false positive → present reasoning, wait for confirmation.**
Never suppress unilaterally. Once confirmed, mark it *False Positive*/*Won't Fix* in SonarCloud and
record the decision.

**6. Record the decision — rejected findings only.**
For a finding marked *False Positive*/*Won't Fix*, add an entry to `docs/sonarcloud-decisions.md` using `reference/decision-entry-template.md` — rule ID,
verdict, rationale, and the specific fields/methods it covers, so future re-flags can be resolved without
re-investigating from scratch. Fixed findings get no entry: they do not recur, so there is nothing to re-apply.

## Reference Files

- `reference/query-recipe.md` — project coordinates and the exact API queries
- `reference/decision-entry-template.md` — the decision-register entry shape

## Common Mistakes

| Mistake | Correct Approach |
|---------|-------------------|
| Fixing a finding without checking the decision register first | Always check `docs/sonarcloud-decisions.md` before acting |
| Dismissing a finding as a false positive without asking | Present reasoning and wait for explicit confirmation |
| Re-applying an old decision without re-checking it still holds | Re-evaluate in context — code may have changed since the decision was recorded |
| Querying without `&branch=` | That returns `master`; pass the current git branch |
| Assuming a green build means a green quality gate | The Sonar step doesn't wait for the gate; query the gate status |
| Changing public API to silence a rule | Raise it — `public-api-design.md` governs, not the Sonar rule |
| Relying on a stale snapshot of issues from earlier in the conversation | Query fresh at the start of any SonarCloud work |
