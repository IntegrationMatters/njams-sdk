package com.im.njams.sdk.serializer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StringSerializerTest {

    @Test
    public void nullReturnsEmptyString() {
        StringSerializer<Object> ser = new StringSerializer<>();
        assertEquals("", ser.serialize(null));
        assertEquals("", ser.serialize(null, 5).value());
        assertFalse(ser.serialize(null, 5).truncated());
    }

    @Test
    public void serializeReturnsToString() {
        StringSerializer<Integer> ser = new StringSerializer<>();
        assertEquals("42", ser.serialize(42));
    }

    @Test
    public void serializeWithLimitTruncatesToLimit() {
        StringSerializer<String> ser = new StringSerializer<>();
        SerializerResult result = ser.serialize("abcdefghij", 5);
        assertEquals("abcde", result.value());
        assertTrue("a value longer than the limit must be reported as truncated", result.truncated());
    }

    @Test
    public void serializeWithLimitLongerThanStringReturnsFullString() {
        StringSerializer<String> ser = new StringSerializer<>();
        SerializerResult result = ser.serialize("abc", 50);
        assertEquals("abc", result.value());
        assertFalse("a value within the limit must not be reported as truncated", result.truncated());
    }

    @Test
    public void serializeWithMaxValueOrNonPositiveLimitReturnsFullString() {
        StringSerializer<String> ser = new StringSerializer<>();
        assertEquals("abcdefghij", ser.serialize("abcdefghij", Integer.MAX_VALUE).value());
        assertEquals("abcdefghij", ser.serialize("abcdefghij", 0).value());
        assertEquals("abcdefghij", ser.serialize("abcdefghij", -1).value());
        assertFalse(ser.serialize("abcdefghij", Integer.MAX_VALUE).truncated());
    }
}
