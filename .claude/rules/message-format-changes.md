# Message Format Changes

The nJAMS message format is defined in a separate project located locally at `../njams-messageformat/` and on GitHub at https://github.com/IntegrationMatters/njams-messageformat. It is the interface for communication with nJAMS Server and must be kept stable or at least backward-compatible.

**Jira project key for message format changes: `MSG`**

Rules for working with the message format:

- **Any change to the message format must be explicitly confirmed by a human before being made.** Never modify message format types or wire-format fields on your own initiative.
- **Changes must always be aligned with the nJAMS Server implementation.** When a message format change requires a corresponding adaptation in nJAMS Server, create a Jira ticket in the **`SER`** project to track that work.
- Changes to the message format are permitted when required by SDK work, provided the constraints above are observed.

This gate applies wherever message-format types are touched in this repo (`logmessage/`, `communication/`, model serialization) — it is not scoped to one directory, since these types are used across layers. This is the Wire Contract referenced in `public-api-design.md`: the fixed contract with nJAMS Server, kept stable against the server version it implements, transparent to Client Contract users but binding on SPI Contract implementers.

Inside `communication/`, this gate covers exactly the same files `communication-layer.md` identifies as the Wire Contract there — `communication/fragments/` (message chunking) and `MessageHeaders`/properties — in addition to the njams-messageformat types themselves. A change to any of these goes through this gate, not the `breaking-change` Jira label.
