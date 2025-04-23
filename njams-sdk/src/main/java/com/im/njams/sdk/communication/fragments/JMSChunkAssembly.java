package com.im.njams.sdk.communication.fragments;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMSChunkAssembly extends GenericChunkAssembly<TextMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(JMSChunkAssembly.class);

    public JMSChunkAssembly() {
        super(null,
            JMSChunkAssembly::getHeaders,
            JMSChunkAssembly::getText,
            false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getHeaders(Message jmsMsg) {
        final Enumeration<String> properties;
        try {
            properties = jmsMsg.getPropertyNames();
        } catch (JMSException e) {
            LOG.error("Failed to list JMS properties", e);
            return Collections.emptyMap();
        }
        final Map<String, String> headers = new HashMap<>();
        while (properties.hasMoreElements()) {
            final String key = properties.nextElement();
            try {
                Object val = jmsMsg.getObjectProperty(key);
                if (val != null) {
                    headers.put(key, String.valueOf(val));
                }
            } catch (JMSException e) {
                LOG.error("Failed to read JMS property {}", key, e);
            }
        }
        return headers;
    }

    private static String getText(TextMessage message) {
        try {
            return message.getText();
        } catch (JMSException e) {
            LOG.error("Failed to get text from JMS message", e);
            return null;
        }
    }

    public static void setHeader(Message msg, String key, String value) {
        try {
            msg.setStringProperty(key, value);
        } catch (JMSException e) {
            LOG.error("Failed to set JMS property {}={}", key, value, e);
        }
    }
}
