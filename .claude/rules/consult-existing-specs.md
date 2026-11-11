# Consult Existing Specs When Proposing a Fix

**Before proposing a fix (or any change to existing behavior), check it against the existing design specs in
`docs/superpowers/specs/`.** A fix that repairs one symptom can silently break a requirement another ticket's spec
established.

- **Search by topic, not by ticket number.** The ticket being worked on is rarely the only one that shaped the code
  involved. Find every spec that touches the affected area (e.g. grep the specs for the affected classes, the
  component, and the behavior — "receiver", "reconnect", "shutdown"), regardless of which ticket it was written for.
- **Later specs may override earlier ones.** Specs are named `YYYY-MM-DD-sdk-NNN-topic.md`; read the relevant ones in
  date order and treat a later spec's requirement as authoritative where it revises or narrows an earlier one.
  Check whether an earlier requirement was explicitly revised, descoped or superseded before relying on it.
- **State the result in the proposal.** Name the specs checked and, for each relevant requirement, whether the
  proposed fix holds it. If the fix conflicts with a spec, say so and propose either a different fix or an explicit
  spec update — never deviate silently (spec updates are kept permanently, see `docs-superpowers-lifecycle.md`).
