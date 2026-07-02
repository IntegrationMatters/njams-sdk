package com.im.njams.sdk.communication.it;

import org.apache.activemq.ActiveMQConnectionFactory;

/** Convenience holder for an ActiveMQ ConnectionFactory used by test-side consumers. */
final class ConnectionFactorySupport {
    private final ActiveMQConnectionFactory factory;

    ConnectionFactorySupport(String brokerUrl) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
    }

    ActiveMQConnectionFactory factory() {
        return factory;
    }
}
