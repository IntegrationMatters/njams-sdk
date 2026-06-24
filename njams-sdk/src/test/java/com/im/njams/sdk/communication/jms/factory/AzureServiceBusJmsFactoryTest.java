package com.im.njams.sdk.communication.jms.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Unit tests for {@link AzureServiceBusJmsFactory}: identity, not-initialized guard and the
 * exception handling for an invalid provider URL.
 */
public class AzureServiceBusJmsFactoryTest {

    @Test
    public void nameAndToString() {
        AzureServiceBusJmsFactory factory = new AzureServiceBusJmsFactory();
        assertEquals(AzureServiceBusJmsFactory.NAME, factory.getName());
        assertTrue(factory.toString().contains(AzureServiceBusJmsFactory.NAME));
    }

    @Test
    public void createConnectionFactoryBeforeInitThrows() {
        try {
            new AzureServiceBusJmsFactory().createConnectionFactory();
            fail("expected IllegalStateException when not initialized");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void initWithInvalidProviderUrlThrowsJmsException() {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, ":::");

        try {
            new AzureServiceBusJmsFactory().init(ClientSettings.from(props));
            fail("expected JMSException for an invalid provider URL");
        } catch (JMSException expected) {
            // expected: the malformed URI is wrapped into a JMSException
        }
    }
}
