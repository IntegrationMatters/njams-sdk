package com.im.njams.sdk.configuration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Path;
import com.im.njams.sdk.configuration.provider.MemoryConfigurationProvider;

/**
 * Tests the {@link Configuration}/{@link ProcessFilter} methods that take a process path, in their legacy
 * ({@link com.im.njams.sdk.common.Path}) and new ({@link Path}) form. The legacy tests are a baseline written before
 * the new overloads were introduced; they must stay green unchanged.
 */
@SuppressWarnings("removal")
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

    private static com.im.njams.sdk.common.Path legacy(String... parts) {
        return new com.im.njams.sdk.common.Path(parts);
    }

    // ---- legacy behavior baseline ----

    @Test
    public void legacyGetProcessCreatesAndReturnsSameConfigurationAsStringVariant() {
        ProcessConfiguration created = config.getProcess(legacy("a", "b"));
        assertNotNull(created);
        assertSame(created, config.getProcess(">a>b>"));
        assertSame(created, config.getProcess(legacy("a", "b")));
    }

    @Test
    public void legacyHasProcessReflectsExistenceOfProcessConfiguration() {
        assertFalse(config.hasProcess(legacy("a", "b")));
        config.getProcess(">a>b>");
        assertTrue(config.hasProcess(legacy("a", "b")));
        assertFalse(config.hasProcess(legacy("a", "c")));
    }

    @Test
    public void legacyHasProcessExcludeFilterFollowsSetProcessExcluded() {
        assertFalse(config.hasProcessExcludeFilter(legacy("a", "b")));
        config.setProcessExcluded(legacy("a", "b"), true);
        assertTrue(config.hasProcessExcludeFilter(legacy("a", "b")));
        assertTrue(config.isProcessExcluded(legacy("a", "b")));
        config.setProcessExcluded(legacy("a", "b"), false);
        assertFalse(config.hasProcessExcludeFilter(legacy("a", "b")));
        assertFalse(config.isProcessExcluded(legacy("a", "b")));
    }

    @Test
    public void legacyIsProcessExcludedWithNullPathIsExcluded() {
        assertTrue(config.isProcessExcluded((com.im.njams.sdk.common.Path) null));
    }

    @Test
    public void legacyIsSelectedWithNullPathIsFalse() {
        assertFalse(new ProcessFilter(config).isSelected((com.im.njams.sdk.common.Path) null));
    }

    @Test
    public void legacySetExcludedOnFilterAddsAndRemovesExcludeFilter() {
        ProcessFilter filter = new ProcessFilter(config);
        assertFalse(filter.hasExcludeFilter(legacy("a", "b")));
        filter.setExcluded(legacy("a", "b"), true);
        assertTrue(filter.hasExcludeFilter(legacy("a", "b")));
        filter.setExcluded(legacy("a", "b"), false);
        assertFalse(filter.hasExcludeFilter(legacy("a", "b")));
    }

    @Test
    public void legacyIsSelectedIgnoresInstanceIdentity() {
        ProcessFilter filter = new ProcessFilter(config);
        config.setProcessExcluded(legacy("x", "y"), true);
        filter = new ProcessFilter(config);
        com.im.njams.sdk.common.Path first = legacy("x", "y");
        com.im.njams.sdk.common.Path second = legacy("x", "y");
        assertNotSame(first, second);
        assertFalse(filter.isSelected(first));
        assertFalse(filter.isSelected(second));
    }

    // ---- new Path overloads ----

    @Test
    public void getProcessWithNewPathSharesConfigurationWithStringAndLegacyVariants() {
        ProcessConfiguration created = config.getProcess(Path.of("a", "b"));
        assertNotNull(created);
        assertSame(created, config.getProcess(">a>b>"));
        assertSame(created, config.getProcess(legacy("a", "b")));
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
        assertTrue(config.hasProcessExcludeFilter(legacy("a", "b")));
        assertTrue(config.isProcessExcluded(Path.of("a", "b")));
        assertTrue(config.isProcessExcluded(legacy("a", "b")));

        config.setProcessExcluded(legacy("a", "b"), false);
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
        assertTrue(filter.hasExcludeFilter(legacy("a", "b")));
        filter.setExcluded(Path.of("a", "b"), false);
        assertFalse(filter.hasExcludeFilter(Path.of("a", "b")));
    }

    @Test
    public void isSelectedGivesSameResultForNewAndLegacyPath() {
        config.setProcessExcluded(Path.of("x", "y"), true);
        ProcessFilter filter = new ProcessFilter(config);
        // the decision is cached per path string, so both forms must agree, whichever is asked first
        assertFalse(filter.isSelected(Path.of("x", "y")));
        assertFalse(filter.isSelected(legacy("x", "y")));
        assertTrue(filter.isSelected(Path.of("x", "z")));
        assertTrue(filter.isSelected(legacy("x", "z")));
    }

    @Test
    public void isSelectedWithLegacyPathFirstThenNewPathAgree() {
        config.setProcessExcluded(legacy("p", "q"), true);
        ProcessFilter filter = new ProcessFilter(config);
        assertFalse(filter.isSelected(legacy("p", "q")));
        assertFalse(filter.isSelected(Path.of("p", "q")));
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
    public void setExcludedOnFilterWithLegacyPathUpdatesSelection() {
        ProcessFilter filter = filterIncluding(">a>.*");
        assertTrue(filter.isSelected(legacy("a", "b")));
        filter.setExcluded(legacy("a", "b"), true);
        assertFalse(filter.isSelected(legacy("a", "b")));
        filter.setExcluded(legacy("a", "b"), false);
        assertTrue(filter.isSelected(legacy("a", "b")));
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
