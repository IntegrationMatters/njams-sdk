/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 */
package com.im.njams.sdk.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Covers the default methods on {@link ReadOnlySettings} / {@link WritableSettings} and the
 * {@code from(...)} factories. Exercises the {@code ReadOnlySettingsImpl} / {@code WritableSettingsImpl}
 * behavior indirectly through the factories.
 */
public class ReadOnlySettingsTest {

    private Map<String, String> backing;
    private WritableSettings settings;

    @Before
    public void setUp() {
        backing = new HashMap<>();
        settings = WritableSettings.from(backing);
    }

    // ---------- factories ----------

    @Test
    public void fromMap_readsBackingChanges() {
        backing.put("a", "1");
        assertEquals("1", settings.getProperty("a"));
        backing.put("b", "2");
        assertEquals("2", settings.getProperty("b"));
    }

    @Test
    public void fromMap_writesGoToBackingMap() {
        settings.put("a", "1");
        assertEquals("1", backing.get("a"));
    }

    @Test
    public void fromProperties_isLiveView() {
        Properties props = new Properties();
        WritableSettings s = WritableSettings.from(props);
        props.setProperty("a", "1");
        assertEquals("1", s.getProperty("a"));
        s.put("b", "2");
        assertEquals("2", props.getProperty("b"));
    }

    @Test
    public void fromMap_readOnlyFactoryReturnsReadable() {
        backing.put("a", "1");
        ReadOnlySettings ro = ReadOnlySettings.from(backing);
        assertEquals("1", ro.getProperty("a"));
    }

    // ---------- fromEnvironment ----------

    @Test
    public void fromEnvironment_publicFactoryWrapsSystemEnv() {
        ReadOnlySettings ro = ReadOnlySettings.fromEnvironment(key -> true);
        int count = 0;
        for (Entry<String, String> e : ro) {
            count++;
        }
        assertEquals(System.getenv().size(), count);
    }

    @Test
    public void fromEnvironment_publicFactoryAcceptsNullFilter() {
        ReadOnlySettings ro = ReadOnlySettings.fromEnvironment(null);
        int count = 0;
        for (Entry<String, String> e : ro) {
            count++;
        }
        assertEquals(System.getenv().size(), count);
    }

    @Test
    public void environment_filterAppliesToGetProperty() {
        Map<String, String> env = new HashMap<>();
        env.put("FOO", "1");
        env.put("BAR", "2");
        ReadOnlyFilteringSettings ro =
            new ReadOnlyFilteringSettings(env, key -> key.startsWith("F"));
        assertEquals("1", ro.getProperty("FOO"));
        assertNull(ro.getProperty("BAR"));
    }

    @Test
    public void environment_filterAppliesToContainsKey() {
        Map<String, String> env = new HashMap<>();
        env.put("FOO", "1");
        env.put("BAR", "2");
        ReadOnlyFilteringSettings ro =
            new ReadOnlyFilteringSettings(env, key -> key.startsWith("F"));
        assertTrue(ro.containsKey("FOO"));
        assertFalse(ro.containsKey("BAR"));
    }

    @Test
    public void environment_filterAppliesToIteration() {
        Map<String, String> env = new HashMap<>();
        env.put("FOO", "1");
        env.put("BAR", "2");
        env.put("BAZ", "3");
        ReadOnlyFilteringSettings ro =
            new ReadOnlyFilteringSettings(env, key -> key.startsWith("B"));
        Map<String, String> seen = new HashMap<>();
        ro.forEach(e -> seen.put(e.getKey(), e.getValue()));
        assertEquals(2, seen.size());
        assertEquals("2", seen.get("BAR"));
        assertEquals("3", seen.get("BAZ"));
    }

    @Test
    public void environment_filterAppliesToDefaultFilterPrefix() {
        Map<String, String> env = new HashMap<>();
        env.put("FOO_X", "1");
        env.put("BAR_X", "2");
        ReadOnlyFilteringSettings ro =
            new ReadOnlyFilteringSettings(env, key -> key.startsWith("FOO"));
        Properties result = ro.filter("");
        assertEquals(1, result.size());
        assertEquals("1", result.getProperty("FOO_X"));
    }

