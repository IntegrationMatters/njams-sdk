---
name: njams-settings-sync
description: Use whenever a setting is added, renamed, has its behavior or default changed, or is deprecated/removed anywhere in njams-sdk — keeps NjamsSettings, wiki/FAQ.md, and njams-sdk-sample-client's settings_full.properties in sync as one atomic change. Trigger this the moment you're about to add or touch a PROPERTY_* constant in NjamsSettings, or whenever the user says things like "add a new setting for...", "let's make this configurable", "deprecate this property", or "change the default for X" — even if they only asked for the code change and didn't mention documentation. Also applies to checking a new setting key against existing dotted-prefix collisions before introducing it.
---

# Settings Documentation Sync for nJAMS SDK

## Overview

A setting in this SDK doesn't really exist until it's known in three places, and they drift out of sync easily because each one is edited for a different reason: `NjamsSettings` is what the code reads, `wiki/FAQ.md` is what a user configuring the SDK reads, and `settings_full.properties` is the copy-pasteable reference a client implementation actually starts from. It's entirely possible to add a working setting to the code and forget the other two — the build still passes, so nothing catches it. That's exactly why this needs to be a deliberate step rather than an afterthought: "forgot to update the FAQ" and "forgot to update settings_full.properties" are both named as recurring mistakes in this codebase's own skill docs.

## Hard Rules

**`NjamsSettings` holds only `PROPERTY_*` `String` key constants** — never a default value or any other kind of constant. A default belongs as a `private` constant in the class that consumes the setting.

**Never introduce a setting key that is also the dotted prefix of another setting key.** Because `ClientSettings` is a flat dotted-key map that some sources map onto nested structure by splitting on `.`, a key can't simultaneously be a leaf value and a prefix (e.g. `njams.sdk.communication` next to `njams.sdk.communication.http.base.url`). Check the new key against every existing key in `NjamsSettings` before adding it. If a collision is unavoidable, follow the existing pattern: an equally-valid alternative key via `ReadOnlyClientSettings.getPropertyWithAlternativeKey`, with no deprecation warning since neither spelling is deprecated.

**Update `wiki/FAQ.md`** whenever a setting is added, its behavior or default changes, or it's deprecated/removed. Don't document a fix as if it were new behavior when the property's original intention hasn't actually changed (e.g. a more accurate size estimate isn't a documented behavior change).

**Update `njams-sdk-sample-client/src/main/resources/settings_full.properties`** in the same change — this is a required step, not best-effort, even though the file lives inside a sample module that otherwise has looser standards.

**Do all three in the same change**, not as a follow-up — a setting added to the code without its documentation counterpart is incomplete work, not a later cleanup task.

## Steps in Detail

**1. Choose the key, check for collisions.**
Grep `NjamsSettings` for every existing `PROPERTY_*` value. Confirm the new key is neither a prefix of, nor prefixed by, any existing key.

**2. Add the constant.**
Add the `PROPERTY_*` `String` constant to `NjamsSettings`. Put any default value as a `private` constant on the consuming class, not here.

**3. Update the FAQ.**
Add or edit the relevant entry in `wiki/FAQ.md`: what the setting does, accepted values, and default. If deprecating, say so and point to the replacement.

**4. Update settings_full.properties.**
Add, edit, or comment out the corresponding line so the canonical reference file matches what `NjamsSettings` now defines.

**5. Double check before finishing whatever ticket this belongs to.**
This is exactly the kind of thing `njams-ticket-finish` re-checks before resolving — don't rely on remembering it independently.

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Adding a setting to NjamsSettings and stopping there | Update wiki/FAQ.md and settings_full.properties in the same change |
| Putting a default value in NjamsSettings | Defaults are private constants on the consuming class |
| Adding a key that's a prefix of an existing one (or vice versa) | Check every existing key first; use the alternative-key pattern if a collision is unavoidable |
| Treating settings_full.properties as "just sample code" | It's the canonical settings reference despite its location — keep it required, not best-effort |
| Documenting an internal accuracy fix as a behavior change in the FAQ | Only document it if the property's actual intention or default changed |
