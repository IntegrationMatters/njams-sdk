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
package com.im.njams.sdk.communication.cloud;

/**
 *
 * @author pnientiedt
 */
public class CloudConstants {
    
    private CloudConstants() {}

    public static final String PROPERTY_PREFIX = "njams.sdk.communication.cloud";
    public static final String COMMUNICATION_NAME = "CLOUD";
    public static final String ENDPOINT = PROPERTY_PREFIX + ".endpoint";
    public static final String APIKEY = PROPERTY_PREFIX + ".apikey";
    public static final String CLIENT_INSTANCEID = PROPERTY_PREFIX + ".client.instanceId";
    public static final String CLIENT_CERTIFICATE = PROPERTY_PREFIX + ".client.certificate";
    public static final String CLIENT_PRIVATEKEY = PROPERTY_PREFIX + ".client.privatekey";
    
    public static final String MAX_PAYLOAD_BYTES = PROPERTY_PREFIX + ".maxPayloadBytes";

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
