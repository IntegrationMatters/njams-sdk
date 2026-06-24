package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Unit tests for {@link JobAttributes}, the wire-data key/value store of a job. The owning
 * {@link JobImpl} is mocked so the facet is exercised in isolation, without the recorded-attribute
 * a real started job would add.
 */
public class JobAttributesTest {

    private JobImpl job;
    private JobAttributes attributes;

    @Before
    public void setUp() {
        job = mock(JobImpl.class);
        // payload limiting is the job's responsibility; here it is a pass-through
        when(job.limitPayload(anyString())).thenAnswer(inv -> inv.getArgument(0));
        attributes = new JobAttributes(job);
    }

    @After
    public void tearDown() {
        DataMasking.removePatterns();
    }

    @Test
    public void addStoresValueAndReportsPresence() {
        attributes.add("key", "value");
        assertEquals("value", attributes.get("key"));
        assertTrue(attributes.has("key"));
        assertFalse(attributes.isEmpty());
    }

    @Test
    public void addNullValueIsIgnored() {
        attributes.add("key", null);
        assertFalse(attributes.has("key"));
        assertNull(attributes.get("key"));
        assertTrue(attributes.isEmpty());
    }

    @Test
    public void addContributesToEstimatedSize() {
        attributes.add("key", "value"); // 3 + 5
        verify(job).addToEstimatedSize(8L);
    }

    @Test
    public void addMasksValue() {
        DataMasking.addPattern(".*");
        attributes.add("key", "secret");
        assertEquals("******", attributes.get("key"));
    }

    @Test
    public void getAllMergesAndIsDetached() {
        attributes.add("a", "1");
        attributes.add("b", "2");

        Map<String, String> all = attributes.getAll();
        assertEquals("1", all.get("a"));
        assertEquals("2", all.get("b"));

        // mutating the returned copy must not affect the facet
        all.put("c", "3");
        assertFalse(attributes.has("c"));
    }

    @Test
    public void flushIntoMovesPendingToFlushed() {
        attributes.add("k", "v");
        LogMessage logMessage = new LogMessage();

        attributes.flushInto(logMessage);

        // pending is now empty, but the value remains readable from the flushed store
        assertTrue(attributes.isEmpty());
        assertEquals("v", attributes.get("k"));
        assertEquals("v", logMessage.getAttributes().get("k"));
    }

    @Test
    public void pendingValueWinsOverFlushedValue() {
        attributes.add("k", "first");
        attributes.flushInto(new LogMessage());
        attributes.add("k", "second");
        assertEquals("second", attributes.get("k"));
    }

    @Test
    public void attributeNameIsLengthLimited() {
        StringBuilder longKey = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longKey.append('x');
        }
        attributes.add(longKey.toString(), "v");

        String storedKey = attributes.getAll().keySet().iterator().next();
        assertTrue("attribute name must be limited to 500 chars", storedKey.length() <= 500);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void addAfterEndThrows() {
        doThrow(new NjamsSdkRuntimeException("finished")).when(job).requireNotFinished(any());
        attributes.add("k", "v");
    }
}
