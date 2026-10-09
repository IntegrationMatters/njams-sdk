# SDK-490: Instance-scoped data masking — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILLS: `njams-safe-modification` (establish coverage before changing existing code) and `njams-new-feature` (new public class `DataMasker`). Use superpowers:subagent-driven-development or superpowers:executing-plans to implement task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every `Njams` instance masks with exactly the patterns from its own settings and configuration (plus patterns client code adds to that instance); patterns of one instance never affect another in the same JVM.

**Architecture:** A new public class `com.im.njams.sdk.DataMasker` holds one instance's patterns in an immutable, volatile list (lock-free reads on the hot path). `NjamsConfiguration` owns one `DataMasker` per `Njams` instance, exposes it via `njams.configuration().dataMasking()`, and replaces its *configured* patterns (settings + configuration store) on every start. `JobImpl` captures the masker once in its constructor; `ActivityImpl`, `JobAttributes` and `ExtractHandler` mask through the job. The static `DataMasking` API is deprecated for removal and delegates to one JVM-wide `DataMasker`, which every instance masker still applies after its own patterns, so external callers of the static API keep today's behavior.

**Tech stack:** Java 11, JUnit 4, existing `TestSender` test transport.

**Spec:** none — the approach was agreed in conversation (2026-10-09). Ticket: SDK-490 (fix version 6.1.0).

**Decisions (agreed with the user):**
1. Instance masker reached via the public accessor `njams.configuration().dataMasking()`, returning `DataMasker`.
2. The `DataMasker` public API supports adding patterns from client code exactly like the static variant did: `addPattern(String)`, `addPattern(String, String)`, `addPatterns(List<String>)`, `addPatterns(ClientSettings)`, `getPatterns()`, `maskString(String)`, `removePatterns()`.
3. The static `DataMasking` API is deprecated with `@Deprecated(since = "6.1.0", forRemoval = true)`, delegating to a JVM-wide `DataMasker`; the SDK itself never registers patterns there any more.
4. The four `NjamsTest` data-masking tests (`NjamsTest.java:478-538`) may be adjusted to assert via the instance masker (permission given). No other existing test is modified.

