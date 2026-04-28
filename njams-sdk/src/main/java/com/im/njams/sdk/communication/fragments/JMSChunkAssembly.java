/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
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

/**
 * JMS implementation working on {@link TextMessage} messages.
 */
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
