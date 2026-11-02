package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

/**
 * The classification is applied to every transport's providers, not only the JMS ones, so it must recognize a JMS
 * namespace problem without swallowing an unrelated defect in an HTTP or Kafka provider.
 */
public class TransportNotApplicableExceptionTest {

    private static final String NAMESPACE_CAST =
        "class javax.jms.ConnectionFactory cannot be cast to class jakarta.jms.ConnectionFactory";

    @Test
    public void aCastBetweenTheJmsNamespacesIsNotApplicable() {
        assertTrue(TransportNotApplicableException.isNotApplicable(new ClassCastException(NAMESPACE_CAST)));
    }

    @Test
    public void aCastFailureNamingNoJmsApiTypeIsABrokenProvider() {
        assertFalse(TransportNotApplicableException.isNotApplicable(new ClassCastException(
            "class org.example.Response cannot be cast to class org.example.Request")));
    }

    @Test
    public void aCastFailureWithoutAMessageIsABrokenProvider() {
        assertFalse(TransportNotApplicableException.isNotApplicable(new ClassCastException()));
    }

    @Test
    public void aMissingJmsApiTypeIsNotApplicableInBothSpellings() {
        assertTrue(TransportNotApplicableException.isNotApplicable(
            new NoClassDefFoundError("javax/jms/Message")));
        assertTrue(TransportNotApplicableException.isNotApplicable(
            new ClassNotFoundException("jakarta.jms.Message")));
    }

    @Test
    public void aTypeMissingForAnyOtherReasonIsAnError() {
        assertFalse(TransportNotApplicableException.isNotApplicable(
            new NoClassDefFoundError("org/apache/activemq/ActiveMQConnectionFactory")));
        assertFalse(TransportNotApplicableException.isNotApplicable(
            new ClassNotFoundException("org.apache.kafka.clients.producer.KafkaProducer")));
    }

    @Test
    public void theWholeCauseChainIsExamined() {
        final Exception wrapped = new IllegalStateException("could not initialize",
            new IOException("while connecting", new ClassCastException(NAMESPACE_CAST)));

        assertTrue(TransportNotApplicableException.isNotApplicable(wrapped));
    }

    /** {@code initCause(this)} is rejected by the JDK, so only an overridden {@code getCause()} can loop. */
    @Test
    public void aSelfReferencingCauseChainTerminates() {
        final Exception loop = new IllegalStateException("broken") {
            private static final long serialVersionUID = 1L;

            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertFalse(TransportNotApplicableException.isNotApplicable(loop));
    }

    @Test
    public void nullIsNotApplicableToNothing() {
        assertFalse(TransportNotApplicableException.isNotApplicable(null));
    }
}
