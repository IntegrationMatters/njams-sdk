package com.im.njams.sdk.jms.namespace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.ServiceLoaderSupport;

/**
 * Proves selection on the classpath the single-namespace modules cannot arrange: <b>both</b> JMS APIs and two
 * different broker products at once - ActiveMQ Classic on javax, Artemis on jakarta. Both trees load here, so
 * neither can be chosen by what happens to be loadable; only the configured connection factory decides.
 * <p>
 * Each product brings its own JNDI initial context factory, and that setting alone determines which namespace's
 * {@code ConnectionFactory} the lookup yields, and therefore which tree's cast succeeds.
 * <p>
 * No broker is involved and nothing connects: {@code CommunicationFactory} only instantiates and initializes,
 * and both clients' JNDI providers synthesise the connection factory from the provider URL without opening a
 * socket. {@code Njams.start()} is therefore never called.
 * <p>
 * All class assertions use fully-qualified names, because both trees use the same simple names.
 */
public class MixedNamespaceSelectionTest {

    private static final String JAVAX_SENDER = "com.im.njams.sdk.communication.jms.javax.JmsSender";
    private static final String JAVAX_RECEIVER = "com.im.njams.sdk.communication.jms.javax.JmsReceiver";
    private static final String JAKARTA_SENDER = "com.im.njams.sdk.communication.jms.JmsSender";
    private static final String JAKARTA_RECEIVER = "com.im.njams.sdk.communication.jms.JmsReceiver";

    /** ActiveMQ Classic's client-side JNDI provider; its connection factories are {@code javax.jms} ones. */
    private static final String CLASSIC_CONTEXT_FACTORY = "org.apache.activemq.jndi.ActiveMQInitialContextFactory";
    /** Artemis' client-side JNDI provider; its connection factories are {@code jakarta.jms} ones. */
    private static final String ARTEMIS_CONTEXT_FACTORY =
        "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory";
    /** A JNDI provider on no classpath at all: neither tree can resolve settings naming it. */
    private static final String MISSING_CONTEXT_FACTORY = "com.im.njams.sdk.jms.namespace.NoSuchContextFactory";

    private Njams njams;

    @Before
    public void setUp() {
        // this factory's shared-receiver cache is static; never inherit what another test left in it
        CommunicationFactory.clearSharedReceiversForTesting();
        njams = mock(Njams.class);
        final NjamsMetadata metadata = mock(NjamsMetadata.class);
        when(njams.metadata()).thenReturn(metadata);
        when(metadata.getClientPath()).thenReturn(Path.of("test"));
        requireBothTreesOnTheClasspath();
    }

    /**
     * Both trees must have loaded, or every assertion here would only repeat what a single-namespace module
     * already proves. Checked before each test rather than asserted in one, so no test can pass vacuously.
     */
    private static void requireBothTreesOnTheClasspath() {
        final ServiceLoaderSupport<AbstractSender> senders = new ServiceLoaderSupport<>(AbstractSender.class);
        final String report = senders.describeAvailability(AbstractSender::getName);
        assertEquals("both JMS senders must load on this classpath: " + report, 2,
            senders.stream().filter(s -> "JMS".equalsIgnoreCase(s.getName())).count());
        assertFalse(report, senders.hasUnavailable());
    }

    @After
    public void tearDown() {
        CommunicationFactory.clearSharedReceiversForTesting();
    }

