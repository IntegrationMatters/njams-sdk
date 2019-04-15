/*
 */

package com.im.njams.sdk.communication.http;

public class HttpConstants {

    private HttpConstants(){}

    public static final String PROPERTY_PREFIX = "njams.sdk.communication.http";

    /**
     * Name of the HTTP component
     */
    public static final String COMMUNICATION_NAME_HTTP = "HTTP";

    /**
     * Name of the HTTPS component
     */
    public static final String COMMUNICATION_NAME_HTTPS = "HTTPS";

    /**
     * Http receiver port
     */
    public static final String RECEIVER_PORT = PROPERTY_PREFIX + ".receiver.port";


    /**
     * http sender urlport
     */
    public static final String SENDER_URL = PROPERTY_PREFIX + ".sender.urlport";


    /**
     * http sender username
     */
    public static final String SENDER_USERNAME = PROPERTY_PREFIX + ".sender.username";

    /**
     * http sender password
     */
    public static final String SENDER_PASSWORD = PROPERTY_PREFIX + ".sender.password";
}
