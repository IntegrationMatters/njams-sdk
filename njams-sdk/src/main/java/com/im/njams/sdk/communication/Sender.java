/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication;

import java.util.Properties;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.Njams;

/**
 * This interface must be implemented to create a nJAMS sender implementation
 * which can send project and log-messages to nJAMS server.
 *
 * @author bwand
 */
public interface Sender {

    /**
     * This implementation should initialize itself via the given properties.
     *
     * @param properties to be used for initialization
     */
    public void init(Properties properties);

    /**
     * Send the given message to the new communication layer
     *
     * @param msg the message to send
     * @param clientSessionId The session ID of the {@link Njams} instance that sends the message.
     */
    void send(CommonMessage msg, String clientSessionId);

    /**
     * Close this Sender.
     */
    public void close();

    /**
     * Each implementation should provide a unique name.
     * @return This implementation's name
     */
    public String getName();

}
