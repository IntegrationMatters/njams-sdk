package com.im.njams.sdk.communication.it;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.settings.ClientSettings;

/** JmsFactory (selected via PROPERTY_JMS_CONNECTION_FACTORY=EmbeddedActiveMq) pointing at the embedded vm:// broker. */
public class EmbeddedActiveMqJmsFactory implements JmsFactory {

    public static final String NAME = "EmbeddedActiveMq";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(ClientSettings settings) {
        // nothing to initialize
    }

    @Override
    public ConnectionFactory createConnectionFactory() {
        return new ActiveMQConnectionFactory(EmbeddedActiveMqBroker.BROKER_URL);
    }
}
