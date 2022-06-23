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

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;

import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.HostResolver;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder;

import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CloudReceiver extends AbstractReceiver {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CloudReceiver.class);

    private static final Boolean isShared = false;

    private String mainEndpoint;
    protected String endpoint;
    protected String instanceId;
    protected String connectionId;
    protected String certificateFile;
    protected String privateKeyFile;
    protected String caFile;

    protected MqttClientConnection connection;

    protected String topicName;

    private String apikey;

    protected boolean retryConnection = false;
    protected ScheduledExecutorService retryService = null;
    protected static final int retryInterval = 600000;

    @Override
    public String getName() {
        return NjamsSettings.PROPERTY_CLOUD_NAME;
    }

    @Override
    public void init(Properties properties) {

        String apikeypath = properties.getProperty(NjamsSettings.PROPERTY_CLOUD_APIKEY);

        if (apikeypath == null) {
            LOG.error("Please provide property {} for CloudSender", NjamsSettings.PROPERTY_CLOUD_APIKEY);
        }

        String instanceIdPath = properties.getProperty(NjamsSettings.PROPERTY_CLOUD_INSTANCEID);

        if (instanceIdPath == null) {
            LOG.error("Please provide property {} for CloudSender", NjamsSettings.PROPERTY_CLOUD_INSTANCEID);
        }

        try {
            apikey = ApiKeyReader.getApiKey(apikeypath);
        } catch (Exception e) {
            LOG.error("Failed to load api key from file " + apikeypath, e);
            throw new IllegalStateException("Failed to load api key from file");
        }

        try {
            instanceId = ApiKeyReader.getApiKey(instanceIdPath);
        } catch (Exception e) {
            LOG.error("Failed to load instanceId from file " + instanceIdPath, e);
            throw new IllegalStateException("Failed to load instanceId from file");
        }

        mainEndpoint = properties.getProperty(NjamsSettings.PROPERTY_CLOUD_ENDPOINT);

        if (mainEndpoint == null) {
            LOG.error("Please provide property {} for CloudSender", NjamsSettings.PROPERTY_CLOUD_ENDPOINT);
        }

        try {
            endpoint = getClientEndpoint(mainEndpoint);
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("Unable to init CloudReceiver", ex);
        }

        if (endpoint == null) {
            LOG.error("Please provide property {} for CloudReceiver", NjamsSettings.PROPERTY_CLOUD_ENDPOINT);
        }

        certificateFile = properties.getProperty(NjamsSettings.PROPERTY_CLOUD_CERTIFICATE);
        if (certificateFile == null) {
            LOG.error("Please provide property {} for CloudReceiver", NjamsSettings.PROPERTY_CLOUD_CERTIFICATE);
        }

        privateKeyFile = properties.getProperty(NjamsSettings.PROPERTY_CLOUD_PRIVATEKEY);
        if (privateKeyFile == null) {
            LOG.error("Please provide property {} for CloudReceiver", NjamsSettings.PROPERTY_CLOUD_PRIVATEKEY);
        }

        caFile = properties.getProperty(NjamsSettings.PROPERTY_CLOUD_CA);
        if (caFile == null) {
            LOG.error("Please provide property {} for CloudReceiver", NjamsSettings.PROPERTY_CLOUD_CA);
        }

        // build connectionId String
        CloudClientId cloudClientId = CloudClientId.getInstance(instanceId, isShared);
        connectionId = cloudClientId.clientId + "_" + cloudClientId.suffix;
        LOG.debug("connectionId: {}", connectionId);

    }

    protected void retryInit() {
        try {
            this.endpoint = getClientEndpoint(mainEndpoint);
            connect();
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("unable to init CloudReceiver", ex);
        }
    }

    @Override
    public void stop() {
        if (!isConnected()) {
            return;
        }
        connectionStatus = ConnectionStatus.DISCONNECTED;
        try {
            // getMqttclient().disconnect();
        } catch (Exception e) {
            LOG.error("Error disconnecting MQTTClient", e);
        }
    }

    /**
     * @return the client endpoint
     */
    protected String getClientEndpoint(String endpoint) throws Exception {
        String endpointUrl = "https://" + endpoint.trim() + "/v1/endpoints";

        URL url = new URL(endpointUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("x-api-key", apikey);

        int responseCode = connection.getResponseCode();
        LOG.debug("Sending 'GET' request to URL : " + url);
        LOG.debug("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        LOG.debug("Response Payload : " + response.toString());

        Endpoints endpoints = JsonUtils.parse(response.toString(), Endpoints.class);

        if (endpoints.isError()) {
            LOG.error(endpoints.getErrorMessage());

            LOG.info("Try to establish connection again in 10 min.");
            retryConnection = true;

            return "https://localhost";
        }
        retryConnection = false;
        return endpoints.getClient();
    }

    /**
     * @return the topicName
     */
    public String getTopicName() {
        return topicName;
    }

    /**
     * @return the endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }

    public String getInstanceId() {
        return instanceId;
    }

    /**
     * @return the clientId
     */
    public String getClientId() {
        return connectionId;
    }

    /**
     * @return the certificateFile
     */
    public String getCertificateFile() {
        return certificateFile;
    }

    /**
     * @return the privateKeyFile
     */
    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    protected String getPayload(String presignedUrl) throws Exception {
        URL url = new URL(presignedUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != 200) {
            throw new NjamsSdkRuntimeException("Error while retrieving payload");
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }

    @Override
    public synchronized void connect() {

        if (isConnected()) {
            return;
        }

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

    protected void onConnectionSuccess() {

    }

    public void resendOnConnect() throws JsonProcessingException, InterruptedException, ExecutionException {
        sendOnConnectMessage(isShared, connectionId, instanceId, njams.getClientPath().toString(),
                njams.getClientVersion(), njams.getSdkVersion(), njams.getMachine(), njams.getCategory());
    }

    protected void sendOnConnectMessage(Boolean isShared, String connectionId, String instanceId, String path,
            String clientVersion, String sdkVersion, String machine, String category)
            throws JsonProcessingException, InterruptedException, ExecutionException {
        String topicName = "/onConnect/";
        OnConnectMessage onConnectMessage = new OnConnectMessage(isShared, connectionId, instanceId, path,
                clientVersion, sdkVersion, machine, category);

        ObjectMapper objectMapper = new ObjectMapper();

        String message = objectMapper.writeValueAsString(onConnectMessage);

        CompletableFuture<Integer> published = connection
                .publish(new MqttMessage(topicName, message.getBytes(), QualityOfService.AT_LEAST_ONCE, false));
        published.get();

        LOG.info("Sent message: {} to topic: {}", message, topicName);
    }

    /**
     * Every time onInstruction in the AbstractReceiver is called, the instruction's request is extended by this method.
     *
     * @param request
     *            the request that will be extended
     * @return an exception response if there was a problem while retrieving payload from nJAMS Cloud. If everything
     *         worked fine, it returns null.
     */
    @Override
    protected Response extendRequest(Request request) {
        String payloadUrl = "";
        Map<String, String> parameters = request.getParameters();
        if (parameters != null) {
            payloadUrl = parameters.get("PayloadUrl");
        }
        if (StringUtils.isNotBlank(payloadUrl)) {
            try {
                parameters.put("Payload", getPayload(payloadUrl));
            } catch (Exception e) {
                LOG.warn("Error while retrieving payload from nJAMS Cloud {}", request.getCommand());
                Response response = new Response();
                response.setResultCode(1);
                response.setResultMessage("Error while retrieving payload from nJAMS Cloud.");
                return response;
            }
        }
        // Everything worked fine
        return null;
    }

    public Instruction getInstruction(String message) {
        try {
            Instruction instruction = JsonUtils.parse(message, Instruction.class);
            if (instruction.getRequest() != null) {
                return instruction;
            }
        } catch (Exception e) {
            LOG.error("Error deserializing Instruction", e);
        }
        LOG.warn("MSG is not a valid Instruction");
        return null;
    }

    public String getUUID(Instruction instruction) {
        try {
            if (instruction != null && instruction.getRequest() != null
                    && instruction.getRequest().getParameters() != null) {
                return instruction.getRequest().getParameters().get("mqttUuid");
            }
        } catch (Exception e) {
            LOG.error("Error deserializing Instruction", e);
        }
        LOG.warn("Instruction does not contain a property mqttUuid");
        return null;
    }

    public void reply(Instruction instruction, String uuid) {
        try {
            // clear Payload to avoid messages exceeding limit
            instruction.setRequestParameter("Payload", "");
            instruction.setRequestParameter("PayloadUrl", "");

            String response = JsonUtils.serialize(instruction);
            String replyTopic = "/" + instanceId + "/replies/";
            
            LOG.debug("Send reply on {}:\n{}", replyTopic, response);

            CompletableFuture<Integer> published = connection
                    .publish(new MqttMessage(replyTopic, response.getBytes(), QualityOfService.AT_LEAST_ONCE, false));
            published.get();

           
        } catch (Exception e) {
            LOG.error("Error while sending reply for {}", uuid, e);
        }
    }

}