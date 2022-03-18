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

import java.lang.reflect.Method;
import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.communication.jms.JmsConstants;

/**
 * nJAMS ConnectionFactory provider
 *
 * @author sfaiz
 * @version 4.1.7
 */
public class NjamsConnectionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsConnectionFactory.class);

    /**
     * Creates the Factory via the given Properties or Context.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#CONNECTION_FACTORY}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#KEYSTORE}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#KEYSTOREPASSWORD}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#USERNAME}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#PASSWORD}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#DESTINATION}
     * </ul>
     *
     * @param context the context needed to intialize
     * @param properties the properties needed to initialize
     *
     * @return the ConnectionFactory
     * @throws Exception is an error occured
     */
    public static ConnectionFactory getFactory(Context context, Properties properties) throws Exception {
        ConnectionFactory factory;
        if (properties.getProperty(JmsConstants.CONNECTION_FACTORY).equalsIgnoreCase("ActiveMQSslConnectionFactory")) {
            factory = createActiveMQSslConnectionFactory(properties);
        } else {
            factory = (ConnectionFactory) context.lookup(properties.getProperty(JmsConstants.CONNECTION_FACTORY));
        }
        return factory;
    }

    private static ConnectionFactory createActiveMQSslConnectionFactory(Properties properties) throws Exception {
        try {
            @SuppressWarnings("unchecked")
            final Class<ConnectionFactory> clazz =
                    (Class<ConnectionFactory>) Class.forName("org.apache.activemq.ActiveMQSslConnectionFactory");
            final ConnectionFactory amqSsl = clazz.getDeclaredConstructor().newInstance();
            setProperty(amqSsl, properties, "setKeyStore", JmsConstants.KEYSTORE);
            setProperty(amqSsl, properties, "setKeyStorePassword", JmsConstants.KEYSTOREPASSWORD);
            setProperty(amqSsl, properties, "setKeyStoreType", JmsConstants.KEYSTORETYPE);
            setProperty(amqSsl, properties, "setTrustStore", JmsConstants.TRUSTSTORE);
            setProperty(amqSsl, properties, "setTrustStorePassword", JmsConstants.TRUSTSTOREPASSWORD);
            setProperty(amqSsl, properties, "setTrustStoreType", JmsConstants.TRUSTSTORETYPE);
            setProperty(amqSsl, properties, "setPassword", JmsConstants.PASSWORD);
            setProperty(amqSsl, properties, "setUserName", JmsConstants.USERNAME);
            setProperty(amqSsl, properties, "setBrokerURL", JmsConstants.PROVIDER_URL);
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
                LOG.error("Failed to invoke {}(String) on {} with configured property {}", setter, target.getClass(),
                        property, e);
            }
        }
    }
}
