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

Diagram assets referenced by a wiki page (e.g. `wiki/polyline-routing/*.svg`) are intentionally duplicated into the `docs/superpowers/specs/` folder of the spec that introduced them — see `docs-superpowers-lifecycle.md`. Don't treat that duplication as a leftover to clean up.
