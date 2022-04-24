/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMessage;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.Receiver;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.SharedReceiverSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Overrides the common {@link CloudReceiver} for supporting receiving messages for multiple {@link Receiver} instances.
 */
public class SharedCloudReceiver extends CloudReceiver implements ShareableReceiver<AWSIotMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(SharedCloudReceiver.class);

    private static final boolean IS_SHARED = true;
    private SharedCloudTopic shareCloudTopic = null;
    private final SharedReceiverSupport<SharedCloudReceiver, AWSIotMessage> sharingSupport =
            new SharedReceiverSupport<>(this);

    /**
     * Adds the given instance to this receiver for receiving instructions.
     */
    @Override
    public void addReceiver(Receiver receiver) {

        sharingSupport.addReceiver(receiver);
        updateReceiver(receiver);
    }

    @Override
    public void removeReceiver(Receiver receiver) {
        sharingSupport.removeReceiver(receiver);
    }

    @Override
    public synchronized void connect() {
        try {
            if (isConnected() || isConnecting()) {
                return;
            }
            if (retryConnection) {
                connectionStatus = ConnectionStatus.CONNECTED;

                if (retryService == null) {
                    LOG.debug("Spawning CloudReceiverRetry");
                    Runnable r = new CloudReceiverRetry(this);
                    retryService = Executors.newScheduledThreadPool(1);
                    retryService.scheduleAtFixedRate(r, retryInterval, retryInterval, TimeUnit.MILLISECONDS);
                }

                return;
            }

            if (!retryConnection && retryService != null) {
                LOG.debug("Shutdown CloudReceiverRetry");
                retryService.shutdownNow();
                connectionStatus = ConnectionStatus.CONNECTING;
            }

            if (isConnected() || isConnecting()) {
                return;
            }

            connectionStatus = ConnectionStatus.CONNECTING;
            LOG.debug("Connect to endpoint: {} with clientId: {}", endpoint, connectionId);
            mqttclient = new CustomAWSIotMqttClient(endpoint, connectionId, keyStorePasswordPair.keyStore,
                    keyStorePasswordPair.keyPassword, this);

            // optional parameters can be set before connect()
            getMqttclient().connect(30000);
            setQos(AWSIotQos.QOS1);

            // subscribe commands topic
            topicName = "/" + instanceId + "/commands/" + connectionId + "/";
            shareCloudTopic = new SharedCloudTopic(this);

            LOG.debug("Topic Subscription: {}", shareCloudTopic.getTopic());

            getMqttclient().subscribe(shareCloudTopic);
            connectionStatus = ConnectionStatus.CONNECTED;

        } catch (Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }

    private void updateReceiver(Receiver receiver) {

        if (!isConnected()) {
            return;
        }

        try {
            sendOnConnect(receiver);
        } catch (Exception e) {
            LOG.error("Failed to update receiver", e);
        }
    }

    @Override
    public void resendOnConnect() throws JsonProcessingException, AWSIotException {
        for (Receiver receiver : sharingSupport.getAllReceiverInstances()) {
            sendOnConnect(receiver);
        }
    }

    private void sendOnConnect(Receiver receiver) throws JsonProcessingException, AWSIotException {
        sendOnConnectMessage(IS_SHARED, connectionId, instanceId, receiver.getInstanceMetadata());
    }

    public void onInstruction(AWSIotMessage message, Instruction instruction) {
        sharingSupport.onInstruction(message, instruction, true);
    }

    @Override
    public Path getReceiverPath(AWSIotMessage requestMessage, Instruction instruction) {
        return new Path(instruction.getRequestParameterByName("processPath"));
    }

    @Override
    public void sendReply(AWSIotMessage requestMessage, Instruction reply) {
        final String uuid = shareCloudTopic.getUUID(reply);
        shareCloudTopic.reply(reply, uuid);

    }

}