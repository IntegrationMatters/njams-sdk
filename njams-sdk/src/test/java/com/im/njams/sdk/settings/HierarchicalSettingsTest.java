/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 */
package com.im.njams.sdk.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Test;
import org.slf4j.Logger;

import com.im.njams.sdk.utils.StringUtils;

public class HierarchicalSettingsTest {

    private static WritableSettings backing(Map<String, String> map) {
        return WritableSettings.from(map);
    }

    // ---------- builder validation ----------

    @Test
    public void from_rejectsNullBase() {
        assertThrows(NullPointerException.class, () -> HierarchicalSettings.from(null));
    }

    @Test
    public void fromEmpty_buildsUsableSettingsWithEmptyBase() {
        WritableSettings settings = HierarchicalSettings.fromEmpty().build();

        assertFalse(settings.containsKey("anything"));
        assertNull(settings.getProperty("anything"));
    }

    @Test
    public void fromEmpty_writesGoToTransientBase() {
        WritableSettings settings = HierarchicalSettings.fromEmpty().build();

        settings.put("k", "v");

        assertEquals("v", settings.getProperty("k"));
        assertTrue(settings.containsKey("k"));
    }

    @Test
    public void fromEmpty_overlayLayerStillReadable() {
        WritableSettings settings = HierarchicalSettings.fromEmpty()
            .andThen(backing(map("overlay-key", "overlay-value")))
            .build();

        settings.put("base-key", "base-value");

        assertEquals("base-value", settings.getProperty("base-key"));
        assertEquals("overlay-value", settings.getProperty("overlay-key"));
    }

    @Test
    public void fromEmpty_isolatesBaseBetweenCalls() {
        WritableSettings first = HierarchicalSettings.fromEmpty().build();
        WritableSettings second = HierarchicalSettings.fromEmpty().build();

        first.put("k", "v");

        assertEquals("v", first.getProperty("k"));
        assertNull(second.getProperty("k"));
    }

    // ---------- read priority ----------

    @Test
    public void getProperty_firstLayerWins() {
        Map<String, String> baseMap = new HashMap<>();
        baseMap.put("k", "from-base");
        Map<String, String> overlayMap = new HashMap<>();
        overlayMap.put("k", "from-overlay");

        WritableSettings settings = HierarchicalSettings.from(backing(baseMap))
            .andThen(backing(overlayMap))
            .build();

        assertEquals("from-base", settings.getProperty("k"));
    }

    @Test
    public void getProperty_fallsThroughLayers() {
        Map<String, String> baseMap = new HashMap<>();
        baseMap.put("only-base", "1");
        Map<String, String> overlayMap = new HashMap<>();
        overlayMap.put("only-overlay", "2");

        WritableSettings settings = HierarchicalSettings.from(backing(baseMap))
            .andThen(backing(overlayMap))
            .build();

        assertEquals("1", settings.getProperty("only-base"));
        assertEquals("2", settings.getProperty("only-overlay"));
        assertNull(settings.getProperty("nowhere"));
    }

    @Test
    public void containsKey_anyLayerCounts() {
        WritableSettings settings = HierarchicalSettings.from(backing(map("a", "1")))
            .andThen(backing(map("b", "2")))
            .build();

        assertTrue(settings.containsKey("a"));
        assertTrue(settings.containsKey("b"));
        assertFalse(settings.containsKey("c"));
    }

    // ---------- writes go to base only ----------

    @Test
    public void put_writesOnlyToBaseLayer() {
        Map<String, String> baseMap = new HashMap<>();
        Map<String, String> overlayMap = new HashMap<>();
        WritableSettings overlay = backing(overlayMap);

        WritableSettings settings = HierarchicalSettings.from(backing(baseMap))
            .andThen(overlay)
            .build();

        settings.put("k", "v");

        assertEquals("v", baseMap.get("k"));
        assertFalse(overlayMap.containsKey("k"));
    }

