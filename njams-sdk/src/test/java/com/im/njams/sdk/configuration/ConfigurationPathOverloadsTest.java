package com.im.njams.sdk.configuration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Path;
import com.im.njams.sdk.configuration.provider.MemoryConfigurationProvider;

/**
 * Tests the {@link Configuration}/{@link ProcessFilter} methods that take a process {@link Path}.
 */
public class ConfigurationPathOverloadsTest {

    private Configuration config;

    @Before
    public void setUp() {
        config = new Configuration() {
            @Override
            public void save() {
                // no persistence needed
            }
        };
        config.setConfigurationProvider(new MemoryConfigurationProvider());
    }

    @Test
    public void getProcessWithNewPathSharesConfigurationWithStringAndLegacyVariants() {
        ProcessConfiguration created = config.getProcess(Path.of("a", "b"));
        assertNotNull(created);
        assertSame(created, config.getProcess(">a>b>"));
        assertSame(created, config.getProcess(Path.of("a", "b")));
    }

    @Test
    public void hasProcessWithNewPathReflectsExistenceOfProcessConfiguration() {
        assertFalse(config.hasProcess(Path.of("a", "b")));
        config.getProcess(">a>b>");
        assertTrue(config.hasProcess(Path.of("a", "b")));
        assertFalse(config.hasProcess(Path.of("a", "c")));
    }

    @Test
    public void newAndLegacyExcludeSettingsAreInterchangeable() {
        config.setProcessExcluded(Path.of("a", "b"), true);
        assertTrue(config.hasProcessExcludeFilter(Path.of("a", "b")));
        assertTrue(config.isProcessExcluded(Path.of("a", "b")));

        config.setProcessExcluded(Path.of("a", "b"), false);
        assertFalse(config.hasProcessExcludeFilter(Path.of("a", "b")));
        assertFalse(config.isProcessExcluded(Path.of("a", "b")));
    }

    @Test
    public void isProcessExcludedWithNullNewPathIsExcluded() {
        assertTrue(config.isProcessExcluded((Path) null));
    }

    @Test
    public void isSelectedWithNullNewPathIsFalse() {
        assertFalse(new ProcessFilter(config).isSelected((Path) null));
    }

    @Test
    public void setExcludedOnFilterWithNewPathAddsAndRemovesExcludeFilter() {
        ProcessFilter filter = new ProcessFilter(config);
        assertFalse(filter.hasExcludeFilter(Path.of("a", "b")));
        filter.setExcluded(Path.of("a", "b"), true);
        assertTrue(filter.hasExcludeFilter(Path.of("a", "b")));
        filter.setExcluded(Path.of("a", "b"), false);
        assertFalse(filter.hasExcludeFilter(Path.of("a", "b")));
    }

    @Test
    public void isSelectedGivesSameResultForNewAndLegacyPath() {
        config.setProcessExcluded(Path.of("x", "y"), true);
        ProcessFilter filter = new ProcessFilter(config);
        assertFalse(filter.isSelected(Path.of("x", "y")));
        assertTrue(filter.isSelected(Path.of("x", "z")));
    }

    // ---- ProcessFilter.setExcluded keeps the decision cache consistent ----

    private ProcessFilter filterIncluding(String regex) {
        config.addProcessFilter(new ProcessFilterEntry(ProcessFilterEntry.FilterType.INCLUDE,
            ProcessFilterEntry.MatcherType.REGEX, regex));
        return new ProcessFilter(config);
    }

    @Test
    public void setExcludedOnFilterWithNewPathUpdatesSelection() {
        ProcessFilter filter = filterIncluding(">a>.*");
        assertTrue(filter.isSelected(Path.of("a", "b")));
        filter.setExcluded(Path.of("a", "b"), true);
        assertFalse(filter.isSelected(Path.of("a", "b")));
        filter.setExcluded(Path.of("a", "b"), false);
        assertTrue(filter.isSelected(Path.of("a", "b")));
    }

    @Test
    public void removingExplicitExcludeDoesNotSelectProcessExcludedByOtherFilter() {
        config.addProcessFilter(new ProcessFilterEntry(ProcessFilterEntry.FilterType.EXCLUDE,
            ProcessFilterEntry.MatcherType.REGEX, ">a>.*"));
        ProcessFilter filter = new ProcessFilter(config);
        filter.setExcluded(Path.of("a", "b"), true);
        filter.setExcluded(Path.of("a", "b"), false);
        assertFalse(filter.isSelected(Path.of("a", "b")));
    }
}
