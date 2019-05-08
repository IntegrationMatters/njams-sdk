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
package com.im.njams.sdk.communication.http.https.connectable;

import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.communication.http.HttpConstants;
import com.im.njams.sdk.communication.http.connectable.HttpSender;
import com.im.njams.sdk.communication.http.https.connector.HttpsSenderConnector;

import java.util.Properties;

/**
 * Https Sender
 *
 * @author stkniep
 */
public class HttpsSender extends HttpSender {

    @Override
    public String getName() {
        return HttpConstants.COMMUNICATION_NAME_HTTPS;
    }

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.http.HttpConstants#SENDER_URL}
     * <li>{@value com.im.njams.sdk.communication.http.HttpConstants#SENDER_USERNAME}
     * <li>{@value com.im.njams.sdk.communication.http.HttpConstants#SENDER_PASSWORD}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    protected Connector initialize(Properties properties) {
        return new HttpsSenderConnector(properties, getName() + Connector.SENDER_NAME_ENDING);
    }
}
