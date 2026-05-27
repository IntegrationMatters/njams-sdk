# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Resources

- **Wiki:** https://github.com/IntegrationMatters/njams-sdk/wiki — project home page
- **FAQ:** https://github.com/IntegrationMatters/njams-sdk/wiki/FAQ — covers settings providers, all transport configurations (JMS, HTTP/HTTPS, Kafka), message size and flush tuning, data masking, Argos metrics, and custom `ProcessModelLayouter` implementations

Consult the FAQ before implementing or modifying anything related to configuration, communication, or advanced SDK features — it documents intended behavior and usage patterns.

### Wiki drafts

The `wiki/` folder in this repository contains branch-local drafts of the GitHub wiki pages (`Home.md`, `FAQ.md`, …). During development, update the relevant files in `wiki/` rather than editing the public wiki directly.

**Never push to the public wiki repo (`C:\scm\GitHub\njams-sdk.wiki\`) on your own initiative.** Only push to the public wiki when explicitly asked, and even then always ask for confirmation before pushing.

Update `wiki/FAQ.md` whenever:
- A new setting is added
- The behavior or default value of an existing setting changes
- A setting is deprecated or removed

Update any other affected `wiki/` page when a feature or fix that changes documented behavior is declared complete.

## Project Tracking

Issues and tasks for this project are tracked in Jira: **https://salesfive.atlassian.net** — space key **SDK**.

**When creating a new Jira ticket, always set the `fix version` field** to the current working copy's version with the `-SNAPSHOT` suffix stripped. Read the version from the root `pom.xml`. Example: working on `6.0.0-SNAPSHOT` → fix version `6.0.0`.

**When working on a Jira ticket, manage the `breaking-change` label.** If the work introduces a breaking change to public or protected API (signature / return-type / parameter-type change, removal, observable behaviour change), add the `breaking-change` label to the ticket. If the work does not break public API, remove the label if present. Adding new methods, classes, or overloads is not breaking. Check the label at the start of working on the ticket and again before declaring it done.

**When starting work on a Jira ticket, transition it to `In Progress`** (unless it is already in a started or done state) and **assign it to the current Atlassian plugin user** (call `atlassianUserInfo` to get the `account_id`, then set that as the assignee). If the ticket is already assigned to a different user, ask before changing the assignee. Do this after the ticket key is confirmed and before making any code changes. For tickets created on the spot, transition and assign immediately after creation.

**Jira ticket descriptions** focus on WHAT is needed, not HOW it is implemented. No design decisions or implementation details belong in the description, even when the ticket is created after the work is done. Structure every description in two parts:
1. **Brief summary** — a short abstract readable in ~30 seconds.
2. **Detail section** — context, constraints, and acceptance criteria needed to understand the task; still no implementation decisions.

**Closing comments** should be brief: state that the issue is resolved and optionally note the root cause. Do not include deep technical detail about how the solution was implemented — that belongs in commit messages or PR descriptions.

## Commit Message Convention

Every commit must reference the related Jira ticket using the Jira Smart Commits format:

```
SDK-XXX #comment <description>
```

The `#comment` token causes the commit message to be posted automatically as a comment on the Jira ticket. If no ticket exists for the work being committed, ask the user to provide one before committing.

**Exception:** Commits that touch only `CLAUDE.md` or files under `docs/` (e.g. plans, notes) do not need a Jira ticket reference. Use a plain commit message for those.

## Branching and Committing

All current work is committed directly to the `6.0-dev` branch. Do not create additional branches unless explicitly requested. The `master` branch is the stable release baseline and is not the target for ongoing development.

**At the start of each session on a non-`master` branch, check whether `master` has commits not yet merged into the current branch.** Run `git fetch origin master` then `git log --oneline HEAD..origin/master`. If the list is non-empty, summarize what is missing and ask the user whether to merge `master` into the current branch before doing further work. Do not merge without confirmation. Skip the check if already done earlier in the same session.

## Dependencies

Avoid introducing new third-party libraries. Prefer solving problems with the libraries already in the project (Jackson, SLF4J, ActiveMQ, Kafka, Resteasy, etc.) or with the Java standard library. If a new dependency is genuinely necessary, ask before adding it. When a new dependency is approved, always check online for the latest stable version and use that.

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

The copyright header does not apply to test files.

## Checkstyle

Checkstyle is enforced by the CI pipeline (`mvn checkstyle:check`). It validates Javadoc on all public methods, types, and variables. Run locally with:

```bash
mvn checkstyle:check -pl njams-sdk
```

## Javadoc Validation

**Javadoc must build without errors before any commit.** Run locally with:

```bash
mvn javadoc:javadoc -pl njams-sdk
```

A broken `{@link}` or `@see` reference (e.g. pointing to a renamed or removed method) is a hard error that fails the 
Javadoc build — it is not a warning. Always fix errors before committing; warnings are tolerated but errors are not.

## Build Commands

```bash
# Build entire project
mvn clean install

# Build only the core SDK module
mvn clean install -pl njams-sdk

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=NjamsSampleTest -pl njams-sdk

# Run a single test method
mvn test -Dtest=NjamsSampleTest#testMethodName -pl njams-sdk

# Generate Javadoc
mvn javadoc:javadoc -pl njams-sdk

# Run with SonarQube coverage
mvn clean test -Psonar -pl njams-sdk

# Skip tests for faster builds
mvn clean install -DskipTests
```

**Requirements:** Java 11+, Maven 3.8+

## Project Structure

Multi-module Maven project (`com.salesfive.njams`, version 6.0.0-SNAPSHOT):
- `njams-sdk/` — Core SDK library (primary development target)
- `njams-sdk-sample-client/` — Usage examples demonstrating all SDK features
- `njams-sdk-sample-app/` — Additional sample application

All source code is under `com.im.njams.sdk` (despite the `com.faizsiegeln` groupId).

## Architecture Overview

The SDK instruments Java applications to track process execution and stream monitoring data to an nJAMS Server. The communication model is unidirectional: applications push `LogMessage` (job execution data) and `ProjectMessage` (process model definitions) to the server, while the server can push commands back to the client.

### Core Lifecycle

1. **Define:** Create a `ProcessModel` with `ActivityModel` nodes and `TransitionModel` edges
2. **Start:** `Njams.start()` registers models with server, opens communication channel
3. **Execute:** For each process execution, call `njams.createJob(processPath)` → record activities → `job.end()`
4. **Stop:** `Njams.stop()` flushes pending messages and closes connections

### Key Classes

| Class | Role |
|-------|------|
| `Njams` | Main entry point — manages lifecycle, process registry, sender thread pool, server command handling |
| `ProcessModel` | Defines process structure (activities + transitions); creates `Job` instances |
| `ActivityModel` / `TransitionModel` | Static definition of process nodes and edges |
| `Job` | Runtime instance of a process execution; collects activity data |
| `Activity` / `Group` | Runtime execution state for a single step or group of steps |
| `NjamsSettings` | Constants for all configuration property keys |

### Communication Layer (`communication/`)

Transport is pluggable via `CommunicationFactory`. Three built-in transports:
- **HTTP** (`communication/http/`) — Default, connects to nJAMS Server REST API
- **JMS** (`communication/jms/`) — ActiveMQ/JMS queue-based transport
- **Kafka** (`communication/kafka/`) — Kafka topic-based transport

Each transport implements `AbstractSender` (outbound) and `Receiver` (inbound commands). The `communication/fragments/` package handles message chunking for payloads exceeding the flush size limit.

The sender thread pool (`maxSenderThreads`, default 8) asynchronously dispatches messages. Messages are batched by flush size (`flushsize`, default 5MB) or flush interval (`flush_interval`, default 30s).

### Configuration (`settings/` and `configuration/`)

`Settings` object is created with transport-specific properties before instantiating `Njams`. Key settings are defined as constants in `NjamsSettings`. The `njams-sdk-sample-client/src/main/resources/settings_full.properties` file is the canonical reference for all available settings.

Configuration providers (`ConfigurationProvider` implementations) allow loading settings from files, classpath resources, or in-memory properties.

### Process Diagram Generation (`model/svg/`)

`ProcessDiagramFactory` generates SVG diagrams from `ProcessModel` definitions. The `model/layout/` package provides automatic layout algorithms for positioning activities. The default factory can be replaced with an XSLT-based variant.

### Argos Metrics (`argos/`)

`ArgosCollector` and its implementations (including `JVMCollector`) periodically collect and transmit JVM and custom application metrics to nJAMS Server alongside process monitoring data.

## Code Quality and Architecture

Apply best practices and maintain clean architecture in all production code. Code quality rules do not apply to test code.

### General Principles

- **Prefer imports over fully qualified class names.** Always import a class and use the simple name. Reach for fully qualified names only when a same-simple-name conflict in the file leaves no other option — for example, during the legacy-to-new `Path` migration where both `com.im.njams.sdk.Path` and `com.im.njams.sdk.common.Path` appear. In that case, **import the new type and fully qualify the legacy one**.
- **SOLID.** Single responsibility per class and method. Depend on abstractions, not implementations. Keep interfaces focused.
- **Self-documenting code.** Names for classes, methods, and variables should express intent clearly enough that comments are rarely needed. A comment is warranted only when the *why* is non-obvious from the code.
- **No unnecessary complexity.** Solve the problem at hand. Do not introduce abstractions, patterns, or generalisations that have no current use.
- **DRY within reason.** Eliminate duplication, but do not create premature abstractions to unify code that merely looks similar.
- **Delegate, don't duplicate.** When adding an overload, alternative entry point, or deprecated alias for an existing method, have the new method adapt its input and call through to the canonical implementation. Never copy an algorithm into a second method just because the signature differs.
- **Avoid code smells.** Long methods, deep nesting, large classes, primitive obsession, and feature envy are signals to refactor.

### Architecture Constraints

- **Respect the existing layering.** The separation between model (process definition), logmessage (runtime execution), and communication (transport) is intentional. Do not introduce dependencies that cross these layers in the wrong direction.
- **Transport independence.** Business logic must not depend on a specific transport. Communication-specific code belongs in `communication/http`, `communication/jms`, or `communication/kafka`.
- **Performance overrides elegance in hot paths.** In the runtime monitoring path (`logmessage/`, `communication/`, `argos/`), a simpler and faster implementation is preferred over a more elegant but heavier abstraction. Raise the trade-off with the user if the two goals conflict.

## API Design Principles

This SDK is a **public API** consumed by nJAMS client implementations. Client implementations must use SDK functionality whenever it is available rather than reimplementing it themselves.

### What Counts as Public API

Not all `public` code in this project is intended as public API. Some code is `public` only to satisfy internal cross-package access needs, not because it is meant for third-party use.

**The following are NOT public API, regardless of Java visibility:**

- **Communication layer** (`communication/`) — transports, senders, receivers, message formats, and fragmentation are internal infrastructure. SDK users should not need to know or care which transport is active, how messages are structured on the wire, or how chunking works. These details must remain fully transparent to SDK users and must not leak into the public API surface.
- Any other code that is `public` solely to enable internal cross-package access.

**Practical implications:**
- Do not expose communication types, message formats, or transport details through any public API surface.
- When assessing whether a change is a `breaking-change`, only consider the intended public API — changes to communication internals that are technically `public` in Java are not breaking changes in the API sense.
- Do not implement or change anything about the public API boundary without asking first.

### Visibility and Scoping

Access control is critical. Users of the SDK will use everything that is accessible, so anything not intended for external use must be actively hidden:

- **Prefer the most restrictive scope possible.** Use `private` or package-private (no modifier) for internal implementation details. Reserve `public` and `protected` for intentional API surface.
- **Use interfaces to hide implementations.** When a type is part of the public API but its implementation should not be, expose an interface (or abstract class) and keep the concrete class package-private or internal. `Job`, `Activity`, and `Group` are examples of this pattern — they are interfaces rather than exposing their implementation classes directly.
- **Be deliberate about `protected`.** Protected methods are also API surface — subclasses in external code can call them. Only use `protected` when subclassing is an intentional extension point.
- **New public members are permanent commitments.** Adding a `public` method or class is easy; removing or changing it is a breaking change for all client implementations. Introduce new public API with care.

When adding or modifying functionality, always ask: should external code be able to see and call this? If not, restrict the scope or introduce an interface boundary.

### Implementing New Functionality

When implementing new functionality, keep all implementation details private by default and only expose what is explicitly intended as public API. If it is not obvious what the public API surface should be, **ask before planning or writing any code**. Use the `njams-new-feature` skill when implementing new functionality.

### Javadoc Requirement

**All `public` and `protected` members must have Javadoc.** This includes classes, interfaces, methods, constructors, and fields. When adding new public API, write Javadoc as part of the implementation — not as an afterthought. When deprecating an existing member, ensure its Javadoc includes a `@deprecated` tag referencing the replacement. Internal (`private` and package-private) members do not require Javadoc.

Documentation and code quality rules do not apply to test code.

### Immutability of Existing Public API

**All existing `public` and `protected` API must be treated as in active use by external client implementations and must not be changed unless explicitly requested.** This is a hard rule, not a guideline. Changing a method signature, return type, behavior, or removing a member is a breaking change. If you identify a problem with an existing public API, raise it with the user rather than fixing it silently.

**Extending the public API with new methods, classes, or overloads is permitted without explicit request.** Adding is safe; changing or removing is not.

**When changing existing public API is explicitly requested**, do not remove the old API. Instead, keep it in place, mark it `@Deprecated`, and add a Javadoc `@deprecated` tag that references the new replacement:

```java
/**
 * @deprecated Use {@link #newMethod()} instead.
 */
@Deprecated
public void oldMethod() {
    return newMethod(); // delegate to new implementation where possible
}
```

The deprecated member is still existing code being modified — test coverage must be established for it before making any changes, exactly as for any other code change.

When modifying existing code, always use the `njams-safe-modification` skill, which enforces test coverage before any change is made.

## Performance Requirements

Memory consumption and CPU usage must be kept small throughout the SDK. This is especially critical in the **runtime monitoring path** — the code executed for every job and activity during process execution — where overhead accumulates directly on the instrumented application.

The runtime monitoring path includes:
- `logmessage/` — `Job`, `Activity`, `Group` lifecycle and data collection
- `communication/` — message batching, flushing, and dispatch
- `argos/` — metrics collection and transmission

### Rules for performance-sensitive code

- **Avoid unnecessary object allocation.** Reuse objects, use primitives where possible, prefer lazy initialisation over eager construction.
- **Avoid reflection, dynamic proxies, and classpath scanning** in hot paths.
- **Do not add synchronisation overhead** beyond what is required for correctness.
- **Prefer simple data structures.** Avoid heavy frameworks or abstractions where a straightforward implementation suffices.
- **Do not perform I/O or blocking operations** on threads that process monitoring data.

When implementing new functionality that touches the runtime monitoring path, consider memory and CPU impact explicitly. If a design choice has a meaningful performance trade-off, raise it before implementing.

## Testing

Tests use JUnit 4 + Mockito. The `AbstractTest` base class provides common test setup. Transport-specific tests (JMS, Kafka) may require mocking of connection infrastructure.

Test reports are generated in `njams-sdk/target/surefire-reports/`.

When fixing bugs, always use the `njams-bug-fix` skill. Every bug fix must be linked to a Jira ticket in the SDK project — confirm the ticket key (e.g. `SDK-123`) before starting and reference it in commit messages. Once a fix is confirmed successful, post a short comment on the Jira ticket describing the root cause and the fix. Existing test cases must never be modified without explicit user permission — if a fix causes a test to fail, the fix is wrong. If there is a genuine reason to believe a test is incorrect, explain the specific conflict and ask the user before changing anything.

All content posted to Jira by Claude Code must end with the following signature:

```
---
_Generated by Claude Code_
```
