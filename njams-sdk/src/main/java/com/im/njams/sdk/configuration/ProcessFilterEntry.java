package com.im.njams.sdk.configuration;

public class ProcessFilterEntry {
    public enum MatcherType {
        VALUE, REGEX
    }

    public enum FilterType {
        INCLUDE, EXCLUDE
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
