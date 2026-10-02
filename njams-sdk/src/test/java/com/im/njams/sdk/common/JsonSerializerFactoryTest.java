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
package com.im.njams.sdk.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.LocalDateTime;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.converter.Converter;
import com.faizsiegeln.njams.messageformat.v4.converter.DefaultConverter;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.AttributeType;
import com.im.njams.sdk.utils.JsonUtils;

public class JsonSerializerFactoryTest {
    public static class MyTestClass {
        public LocalDateTime dateTime = LocalDateTime.now();
        public AttributeType attributeType = AttributeType.EVENT;
    }

    @Test
    public void testSerializer() throws IOException {
        MyTestClass test = new MyTestClass();
        LocalDateTime now = LocalDateTime.now();
        test.dateTime = now;
        test.attributeType = AttributeType.EVENT;
        String s = JsonSerializerFactory._internal().getDefaultMapper().writer().writeValueAsString(test);
        System.out.println(s);

        assertTrue(s.contains(now.toString()));
        assertTrue(s.contains(AttributeType.EVENT.toString()));
        MyTestClass parsed = JsonSerializerFactory._internal().getDefaultMapper().readValue(s, MyTestClass.class);
        assertEquals(now, parsed.dateTime);
        assertEquals(AttributeType.EVENT, parsed.attributeType);
    }

    @Test
    public void testSerializer2() throws Exception {
        MyTestClass test = new MyTestClass();
        LocalDateTime now = LocalDateTime.now();
        test.dateTime = now;
        test.attributeType = AttributeType.EVENT;
        Converter<LocalDateTime> converter = spy(DefaultConverter.get(LocalDateTime.class));
        JsonSerializerFactory.addSerializer(converter, true);
        String s = JsonSerializerFactory._internal().getDefaultMapper().writer().writeValueAsString(test);
        System.out.println(s);

        assertTrue(s.contains(now.toString()));
        assertTrue(s.contains(AttributeType.EVENT.toString()));
        MyTestClass parsed = JsonSerializerFactory._internal().getDefaultMapper().readValue(s, MyTestClass.class);
        assertEquals(now, parsed.dateTime);
        assertEquals(AttributeType.EVENT, parsed.attributeType);

        verify(converter, times(1)).serialize(any(LocalDateTime.class));
        verify(converter, times(1)).deserialize(any(String.class));
    }

    public static class NullableFieldTestClass {
        public String value = "present";
        public String missing = null;
    }

    @Test
    public void testFastMapperIsCompactAndSkipsNullValues() throws IOException {
        NullableFieldTestClass test = new NullableFieldTestClass();
        String s = JsonSerializerFactory._internal().getFastMapper().writeValueAsString(test);
        System.out.println(s);

        assertFalse("getFastMapper() must not pretty-print, per its own Javadoc", s.contains("\n"));
        assertFalse("getFastMapper() must skip null-valued properties", s.contains("missing"));
    }

    @Test
    public void testInternalIsSingleton() {
        assertSame(JsonSerializerFactory._internal(), JsonSerializerFactory._internal());
    }

    @Test
    public void testInternalMappersAreCached() {
        JsonSerializerFactory.Internal internal = JsonSerializerFactory._internal();
        assertSame(internal.getFastMapper(), internal.getFastMapper());
        assertSame(internal.getDefaultMapper(), internal.getDefaultMapper());
        assertSame(internal.getMapper(true, false), internal.getMapper(true, false));
        assertSame(internal.getFastMapper(), internal.getMapper(true, false));
        assertSame(internal.getDefaultMapper(), internal.getMapper(true, true));
        assertNotSame(internal.getFastMapper(), internal.getDefaultMapper());
    }

    @Test
    public void testInternalDefaultMapperIsPrettyAndGetMapperRespectsSkipNull() throws IOException {
        JsonSerializerFactory.Internal internal = JsonSerializerFactory._internal();
        NullableFieldTestClass test = new NullableFieldTestClass();

        assertTrue(internal.getDefaultMapper().writeValueAsString(test).contains("\n"));
        String keepNull = internal.getMapper(false, false).writeValueAsString(test);
        assertFalse(keepNull.contains("\n"));
        assertTrue(keepNull.contains("missing"));
        assertFalse(internal.getMapper(true, false).writeValueAsString(test).contains("missing"));
    }

    @Test
    public void testInternalCreateWriterMatchesJsonUtils() throws IOException {
        NullableFieldTestClass test = new NullableFieldTestClass();
        assertEquals(JsonUtils.serialize(test, true, true),
            JsonSerializerFactory._internal().createWriter(true, true).writeValueAsString(test));
        assertEquals(JsonUtils.serialize(test, false, false),
            JsonSerializerFactory._internal().createWriter(false, false).writeValueAsString(test));
    }

}
