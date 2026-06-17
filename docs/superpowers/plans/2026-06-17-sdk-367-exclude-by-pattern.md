# SDK-367: Exclude processes by name pattern (settings-based) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:executing-plans` (or `superpowers:subagent-driven-development`) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Because this touches existing behaviour, also honour `njams-safe-modification` (establish coverage before changing existing code).

> **⚠️ BLOCKED BY SDK-451.** Do **not** start this plan until SDK-451 ("Process filters from loaded configuration are not applied at runtime") is resolved. SDK-367 relies on `ProcessFilter` being (re)initialised against the **loaded** configuration. Task 0 verifies this precondition before any other work.

**Goal:** Add a client setting that lets users exclude processes whose path matches a regular expression. The setting works **in addition** to the existing process-filter mechanism: a process is excluded if it is excluded by the existing filters **or** by a configured exclude pattern (logical OR).

**Decisions (agreed):**
1. **Reuse `ProcessFilter.excludePatterns`.** The setting's compiled patterns are added to the existing in-memory `excludePatterns` collection. They therefore behave *exactly* like a server-configured `EXCLUDE` + `REGEX` filter and inherit its precedence and matching. They are **never** turned into a `ProcessFilterEntry`, never persisted to `configuration.json`, and never sent back to the server.
2. **Precedence / include override is acceptable and must be documented.** Because the patterns live in `excludePatterns`, an explicit exact-value `INCLUDE` filter (evaluated earlier in `isSelected`) overrides the new exclude pattern. This is intentional and must be stated in the Javadoc and FAQ.
3. **Short-circuit survives.** The `includeAll && excludeNone` fast path in `isSelected` is kept. `excludeNone` must be `true` only when there are **neither** server-configured excludes **nor** injected setting patterns. This is achieved by folding the setting patterns into `excludePatterns` *as part of the same initialisation that computes `excludeNone`* — no special-casing needed.
4. **Matching:** full-match Java regex (`Pattern.matcher(path).matches()`), **case-sensitive** (consistent with existing `excludePatterns`; users may prefix `(?i)`). Patterns are matched against the process path string in its canonical form (e.g. `>App>Process>`). Invalid patterns are logged at `WARN` and skipped (same lenient behaviour as `ProcessFilter.compilePattern`).
5. **Setting:** `njams.sdk.process.exclude.regex.<name> = <java-regex>` — a prefix key supporting multiple named patterns, mirroring `njams.sdk.datamasking.regex.<name>`.

**Out of scope:** extending the nJAMS Server `SET_LOG_LEVEL` exclude command to carry a matcher type (would require `SER`/`MSG` work).

**breaking-change:** No — purely additive (new setting key + new initialisation input). No existing public/protected signature changes.

**Fix version:** 6.0.0

**Tech stack:** Java 11, JUnit 4, Mockito, `java.util.regex.Pattern`.

---

## File Map

| Action | File |
|--------|------|
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/configuration/ProcessFilter.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/NjamsConfiguration.java` |
| Modify (maybe) | `njams-sdk/src/main/java/com/im/njams/sdk/configuration/Configuration.java` (delegation, only if SDK-451's init lives here) |
| Modify | `njams-sdk/src/test/java/com/im/njams/sdk/configuration/ProcessFilterTest.java` |
| Modify | `njams-sdk-sample-client/src/main/resources/settings_full.properties` |
| Modify | `wiki/FAQ.md` |

---

## Task 0 — Verify the SDK-451 precondition

**No code changes. This task gates the rest of the plan.**

- [ ] **Step 1: Confirm SDK-451 is resolved.** Check the ticket status and that its fix is merged into the current branch (`git log --oneline | grep -i SDK-451`).

- [ ] **Step 2: Confirm loaded filters are now honoured.** SDK-451 must ship a test that loads process filters into the configuration the way it happens at runtime (via the configuration provider / deserialisation) and asserts they take effect. Run it:

```bash
mvn test -Dtest=ProcessFilterTest -pl njams-sdk
mvn test -Dtest=ConfigurationInstructionListenerTest -pl njams-sdk
```

Expected: `BUILD SUCCESS`. If no such test exists, SDK-451 is **not** truly fixed — stop and resolve it first.

- [ ] **Step 3: Identify the initialisation hook and record it here.** Read `ProcessFilter.java` and `Configuration.java`/`NjamsConfiguration.java` as they stand *after* SDK-451. Determine how `ProcessFilter` now receives the loaded filters. Write the answer into this plan before continuing:

  - **Init shape (circle one):**
    - **(A) Constructor** — `ProcessFilter` is constructed *after* the configuration is fully loaded (e.g. built lazily / rebuilt in `loadConfiguration` or `NjamsConfiguration.load()`). `excludeNone`/`includeAll` are `final`, computed in the constructor.
    - **(B) Init/reload method** — `ProcessFilter` exposes a method (e.g. `init()` / `rebuild()`) called after load; `excludeNone`/`includeAll` may be non-final.
  - **Exact method/constructor signature and caller (file:line):** ____________________

  Task 2's wiring uses this answer. Do not guess — the rest of the plan adapts to it.

- [ ] **Step 4: Confirm the match string form.** In a scratch test or by reading code, confirm what string `isSelected` matches patterns against for a typical path (expected canonical form `>App>Process>`). Record it; it goes into the FAQ and the setting's Javadoc so users write correct patterns.

---

## Task 1 — Add the `PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX` setting constant

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java`
- Modify: `njams-sdk-sample-client/src/main/resources/settings_full.properties`

