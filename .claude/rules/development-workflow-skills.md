# Which Skill to Use

- **New functionality** (new class, method, or feature): use the `njams-new-feature` skill before writing any code or plan.
- **Modifying existing code** (any existing method, class, or behavior — including deprecating a public member): use the `njams-safe-modification` skill before touching it. It enforces establishing test coverage before any change is made.
- **Fixing a bug**: use the `njams-bug-fix` skill before changing code to address a defect. Every bug fix must be linked to a Jira ticket — confirm the ticket key before starting and reference it in commit messages. Once a fix is confirmed successful, post a short comment on the ticket describing the root cause and the fix (see `jira-workflow.md` for comment formatting).
