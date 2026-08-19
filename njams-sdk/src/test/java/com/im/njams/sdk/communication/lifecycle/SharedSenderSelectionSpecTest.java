package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.settings.Settings;

/**
 * Specifies what {@code njams.sdk.communication.shared=true} yields on the sender side, through {@link
 * Njams#getSender()} itself: every client sharing one JVM-wide {@link NjamsSender} group vs. each client owning its
 * own dedicated instance. {@code SharedSenderOutageSpecTest}/{@code SharedSenderRecoverySignalSpecTest} already
 * cover group-sharing *behaviour* once shared, but they take the shared sender directly via {@link
 * NjamsSender#takeSharedSender}, never through {@code Njams.getSender()}'s own {@code shared ? ... : ...} dispatch
 * (mirrors {@code SharedReceiverSelectionSpecTest} for the receiver side).
 */
public class SharedSenderSelectionSpecTest extends AbstractLifecycleSpecTest {

    private Njams njamsA;
    private Njams njamsB;

    @After
    public void stopNjamsInstances() {
        if (njamsA != null && njamsA.isStarted()) {
            njamsA.stop();
        }
        if (njamsB != null && njamsB.isStarted()) {
            njamsB.stop();
        }
    }

    private static Settings settings(boolean shared) {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, String.valueOf(shared));
        return s;
    }

    @Test
    public void sharedCommunicationsTrueGivesEveryClientTheSameSenderInstance() {
        njamsA = new Njams(Path.of("test", "sharedSenderSelectionA"), "1.0", "test", settings(true));
        njamsB = new Njams(Path.of("test", "sharedSenderSelectionB"), "1.0", "test", settings(true));
        assertTrue(njamsA.start());
        assertTrue(njamsB.start());

        assertSame("both clients must be handed the very same sender instance",
            njamsA.getSender(), njamsB.getSender());
    }

    @Test
    public void sharedCommunicationsFalseGivesEveryClientItsOwnDedicatedSenderInstance() {
        njamsA = new Njams(Path.of("test", "dedicatedSenderSelectionA"), "1.0", "test", settings(false));
        njamsB = new Njams(Path.of("test", "dedicatedSenderSelectionB"), "1.0", "test", settings(false));
        assertTrue(njamsA.start());
        assertTrue(njamsB.start());

        assertNotSame("without sharing each client must get its own sender instance",
            njamsA.getSender(), njamsB.getSender());
    }
}