### Background

`NjamsSettings` holds setting-key `String` constants only (no defaults). This follows the existing `PROPERTY_DATA_MASKING_REGEX_PREFIX` prefix-key pattern: the user appends an arbitrary name after the prefix and sets a regex as the value, allowing multiple patterns.

- [ ] **Step 1: Add the constant.** Find `PROPERTY_DATA_MASKING_REGEX_PREFIX` and add nearby (in a process-filtering section, or after it):

```java
/**
 * Prefix for defining process-exclusion regular expressions. Append an arbitrary name to the
 * prefix and set a Java regular expression as the value, e.g.
 * {@code njams.sdk.process.exclude.regex.internal=>MyApp>internal>.*}. Multiple patterns may be
 * defined using different names.
 * <p>
 * A process is excluded from processing when its path matches any of these patterns (full match,
 * case-sensitive; use {@code (?i)} for case-insensitive matching). The path is matched in its
 * canonical form (e.g. {@code >MyApp>MyProcess>}).
 * <p>
 * These patterns are applied <em>in addition</em> to the process filters configured via the nJAMS
 * server: a process is excluded if it is excluded by either mechanism. An explicit
 * <em>include</em> filter for a process takes precedence and re-includes it. Invalid expressions
 * are ignored (logged as a warning).
 *
 * @since 6.0.0
 */
public static final String PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX = "njams.sdk.process.exclude.regex.";
```

- [ ] **Step 2: Document it in `settings_full.properties`.** Add near other process/logging settings:

```properties
# Exclude processes whose path matches a regular expression. Append any name after the prefix and
# set a Java regex as the value. Multiple patterns are allowed. Matching is full-match and
# case-sensitive (use (?i) for case-insensitive). The path is matched in canonical form, e.g.
# >MyApp>MyProcess>. These patterns work in addition to server-configured process filters: a
# process is excluded if excluded by either. An explicit include filter overrides the pattern.
#njams.sdk.process.exclude.regex.internal=>MyApp>internal>.*
```

- [ ] **Step 3: Checkstyle.**

```bash
mvn validate -Pcheckstyle -pl njams-sdk
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit.**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java njams-sdk-sample-client/src/main/resources/settings_full.properties
git commit -m "SDK-367 Add PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX setting constant"
```

---

## Task 2 — Fold setting patterns into `ProcessFilter` initialisation

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/configuration/ProcessFilter.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/configuration/ProcessFilterTest.java`

### Background

The setting patterns are compiled and added to the **same `excludePatterns` collection** the existing filters use, **before** `excludeNone` is computed. This makes them indistinguishable from a server-configured regex exclude (decision 1) and keeps the `includeAll && excludeNone` short-circuit correct (decision 3): with patterns present, `excludeNone` is `false`, so `isSelected` no longer short-circuits and the patterns are evaluated at the existing `excludePatterns` check.

The exact wiring depends on Task 0's recorded init shape:

- **Shape (A) constructor:** add a parameter `Collection<String> settingsExcludePatterns` to the (post-load) constructor; compile and add them to `excludePatterns` immediately before the `excludeNone`/`includeAll` assignment.
- **Shape (B) init/reload method:** add the same compile-and-add at the start of that method, before it (re)computes `excludeNone`/`includeAll`.

Either way the new lines are the same; only their placement differs. The setter that produces `excludeNone` must run **after** the patterns are added.

### Step 1 — Write the failing tests (TDD)

- [ ] Add tests to `ProcessFilterTest`. The test `Builder` already has `exValue`/`exPattern`/`inValue`/`inPattern`; add a way to supply setting patterns matching Task 0's init shape (a builder field `settingsExcludePatterns` passed into the constructor for (A), or a call to the init method for (B)). Then:

```java
@Test
public void settingPatternExcludesMatchingProcess() {
    ProcessFilter filter = new Builder().settingExclude(">a>b>.*").build();
    assertFalse(filter.isSelected(new Path(">a>b>c>")));   // matches → excluded
    assertTrue(filter.isSelected(new Path(">a>x>c>")));    // no match → selected
}

