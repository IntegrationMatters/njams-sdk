# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository. Topic- and
path-specific rules live under `.claude/rules/` — see the index at the bottom of this file.

## Project Resources

- **Wiki:** https://github.com/IntegrationMatters/njams-sdk/wiki — project home page
- **FAQ:** https://github.com/IntegrationMatters/njams-sdk/wiki/FAQ — covers settings providers, all transport configurations (JMS, HTTP/HTTPS, Kafka), message size and flush tuning, data masking, Argos metrics, and custom `ProcessModelLayouter` implementations

Consult the FAQ before implementing or modifying anything related to configuration, communication, or advanced SDK features — it documents intended behavior and usage patterns.

The `wiki/` folder in this repository contains branch-local drafts of the GitHub wiki pages — see `.claude/rules/wiki-drafts.md` for the editing workflow and when to update them.

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
mvn clean test -Psonar -pl njams-sdk

# Skip tests for faster builds
mvn clean install -DskipTests
```

**Requirements:** Java 11+, Maven 3.8+

## Project Structure

Multi-module Maven project (`com.salesfive.njams`, version 6.0.0-SNAPSHOT):
- `njams-sdk/` — Core SDK library (primary development target)
- `njams-sdk-sample-client/` — Usage examples demonstrating all SDK features (looser standards — see `.claude/rules/sample-modules.md`)
- `njams-sdk-sample-app/` — Additional sample application (looser standards — see `.claude/rules/sample-modules.md`)

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
- **Kafka** (`communication/kafka/`) — Kafka topic-based transport, deprecated (see `.claude/rules/kafka-argos-deprecated.md`)

Each transport implements `AbstractSender` (outbound) and `Receiver` (inbound commands). The `communication/fragments/` package handles message chunking for payloads exceeding the flush size limit. This package is internal infrastructure, not public API — see `.claude/rules/communication-layer.md`.

The sender thread pool (`maxSenderThreads`, default 8) asynchronously dispatches messages. Messages are batched by flush size (`flushsize`, default 5MB) or flush interval (`flush_interval`, default 30s).

### Configuration (`settings/` and `configuration/`)

`Settings` object is created with transport-specific properties before instantiating `Njams`. Key settings are defined as constants in `NjamsSettings`. The `njams-sdk-sample-client/src/main/resources/settings_full.properties` file is the canonical reference for all available settings. See `.claude/rules/settings-management.md` for the rules governing `NjamsSettings` and setting keys.

Configuration providers (`ConfigurationProvider` implementations) allow loading settings from files, classpath resources, or in-memory properties.

### Process Diagram Generation (`model/svg/`)

`ProcessDiagramFactory` generates SVG diagrams from `ProcessModel` definitions. The `model/layout/` package provides automatic layout algorithms for positioning activities. The default factory can be replaced with an XSLT-based variant.

### Argos Metrics (`argos/`)

`ArgosCollector` and its implementations (including `JVMCollector`) periodically collect and transmit JVM and custom application metrics to nJAMS Server alongside process monitoring data. This package is deprecated (see `.claude/rules/kafka-argos-deprecated.md`).

## Additional Rules (`.claude/rules/`)

Topic-specific rules that apply either always or only when working with certain files:

| Rule file | Applies to | Topic |
|---|---|---|
| `jira-workflow.md` | always | Ticket lifecycle, labels, fix version, descriptions, closing comments |
| `commit-conventions.md` | always | Smart Commit message format, branching policy, pushing |
| `message-format-changes.md` | always | nJAMS message format change-confirmation gate |
| `development-workflow-skills.md` | always | Which skill to invoke for new features / modifications / bug fixes |
| `public-api-design.md` | `njams-sdk/src/main/java/**` | Public API surface, relocated/shaded types, visibility, immutability, Javadoc, checkstyle |
| `code-quality-general.md` | `njams-sdk/src/main/java/**` | Copyright header, SOLID/DRY principles, layering |
| `runtime-performance-hotpath.md` | `logmessage/`, `communication/`, `argos/` | Allocation/reflection/I-O rules, settings-snapshot invariant |
| `job-thread-safety.md` | `logmessage/**` | Job/Activity/Group concurrency contract |
| `communication-layer.md` | `communication/**` | Not-public-API boundary, transport independence |
| `kafka-argos-deprecated.md` | `communication/kafka/**`, `argos/**` | Standing deprecation policy |
| `settings-management.md` | `settings/**`, `configuration/**`, `NjamsSettings.java`, `settings_full.properties` | Setting-key rules, canonical properties file sync |
| `testing-conventions.md` | `njams-sdk/src/test/**` | Test infra (`AbstractTest`, `TestSender`), coverage requirements |
| `sample-modules.md` | `njams-sdk-sample-client/**`, `njams-sdk-sample-app/**` | Looser demo-only standards |
| `wiki-drafts.md` | `wiki/**` | Wiki draft editing workflow, FAQ update triggers |
| `docs-superpowers-lifecycle.md` | `docs/superpowers/plans/**`, `docs/superpowers/specs/**` | Plan/spec retention policy |
