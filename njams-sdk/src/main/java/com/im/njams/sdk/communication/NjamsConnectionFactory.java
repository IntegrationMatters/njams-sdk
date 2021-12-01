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

import com.im.njams.sdk.communication.jms.JmsConstants;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ConnectionFactory;
import javax.naming.Context;
import java.util.Properties;

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
        if(properties.getProperty(JmsConstants.CONNECTION_FACTORY).equalsIgnoreCase("ActiveMQSslConnectionFactory")) {
            ActiveMQSslConnectionFactory sslFactory = new ActiveMQSslConnectionFactory();
            if (properties.containsKey(JmsConstants.KEYSTORE))
                sslFactory.setKeyStore(properties.getProperty(JmsConstants.KEYSTORE));
            if (properties.containsKey(JmsConstants.KEYSTOREPASSWORD))
                sslFactory.setKeyStorePassword(properties.getProperty(JmsConstants.KEYSTOREPASSWORD));
            if (properties.containsKey(JmsConstants.KEYSTORETYPE))
                sslFactory.setKeyStoreType(properties.getProperty(JmsConstants.KEYSTORETYPE));
            if (properties.containsKey(JmsConstants.TRUSTSTORE))
                sslFactory.setTrustStore(properties.getProperty(JmsConstants.TRUSTSTORE));
            if (properties.containsKey(JmsConstants.TRUSTSTOREPASSWORD))
                sslFactory.setTrustStorePassword(properties.getProperty(JmsConstants.TRUSTSTOREPASSWORD));
            if (properties.containsKey(JmsConstants.TRUSTSTORETYPE))
                sslFactory.setTrustStoreType(properties.getProperty(JmsConstants.TRUSTSTORETYPE));
            if (properties.containsKey(JmsConstants.PASSWORD))
                sslFactory.setPassword(properties.getProperty(JmsConstants.PASSWORD));
            if (properties.containsKey(JmsConstants.USERNAME))
                sslFactory.setUserName(properties.getProperty(JmsConstants.USERNAME));
            if (properties.containsKey(JmsConstants.PROVIDER_URL))
                sslFactory.setBrokerURL(properties.getProperty(JmsConstants.PROVIDER_URL));
            factory = sslFactory;
        } else {
            factory = (ConnectionFactory) context
                    .lookup(properties.getProperty(JmsConstants.CONNECTION_FACTORY));
        }
        return factory;
    }
}
