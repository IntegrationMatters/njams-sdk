/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import static com.im.njams.sdk.communication.jms.JmsConstants.CONNECTION_FACTORY;
import static com.im.njams.sdk.communication.jms.JmsConstants.KEYSTORE;
import static com.im.njams.sdk.communication.jms.JmsConstants.KEYSTOREPASSWORD;
import static com.im.njams.sdk.communication.jms.JmsConstants.KEYSTORETYPE;
import static com.im.njams.sdk.communication.jms.JmsConstants.PASSWORD;
import static com.im.njams.sdk.communication.jms.JmsConstants.PROVIDER_URL;
import static com.im.njams.sdk.communication.jms.JmsConstants.TRUSTSTORE;
import static com.im.njams.sdk.communication.jms.JmsConstants.TRUSTSTOREPASSWORD;
import static com.im.njams.sdk.communication.jms.JmsConstants.TRUSTSTORETYPE;
import static com.im.njams.sdk.communication.jms.JmsConstants.USERNAME;

import java.lang.reflect.Method;
import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * nJAMS ConnectionFactory provider mainly used for configuring SSL connection for ActiveMQ.
 *
 * @author sfaiz
 * @version 4.1.7
 */
public class NjamsConnectionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsConnectionFactory.class);

    /**
     * Creates the Factory via the given {@link Properties} and {@link Context}.
     *
     * @param context the context needed to initialize
     * @param properties the properties needed to initialize
     *
     * @return the ConnectionFactory to use
     * @throws Exception if an error occurred
     */
    public static ConnectionFactory getFactory(Context context, Properties properties) throws Exception {
        ConnectionFactory factory;
        if (properties.getProperty(CONNECTION_FACTORY).equalsIgnoreCase("ActiveMQSslConnectionFactory")) {
            factory = createActiveMQSslConnectionFactory(properties);
        } else {
            factory = (ConnectionFactory) context.lookup(properties.getProperty(CONNECTION_FACTORY));
        }
        return factory;
    }

    private static ConnectionFactory createActiveMQSslConnectionFactory(Properties properties) throws Exception {
        try {
            @SuppressWarnings("unchecked")
            final Class<ConnectionFactory> clazz =
                    (Class<ConnectionFactory>) Class.forName("org.apache.activemq.ActiveMQSslConnectionFactory");
            final ConnectionFactory amqSsl = clazz.getDeclaredConstructor().newInstance();
            setProperty(amqSsl, properties, "setKeyStore", KEYSTORE);
            setProperty(amqSsl, properties, "setKeyStorePassword", KEYSTOREPASSWORD);
            setProperty(amqSsl, properties, "setKeyStoreType", KEYSTORETYPE);
            setProperty(amqSsl, properties, "setTrustStore", TRUSTSTORE);
            setProperty(amqSsl, properties, "setTrustStorePassword", TRUSTSTOREPASSWORD);
            setProperty(amqSsl, properties, "setTrustStoreType", TRUSTSTORETYPE);
            setProperty(amqSsl, properties, "setPassword", PASSWORD);
            setProperty(amqSsl, properties, "setUserName", USERNAME);
            setProperty(amqSsl, properties, "setBrokerURL", PROVIDER_URL);
            LOG.debug("Created ActiveMQSslConnectionFactory");
            return amqSsl;
        } catch (Exception e) {
            LOG.error("ActiveMQSslConnectionFactory could not be created", e);
            throw e;
        }
    }

    private static void setProperty(ConnectionFactory target, Properties source, String setter, String property) {
        if (source.containsKey(property)) {
            try {
                final Method setMethod = target.getClass().getMethod(setter, String.class);
                setMethod.invoke(target, source.getProperty(property));
            } catch (Exception e) {
                LOG.error("Failed to invoke {}(String) on {} with property {}={}", setter, target.getClass(),
                        property, source.getProperty(property), e);
            }
        }
    }
}
