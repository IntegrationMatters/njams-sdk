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

import static com.im.njams.sdk.NjamsSettings.PROPERTY_JMS_PASSWORD;
import static com.im.njams.sdk.NjamsSettings.PROPERTY_JMS_PROVIDER_URL;
import static com.im.njams.sdk.NjamsSettings.PROPERTY_JMS_USERNAME;
import static com.im.njams.sdk.utils.ReflectionWrapper.argsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.utils.ReflectionWrapper;

/**
 * IMPORTANT: This is loaded and initialized via SPI. Make sure that an instance can be created even when
 * required libraries are missing!
 *
 */
public class AzureServiceBusJmsFactory implements JmsFactory {

    private static final Logger LOG = LoggerFactory.getLogger(AzureServiceBusJmsFactory.class);
    /**
     * This implementation's identifier for SPI lookup.
     */
    public static final String NAME = "AzureServiceBusPremium";
    private ConnectionFactory factory = null;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(Properties properties) throws JMSException {
        if (factory != null) {
            // assuming that config does not change
            return;
        }
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            final URI uri = new URI(properties.getProperty(PROPERTY_JMS_PROVIDER_URL));
            final ReflectionWrapper connectStringBuilder =
                    new ReflectionWrapper("com.microsoft.azure.servicebus.jms.ConnectionStringBuilder", cl,
                            argsBuilder()
                                    .addObject(uri)
                                    .addNull(String.class)
                                    .addObject(properties.getProperty(PROPERTY_JMS_USERNAME))
                                    .addObject(properties.getProperty(PROPERTY_JMS_PASSWORD)));

            final ReflectionWrapper connectSettings =
                    new ReflectionWrapper("com.microsoft.azure.servicebus.jms.ServiceBusJmsConnectionFactorySettings",
                            cl, null)
                                    .setPrimitive("setShouldReconnect", false)
                                    .setPrimitive("setConnectionIdleTimeoutMS", 120_000l)
                                    .setPrimitive("setMaxReconnectAttempts", 1)
                                    .setPrimitive("setStartupMaxReconnectAttempts", 1);

            factory = (ConnectionFactory) new ReflectionWrapper(
                    "com.microsoft.azure.servicebus.jms.ServiceBusJmsConnectionFactory", cl,
                    argsBuilder()
                            .addObject(connectStringBuilder.getTarget())
                            .addObject(connectSettings.getTarget())).getTarget();

            LOG.debug("Created connection factory for URI: {}", uri);
        } catch (ReflectiveOperationException e) {
            throw buildException("Failed to load Azure Service Bus Premium JMS implemenation", e);
        } catch (URISyntaxException e) {
            throw buildException("Failed to create URI from: " + properties.getProperty(PROPERTY_JMS_PROVIDER_URL), e);
        }

    }

    private static JMSException buildException(String message, Throwable cause) {
        final JMSException jmsEx = new JMSException(message);
        jmsEx.initCause(cause);
        return jmsEx;
    }

    @Override
    public ConnectionFactory createConnectionFactory() {
        if (factory == null) {
            throw new IllegalStateException("Not initialized.");
        }
        return factory;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + NAME + "]";
    }
}
