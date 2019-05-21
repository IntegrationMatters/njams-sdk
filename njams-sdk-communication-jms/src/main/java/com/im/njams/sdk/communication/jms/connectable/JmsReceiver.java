/*
 */
package com.im.njams.sdk.communication.jms.connectable;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication.connectable.receiver.AbstractReceiver;
import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.communication.jms.JmsConstants;
import com.im.njams.sdk.communication.jms.connector.JmsReceiverConnector;
import com.im.njams.sdk.communication.jms.connector.JmsConnector;
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
     * @param message the destination where the response will be sent to and the
     * jmsCorrelationId are safed in here.
     * @param instruction the instruction that holds the response.
     */
    private void reply(Message message, Instruction instruction) {
        MessageProducer replyProducer = null;
        try {
            replyProducer = ((JmsConnector)connector).getSession().createProducer(message.getJMSReplyTo());
            String response = util.writeJson(instruction);
            final TextMessage responseMessage = ((JmsConnector)connector).getSession().createTextMessage();
            responseMessage.setText(response);
            final String jmsCorrelationID = message.getJMSCorrelationID();
            if (jmsCorrelationID != null && !jmsCorrelationID.isEmpty()) {
                responseMessage.setJMSCorrelationID(jmsCorrelationID);
            }
            replyProducer.send(responseMessage);
            LOG.debug("Response: {}", response);
        } catch (Exception e) {
            LOG.error("Error while sending reply for {}", ((JmsReceiverConnector)connector).getTopicName(), e);
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

    @Override
    protected Connector initialize(Properties properties) {
        if(njams == null){
            LOG.error("setNjams must be called before initialize!");
        }
        return connector = new JmsReceiverConnector(properties, this.getName() + Connector.RECEIVER_NAME_ENDING, this, njams);
    }
}
