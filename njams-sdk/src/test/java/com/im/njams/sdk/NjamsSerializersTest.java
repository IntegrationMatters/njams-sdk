package com.im.njams.sdk;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import java.io.Serializable;

import org.junit.Test;

import com.im.njams.sdk.serializer.Serializer;

/**
 * Specifies {@link NjamsSerializers#find(Class)}.
 */
public class NjamsSerializersTest {

    private static class Base {
    }

    private static class Derived extends Base implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @SuppressWarnings("unchecked")
    private static <T> Serializer<T> serializer() {
        return mock(Serializer.class);
    }

    @Test
    public void findsExactRegistration() {
        NjamsSerializers serializers = new NjamsSerializers();
        Serializer<Derived> s = serializer();
        serializers.add(Derived.class, s);
        assertSame(s, serializers.find(Derived.class));
    }

    @Test
    public void findsSuperclassRegistration() {
        NjamsSerializers serializers = new NjamsSerializers();
        Serializer<Base> s = serializer();
        serializers.add(Base.class, s);
        assertSame(s, serializers.find(Derived.class));
        assertSame("cached lookup returns the same serializer", s, serializers.find(Derived.class));
    }

    @Test
    public void findsInterfaceRegistration() {
        NjamsSerializers serializers = new NjamsSerializers();
        Serializer<Serializable> s = serializer();
        serializers.add(Serializable.class, s);
        assertSame(s, serializers.find(Derived.class));
    }

    @Test
    public void returnsNullWithoutMatchingRegistration() {
        NjamsSerializers serializers = new NjamsSerializers();
        assertNull(serializers.find(Derived.class));
        assertNull("cached negative lookup", serializers.find(Derived.class));
    }

    /** SDK-500: like {@link NjamsSerializers#get(Class)}, a null class finds no serializer. */
    @Test
    public void returnsNullForNullClass() {
        assertNull(new NjamsSerializers().find(null));
    }
}
