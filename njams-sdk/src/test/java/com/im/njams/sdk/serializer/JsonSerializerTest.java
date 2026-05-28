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

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * This class tests the JsonSerializer
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
public class JsonSerializerTest implements Serializable {

    final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

    /**
     * Test of serialize method, of class JsonSerializer.
     */
    @Test
    public void testDoesSerializeDeepCopyArrays() throws Exception {
        String[] words = new String[2];
        JsonSerializer<String[]> ser = new JsonSerializer<>();
        //Add one word
        words[0] = "a";
        String serialized1 = ser.serialize(words);
        System.out.println(serialized1);
        //Add a second word
        words[1] = "b";
        String serialized2 = ser.serialize(words);
        System.out.println(serialized2);
        assertNotEquals(serialized1, serialized2);
    }

    @Test
    public void serializeWithLimitReturnsAtMostRoughlyLimit() {
        JsonSerializer<int[]> ser = new JsonSerializer<>();
        int[] data = new int[10_000]; // serializes to a long JSON array
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        String full = ser.serialize(data);
        String limited = ser.serialize(data, 100);

        // size-aware result is much shorter than the full result
        assertTrue("full length should be much greater than 100, got " + full.length(), full.length() > 1000);
        assertTrue("limited length should not exceed ~limit + small Jackson buffering, got "
                + limited.length(), limited.length() <= 200);
        // and it is a prefix of (the start of) the full string
        assertTrue("limited result should be a prefix of the full result",
                full.startsWith(limited.substring(0, Math.min(limited.length(), 50))));
    }

    @Test
    public void serializeWithMaxValueLimitMatchesUnlimited() {
        JsonSerializer<String[]> ser = new JsonSerializer<>();
        String[] words = {"alpha", "beta", "gamma"};
        assertEquals(ser.serialize(words), ser.serialize(words, Integer.MAX_VALUE));
    }

    @Test
    public void serializeWithZeroOrNegativeLimitMatchesUnlimited() {
        JsonSerializer<String[]> ser = new JsonSerializer<>();
        String[] words = {"alpha", "beta", "gamma"};
        assertEquals(ser.serialize(words), ser.serialize(words, 0));
        assertEquals(ser.serialize(words), ser.serialize(words, -1));
    }

    @Test
    public void serializeWithNullObjectReturnsNull() {
        JsonSerializer<Object> ser = new JsonSerializer<>();
        assertNull(ser.serialize(null));
        assertNull(ser.serialize(null, 50));
    }

    @Test
    public void noArgConstructorProducesCompactOutput() {
        JsonSerializer<String[]> ser = new JsonSerializer<>();
        String result = ser.serialize(new String[]{"a", "b"});
        assertFalse("no-arg constructor should produce compact JSON without newlines",
                result.contains("\n"));
    }

    @Test
    public void prettyFalseConstructorProducesCompactOutput() {
        JsonSerializer<String[]> ser = new JsonSerializer<>(false);
        String result = ser.serialize(new String[]{"a", "b"});
        assertFalse("pretty=false should produce compact JSON without newlines",
                result.contains("\n"));
    }

    @Test
    public void prettyTrueConstructorProducesIndentedOutput() {
        JsonSerializer<Map<String, String>> ser = new JsonSerializer<>(true);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");
        String result = ser.serialize(data);
        assertTrue("pretty=true should produce indented JSON with newlines",
                result.contains("\n"));
    }

}
