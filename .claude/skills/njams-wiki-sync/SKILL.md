---
name: njams-wiki-sync
description: Use whenever checking if the public GitHub wiki is in sync with the branch-local `wiki/` drafts, or when asked to sync, push, or update the public/GitHub wiki. Covers diffing drafts against the public wiki clone, the authorized-push procedure, and verifying the actual GitHub-rendered result afterward rather than trusting a clean `git push`.
---

# Wiki Sync for nJAMS SDK

## Overview

The branch-local `wiki/` folder and the public wiki clone (`C:\scm\GitHub\njams-sdk.wiki\`) are two independent git repositories that nothing keeps in sync automatically — they must be reconciled by hand. See `.claude/rules/wiki-drafts.md` for the editing workflow and the push-authorization rule. This skill covers the mechanics: checking sync status, performing an authorized push, and — the part that's easy to skip — verifying the push actually produced the intended result on GitHub, not just that `git push` exited successfully.

A successful push proves nothing about rendering. GitHub's wiki markdown sanitizer silently strips constructs like raw inline `<svg>`/HTML blocks, and a git-level diff can't see that. Treat "pushed" and "renders correctly" as two separate claims that both need evidence.

## Hard Rules (from wiki-drafts.md — do not relax here)

- Never push to the public wiki on your own initiative.
- Push only when explicitly asked, and even then ask for confirmation before the actual push.
- Update `wiki/FAQ.md` whenever a setting is added/changed/deprecated (see `njams-settings-sync`).

## Steps in Detail

**1. Check sync status.**
In the public wiki clone, `git fetch origin` and confirm it's up to date with its own remote (`git log HEAD..origin/master` and `origin/master..HEAD`) before comparing anything — otherwise you're diffing against a stale local copy. Then `diff -rq` (or per-file `diff -u`) the branch-local `wiki/` folder against the public wiki clone, excluding `.git`. Report which pages differ and in which direction (draft ahead, public ahead, or diverged).

**2. Push only when explicitly asked, with confirmation.**
Per the hard rule above: state exactly which file(s) will be copied, ask for explicit confirmation, then copy the changed file(s) from `wiki/` into the public wiki clone, `git add`, commit with a descriptive message (no Jira ticket required for wiki-only changes per `commit-conventions.md`; referencing `SDK-XXX` is fine when the change belongs to a ticket), and `git push origin master` from the wiki clone.

**3. Verify the live rendered result — do not stop at a clean push.**
Fetch the actual public page (`https://github.com/IntegrationMatters/njams-sdk/wiki/<Page-Name>`) with WebFetch — not just by re-reading the local clone — and check for these known GitHub wiki rendering pitfalls:

| Pitfall | Symptom | Fix |
|---|---|---|
| Inline `<svg>`/raw HTML block | Tags are stripped but inner `<text>`/content survives as garbled running prose (e.g. "row 0 row 1 Start A B C D (r=0) (r=0)...") | Extract to a standalone `.svg` file and reference it with `![alt](relative/path.svg)` — see `wiki/polyline-routing/` for the established pattern |
| Broken relative image path | `![alt](path)` renders as a broken-image icon | Confirm the file exists at that exact relative path in the **public wiki clone**, not just in `wiki/` |
| Broken anchor link | A link to `Some-Page#a-heading` doesn't jump/resolve | Confirm the heading text (and therefore its auto-generated slug) still matches after any edit to the target page |

If any pitfall shows up, fix it in `wiki/` first (the source of truth), then repeat steps 2–3. Never patch the public clone directly without mirroring the fix back into `wiki/`.

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Diffing against a stale public wiki clone | `git fetch` and check the clone against its own origin before diffing it against `wiki/` |
| Treating a clean `git push` as proof the page renders correctly | Fetch the live GitHub page and check for the pitfalls table above |
| Using raw inline `<svg>`/HTML for diagrams | Use standalone image files referenced via markdown image syntax, per `wiki/polyline-routing/` |
| Pushing without asking, because the user "obviously" wants it synced | Always ask for confirmation before the actual push, even when asked to sync |
| Fixing a rendering bug only in the public wiki clone | Fix `wiki/` (source of truth) first, then repeat the push |
