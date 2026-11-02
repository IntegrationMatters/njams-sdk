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
 * The classpath njams-sdk's own suite cannot arrange: neither JMS API and no broker client. Both JMS trees are
 * skipped, so asking for the JMS transport must fail with a plain {@link IllegalStateException} that the caller
 * can handle - never with a {@link NoClassDefFoundError} escaping from class loading - and the availability
 * report must say which API is missing. Every other transport stays usable.
 */
public class NoJmsApiPresentTest {

    private Njams njams;

    @Before
    public void setUp() {
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

    private static ClientSettings jmsSettings() {
        final Properties properties = new Properties();
        properties.setProperty(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "ConnectionFactory");
        return ClientSettings.from(properties);
    }

    @Test
    public void askingForTheJmsSenderFailsWithAHandleableError() {
        try {
            new CommunicationFactory(jmsSettings()).getSender();
            fail("expected an IllegalStateException; no JMS implementation can load here");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("JMS"));
        }
    }

    @Test
    public void askingForTheJmsReceiverFailsWithAHandleableError() {
        try {
            new CommunicationFactory(jmsSettings()).getReceiver(njams);
            fail("expected an IllegalStateException; no JMS implementation can load here");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("JMS"));
        }
    }

    @Test
    public void bothTreesAreSkippedRatherThanBroken() {
        final ServiceLoaderSupport<AbstractSender> senders = new ServiceLoaderSupport<>(AbstractSender.class);
        senders.getAll();
        final ServiceLoaderSupport<AbstractReceiver> receivers = new ServiceLoaderSupport<>(AbstractReceiver.class);
        receivers.getAll();

        assertFalse("senders: " + senders.describeAvailability(AbstractSender::getName), senders.hasUnavailable());
        assertFalse("receivers: " + receivers.describeAvailability(AbstractReceiver::getName),
            receivers.hasUnavailable());
    }

    @Test
    public void describeAvailabilityNamesBothMissingApis() {
        final ServiceLoaderSupport<AbstractSender> senders = new ServiceLoaderSupport<>(AbstractSender.class);
        final String senderReport = senders.describeAvailability(AbstractSender::getName);
        final ServiceLoaderSupport<AbstractReceiver> receivers = new ServiceLoaderSupport<>(AbstractReceiver.class);
        final String receiverReport = receivers.describeAvailability(AbstractReceiver::getName);

        for (String report : new String[] { senderReport, receiverReport }) {
            assertTrue(report, report.contains("Not applicable (required JMS API not present)"));
            assertTrue(report, names(report, "jakarta"));
            assertTrue(report, names(report, "javax"));
            assertTrue(report, report.contains("Unavailable: none"));
            // no JMS implementation is left, while the other transports are unaffected. Only the available
            // list may be inspected for "JMS": the "required JMS API not present" heading also contains it.
            final String available = availableList(report);
            assertFalse(available, available.contains("JMS"));
            assertTrue(available, available.contains("HTTP"));
        }
    }

    /** The report's leading {@code Available: [...]} line, which is the only place implementations are listed. */
    private static String availableList(String report) {
        assertTrue(report, report.startsWith("Available: ["));
        return report.substring(0, report.indexOf(']') + 1);
    }

    /**
     * No JMS implementation may be registered at all here - a provider that silently loaded would make every
     * other assertion in this module meaningless.
     */
    @Test
    public void noJmsImplementationIsRegistered() {
        assertEquals(0, new ServiceLoaderSupport<>(AbstractSender.class).stream()
            .filter(s -> "JMS".equalsIgnoreCase(s.getName())).count());
        assertEquals(0, new ServiceLoaderSupport<>(AbstractReceiver.class).stream()
            .filter(r -> "JMS".equalsIgnoreCase(r.getName())).count());
    }

    /** A linkage error names the missing type with slashes, a ClassNotFoundException with dots. */
    private static boolean names(String report, String namespace) {
        return report.contains(namespace + ".jms.") || report.contains(namespace + "/jms/");
    }
}
