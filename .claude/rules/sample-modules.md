---
globs: "njams-sdk-sample-client/**, njams-sdk-sample-app/**"
---

# Sample Modules

`njams-sdk-sample-client/` and `njams-sdk-sample-app/` contain example/usage code demonstrating the SDK, not the SDK itself. They are held to looser, demo-only standards:

- Skip the strict Javadoc-on-public, visibility-discipline, and copyright-header rigor that applies to `njams-sdk/` core (see `public-api-design.md` and `code-quality-general.md`).
- Code should compile and demonstrate usage clearly; it does not need to model the SDK's own API-surface discipline.

**Exception:** `njams-sdk-sample-client/src/main/resources/settings_full.properties` must still be kept in sync with `NjamsSettings` — see `settings-management.md`. It is a reference document, not example code, despite its location.
