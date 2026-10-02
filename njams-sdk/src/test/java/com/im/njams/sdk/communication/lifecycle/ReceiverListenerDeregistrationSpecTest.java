package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.SenderProbe;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.NjamsSender;

/**
 * Specifies that {@code Njams.stop()} takes its receiver out of every listener set of the sender group it
 * registered it in ({@code Njams.startReceiver(...)}): a group outlives the instances using it, so a stopped
 * receiver must not stay referenced by it. {@link LifecycleTestReceiver} is both a {@code SenderRecoveryListener}
 * and a {@code SenderExceptionListener}, so it is registered in both.
 */
public class ReceiverListenerDeregistrationSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void stopDeregistersTheReceiverFromTheRecoveryAndTheExceptionListeners() {
        njams = new Njams(Path.of("test", "receiverListenerDeregistration"), "1.0", "test",
            LifecycleTestTransport.settings());
        assertTrue(njams.start());
        NjamsSender sender = SenderProbe.of(njams);
        assertEquals("start() registers the receiver as recovery listener", 1, sender.recoveryListenerCount());
        assertEquals("start() registers the receiver as exception listener", 1, sender.exceptionListenerCount());

        assertTrue(njams.stop());

        assertEquals("stop() must deregister the recovery listener", 0, sender.recoveryListenerCount());
        assertEquals("stop() must deregister the exception listener", 0, sender.exceptionListenerCount());
    }
}
