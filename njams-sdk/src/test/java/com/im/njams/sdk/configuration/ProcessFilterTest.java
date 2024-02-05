package com.im.njams.sdk.configuration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.configuration.ProcessFilterEntry.FilterType;
import com.im.njams.sdk.configuration.ProcessFilterEntry.MatcherType;
import com.im.njams.sdk.configuration.provider.MemoryConfigurationProvider;

public class ProcessFilterTest {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessFilterTest.class);

    private static class Builder {
        final Configuration config;

        public Builder() {
            config = new Configuration() {
                @Override
                public void save() {
                    try {
                        LOG.debug(JsonSerializerFactory.getDefaultMapper().writeValueAsString(this));
                    } catch (JsonProcessingException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            };
            config.setConfigurationProvider(new MemoryConfigurationProvider());
        }

        public Builder inValue(String value) {
            config.addProcessFilter(new ProcessFilterEntry(FilterType.INCLUDE, MatcherType.VALUE, value));
            return this;
        }

        public Builder exValue(String value) {
            config.addProcessFilter(new ProcessFilterEntry(FilterType.EXCLUDE, MatcherType.VALUE, value));
            return this;
        }

        public Builder inPattern(String value) {
            config.addProcessFilter(new ProcessFilterEntry(FilterType.INCLUDE, MatcherType.REGEX, value));
            return this;
        }

        public Builder exPattern(String value) {
            config.addProcessFilter(new ProcessFilterEntry(FilterType.EXCLUDE, MatcherType.REGEX, value));
            return this;
        }

        public Builder process(String path, boolean exclude) {
            config.setProcessExcluded(new Path(path), exclude);
            return this;
        }

        public Builder oldConfig(String path, boolean exclude) {
            config.getProcess(path).setExclude(exclude);
            return this;
        }

        public ProcessFilter build() {
            config.setConfigurationProvider(new MemoryConfigurationProvider());
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
        assertTrue(filter.hasExcludeFilter(new Path(">a>b>c>")));
        assertFalse(filter.hasExcludeFilter(new Path(">a>b>d>")));

        filter = new Builder().process(">a>b>c>", true).process(">a>b>d>", false).exValue(">a>b>d>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertFalse(filter.isSelected(new Path(">a>b>d>")));
        assertTrue(filter.hasExcludeFilter(new Path(">a>b>c>")));
        assertTrue(filter.hasExcludeFilter(new Path(">a>b>d>")));

        filter = new Builder().process(">a>b>c>", true).process(">a>b>d>", true).inValue(">a>b>d>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertFalse(filter.isSelected(new Path(">a>b>d>")));
        assertTrue(filter.hasExcludeFilter(new Path(">a>b>c>")));
        assertTrue(filter.hasExcludeFilter(new Path(">a>b>d>")));
    }

    @Test
    public void testConvertOldConfig() {
        ProcessFilter filter = new Builder().oldConfig(">a>b>c>", true).oldConfig(">a>b>d>", false).build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertTrue(filter.isSelected(new Path(">a>b>d>")));
        assertTrue(filter.hasExcludeFilter(new Path(">a>b>c>")));
        assertFalse(filter.hasExcludeFilter(new Path(">a>b>d>")));

        filter = new Builder().oldConfig(">a>b>c>", true).oldConfig(">a>b>d>", false).exValue(">a>b>d>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertFalse(filter.isSelected(new Path(">a>b>d>")));
        assertTrue(filter.hasExcludeFilter(new Path(">a>b>c>")));
        assertTrue(filter.hasExcludeFilter(new Path(">a>b>d>")));

        filter = new Builder().oldConfig(">a>b>c>", true).oldConfig(">a>b>d>", true).inValue(">a>b>d>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertFalse(filter.isSelected(new Path(">a>b>d>")));
        assertTrue(filter.hasExcludeFilter(new Path(">a>b>c>")));
        assertTrue(filter.hasExcludeFilter(new Path(">a>b>d>")));
    }

}
