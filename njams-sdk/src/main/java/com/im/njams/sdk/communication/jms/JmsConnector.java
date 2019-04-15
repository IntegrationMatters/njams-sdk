/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.communication.jms;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.connection.NjamsConnection;
import com.im.njams.sdk.settings.PropertyUtil;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.MessageProducer;
import javax.jms.MessageListener;
import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.Destination;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;

import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.util.*;

/**
 * JMS implementation for Receiver and Sender
 *
 * @author krautenberg@integrationmatters.ocm
 * @version 4.0.6
 */
public class JmsConnector implements ExceptionListener, AutoCloseable {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsConnector.class);

    private Properties properties;
    private NjamsConnection njamsConnection;

    private InitialContext context;
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private MessageProducer producer;

    /**
     * This constructor creates a JmsConnector for the given njamsConnection and properties.
     *
     * @param njamsConnection the njamsConnection that will be used to connect
     * @param properties the properties for the connection
     */
    public JmsConnector(NjamsConnection njamsConnection, Properties properties) {
        this.njamsConnection = njamsConnection;
        this.properties = properties;
    }

    public void connectReceiver(String topicName, String messageSelector, MessageListener messageListener) {
        try {
            this.connect();
            Topic topic = this.getOrCreateTopic(topicName);
            LOG.debug("The Topic was created successfully.");

            this.consumer = this.createConsumer(topic, messageSelector, messageListener);
            LOG.debug("The MessageConsumer was created successfully.");

        } catch (Exception e) {
            close();
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }

    public void connectSender(String destinationName) {
        try {
            this.connect();
            Destination destination = this.getOrCreateDestination(destinationName);
            LOG.debug("The Queue was created successfully.");

            this.producer = this.createProducer(destination);
            LOG.debug("The MessageProducer was created successfully.");
        } catch (Exception e) {
            close();
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }

    }

    private void connect() throws NamingException, JMSException {
        if (njamsConnection.isConnected()) {
            LOG.warn("Can't connect while being connected.");
            return;
        }
        context = this.getInitialContext();
        LOG.debug("The InitialContext was created successfully.");

        ConnectionFactory factory = this.getConnectionFactory();
        LOG.debug("The ConnectionFactory was created successfully.");

        this.connection = this.createConnection(factory);
        LOG.debug("The connection was created successfully.");

        this.session = this.createSession();
        LOG.debug("The Session was created successfully.");

        this.startConnection();
        LOG.debug("The connection was started successfully.");
    }

    /**
     * This method creates a new InitialContext out of the properties that are
     * given as parameter.
     *
     * @return the InitialContext that has been created.
     * @throws NamingException is thrown if something with the name is wrong.
     */
    private InitialContext getInitialContext() throws NamingException {
        return new InitialContext(PropertyUtil.filterAndCut(properties, JmsConstants.PROPERTY_PREFIX + "."));
    }

    /**
     * This method gets a ConnectionFactory out of the properties value for
     * JmsConstants.CONNECTION_FACTORY and the context that looks up for the
     * factory.
     *
     * @return the ConnectionFactory, if found.
     * @throws NamingException is thrown if something with the name is wrong.
     */
    private ConnectionFactory getConnectionFactory() throws NamingException {
        return (ConnectionFactory) context.lookup(properties.getProperty(JmsConstants.CONNECTION_FACTORY));
    }

    /**
     * This method creates a connection out of the properties and the factory.
     * It established a secure connection with JmsConstants.USERNAME and
     * JmsConstants.PASSWORD, or if they are not provided, creates a connection
     * that uses the default username and password.
     *
     * @param factory the factory where the connection will be created from.
     * @return the connection if it can be created.
     * @throws JMSException is thrown if something is wrong with the username or
     *                      password.
     */
    private Connection createConnection(ConnectionFactory factory) throws JMSException {
        Connection con;
        if (properties.containsKey(JmsConstants.USERNAME) && properties.containsKey(JmsConstants.PASSWORD)) {
            con = factory.createConnection(properties.getProperty(JmsConstants.USERNAME),
                    properties.getProperty(JmsConstants.PASSWORD));
        } else {
            con = factory.createConnection();
        }
        return con;
    }

    /**
     * This method creates a session to the given connection. The transacted
     * boolean has been set to false and the acknowledgeMode is
     * JMSContext.CLIENT_ACKNOWLEDGE for the created session.
     *
     * @return the session if it can be created
     * @throws JMSException is thrown if something failed.
     */
    private Session createSession() throws JMSException {
        return connection.createSession(false, JMSContext.CLIENT_ACKNOWLEDGE);
    }

    /**
     * This method starts the provided connection and sets this object as
     * exceptionListener.
     *
     * @throws JMSException is thrown if either setting the exceptionListener
     *                      didn't work or the connection didn't start.
     */
    private void startConnection() throws JMSException {
        connection.setExceptionListener(this);
        connection.start();
    }

    /**
     * This method creates or gets a existing queue that has been specified in
     * the properties beforehand.
     *
     * @param destinationName the name of the Queue
     *
     * @return the destination that was created or has been found before.
     * @throws NamingException is thrown if something with the name is wrong.
     * @throws JMSException    is thrown if the topic can't be created.
     */
    private Destination getOrCreateDestination(String destinationName) throws NamingException, JMSException {
        Destination destination = null;
        try {
            destination = (Destination) context.lookup(destinationName);
            LOG.info("Queue {} has been found.", destinationName);
        } catch (NameNotFoundException e) {
            LOG.warn("Queue {} hasn't been found. Create Queue...", destinationName);
            destination = session.createQueue(destinationName);
        }
        return destination;
    }

    /**
     * This method creates a MessageProducer out of the provided session for the event queue.
     *
     * @param destination the destination to send to.
     * @return
     * @throws JMSException
     */
    private MessageProducer createProducer(Destination destination) throws JMSException {
        return session.createProducer(destination);
    }

    /**
     * This method creates or gets a existing topic that has been specified in
     * the properties beforehand.
     *
     * @param topicName the name of the Topic
     *
     * @return the topic that was created or has been found before.
     * @throws NamingException is thrown if something with the name is wrong.
     * @throws JMSException    is thrown if the topic can't be created.
     */
    private Topic getOrCreateTopic(String topicName) throws NamingException, JMSException {
        Topic topic;
        try {
            topic = (Topic) context.lookup(topicName);
            LOG.info("Topic {} has been found.", topicName);
        } catch (NameNotFoundException e) {
            LOG.warn("Topic {} hasn't been found. Create Topic...", topicName);
            topic = session.createTopic(topicName);
        }
        return topic;
    }

    /**
     * This method creates a MessageConsumer out of the provided session for the
     * Topic topic, and listens only to the messages that are filtered by the messageSelector.
     *
     * @param topic the topic to listen to
     * @param messageSelector the messages the messageListener only listens to
     * @param messageListener the messageListener that listens on the topic
     * @return the MessageConsumer if it can be created. It listens on the given
     * topic for messages that match the
     * messageSelector. If a message is found on the topic,
     * messageListener.onMessage(Message msg) will be invoked.
     * @throws JMSException is thrown if the MessageConsumer can' be created.
     */
    private MessageConsumer createConsumer(Topic topic, String messageSelector, MessageListener messageListener) throws JMSException {
        MessageConsumer cons = session.createConsumer(topic, messageSelector);
        cons.setMessageListener(messageListener);
        return cons;
    }

    /**
     * This method logs all JMS Exceptions and tries to reconnect the
     * connection.
     *
     * @param exception The jmsException to be logged.
     */
    @Override
    public void onException(JMSException exception) {
        njamsConnection.onException(new NjamsSdkRuntimeException("Transport error", exception));
    }

    /**
     * This method closes all resources.
     */
    @Override
    public void close() {
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
        if (producer != null) {
            try {
                producer.close();

            } catch (JMSException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close producer correctly", ex));
            } finally {
                producer = null;
            }
        }
        if (session != null) {
            try {
                session.close();
            } catch (JMSException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close session correctly", ex));
            } finally {
                session = null;
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close connection correctly", ex));
            } finally {
                connection = null;
            }
        }
        if (context != null) {
            try {
                context.close();
            } catch (NamingException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close initial context correctly", ex));
            } finally {
                context = null;
            }
        }
        if (!exceptions.isEmpty()) {
            exceptions.forEach(exception -> LOG.error(exception.getMessage()));
            throw new NjamsSdkRuntimeException("Unable to close jms connector");
        } else {
            LOG.info("JmsConnector has been closed.");
        }
    }

    public Session getSession() {
        return session;
    }

    public MessageProducer getProducer() {
        return producer;
    }

    /**
     * This method gets all libraries that need to be checked.
     *
     * @return an array of Strings of fully qualified class names.
     */
    public Set<String> librariesToCheck() {
        Set<String> libs = new HashSet<>();
        libs.add("javax.jms.Connection");
        libs.add("javax.jms.ExceptionListener");
        libs.add("javax.jms.MessageConsumer");
        libs.add("javax.jms.Session");
        libs.add("javax.jms.MessageProducer");
        libs.add("javax.jms.MessageListener");
        libs.add("javax.jms.JMSException");
        libs.add("javax.jms.Topic");
        libs.add("javax.jms.Destination");
        libs.add("javax.jms.ConnectionFactory");
        libs.add("javax.jms.JMSContext");
        libs.add("javax.naming.InitialContext");
        libs.add("javax.naming.NameNotFoundException");
        libs.add("javax.naming.NamingException");
        return libs;
    }
}

