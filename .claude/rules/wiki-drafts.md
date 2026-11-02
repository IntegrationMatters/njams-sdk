---
globs: "wiki/**"
---

# Wiki Drafts

The `wiki/` folder in this repository contains branch-local drafts of the GitHub wiki pages (`Home.md`, `FAQ.md`, …). During development, update the relevant files in `wiki/` rather than editing the public wiki directly.

**Never push to the public wiki repo (`C:\scm\GitHub\njams-sdk.wiki\`) on your own initiative.** Only push to the public wiki when explicitly asked, and even then always ask for confirmation before pushing.

Update `wiki/FAQ.md` whenever:
- A new setting is added
- The behavior or default value of an existing setting changes
- A setting is deprecated or removed

Update any other affected `wiki/` page when a feature or fix that changes documented behavior is declared complete.

**Do not document a fix in the FAQ/reference docs when it doesn't change a property's original intention** (e.g. a more accurate flushsize estimate is not a behavior change worth documenting as new behavior).

## The FAQ's Audience: Client Implementers, Not a Changelog

**`wiki/FAQ.md` is not a changelog.** Its "What changed in X" sections are written for client implementers, and
they only hint at the significant, relevant changes in public API that an implementer **must react on**.
Confusing that audience with too many facts and details is not helpful — completeness is not the goal, and an
exhaustive list makes the entries that do require action harder to find.

Apply this both ways:

- **Leave it out** when an implementer needs to do nothing: an internal relocation, a `public` member that was
  never Client Contract, a correctness fix with marginal observable effect, or a long list of individually
  deprecated members when one sentence already tells the reader what to migrate to. A change being real, or
  even technically breaking, is not by itself a reason to list it.
- **Keep it accurate.** A wrong statement is worse than an omitted one, because it misleads exactly the audience
  the section exists for. Before writing that something "no longer works", verify against the code what actually
  changed — e.g. "the built-in factories moved, so a setting naming one of them by fully qualified class name no
  longer matches" is accurate, while "a factory named by fully qualified class name is no longer found" is not,
  since a third-party factory's FQCN still matches.

When unsure whether an entry earns its place, state the change in one sentence and ask whether an implementer
would have to do anything about it. If the answer is no, it does not belong in the FAQ; the details belong in
the commit message, the Jira ticket, or a design spec.

Diagram assets referenced by a wiki page (e.g. `wiki/polyline-routing/*.svg`) are intentionally duplicated into the `docs/superpowers/specs/` folder of the spec that introduced them — see `docs-superpowers-lifecycle.md`. Don't treat that duplication as a leftover to clean up.
