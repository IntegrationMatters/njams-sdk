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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.SharedReceiverSupport;

import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.HostResolver;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder;

/**
 * Overrides the common {@link CloudReceiver} for supporting receiving messages for multiple {@link Njams} instances.
 *
 *
 */
public class SharedCloudReceiver extends CloudReceiver implements ShareableReceiver<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(SharedCloudReceiver.class);

    private static final boolean IS_SHARED = true;
    private final SharedReceiverSupport<SharedCloudReceiver, ?> sharingSupport =
            new SharedReceiverSupport<>(this);

    /**
     * Adds the given instance to this receiver for receiving instructions.
     *
     */
    @Override
    public void setNjams(Njams njamsInstance) {
        super.setNjams(null);
        sharingSupport.addNjams(njamsInstance);
        updateReceiver(njamsInstance);
    }

    @Override
    public void removeNjams(Njams njamsInstance) {
        sharingSupport.removeNjams(njamsInstance);
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
           
            MqttClientConnectionEvents callbacks = new MqttClientConnectionEvents() {
                @Override
                public void onConnectionInterrupted(int errorCode) {
                    if (errorCode != 0) {
                        System.out
                                .println("Connection interrupted: " + errorCode + ": " + CRT.awsErrorString(errorCode));
                    }
                }

                @Override
                public void onConnectionResumed(boolean sessionPresent) {
                    System.out
                            .println("Connection resumed: " + (sessionPresent ? "existing session" : "clean session"));
                }
            };

            EventLoopGroup eventLoopGroup = new EventLoopGroup(1);
            HostResolver resolver = new HostResolver(eventLoopGroup);
            ClientBootstrap clientBootstrap = new ClientBootstrap(eventLoopGroup, resolver);
            AwsIotMqttConnectionBuilder builder =
                    AwsIotMqttConnectionBuilder.newMtlsBuilderFromPath(certificateFile, privateKeyFile);

            builder.withCertificateAuthorityFromPath(null, caFile).withBootstrap(clientBootstrap)
                    .withConnectionEventCallbacks(callbacks).withClientId(connectionId).withEndpoint(endpoint)
                    .withCleanSession(true);

            connection = builder.build();

            CompletableFuture<Boolean> connected = connection.connect();
            try {
                boolean sessionPresent = connected.get();
                LOG.info("Connected to " + (!sessionPresent ? "new" : "existing") + " session!");

                resendOnConnect();

            } catch (Exception ex) {
                LOG.error("Exception occurred during connect: {}", ex);
                throw new RuntimeException("Exception occurred during connect", ex);
            }

            // subscribe commands topic

            topicName = "/" + instanceId + "/commands/" + connectionId + "/";

            LOG.info("Subscribe to commands topic: {}", topicName);

            CompletableFuture<Integer> subscribed =
                    connection.subscribe(topicName, QualityOfService.AT_LEAST_ONCE, (message) -> {
                        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);

                        try {
                            LOG.info("Received message on topic {}:\n{}", message.getTopic(), payload);
                            Instruction instruction = getInstruction(payload);
                            if (instruction != null) {
                                String uuid = getUUID(instruction);
                                if (uuid != null) {
                                    onInstruction(instruction);
                                    reply(instruction, uuid);
                                } else {
                                    LOG.error(
                                            "Received message on topic {} does not contain a valid mqttUuid. Ignore!");
                                }
                            } else {
                                LOG.warn("Received message on topic {} is not a valid instruction. Ignore!",
                                        message.getTopic());
                            }
                        } catch (Exception e) {
                            LOG.error("Error in onMessage", e);
                        }

                    });

            subscribed.get();

            connectionStatus = ConnectionStatus.CONNECTED;

        } catch (Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }

    private void updateReceiver(Njams njamsInstance) {

        if (!isConnected()) {
            return;
        }

        try {
            sendOnConnect(njamsInstance);
        } catch (Exception e) {
            LOG.error("Failed to update receiver", e);
        }
    }

    @Override
    public void resendOnConnect() throws JsonProcessingException, InterruptedException, ExecutionException {
        for (Njams njams : sharingSupport.getAllNjamsInstances()) {
            sendOnConnect(njams);
        }
    }

    private void sendOnConnect(Njams njams) throws JsonProcessingException, InterruptedException, ExecutionException {
        sendOnConnectMessage(IS_SHARED, connectionId, instanceId, njams.getClientPath().toString(),
                njams.getClientVersion(), njams.getSdkVersion(), njams.getMachine(), njams.getCategory());
    }

 
    @Override
    public Path getReceiverPath(Object requestMessage, Instruction instruction) {
        return new Path(instruction.getRequestParameterByName("processPath"));
    }

    @Override
    public void sendReply(Object requestMessage, Instruction reply) {
       //      

    }

   

}