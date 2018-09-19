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

/**
 * This interface must be implemented to create a nJAMS Sender Inplementations
 * which can send Project and Logmessages to nJAMS Server.
 *
 * @author bwand
 */
public interface Sender extends AutoCloseable {

    /**
     * Property key for header properties which will specify the messageVersion
     */
    public static final String NJAMS_MESSAGEVERSION = "NJAMS_MESSAGEVERSION";
    /**
     * Property key for header properties which will specify the path
     */
    public static final String NJAMS_PATH = "NJAMS_PATH";
    /**
     * Property key for header properties which must be send to the server in
     * logmessages specifing the logId
     */
    public static final String NJAMS_LOGID = "NJAMS_LOGID";
    /**
     * Property key for header properties which must be send to the server in
     * every message
     */
    public static final String NJAMS_MESSAGETYPE = "NJAMS_MESSAGETYPE";
    /**
     * Property value for header properties which specifies a logmessage
     */
    public static final String NJAMS_MESSAGETYPE_EVENT = "event";
    /**
     * Property value for header properties which specifies a projectmessage
     */
    public static final String NJAMS_MESSAGETYPE_PROJECT = "project";

    /**
     * This new implementation should initialize itself via the given
     * Properties.
     *
     * @param properties to be used for initialization
     */
    public void init(Properties properties);

    /**
     * Send the given message to the new communication layer
     *
     * @param msg the message to send
     */
    void send(CommonMessage msg);

    /**
     * Close this Sender.
     */
    @Override
    public void close();

    public String getName();

}
