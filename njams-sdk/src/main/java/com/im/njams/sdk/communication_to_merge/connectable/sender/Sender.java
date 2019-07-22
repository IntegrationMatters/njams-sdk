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
package com.im.njams.sdk.communication_to_merge.connectable.sender;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.communication_to_merge.connectable.Connectable;
import com.im.njams.sdk.service.NjamsService;

/**
 * This interface must be implemented to create a nJAMS Sender Inplementations
 * which can send Project and Logmessages to nJAMS Server.
 *
 * @author bwand
 */
public interface Sender extends Connectable, NjamsService {

    /**
     * Property key for header properties which will specify the messageVersion for the server
     */
    public static final String NJAMS_SERVER_MESSAGEVERSION = "NJAMS_MESSAGEVERSION";
    /**
     * Property key for header properties which will specify the path for the server
     */
    public static final String NJAMS_SERVER_PATH = "NJAMS_PATH";
    /**
     * Property key for header properties which must be send to the server in
     * logmessages specifing the logId
     */
    public static final String NJAMS_SERVER_LOGID = "NJAMS_LOGID";
    /**
     * Property key for header properties which must be send to the server in
     * every message
     */
    public static final String NJAMS_SERVER_MESSAGETYPE = "NJAMS_MESSAGETYPE";

    /**
     * Property key for header properties which will specify the messageVersion for the cloud
     */
    public static final String NJAMS_CLOUD_MESSAGEVERSION = "x-njams-messageversion";
    /**
     * Property key for header properties which will specify the path for the cloud
     */
    public static final String NJAMS_CLOUD_PATH = "x-njams-path";
    /**
     * Property key for header properties which must be send to the cloud in
     * logmessages specifing the logId
     */
    public static final String NJAMS_CLOUD_LOGID = "x-njams-logid";
    /**
     * Property key for header properties which must be send to the cloud in
     * every message
     */
    public static final String NJAMS_CLOUD_MESSAGETYPE = "x-njams-messagetype";

    /**
     * Property value for header properties which specifies a logmessage
     */
    public static final String NJAMS_MESSAGETYPE_EVENT = "event";
    /**
     * Property value for header properties which specifies a projectmessage
     */
    public static final String NJAMS_MESSAGETYPE_PROJECT = "project";
    /**
     * Property value for header properties which specifies a tracemessage
     */
    public static final String NJAMS_MESSAGETYPE_TRACE = "command";

    /**
     * Send the given message to the new communication_to_merge layer
     *
     * @param msg the message to send
     */
    void send(CommonMessage msg);


}
