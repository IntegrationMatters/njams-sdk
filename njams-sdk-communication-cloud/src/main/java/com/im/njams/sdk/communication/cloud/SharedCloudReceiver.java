/*
 * Copyright (c) 2020 Faiz & Siegeln Software GmbH
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

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.ShareableReceiver;

/**
 * Overrides the common {@link CloudReceiver} for supporting receiving messages for multiple {@link Njams} instances.
 *
 *
 */
public class SharedCloudReceiver extends CloudReceiver implements ShareableReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(SharedCloudReceiver.class);

    private static final Boolean isShared = true;

    private final Map<Path, Njams> njamsInstances = new ConcurrentHashMap<>();

    /**
     * Adds the given instance to this receiver for receiving instructions.
     *
     */
    @Override
    public void setNjams(Njams njamsInstance) {
        super.setNjams(null);
        synchronized (njamsInstances) {
            njamsInstances.put(njamsInstance.getClientPath(), njamsInstance);
            updateConsumer(njamsInstance);
        }
        LOG.debug("Added client {} to shared receiver; {} attached receivers.", njamsInstance.getClientPath(),
                njamsInstances.size());
    }

    @Override
    public void removeNjams(Njams njamsInstance) {

        boolean callStop;
        synchronized (njamsInstances) {
            njamsInstances.remove(njamsInstance.getClientPath());
            callStop = njamsInstances.isEmpty();
            if (!callStop) {
                updateConsumer(njamsInstance);
            }
        }
        LOG.debug("Removed client {} from shared receiver; {} remaining receivers.", njamsInstance.getClientPath(),
                njamsInstances.size());
        if (callStop) {
            super.stop();
        }
    }

    Njams getNjamsInstanceByPath(String path) {
        for (Entry<Path, Njams> entry : njamsInstances.entrySet()) {
            if (path.startsWith(entry.getKey().toString())) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public void connect() {
        try {
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

            if (isConnected()) {
                return;
            }

            connectionStatus = ConnectionStatus.CONNECTING;
            LOG.debug("Connect to endpoint: {} with clientId: {}", endpoint, connectionId);
            mqttclient = new AWSIotMqttClient(endpoint, connectionId, keyStorePasswordPair.keyStore,
                    keyStorePasswordPair.keyPassword);

            // optional parameters can be set before connect()
            getMqttclient().connect(30000);
            setQos(AWSIotQos.QOS1);

            // send on connect for first client
            for (Entry<Path, Njams> entry : njamsInstances.entrySet()) {
                sendOnConnect(entry.getValue());
            }

            // subscribe commands topic
            this.topicName = "/" + instanceId + "/commands/" + connectionId + "/";
            SharedCloudTopic topic = new SharedCloudTopic(this);

            LOG.debug("Topic Subscription: {}", topic.getTopic());

            getMqttclient().subscribe(topic);
            connectionStatus = ConnectionStatus.CONNECTED;

        } catch (Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }

    private void updateConsumer(Njams njamsInstance) {
        try {
            if (!isConnected()) {
                return;
            }
            sendOnConnect(njamsInstance);
        } catch (Exception e) {
            LOG.error("Failed to update consumer", e);
        }
    }

    private void sendOnConnect(Njams njams) throws JsonProcessingException, AWSIotException {
        sendOnConnectMessage(isShared, connectionId, instanceId, njams.getClientPath().toString(),
                njams.getClientVersion(), njams.getSdkVersion(), njams.getMachine(), njams.getCategory());
    }

    @Override
    public void onInstruction(Instruction instruction, Njams njams) {

        LOG.debug("OnInstruction: {} for {}", instruction == null ? "null" : instruction.getCommand(),
                njams.getClientPath());
        if (instruction == null) {
            LOG.error("Instruction should not be null");
            return;
        }
        if (instruction.getRequest() == null || instruction.getRequest().getCommand() == null) {
            LOG.error("Instruction should have a valid request with a command");
            Response response = new Response();
            response.setResultCode(1);
            response.setResultMessage("Instruction should have a valid request with a command");
            instruction.setResponse(response);
            return;
        }
        //Extend your request here. If something doesn't work as expected,
        //you can return a response that will be sent back to the server without further processing.
        Response exceptionResponse = extendRequest(instruction.getRequest());
        if (exceptionResponse != null) {
            //Set the exception response
            instruction.setResponse(exceptionResponse);
        } else {
            for (InstructionListener listener : njams.getInstructionListeners()) {
                try {
                    listener.onInstruction(instruction);
                } catch (Exception e) {
                    LOG.error("Error in InstructionListener {}", listener.getClass().getSimpleName(), e);
                }
            }
            //If response is empty, no InstructionListener found. Set default Response indicating this.
            if (instruction.getResponse() == null) {
                LOG.warn("No InstructionListener for {} found", instruction.getRequest().getCommand());
                Response response = new Response();
                response.setResultCode(1);
                response.setResultMessage(
                        "No InstructionListener for " + instruction.getRequest().getCommand() + " found");
                instruction.setResponse(response);
            }
        }
    }

}