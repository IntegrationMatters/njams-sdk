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