@Test
public void settingPatternWorksWithNoOtherFilters() {
    // Regression for the excludeNone short-circuit: with ONLY a setting pattern and no
    // server filters, the pattern must still be evaluated (excludeNone must be false).
    ProcessFilter filter = new Builder().settingExclude(">a>b>c>").build();
    assertFalse(filter.isSelected(new Path(">a>b>c>")));
    assertTrue(filter.isSelected(new Path(">a>b>d>")));
}

@Test
public void settingPatternOredWithServerExclude() {
    ProcessFilter filter = new Builder().exValue(">x>y>z>").settingExclude(">a>.*").build();
    assertFalse(filter.isSelected(new Path(">x>y>z>")));   // excluded by server filter
    assertFalse(filter.isSelected(new Path(">a>b>c>")));   // excluded by setting pattern
    assertTrue(filter.isSelected(new Path(">q>r>s>")));    // neither → selected
}

@Test
public void explicitIncludeOverridesSettingPattern() {
    // Documented precedence: an exact-value include wins over the setting exclude pattern.
    ProcessFilter filter = new Builder().settingExclude(">a>b>.*").inValue(">a>b>c>").build();
    assertTrue(filter.isSelected(new Path(">a>b>c>")));    // re-included
    assertFalse(filter.isSelected(new Path(">a>b>d>")));   // still excluded by pattern
}

@Test
public void invalidSettingPatternIsIgnored() {
    ProcessFilter filter = new Builder().settingExclude("[invalid(").build();
    assertTrue(filter.isSelected(new Path(">a>b>c>")));    // bad pattern skipped → selected
}

@Test
public void caseSensitiveByDefault_caseInsensitiveWithFlag() {
    assertTrue(new Builder().settingExclude(">A>B>.*").build().isSelected(new Path(">a>b>c>")));
    assertFalse(new Builder().settingExclude("(?i)>A>B>.*").build().isSelected(new Path(">a>b>c>")));
}
```

- [ ] Run — expect failures (no setting-pattern support yet):

```bash
mvn test -Dtest=ProcessFilterTest -pl njams-sdk
```

### Step 2 — Implement

- [ ] In `ProcessFilter`, per Task 0's shape, add the setting patterns to `excludePatterns` **before** `excludeNone` is assigned. The reusable snippet (uses the existing `compilePattern`):

```java
if (settingsExcludePatterns != null) {
    for (final String regex : settingsExcludePatterns) {
        final Pattern c = compilePattern(regex);
        if (c != null) {
            excludePatterns.add(c);
            LOG.debug("Added setting-based exclude pattern: {}", regex);
        }
    }
}
```

  - **(A) constructor:** add `Collection<String> settingsExcludePatterns` as the new constructor parameter; place the snippet right after the existing config-filter loop and before:
    ```java
    excludeNone = excludes.isEmpty() && excludePatterns.isEmpty();
    ```
  - **(B) init method:** place the snippet at the top of the init body, before it (re)computes `excludeNone`. Add the field/parameter to receive the patterns.

- [ ] Update the `isSelected` Javadoc to document the new source and the include-override precedence. Replace the ordered list describing exclusion reasons with one that also mentions: "process path matches a regular expression configured via {@link com.im.njams.sdk.NjamsSettings#PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX}, unless an explicit include filter re-includes it."

- [ ] Run — expect pass:

```bash
mvn test -Dtest=ProcessFilterTest -pl njams-sdk
```

- [ ] Checkstyle + Javadoc:

```bash
mvn validate -Pcheckstyle -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```

Expected: `BUILD SUCCESS` for both.

- [ ] **Commit.**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/configuration/ProcessFilter.java njams-sdk/src/test/java/com/im/njams/sdk/configuration/ProcessFilterTest.java
git commit -m "SDK-367 Apply settings-based exclude patterns in ProcessFilter (OR with existing filters)"
```

---

