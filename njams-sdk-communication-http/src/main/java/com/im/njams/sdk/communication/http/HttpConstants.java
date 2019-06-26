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

package com.im.njams.sdk.communication.http;

public class HttpConstants {

    private HttpConstants(){}

    public static final String PROPERTY_PREFIX = "njams.sdk.communication.http";

    /**
     * Name of the HTTP component
     */
    public static final String COMMUNICATION_NAME = "HTTP";

    /**

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