**Design details fixed by this plan (flag at review if you disagree):**
- A `DataMasker` keeps two pattern sources: *configured* (settings `njams.sdk.datamasking.regex.*` + configuration store, replaced as a whole on each `Njams` start) and *client-added* (via the public `add*` methods, kept for the instance's lifetime, i.e. across stop/start). Masking applies configured first, then client-added; a client-added regex already present in the configured set is skipped.
- `njams.sdk.datamasking.enabled=false` clears the configured patterns only; client-added patterns and the deprecated JVM-wide static patterns still apply — same as today, where the setting only suppressed adding settings/configuration patterns.
- `stop()` does **not** clear the configured patterns: jobs still ending after `stop()` must stay masked. Nothing JVM-wide is left behind because the masker is referenced only by the instance and its jobs; the next `start()` replaces the configured set.
- `getPatterns()` returns an immutable snapshot (configured + client-added). The deprecated `DataMasking.getPatterns()` delegates and therefore also returns a snapshot instead of the former live view; its Javadoc is updated. (Iteration was already safe; only "sees later additions through the same list object" changes.)
- `removePatterns()` removes client-added patterns only; configured ones come back from settings/configuration anyway and are owned by the SDK.

**breaking-change:** expected No (additions + deprecations only; the `getPatterns()` snapshot nuance on a deprecated method is the only observable change). `njams-ticket-finish` decides on the real diff.

## Global Constraints

- Copyright header (from `.claude/rules/code-quality-general.md`) on the new production file `DataMasker.java`.
- Javadoc on every public/protected member; `mvn validate -Pcheckstyle -pl njams-sdk` and `mvn javadoc:javadoc -pl njams-sdk` must pass with no errors.
- No relocated third-party type on the public surface (`DataMasker` uses only JDK, SLF4J-internal logger field, `ClientSettings`, `DataMaskingType`).
- Hot path (`logmessage/`): no settings reads per job/activity, no locking on `maskString`, no extra allocation when no pattern is registered.
- Never `mvn install`; use `test`/`test-compile`/`validate`/`javadoc:javadoc`.
- Commits: `SDK-490 <one sentence>`; use the `njams-commit` skill before every commit; only the final commit may carry `#comment`.
- Existing tests other than the four named `NjamsTest` methods must pass unchanged.

## Review Focus

1. **Two instances, different settings** — instance A's settings patterns must not mask data recorded on instance B's jobs (and vice versa). Pinned by `DataMaskingPerInstanceTest.patternsOfOneInstanceDoNotAffectAnother` (Task 2).
2. **Stop then start with a changed configuration-store pattern list** — the removed pattern must no longer mask, the new one must. Pinned by `DataMaskingPerInstanceTest.restartReplacesConfiguredPatterns` (Task 2).
3. **Client pattern added before `start()`** — must survive the start's replacement of configured patterns. Pinned by `DataMaskingPerInstanceTest.clientPatternAddedBeforeStartSurvivesStart` (Task 2).
4. **Masking disabled on one instance, enabled on another** — the disabled instance masks nothing from settings; the enabled one still masks. Pinned by `DataMaskingPerInstanceTest.disabledMaskingOnOneInstanceOnly` (Task 2).
5. **Patterns added concurrently while masking runs** — no exception, every pattern registered once. Pinned by `DataMaskerTest.concurrentAddAndMask` (Task 1).

---

## File Map

| Action | File |
|--------|------|
| Create | `njams-sdk/src/main/java/com/im/njams/sdk/DataMasker.java` |
| Create | `njams-sdk/src/test/java/com/im/njams/sdk/DataMaskerTest.java` |
| Create | `njams-sdk/src/test/java/com/im/njams/sdk/DataMaskingPerInstanceTest.java` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/DataMasking.java` (deprecate, delegate) |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/NjamsConfiguration.java` (own masker, accessor, `initializeDataMasking`) |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java` (capture masker, `mask(String)`) |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java` (10 call sites) |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobAttributes.java:69` |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ExtractHandler.java:152,389-390` |
| Modify | `njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java:478-538` (permitted) |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java:575-601` (Javadoc) |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/configuration/Configuration.java:205-224` (Javadoc) |
| Modify | `wiki/FAQ.md` (data-masking how-to, 6.1 deprecations) |

---

## Tasks

### Task 1: `DataMasker` class and deprecated static `DataMasking` delegating to it

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/DataMasker.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/DataMaskerTest.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/DataMasking.java`
- Baseline (unchanged): `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/DataMaskingTest.java`

**Interfaces:**
- Produces (public): `DataMasker()`, `String maskString(String)`, `void addPattern(String)`, `void addPattern(String, String)`, `void addPatterns(List<String>)`, `void addPatterns(ClientSettings)`, `List<DataMaskingType> getPatterns()`, `void removePatterns()`.
- Produces (package-private, for Task 2): `DataMasker(boolean applyJvmWidePatterns)`, `void replaceConfiguredPatterns(ClientSettings settings, List<String> configurationRegexes)`, `void clearConfiguredPatterns()`.

- [ ] **Step 1: Safe-modification baseline.** Run the existing static-API tests and record they pass:

Run: `mvn test -Dtest=DataMaskingTest+ExtractHandlerTest+JobAttributesTest -pl njams-sdk`
Expected: PASS (all).

- [ ] **Step 2: Write the failing `DataMaskerTest`.**

```java
package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.settings.ClientSettings;

@SuppressWarnings("removal")
public class DataMaskerTest {

    @Before
    @After
    public void clearJvmWidePatterns() {
        DataMasking.removePatterns();
    }

    @Test
    public void masksWithOwnPatterns() {
        DataMasker masker = new DataMasker();
        masker.addPattern("secret");
        assertEquals("a ****** b", masker.maskString("a secret b"));
    }

    @Test
    public void blankAndNullInputAreReturnedUnchanged() {
        DataMasker masker = new DataMasker();
        masker.addPattern(".*");
        assertNull(masker.maskString(null));
        assertEquals(" ", masker.maskString(" "));
    }

    @Test
    public void instancesAreIndependent() {
        DataMasker a = new DataMasker();
        DataMasker b = new DataMasker();
        a.addPattern("secret");
        assertEquals("secret", b.maskString("secret"));
        assertTrue(b.getPatterns().isEmpty());
    }

    @Test
    public void duplicateRegexIsIgnoredRegardlessOfName() {
        DataMasker masker = new DataMasker();
        masker.addPattern("first", "secret");
        masker.addPattern("second", "secret");
        masker.addPatterns(Arrays.asList("secret", "other"));
        assertEquals(2, masker.getPatterns().size());
        assertEquals("first", masker.getPatterns().get(0).getNameOfPattern());
    }

    @Test
    public void defaultNameIsIndex() {
        DataMasker masker = new DataMasker();
        masker.addPattern("a");
        masker.addPattern("b");
        assertEquals("0", masker.getPatterns().get(0).getNameOfPattern());
        assertEquals("1", masker.getPatterns().get(1).getNameOfPattern());
    }

    @Test
    public void invalidOrBlankRegexIsIgnored() {
        DataMasker masker = new DataMasker();
        masker.addPattern("[");
        masker.addPattern(" ");
        masker.addPattern(null);
        assertTrue(masker.getPatterns().isEmpty());
    }

    @Test
    public void addPatternsFromSettings() {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + "ssn", "\\d{3}-\\d{2}-\\d{4}");
        settings.put("unrelated", "x");
        DataMasker masker = new DataMasker();
        masker.addPatterns(settings);
        assertEquals(1, masker.getPatterns().size());
        assertEquals("ssn", masker.getPatterns().get(0).getNameOfPattern());
        assertEquals("a *********** b", masker.maskString("a 123-45-6789 b"));
    }

    @Test
    public void replaceConfiguredPatternsReplacesPreviousConfiguredSet() {
        DataMasker masker = new DataMasker();
        masker.replaceConfiguredPatterns(settingsWith("one", "first"), Collections.singletonList("second"));
        masker.replaceConfiguredPatterns(settingsWith("two", "third"), Collections.emptyList());
        assertEquals("first second *****", masker.maskString("first second third"));
    }

    @Test
    public void replaceConfiguredPatternsKeepsClientPatterns() {
        DataMasker masker = new DataMasker();
        masker.addPattern("client");
        masker.replaceConfiguredPatterns(settingsWith("one", "configured"), Collections.emptyList());
        masker.clearConfiguredPatterns();
        assertEquals("****** configured", masker.maskString("client configured"));
    }

    @Test
    public void configuredPatternsComeFirstAndDuplicateClientRegexIsSkipped() {
        DataMasker masker = new DataMasker();
        masker.addPattern("clientName", "secret");
        masker.replaceConfiguredPatterns(settingsWith("cfg", "secret"), Collections.emptyList());
        assertEquals(1, masker.getPatterns().size());
        assertEquals("cfg", masker.getPatterns().get(0).getNameOfPattern());
    }

    @Test
    public void removePatternsRemovesClientPatternsOnly() {
        DataMasker masker = new DataMasker();
        masker.replaceConfiguredPatterns(settingsWith("cfg", "configured"), Collections.emptyList());
        masker.addPattern("client");
        masker.removePatterns();
        assertEquals("client **********", masker.maskString("client configured"));
    }

    @Test
    public void jvmWidePatternsAreAppliedOnlyWhenRequested() {
        DataMasking.addPattern("legacy");
        assertEquals("******", new DataMasker(true).maskString("legacy"));
        assertEquals("legacy", new DataMasker().maskString("legacy"));
    }

    @Test
    public void concurrentAddAndMask() throws Exception {
        DataMasker masker = new DataMasker();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                final int n = i % 50;
                futures.add(pool.submit(() -> {
                    masker.addPattern("secret" + n);
                    masker.maskString("some secret0 and secret" + n + " text");
                    masker.getPatterns().size();
                }));
            }
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(50, masker.getPatterns().size());
    }

    private static ClientSettings settingsWith(String name, String regex) {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + name, regex);
        return settings;
    }
}
```

- [ ] **Step 3: Run it to verify it fails.**

Run: `mvn test-compile -pl njams-sdk`
Expected: compilation FAILURE — `cannot find symbol: class DataMasker`.

- [ ] **Step 4: Create `DataMasker`** (copyright header from `code-quality-general.md` first):

```java
package com.im.njams.sdk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.logmessage.DataMaskingType;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Masks data with a set of regex patterns: every substring matching a pattern is replaced with asterisks.
 * <p>
 * Each {@link Njams} instance has its own masker, obtained via {@code njams.configuration().dataMasking()}. It masks
 * with the patterns from the instance's settings and configuration, which the SDK applies on every start, and with
 * the patterns client code adds through the {@code add*} methods, which stay for the instance's lifetime. Patterns
 * of one instance never affect another instance.
 * <p>
 * Registering a pattern whose regex is already registered has no effect. All methods are thread-safe; masking does
 * not lock.
 */
