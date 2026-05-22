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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an immutable nJAMS path node in a shared, lazily built path tree.
 *
 * <p>A path in nJAMS uses {@code >} as a separator and always starts and ends with it.
 * Examples of valid path strings: {@code >}, {@code >a>}, {@code >a>b>}.
 *
 * <p>Each instance is a unique, immutable node. The same logical path always returns
 * the same object reference. Use {@link #of(String...)} to obtain instances; there
 * is no public constructor.
 *
 * <p>The tree is thread-safe: reads are non-blocking; node creation is atomic per slot.
 *
 * <h2>Strict vs. resolve API — which to use</h2>
 *
 * <p>Two parallel APIs are provided:
 * <ul>
 *   <li><b>Strict (segment-based)</b>: {@link #of(String...)}, {@link #getChild(String...)},
 *       {@link #hasChild(String...)}, {@link #getOrCreateChild(String...)}. Each argument
 *       is treated as a single segment name. Walking the tree is one
 *       {@code ConcurrentHashMap.get} per level — no parsing, no string allocation, and
 *       validation is skipped entirely for already-existing nodes.
 *   <li><b>Resolve (path-string-based)</b>: {@link #resolve(String...)},
 *       {@link #resolveChild(String...)}, {@link #resolvesChild(String...)},
 *       {@link #resolveOrCreateChild(String...)}. Each argument may be a partial path
 *       string with embedded {@code >} separators; arguments are split, empty and
 *       {@code null} segments are dropped, and the remaining segments are walked the
 *       same way as the strict variants. Split results are cached per input string, but
 *       even with a cache hit the split + cache lookup costs more than passing segments
 *       directly.
 * </ul>
 *
 * <p><b>Prefer the strict variants whenever the segment names are already known.</b>
 * Use the resolve variants only when the input is itself a path string — for example
 * parsed from configuration, received from the wire, or otherwise arriving as raw text
 * that may contain {@code >} separators.
 *
 * @see com.im.njams.sdk.common.Path
 */
public final class Path {

    private static final Set<String> INVALID_SEGMENTS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, String[]> SPLIT_CACHE = new ConcurrentHashMap<>();
    private static final String[] EMPTY_SEGMENTS = new String[0];

    /**
     * The root path {@code ">"}, the only path with a {@code null} parent.
     * Always exists; never {@code null}.
     */
    public static final Path ROOT = new Path(null, null);

    private final Path parent;
    private final String segment;
    private final String pathString;
    private final int cachedHashCode;
    private final ConcurrentHashMap<String, Path> children = new ConcurrentHashMap<>();

    private Path(Path parent, String segment) {
        this.parent = parent;
        this.segment = segment;
        this.pathString = parent == null ? ">" : parent.toString() + segment + ">";
        this.cachedHashCode = pathString.hashCode();
    }

    /**
     * Returns the {@link Path} instance identified by the given segments.
     *
     * <p>Calling this method with the same segments always returns the identical object.
     * Reads are non-blocking: segment validation is skipped entirely when the child node
     * already exists. Validation and creation are atomic per slot in the path tree.
     *
     * <p>For arguments that may contain embedded {@code >} separators (partial path strings),
     * use {@link #resolve(String...)} instead.
     *
     * @param segments zero or more non-null, non-blank path segment names; must not contain {@code >}.
     *     The single value {@code ">"} is treated as the root and returns {@link #ROOT}.
     * @return the corresponding {@link Path} node; {@link #ROOT} if no segments are provided
     * @throws IllegalArgumentException if any segment is {@code null}, blank, or contains {@code >}
     */
    public static Path of(String... segments) {
        if (segments == null || segments.length == 0) {
            return ROOT;
        }
        if (segments.length == 1 && ">".equals(segments[0])) {
            return ROOT;
        }
        return ROOT.getOrCreateChild(segments);
    }

    /**
     * List variant of {@link #of(String...)}; see that method for full semantics.
     *
     * @param segments segment names; {@code null} returns {@link #ROOT}
     * @return the corresponding {@link Path} node
     * @throws IllegalArgumentException if any segment is {@code null}, blank, or contains {@code >}
     */
    public static Path of(List<String> segments) {
        return of(segments == null ? EMPTY_SEGMENTS : segments.toArray(new String[0]));
    }

    /**
     * Returns the new {@link Path} instance equivalent to the given legacy
     * {@link com.im.njams.sdk.common.Path}, resolved via its
     * {@link com.im.njams.sdk.common.Path#getParts() getParts()} segments.
     *
     * <p>Intended only as a migration helper while callers still hold legacy instances.
     * Switch call sites to construct {@link Path} directly via {@link #of(String...)} or
     * {@link #resolve(String...)} and drop the dependency on
     * {@link com.im.njams.sdk.common.Path}.
     *
     * @param legacyPath the legacy path; {@code null} returns {@link #ROOT}
     * @return the equivalent new {@link Path} node
     * @throws IllegalArgumentException if any of the legacy path's parts is invalid as a segment
     * @deprecated Switch callers from {@link com.im.njams.sdk.common.Path} to the new
     *     {@link Path} type and construct instances directly via {@link #of(String...)} or
     *     {@link #resolve(String...)}.
     */
    @Deprecated
    public static Path of(com.im.njams.sdk.common.Path legacyPath) {
        if (legacyPath == null) {
            return ROOT;
        }
        return of(legacyPath.getParts().toArray(new String[0]));
    }

    /**
     * Returns the {@link Path} instance for the given arguments, treating each argument as
     * a (possibly partial) path string. Each argument is split at {@code >}; empty and
     * {@code null} segments are silently dropped. The remaining segments are then walked
     * exactly like {@link #of(String...)}.
     *
     * <p>This is more lenient than {@link #of(String...)}: it accepts arguments such as
     * {@code "a>b>"} or {@code ">c>"} that {@code of} would reject. Bare segment arguments
     * such as {@code resolve("a", "b", "c")} produce the same instance as the equivalent
     * {@code of} call.
     *
     * <p>Split results are cached per input string to amortise the splitting overhead across
     * repeated calls. Even with caching, this method is slower than {@link #of(String...)};
     * prefer {@link #of(String...)} whenever the individual segment names are already known.
     *
     * @param paths zero or more path strings to split and walk; {@code null} array or
     *     {@code null} entries return / contribute nothing
     * @return the resolved {@link Path} node; {@link #ROOT} if no non-empty segments result
     * @throws IllegalArgumentException if any resulting segment is blank or otherwise invalid
     */
    public static Path resolve(String... paths) {
        return ROOT.resolveOrCreateChild(paths);
    }

    /**
     * List variant of {@link #resolve(String...)}; see that method for full semantics.
     *
     * @param paths path strings to split and walk; {@code null} returns {@link #ROOT}
     * @return the resolved {@link Path} node
     * @throws IllegalArgumentException if any resulting segment is blank or otherwise invalid
     */
    public static Path resolve(List<String> paths) {
        return resolve(paths == null ? EMPTY_SEGMENTS : paths.toArray(new String[0]));
    }

    private static String[] splitSegments(String input) {
        if (input.isEmpty()) {
            return EMPTY_SEGMENTS;
        }
        String[] raw = input.split(">");
        int count = 0;
        for (String p : raw) {
            if (p != null && !p.isEmpty()) {
                count++;
            }
        }
        if (count == 0) {
            return EMPTY_SEGMENTS;
        }
        if (count == raw.length) {
            return raw;
        }
        String[] result = new String[count];
        int i = 0;
        for (String p : raw) {
            if (p != null && !p.isEmpty()) {
                result[i++] = p;
            }
        }
        return result;
    }

    private static void validate(String segment) {
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
     * Returns {@code true} if this is the {@link #ROOT} path.
     *
     * @return {@code true} if this path has no parent
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Returns this node's own segment name (the last component of its path), or
     * {@code null} if this is the {@link #ROOT}.
     *
     * @return the segment name, or {@code null} for root
     */
    public String getSegmentName() {
        return segment;
    }

    /**
     * Returns the ordered list of segment names that make up this path, root first.
     * Empty for the {@link #ROOT} path.
     *
     * <p>A fresh list is returned on every call and is not backed by any internal state,
     * so callers are free to mutate it.
     *
     * @return an ordered list of segment names; never {@code null}
     */
    public List<String> getSegments() {
        ArrayList<String> segments = new ArrayList<>();
        Path current = this;
        while (current != null && !current.isRoot()) {
            segments.add(0, current.segment);
            current = current.parent;
        }
        return segments;
    }

    /**
     * Returns the descendant path node reached by walking the given segment names as a
     * relative path from this node, or {@code null} if any step along the chain does not
     * yet exist.
     *
     * <p>With no arguments (or a {@code null} array) returns this node itself.
     *
     * @param names zero or more segment names forming a relative path from this node
     * @return the descendant path, this node if no names are given, or {@code null} if
     *     any intermediate child has not been created
     */
    public Path getChild(String... names) {
        if (names == null || names.length == 0) {
            return this;
        }
        Path current = this;
        for (String name : names) {
            current = current.children.get(name);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * List variant of {@link #getChild(String...)}; see that method for full semantics.
     *
     * @param names segment names forming a relative path from this node; {@code null} returns this node
     * @return the descendant path, this node if no names are given, or {@code null} if any intermediate
     *     child has not been created
     */
    public Path getChild(List<String> names) {
        return getChild(names == null ? EMPTY_SEGMENTS : names.toArray(new String[0]));
    }

    /**
     * Returns the descendant path node reached by walking the given (possibly partial)
     * path strings as a relative path from this node, or {@code null} if any step along
     * the chain does not yet exist. Each argument is split at {@code >} and empty/null
     * segments are dropped before walking.
     *
     * <p>This is the instance counterpart of {@link #resolve(String...)}. Prefer
     * {@link #getChild(String...)} whenever the segment names are already known to avoid
     * the splitting overhead.
     *
     * @param paths zero or more (possibly partial) path strings forming a relative path
     *     from this node; {@code null} entries are skipped
     * @return the descendant path, this node if no segments result, or {@code null} if
     *     any intermediate child has not been created
     */
    public Path resolveChild(String... paths) {
        return walkSplit(paths, false);
    }

    /**
     * List variant of {@link #resolveChild(String...)}; see that method for full semantics.
     *
     * @param paths path strings forming a relative path from this node; {@code null} returns this node
     * @return the descendant path, this node if no segments result, or {@code null} if any intermediate
     *     child has not been created
     */
    public Path resolveChild(List<String> paths) {
        return resolveChild(paths == null ? EMPTY_SEGMENTS : paths.toArray(new String[0]));
    }

    /**
     * Tests whether the descendant path reached by walking the given segment names exists
     * as a chain of created children under this path. This is the strict variant: each
     * argument is treated as a single segment name and is not parsed.
     *
     * <p>With no arguments (or a {@code null} array) returns {@code true} (the empty
     * relative path always exists). A {@code null} entry in the array returns
     * {@code false} (a null segment cannot identify a child).
     *
     * <p>For arguments that may contain embedded {@code >} separators (partial path strings),
     * use {@link #resolvesChild(String...)} instead — it splits each argument and drops
     * empty/null segments, at the cost of splitting overhead.
     *
     * @param names zero or more segment names forming a relative path from this node
     * @return {@code true} if the full chain has been created; {@code false} otherwise
     * @see #resolvesChild(String...)
     */
    public boolean hasChild(String... names) {
        if (names != null) {
            for (String name : names) {
                if (name == null) {
                    return false;
                }
            }
        }
        return getChild(names) != null;
    }

    /**
     * List variant of {@link #hasChild(String...)}; see that method for full semantics.
     *
     * @param names segment names forming a relative path from this node; {@code null} returns {@code true}
     * @return {@code true} if the full chain has been created; {@code false} otherwise
     */
    public boolean hasChild(List<String> names) {
        return hasChild(names == null ? EMPTY_SEGMENTS : names.toArray(new String[0]));
    }

    /**
     * Tests whether the descendant path reached by walking the given (possibly partial)
     * path strings exists as a chain of created children under this path. Each argument
     * is split at {@code >} and empty segments are dropped before walking; {@code null}
     * entries in the array are silently skipped.
     *
     * <p>With no arguments (or a {@code null} array) returns {@code true} (the empty
     * relative path always exists).
     *
     * <p>This is the lenient counterpart of {@link #hasChild(String...)}: it accepts
     * arguments such as {@code "a>b>"} or {@code ">c>"} that {@code hasChild} would
     * treat as a single (and therefore non-matching) segment. Prefer
     * {@link #hasChild(String...)} when the segment names are already known, to avoid
     * the splitting overhead.
     *
     * @param paths zero or more (possibly partial) path strings forming a relative path
     *     from this node; {@code null} entries are skipped
     * @return {@code true} if the full chain has been created; {@code false} otherwise
     * @see #hasChild(String...)
     */
    public boolean resolvesChild(String... paths) {
        return resolveChild(paths) != null;
    }

    /**
     * List variant of {@link #resolvesChild(String...)}; see that method for full semantics.
     *
     * @param paths path strings forming a relative path from this node; {@code null} returns {@code true}
     * @return {@code true} if the full chain has been created; {@code false} otherwise
     */
    public boolean resolvesChild(List<String> paths) {
        return resolvesChild(paths == null ? EMPTY_SEGMENTS : paths.toArray(new String[0]));
    }

    /**
     * Returns the descendant path node reached by walking the given (possibly partial)
     * path strings as a relative path from this node, creating any missing intermediate
     * children along the way. Each argument is split at {@code >} and empty/null
     * segments are dropped before walking.
     *
     * <p>This is the instance counterpart of {@link #resolve(String...)}. Prefer
     * {@link #getOrCreateChild(String...)} whenever the segment names are already known
     * to avoid the splitting overhead.
     *
     * @param paths zero or more (possibly partial) path strings forming a relative path
     *     from this node; {@code null} entries are skipped
     * @return the existing or newly created descendant path; this node if no segments result
     * @throws IllegalArgumentException if any resulting segment is blank or contains {@code >}
     */
    public Path resolveOrCreateChild(String... paths) {
        return walkSplit(paths, true);
    }

    /**
     * List variant of {@link #resolveOrCreateChild(String...)}; see that method for full semantics.
     *
     * @param paths path strings forming a relative path from this node; {@code null} returns this node
     * @return the existing or newly created descendant path; this node if no segments result
     * @throws IllegalArgumentException if any resulting segment is blank or contains {@code >}
     */
    public Path resolveOrCreateChild(List<String> paths) {
        return resolveOrCreateChild(paths == null ? EMPTY_SEGMENTS : paths.toArray(new String[0]));
    }

    private Path walkSplit(String[] paths, boolean createMissing) {
        if (paths == null || paths.length == 0) {
            return this;
        }
        Path current = this;
        for (String path : paths) {
            if (path == null) {
                continue;
            }
            String[] segments = SPLIT_CACHE.computeIfAbsent(path, Path::splitSegments);
            current = createMissing ? current.getOrCreateChild(segments) : current.getChild(segments);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * Returns the descendant path node reached by walking the given segment names as a
     * relative path from this node, creating any missing intermediate children along the
     * way. Concurrent calls with the same names return the identical instance for each
     * level.
     *
     * <p>With no arguments (or a {@code null} array) returns this node itself.
     *
     * @param names zero or more segment names; each must be non-{@code null}, non-blank,
     *     and must not contain {@code >}
     * @return the existing or newly created descendant path
     * @throws IllegalArgumentException if any of the names is {@code null}, blank, or
     *     contains {@code >}
     */
    public Path getOrCreateChild(String... names) {
        if (names == null || names.length == 0) {
            return this;
        }
        Path current = this;
        for (String name : names) {
            if (name == null) {
                throw new IllegalArgumentException("Path segment must not be null");
            }
            Path next = current.children.get(name);
            if (next == null) {
                validate(name);
                final Path nodeParent = current;
                next = current.children.computeIfAbsent(name, s -> new Path(nodeParent, s));
            }
            current = next;
        }
        return current;
    }

    /**
     * List variant of {@link #getOrCreateChild(String...)}; see that method for full semantics.
     *
     * @param names segment names; {@code null} returns this node
     * @return the existing or newly created descendant path
     * @throws IllegalArgumentException if any of the names is {@code null}, blank, or contains {@code >}
     */
    public Path getOrCreateChild(List<String> names) {
        return getOrCreateChild(names == null ? EMPTY_SEGMENTS : names.toArray(new String[0]));
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
     * Returns a legacy {@link com.im.njams.sdk.common.Path} equivalent to this path,
     * constructed from this path's full string representation.
     *
     * <p>Intended only as a migration helper for code still consuming
     * {@link com.im.njams.sdk.common.Path}. Switch call sites to use {@link Path} directly.
     *
     * @return a new legacy path instance with the same {@code toString()} value as this path
     * @deprecated Switch callers from {@link com.im.njams.sdk.common.Path} to the new
     *     {@link Path} type. This bridge exists only to ease incremental migration.
     */
    @Deprecated
    public com.im.njams.sdk.common.Path toLegacyPath() {
        return new com.im.njams.sdk.common.Path(pathString);
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
