package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

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
    public void repeatedRegistrationAgainstASharedGroupLeaksExceptionListenersButNotRecoveryListeners()
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
        for (int i = 0; i < 10; i++) {
            Njams instance = new Njams(Path.of("HttpRepeatedStartStopLeakIT-" + i), "1.0.0", "CommunicationIT",
                settings);
            instance.start();
            // Simulates one external SenderExceptionListener registration per client instance — see
            // NoOpSenderExceptionListener's Javadoc for why this is necessary instead of relying on a built-in
            // receiver.
            instance.getSender().addSenderExceptionListener(new NoOpSenderExceptionListener());
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
        // Known gap, confirmed via source: neither SenderPool nor NjamsSender has any removeSenderExceptionListener
        // method at all, so a registration made through the public addSenderExceptionListener API has no way to
        // ever be undone — it persists even after every instance that registered one has stopped. This assertion
        // documents/regression-guards the currently-confirmed count; per communication-it-module.md this module
        // detects real defects rather than fixing them, so a future fix for the underlying SenderPool gap is
        // expected to update this expected value as part of that fix.
        assertEquals("Expected count for the known, ticketed SenderExceptionListener leak (see SDK ticket) — "
            + "update this value if/when SenderPool gains a matching remove path", 10,
            sharedSender.exceptionListenerCount());
    }
}
