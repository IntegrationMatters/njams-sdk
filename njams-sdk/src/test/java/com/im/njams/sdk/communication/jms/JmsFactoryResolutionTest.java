package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

// NOTE: this test stays in ...jms because it reads protected members of the two base classes. The
// single-type imports below keep naming the javax classes once Task 11 puts jakarta types of the same
// simple name into this very package (JLS 6.4.1) - do not turn them into an on-demand import.
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.TransportNotApplicableException;
import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.communication.jms.javax.JmsReceiver;
import com.im.njams.sdk.communication.jms.javax.JmsSender;
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
     * Fails the way {@code ActiveMqSslJmsFactory} and {@code AzureServiceBusJmsFactory} do: they catch whatever
     * went wrong and rethrow it wrapped into a {@link JMSException} of their own, so the discriminating failure
     * sits one level below the exception the factory hands out.
     */
    public static class WrappedCastFailureJmsFactory implements JmsFactory {
        static final String NAME = "WrappedCastFailureJmsFactory";

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void init(ClientSettings settings) throws JMSException {
            final JMSException wrapper = new JMSException("Failed to setup the connection factory");
            wrapper.initCause(new ClassCastException(
                "class jakarta.jms.ConnectionFactory cannot be cast to class javax.jms.ConnectionFactory"));
            throw wrapper;
        }

        @Override
        public ConnectionFactory createConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    /**
     * Fails like a JNDI context whose configured initial-context-factory class is missing. The
     * {@link ClassNotFoundException} is real, but the type it names is a broker's JNDI provider rather than a
     * JMS API type, so it means a deployment problem and not a JMS namespace mismatch.
     */
    public static class JndiProviderMissingJmsFactory implements JmsFactory {
        static final String NAME = "JndiProviderMissingJmsFactory";
        static final NoInitialContextException FAILURE = new NoInitialContextException(
            "Cannot instantiate class: com.example.ExampleInitialContextFactory");

        static {
            FAILURE.initCause(new ClassNotFoundException("com.example.ExampleInitialContextFactory"));
        }

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
     * Fails the way {@code ActiveMqSslJmsFactory} and {@code AzureServiceBusJmsFactory} do when their broker
     * client library is simply not deployed: {@code ReflectionWrapper} throws a {@link ClassNotFoundException}
     * for a broker class, and the factory rethrows it inside a {@link JMSException}. Same exception types as
     * {@link WrappedCastFailureJmsFactory}, but the missing type belongs to no JMS API - so this is a hard
     * error, not a reason to decline.
     */
    public static class MissingBrokerLibraryJmsFactory implements JmsFactory {
        static final String NAME = "MissingBrokerLibraryJmsFactory";
        static final JMSException FAILURE = new JMSException("Failed to setup ActiveMQSslConnectionFactory");

        static {
            FAILURE.initCause(new ClassNotFoundException("org.apache.activemq.ActiveMQSslConnectionFactory"));
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void init(ClientSettings settings) throws JMSException {
            throw FAILURE;
        }

        @Override
        public ConnectionFactory createConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    /**
     * Fails while it is being looked up rather than while it is initialized, so that the linkage error reaches
     * the classification from {@code JmsFactory.find} itself rather than from {@code JmsFactory.init}.
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
        LinkageFailingJmsFactory.class, WrappedCastFailureJmsFactory.class, JndiProviderMissingJmsFactory.class,
        MissingBrokerLibraryJmsFactory.class);

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
        assertHardError(MisconfiguredJmsFactory.FAILURE,
            () -> new JmsSender().init(using(MisconfiguredJmsFactory.NAME)));
    }

    @Test
    public void receiverInit_propagatesAConfigurationFailureUnchanged() {
        assertHardError(MisconfiguredJmsFactory.FAILURE,
            () -> new JmsReceiver().init(using(MisconfiguredJmsFactory.NAME)));
    }

    @Test
    public void init_throwsTransportNotApplicable_whenTheFactoryWrapsTheCastFailure() {
        assertNotApplicable(ClassCastException.class,
            () -> new JmsSender().init(using(WrappedCastFailureJmsFactory.NAME)));
    }

    @Test
    public void receiverInit_throwsTransportNotApplicable_whenTheFactoryWrapsTheCastFailure() {
        assertNotApplicable(ClassCastException.class,
            () -> new JmsReceiver().init(using(WrappedCastFailureJmsFactory.NAME)));
    }

    @Test
    public void init_propagatesAMissingJndiProviderUnchanged() {
        assertHardError(JndiProviderMissingJmsFactory.FAILURE,
            () -> new JmsSender().init(using(JndiProviderMissingJmsFactory.NAME)));
    }

    @Test
    public void receiverInit_propagatesAMissingJndiProviderUnchanged() {
        assertHardError(JndiProviderMissingJmsFactory.FAILURE,
            () -> new JmsReceiver().init(using(JndiProviderMissingJmsFactory.NAME)));
    }

    @Test
    public void init_propagatesAMissingBrokerLibraryUnchanged() {
        assertHardError(MissingBrokerLibraryJmsFactory.FAILURE,
            () -> new JmsSender().init(using(MissingBrokerLibraryJmsFactory.NAME)));
    }

    @Test
    public void receiverInit_propagatesAMissingBrokerLibraryUnchanged() {
        assertHardError(MissingBrokerLibraryJmsFactory.FAILURE,
            () -> new JmsReceiver().init(using(MissingBrokerLibraryJmsFactory.NAME)));
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
            assertTrue("cause chain must keep the original failure: " + e.getCause(),
                chainContains(e.getCause(), expectedCause));
        }
    }

    private static boolean chainContains(Throwable failure, Class<? extends Throwable> expected) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (expected.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static void assertHardError(Throwable expectedCause, Runnable init) {
        try {
            init.run();
            fail("expected the configuration failure to propagate");
        } catch (TransportNotApplicableException e) {
            fail("a configuration failure must not be reported as not applicable: " + e);
        } catch (IllegalStateException e) {
            assertSame(expectedCause, e.getCause());
        }
    }
}
