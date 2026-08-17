package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.SenderExceptionListener;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Specifies §5.4's shared-communications behaviour: two {@code Njams} instances sharing one communication group
 * (via {@link NjamsSender#takeSharedSender(ClientSettings)}) share the group's single reconnector and its
 * exception-listener fan-out, and one instance's {@code close()} does not tear the group down for its sibling.
 */
public class SharedSenderOutageSpecTest extends AbstractLifecycleSpecTest {

    /** Counts notifications reaching this listener. */
    private static final class CountingListener implements SenderExceptionListener {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void onException(Exception exception, CommonMessage msg) {
            count.incrementAndGet();
        }
    }

    private final List<NjamsSender> taken = new ArrayList<>();

    /**
     * The shared sender is a JVM-wide static (see {@link NjamsSender#takeSharedSender(ClientSettings)}) that is
     * reference-counted, so it must be closed exactly as often as it was taken or it leaks into later tests. This
     * is on top of, not instead of, the base class's own teardown: the shared instance's {@code SenderPool} is
     * created directly by {@code NjamsSender.init()}, so it is never registered with
     * {@code SenderPoolTestAccess}'s pool registry, and its background reconnect thread is only ever stopped by
     * actually closing the shared instance down to zero usages here.
     */
    @After
    public void releaseSharedSenders() {
        taken.forEach(NjamsSender::close);
        taken.clear();
    }

    private NjamsSender take() {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
        // Two core sender threads, so a message dispatched through `first` and one dispatched through `second`
        // can genuinely run at the same time instead of serializing behind a single core thread — needed for the
        // race below to actually be a race.
        s.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "2");
        NjamsSender sender = NjamsSender.takeSharedSender(ClientSettings.from(s.getAllProperties()));
        taken.add(sender);
        return sender;
    }

    @Test
    public void oneOutageElectsOneReconnectorAndNotifiesEveryRegisteredListener() throws Exception {
        NjamsSender first = take();
        NjamsSender second = take();
        assertSame("both takers must share one group", first, second);

        CountingListener a = new CountingListener();
        CountingListener b = new CountingListener();
        first.addSenderExceptionListener(a);
        second.addSenderExceptionListener(b);
        assertTrue(first.startWithTimeout(5000));

        // Two genuinely concurrent failures, one dispatched through each instance: `first`'s send takes the one
        // already-connected sender from startup, `second`'s send finds none idle and connects a second sender of
        // its own. Arming the block-then-fail gate before either send means both then block on the very same
        // gate, so releasing it fails both at essentially the same instant — racing two reportFailure() calls
        // into the shared pool. That race is what actually exercises the `!reconnecting` dedup guard: spec 5.4's
        // claim is that it still elects exactly one reconnector and fires each listener exactly once, even though
        // the two failures are attributed to two different Njams instances sharing the one group.
        int connectsAfterStartup = LifecycleTestTransport.senderConnectCount();
        LifecycleTestTransport.armSendBlocksThenFails();
        first.send(new LogMessage(), "session-first");
        second.send(new LogMessage(), "session-second");

        assertTrue("the second instance's send must create and connect a sender of its own",
            LifecycleTestTransport.awaitConnectAttempts(connectsAfterStartup + 1, 5, TimeUnit.SECONDS));
        assertTrue("a send must reach the transport",
            LifecycleTestTransport.sendEnteredLatch().await(5, TimeUnit.SECONDS));

        // Only now does the group's reconnect get parked in BLOCK — the connect above must stay on SUCCEED, or
        // the second instance's own startup connect would hang instead of racing into the failure below.
        int connectsBeforeOutage = LifecycleTestTransport.senderConnectCount();
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        LifecycleTestTransport.releaseSend();

        assertTrue("exactly one reconnect must be attempted for the whole JVM group",
            LifecycleTestTransport.awaitConnectAttempts(connectsBeforeOutage + 1, 10, TimeUnit.SECONDS));
        assertEquals("one reconnector, not one per concurrently failing instance", connectsBeforeOutage + 1,
            LifecycleTestTransport.senderConnectCount());

        // Listener fan-out is one notification per registered listener, per outage (spec 5.4).
        assertEquals("the first instance's listener is notified exactly once", 1, a.count.get());
        assertEquals("the second instance's listener is notified exactly once", 1, b.count.get());
    }

    @Test
    public void oneInstancesCloseDoesNotShutTheGroupDownForItsSibling() {
        NjamsSender first = take();
        NjamsSender second = take();
        assertTrue(first.startWithTimeout(5000));

        first.close();                    // reference-counted: must NOT really close the group
        taken.remove(first);              // already closed; do not close it twice in teardown

        assertTrue("the sibling's group must still be connected and usable", second.startWithTimeout(0));
    }
}
