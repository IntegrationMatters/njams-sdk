# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository. Topic- and
path-specific rules live under `.claude/rules/` — see the index at the bottom of this file.

## Project Resources

- **Wiki:** https://github.com/IntegrationMatters/njams-sdk/wiki — project home page
- **FAQ:** https://github.com/IntegrationMatters/njams-sdk/wiki/FAQ — covers settings (`ClientSettings`/`HierarchicalSettings`), all transport configurations (JMS, HTTP/HTTPS, Kafka), message size and flush tuning, data masking, Argos metrics, and custom `ProcessModelLayouter` implementations

Consult the FAQ before implementing or modifying anything related to configuration, communication, or advanced SDK features — it documents intended behavior and usage patterns.

The `wiki/` folder in this repository contains branch-local drafts of the GitHub wiki pages — see `.claude/rules/wiki-drafts.md` for the editing workflow and when to update them.

## Pending: Jenkins → GitHub Actions Cutover (SDK-381)

This branch builds and releases with GitHub Actions (`.github/workflows/`); the Jenkins multibranch job excludes only
`6.1-dev`, while `master` and the old version branches (`4.x.X`, `5.0.X`) still build on Jenkins. **When `6.1-dev` is
merged into `master`, remind the user that the Jenkins job configuration has to be switched so that only the old
version branches are built with Jenkins.** Remove this section once that switch is done.

## No Unsupported Assumptions

**Every decision must rest on verified evidence, not inference.** Before asserting a fact about this codebase, a
dependency's behavior, or the nJAMS message format, verify it by reading the actual source, running the actual
command, or checking the actual output — never assume it from general training-data familiarity or what "should"
be true.

**When genuinely uncertain, stop and ask the user** rather than deciding unilaterally — whether the uncertainty is a
technical fact, a scope judgment, or a choice between multiple reasonable interpretations.

## Dependencies

Avoid introducing new third-party libraries. Prefer solving problems with the libraries already in the project (Jackson, SLF4J, ActiveMQ, Kafka, Resteasy, etc.) or with the Java standard library. If a new dependency is genuinely necessary, ask before adding it. When a new dependency is approved, always check online for the latest stable version and use that.

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
mvn clean test -Psonarcloud -pl njams-sdk

# Skip tests for faster builds
mvn clean install -DskipTests
```

**Requirements:** Java 11+, Maven 3.9+

## Project Structure

Multi-module Maven project (`com.salesfive.njams`, version 6.1.0-SNAPSHOT):
- `njams-sdk/` — Core SDK library (primary development target)
- `njams-sdk-sample-client/` — Usage examples demonstrating all SDK features (looser standards — see `.claude/rules/sample-modules.md`)
- `njams-sdk-sample-app/` — Additional sample application (looser standards — see `.claude/rules/sample-modules.md`)
- `njams-sdk-communication-it/` — Docker-based communication resilience tests; manual/on-demand only, bound to the non-default `docker-it` profile (see `.claude/rules/communication-it-module.md`)
- `njams-sdk-jms-namespace-tests/` — Aggregator of four broker-less harnesses proving that the SDK selects the JMS implementation matching the JMS API actually on the classpath; built and run on every build:
  - `njams-sdk-jms-javax-test/` — only `javax.jms` plus a javax ActiveMQ client
  - `njams-sdk-jms-jakarta-test/` — only `jakarta.jms` plus a jakarta ActiveMQ client
  - `njams-sdk-jms-absent-test/` — neither JMS API and no broker client at all
  - `njams-sdk-jms-mixed-test/` — both JMS APIs with two broker products at once (ActiveMQ Classic on javax, Artemis on jakarta)

All source code is under `com.im.njams.sdk` (despite the `com.salesfive.njams` groupId).

## Architecture Overview

The SDK instruments Java applications to track process execution and stream monitoring data to an nJAMS Server. The communication model is unidirectional: applications push `LogMessage` (job execution data) and `ProjectMessage` (process model definitions) to the server, while the server can push commands back to the client.

### Core Lifecycle

1. **Define:** Create a `ProcessModel` with `ActivityModel` nodes and `TransitionModel` edges
2. **Start:** `Njams.start()` registers models with server, opens communication channel
3. **Execute:** For each process execution, call `processModel.createJob()` → record activities → `job.end(true)`
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
- **Kafka** (`communication/kafka/`) — Kafka topic-based transport, deprecated (see `.claude/rules/kafka-argos-deprecated.md`)

Each transport implements `AbstractSender` (outbound) and `AbstractReceiver` (inbound commands). The `communication/fragments/` package handles message chunking for payloads exceeding the flush size limit. This package is internal infrastructure, not public API — see `.claude/rules/communication-layer.md`. See that rule's "Transport Relevance" section for how much production weight each transport carries when prioritizing a change.

The sender thread pool (`maxSenderThreads`, default 8) asynchronously dispatches messages. Messages are batched by flush size (`flushsize`, default 5MB) or flush interval (`flush_interval`, default 30s).

### Configuration (`settings/` and `configuration/`)

`Settings` object is created with transport-specific properties before instantiating `Njams`. Key settings are defined as constants in `NjamsSettings`. The "Which settings can I use" section of `wiki/FAQ.md` is the canonical reference for all available settings: tables with descriptions plus a collapsed listing of every current key with its default. See `.claude/rules/settings-management.md` for the rules governing `NjamsSettings` and setting keys.

Configuration providers (`ConfigurationProvider` implementations) allow loading settings from files, classpath resources, or in-memory properties.

### Process Diagram Generation (`model/svg/`)

`ProcessDiagramFactory` generates SVG diagrams from `ProcessModel` definitions. The `model/layout/` package provides automatic layout algorithms for positioning activities. The default factory can be replaced with an XSLT-based variant.

### Argos Metrics (`argos/`)

`ArgosCollector` and its implementations (including `JVMCollector`) periodically collect and transmit JVM and custom application metrics to nJAMS Server alongside process monitoring data. This package is deprecated (see `.claude/rules/kafka-argos-deprecated.md`).

## Additional Rules (`.claude/rules/`)

Topic-specific rules that apply either always or only when working with certain files:

**Ticket completion checks:** a rule file may contain a section titled `## Ticket completion checks` listing steps to carry out before a ticket is resolved. The `njams-ticket-finish` skill reads these sections from every rule whose path scope matches the ticket's diff, so project-specific completion steps live in the rule that owns them and not in the skill.

