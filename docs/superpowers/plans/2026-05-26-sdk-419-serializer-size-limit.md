# SDK-419 — Serializer size-limit overload (breaking change) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Every commit message MUST start with `SDK-419 #comment` per project convention.

**Goal:** Add a size-aware `serialize(T, int sizeLimit)` to the `Serializer` interface so that input/output and tracing payloads can stop producing string output once the configured payload limit is reached, instead of building the full string and truncating afterwards.

**Architecture:**
- Flip which method on `Serializer<T>` is abstract: the new `serialize(T, int)` becomes the abstract method that every implementation must provide; the legacy `serialize(T)` becomes a `default` that delegates to `serialize(t, Integer.MAX_VALUE)` (meaning "no effective limit").
- `JsonSerializer<T>` overrides both methods. The new method uses a `LimitedWriter` that aborts Jackson's streaming once `sizeLimit` characters have been written.
- `StringSerializer<T>` overrides only the new method (substring after `toString()`).
- `Njams.serialize(T)` gets a sibling overload `Njams.serialize(T, int)`.
- `ActivityImpl.processInput`/`processOutput` pass an effective size hint of `payloadLimit + 1` to `Njams.serialize` so that downstream `JobImpl.limitPayload` still detects overrun for both `truncate` and `discard` modes. The hint is `0` (= unlimited) whenever `needsData(extract)` is true, so XPath / regex / JMESPath rules continue to see the complete serialized data. `processStartData` is unchanged (startData is never subject to `limitPayload`).
- This is a **breaking change** for any external code that supplies a `Serializer<T>` (lambdas, anonymous classes, named subclasses) — they must now implement `serialize(T, int)`. The ticket carries the `breaking-change` label.

**Tech Stack:** Java 11, Maven 3.8+, JUnit 4, Mockito, Jackson (`com.fasterxml.jackson.databind.ObjectMapper`/`ObjectWriter`).

**Files this plan touches (created or modified):**

| File | Why |
|------|-----|
| `njams-sdk/src/main/java/com/im/njams/sdk/serializer/Serializer.java` | Flip abstract method; add default delegate |
| `njams-sdk/src/main/java/com/im/njams/sdk/serializer/JsonSerializer.java` | Override both methods; add `LimitedWriter` |
| `njams-sdk/src/main/java/com/im/njams/sdk/serializer/StringSerializer.java` | Override `serialize(T, int)` (substring truncate) |
| `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java` | Add `serialize(T, int)` overload (lines ~1223–1255) |
| `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java` | Add package-private `getSerializeSizeHint()` |
| `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java` | `processInput`/`processOutput` use new overload conditional on `needsData(extract)` |
| `njams-sdk/src/test/java/com/im/njams/sdk/serializer/JsonSerializerTest.java` | New tests for `serialize(T, int)` |
| `njams-sdk/src/test/java/com/im/njams/sdk/serializer/StringSerializerTest.java` | **New file** — covers `serialize(T, int)` |
| `njams-sdk/src/test/java/com/im/njams/sdk/serializer/SerializerDefaultTest.java` | **New file** — covers interface `default` delegation |
| `njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java` | Update lambdas (now two-arg); add tests for `Njams.serialize(T, int)` |
| `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/LimitPayloadTest.java` | Extend to verify discard/truncate still produce same final strings after the wiring change |
| `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/ActivityImplExtractDataTest.java` | **New file** — characterization: extract rules always receive the **untruncated** serialized data; new wiring still passes the size hint when `needsData == false` |

---

## Pre-flight (no code)

- [ ] **Step 0.1: Confirm the working branch is `6.0-dev` and that `master` has no unmerged commits**

Run:
```
git rev-parse --abbrev-ref HEAD
git fetch origin master
git log --oneline HEAD..origin/master
```
Expected: branch is `6.0-dev`; the last command produces no output. If `master` has unmerged commits, stop and ask the user before continuing.

- [ ] **Step 0.2: Confirm baseline build is green**

Run:
```
mvn clean test -pl njams-sdk -DfailIfNoTests=false
```
Expected: `BUILD SUCCESS`, all existing tests green. If any are red on a clean checkout, stop and ask the user — do not start work on a red baseline.

- [ ] **Step 0.3: Jira admin for SDK-419**

Using the Atlassian MCP tools (`mcp__claude_ai_Atlassian__*`, cloudId `salesfive.atlassian.net`):
1. Look up the current Atlassian user via `atlassianUserInfo` → confirm `account_id = 61ae3124ef18ca0071cd158d` (Christian Winkler).
2. `getJiraIssue` SDK-419, confirm status is `Open` and assignee is `null`.
3. `getTransitionsForJiraIssue` SDK-419 → find the transition to `In Progress`.
4. `transitionJiraIssue` SDK-419 to `In Progress`.
5. `editJiraIssue` SDK-419: set assignee to `61ae3124ef18ca0071cd158d`, set `fixVersion` to `6.0.0` (root pom version `6.0.0-SNAPSHOT` minus `-SNAPSHOT`), add the `breaking-change` label (keep existing labels if any).

Expected: ticket is In Progress, assigned to Christian Winkler, has label `breaking-change` and fix version `6.0.0`.

- [ ] **Step 0.4: Confirm with the user that updating `NjamsTest.testSerializer` and other test lambdas is OK**

Per `CLAUDE.md`: "Existing test cases must never be modified without explicit user permission". The interface change makes the existing `Serializer<List> expResult = l -> "list";` lambda in `NjamsTest.testSerializer` (line 70) and `a -> a.getClass().getSimpleName()` (line 72) fail to compile because the SAM target shifts to `serialize(T, int)`. We must update these lambdas to keep the test's intent. Read out the affected lines to the user and get explicit OK before modifying. If the user declines, stop and re-plan.

