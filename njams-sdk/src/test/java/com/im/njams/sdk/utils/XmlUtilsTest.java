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
package com.im.njams.sdk.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import org.junit.Test;

public class XmlUtilsTest {

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

    @Test
    public void serializeCompactByDefaultHasNoNewlines() {
        assertFalse(XmlUtils.serialize(new Bean("a", "b")).contains("\n"));
    }

    @Test
    public void serializeProducesNoProlog() {
        assertFalse(XmlUtils.serialize(new Bean("a", "b")).startsWith("<?xml"));
    }

    @Test
    public void serializePrettyHasNewlines() {
        assertTrue(XmlUtils.serialize(new Bean("a", "b"), true).contains("\n"));
    }

    @Test
    public void parseFromStringRoundTrips() {
        String xml = XmlUtils.serialize(new Bean("hello", "world"));
        Bean parsed = XmlUtils.parse(xml, Bean.class);
        assertEquals("hello", parsed.name);
        assertEquals("world", parsed.data);
    }

    @Test
    public void parseFromInputStreamRoundTrips() {
        String xml = XmlUtils.serialize(new Bean("hello", "world"));
        Bean parsed =
            XmlUtils.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), Bean.class);
        assertEquals("hello", parsed.name);
        assertEquals("world", parsed.data);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void parseInvalidXmlThrows() {
        XmlUtils.parse("<not-valid", Bean.class);
    }
}
