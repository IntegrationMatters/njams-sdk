package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.settings.ClientSettings;

@SuppressWarnings("removal")
public class DataMaskerTest {

    @Before
    @After
    public void clearJvmWidePatterns() {
        DataMasking.removePatterns();
    }

    @Test
    public void masksWithOwnPatterns() {
        DataMasker masker = new DataMasker();
        masker.addPattern("secret");
        assertEquals("a ****** b", masker.maskString("a secret b"));
    }

    @Test
    public void blankAndNullInputAreReturnedUnchanged() {
        DataMasker masker = new DataMasker();
        masker.addPattern(".*");
        assertNull(masker.maskString(null));
        assertEquals(" ", masker.maskString(" "));
    }

    @Test
    public void instancesAreIndependent() {
        DataMasker a = new DataMasker();
        DataMasker b = new DataMasker();
        a.addPattern("secret");
        assertEquals("secret", b.maskString("secret"));
        assertTrue(b.getPatterns().isEmpty());
    }

    @Test
    public void duplicateRegexIsIgnoredRegardlessOfName() {
        DataMasker masker = new DataMasker();
        masker.addPattern("first", "secret");
        masker.addPattern("second", "secret");
        masker.addPatterns(Arrays.asList("secret", "other"));
        assertEquals(2, masker.getPatterns().size());
        assertEquals("first", masker.getPatterns().get(0).getNameOfPattern());
    }

    @Test
    public void defaultNameIsIndex() {
        DataMasker masker = new DataMasker();
        masker.addPattern("a");
        masker.addPattern("b");
        assertEquals("0", masker.getPatterns().get(0).getNameOfPattern());
        assertEquals("1", masker.getPatterns().get(1).getNameOfPattern());
    }

    @Test
    public void invalidOrBlankRegexIsIgnored() {
        DataMasker masker = new DataMasker();
        masker.addPattern("[");
        masker.addPattern(" ");
        masker.addPattern(null);
        assertTrue(masker.getPatterns().isEmpty());
    }

    @Test
    public void addPatternsFromSettings() {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + "ssn", "\\d{3}-\\d{2}-\\d{4}");
        settings.put("unrelated", "x");
        DataMasker masker = new DataMasker();
        masker.addPatterns(settings);
        assertEquals(1, masker.getPatterns().size());
        assertEquals("ssn", masker.getPatterns().get(0).getNameOfPattern());
        assertEquals("a *********** b", masker.maskString("a 123-45-6789 b"));
    }

    @Test
    public void replaceConfiguredPatternsReplacesPreviousConfiguredSet() {
        DataMasker masker = new DataMasker();
        masker.replaceConfiguredPatterns(settingsWith("one", "first"), Collections.singletonList("second"));
        masker.replaceConfiguredPatterns(settingsWith("two", "third"), Collections.emptyList());
        assertEquals("first second *****", masker.maskString("first second third"));
    }

    @Test
    public void replaceConfiguredPatternsKeepsClientPatterns() {
        DataMasker masker = new DataMasker();
        masker.addPattern("client");
        masker.replaceConfiguredPatterns(settingsWith("one", "configured"), Collections.emptyList());
        masker.clearConfiguredPatterns();
        assertEquals("****** configured", masker.maskString("client configured"));
    }

    @Test
    public void configuredPatternsComeFirstAndDuplicateClientRegexIsSkipped() {
        DataMasker masker = new DataMasker();
        masker.addPattern("clientName", "secret");
        masker.replaceConfiguredPatterns(settingsWith("cfg", "secret"), Collections.emptyList());
        assertEquals(1, masker.getPatterns().size());
        assertEquals("cfg", masker.getPatterns().get(0).getNameOfPattern());
    }

    @Test
    public void clientPatternDuplicatingConfiguredOneSurvivesItsRemovalFromConfiguration() {
        DataMasker masker = new DataMasker();
        masker.replaceConfiguredPatterns(settingsWith("cfg", "secret"), Collections.emptyList());
        masker.addPattern("secret");
        masker.replaceConfiguredPatterns(ClientSettings.from(new Properties()), Collections.emptyList());
        assertEquals("******", masker.maskString("secret"));
    }

    @Test
    public void removePatternsRemovesClientPatternsOnly() {
        DataMasker masker = new DataMasker();
        masker.replaceConfiguredPatterns(settingsWith("cfg", "configured"), Collections.emptyList());
        masker.addPattern("client");
        masker.removePatterns();
        assertEquals("client **********", masker.maskString("client configured"));
    }

    @Test
    public void jvmWidePatternsAreAppliedOnlyWhenRequested() {
        DataMasking.addPattern("legacy");
        assertEquals("******", new DataMasker(true).maskString("legacy"));
        assertEquals("legacy", new DataMasker().maskString("legacy"));
    }

    @Test
    public void concurrentAddAndMask() throws Exception {
        DataMasker masker = new DataMasker();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                final int n = i % 50;
                futures.add(pool.submit(() -> {
                    masker.addPattern("secret" + n);
                    masker.maskString("some secret0 and secret" + n + " text");
                    masker.getPatterns().size();
                }));
            }
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(50, masker.getPatterns().size());
    }

    private static ClientSettings settingsWith(String name, String regex) {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + name, regex);
        return settings;
    }
}
