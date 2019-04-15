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

import java.util.Properties;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;

import com.im.njams.sdk.communication.connection.Connector;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.Sender;

/**
 * JMS implementation for a Sender.
 *
 * @author hsiegeln, krautenberg
 * @version 4.0.6
 */
public class JmsSender extends AbstractSender {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsSender.class);

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#CONNECTION_FACTORY}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#USERNAME}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#PASSWORD}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#DESTINATION}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public Connector initialize(Properties properties) {
        return connector = new JmsSenderConnector(properties, this.getName() + "-Sender-Connector");
    }

    /**
     * Send the given LogMessage to the specified JMS.
     *
     * @param msg the Logmessage to send
     */
    @Override
    protected void send(LogMessage msg) throws NjamsSdkRuntimeException {
        try {
            String data = ((JmsConnector)connector).getMapper().writeValueAsString(msg);
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
            String data = ((JmsConnector)connector).getMapper().writeValueAsString(msg);
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
            String data = ((JmsConnector)connector).getMapper().writeValueAsString(msg);
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
