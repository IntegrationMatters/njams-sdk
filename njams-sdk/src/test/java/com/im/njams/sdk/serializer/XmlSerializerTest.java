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
package com.im.njams.sdk.serializer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class XmlSerializerTest {

    public static class Bean {
        public String name;
        public String data;

        public Bean() {
        }

        public Bean(String name, String data) {
            this.name = name;
            this.data = data;
        }
    }

    private static Bean large() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append('x');
        }
        return new Bean("big", sb.toString());
    }

    @Test
    public void serializeProducesXmlWithoutProlog() {
        XmlSerializer<Bean> ser = new XmlSerializer<>();
        String xml = ser.serialize(new Bean("a", "b"));
        assertFalse("must not emit XML prolog", xml.startsWith("<?xml"));
        assertTrue("expected name element, got " + xml, xml.contains("<name>a</name>"));
        assertTrue("expected data element, got " + xml, xml.contains("<data>b</data>"));
    }

    @Test
    public void noArgConstructorProducesCompactOutput() {
        XmlSerializer<Bean> ser = new XmlSerializer<>();
        assertFalse("compact XML must not contain newlines", ser.serialize(new Bean("a", "b")).contains("\n"));
    }

    @Test
    public void prettyTrueConstructorProducesIndentedOutput() {
        XmlSerializer<Bean> ser = new XmlSerializer<>(true);
        assertTrue("pretty XML must contain newlines", ser.serialize(new Bean("a", "b")).contains("\n"));
    }

    @Test
    public void serializeWithLimitReturnsAtMostRoughlyLimit() {
        XmlSerializer<Bean> ser = new XmlSerializer<>();
        Bean data = large();
        String full = ser.serialize(data);
        SerializerResult limited = ser.serialize(data, 100);

        assertTrue("full length should be much greater than 100, got " + full.length(), full.length() > 1000);
        assertTrue("limited length should not greatly exceed the limit, got " + limited.value().length(),
            limited.value().length() <= 200);
        assertTrue("limited result must be reported as truncated", limited.truncated());
    }

    @Test
    public void serializeWithinLimitReportsNotTruncated() {
        XmlSerializer<Bean> ser = new XmlSerializer<>();
        Bean data = new Bean("a", "b");
        SerializerResult result = ser.serialize(data, 10_000);
        assertEquals(ser.serialize(data), result.value());
        assertFalse("result that fits within the limit must not be truncated", result.truncated());
    }

    @Test
    public void serializeWithMaxValueLimitMatchesUnlimited() {
        XmlSerializer<Bean> ser = new XmlSerializer<>();
        Bean data = new Bean("a", "b");
        SerializerResult result = ser.serialize(data, Integer.MAX_VALUE);
        assertEquals(ser.serialize(data), result.value());
        assertFalse(result.truncated());
    }

    @Test
    public void serializeWithZeroOrNegativeLimitMatchesUnlimited() {
        XmlSerializer<Bean> ser = new XmlSerializer<>();
        Bean data = new Bean("a", "b");
        assertEquals(ser.serialize(data), ser.serialize(data, 0).value());
        assertEquals(ser.serialize(data), ser.serialize(data, -1).value());
    }

    @Test
    public void serializeWithNullObjectReturnsNull() {
        XmlSerializer<Object> ser = new XmlSerializer<>();
        assertNull(ser.serialize(null));
        assertNull(ser.serialize(null, 50));
    }
}
