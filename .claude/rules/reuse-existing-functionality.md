---
globs: "njams-sdk/src/main/java/**"
---

# Reuse and Generalize Existing Functionality Before Reimplementing

**Rule:** Before implementing a bug fix or new feature in production code, check whether an existing
production mechanism already covers part or all of the same need — especially one built for a
*structurally* similar problem (a similar cross-thread handoff, a similar chunking/batching shape, a
similar settings-snapshot pattern), not just an identically-named one. Prefer merging into and
generalizing that mechanism over building a second, parallel implementation. Do not force an existing
mechanism to cover a case it doesn't fit naturally if doing so would make the result overcomplicated —
judge this case by case. When it is not obvious whether to reuse/generalize or build separately, present
the comparison to the user (what the existing mechanism already covers, what would need to change or be
added) and ask, rather than deciding unilaterally.

**Why:** Parallel, near-duplicate mechanisms are a recurring risk in a codebase with several transport
implementations (`AbstractSender` subclasses), shared batching/chunking logic
(`communication/fragments/`), and layered settings resolution (`HierarchicalSettings`,
`JobSettings`-style snapshots). A new feature or fix that looks self-contained can easily reinvent one of
these shapes under a new name instead of extending the existing one — increasing maintenance cost and
risking behavioral drift between the two.

**How to apply:**
- During `njams-ticket-start`'s solution-approach step (or the initial analysis step of
  `njams-new-feature`, `njams-bug-fix`, or `njams-safe-modification` — whichever applies), explicitly
  search for existing production code that solves a structurally similar problem — not just an
  identically-named feature. Ask "does this look like something we've already built a mechanism for?",
  not only "does this exact feature exist?".
- If a candidate is found, the draft presented to the user must state: what the existing mechanism already
  covers, what would need to change/generalize to also cover the new case, and the recommended direction
  (reuse-and-generalize vs. build separately) with reasoning.
- Prefer generalizing existing code when the new case is a natural variant of the same shape (same
  invariant, same lifecycle, different trigger/config).
- Do not generalize when it would force an unrelated case into a shape that doesn't fit, producing a more
  complicated result than two focused implementations would — this is a judgment call, not an automatic
  default either way.
- When the right call isn't obvious from the comparison, ask the user rather than deciding unilaterally —
  same principle as "No Unsupported Assumptions" in `CLAUDE.md`, applied to design choices rather than
  factual claims.
