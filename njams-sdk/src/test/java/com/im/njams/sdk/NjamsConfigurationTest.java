package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;

/**
 * Unit tests for {@link NjamsConfiguration} exercised through a started {@link Njams} instance
 * (memory configuration provider via {@link AbstractTest}).
 */
public class NjamsConfigurationTest extends AbstractTest {

    @Test
    public void getReturnsConfiguration() {
        assertNotNull(njams.configuration().get());
    }

    @Test
    public void logModeDefaultsToComplete() {
        assertEquals(LogMode.COMPLETE, njams.configuration().getLogMode());
    }

    @Test
    public void processNotExcludedByDefault() {
        assertFalse(njams.configuration().isExcluded(process.getPath()));
    }

    @Test
    public void isExcludedWithNullPathIsTreatedAsExcluded() {
        // a null path is tolerated (no exception) and reported as excluded
        assertTrue(njams.configuration().isExcluded(null));
    }
}
