package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.Receiver;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.settings.Settings;

/**
 * Specifies what {@code njams.sdk.communication.shared=true} actually yields on the receiver side: for a transport
 * that has a shareable receiver, every client gets the very same instance, and that instance tracks all of them.
 * Nothing else in the suite asserts this — the other shared-receiver tests infer it from behaviour that would also
 * hold if each client had its own instance — yet SDK-473's "one cycle per outage for a shared receiver" (D473.7)
 * depends on it entirely.
 */
public class SharedReceiverSelectionSpecTest extends AbstractLifecycleSpecTest {

    @After
    public void clearSharedReceiverState() {
        // Both statics must be reset: the factory's cache, and the test receiver's own instance registry that the
        // lifecycle tests read through lastCreated(). Mirrors SharedReceiverRestartSpecTest's teardown.
        SharedLifecycleTestReceiver.clearInstanceRegistry();
        CommunicationFactory.clearSharedReceiversForTesting();
    }

    private static Settings settings(boolean shared) {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, String.valueOf(shared));
        return s;
    }

    /** A distinct client, since the shared receiver keys its registered instances by client path. */
    private static Njams client(String name) {
        Njams njams = mock(Njams.class);
        when(njams.getClientPath()).thenReturn(Path.of("test", name));
        return njams;
    }

    @Test
    public void everyClientGetsTheSameShareableReceiverInstance() {
        CommunicationFactory factory = new CommunicationFactory(settings(true));

        Receiver first = factory.getReceiver(client("sharedSelectionA"));
        Receiver second = factory.getReceiver(client("sharedSelectionB"));

        assertTrue("sharing must select the shareable receiver implementation",
            first instanceof SharedLifecycleTestReceiver);
        assertSame("both clients must be handed the very same receiver instance", first, second);
    }

    @Test
    public void theSharedInstanceTracksEveryClientThatTookIt() {
        CommunicationFactory factory = new CommunicationFactory(settings(true));
        Njams a = client("sharedTrackingA");
        Njams b = client("sharedTrackingB");

        ShareableReceiver<?> receiver = (ShareableReceiver<?>) factory.getReceiver(a);
        factory.getReceiver(b);

        // removeNjams reports "this was the last user". That it is false for the first and true for the second is
        // what proves both clients were really registered with this one instance, rather than each having its own.
        assertFalse("the first of two registered clients leaving must not be the last user", receiver.removeNjams(a));
        assertTrue("the second client leaving must be the last user", receiver.removeNjams(b));
    }

    @Test
    public void withoutSharingEachClientGetsItsOwnDedicatedReceiver() {
        CommunicationFactory factory = new CommunicationFactory(settings(false));

        Receiver first = factory.getReceiver(client("dedicatedSelectionA"));
        Receiver second = factory.getReceiver(client("dedicatedSelectionB"));

        // SharedLifecycleTestReceiver extends LifecycleTestReceiver, so the meaningful check is the interface.
        assertFalse("without sharing the shareable implementation must not be selected",
            first instanceof ShareableReceiver);
        assertNotSame("each client must get its own instance", first, second);
    }

    @Test
    public void honoredSharingLogsNoDedicatedFallbackMessage() {
        Logger factoryLogger = Logger.getLogger(CommunicationFactory.class);
        CapturingAppender appender = new CapturingAppender();
        Level originalLevel = factoryLogger.getLevel();
        factoryLogger.addAppender(appender);
        factoryLogger.setLevel(Level.DEBUG);
        try {
            new CommunicationFactory(settings(true)).getReceiver(client("sharedNoFallbackLog"));

            long fallbackMessages = appender.events().stream()
                .filter(e -> String.valueOf(e.getRenderedMessage()).contains("dedicated receiver instance"))
                .count();
            assertEquals("the dedicated-receiver fallback must not be reported when sharing was honored",
                0, fallbackMessages);
        } finally {
            // log4j Loggers are JVM-wide: leaving DEBUG installed would leak into every later test.
            factoryLogger.removeAppender(appender);
            factoryLogger.setLevel(originalLevel);
        }
    }

    /** Captures log events so a test can assert on their absence. Mirrors {@code ReceiverLoggingSpecTest}'s. */
    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<LoggingEvent> events = new CopyOnWriteArrayList<>();

        List<LoggingEvent> events() {
            return events;
        }

        @Override
        protected void append(LoggingEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
            // nothing to release
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