    @Test
    public void environment_filterAppliesToTypedGetters() {
        Map<String, String> env = new HashMap<>();
        env.put("ALLOWED_INT", "42");
        env.put("BLOCKED_INT", "99");
        ReadOnlyFilteringSettings ro =
            new ReadOnlyFilteringSettings(env, key -> key.startsWith("ALLOWED"));
        assertEquals(42, ro.getInt("ALLOWED_INT", -1));
        assertEquals(-1, ro.getInt("BLOCKED_INT", -1));
    }

    @Test
    public void environment_acceptAllFilterExposesAllEntries() {
        Map<String, String> env = new HashMap<>();
        env.put("FOO", "1");
        env.put("BAR", "2");
        ReadOnlyFilteringSettings ro = new ReadOnlyFilteringSettings(env, key -> true);
        assertEquals("1", ro.getProperty("FOO"));
        assertEquals("2", ro.getProperty("BAR"));
        assertTrue(ro.containsKey("FOO"));
        assertTrue(ro.containsKey("BAR"));
    }

    @Test
    public void filteringSettings_nullFilterInConstructorAcceptsAllKeys() {
        Map<String, String> backing = new HashMap<>();
        backing.put("FOO", "1");
        backing.put("BAR", "2");
        ReadOnlyFilteringSettings ro = new ReadOnlyFilteringSettings(backing, null);
        assertEquals("1", ro.getProperty("FOO"));
        assertEquals("2", ro.getProperty("BAR"));
        assertTrue(ro.containsKey("FOO"));
        assertTrue(ro.containsKey("BAR"));
    }

    // ---------- fromSystemProperties ----------

