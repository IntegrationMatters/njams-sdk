package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.StartupResult;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Checks the value returned by {@link Njams#startup()}. SDK-local behavior is identical for {@code fail} and
 * {@code exit}, so the behavior tests do not differentiate them; only the returned value does.
 */
public class StartupResultTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    private Njams newNjams(String failBehavior) {
        ClientSettings s = LifecycleTestTransport.settings();
        if (failBehavior != null) {
            s.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, failBehavior);
        }
        return new Njams(Path.of("test", "startupresult"), "1.0", "test", s);
    }

    @Test
    public void continueWhenSenderConnects() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        njams = newNjams("exit");
        assertEquals(StartupResult.CONTINUE, njams.startup());
    }

    @Test
    public void failWhenSenderCannotConnectUnderFailBehaviorFail() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("fail");
        assertEquals(StartupResult.FAIL, njams.startup());
        assertFalse(njams.isStarted());
    }

    @Test
    public void failIsTheDefaultWhenSenderCannotConnect() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams(null);
        assertEquals(StartupResult.FAIL, njams.startup());
    }

    @Test
    public void exitWhenSenderCannotConnectUnderFailBehaviorExit() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("exit");
        assertEquals(StartupResult.EXIT, njams.startup());
        assertFalse(njams.isStarted());
    }

    @Test
    public void continueUnderReconnectDespiteInitialFailure() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("reconnect");
        assertEquals(StartupResult.CONTINUE, njams.startup());
    }

    @Test
    public void startStillReturnsFalseUnderFailBehaviorExit() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("exit");
        assertFalse(njams.start());
        assertFalse(njams.isStarted());
    }

    @Test
    public void failWhenSenderCannotBeObtainedUnderFailBehaviorFail() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        ClientSettings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "fail");
        // Makes NjamsSender's constructor throw, so Njams.startup() cannot obtain a sender at all.
        s.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "0");
        njams = new Njams(Path.of("test", "startupresult"), "1.0", "test", s);
        assertEquals(StartupResult.FAIL, njams.startup());
        assertFalse(njams.isStarted());
    }

    @Test
    public void failWhenProjectMessageCannotBeBuiltUnderFailBehaviorFail() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        njams = newNjams("fail");
        njams.model().setLayouter(pm -> {
            throw new NoClassDefFoundError("simulated project-message failure");
        });
        njams.model().create("P1");
        assertEquals(StartupResult.FAIL, njams.startup());
        assertFalse(njams.isStarted());
    }

    @Test
    public void exitWhenSenderCannotBeObtainedUnderFailBehaviorExit() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        ClientSettings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "exit");
        // Makes NjamsSender's constructor throw, so Njams.startup() cannot obtain a sender at all.
        s.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "0");
        njams = new Njams(Path.of("test", "startupresult"), "1.0", "test", s);
        assertEquals(StartupResult.EXIT, njams.startup());
        assertFalse(njams.isStarted());
    }

    @Test
    public void exitWhenProjectMessageCannotBeBuiltUnderFailBehaviorExit() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        njams = newNjams("exit");
        njams.model().setLayouter(pm -> {
            throw new NoClassDefFoundError("simulated project-message failure");
        });
        njams.model().create("P1");
        assertEquals(StartupResult.EXIT, njams.startup());
        assertFalse(njams.isStarted());
    }

    @Test
    public void failWhenSenderCannotBeObtainedUnderReconnect() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        ClientSettings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "reconnect");
        s.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "0");
        njams = new Njams(Path.of("test", "startupresult"), "1.0", "test", s);
        assertEquals(StartupResult.FAIL, njams.startup());
        assertFalse(njams.isStarted());
    }
}
