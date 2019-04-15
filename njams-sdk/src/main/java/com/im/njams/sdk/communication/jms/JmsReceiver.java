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

import java.util.*;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.jms.MessageProducer;

import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.Path;

import com.im.njams.sdk.communication.AbstractReceiver;

/**
 * JMS implementation for a Receiver.
 *
 * @author pnientiedt, krautenberg@integrationmatters.ocm
 * @version 4.0.6
 */
public class JmsReceiver extends AbstractReceiver implements MessageListener {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsReceiver.class);

    private JmsConnector jmsConnector;

    private String topicName;
    private ObjectMapper mapper;
    private String messageSelector;

    /**
     * Initializes this Receiver via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#CONNECTION_FACTORY}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#USERNAME}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#PASSWORD}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#DESTINATION}
     * <li>...
     * </ul>
     * For more look in the github FAQ of this project.
     *
     * @param properties the properties needed to init
     */
    @Override
    protected void initialize(Properties properties) {
        mapper = JsonSerializerFactory.getDefaultMapper();
        if (properties.containsKey(JmsConstants.COMMANDS_DESTINATION)) {
            topicName = properties.getProperty(JmsConstants.COMMANDS_DESTINATION);
        } else {
            topicName = properties.getProperty(JmsConstants.DESTINATION) + ".commands";
        }
        this.messageSelector = this.createMessageSelector();
        this.jmsConnector = new JmsConnector(njamsConnection, properties);
    }

    @Override
    public void connect() {
        jmsConnector.connectReceiver(topicName, messageSelector, this);
    }

    /**
     * This method stops the Jms Receiver by closing all its resources, if its
     * status is CONNECTED.
     */
    @Override
    public void close() {
        jmsConnector.close();
    }

    /**
     * Returns the name for this Receiver. (JMS)
     *
     * @return the name of this Receiver. (JMS)
     */
    @Override
    public String getName() {
        return JmsConstants.COMMUNICATION_NAME;
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

    /**
     * This method is the MessageListener implementation. It receives JMS
     * Messages automatically.
     *
     * @param msg the newly arrived JMS message.
     */
    @Override
    public void onMessage(Message msg) {
        try {
            String njamsContent = msg.getStringProperty("NJAMS_CONTENT");
            if (!njamsContent.equalsIgnoreCase("json")) {
                LOG.debug("Received non json instruction -> ignore");
                return;
            }
            Instruction instruction = getInstruction(msg);
            if (instruction != null) {
                super.onInstruction(instruction);
                this.reply(msg, instruction);
            }
        } catch (Exception e) {
            LOG.error("Error in onMessage", e);
        }
    }

    /**
     * This method tries to extract the Instruction out of the provided message.
     * It maps the Json string to an Instruction object.
     *
     * @param message the Json Message
     * @return the Instruction object that was extracted or null, if no valid
     * instruction was found or it could be parsed to an instruction object.
     */
    private Instruction getInstruction(Message message) {
        try {
            String instructionString = ((TextMessage) message).getText();
            Instruction instruction = mapper.readValue(instructionString, Instruction.class
            );
            if (instruction.getRequest() != null) {
                return instruction;
            }
        } catch (Exception e) {
            LOG.error("Error deserializing Instruction", e);
        }
        LOG.warn("MSG is not a valid Instruction");
        return null;
    }

    /**
     * This method tries to reply the instructions response back to the sender.
     * Send a message to the sender that is metioned in the message. If a
     * JmsCorrelationId is set in the message, it will be forwarded aswell.
     *
     * @param message the destination where the response will be sent to and the
     * jmsCorrelationId are safed in here.
     * @param instruction the instruction that holds the response.
     */
    private void reply(Message message, Instruction instruction) {
        MessageProducer replyProducer = null;
        try {
            replyProducer = jmsConnector.getSession().createProducer(message.getJMSReplyTo());
            String response = mapper.writeValueAsString(instruction);
            final TextMessage responseMessage = jmsConnector.getSession().createTextMessage();
            responseMessage.setText(response);
            final String jmsCorrelationID = message.getJMSCorrelationID();
            if (jmsCorrelationID != null && !jmsCorrelationID.isEmpty()) {
                responseMessage.setJMSCorrelationID(jmsCorrelationID);
            }
            replyProducer.send(responseMessage);
            LOG.debug("Response: {}", response);
        } catch (Exception e) {
            LOG.error("Error while sending reply for {}", topicName, e);
        } finally {
            if (replyProducer != null) {
                try {
                    replyProducer.close();
                } catch (JMSException ex) {
                    LOG.error("Error while closing the {} receiver's reply producer.", this.getName());
                }
            }
        }
    }

    /**
     * This method gets all libraries that need to be checked.
     *
     * @return an array of Strings of fully qualified class names.
     */
    @Override
    public String[] librariesToCheck() {
        Set<String> libs = new HashSet<>();
        libs.add("javax.jms.JMSException");
        libs.add("javax.jms.Message");
        libs.add("javax.jms.MessageListener");
        libs.add("javax.jms.TextMessage");
        libs.add("javax.jms.MessageProducer");
        libs.addAll(jmsConnector.librariesToCheck());
        String[] toRet = new String[libs.size()];
        libs.toArray(toRet);
        return toRet;
    }
}
