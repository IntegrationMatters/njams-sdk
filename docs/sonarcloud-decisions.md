# SonarCloud Findings — Decision Register

Persistent record of how SonarCloud findings for njams-sdk were triaged, so that **findings we deliberately
rejected as false positives are not re-litigated** every time Sonar re-analyses the code. When a rule below is
flagged again on the same construct, apply the recorded decision (re-mark it *False Positive* / *Won't Fix* in
SonarCloud with the same rationale) instead of "fixing" correct code.

**Always re-evaluate in context first** (per `.claude/rules/sonarcloud-workflow.md`): a decision here applies only
while the underlying reason still holds. If the code changed so the rationale no longer applies, treat it as a fresh
finding.

Project coordinates, auth and the query recipe are in `.claude/rules/sonarcloud-workflow.md` and the
`njams-sonarcloud-triage` skill. The GitHub build workflow analyses every branch under its own name, so query with
`&branch=<current git branch>`.

Entries follow `.claude/skills/njams-sonarcloud-triage/reference/decision-entry-template.md`.

---

## Rejected as false positive (re-apply on recurrence)

_No entries yet._

## Won't fix (re-apply on recurrence)

_No entries yet._