public class DataMasker {

    private static final Logger LOG = LoggerFactory.getLogger(DataMasker.class);

    private static final char MASK_CHAR = '*';
    private static volatile char[] mask = new char[0];

    private final boolean applyJvmWidePatterns;
    // guarded by this
    private final List<DataMaskingType> configuredPatterns = new ArrayList<>();
    // guarded by this
    private final List<DataMaskingType> clientPatterns = new ArrayList<>();
    // configured followed by client patterns; replaced as a whole on every change so masking reads lock-free
    private volatile List<DataMaskingType> patterns = Collections.emptyList();

    /**
     * Creates a masker without any patterns.
     */
    public DataMasker() {
        this(false);
    }

    /**
     * @param applyJvmWidePatterns whether {@link #maskString(String)} also applies the patterns registered through
     *        the deprecated static {@link DataMasking} API
     */
    DataMasker(boolean applyJvmWidePatterns) {
        this.applyJvmWidePatterns = applyJvmWidePatterns;
    }

    /**
     * Masks the given string with the registered patterns.
     *
     * @param inString the string to mask
     * @return the masked string; <code>null</code> or blank input is returned unchanged
     */
    @SuppressWarnings("removal")
    public String maskString(final String inString) {
        final String masked = applyPatterns(inString);
        return applyJvmWidePatterns ? DataMasking.maskString(masked) : masked;
    }

