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
package com.im.njams.sdk.communication_to_merge.https.connector;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication_to_merge.https.HttpsConstants;
import com.im.njams.sdk.communication_to_merge.https.connectable.HttpsReceiver;
import com.im.njams.sdk.settings.encoding.Transformer;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
/**
 * Todo: write doc
 */
public class HttpsReceiverConnector extends HttpsConnector{

    private static final Logger LOG = LoggerFactory.getLogger(HttpsReceiverConnector.class);

    private HttpServer httpServer;

    private HttpsReceiver httpsReceiver;

    private final int port;

    public HttpsReceiverConnector(Properties properties, String name, HttpsReceiver httpsReceiver) {
        super(properties, name);
        this.httpsReceiver = httpsReceiver;
        this.port = Integer.parseInt(Transformer.decode(properties.getProperty(HttpsConstants.RECEIVER_PORT)));
    }

    @Override
    public final void connect() {
        final InetSocketAddress isa = new InetSocketAddress(port);
        try {
            httpServer = HttpServer.create(isa, 5);
            LOG.debug("The HttpServer was created successfully.");

            httpServer.createContext("/command", httpsReceiver);
            LOG.debug("The HttpContext was created successfully.");

            httpServer.start();
            LOG.debug("The HttpServer was started successfully.");
        } catch (final IOException ex) {
            throw new NjamsSdkRuntimeException("unable to create http server", ex);
        }
    }

    @Override
    protected List<Exception> extClose() {
        List<Exception> exceptions = new ArrayList<>();
        if (httpServer != null) {
            try {
                httpServer.stop(0);
            }catch(Exception ex){
                exceptions.add(new NjamsSdkRuntimeException("Unable to close httpServer correctly", ex));
            }finally{
                httpServer = null;
            }
        }
        return exceptions;
    }
}
