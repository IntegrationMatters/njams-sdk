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

- **Prefer imports over fully qualified class names.** Always import a class and use the simple name. Reach for fully qualified names only when a same-simple-name conflict in the file leaves no other option — for example, during the legacy-to-new `Path` migration where both `com.im.njams.sdk.Path` and `com.im.njams.sdk.common.Path` appear. In that case, **import the new type and fully qualify the legacy one**.
- **SOLID.** Single responsibility per class and method. Depend on abstractions, not implementations. Keep interfaces focused.
- **Self-documenting code.** Names for classes, methods, and variables should express intent clearly enough that comments are rarely needed. A comment is warranted only when the *why* is non-obvious from the code.
- **Keep comments truthful when changing code.** Whenever you modify code, double-check that every nearby comment is still correct — both Javadoc (on the changed member and on any member that references it) and inline comments inside method bodies. Update or remove anything that the change has made inaccurate. Never leave a stale comment that describes the old behavior.
- **No unnecessary complexity.** Solve the problem at hand. Do not introduce abstractions, patterns, or generalisations that have no current use.
- **DRY within reason.** Eliminate duplication, but do not create premature abstractions to unify code that merely looks similar.
- **Delegate, don't duplicate.** When adding an overload, alternative entry point, or deprecated alias for an existing method, have the new method adapt its input and call through to the canonical implementation. Never copy an algorithm into a second method just because the signature differs.
- **Avoid code smells.** Long methods, deep nesting, large classes, primitive obsession, and feature envy are signals to refactor.

## Architecture Constraints

- **Respect the existing layering.** The separation between model (process definition), logmessage (runtime execution), and communication (transport) is intentional. Do not introduce dependencies that cross these layers in the wrong direction.
- **Transport independence.** Business logic must not depend on a specific transport. Communication-specific code belongs in `communication/http`, `communication/jms`, or `communication/kafka` — see `communication-layer.md`.
- **Performance overrides elegance in hot paths.** See `runtime-performance-hotpath.md` for the runtime monitoring path this applies to.
