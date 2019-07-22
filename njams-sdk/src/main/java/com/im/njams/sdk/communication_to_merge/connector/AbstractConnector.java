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
package com.im.njams.sdk.communication_to_merge.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Todo: Write Doc
 */
public abstract class AbstractConnector implements Connector {

    //The Logger
    private static final Logger LOG = LoggerFactory.getLogger(AbstractConnector.class);

    protected Properties properties;

    protected NjamsConnection njamsConnection;

    public AbstractConnector(Properties properties, String name) {
        this.properties = properties;
        this.njamsConnection = new NjamsConnection(this, name);
    }

    public final void start() {
        if (properties == null) {
            LOG.error("Couldn't start the AbstractConnector, because the properties are null");
        } else if (properties.isEmpty()) {
            LOG.error("Couldn't start the AbstractConnector, because the properties are empty");
        } else if (njamsConnection == null) {
            LOG.error("Couldn't start the AbstractConnector, because the njamsConnection is null");
        } else {
            Thread initialConnect = new Thread(() -> njamsConnection.initialConnect());
            initialConnect.setDaemon(true);
            initialConnect.setName(String.format("%s-Initial-Connector-Thread", this.getClass().getSimpleName()));
            initialConnect.start();
        }
    }

    @Override
    public final NjamsConnection getNjamsConnection(){
        return njamsConnection;
    }

    @Override
    public final void stop(){
        if(njamsConnection != null) {
            njamsConnection.stop();
        }
        njamsConnection = null;
        this.close();
    }
}
