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
package com.im.njams.sdk.communication_to_merge.jms.connectable;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_to_merge.connectable.receiver.AbstractReceiver;
import com.im.njams.sdk.communication_to_merge.connector.Connector;
import com.im.njams.sdk.communication_to_merge.jms.JmsConstants;
import com.im.njams.sdk.communication_to_merge.jms.connector.JmsReceiverConnector;
import com.im.njams.sdk.communication_to_merge.jms.connector.JmsConnector;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.util.Properties;

/**
 * JMS implementation for a Receiver.
 *
 * @author krautenberg@integrationmatters.ocm
 * @version 4.1.0
 */
public class JmsReceiver extends AbstractReceiver implements MessageListener {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsReceiver.class);

    protected static final String NJAMS_CONTENT = "NJAMS_CONTENT";

    protected static final String MESSAGE_FORMAT_JSON = "json";

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
     * This method is the MessageListener implementation. It receives JMS
     * Messages automatically.
     *
     * @param msg the newly arrived JMS message.
     */
    @Override
    public void onMessage(Message msg) {
        try {
            String njamsContent = msg.getStringProperty(NJAMS_CONTENT);
            if (!njamsContent.equalsIgnoreCase(MESSAGE_FORMAT_JSON)) {
                LOG.debug("Received non json instruction -> ignore");
                return;
            }
            Instruction instruction = getInstruction(msg);
            if (instruction != null) {
                onInstruction(instruction);
                reply(msg, instruction);
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
    protected Instruction getInstruction(Message message) {
        try {
            String instructionString = ((TextMessage) message).getText();
            Instruction instruction = util.readJson(instructionString, Instruction.class);
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
     * @param message     the destination where the response will be sent to and the
     *                    jmsCorrelationId are safed in here.
     * @param instruction the instruction that holds the response.
     */
    protected void reply(Message message, Instruction instruction) {
        MessageProducer replyProducer = null;
        try {
            //createReplier
            replyProducer = ((JmsConnector) connector).getSession().createProducer(message.getJMSReplyTo());
            //serializeResponse
            String response = util.writeJson(instruction);
            //wrapResponseInTextMessage
            final TextMessage responseMessage = ((JmsConnector) connector).getSession().createTextMessage();
            responseMessage.setText(response);
            //addCorrelationIdIfPresent
            final String jmsCorrelationID = message.getJMSCorrelationID();
            if (jmsCorrelationID != null && !jmsCorrelationID.isEmpty()) {
                responseMessage.setJMSCorrelationID(jmsCorrelationID);
            }
            //sendTextMessage
            replyProducer.send(responseMessage);
            LOG.debug("Response: {}", response);
        } catch (Exception e) {
            LOG.error("Error while sending reply for {}", ((JmsReceiverConnector) connector).getTopicName(), e);
        } finally {
            //closeReplier
            if (replyProducer != null) {
                try {
                    replyProducer.close();
                } catch (JMSException ex) {
                    LOG.error("Error while closing the {} receiver's reply producer.", this.getName());
                }
            }
        }
    }

    @Override
    protected Connector initialize(Properties properties) {
        if (njams == null) {
            LOG.error("njams must be set before initialize!");
        }
        return connector = new JmsReceiverConnector(properties, this.getName() + Connector.RECEIVER_NAME_ENDING, this,
                njams);
    }

    @Override
    public void onInstruction(Instruction instruction) {
        super.onInstruction(instruction);
    }
}
