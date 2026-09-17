# SDK-359: Facet Re-shaping (`model()` consolidation) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL — use `superpowers:executing-plans` (or
> `superpowers:subagent-driven-development`) to implement task-by-task. Each existing-code
> change MUST go through `superpowers:njams-safe-modification`. Steps use checkbox syntax.

**Goal:** Rename the `processes()` accessor to `model()` (`NjamsProcesses` → `NjamsModel`),
move global variables out of `NjamsMetadata` into `model()`, and back `NjamsModel` with three
package-private collaborators (`TaxonomyTree`, `ProjectMessageAssembler`, `GlobalVariables`).

**Architecture:** Per `docs/superpowers/specs/2026-06-12-sdk-359-facet-reshaping-design.md`.

**Not a breaking change:** 6.0.0 is unreleased; the facet API is intra-SNAPSHOT. Behavior is
preserved everywhere. The ticket must **not** carry the `breaking-change` label.

**Tech stack:** Java 11, Maven, JUnit 4 + Mockito, `TestSender`/`TestReceiver` mock transports.

**Jira:** Continue under SDK-359 (status In Progress, assignee Christian Winkler, fix version
6.0.0 — all already set). Intermediate commits `SDK-359 <description>` (no `#comment`); the
finalizing commit may use `SDK-359 #comment …`.

### Validation commands (run after every task)
- Tests: `mvn test -pl njams-sdk`
- Single class: `mvn test -Dtest=NjamsFacetApiTest -pl njams-sdk`
- Checkstyle: `mvn validate -Pcheckstyle -pl njams-sdk`
- Javadoc (must be error-free): `mvn javadoc:javadoc -pl njams-sdk`
- Whole build incl. samples: `mvn clean install -DskipTests` (compiles sample modules)

---

## Affected files (baseline = HEAD `8a598ac8`)

**Production (`njams-sdk/src/main/java/com/im/njams/sdk/`):**
- `Njams.java` — field `processes`→`model`, accessor `processes()`→`model()`, constructor
  wiring; global-var deprecated delegations repointed `metadata`→`model`; `@deprecated`
  Javadoc retargeted `processes().…`→`model().…` and `metadata().…GlobalVariables…`→`model().…`.
- `NjamsProcesses.java` → **rename** `NjamsModel.java`; absorb global variables; delegate to
  collaborators.
- `NjamsMetadata.java` — remove global variables (map, pattern, regex, 4 public methods +
  `*Internal` + `validateGlobalVariablesPattern`); drop now-unused `Matcher`/`Pattern`/
  `PatternSyntaxException` imports.
- **New:** `TaxonomyTree.java`, `ProjectMessageAssembler.java`, `GlobalVariables.java`
  (package-private; standard copyright header required).

**Tests (`njams-sdk/src/test/java/com/im/njams/sdk/`):**
- `NjamsFacetApiTest.java` — 39 `.processes()` calls + ~20 `.metadata().*GlobalVariables`
  calls → `.model()`.
- `NjamsTest.java`, `AbstractTest.java`, `client/NjamsSampleTest.java`,
  `model/ProcessModelTest.java`, `model/layout/CommonBfsModelLayouterTest.java`,
  `model/layout/SimpleProcessModelLayouterTest.java`,
  `logmessage/JobTest.java`, `logmessage/JobFacadeBaselineTest.java`,
  `logmessage/JobFacetApiTest.java` — `.processes()` → `.model()` (call-site renames only).
- `NjamsFacadeBaselineTest.java` — exercises deprecated `Njams.*` methods; **no change**
  expected (those methods stay). Verify it still passes unchanged.

**Samples:** `njams-sdk-sample-client` (`AdditionalProcessClient`, `GroupClient`,
`SettingsFromFileClient`, `SimpleClient`, `SimpleEndlessClient`, `SubProcessClient`,
`SubProcessSpawnedClient`) and `njams-sdk-sample-app` (`LogMessageResource`, `NjamsStartup`)
— `.processes()` → `.model()`.

**Docs:** `wiki/FAQ.md` and any `wiki/*.md` referencing `processes()` or
`metadata().…GlobalVariables`.

---

## Stage 0 — Safety net

### Task 0: Establish green baseline and confirm coverage
- [ ] Run all four validation commands on HEAD; confirm green.
- [ ] Confirm `NjamsFacetApiTest` covers, on the **facet API**: global variables surviving
      into the project message, the pattern validation (valid, invalid-regex, missing named
      groups, `null`-clear), the pre-start guards, and `processes().create/add/get/has/getAll`,
      `addImage`, `setTreeElementType`, `send`, `announce`. Add characterization tests for any
      gap **before** refactoring (these become the behavioral net).
- [ ] **Permission gate:** the `processes()`→`model()` rename forces mechanical call-site
      edits in existing test classes (method name only; no assertion or behavior change).
      Per CLAUDE.md, obtain explicit user permission to modify those test files before Stage 1.

## Stage 1 — Rename `processes()` → `model()` (mechanical, no behavior change)

### Task 1: Rename the facet and accessor
- [ ] Rename file/class `NjamsProcesses` → `NjamsModel`; update its class Javadoc
      (`njams.processes()` → `njams.model()`).
- [ ] In `Njams.java`: rename field `processes`→`model` and accessor `processes()`→`model()`;
      update constructor (`new NjamsModel(...)`) and `NjamsCommands` wiring (it receives the
      facet); retarget every `@deprecated` Javadoc `processes().…`→`model().…` and the
      process-model delegations.