    @Test
    public void fromSystemProperties_publicFactoryWrapsSystemProperties() {
        String key = "njams.test.fromSystemProperties." + System.nanoTime();
        System.setProperty(key, "value");
        try {
            WritableSettings ws = WritableSettings.fromSystemProperties(null);
            assertEquals("value", ws.getProperty(key));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    public void fromSystemProperties_appliesFilterToReads() {
        String allowed = "njams.test.allowed." + System.nanoTime();
        String blocked = "njams.test.blocked." + System.nanoTime();
        System.setProperty(allowed, "1");
        System.setProperty(blocked, "2");
        try {
            WritableSettings ws =
                WritableSettings.fromSystemProperties(k -> k.startsWith("njams.test.allowed"));
            assertEquals("1", ws.getProperty(allowed));
            assertNull(ws.getProperty(blocked));
            assertTrue(ws.containsKey(allowed));
            assertFalse(ws.containsKey(blocked));
        } finally {
            System.clearProperty(allowed);
            System.clearProperty(blocked);
        }
    }

    @Test
    public void fromSystemProperties_putWritesThroughToSystemProperties() {
        String key = "njams.test.writeThrough." + System.nanoTime();
        try {
            WritableSettings ws = WritableSettings.fromSystemProperties(null);
            ws.put(key, "v");
            assertEquals("v", System.getProperty(key));
        } finally {
            System.clearProperty(key);
        }
    }

    // ---------- FilteringWritableSettings ----------

    @Test
    public void filteringWritable_putWritesToBacking() {
        Map<String, String> backing = new HashMap<>();
        FilteringWritableSettings ws = new FilteringWritableSettings(backing, key -> true);
        ws.put("FOO", "1");
        assertEquals("1", backing.get("FOO"));
    }

    @Test
    public void filteringWritable_filterAppliesToReadsNotWrites() {
        Map<String, String> backing = new HashMap<>();
        FilteringWritableSettings ws =
            new FilteringWritableSettings(backing, key -> !key.equals("FOO"));
        ws.put("FOO", "1");
        assertEquals("1", backing.get("FOO"));
        assertNull(ws.getProperty("FOO"));
        assertFalse(ws.containsKey("FOO"));
    }

    @Test
    public void filteringWritable_putAllWritesAllUnfiltered() {
        Map<String, String> backing = new HashMap<>();
        FilteringWritableSettings ws =
            new FilteringWritableSettings(backing, key -> key.startsWith("A"));
        Map<String, String> extra = new HashMap<>();
        extra.put("AAA", "1");
        extra.put("BBB", "2");
        ws.putAll(extra);
        assertEquals("1", backing.get("AAA"));
        assertEquals("2", backing.get("BBB"));
        assertEquals("1", ws.getProperty("AAA"));
        assertNull(ws.getProperty("BBB"));
    }

    @Test
    public void filteringWritable_nullFilterAcceptsAllReads() {
        Map<String, String> backing = new HashMap<>();
        FilteringWritableSettings ws = new FilteringWritableSettings(backing, null);
        ws.put("FOO", "1");
        assertEquals("1", ws.getProperty("FOO"));
    }

    // ---------- environmentKeyTransformer ----------

    @Test
    public void environmentKeyTransformer_helloWorldExample() {
        assertEquals("HELLO_WORLD", ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER.apply("Hello.world"));
    }

    @Test
    public void environmentKeyTransformer_specialCharsExample() {
        assertEquals("__HELLO_123___",
            ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER.apply("$_Hello.123-!§"));
    }

    @Test
    public void environmentKeyTransformer_preservesAlphanumericUppercased() {
        assertEquals("ABC123", ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER.apply("abc123"));
    }

    @Test
    public void environmentKeyTransformer_emptyStringReturnsEmpty() {
        assertEquals("", ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER.apply(""));
    }

    @Test
    public void environmentKeyTransformer_nullInputReturnsNull() {
        assertNull(ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER.apply(null));
    }

    // ---------- keyTransformer in ReadOnlyFilteringSettings ----------

    @Test
    public void keyTransformer_appliedOnGetProperty() {
        Map<String, String> backing = new HashMap<>();
        backing.put("HELLO_WORLD", "value");
        ReadOnlyFilteringSettings ro = new ReadOnlyFilteringSettings(
            backing, null, ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER);
        assertEquals("value", ro.getProperty("Hello.world"));
        assertEquals("value", ro.getProperty("HELLO_WORLD"));
    }

    @Test
    public void keyTransformer_appliedOnContainsKey() {
        Map<String, String> backing = new HashMap<>();
        backing.put("HELLO_WORLD", "value");
        ReadOnlyFilteringSettings ro = new ReadOnlyFilteringSettings(
            backing, null, ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER);
        assertTrue(ro.containsKey("Hello.world"));
        assertTrue(ro.containsKey("HELLO_WORLD"));
        assertFalse(ro.containsKey("unknown.key"));
    }

    @Test
    public void keyTransformer_iteratorYieldsStorageKeys() {
        Map<String, String> backing = new HashMap<>();
        backing.put("FOO_BAR", "1");
        backing.put("BAZ_QUX", "2");
        ReadOnlyFilteringSettings ro = new ReadOnlyFilteringSettings(
            backing, null, ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER);
        Set<String> keys = new HashSet<>();
        ro.forEach(e -> keys.add(e.getKey()));
        assertEquals(2, keys.size());
        assertTrue(keys.contains("FOO_BAR"));
        assertTrue(keys.contains("BAZ_QUX"));
    }

    @Test
    public void keyTransformer_filterAppliesToTransformedKey() {
        Map<String, String> backing = new HashMap<>();
        backing.put("HELLO_WORLD", "1");
        backing.put("FOO_BAR", "2");
        ReadOnlyFilteringSettings ro = new ReadOnlyFilteringSettings(
            backing, key -> key.startsWith("HELLO"),
            ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER);
        assertEquals("1", ro.getProperty("Hello.world"));
        assertNull(ro.getProperty("Foo.bar"));
    }

    @Test
    public void keyTransformer_nullActsAsIdentity() {
        Map<String, String> backing = new HashMap<>();
        backing.put("Hello.world", "1");
        ReadOnlyFilteringSettings ro = new ReadOnlyFilteringSettings(backing, null, null);
        assertEquals("1", ro.getProperty("Hello.world"));
        assertNull(ro.getProperty("HELLO_WORLD"));
    }

    @Test
    public void keyTransformer_cachesTransformedKeys() {
        Map<String, String> backing = new HashMap<>();
        backing.put("HELLO_WORLD", "value");
        AtomicInteger callCount = new AtomicInteger();
        Function<String, String> counting = key -> {
            callCount.incrementAndGet();
            return ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER.apply(key);
        };
        ReadOnlyFilteringSettings ro = new ReadOnlyFilteringSettings(backing, null, counting);
        ro.getProperty("Hello.world");
        ro.getProperty("Hello.world");
        ro.getProperty("Hello.world");
        ro.containsKey("Hello.world");
        assertEquals(1, callCount.get());
    }

    @Test
    public void keyTransformer_putAppliesTransformer() {
        Map<String, String> backing = new HashMap<>();
        FilteringWritableSettings ws = new FilteringWritableSettings(
            backing, null, ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER);
        ws.put("Hello.world", "value");
        assertEquals("value", backing.get("HELLO_WORLD"));
        assertNull(backing.get("Hello.world"));
    }

    @Test
    public void keyTransformer_putAllAppliesTransformer() {
        Map<String, String> backing = new HashMap<>();
        FilteringWritableSettings ws = new FilteringWritableSettings(
            backing, null, ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER);
        Map<String, String> extra = new HashMap<>();
        extra.put("foo", "1");
        extra.put("bar.baz", "2");
        ws.putAll(extra);
        assertEquals("1", backing.get("FOO"));
        assertEquals("2", backing.get("BAR_BAZ"));
    }

    @Test
    public void keyTransformer_writeAndReadRoundTrip() {
        Map<String, String> backing = new HashMap<>();
        FilteringWritableSettings ws = new FilteringWritableSettings(
            backing, null, ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER);
        ws.put("Hello.world", "value");
        assertEquals("value", ws.getProperty("Hello.world"));
        assertEquals("value", ws.getProperty("hello.WORLD"));
    }

    // ---------- AbstractReadOnlySettings ----------

    @Test
    public void abstractReadOnlySettings_canBeExtendedExternallyWithSecuredHandling() {
        AbstractReadOnlySettings sample = new AbstractReadOnlySettings() {
            @Override
            public String getProperty(String key) {
                return null;
            }

            @Override
            public boolean containsKey(String key) {
                return false;
            }

            @Override
            public java.util.Iterator<Entry<String, String>> iterator() {
                return java.util.Collections.emptyIterator();
            }
        };
        Set<String> defaults = sample.getSecuredProperties();
        assertTrue(defaults.contains("password"));
        assertTrue(defaults.contains("credentials"));
        assertTrue(defaults.contains("secret"));
        assertTrue(defaults.contains("keystore.key"));

        sample.addSecureProperties(Set.of("CustomToken"));
        assertTrue(sample.getSecuredProperties().contains("customtoken"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void abstractReadOnlySettings_returnedSetIsUnmodifiable() {
        AbstractReadOnlySettings sample = new AbstractReadOnlySettings() {
            @Override
            public String getProperty(String key) {
                return null;
            }

            @Override
            public boolean containsKey(String key) {
                return false;
            }

            @Override
            public java.util.Iterator<Entry<String, String>> iterator() {
                return java.util.Collections.emptyIterator();
            }
        };
        sample.getSecuredProperties().add("x");
    }

    @Test
    public void fromEnvironment_usesEnvironmentKeyTransformer() {
        // System.getenv() varies by OS / environment. We can only validate that the public
        // factory wires the transformer by checking that any known env var is reachable via a
        // transformed alias. Use the first available env var that contains an underscore for a
        // deterministic transformation target.
        Map<String, String> env = System.getenv();
        Map.Entry<String, String> sample = env.entrySet().stream()
            .filter(e -> e.getKey().matches("[A-Z0-9_]+"))
            .findFirst()
            .orElse(null);
        if (sample == null) {
            return; // No suitable env var in this environment; skip.
        }
        ReadOnlySettings ro = ReadOnlySettings.fromEnvironment(null);
        // Storage key is already in env style — should be retrievable as-is.
        assertEquals(sample.getValue(), ro.getProperty(sample.getKey()));
        // A lowercased dotted alias should round-trip through the transformer to the same key.
        String alias = sample.getKey().toLowerCase().replace('_', '.');
        assertEquals(sample.getValue(), ro.getProperty(alias));
    }

    // ---------- containsKey ----------

    @Test
    public void containsKey_trueForExistingKey() {
        settings.put("k", "v");
        assertTrue(settings.containsKey("k"));
    }

    @Test
    public void containsKey_falseForMissingKey() {
        assertFalse(settings.containsKey("missing"));
    }

    // ---------- getInt ----------

    @Test
    public void getInt_returnsParsedValue() {
        settings.put("k", "42");
        assertEquals(42, settings.getInt("k", -1));
    }

    @Test
    public void getInt_returnsDefaultForMissingKey() {
        assertEquals(7, settings.getInt("missing", 7));
    }

    @Test
    public void getInt_returnsDefaultForInvalidValue() {
        settings.put("k", "not-a-number");
        assertEquals(7, settings.getInt("k", 7));
    }

    @Test
    public void getInt_returnsDefaultForEmptyValue() {
        settings.put("k", "");
        assertEquals(7, settings.getInt("k", 7));
    }

    // ---------- getLong ----------

    @Test
    public void getLong_returnsParsedValue() {
        settings.put("k", "9999999999");
        assertEquals(9999999999L, settings.getLong("k", -1L));
    }

    @Test
    public void getLong_returnsDefaultForMissingKey() {
        assertEquals(7L, settings.getLong("missing", 7L));
    }

    @Test
    public void getLong_returnsDefaultForInvalidValue() {
        settings.put("k", "abc");
        assertEquals(-1L, settings.getLong("k", -1L));
    }

    // ---------- getBool ----------

    @Test
    public void getBool_acceptsTrueCaseInsensitive() {
        settings.put("k", "TrUe");
        assertTrue(settings.getBool("k", false));
    }

    @Test
    public void getBool_acceptsFalseCaseInsensitive() {
        settings.put("k", "FALSE");
        assertFalse(settings.getBool("k", true));
    }

    @Test
    public void getBool_returnsDefaultForInvalidValue() {
        settings.put("k", "yes");
        assertTrue(settings.getBool("k", true));
    }

    @Test
    public void getBool_returnsDefaultForMissingKey() {
        assertFalse(settings.getBool("missing", false));
    }

    // ---------- typed deprecation-aware getters ----------

    @Test
    public void getIntWithDeprecationWarning_returnsExpectedValue() {
        settings.put("new", "42");
        assertEquals(42, settings.getIntWithDeprecationWarning("new", -1, "old"));
    }

    @Test
    public void getIntWithDeprecationWarning_returnsDeprecatedValue() {
        settings.put("old", "42");
        assertEquals(42, settings.getIntWithDeprecationWarning("new", -1, "old"));
    }

    @Test
    public void getIntWithDeprecationWarning_returnsDefaultWhenNeitherPresent() {
        assertEquals(-1, settings.getIntWithDeprecationWarning("new", -1, "old"));
    }

    @Test
    public void getIntWithDeprecationWarning_returnsDefaultForInvalidValue() {
        settings.put("new", "not-a-number");
        assertEquals(-1, settings.getIntWithDeprecationWarning("new", -1, "old"));
    }

    @Test
    public void getLongWithDeprecationWarning_returnsExpectedValue() {
        settings.put("new", "9999999999");
        assertEquals(9999999999L, settings.getLongWithDeprecationWarning("new", -1L, "old"));
    }

    @Test
    public void getLongWithDeprecationWarning_returnsDeprecatedValue() {
        settings.put("old", "42");
        assertEquals(42L, settings.getLongWithDeprecationWarning("new", -1L, "old"));
    }

    @Test
    public void getLongWithDeprecationWarning_returnsDefaultForInvalidValue() {
        settings.put("new", "abc");
        assertEquals(-1L, settings.getLongWithDeprecationWarning("new", -1L, "old"));
    }

    @Test
    public void getBoolWithDeprecationWarning_returnsExpectedValue() {
        settings.put("new", "true");
        assertTrue(settings.getBoolWithDeprecationWarning("new", false, "old"));
    }

    @Test
    public void getBoolWithDeprecationWarning_returnsDeprecatedValue() {
        settings.put("old", "false");
        assertFalse(settings.getBoolWithDeprecationWarning("new", true, "old"));
    }

    @Test
    public void getBoolWithDeprecationWarning_returnsDefaultWhenNeitherPresent() {
        assertTrue(settings.getBoolWithDeprecationWarning("new", true, "old"));
    }

    @Test
    public void getBoolWithDeprecationWarning_returnsDefaultForInvalidValue() {
        settings.put("new", "yes");
        assertTrue(settings.getBoolWithDeprecationWarning("new", true, "old"));
    }

    @Test
    public void getBoolWithDeprecationWarning_prefersExpectedOverDeprecated() {
        settings.put("new", "false");
        settings.put("old", "true");
        assertFalse(settings.getBoolWithDeprecationWarning("new", true, "old"));
    }

    // ---------- filter ----------

    @Test
    public void filter_returnsEntriesWithPrefix() {
        settings.put("a.x", "1");
        settings.put("a.y", "2");
        settings.put("b.x", "3");
        Properties result = settings.filter("a.");
        assertEquals(2, result.size());
        assertEquals("1", result.getProperty("a.x"));
        assertEquals("2", result.getProperty("a.y"));
    }

    @Test
    public void filter_returnsEmptyWhenNoMatches() {
        settings.put("a.x", "1");
        Properties result = settings.filter("z.");
        assertTrue(result.isEmpty());
    }

    // ---------- getPropertyWithDeprecationWarning ----------

    @Test
    public void getPropertyWithDeprecationWarning_returnsExpectedValue() {
        settings.put("new", "v");
        assertEquals("v", settings.getPropertyWithDeprecationWarning("new", "old"));
    }

    @Test
    public void getPropertyWithDeprecationWarning_returnsDeprecatedValueWhenExpectedMissing() {
        settings.put("old", "v");
        assertEquals("v", settings.getPropertyWithDeprecationWarning("new", "old"));
    }

    @Test
    public void getPropertyWithDeprecationWarning_returnsNullWhenNeitherPresent() {
        assertNull(settings.getPropertyWithDeprecationWarning("new", "old"));
    }

    @Test
    public void getPropertyWithDeprecationWarning_returnsDefaultWhenNeitherPresent() {
        assertEquals("d", settings.getPropertyWithDeprecationWarning("new", "d", "old"));
    }

    @Test
    public void getPropertyWithDeprecationWarning_prefersExpectedOverDeprecated() {
        settings.put("new", "x");
        settings.put("old", "y");
        assertEquals("x", settings.getPropertyWithDeprecationWarning("new", "old"));
    }

    // ---------- secured properties ----------

    @Test
    public void getSecuredProperties_containsDefaultTokens() {
        Set<String> tokens = settings.getSecuredProperties();
        assertTrue(tokens.contains("password"));
        assertTrue(tokens.contains("credentials"));
        assertTrue(tokens.contains("secret"));
        assertTrue(tokens.contains("keystore.key"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getSecuredProperties_returnsUnmodifiableView() {
        settings.getSecuredProperties().add("anything");
    }

    @Test
    public void addSecureProperties_extendsTokensLowercased() {
        settings.addSecureProperties(Set.of("ApiKey", "TOKEN"));
        Set<String> tokens = settings.getSecuredProperties();
        assertTrue(tokens.contains("apikey"));
        assertTrue(tokens.contains("token"));
    }

    // ---------- printPropertiesWithoutPasswords ----------

    @Test
    public void printPropertiesWithoutPasswords_masksSecuredKeysAndPrintsOthers() {
        settings.put("user.password", "secret");
        settings.put("user.name", "alice");
        List<String> records = new ArrayList<>();
        settings.printPropertiesWithoutPasswords(newRecordingLogger(records));
        assertEquals(2, records.size());
        assertTrue(records.contains("***      {} = ****|user.password"));
        assertTrue(records.contains("***      {} = {}|user.name|alice"));
    }

    @Test
    public void printPropertiesWithoutPasswords_sortsKeysAlphabetically() {
        settings.put("b", "2");
        settings.put("a", "1");
        settings.put("c", "3");
        List<String> records = new ArrayList<>();
        settings.printPropertiesWithoutPasswords(newRecordingLogger(records));
        assertEquals(3, records.size());
        assertEquals("***      {} = {}|a|1", records.get(0));
        assertEquals("***      {} = {}|b|2", records.get(1));
        assertEquals("***      {} = {}|c|3", records.get(2));
    }

    /**
     * Returns a {@link Logger} proxy that records every {@code info(...)} invocation as a
     * pipe-delimited string ({@code format|arg1|arg2|...}) into the given list. All other Logger
     * methods are no-ops.
     */
    private static Logger newRecordingLogger(List<String> records) {
        return (Logger) Proxy.newProxyInstance(
            Logger.class.getClassLoader(),
            new Class<?>[]{Logger.class},
            (proxy, method, args) -> {
                if ("info".equals(method.getName())
                    && args != null && args.length >= 1
                    && args[0] instanceof String) {
                    StringBuilder sb = new StringBuilder().append((String) args[0]);
                    for (int i = 1; i < args.length; i++) {
                        sb.append("|").append(args[i]);
                    }
                    records.add(sb.toString());
                }
                Class<?> ret = method.getReturnType();
                if (ret == boolean.class) {
                    return Boolean.FALSE;
                }
                return null;
            });
    }

    // ---------- putAll ----------

    @Test
    public void putAll_addsAllEntries() {
        Map<String, String> extra = new HashMap<>();
        extra.put("a", "1");
        extra.put("b", "2");
        settings.putAll(extra);
        assertEquals("1", settings.getProperty("a"));
        assertEquals("2", settings.getProperty("b"));
    }

    @Test
    public void putAll_overwritesExistingValues() {
        settings.put("a", "old");
        Map<String, String> extra = new HashMap<>();
        extra.put("a", "new");
        settings.putAll(extra);
        assertEquals("new", settings.getProperty("a"));
    }

    // ---------- stream ----------

    @Test
    public void stream_yieldsAllEntries() {
        settings.put("a", "1");
        settings.put("b", "2");
        Map<String, String> seen = new HashMap<>();
        settings.stream().forEach(e -> seen.put(e.getKey(), e.getValue()));
        assertEquals(2, seen.size());
        assertEquals("1", seen.get("a"));
        assertEquals("2", seen.get("b"));
    }

    @Test
    public void stream_emptyForEmptySettings() {
        assertEquals(0L, settings.stream().count());
    }

    // ---------- keySet ----------

    @Test
    public void keySet_returnsAllKeys() {
        settings.put("a", "1");
        settings.put("b", "2");
        Set<String> keys = settings.keySet();
        assertEquals(2, keys.size());
        assertTrue(keys.contains("a"));
        assertTrue(keys.contains("b"));
    }

    @Test
    public void keySet_emptyForEmptySettings() {
        assertTrue(settings.keySet().isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void keySet_isUnmodifiable() {
        settings.put("a", "1");
        settings.keySet().add("z");
    }

    @Test
    public void keySet_filteringSettings_appliesFilter() {
        Map<String, String> backing = new HashMap<>();
        backing.put("FOO", "1");
        backing.put("BAR", "2");
        backing.put("BAZ", "3");
        ReadOnlyFilteringSettings ro = new ReadOnlyFilteringSettings(backing, key -> key.startsWith("B"));
        Set<String> keys = ro.keySet();
        assertEquals(2, keys.size());
        assertTrue(keys.contains("BAR"));
        assertTrue(keys.contains("BAZ"));
        assertFalse(keys.contains("FOO"));
    }

    @Test
    public void keySet_filteringSettings_emptyWhenFilterRejectsAll() {
        Map<String, String> backing = new HashMap<>();
        backing.put("FOO", "1");
        ReadOnlyFilteringSettings ro = new ReadOnlyFilteringSettings(backing, key -> false);
        assertTrue(ro.keySet().isEmpty());
    }

    // ---------- iterator ----------

    @Test
    public void iterator_yieldsAllEntries() {
        settings.put("a", "1");
        settings.put("b", "2");
        Map<String, String> seen = new HashMap<>();
        settings.forEach(e -> seen.put(e.getKey(), e.getValue()));
        assertEquals(2, seen.size());
        assertEquals("1", seen.get("a"));
        assertEquals("2", seen.get("b"));
    }

    @Test
    public void iterator_emptyForEmptySettings() {
        assertFalse(settings.iterator().hasNext());
    }
}
