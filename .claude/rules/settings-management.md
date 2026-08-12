---
globs: "njams-sdk/src/main/java/com/im/njams/sdk/settings/**, njams-sdk/src/main/java/com/im/njams/sdk/configuration/**, njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java, njams-sdk-sample-client/src/main/resources/settings_full.properties"
---

# Settings Management

`Settings` object is created with transport-specific properties before instantiating `Njams`. Key settings are defined as constants in `NjamsSettings`. Configuration providers (`ConfigurationProvider` implementations) allow loading settings from files, classpath resources, or in-memory properties.

**`NjamsSettings` is exclusively for setting-key `String` constants (`PROPERTY_*`).** Do not put default values or any other kind of constant in it. A setting's default value belongs as a `private` constant in the class that consumes the setting (e.g. `Njams.DEFAULT_CONNECT_TIMEOUT_MS`), not in `NjamsSettings`.

**Never introduce a new setting key that is also the dotted prefix of another setting key.** `ClientSettings` is a flat map of dotted string keys, which some settings sources (e.g. a hypothetical YAML-backed provider) map onto nested structure by splitting on `.`. A key that is simultaneously a leaf value and the prefix of another key (e.g. `njams.sdk.communication` next to `njams.sdk.communication.http.base.url`) cannot be represented that way — a mapping key resolves to either a scalar or a nested mapping, never both. `njams.sdk.communication`/`njams.sdk.communication.type` and `njams.sdk.communication.jms.destination`/`njams.sdk.communication.jms.destination.prefix` are existing collisions worked around with an equally-valid alternative key (via `ReadOnlyClientSettings.getPropertyWithAlternativeKey`, no deprecation warning, since neither spelling is deprecated). When designing a new setting, check its key against the existing prefixes in `NjamsSettings`, and never add a new key that shares this shape.

**Use `ClientSettings` by default.** Reach for `ReadOnlyClientSetting` only for `PropertyUtil.toProperties()` and the env-var builder.

## settings_full.properties

`njams-sdk-sample-client/src/main/resources/settings_full.properties` is the canonical reference for all available settings. **Keeping it in sync is a required step, not best-effort**: whenever a setting key is added, changed, or removed in `NjamsSettings`, update this file in the same change. This applies even though the file lives in a sample module that otherwise has looser standards (see `sample-modules.md`) — this specific file is the one exception.

## Documentation

Update `wiki/FAQ.md` whenever a setting is added, its behavior/default changes, or it is deprecated/removed — see `wiki-drafts.md`.

## Performance

Settings reads must never happen live on the runtime monitoring path — see the snapshot invariant in `runtime-performance-hotpath.md`.
