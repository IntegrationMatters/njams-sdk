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
     * @param segments zero or more non-null, non-blank path segment names; must not contain {@code >}.
     *     The single value {@code ">"} is treated as the root and returns {@link #ROOT}.
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
            current = current.getOrCreateChild(seg);
        }
        return current;
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
     * Returns this node's own segment name (the last component of its path), or
     * {@code null} if this is the {@link #ROOT}.
     *
     * @return the segment name, or {@code null} for root
     */
    public String getSegmentName() {
        return segment;
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
     * Tests whether a child node with the given segment name has been created under this path.
     *
     * @param name the segment name to look up; {@code null} always returns {@code false}
     * @return {@code true} if a child by that name exists; {@code false} otherwise
     */
    public boolean hasChild(String name) {
        return name != null && children.containsKey(name);
    }

    /**
     * Returns the child path node with the given segment name, creating it if it does
     * not yet exist. Concurrent calls with the same name return the identical instance.
     *
     * @param name the segment name; must not be {@code null}, blank, or contain {@code >}
     * @return the existing or newly created child path
     * @throws IllegalArgumentException if {@code name} is {@code null}, blank, or contains {@code >}
     */
    public Path getOrCreateChild(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Path segment must not be null");
        }
        Path next = children.get(name);
        if (next == null) {
            validate(name);
            next = children.computeIfAbsent(
                    name,
                    s -> new Path(this, s, pathString + s + ">"));
        }
        return next;
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
