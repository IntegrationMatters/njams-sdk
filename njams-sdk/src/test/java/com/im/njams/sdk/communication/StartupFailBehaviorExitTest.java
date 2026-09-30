package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

public class StartupFailBehaviorExitTest {

    private static ClientSettings withFailBehavior(String value) {
        Properties p = new Properties();
        p.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, value);
        return ClientSettings.from(p);
    }

    @Test
    public void parsesExitCaseInsensitively() {
        assertEquals(StartupFailBehavior.EXIT, StartupFailBehavior.fromSettings(withFailBehavior("exit")));
        assertEquals(StartupFailBehavior.EXIT, StartupFailBehavior.fromSettings(withFailBehavior(" EXIT ")));
    }

    @Test
    public void exitDoesNotReconnectLikeFail() {
        ClientSettings exit = withFailBehavior("exit");
        assertFalse(StartupFailBehavior.fromSettings(exit).reconnectOnStartupFailure());
        assertFalse(NjamsSender.reconnectOnStartupFailure(exit));
        assertTrue(NjamsSender.exitOnStartupFailure(exit));
    }

    @Test
    public void failAndReconnectAreNotExit() {
        assertFalse(NjamsSender.exitOnStartupFailure(withFailBehavior("fail")));
        assertFalse(NjamsSender.exitOnStartupFailure(withFailBehavior("reconnect")));
    }
}
