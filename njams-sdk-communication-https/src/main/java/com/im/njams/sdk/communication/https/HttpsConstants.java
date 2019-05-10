package com.im.njams.sdk.communication.https;

public class HttpsConstants {

    private HttpsConstants(){}

    public static final String PROPERTY_PREFIX = "njams.sdk.communication.http";

    /**
     * Name of the HTTPS component
     */
    public static final String COMMUNICATION_NAME= "HTTPS";

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

    public static final String CLIENT_KS = "client.ks";

    public static final String CLIENT_TS = "client.ts";

    public static final String TRUST_STORE = "javax.net.ssl.trustStore";

    /**
     * Content type json
     */
    public static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * Content type text plain
     */
    public  static final String CONTENT_TYPE = "Content-Type";
    public  static final String CONTENT_TYPE_TEXT = "text/plain";

    public  static final String HTTP_REQUEST_TYPE_POST = "POST";
    public  static final String HTTP_REQUEST_TYPE_PUT= "PUT";
    public  static final String HTTP_REQUEST_TYPE_GET= "GET";

    public  static final String ACCEPT = "Accept";

    public  static final String CONTENT_LANGUAGE = "Content-Language";

    public  static final String CONTENT_LANGUAGE_EN_US = "en-US";

    public  static final String UTF_8 = "charset=UTF-8";
}