    private String applyPatterns(final String inString) {
        final List<DataMaskingType> current = patterns;
        if (current.isEmpty() || StringUtils.isBlank(inString)) {
            return inString;
        }
        final StringBuilder maskedString = new StringBuilder(inString);
        for (DataMaskingType dataMaskingType : current) {
            final Matcher m = dataMaskingType.getPattern().matcher(inString);
            while (m.find()) {
                maskedString.replace(m.start(), m.end(), getMask(m.end() - m.start()));
            }
            LOG.trace("\nApplied {}, new result={}", dataMaskingType, maskedString);
        }
        LOG.debug("Masked string: {}", maskedString);
        return maskedString.toString();
    }

    /**
     * Efficient way for getting a string containing only the masking character.
     */
    private static String getMask(int len) {
        char[] current = mask;
        if (current.length < len) {
            synchronized (DataMasker.class) {
                current = mask;
                if (current.length < len) {
                    // extend by multiples of 100 chars
                    final char[] newMask = new char[(len / 100 + 1) * 100];
                    Arrays.fill(newMask, MASK_CHAR);
                    // the field is volatile, so that concurrent executions only see the completely filled array
                    mask = newMask;
                    current = newMask;
                }
            }
        }
        return String.valueOf(current, 0, len);
    }

    /**
     * Adds the given patterns.
     *
     * @param regexes the regexes to add
     */
    public void addPatterns(List<String> regexes) {
        regexes.forEach(this::addPattern);
    }

    /**
     * Adds a pattern, named by its index in {@link #getPatterns()}.
     *
     * @param regex the regex to add
     */
    public void addPattern(String regex) {
        addPattern(null, regex);
    }

    /**
     * Adds all patterns from the given settings whose key starts with
     * {@value com.im.njams.sdk.NjamsSettings#PROPERTY_DATA_MASKING_REGEX_PREFIX}; the rest of the key is the
     * pattern's name.
     *
     * @param settings the settings to read patterns from
     */
    public void addPatterns(ClientSettings settings) {
        forEachSettingsRegex(settings, this::addPattern);
    }

    /**
     * Adds a pattern. A pattern is ignored if its regex is blank, invalid, or already registered (regardless of its
     * name).
     *
     * @param nameOfPattern the name of the pattern; if <code>null</code> or empty, its index in
     *        {@link #getPatterns()} is used
     * @param regex the regex to add
     */
    public void addPattern(String nameOfPattern, String regex) {
        if (StringUtils.isBlank(regex)) {
            LOG.debug("Skipping empty regex for pattern \"{}\"", nameOfPattern);
            return;
        }
        synchronized (this) {
            if (containsRegex(patterns, regex)) {
                LOG.debug("Skipping masking pattern \"{}\": regex \"{}\" is already registered", nameOfPattern, regex);
                return;
            }
            final DataMaskingType added = newPattern(nameOfPattern, regex, patterns.size());
            if (added != null) {
                clientPatterns.add(added);
                rebuild();
            }
        }
    }

    /**
     * Returns an immutable snapshot of the registered patterns.
     *
     * @return the registered patterns
     */
    public List<DataMaskingType> getPatterns() {
        return patterns;
    }

    /**
     * Removes all patterns added through the {@code add*} methods. Patterns the SDK applies from the instance's
     * settings and configuration stay.
     */
    public synchronized void removePatterns() {
        clientPatterns.clear();
        rebuild();
    }

