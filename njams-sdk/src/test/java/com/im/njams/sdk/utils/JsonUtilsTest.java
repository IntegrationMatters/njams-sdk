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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

public class JsonUtilsTest {

    @Test
    public void serializeUsesFastMapperByDefault() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("b", "2");
        payload.put("a", "1");

        String json = JsonUtils.serialize(payload);

        assertFalse(json.contains("\n"));
    }

    @Test
    public void serializeUsesDefaultMapperWhenPrettyPrintEnabled() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("b", "2");
        payload.put("a", "1");

        String json = JsonUtils.serialize(payload, true);

        assertTrue(json.contains("\n"));
    }

    @Test
    public void serializeSkipsNullValuesByDefault() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("a", null);
        payload.put("b", "2");

        String json = JsonUtils.serialize(payload);

        assertFalse(json.contains("\"a\""));
        assertTrue(json.contains("\"b\""));
    }

    @Test
    public void serializeWithPrettyPrintAlsoSkipsNullValues() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("a", null);
        payload.put("b", "2");

        String json = JsonUtils.serialize(payload, true);

        assertFalse(json.contains("\"a\""));
        assertTrue(json.contains("\n"));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void serializeWithSkipNullValuesFalseIncludesNullValues() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("a", null);
        payload.put("b", "2");

        String json = JsonUtils.serialize(payload, false, false);

        assertTrue(json.contains("\"a\":null"));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void serializeWrapsSerializationFailureInRuntimeException() {
        JsonUtils.serialize(new FailingBean());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void parseStringParsesJsonIntoObject() {
        Map<String, String> result = JsonUtils.parse("{\"a\":\"1\",\"b\":\"2\"}", Map.class);

        assertEquals("1", result.get("a"));
        assertEquals("2", result.get("b"));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void parseStringWrapsParseFailureInRuntimeException() {
        JsonUtils.parse("not valid json", Map.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void parseInputStreamParsesJsonIntoObject() {
        InputStream stream = new ByteArrayInputStream("{\"a\":\"1\"}".getBytes(StandardCharsets.UTF_8));

        Map<String, String> result = JsonUtils.parse(stream, Map.class);

        assertEquals("1", result.get("a"));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void parseInputStreamWrapsParseFailureInRuntimeException() {
        InputStream stream = new ByteArrayInputStream("not valid json".getBytes(StandardCharsets.UTF_8));

        JsonUtils.parse(stream, Map.class);
    }

    private static final class FailingBean {
        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }
}

