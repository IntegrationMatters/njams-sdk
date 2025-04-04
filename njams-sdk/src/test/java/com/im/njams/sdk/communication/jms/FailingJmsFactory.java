package com.im.njams.sdk.communication.jms;

import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.naming.NamingException;

import com.im.njams.sdk.communication.jms.factory.JmsFactory;

public class FailingJmsFactory implements JmsFactory {
    private static class IntentionalError extends Error {
        public IntentionalError() {
            super("Intentionally failing with an Error");
        }
    }

    public static final String NAME = "FailingJmsFactory";

    public FailingJmsFactory() {
        throw new IntentionalError();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(Properties settings) throws JMSException, NamingException {
        throw new IntentionalError();
    }

    @Override
    public ConnectionFactory createConnectionFactory() throws JMSException, NamingException {
        throw new IntentionalError();
    }

}
