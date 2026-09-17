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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.Test;

import com.im.njams.sdk.utils.PropertyUtil;

import static org.junit.Assert.*;

/**
 *
 * @author krautenberg@integrationmatters.com
 */
public class PropertyUtilTest {

    /**
     * Test of filter method, of class PropertyUtil.
     */
    @Test
    public void testFilter() {
        Properties properties = new Properties();
        properties.put("ABC.DE", "DE");
        properties.put("ABC.EF", "EF");
        properties.put("ABCD.EF", "DEF");
        properties.put("Foo", "Bar");

        Properties toTest = PropertyUtil.filter(properties, "ABC");
        assertNotNull(toTest);

        assertEquals("DE", toTest.get("ABC.DE"));
        assertEquals("EF", toTest.get("ABC.EF"));
        assertEquals("DEF", toTest.get("ABCD.EF"));
        assertNull(toTest.get("Foo"));
    }
    /**
     * Test of filterAndCut method, of class InitialContextUtil.
     */
    @Test
    public void testFilterAndCut() {
        Properties properties = new Properties();
        properties.put("ABC.DE", "DE");
        properties.put("ABC.EF", "EF");
        properties.put("ABCD.EF", "DEF");
        properties.put("foo", "bar");

        Properties toTest = PropertyUtil.filterAndCut(properties, "ABC");
        assertNotNull(toTest);

        assertEquals("DE", toTest.get(".DE"));
        assertEquals("EF", toTest.get(".EF"));
        assertEquals("DEF", toTest.get("D.EF"));
        assertNull(toTest.get("ABC.DE"));
        assertNull(toTest.get("foo"));
    }

    @Test
    public void toPropertiesFromMap_copiesAllEntries() {
        Map<String, String> map = new HashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");
        Properties result = PropertyUtil.toProperties(map);
        assertEquals(2, result.size());
        assertEquals("v1", result.getProperty("k1"));
        assertEquals("v2", result.getProperty("k2"));
    }

    @Test
    public void toPropertiesFromMap_emptyMapProducesEmptyProperties() {
        Properties result = PropertyUtil.toProperties(new HashMap<>());
        assertTrue(result.isEmpty());
    }

    @Test
    public void toPropertiesFromStream_copiesAllEntries() {
        Stream<Map.Entry<String, String>> stream = Stream.of(
            Map.entry("a", "1"),
            Map.entry("b", "2")
        );
        Properties result = PropertyUtil.toProperties(stream);
        assertEquals(2, result.size());
        assertEquals("1", result.getProperty("a"));
        assertEquals("2", result.getProperty("b"));
    }

    @Test
    public void toPropertiesFromStream_emptyStreamProducesEmptyProperties() {
        Properties result = PropertyUtil.toProperties(Stream.empty());
        assertTrue(result.isEmpty());
    }

}
