/*
 */

package com.im.njams.sdk.communication.jms.connector;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.jms.JmsConstants;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 *
 *
 * @author krautenberg@integrationmatters.ocm
 * @version 4.1.0
 */
public class JmsSenderConnector extends JmsConnector {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsSenderConnector.class);

    private String destinationName;

    private MessageProducer producer;

    public JmsSenderConnector(Properties properties, String name) {
        super(properties, name);
        destinationName = properties.getProperty(JmsConstants.DESTINATION) + ".event";
    }

    @Override
    protected void extConnect() throws Exception {
        Destination destination = this.getOrCreateDestination(destinationName);
        LOG.debug("The Queue was created successfully.");

        this.producer = this.createProducer(destination);
        LOG.debug("The MessageProducer was created successfully.");
    }

    /**
     * This method creates or gets a existing queue that has been specified in
     * the properties beforehand.
     *
     * @param destinationName the name of the Queue
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

    @Override
    protected List<Exception> extClose() {
        List<Exception> exceptions = new ArrayList<>();
        if (producer != null) {
            try {
                producer.close();

            } catch (JMSException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close producer correctly", ex));
            } finally {
                producer = null;
            }
        }
        return exceptions;
    }

    @Override
    protected Set<String> extLibrariesToCheck() {
        return null;
    }

    public MessageProducer getProducer() {
        return producer;
    }
}
