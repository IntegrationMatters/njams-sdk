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
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.ServiceLoaderSupport;
import com.im.njams.sdk.utils.StringUtils;

/**
 * A named factory loaded via SPI for creating JMS related instances.<br>
 * Use {@link #find(Properties)} for getting an instance.
 */
public interface JmsFactory extends AutoCloseable {
    /** Logger for static and default implementations */
    static final Logger LOG = LoggerFactory.getLogger(JmsFactory.class);

    /**
     * Tries to find a {@link JmsFactory} from SPI according to the given settings and using
     * the following steps. Matches on {@link JmsFactory#getName()} (ignoring case),
     * simple class name or full-qualified class name.
     * <ol>
     * <li>Use key {@value NjamsSettings#PROPERTY_JMS_JMSFACTORY} for SPI lookup</li>
     * <li>Use key {@value NjamsSettings#PROPERTY_JMS_CONNECTION_FACTORY} for SPI lookup</li>
     * <li>Use {@link JndiJmsFactory} as default.</li>
     * </ol>
     *
     * In the traditional scenario where only {@value NjamsSettings#PROPERTY_JMS_CONNECTION_FACTORY} is configured
     * and is expected to be used for JNDI lookup for a {@link ConnectionFactory} instance, no additional
     * configuration is necessary since the lookup done here will not find any matching {@link JmsFactory}
     * and will use {@link JndiJmsFactory} as default, which works as before.
     * <br><br>
     * When needing a non-JNDI implementation like {@link ActiveMqSslJmsFactory} or {@link AzureServiceBusJmsFactory}
     * it's sufficient to specify the according SPI name in {@value NjamsSettings#PROPERTY_JMS_CONNECTION_FACTORY}.
     * <br><br>
     * The new {@value NjamsSettings#PROPERTY_JMS_JMSFACTORY} key is only needed, when implementing a different JNDI
     * {@link JmsFactory} that still needs the common {@value NjamsSettings#PROPERTY_JMS_CONNECTION_FACTORY} key
     * for looking the {@link ConnectionFactory} in the JNDI context as second step.
     *
     * @param settings Settings specifying what provider to use. Obtained from nJAMS' {@link Settings}.
     * @return Found instance or {@link JndiJmsFactory} as a default. The returned instance
     * is {@link JmsFactory#init(Properties) initialized} with the given settings.
     */
    public static JmsFactory find(Properties settings) {
        return find(settings, true);
    }

    /**
     * This is a variant of {@link #find(Properties)} that allows to get an <b>un</b>initialized instance of
     * {@link JmsFactory}.<br>
     * This is only needed in rare cases. Most common cases should use {@link #find(Properties)} instead.
     *
     * @param settings Settings specifying what provider to use. Obtained from nJAMS' {@link Settings}.
     * @param init Whether the returned instance shall be initialize by calling {@link JmsFactory#init(Properties)}.
     * If <code>false</code>, the returned instance still needs to be initialized before using.
     * @return Found instance or {@link JndiJmsFactory} as a default. The returned instance
     * is only {@link JmsFactory#init(Properties) initialized} with the given settings, if the <code>init</code>
     * argument is <code>true</code>.
     * @see #find(Properties)
     */
    public static JmsFactory find(Properties settings, boolean init) {
        String toFind = settings.getProperty(NjamsSettings.PROPERTY_JMS_JMSFACTORY);
        JmsFactory found = find(toFind);
        if (found == null) {
            toFind = settings.getProperty(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY);
            found = find(toFind);
        }

        if (found == null) {
            // default/fallback
            found = new JndiJmsFactory();
            LOG.debug("Using default JMS factory implementation: {}", found);
        }
        if (init) {
            initFactory(found, settings);
        }
        return found;
    }

    private static JmsFactory find(final String toFind) {
        if (StringUtils.isBlank(toFind)) {
            return null;
        }
        final ServiceLoaderSupport<JmsFactory> spiLoader = new ServiceLoaderSupport<>(JmsFactory.class);
        final JmsFactory factory =
                spiLoader.find(j -> toFind.equalsIgnoreCase(j.getName()) || j.getClass().getSimpleName().equals(toFind)
                        || j.getClass().getName().equals(toFind));
        if (factory != null) {
            LOG.debug("Found JMS factory implementation {} for key {}", factory, toFind);
            return factory;
        }
        return null;
    }

    private static void initFactory(JmsFactory factory, Properties settings) {
        try {
            factory.init(settings);
        } catch (Throwable e) {
            // could be an Error, for example when required libs are missing
            LOG.error("Could not initialize {}", factory, e);
            throw new IllegalStateException("Could not initialize: " + factory, e);
        }
    }

    /**
     * Has to return a unique implementation name that is used for SPI lookup.
     * @return Unique implementation name.
     */
    public String getName();

    /**
     * Is called to initialize this factory. After calling this, the factory is usable until {@link #close()} is
     * called.<br>
     * Implementations have to handle that this method could be invoked multiple times.
     * @param settings The {@link Njams} instance's settings that uses this factory.
     * @throws JMSException If creating a JMS object failed
     * @throws NamingException If a JNDI related error occurred
     */
    public void init(Properties settings) throws JMSException, NamingException;

    /**
     * Is called for obtaining a {@link ConnectionFactory} instance. Once created, a factory should be reusable.
     * So, it should be ok the return always the same instance.
     * @return Has to return a {@link ConnectionFactory} instance.
     * @throws JMSException If creating a JMS object failed
     * @throws NamingException If a JNDI related error occurred
     */
    public ConnectionFactory createConnectionFactory() throws JMSException, NamingException;

    /**
     * Creates a topic for the given name. The default simply calls {@link Session#createTopic(String)}.
     * @param session The session to be used for creating the topic.
     * @param topicName The name of the topic to create.
     * @return The created topic.
     * @throws JMSException If creating a JMS object failed
     * @throws NamingException If a JNDI related error occurred
     */
    public default Topic createTopic(Session session, String topicName) throws JMSException, NamingException {
        return session.createTopic(topicName);
    }

    /**
     * Creates a queue for the given name. The default simply calls {@link Session#createQueue(String)}.
     * @param session The session to be used for creating the queue.
     * @param queueName The name of the queue to create.
     * @return The created queue.
     * @throws JMSException If creating a JMS object failed
     * @throws NamingException If a JNDI related error occurred
     */
    public default Queue createQueue(Session session, String queueName) throws JMSException, NamingException {
        return session.createQueue(queueName);
    }

    /**
     * Is called when the factory is no longer used. Before using the same instance again, {@link #init(Properties)}
     * has to be called again.<br>
     * The default does nothing.
     */
    @Override
    public default void close() {
        // nothing
    }
}
