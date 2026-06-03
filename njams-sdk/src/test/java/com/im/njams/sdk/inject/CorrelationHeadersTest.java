package com.im.njams.sdk.inject;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Locks the exact wire values of the correlation header names. External systems rely on these exact
 * strings, so they must not change accidentally.
 */
public class CorrelationHeadersTest {

    @Test
    public void headerNamesHaveExpectedWireValues() {
        assertEquals("NJAMS_CorrelationLogID", CorrelationHeaders.NJAMS_CORRELATION_LOG_ID);
        assertEquals("NJAMS_ParentLogID", CorrelationHeaders.NJAMS_PARENT_LOG_ID);
        assertEquals("NJAMS_DeepTrace", CorrelationHeaders.NJAMS_DEEP_TRACE);
    }
}
