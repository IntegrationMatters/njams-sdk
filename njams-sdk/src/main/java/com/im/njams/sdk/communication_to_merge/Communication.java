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
package com.im.njams.sdk.communication_to_merge;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication_to_merge.connection.receiver.NjamsReceiver;
import com.im.njams.sdk.communication_to_merge.connection.sender.NjamsAbstractSender;
import com.im.njams.sdk.communication_to_merge.connection.sender.NjamsSender;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.encoding.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Todo: Write Doc
 */
public class Communication{

    /**
     * Communication Key
     */
    public static final String COMMUNICATION = "njams.sdk.communication";
    private static final Logger LOG = LoggerFactory.getLogger(com.im.njams.sdk.api.communication.Communication.class);

    private NjamsAbstractSender njamsSender;
    private NjamsReceiver njamsReceiver;

    private final Properties properties;
    private final Njams njams;

    /**
     * Create a new CommunicationFactory
     *
     * @param njams Njams to add
     * @param settings Settings to add
     */
    public Communication(Njams njams, Settings settings) {
        this.njams = njams;
        this.properties = Transformer.decode(settings.getProperties());
    }

    public void stopAll() {
        if(njamsSender != null){
            njamsSender.stop();
        }

        if(njamsReceiver != null){
            njamsReceiver.stop();
        }
    }

    public void initializeNjamsReceiver() {
        try {
            njamsReceiver = new NjamsReceiver(njams, properties);
        } catch (Exception e) {
            LOG.error("Error starting NjamsReceiver", e);
            try {
                njamsReceiver.stop();
            } catch (Exception ex) {
                LOG.debug("Unable to stop receiver", ex);
            }
            njamsReceiver = null;
        }
    }

    public void sendMessage(CommonMessage msg) {
        if(njamsSender == null){
            njamsSender = new NjamsSender(njams, properties);
        }
        njamsSender.send(msg);
    }
}
