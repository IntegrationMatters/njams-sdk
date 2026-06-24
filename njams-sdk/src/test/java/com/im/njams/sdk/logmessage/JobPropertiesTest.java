package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link JobProperties}, the client-local key/value storage of a job that is never
 * transmitted to the nJAMS server.
 */
public class JobPropertiesTest {

    private JobProperties properties;

    @Before
    public void setUp() {
        properties = new JobProperties();
    }

    @Test
    public void setThenGetReturnsValue() {
        Object value = new Object();
        properties.set("key", value);
        assertEquals(value, properties.get("key"));
        assertTrue(properties.has("key"));
    }

    @Test
    public void getUnknownKeyReturnsNull() {
        assertNull(properties.get("missing"));
        assertFalse(properties.has("missing"));
    }

    @Test
    public void setOverwritesExistingValue() {
        properties.set("key", "first");
        properties.set("key", "second");
        assertEquals("second", properties.get("key"));
    }

    @Test
    public void removeReturnsPreviousValueAndDeletes() {
        properties.set("key", "value");
        assertEquals("value", properties.remove("key"));
        assertFalse(properties.has("key"));
        assertNull(properties.get("key"));
    }

    @Test
    public void removeUnknownKeyReturnsNull() {
        assertNull(properties.remove("missing"));
    }

    @Test
    public void nullValueIsStoredAndHasReturnsTrue() {
        properties.set("key", null);
        // a null value is a present mapping: has() must report it, get() returns null
        assertTrue(properties.has("key"));
        assertNull(properties.get("key"));
    }
}