- [ ] Update all call sites: SDK main, the 9 affected test classes, 7 sample-client + 2
      sample-app files (`.processes()`→`.model()`).
- [ ] Validate: tests + checkstyle + javadoc + sample compile all green.
- [ ] Commit: `SDK-359 Rename processes() facet accessor to model()`.

## Stage 2 — Extract internal collaborators (behavior identical)

### Task 2: Extract `TaxonomyTree`
- [ ] New package-private `TaxonomyTree` owning `List<TreeElement> treeElements` + the
      `projectMessageLock`; move `createTreeElements`, `setTreeElementTypeInternal`,
      `getTreeElementDefaultType`, `setStarters`, `addTreeElements`, the default-type/icon
      constants, and a `usesType(String)` helper. `NjamsModel` holds a `final TaxonomyTree`
      and delegates; `Njams` constructor's `createTreeElements(path, CLIENT)` call routes
      through `model`.
- [ ] Validate; commit `SDK-359 Extract TaxonomyTree collaborator from NjamsModel`.

### Task 3: Extract `ProjectMessageAssembler`
- [ ] New package-private `ProjectMessageAssembler` built from `metadata`, `features`,
      `configuration`; move `prepareProjectMessage`, `addDefaultImagesIfNeededAndAbsent`, and
      the body assembly of `send()`/`announce()`. `NjamsModel.send()`/`announce()` gather
      models, images, global vars, and `taxonomy.snapshot()`, pass them to the assembler, then
      hand the message to `njams.getSender()`.
- [ ] Confirm no `ProjectMessage`/`TreeElement` type appears on any `public`/`protected`
      signature (checkstyle relocated-type review + manual check).
- [ ] Validate; commit `SDK-359 Extract ProjectMessageAssembler collaborator from NjamsModel`.

## Stage 3 — Move global variables `metadata()` → `model()`

### Task 4: Introduce `GlobalVariables` holder and relocate the API
- [ ] New package-private `GlobalVariables`: `Map<String,String>` + pattern `String` +
      `NAMED_GROUP_DECLARATION` + `validate(...)`; methods `getAll`, `add(Map)`, `getPattern`,
      `setPattern(String)`. Synchronization on `projectMessageLock` preserved for map/pattern
      mutation, matching current behavior.
- [ ] Add to `NjamsModel` the four public methods (`getGlobalVariables`,
      `addGlobalVariables`, `getGlobalVariablesPattern`, `setGlobalVariablesPattern`) — same
      signatures, chainable mutators returning `NjamsModel`, same pre-start guards — delegating
      to the holder. Javadoc copied/adapted from `NjamsMetadata`.
- [ ] `ProjectMessageAssembler` reads global vars + pattern from the holder (was
      `metadata.getGlobalVariables()` / `getGlobalVariablesPattern()`).
- [ ] Remove global variables from `NjamsMetadata` (fields, 4 public + `*Internal` methods,
      `validateGlobalVariablesPattern`, now-unused imports); update its class Javadoc.
- [ ] Repoint `Njams` deprecated global-var delegations from `metadata`→`model`; retarget
      their `@deprecated` Javadoc `metadata().…`→`model().…`; keep `warnIfStarted` text updated.
- [ ] Migrate `NjamsFacetApiTest` global-var call sites `.metadata().…`→`.model().…`
      (assertions unchanged).
- [ ] Validate; commit `SDK-359 Move global variables from metadata() to model()`.

## Stage 4 — Downstream and documentation

### Task 5: Samples and wiki
- [ ] Confirm samples compile against `model()` (done in Task 1); scan for any global-var
      usage in samples and migrate if present.
- [ ] Update `wiki/FAQ.md` and other `wiki/*.md` references (`processes()`→`model()`;
      global-var docs now under `model()`). Do **not** touch the public wiki repo.
- [ ] Commit (docs-only, plain message): `Update wiki for model() facet re-shaping`.

### Task 6: Final verification and Jira housekeeping
- [ ] `grep -rn "\.processes(\|NjamsProcesses\|metadata().*GlobalVariables"` across the repo →
      expect zero stale references (outside historical docs/plans).
- [ ] Full run: `mvn clean install` (all modules) + checkstyle + javadoc — all green.
- [ ] Verify SDK-359 still has **no** `breaking-change` label and fix version `6.0.0`.
- [ ] Finalizing commit: `SDK-359 #comment Re-shape facets: rename processes()→model(), …`.
- [ ] Post the closing comment on SDK-359 **only when the user resolves the ticket** (not on
      task completion), ending with the `Generated by Claude Code` signature.

---

## Risks / notes
- **Test-file edits** are required by the rename; they are mechanical (method name only).
  Gated on permission in Task 0.
- **Two facets named in the deprecated Javadoc** (process methods + global-var methods) must
  both end up pointing at `model()`; easy to miss the global-var ones — Task 4 covers them.
- **`NjamsCommands` dependency:** the `Njams` constructor passes the facet into
  `new NjamsCommands(processes, replay, metadata, features)`; that argument and any field/
  parameter type in `NjamsCommands` must be renamed too (Task 1).
- Keep every moved method body byte-for-byte except renamed field/parameter references, so the
  behavioral net in `NjamsFacetApiTest`/`NjamsFacadeBaselineTest` stays authoritative.
