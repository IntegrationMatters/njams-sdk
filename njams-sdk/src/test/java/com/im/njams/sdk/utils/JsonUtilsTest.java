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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

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
}

