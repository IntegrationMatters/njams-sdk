---
globs: "njams-sdk/src/main/java/**/*.java"
---

# Code Quality and Architecture

Apply best practices and maintain clean architecture in all production code. Code quality rules do not apply to test code, and apply more loosely to the sample modules (see `sample-modules.md`).

## Copyright Header

Every new production source file must begin with this copyright header:

```java
/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
```

The copyright header does not apply to test files, and is not required in the sample modules (`njams-sdk-sample-client/`, `njams-sdk-sample-app/`) — see `sample-modules.md`.

## General Principles

- **Prefer imports over fully qualified class names.** Always import a class and use the simple name. Reach for fully qualified names only when a same-simple-name conflict in the file leaves no other option — for example, when a file needs both `java.util.Date` and `java.sql.Date`. In that case, import the type used most in the file and fully qualify the other.
- **SOLID.** Single responsibility per class and method. Depend on abstractions, not implementations. Keep interfaces focused.
- **Self-documenting code.** Names for classes, methods, and variables should express intent clearly enough that comments are rarely needed. A comment is warranted only when the *why* is non-obvious from the code.
- **Keep comments truthful when changing code.** Whenever you modify code, double-check that every nearby comment is still correct — both Javadoc (on the changed member and on any member that references it) and inline comments inside method bodies. Update or remove anything that the change has made inaccurate. Never leave a stale comment that describes the old behavior.
- **No unnecessary complexity.** Solve the problem at hand. Do not introduce abstractions, patterns, or generalisations that have no current use.
- **DRY within reason.** Eliminate duplication, but do not create premature abstractions to unify code that merely looks similar.
- **Delegate, don't duplicate.** When adding an overload, alternative entry point, or deprecated alias for an existing method, have the new method adapt its input and call through to the canonical implementation. Never copy an algorithm into a second method just because the signature differs.
- **Avoid code smells.** Long methods, deep nesting, large classes, primitive obsession, and feature envy are signals to refactor.

## Size Limits

Limits matching SonarCloud's, so findings don't accumulate change by change — each diff reviewed on its own can stay
within the qualitative wording above while the method or class grows past what Sonar flags:

| Measure | Limit |
|---|---|
| Cognitive complexity per method (`java:S3776`) | target ≤ 15, never above 20 |
| Parameters per method (`java:S107`) | ≤ 7 |
| Class dependencies (`java:S6539`) | ≤ 20 |

**They apply to new and changed code.** When a change adds or grows a method or class, measure it against these
limits before committing — count the cognitive complexity by hand (+1 per `if`/`else if`/`else`/loop/`catch`/
`switch`/ternary/`&&`-`||` sequence, plus 1 per nesting level for nested ones) or read SonarCloud's result for the
pushed branch. Existing code over a limit is not refactored on its own initiative; it only has to stop growing.
Exceeding a limit is resolved by splitting, not by an exception in `docs/sonarcloud-decisions.md`, unless the user
agrees. Splitting must not widen visibility or add public API — the extracted parts stay private or package-private
(see `public-api-design.md`), and a public/protected signature over the `java:S107` limit is never changed to satisfy
it.

**Standing exception for class dependencies (`java:S6539`):** Sonar counts only types of the class's own package and
its subpackages, each once, wherever one appears in a declaration — field, parameter, local, return type, cast,
`instanceof`, generic argument, supertype — but not in `new Foo()`, a static call, a `throws`/catch clause or `var`.
It weighs every such type equally, so it measures complexity only where the types are used substantially. A finding
may be recorded as a false positive in `docs/sonarcloud-decisions.md`, without asking again, when the types pushing
the count over the limit are each referenced **only** as an `instanceof` test — no cast, no pattern binding that is
then used, no call, field, parameter, return or local of that type. Name those types in the decision entry. If the
count stays over the limit without them, split the class.

## Architecture Constraints

- **Respect the existing layering.** The separation between model (process definition), logmessage (runtime execution), and communication (transport) is intentional. Do not introduce dependencies that cross these layers in the wrong direction.
- **Transport independence.** Business logic must not depend on a specific transport. Communication-specific code belongs in `communication/http`, `communication/jms`, or `communication/kafka` — see `communication-layer.md`.
- **Performance overrides elegance in hot paths.** See `runtime-performance-hotpath.md` for the runtime monitoring path this applies to.
