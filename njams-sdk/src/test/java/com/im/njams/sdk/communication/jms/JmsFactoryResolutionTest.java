package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.ConnectionFactory;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.TransportNotApplicableException;
import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.ScopedSpiRule;

/**
 * Verifies that {@link JmsSender#init(ClientSettings)} and {@link JmsReceiver#init(ClientSettings)} resolve the
 * configured {@link JmsFactory} as their first action, close it again, and decline with a
 * {@link TransportNotApplicableException} when the failure says that the configured factory belongs to the other
 * JMS namespace - while any other failure propagates unchanged.
 */
public class JmsFactoryResolutionTest {

    /** Records that it was initialized and closed again, so the probe-and-discard can be observed. */
    public static class RecordingJmsFactory implements JmsFactory {
        static final String NAME = "RecordingJmsFactory";
        static final AtomicInteger INITIALIZED = new AtomicInteger();
        static final AtomicInteger CLOSED = new AtomicInteger();

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            INITIALIZED.incrementAndGet();
        }

        @Override
        public ConnectionFactory createConnectionFactory() {
            return mock(ConnectionFactory.class);
        }

        @Override
        public void close() {
            CLOSED.incrementAndGet();
        }
    }

    /** Fails like a JNDI lookup that returned a connection factory of the other JMS namespace. */
    public static class CastFailingJmsFactory implements JmsFactory {
        static final String NAME = "CastFailingJmsFactory";

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            throw new ClassCastException(
                "class jakarta.jms.ConnectionFactory cannot be cast to class javax.jms.ConnectionFactory");
        }

        @Override
        public ConnectionFactory createConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    /** Fails like a factory whose broker client library is built against the other JMS namespace. */
    public static class MissingBrokerClientJmsFactory implements JmsFactory {
        static final String NAME = "MissingBrokerClientJmsFactory";

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            throw new NoClassDefFoundError("javax/jms/ConnectionFactory");
        }

        @Override
        public ConnectionFactory createConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    /** Fails with a plain configuration error that has nothing to do with the JMS namespace. */
    public static class MisconfiguredJmsFactory implements JmsFactory {
        static final String NAME = "MisconfiguredJmsFactory";
        static final NameNotFoundException FAILURE = new NameNotFoundException("njams.connectionFactory");

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void init(ClientSettings settings) throws NamingException {
            throw FAILURE;
        }

        @Override
        public ConnectionFactory createConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    /**
     * Fails while it is being looked up rather than while it is initialized, so that the linkage error reaches
     * the sender/receiver unwrapped instead of wrapped into the {@link IllegalStateException} that
     * {@code JmsFactory.initFactory} produces.
     */
    public static class LinkageFailingJmsFactory implements JmsFactory {
        static volatile boolean failOnLookup = false;

        @Override
        public String getName() {
            if (failOnLookup) {
                throw new NoClassDefFoundError("javax/jms/ConnectionFactory");
            }
            return "LinkageFailingJmsFactory";
        }

        @Override
        public void init(ClientSettings settings) {
            // never reached
        }

        @Override
        public ConnectionFactory createConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    /** Registers the fixtures above for this test class only, in addition to the regular providers. */
    @Rule
    public ScopedSpiRule fixtures = new ScopedSpiRule(JmsFactory.class, RecordingJmsFactory.class,
        CastFailingJmsFactory.class, MissingBrokerClientJmsFactory.class, MisconfiguredJmsFactory.class,
        LinkageFailingJmsFactory.class);

    @Before
    public void beforeEach() {
        RecordingJmsFactory.INITIALIZED.set(0);
        RecordingJmsFactory.CLOSED.set(0);
        LinkageFailingJmsFactory.failOnLookup = false;
    }

    @After
    public void afterEach() {
        LinkageFailingJmsFactory.failOnLookup = false;
    }

    private static ClientSettings settings(String... keyValues) {
        final Properties properties = new Properties();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            properties.put(keyValues[i], keyValues[i + 1]);
        }
        return ClientSettings.from(properties);
    }

    private static ClientSettings using(String factoryName) {
        return settings(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, factoryName);
    }

    @Test
    public void init_resolvesAndThenClosesTheFactory() {
        new JmsSender().init(using(RecordingJmsFactory.NAME));

        assertEquals(1, RecordingJmsFactory.INITIALIZED.get());
        assertEquals(1, RecordingJmsFactory.CLOSED.get());
    }

    @Test
    public void receiverInit_resolvesAndThenClosesTheFactory() {
        new JmsReceiver().init(using(RecordingJmsFactory.NAME));

        assertEquals(1, RecordingJmsFactory.INITIALIZED.get());
        assertEquals(1, RecordingJmsFactory.CLOSED.get());
    }

    @Test
    public void init_throwsTransportNotApplicable_whenTheFactoryCastFails() {
        assertNotApplicable(ClassCastException.class, () -> new JmsSender().init(using(CastFailingJmsFactory.NAME)));
    }

    @Test
    public void receiverInit_throwsTransportNotApplicable_whenTheFactoryCastFails() {
        assertNotApplicable(ClassCastException.class, () -> new JmsReceiver().init(using(CastFailingJmsFactory.NAME)));
    }

    @Test
    public void init_throwsTransportNotApplicable_whenTheBrokerClientIsAbsent() {
        assertNotApplicable(NoClassDefFoundError.class,
            () -> new JmsSender().init(using(MissingBrokerClientJmsFactory.NAME)));
    }

    @Test
    public void receiverInit_throwsTransportNotApplicable_whenTheBrokerClientIsAbsent() {
        assertNotApplicable(NoClassDefFoundError.class,
            () -> new JmsReceiver().init(using(MissingBrokerClientJmsFactory.NAME)));
    }

    @Test
    public void init_throwsTransportNotApplicable_whenTheLinkageErrorIsNotWrapped() {
        LinkageFailingJmsFactory.failOnLookup = true;
        assertNotApplicable(NoClassDefFoundError.class, () -> new JmsSender().init(using("anyNameThatIsLookedUp")));
    }

    @Test
    public void receiverInit_throwsTransportNotApplicable_whenTheLinkageErrorIsNotWrapped() {
        LinkageFailingJmsFactory.failOnLookup = true;
        assertNotApplicable(NoClassDefFoundError.class, () -> new JmsReceiver().init(using("anyNameThatIsLookedUp")));
    }

    @Test
    public void init_propagatesAConfigurationFailureUnchanged() {
        assertConfigurationFailurePropagates(() -> new JmsSender().init(using(MisconfiguredJmsFactory.NAME)));
    }

    @Test
    public void receiverInit_propagatesAConfigurationFailureUnchanged() {
        assertConfigurationFailurePropagates(() -> new JmsReceiver().init(using(MisconfiguredJmsFactory.NAME)));
    }

    @Test
    public void init_stillAppliesEverySettingAfterTheFactoryResolves() {
        final ClientSettings settings = settings(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY,
            RecordingJmsFactory.NAME, NjamsSettings.PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR, "false");
        final JmsSender sender = new JmsSender();

        sender.init(settings);

        assertTrue("project queue setting applied", sender.isUseProjectQueue());
        assertEquals(1, RecordingJmsFactory.INITIALIZED.get());
    }

    @Test
    public void receiverInit_stillAppliesEverySettingAfterTheFactoryResolves() {
        final ClientSettings settings = settings(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY,
            RecordingJmsFactory.NAME, NjamsSettings.PROPERTY_JMS_DESTINATION, "njams4.resolution",
            NjamsSettings.PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR, "false");
        final JmsReceiver receiver = new JmsReceiver();

        receiver.init(settings);

        assertEquals("njams4.resolution.commands", receiver.topicName);
        assertTrue("message selector setting applied", !receiver.useMessageselector);
        assertEquals(1, RecordingJmsFactory.INITIALIZED.get());
    }

    private static void assertNotApplicable(Class<? extends Throwable> expectedCause, Runnable init) {
        try {
            init.run();
            fail("expected the implementation to decline with a " + TransportNotApplicableException.class.getName());
        } catch (TransportNotApplicableException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("javax.jms"));
            final Throwable cause = e.getCause();
            final Throwable wrapped = cause == null ? null : cause.getCause();
            assertTrue("cause chain must keep the original failure: " + cause,
                expectedCause.isInstance(cause) || expectedCause.isInstance(wrapped));
        }
    }

    private static void assertConfigurationFailurePropagates(Runnable init) {
        try {
            init.run();
            fail("expected the configuration failure to propagate");
        } catch (TransportNotApplicableException e) {
            fail("a configuration failure must not be reported as not applicable: " + e);
        } catch (IllegalStateException e) {
            assertSame(MisconfiguredJmsFactory.FAILURE, e.getCause());
        }
    }
}
