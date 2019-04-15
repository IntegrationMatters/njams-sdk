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

import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.Sender;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.*;

import static java.nio.charset.Charset.defaultCharset;

public class HttpSenderConnector extends HttpConnector {


    /**
     * Content type json
     */
    private static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * Content type text plain
     */
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_TEXT = "text/plain";

    private static final String HTTP_REQUEST_TYPE = "POST";

    private static final String ACCEPT = "Accept";

    private static final String CONTENT_LANGUAGE = "Content-Language";

    private static final String CONTENT_LANGUAGE_EN_US = "en-US";

    protected String user;
    protected String password;
    protected URL url;

    public HttpSenderConnector(Properties properties, String name) {
        super(properties, name);
        try {
            url = new URL(properties.getProperty(HttpConstants.SENDER_URL));
        } catch (final MalformedURLException ex) {
            throw new NjamsSdkRuntimeException("unable to init http sender", ex);
        }
        user = properties.getProperty(HttpConstants.SENDER_USERNAME);
        password = properties.getProperty(HttpConstants.SENDER_PASSWORD);
    }

    @Override
    protected List<Exception> extClose() {
        List<Exception> exceptions = new ArrayList<>();
        return exceptions;
    }

    @Override
    public void connect() {
        //Do nothing, a connection has to be established with each send.
    }

    public HttpURLConnection getConnection() throws IOException{
        //Create connection
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        setDefaultRequestProperties(connection);

        setUser(connection);

        return connection;
    }

    protected void setDefaultRequestProperties(HttpURLConnection connection) throws ProtocolException {
        connection.setRequestMethod(HTTP_REQUEST_TYPE);
        connection.setRequestProperty(CONTENT_TYPE, CONTENT_TYPE_JSON);
        connection.setRequestProperty(ACCEPT, CONTENT_TYPE_TEXT);
        connection.setRequestProperty(CONTENT_LANGUAGE, CONTENT_LANGUAGE_EN_US);
        connection.setRequestProperty(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());

        connection.setUseCaches(false);
        connection.setDoOutput(true);
    }

    protected void setUser(HttpURLConnection connection){
        if (user != null) {
            final Base64.Encoder encoder = Base64.getEncoder();
            final String userpassword = user + ":" + password;
            final byte[] encodedAuthorization = encoder.encode(userpassword.getBytes(defaultCharset()));
            connection.setRequestProperty("Authorization",
                    "Basic " + new String(encodedAuthorization, defaultCharset()));
        }
    }
}