    /**
     * JMS settings that resolve without a broker: both clients' initial context factories synthesise the
     * connection factory from the provider URL, and look it up under the name {@code ConnectionFactory}.
     *
     * @param initialContextFactory the JNDI provider deciding which namespace the looked-up factory belongs to.
     */
    private static ClientSettings settings(String initialContextFactory) {
        final Properties properties = new Properties();
        properties.setProperty(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY, initialContextFactory);
        properties.setProperty(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "tcp://localhost:61616");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "ConnectionFactory");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        return ClientSettings.from(properties);
    }

    @Test
    public void classicSettingsSelectTheJavaxTree() {
        final ClientSettings classic = settings(CLASSIC_CONTEXT_FACTORY);

        assertEquals(JAVAX_SENDER, new CommunicationFactory(classic).getSender().getClass().getName());
        assertEquals(JAVAX_RECEIVER, new CommunicationFactory(classic).getReceiver(njams).getClass().getName());
    }

    @Test
    public void artemisJakartaSettingsSelectTheJakartaTree() {
        final ClientSettings artemis = settings(ARTEMIS_CONTEXT_FACTORY);

        assertEquals(JAKARTA_SENDER, new CommunicationFactory(artemis).getSender().getClass().getName());
        assertEquals(JAKARTA_RECEIVER, new CommunicationFactory(artemis).getReceiver(njams).getClass().getName());
    }

    /**
     * The point of this module: two clients in one JVM, each with its own settings, each landing on its own
     * tree. The resolution is memoized per {@code CommunicationFactory} instance, so the two never see each
     * other's decision - a JVM-global switch could not pass this.
     */
    @Test
    public void twoClientsInOneJvmEachResolveTheirOwnTree() {
        final CommunicationFactory onClassic = new CommunicationFactory(settings(CLASSIC_CONTEXT_FACTORY));
        final CommunicationFactory onArtemis = new CommunicationFactory(settings(ARTEMIS_CONTEXT_FACTORY));

        final AbstractSender classicSender = onClassic.getSender();
        final AbstractSender artemisSender = onArtemis.getSender();
        // interleaved, and asked a second time: the memo is per instance, so neither answer may drift
        final AbstractSender classicAgain = onClassic.getSender();
        final AbstractSender artemisAgain = onArtemis.getSender();

        assertEquals(JAVAX_SENDER, classicSender.getClass().getName());
        assertEquals(JAKARTA_SENDER, artemisSender.getClass().getName());
        assertEquals(JAVAX_SENDER, classicAgain.getClass().getName());
        assertEquals(JAKARTA_SENDER, artemisAgain.getClass().getName());

        final AbstractReceiver classicReceiver =
            new CommunicationFactory(settings(CLASSIC_CONTEXT_FACTORY)).getReceiver(njams);
        final AbstractReceiver artemisReceiver =
            new CommunicationFactory(settings(ARTEMIS_CONTEXT_FACTORY)).getReceiver(njams);

        assertEquals(JAVAX_RECEIVER, classicReceiver.getClass().getName());
        assertEquals(JAKARTA_RECEIVER, artemisReceiver.getClass().getName());
    }

    /**
     * Settings that resolve in neither tree - here a JNDI provider that is on no classpath at all, which is a
     * genuine misconfiguration rather than a namespace mismatch. Both candidates fail, and that must produce
     * one error carrying every candidate's cause rather than falling through to another transport: the message
     * names each candidate by its fully-qualified class name (they did load, so their names are known), and
     * each one's cause is attached as a suppressed throwable.
     */
    @Test
    public void settingsResolvingInNeitherTreeProduceOneErrorNamingEveryCause() {
        final ClientSettings broken = settings(MISSING_CONTEXT_FACTORY);

        assertNamesEveryCause(failureOf(() -> new CommunicationFactory(broken).getSender()),
            JAVAX_SENDER, JAKARTA_SENDER);
        assertNamesEveryCause(failureOf(() -> new CommunicationFactory(broken).getReceiver(njams)),
            JAVAX_RECEIVER, JAKARTA_RECEIVER);
    }

    private static void assertNamesEveryCause(IllegalStateException failure, String javaxClass,
        String jakartaClass) {
        final String message = failure.getMessage();
        assertTrue("must name the javax candidate: " + message, message.contains(javaxClass));
        assertTrue("must name the jakarta candidate: " + message, message.contains(jakartaClass));

        final Throwable[] causes = failure.getSuppressed();
        assertTrue("one cause per tried candidate, but got " + causes.length, causes.length >= 2);
        for (Throwable cause : causes) {
            assertTrue("every cause must report the misconfiguration: " + cause, reports(cause));
        }
    }

    /** Whether the given failure's cause chain names the JNDI provider that could not be loaded. */
    private static boolean reports(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(MISSING_CONTEXT_FACTORY)) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }

    /** Runs the given call, requiring it to fail with a single {@link IllegalStateException}. */
    private static IllegalStateException failureOf(Runnable call) {
        try {
            call.run();
            fail("expected an IllegalStateException; these settings resolve in neither JMS namespace");
            return null;
        } catch (IllegalStateException expected) {
            return expected;
        }
    }
}
