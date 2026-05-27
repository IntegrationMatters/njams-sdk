package com.im.njams.sdk.serializer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SerializerDefaultTest {

    /** Verifies the interface default routes serialize(T) through serialize(T, Integer.MAX_VALUE). */
    @Test
    public void defaultSerializeDelegatesToSizeAwareWithMaxValue() {
        final int[] capturedLimit = {-1};
        Serializer<String> s = (value, sizeLimit) -> {
            capturedLimit[0] = sizeLimit;
            return value;
        };
        String result = s.serialize("hello");
        assertEquals("hello", result);
        assertEquals(Integer.MAX_VALUE, capturedLimit[0]);
    }
}