## Task 3 — Parse the setting and feed it into initialisation

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsConfiguration.java`
- Modify (only if SDK-451's init lives on `Configuration`): `njams-sdk/src/main/java/com/im/njams/sdk/configuration/Configuration.java`

### Background

`NjamsConfiguration` holds both the `ClientSettings` and the `Configuration`, and already loads the configuration in `load()`. It is the right place to read the prefix keys and hand the regex strings to the `ProcessFilter` initialisation identified in Task 0. Parsing mirrors `DataMasking.addPatterns(ClientSettings)`: iterate the settings entries, match the prefix, take the value as the regex.

- [ ] **Step 1: Add a parser.** In `NjamsConfiguration`, add a private helper:

```java
private List<String> readExcludePatternSettings() {
    final List<String> patterns = new ArrayList<>();
    for (final Map.Entry<String, String> entry : settings) {
        if (entry.getKey().startsWith(NjamsSettings.PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX)
            && StringUtils.isNotBlank(entry.getValue())) {
            patterns.add(entry.getValue().trim());
        }
    }
    return patterns;
}
```

- [ ] **Step 2: Wire it into load().** After `configuration = configurationProvider.loadConfiguration();` in `NjamsConfiguration.load()`, pass `readExcludePatternSettings()` into the `ProcessFilter` init point recorded in Task 0:
  - **(A) constructor:** ensure the post-load construction call (introduced by SDK-451) is given the parsed list.
  - **(B) init method:** call it with the parsed list, e.g. `configuration.initProcessFilter(readExcludePatternSettings());` (exact name per Task 0). If the init lives on `Configuration`, add a thin public delegating method there with Javadoc.

- [ ] **Step 3: Test the end-to-end wiring.** Add a test (in `NjamsConfigurationTest` if present, otherwise the most appropriate existing config test) that sets `njams.sdk.process.exclude.regex.x=>a>b>.*` in the settings, loads, and asserts `isExcluded(new Path(">a>b>c>"))` is `true` and `isExcluded(new Path(">a>x>"))` is `false`. (Use `njams-safe-modification` to baseline existing `NjamsConfiguration` behaviour first.)

```bash
mvn test -Dtest=NjamsConfigurationTest -pl njams-sdk
```

- [ ] **Step 4: Full suite + checkstyle + javadoc.**

```bash
mvn test -pl njams-sdk
mvn validate -Pcheckstyle -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit.**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsConfiguration.java njams-sdk/src/main/java/com/im/njams/sdk/configuration/Configuration.java njams-sdk/src/test/java/com/im/njams/sdk/...
git commit -m "SDK-367 Read process exclude-pattern settings and apply them at configuration load"
```

---

## Task 4 — Document in the FAQ

**Files:**
- Modify: `wiki/FAQ.md`

### Background

Per the FAQ-update rule, any new setting must be documented. State the OR semantics and the include-override precedence explicitly.

- [ ] **Step 1: Add an entry** in the process-filtering / logging settings section:

```
### njams.sdk.process.exclude.regex.<name>

Excludes processes whose path matches a regular expression.

- **Type:** Java regular expression (value); arbitrary suffix `<name>` distinguishes multiple patterns
- **Default:** none
- **Since:** 6.0.0

Append any name after the prefix and set a Java regex as the value; define as many as needed:

    njams.sdk.process.exclude.regex.internal=>MyApp>internal>.*
    njams.sdk.process.exclude.regex.tmp=>MyApp>.*>tmp>

Matching is a **full match** against the process path in canonical form (e.g. `>MyApp>MyProcess>`)
and is **case-sensitive** — prefix the expression with `(?i)` for case-insensitive matching.

These patterns are applied **in addition** to the process filters managed by the nJAMS server: a
process is excluded if it is excluded by **either** mechanism. An explicit **include** filter for a
process takes precedence and re-includes it even if a pattern would exclude it. Invalid expressions
are ignored (logged as a warning at startup).
```

- [ ] **Step 2: Commit.**

```bash
git add wiki/FAQ.md
git commit -m "SDK-367 Document process exclude-pattern setting in FAQ"
```

---

## Self-Review Checklist

| Requirement | Task |
|---|---|
| New setting key, prefix-based, multiple patterns | Task 1 |
| Patterns OR with existing filters | Task 2 (`settingPatternOredWithServerExclude`) |
| Works with no other filters (short-circuit correct) | Task 2 (`settingPatternWorksWithNoOtherFilters`) |
| Explicit include overrides pattern (documented) | Task 2 (`explicitIncludeOverridesSettingPattern`), Tasks 1 & 4 Javadoc/FAQ |
| Full-match, case-sensitive, `(?i)` opt-in | Task 2 (`caseSensitiveByDefault_caseInsensitiveWithFlag`) |
| Invalid pattern ignored | Task 2 (`invalidSettingPatternIsIgnored`) |
| Not persisted / never a ProcessFilterEntry | Design (patterns added only to in-memory `excludePatterns`) |
| End-to-end from settings | Task 3 |
| FAQ + settings_full.properties updated | Tasks 1, 4 |
| Precondition (SDK-451) verified | Task 0 |
| breaking-change label off | Additive only — confirm on the ticket before closing |

### Open adaptation point
The single contingent detail is **how `ProcessFilter` is initialised after load** (Task 0). Tasks 2 and 3 are written for both shapes (A constructor / B init method); pick per Task 0's recorded answer. Everything else is fixed.
