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

import java.util.HashMap;
import java.util.Map;

import javax.jms.Message;

import com.im.njams.sdk.utils.JmsMessageAccess;

/**
 * JMS implementation working on text messages of either {@code javax.jms} or {@code jakarta.jms}.
 */
public class JMSChunkAssembly extends GenericChunkAssembly<Object> {

    /**
     * Creates a new instance.
     */
    public JMSChunkAssembly() {
        super(null,
            JMSChunkAssembly::getHeaders,
            JMSChunkAssembly::getText,
            false);
    }

    private static Map<String, String> getHeaders(Object jmsMsg) {
        final Map<String, String> headers = new HashMap<>();
        for (final String key : JmsMessageAccess.propertyNames(jmsMsg)) {
            final Object val = JmsMessageAccess.objectProperty(jmsMsg, key);
            if (val != null) {
                headers.put(key, String.valueOf(val));
            }
        }
        return headers;
    }

    private static String getText(Object message) {
        return JmsMessageAccess.text(message);
    }

    /**
     * Sets a string property on the given JMS message; failures are logged and otherwise ignored.
     *
     * @param msg the message; any {@code javax.jms} or {@code jakarta.jms} message
     * @param key the property name
     * @param value the property value
     */
    public static void setHeader(Object msg, String key, String value) {
        JmsMessageAccess.setStringProperty(msg, key, value);
    }

    /**
     * Sets a string property on the given JMS message; failures are logged and otherwise ignored.
     *
     * @param msg the message
     * @param key the property name
     * @param value the property value
     * @deprecated Use {@link #setHeader(Object, String, String)} instead.
     */
    @Deprecated(since = "6.1.0")
    public static void setHeader(Message msg, String key, String value) {
        setHeader((Object) msg, key, value);
    }
}
