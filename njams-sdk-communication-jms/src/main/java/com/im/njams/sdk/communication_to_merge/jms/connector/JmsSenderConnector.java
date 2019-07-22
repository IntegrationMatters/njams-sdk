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

package com.im.njams.sdk.communication_to_merge.jms.connector;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication_to_merge.jms.JmsConstants;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.HashSet;
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

    protected MessageProducer producer;

    public JmsSenderConnector(Properties properties, String name) {
        super(properties, name);
        destinationName = properties.getProperty(JmsConstants.DESTINATION) + ".event";
    }

    @Override
    protected void extConnect() throws Exception {
        Destination destination = this.getOrCreateDestination(destinationName);
        LOG.trace("The Queue was created successfully.");

        this.producer = this.createProducer(destination);
        LOG.trace("The MessageProducer was created successfully.");
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
    protected Destination getOrCreateDestination(String destinationName) throws NamingException, JMSException {
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
    protected MessageProducer createProducer(Destination destination) throws JMSException {
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
        Set<String> libs = new HashSet<>();
        libs.add("javax.jms.Destination");
        libs.add("javax.jms.JMSException");
        libs.add("javax.jms.MessageProducer");
        libs.add("javax.naming.NameNotFoundException");
        libs.add("javax.naming.NamingException");
        return libs;
    }

    public MessageProducer getProducer() {
        return producer;
    }
}
