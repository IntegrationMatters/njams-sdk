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

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Test;

public class ReflectionWrapperTest {

    @Test
    public void testConstructor() throws ReflectiveOperationException {
        ReflectionWrapper toTest = new ReflectionWrapper("com.im.njams.sdk.utils.TestClass", null);
        assertTrue(toTest.getTarget() instanceof TestClass);
        assertEquals(0, ((TestClass) toTest.getTarget()).getPrimitiveInt());
        assertEquals(0, (int) ((TestClass) toTest.getTarget()).getWrappedInt());
        assertTrue(((TestClass) toTest.getTarget()).isPrimitiveBoolean());
        assertTrue(((TestClass) toTest.getTarget()).getWrappedBoolean());
        assertNull(((TestClass) toTest.getTarget()).getString());

        toTest = new ReflectionWrapper("com.im.njams.sdk.utils.TestClass", ReflectionWrapper.argsBuilder()
                .addPrimitive(5).addObject(8).addPrimitive(false).addObject(false).addObject("Hello"));
        assertTrue(toTest.getTarget() instanceof TestClass);
        assertEquals(5, ((TestClass) toTest.getTarget()).getPrimitiveInt());
        assertEquals(8, (int) ((TestClass) toTest.getTarget()).getWrappedInt());
        assertFalse(((TestClass) toTest.getTarget()).isPrimitiveBoolean());
        assertFalse(((TestClass) toTest.getTarget()).getWrappedBoolean());
        assertEquals("Hello", ((TestClass) toTest.getTarget()).getString());

        toTest = new ReflectionWrapper("com.im.njams.sdk.utils.TestClass", ReflectionWrapper.argsBuilder()
                .addPrimitive(5).addObject(8).addPrimitive(false).addObject(false).addNull(String.class));
        assertTrue(toTest.getTarget() instanceof TestClass);
        assertEquals(5, ((TestClass) toTest.getTarget()).getPrimitiveInt());
        assertEquals(8, (int) ((TestClass) toTest.getTarget()).getWrappedInt());
        assertFalse(((TestClass) toTest.getTarget()).isPrimitiveBoolean());
        assertFalse(((TestClass) toTest.getTarget()).getWrappedBoolean());
        assertNull(((TestClass) toTest.getTarget()).getString());

        assertThrows(NoSuchMethodException.class,
                () -> new ReflectionWrapper("com.im.njams.sdk.utils.TestClass", ReflectionWrapper.argsBuilder()
                        .addPrimitive(5).addObject(8).addPrimitive(false).addObject(false).addNull(Exception.class)));

    }

    @Test
    public void testSetter() throws ReflectiveOperationException {
        TestClass target = spy(new TestClass());
        ReflectionWrapper toTest = new ReflectionWrapper(target);
        assertSame(target, toTest.getTarget());
        toTest.setPrimitive("setPrimitiveInt", 9);
        assertEquals(9, target.getPrimitiveInt());
        verify(target, times(1)).setPrimitiveInt(9);
        toTest.setObject("setWrappedInt", 99);
        assertEquals(99, (int) target.getWrappedInt());
        verify(target, times(1)).setWrappedInt(99);
        toTest.setNull("setWrappedInt", Integer.class);
        assertNull(target.getWrappedInt());
        verify(target, times(1)).setWrappedInt(null);
        assertThrows(NoSuchMethodException.class, () -> toTest.setPrimitive("setWrappedInt", 1));
    }

    @Test
    public void testInvoke() throws ReflectiveOperationException {
        TestClass target = spy(new TestClass());
        ReflectionWrapper toTest = new ReflectionWrapper(target);
        assertSame(target, toTest.getTarget());
        Object s = toTest.invoke("setAndGet", ReflectionWrapper.argsBuilder().addObject("qwert").addPrimitive(44));
        assertEquals("qwert44", s);
        verify(target, times(1)).setAndGet("qwert", 44);
    }

    private static class OtherClass extends TestClass {
        private String myString;

        private OtherClass(String s) {
            myString = s;
        }

        @Override
        public String getString() {
            return myString;
        }

    }

    @Test
    public void testInheritedType() throws ReflectiveOperationException {
        TestClass target = spy(new TestClass());
        ReflectionWrapper toTest = new ReflectionWrapper(target);
        assertSame(target, toTest.getTarget());
        final OtherClass other = new OtherClass("aaaaa");
        assertThrows(NoSuchMethodException.class,
                () -> toTest.invoke("setOther", ReflectionWrapper.argsBuilder().addObject(other)));
        toTest.invoke("setOther", ReflectionWrapper.argsBuilder().addObject(other, TestClass.class));
        assertNotNull(target.getOther());
        assertSame(other, target.getOther());
        assertEquals("aaaaa", target.getOther().getString());
        verify(target, times(1)).setOther(any(OtherClass.class));

        final OtherClass other2 = new OtherClass("bbbbb");
        assertThrows(NoSuchMethodException.class, () -> toTest.setObject("setOther", other2));
        toTest.setObject("setOther", other2, TestClass.class);
        assertNotNull(target.getOther());
        assertSame(other2, target.getOther());
        assertEquals("bbbbb", target.getOther().getString());
        verify(target, times(2)).setOther(any(OtherClass.class));

    }

    @Test
    public void testExplicitType() throws ReflectiveOperationException {
        TestClass target = spy(new TestClass());
        ReflectionWrapper toTest = new ReflectionWrapper(target);
        assertSame(target, toTest.getTarget());
        toTest.setObject("setPrimitiveInt", Integer.valueOf(5), int.class);
        assertEquals(5, target.getPrimitiveInt());
        verify(target, times(1)).setPrimitiveInt(5);
        toTest.setObject("setWrappedInt", null, Integer.class);
        assertNull(target.getWrappedInt());
        verify(target, times(1)).setWrappedInt(null);

    }

    @Test
    public void testInheritedMethods() throws ReflectiveOperationException {
        OtherClass target = spy(new OtherClass("Hello"));
        ReflectionWrapper toTest = new ReflectionWrapper(target);
        assertSame(target, toTest.getTarget());
        Object s = toTest.invoke("setAndGet", ReflectionWrapper.argsBuilder().addObject("qwert").addPrimitive(44));
        assertEquals("qwert44", s);
        verify(target, times(1)).setAndGet("qwert", 44);
    }
}