---

## Task 1: Baseline characterization tests for the existing extract-data contract

The single most important invariant SDK-419 must preserve is: **extract rules see the full, untruncated serialized data**. There is no direct test today that nails this down. Add it first, while the code still has the original behaviour, then make sure it stays green after every later change.

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/ActivityImplExtractDataTest.java`

- [ ] **Step 1.1: Write the characterization test**

```java
/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 * ...same header omitted for brevity... (TEST FILE — copyright header NOT required per CLAUDE.md)
 */
package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.settings.ClientSettings;

public class ActivityImplExtractDataTest extends AbstractTest {

    private static final String LONG_PAYLOAD =
            "<root><value>NEEDLE_AT_THE_END_OF_THIS_VERY_LONG_STRING_PAST_THE_PAYLOAD_LIMIT</value></root>";

    private ActivityConfiguration activityConfig;

    @Before
    public void setupExtract() {
        ClientSettings settings = njams.getSettings();
        // discard whenever payload > 10
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, "truncate");
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, "10");

        Extract extract = new Extract();
        ExtractRule rule = new ExtractRule();
        rule.setRuleType(RuleType.REGEXP);
        rule.setInout("in");
        rule.setAttribute("matched");
        rule.setRule("NEEDLE_AT_THE_END[A-Z_]+");
        extract.getExtractRules().add(rule);

        activityConfig = new ActivityConfiguration();
        activityConfig.setExtract(extract);
    }

    @Test
    public void extractRulesReceiveFullUntruncatedSerializedData() {
        JobImpl job = createDefaultJob();
        job.getProcessConfiguration().getActivities()
                .put(createDefaultActivity(job).getModelId(), activityConfig);
        job.start();

        ActivityImpl activity = (ActivityImpl) createDefaultActivity(job);
        activity.processInput(LONG_PAYLOAD);

        // input is truncated for storage...
        assertEquals(true, activity.getInput().length() <= 10 + JobImpl.PAYLOAD_TRUNCATED_SUFFIX.length() + 5);
        // ...but the regex (operating on full data) extracted the needle past the limit.
        String matched = activity.getAttributes().get("matched");
        assertNotNull("extract rule should have matched the needle past the payload limit", matched);
        assertEquals("NEEDLE_AT_THE_END_OF_THIS_VERY_LONG_STRING_PAST_THE_PAYLOAD_LIMIT", matched);
    }
}
```

> Notes:
> - The test does not have a copyright header because test files are exempt per `CLAUDE.md`.
> - `AbstractTest`, `createDefaultJob()`, `createDefaultActivity()` are pre-existing helpers (see `LimitPayloadTest` for a working pattern of using them with `ClientSettings`).
> - `JobImpl.getProcessConfiguration()` may not be the exact accessor name — if compile fails on that line, look in `JobImpl` for the analogue used by `ExtractHandler.handleExtract(JobImpl, ActivityImpl, ExtractSource, String)` (line 115) and adapt. The intent is to register the extract for the activity so `ActivityImpl.processInput` triggers `ExtractHandler.handleExtract`.

- [ ] **Step 1.2: Run the test on the unchanged production code and verify it PASSES**

Run:
```
mvn test -Dtest=ActivityImplExtractDataTest -pl njams-sdk
```
Expected: BUILD SUCCESS, 1 test, 0 failures. If it fails on the current code, fix the test until it asserts the **current** (correct) behaviour before continuing. This test is the green safety net for Task 6.

- [ ] **Step 1.3: Commit**

```
git add njams-sdk/src/test/java/com/im/njams/sdk/logmessage/ActivityImplExtractDataTest.java
git commit -m "SDK-419 #comment Characterize extract data is never truncated"
```

---

## Task 2: Refactor the `Serializer` interface

Flip the abstract method. After this task the project still compiles and all existing tests still pass, but the type system now requires `serialize(T, int)` from every implementor.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/serializer/Serializer.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/serializer/JsonSerializer.java` (add minimal `serialize(T, int)` that delegates to `serialize(T)`)
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/serializer/StringSerializer.java` (same minimal delegation)
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java` (lambdas now take two args)
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/serializer/SerializerDefaultTest.java`

- [ ] **Step 2.1: Write the failing test for the default delegation**

Create `njams-sdk/src/test/java/com/im/njams/sdk/serializer/SerializerDefaultTest.java`:
```java
package com.im.njams.sdk.serializer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SerializerDefaultTest {

    /** Verifies the interface default routes serialize(T) through serialize(T, Integer.MAX_VALUE). */
    @Test
    public void defaultSerializeDelegatesToSizeAwareWithMaxValue() {
        final int[] capturedLimit = {-1};
        Serializer<String> s = (value, sizeLimit) -> {
            capturedLimit[0] = sizeLimit;
            return value;
        };
        String result = s.serialize("hello");
        assertEquals("hello", result);
        assertEquals(Integer.MAX_VALUE, capturedLimit[0]);
    }
}
```

- [ ] **Step 2.2: Run the test and verify it fails to compile**

Run:
```
mvn test -Dtest=SerializerDefaultTest -pl njams-sdk
```
Expected: compile error — the lambda has two parameters but `Serializer` currently has a single-arg abstract method. This is the expected RED state.

- [ ] **Step 2.3: Rewrite `Serializer.java`**

Replace the file contents (keep the copyright header from the existing file) with:
```java
package com.im.njams.sdk.serializer;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Serializer interface. Implementations turn a typed object into a {@link String} representation,
 * optionally respecting a maximum string length to avoid producing very large strings that will
 * be truncated downstream.
 *
 * <p>Implementations must declare {@link #serialize(Object, int)}, but honouring the
 * {@code sizeLimit} argument is <strong>optional</strong>. Honouring it is encouraged because it
 * can save significant CPU and memory when serializing large objects that would otherwise be
 * built in full and then truncated. If an implementation cannot easily limit its output, it may
 * ignore {@code sizeLimit} entirely and serialize the whole object: the SDK applies final
 * truncation separately, exactly as it did before this argument existed, so correctness is
 * unaffected. For the same reason an over-estimating implementation that stops at roughly
 * {@code sizeLimit + X} characters is also perfectly acceptable.</p>
 *
 * <p>The single-argument {@link #serialize(Object)} is a convenience that delegates with no
 * effective limit.</p>
 *
 * <p>The SDK ships two implementations:</p>
 * <ul>
 *   <li>{@link JsonSerializer} — serializes via Jackson and honours {@code sizeLimit} effectively,
 *       aborting the stream once the limit is reached so a large object is never fully
 *       materialised.</li>
 *   <li>{@link StringSerializer} — serializes via {@link Object#toString()}. Because it must build
 *       the complete {@code toString()} result before it can clip it, it <strong>cannot</strong>
 *       limit work or memory during serialization; passing {@code sizeLimit} only trims the
 *       already-allocated string. Prefer a serializer that truncates effectively (such as
 *       {@link JsonSerializer}, or a custom implementation) whenever that is feasible, and reserve
 *       {@link StringSerializer} for cases where no better option exists.</li>
 * </ul>
 *
 * @author stkniep
 * @param <T> generic
 */
public interface Serializer<T> {

    /**
     * Serialize given Object to String with no effective size limit.
     *
     * <p>The default implementation delegates to {@link #serialize(Object, int)} passing
     * {@link Integer#MAX_VALUE} as the size limit. Implementations may override this method
     * to provide a faster unlimited path that skips size-tracking bookkeeping.</p>
     *
     * @param object Object to be serialized
     * @return String representation for the given Object
     * @throws NjamsSdkRuntimeException if serialization fails
     */
    default String serialize(T object) throws NjamsSdkRuntimeException {
        return serialize(object, Integer.MAX_VALUE);
    }

    /**
     * Serialize given Object to String, respecting the given size limit when greater than 0.
     *
     * <p>Honouring {@code sizeLimit} is <strong>optional but recommended</strong>: stopping output
     * once roughly {@code sizeLimit} characters have been produced avoids building large strings
     * that are discarded by downstream truncation, saving CPU and memory. Implementations that
     * cannot easily do this may ignore {@code sizeLimit} and return the full serialization — the
     * SDK truncates the result separately afterwards, so behaviour is still correct, just less
     * efficient. An over-estimating implementation that stops near {@code sizeLimit + X} is also
     * acceptable; the returned string may therefore exceed {@code sizeLimit} and callers are
     * expected to apply final truncation. A {@code sizeLimit} of {@link Integer#MAX_VALUE} or any
     * non-positive value means "no limit".</p>
     *
     * @param object    Object to be serialized
     * @param sizeLimit Recommended (not mandatory) upper bound for the returned string length when
     *                  positive and less than {@link Integer#MAX_VALUE}; otherwise the limit is
     *                  ignored. Implementations may ignore or over-estimate this value
     * @return String representation for the given Object, possibly clipped near {@code sizeLimit}
     * @throws NjamsSdkRuntimeException if serialization fails
     */
    String serialize(T object, int sizeLimit) throws NjamsSdkRuntimeException;
}
```

- [ ] **Step 2.4: Add a minimal `serialize(T, int)` override in `JsonSerializer`**

In `njams-sdk/src/main/java/com/im/njams/sdk/serializer/JsonSerializer.java`, immediately after the existing `serialize(T)` method, add:
```java
    /**
     * {@inheritDoc}
     *
     * <p>This minimal implementation ignores {@code sizeLimit}; size-aware serialization is added
     * in a follow-up step.</p>
     */
    @Override
    public String serialize(final T object, final int sizeLimit) throws NjamsSdkRuntimeException {
        return serialize(object);
    }
```

- [ ] **Step 2.5: Add a minimal `serialize(T, int)` override in `StringSerializer`**

In `njams-sdk/src/main/java/com/im/njams/sdk/serializer/StringSerializer.java`, immediately after the existing `serialize(T)` method, add:
```java
    /**
     * {@inheritDoc}
     *
     * <p>This minimal implementation ignores {@code sizeLimit}; size-aware serialization is added
     * in a follow-up step.</p>
     */
    @Override
    public String serialize(final T t, final int sizeLimit) throws NjamsSdkRuntimeException {
        return serialize(t);
    }
```

- [ ] **Step 2.6: Update the lambdas in `NjamsTest.testSerializer`**

In `njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java` lines 70, 72, 73, change:
```java
        final Serializer<List> expResult = l -> "list";

        instance.addSerializer(ArrayList.class, a -> a.getClass().getSimpleName());
        instance.addSerializer(List.class, expResult);
```
to:
```java
        final Serializer<List> expResult = (l, sizeLimit) -> "list";

        instance.addSerializer(ArrayList.class, (a, sizeLimit) -> a.getClass().getSimpleName());
        instance.addSerializer(List.class, expResult);
```

- [ ] **Step 2.7: Run the full SDK test suite and verify everything is green**

Run:
```
mvn test -pl njams-sdk
```
Expected: BUILD SUCCESS, including `SerializerDefaultTest`, `JsonSerializerTest`, `LimitPayloadTest`, `NjamsTest`, `ActivityImplExtractDataTest`. Any failure here means Task 2 went wrong — fix before moving on.

- [ ] **Step 2.8: Run checkstyle and javadoc**

Run:
```
mvn checkstyle:check -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```
Expected: both green. Fix any reported issues (most likely Javadoc on the new `serialize(T, int)` overrides) before continuing.

- [ ] **Step 2.9: Commit**

```
git add njams-sdk/src/main/java/com/im/njams/sdk/serializer/Serializer.java \
        njams-sdk/src/main/java/com/im/njams/sdk/serializer/JsonSerializer.java \
        njams-sdk/src/main/java/com/im/njams/sdk/serializer/StringSerializer.java \
        njams-sdk/src/test/java/com/im/njams/sdk/serializer/SerializerDefaultTest.java \
        njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java
git commit -m "SDK-419 #comment Flip Serializer abstract method to serialize(T, int)"
```

---

## Task 3: Size-aware `JsonSerializer.serialize(T, int)`

Now make `JsonSerializer` actually honour the limit using a `LimitedWriter` that aborts Jackson when enough characters have been written.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/serializer/JsonSerializer.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/serializer/JsonSerializerTest.java`

- [ ] **Step 3.1: Write failing tests for the size-aware path**

Append to `JsonSerializerTest.java` (inside the same class):
```java
    @Test
    public void serializeWithLimitReturnsAtMostRoughlyLimit() {
        JsonSerializer<int[]> ser = new JsonSerializer<>();
        int[] data = new int[10_000]; // serializes to a long JSON array
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        String full = ser.serialize(data);
        String limited = ser.serialize(data, 100);

        // size-aware result is much shorter than the full result
        assertTrue("full length should be much greater than 100, got " + full.length(), full.length() > 1000);
        assertTrue("limited length should not exceed ~limit + small Jackson buffering, got "
                + limited.length(), limited.length() <= 200);
        // and it is a prefix of (the start of) the full string
        assertTrue("limited result should be a prefix of the full result",
                full.startsWith(limited.substring(0, Math.min(limited.length(), 50))));
    }

    @Test
    public void serializeWithMaxValueLimitMatchesUnlimited() {
        JsonSerializer<String[]> ser = new JsonSerializer<>();
        String[] words = {"alpha", "beta", "gamma"};
        assertEquals(ser.serialize(words), ser.serialize(words, Integer.MAX_VALUE));
    }

    @Test
    public void serializeWithZeroOrNegativeLimitMatchesUnlimited() {
        JsonSerializer<String[]> ser = new JsonSerializer<>();
        String[] words = {"alpha", "beta", "gamma"};
        assertEquals(ser.serialize(words), ser.serialize(words, 0));
        assertEquals(ser.serialize(words), ser.serialize(words, -1));
    }

    @Test
    public void serializeWithNullObjectReturnsEmptyJsonObject() {
        JsonSerializer<Object> ser = new JsonSerializer<>();
        assertEquals("{}", ser.serialize(null, 50));
    }
```
Add the missing static imports `org.junit.Assert.assertEquals` and `org.junit.Assert.assertTrue` if they are not already imported via the existing `import static org.junit.Assert.*;`.

- [ ] **Step 3.2: Run the new tests and verify the size-aware test FAILS**

Run:
```
mvn test -Dtest=JsonSerializerTest -pl njams-sdk
```
Expected: `serializeWithLimitReturnsAtMostRoughlyLimit` FAILS (current minimal override returns the full string), the other three PASS.

- [ ] **Step 3.3: Replace `JsonSerializer.java` with the size-aware implementation**

Keep the existing copyright header and the package/imports, then replace the class body to:
```java
public class JsonSerializer<T> implements Serializer<T> {

    private final ObjectMapper objectMapper = JsonSerializerFactory.getDefaultMapper();
    private final ObjectWriter objectWriter = this.objectMapper.writer();

    /**
     * Serialize the given object to a JSON string, with no effective size limit.
     *
     * @param object Object to serialize, may be {@code null}
     * @return JSON representation, or {@code "{}"} if {@code object} is {@code null}
     * @throws NjamsSdkRuntimeException if Jackson fails to serialize the object
     */
    @Override
    public String serialize(final T object) throws NjamsSdkRuntimeException {
        if (object == null) {
            return "{}";
        }
        try {
            final StringWriter writer = new StringWriter();
            objectWriter.writeValue(writer, object);
            return writer.toString();
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Could not serialize object " + object, e);
        }
    }

    /**
     * Serialize the given object to a JSON string, stopping near {@code sizeLimit} characters.
     *
     * <p>The returned string may slightly exceed {@code sizeLimit} due to Jackson's internal
     * buffering. {@code sizeLimit <= 0} or {@code sizeLimit == Integer.MAX_VALUE} mean
     * "no limit" and route to the unlimited fast path.</p>
     *
     * @param object    Object to serialize, may be {@code null}
     * @param sizeLimit Approximate maximum length of the returned string
     * @return JSON representation, possibly clipped near {@code sizeLimit}
     * @throws NjamsSdkRuntimeException if Jackson fails for a reason other than the size limit
     */
    @Override
    public String serialize(final T object, final int sizeLimit) throws NjamsSdkRuntimeException {
        if (object == null) {
            return "{}";
        }
        if (sizeLimit <= 0 || sizeLimit == Integer.MAX_VALUE) {
            return serialize(object);
        }
        final StringWriter buffer = new StringWriter();
        try (LimitedWriter limited = new LimitedWriter(buffer, sizeLimit)) {
            objectWriter.writeValue(limited, object);
        } catch (Exception e) {
            if (!containsSizeLimitReached(e)) {
                throw new NjamsSdkRuntimeException("Could not serialize object " + object, e);
            }
        }
        return buffer.toString();
    }

    private static boolean containsSizeLimitReached(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof LimitedWriter.SizeLimitReached) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * Writer wrapper that aborts once a fixed number of characters have been written.
     */
    private static final class LimitedWriter extends Writer {
        private final Writer delegate;
        private final int limit;
        private int written;

        LimitedWriter(Writer delegate, int limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (written >= limit) {
                throw new SizeLimitReached();
            }
            int allowed = Math.min(len, limit - written);
            delegate.write(cbuf, off, allowed);
            written += allowed;
            if (allowed < len) {
                throw new SizeLimitReached();
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        /** Sentinel thrown when the size limit has been reached. */
        static final class SizeLimitReached extends IOException {
            private static final long serialVersionUID = 1L;
        }
    }
}
```
Add the additional imports:
```java
import java.io.IOException;
import java.io.Writer;
```

- [ ] **Step 3.4: Run the JsonSerializer tests and verify all PASS**

Run:
```
mvn test -Dtest=JsonSerializerTest -pl njams-sdk
```
Expected: 5 tests pass.

- [ ] **Step 3.5: Run checkstyle and javadoc**

Run:
```
mvn checkstyle:check -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```
Expected: both green.

- [ ] **Step 3.6: Commit**

```
git add njams-sdk/src/main/java/com/im/njams/sdk/serializer/JsonSerializer.java \
        njams-sdk/src/test/java/com/im/njams/sdk/serializer/JsonSerializerTest.java
git commit -m "SDK-419 #comment Size-aware JsonSerializer via LimitedWriter"
```

---

## Task 4: Size-aware `StringSerializer.serialize(T, int)`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/serializer/StringSerializer.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/serializer/StringSerializerTest.java`

- [ ] **Step 4.1: Write the failing tests**

Create `StringSerializerTest.java`:
```java
package com.im.njams.sdk.serializer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StringSerializerTest {

    @Test
    public void nullReturnsEmptyString() {
        StringSerializer<Object> ser = new StringSerializer<>();
        assertEquals("", ser.serialize(null));
        assertEquals("", ser.serialize(null, 5));
    }

    @Test
    public void serializeReturnsToString() {
        StringSerializer<Integer> ser = new StringSerializer<>();
        assertEquals("42", ser.serialize(42));
    }

    @Test
    public void serializeWithLimitTruncatesToLimit() {
        StringSerializer<String> ser = new StringSerializer<>();
        assertEquals("abcde", ser.serialize("abcdefghij", 5));
    }

    @Test
    public void serializeWithLimitLongerThanStringReturnsFullString() {
        StringSerializer<String> ser = new StringSerializer<>();
        assertEquals("abc", ser.serialize("abc", 50));
    }

    @Test
    public void serializeWithMaxValueOrNonPositiveLimitReturnsFullString() {
        StringSerializer<String> ser = new StringSerializer<>();
        assertEquals("abcdefghij", ser.serialize("abcdefghij", Integer.MAX_VALUE));
        assertEquals("abcdefghij", ser.serialize("abcdefghij", 0));
        assertEquals("abcdefghij", ser.serialize("abcdefghij", -1));
    }
}
```

- [ ] **Step 4.2: Run and verify the truncate test FAILS**

Run:
```
mvn test -Dtest=StringSerializerTest -pl njams-sdk
```
Expected: `serializeWithLimitTruncatesToLimit` fails (minimal override ignores the limit).

- [ ] **Step 4.3: Implement size-aware `serialize(T, int)` in `StringSerializer`**

Replace the minimal `serialize(T, int)` override added in Task 2 with:
```java
    /**
     * Serialize via {@link Object#toString()} and substring the result to {@code sizeLimit}
     * characters when a positive, less-than-{@link Integer#MAX_VALUE} limit is given.
     *
     * @param t         Object to serialize, may be {@code null}
     * @param sizeLimit Maximum length of the returned string when positive and less than
     *                  {@link Integer#MAX_VALUE}; otherwise the limit is ignored
     * @return {@code t.toString()} clipped to {@code sizeLimit} characters, or {@code ""} when
     *         {@code t} is {@code null}
     */
    @Override
    public String serialize(final T t, final int sizeLimit) throws NjamsSdkRuntimeException {
        if (t == null) {
            return "";
        }
        final String s = t.toString();
        if (sizeLimit <= 0 || sizeLimit == Integer.MAX_VALUE || sizeLimit >= s.length()) {
            return s;
        }
        return s.substring(0, sizeLimit);
    }
```

- [ ] **Step 4.4: Run the tests and verify all PASS**

Run:
```
mvn test -Dtest=StringSerializerTest -pl njams-sdk
mvn test -Dtest=LimitPayloadTest -pl njams-sdk
```
Expected: both green. `LimitPayloadTest.testFields` in particular goes through `StringSerializer.serialize(T, int)` once Task 7 wires `ActivityImpl` to call the new overload, but it must already remain green now while the wiring hasn't been switched.

- [ ] **Step 4.5: Checkstyle + javadoc**

Run:
```
mvn checkstyle:check -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```
Expected: both green.

- [ ] **Step 4.6: Commit**

```
git add njams-sdk/src/main/java/com/im/njams/sdk/serializer/StringSerializer.java \
        njams-sdk/src/test/java/com/im/njams/sdk/serializer/StringSerializerTest.java
git commit -m "SDK-419 #comment Size-aware StringSerializer via substring"
```

---

## Task 5: Add `Njams.serialize(T, int)` overload

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java` (around lines 1223–1255)
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java`

- [ ] **Step 5.1: Write the failing test**

Append a new test method to `NjamsTest`:
```java
    @Test
    public void serializeWithSizeLimitForwardsLimitToRegisteredSerializer() {
        final int[] capturedLimit = {-1};
        instance.addSerializer(String.class, (value, sizeLimit) -> {
            capturedLimit[0] = sizeLimit;
            return value;
        });

        String result = instance.serialize("hello", 7);
        assertEquals("hello", result);
        assertEquals(7, capturedLimit[0]);
    }

    @Test
    public void serializeWithoutSizeLimitStillUsesMaxValue() {
        final int[] capturedLimit = {-1};
        instance.addSerializer(String.class, (value, sizeLimit) -> {
            capturedLimit[0] = sizeLimit;
            return value;
        });

        instance.serialize("hello");
        assertEquals(Integer.MAX_VALUE, capturedLimit[0]);
    }
```

- [ ] **Step 5.2: Run and verify the size-aware test FAILS**

Run:
```
mvn test -Dtest=NjamsTest#serializeWithSizeLimitForwardsLimitToRegisteredSerializer -pl njams-sdk
```
Expected: compile error (method `serialize(String, int)` not yet on `Njams`). This is the RED state.

- [ ] **Step 5.3: Add the overload to `Njams.java`**

Edit `Njams.java`. Locate the existing `serialize(T t)` method (lines ~1223–1255). Replace its body to delegate, and add the new size-aware method directly above (or below) it:
```java
    /**
     * Serializes a given object using {@link #findSerializer(java.lang.Class)} with no effective
     * size limit.
     *
     * @param <T> type of the class
     * @param t   Object to be serialized
     * @return a string representation of the object, or {@code null} if {@code t} is {@code null},
     *         or {@code ""} when the serializer threw
     */
    public <T> String serialize(final T t) {
        return serialize(t, Integer.MAX_VALUE);
    }

    /**
     * Serializes a given object using {@link #findSerializer(java.lang.Class)}, passing
     * {@code sizeLimit} through to the resolved {@link Serializer}.
     *
     * <p>The returned string may slightly exceed {@code sizeLimit} due to serializer-specific
     * buffering. {@code sizeLimit <= 0} or {@link Integer#MAX_VALUE} mean "no limit".</p>
     *
     * @param <T>       type of the class
     * @param t         Object to be serialized
     * @param sizeLimit Approximate maximum length of the returned string
     * @return a string representation of the object, or {@code null} if {@code t} is {@code null},
     *         or {@code ""} when the serializer threw
     */
    public <T> String serialize(final T t, final int sizeLimit) {
        if (t == null) {
            return null;
        }
        final Class<? super T> clazz = (Class) t.getClass();
        synchronized (cachedSerializers) {
            Serializer<? super T> serializer = this.findSerializer(clazz);
            if (serializer == null) {
                serializer = DEFAULT_SERIALIZER;
                cachedSerializers.put(clazz, serializer);
            }
            try {
                return serializer.serialize(t, sizeLimit);
            } catch (final Exception ex) {
                LOG.error("could not serialize object " + t, ex);
                return "";
            }
        }
    }
```

- [ ] **Step 5.4: Run the tests and verify all PASS**

Run:
```
mvn test -Dtest=NjamsTest -pl njams-sdk
```
Expected: all `NjamsTest` cases green, including the two new ones.

- [ ] **Step 5.5: Checkstyle + javadoc**

Run:
```
mvn checkstyle:check -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```
Expected: both green.

- [ ] **Step 5.6: Commit**

```
git add njams-sdk/src/main/java/com/im/njams/sdk/Njams.java \
        njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java
git commit -m "SDK-419 #comment Add Njams.serialize(T, int) overload"
```

---

## Task 6: Add `JobImpl.getSerializeSizeHint()` helper

The hint is `payloadLimit + 1` when any payload limit is configured (truncate or discard), else `0`. The `+1` ensures `JobImpl.limitPayload` still detects overrun and applies the correct mode-specific behaviour after the serializer stops early.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java`
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java`

- [ ] **Step 6.1: Write the failing tests**

Append to `JobImplTest`:
```java
    @Test
    public void getSerializeSizeHintReturnsZeroWhenNoLimitConfigured() {
        JobImpl job = createDefaultJob();
        assertEquals(0, job.getSerializeSizeHint());
    }

    @Test
    public void getSerializeSizeHintReturnsLimitPlusOneInTruncateMode() {
        njams.getSettings().put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, "truncate");
        njams.getSettings().put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, "100");
        JobImpl job = createDefaultJob();
        assertEquals(101, job.getSerializeSizeHint());
    }

    @Test
    public void getSerializeSizeHintReturnsLimitPlusOneInDiscardMode() {
        njams.getSettings().put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, "discard");
        njams.getSettings().put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, "50");
        JobImpl job = createDefaultJob();
        assertEquals(51, job.getSerializeSizeHint());
    }
```
Add the import `import com.im.njams.sdk.NjamsSettings;` if not already present.

- [ ] **Step 6.2: Run and verify the new tests fail to compile**

Run:
```
mvn test -Dtest=JobImplTest -pl njams-sdk
```
Expected: compile error (`getSerializeSizeHint` not yet defined).

- [ ] **Step 6.3: Add the helper to `JobImpl.java`**

Add immediately above the existing `limitPayload(String payload)` method (around line 1437):
```java
    /**
     * Returns the size hint to pass to {@link com.im.njams.sdk.serializer.Serializer#serialize(Object, int)}
     * for payloads that will be fed through {@link #limitPayload(String)} afterwards.
     *
     * <p>The hint is one character above the configured payload limit so that
     * {@code limitPayload} still observes the overrun and applies the correct
     * truncate / discard behaviour. Returns {@code 0} (= no limit) when no
     * payload limit is configured.</p>
     *
     * @return effective size hint, or {@code 0} when no payload limit is configured
     */
    int getSerializeSizeHint() {
        if (payloadLimit == null) {
            return 0;
        }
        final int limit = payloadLimit.getValue();
        return limit <= 0 ? 0 : limit + 1;
    }
```

- [ ] **Step 6.4: Run the tests and verify all PASS**

Run:
```
mvn test -Dtest=JobImplTest -pl njams-sdk
```
Expected: green.

- [ ] **Step 6.5: Checkstyle (no javadoc check needed; method is package-private)**

Run:
```
mvn checkstyle:check -pl njams-sdk
```
Expected: green.

- [ ] **Step 6.6: Commit**

```
git add njams-sdk/src/main/java/com/im/njams/sdk/logmessage/JobImpl.java \
        njams-sdk/src/test/java/com/im/njams/sdk/logmessage/JobImplTest.java
git commit -m "SDK-419 #comment Add JobImpl.getSerializeSizeHint()"
```

---

## Task 7: Wire `ActivityImpl.processInput` / `processOutput` to the size hint

This is the only change that actually exercises the optimization end-to-end. Critical invariant: when `needsData(extract)` is `true`, **the serializer must be called with `sizeLimit = 0`** so extract rules still see the full string.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java` (lines 240–257 and 305–322)
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/ActivityImplExtractDataTest.java` (extend with a counter-test asserting the size hint *is* applied when extracts don't need data)

- [ ] **Step 7.1: Write the failing test asserting the size hint is applied when extracts don't need data**

Append to `ActivityImplExtractDataTest`:
```java
    @Test
    public void processInputPassesSizeHintWhenExtractDoesNotNeedData() {
        // Configure a payload truncate limit
        ClientSettings settings = njams.getSettings();
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, "truncate");
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, "10");

        // Configure an extract that does NOT need data (RuleType.VALUE)
        Extract extract = new Extract();
        ExtractRule rule = new ExtractRule();
        rule.setRuleType(RuleType.VALUE);
        rule.setInout("in");
        rule.setAttribute("constant");
        rule.setRule("fixed-value");
        extract.getExtractRules().add(rule);
        ActivityConfiguration cfg = new ActivityConfiguration();
        cfg.setExtract(extract);

        // Spy on Njams.serialize(Object, int) to capture the sizeLimit
        Njams spy = Mockito.spy(njams);
        // ... (test setup mirrors testFields in LimitPayloadTest; use `spy` everywhere
        //      the activity reads from its Njams reference)

        JobImpl job = createDefaultJob();
        job.getProcessConfiguration().getActivities()
                .put(createDefaultActivity(job).getModelId(), cfg);
        job.start();
        ActivityImpl activity = (ActivityImpl) createDefaultActivity(job);
        activity.processInput("a-long-input-string");

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(spy, atLeastOnce()).serialize(any(), limit.capture());
        // hint is payloadLimit + 1 = 11
        assertEquals(Integer.valueOf(11), limit.getValue());
    }
```
> If the spy plumbing through `AbstractTest` turns out to be awkward, fall back to a hand-rolled test double for `Njams` (an anonymous subclass that overrides `serialize(Object, int)` and records the captured `sizeLimit`). Either approach is acceptable — the assertion is what matters: when there is a payload limit AND no extract rule that needs data, `serialize(Object, int)` is called with `payloadLimit + 1`.

- [ ] **Step 7.2: Run and verify the new test FAILS (current wiring calls `serialize(Object)`, not `serialize(Object, int)`)**

Run:
```
mvn test -Dtest=ActivityImplExtractDataTest -pl njams-sdk
```
Expected: `processInputPassesSizeHintWhenExtractDoesNotNeedData` fails; `extractRulesReceiveFullUntruncatedSerializedData` from Task 1 still PASSES.

- [ ] **Step 7.3: Wire the size hint in `ActivityImpl.processInput` (around line 240)**

Replace:
```java
    @Override
    public void processInput(Object input) {
        final String serializedData;
        if (isTracing() || needsData(extract)) {
            serializedData = DataMasking.maskString(job.getNjams().serialize(input));
        } else {
            serializedData = null;
        }
```
with:
```java
    @Override
    public void processInput(Object input) {
        final String serializedData;
        if (isTracing() || needsData(extract)) {
            final int sizeLimit = needsData(extract) ? 0 : job.getSerializeSizeHint();
            serializedData = DataMasking.maskString(job.getNjams().serialize(input, sizeLimit));
        } else {
            serializedData = null;
        }
```

- [ ] **Step 7.4: Wire the size hint in `ActivityImpl.processOutput` (around line 305)**

Replace:
```java
    @Override
    public void processOutput(Object output) {
        final String serializedData;
        if (isTracing() || needsData(extract)) {
            serializedData = DataMasking.maskString(job.getNjams().serialize(output));
        } else {
            serializedData = null;
        }
```
with:
```java
    @Override
    public void processOutput(Object output) {
        final String serializedData;
        if (isTracing() || needsData(extract)) {
            final int sizeLimit = needsData(extract) ? 0 : job.getSerializeSizeHint();
            serializedData = DataMasking.maskString(job.getNjams().serialize(output, sizeLimit));
        } else {
            serializedData = null;
        }
```

> `processStartData` (line 516) is intentionally **not** changed: `setStartData` doesn't go through `limitPayload` and the size-limit semantic doesn't apply.

- [ ] **Step 7.5: Run the full SDK test suite**

Run:
```
mvn test -pl njams-sdk
```
Expected: all green. In particular:
- `LimitPayloadTest.testFields` still produces `PAYLOAD_DISCARDED_MESSAGE` for the 20-char string (size hint `11` → JsonSerializer/StringSerializer return ≤ 11 chars → `limitPayload` sees `length > limit` and discards). If this test fails, the `+1` math in `getSerializeSizeHint` is wrong — recheck Step 6.3.
- `ActivityImplExtractDataTest.extractRulesReceiveFullUntruncatedSerializedData` still PASSES (extract needs data → `sizeLimit = 0` → full serialization).
- `ActivityImplExtractDataTest.processInputPassesSizeHintWhenExtractDoesNotNeedData` now PASSES.
- `DataMaskingTest` still PASSES (its mock `Njams.serialize(any())` answer must still apply — if Mockito's `any()` stub does not match the two-arg overload, also stub `Njams.serialize(any(), anyInt())` with the same answer. Update `DataMaskingTest` line 62 if needed).

- [ ] **Step 7.6: Checkstyle + javadoc**

Run:
```
mvn checkstyle:check -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```
Expected: both green.

- [ ] **Step 7.7: Commit**

```
git add njams-sdk/src/main/java/com/im/njams/sdk/logmessage/ActivityImpl.java \
        njams-sdk/src/test/java/com/im/njams/sdk/logmessage/ActivityImplExtractDataTest.java \
        njams-sdk/src/test/java/com/im/njams/sdk/logmessage/DataMaskingTest.java
git commit -m "SDK-419 #comment Wire ActivityImpl payload size hint into Njams.serialize"
```
(If `DataMaskingTest.java` did not need changes, omit it from the `git add` line.)

---

## Task 8: Final verification, top-level build, Jira close-out

- [ ] **Step 8.1: Full top-level build**

Run from repository root:
```
mvn clean install
```
Expected: BUILD SUCCESS across all modules (`njams-sdk`, `njams-sdk-sample-client`, `njams-sdk-sample-app`). The sample modules link against the SDK and would catch any `Serializer` usage we missed.

- [ ] **Step 8.2: Re-run checkstyle and javadoc as a final gate**

```
mvn checkstyle:check -pl njams-sdk
mvn javadoc:javadoc -pl njams-sdk
```
Expected: both green. Javadoc errors (broken `{@link}`, missing tags on `public`/`protected` members) are hard failures per `CLAUDE.md`.

- [ ] **Step 8.3: Sanity-check the `breaking-change` label scope**

Confirm with the user that the impact of the breaking change on downstream client implementations of `Serializer<T>` is acceptable. They must add a `serialize(T, int)` method to compile against the new SDK. (No further code action — this is a confirmation step.)

- [ ] **Step 8.4: Stop. Do NOT push.**

Per the user's standing rule (`feedback_explicit_push_authorization`), do not push or open a PR without an explicit per-action authorization. Surface the local commit list and ask whether to push.

- [ ] **Step 8.5: Post the close-out comment on SDK-419 (after the user confirms push)**

Using `mcp__claude_ai_Atlassian__addCommentToJiraIssue` once the user confirms the work is shipping:
```
SDK-419 resolved.

Serializer interface now declares serialize(T, int sizeLimit) as the abstract method;
the legacy serialize(T) is a default that delegates with Integer.MAX_VALUE. JsonSerializer
overrides both for performance (LimitedWriter aborts Jackson at the size cap). StringSerializer
overrides the size-aware method (substring after toString). Njams.serialize(T, int) is exposed
publicly and is used by ActivityImpl.processInput/Output, passing payloadLimit + 1 as the hint
so JobImpl.limitPayload still detects overrun for both truncate and discard modes. The size
hint is suppressed (= 0) when an extract rule needs data, preserving the invariant that extracts
operate on the full serialized string.

This is a breaking change for external Serializer implementations: they must add
serialize(T, int).

---
_Generated by Claude Code_
```

---

## Self-Review checklist (for the executing agent)

Before declaring done, walk through:
1. Every spec requirement in SDK-419 maps to a task: ✓ new method on interface (Task 2), default delegation with `Integer.MAX_VALUE` (Task 2), JsonSerializer overrides both (Tasks 2 + 3), size-limit honoured during serialization (Task 3), wired into the runtime path (Tasks 5–7).
2. No placeholders, every code block above is the actual content to write.
3. Type consistency: `serialize(T, int)` everywhere (interface, both built-ins, `Njams`, all tests). `getSerializeSizeHint` (not `getPayloadSizeHint`, not `getSerializeLimit`) everywhere.
4. CLAUDE.md compliance: every commit references SDK-419, copyright header on all new production files (no new production files in this plan — only test files, which are exempt), Javadoc on every new public/protected member, `breaking-change` label set, fix version `6.0.0`, FAQ unchanged (no new/changed setting).
