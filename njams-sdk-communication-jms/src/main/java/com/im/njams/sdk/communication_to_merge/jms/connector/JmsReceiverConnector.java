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

package com.im.njams.sdk.communication_to_merge.jms.connector;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication_to_merge.jms.JmsConstants;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Topic;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * @author krautenberg@integrationmatters.ocm
 * @version 4.1.0
 */
public class JmsReceiverConnector extends JmsConnector {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsReceiverConnector.class);

    protected String topicName;
    private String messageSelector;
    private MessageListener messageListener;

    private Njams njams;

    protected MessageConsumer consumer;

    public JmsReceiverConnector(Properties properties, String name, MessageListener messageListener, Njams njams) {
        super(properties, name);
        if (properties.containsKey(JmsConstants.COMMANDS_DESTINATION)) {
            topicName = properties.getProperty(JmsConstants.COMMANDS_DESTINATION);
        } else {
            topicName = properties.getProperty(JmsConstants.DESTINATION) + ".commands";
        }
        this.njams = njams;
        this.messageSelector = createMessageSelector();
        this.messageListener = messageListener;
    }

    /**
     * This method creates a String that is used as a message selector.
     *
     * @return the message selector String.
     */
    private String createMessageSelector() {
        Path fullPath = new Path(njams.getClientPath().toString());
        Path path = null;
        StringBuilder selector = new StringBuilder();
        for (String part : fullPath.getParts()) {
            if (path == null) {
                path = new Path(part);
            } else {
                path = path.add(part);
                selector.append(" OR ");
            }
            selector.append("NJAMS_RECEIVER = '").append(path.toString()).append("'");
        }
        return selector.toString();
    }

    @Override
    protected void extConnect() throws JMSException, NamingException {
        Topic topic = this.getOrCreateTopic();
        LOG.trace("The Topic was created successfully.");

        this.consumer = this.createConsumer(topic);
        LOG.trace("The MessageConsumer was created successfully.");
    }

    @Override
    protected List<Exception> extClose() {
        List<Exception> exceptions = new ArrayList<>();
        if (consumer != null) {
            try {
                consumer.close();

            } catch (JMSException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close consumer correctly", ex));
            } finally {
                consumer = null;
            }
        }
        return exceptions;
    }

    @Override
    protected Set<String> extLibrariesToCheck() {
        Set<String> libs = new HashSet<>();
        libs.add("javax.jms.JMSException");
        libs.add("javax.jms.MessageConsumer");
        libs.add("javax.jms.MessageListener");
        libs.add("javax.jms.Topic");
        libs.add("javax.naming.NameNotFoundException");
        libs.add("javax.naming.NamingException");
        return libs;
    }

    /**
     * This method creates or gets a existing topic that has been specified in
     * the properties beforehand.
     *
     * @return the topic that was created or has been found before.
     * @throws NamingException is thrown if something with the name is wrong.
     * @throws JMSException    is thrown if the topic can't be created.
     */
    protected Topic getOrCreateTopic() throws NamingException, JMSException {
        Topic topic;
        try {
            topic = (Topic) context.lookup(topicName);
            LOG.info("Topic {} has been found.", topicName);
        } catch (NameNotFoundException e) {
            LOG.warn("Topic {} hasn't been found. Creating temporary topic...", topicName);
            topic = session.createTopic(topicName);
        }
        return topic;
    }

    /**
     * This method creates a MessageConsumer out of the provided session for the
     * Topic topic, and listens only to the messages that are filtered by the messageSelector.
     *
     * @param topic the topic to listen to
     * @return the MessageConsumer if it can be created. It listens on the given
     * topic for messages that match the
     * messageSelector. If a message is found on the topic,
     * messageListener.onMessage(Message msg) will be invoked.
     * @throws JMSException is thrown if the MessageConsumer can' be created.
     */
    protected MessageConsumer createConsumer(Topic topic) throws JMSException {
        MessageConsumer cons = session.createConsumer(topic, messageSelector);
        cons.setMessageListener(messageListener);
        return cons;
    }

    public String getTopicName() {
        return topicName;
    }
}
