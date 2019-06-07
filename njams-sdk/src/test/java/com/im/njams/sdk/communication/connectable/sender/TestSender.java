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
package com.im.njams.sdk.communication.connectable.sender;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.communication.Communication;
import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.communication.connector.NullConnector;
import com.im.njams.sdk.configuration.control.ConfigurationProxyFactory;
import com.im.njams.sdk.settings.Settings;

import java.util.Properties;

/**
 * Dummy implementation for testing.<br>
 * <b>Note:</b> For using this instance, the test environment needs to have a the full qualified class name of this
 * {@link TestSender} in the <code>META_INF/services/com.im.njams.sdk.communication.connectable.sender.Sender</code> file.
 *
 * @author cwinkler
 *
 */
public class TestSender implements Sender {

    public static final String NAME = "TEST_COMMUNICATION";
    private static Sender sender = null;

    /**
     * Delegates all request to the given sender.<br>
     * <b>Note:</b> {@link #getName()} is invoked on the given sender but the value returned is always {@link #NAME}.
     * @param sender
     */
    public static void setSenderMock(Sender sender) {
        TestSender.sender = sender;
    }

    /**
     * Returns a settings prepared for using this sender implementation.
     * @return
     */
    public static Settings getSettings() {
        Properties properties = new Properties();
        properties.setProperty(Communication.COMMUNICATION, NAME);
        properties.setProperty(ConfigurationProxyFactory.CONFIGURATION_PROXY_SERVICE, "memory");

        Settings config = new Settings();
        config.setProperties(properties);
        return config;
    }

    @Override
    public String getName() {
        if (sender != null) {
            sender.getName();
        }
        return NAME;
    }

    @Override
    public void send(CommonMessage msg) {
        if (sender != null) {
            sender.send(msg);
        }
    }

    @Override
    public void init(Properties properties) {
        if (sender != null){
            sender.init(properties);
        }
    }

    @Override
    public void stop() {
        if (sender != null){
            sender.stop();
        }
    }

    @Override
    public Connector getConnector() {
        if(sender != null){
            return sender.getConnector();
        }
        else return new NullConnector();
    }
}
