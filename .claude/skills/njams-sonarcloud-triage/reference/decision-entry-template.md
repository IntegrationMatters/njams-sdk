# Decision Register Entry Template

Append under the appropriate heading (e.g. `## Rejected as false positive (re-apply on recurrence)`) in
`docs/sonarcloud-decisions.md`.

```markdown
### `<rule-id>` — <rule title> (×<count>)
**Verdict: <false positive | won't fix | fixed>.** <1–3 sentences: why this rule's general concern does
not apply here, or why the project's constraint makes it a non-issue. Be specific about the mechanism,
not just "it's fine.">
- <Affected fields/methods/classes, one bullet or a comma list, with enough context (ticket references,
  class names) that a future re-flag can be resolved by reading this entry alone.>
```
