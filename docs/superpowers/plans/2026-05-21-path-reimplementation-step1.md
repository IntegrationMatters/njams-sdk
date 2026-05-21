# Path Re-implementation – Step 1: Core Tree Structure

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create an immutable, singleton-per-path `com.im.njams.sdk.Path` class backed by a thread-safe tree, and deprecate the existing `com.im.njams.sdk.common.Path`.

**Architecture:** Each `Path` instance is a node in a shared tree rooted at `ROOT (">")`. Nodes are created on demand via `Path.get(String... segments)` and stored in per-node `ConcurrentHashMap` children maps. `computeIfAbsent` on `ConcurrentHashMap` guarantees that only one instance per segment slot is ever created, making reads lock-free and writes atomic. Invalid segments are cached in a concurrent set for fast repeated rejection.

**Tech Stack:** Java 11, JUnit 4, no new dependencies.

---

## File Map

| Action | File |
|--------|------|
| **Create** | `njams-sdk/src/main/java/com/im/njams/sdk/Path.java` |
| **Create** | `njams-sdk/src/test/java/com/im/njams/sdk/PathTest.java` |
| **Modify** | `njams-sdk/src/main/java/com/im/njams/sdk/common/Path.java` — add `@Deprecated` + Javadoc |

---

## Task 1: Write the failing tests

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/PathTest.java`

- [ ] **Step 1: Create the test file**

```java
package com.im.njams.sdk;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class PathTest {

    // --- ROOT ---

    @Test
    public void rootExists() {
        assertNotNull(Path.ROOT);
        assertEquals(">", Path.ROOT.toString());
    }

    @Test
    public void rootHasNullParent() {
        assertNull(Path.ROOT.getParent());
    }

    @Test
    public void rootChildrenIsEmptyCollection() {
        // ROOT may accumulate children as other tests run; just verify the collection is non-null
        assertNotNull(Path.ROOT.getChildren());
    }

    // --- Factory: no-arg / null ---

    @Test
    public void getWithNoSegmentsReturnsRoot() {
        assertSame(Path.ROOT, Path.get());
    }

    @Test
    public void getWithNullArrayReturnsRoot() {
        assertSame(Path.ROOT, Path.get((String[]) null));
    }

    @Test
    public void getWithRootStringReturnsRoot() {
        assertSame(Path.ROOT, Path.get(">"));
    }

    // --- Path string representation ---

    @Test
    public void singleSegmentPathString() {
        assertEquals(">seg1>", Path.get("seg1").toString());
    }

    @Test
    public void twoSegmentPathString() {
        assertEquals(">alpha>beta>", Path.get("alpha", "beta").toString());
    }

    @Test
    public void threeSegmentPathString() {
        assertEquals(">a>b>c>", Path.get("a", "b", "c").toString());
    }

    // --- Singleton guarantee ---

    @Test
    public void sameSegmentsReturnSameInstance() {
        Path p1 = Path.get("singleton", "test");
        Path p2 = Path.get("singleton", "test");
        assertSame(p1, p2);
    }

    @Test
    public void intermediateNodeIsShared() {
        Path child = Path.get("shared", "child");
        Path parent = Path.get("shared");
        assertSame(parent, child.getParent());
    }

    // --- Navigation: getParent ---

    @Test
    public void parentOfRootChildIsRoot() {
        assertSame(Path.ROOT, Path.get("rootchild").getParent());
    }

    @Test
    public void parentAtDepthTwo() {
        Path parent = Path.get("depth", "one");
        Path child = Path.get("depth", "one", "two");
        assertSame(parent, child.getParent());
    }

    // --- Navigation: getChild ---

    @Test
    public void getChildReturnsKnownChild() {
        Path parent = Path.get("nav", "parent");
        Path child = Path.get("nav", "parent", "kid");
        assertSame(child, parent.getChild("kid"));
    }

    @Test
    public void getChildReturnsNullForUnknown() {
        Path p = Path.get("unknownchild");
        assertNull(p.getChild("doesnotexist"));
    }

    // --- Navigation: getChildren ---

    @Test
    public void getChildrenContainsCreatedChildren() {
        Path parent = Path.get("multi");
        Path c1 = Path.get("multi", "first");
        Path c2 = Path.get("multi", "second");
        Collection<Path> children = parent.getChildren();
        assertTrue(children.contains(c1));
        assertTrue(children.contains(c2));
    }

    @Test
    public void getChildrenIsUnmodifiable() {
        Path p = Path.get("immutablechildren");
        assertThrows(UnsupportedOperationException.class,
                () -> p.getChildren().add(Path.get("illegal")));
    }

    // --- equals and hashCode ---

    @Test
    public void equalsIsTrueForSameInstance() {
        Path p = Path.get("eqtest");
        assertTrue(p.equals(p));
    }

    @Test
    public void equalsIsFalseForDifferentInstance() {
        Path p1 = Path.get("eq", "a");
        Path p2 = Path.get("eq", "b");
        assertFalse(p1.equals(p2));
    }

    @Test
    public void equalsIsFalseForNull() {
        assertFalse(Path.get("eqnull").equals(null));
    }

    @Test
    public void equalsIsFalseForOtherType() {
        assertFalse(Path.get("eqtype").equals(">eqtype>"));
    }

    @Test
    public void hashCodeIsStable() {
        Path p = Path.get("hashstable");
        assertEquals(p.hashCode(), p.hashCode());
    }

    @Test
    public void equalInstancesHaveSameHashCode() {
        Path p1 = Path.get("hashequal");
        Path p2 = Path.get("hashequal");
        assertSame(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    // --- Validation ---

    @Test(expected = IllegalArgumentException.class)
    public void nullSegmentThrows() {
        Path.get("valid", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySegmentThrows() {
        Path.get("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankSegmentThrows() {
        Path.get("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void segmentContainingSeparatorThrows() {
        Path.get("a>b");
    }

    @Test
    public void invalidSegmentIsRejectedOnRepeat() {
        // First call discovers the bad segment; second call must also reject it (fail-fast cache)
        try {
            Path.get("bad>repeat");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertThrows(IllegalArgumentException.class, () -> Path.get("bad>repeat"));
    }

    // --- Thread safety ---

    @Test
    public void concurrentCreationReturnsSameInstance() throws Exception {
        int threads = 20;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<AtomicReference<Path>> results = new ArrayList<>(threads);
        List<Thread> threadList = new ArrayList<>(threads);

        for (int i = 0; i < threads; i++) {
            AtomicReference<Path> ref = new AtomicReference<>();
            results.add(ref);
            threadList.add(new Thread(() -> {
                try {
                    barrier.await();
                    ref.set(Path.get("concurrent", "singleton", "test"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        threadList.forEach(Thread::start);
        for (Thread t : threadList) {
            t.join(5000);
        }

        Path first = results.get(0).get();
        assertNotNull(first);
        for (AtomicReference<Path> ref : results) {
            assertSame("All threads must return the same instance", first, ref.get());
        }
    }
}
```

- [ ] **Step 2: Run the tests to confirm they fail (class not found)**

```
mvn test -Dtest=com.im.njams.sdk.PathTest -pl njams-sdk
```

Expected: compilation failure — `com.im.njams.sdk.Path` does not exist.

---

## Task 2: Implement `com.im.njams.sdk.Path`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/Path.java`

- [ ] **Step 1: Create the implementation**

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
package com.im.njams.sdk;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an immutable nJAMS path node in a shared, lazily built path tree.
 *
 * <p>A path in nJAMS uses {@code >} as a separator and always starts and ends with it.
 * Examples of valid path strings: {@code >}, {@code >a>}, {@code >a>b>}.
 *
 * <p>Each instance is a unique, immutable node. The same logical path always returns
 * the same object reference. Use {@link #get(String...)} to obtain instances; there
 * is no public constructor.
 *
 * <p>The tree is thread-safe: reads are non-blocking; node creation is atomic per slot.
 *
 * @see com.im.njams.sdk.common.Path
 */
public final class Path {

    private static final Set<String> INVALID_SEGMENTS = ConcurrentHashMap.newKeySet();

    /**
     * The root path {@code ">"}, the only path with a {@code null} parent.
     * Always exists; never {@code null}.
     */
    public static final Path ROOT = new Path(null, null, ">");

    private final Path parent;
    private final String segment;
    private final String pathString;
    private final int cachedHashCode;
    private final ConcurrentHashMap<String, Path> children = new ConcurrentHashMap<>();

    private Path(Path parent, String segment, String pathString) {
        this.parent = parent;
        this.segment = segment;
        this.pathString = pathString;
        this.cachedHashCode = pathString.hashCode();
    }

    /**
     * Returns the {@link Path} instance identified by the given segments.
     *
     * <p>Calling this method with the same segments always returns the identical object.
     * Reads are non-blocking: segment validation is skipped entirely when the child node
     * already exists. Validation and creation are atomic per slot in the path tree.
     *
     * @param segments zero or more non-null, non-blank path segment names; must not contain {@code >}
     * @return the corresponding {@link Path} node; {@link #ROOT} if no segments are provided
     * @throws IllegalArgumentException if any segment is {@code null}, blank, or contains {@code >}
     */
    public static Path get(String... segments) {
        if (segments == null || segments.length == 0) {
            return ROOT;
        }
        if (segments.length == 1 && ">".equals(segments[0])) {
            return ROOT;
        }
        Path current = ROOT;
        for (String seg : segments) {
            Path next = current.children.get(seg);
            if (next == null) {
                // Only validate and create when the child does not yet exist.
                // An invalid segment can never have an existing node, so validating
                // only here is sufficient and keeps the read hot-path overhead-free.
                validate(seg);
                final Path nodeParent = current;
                next = current.children.computeIfAbsent(
                        seg,
                        s -> new Path(nodeParent, s, nodeParent.pathString + s + ">"));
            }
            current = next;
        }
        return current;
    }

    private static void validate(String segment) {
        if (segment == null) {
            throw new IllegalArgumentException("Path segment must not be null");
        }
        if (INVALID_SEGMENTS.contains(segment)) {
            throw new IllegalArgumentException("Invalid path segment: " + segment);
        }
        if (segment.isBlank() || segment.contains(">")) {
            INVALID_SEGMENTS.add(segment);
            throw new IllegalArgumentException("Invalid path segment: " + segment);
        }
    }

    /**
     * Returns the parent path node, or {@code null} if this is the {@link #ROOT}.
     *
     * @return the parent path, or {@code null} for root
     */
    public Path getParent() {
        return parent;
    }

    /**
     * Returns the child path node with the given segment name, or {@code null} if
     * no such child has been created yet.
     *
     * @param name the segment name of the child to look up
     * @return the child path, or {@code null} if not present
     */
    public Path getChild(String name) {
        return children.get(name);
    }

    /**
     * Returns an unmodifiable view of all direct children of this path.
     *
     * @return a live, unmodifiable collection of child paths; never {@code null}
     */
    public Collection<Path> getChildren() {
        return Collections.unmodifiableCollection(children.values());
    }

    /**
     * Returns the full path string, e.g., {@code ">a>b>"}.
     *
     * @return the full path string; never {@code null}
     */
    @Override
    public String toString() {
        return pathString;
    }

    /**
     * Returns {@code true} only if {@code obj} is this exact instance.
     *
     * <p>Because each path is a unique singleton, identity comparison is sufficient and correct.
     *
     * @param obj the object to compare
     * @return {@code true} if {@code obj == this}
     */
    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    /**
     * Returns a pre-computed hash code derived from the full path string.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return cachedHashCode;
    }
}
```

- [ ] **Step 2: Run the tests to confirm they pass**

```
mvn test -Dtest=com.im.njams.sdk.PathTest -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all tests green.

- [ ] **Step 3: Run Checkstyle to confirm Javadoc compliance**

```
mvn checkstyle:check -pl njams-sdk
```

Expected: `BUILD SUCCESS`, no Checkstyle violations.

---

## Task 3: Deprecate `com.im.njams.sdk.common.Path`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/common/Path.java`

- [ ] **Step 1: Add `@Deprecated` annotation and `@deprecated` Javadoc tag to the class**

Locate the class-level Javadoc and declaration (lines 33–39 in the current file). Replace:

```java
/**
 * Path object for handling pathes. A path consists of several path parts. The
 * String representation uses the &gt; as separator between path elements.
 *
 * @author pnientiedt
 */
public class Path implements Comparable<Path> {
```

With:

```java
/**
 * Path object for handling pathes. A path consists of several path parts. The
 * String representation uses the &gt; as separator between path elements.
 *
 * @author pnientiedt
 * @deprecated Use {@link com.im.njams.sdk.Path} instead, which guarantees path uniqueness,
 *     immutability, and thread-safety.
 */
@Deprecated
public class Path implements Comparable<Path> {
```

- [ ] **Step 2: Run the existing `PathTest` for the old class to confirm it still passes**

```
mvn test -Dtest=com.im.njams.sdk.common.PathTest -pl njams-sdk
```

Expected: `BUILD SUCCESS`, all tests green (the old tests must not be broken).

- [ ] **Step 3: Run Checkstyle**

```
mvn checkstyle:check -pl njams-sdk
```

Expected: `BUILD SUCCESS`.

---

## Task 4: Full test run and commit

- [ ] **Step 1: Run all SDK tests**

```
mvn test -pl njams-sdk
```

Expected: `BUILD SUCCESS`, zero failures, zero errors.

- [ ] **Step 2: Commit**

```
git add njams-sdk/src/main/java/com/im/njams/sdk/Path.java
git add njams-sdk/src/test/java/com/im/njams/sdk/PathTest.java
git add njams-sdk/src/main/java/com/im/njams/sdk/common/Path.java
git commit -m "SDK-205 #comment Introduce new com.im.njams.sdk.Path as immutable singleton tree; deprecate com.im.njams.sdk.common.Path"
```

---

## Self-Review

**Spec coverage:**
| Requirement | Covered by |
|-------------|-----------|
| New class in `com.im.njams.sdk` | Task 2 |
| No public constructor; `Path.get(String... segments)` factory | Task 2 |
| Unique instances (same args → same object) | Task 2 + test `sameSegmentsReturnSameInstance` |
| Immutable fields | Task 2 (all fields `final`, no setters) |
| ROOT always exists, null parent | Task 2 + tests `rootExists`, `rootHasNullParent` |
| `Path.get(">")` returns ROOT | Task 2 `get()` upfront check + test `getWithRootStringReturnsRoot` |
| `getParent()`, `getChild(String)`, `getChildren()` | Task 2 + navigation tests |
| Full path string stored per instance | Task 2 (`pathString` field + `toString()`) |
| Cached `hashCode` | Task 2 (`cachedHashCode` field) |
| `equals()` uses identity | Task 2 + test `equalsIsTrueForSameInstance` |
| `IllegalArgumentException` for null/blank/`>` segments | Task 2 `validate()` + validation tests |
| Fail-fast: bad segments cached | Task 2 `INVALID_SEGMENTS` set + test `invalidSegmentIsRejectedOnRepeat` |
| Thread-safe singleton creation | Task 2 `computeIfAbsent` + test `concurrentCreationReturnsSameInstance` |
| Deprecate `com.im.njams.sdk.common.Path` | Task 3 |
| Existing tests unmodified and still passing | Task 3 step 2 |
| Copyright header on new production file | Task 2 |
| Checkstyle / Javadoc on all public members | Task 2 + Checkstyle steps |
| Commit references SDK-205 | Task 4 |

**Placeholder scan:** None found — all steps contain complete code or exact commands.

**Type consistency:** `Path.get()` returns `Path`; `getParent()` returns `Path`; `getChild()` returns `Path`; `getChildren()` returns `Collection<Path>` — consistent throughout.
