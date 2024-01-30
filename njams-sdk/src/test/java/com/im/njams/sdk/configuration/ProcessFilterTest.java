package com.im.njams.sdk.configuration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.configuration.ProcessFilterEntry.FilterType;
import com.im.njams.sdk.configuration.ProcessFilterEntry.MatcherType;

public class ProcessFilterTest {

    private static class Builder {
        Configuration config = new Configuration();

        public Builder inValue(String value) {
            ProcessFilterEntry e = new ProcessFilterEntry();
            e.setFilterType(FilterType.INCLUDE);
            e.setMatcherType(MatcherType.VALUE);
            e.setFilterValue(value);
            config.getProcessFilter().add(e);
            return this;
        }

        public Builder exValue(String value) {
            ProcessFilterEntry e = new ProcessFilterEntry();
            e.setFilterType(FilterType.EXCLUDE);
            e.setMatcherType(MatcherType.VALUE);
            e.setFilterValue(value);
            config.getProcessFilter().add(e);
            return this;
        }

        public Builder inPattern(String value) {
            ProcessFilterEntry e = new ProcessFilterEntry();
            e.setFilterType(FilterType.INCLUDE);
            e.setMatcherType(MatcherType.REGEX);
            e.setFilterValue(value);
            config.getProcessFilter().add(e);
            return this;
        }

        public Builder exPattern(String value) {
            ProcessFilterEntry e = new ProcessFilterEntry();
            e.setFilterType(FilterType.EXCLUDE);
            e.setMatcherType(MatcherType.REGEX);
            e.setFilterValue(value);
            config.getProcessFilter().add(e);
            return this;
        }

        public Builder process(String path, boolean exclude) {
            config.getProcess(path).setExclude(exclude);
            return this;
        }

        public ProcessFilter build() {
            return new ProcessFilter(config);
        }
    }

    @Test
    public void testIsSelectedValue() {
        ProcessFilter filter = new Builder().exValue(">a>.>c>").build();
        assertTrue(filter.isSelected(new Path(">a>b>c>")));
        assertTrue(filter.isSelected(new Path(">a>")));
        assertFalse(filter.isSelected(new Path(">a>.>c>")));

        filter = new Builder().inValue(">a>.>c>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertFalse(filter.isSelected(new Path(">a>")));
        assertTrue(filter.isSelected(new Path(">a>.>c>")));

        filter = new Builder().inValue(">a>.>c>").exValue(">a>b>c>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertFalse(filter.isSelected(new Path(">a>")));
        assertTrue(filter.isSelected(new Path(">a>.>c>")));

    }

    @Test
    public void testIsSelectedPattern() {
        ProcessFilter filter = new Builder().exPattern(">a>.>c>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertTrue(filter.isSelected(new Path(">a>")));
        assertFalse(filter.isSelected(new Path(">a>.>c>")));
        assertFalse(filter.isSelected(new Path(">a>.>c>")));

        filter = new Builder().inPattern(">a>.>c>").build();
        assertTrue(filter.isSelected(new Path(">a>b>c>")));
        assertFalse(filter.isSelected(new Path(">a>")));
        assertTrue(filter.isSelected(new Path(">a>.>c>")));

        filter = new Builder().inPattern(">a>.>c>").exPattern(">a>b>.>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertTrue(filter.isSelected(new Path(">a>d>c>")));
        assertFalse(filter.isSelected(new Path(">a>")));
        assertTrue(filter.isSelected(new Path(">a>.>c>")));

    }

    @Test
    public void testIsSelectedProcess() {
        ProcessFilter filter = new Builder().process(">a>b>c>", true).process(">a>b>d>", false).build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertTrue(filter.isSelected(new Path(">a>b>d>")));

        filter = new Builder().process(">a>b>c>", true).process(">a>b>d>", false).exValue(">a>b>d>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertFalse(filter.isSelected(new Path(">a>b>d>")));

        filter = new Builder().process(">a>b>c>", true).process(">a>b>d>", true).inValue(">a>b>d>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertFalse(filter.isSelected(new Path(">a>b>d>")));
    }

}
