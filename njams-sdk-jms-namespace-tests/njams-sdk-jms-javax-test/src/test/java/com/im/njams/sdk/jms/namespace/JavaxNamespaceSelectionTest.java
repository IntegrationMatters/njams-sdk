package com.im.njams.sdk.jms.namespace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
import com.im.njams.sdk.communication.TransportNotApplicableException;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.ServiceLoaderSupport;

/**
 * Proves that on a classpath carrying only {@code javax.jms} and a javax ActiveMQ client, the SDK selects the
 * javax tree, skips the jakarta one without treating it as a failure, and says so in its availability report.
 * <p>
 * No broker is involved and nothing connects: {@code CommunicationFactory} only instantiates and initializes,
 * and ActiveMQ's client-side JNDI provider synthesises the connection factory from the URL without opening a
 * socket. {@code Njams.start()} is therefore never called.
 * <p>
 * All class assertions use fully-qualified names, because both trees use the same simple names.
 */
public class JavaxNamespaceSelectionTest {

    private static final String SELECTED_SENDER = "com.im.njams.sdk.communication.jms.javax.JmsSender";
    private static final String SELECTED_RECEIVER = "com.im.njams.sdk.communication.jms.javax.JmsReceiver";
    /** The skipped tree is identified by the JMS API it needs, not by its class names; see below. */
    private static final String SKIPPED_NAMESPACE = "jakarta";

    private Njams njams;

    @Before
    public void setUp() {
        // this factory's shared-receiver cache is static; never inherit what another test left in it
        CommunicationFactory.clearSharedReceiversForTesting();
        njams = mock(Njams.class);
        final NjamsMetadata metadata = mock(NjamsMetadata.class);
        when(njams.metadata()).thenReturn(metadata);
        when(metadata.getClientPath()).thenReturn(Path.of("test"));
    }

    @After
    public void tearDown() {
        CommunicationFactory.clearSharedReceiversForTesting();
    }

    /**
     * JMS settings that resolve without a broker: ActiveMQ's client-side initial context factory synthesises
     * the connection factory from the provider URL. Its class name is the same in both ActiveMQ clients, so it
     * is each module's own client that decides which JMS namespace the looked-up factory belongs to.
     */
    private static ClientSettings settings() {
        final Properties properties = new Properties();
        properties.setProperty(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY,
            "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "tcp://localhost:61616");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "ConnectionFactory");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        return ClientSettings.from(properties);
    }

    @Test
    public void communicationFactoryReturnsTheExpectedSenderImplementation() {
        final AbstractSender sender = new CommunicationFactory(settings()).getSender();

        assertEquals(SELECTED_SENDER, sender.getClass().getName());
    }

    @Test
    public void communicationFactoryReturnsTheExpectedReceiverImplementation() {
        final AbstractReceiver receiver = new CommunicationFactory(settings()).getReceiver(njams);

        assertEquals(SELECTED_RECEIVER, receiver.getClass().getName());
    }

    @Test
    public void theOtherNamespacesProvidersAreSkipped_notFatal() {
        final ServiceLoaderSupport<AbstractSender> senders = loaderFor(AbstractSender.class);
        senders.getAll();
        final ServiceLoaderSupport<AbstractReceiver> receivers = loaderFor(AbstractReceiver.class);
        receivers.getAll();

        assertFalse("senders: " + senders.describeAvailability(AbstractSender::getName), senders.hasUnavailable());
        assertFalse("receivers: " + receivers.describeAvailability(AbstractReceiver::getName),
            receivers.hasUnavailable());
    }

    /**
     * The report identifies the skipped tree by the JMS API it is missing, not by its provider class names:
     * loading {@code ...jms.JmsSender} fails on its {@code jakarta.jms} superinterface, and the resulting
     * {@link NoClassDefFoundError} is not a {@code ClassNotFoundException}, so {@code ServiceLoader} lets it
     * escape raw instead of wrapping it in a {@code ServiceConfigurationError} naming the provider. That
     * wrapper is the only place {@code ServiceLoaderSupport} can read a provider name from, so it falls back
     * to "Unidentified provider" - and the two jakarta receivers, failing identically, collapse into one line.
     */
    @Test
    public void describeAvailabilityNamesTheSkippedTree() {
        final ServiceLoaderSupport<AbstractSender> senders = loaderFor(AbstractSender.class);
        final String senderReport = senders.describeAvailability(AbstractSender::getName);
        final ServiceLoaderSupport<AbstractReceiver> receivers = loaderFor(AbstractReceiver.class);
        final String receiverReport = receivers.describeAvailability(AbstractReceiver::getName);

        for (String report : new String[] { senderReport, receiverReport }) {
            assertTrue(report, report.contains("Not applicable (required JMS API not present)"));
            assertTrue("must name the JMS API that is missing: " + report, namesTheMissingApi(report));
            // the skipped tree must not be reported as broken
            assertTrue(report, report.contains("Unavailable: none"));
            // ... and the tree that did load is still offered under its communication type. Only the
            // available list may be inspected: the "required JMS API not present" heading contains "JMS" too.
            final String available = availableList(report);
            assertTrue(available, available.contains("JMS"));
        }
    }

    /** The report's leading {@code Available: [...]} line, which is the only place implementations are listed. */
    private static String availableList(String report) {
        assertTrue(report, report.startsWith("Available: ["));
        return report.substring(0, report.indexOf(']') + 1);
    }

    /** A linkage error names the missing type with slashes, a ClassNotFoundException with dots. */
    private static boolean namesTheMissingApi(String report) {
        return report.contains(SKIPPED_NAMESPACE + ".jms.") || report.contains(SKIPPED_NAMESPACE + "/jms/");
    }

    /** Builds a loader the way {@code CommunicationFactory} does, with the transport not-applicable classifier. */
    private static <S> ServiceLoaderSupport<S> loaderFor(Class<S> type) {
        return new ServiceLoaderSupport<>(type, TransportNotApplicableException::isNotApplicable,
            TransportNotApplicableException.NOT_APPLICABLE_REASON);
    }
}
