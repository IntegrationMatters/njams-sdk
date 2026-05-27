package com.im.njams.sdk.serializer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StringSerializerTest {

    @Test
    public void nullReturnsEmptyString() {
        StringSerializer<Object> ser = new StringSerializer<>();
        assertEquals("", ser.serialize(null));
        assertEquals("", ser.serialize(null, 5));
    }

    @Test
    public void serializeReturnsToString() {
        StringSerializer<Integer> ser = new StringSerializer<>();
        assertEquals("42", ser.serialize(42));
    }

    @Test
    public void serializeWithLimitTruncatesToLimit() {
        StringSerializer<String> ser = new StringSerializer<>();
        assertEquals("abcde", ser.serialize("abcdefghij", 5));
    }

    @Test
    public void serializeWithLimitLongerThanStringReturnsFullString() {
        StringSerializer<String> ser = new StringSerializer<>();
        assertEquals("abc", ser.serialize("abc", 50));
    }

    @Test
    public void serializeWithMaxValueOrNonPositiveLimitReturnsFullString() {
        StringSerializer<String> ser = new StringSerializer<>();
        assertEquals("abcdefghij", ser.serialize("abcdefghij", Integer.MAX_VALUE));
        assertEquals("abcdefghij", ser.serialize("abcdefghij", 0));
        assertEquals("abcdefghij", ser.serialize("abcdefghij", -1));
    }
}
