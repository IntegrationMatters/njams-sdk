package com.im.njams.sdk.serializer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SerializerResultTest {

    @Test
    public void exposesValueAndTruncatedFlag() {
        SerializerResult truncated = new SerializerResult("abc", true);
        assertEquals("abc", truncated.value());
        assertTrue(truncated.truncated());

        SerializerResult complete = new SerializerResult("abc", false);
        assertEquals("abc", complete.value());
        assertFalse(complete.truncated());
    }

    @Test
    public void allowsNullValue() {
        SerializerResult result = new SerializerResult(null, false);
        assertNull(result.value());
        assertFalse(result.truncated());
    }
}
