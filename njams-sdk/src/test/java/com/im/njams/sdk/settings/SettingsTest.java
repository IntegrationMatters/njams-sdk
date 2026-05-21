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
package com.im.njams.sdk.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;

/**
 * @author krautenberg@integrationmatters.com
 */
public class SettingsTest {

    private static Settings settings;

    @BeforeClass
    public static void configure() {
        settings = new Settings();
    }

    @Before
    public void reset() {
        settings.reset();
    }

    /**
     * This test tests if the PrintPropertiesWithoutPasswords only prints the
     * passwords as "****" without changing them to "****".
     */
    @Test
    public void testPrintPropertiesWithoutChangingThem() {
        Properties properties = settings.getAllProperties();
        properties.put(NjamsSettings.PROPERTY_JMS_SECURITY_CREDENTIALS, "njams");
        properties.put(NjamsSettings.PROPERTY_JMS_PASSWORD, "njams");
        settings.printPropertiesWithoutPasswords();
        String credentials = properties.getProperty(NjamsSettings.PROPERTY_JMS_SECURITY_CREDENTIALS);
        Assert.assertEquals("njams", credentials);
        String jmsPassword = properties.getProperty(NjamsSettings.PROPERTY_JMS_PASSWORD);
        Assert.assertEquals("njams", jmsPassword);
    }

    /**
     * This test tests if the PrintPropertiesWithoutPasswords prints the
     * properties in correct order.
     */
    @Test
    public void testPrintPropertiesInCorrectOrder() {
        Properties properties = settings.getAllProperties();
        properties.put("a", "a");
        properties.put("c", "c");
        properties.put("d", "d");
        properties.put("b", "b");
        properties.put("ce", "ce");
        settings.printPropertiesWithoutPasswords();
    }

    @Test
    public void testFilter() {
        settings.put("a.a", "a");
        settings.put("a.c", "c");
        settings.put("a.d", "d");
        settings.put("b.b", "b");
        settings.put("c.ce", "ce");
        Properties result = settings.filter("a.");
        assertEquals(3, result.size());
        assertEquals("a", result.get("a.a"));
        assertEquals("c", result.get("a.c"));
        assertEquals("d", result.get("a.d"));
    }

    @Test
    public void testFilterAndCut() {
        settings.put("a.a", "a");
        settings.put("a.c", "c");
        settings.put("a.d", "d");
        settings.put("b.b", "b");
        settings.put("c.ce", "ce");
        Properties result = settings.filterAndCut("a.");
        assertEquals(3, result.size());
        assertEquals("a", result.get("a"));
        assertEquals("c", result.get("c"));
        assertEquals("d", result.get("d"));
    }

    @Test
    public void testIteratorYieldsAllEntries() {
        settings.put("a", "1");
        settings.put("b", "2");
        Map<String, String> seen = new HashMap<>();
        settings.forEach(e -> seen.put(e.getKey(), e.getValue()));
        assertEquals(2, seen.size());
        assertEquals("1", seen.get("a"));
        assertEquals("2", seen.get("b"));
    }

    @Test
    public void testIteratorIsEmptyForEmptySettings() {
        assertFalse(settings.iterator().hasNext());
    }

    @Test
    public void testGetSecuredPropertiesContainsDefaultTokens() {
        Set<String> tokens = settings.getSecuredProperties();
        assertTrue(tokens.contains("password"));
        assertTrue(tokens.contains("credentials"));
        assertTrue(tokens.contains("secret"));
        assertTrue(tokens.contains("keystore.key"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetSecuredPropertiesIsUnmodifiable() {
        settings.getSecuredProperties().add("x");
    }

    @Test
    public void testGetSecuredPropertiesReflectsAddSecureProperties() {
        Set<String> extra = new HashSet<>();
        extra.add("ApiKey");
        settings.addSecureProperties(extra);
        assertTrue(settings.getSecuredProperties().contains("apikey"));
    }

    @Test
    public void testPutAllAddsEntries() {
        Map<String, String> extra = new HashMap<>();
        extra.put("a", "1");
        extra.put("b", "2");
        settings.putAll(extra);
        assertEquals("1", settings.getProperty("a"));
        assertEquals("2", settings.getProperty("b"));
    }

    @Test
    public void testPutAllOverwritesExistingValues() {
        settings.put("a", "old");
        Map<String, String> extra = new HashMap<>();
        extra.put("a", "new");
        settings.putAll(extra);
        assertEquals("new", settings.getProperty("a"));
    }
}
