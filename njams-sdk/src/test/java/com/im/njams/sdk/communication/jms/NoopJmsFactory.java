package com.im.njams.sdk.communication.jms;

import static org.mockito.Mockito.mock;

import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.naming.NamingException;

import com.im.njams.sdk.communication.jms.factory.JmsFactory;

public class NoopJmsFactory implements JmsFactory {

    public static final String NAME = "NoopJmsFactory";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(Properties settings) throws JMSException, NamingException {
    }

    @Override
    public ConnectionFactory createConnectionFactory() throws JMSException, NamingException {
        return mock(ConnectionFactory.class);
    }

}
