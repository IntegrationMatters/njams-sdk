package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

public class StartupFailBehaviorTest {

    private static ClientSettings withFailBehavior(String value) {
        Properties p = new Properties();
        if (value != null) {
            p.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, value);
        }
        return ClientSettings.from(p);
    }

    @Test
    public void defaultsToFailWhenAbsent() {
        assertEquals(StartupFailBehavior.FAIL, StartupFailBehavior.fromSettings(withFailBehavior(null)));
        assertFalse(StartupFailBehavior.fromSettings(withFailBehavior(null)).reconnectOnStartupFailure());
    }

    @Test
    public void parsesReconnectCaseInsensitively() {
        assertEquals(StartupFailBehavior.RECONNECT, StartupFailBehavior.fromSettings(withFailBehavior("reconnect")));
        assertEquals(StartupFailBehavior.RECONNECT, StartupFailBehavior.fromSettings(withFailBehavior("RECONNECT")));
        assertTrue(StartupFailBehavior.fromSettings(withFailBehavior("reconnect")).reconnectOnStartupFailure());
    }

    @Test
    public void unknownValueFallsBackToFail() {
        assertEquals(StartupFailBehavior.FAIL, StartupFailBehavior.fromSettings(withFailBehavior("bogus")));
    }
}
