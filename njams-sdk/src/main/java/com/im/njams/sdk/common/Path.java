/* 
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.common;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Path object for handling pathes. A path consists of several path parts. The
 * String representation uses the &gt; as separator between path elements.
 *
 * @author pnientiedt
 */
public class Path implements Comparable<Path> {

    private final String pathAsString;
    private final List<String> parts;

    /**
     * Construct Path with several string parts.
     *
     * @param parts an array of path parts (or a comma separated list of path
     * parts).
     */
    public Path(final String... parts) {
        this(asList(parts));
    }

    /**
     * Construct Path with several string parts.
     *
     * @param parts a list of path parts.
     */
    public Path(final List<String> parts) {
        if (parts.size() == 1 && parts.get(0).contains(">")) {
            this.parts
                    = asList(parts.get(0).split(">")).stream().filter(Objects::nonNull).filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
            pathAsString = parts.get(0);
        } else {
            this.parts = new ArrayList<>(parts);
            pathAsString = ">" + this.parts.stream().collect(Collectors.joining(">")) + ">";
        }
    }

    /**
     * The Object Name is the last part of the path.
     *
     * @return the last part of the pass.
     */
    public String getObjectName() {
        return parts.get(parts.size() - 1);
    }

    /**
     * Return Path as one String separated by &gt;
     *
     * @return the path
     */
    @Override
    public String toString() {
        return pathAsString;
    }

    /**
     * Get all parts as list of string
     *
     * @return a string list containing all path elements
     */
    public List<String> getParts() {
        return parts;
    }

    /**
     * Creates a new path Add this a given base/prefix to an existing path. All
     * new path element will be appended in the front of the path. The Path will
     * not be changed.
     *
     * @param prefix the new path elements to be added
     * @return New path starting with prefix path and the continuing with
     * current path.
     */
    public Path addBase(final Path prefix) {
        final List<String> newParts = new ArrayList<>();
        newParts.addAll(prefix.getParts());
        newParts.addAll(parts);
        return new Path(newParts);
    }

    /**
     * Add new path elements
     *
     * @param postfix new path elements
     * @return new path
     */
    public Path add(final String... postfix) {
        final List<String> newPath = new ArrayList<>(parts.size() + postfix.length);
        newPath.addAll(parts);
        asList(postfix).forEach(newPath::add);
        return new Path(newPath);
    }

    /**
     * Add new path element
     *
     * @param postfix new path elements
     * @return new path
     */
    public Path add(final Path postfix) {
        final List<String> newPath = new ArrayList<>(parts.size() + postfix.parts.size());
        newPath.addAll(parts);
        newPath.addAll(postfix.getParts());
        return new Path(newPath);
    }

    /**
     * Returns the parent path for this path, i.e., a new path based on this
     * path but truncated by the last element.
     *
     * @return <code>null</code> if this path has no parent path.
     */
    public Path getParent() {
        if (parts.size() < 2) {
            return null;
        }
        return new Path(parts.subList(0, parts.size() - 1));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(pathAsString);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Path other = (Path) obj;
        return pathAsString.equals(other.pathAsString);
    }

    @Override
    public int compareTo(Path o) {
        return pathAsString.compareTo(o.pathAsString);
    }
}
