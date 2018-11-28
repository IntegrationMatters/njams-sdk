/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.settings;

import java.util.Properties;
import org.junit.Test;
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
    
}
