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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.configuration;

/**
 * Defines a filter to be matched against process paths.
 */
public class ProcessFilterEntry {
    /**
     * The matcher to be used when comparing the process path against the filter.
     */
    public enum MatcherType {
        /** The given filter value is a plain value (literal) */
        VALUE,
        /** The given filter value is a regular expression. */
        REGEX
    }

    /**
     * Defines the type of the filter (include or exclude)
     */
    public enum FilterType {
        /** The filter value should be used for including processes. */
        INCLUDE,
        /** The filter value should be used for excluding processes. */
        EXCLUDE
    }

    private String filterValue = null;
    private FilterType filterType = FilterType.EXCLUDE;
    private MatcherType matcherType = MatcherType.VALUE;

    /**
     * Default constructor for de-serialization.
     */
    public ProcessFilterEntry() {
        // nothing
    }

    /**
     * Full initializing constructor.
     * @param type The filter type for this instance.
     * @param matcher The matcher type for this instance.
     * @param value The actual match/filter value.
     */
    public ProcessFilterEntry(FilterType type, MatcherType matcher, String value) {
        filterType = type;
        matcherType = matcher;
        filterValue = value;
    }

    public String getFilterValue() {
        return filterValue;
    }

    public void setFilterValue(String filterValue) {
        this.filterValue = filterValue;
    }

    public FilterType getFilterType() {
        return filterType;
    }

    public void setFilterType(FilterType filterType) {
        this.filterType = filterType;
    }

    public MatcherType getMatcherType() {
        return matcherType;
    }

    public void setMatcherType(MatcherType matcherType) {
        this.matcherType = matcherType;
    }

}
