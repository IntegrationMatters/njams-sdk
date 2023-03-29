/*
 * Copyright (c) 2023 Integration Matters GmbH
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

/**
 * Constants used for message headers/properties.
 */
public class MessageHeaders {

    // ==================================================================================
    // ==================================================================================
    // Common nJAMS headers
    // ==================================================================================
    // commands
    // ==================================================================================
    /**
     * Property key for header property which will contain the clientId for container setups
     */
    public static final String NJAMS_CLIENTID_HEADER = "NJAMS_CLIENTID";
    /**
     * Content type header. Is used to determine whether this command can be processed by this receiver.
     */
    public static final String NJAMS_CONTENT_HEADER = "NJAMS_CONTENT";
    /**
     * Name of the header storing a unique message ID
     */
    public static final String NJAMS_MESSAGE_ID_HEADER = "NJAMS_MESSAGE_ID";
    /**
     * Name of the header storing the receiver (client) path
     */
    public static final String NJAMS_RECEIVER_HEADER = "NJAMS_RECEIVER";
    /**
     * Name of the header storing the ID of the request message to that a reply message belongs
     */
    public static final String NJAMS_REPLY_FOR_HEADER = "NJAMS_REPLY_FOR";
    /**
     * Name of the header storing command type.
     */
    public static final String NJAMS_TYPE_HEADER = "NJAMS_TYPE";

    // ==================================================================================
    // log-/project-messages
    // ==================================================================================
    /**
     * Property key for header properties which must be send to the server in
     * log-messages specifying the logId
     */
    public static final String NJAMS_LOGID_HEADER = "NJAMS_LOGID";
    /**
     * Property key for header properties which will specify the messageVersion
     */
    public static final String NJAMS_MESSAGEVERSION_HEADER = "NJAMS_MESSAGEVERSION";
    /**
     * Property key for header properties which will specify the path
     */
    public static final String NJAMS_PATH_HEADER = "NJAMS_PATH";
    /**
     * Property key for header properties which must be send to the server in
     * every message
     */
    public static final String NJAMS_MESSAGETYPE_HEADER = "NJAMS_MESSAGETYPE";

    // ==================================================================================
    // Message-type constants
    // ==================================================================================
    /**
     * Property value for header properties which specifies a log-message
     */
    public static final String MESSAGETYPE_EVENT = "event";
    /**
     * Property value for header properties which specifies a project-message
     */
    public static final String MESSAGETYPE_PROJECT = "project";
    /**
     * Property value for header properties which specifies a trace-message
     */
    public static final String MESSAGETYPE_TRACE = "command";

    // ==================================================================================
    // Content-type constants
    // ==================================================================================
    /**
     * Content type being used with {@link #NJAMS_CONTENT_HEADER} respectively {@link #NJAMS_CONTENT_HTTP_HEADER}
     */
    public static final String CONTENT_TYPE_JSON = "json";
    // ==================================================================================
    // Command types
    // ==================================================================================
    /**
     * Command type to be used with {@link #NJAMS_TYPE_HEADER} for sending replies.
     */
    public static final String COMMAND_TYPE_REPLY = "Reply";
    // ==================================================================================
    // Receiver constants
    // ==================================================================================
    /**
     * Constant used with {@link #NJAMS_RECEIVER_HEADER} when sending messages to nJAMS server
     */
    public static final String RECEIVER_SERVER = "server";

    // ==================================================================================
    // ==================================================================================
    // NGINX compatible HTTP headers use by (new) http implementations only.
    // ==================================================================================
    /**
     * <b>http-only!</b> Property key for header property which will contain the clientId for container setups
     */
    public static final String NJAMS_CLIENTID_HTTP_HEADER = "njams-clientid";
    /**
     * <b>http-only!</b> Content type header. Is used to determine whether this command can be processed by this receiver.
     */
    public static final String NJAMS_CONTENT_HTTP_HEADER = "njams-content";
    /**
     * <b>http-only!</b> Property key for header properties which must be send to the server in
     * log-messages specifying the logId
     */
    public static final String NJAMS_LOGID_HTTP_HEADER = "njams-logid";
    /**
     * <b>http-only!</b> Name of the header storing a unique message ID
     */
    public static final String NJAMS_MESSAGE_ID_HTTP_HEADER = "njams-message-id";
    /**
     * <b>http-only!</b> Property key for header properties which must be send to the server in
     * every message
     */
    public static final String NJAMS_MESSAGETYPE_HTTP_HEADER = "njams-messagetype";
    /**
     * <b>http-only!</b> Property key for header properties which will specify the messageVersion
     */
    public static final String NJAMS_MESSAGEVERSION_HTTP_HEADER = "njams-messageversion";
    /**
     * <b>http-only!</b> Property key for header properties which will specify the path
     */
    public static final String NJAMS_PATH_HTTP_HEADER = "njams-path";
    /**
     * <b>http-only!</b> Name of the header storing the receiver (client) path
     */
    public static final String NJAMS_RECEIVER_HTTP_HEADER = "njams-receiver";
    /**
     * <b>http-only!</b> Name of the header storing the ID of the request message to that a reply message belongs
     */
    public static final String NJAMS_REPLY_FOR_HTTP_HEADER = "njams-reply-for";
    /**
     * <b>http-only!</b> Name of the header storing command type.
     */
    public static final String NJAMS_TYPE_HTTP_HEADER = "njams-type";

    private MessageHeaders() {
        // static
    }

}
