package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import org.junit.Test;

/**
 * Unit tests for {@link ErrorEvent}: defaults, fluent setters, exception-based construction and
 * the event-time helpers.
 */
public class ErrorEventTest {

    @Test
    public void defaultStatusIsError() {
        ErrorEvent event = new ErrorEvent();
        assertEquals(EventStatus.ERROR, event.getStatus());
        assertNull(event.getCode());
        assertNull(event.getMessage());
        assertNull(event.getPayload());
        assertNull(event.getStacktrace());
        assertNull(event.getEventTime());
    }

    @Test
    public void settersAreFluentAndStoreValues() {
        ErrorEvent event = new ErrorEvent();
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 12, 0);

        ErrorEvent returned = event
                .setStatus(EventStatus.WARNING)
                .setCode("E42")
                .setMessage("boom")
                .setPayload("payload")
                .setStacktrace("trace")
                .setEventTime(time);

        assertSame("setters must return the same instance for chaining", event, returned);
        assertEquals(EventStatus.WARNING, event.getStatus());
        assertEquals("E42", event.getCode());
        assertEquals("boom", event.getMessage());
        assertEquals("payload", event.getPayload());
        assertEquals("trace", event.getStacktrace());
        assertEquals(time, event.getEventTime());
    }

    @Test
    public void throwableConstructorFillsMessageAndStacktrace() {
        Exception cause = new IllegalStateException("the-cause");
        ErrorEvent event = new ErrorEvent(cause);

        assertEquals(cause.toString(), event.getMessage());
        assertNotNull(event.getStacktrace());
        assertTrue("stacktrace must contain the exception type",
                event.getStacktrace().contains("IllegalStateException"));
        // default status is unaffected by the throwable constructor
        assertEquals(EventStatus.ERROR, event.getStatus());
    }

    @Test
    public void setExceptionOverwritesMessageAndStacktrace() {
        ErrorEvent event = new ErrorEvent().setMessage("old").setStacktrace("old-trace");
        Exception cause = new RuntimeException("new-cause");

        ErrorEvent returned = event.setException(cause);

        assertSame(event, returned);
        assertEquals(cause.toString(), event.getMessage());
        assertTrue(event.getStacktrace().contains("RuntimeException"));
    }

    @Test
    public void nowSetsEventTime() {
        ErrorEvent event = new ErrorEvent();
        assertNull(event.getEventTime());

        ErrorEvent returned = event.now();

        assertSame(event, returned);
        assertNotNull(event.getEventTime());
    }
}
