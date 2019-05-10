/*
 */
package com.im.njams.sdk.communication.connectable;

import com.im.njams.sdk.communication.connectable.Connectable;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;

/**
 * This interface must be implemented to create a nJAMS Sender Inplementations
 * which can send Project and Logmessages to nJAMS Server.
 *
 * @author bwand
 */
public interface Sender extends Connectable {

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
     * Send the given message to the new communication layer
     *
     * @param msg the message to send
     */
    void send(CommonMessage msg);


}
