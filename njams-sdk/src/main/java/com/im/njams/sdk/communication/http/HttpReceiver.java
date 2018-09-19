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
package com.im.njams.sdk.communication.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Http Receiver
 *
 * @author stkniep
 */
public class HttpReceiver extends AbstractReceiver {

    private static final String PROPERTY_PREFIX = "njams.sdk.communication.http";

    private static final String NAME = "HTTP";
    /**
     * Http receiver port
     */
    public static final String RECEIVER_PORT = PROPERTY_PREFIX + ".receiver.port";
    private HttpServer httpServer;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(final Properties properties) {
        final int port = Integer.parseInt(properties.getProperty(RECEIVER_PORT));
        final InetSocketAddress isa = new InetSocketAddress(port);
        this.connectionStatus = ConnectionStatus.DISCONNECTED;

        try {
            httpServer = HttpServer.create(isa, 5);
        } catch (final IOException ex) {
            throw new NjamsSdkRuntimeException("unable to create http server", ex);
        }
        HttpHandler commandHandler = new CommandHandler(this);
        httpServer.createContext("/command", commandHandler);
    }

    @Override
    public void start() {
        connect();
    }

    @Override
    public void stop() {
        httpServer.stop(0);
    }

    @Override
    public void connect() {
        this.connectionStatus = ConnectionStatus.CONNECTING;
        try {
            httpServer.start();
            this.connectionStatus = ConnectionStatus.CONNECTED;
        } catch (Exception e) {
            this.connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }

}
