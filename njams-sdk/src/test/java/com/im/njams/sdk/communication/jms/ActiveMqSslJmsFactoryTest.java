package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link ActiveMqSslJmsFactory}: identity and JNDI inheritance. The reflective creation of an
 * {@code ActiveMQSslConnectionFactory} needs a jakarta ActiveMQ client and is covered by
 * {@code JakartaActiveMqSslJmsFactoryTest} in the jakarta namespace test module.
 */
public class ActiveMqSslJmsFactoryTest {

    @Test
    public void nameAndToString() {
        ActiveMqSslJmsFactory factory = new ActiveMqSslJmsFactory();
        assertEquals(ActiveMqSslJmsFactory.NAME, factory.getName());
        assertTrue(factory.toString().contains(ActiveMqSslJmsFactory.NAME));
    }

    @Test
    public void inheritsFromJndiJmsFactory() {
        // it reuses JNDI for queue/topic lookup, only the connection factory differs
        assertTrue(new ActiveMqSslJmsFactory() instanceof JndiJmsFactory);
    }
}
