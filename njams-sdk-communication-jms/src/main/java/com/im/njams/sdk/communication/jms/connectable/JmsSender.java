/*
 */
package com.im.njams.sdk.communication.jms.connectable;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.connectable.AbstractSender;
import com.im.njams.sdk.communication.connectable.Sender;
import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.communication.jms.JmsConstants;
import com.im.njams.sdk.communication.jms.connector.JmsConnector;
import com.im.njams.sdk.communication.jms.connector.JmsSenderConnector;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import java.util.Properties;

/**
 * JMS implementation for a Sender.
 *
 * @author krautenberg@integrationmatters.ocm
 * @version 4.1.0
 */
public class JmsSender extends AbstractSender {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsSender.class);

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value JmsConstants#CONNECTION_FACTORY}
     * <li>{@value JmsConstants#USERNAME}
     * <li>{@value JmsConstants#PASSWORD}
     * <li>{@value JmsConstants#DESTINATION}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public Connector initialize(Properties properties) {
        return connector = new JmsSenderConnector(properties, this.getName() + Connector.SENDER_NAME_ENDING);
    }

    /**
     * Send the given LogMessage to the specified JMS.
     *
     * @param msg the Logmessage to send
     */
    @Override
    protected void send(LogMessage msg) throws NjamsSdkRuntimeException {
        try {
            String data = util.writeJson(msg);
            sendMessage(msg, Sender.NJAMS_MESSAGETYPE_EVENT, data);
            LOG.debug("Send LogMessage {} to {}:\n{}", msg.getPath(), ((JmsSenderConnector)connector).getProducer().getDestination(), data);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send LogMessage", e);
        }
    }

    /**
     * Send the given ProjectMessage to the specified JMS.
     *
     * @param msg the Projectmessage to send
     */
    @Override
    protected void send(ProjectMessage msg) throws NjamsSdkRuntimeException {
        try {
            String data = util.writeJson(msg);
            sendMessage(msg, Sender.NJAMS_MESSAGETYPE_PROJECT, data);
            LOG.debug("Send ProjectMessage {} to {}:\n{}", msg.getPath(), ((JmsSenderConnector)connector).getProducer().getDestination(), data);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send ProjectMessage", e);
        }
    }

    /**
     * Send the given TraceMessage to the specifies JMS
     *
     * @param msg the Tracemessage to send
     */
    @Override
    protected void send(TraceMessage msg) throws NjamsSdkRuntimeException {
        try {
            String data = util.writeJson(msg);
            sendMessage(msg, Sender.NJAMS_MESSAGETYPE_TRACE, data);
            LOG.debug("Send TraceMessage {} to {}:\n{}", msg.getPath(), ((JmsSenderConnector)connector).getProducer().getDestination(), data);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send TraceMessage", e);
        }
    }

    @Override
    protected void extStop() {
        //Nothing to do
    }

    private void sendMessage(CommonMessage msg, String messageType, String data) throws JMSException {
        TextMessage textMessage = ((JmsConnector)connector).getSession().createTextMessage(data);
        if (msg instanceof LogMessage) {
            textMessage.setStringProperty(Sender.NJAMS_LOGID, ((LogMessage) msg).getLogId());
        }
        textMessage.setStringProperty(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        textMessage.setStringProperty(Sender.NJAMS_MESSAGETYPE, messageType);
        textMessage.setStringProperty(Sender.NJAMS_PATH, msg.getPath());
        ((JmsSenderConnector)connector).getProducer().send(textMessage);
    }

    @Override
    public String getName() {
        return JmsConstants.COMMUNICATION_NAME;
    }
}
