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
package com.im.njams.sdk.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.LocalDateTime;

import org.junit.Test;

public class JsonSerializerFactoryTest {
    public static class MyTestClass {
        public LocalDateTime dateTime = LocalDateTime.now();
    }

    @Test
    public void testLocalDateTimeSerializer() throws IOException {
        JsonSerializerFactory.addLocalDateTimeSerializer();
        MyTestClass test = new MyTestClass();
        LocalDateTime now = LocalDateTime.now();
        test.dateTime = now;

        String s = JsonSerializerFactory.createDefaultWriter().writeValueAsString(test);
        System.out.println(s);

        assertTrue(s.contains(now.toString()));

        MyTestClass parsed = JsonSerializerFactory.getDefaultMapper().readValue(s, MyTestClass.class);
        assertEquals(now, parsed.dateTime);
    }
}
