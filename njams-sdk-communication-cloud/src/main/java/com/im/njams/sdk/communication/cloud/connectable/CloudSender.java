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
package com.im.njams.sdk.communication.cloud.connectable;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;

import com.im.njams.sdk.communication.cloud.CloudConstants;
import com.im.njams.sdk.communication.cloud.connector.CloudSenderConnector;

import com.im.njams.sdk.communication.connectable.Sender;
import com.im.njams.sdk.communication.connector.Connector;

import com.im.njams.sdk.communication.http.https.connectable.HttpsSender;

import java.util.Properties;

/**
 * @author stkniep
 */
public class CloudSender extends HttpsSender {

    @Override
    public String getName() {
        return CloudConstants.COMMUNICATION_NAME;
    }

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#ENDPOINT}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#APIKEY}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#CLIENT_INSTANCEID}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#CLIENT_CERTIFICATE}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#CLIENT_PRIVATEKEY}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#MAX_PAYLOAD_BYTES}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    protected Connector initialize(Properties properties) {
        return new CloudSenderConnector(properties, getName() + Connector.SENDER_NAME_ENDING);
    }

    @Override
    protected void fillProperties(Properties properties, CommonMessage msg, String eventType) {
        properties.put(Sender.NJAMS_CLOUD_MESSAGEVERSION, MessageVersion.V4.toString());
        properties.put(Sender.NJAMS_CLOUD_PATH, msg.getPath());
        properties.put(Sender.NJAMS_CLOUD_MESSAGETYPE, eventType);
        if (eventType.equals(Sender.NJAMS_MESSAGETYPE_EVENT)) {
            properties.put(Sender.NJAMS_CLOUD_LOGID, ((LogMessage) msg).getLogId());
        }
    }
}
