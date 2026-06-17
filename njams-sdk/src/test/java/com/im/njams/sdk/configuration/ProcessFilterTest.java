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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.configuration.ProcessFilterEntry.FilterType;
import com.im.njams.sdk.configuration.ProcessFilterEntry.MatcherType;
import com.im.njams.sdk.configuration.provider.MemoryConfigurationProvider;
import com.im.njams.sdk.settings.HierarchicalSettings;

public class ProcessFilterTest {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessFilterTest.class);

    private static class Builder {
        final Configuration config;
        final Map<String, String> settingsProps = new HashMap<>();

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

        public Builder settingExclude(String regex) {
            settingsProps.put(NjamsSettings.PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX + "p" + settingsProps.size(), regex);
            return this;
        }

        public ProcessFilter build() {
            config.setConfigurationProvider(new MemoryConfigurationProvider());
            return new ProcessFilter(config, HierarchicalSettings.from(settingsProps).build());
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

    /**
     * Serializes the given configuration and loads it back exactly as the (default)
     * FileConfigurationProvider does at startup, so the resulting configuration's process filter is
     * exercised the same way it is at runtime (built only after the filter list is populated).
     */
    private static Configuration loadRoundTrip(Configuration source) throws Exception {
        final String json = JsonSerializerFactory.getDefaultMapper().writeValueAsString(source);
        final Configuration loaded = JsonSerializerFactory.getDefaultMapper().readValue(json, Configuration.class);
        loaded.setConfigurationProvider(new MemoryConfigurationProvider());
        return loaded;
    }

    @Test
    public void testExcludeFiltersFromLoadedConfigurationAreApplied() throws Exception {
        // Reproducer for SDK-451: exclude filters present in a configuration that is loaded the way
        // it happens at runtime must take effect. This must NOT use the Builder, which constructs
        // the ProcessFilter only AFTER the filters have been added and therefore hides the bug.
        final Configuration source = new Configuration();
        source.addProcessFilter(new ProcessFilterEntry(FilterType.EXCLUDE, MatcherType.VALUE, ">a>b>c>"));
        source.addProcessFilter(new ProcessFilterEntry(FilterType.EXCLUDE, MatcherType.REGEX, ">x>.*"));
        final Configuration loaded = loadRoundTrip(source);

        assertTrue("literal exclude from loaded configuration must be applied",
            loaded.isProcessExcluded(new Path(">a>b>c>")));
        assertTrue("regex exclude from loaded configuration must be applied",
            loaded.isProcessExcluded(new Path(">x>y>")));
        assertFalse("a non-matching process must not be excluded",
            loaded.isProcessExcluded(new Path(">q>r>")));
    }

    @Test
    public void testIncludeFiltersFromLoadedConfigurationAreApplied() throws Exception {
        // Include filters switch the filter into whitelist mode; this must also survive loading.
        final Configuration source = new Configuration();
        source.addProcessFilter(new ProcessFilterEntry(FilterType.INCLUDE, MatcherType.VALUE, ">a>b>c>"));
        source.addProcessFilter(new ProcessFilterEntry(FilterType.INCLUDE, MatcherType.REGEX, ">in>.*"));
        final Configuration loaded = loadRoundTrip(source);

        assertFalse("explicitly included process must be selected", loaded.isProcessExcluded(new Path(">a>b>c>")));
        assertFalse("regex-included process must be selected", loaded.isProcessExcluded(new Path(">in>x>")));
        assertTrue("non-included process must be excluded in whitelist mode",
            loaded.isProcessExcluded(new Path(">a>b>d>")));
    }

    @Test
    public void testFilterAddedAfterFirstAccessTakesEffect() throws Exception {
        // A server command may exclude a process during a running session, after the filter has
        // already been built. The change must take effect (the cached filter is invalidated).
        final Configuration loaded = loadRoundTrip(new Configuration());
        assertFalse("no filters yet -> nothing excluded", loaded.isProcessExcluded(new Path(">a>b>c>")));

        loaded.setProcessExcluded(new Path(">a>b>c>"), true);
        assertTrue("exclude added during the session must take effect",
            loaded.isProcessExcluded(new Path(">a>b>c>")));

        loaded.setProcessExcluded(new Path(">a>b>c>"), false);
        assertFalse("removing the exclude during the session must take effect",
            loaded.isProcessExcluded(new Path(">a>b>c>")));
    }

    @Test
    public void testSettingPatternExcludesMatchingProcess() {
        ProcessFilter filter = new Builder().settingExclude(">a>b>.*").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertTrue(filter.isSelected(new Path(">a>x>c>")));
    }

    @Test
    public void testSettingPatternWorksWithNoOtherFilters() {
        // Regression for the excludeNone short-circuit: with ONLY a setting pattern and no server
        // filters, the pattern must still be evaluated.
        ProcessFilter filter = new Builder().settingExclude(">a>b>c>").build();
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertTrue(filter.isSelected(new Path(">a>b>d>")));
    }

    @Test
    public void testSettingPatternOredWithServerExclude() {
        ProcessFilter filter = new Builder().exValue(">x>y>z>").settingExclude(">a>.*").build();
        assertFalse(filter.isSelected(new Path(">x>y>z>")));
        assertFalse(filter.isSelected(new Path(">a>b>c>")));
        assertTrue(filter.isSelected(new Path(">q>r>s>")));
    }

    @Test
    public void testExplicitIncludeOverridesSettingPattern() {
        // Documented precedence: an exact-value include wins over the setting exclude pattern.
        ProcessFilter filter = new Builder().settingExclude(">a>b>.*").inValue(">a>b>c>").build();
        assertTrue(filter.isSelected(new Path(">a>b>c>")));
        assertFalse(filter.isSelected(new Path(">a>b>d>")));
    }

    @Test
    public void testInvalidSettingPatternIsIgnored() {
        ProcessFilter filter = new Builder().settingExclude("[invalid(").build();
        assertTrue(filter.isSelected(new Path(">a>b>c>")));
    }

    @Test
    public void testSettingPatternCaseSensitivity() {
        assertTrue(new Builder().settingExclude(">A>B>.*").build().isSelected(new Path(">a>b>c>")));
        assertFalse(new Builder().settingExclude("(?i)>A>B>.*").build().isSelected(new Path(">a>b>c>")));
    }

    @Test
    public void testInitFilterAppliesSettingPatterns() {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX + "x", ">a>b>.*");
        Configuration config = new Configuration();
        config.setConfigurationProvider(new MemoryConfigurationProvider());
        config.initFilter(HierarchicalSettings.from(props).build());
        assertFalse(config.isProcessExcluded(new Path(">a>x>")));
        assertTrue(config.isProcessExcluded(new Path(">a>b>c>")));
    }

    @Test
    public void testInitFilterAfterFirstAccessRebuildsFilter() {
        Configuration config = new Configuration();
        config.setConfigurationProvider(new MemoryConfigurationProvider());
        // first access builds the filter lazily without settings patterns
        assertFalse(config.isProcessExcluded(new Path(">a>b>c>")));

        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX + "x", ">a>b>.*");
        config.initFilter(HierarchicalSettings.from(props).build());
        assertTrue(config.isProcessExcluded(new Path(">a>b>c>")));
    }

    @Test
    public void testSettingPatternSurvivesConfigMutation() {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX + "x", ">a>b>.*");
        Configuration config = new Configuration();
        config.setConfigurationProvider(new MemoryConfigurationProvider());
        config.initFilter(HierarchicalSettings.from(props).build());
        assertTrue(config.isProcessExcluded(new Path(">a>b>c>")));

        // a server command mutates the filter list; the filter is rebuilt and the settings-based
        // pattern must be preserved (not re-read from settings, but carried over)
        config.setProcessExcluded(new Path(">z>z>"), true);
        assertTrue(config.isProcessExcluded(new Path(">z>z>")));
        assertTrue(config.isProcessExcluded(new Path(">a>b>c>")));
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