    /**
     * Replaces the patterns taken from the instance's settings and configuration; client-added patterns stay.
     */
    synchronized void replaceConfiguredPatterns(ClientSettings settings, List<String> configurationRegexes) {
        configuredPatterns.clear();
        forEachSettingsRegex(settings, this::addConfigured);
        configurationRegexes.forEach(regex -> addConfigured(null, regex));
        rebuild();
    }

    /**
     * Removes the patterns taken from the instance's settings and configuration; client-added patterns stay.
     */
    synchronized void clearConfiguredPatterns() {
        configuredPatterns.clear();
        rebuild();
    }

    // caller holds the lock
    private void addConfigured(String nameOfPattern, String regex) {
        if (StringUtils.isBlank(regex) || containsRegex(configuredPatterns, regex)) {
            return;
        }
        final DataMaskingType added = newPattern(nameOfPattern, regex, configuredPatterns.size());
        if (added != null) {
            configuredPatterns.add(added);
        }
    }

    // caller holds the lock
    private void rebuild() {
        final List<DataMaskingType> all = new ArrayList<>(configuredPatterns);
        for (DataMaskingType clientPattern : clientPatterns) {
            if (!containsRegex(all, clientPattern.getRegex())) {
                all.add(clientPattern);
            }
        }
        patterns = Collections.unmodifiableList(all);
    }

    private static DataMaskingType newPattern(String nameOfPattern, String regex, int index) {
        try {
            final String name = nameOfPattern != null && !nameOfPattern.isEmpty() ? nameOfPattern : "" + index;
            final DataMaskingType pattern = new DataMaskingType(name, regex);
            LOG.info("Added masking pattern \"{}\" with regex: \"{}\"", pattern.getNameOfPattern(), pattern.getRegex());
            return pattern;
        } catch (Exception e) {
            LOG.error("Could not add pattern {}", regex, e);
            return null;
        }
    }

    private static boolean containsRegex(List<DataMaskingType> types, String regex) {
        for (DataMaskingType type : types) {
            if (type.getRegex().equals(regex)) {
                return true;
            }
        }
        return false;
    }

    private static void forEachSettingsRegex(ClientSettings settings, BiConsumer<String, String> consumer) {
        for (Map.Entry<String, String> entry : settings) {
            if (entry.getKey().startsWith(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX)) {
                consumer.accept(entry.getKey().substring(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX.length()),
                    entry.getValue());
            }
        }
    }
}
```

- [ ] **Step 5: Deprecate `DataMasking` and delegate to a JVM-wide `DataMasker`.** Replace the class body (keep header, package, `@author`); remove now-unused imports (`Arrays`, `CopyOnWriteArrayList`, `Matcher`, `Map`, `Collections`, `NjamsSettings`, `StringUtils`, logger):

```java
/**
 * JVM-wide data masking, shared by all client instances.
 *
 * @author pnientiedt
 * @deprecated Masking is scoped to the {@link com.im.njams.sdk.Njams} instance since 6.1.0: use the masker returned
 *             by {@code njams.configuration().dataMasking()} ({@link DataMasker}). Patterns registered here still
 *             apply to every instance in the JVM until this class is removed.
 */
@Deprecated(since = "6.1.0", forRemoval = true)
public class DataMasking {

    private static final DataMasker JVM_WIDE = new DataMasker();

    /**
     * Masks a string with the JVM-wide patterns only.
     *
     * @param inString String to apply datamasking to
     * @return String with applied datamasking
     * @deprecated See {@link DataMasking}; use {@link DataMasker#maskString(String)}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static String maskString(final String inString) {
        return JVM_WIDE.maskString(inString);
    }

    /** ... @deprecated See {@link DataMasking}; use {@link DataMasker#addPatterns(List)}. */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void addPatterns(List<String> patterns) {
        JVM_WIDE.addPatterns(patterns);
    }

    /** ... @deprecated See {@link DataMasking}; use {@link DataMasker#addPattern(String)}. */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void addPattern(String pattern) {
        JVM_WIDE.addPattern(pattern);
    }

    /** ... @deprecated See {@link DataMasking}; use {@link DataMasker#addPatterns(ClientSettings)}. */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void addPatterns(ClientSettings settings) {
        JVM_WIDE.addPatterns(settings);
    }

    /**
     * Returns an immutable snapshot of the JVM-wide patterns.
     * ... @deprecated See {@link DataMasking}; use {@link DataMasker#getPatterns()}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static List<DataMaskingType> getPatterns() {
        return JVM_WIDE.getPatterns();
    }

    /** ... @deprecated See {@link DataMasking}; use {@link DataMasker#addPattern(String, String)}. */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void addPattern(String nameOfPattern, String regexAsString) {
        JVM_WIDE.addPattern(nameOfPattern, regexAsString);
    }

