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

import static com.im.njams.sdk.NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY;

import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.utils.PropertyUtil;
import com.im.njams.sdk.utils.StringUtils;

/**
 * This factory implements the traditional way of creating JMS object via JNDI lookups.
 */
public class JndiJmsFactory implements JmsFactory {

    private static final Logger LOG = LoggerFactory.getLogger(JndiJmsFactory.class);

    /**
     * This implementation identifier for SPI lookup.
     */
    public static final String NAME = "JNDI";

    protected ConnectionFactory factory = null;
    private Context context = null;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(Properties settings) throws NamingException, JMSException {
        if (context != null) {
            // assuming that config does not change
            return;
        }
        context = new InitialContext(PropertyUtil.filterAndCut(settings, NjamsSettings.PROPERTY_JMS_PREFIX));
        initFactory(settings);
    }

    protected void initFactory(Properties settings) throws NamingException, JMSException {
        final String connectFactory = settings.getProperty(PROPERTY_JMS_CONNECTION_FACTORY);
        if (StringUtils.isNotBlank(connectFactory)) {
            factory = (ConnectionFactory) context.lookup(connectFactory);
        }
        LOG.debug("Got connection factory {} from JNDI lookup.", factory);
    }

    @Override
    public ConnectionFactory createConnectionFactory() {
        if (factory == null) {
            throw new IllegalStateException("Not initialized.");
        }

        return factory;
    }

    @Override
    public Topic createTopic(Session session, String topicName) throws JMSException, NamingException {
        if (context != null) {
            try {
                final Topic topic = (Topic) context.lookup(topicName);
                LOG.debug("Topic {} has been found via JNDI.", topicName);
                return topic;
            } catch (NameNotFoundException | ClassCastException e) {
                LOG.debug("Topic {} hasn't been found via JNDI.", topicName);
            }
        }
        return JmsFactory.super.createTopic(session, topicName);
    }

    @Override
    public Queue createQueue(Session session, String queueName) throws JMSException, NamingException {
        if (context != null) {
            try {
                final Queue queue = (Queue) context.lookup(queueName);
                LOG.debug("Queue {} has been found via JNDI.", queueName);
                return queue;
            } catch (NameNotFoundException | ClassCastException e) {
                LOG.debug("Queue {} hasn't been found via JNDI.", queueName);
            }
        }
        return JmsFactory.super.createQueue(session, queueName);
    }

    @Override
    public void close() {
        if (context != null) {
            LOG.debug("Closing JNDI context.");
            factory = null;
            try {
                context.close();
            } catch (NamingException e) {
                LOG.debug("Failed to close JNDI context", e);
            }
            context = null;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + NAME + "]";
    }
}