    @Test
    public void putAll_writesOnlyToBaseLayer() {
        Map<String, String> baseMap = new HashMap<>();
        Map<String, String> overlayMap = new HashMap<>();

        WritableSettings settings = HierarchicalSettings.from(backing(baseMap))
            .andThen(backing(overlayMap))
            .build();

        Map<String, String> entries = new HashMap<>();
        entries.put("a", "1");
        entries.put("b", "2");
        settings.putAll(entries);

        assertEquals("1", baseMap.get("a"));
        assertEquals("2", baseMap.get("b"));
        assertTrue(overlayMap.isEmpty());
    }

    @Test
    public void addSecureProperties_writesOnlyToBaseLayer() {
        Map<String, String> baseMap = new HashMap<>();
        Map<String, String> overlayMap = new HashMap<>();
        WritableSettings base = backing(baseMap);
        WritableSettings overlay = backing(overlayMap);

        WritableSettings settings = HierarchicalSettings.from(base).andThen(overlay).build();

        Set<String> extra = new HashSet<>();
        extra.add("ApiKey");
        settings.addSecureProperties(extra);

        assertTrue(base.getSecuredProperties().contains("apikey"));
        assertFalse(overlay.getSecuredProperties().contains("apikey"));
    }

    @Test
    public void getSecuredProperties_returnsBaseLayerOnly() {
        WritableSettings base = backing(new HashMap<>());
        WritableSettings overlay = backing(new HashMap<>());
        overlay.addSecureProperties(Set.of("overlay-only"));

        WritableSettings settings = HierarchicalSettings.from(base).andThen(overlay).build();

        assertTrue(settings.getSecuredProperties().contains("password"));
        assertFalse(settings.getSecuredProperties().contains("overlay-only"));
    }

    // ---------- iteration ----------

    @Test
    public void iterator_unionFirstWinsValues() {
        WritableSettings settings = HierarchicalSettings.from(backing(map("a", "from-base", "b", "from-base")))
            .andThen(backing(map("b", "from-overlay", "c", "from-overlay")))
            .build();

        Map<String, String> seen = new HashMap<>();
        for (Entry<String, String> e : settings) {
            seen.put(e.getKey(), e.getValue());
        }
        assertEquals(3, seen.size());
        assertEquals("from-base", seen.get("a"));
        assertEquals("from-base", seen.get("b"));
        assertEquals("from-overlay", seen.get("c"));
    }

    @Test
    public void keySet_unionAcrossLayers() {
        WritableSettings settings = HierarchicalSettings.from(backing(map("a", "1")))
            .andThen(backing(map("b", "2")))
            .build();

        Set<String> keys = settings.keySet();
        assertEquals(2, keys.size());
        assertTrue(keys.contains("a"));
        assertTrue(keys.contains("b"));
    }

    // ---------- null layers + names ----------

    @Test
    public void andThenNull_isSkipped() {
        WritableSettings settings = HierarchicalSettings.from(backing(map("a", "1")))
            .andThen(null).withName("ignored")
            .andThen(backing(map("b", "2")))
            .build();

        assertEquals("1", settings.getProperty("a"));
        assertEquals("2", settings.getProperty("b"));
    }

    @Test
    public void defaultName_forCommonLayer_isClassNameAtIdentityHash() {
        WritableSettings base = backing(map("a", "1"));
        WritableSettings settings = HierarchicalSettings.from(base).build();
        List<String> records = new ArrayList<>();
        settings.printPropertiesWithoutPasswords(newRecordingLogger(records));
        assertEquals(2, records.size());
        String fullDefault = "<" + base.getClass().getSimpleName() + "@"
            + Integer.toHexString(System.identityHashCode(base)) + ">";
        String expectedDisplayed = StringUtils.abbreviate(fullDefault, 19);
        // recorded as "<format>|<paddedLayerName>|<key>|<value>"; entry log is at index 1 (chain at 0)
        assertTrue("expected abbreviated default name " + expectedDisplayed + " in: " + records.get(1),
            records.get(1).contains("|" + expectedDisplayed + "|"));
    }