    /** Removes all JVM-wide patterns. ... @deprecated See {@link DataMasking}; use {@link DataMasker#removePatterns()}. */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void removePatterns() {
        JVM_WIDE.removePatterns();
    }
}
```

Keep each method's existing Javadoc description and `@param`/`@return` (`...` above); only append the `@deprecated` tag and adjust "live view" → "immutable snapshot" on `getPatterns()`. Import `com.im.njams.sdk.DataMasker`. Do **not** add a private constructor: `DataMasking` has an implicit public one today, and removing it would be breaking.

- [ ] **Step 6: Run the new and the baseline tests.**

Run: `mvn test -Dtest=DataMaskerTest+DataMaskingTest+ExtractHandlerTest+JobAttributesTest -pl njams-sdk`
Expected: PASS (all, `DataMaskingTest` unchanged).

- [ ] **Step 7: Commit** (via `njams-commit`):

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/DataMasker.java njams-sdk/src/test/java/com/im/njams/sdk/DataMaskerTest.java njams-sdk/src/main/java/com/im/njams/sdk/logmessage/DataMasking.java
git commit -m "SDK-490 Add DataMasker and deprecate the static DataMasking API, which now delegates to a JVM-wide DataMasker."
```

---

### Task 2: Wire one masker per `Njams` instance into the runtime path

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsConfiguration.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java` (lines 255, 326, 571, 581, 592, 609, 628, 648, 684, 735)
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobAttributes.java:69`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ExtractHandler.java:152,389-390`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/DataMaskingPerInstanceTest.java`
- Modify (permitted): `njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java:478-538`

**Interfaces:**
- Consumes: `DataMasker(boolean)`, `replaceConfiguredPatterns(ClientSettings, List<String>)`, `clearConfiguredPatterns()`, `maskString(String)` from Task 1.
- Produces: `public DataMasker NjamsConfiguration.dataMasking()`; package-private `String JobImpl.mask(String)`.

- [ ] **Step 1: Write the failing `DataMaskingPerInstanceTest`.**

```java
package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.settings.ClientSettings;

@SuppressWarnings("removal")
public class DataMaskingPerInstanceTest {

    private final List<Njams> started = new ArrayList<>();

    @Before
    public void clearJvmWidePatterns() {
        DataMasking.removePatterns();
    }

    @After
    public void stopAll() {
        started.forEach(n -> {
            if (n.isStarted()) {
                n.stop();
            }
        });
        DataMasking.removePatterns();
    }

    @Test
    public void patternsOfOneInstanceDoNotAffectAnother() {
        Njams a = start("A", settingsWithRegex("secretA"));
        Njams b = start("B", settingsWithRegex("secretB"));

        assertEquals("******* secretB", recordAttribute(a, "secretA secretB"));
        assertEquals("secretA *******", recordAttribute(b, "secretA secretB"));
    }

    @Test
    public void restartReplacesConfiguredPatterns() {
        Njams njams = newNjams("R", TestSender.getSettings());
        // start() replaces configuration().get() with what the provider loads; the memory provider caches the
        // Configuration it creates on first load, so edit that object
        njams.configuration().get().getConfigurationProvider().loadConfiguration()
            .setDataMasking(Collections.singletonList("old"));
        startTracked(njams);
        assertEquals("*** new", njams.configuration().dataMasking().maskString("old new"));

        njams.stop();
        njams.configuration().get().setDataMasking(Collections.singletonList("new"));
        startTracked(njams);

        assertEquals("old ***", njams.configuration().dataMasking().maskString("old new"));
    }

    @Test
    public void clientPatternAddedBeforeStartSurvivesStart() {
        Njams njams = newNjams("C", settingsWithRegex("configured"));
        njams.configuration().dataMasking().addPattern("client");
        startTracked(njams);

        assertEquals("****** **********", recordAttribute(njams, "client configured"));
    }

    @Test
    public void disabledMaskingOnOneInstanceOnly() {
        ClientSettings disabled = settingsWithRegex("secret");
        disabled.put(NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, "false");
        Njams off = start("Off", disabled);
        Njams on = start("On", settingsWithRegex("secret"));

        assertEquals("secret", recordAttribute(off, "secret"));
        assertEquals("******", recordAttribute(on, "secret"));
    }

    @Test
    public void jvmWidePatternsStillApplyToEveryInstance() {
        DataMasking.addPattern("legacy");
        Njams njams = start("L", TestSender.getSettings());

        assertEquals("******", recordAttribute(njams, "legacy"));
    }

    private Njams start(String name, ClientSettings settings) {
        return startTracked(newNjams(name, settings));
    }

    private Njams newNjams(String name, ClientSettings settings) {
        Njams njams = new Njams(Path.of("SDK490", name), "1.0.0", "SDK", settings);
        njams.model().create("PROCESS");
        started.add(njams);
        return njams;
    }

    private static Njams startTracked(Njams njams) {
        njams.start();
        return njams;
    }

    private static String recordAttribute(Njams njams, String value) {
        Job job = njams.model().get("PROCESS").createJob();
        job.attributes().add("key", value);
        return job.attributes().get("key");
    }

    private static ClientSettings settingsWithRegex(String regex) {
        ClientSettings settings = TestSender.getSettings();
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + "r", regex);
        return settings;
    }
}
```

