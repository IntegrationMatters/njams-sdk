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
