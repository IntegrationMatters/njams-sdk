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
package com.im.njams.sdk.communication.cloud;

import com.amazonaws.services.iot.client.AWSIotMessage;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.cloud.CertificateUtil.KeyStorePasswordPair;
import com.im.njams.sdk.utils.JsonUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;
import java.util.UUID;
import javax.net.ssl.HttpsURLConnection;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pnientiedt
 */
public class CloudReceiver extends AbstractReceiver {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CloudTopic.class);

    private String endpoint;
    private String instanceId;
    private String clientId;
    private String certificateFile;
    private String privateKeyFile;

    private AWSIotQos qos;
    private AWSIotMqttClient mqttclient;
    private String topicName;

    private KeyStorePasswordPair keyStorePasswordPair;
    private String apikey;
    
    private UUID uuid;

    @Override
    public String getName() {
        return CloudConstants.NAME;
    }

    @Override
    public void init(Properties properties) {
        
        uuid = UUID.randomUUID();

        String apikeypath = properties.getProperty(CloudConstants.APIKEY);

        if (apikeypath == null) {
            LOG.error("Please provide property {} for CloudSender", CloudConstants.APIKEY);
        }

        try {
            apikey = ApiKeyReader.getApiKey(apikeypath);
        } catch (Exception e) {
            LOG.error("Failed to load api key from file " + apikeypath, e);
            throw new IllegalStateException("Failed to load api key from file");
        }

        try {
            endpoint = getClientEndpoint(properties.getProperty(CloudConstants.ENDPOINT));
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("unable to init http sender", ex);
        }

        if (endpoint == null) {
            LOG.error("Please provide property {} for CloudReceiver", CloudConstants.ENDPOINT);
        }
        instanceId = properties.getProperty(CloudConstants.CLIENT_INSTANCEID);
        if (instanceId == null) {
            LOG.error("Please provide property {} for CloudReceiver", CloudConstants.CLIENT_INSTANCEID);
        }
        certificateFile = properties.getProperty(CloudConstants.CLIENT_CERTIFICATE);
        if (certificateFile == null) {
            LOG.error("Please provide property {} for CloudReceiver", CloudConstants.CLIENT_CERTIFICATE);
        }
        privateKeyFile = properties.getProperty(CloudConstants.CLIENT_PRIVATEKEY);
        if (privateKeyFile == null) {
            LOG.error("Please provide property {} for CloudReceiver", CloudConstants.CLIENT_PRIVATEKEY);
        }
        LOG.info("Creating KeyStorePasswordPair from {} and {}", getCertificateFile(), getPrivateKeyFile());
        keyStorePasswordPair
                = CertificateUtil.getKeyStorePasswordPair(getCertificateFile(), getPrivateKeyFile());
        if (keyStorePasswordPair == null) {
            throw new IllegalStateException("Certificate or PrivateKey invalid");
        }
        clientId = instanceId + "_" + uuid.toString();
    }

    @Override
    public void stop() {
        if (!isConnected()) {
            return;
        }
        connectionStatus = ConnectionStatus.DISCONNECTED;
        try {
            getMqttclient().disconnect();
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

        Endpoints endpoints = JsonUtils.parse(response.toString(), Endpoints.class);

        return endpoints.client;
    }

    /**
     * @return the qos
     */
    public AWSIotQos getQos() {
        return qos;
    }

    /**
     * @param qos the qos to set
     */
    public void setQos(AWSIotQos qos) {
        this.qos = qos;
    }

    /**
     * @return the mqttclient
     */
    public AWSIotMqttClient getMqttclient() {
        return mqttclient;
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
        return clientId;
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
    
    protected String getPayload(String presignedUrl) throws Exception{
        URL url = new URL(presignedUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
      
        if(connection.getResponseCode() != 200){
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
    public void connect() {
        if (isConnected()) {
            return;
        }
        try {
            connectionStatus = ConnectionStatus.CONNECTING;
            LOG.debug("Connect to endpoint: {} with clientId: {}", endpoint, clientId);
            mqttclient = new AWSIotMqttClient(endpoint, clientId, keyStorePasswordPair.keyStore, keyStorePasswordPair.keyPassword);
            
            // optional parameters can be set before connect()
            getMqttclient().connect();
            setQos(AWSIotQos.QOS1);
            
            // send onConnect            
            topicName = "/onConnect/";            
            AWSIotMessage msg = new AWSIotMessage(topicName, AWSIotQos.QOS1, "{\"connectionId\":\""+uuid.toString()+"\", \"instanceId\":\""+instanceId+"\", \"path\":\""+ njams.getClientPath().toString()+"\", \"clientVersion\":\""+ njams.getClientVersion()+"\", \"sdkVersion\":\""+ njams.getSdkVersion()+"\", \"machine\":\""+ njams.getMachine()+"\" }");
            LOG.debug("Send message: {} to topic: {}", msg.getStringPayload(), topicName);
            getMqttclient().publish(msg);
            
            // subscribe commands topic      
            topicName = "/" + instanceId + "/commands/" + uuid.toString()+ "/";
            CloudTopic topic = new CloudTopic(this);
            
            LOG.debug("Topic Subscription: {}", topic.getTopic());
            
            getMqttclient().subscribe(topic);
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }
    
    @Override
    public void onInstruction(Instruction instruction) {
       if (njams == null) {
            LOG.error("Njams should not be null");
            return;
        }
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
        
        if(instruction.getRequestParameterByName("PayloadUrl") != null){   
            try{
               instruction.setRequestParameter("Payload", getPayload(instruction.getRequestParameterByName("PayloadUrl")));    
            }catch(Exception e){
                LOG.warn("Error while retrieving payload from nJAMS Cloud {}", instruction.getRequest().getCommand());
                Response response = new Response();
                response.setResultCode(1);
                response.setResultMessage("Error while retrieving payload from nJAMS Cloud.");
                instruction.setResponse(response);
            }
        }
         
 
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
            response.setResultMessage("No InstructionListener for " + instruction.getRequest().getCommand() + " found");
            instruction.setResponse(response);
        }  
    }
    
    
}