| Rule file | Applies to | Topic |
|---|---|---|
| `jira-workflow.md` | always | Ticket lifecycle, labels, fix version, descriptions, closing comments |
| `commit-conventions.md` | always | Smart Commit message format, branching policy, pushing |
| `message-format-changes.md` | always | nJAMS message format change-confirmation gate |
| `development-workflow-skills.md` | always | Which skill to invoke for new features / modifications / bug fixes / SonarCloud findings |
| `public-api-design.md` | `njams-sdk/src/main/java/**` | Public API surface, relocated/shaded types, visibility, immutability |
| `documentation.md` | `njams-sdk/src/main/java/**` | Javadoc and code comments: what they say, deprecation on overrides, Javadoc/checkstyle verification |
| `code-quality-general.md` | `njams-sdk/src/main/java/**` | Copyright header, SOLID/DRY principles, Sonar-matching size limits for new/changed code, layering |
| `reuse-existing-functionality.md` | `njams-sdk/src/main/java/**` | Search for structurally similar existing mechanisms before building a parallel one |
| `message-sending-control.md` | `njams-sdk/src/main/java/**` | Send-cadence invariant: SDK, not client, controls when project/log messages go out |
| `runtime-performance-hotpath.md` | `logmessage/`, `communication/`, `argos/` | Allocation/reflection/I-O rules, settings-snapshot invariant |
| `job-thread-safety.md` | `logmessage/**` | Job/Activity/Group concurrency contract |
| `communication-layer.md` | `communication/**` | Not-public-API boundary, transport independence, when to propose the communication-it suite |
| `kafka-argos-deprecated.md` | `communication/kafka/**`, `argos/**` | Standing deprecation policy |
| `settings-management.md` | `settings/**`, `configuration/**`, `NjamsSettings.java` | Setting-key rules, FAQ settings reference sync |
| `testing-conventions.md` | `njams-sdk/src/test/**` | Test infra (`AbstractTest`, `TestSender`), coverage requirements |
| `sample-modules.md` | `njams-sdk-sample-client/**`, `njams-sdk-sample-app/**` | Looser demo-only standards |
| `wiki-drafts.md` | `wiki/**` | Wiki draft editing workflow, FAQ update triggers, FAQ audience (implementer-facing, not a changelog) |
| `docs-superpowers-lifecycle.md` | `docs/superpowers/plans/**`, `docs/superpowers/specs/**` | Plan/spec retention policy, plan heading structure |
| `consult-existing-specs.md` | always | Check a proposed fix against all topic-related specs; later specs override earlier ones |
| `sonarcloud-workflow.md` | `njams-sdk/**` | SonarCloud triage workflow, decision register, project coordinates and branch-aware queries |
| `communication-it-module.md` | `njams-sdk-communication-it/**` | Docker resilience test module: design spec pointer, manual-profile-only, Kafka out of scope, detects not fixes |