Verified API facts: `NjamsModel.get(String)` looks up a process directly below the client path; `TestSender.getSettings()` selects the `memory` configuration provider, whose `loadConfiguration()` creates its `Configuration` once and returns the same object on every later load. Note that the pre-existing `NjamsTest.disableDataMaskingDisablesAllDataMasking` sets its list on the pre-start `Configuration`, which `start()` discards — it passes for the "disabled" reason anyway; leave it as described in Step 6.

- [ ] **Step 2: Run it to verify it fails.**

Run: `mvn test-compile -pl njams-sdk`
Expected: compilation FAILURE — `cannot find symbol: method dataMasking()`.

- [ ] **Step 3: `NjamsConfiguration` owns the masker.** Add import `java.util.List`, remove import `com.im.njams.sdk.logmessage.DataMasking`, and:

```java
    private final ClientSettings settings;
    private final DataMasker dataMasker = new DataMasker(true);
    private Configuration configuration;
```

```java
    /**
     * Returns the data masking of this client instance. It masks with the patterns from this instance's settings
     * and configuration, applied on every start, and with patterns client code adds to it.
     *
     * @return the data masker of this instance, never <code>null</code>
     */
    public DataMasker dataMasking() {
        return dataMasker;
    }
```

Replace `initializeDataMasking()`:

```java
    /**
     * Applies the masking patterns from the settings and the configuration; called by Njams.start().
     */
    void initializeDataMasking() {
        if (!settings.getBool(NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, true)) {
            LOG.info("DataMasking is disabled.");
            dataMasker.clearConfiguredPatterns();
            return;
        }
        final List<String> fromConfiguration = configuration.getDataMasking();
        if (!fromConfiguration.isEmpty()) {
            LOG.warn("DataMasking via the configuration is deprecated but will be used as well. Use settings " +
                    "with the properties \n{} = " +
                    "\"true\" \nand multiple \n{}<YOUR-REGEX-NAME> = <YOUR-REGEX> \nfor this.",
                NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX);
        }
        dataMasker.replaceConfiguredPatterns(settings, fromConfiguration);
    }
```

Update the class Javadoc ("Owns the server-driven runtime Configuration … (log mode, process exclusions, tracepoints)") to also mention data masking.

- [ ] **Step 4: `JobImpl` captures the masker once.** Add import `com.im.njams.sdk.DataMasker`, a field next to `jobSettings`:

```java
    private final DataMasker dataMasker;
```

In the constructor, directly after `njams = processModel.getNjams();`:

```java
        dataMasker = njams.configuration().dataMasking();
```

(Must precede `attributes.addInternal(RECORDED_ATTRIBUTE, ...)`, which masks.) Add next to `getNjams()`:

```java
    /**
     * Masks the given value with the data masking of the client instance owning this job.
     *
     * @param value the value to mask
     * @return the masked value
     */
    String mask(String value) {
        return dataMasker.maskString(value);
    }
```

- [ ] **Step 5: Replace the static calls.**
  - `ActivityImpl`: each `DataMasking.maskString(` → `job.mask(` (10 sites listed above); remove the `DataMasking` import if present (same package — no import, just verify no reference remains).
  - `JobAttributes.java:69`: `DataMasking.maskString(jobImpl.limitPayload(value))` → `jobImpl.mask(jobImpl.limitPayload(value))`.
  - `ExtractHandler.java:152`: `DataMasking.maskString(data)` → `job.mask(data)`.
  - `ExtractHandler.java:389-390`: change `private static void setAttributes(Job job, ...)` to `setAttributes(JobImpl job, ...)` (all callers already pass `JobImpl`) and `DataMasking.maskString(uncheckedvalue)` → `job.mask(uncheckedvalue)`. Remove the `Job` import if now unused.

