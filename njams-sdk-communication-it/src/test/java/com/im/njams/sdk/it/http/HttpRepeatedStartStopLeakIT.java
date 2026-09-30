package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.SenderExceptionListener;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.settings.Settings;

public class HttpRepeatedStartStopLeakIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    /**
     * A repo-wide grep found no production class implementing {@link SenderExceptionListener} — {@link
     * com.im.njams.sdk.communication.AbstractReceiver} only implements {@code SenderRecoveryListener} — so
     * {@code Njams.startReceiver()}'s {@code addSenderExceptionListener} branch is currently dead code for every
     * built-in transport, and repeated start/stop of ordinary {@code Njams} instances alone can never grow that
     * collection. This no-op stand-in simulates the real extension-point use case: external client code that
     * registers its own {@link SenderExceptionListener} via the public {@code NjamsSender.addSenderExceptionListener}
     * API, the only way that collection is exercised today.
     */
    private static final class NoOpSenderExceptionListener implements SenderExceptionListener {
        @Override
        public void onException(Exception exception, CommonMessage msg) {
            // Intentionally does nothing — this stand-in exists only to be a distinct, identifiable registration.
        }
    }

    @Test(timeout = 60000)
    @SuppressWarnings("deprecation") // Njams.getSender() is deprecated for removal, but is the only way to reach
                                      // the shared group's listener-count accessors from outside the SDK.
    public void listenerRegistrationsOnASharedGroupReturnToZeroOnceRemovedOrTheirClientsStopped()
        throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        // Opts all instances below into the SAME static shared SenderPool (NjamsSender.takeSharedSender) — without
        // this, each instance gets a fresh, private SenderPool (confirmed via Njams.getSender() source), and
        // nothing would ever accumulate in a single pool's listener collections. HTTP is deliberately used instead
        // of JMS: JMS has a ShareableReceiver variant (SharedJmsReceiver) that dedupes every instance's receiver to
        // one object under this same setting, which would collapse recoveryListenerCount() to 1 regardless of
        // instance count. HTTP has no shareable receiver (confirmed via source — no such class exists for
        // HttpSseReceiver), so sharing applies to the sender only (per CommunicationFactory's own log message),
        // giving each instance a distinct receiver identity registered on the one shared pool.
        settings.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");

        // Ten independent Njams instances sharing the same connection settings, hence the same shared sender
        // group, kept alive simultaneously: stopping each before starting the next would let the shared pool's
        // usage refcount return to 0 between iterations, destroying and recreating it every time and never letting
        // registrations actually accumulate.
        List<Njams> instances = new ArrayList<>();
        List<SenderExceptionListener> externalListeners = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Njams instance = new Njams(Path.of("HttpRepeatedStartStopLeakIT-" + i), "1.0.0", "CommunicationIT",
                settings);
            instance.start();
            // Simulates one external SenderExceptionListener registration per client instance — see
            // NoOpSenderExceptionListener's Javadoc for why this is necessary instead of relying on a built-in
            // receiver.
            SenderExceptionListener externalListener = new NoOpSenderExceptionListener();
            instance.getSender().addSenderExceptionListener(externalListener);
            externalListeners.add(externalListener);
            instances.add(instance);
        }
        NjamsSender sharedSender = instances.get(0).getSender();

        // While all 10 instances are still alive: each contributed one exception-listener registration (this
        // test's own doing, per the class Javadoc above) and one recovery-listener registration (each instance's
        // own distinct HttpSseReceiver, registered by Njams.startReceiver()).
        assertEquals(10, sharedSender.exceptionListenerCount());
        assertEquals(10, sharedSender.recoveryListenerCount());

        for (Njams instance : instances) {
            instance.stop();
        }

        // SenderRecoveryListener has a correct add/remove pair (Njams.stop() calls removeSenderRecoveryListener),
        // so its registration count must return to 0 once every instance has stopped — no leak here.
        assertEquals("SenderRecoveryListener registration leaked across repeated start/stop", 0,
            sharedSender.recoveryListenerCount());
        // Registrations made by external client code through the public addSenderExceptionListener API are not
        // tied to any Njams instance's lifecycle, so stopping the instances does not undo them: the owner removes
        // them via removeSenderExceptionListener (SDK-485), mirroring removeSenderRecoveryListener.
        assertEquals(10, sharedSender.exceptionListenerCount());
        externalListeners.forEach(sharedSender::removeSenderExceptionListener);
        assertEquals("SenderExceptionListener registration leaked although explicitly removed", 0,
            sharedSender.exceptionListenerCount());
    }

    /**
     * Mirrors {@code RepeatedFlapIT}'s "no thread pileup" check, but for a different failure mode: that test flaps
     * one already-running instance's connection and checks the JVM's total thread count; this checks whether
     * {@code Njams.stop()} itself actually terminates a receiver's own threads, across many independent
     * start/stop cycles. {@code AbstractReceiver}'s {@code startupConnectThread} and {@code reconnectThread} fields
     * (confirmed via source) are named {@code "Receiver-Startup-" + getName()},
     * {@code "Receiver-Sender-Reconnector-Thread[...]"}, and {@code "Receiver-Recovery-Cycle-Thread[...]"} — all
     * sharing the {@code "Receiver-"} prefix used below. This uses its own, non-shared instances (unlike the
     * listener-leak test above): sharing is irrelevant here since HTTP has no shareable receiver (see that test's
     * Javadoc), so each instance always gets its own distinct receiver and threads regardless.
     */
    @Test(timeout = 60000)
    public void repeatedStartStopDoesNotLeaveReceiverThreadsRunning() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");

        for (int i = 0; i < 10; i++) {
            Njams instance = new Njams(Path.of("ReceiverThreadLeakIT-" + i), "1.0.0", "CommunicationIT", settings);
            instance.start();
            instance.stop();
        }

        // Allow the last iteration's startup/reconnect threads a moment to actually terminate. Polls rather than
        // a single fixed sleep: beginConnect() never blocks Njams.start(), so a startup thread can still be
        // in-flight (running its own real connect() call) at the moment this loop's last stop() returns.
        Set<String> survivingReceiverThreads = Set.of();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            survivingReceiverThreads = Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.startsWith("Receiver-"))
                .collect(Collectors.toSet());
            if (!survivingReceiverThreads.isEmpty()) {
                Thread.sleep(200);
            }
        } while (!survivingReceiverThreads.isEmpty() && System.nanoTime() < deadline);
        assertTrue("Receiver-side threads survived repeated start/stop: " + survivingReceiverThreads,
            survivingReceiverThreads.isEmpty());
    }
}
