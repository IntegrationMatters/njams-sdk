# Consult Existing Specs When Proposing a Fix

**Before proposing a fix (or any change to existing behavior), check it against the design specs.** A fix that
repairs one symptom can silently break a requirement established for another reason.

- **Topic specs (`docs/specs/`) describe the current design.** Find every topic spec that touches the affected area —
  grep them for the affected classes, the component and the behavior ("receiver", "reconnect", "shutdown"), not only
  the one whose name fits best.
- **Ticket specs (`docs/superpowers/specs/`) are changes in flight.** Each one normally belongs to an open ticket and
  revises the topic specs once that ticket is resolved; one of a resolved ticket is a leftover not yet consolidated. Check those in the same area too, so a fix doesn't collide with work
  that is already designed but not yet shipped.
- **State the result in the proposal.** Name the specs checked and, for each relevant requirement, whether the
  proposed fix holds it. If the fix conflicts with a spec, say so and propose either a different fix or an explicit
  spec update — never deviate silently. How topic specs are updated is defined in `specs-and-plans-lifecycle.md`.