    @Test
    public void withName_setsLayerName_visibleInPrint() {
        WritableSettings settings = HierarchicalSettings.from(backing(map("a", "1")))
            .withName("my-base")
            .build();
        List<String> records = new ArrayList<>();
        settings.printPropertiesWithoutPasswords(newRecordingLogger(records));
        assertEquals(2, records.size());
        assertEquals("***      Lookup chain: {}|[my-base]", records.get(0));
        // recorded as "<format>|<paddedLayerName>|<key>|<value>"
        assertEquals("***      [{}]  {} = {}|my-base|a|1", records.get(1));
    }

    // ---------- system-properties layer ----------

    @Test
    public void systemPropertiesLayer_readsSystemProperty() {
        String key = "njams.test.hsys." + System.nanoTime();
        System.setProperty(key, "v");
        try {
            WritableSettings settings = HierarchicalSettings.from(backing(new HashMap<>()))
                .andThenSystemProperties()
                .build();
            assertEquals("v", settings.getProperty(key));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    public void systemPropertiesLayer_prefixFilterApplies() {
        String allowed = "njams.test.allowed." + System.nanoTime();
        String blocked = "other.test.blocked." + System.nanoTime();
        System.setProperty(allowed, "a");
        System.setProperty(blocked, "b");
        try {
            WritableSettings settings = HierarchicalSettings.from(backing(new HashMap<>()))
                .andThenSystemProperties().withPrefixFilter("njams.test.")
                .build();
            assertEquals("a", settings.getProperty(allowed));
            assertNull(settings.getProperty(blocked));
        } finally {
            System.clearProperty(allowed);
            System.clearProperty(blocked);
        }
    }

    @Test
    public void systemPropertiesLayer_prefixAndRegexAreAnded() {
        String matchBoth = "njams.test.A." + System.nanoTime();
        String matchPrefixOnly = "njams.test.B." + System.nanoTime();
        String matchRegexOnly = "other.test.A." + System.nanoTime();
        System.setProperty(matchBoth, "1");
        System.setProperty(matchPrefixOnly, "2");
        System.setProperty(matchRegexOnly, "3");
        try {
            WritableSettings settings = HierarchicalSettings.from(backing(new HashMap<>()))
                .andThenSystemProperties()
                .withPrefixFilter("njams.test.")
                .withRegexFilter(".*\\.A\\..*")
                .build();
            assertEquals("1", settings.getProperty(matchBoth));
            assertNull(settings.getProperty(matchPrefixOnly));
            assertNull(settings.getProperty(matchRegexOnly));
        } finally {
            System.clearProperty(matchBoth);
            System.clearProperty(matchPrefixOnly);
            System.clearProperty(matchRegexOnly);
        }
    }

    // ---------- environment layer ----------

    @Test
    public void environmentLayer_readsAnEnvVar() {
        Map.Entry<String, String> sample = System.getenv().entrySet().stream()
            .filter(e -> e.getKey().matches("[A-Z0-9_]+"))
            .findFirst()
            .orElse(null);
        if (sample == null) {
            return; // no suitable env var in this JVM
        }
        WritableSettings settings = HierarchicalSettings.from(backing(new HashMap<>()))
            .andThenEnvironmentVariables()
            .build();
        assertEquals(sample.getValue(), settings.getProperty(sample.getKey()));
    }

    // ---------- printPropertiesWithoutPasswords ----------

    @Test
    public void print_prependsLayerNameAndSortsKeys() {
        WritableSettings settings = HierarchicalSettings.from(backing(map("b", "B"))).withName("base")
            .andThen(backing(map("a", "A", "c", "C"))).withName("overlay")
            .build();

        List<String> records = new ArrayList<>();
        settings.printPropertiesWithoutPasswords(newRecordingLogger(records));
        assertEquals(4, records.size());
        assertEquals("***      Lookup chain: {}|[base] -> [overlay]", records.get(0));
        // layer names padded to width 7 (max of "base", "overlay")
        assertEquals("***      [{}]  {} = {}|overlay|a|A", records.get(1));
        assertEquals("***      [{}]  {} = {}|base   |b|B", records.get(2));
        assertEquals("***      [{}]  {} = {}|overlay|c|C", records.get(3));
    }

    @Test
    public void print_masksSecuredKeysUsingSourceLayerTokens() {
        // base layer: default tokens include "password"
        WritableSettings base = backing(map("user.password", "secret-base"));
        // overlay: no extra tokens, but key still hits the default "password" token
        WritableSettings overlay = backing(map("api.token", "t"));
        overlay.addSecureProperties(Set.of("token"));

        WritableSettings settings = HierarchicalSettings.from(base).withName("base")
            .andThen(overlay).withName("overlay")
            .build();

        List<String> records = new ArrayList<>();
        settings.printPropertiesWithoutPasswords(newRecordingLogger(records));
        assertEquals(3, records.size());
        assertEquals("***      Lookup chain: {}|[base] -> [overlay]", records.get(0));
        // sorted: api.token first, user.password second; names padded to width 7
        assertEquals("***      [{}]  {} = ****|overlay|api.token", records.get(1));
        assertEquals("***      [{}]  {} = ****|base   |user.password", records.get(2));
    }

    @Test
    public void print_abbreviatesLayerNamesLongerThan20Chars() {
        // 25-char name exceeds the 20-char cap and gets abbreviated to "first19chars…" (20 chars total)
        String longName = "very-long-layer-name-1234"; // 25 chars
        WritableSettings settings = HierarchicalSettings.from(backing(map("k", "v")))
            .withName(longName)
            .build();

        List<String> records = new ArrayList<>();
        settings.printPropertiesWithoutPasswords(newRecordingLogger(records));
        assertEquals(2, records.size());
        // chain log uses full layer name; only the per-entry column is abbreviated
        assertEquals("***      Lookup chain: {}|[" + longName + "]", records.get(0));
        assertEquals("***      [{}]  {} = {}|" + longName.substring(0, 19) + StringUtils.ELLIPSIS + "|k|v",
            records.get(1));
    }

    @Test
    public void print_securedDecisionUsesSourceLayer_notHierarchicalBase() {
        // The key lives only in the overlay; overlay has a custom secured token; base does not.
        WritableSettings base = backing(new HashMap<>());
        WritableSettings overlay = backing(map("special.thing", "secret"));
        overlay.addSecureProperties(Set.of("special"));

        WritableSettings settings = HierarchicalSettings.from(base).withName("base")
            .andThen(overlay).withName("overlay")
            .build();

        List<String> records = new ArrayList<>();
        settings.printPropertiesWithoutPasswords(newRecordingLogger(records));
        assertEquals(2, records.size());
        assertEquals("***      Lookup chain: {}|[base] -> [overlay]", records.get(0));
        // only overlay is printed (base contributes no keys), padding width = 7
        assertEquals("***      [{}]  {} = ****|overlay|special.thing", records.get(1));
    }

    @Test
    public void print_logsLookupChainInPrecedenceOrder() {
        // chain log lists layers in the same order they are consulted (first added = highest precedence)
        WritableSettings settings = HierarchicalSettings.from(backing(new HashMap<>())).withName("first")
            .andThen(backing(new HashMap<>())).withName("second")
            .andThen(backing(new HashMap<>())).withName("third")
            .build();

        List<String> records = new ArrayList<>();
        settings.printPropertiesWithoutPasswords(newRecordingLogger(records));
        assertEquals(1, records.size()); // no entries, only the chain log
        assertEquals("***      Lookup chain: {}|[first] -> [second] -> [third]", records.get(0));
    }

    // ---------- helpers ----------

    private static Map<String, String> map(String... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("expected key/value pairs");
        }
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

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
                        Object arg = args[i];
                        if (arg instanceof Object[]) {
                            for (Object inner : (Object[]) arg) {
                                sb.append("|").append(inner);
                            }
                        } else {
                            sb.append("|").append(arg);
                        }
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
}