Run `grep -rn "DataMasking\." njams-sdk/src/main/java` — expected: matches only inside `DataMasker.java` (the JVM-wide call) and `DataMasking.java`.

- [ ] **Step 6: Adjust the four permitted `NjamsTest` tests.** In `setDataMaskingViaSettings`, `disableDataMaskingViaSettings`, `disableDataMaskingDisablesAllDataMasking`, `enableDataMaskingWithoutRegex`: replace the final `DataMasking.maskString("Hello")` with `njams.configuration().dataMasking().maskString("Hello")`. Leave the `DataMasking.removePatterns()` lines and expected values unchanged. Add `@SuppressWarnings("removal")` on those methods if the compiler warns.

- [ ] **Step 7: Run the targeted tests.**

Run: `mvn test -Dtest=DataMaskingPerInstanceTest+NjamsTest+DataMaskerTest+DataMaskingTest+ExtractHandlerTest+JobAttributesTest+JobImplTest -pl njams-sdk`
Expected: PASS.

- [ ] **Step 8: Run the full module suite** (a mocked `Njams` whose `configuration()` returns `null` would now NPE in the `JobImpl` constructor — this run catches it; if it happens, stop and report rather than adding a null fallback silently).

Run: `mvn test -pl njams-sdk`
Expected: PASS, no new failures.

- [ ] **Step 9: Commit** (via `njams-commit`):

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsConfiguration.java njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobAttributes.java njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ExtractHandler.java njams-sdk/src/test/java/com/im/njams/sdk/DataMaskingPerInstanceTest.java njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java
git commit -m "SDK-490 Mask with one DataMasker per Njams instance instead of the JVM-wide pattern set."
```

---

### Task 3: Documentation and verification

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java:575-601`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/configuration/Configuration.java:205-224`
- Modify: `wiki/FAQ.md` (section "How to use data masking", line ~647; "Breaking changes in 6.1", line ~3)

- [ ] **Step 1: Javadoc.**
  - `NjamsSettings.PROPERTY_DATA_MASKING_ENABLED` / `PROPERTY_DATA_MASKING_REGEX_PREFIX`: state that the patterns apply to the `Njams` instance whose settings define them, and that `enabled=false` does not affect patterns client code adds via `njams.configuration().dataMasking()`.
  - `Configuration.getDataMasking()/setDataMasking(...)`: state the patterns apply to the owning `Njams` instance and are (re)applied on each start.
  - Re-read Javadoc around every changed member (`DataMasking`, `NjamsConfiguration`, `JobImpl`) for stale "JVM-wide" wording.

- [ ] **Step 2: FAQ.**
  - "How to use data masking": add that rules apply per `Njams` instance; add a subsection "Adding rules from code":

```markdown
### Adding rules from code

Patterns can also be added to a running or not yet started instance; they apply to that instance only and stay until
removed:

```java
njams.configuration().dataMasking().addPattern("maskIban", "IBAN: \\p{Alpha}{2}\\p{Digit}+");
```

The static `DataMasking` methods are deprecated since 6.1.0: patterns registered there apply to every instance in the
JVM.
```

  - Under "Breaking changes in 6.1" add a short bullet (or a "Deprecations in 6.1" paragraph after the list): `DataMasking` (static, JVM-wide) is deprecated for removal; use `njams.configuration().dataMasking()`. Masking patterns from settings and configuration now apply only to their own instance.
  - Fix the stale line 655 ("are static and active for the lifetime of the process") → "apply to the instance whose settings define them".

- [ ] **Step 3: Verify.**

Run: `mvn validate -Pcheckstyle -pl njams-sdk`
Expected: BUILD SUCCESS.

Run: `rm -rf njams-sdk/target/reports/apidocs && mvn javadoc:javadoc -pl njams-sdk`
Expected: BUILD SUCCESS, no errors; check `njams-sdk/target/reports/apidocs/com/im/njams/sdk/logmessage/DataMasking.html` shows every method as deprecated with its pointer.

- [ ] **Step 4: Commit** (via `njams-commit`; this is the finalizing commit, `#comment` is appropriate):

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/NjamsSettings.java njams-sdk/src/main/java/com/im/njams/sdk/configuration/Configuration.java wiki/FAQ.md
git commit -m "SDK-490 #comment Scope data masking to the Njams instance; the static DataMasking API is deprecated for removal."
```
