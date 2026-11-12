---
globs: "njams-sdk/src/main/java/**/*.java"
---

# Documentation and Comments

Applies to production code in `njams-sdk`. It does not apply to test code (`njams-sdk/src/test/**`) or to the sample
modules (see `sample-modules.md`).

## What Documentation Says

- **Methods and classes are documented only with Javadoc (`/** ... */`), never with `//` or `/* ... */`
  comments.** This holds for `private` and package-private members too, wherever one is documented at all.
- **Method and class documentation states WHAT, and WHY only where a non-obvious decision was made.** HOW is
  allowed only as a concept, never as implementation detail: "Calculates the response code by evaluating the
  status" is fine, "Calculates the response code by splitting the status into severity and code, then …" is not.
  The implementation shows the details, and a description of them goes stale with the next change.
- **Nothing for the obvious.** A member whose name and signature already say what it does needs no description
  beyond what checkstyle requires.
- **Comments inside method bodies are limited to non-obvious parts:** special-case handling that is not obvious
  from the code, or a complicated construct used where a simpler one would be the first idea. Never narrate what
  the code plainly does.
- **No ticket references** such as "as decided in SDK-123" in Javadoc or comments. The one exception is a bug
  fix's edge-case handling, where the ticket key tells why the special case exists:
  ```java
  // SDK-123: handle null ID which can happen if ...
  if (id == null) { ... }
  ```
- **Self-documenting code first.** Names for classes, methods, and variables should express intent clearly enough
  that comments are rarely needed.
- **Keep comments truthful when changing code.** Whenever you modify code, double-check that every nearby comment
  is still correct — both Javadoc (on the changed member and on any member that references it) and inline comments
  inside method bodies. Update or remove anything that the change has made inaccurate. Never leave a stale comment
  that describes the old behavior.

## Javadoc Requirement

**All `public` and `protected` members must have Javadoc.** This includes classes, interfaces, methods, constructors, and fields. When adding new public API, write Javadoc as part of the implementation — not as an afterthought. When deprecating an existing member, ensure its Javadoc includes a `@deprecated` tag referencing the replacement (see "Inheritance and deprecation" below for overrides). Internal (`private` and package-private) members do not require Javadoc.

### Inheritance and deprecation

Javadoc inheritance and deprecation behave differently, and getting this wrong silently produces docs that don't say what was intended (verified with JDK 25 `javac`/`javadoc`):

- **Doc comments are inherited only by an override that has no comment at all.** It then copies the overridden member's main description, `@param`, `@return` and `@throws`. As soon as the override has any comment, only what it writes itself is shown; use `{@inheritDoc}` to pull in individual parts.
- **Deprecation is never inherited** — neither the `@Deprecated` annotation nor the `@deprecated` tag. An override that lacks them is documented as *not* deprecated, and code calling it through the implementing type (e.g. a `JobImpl` reference instead of `Job`) gets no compiler warning.
- **`{@inheritDoc}` does not work inside `@deprecated`**: `javadoc` warns and the reason text is dropped.

Therefore, when deprecating a method that has public overrides in the SDK (typically an interface method and its `*Impl`):

- Put the full reasoning/replacement text once, on the declaring type.
- On every public override, keep its Javadoc comment, add the same `@Deprecated(since = ..., forRemoval = ...)` annotation, and a short pointer tag: `@deprecated See {@link Job#method(String)}.`
- A package-private member may also be marked `@Deprecated(forRemoval = true)` purely to schedule its removal (e.g. an internal helper that only backs deprecated API); its `@deprecated` tag should name what it has to be removed together with.

`javac` still reports a `[removal]` warning on the override itself even when it is annotated — that is expected for terminal deprecation and needs no suppression.

When deprecation or inherited docs matter, check the generated page (`njams-sdk/target/reports/apidocs/...`) rather than trusting the source. Delete that folder first — `javadoc:javadoc` may leave a stale page in place.

### Verification

Checkstyle is enforced by the CI pipeline and validates Javadoc on all public methods, types, and variables. Run locally with:

```bash
mvn validate -Pcheckstyle -pl njams-sdk
```

(Do not use the bare `checkstyle:check` goal — it resolves an obsolete plugin version that cannot parse Java 8+.)

**Javadoc must build without errors before any commit.** Run locally with:

```bash
mvn javadoc:javadoc -pl njams-sdk
```

A broken `{@link}` or `@see` reference (e.g. pointing to a renamed or removed method) is a hard error that fails the Javadoc build — it is not a warning. Always fix errors before committing; warnings are tolerated but errors are not.

### Conciseness

Javadoc states intent and contract, not implementation detail — for every member, `private` ones included. Keep it short:

- **No numeric or implementation specifics of concrete subclasses or call sites** (retry counts, timeouts, buffer sizes). State *that* a bound or behavior exists, not its current value — Javadoc on a supertype/interface goes stale the moment one implementation's constant changes. ("Implementations bound their own retries and throw once exhausted", not "HTTP retries 20 times at 50 ms.")
- **State restrictions plainly, without justifying them.** "Not part of the user-facing API; client code must not call this directly" is sufficient — skip the downstream-consequences rationale (performance impact, server behavior, etc.) unless that reasoning is itself something a caller needs to use the member correctly.
- If the deeper rationale or current numeric values are worth documenting somewhere, that belongs in the wiki/FAQ or a design spec — not the Javadoc.

