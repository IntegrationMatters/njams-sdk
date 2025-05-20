/*
 * Copyright (c) 2025 Integration Matters GmbH
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
package com.im.njams.sdk.communication.jms.factory;

import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.utils.ReflectionWrapper;

/**
 * IMPORTANT: This is loaded and initialized via SPI. Make sure that an instance can be created even when
 * required libraries are missing!<br>
 * This extends {@link JndiJmsFactory} for using JNDI when looking up queue/topic. Only obtaining a
 * {@link ConnectionFactory} is different here.
 */
public class ActiveMqSslJmsFactory extends JndiJmsFactory implements JmsFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveMqSslJmsFactory.class);
    /**
     * This implementation identifier for SPI lookup.
     */
    public static final String NAME = "ActiveMQSslConnectionFactory";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected void initFactory(Properties properties) throws NamingException, JMSException {
        try {

            final ReflectionWrapper amqSsl =
                    new ReflectionWrapper("org.apache.activemq.ActiveMQSslConnectionFactory", null);

            setProperty(amqSsl, properties, "setKeyStore", NjamsSettings.PROPERTY_JMS_KEYSTORE);
            setProperty(amqSsl, properties, "setKeyStorePassword", NjamsSettings.PROPERTY_JMS_KEYSTOREPASSWORD);
            setProperty(amqSsl, properties, "setKeyStoreType", NjamsSettings.PROPERTY_JMS_KEYSTORETYPE);
            setProperty(amqSsl, properties, "setTrustStore", NjamsSettings.PROPERTY_JMS_TRUSTSTORE);
            setProperty(amqSsl, properties, "setTrustStorePassword", NjamsSettings.PROPERTY_JMS_TRUSTSTOREPASSWORD);
            setProperty(amqSsl, properties, "setTrustStoreType", NjamsSettings.PROPERTY_JMS_TRUSTSTORETYPE);
            setProperty(amqSsl, properties, "setPassword", NjamsSettings.PROPERTY_JMS_PASSWORD);
            setProperty(amqSsl, properties, "setUserName", NjamsSettings.PROPERTY_JMS_USERNAME);
            setProperty(amqSsl, properties, "setBrokerURL", NjamsSettings.PROPERTY_JMS_PROVIDER_URL);
            factory = (ConnectionFactory) amqSsl.getTarget();

            LOG.debug("Created ActiveMQSslConnectionFactory");
        } catch (Exception e) {
            LOG.error("ActiveMQSslConnectionFactory could not be created", e);
            final JMSException jmsEx = new JMSException("Failed to setup ActiveMQSslConnectionFactory");
            jmsEx.initCause(e);
            throw jmsEx;
        }
    }

    private void setProperty(ReflectionWrapper target, Properties source, String setter, String property) {
        if (!source.containsKey(property)) {
            return;
        }
        try {
            target.setObject(setter, source.getProperty(property));
        } catch (Exception e) {
            LOG.error("Failed to invoke {}(String) on {} with property {}={}", setter, target.getClass(),
                    property, source.getProperty(property), e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + NAME + "]";
    }
}
