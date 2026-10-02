package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.SenderProbe;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * A {@link Njams} instance that was stopped can be started again; the restart must not reuse the sender that
 * {@code stop()} closed.
 */
public class SameInstanceRestartSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
        CommunicationFactory.clearSharedReceiversForTesting();
    }

    private void assertRestartUsesFreshSender(boolean shared) {
        ClientSettings s = LifecycleTestTransport.settings();
        if (shared) {
            s.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
        }
        njams = new Njams(Path.of("test", "sameInstanceRestart"), "1.0", "test", s);
        assertTrue(njams.start());
        Object first = SenderProbe.of(njams);
        assertTrue(njams.stop());

        assertTrue("a stopped instance must be startable again", njams.start());
        assertNotSame("the restart must not reuse the sender closed by stop()", first, SenderProbe.of(njams));
        assertTrue(njams.stop());
    }

    @Test
    public void restartWithDedicatedCommunicationUsesFreshSender() {
        assertRestartUsesFreshSender(false);
    }

    @Test
    public void restartWithSharedCommunicationUsesFreshSender() {
        assertRestartUsesFreshSender(true);
    }
}
